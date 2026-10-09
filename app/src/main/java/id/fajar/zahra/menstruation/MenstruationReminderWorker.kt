package id.fajar.zahra.menstruation

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import androidx.work.WorkerParameters
import id.fajar.zahra.MainActivity
import id.fajar.zahra.Notifications

/** One-shot notification that schedules the next cycle interval, never a daily repeat. */
class MenstruationReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33 && applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        Notifications.createChannel(applicationContext)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(applicationContext, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Pengingat kalender pribadi")
            .setContentText("Perkiraan siklus yang kamu catat mungkin sudah dekat. Periksa catatanmu jika perlu.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        // Schedule one reminder for the next estimated cycle; never repeat daily.
        val cycleLength = inputData.getInt("cycleLength", 0)
        if (cycleLength in 15..60) {
            val nextData = Data.Builder().putInt("cycleLength", cycleLength).build()
            val nextRequest = OneTimeWorkRequestBuilder<MenstruationReminderWorker>()
                .setInputData(nextData)
                .setInitialDelay(cycleLength.toLong(), TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "private-cycle-approaching-reminder", ExistingWorkPolicy.REPLACE, nextRequest
            )
        }
        return Result.success()
    }

    companion object { const val NOTIFICATION_ID = 41873 }
}
