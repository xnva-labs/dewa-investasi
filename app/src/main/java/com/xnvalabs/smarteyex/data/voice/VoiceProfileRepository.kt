package com.xnvalabs.smarteyex.data.voice

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.SecureStorage
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
        load()
        initialized = true
    }

    fun observeProsody(energy: Float, speakingRate: Float, languageTag: String?) {
        val current = profile.value
        val n = current.sampleCount.coerceAtMost(999_999)
        val nextN = n + 1
        profile.value = current.copy(
            meanEnergy = ((current.meanEnergy * n) + energy.coerceIn(0f, 1f)) / nextN,
            speakingRate = ((current.speakingRate * n) + speakingRate.coerceIn(0f, 1f)) / nextN,
            preferredLanguageTag = languageTag?.take(20).orEmpty().ifBlank { current.preferredLanguageTag },
            sampleCount = nextN,
        )
        persist()
    }

    fun clear() {
        profile.value = VoiceProfile()
        SecureStorage.remove(KEY)
    }

    private fun persist() {
        SecureStorage.putString(KEY, JSONObject().apply {
            put("energy", profile.value.meanEnergy); put("rate", profile.value.speakingRate); put("language", profile.value.preferredLanguageTag); put("samples", profile.value.sampleCount)
        }.toString())
    }

    private fun load() {
        runCatching {
            SecureStorage.getString(KEY)?.let { o ->
                val j = JSONObject(o)
                profile.value = VoiceProfile(j.optDouble("energy", .5).toFloat().coerceIn(0f, 1f), j.optDouble("rate", .5).toFloat().coerceIn(0f, 1f), j.optString("language", "id-ID").take(20), j.optInt("samples", 0).coerceAtLeast(0))
            }
        }
    }
}
