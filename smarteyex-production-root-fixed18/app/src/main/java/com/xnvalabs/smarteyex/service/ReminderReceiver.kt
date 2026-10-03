package com.xnvalabs.smarteyex.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.xnvalabs.smarteyex.MainActivity
import com.xnvalabs.smarteyex.R
import com.xnvalabs.smarteyex.data.notifications.NotificationRepository
import com.xnvalabs.smarteyex.data.reminder.ReminderRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Receives scheduled reminders, the "Tunda" button on a reminder notification, and the one-shot
 * snooze alarm it arms. A snoozed alert carries its own content, so it works even after a
 * one-time schedule was already removed when it first rang.
 */
class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_ID = "extra_id"
        const val ACTION_SNOOZE = "com.xnvalabs.smarteyex.action.SNOOZE"
        const val ACTION_SNOOZE_FIRE = "com.xnvalabs.smarteyex.action.SNOOZE_FIRE"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_SUB = "extra_sub"
        const val EXTRA_SPOKEN = "extra_spoken"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_VOLUME = "extra_volume"
        const val EXTRA_SOUND = "extra_sound"
        private const val CHANNEL_ID = "smarteyex_reminders"
        private const val SNOOZE_ACTION_REQUEST_BASE = 3_000_000
    }

    /** Everything needed to show and speak one alert, independent of the stored schedule. */
    private class Alert(
        val id: Int,
        val title: String,
        val category: String,
        val text: String,
        val sub: String,
        val spoken: String,
        val mode: Int,
        val volume: Int,
        val sound: Boolean,
    ) {
        fun toBundle(): Bundle = Bundle().apply {
            putInt(EXTRA_ID, id)
            putString(EXTRA_TITLE, title)
            putString(EXTRA_CATEGORY, category)
            putString(EXTRA_TEXT, text)
            putString(EXTRA_SUB, sub)
            putString(EXTRA_SPOKEN, spoken)
            putInt(EXTRA_MODE, mode)
            putInt(EXTRA_VOLUME, volume)
            putBoolean(EXTRA_SOUND, sound)
        }

        companion object {
            fun from(intent: Intent): Alert? {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: return null
                return Alert(
                    id = intent.getIntExtra(EXTRA_ID, 0),
                    title = title,
                    category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty(),
                    text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
                    sub = intent.getStringExtra(EXTRA_SUB).orEmpty(),
                    spoken = intent.getStringExtra(EXTRA_SPOKEN) ?: title,
                    mode = intent.getIntExtra(EXTRA_MODE, NotificationRepository.MODE_GETAR),
                    volume = intent.getIntExtra(EXTRA_VOLUME, 65),
                    sound = intent.getBooleanExtra(EXTRA_SOUND, true),
                )
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SNOOZE -> snoozeFromNotification(context, intent)
            ACTION_SNOOZE_FIRE -> Alert.from(intent)?.let { present(context, it) }
            else -> fireScheduled(context, intent)
        }
    }

    private fun snoozeFromNotification(context: Context, intent: Intent) {
        val alert = Alert.from(intent) ?: return
        ReminderRepository.init(context)
        NotificationManagerCompat.from(context).cancel(alert.id)
        ReminderRepository.scheduleSnooze(alert.id, alert.toBundle())
    }

    private fun fireScheduled(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        ReminderRepository.init(context)
        val entry = ReminderRepository.find(id) ?: return

        val date = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(entry.dateMillis ?: System.currentTimeMillis()))
        val time = "%02d:%02d".format(entry.hour, entry.minute)
        val voiceParts = buildList {
            if (entry.voiceName) add(entry.title)
            if (entry.voiceTime) add("jam $time")
            if (entry.voiceLocation && entry.location.isNotBlank()) add("di ${entry.location}")
        }
        val deliveryMode = when (entry.delivery) {
            "SPEAK" -> NotificationRepository.MODE_SPEAK
            "DERING" -> NotificationRepository.MODE_RING
            "SENYAP" -> NotificationRepository.MODE_SILENT
            else -> NotificationRepository.MODE_GETAR
        }
        present(
            context,
            Alert(
                id = entry.id,
                title = entry.title,
                category = entry.category,
                text = entry.title + if (entry.location.isNotBlank()) " · ${entry.location}" else "",
                sub = "$date · $time",
                spoken = if (voiceParts.isNotEmpty()) voiceParts.joinToString(", ") else entry.title,
                mode = deliveryMode,
                volume = entry.volume,
                sound = entry.soundEnabled,
            ),
        )

        if (entry.repeat == "Never") ReminderRepository.remove(id)
        else ReminderRepository.rescheduleNext(id)
    }

    private fun present(context: Context, alert: Alert) {
        ensureChannel(context)
        val canPostNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (canPostNotifications) {
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val snooze = PendingIntent.getBroadcast(
                context,
                SNOOZE_ACTION_REQUEST_BASE + alert.id,
                Intent(context, ReminderReceiver::class.java).apply {
                    action = ACTION_SNOOZE
                    putExtras(alert.toBundle())
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("SmartEyeX · ${alert.category}")
                .setContentText(alert.text)
                .setSubText(alert.sub)
                .setContentIntent(openApp)
                .addAction(R.drawable.ic_notification, "Tunda ${ReminderRepository.SNOOZE_MINUTES} mnt", snooze)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(alert.id, notification) }
        }

        if (alert.sound || alert.mode == NotificationRepository.MODE_GETAR) {
            // SPEAK/DERING finish asynchronously (TTS init, tone playback); goAsync() keeps
            // the process alive until they are done instead of being killed on return.
            val pending = if (alert.mode == NotificationRepository.MODE_SPEAK || alert.mode == NotificationRepository.MODE_RING) goAsync() else null
            NotificationRepository.deliverMode(context, alert.spoken, alert.mode, alert.volume)
            pending?.let { result -> Handler(Looper.getMainLooper()).postDelayed({ result.finish() }, 8_000L) }
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Reminders & Schedule", NotificationManager.IMPORTANCE_HIGH),
                )
            }
        }
    }
}
