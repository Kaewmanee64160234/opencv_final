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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
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
