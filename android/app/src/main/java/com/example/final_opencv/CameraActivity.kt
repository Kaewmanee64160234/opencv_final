package com.example.final_opencv

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Bundle
import android.widget.Toast
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
                Toast.makeText(context, "Failed to bind camera use cases", Toast.LENGTH_SHORT).show()
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
                modifier = Modifier.align(Alignment.Center),
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
                        captureAndCropRectangleImage(it, rectX, rectY, rectWidth, rectHeight, previewView.width, previewView.height)
                    }
                }
            ) {
                Text("Capture Rectangle", style = TextStyle(fontSize = 16.sp))
            }
        }
    }

    private fun captureAndCropRectangleImage(
        imageCapture: ImageCapture,
        rectX: Int,
        rectY: Int,
        rectWidth: Int,
        rectHeight: Int,
        previewWidth: Int,
        previewHeight: Int
    ) {
        // Define the output file
        val photoFile = File(
            externalMediaDirs.firstOrNull(),
            "TEMP_IMG_${System.currentTimeMillis()}.jpg"
        )

        // Set up output options to save the image
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Take the picture
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Load the captured image
                    val fullImageBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)

                    // Calculate scale factors between preview and captured image
                    val scaleX = fullImageBitmap.width.toFloat() / previewWidth
                    val scaleY = fullImageBitmap.height.toFloat() / previewHeight

                    // Map the rectangle dimensions to the captured image's coordinate system
                    val scaledX = (rectX * scaleX).toInt()
                    val scaledY = (rectY * scaleY).toInt()
                    val scaledWidth = (rectWidth * scaleX).toInt()
                    val scaledHeight = (rectHeight * scaleY).toInt()

                    // Ensure the rectangle stays within the image boundaries
                    val validRect = Rect(
                        scaledX.coerceAtLeast(0),
                        scaledY.coerceAtLeast(0),
                        (scaledX + scaledWidth).coerceAtMost(fullImageBitmap.width),
                        (scaledY + scaledHeight).coerceAtMost(fullImageBitmap.height)
                    )

                    // Crop the bitmap based on the scaled rectangle
                    val croppedBitmap = Bitmap.createBitmap(
                        fullImageBitmap,
                        validRect.left,
                        validRect.top,
                        validRect.width(),
                        validRect.height()
                    )

                    // Save only the cropped bitmap
                    saveCroppedImage(croppedBitmap)

                    // Delete the temporary full image file
                    photoFile.delete()

                    // Display success
                    Toast.makeText(this@CameraActivity, "Cropped Image Saved!", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    // Handle any errors that occur during image capture
                    Toast.makeText(this@CameraActivity, "Error capturing image: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
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
            .border(2.dp, Color.Red, RoundedCornerShape(13.dp))
            .padding(7.dp)
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
            .aspectRatio(1.59f) // ID card aspect ratio
    )
}
