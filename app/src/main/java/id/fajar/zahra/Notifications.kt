package id.fajar.zahra
import android.app.*
import android.content.Context
import android.os.Build
object Notifications{const val CHANNEL_ID="zahra_reminders";fun createChannel(c:Context){if(Build.VERSION.SDK_INT>=26)c.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID,"Pengingat Zahra",NotificationManager.IMPORTANCE_DEFAULT).apply{description="Pengingat aktivitas dan misi Zahra"})}}
