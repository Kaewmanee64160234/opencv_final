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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
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

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        cameraExecutor.shutdown()
    }

    @Composable
    fun CameraPreviewScreen() {
        val context = this
        val previewView = remember { PreviewView(context) }
        var brightnessText by remember { mutableStateOf("Calculating...") }
        var statusMessage by remember { mutableStateOf("Analyzing...") }
        val brightnessList = remember { mutableStateListOf<Double>() }
        val glareList = remember { mutableStateListOf<Double>() }

        // Coroutine to periodically calculate averages
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000) // Every second
                val avgBrightness = brightnessList.averageOrNull() ?: 0.0
                val avgGlare = glareList.averageOrNull() ?: 0.0

                // Update status message based on averages
                statusMessage = when {
                    avgBrightness < 81 -> "Brightness too low. Increase lighting."
                    avgBrightness > 155 -> "Brightness too high. Reduce lighting."
                    avgGlare > 20.0 -> "High glare detected. Adjust lighting."
                    else -> "Lighting conditions are optimal."
                }

                // Update displayed brightness
                brightnessText = "Avg Brightness: ${"%.2f".format(avgBrightness)}, Avg Glare: ${"%.2f".format(avgGlare)}"

                // Clear lists for next interval
                brightnessList.clear()
                glareList.clear()
            }
        }

        LaunchedEffect(Unit) {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()

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
                    context,
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
                Text(
                    text = statusMessage,
                    color = Color.White,
                    fontSize = 18.sp
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        captureBurstImages(imageCapture, 5)
                    }
                ) {
                    Text("Capture 5 Images")
                }
            }
        }
    }

    // Extension to calculate average or return null
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
            if (area > 100) { // Ignore small noise
                glareArea += area // Add the area of bright regions
            }
        }

        gray.release()
        binary.release()

        return glareArea // Return the total glare area
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

        // Map the rectangle dimensions to the bitmap size
        val scaleX = bitmap.width.toFloat() / previewWidth
        val scaleY = bitmap.height.toFloat() / previewHeight

        val cropX = (rectX * scaleX).toInt()
        val cropY = (rectY * scaleY).toInt()
        val cropWidth = (rectWidth * scaleX).toInt()
        val cropHeight = (rectHeight * scaleY).toInt()

        if (cropWidth <= 0 || cropHeight <= 0 || cropX < 0 || cropY < 0 ||
            cropX + cropWidth > bitmap.width || cropY + cropHeight > bitmap.height
        ) {
            Log.e("CropError", "Invalid crop dimensions: x=$cropX, y=$cropY, width=$cropWidth, height=$cropHeight")
            return null
        }

        return Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)
    }
    private fun captureBurstImages(imageCapture: ImageCapture, totalCaptures: Int = 5) {
        var currentCapture = 0
        val photoFiles = List(totalCaptures) { index ->
            File(externalMediaDirs.firstOrNull(), "CROPPED_IMG_${System.currentTimeMillis()}_$index.jpg")
        }

        GlobalScope.launch(Dispatchers.Main) {
            while (currentCapture < totalCaptures) {
                val photoFile = photoFiles[currentCapture]
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(this@CameraActivity),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            GlobalScope.launch(Dispatchers.IO) {
                                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                if (bitmap != null) {
                                    val croppedBitmap = cropToCreditCardAspect(bitmap)
                                    if (croppedBitmap != null) {
                                        saveBitmap(croppedBitmap, photoFile)
                                        Log.d("Burst", "Image ${currentCapture + 1} saved: ${photoFile.absolutePath}")
                                    }
                                } else {
                                    Log.e("Burst", "Failed to decode bitmap for image ${currentCapture + 1}")
                                }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("Burst", "Error capturing image ${currentCapture + 1}: ${exception.message}")
                        }
                    }
                )

                currentCapture++
            }
            Log.d("Burst", "Captured and cropped $totalCaptures images.")
        }
    }


    private fun cropToCreditCardAspect(bitmap: Bitmap): Bitmap? {
        val creditCardAspectRatio = 3.37f / 2.125f // Aspect ratio 3.37:2.125

        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height

        val rectWidth = bitmapWidth * 0.7f
        val rectHeight = rectWidth / creditCardAspectRatio
        val rectLeft = (bitmapWidth - rectWidth) / 2
        val rectTop = (bitmapHeight - rectHeight) / 2

        val cropLeft = rectLeft.coerceAtLeast(0f).toInt()
        val cropTop = rectTop.coerceAtLeast(0f).toInt()
        val cropWidth = rectWidth.coerceAtMost(bitmapWidth - cropLeft.toFloat()).toInt()
        val cropHeight = rectHeight.coerceAtMost(bitmapHeight - cropTop.toFloat()).toInt()

        return try {
            Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)
        } catch (e: Exception) {
            Log.e("Crop", "Error cropping to credit card aspect: ${e.message}")
            null
        }
    }

    private fun saveBitmap(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.flush()
            }
            Log.d("Crop", "Cropped image saved: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("Crop", "Error saving cropped image: ${e.message}")
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
