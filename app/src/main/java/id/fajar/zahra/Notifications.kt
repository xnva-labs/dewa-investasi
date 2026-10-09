package id.fajar.zahra

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build

object Notifications {
    // A new channel ID lets existing installs pick up the new, softer reminder sound.
    const val CHANNEL_ID = "zahra_reminders_calm_v2"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.soft_chime}")
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pengingat lembut Zahra",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Pengingat aktivitas dengan nada lembut dan getaran ringan."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 100, 60, 100)
                setSound(soundUri, audioAttributes)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
