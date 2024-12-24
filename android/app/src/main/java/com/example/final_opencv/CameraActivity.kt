package com.example.final_opencv

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Camera Permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        }

        // Initialize OpenCV
        if (!org.opencv.android.OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Initialization failed")
        } else {
            Log.d("OpenCV", "Initialization successful")
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            CameraPreviewWithRectangleOverlay()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    @Composable
    fun CameraPreviewWithRectangleOverlay() {
        val context = this

        val previewView = remember { PreviewView(context) }

        // Rectangle's position and size
        var rectX by remember { mutableStateOf(0) }
        var rectY by remember { mutableStateOf(0) }
        var rectWidth by remember { mutableStateOf(0) }
        var rectHeight by remember { mutableStateOf(0) }

        // Real-time brightness and glare values
        var brightness by remember { mutableStateOf(0.0) }
        var glare by remember { mutableStateOf(0.0) }

        LaunchedEffect(Unit) {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        Log.d("Debug", "Starting image analysis...")
                        val bitmap = imageProxyToBitmap(imageProxy)
                        if (bitmap == null) {
                            Log.e("Error", "Bitmap conversion failed")
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val croppedBitmap = cropBitmapToRect(bitmap, rectX, rectY, rectWidth, rectHeight)
                        if (croppedBitmap == null) {
                            Log.e("Error", "Cropping failed. Check rect bounds.")
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val mat = bitmapToMat(croppedBitmap)
                        if (mat == null || mat.empty()) {
                            Log.e("Error", "Failed to convert Bitmap to Mat.")
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val brightnessValue = calculateBrightness(mat)
                        Log.d("Debug", "Brightness: $brightnessValue")

                        val glareValue = calculateGlarePercentage(mat)
                        Log.d("Debug", "Glare: $glareValue")

                        // Update UI values
                        brightness = brightnessValue
                        glare = glareValue

                        mat.release()
                        imageProxy.close()
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

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
            // Camera Preview
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { previewView }
            )

            // Rectangle Overlay
            RectangleOverlay(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.95f), // Occupy 95% of screen width
                onOverlayPositioned = { x, y, width, height ->
                    rectX = x
                    rectY = y
                    rectWidth = width
                    rectHeight = height
                }
            )

            // Display brightness and glare percentages
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Brightness: ${brightness.toInt()}%", style = TextStyle(fontSize = 18.sp, color = Color.White))
                Text("Glare: ${glare.toInt()}%", style = TextStyle(fontSize = 18.sp, color = Color.White))
            }
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val yuvImage = android.graphics.YuvImage(
                bytes,
                android.graphics.ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )

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

    private fun cropBitmapToRect(bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap? {
        // Ensure the rectangle is within the bounds of the image
        val validX = x.coerceIn(0, bitmap.width - 1)
        val validY = y.coerceIn(0, bitmap.height - 1)
        val validWidth = width.coerceAtMost(bitmap.width - validX)
        val validHeight = height.coerceAtMost(bitmap.height - validY)

        // Check if the dimensions are valid for cropping
        if (validWidth <= 0 || validHeight <= 0) {
            Log.e("Error", "Invalid cropping dimensions: x=$validX, y=$validY, width=$validWidth, height=$validHeight")
            return null
        }

        return Bitmap.createBitmap(bitmap, validX, validY, validWidth, validHeight)
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
            (meanIntensity / 255.0) * 100
        } catch (e: Exception) {
            e.printStackTrace()
            0.0
        }
    }

    private fun calculateGlarePercentage(mat: Mat): Double {
        return try {
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

            val binary = Mat()
            Imgproc.threshold(gray, binary, 240.0, 255.0, Imgproc.THRESH_BINARY)

            val glarePixels = Core.countNonZero(binary)
            val totalPixels = mat.rows() * mat.cols()

            gray.release()
            binary.release()

            (glarePixels.toDouble() / totalPixels.toDouble()) * 100
        } catch (e: Exception) {
            e.printStackTrace()
            0.0
        }
    }
}

@Composable
fun RectangleOverlay(
    modifier: Modifier = Modifier,
    onOverlayPositioned: (Int, Int, Int, Int) -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(3.37f / 2.125f) // Credit card aspect ratio
            .border(2.dp, Color.Red, RoundedCornerShape(13.dp))
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                val width = coordinates.size.width
                val height = coordinates.size.height

                // Pass position and size of the overlay to the parent composable
                onOverlayPositioned(
                    position.x.toInt(),
                    position.y.toInt(),
                    width,
                    height
                )
            }
    )
}
