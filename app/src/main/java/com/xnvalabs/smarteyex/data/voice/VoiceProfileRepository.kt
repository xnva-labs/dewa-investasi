package com.xnvalabs.smarteyex.data.voice

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.SecureStorage
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import org.json.JSONObject

/**
 * Stores only bounded prosody statistics. It intentionally does not persist
 * raw microphone audio or create an identity/voiceprint by itself.
 */
object VoiceProfileRepository {
    private const val KEY = "voice.profile.v1"
    private var initialized = false
    val profile = mutableStateOf(VoiceProfile())

    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        initialized = true
        if (!PrivacyRepository.settings.value.voicePersonalizationEnabled) {
            runCatching { SecureStorage.removeSync(KEY) }
                .onFailure { AppDiagnostics.warn("Voice profile privacy cleanup failed", it) }
            profile.value = VoiceProfile()
            return
        }
        load()
    }

    fun observeProsody(energy: Float, speakingRate: Float, languageTag: String?): Boolean {
        if (!initialized || !PrivacyRepository.settings.value.voicePersonalizationEnabled) return false
        val current = profile.value
        val n = current.sampleCount.coerceIn(0, 999_999)
        val nextN = n + 1
        val next = current.copy(
            meanEnergy = ((current.meanEnergy * n) + energy.coerceIn(0f, 1f)) / nextN,
            speakingRate = ((current.speakingRate * n) + speakingRate.coerceIn(0f, 1f)) / nextN,
            preferredLanguageTag = languageTag?.take(20).orEmpty().ifBlank { current.preferredLanguageTag },
            sampleCount = nextN,
        )
        val saved = persist(next)
        if (saved) profile.value = next
        return saved
    }

    fun clear(): Boolean {
        val removed = runCatching { SecureStorage.removeSync(KEY) }
            .onFailure { AppDiagnostics.warn("Voice profile clear failed", it) }
            .getOrDefault(false)
        if (removed) profile.value = VoiceProfile()
        return removed
    }

    private fun persist(next: VoiceProfile): Boolean = runCatching {
        SecureStorage.putStringSync(KEY, JSONObject().apply {
            put("energy", next.meanEnergy.coerceIn(0f, 1f))
            put("rate", next.speakingRate.coerceIn(0f, 1f))
            put("language", next.preferredLanguageTag.take(20))
            put("samples", next.sampleCount.coerceIn(0, 1_000_000))
        }.toString())
    }.onFailure { AppDiagnostics.warn("Voice profile persistence failed", it) }.getOrDefault(false)

    private fun load() {
        runCatching {
            SecureStorage.getString(KEY)?.let { o ->
                val j = JSONObject(o)
                profile.value = VoiceProfile(
                    j.optDouble("energy", .5).toFloat().coerceIn(0f, 1f),
                    j.optDouble("rate", .5).toFloat().coerceIn(0f, 1f),
                    j.optString("language", "id-ID").take(20),
                    j.optInt("samples", 0).coerceIn(0, 1_000_000),
                )
            }
        }.onFailure { AppDiagnostics.warn("Voice profile parse failed", it) }
    }
}
