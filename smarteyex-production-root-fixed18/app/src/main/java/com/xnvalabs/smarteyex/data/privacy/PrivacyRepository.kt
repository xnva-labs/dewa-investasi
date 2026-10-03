package com.xnvalabs.smarteyex.data.privacy

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.DeviceIdentity
import com.xnvalabs.smarteyex.core.SecureStorage
import com.xnvalabs.smarteyex.data.call.CallRepository
import com.xnvalabs.smarteyex.data.companion.CompanionRepository
import com.xnvalabs.smarteyex.data.education.EducationRepository
import com.xnvalabs.smarteyex.data.emergency.EmergencyRepository
import com.xnvalabs.smarteyex.data.enterprise.EnterpriseRepository
import com.xnvalabs.smarteyex.data.face.FaceRepository
import com.xnvalabs.smarteyex.data.glasses.GlassesRepository
import com.xnvalabs.smarteyex.data.memory.MemoryRepository
import com.xnvalabs.smarteyex.data.notifications.NotificationRepository
import com.xnvalabs.smarteyex.data.reminder.ReminderRepository
import com.xnvalabs.smarteyex.data.voice.VoiceProfileRepository

/** Single source of truth for sensitive-feature consent. */
object PrivacyRepository {
    private const val KEY_CAMERA = "privacy.camera"
    private const val KEY_MIC = "privacy.microphone"
    private const val KEY_MEMORY = "privacy.memory"
    private const val KEY_CLOUD = "privacy.cloud"
    private const val KEY_FACE = "privacy.face"
    private const val KEY_NOTIFICATIONS = "privacy.notifications"
    private const val KEY_VOICE = "privacy.voice_personalization"

    private const val LEGACY_PREFS = "smarteyex_privacy"
    private const val FALLBACK_PREFS = "smarteyex_privacy_state"
    private var initialized = false
    private var fallbackPrefs: android.content.SharedPreferences? = null

    var settings = mutableStateOf(PrivacySettings.DEFAULT)
        private set
    var lastWriteError = mutableStateOf<String?>(null)
        private set

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        val app = context.applicationContext
        val legacy = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val fallback = app.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
        fallbackPrefs = fallback
        settings.value = PrivacySettings(
            cameraEnabled = readBoolean(KEY_CAMERA, fallback, legacy, "camera_enabled", false),
            microphoneEnabled = readBoolean(KEY_MIC, fallback, legacy, "microphone_enabled", false),
            memoryEnabled = readBoolean(KEY_MEMORY, fallback, legacy, "memory_enabled", false),
            cloudProcessingEnabled = readBoolean(KEY_CLOUD, fallback, legacy, "cloud_processing_enabled", false),
            faceRecognitionEnabled = readBoolean(KEY_FACE, fallback, legacy, "face_recognition_enabled", false),
            notificationContentEnabled = readBoolean(KEY_NOTIFICATIONS, fallback, legacy, "notification_content_enabled", false),
            voicePersonalizationEnabled = readBoolean(KEY_VOICE, fallback, legacy, "voice_personalization_enabled", false),
        )
        // Keep the fallback mirror as a recovery copy. Clearing it here can
        // resurrect an older encrypted value after a transient secure-store
        // write failure, so only the legacy store is retired after migration.
        val secureMigrationComplete = listOf(
            KEY_CAMERA, KEY_MIC, KEY_MEMORY, KEY_CLOUD, KEY_FACE, KEY_NOTIFICATIONS, KEY_VOICE,
        ).all { SecureStorage.getString(it) != null }
        if (secureMigrationComplete) {
            legacy.edit().clear().apply()
        }
        initialized = true
    }

    fun setCameraEnabled(enabled: Boolean): Boolean = update { it.copy(cameraEnabled = enabled) }
    fun setMicrophoneEnabled(enabled: Boolean): Boolean = update { it.copy(microphoneEnabled = enabled) }

    fun setMemoryEnabled(enabled: Boolean): Boolean {
        val saved = update { it.copy(memoryEnabled = enabled) }
        if (saved && !enabled) {
            val memoryCleared = runCatching { MemoryRepository.deleteAll() }.getOrDefault(false)
            val companionCleared = runCatching { CompanionRepository.clearPersonalizationSynchronously() }.getOrDefault(false)
            if (!memoryCleared || !companionCleared) {
                lastWriteError.value = "Memory dimatikan, tetapi sebagian data lama belum terhapus. Coba Hapus Semua Data sekali lagi."
            }
        }
        return saved
    }

    fun setCloudProcessingEnabled(enabled: Boolean): Boolean = update { it.copy(cloudProcessingEnabled = enabled) }
    fun setFaceRecognitionEnabled(enabled: Boolean): Boolean {
        val saved = update { it.copy(faceRecognitionEnabled = enabled) }
        if (saved && !enabled) {
            // Consent withdrawn: stop processing (FaceEngine checks this flag) and erase stored templates.
            val cleared = runCatching { FaceRepository.deleteAll() }.getOrDefault(false)
            if (!cleared) {
                lastWriteError.value = "Face Recognition dimatikan, tetapi data wajah lama belum terhapus. Buka Data Wajah lalu Hapus Semua."
            }
        }
        return saved
    }

    fun setNotificationContentEnabled(enabled: Boolean): Boolean {
        val saved = update { it.copy(notificationContentEnabled = enabled) }
        if (saved) {
            if (enabled) {
                // Reconnect the Android listener so already-active notifications
                // can be backfilled immediately after consent is enabled.
                runCatching { NotificationRepository.requestListenerRefreshFromBoundContext() }
                    .onFailure { AppDiagnostics.warn("Notification listener refresh failed", it) }
            } else {
                runCatching { NotificationRepository.clear() }
            }
        }
        return saved
    }

    fun setVoicePersonalizationEnabled(enabled: Boolean): Boolean {
        val saved = update { it.copy(voicePersonalizationEnabled = enabled) }
        if (saved && !enabled) runCatching { VoiceProfileRepository.clear() }
        return saved
    }

    fun clearAllData(): Boolean {
        val operations = listOf(
            runCatching { MemoryRepository.deleteAll() }.getOrDefault(false),
            runCatching { ReminderRepository.clearAll() }.getOrDefault(false),
            runCatching { CallRepository.clearAll() }.getOrDefault(false),
            runCatching { EmergencyRepository.clear() }.getOrDefault(false),
            runCatching { EducationRepository.clearAll() }.getOrDefault(false),
            runCatching { EnterpriseRepository.clearAll() }.getOrDefault(false),
            runCatching { CompanionRepository.clearPersonalizationSynchronously(); true }.getOrDefault(false),
            runCatching { VoiceProfileRepository.clear(); true }.getOrDefault(false),
            runCatching { NotificationRepository.clear(); true }.getOrDefault(false),
            runCatching { FaceRepository.deleteAll() }.getOrDefault(false),
            runCatching { DeviceIdentity.reset(); true }.getOrDefault(false),
            runCatching { GlassesRepository.clearFrame(); true }.getOrDefault(false),
        )
        val saved = update { PrivacySettings.DEFAULT }
        val ok = saved && operations.all { it }
        if (!ok) lastWriteError.value = "Sebagian data belum berhasil dihapus. Coba reset sekali lagi."
        return ok
    }

    private fun readBoolean(
        key: String,
        fallback: android.content.SharedPreferences,
        legacy: android.content.SharedPreferences,
        legacyKey: String,
        default: Boolean,
    ): Boolean {
        // The fallback mirror is written together with the encrypted store.
        // Prefer it when present so a transient Keystore write failure cannot
        // resurrect an older secure value on the next process start.
        if (fallback.contains(key)) return fallback.getBoolean(key, default)
        SecureStorage.getString(key)?.toBooleanStrictOrNull()?.let { return it }
        if (legacy.contains(legacyKey)) {
            val value = legacy.getBoolean(legacyKey, default)
            SecureStorage.putStringSync(key, value.toString())
            fallback.edit().putBoolean(key, value).apply()
            return value
        }
        return default
    }

    private inline fun update(transform: (PrivacySettings) -> PrivacySettings): Boolean {
        if (!initialized) {
            lastWriteError.value = "Privacy Control belum selesai diinisialisasi. Buka ulang menu ini."
            return false
        }
        val next = transform(settings.value)
        val values = mapOf(
            KEY_CAMERA to next.cameraEnabled,
            KEY_MIC to next.microphoneEnabled,
            KEY_MEMORY to next.memoryEnabled,
            KEY_CLOUD to next.cloudProcessingEnabled,
            KEY_FACE to next.faceRecognitionEnabled,
            KEY_NOTIFICATIONS to next.notificationContentEnabled,
            KEY_VOICE to next.voicePersonalizationEnabled,
        )
        // Consent switches are control state, not user content. Keep a small
        // plain SharedPreferences copy as the local source of truth so an
        // Android Keystore hiccup cannot leave the UI stuck on OFF. The same
        // values are still mirrored into encrypted storage for defense in depth.
        val fallback = fallbackPrefs
        val fallbackSaved = runCatching {
            requireNotNull(fallback) { "Privacy fallback store is not initialized" }
            val editor = fallback.edit()
            values.forEach { (key, value) -> editor.putBoolean(key, value) }
            val committed = editor.commit()
            committed && values.all { (key, expected) -> fallback.getBoolean(key, !expected) == expected }
        }.onFailure { AppDiagnostics.warn("Privacy preference fallback write failed", it) }.getOrDefault(false)

        val secureSaved = if (fallbackSaved) {
            runCatching { SecureStorage.putStringsSync(values.mapValues { it.value.toString() }) }
                .onFailure { AppDiagnostics.warn("Privacy preference secure mirror failed", it) }
                .getOrDefault(false)
        } else false

        if (fallbackSaved) {
            settings.value = next
            lastWriteError.value = null
            if (!secureSaved) AppDiagnostics.warn("Privacy preference stored in local recovery store; encrypted mirror unavailable")
        } else {
            lastWriteError.value = "Pengaturan tidak berhasil disimpan. Coba lagi."
            AppDiagnostics.warn("Privacy preference write failed")
        }
        return fallbackSaved
    }
}
