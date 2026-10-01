package com.xnvalabs.smarteyex.data.memory

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.SecureStorage
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Local encrypted memory store with privacy enforcement and bounded records. */
object MemoryRepository {
    private const val KEY_ENTRIES = "memory.entries"
    private const val LEGACY_PREFS = "smarteyex_memory"
    private const val MAX_ENTRIES = 500
    private const val MAX_FIELD_CHARS = 4000
    private var initialized = false

    var entries = mutableStateOf(emptyList<MemoryEntry>())
        private set

    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        val secure = SecureStorage.getString(KEY_ENTRIES)
        val legacy = context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val raw = secure ?: legacy.getString("entries", null)
        entries.value = parse(raw)
        val migrated = secure != null || raw == null || SecureStorage.putStringSync(KEY_ENTRIES, raw)
        if (migrated) legacy.edit().clear().apply()
        initialized = true
    }

    fun addEntry(type: MemoryType, title: String, content: String) {
        if (!memoryAllowed()) return
        val safeTitle = title.trim().take(MAX_FIELD_CHARS)
        val safeContent = content.trim().take(MAX_FIELD_CHARS)
        if (safeTitle.isBlank() || safeContent.isBlank()) return
        val entry = MemoryEntry(UUID.randomUUID().toString(), type, safeTitle, safeContent, System.currentTimeMillis())
        persist((entries.value + entry).takeLast(MAX_ENTRIES))
    }

    fun upsertProfileField(label: String, content: String) {
        if (!memoryAllowed()) return
        val key = label.trim().take(120)
        if (key.isBlank()) return
        val without = entries.value.filterNot { it.type == MemoryType.PROFILE && it.title == key }
        if (content.isBlank()) return persist(without)
        val existingId = entries.value.firstOrNull { it.type == MemoryType.PROFILE && it.title == key }?.id
        persist((without + MemoryEntry(existingId ?: UUID.randomUUID().toString(), MemoryType.PROFILE, key, content.trim().take(MAX_FIELD_CHARS), System.currentTimeMillis())).takeLast(MAX_ENTRIES))
    }

    fun getProfileValue(label: String): String? = entries.value.firstOrNull { it.type == MemoryType.PROFILE && it.title == label }?.content

    fun deleteEntry(id: String) = persist(entries.value.filterNot { it.id == id })
    fun deleteByType(type: MemoryType) = persist(entries.value.filterNot { it.type == type })
    fun deleteAll() = persist(emptyList(), synchronous = true)

    private fun memoryAllowed(): Boolean {
        check(initialized) { "MemoryRepository.init(context) must be called first" }
        return PrivacyRepository.settings.value.memoryEnabled
    }

    private fun persist(next: List<MemoryEntry>, synchronous: Boolean = false) {
        entries.value = next
        val serialized = serialize(next.takeLast(MAX_ENTRIES))
        if (synchronous) SecureStorage.putStringSync(KEY_ENTRIES, serialized) else SecureStorage.putString(KEY_ENTRIES, serialized)
    }

    private fun serialize(next: List<MemoryEntry>): String = JSONArray().apply {
        next.forEach { entry ->
            put(JSONObject().apply {
                put("id", entry.id)
                put("type", entry.type.name)
                put("title", entry.title)
                put("content", entry.content)
                put("timestamp", entry.timestamp)
            })
        }
    }.toString()

    private fun parse(raw: String?): List<MemoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val type = runCatching { MemoryType.valueOf(obj.optString("type")) }.getOrNull() ?: return@mapNotNull null
                MemoryEntry(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    type = type,
                    title = obj.optString("title").take(MAX_FIELD_CHARS),
                    content = obj.optString("content").take(MAX_FIELD_CHARS),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                )
            }.takeLast(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }
}
