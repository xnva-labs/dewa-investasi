package id.fajar.zahra.camera

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

object ObjectProofAnalyzer {
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    fun analyze(image: InputImage, expectedTarget: String, onResult: (Evidence) -> Unit) {
        labeler.process(image)
            .addOnSuccessListener { labels ->
                val top = labels.sortedByDescending { it.confidence }.take(5)
                if (top.isEmpty()) {
                    onResult(Evidence(false, 0f, "Tidak ada label objek yang cukup meyakinkan."))
                } else {
                    val best=top.first()
                    val target=expectedTarget.trim().lowercase()
                    val match=target.isBlank() || top.any{it.text.lowercase().contains(target) && it.confidence>=0.55f}
                    val message=if(target.isBlank()) "Objek terdeteksi: ${best.text}" else "Target '$expectedTarget' ${if(match) "terlihat cocok" else "belum terlihat cocok"}; label terbaik: ${best.text}."
                    onResult(Evidence(match,best.confidence,message))
                }
            }
            .addOnFailureListener { onResult(Evidence(false, 0f, "Analisis objek tidak tersedia, gunakan konfirmasi manual.")) }
    }

    data class Evidence(val suggestedMatch:Boolean,val confidence:Float,val message:String)
}
