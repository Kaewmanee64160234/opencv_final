package com.example.final_opencv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.concurrent.Executors

class CameraActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Camera Permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        }

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Initialization failed")
        } else {
            Log.d("OpenCV", "Initialization succeeded")
        }

        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                CameraWithAnalysisOverlay()
            }
        }
    }
}

@Composable
fun CameraWithAnalysisOverlay() {
    val context = LocalContext.current

    // States for brightness and glare
    var brightness by remember { mutableStateOf(0.0) }
    var glare by remember { mutableStateOf(0.0) }

    // A single-threaded executor for CameraX
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Remember CameraProvider
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Create a PreviewView for the camera feed
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        val cameraProvider = cameraProviderFuture.get()

        // Build CameraX preview
        val preview = androidx.camera.core.Preview.Builder()
            .build()
            .apply { setSurfaceProvider(previewView.surfaceProvider) }

        // Build Image Analysis use case
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            val mat = imageProxyToMat(imageProxy)
            mat?.let {
                try {
                    // Log frame dimensions
                    Log.d("FrameDimensions", "Frame width: ${it.width()}, height: ${it.height()}")

                    // Dynamically calculate ROI dimensions based on frame size
                    val roiX = (it.width() * 0.1).toInt()  // 10% from the left
                    val roiY = (it.height() * 0.1).toInt() // 10% from the top
                    val roiWidth = (it.width() * 0.8).toInt()  // 80% of the frame width
                    val roiHeight = (it.height() * 0.8).toInt() // 80% of the frame height

                    // Log ROI dimensions
                    Log.d("ROIDimensions", "ROI x=$roiX, y=$roiY, width=$roiWidth, height=$roiHeight")

                    // Ensure ROI is within frame boundaries
                    if (roiX + roiWidth <= it.width() && roiY + roiHeight <= it.height()) {
                        val roi = Mat(it, Rect(roiX, roiY, roiWidth, roiHeight))

                        // Calculate brightness and glare
                        brightness = calculateBrightness(roi)
                        glare = calculateGlarePercentage(roi)

                        Log.d("FrameAnalysis", "Brightness: ${"%.2f".format(brightness)}%, Glare: ${"%.2f".format(glare)}%")

                        roi.release()
                    } else {
                        Log.e("ROIError", "ROI dimensions exceed frame size: roiX=$roiX, roiY=$roiY, roiWidth=$roiWidth, roiHeight=$roiHeight")
                    }
                } catch (e: Exception) {
                    Log.e("FrameAnalysis", "Error analyzing frame: ${e.message}")
                } finally {
                    it.release()
                }
            }
            imageProxy.close()
        }

        // Bind CameraX lifecycle
        try {
            val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(context as ComponentActivity, cameraSelector, preview, imageAnalysis)
        } catch (e: Exception) {
            Log.e("CameraPreview", "Failed to bind camera: ${e.message}")
        }
    }

    // UI Composition
    Box(modifier = Modifier.fillMaxSize()) {
        // Show Camera Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView }
        )

        // Overlay showing brightness and glare
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text("Brightness: ${"%.2f".format(brightness)}%", style = TextStyle(color = Color.White, fontSize = 16.sp))
            Text("Glare: ${"%.2f".format(glare)}%", style = TextStyle(color = Color.White, fontSize = 16.sp))
        }

        // Rectangle overlay to indicate ROI
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                .size(300.dp, 400.dp)
        )
    }
}

// Convert ImageProxy to OpenCV Mat
private fun imageProxyToMat(image: ImageProxy): Mat? {
    return try {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val mat = Mat(image.height, image.width, CvType.CV_8UC1)
        mat.put(0, 0, bytes)

        val rgbMat = Mat()
        Imgproc.cvtColor(mat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21)
        mat.release()
        rgbMat
    } catch (e: Exception) {
        Log.e("ImageConversion", "Error converting ImageProxy to Mat: ${e.message}")
        null
    }
}

// Calculate brightness percentage
private fun calculateBrightness(mat: Mat): Double {
    val gray = Mat()
    Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
    val meanIntensity = Core.mean(gray).`val`[0]
    gray.release()
    return (meanIntensity / 255.0) * 100
}

// Calculate glare percentage
private fun calculateGlarePercentage(mat: Mat): Double {
    val gray = Mat()
    Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

    // Threshold the image to isolate bright regions
    val binary = Mat()
    Imgproc.threshold(gray, binary, 240.0, 255.0, Imgproc.THRESH_BINARY)

    val glarePixels = Core.countNonZero(binary)
    val totalPixels = mat.rows() * mat.cols()

    gray.release()
    binary.release()

    return (glarePixels.toDouble() / totalPixels.toDouble()) * 100
}
