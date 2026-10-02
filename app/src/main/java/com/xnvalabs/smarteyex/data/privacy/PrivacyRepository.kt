package com.xnvalabs.smarteyex.data.privacy

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.SecureStorage
import com.xnvalabs.smarteyex.data.companion.CompanionRepository
import com.xnvalabs.smarteyex.data.memory.MemoryRepository
import com.xnvalabs.smarteyex.data.notifications.NotificationRepository
import com.xnvalabs.smarteyex.data.voice.VoiceProfileRepository
import com.xnvalabs.smarteyex.data.reminder.ReminderRepository
import com.xnvalabs.smarteyex.data.call.CallRepository
import com.xnvalabs.smarteyex.data.emergency.EmergencyRepository
import com.xnvalabs.smarteyex.data.education.EducationRepository
import com.xnvalabs.smarteyex.data.enterprise.EnterpriseRepository

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
        val secureMigrationComplete = listOf(
            KEY_CAMERA, KEY_MIC, KEY_MEMORY, KEY_CLOUD, KEY_FACE,
        ).all { SecureStorage.getString(it) != null }
        if (secureMigrationComplete) legacy.edit().clear().apply()
        initialized = true
    }

    fun setCameraEnabled(enabled: Boolean) { update { it.copy(cameraEnabled = enabled) } }
    fun setMicrophoneEnabled(enabled: Boolean) { update { it.copy(microphoneEnabled = enabled) } }
    fun setMemoryEnabled(enabled: Boolean) {
        if (update { it.copy(memoryEnabled = enabled) } && !enabled) {
            runCatching { MemoryRepository.deleteAll() }
            runCatching { CompanionRepository.clearPersonalizationSynchronously() }
        }
    }
    fun setCloudProcessingEnabled(enabled: Boolean) { update { it.copy(cloudProcessingEnabled = enabled) } }
    fun setFaceRecognitionEnabled(enabled: Boolean) { update { it.copy(faceRecognitionEnabled = enabled) } }
    fun setNotificationContentEnabled(enabled: Boolean) {
        if (update { it.copy(notificationContentEnabled = enabled) } && !enabled) {
            runCatching { NotificationRepository.clear() }
        }
    }
    fun setVoicePersonalizationEnabled(enabled: Boolean) {
        if (update { it.copy(voicePersonalizationEnabled = enabled) } && !enabled) {
            runCatching { VoiceProfileRepository.clear() }
        }
    }

    fun clearAllData() {
        MemoryRepository.deleteAll()
        ReminderRepository.clearAll()
        CallRepository.clearAll()
        EmergencyRepository.clear()
        EducationRepository.clearAll()
        EnterpriseRepository.clearAll()
        CompanionRepository.clearPersonalizationSynchronously()
        VoiceProfileRepository.clear()
        NotificationRepository.clear()
        update { PrivacySettings.DEFAULT }
    }

    private fun readBoolean(key: String, legacy: android.content.SharedPreferences, legacyKey: String, default: Boolean): Boolean {
        SecureStorage.getString(key)?.let { return it.toBooleanStrictOrNull() ?: default }
        return if (legacy.contains(legacyKey)) legacy.getBoolean(legacyKey, default).also { SecureStorage.putStringSync(key, it.toString()) } else default
    }

    private inline fun update(transform: (PrivacySettings) -> PrivacySettings): Boolean {
        if (!initialized) return false
        val next = transform(settings.value)
        val saved = SecureStorage.putStringsSync(mapOf(
            KEY_CAMERA to next.cameraEnabled.toString(),
            KEY_MIC to next.microphoneEnabled.toString(),
            KEY_MEMORY to next.memoryEnabled.toString(),
            KEY_CLOUD to next.cloudProcessingEnabled.toString(),
            KEY_FACE to next.faceRecognitionEnabled.toString(),
            KEY_NOTIFICATIONS to next.notificationContentEnabled.toString(),
            KEY_VOICE to next.voicePersonalizationEnabled.toString(),
        ))
        if (saved) settings.value = next
        return saved
    }
}
