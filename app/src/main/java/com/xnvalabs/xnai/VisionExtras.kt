package com.xnvalabs.xnai

import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.image.label.ImageLabeling
import com.google.mlkit.vision.image.label.defaults.ImageLabelerOptions

data class VisionExtraResult(
    val labels: List<String> = emptyList(),
    val faceCount: Int = 0,
    val barcodes: List<String> = emptyList(),
    val language: String? = null
)

class VisionExtras {
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .build()
    )
    private val barcodeScanner = BarcodeScanning.getClient()

    fun process(
        image: InputImage,
        onDone: (VisionExtraResult) -> Unit
    ) {
        var labels = emptyList<String>()
        var faces = 0
        var barcodes = emptyList<String>()
        var pending = 3

        fun doneOne() {
            pending--
            if (pending == 0) onDone(VisionExtraResult(labels, faces, barcodes))
        }

        labeler.process(image)
            .addOnSuccessListener { result ->
                labels = result.sortedByDescending { it.confidence }
                    .take(8)
                    .map { "${it.text} (${(it.confidence * 100).toInt()}%)" }
            }
            .addOnCompleteListener { doneOne() }

        faceDetector.process(image)
            .addOnSuccessListener { result -> faces = result.size }
            .addOnCompleteListener { doneOne() }

        barcodeScanner.process(image)
            .addOnSuccessListener { result ->
                barcodes = result.mapNotNull { it.rawValue }.take(8)
            }
            .addOnCompleteListener { doneOne() }
    }
}
