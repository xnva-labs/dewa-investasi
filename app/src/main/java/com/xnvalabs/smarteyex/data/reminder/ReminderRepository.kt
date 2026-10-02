package com.xnvalabs.smarteyex.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.SecureStorage
import com.xnvalabs.smarteyex.service.ReminderReceiver
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Daily local reminders backed by encrypted storage and resilient alarms. */
object ReminderRepository {
    private const val KEY_ENTRIES = "reminders.entries"
    private const val KEY_NEXT_ID = "reminders.next_id"
    private const val LEGACY_PREFS = "smarteyex_reminders"
    private var initialized = false
    private lateinit var appContext: Context

    var reminders = mutableStateOf(emptyList<ReminderEntry>())
        private set

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        SecureStorage.init(appContext)
        val legacy = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val secureEntries = SecureStorage.getString(KEY_ENTRIES)
        val legacyEntries = legacy.getString("entries", null)
        val raw = secureEntries ?: legacyEntries
        reminders.value = load(raw)

        val entriesReady = secureEntries != null || legacyEntries == null || SecureStorage.putStringSync(KEY_ENTRIES, legacyEntries)
        val nextIdReady = SecureStorage.getString(KEY_NEXT_ID) != null ||
            !legacy.contains("next_id") ||
            SecureStorage.putStringSync(KEY_NEXT_ID, legacy.getInt("next_id", 1).coerceAtLeast(1).toString())

        if (entriesReady && nextIdReady) legacy.edit().clear().apply()
        initialized = true
        rescheduleAll()
    }

    fun add(title: String, hour: Int, minute: Int): Boolean = runCatching {
        if (!initialized || title.isBlank() || hour !in 0..23 || minute !in 0..59) return@runCatching false
        val id = nextId()
        val entry = ReminderEntry(id, title.trim().take(200), hour, minute)
        val previous = reminders.value
        if (!persist(previous + entry, synchronous = true)) return@runCatching false
        if (!schedule(entry)) {
            persist(previous, synchronous = true)
            return@runCatching false
        }
        if (!SecureStorage.putStringSync(KEY_NEXT_ID, (id + 1).toString())) {
            cancelAlarm(id)
            persist(previous, synchronous = true)
            return@runCatching false
        }
        true
    }.getOrDefault(false)

    fun clearAll() {
        reminders.value.forEach { cancelAlarm(it.id) }
        persist(emptyList(), synchronous = true)
    }

    fun remove(id: Int) {
        cancelAlarm(id)
        persist(reminders.value.filterNot { it.id == id })
    }

    fun rescheduleNextDay(id: Int) {
        reminders.value.firstOrNull { it.id == id }?.let { schedule(it) }
    }

    fun rescheduleAll() {
        reminders.value.forEach { schedule(it) }
    }

    private fun nextId(): Int {
        var candidate = SecureStorage.getInt(KEY_NEXT_ID, 1).coerceAtLeast(1)
        val used = reminders.value.asSequence().map { it.id }.toHashSet()
        while (candidate <= 0 || candidate in used) candidate++
        return candidate
    }

    private fun schedule(entry: ReminderEntry): Boolean {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTriggerMillis(entry.hour, entry.minute)
        return runCatching {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntentFor(entry))
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntentFor(entry))
            }
            true
        }.getOrElse { false }
    }

    private fun cancelAlarm(id: Int) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntentFor(ReminderEntry(id, "", 0, 0)))
    }

    private fun pendingIntentFor(entry: ReminderEntry): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        entry.id,
        Intent(appContext, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE, entry.title)
            putExtra(ReminderReceiver.EXTRA_ID, entry.id)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }

    private fun persist(next: List<ReminderEntry>, synchronous: Boolean = false): Boolean {
        val serialized = JSONArray().apply {
            next.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    put("hour", r.hour)
                    put("minute", r.minute)
                })
            }
        }.toString()
        val saved = runCatching {
            if (synchronous) SecureStorage.putStringSync(KEY_ENTRIES, serialized)
            else SecureStorage.putString(KEY_ENTRIES, serialized)
        }.getOrDefault(false)
        if (saved) reminders.value = next
        return saved
    }

    private fun load(raw: String?): List<ReminderEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optInt("id", -1)
                val hour = obj.optInt("hour", -1)
                val minute = obj.optInt("minute", -1)
                if (id <= 0 || hour !in 0..23 || minute !in 0..59) return@mapNotNull null
                ReminderEntry(id, obj.optString("title").trim().take(200), hour, minute)
            }
        }.getOrDefault(emptyList())
    }
}
