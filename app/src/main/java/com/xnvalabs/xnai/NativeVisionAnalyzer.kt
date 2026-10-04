package com.xnvalabs.xnai

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicInteger

class NativeVisionAnalyzer(
    private val mode: VisionMode,
    private val onResult: (VisionResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
    )
    private val extras = VisionExtras()
    private var frameCounter = 0

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        frameCounter++
        if (frameCounter % 3 != 0) { imageProxy.close(); return }

        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val needText = mode == VisionMode.TEXT || mode == VisionMode.ALL
        val needObjects = mode == VisionMode.OBJECTS || mode == VisionMode.ALL
        val needShapes = mode == VisionMode.SHAPES || mode == VisionMode.ALL
        val needExtras = mode == VisionMode.ALL || mode == VisionMode.OBJECTS || mode == VisionMode.TEXT

        val pending = AtomicInteger(
            (if (needText) 1 else 0) +
                (if (needObjects) 1 else 0) +
                (if (needExtras) 1 else 0)
        )
        var latestText = ""
        var latestObjects = emptyList<ObjectResult>()
        var latestShapes = emptyList<ShapeResult>()
        var latestExtras = VisionExtraResult()

        fun maybeClose() {
            if (pending.get() == 0) {
                onResult(
                    VisionResult(
                        text = latestText,
                        objects = latestObjects,
                        shapes = latestShapes,
                        labels = latestExtras.labels,
                        faceCount = latestExtras.faceCount,
                        barcodes = latestExtras.barcodes,
                        language = latestExtras.language
                    )
                )
                imageProxy.close()
            }
        }

        if (needShapes) {
            ImageProxyConverter.toBitmap(imageProxy)?.let {
                latestShapes = ShapeDetector.detect(it)
                it.recycle()
            }
        }

        if (needText) {
            recognizer.process(input)
                .addOnSuccessListener { latestText = it.text }
                .addOnCompleteListener { pending.decrementAndGet(); maybeClose() }
        }

        if (needObjects) {
            detector.process(input)
                .addOnSuccessListener { objects ->
                    latestObjects = objects.mapNotNull { item ->
                        item.labels.firstOrNull()?.let { label -> ObjectResult(label.text, label.confidence) }
                    }
                }
                .addOnCompleteListener { pending.decrementAndGet(); maybeClose() }
        }

        if (needExtras) {
            extras.process(input, onDone = {
                latestExtras = it
                pending.decrementAndGet()
                maybeClose()
            })
        }

        maybeClose()
    }
}
