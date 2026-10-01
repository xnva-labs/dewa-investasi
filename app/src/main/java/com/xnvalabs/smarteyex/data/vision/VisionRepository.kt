package com.xnvalabs.smarteyex.data.vision

import android.util.Base64
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.NetworkClient
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import com.xnvalabs.smarteyex.data.xnai.XnaiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Secure camera-to-cloud vision client. */
object VisionRepository {
    suspend fun analyzeFrame(jpegBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        if (!PrivacyRepository.settings.value.cameraEnabled) return@withContext Result.failure(IllegalStateException("Camera OFF — aktifkan di Privacy Control."))
        if (!PrivacyRepository.settings.value.cloudProcessingEnabled) return@withContext Result.failure(IllegalStateException("Cloud Processing OFF — aktifkan di Privacy Control."))
        if (jpegBytes.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Frame kamera kosong."))
        if (jpegBytes.size > 2_000_000) return@withContext Result.failure(IllegalArgumentException("Frame terlalu besar. Ambil gambar lagi."))
        if (XnaiRepository.endpointUrl.isBlank()) return@withContext Result.failure(IllegalStateException("Layanan Vision belum dikonfigurasi untuk build ini."))

        runCatching {
            val body = JSONObject().apply {
                put("imageBase64", Base64.encodeToString(jpegBytes, Base64.NO_WRAP))
                put("mimeType", "image/jpeg")
            }
            val response = NetworkClient.postJson(XnaiRepository.endpointUrl, "/vision", body).getOrThrow()
            NetworkClient.requireSuccess(response, "Vision").optString("description").trim().also {
                require(it.isNotBlank()) { "Vision mengembalikan deskripsi kosong." }
            }
        }.onFailure { AppDiagnostics.warn("Vision request failed", it) }
    }
}
