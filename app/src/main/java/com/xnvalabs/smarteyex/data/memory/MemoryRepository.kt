package com.xnvalabs.smarteyex.data.memory

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.AppDiagnostics
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
    var lastWriteError = mutableStateOf<String?>(null)
        private set

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        val app = context.applicationContext
        val legacy = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        initialized = true

        if (!PrivacyRepository.settings.value.memoryEnabled) {
            // Memory consent is authoritative. Do not load old personal data into RAM while OFF.
            runCatching { SecureStorage.removeSync(KEY_ENTRIES) }
                .onFailure { AppDiagnostics.warn("Memory privacy cleanup failed", it) }
            legacy.edit().clear().apply()
            entries.value = emptyList()
            return
        }

        val secure = SecureStorage.getString(KEY_ENTRIES)
        val raw = secure ?: legacy.getString("entries", null)
        entries.value = parse(raw)
        val migrated = secure != null || raw == null || SecureStorage.putStringSync(KEY_ENTRIES, raw)
        if (migrated) legacy.edit().clear().apply()
    }

    fun addEntry(type: MemoryType, title: String, content: String): Boolean {
        if (!memoryAllowed()) return false
        val safeTitle = title.trim().take(MAX_FIELD_CHARS)
        val safeContent = content.trim().take(MAX_FIELD_CHARS)
        if (safeTitle.isBlank() || safeContent.isBlank()) return false
        val entry = MemoryEntry(UUID.randomUUID().toString(), type, safeTitle, safeContent, System.currentTimeMillis())
        return persist((entries.value + entry).takeLast(MAX_ENTRIES))
    }

    fun upsertProfileField(label: String, content: String): Boolean {
        if (!memoryAllowed()) return false
        val key = label.trim().take(120)
        if (key.isBlank()) return false
        val without = entries.value.filterNot { it.type == MemoryType.PROFILE && it.title == key }
        if (content.isBlank()) return persist(without)
        val existingId = entries.value.firstOrNull { it.type == MemoryType.PROFILE && it.title == key }?.id
        return persist(
            (without + MemoryEntry(
                existingId ?: UUID.randomUUID().toString(),
                MemoryType.PROFILE,
                key,
                content.trim().take(MAX_FIELD_CHARS),
                System.currentTimeMillis(),
            )).takeLast(MAX_ENTRIES),
        )
    }

    fun getProfileValue(label: String): String? =
        entries.value.firstOrNull { it.type == MemoryType.PROFILE && it.title == label }?.content

    fun deleteEntry(id: String): Boolean = if (memoryAllowed()) {
        persist(entries.value.filterNot { it.id == id })
    } else {
        false
    }

    /** Explicit privacy purge: allowed even after Memory consent was just switched OFF. */
    fun deleteAll(): Boolean = persist(emptyList(), synchronous = true, bypassConsent = true)

    private fun memoryAllowed(): Boolean {
        check(initialized) { "MemoryRepository.init(context) must be called first" }
        return PrivacyRepository.settings.value.memoryEnabled
    }

    private fun persist(
        next: List<MemoryEntry>,
        synchronous: Boolean = false,
        bypassConsent: Boolean = false,
    ): Boolean {
        if (!bypassConsent && !memoryAllowed()) return false
        val clean = next.takeLast(MAX_ENTRIES)
        val serialized = serialize(clean)
        val saved = runCatching {
            if (synchronous) SecureStorage.putStringSync(KEY_ENTRIES, serialized)
            else SecureStorage.putString(KEY_ENTRIES, serialized)
        }.onFailure { AppDiagnostics.warn("Memory persistence failed", it) }.getOrDefault(false)
        if (saved) {
            entries.value = clean
            lastWriteError.value = null
        } else {
            lastWriteError.value = "Memory belum berhasil disimpan."
        }
        return saved
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
                val title = obj.optString("title").trim().take(MAX_FIELD_CHARS)
                val content = obj.optString("content").trim().take(MAX_FIELD_CHARS)
                if (title.isBlank() || content.isBlank()) return@mapNotNull null
                MemoryEntry(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    type = type,
                    title = title,
                    content = content,
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                )
            }.takeLast(MAX_ENTRIES)
        }.onFailure { AppDiagnostics.warn("Memory data parse failed", it) }.getOrDefault(emptyList())
    }
}
