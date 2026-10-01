package com.xnvalabs.smarteyex.data.education

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.SecureStorage
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StudyItem(
    val id: String,
    val title: String,
    val subject: String,
    val done: Boolean = false,
    val completedAt: Long? = null,
)

object EducationRepository {
    private const val KEY_ITEMS = "education.items"
    private const val LEGACY_PREFS = "smarteyex_education"
    private const val MAX_ITEMS = 500
    private var initialized = false

    var items = mutableStateOf(emptyList<StudyItem>())
        private set

    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        val legacy = context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val raw = SecureStorage.getString(KEY_ITEMS) ?: legacy.getString("items", null)
        items.value = load(raw)
        val secureReady = SecureStorage.getString(KEY_ITEMS) != null || (raw != null && SecureStorage.putStringSync(KEY_ITEMS, raw))
        if (secureReady || raw == null) legacy.edit().clear().apply()
        initialized = true
    }

    fun add(title: String, subject: String): Boolean {
        val cleanTitle = title.trim().take(200)
        val cleanSubject = subject.trim().take(120)
        if (cleanTitle.isBlank()) return false
        persist((items.value + StudyItem(UUID.randomUUID().toString(), cleanTitle, cleanSubject)).takeLast(MAX_ITEMS))
        return true
    }

    fun toggleDone(id: String) = persist(items.value.map { item ->
        if (item.id != id) item else if (item.done) item.copy(done = false, completedAt = null) else item.copy(done = true, completedAt = System.currentTimeMillis())
    })

    fun remove(id: String) = persist(items.value.filterNot { it.id == id })
    fun clearAll() = persist(emptyList(), synchronous = true)

    private fun persist(next: List<StudyItem>, synchronous: Boolean = false) {
        items.value = next
        val serialized = JSONArray().apply {
            next.takeLast(MAX_ITEMS).forEach { i ->
                put(JSONObject().apply {
                    put("id", i.id); put("title", i.title); put("subject", i.subject); put("done", i.done)
                    if (i.completedAt == null) put("completedAt", JSONObject.NULL) else put("completedAt", i.completedAt)
                })
            }
        }.toString()
        if (synchronous) SecureStorage.putStringSync(KEY_ITEMS, serialized) else SecureStorage.putString(KEY_ITEMS, serialized)
    }

    private fun load(raw: String?): List<StudyItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val title = obj.optString("title").trim()
                if (title.isBlank()) null else StudyItem(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    title = title.take(200),
                    subject = obj.optString("subject").take(120),
                    done = obj.optBoolean("done", false),
                    completedAt = if (obj.isNull("completedAt")) null else obj.optLong("completedAt").takeIf { it > 0 },
                )
            }.takeLast(MAX_ITEMS)
        }.getOrDefault(emptyList())
    }
}
