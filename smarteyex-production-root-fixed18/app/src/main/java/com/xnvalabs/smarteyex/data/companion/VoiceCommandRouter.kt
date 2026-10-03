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
    /** 0 = tidak disebut / hari ini, 1 = besok, 2 = lusa. */
    val dayOffset: Int = 0,
    /** True bila pengguna menyebut hari ("besok", "lusa"), sehingga pengingat berlaku sekali, bukan harian. */
    val dateSpecified: Boolean = false,
)

object VoiceCommandRouter {
    private val OPEN_VERBS = listOf("buka", "tampilkan", "lihat", "masuk ke", "pergi ke", "tolong buka")

    fun parse(text: String): VoiceCommand {
        val t = text.trim().lowercase().replace(Regex("\\s+"), " ")
        if (t.isBlank()) return VoiceCommand(VoiceIntent.UNKNOWN)
        val wantsOpen = OPEN_VERBS.any { t.startsWith(it) || t.contains(" $it ") }
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

            t.startsWith("ingatkan") || t.startsWith("ingetin") -> parseReminder(t)

            // Navigation intents need an explicit command verb so questions such as
            // "apa itu system prompt?" or "jelaskan privasi data" still go to XNAI.
            wantsOpen && (t.contains("memori") || t.contains("memory")) -> VoiceCommand(VoiceIntent.OPEN_MEMORY)
            t.contains("kamera") && (wantsOpen || t.startsWith("nyalakan")) -> VoiceCommand(VoiceIntent.OPEN_VISION)
            wantsOpen && t.contains("vision") -> VoiceCommand(VoiceIntent.OPEN_VISION)
            t.startsWith("terjemah") || t.startsWith("translate") || t.contains("tolong terjemah") ||
                (wantsOpen && (t.contains("terjemah") || t.contains("translate"))) ->
                VoiceCommand(VoiceIntent.OPEN_TRANSLATION)
            t.startsWith("navigasi") || t.startsWith("rute") || t.contains("arah ke") || (wantsOpen && t.contains("navigasi")) ->
                VoiceCommand(VoiceIntent.OPEN_NAVIGATION, t.substringAfter("arah ke", "").trim())
            wantsOpen && (t.contains("privacy") || t.contains("privasi")) -> VoiceCommand(VoiceIntent.OPEN_PRIVACY)
            t.contains("profil saya") || t.contains("profile saya") || (wantsOpen && (t.contains("profil") || t.contains("profile"))) ->
                VoiceCommand(VoiceIntent.OPEN_PROFILE)
            t.contains("pengaturan sistem") || (wantsOpen && (t.contains("system") || t.contains("sistem"))) ->
                VoiceCommand(VoiceIntent.OPEN_SYSTEM)
            t.startsWith("darurat") || t.contains("mode darurat") || t.contains("panggil bantuan") ->
                VoiceCommand(VoiceIntent.START_EMERGENCY, requiresConfirmation = true)
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

    /**
     * Mengubah jam yang diucapkan menjadi format 24 jam memakai kata keterangan waktu bahasa Indonesia.
     * "jam 8 malam" = 20, "jam 12 malam" = 0, "jam 3 sore" = 15, "jam 1 siang" = 13, "jam 5 pagi" = 5.
     */
    internal fun adjustHour(hour: Int, partOfDay: String?): Int = when (partOfDay) {
        "pagi" -> if (hour == 12) 0 else hour
        "siang" -> if (hour in 1..6) hour + 12 else hour
        "sore" -> if (hour in 1..11) hour + 12 else hour
        "malam" -> when {
            hour == 12 -> 0
            hour in 5..11 -> hour + 12
            else -> hour
        }
        else -> hour
    }

    private fun parseReminder(text: String): VoiceCommand {
        var argument = text.replaceFirst(Regex("^(ingatkan|ingetin)"), "").trim()
            .replaceFirst(Regex("^(gue|saya|aku)\\s+"), "")

        val dayOffset = when {
            Regex("\\blusa\\b").containsMatchIn(argument) -> 2
            Regex("\\bbesok\\b").containsMatchIn(argument) -> 1
            else -> 0
        }
        val dateSpecified = dayOffset > 0
        if (dateSpecified) argument = argument.replace(Regex("\\b(besok|lusa)\\b"), " ")

        val partOfDay = Regex("\\b(pagi|siang|sore|malam)\\b").find(argument)?.groupValues?.get(1)

        var hour: Int? = null
        var minute: Int? = null
        var removal: IntRange? = null

        val half = Regex("setengah\\s+(\\d{1,2})\\b").find(argument)
        val clock = Regex("(?:(?:jam|pukul)\\s*)?(\\d{1,2})[:.](\\d{2})\\b").find(argument)
        val whole = Regex("(?:jam|pukul)\\s*(\\d{1,2})\\b").find(argument)
        when {
            half != null -> {
                val h = half.groupValues[1].toInt()
                hour = if (h == 1) 12 else h - 1
                minute = 30
                removal = half.range
            }
            clock != null -> {
                hour = clock.groupValues[1].toInt()
                minute = clock.groupValues[2].toInt()
                removal = clock.range
            }
            whole != null -> {
                hour = whole.groupValues[1].toInt()
                minute = 0
                removal = whole.range
            }
        }
        if (hour != null) hour = adjustHour(hour, partOfDay)
        if (removal != null) argument = argument.removeRange(removal)
        if (partOfDay != null && hour != null) argument = argument.replace(Regex("\\b$partOfDay\\b"), " ")
        argument = argument.replace(Regex("\\s+"), " ").trim()

        return VoiceCommand(
            intent = VoiceIntent.CREATE_REMINDER,
            argument = argument,
            timeHour = hour?.takeIf { it in 0..23 },
            timeMinute = minute?.takeIf { it in 0..59 },
            dayOffset = dayOffset,
            dateSpecified = dateSpecified,
        )
    }
}
