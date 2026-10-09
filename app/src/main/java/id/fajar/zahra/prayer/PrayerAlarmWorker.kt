package id.fajar.zahra.prayer

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.fajar.zahra.MainActivity
import id.fajar.zahra.Notifications
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** WorkManager reminders are battery-aware and may be delivered a little after the target time. */
class PrayerAlarmWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Notifications.createChannel(applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= 33 && applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val title = inputData.getString("title") ?: "Waktu ibadah"
        val body = inputData.getString("body") ?: "Waktunya berhenti sejenak dan beribadah."
        val id = inputData.getInt("notificationId", title.hashCode())
        val intent = Intent(applicationContext, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pending = PendingIntent.getActivity(applicationContext, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).setContentIntent(pending).build()
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
        // Reschedule this named reminder for tomorrow using the same local wall-clock time.
        val hhmm = inputData.getString("time")
        if (!hhmm.isNullOrBlank()) {
            val parts = hhmm.split(":")
            if (parts.size == 2) {
                val next = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta")).apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, parts[0].toInt()); set(Calendar.MINUTE, parts[1].toInt()); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                schedule(applicationContext, id, title, body, hhmm, next)
            }
        }
        return Result.success()
    }
    companion object {
        fun cancel(context: Context, id: Int) {
            WorkManager.getInstance(context).cancelUniqueWork("prayer-alarm-$id")
        }
        fun schedule(context: Context, id: Int, title: String, body: String, time: String, epoch: Long) {
            val delay = (epoch - System.currentTimeMillis()).coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
            val data = androidx.work.Data.Builder().putInt("notificationId", id).putString("title", title).putString("body", body).putString("time", time).build()
            val request = OneTimeWorkRequestBuilder<PrayerAlarmWorker>().setInputData(data).setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("prayer-alarm-$id", androidx.work.ExistingWorkPolicy.REPLACE, request)
        }
    }
}
