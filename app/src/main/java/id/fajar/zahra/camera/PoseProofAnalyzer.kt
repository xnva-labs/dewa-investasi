package id.fajar.zahra.camera

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

/** Optional proof analyzer. It provides evidence, never an absolute verdict. */
object PoseProofAnalyzer {
    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build()
    )

    fun analyze(image: InputImage, onResult: (Evidence) -> Unit) {
        detector.process(image)
            .addOnSuccessListener { pose -> onResult(scoreSquatEvidence(pose)) }
            .addOnFailureListener { onResult(Evidence(false, 0f, "Analisis tidak tersedia, gunakan konfirmasi manual.")) }
    }

    private fun scoreSquatEvidence(pose: Pose): Evidence {
        val left = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_KNEE)
        val hip = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_HIP)
        val ankle = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_ANKLE)
        if (left == null || hip == null || ankle == null) return Evidence(false, 0f, "Landmark tubuh tidak cukup terlihat.")
        val dyHipKnee = kotlin.math.abs(left.position3D.y - hip.position3D.y)
        val dyKneeAnkle = kotlin.math.abs(ankle.position3D.y - left.position3D.y)
        val confidence = ((dyHipKnee + dyKneeAnkle) / 250f).coerceIn(0f, 1f)
        return Evidence(confidence > 0.30f, confidence, "Evidence pose: ${(confidence * 100).toInt()}%")
    }

    data class Evidence(val suggestedMatch: Boolean, val confidence: Float, val message: String)
}
