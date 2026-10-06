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
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.xnvalabs.smarteyex.MainActivity
import com.xnvalabs.smarteyex.R
import com.xnvalabs.smarteyex.data.notifications.NotificationRepository
import com.xnvalabs.smarteyex.data.reminder.ReminderEntry
import com.xnvalabs.smarteyex.data.reminder.ReminderRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Receives both legacy daily reminders and full Schedule alerts. */
class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_ID = "extra_id"
        private const val CHANNEL_ID = "smarteyex_reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        ReminderRepository.init(context)
        val entry = ReminderRepository.find(id) ?: return
        ensureChannel(context)

        val date = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(entry.dateMillis ?: System.currentTimeMillis()))
        val time = "%02d:%02d".format(entry.hour, entry.minute)
        val voiceParts = buildList {
            if (entry.voiceName) add(entry.title)
            if (entry.voiceTime) add("jam $time")
            if (entry.voiceLocation && entry.location.isNotBlank()) add("di ${entry.location}")
        }
        val message = if (voiceParts.isNotEmpty()) voiceParts.joinToString(", ") else entry.title

        val canPostNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (canPostNotifications) {
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("SmartEyeX · ${entry.category}")
                .setContentText(entry.title + if (entry.location.isNotBlank()) " · ${entry.location}" else "")
                .setSubText("$date · $time")
                .setContentIntent(openApp)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(entry.id, notification) }
        }

        val deliveryMode = when (entry.delivery) {
            "SPEAK" -> NotificationRepository.MODE_SPEAK
            "DERING" -> NotificationRepository.MODE_RING
            "SENYAP" -> NotificationRepository.MODE_SILENT
            else -> NotificationRepository.MODE_GETAR
        }
        if (entry.soundEnabled || deliveryMode == NotificationRepository.MODE_GETAR) {
            // SPEAK/DERING finish asynchronously (TTS init, tone playback); goAsync() keeps
            // the process alive until they are done instead of being killed on return.
            val pending = if (deliveryMode == NotificationRepository.MODE_SPEAK || deliveryMode == NotificationRepository.MODE_RING) goAsync() else null
            NotificationRepository.deliverMode(context, message, deliveryMode, entry.volume)
            pending?.let { result -> Handler(Looper.getMainLooper()).postDelayed({ result.finish() }, 8_000L) }
        }

        if (entry.repeat == "Never") ReminderRepository.remove(id)
        else ReminderRepository.rescheduleNext(id)
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
