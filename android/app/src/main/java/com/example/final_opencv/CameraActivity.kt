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
import androidx.compose.material.Text
import androidx.compose.runtime.*
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
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load OpenCV native libraries
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
            CameraPreviewWithRealTimeAnalysis()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    @Composable
    fun CameraPreviewWithRealTimeAnalysis() {
        val context = this
        val previewView = remember { PreviewView(context) }

        // Rectangle overlay properties
        var rectX by remember { mutableStateOf(0) }
        var rectY by remember { mutableStateOf(0) }
        var rectWidth by remember { mutableStateOf(300) }
        var rectHeight by remember { mutableStateOf(200) }

        // Analysis values
        var brightnessList = remember { mutableStateListOf<Double>() }
        var glareList = remember { mutableStateListOf<Double>() }
        var statusMessage by remember { mutableStateOf("Analyzing...") }

        // Mutable state to store the last update timestamp
        var lastUpdateTime by remember { mutableStateOf(System.currentTimeMillis()) }

        LaunchedEffect(Unit) {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

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
                        val brightRegionStatus = analyzeBrightRegions(mat)

                        brightnessList.add(brightness)

                        if (System.currentTimeMillis() - lastUpdateTime >= 1000) {
                            // Calculate averages
                            val avgBrightness = brightnessList.average()

                            // Update status message
                            statusMessage = when {
                                avgBrightness < 81 -> "Brightness too low. Increase lighting."
                                avgBrightness > 155 -> "Brightness too high. Reduce lighting."
                                brightRegionStatus == "Watermark Detected" -> "Watermark detected."
                                brightRegionStatus == "Reflection Detected" -> "Reflection detected."
                                else -> "Lighting conditions are optimal."
                            }

                            // Clear lists for next interval
                            brightnessList.clear()

                            // Update the last update time
                            lastUpdateTime = System.currentTimeMillis()
                        }

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
                Text(statusMessage, style = TextStyle(fontSize = 18.sp, color = Color.White))
            }
        }
    }


    private fun analyzeBrightRegions(mat: Mat): String {
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        val binary = Mat()
        Imgproc.threshold(gray, binary, 230.0, 255.0, Imgproc.THRESH_BINARY)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > 100) { // Ignore small noise
                val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                val circularity = 4 * Math.PI * (area / (perimeter * perimeter))

                if (circularity > 0.7) {
                    gray.release()
                    binary.release()
                    return "Watermark Detected"
                } else {
                    gray.release()
                    binary.release()
                    return "Reflection Detected"
                }
            }
        }

        gray.release()
        binary.release()
        return "No significant regions detected"
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

//    private fun calculateGlarePercentage(mat: Mat): Double {
//        return try {
//            val gray = Mat()
//            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
//
//            val binary = Mat()
//            Imgproc.threshold(gray, binary, 230.0, 255.0, Imgproc.THRESH_BINARY)
//
//            val glarePixels = Core.countNonZero(binary)
//            val totalPixels = mat.rows() * mat.cols()
//
//            gray.release()
//            binary.release()
//
//            if (totalPixels > 0) {
//                (glarePixels.toDouble() / totalPixels.toDouble()) * 100.0
//            } else {
//                0.0
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            0.0
//        }
//    }
}

@Composable
fun RectangleOverlay(
    modifier: Modifier = Modifier,
    onOverlayPositioned: (Int, Int, Int, Int) -> Unit
) {
    Box(
        modifier = modifier
            .size(300.dp, 200.dp) // Fixed size for rectangle
            .border(2.dp, Color.Red, RoundedCornerShape(13.dp))
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                val width = coordinates.size.width
                val height = coordinates.size.height

                Log.d("RectangleOverlay", "Rectangle dimensions: x=${position.x}, y=${position.y}, width=$width, height=$height")

                if (width > 0 && height > 0) {
                    onOverlayPositioned(
                        position.x.toInt(),
                        position.y.toInt(),
                        width,
                        height
                    )
                } else {
                    Log.e("OverlayError", "Invalid rectangle dimensions: width=$width, height=$height")
                }
            }
    )
}
