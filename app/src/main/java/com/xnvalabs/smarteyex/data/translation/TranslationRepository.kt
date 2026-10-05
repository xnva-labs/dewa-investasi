package com.xnvalabs.smarteyex.data.translation

import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.NetworkClient
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import com.xnvalabs.smarteyex.data.xnai.XnaiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

object TranslationRepository {
    suspend fun translate(text: String, targetLanguage: String): Result<String> = withContext(Dispatchers.IO) {
        if (!PrivacyRepository.settings.value.cloudProcessingEnabled) return@withContext Result.failure(IllegalStateException("Cloud Processing OFF — aktifkan di Privacy Control."))
        val cleanText = text.trim()
        val cleanLanguage = targetLanguage.trim().take(20)
        if (cleanText.isBlank()) return@withContext Result.failure(IllegalArgumentException("Teks kosong."))
        if (cleanText.length > 10000) return@withContext Result.failure(IllegalArgumentException("Teks terlalu panjang."))
        if (cleanLanguage.isBlank()) return@withContext Result.failure(IllegalArgumentException("Bahasa tujuan kosong."))
        if (XnaiRepository.endpointUrl.isBlank()) return@withContext Result.failure(IllegalStateException("Layanan Translation belum dikonfigurasi untuk build ini."))

        runCatching {
            val body = JSONObject().apply { put("text", cleanText); put("targetLanguage", cleanLanguage) }
            val response = NetworkClient.postJson(XnaiRepository.endpointUrl, "/translate", body, UUID.randomUUID().toString()).getOrThrow()
            NetworkClient.requireSuccess(response, "Translation").optString("translated").trim().also {
                require(it.isNotBlank()) { "Translation mengembalikan hasil kosong." }
            }
        }.onFailure { AppDiagnostics.warn("Translation request failed", it) }
    }
}
