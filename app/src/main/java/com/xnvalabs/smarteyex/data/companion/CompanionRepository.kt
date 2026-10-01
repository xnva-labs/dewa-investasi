package com.xnvalabs.smarteyex.data.companion

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.SecureStorage
import com.xnvalabs.smarteyex.data.intelligence.UserModelRepository
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import org.json.JSONObject

/** Coordinates companion behavior without pretending the model has subjective feelings. */
object CompanionRepository {
    private const val KEY_PROFILE = "companion.profile.v1"
    private const val KEY_EMOTION = "companion.emotion.v1"
    private var initialized = false

    val profile = mutableStateOf(CompanionProfile())
    val emotion = mutableStateOf(EmotionalSnapshot())
    val currentMode = mutableStateOf(CompanionMode.FRIEND)

    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        load()
        initialized = true
    }

    fun setMode(mode: CompanionMode) {
        currentMode.value = mode
        profile.value = profile.value.copy(mode = mode)
        persist()
    }

    fun observeUserText(text: String) {
        if (text.isBlank()) return
        val inferred = EmotionEngine.inferFromText(text)
        emotion.value = EmotionEngine.merge(emotion.value, inferred)
        if (PrivacyRepository.settings.value.memoryEnabled) {
            UserModelRepository.observeInteraction()
            inferPreference(text)
            persist()
        }
    }

    fun recordVoiceProsody(energy: Float, speakingRate: Float) {
        val safeEnergy = energy.coerceIn(0f, 1f)
        val state = when {
            safeEnergy > 0.82f && speakingRate > 0.65f -> EmotionalState.EXCITEMENT
            safeEnergy < 0.25f && speakingRate < 0.35f -> EmotionalState.SADNESS
            else -> EmotionalState.CALM
        }
        val snapshot = EmotionalSnapshot(state, (0.25f + safeEnergy * 0.45f).coerceIn(0f, 1f), 0.35f, System.currentTimeMillis())
        emotion.value = EmotionEngine.merge(emotion.value, snapshot)
        if (PrivacyRepository.settings.value.memoryEnabled) persist()
    }

    fun responseStyle(): String {
        val p = profile.value
        val e = EmotionEngine.decay(emotion.value)
        val mode = currentMode.value.name.lowercase()
        return "mode=$mode; warmth=${p.warmth}; verbosity=${p.verbosity}; expression=${p.emotionalExpression}; state=${e.state.name.lowercase()}; intensity=${"%.2f".format(e.intensity)}"
    }

    fun companionContext(): String = buildString {
        append("Companion style: ").append(responseStyle())
        val model = UserModelRepository.contextSummary()
        if (model.isNotBlank()) append(" | ").append(model)
        append(" | Boundaries: do not claim consciousness, do not manipulate dependency, respect privacy and user autonomy.")
    }.take(5000)

    fun clearPersonalization() {
        UserModelRepository.clear()
        emotion.value = EmotionalSnapshot()
        persist()
    }

    private fun inferPreference(text: String) {
        val normalized = text.lowercase()
        when {
            normalized.contains("jawab singkat") || normalized.contains("jangan panjang") -> UserModelRepository.learnPreference("response_length", "concise", 0.9f)
            normalized.contains("jelasin detail") || normalized.contains("lebih detail") -> UserModelRepository.learnPreference("response_length", "detailed", 0.9f)
            normalized.contains("bahasa indonesia") -> UserModelRepository.learnPreference("language", "Indonesian", 0.9f)
        }
    }

    private fun persist() {
        if (!initialized) return
        val p = profile.value
        SecureStorage.putString(KEY_PROFILE, JSONObject().apply {
            put("mode", p.mode.name); put("warmth", p.warmth); put("proactivity", p.proactivity); put("verbosity", p.verbosity); put("humor", p.humor); put("expression", p.emotionalExpression)
        }.toString())
        val e = emotion.value
        SecureStorage.putString(KEY_EMOTION, JSONObject().apply { put("state", e.state.name); put("intensity", e.intensity); put("confidence", e.confidence); put("updatedAt", e.updatedAt) }.toString())
    }

    private fun load() {
        runCatching {
            SecureStorage.getString(KEY_PROFILE)?.let { json ->
                val o = JSONObject(json)
                val mode = runCatching { CompanionMode.valueOf(o.optString("mode")) }.getOrDefault(CompanionMode.FRIEND)
                profile.value = CompanionProfile(mode, o.optDouble("warmth", .7).toFloat(), o.optDouble("proactivity", .55).toFloat(), o.optDouble("verbosity", .35).toFloat(), o.optDouble("humor", .25).toFloat(), o.optDouble("expression", .7).toFloat())
                currentMode.value = mode
            }
            SecureStorage.getString(KEY_EMOTION)?.let { json ->
                val o = JSONObject(json)
                val state = runCatching { EmotionalState.valueOf(o.optString("state")) }.getOrDefault(EmotionalState.CALM)
                emotion.value = EmotionalSnapshot(state, o.optDouble("intensity", 0.0).toFloat().coerceIn(0f, 1f), o.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f), o.optLong("updatedAt", 0L))
            }
        }
    }
}
