package com.xnvalabs.smarteyex.data.intelligence

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.SecureStorage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded, encrypted personalization model. It learns preferences and
 * interaction patterns without retraining the foundation model on-device.
 */
data class UserPreference(
    val key: String,
    val value: String,
    val confidence: Float,
    val updatedAt: Long,
)

data class UserGoal(
    val title: String,
    val progress: Float,
    val updatedAt: Long,
)

object UserModelRepository {
    private const val PREFS_KEY = "intelligence.user_model.v1"
    private const val MAX_PREFERENCES = 100
    private const val MAX_GOALS = 50
    private const val MAX_TEXT = 500
    private var initialized = false

    val preferences = mutableStateOf(emptyList<UserPreference>())
    val goals = mutableStateOf(emptyList<UserGoal>())
    val interactionCount = mutableStateOf(0L)

    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        load(SecureStorage.getString(PREFS_KEY))
        initialized = true
    }

    fun observeInteraction() {
        interactionCount.value = (interactionCount.value + 1L).coerceAtMost(10_000_000L)
        persist()
    }

    fun learnPreference(key: String, value: String, confidence: Float = 0.65f) {
        if (!initialized) return
        val safeKey = key.trim().take(100)
        val safeValue = value.trim().take(MAX_TEXT)
        if (safeKey.isBlank() || safeValue.isBlank()) return
        val now = System.currentTimeMillis()
        val existing = preferences.value.firstOrNull { it.key == safeKey }
        val nextConfidence = if (existing == null) confidence else ((existing.confidence * 0.65f) + (confidence * 0.35f)).coerceIn(0f, 1f)
        preferences.value = (preferences.value.filterNot { it.key == safeKey } + UserPreference(safeKey, safeValue, nextConfidence, now)).takeLast(MAX_PREFERENCES)
        persist()
    }

    fun setGoal(title: String, progress: Float = 0f) {
        if (!initialized) return
        val clean = title.trim().take(MAX_TEXT)
        if (clean.isBlank()) return
        goals.value = (goals.value.filterNot { it.title.equals(clean, ignoreCase = true) } + UserGoal(clean, progress.coerceIn(0f, 1f), System.currentTimeMillis())).takeLast(MAX_GOALS)
        persist()
    }

    fun updateGoal(title: String, progress: Float) {
        val clean = title.trim().take(MAX_TEXT)
        goals.value = goals.value.map { if (it.title.equals(clean, true)) it.copy(progress = progress.coerceIn(0f, 1f), updatedAt = System.currentTimeMillis()) else it }
        persist()
    }

    fun contextSummary(): String = buildString {
        if (preferences.value.isNotEmpty()) append("Preferences: ").append(preferences.value.takeLast(20).joinToString("; ") { "${it.key}=${it.value}" })
        if (goals.value.isNotEmpty()) {
            if (isNotEmpty()) append(" | ")
            append("Goals: ").append(goals.value.takeLast(10).joinToString("; ") { "${it.title}=${(it.progress * 100).toInt()}%" })
        }
        if (interactionCount.value > 0L) {
            if (isNotEmpty()) append(" | ")
            append("Interactions: ").append(interactionCount.value)
        }
    }.take(4000)

    fun clear() {
        preferences.value = emptyList()
        goals.value = emptyList()
        interactionCount.value = 0L
        persist()
    }

    private fun persist() {
        if (!initialized) return
        val json = JSONObject().apply {
            put("interactions", interactionCount.value)
            put("preferences", JSONArray().apply {
                preferences.value.forEach { p -> put(JSONObject().apply { put("key", p.key); put("value", p.value); put("confidence", p.confidence); put("updatedAt", p.updatedAt) }) }
            })
            put("goals", JSONArray().apply {
                goals.value.forEach { g -> put(JSONObject().apply { put("title", g.title); put("progress", g.progress); put("updatedAt", g.updatedAt) }) }
            })
        }
        SecureStorage.putString(PREFS_KEY, json.toString())
    }

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
                    if (key.isNotBlank() && value.isNotBlank()) add(UserPreference(key, value, p.optDouble("confidence", 0.5).toFloat().coerceIn(0f, 1f), p.optLong("updatedAt", 0L)))
                }
            }.takeLast(MAX_PREFERENCES)
            val goalArray = json.optJSONArray("goals")
            goals.value = buildList {
                if (goalArray != null) for (i in 0 until goalArray.length()) {
                    val g = goalArray.optJSONObject(i) ?: continue
                    val title = g.optString("title").take(MAX_TEXT)
                    if (title.isNotBlank()) add(UserGoal(title, g.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f), g.optLong("updatedAt", 0L)))
                }
            }.takeLast(MAX_GOALS)
        }
    }
}
