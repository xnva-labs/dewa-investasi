package com.xnvalabs.smarteyex.data.companion

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.AppDiagnostics
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
        initialized = true
        if (!PrivacyRepository.settings.value.memoryEnabled) {
            clearStoredPersonalization()
            return
        }
        load()
    }

    fun observeUserText(text: String) {
        if (text.isBlank()) return
        if (!PrivacyRepository.settings.value.memoryEnabled) return
        val inferred = EmotionEngine.inferFromText(text)
        val nextEmotion = EmotionEngine.merge(emotion.value, inferred)
        UserModelRepository.observeInteraction()
        inferPreference(text)
        if (!persist(profile.value, nextEmotion)) {
            AppDiagnostics.warn("Companion personalization was not persisted")
            return
        }
        emotion.value = nextEmotion
    }

    fun recordVoiceProsody(energy: Float, speakingRate: Float) {
        val safeEnergy = energy.coerceIn(0f, 1f)
        val state = when {
            safeEnergy > 0.82f && speakingRate > 0.65f -> EmotionalState.EXCITEMENT
            safeEnergy < 0.25f && speakingRate < 0.35f -> EmotionalState.SADNESS
            else -> EmotionalState.CALM
        }
        val snapshot = EmotionalSnapshot(
            state,
            (0.25f + safeEnergy * 0.45f).coerceIn(0f, 1f),
            0.35f,
            System.currentTimeMillis(),
        )
        if (!PrivacyRepository.settings.value.memoryEnabled) return
        val nextEmotion = EmotionEngine.merge(emotion.value, snapshot)
        if (persist(profile.value, nextEmotion)) emotion.value = nextEmotion
    }

    fun responseStyle(): String {
        val p = profile.value
        val e = EmotionEngine.decay(emotion.value)
        val mode = currentMode.value.name.lowercase()
        return "mode=$mode; warmth=${p.warmth}; verbosity=${p.verbosity}; expression=${p.emotionalExpression}; state=${e.state.name.lowercase()}; intensity=${"%.2f".format(e.intensity)}"
    }

    fun companionContext(): String = buildString {
        append("Companion style: ").append(responseStyle())
        val model = if (PrivacyRepository.settings.value.memoryEnabled) UserModelRepository.contextSummary() else ""
        if (model.isNotBlank()) append(" | ").append(model)
        append(" | Boundaries: do not claim consciousness, do not manipulate dependency, respect privacy and user autonomy.")
    }.take(5000)

    fun clearPersonalization(): Boolean = clearPersonalizationSynchronously()

    fun clearPersonalizationSynchronously(): Boolean {
        val modelCleared = UserModelRepository.clearSynchronously()
        val profileCleared = runCatching { SecureStorage.removeSync(KEY_PROFILE) }
            .onFailure { AppDiagnostics.warn("Companion profile clear failed", it) }
            .getOrDefault(false)
        val emotionCleared = runCatching { SecureStorage.removeSync(KEY_EMOTION) }
            .onFailure { AppDiagnostics.warn("Companion emotion clear failed", it) }
            .getOrDefault(false)
        val ok = modelCleared && profileCleared && emotionCleared
        if (ok) resetInMemory()
        return ok
    }

    private fun inferPreference(text: String) {
        val normalized = text.lowercase()
        when {
            normalized.contains("jawab singkat") || normalized.contains("jangan panjang") ->
                UserModelRepository.learnPreference("response_length", "concise", 0.9f)
            normalized.contains("jelasin detail") || normalized.contains("lebih detail") ->
                UserModelRepository.learnPreference("response_length", "detailed", 0.9f)
            normalized.contains("bahasa indonesia") ->
                UserModelRepository.learnPreference("language", "Indonesian", 0.9f)
        }
    }

    private fun persist(nextProfile: CompanionProfile, nextEmotion: EmotionalSnapshot): Boolean {
        if (!initialized) return false
        val profileJson = JSONObject().apply {
            put("mode", nextProfile.mode.name)
            put("warmth", nextProfile.warmth.coerceIn(0f, 1f))
            put("proactivity", nextProfile.proactivity.coerceIn(0f, 1f))
            put("verbosity", nextProfile.verbosity.coerceIn(0f, 1f))
            put("humor", nextProfile.humor.coerceIn(0f, 1f))
            put("expression", nextProfile.emotionalExpression.coerceIn(0f, 1f))
            put("boundaryStrength", nextProfile.boundaryStrength.coerceIn(0f, 1f))
        }.toString()
        val emotionJson = JSONObject().apply {
            put("state", nextEmotion.state.name)
            put("intensity", nextEmotion.intensity.coerceIn(0f, 1f))
            put("confidence", nextEmotion.confidence.coerceIn(0f, 1f))
            put("updatedAt", nextEmotion.updatedAt)
        }.toString()
        return runCatching {
            SecureStorage.putStringsSync(
                mapOf(KEY_PROFILE to profileJson, KEY_EMOTION to emotionJson),
            )
        }.onFailure { AppDiagnostics.warn("Companion persistence failed", it) }.getOrDefault(false)
    }

    private fun resetInMemory() {
        profile.value = CompanionProfile()
        currentMode.value = CompanionMode.FRIEND
        emotion.value = EmotionalSnapshot()
    }

    private fun clearStoredPersonalization() {
        runCatching { SecureStorage.removeSync(KEY_PROFILE) }
            .onFailure { AppDiagnostics.warn("Companion profile privacy cleanup failed", it) }
        runCatching { SecureStorage.removeSync(KEY_EMOTION) }
            .onFailure { AppDiagnostics.warn("Companion emotion privacy cleanup failed", it) }
        resetInMemory()
    }

    private fun load() {
        runCatching {
            SecureStorage.getString(KEY_PROFILE)?.let { json ->
                val o = JSONObject(json)
                val mode = runCatching { CompanionMode.valueOf(o.optString("mode")) }.getOrDefault(CompanionMode.FRIEND)
                val nextProfile = CompanionProfile(
                    mode = mode,
                    warmth = o.optDouble("warmth", .7).toFloat().coerceIn(0f, 1f),
                    proactivity = o.optDouble("proactivity", .55).toFloat().coerceIn(0f, 1f),
                    verbosity = o.optDouble("verbosity", .35).toFloat().coerceIn(0f, 1f),
                    humor = o.optDouble("humor", .25).toFloat().coerceIn(0f, 1f),
                    emotionalExpression = o.optDouble("expression", .7).toFloat().coerceIn(0f, 1f),
                    boundaryStrength = o.optDouble("boundaryStrength", 1.0).toFloat().coerceIn(0f, 1f),
                )
                profile.value = nextProfile
                currentMode.value = mode
            }
            SecureStorage.getString(KEY_EMOTION)?.let { json ->
                val o = JSONObject(json)
                val state = runCatching { EmotionalState.valueOf(o.optString("state")) }.getOrDefault(EmotionalState.CALM)
                emotion.value = EmotionalSnapshot(
                    state,
                    o.optDouble("intensity", 0.0).toFloat().coerceIn(0f, 1f),
                    o.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f),
                    o.optLong("updatedAt", 0L),
                )
            }
        }.onFailure { AppDiagnostics.warn("Companion data load failed", it) }
    }
}
