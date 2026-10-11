package com.xnvalabs.smarteyex.data.enterprise

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.SecureStorage
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TaskStatus(val label: String) {
    TODO("Todo"),
    DOING("Dikerjakan"),
    DONE("Selesai");

    fun next(): TaskStatus = entries[(ordinal + 1) % entries.size]
}

data class WorkTask(
    val id: String,
    val title: String,
    val assignee: String,
    val status: TaskStatus,
)

object EnterpriseRepository {
    private const val KEY_TASKS = "enterprise.tasks"
    private const val LEGACY_PREFS = "smarteyex_enterprise"
    private const val MAX_TASKS = 500
    private var initialized = false

    var tasks = mutableStateOf(emptyList<WorkTask>())
        private set

    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        val legacy = context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val secure = SecureStorage.getString(KEY_TASKS)
        val raw = secure ?: legacy.getString("tasks", null)
        tasks.value = load(raw)
        val ready = secure != null || raw == null || SecureStorage.putStringSync(KEY_TASKS, raw)
        if (ready) legacy.edit().clear().apply()
        initialized = true
    }

    fun add(title: String, assignee: String): Boolean {
        val cleanTitle = title.trim().take(200)
        if (cleanTitle.isBlank()) return false
        val task = WorkTask(UUID.randomUUID().toString(), cleanTitle, assignee.trim().take(100), TaskStatus.TODO)
        return persist((tasks.value + task).takeLast(MAX_TASKS))
    }

    fun advance(id: String): Boolean = persist(tasks.value.map { if (it.id == id) it.copy(status = it.status.next()) else it })
    fun remove(id: String): Boolean = persist(tasks.value.filterNot { it.id == id })
    fun clearAll(): Boolean = persist(emptyList(), synchronous = true)

    private fun persist(next: List<WorkTask>, synchronous: Boolean = false): Boolean {
        val clean = next.takeLast(MAX_TASKS)
        val serialized = JSONArray().apply {
            clean.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("title", t.title); put("assignee", t.assignee); put("status", t.status.name)
                })
            }
        }.toString()
        val saved = runCatching {
            if (synchronous) SecureStorage.putStringSync(KEY_TASKS, serialized) else SecureStorage.putString(KEY_TASKS, serialized)
        }.onFailure { AppDiagnostics.warn("Enterprise persistence failed", it) }.getOrDefault(false)
        if (saved) tasks.value = clean
        return saved
    }

    private fun load(raw: String?): List<WorkTask> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val status = runCatching { TaskStatus.valueOf(obj.optString("status")) }.getOrNull() ?: TaskStatus.TODO
                val title = obj.optString("title").trim()
                if (title.isBlank()) null else WorkTask(
                    obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    title.take(200),
                    obj.optString("assignee").take(100),
                    status,
                )
            }.takeLast(MAX_TASKS)
        }.getOrDefault(emptyList())
    }
}
