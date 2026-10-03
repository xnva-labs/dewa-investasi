package com.xnvalabs.smarteyex.data.intelligence

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.SecureStorage
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded, encrypted personalization model. It learns preferences and
 * interaction patterns without retraining the foundation model on-device.
 * Personalization is only loaded/updated while Memory consent is enabled.
 */
data class UserPreference(
    val key: String,
    val value: String,
    val confidence: Float,
    val updatedAt: Long,
)

object UserModelRepository {
    private const val PREFS_KEY = "intelligence.user_model.v1"
    private const val MAX_PREFERENCES = 100
    private const val MAX_TEXT = 500
    private var initialized = false

    val preferences = mutableStateOf(emptyList<UserPreference>())
    val interactionCount = mutableStateOf(0L)

    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        initialized = true
        if (!PrivacyRepository.settings.value.memoryEnabled) {
            clearStoredPersonalization()
            return
        }
        load(SecureStorage.getString(PREFS_KEY))
    }

    fun observeInteraction(): Boolean {
        if (!initialized || !PrivacyRepository.settings.value.memoryEnabled) return false
        val nextCount = (interactionCount.value + 1L).coerceAtMost(10_000_000L)
        return persist(nextPreferences = preferences.value, nextInteractionCount = nextCount)
    }

    fun learnPreference(key: String, value: String, confidence: Float = 0.65f): Boolean {
        if (!initialized || !PrivacyRepository.settings.value.memoryEnabled) return false
        val safeKey = key.trim().take(100)
        val safeValue = value.trim().take(MAX_TEXT)
        if (safeKey.isBlank() || safeValue.isBlank()) return false
        val now = System.currentTimeMillis()
        val existing = preferences.value.firstOrNull { it.key == safeKey }
        val nextConfidence = if (existing == null) confidence.coerceIn(0f, 1f) else {
            ((existing.confidence * 0.65f) + (confidence.coerceIn(0f, 1f) * 0.35f)).coerceIn(0f, 1f)
        }
        val next = (preferences.value.filterNot { it.key == safeKey } + UserPreference(
            safeKey,
            safeValue,
            nextConfidence,
            now,
        )).takeLast(MAX_PREFERENCES)
        return persist(nextPreferences = next, nextInteractionCount = interactionCount.value)
    }

    fun contextSummary(): String = buildString {
        if (!PrivacyRepository.settings.value.memoryEnabled) return@buildString
        if (preferences.value.isNotEmpty()) {
            append("Preferences: ")
                .append(preferences.value.takeLast(20).joinToString("; ") { "${it.key}=${it.value}" })
        }
        if (interactionCount.value > 0L) {
            if (isNotEmpty()) append(" | ")
            append("Interactions: ").append(interactionCount.value)
        }
    }.take(4000)

    fun clear(): Boolean {
        if (!initialized) {
            preferences.value = emptyList()
            interactionCount.value = 0L
            return true
        }
        return clearSynchronously()
    }

    fun clearSynchronously(): Boolean {
        val json = emptyJson()
        val saved = runCatching { SecureStorage.putStringSync(PREFS_KEY, json) }
            .onFailure { AppDiagnostics.warn("User model clear failed", it) }
            .getOrDefault(false)
        if (saved) {
            preferences.value = emptyList()
            interactionCount.value = 0L
        }
        return saved
    }

    private fun persist(nextPreferences: List<UserPreference>, nextInteractionCount: Long): Boolean {
        if (!initialized || !PrivacyRepository.settings.value.memoryEnabled) return false
        val json = JSONObject().apply {
            put("interactions", nextInteractionCount.coerceIn(0L, 10_000_000L))
            put("preferences", JSONArray().apply {
                nextPreferences.takeLast(MAX_PREFERENCES).forEach { p ->
                    put(JSONObject().apply {
                        put("key", p.key.take(100))
                        put("value", p.value.take(MAX_TEXT))
                        put("confidence", p.confidence.coerceIn(0f, 1f))
                        put("updatedAt", p.updatedAt)
                    })
                }
            })
        }
        val saved = runCatching { SecureStorage.putStringSync(PREFS_KEY, json.toString()) }
            .onFailure { AppDiagnostics.warn("User model persistence failed", it) }
            .getOrDefault(false)
        if (saved) {
            preferences.value = nextPreferences.takeLast(MAX_PREFERENCES)
            interactionCount.value = nextInteractionCount.coerceIn(0L, 10_000_000L)
        }
        return saved
    }

    private fun clearStoredPersonalization() {
        runCatching { SecureStorage.removeSync(PREFS_KEY) }
            .onFailure { AppDiagnostics.warn("User model privacy cleanup failed", it) }
        preferences.value = emptyList()
        interactionCount.value = 0L
    }

    private fun emptyJson(): String = JSONObject().apply {
        put("interactions", 0L)
        put("preferences", JSONArray())
    }.toString()

    private fun load(raw: String?) {
        if (raw.isNullOrBlank()) return
        runCatching {
            val json = JSONObject(raw)
            interactionCount.value = json.optLong("interactions", 0L).coerceIn(0L, 10_000_000L)
            val prefArray = json.optJSONArray("preferences")
            preferences.value = buildList {
                if (prefArray != null) for (i in 0 until prefArray.length()) {
                    val p = prefArray.optJSONObject(i) ?: continue
                    val key = p.optString("key").take(100)
                    val value = p.optString("value").take(MAX_TEXT)
                    if (key.isNotBlank() && value.isNotBlank()) {
                        add(
                            UserPreference(
                                key,
                                value,
                                p.optDouble("confidence", 0.5).toFloat().coerceIn(0f, 1f),
                                p.optLong("updatedAt", 0L),
                            ),
                        )
                    }
                }
            }.takeLast(MAX_PREFERENCES)
        }.onFailure {
            AppDiagnostics.warn("User model parse failed", it)
            preferences.value = emptyList()
            interactionCount.value = 0L
        }
    }
}
