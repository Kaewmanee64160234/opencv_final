package com.example.final_opencv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class CameraActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CameraWithRectangleOverlay()
            }
        }
    }
}

@Composable
fun CameraWithRectangleOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview()

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RectangleOverlay(
                modifier = Modifier
                    .fillMaxWidth(0.9f) // Adjust the overlay width (90% of the screen)
                    .aspectRatio(47f / 30f) // Maintain the new aspect ratio (27:47)
            )
        }
    }
}

@Composable
fun CameraPreview() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            bindToLifecycle(lifecycleOwner)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_START
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                controller = cameraController
            }
        },
        onRelease = {
            cameraController.unbind()
        }
    )
}

@Composable
fun RectangleOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(2.dp, Color.Gray, RoundedCornerShape(13.dp))
            .padding(7.dp) // Padding inside the card
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
//                circle
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .border(2.dp, Color.Black, CircleShape)
                )
                Spacer(modifier = Modifier.height(8.dp))
// barcode
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(160.dp)
                        .border(2.dp, Color.Black, RoundedCornerShape(5.dp))
                )
            }
        }
//card image
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
