package com.xnvalabs.smarteyex.data.assistant

/**
 * Parses what the always-listening mic hears. Only speech that starts with the wake word
 * ("SmartEyeX") becomes a command; everything else is discarded by the caller.
 * Pure JVM so it can be unit-tested without Android.
 */
object AssistantCommandParser {
    sealed interface Command {
        data object WakeOnly : Command
        data object StopListening : Command
        data object ReadNotifications : Command
        data class OpenApp(val name: String) : Command
        data class Reply(val body: String) : Command
        data class Ask(val text: String) : Command
    }

    data class ReplySplit(val sender: String, val message: String)

    private val SPACES = Regex("\\s+")
    private val WAKE_TARGETS = listOf("smarteyex", "smarteyes")
    private val GREETINGS = setOf("hai", "halo", "hallo", "hey", "hei", "ok", "oke", "okay")
    private val POLITE = setOf("tolong", "coba", "please")
    private val STOP_PHRASES = listOf(
        "matikan mic", "matikan mik", "matiin mic", "matikan mikrofon", "matikan microphone",
        "mic off", "mic mati", "berhenti mendengar", "stop mendengar", "stop mic",
    )
    private val OPEN_VERBS = setOf("buka", "bukain", "bukakan", "jalankan", "open")
    private val APP_FILLERS = setOf("aplikasi", "aplikasinya", "apk", "app", "dong", "ya")
    private val REPLY_VERBS = setOf("jawab", "balas")
    private val READ_VERBS = setOf("bacakan", "bacain", "baca")

    /**
     * Returns the text after the wake word (possibly empty), or null when the wake word is absent.
     * The recognizer may split the brand name ("Smart Eye X"), so 1-3 words are tried and the
     * closest match wins (ties keep the shorter span so the command is not swallowed).
     */
    fun stripWakeWord(text: String): String? {
        val tokens = text.trim().split(SPACES).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val start = if (TextMatch.token(tokens[0]) in GREETINGS) 1 else 0
        var bestCount = 0
        var bestDistance = Int.MAX_VALUE
        for (count in 1..3) {
            if (start + count > tokens.size) break
            val squashed = tokens.subList(start, start + count).joinToString("") { TextMatch.token(it) }
            val distance = wakeDistance(squashed) ?: continue
            if (distance < bestDistance) {
                bestDistance = distance
                bestCount = count
            }
        }
        if (bestCount == 0) return null
        return tokens.drop(start + bestCount).joinToString(" ").trimStart(',', '.', ':', '-', ' ')
    }

    /** Edit distance to the wake word, or null when the text is not close enough. */
    private fun wakeDistance(squashed: String): Int? {
        if (squashed == "xnai") return 0
        if (squashed.length !in 6..12) return null
        val distance = WAKE_TARGETS.minOf { TextMatch.levenshtein(squashed, it) }
        return if (distance <= 3) distance else null
    }

    fun parse(afterWake: String): Command {
        val tokens = afterWake.trim().split(SPACES).filter { it.isNotEmpty() }.toMutableList()
        while (tokens.isNotEmpty() && TextMatch.token(tokens[0]) in POLITE) tokens.removeAt(0)
        if (tokens.isEmpty()) return Command.WakeOnly

        val normalized = TextMatch.norm(tokens.joinToString(" "))
        if (STOP_PHRASES.any { normalized.contains(it) }) return Command.StopListening

        val verb = TextMatch.token(tokens[0])
        val rest = tokens.drop(1)
        when {
            verb in OPEN_VERBS -> {
                val name = rest.map { TextMatch.token(it) }.filter { it.isNotEmpty() && it !in APP_FILLERS }.joinToString(" ")
                if (name.isNotBlank()) return Command.OpenApp(name)
            }
            verb in REPLY_VERBS -> return Command.Reply(rest.joinToString(" "))
            verb in READ_VERBS && (normalized.contains("notif") || normalized.contains("pesan")) -> return Command.ReadNotifications
        }
        return Command.Ask(tokens.joinToString(" "))
    }

    /** "jawab dek zaa ..." spoken without the wake word. Returns the part after the verb, else null. */
    fun replyBody(text: String): String? {
        val tokens = text.trim().split(SPACES).filter { it.isNotEmpty() }
        if (tokens.size < 2 || TextMatch.token(tokens[0]) !in REPLY_VERBS) return null
        return tokens.drop(1).joinToString(" ")
    }

    /**
     * Splits "dek zaa aku otw ya" into sender "Dek Zaa" and message "aku otw ya".
     * Returns null when no sender matches or when two senders match equally well.
     */
    fun splitReply(body: String, senders: List<String>): ReplySplit? {
        val tokens = body.trim().split(SPACES).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val heard = tokens.map { TextMatch.token(it) }
        var best: ReplySplit? = null
        var bestWords = 0
        var bestCost = Int.MAX_VALUE
        var tie = false
        for (sender in senders) {
            val words = TextMatch.norm(sender).split(' ').map { TextMatch.token(it) }.filter { it.isNotEmpty() }
            if (words.isEmpty() || words.size > heard.size) continue
            var cost = 0
            var matches = true
            for (i in words.indices) {
                if (!TextMatch.similarWord(heard[i], words[i])) {
                    matches = false
                    break
                }
                cost += TextMatch.levenshtein(heard[i], words[i])
            }
            if (!matches) continue
            val message = tokens.drop(words.size).joinToString(" ").trimStart(',', ':', '-', ' ').trim()
            if (words.size > bestWords || (words.size == bestWords && cost < bestCost)) {
                best = ReplySplit(sender, message)
                bestWords = words.size
                bestCost = cost
                tie = false
            } else if (words.size == bestWords && cost == bestCost) {
                tie = true
            }
        }
        return if (tie) null else best
    }
}
