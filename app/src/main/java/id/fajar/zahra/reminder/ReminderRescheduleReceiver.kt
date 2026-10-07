package id.fajar.zahra.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Rebuilds relative WorkManager delays after boot, clock, or timezone changes. */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> ReminderRescheduleWorker.enqueue(context)
        }
    }
}
