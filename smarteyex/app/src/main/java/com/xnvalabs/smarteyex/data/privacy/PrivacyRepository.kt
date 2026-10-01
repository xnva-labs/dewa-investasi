package com.xnvalabs.smarteyex.data.privacy

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.SecureStorage
import com.xnvalabs.smarteyex.data.companion.CompanionRepository
import com.xnvalabs.smarteyex.data.intelligence.UserModelRepository
import com.xnvalabs.smarteyex.data.memory.MemoryRepository
import com.xnvalabs.smarteyex.data.notifications.NotificationRepository
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
    private var initialized = false

    var settings = mutableStateOf(PrivacySettings.DEFAULT)
        private set

    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        val legacy = context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        settings.value = PrivacySettings(
            cameraEnabled = readBoolean(KEY_CAMERA, legacy, "camera_enabled", false),
            microphoneEnabled = readBoolean(KEY_MIC, legacy, "microphone_enabled", false),
            memoryEnabled = readBoolean(KEY_MEMORY, legacy, "memory_enabled", false),
            cloudProcessingEnabled = readBoolean(KEY_CLOUD, legacy, "cloud_processing_enabled", false),
            faceRecognitionEnabled = readBoolean(KEY_FACE, legacy, "face_recognition_enabled", false),
            notificationContentEnabled = SecureStorage.getBoolean(KEY_NOTIFICATIONS, false),
            voicePersonalizationEnabled = SecureStorage.getBoolean(KEY_VOICE, false),
        )
        legacy.edit().clear().apply()
        initialized = true
    }

    fun setCameraEnabled(enabled: Boolean) = update { it.copy(cameraEnabled = enabled) }
    fun setMicrophoneEnabled(enabled: Boolean) = update { it.copy(microphoneEnabled = enabled) }
    fun setMemoryEnabled(enabled: Boolean) {
        update { it.copy(memoryEnabled = enabled) }
        if (!enabled) {
            MemoryRepository.deleteAll()
            UserModelRepository.clear()
            CompanionRepository.clearPersonalization()
        }
    }
    fun setCloudProcessingEnabled(enabled: Boolean) = update { it.copy(cloudProcessingEnabled = enabled) }
    fun setFaceRecognitionEnabled(enabled: Boolean) = update { it.copy(faceRecognitionEnabled = enabled) }
    fun setNotificationContentEnabled(enabled: Boolean) {
        update { it.copy(notificationContentEnabled = enabled) }
        if (!enabled) NotificationRepository.clear()
    }
    fun setVoicePersonalizationEnabled(enabled: Boolean) {
        update { it.copy(voicePersonalizationEnabled = enabled) }
        if (!enabled) VoiceProfileRepository.clear()
    }

    fun clearAllData(onClearMemory: () -> Unit = {}) {
        onClearMemory()
        MemoryRepository.deleteAll()
        UserModelRepository.clear()
        CompanionRepository.clearPersonalization()
        VoiceProfileRepository.clear()
        NotificationRepository.clear()
        update { PrivacySettings.DEFAULT }
    }

    private fun readBoolean(key: String, legacy: android.content.SharedPreferences, legacyKey: String, default: Boolean): Boolean {
        SecureStorage.getString(key)?.let { return it.toBooleanStrictOrNull() ?: default }
        return if (legacy.contains(legacyKey)) legacy.getBoolean(legacyKey, default).also { SecureStorage.putStringSync(key, it.toString()) } else default
    }

    private inline fun update(transform: (PrivacySettings) -> PrivacySettings) {
        check(initialized) { "PrivacyRepository.init(context) must be called first" }
        val next = transform(settings.value)
        settings.value = next
        SecureStorage.putBoolean(KEY_CAMERA, next.cameraEnabled)
        SecureStorage.putBoolean(KEY_MIC, next.microphoneEnabled)
        SecureStorage.putBoolean(KEY_MEMORY, next.memoryEnabled)
        SecureStorage.putBoolean(KEY_CLOUD, next.cloudProcessingEnabled)
        SecureStorage.putBoolean(KEY_FACE, next.faceRecognitionEnabled)
        SecureStorage.putBoolean(KEY_NOTIFICATIONS, next.notificationContentEnabled)
        SecureStorage.putBoolean(KEY_VOICE, next.voicePersonalizationEnabled)
    }
}
