package com.xnvalabs.smarteyex.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.SecureStorage
import com.xnvalabs.smarteyex.service.ReminderReceiver
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Durable local Schedule/Reminder store. Supports the schedule prototype's
 * date, duration, categories, reminder offsets, sound/volume, repeat mode,
 * voice reminder fields, and GETAR/SPEAK/DERING/SENYAP delivery modes.
 */
object ReminderRepository {
    private const val KEY_ENTRIES = "reminders.entries"
    private const val KEY_NEXT_ID = "reminders.next_id"
    private const val LEGACY_PREFS = "smarteyex_reminders"
    private const val FALLBACK_PREFS = "smarteyex_reminder_state"
    private const val MAX_TITLE = 200
    private const val MAX_LOCATION = 200
    private const val MAX_ENTRIES = 500
    private var initialized = false
    private lateinit var appContext: Context

    var reminders = mutableStateOf(emptyList<ReminderEntry>())
        private set
    var lastStatus = mutableStateOf<String?>(null)
        private set

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        SecureStorage.init(appContext)
        val legacy = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val fallback = appContext.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
        val secureEntries = SecureStorage.getString(KEY_ENTRIES)
        val fallbackEntries = fallback.getString(KEY_ENTRIES, null)
        val legacyEntries = legacy.getString("entries", null)
        // Every mutation commits both stores. Prefer the mirror so a failed
        // encrypted write cannot make an older schedule reappear after restart.
        val raw = fallbackEntries ?: secureEntries ?: legacyEntries
        val loaded = load(raw)
        reminders.value = loaded

        val secureNext = SecureStorage.getInt(KEY_NEXT_ID, 1).coerceAtLeast(1)
        val fallbackNext = fallback.getInt(KEY_NEXT_ID, 1).coerceAtLeast(1)
        val legacyNext = legacy.getInt("next_id", 1).coerceAtLeast(1)
        val nextId = maxOf(secureNext, fallbackNext, legacyNext, (loaded.maxOfOrNull { it.id } ?: 0) + 1)
        val migrationNeeded = secureEntries == null || SecureStorage.getString(KEY_NEXT_ID) == null
        val migrated = if (migrationNeeded) persistState(loaded, nextId) else true
        if (migrated) legacy.edit().clear().apply()
        initialized = true
        refreshScheduling()
    }

    /** Legacy convenience used by voice reminders: daily at the selected time. */
    fun add(title: String, hour: Int, minute: Int): Boolean = addSchedule(
        title = title,
        dateMillis = null,
        startHour = hour,
        startMinute = minute,
        endHour = hour,
        endMinute = minute,
        location = "",
        category = "REMINDER",
        reminderMinutes = null,
        soundEnabled = true,
        volume = 65,
        repeat = "Daily",
        voiceName = true,
        voiceTime = true,
        voiceLocation = false,
        delivery = "GETAR",
    )

    fun addSchedule(
        title: String,
        dateMillis: Long?,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        location: String,
        category: String,
        reminderMinutes: Int?,
        soundEnabled: Boolean,
        volume: Int,
        repeat: String,
        voiceName: Boolean,
        voiceTime: Boolean,
        voiceLocation: Boolean,
        delivery: String,
    ): Boolean = saveSchedule(
        null, title, dateMillis, startHour, startMinute, endHour, endMinute, location, category, reminderMinutes,
        soundEnabled, volume, repeat, voiceName, voiceTime, voiceLocation, delivery,
    )

    /** Edit an existing schedule in place (same id). The old data and alarm are restored if the new one cannot be armed. */
    fun updateSchedule(
        id: Int,
        title: String,
        dateMillis: Long?,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        location: String,
        category: String,
        reminderMinutes: Int?,
        soundEnabled: Boolean,
        volume: Int,
        repeat: String,
        voiceName: Boolean,
        voiceTime: Boolean,
        voiceLocation: Boolean,
        delivery: String,
    ): Boolean = saveSchedule(
        id, title, dateMillis, startHour, startMinute, endHour, endMinute, location, category, reminderMinutes,
        soundEnabled, volume, repeat, voiceName, voiceTime, voiceLocation, delivery,
    )

    private fun saveSchedule(
        existingId: Int?,
        title: String,
        dateMillis: Long?,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        location: String,
        category: String,
        reminderMinutes: Int?,
        soundEnabled: Boolean,
        volume: Int,
        repeat: String,
        voiceName: Boolean,
        voiceTime: Boolean,
        voiceLocation: Boolean,
        delivery: String,
    ): Boolean = runCatching {
        if (!initialized) {
            lastStatus.value = "Schedule belum selesai diinisialisasi."
            return@runCatching false
        }
        val cleanTitle = title.trim().take(MAX_TITLE)
        val cleanLocation = location.trim().take(MAX_LOCATION)
        val cleanCategory = category.uppercase().takeIf { it in ALLOWED_CATEGORIES } ?: "OTHER"
        val cleanRepeat = repeat.takeIf { it in ALLOWED_REPEATS } ?: "Never"
        val cleanDelivery = delivery.uppercase().takeIf { it in ALLOWED_DELIVERIES } ?: "GETAR"
        val cleanReminder = reminderMinutes?.coerceIn(0, 24 * 60)
        if (cleanTitle.isBlank()) {
            lastStatus.value = "Nama kegiatan belum diisi."
            return@runCatching false
        }
        if (startHour !in 0..23 || startMinute !in 0..59 || endHour !in 0..23 || endMinute !in 0..59) {
            lastStatus.value = "Waktu schedule tidak valid."
            return@runCatching false
        }
        if (endHour * 60 + endMinute < startHour * 60 + startMinute) {
            lastStatus.value = "Waktu selesai tidak boleh sebelum waktu mulai."
            return@runCatching false
        }
        if (cleanRepeat == "Never" && dateMillis == null) {
            lastStatus.value = "Tanggal schedule diperlukan untuk repeat Never."
            return@runCatching false
        }
        val old = existingId?.let { eid -> reminders.value.firstOrNull { it.id == eid } }
        if (existingId != null && old == null) {
            lastStatus.value = "Schedule yang diubah sudah tidak ada."
            return@runCatching false
        }
        val date = dateMillis?.let { normalizeDate(it) }
        val id = existingId ?: nextId()
        val entry = ReminderEntry(
            id = id,
            title = cleanTitle,
            hour = startHour,
            minute = startMinute,
            dateMillis = date?.timeInMillis,
            endHour = endHour,
            endMinute = endMinute,
            location = cleanLocation,
            category = cleanCategory,
            reminderMinutes = cleanReminder,
            soundEnabled = soundEnabled,
            volume = volume.coerceIn(0, 100),
            repeat = cleanRepeat,
            voiceName = voiceName,
            voiceTime = voiceTime,
            voiceLocation = voiceLocation,
            delivery = cleanDelivery,
        )
        val previous = reminders.value
        val next = if (old != null) previous.map { if (it.id == id) entry else it } else (previous + entry).takeLast(MAX_ENTRIES)
        val nextIdValue = if (old != null) currentNextId() else id + 1
        // Persist before arming the alarm so a receiver can always find the
        // entry when the trigger fires. If arming fails, roll the data back.
        if (!persistState(next, nextIdValue)) {
            lastStatus.value = "Schedule belum tersimpan. Penyimpanan Android gagal."
            return@runCatching false
        }
        val scheduled = schedule(entry)
        if (!scheduled) {
            if (old != null) {
                persistState(previous, nextIdValue)
                schedule(old)
            } else {
                runCatching { cancelAlarm(id) }
                persistState(previous, id + 1)
            }
            lastStatus.value = "Schedule belum disimpan: alarm tidak dapat dijadwalkan. Cek tanggal/waktu dan izin Alarm."
            return@runCatching false
        }
        lastStatus.value = if (old != null) "Schedule diperbarui dan alarm aktif." else "Schedule tersimpan dan alarm aktif."
        true
    }.getOrElse {
        AppDiagnostics.warn("Schedule add failed", it)
        lastStatus.value = "Schedule gagal diproses."
        false
    }

    fun remove(id: Int): Boolean {
        if (!initialized) return false
        val existing = reminders.value.firstOrNull { it.id == id } ?: return false
        val fallbackNext = appContext.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_NEXT_ID, 1)
        val secureNext = SecureStorage.getInt(KEY_NEXT_ID, 1)
        val nextId = maxOf(secureNext, fallbackNext, existing.id + 1)
        if (!persistState(reminders.value.filterNot { it.id == id }, nextId)) {
            lastStatus.value = "Schedule belum berhasil dihapus."
            return false
        }
        runCatching { cancelAlarm(id) }.onFailure { AppDiagnostics.warn("Schedule cancel failed id=$id", it) }
        lastStatus.value = "Schedule dihapus."
        return true
    }

    fun clearAll(): Boolean {
        if (!initialized) return false
        val previous = reminders.value
        if (!persistState(emptyList(), 1)) {
            lastStatus.value = "Schedule belum berhasil dihapus."
            return false
        }
        previous.forEach { runCatching { cancelAlarm(it.id) } }
        // Keep the empty fallback mirror. It is the recovery source when the
        // encrypted mirror is temporarily unavailable, so clearing it here
        // could resurrect schedules after the next process restart.
        lastStatus.value = "Semua schedule dihapus."
        return true
    }

    fun find(id: Int): ReminderEntry? = reminders.value.firstOrNull { it.id == id }

    fun rescheduleNextDay(id: Int): Boolean = rescheduleNext(id)

    fun rescheduleNext(id: Int): Boolean {
        val entry = find(id) ?: return false
        if (entry.repeat == "Never") {
            remove(id)
            return true
        }
        val scheduled = schedule(entry, forceNext = true)
        if (!scheduled) AppDiagnostics.warn("Schedule reschedule failed id=$id")
        return scheduled
    }

    /** Re-arms every persisted alarm after process start, resume, boot, or permission changes. */
    fun refreshScheduling() {
        if (!initialized) return
        var failures = 0
        reminders.value.forEach {
            if (nextOccurrence(it) == null || !schedule(it)) failures++
        }
        lastStatus.value = when {
            reminders.value.isEmpty() -> null
            failures == 0 -> "${reminders.value.size} schedule aktif."
            failures < reminders.value.size -> "${reminders.value.size - failures}/${reminders.value.size} schedule aktif; beberapa alarm perlu izin."
            else -> "Schedule tersimpan, tetapi alarm belum aktif."
        }
    }

    fun rescheduleAll() = refreshScheduling()

    private fun currentNextId(): Int {
        val fallbackNext = appContext.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_NEXT_ID, 1)
        return maxOf(SecureStorage.getInt(KEY_NEXT_ID, 1), fallbackNext, 1)
    }

    private fun nextId(): Int {
        val fallbackNext = appContext.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_NEXT_ID, 1)
        var candidate = maxOf(SecureStorage.getInt(KEY_NEXT_ID, 1), fallbackNext, 1)
        val used = reminders.value.asSequence().map { it.id }.toHashSet()
        while (candidate <= 0 || candidate in used) candidate++
        return candidate
    }

    private fun schedule(entry: ReminderEntry, forceNext: Boolean = false): Boolean = runCatching {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@runCatching false
        val occurrence = nextOccurrence(entry, forceNext) ?: return@runCatching false
        var triggerAt = occurrence.timeInMillis - (entry.reminderMinutes ?: 0).coerceIn(0, 24 * 60) * 60_000L
        val now = System.currentTimeMillis()
        if (triggerAt <= now) {
            // If the selected lead time has already passed, still notify at the event itself.
            triggerAt = occurrence.timeInMillis
        }
        if (triggerAt <= now && entry.repeat == "Never") return@runCatching false
        arm(alarmManager, triggerAt, pendingIntentFor(entry))
        true
    }.onFailure { AppDiagnostics.warn("Schedule alarm failed id=${entry.id}", it) }.getOrDefault(false)

    private fun arm(alarmManager: AlarmManager, triggerAt: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun cancelAlarm(id: Int) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(pendingIntentFor(ReminderEntry(id, "", 0, 0)))
        alarmManager.cancel(snoozePendingIntent(id, Intent(appContext, ReminderReceiver::class.java)))
    }

    private fun snoozePendingIntent(id: Int, base: Intent): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        SNOOZE_REQUEST_BASE + id,
        base.apply { action = ReminderReceiver.ACTION_SNOOZE_FIRE },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * One-shot "tunda" alarm. It carries the alert content itself, so it still fires after a
     * one-time schedule was already removed when it first rang, and it never touches the real schedule.
     */
    fun scheduleSnooze(id: Int, alert: Bundle, minutes: Int = SNOOZE_MINUTES): Boolean = runCatching {
        if (!initialized) return@runCatching false
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@runCatching false
        val intent = Intent(appContext, ReminderReceiver::class.java).apply { putExtras(alert) }
        val triggerAt = System.currentTimeMillis() + minutes.coerceIn(1, 120) * 60_000L
        arm(alarmManager, triggerAt, snoozePendingIntent(id, intent))
        true
    }.onFailure { AppDiagnostics.warn("Snooze alarm failed id=$id", it) }.getOrDefault(false)

    private fun pendingIntentFor(entry: ReminderEntry): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        entry.id,
        Intent(appContext, ReminderReceiver::class.java).apply { putExtra(ReminderReceiver.EXTRA_ID, entry.id) },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun nextOccurrence(entry: ReminderEntry, forceNext: Boolean = false): Calendar? {
        val now = Calendar.getInstance()
        return if (entry.dateMillis == null) {
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, entry.hour)
                set(Calendar.MINUTE, entry.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (forceNext || !target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1)
            target
        } else {
            val base = Calendar.getInstance().apply {
                timeInMillis = normalizeDate(entry.dateMillis).timeInMillis
                set(Calendar.HOUR_OF_DAY, entry.hour)
                set(Calendar.MINUTE, entry.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            when (entry.repeat) {
                "Never" -> if (base.after(now) && !forceNext) base else null
                "Daily" -> advanceDaily(base, now, forceNext)
                "Weekly" -> advanceWeekly(base, now, forceNext)
                "Monthly" -> advanceMonthly(base, now, forceNext)
                else -> advanceDaily(base, now, forceNext)
            }
        }
    }

    private fun advanceDaily(base: Calendar, now: Calendar, forceNext: Boolean): Calendar {
        val target = base.clone() as Calendar
        if (forceNext || !target.after(now)) {
            val days = maxOf(1, ((now.timeInMillis - target.timeInMillis) / DAY_MS).toInt() + 1)
            target.add(Calendar.DAY_OF_YEAR, days)
        }
        return target
    }

    private fun advanceWeekly(base: Calendar, now: Calendar, forceNext: Boolean): Calendar {
        val target = base.clone() as Calendar
        if (forceNext || !target.after(now)) {
            while (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 7)
        }
        return target
    }

    private fun advanceMonthly(base: Calendar, now: Calendar, forceNext: Boolean): Calendar {
        // Preserve the selected day while advancing from day 1 to avoid
        // Calendar overflow (for example, Jan 31 + one month becoming Mar 3).
        val desiredDay = base.get(Calendar.DAY_OF_MONTH)
        val target = base.clone() as Calendar
        if (forceNext || !target.after(now)) {
            target.set(Calendar.DAY_OF_MONTH, 1)
            target.add(Calendar.MONTH, 1)
            target.set(Calendar.DAY_OF_MONTH, desiredDay.coerceAtMost(target.getActualMaximum(Calendar.DAY_OF_MONTH)))
            while (!target.after(now)) {
                target.set(Calendar.DAY_OF_MONTH, 1)
                target.add(Calendar.MONTH, 1)
                target.set(Calendar.DAY_OF_MONTH, desiredDay.coerceAtMost(target.getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
        }
        return target
    }

    private fun normalizeDate(millis: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun persistState(next: List<ReminderEntry>, nextId: Int): Boolean {
        val serialized = JSONArray().apply {
            next.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    put("hour", r.hour)
                    put("minute", r.minute)
                    put("dateMillis", r.dateMillis ?: JSONObject.NULL)
                    put("endHour", r.endHour)
                    put("endMinute", r.endMinute)
                    put("location", r.location)
                    put("category", r.category)
                    put("reminderMinutes", r.reminderMinutes ?: JSONObject.NULL)
                    put("soundEnabled", r.soundEnabled)
                    put("volume", r.volume)
                    put("repeat", r.repeat)
                    put("voiceName", r.voiceName)
                    put("voiceTime", r.voiceTime)
                    put("voiceLocation", r.voiceLocation)
                    put("delivery", r.delivery)
                })
            }
        }.toString()
        val values = mapOf(KEY_ENTRIES to serialized, KEY_NEXT_ID to nextId.coerceAtLeast(1).toString())
        val fallback = appContext.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
        val normalizedNextId = nextId.coerceAtLeast(1)
        val fallbackSaved = runCatching {
            val editor = fallback.edit()
                .putString(KEY_ENTRIES, serialized)
                .putInt(KEY_NEXT_ID, normalizedNextId)
            val committed = editor.commit()
            committed &&
                fallback.getString(KEY_ENTRIES, null) == serialized &&
                fallback.getInt(KEY_NEXT_ID, -1) == normalizedNextId
        }.onFailure { AppDiagnostics.warn("Reminder fallback persistence failed", it) }.getOrDefault(false)

        val secureSaved = if (fallbackSaved) {
            runCatching { SecureStorage.putStringsSync(values) }
                .onFailure { AppDiagnostics.warn("Reminder encrypted mirror failed", it) }
                .getOrDefault(false)
        } else false

        if (fallbackSaved) {
            reminders.value = next
            if (!secureSaved) AppDiagnostics.warn("Reminder stored in local recovery store; encrypted mirror unavailable")
        }
        return fallbackSaved
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
                val title = obj.optString("title").trim()
                if (id <= 0 || title.isBlank() || hour !in 0..23 || minute !in 0..59) return@mapNotNull null
                val date = if (obj.has("dateMillis") && !obj.isNull("dateMillis")) obj.optLong("dateMillis", 0L).takeIf { it > 0 } else null
                val repeat = obj.optString("repeat", if (date == null) "Daily" else "Never").takeIf { it in ALLOWED_REPEATS } ?: "Daily"
                val reminder = if (obj.has("reminderMinutes") && !obj.isNull("reminderMinutes")) obj.optInt("reminderMinutes", 0).coerceIn(0, 24 * 60) else null
                ReminderEntry(
                    id = id,
                    title = title.take(MAX_TITLE),
                    hour = hour,
                    minute = minute,
                    dateMillis = date,
                    endHour = obj.optInt("endHour", hour).coerceIn(0, 23),
                    endMinute = obj.optInt("endMinute", minute).coerceIn(0, 59),
                    location = obj.optString("location").trim().take(MAX_LOCATION),
                    category = obj.optString("category", "REMINDER").uppercase().takeIf { it in ALLOWED_CATEGORIES } ?: "REMINDER",
                    reminderMinutes = reminder,
                    soundEnabled = obj.optBoolean("soundEnabled", true),
                    volume = obj.optInt("volume", 65).coerceIn(0, 100),
                    repeat = repeat,
                    voiceName = obj.optBoolean("voiceName", true),
                    voiceTime = obj.optBoolean("voiceTime", true),
                    voiceLocation = obj.optBoolean("voiceLocation", false),
                    delivery = obj.optString("delivery", "GETAR").uppercase().takeIf { it in ALLOWED_DELIVERIES } ?: "GETAR",
                )
            }.distinctBy { it.id }.takeLast(MAX_ENTRIES)
        }.onFailure { AppDiagnostics.warn("Reminder data parse failed", it) }.getOrDefault(emptyList())
    }

    const val SNOOZE_MINUTES = 10
    private const val SNOOZE_REQUEST_BASE = 2_000_000
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private val ALLOWED_CATEGORIES = setOf("PERSONAL", "SCHOOL", "PROJECT", "MEETING", "TASK", "REMINDER", "OTHER")
    private val ALLOWED_REPEATS = setOf("Never", "Daily", "Weekly", "Monthly")
    private val ALLOWED_DELIVERIES = setOf("GETAR", "SPEAK", "DERING", "SENYAP")
}
