package com.xnvalabs.smarteyex.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.xnvalabs.smarteyex.BuildConfig
import com.xnvalabs.smarteyex.data.age.AgeFeature
import com.xnvalabs.smarteyex.data.age.AgeRepository
import com.xnvalabs.smarteyex.data.notifications.NotificationFilter
import com.xnvalabs.smarteyex.data.notifications.NotificationRepository
import com.xnvalabs.smarteyex.data.notifications.RecentDeduper
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository

private val KNOWN_APP_LABELS = mapOf(
    "com.whatsapp" to "WhatsApp",
    "org.telegram.messenger" to "Telegram",
    "com.instagram.android" to "Instagram",
    "com.google.android.gm" to "Gmail",
    "com.google.android.youtube" to "YouTube",
)

/** Real notification listener with explicit in-app privacy gating. */
class SmartEyeXNotificationListener : NotificationListenerService() {
    private val deduper = RecentDeduper()

    override fun onListenerConnected() {
        PrivacyRepository.init(applicationContext)
        NotificationRepository.bind(applicationContext)
        if (!PrivacyRepository.settings.value.notificationContentEnabled) return
        if (!AgeRepository.allows(AgeFeature.NOTIFICATION_CONTENT)) return
        runCatching {
            activeNotifications.orEmpty().forEach(::consume)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        PrivacyRepository.init(applicationContext)
        NotificationRepository.bind(applicationContext)
        consume(sbn)
    }

    override fun onDestroy() {
        NotificationRepository.releaseDelivery()
        super.onDestroy()
    }

    private fun consume(sbn: StatusBarNotification) {
        if (!PrivacyRepository.settings.value.notificationContentEnabled) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val message = listOf(title, text).filter { it.isNotBlank() }.joinToString(" — ")
        val readable = NotificationFilter.shouldRead(
            packageName = sbn.packageName,
            ownPackage = BuildConfig.APPLICATION_ID,
            isOngoing = sbn.isOngoing,
            isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            category = sbn.notification.category,
            message = message,
        )
        if (!readable) return
        if (deduper.isDuplicate(sbn.packageName + "|" + message, System.currentTimeMillis())) return

        val appLabel = KNOWN_APP_LABELS[sbn.packageName] ?: runCatching {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sbn.packageName)

        val replyAction = sbn.notification.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
        NotificationRepository.push(appLabel, message, replyAction)
        NotificationRepository.deliver(this, appLabel, message)
    }
}
