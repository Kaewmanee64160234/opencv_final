package com.example.final_opencv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageCapture: ImageCapture

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
        val photoFiles = mutableListOf<File>()

        for (i in 0 until totalCaptures) {
            val photoFile = File(
                externalMediaDirs.firstOrNull(),
                "IMG_${System.currentTimeMillis()}_$i.jpg"
            )
            photoFiles.add(photoFile)
        }

        fun captureNextImage() {
            if (currentCapture >= totalCaptures) {
                Log.d("Burst", "Captured $totalCaptures images.")
                return
            }

            val photoFile = photoFiles[currentCapture]
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        Log.d("Burst", "Image ${currentCapture + 1} saved: ${photoFile.absolutePath}")
                        currentCapture++
                        captureNextImage()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("Burst", "Error capturing image ${currentCapture + 1}: ${exception.message}")
                    }
                }
            )
        }

        captureNextImage()
    }
}

