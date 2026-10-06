package com.xnvalabs.smarteyex.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.xnvalabs.smarteyex.BuildConfig
import com.xnvalabs.smarteyex.data.notifications.NotificationRepository
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository

private val PENDING_COUNT_SUFFIX = Regex("\\s*\\(\\d+[^)]*\\)\\s*$")

private val KNOWN_APP_LABELS = mapOf(
    "com.whatsapp" to "WhatsApp",
    "org.telegram.messenger" to "Telegram",
    "com.instagram.android" to "Instagram",
    "com.google.android.gm" to "Gmail",
    "com.google.android.youtube" to "YouTube",
)

/** Real notification listener with explicit in-app privacy gating. */
class SmartEyeXNotificationListener : NotificationListenerService() {
    private val lastTextByKey = HashMap<String, String>()

    override fun onListenerConnected() {
        PrivacyRepository.init(applicationContext)
        NotificationRepository.bind(applicationContext)
        if (!PrivacyRepository.settings.value.notificationContentEnabled) return
        runCatching {
            activeNotifications.orEmpty().forEach { consume(it, announce = false) }
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

    /** True for a new, user-visible message (not a group summary, ongoing item, or an unchanged repost). */
    private fun isFreshMessage(sbn: StatusBarNotification, text: String): Boolean {
        val flags = sbn.notification.flags
        if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) return false
        if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) return false
        if (text.isBlank()) return false
        if (lastTextByKey[sbn.key] == text) return false
        if (lastTextByKey.size > 64) lastTextByKey.clear()
        lastTextByKey[sbn.key] = text
        return true
    }

    private fun consume(sbn: StatusBarNotification, announce: Boolean = true) {
        if (!PrivacyRepository.settings.value.notificationContentEnabled) return
        if (sbn.packageName == BuildConfig.APPLICATION_ID) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val message = listOf(title, text).filter { it.isNotBlank() }.joinToString(" — ")
        if (message.isBlank()) return

        val appLabel = KNOWN_APP_LABELS[sbn.packageName] ?: runCatching {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sbn.packageName)

        val replyAction = sbn.notification.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
        val fresh = isFreshMessage(sbn, text)
        val sender = title.replace(PENDING_COUNT_SUFFIX, "").trim()
        NotificationRepository.push(appLabel, message, replyAction, sender, text, announce && fresh)
        NotificationRepository.deliver(this, appLabel, message)
    }
}
