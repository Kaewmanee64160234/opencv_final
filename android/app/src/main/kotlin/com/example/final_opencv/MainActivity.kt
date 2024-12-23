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
            val brightness = calculateBrightness(mat)
            val laplacianVariance = calculateLaplacianVariance(mat)
            val contrast = calculateContrast(mat)
            val glare = calculateGlare(mat)
            val noiseSNR = calculateNoiseSNR(mat)

            mapOf(
                "aspectRatio" to aspectRatio,
                "brightness" to brightness,
                "laplacianVariance" to laplacianVariance,
                "contrast" to contrast,
                "glare" to glare,
                "noiseSNR" to noiseSNR
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




    private fun calculateGlare(mat: Mat): Double {
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        val minMaxLoc = Core.minMaxLoc(gray)
        return minMaxLoc.maxVal
    }

    private fun calculateContrast(mat: Mat): Double {
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        // Use MatOfDouble for mean and stddev
        val mean = MatOfDouble()
        val stddev = MatOfDouble()
        Core.meanStdDev(gray, mean, stddev)

        return stddev[0, 0][0] // Return standard deviation as contrast
    }

    private fun calculateNoiseSNR(mat: Mat): Double {
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        // Use MatOfDouble for mean and stddev
        val mean = MatOfDouble()
        val stddev = MatOfDouble()
        Core.meanStdDev(gray, mean, stddev)

        val meanValue = mean[0, 0][0]
        val stddevValue = stddev[0, 0][0]

        return if (stddevValue != 0.0) meanValue / stddevValue else 0.0 // Handle divide-by-zero case
    }

}


