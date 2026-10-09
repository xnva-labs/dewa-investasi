package id.fajar.zahra.core

/** Transparent, local heuristic: estimates effort from the activity text without sending personal notes anywhere. */
object MissionDifficultyEstimator {
    enum class Band(val label: String) {
        EASY("Mudah"), MEDIUM("Sedang"), HARD("Menantang")
    }

    private val challengingTerms = listOf(
        "puasa daud", "tahajud", "hafal", "menghafal", "murajaah", "lari", "olahraga",
        "latihan", "belajar 2 jam", "belajar dua jam", "proyek besar", "bangun jam 3",
        "bangun pukul 3", "qiyamul lail", "setoran hafalan", "deep work"
    )
    private val steadyTerms = listOf(
        "baca quran", "baca qur'an", "tilawah", "belajar", "jalan kaki", "menulis jurnal",
        "rapikan kamar", "sedekah", "bantu orang tua", "sholat dhuha", "shalat dhuha",
        "dzikir", "zikir", "olahraga ringan", "puasa senin", "puasa kamis"
    )
    private val gentleTerms = listOf(
        "minum air", "rapikan meja", "bereskan meja", "tarik napas", "stretching 5 menit",
        "beres-beres 5 menit", "jurnal 5 menit", "baca 1 halaman", "baca satu halaman"
    )

    fun estimate(title: String, description: String = "", category: String = "General"): Int {
        val text = "$title $description".lowercase().replace('’', '\'').trim()
        if (text.isBlank()) return 1
        val challenging = challengingTerms.count(text::contains)
        val steady = steadyTerms.count(text::contains)
        val gentle = gentleTerms.count(text::contains)
        val isCorePrayer = Regex("(sholat|shalat) (subuh|dzuhur|zuhur|ashar|asar|maghrib|isya)").containsMatchIn(text)
        val base = if (category.equals("Ibadah", ignoreCase = true) || isCorePrayer) 2 else 3
        val score = base + challenging * 3 + steady - gentle - (if (isCorePrayer) 1 else 0)
        return score.coerceIn(1, 10)
    }

    fun band(difficulty: Int): Band = when (difficulty.coerceIn(1, 10)) {
        in 1..3 -> Band.EASY
        in 4..6 -> Band.MEDIUM
        else -> Band.HARD
    }
}
