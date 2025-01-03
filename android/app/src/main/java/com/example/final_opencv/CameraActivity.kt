package com.example.final_opencv

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberImagePainter
import kotlinx.coroutines.*
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageCapture: ImageCapture

    private var rectX = 0
    private var rectY = 0
    private var rectWidth = 0
    private var rectHeight = 0
    private var imagePathList = mutableStateListOf<String>()

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OpenCV
        if (!org.opencv.android.OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "OpenCV initialization failed")
        } else {
            Log.d("OpenCV", "OpenCV initialization successful")
        }

        // Request Camera Permissions
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            CameraPreviewScreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown() // Only clean up executor
    }

    override fun onStop() {
        super.onStop()
        // Do not manually unbind CameraX
    }

    private fun findSharpestImage(imagePaths: List<String>): Pair<String?, Double> {
        var sharpestPath: String? = null
        var maxVariance = 0.0

        // Iterate through each image path
        for (path in imagePaths) {
            // Decode the file into a Bitmap (you can also use a sampled decode for performance)
            val bitmap = BitmapFactory.decodeFile(path) ?: continue

            // Convert the Bitmap to an OpenCV Mat
            val mat = bitmapToMat(bitmap) ?: continue

            // Ensure the Mat is non-empty
            if (!mat.empty()) {
                // Calculate Laplacian variance (sharpness measure)
                val variance = calculateLaplacianVariance(mat)

                // Update maximum if this image is sharper
                if (variance > maxVariance) {
                    maxVariance = variance
                    sharpestPath = path
                }
                // Release the Mat to avoid memory leaks
                mat.release()
            }
        }

        // Return the sharpest path and its variance
        return Pair(sharpestPath, maxVariance)
    }
    @Composable
    fun CameraPreviewScreen() {
        val context = LocalContext.current
        val previewView = remember { PreviewView(context) }
    
        var statusMessage by rememberSaveable { mutableStateOf("Analyzing...") }
        val brightnessList = remember { mutableStateListOf<Double>() }
        val glareList = remember { mutableStateListOf<Double>() }
        var optimalLightingDetected by rememberSaveable { mutableStateOf(false) }
        var showSuccessMessage by rememberSaveable { mutableStateOf(false) }
        var captureCompleted by rememberSaveable { mutableStateOf(false) }
        var sharpestImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    
        // Coroutine to periodically calculate averages
        LaunchedEffect(Unit) {
            // Offload the loop to a background dispatcher
            withContext(Dispatchers.Default) {
                while (true) {
                    delay(1000)

                    val avgBrightness: Double
                    val avgGlare: Double

                    // Safely compute averages here in background
                    avgBrightness = brightnessList.averageOrNull() ?: 0.0
                    avgGlare = glareList.averageOrNull() ?: 0.0

                    // Switch to Main thread to update UI
                    withContext(Dispatchers.Main) {
                        statusMessage = when {
                            avgBrightness < 81 -> "Brightness too low. Increase lighting."
                            avgBrightness > 155 -> "Brightness too high. Reduce lighting."
                            avgGlare > 20.0 -> "High glare detected. Adjust lighting."
                            else -> "Lighting conditions are optimal."
                        }
                        optimalLightingDetected = statusMessage == "Lighting conditions are optimal."
                        brightnessList.clear()
                        glareList.clear()
                    }
                }
            }
        }


        LaunchedEffect(optimalLightingDetected) {
            if (optimalLightingDetected && !captureCompleted) {
                delay(2000)
                if (optimalLightingDetected) {
                    captureBurstImages(imageCapture, 5) {
                        // Called after all images are saved
                        showSuccessMessage = true
                        captureCompleted = true

                        // Now find the sharpest image off the main thread
                        // This entire function is already a suspend function
                        val (bestPath, bestVariance) = findSharpestImage(imagePathList)
                        sharpestImagePath = bestPath

                        // Clear & only keep the best
                        imagePathList.clear()
                        if (bestPath != null) {
                            imagePathList.add(bestPath)
                            Log.d("CameraActivity", "Sharpest image: $bestPath, variance=$bestVariance")
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            val cameraProvider = ProcessCameraProvider.getInstance(this@CameraActivity).get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val croppedBitmap = cropImageProxyToRect(
                    imageProxy,
                    rectX,
                    rectY,
                    rectWidth,
                    rectHeight,
                    previewView.width,
                    previewView.height
                )
                if (croppedBitmap != null) {
                    val mat = bitmapToMat(croppedBitmap)
                    if (mat != null && !mat.empty()) {
                        val brightness = calculateBrightness(mat)
                        val brightArea = analyzeBrightRegions(mat)

                        brightnessList.add(brightness)
                        glareList.add(brightArea)

                        mat.release()
                    }
                }
                imageProxy.close()
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this@CameraActivity, // Use the activity itself as LifecycleOwner
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e("CameraActivity", "Failed to bind use cases", exc)
            }
        }


        Box(modifier = Modifier.fillMaxSize()) {
            if (!captureCompleted) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { previewView }
                )
    
                RectangleOverlay(
                    modifier = Modifier.align(Alignment.Center),
                    onOverlayPositioned = { x, y, width, height ->
                        rectX = x
                        rectY = y
                        rectWidth = width
                        rectHeight = height
                    }
                )
    
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = statusMessage, color = Color.White, fontSize = 18.sp)
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Capture Successful!", color = Color.Green, fontSize = 20.sp)
                }

                LazyColumn {
                    sharpestImagePath?.let { path ->
                        item {
                            // Decode the image again for display (sampled to e.g. 800x800)
                            val sampledBitmap = decodeSampledBitmap(path, 800, 800)
                            if (sampledBitmap != null) {
                                val mat = bitmapToMat(sampledBitmap)
                                val variance = mat?.let { calculateLaplacianVariance(it) } ?: 0.0
                                mat?.release()

                                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                    // Show the image (Coil can handle sampling too)
                                    Image(
                                        painter = rememberImagePainter(path),
                                        contentDescription = "Captured Image",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                    )
                                    Text(
                                        text = "Laplacian Variance: $variance",
                                        color = Color.Black,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = "Failed to load image: $path",
                                    color = Color.Red,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }


                Button(
                    onClick = {
                        captureCompleted = false
                        showSuccessMessage = false
                        sharpestImagePath = null
                        imagePathList.clear()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(text = "Reset Capture")
                }




            }
        }
    }

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }


    private fun calculateLaplacianVariance(mat: Mat): Double {
        val laplacian = Mat()
        Imgproc.Laplacian(mat, laplacian, mat.depth())

        val mean = MatOfDouble()
        val stddev = MatOfDouble()

        // Correct call to Core.meanStdDev
        Core.meanStdDev(laplacian, mean, stddev)

        val variance = stddev[0, 0][0] * stddev[0, 0][0] // Variance = (StdDev)^2
        laplacian.release()

        return variance
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private fun analyzeBrightRegions(mat: Mat): Double {
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        val binary = Mat()
        Imgproc.threshold(gray, binary, 230.0, 255.0, Imgproc.THRESH_BINARY)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        var glareArea = 0.0
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > 100) {
                glareArea += area
            }
        }

        gray.release()
        binary.release()
        return glareArea
    }

    private fun calculateBrightness(mat: Mat): Double {
        return try {
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
            val meanIntensity = Core.mean(gray).`val`[0]
            gray.release()
            meanIntensity
        } catch (e: Exception) {
            e.printStackTrace()
            -1.0
        }
    }

    private fun cropImageProxyToRect(
        imageProxy: ImageProxy,
        rectX: Int,
        rectY: Int,
        rectWidth: Int,
        rectHeight: Int,
        previewWidth: Int,
        previewHeight: Int
    ): Bitmap? {
        val bitmap = imageProxyToBitmap(imageProxy) ?: return null

        val scaleX = bitmap.width.toFloat() / previewWidth
        val scaleY = bitmap.height.toFloat() / previewHeight

        val cropX = (rectX * scaleX).toInt()
        val cropY = (rectY * scaleY).toInt()
        val cropWidth = (rectWidth * scaleX).toInt()
        val cropHeight = (rectHeight * scaleY).toInt()

        if (cropWidth <= 0 || cropHeight <= 0 || cropX < 0 || cropY < 0 ||
            cropX + cropWidth > bitmap.width || cropY + cropHeight > bitmap.height
        ) {
            Log.e("CropError", "Invalid crop dimensions")
            return null
        }

        return Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)
    }
    private fun bitmapToMat(bitmap: Bitmap): Mat? {
        return try {
            val mat = Mat()
            org.opencv.android.Utils.bitmapToMat(bitmap, mat)
            mat
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    private fun captureBurstImages(
        imageCapture: ImageCapture,
        totalCaptures: Int = 5,
        onComplete: () -> Unit
    ) {
        var remainingCaptures = totalCaptures
        val photoFiles = List(totalCaptures) { index ->
            File(externalMediaDirs.firstOrNull(), "IMG_${System.currentTimeMillis()}_$index.jpg")
        }

        GlobalScope.launch(Dispatchers.Main) {
            photoFiles.forEach { photoFile ->
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(this@CameraActivity),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            GlobalScope.launch(Dispatchers.IO) {
                                // Save path
                                imagePathList.add(photoFile.absolutePath)
                                Log.d("Burst", "Image saved: ${photoFile.absolutePath}")

                                // Decrement remaining count
                                remainingCaptures--

                                // If all captures are done, invoke onComplete
                                if (remainingCaptures == 0) {
                                    withContext(Dispatchers.Main) {
                                        onComplete()
                                    }
                                }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("Burst", "Error capturing image: ${exception.message}")
                            remainingCaptures--
                            if (remainingCaptures == 0) {
                                onComplete()
                            }
                        }
                    }
                )
            }
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val plane = imageProxy.planes[0]
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val yuvImage = android.graphics.YuvImage(
                bytes,
                android.graphics.ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )

            if (imageProxy.width <= 0 || imageProxy.height <= 0) {
                Log.e("ImageProxyError", "Invalid image dimensions: width=${imageProxy.width}, height=${imageProxy.height}")
                return null
            }

            val outputStream = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
                100,
                outputStream
            )
            val jpegBytes = outputStream.toByteArray()
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    @Composable
    fun RectangleOverlay(
        modifier: Modifier = Modifier,
        onOverlayPositioned: (Int, Int, Int, Int) -> Unit
    ) {
        Box(
            modifier = modifier
                .size(300.dp, 200.dp)
                .border(2.dp, Color.Red, RoundedCornerShape(8.dp))
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    val width = coordinates.size.width
                    val height = coordinates.size.height

                    if (width > 0 && height > 0) {
                        onOverlayPositioned(
                            position.x.toInt(),
                            position.y.toInt(),
                            width,
                            height
                        )
                    }
                }
        )
    }
}
