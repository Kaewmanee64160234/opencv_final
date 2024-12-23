package com.example.final_opencv


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayInputStream

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.example/image_processor"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        if (!OpenCVLoader.initDebug()) {
            println("OpenCV failed to load")
            return
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "analyzeImage") {
                val imageBytes = call.arguments as ByteArray

                val analysisResults = analyzeImage(imageBytes)
                if (analysisResults != null) {
                    result.success(analysisResults)
                } else {
                    result.error("PROCESSING_ERROR", "Failed to analyze image", null)
                }
            } else {
                result.notImplemented()
            }
        }
    }

    private fun analyzeImage(imageBytes: ByteArray): Map<String, Any>? {
     return try {
         val inputStream = ByteArrayInputStream(imageBytes)
         val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
 
         val mat = Mat()
         val bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true)
         Utils.bitmapToMat(bmp32, mat)
 
         val aspectRatio = mat.width().toDouble() / mat.height()
         val brightnessPercentage = calculateBrightnessPercentage(mat)
         val glarePercentage = calculateGlarePercentage(mat)
         val laplacianVariance = calculateLaplacianVariance(mat)

 
         mapOf(
             "aspectRatio" to aspectRatio,
             "brightnessPercentage" to brightnessPercentage,
             "glarePercentage" to glarePercentage,
             "laplacianVariance" to laplacianVariance,

         )
     } catch (e: Exception) {
         e.printStackTrace()
         null
     }
 }
 

    private fun calculateBrightness(mat: Mat): Double {
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        return Core.mean(gray).`val`[0]
    }

    fun calculateLaplacianVariance(src: Mat): Double {
        if (src.empty()) {
            throw IllegalArgumentException("Input Mat is empty!")
        }

        // Ensure the input image is in grayscale
        val gray = Mat()
        if (src.channels() > 1) {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        } else {
            src.copyTo(gray)
        }

        // Apply Laplacian filter
        val laplacian = Mat()
        Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)

        // Calculate variance
        val mean = MatOfDouble()
        val stddev = MatOfDouble()
        Core.meanStdDev(laplacian, mean, stddev)

        // Variance is the square of the standard deviation
        val variance = Math.pow(stddev[0, 0][0], 2.0)

        // Release resources
        gray.release()
        laplacian.release()

        return variance
    }


private fun calculateBrightnessPercentage(mat: Mat): Double {
    val gray = Mat()
    Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

    // Calculate brightness as the mean intensity value
    val brightness = Core.mean(gray).`val`[0]

    // Normalize brightness to a percentage (assuming 0-255 intensity range)
    val brightnessPercentage = (brightness / 255.0) * 100

    // Release resources
    gray.release()

    return brightnessPercentage
}

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
}