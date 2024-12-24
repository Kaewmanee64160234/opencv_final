package com.example.final_opencv

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Camera Permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
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

        LaunchedEffect(Unit) {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    context,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                // Handle camera binding failure
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

            // Capture Button
            Button(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                onClick = {
                    imageCapture?.let {
                        captureAndCropToAspectRatio(it, 3.37f / 2.125f, rectX, rectY, rectWidth, rectHeight, previewView.width, previewView.height)
                    }
                }
            ) {
                Text("Capture Rectangle", style = TextStyle(fontSize = 16.sp))
            }
        }
    }

    private fun captureAndCropToAspectRatio(
        imageCapture: ImageCapture,
        aspectRatio: Float, // Aspect ratio, e.g., 3.37f / 2.125f
        rectX: Int,
        rectY: Int,
        rectWidth: Int,
        rectHeight: Int,
        previewWidth: Int,
        previewHeight: Int
    ) {
        // Define the output file for the temporary full image
        val photoFile = File(
            externalMediaDirs.firstOrNull(),
            "TEMP_IMG_${System.currentTimeMillis()}.jpg"
        )

        // Set up output options to save the temporary image
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Take the picture
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Load the captured image
                    val fullImageBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)

                    // Crop the image to the specified aspect ratio
                    val croppedBitmap = cropToAspectRatio(fullImageBitmap, aspectRatio)

                    if (croppedBitmap != null) {
                        // Save the cropped bitmap
                        saveCroppedImage(croppedBitmap)
                    }

                    // Delete the temporary full image file
                    photoFile.delete()
                }

                override fun onError(exception: ImageCaptureException) {
                    // Handle any errors during image capture
                }
            }
        )
    }

    private fun cropToAspectRatio(bitmap: Bitmap, aspectRatio: Float): Bitmap? {
        val width = bitmap.width
        val height = bitmap.height

        // Calculate the width and height of the rectangle (bounding box)
        val rectWidth = width * 0.7f // Set width to 70% of image width
        val rectHeight = rectWidth / aspectRatio // Calculate height based on aspect ratio

        // Center the rectangle in the image
        val rectLeft = (width - rectWidth) / 2
        val rectTop = (height - rectHeight) / 2

        // Ensure the rectangle dimensions are valid
        if (rectLeft < 0 || rectTop < 0 || rectLeft + rectWidth > width || rectTop + rectHeight > height) {
            return null // If invalid, return null
        }

        // Crop the Bitmap according to the calculated rectangle area
        return Bitmap.createBitmap(
            bitmap,
            rectLeft.toInt(),
            rectTop.toInt(),
            rectWidth.toInt(),
            rectHeight.toInt()
        )
    }

    private fun saveCroppedImage(croppedBitmap: Bitmap) {
        val croppedFile = File(
            externalMediaDirs.firstOrNull(),
            "CROPPED_IMG_${System.currentTimeMillis()}.jpg"
        )

        val outputStream = FileOutputStream(croppedFile)
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
    }
}

@Composable
fun RectangleOverlay(
    modifier: Modifier = Modifier,
    onOverlayPositioned: (Int, Int, Int, Int) -> Unit // Callback for dimensions
) {
    Box(
        modifier = modifier
            .aspectRatio(3.37f / 2.125f) // Credit card aspect ratio
            .border(2.dp, Color.Gray, RoundedCornerShape(13.dp))
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
