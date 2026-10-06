package com.xnvalabs.smarteyex.data.voice

/** One app the user can open by voice. */
data class LaunchableApp(val label: String, val packageName: String)

/** Text helpers shared by the voice matchers. Pure Kotlin so they can be unit tested on the JVM. */
object VoiceText {
    /** Lowercase letters/digits only, with repeated characters collapsed ("zaa" -> "za"). */
    fun compact(input: String): String {
        val sb = StringBuilder()
        var last = '\u0000'
        for (ch in input.lowercase()) {
            if (!ch.isLetterOrDigit()) continue
            if (ch == last) continue
            sb.append(ch)
            last = ch
        }
        return sb.toString()
    }

    fun words(input: String): List<String> =
        input.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(' ').filter { it.isNotEmpty() }

    /** Levenshtein edit distance. */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }
}

/** Detects "SmartEyeX" (as speech recognizers tend to write it) near the start of an utterance. */
object WakeWord {
    private val VARIANTS = listOf(
        "smarteyex", "smarteyes", "smarteyeks", "smarteye", "smartex", "smartiks", "smartaix", "smarteks", "xnai",
    )

    /** Returns what was said after the wake word (original casing), or null if the wake word is absent. */
    fun strip(text: String): String? {
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val limit = minOf(tokens.size, 4)
        for (start in 0 until limit) {
            for (len in 3 downTo 1) {
                if (start + len > tokens.size) continue
                val joined = VoiceText.compact(tokens.subList(start, start + len).joinToString(""))
                if (isVariant(joined)) return tokens.drop(start + len).joinToString(" ")
            }
        }
        return null
    }

    private fun isVariant(c: String): Boolean {
        if (c.length < 4) return false
        return VARIANTS.any { v -> c == v || (v.length >= 7 && c.length >= 6 && VoiceText.distance(c, v) <= 1) }
    }
}

sealed class AssistantCommand {
    object Empty : AssistantCommand()
    object StopMic : AssistantCommand()
    object StopSpeaking : AssistantCommand()
    object ReadNotifications : AssistantCommand()
    data class OpenApp(val appName: String) : AssistantCommand()
    data class Reply(val sender: String, val message: String) : AssistantCommand()
    data class ReplyNeedsSender(val spoken: String) : AssistantCommand()
    data class ReplyAmbiguous(val options: List<String>) : AssistantCommand()
    data class Ask(val text: String) : AssistantCommand()
}

/** Turns one spoken sentence (wake word already removed) into a command. */
object AssistantCommandParser {
    private val STOP_MIC = Regex("""^(tolong\s+|coba\s+)?(matikan|matiin|nonaktifkan|hentikan|stop|berhenti|udahan)\s+(dulu\s+)?(mic|mik|mikrofon|microphone|mikropon|dengerin|mendengarkan|mendengar|dengar|listening)\b.*$""")
    private val MIC_OFF = Regex("""^(mic|mik|mikrofon|microphone)\s+(off|mati|dimatikan)\b.*$""")
    private val READ_NOTIFS = Regex("""^(tolong\s+)?(bacain|bacakan|baca|bacaan)\s+(semua\s+|ulang\s+)?(notifikasi|notif|pesan|chat)\b.*$""")
    private val OPEN_APP = Regex("""^(tolong\s+)?(buka|bukain|bukakan|jalankan|nyalakan|nyalain|launch|open)\s+(aplikasi\s+|aplikasinya\s+|apk\s+|app\s+)?(.+)$""")
    private val STOP_SPEAKING = setOf("diam", "stop", "cukup", "udah", "sudah", "berhenti")
    private val REPLY_VERBS = setOf("jawab", "jawabin", "jawabkan", "balas", "balasin", "bales", "balesin", "balaskan")
    private val FILLER = setOf("ke", "pesan", "chat", "dari", "buat", "untuk", "si")
    private val TRAILING = setOf("dong", "donk", "ya", "deh", "sekarang", "dulu", "aja", "saja", "lah", "nih", "please", "tolong")

    fun parse(text: String, senders: List<String>): AssistantCommand {
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return AssistantCommand.Empty
        val lower = tokens.map { it.lowercase().trim(',', '.', '!', '?', ':', ';') }
        val joined = lower.joinToString(" ")

        if (STOP_MIC.matches(joined) || MIC_OFF.matches(joined)) return AssistantCommand.StopMic
        if (lower.size == 1 && lower[0] in STOP_SPEAKING) return AssistantCommand.StopSpeaking
        if (READ_NOTIFS.matches(joined)) return AssistantCommand.ReadNotifications
        if (lower[0] in REPLY_VERBS) return parseReply(tokens, lower, senders)
        val open = OPEN_APP.matchEntire(joined)
        if (open != null) {
            val name = trimTrailing(open.groupValues[4])
            if (name.isNotEmpty()) return AssistantCommand.OpenApp(name)
        }
        return AssistantCommand.Ask(text.trim())
    }

    private fun trimTrailing(name: String): String {
        val parts = name.split(' ').filter { it.isNotEmpty() }.toMutableList()
        while (parts.isNotEmpty() && parts[parts.size - 1] in TRAILING) parts.removeAt(parts.size - 1)
        return parts.joinToString(" ")
    }

    private data class Match(val sender: String, val length: Int, val score: Int)

    private fun parseReply(tokens: List<String>, lower: List<String>, senders: List<String>): AssistantCommand {
        var start = 1
        while (start < lower.size && lower[start] in FILLER) start++
        if (start >= tokens.size) return AssistantCommand.ReplyNeedsSender("")
        val spokenHint = tokens.subList(start, minOf(tokens.size, start + 3)).joinToString(" ")

        val matches = mutableListOf<Match>()
        for (sender in senders) {
            val target = VoiceText.compact(sender)
            if (target.isEmpty()) continue
            var bestForSender: Match? = null
            val maxLen = minOf(4, tokens.size - start)
            for (len in 1..maxLen) {
                val spoken = VoiceText.compact(lower.subList(start, start + len).joinToString(""))
                val score = scoreName(spoken, target)
                if (score < 0) continue
                val current = bestForSender
                if (current == null || score < current.score || (score == current.score && len > current.length)) {
                    bestForSender = Match(sender, len, score)
                }
            }
            if (bestForSender != null) matches.add(bestForSender)
        }
        if (matches.isEmpty()) return AssistantCommand.ReplyNeedsSender(spokenHint)

        val bestScore = matches.minOf { it.score }
        val top = matches.filter { it.score == bestScore }
        val longest = top.maxOf { it.length }
        val finalists = top.filter { it.length == longest }
        if (finalists.size > 1) return AssistantCommand.ReplyAmbiguous(finalists.map { it.sender }.distinct())
        val chosen = finalists[0]
        val message = tokens.drop(start + chosen.length).joinToString(" ")
        return AssistantCommand.Reply(chosen.sender, message)
    }

    /** 0 = exact, 1-2 = close enough, -1 = no match. Names are compared in [VoiceText.compact] form. */
    private fun scoreName(spoken: String, target: String): Int {
        if (spoken.isEmpty()) return -1
        if (spoken == target) return 0
        if (spoken.length >= 3 && target.startsWith(spoken)) return 1
        val d = VoiceText.distance(spoken, target)
        if (d == 1 && target.length >= 4) return 1
        if (d == 2 && target.length >= 7) return 2
        return -1
    }
}

/** Finds the installed app the user meant ("claude", "wa", "what's app"). */
object AppMatcher {
    private val ALIASES = mapOf(
        "wa" to "whatsapp", "ig" to "instagram", "fb" to "facebook", "yt" to "youtube", "tele" to "telegram",
    )

    fun best(spoken: String, apps: List<LaunchableApp>): LaunchableApp? {
        var q = VoiceText.compact(spoken)
        val words = VoiceText.words(spoken)
        if (words.size == 1) {
            val alias = ALIASES[words[0]]
            if (alias != null) q = VoiceText.compact(alias)
        }
        if (q.length < 2) return null
        var best: LaunchableApp? = null
        var bestScore = Int.MAX_VALUE
        for (app in apps) {
            val label = VoiceText.compact(app.label)
            if (label.isEmpty()) continue
            val score = scoreApp(label, q)
            if (score < bestScore) {
                best = app
                bestScore = score
            }
        }
        return best
    }

    private fun scoreApp(label: String, q: String): Int {
        if (label == q) return 0
        if (q.length >= 3 && label.startsWith(q)) return 1 + (label.length - q.length)
        if (q.length >= 4 && label.contains(q)) return 20 + (label.length - q.length)
        if (q.length >= 5) {
            val d = VoiceText.distance(label, q)
            if (d <= 1) return 60 + d
            if (d == 2 && label.length >= 6) return 65
        }
        return Int.MAX_VALUE
    }
}
