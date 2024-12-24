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
import androidx.compose.ui.tooling.preview.Preview
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

        // Centered Row for the overlay
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
    // Responsive outer card container
    Box(
        modifier = modifier
            .border(2.dp, Color.Gray, RoundedCornerShape(13.dp)) // Border with rounded corners
            .padding(7.dp) // Padding inside the card
    ) {
        // Use Row for left and right elements
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween // Distribute content horizontally
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween // Distribute content vertically
            ) {
                // Circle at the top-left
                Box(
                    modifier = Modifier
                        .size(45.dp) // Circle size
                        .border(2.dp, Color.Black, CircleShape) // Circle border
                )
                Spacer(modifier = Modifier.height(8.dp)) // Add some space between elements

                // Long rectangle on the left
                Box(
                    modifier = Modifier
                        .width(30.dp) // Rectangle width
                        .height(160.dp) // Fixed height
                        .border(2.dp, Color.Black, RoundedCornerShape(5.dp)) // Rectangle border
                )
            }
        }

        // Bottom-right rectangle with margins
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd) // Align to bottom-right
                .padding(end = 5.dp, bottom = 17.dp) // Add margins
        ) {
            Box(
                modifier = Modifier
                    .width(75.dp) // Rectangle width
                    .height(95.dp) // Fixed height
                    .border(2.dp, Color.Black, RoundedCornerShape(5.dp)) // Rectangle border
            )
        }
    }
}
