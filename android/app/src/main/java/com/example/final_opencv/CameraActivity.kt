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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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

    // States for RectangleOverlay position and size
    var overlayX by remember { mutableStateOf(0) }
    var overlayY by remember { mutableStateOf(0) }
    var overlayWidth by remember { mutableStateOf(0) }
    var overlayHeight by remember { mutableStateOf(0) }

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
                    // Map overlay dimensions to frame coordinates
                    val frameWidth = it.width()
                    val frameHeight = it.height()

                    val roiX = (overlayX.toFloat() / previewView.width * frameWidth).toInt()
                    val roiY = (overlayY.toFloat() / previewView.height * frameHeight).toInt()
                    val roiWidth = (overlayWidth.toFloat() / previewView.width * frameWidth).toInt()
                    val roiHeight = (overlayHeight.toFloat() / previewView.height * frameHeight).toInt()

                    // Ensure ROI is within frame boundaries
                    if (roiX + roiWidth <= frameWidth && roiY + roiHeight <= frameHeight) {
                        val roi = Mat(it, Rect(roiX, roiY, roiWidth, roiHeight))

                        // Perform brightness and glare calculations
                        brightness = calculateBrightness(roi)
                        glare = calculateGlarePercentage(roi)

                        Log.d("FrameAnalysis", "Brightness: ${"%.2f".format(brightness)}%, Glare: ${"%.2f".format(glare)}%")

                        roi.release()
                    } else {
                        Log.e("ROIError", "ROI dimensions exceed frame size: x=$roiX, y=$roiY, width=$roiWidth, height=$roiHeight")
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

        // Rectangle overlay
        RectangleOverlay(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.95f) // Adjust the width as 50% of the screen width
                .aspectRatio(1.59f), // Maintain ID card aspect ratio
            onOverlayPositioned = { x, y, width, height ->
                overlayX = x
                overlayY = y
                overlayWidth = width
                overlayHeight = height
            }
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
fun calculateGlarePercentage(mat: Mat): Double {
    // Ensure the input image is in grayscale
    val gray = Mat()
    Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

    // Threshold the grayscale image to identify bright regions (glare)
    val thresholdValue = 240.0 // Adjust this value based on your application
    val binary = Mat()
    Imgproc.threshold(gray, binary, thresholdValue, 255.0, Imgproc.THRESH_BINARY)

    // Count the number of pixels identified as glare
    val glarePixels = Core.countNonZero(binary)

    // Calculate the total number of pixels in the image
    val totalPixels = mat.rows() * mat.cols()

    // Compute glare percentage
    val glarePercentage = (glarePixels.toDouble() / totalPixels.toDouble()) * 100

    // Release resources
    gray.release()
    binary.release()

    return glarePercentage
}
@Composable
fun RectangleOverlay(
    modifier: Modifier = Modifier,
    onOverlayPositioned: (Int, Int, Int, Int) -> Unit // Callback for dimensions
) {
    Box(
        modifier = modifier
            .border(2.dp, Color.Gray, RoundedCornerShape(13.dp))
            .padding(7.dp) // Padding inside the card
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
            .aspectRatio(1.59f) // ID card aspect ratio: height/width = 1.59
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Circle
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .border(2.dp, Color.Black, CircleShape)
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Barcode
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(160.dp)
                        .border(2.dp, Color.Black, RoundedCornerShape(5.dp))
                )
            }
        }
        // Card Image
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 5.dp, bottom = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(75.dp)
                    .height(95.dp)
                    .border(2.dp, Color.Black, RoundedCornerShape(5.dp))
            )
        }

    }
}
