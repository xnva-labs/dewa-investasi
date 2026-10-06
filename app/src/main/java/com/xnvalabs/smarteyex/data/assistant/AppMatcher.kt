package com.xnvalabs.smarteyex.data.assistant

data class LaunchableApp(val label: String, val packageName: String)

/** Maps a spoken app name ("wa", "claude", "google maps") to an installed launcher app. Pure JVM. */
object AppMatcher {
    private val ALIASES = mapOf(
        "wa" to "whatsapp",
        "ig" to "instagram",
        "fb" to "facebook",
        "yt" to "youtube",
        "tele" to "telegram",
        "gmaps" to "maps",
    )

    fun resolve(query: String, apps: List<LaunchableApp>): LaunchableApp? {
        val spoken = TextMatch.norm(query)
        if (spoken.isEmpty()) return null
        val q = ALIASES[spoken] ?: spoken
        val labelled = apps.map { it to TextMatch.norm(it.label) }.filter { it.second.isNotEmpty() }

        labelled.filter { it.second == q }.minByOrNull { it.second.length }?.let { return it.first }
        labelled.filter { it.second.startsWith(q) }.minByOrNull { it.second.length }?.let { return it.first }
        labelled.filter { entry ->
            entry.second.split(' ').any { it == q } || (q.length >= 4 && entry.second.contains(q))
        }.minByOrNull { it.second.length }?.let { return it.first }

        if (q.contains(' ')) return null
        var best: LaunchableApp? = null
        var bestCost = Int.MAX_VALUE
        for ((app, label) in labelled) {
            for (word in label.split(' ')) {
                if (TextMatch.similarWord(word, q)) {
                    val cost = TextMatch.levenshtein(word, q)
                    if (cost < bestCost) {
                        best = app
                        bestCost = cost
                    }
                }
            }
        }
        return best
    }
}
