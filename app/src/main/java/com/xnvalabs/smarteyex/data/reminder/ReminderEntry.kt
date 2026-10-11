package com.xnvalabs.smarteyex.data.reminder

/**
 * A persisted Schedule/Reminder. The legacy constructor fields [id], [title],
 * [hour], and [minute] remain first so existing reminder data migrates safely.
 */
data class ReminderEntry(
    val id: Int,
    val title: String,
    val hour: Int,
    val minute: Int,
    val dateMillis: Long? = null,
    val endHour: Int = hour,
    val endMinute: Int = minute,
    val location: String = "",
    val category: String = "REMINDER",
    val reminderMinutes: Int? = null,
    val soundEnabled: Boolean = true,
    val volume: Int = 65,
    val repeat: String = "Daily",
    val voiceName: Boolean = true,
    val voiceTime: Boolean = true,
    val voiceLocation: Boolean = false,
    val delivery: String = "GETAR",
)
