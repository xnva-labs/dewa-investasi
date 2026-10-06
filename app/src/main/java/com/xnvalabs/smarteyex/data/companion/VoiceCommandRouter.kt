package com.xnvalabs.smarteyex.data.companion

import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository

/** Local intent parser; sensitive actions still require explicit UI confirmation where applicable. */
enum class VoiceIntent {
    ASK_XNAI, READ_NOTIFICATIONS, REPLY_NOTIFICATION, CREATE_REMINDER, OPEN_MEMORY, OPEN_VISION,
    OPEN_TRANSLATION, OPEN_NAVIGATION, OPEN_PRIVACY, OPEN_PROFILE, OPEN_SYSTEM,
    START_EMERGENCY, UNKNOWN,
}

data class VoiceCommand(
    val intent: VoiceIntent,
    val argument: String = "",
    val requiresConfirmation: Boolean = false,
    val timeHour: Int? = null,
    val timeMinute: Int? = null,
)

object VoiceCommandRouter {
    fun parse(text: String): VoiceCommand {
        val t = text.trim().lowercase()
        if (t.isBlank()) return VoiceCommand(VoiceIntent.UNKNOWN)
        return when {
            t.contains("bacain") && (t.contains("notifikasi") || t.contains("pesan")) ->
                VoiceCommand(VoiceIntent.READ_NOTIFICATIONS)

            t.startsWith("balas aja") || t.startsWith("balas pesan") -> {
                val argument = t
                    .removePrefix("balas aja")
                    .removePrefix("balas pesan")
                    .trim()
                VoiceCommand(VoiceIntent.REPLY_NOTIFICATION, argument, requiresConfirmation = true)
            }

            t.startsWith("ingatkan") -> parseReminder(t)

            t.contains("buka memori") || t.contains("lihat memori") -> VoiceCommand(VoiceIntent.OPEN_MEMORY)
            (t.contains("lihat") && t.contains("kamera")) || t.contains("vision") -> VoiceCommand(VoiceIntent.OPEN_VISION)
            t.contains("terjemah") || t.contains("translate") -> VoiceCommand(VoiceIntent.OPEN_TRANSLATION)
            t.contains("navigasi") || t.contains("arah ke") -> VoiceCommand(
                VoiceIntent.OPEN_NAVIGATION,
                t.substringAfter("arah ke", "").trim(),
            )
            t.contains("privacy") || t.contains("privasi") -> VoiceCommand(VoiceIntent.OPEN_PRIVACY)
            t.contains("profil saya") || t.contains("profile saya") -> VoiceCommand(VoiceIntent.OPEN_PROFILE)
            t.contains("system") || t.contains("pengaturan sistem") -> VoiceCommand(VoiceIntent.OPEN_SYSTEM)
            t.contains("darurat") || t.contains("panggil bantuan") -> VoiceCommand(
                VoiceIntent.START_EMERGENCY,
                requiresConfirmation = true,
            )
            else -> VoiceCommand(VoiceIntent.ASK_XNAI)
        }
    }

    fun canUseSensitiveIntent(intent: VoiceIntent): Boolean = when (intent) {
        VoiceIntent.READ_NOTIFICATIONS, VoiceIntent.REPLY_NOTIFICATION ->
            PrivacyRepository.settings.value.notificationContentEnabled
        VoiceIntent.OPEN_VISION -> PrivacyRepository.settings.value.cameraEnabled
        VoiceIntent.OPEN_MEMORY -> PrivacyRepository.settings.value.memoryEnabled
        VoiceIntent.START_EMERGENCY -> true
        else -> true
    }

    private fun parseReminder(text: String): VoiceCommand {
        var argument = text.substringAfter("ingatkan").trim()
            .replaceFirst(Regex("^(gue|saya)\\s+"), "")
        val match = Regex("(?:jam\\s*)?(\\d{1,2})(?::|\\.)(\\d{2})|jam\\s*(\\d{1,2})\\b").find(argument)
        val hour = match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.toIntOrNull()
            ?: match?.groupValues?.getOrNull(3)?.takeIf { it.isNotBlank() }?.toIntOrNull()
        val minute = match?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: if (hour != null) 0 else null
        if (match != null) argument = argument.removeRange(match.range).replace(Regex("\\s+"), " ").trim()
        return VoiceCommand(
            intent = VoiceIntent.CREATE_REMINDER,
            argument = argument,
            timeHour = hour?.takeIf { it in 0..23 },
            timeMinute = minute?.takeIf { it in 0..59 },
        )
    }
}
