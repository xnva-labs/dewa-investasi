package com.xnvalabs.smarteyex.data.privacy

data class PrivacySettings(
    val cameraEnabled: Boolean = false,
    val microphoneEnabled: Boolean = false,
    val memoryEnabled: Boolean = false,
    val cloudProcessingEnabled: Boolean = false,
    val faceRecognitionEnabled: Boolean = false,
    val notificationContentEnabled: Boolean = false,
    val voicePersonalizationEnabled: Boolean = false,
) {
    companion object {
        val DEFAULT = PrivacySettings()
    }
}
