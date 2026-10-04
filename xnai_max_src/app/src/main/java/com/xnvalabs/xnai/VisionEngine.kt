package com.xnvalabs.xnai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import kotlin.math.abs

enum class VisionMode { TEXT, OBJECTS, SHAPES, ALL }

data class ShapeResult(
    val name: String,
    val vertices: Int,
    val area: Double
)

data class ObjectResult(
    val label: String,
    val confidence: Float
)

data class VisionResult(
    val text: String = "",
    val objects: List<ObjectResult> = emptyList(),
    val shapes: List<ShapeResult> = emptyList(),
    val labels: List<String> = emptyList(),
    val faceCount: Int = 0,
    val barcodes: List<String> = emptyList(),
    val language: String? = null
)

object ImageProxyConverter {
    fun toBitmap(image: ImageProxy): Bitmap? {
        return try {
            val media = image.image ?: return null
            val nv21 = yuv420888ToNv21(media)
            val yuv = YuvImage(nv21, ImageFormat.NV21, media.width, media.height, null)
            val stream = ByteArrayOutputStream()
            yuv.compressToJpeg(Rect(0, 0, media.width, media.height), 78, stream)
            BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size())
        } catch (_: Throwable) {
            null
        }
    }

    private fun yuv420888ToNv21(image: android.media.Image): ByteArray {
        val width = image.width
        val height = image.height
        val out = ByteArray(width * height + width * height / 2)

        copyPlane(
            image.planes[0],
            width,
            height,
            out,
            0,
            1
        )

        val u = image.planes[1]
        val v = image.planes[2]
        var outputIndex = width * height

        val rowStride = u.rowStride
        val pixelStride = u.pixelStride
        val uBuffer = u.buffer
        val vBuffer = v.buffer

        for (row in 0 until height / 2) {
            val uRow = row * rowStride
            val vRow = row * v.rowStride
            for (col in 0 until width / 2) {
                val uIndex = uRow + col * pixelStride
                val vIndex = vRow + col * v.pixelStride
                out[outputIndex++] = vBuffer.get(vIndex)
                out[outputIndex++] = uBuffer.get(uIndex)
            }
        }
        return out
    }

    private fun copyPlane(
        plane: android.media.Image.Plane,
        width: Int,
        height: Int,
        out: ByteArray,
        offset: Int,
        pixelStep: Int
    ) {
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        var target = offset

        for (row in 0 until height) {
            val rowStart = row * rowStride
            for (col in 0 until width) {
                val index = rowStart + col * pixelStride
                if (index < buffer.limit()) {
                    out[target] = buffer.get(index)
                    target += pixelStep
                }
            }
            if (row + 1 < height) {
                while (target < offset + (row + 1) * width) target++
            }
        }
    }
}

object ShapeDetector {
    fun detect(bitmap: Bitmap): List<ShapeResult> {
        return try {
            val mat = Mat()
            val gray = Mat()
            val blurred = Mat()
            val edges = Mat()
            Utils.bitmapToMat(bitmap, mat)
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 70.0, 160.0)

            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(
                edges,
                contours,
                Mat(),
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val result = contours.mapNotNull { contour ->
                val area = Imgproc.contourArea(contour)
                if (area < bitmap.width * bitmap.height * 0.003) return@mapNotNull null
                val curve = org.opencv.core.MatOfPoint2f(*contour.toArray())
                val peri = Imgproc.arcLength(curve, true)
                val approx = org.opencv.core.MatOfPoint2f()
                Imgproc.approxPolyDP(curve, approx, 0.025 * peri, true)
                val n = approx.toArray().size
                val name = when {
                    n == 3 -> "Segitiga"
                    n == 4 -> {
                        val rect = Imgproc.boundingRect(contour)
                        val ratio = rect.width.toDouble() / rect.height.coerceAtLeast(1)
                        if (abs(ratio - 1.0) < 0.18) "Persegi" else "Persegi panjang"
                    }
                    n == 5 -> "Pentagon"
                    n == 6 -> "Heksagon"
                    n > 6 -> "Lingkaran / oval"
                    else -> null
                }
                name?.let { ShapeResult(it, n, area) }
            }
            .sortedByDescending { it.area }
            .take(12)
            .also {
                contours.forEach(MatOfPoint::release)
            }

            mat.release()
            gray.release()
            blurred.release()
            edges.release()
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
