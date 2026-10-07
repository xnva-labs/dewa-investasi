package id.fajar.zahra.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import id.fajar.zahra.MainActivity
import id.fajar.zahra.Notifications
import id.fajar.zahra.core.RepeatRules
import id.fajar.zahra.data.ZahraDatabase
import id.fajar.zahra.settings.SettingsStore
import kotlinx.coroutines.flow.first

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Notifications.createChannel(applicationContext)
        val settings = SettingsStore(applicationContext)
        if (!settings.notifications.first()) return Result.success()
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }

        val missionId = inputData.getLong("missionId", -1L)
        val scheduledAt = inputData.getLong("scheduledAt", -1L)
        if (missionId <= 0L || scheduledAt <= 0L) return Result.success()
        val now = System.currentTimeMillis()
        if (now + 30_000L < scheduledAt) {
            // WorkManager can wake slightly early after a clock adjustment. Keep the event anchored to the requested instant.
            return Result.retry()
        }

        val db = ZahraDatabase.get(applicationContext)
        val mission = db.missionDao().findById(missionId) ?: return Result.success()
        if (mission.status != "ACTIVE") return Result.success()
        if (mission.scheduledAt != scheduledAt) return Result.success()

        val title = inputData.getString("title") ?: mission.title
        val body = inputData.getString("body") ?: "Ada aktivitas yang perlu diperhatikan."
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext,
            (missionId xor (missionId ushr 32)).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        val notificationId = (missionId xor (missionId ushr 32)).toInt()
        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)

        if (mission.repeatRule != null && mission.scheduledAt != null) {
            var next = RepeatRules.next(mission.scheduledAt, mission.repeatRule)
            while (next != null && next <= System.currentTimeMillis()) {
                next = RepeatRules.next(next, mission.repeatRule)
            }
            if (next != null) {
                ReminderScheduler.scheduleAt(applicationContext, mission.id, mission.title, "Pengingat misi berulang: ${mission.title}", next)
            }
        }
        return Result.success()
    }
}
