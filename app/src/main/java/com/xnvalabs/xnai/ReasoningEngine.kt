package com.xnvalabs.xnai

object ReasoningEngine {
    fun answer(
        query: String,
        formulas: List<FormulaItem>,
        artifacts: List<Artifact>
    ): String {
        val q = query.trim().lowercase()

        formulas.firstOrNull { f ->
            q.contains(f.name.lowercase()) ||
            q.contains(f.formula.lowercase()) ||
            f.domain.lowercase() in q
        }?.let { f ->
            return buildString {
                append("Konsep yang paling dekat: ${f.name}.\n\n")
                append("Rumus: ${f.formula}\n")
                append("Makna: ${f.meaning}\n")
                append("Variabel: ${f.variables}\n")
                append("Contoh: ${f.example}\n\n")
                append("Langkah reasoning: identifikasi besaran → cek satuan → substitusi → verifikasi hasil.")
            }
        }

        artifacts.firstOrNull { a ->
            a.title.lowercase().contains(q) || a.content.lowercase().contains(q)
        }?.let { a ->
            return "Saya menemukan artifact \"${a.title}\". Inti yang tersimpan:\n\n${a.content.take(900)}"
        }

        return when {
            listOf("mengapa", "kenapa", "sebab", "akibat").any(q::contains) ->
                "Pecah menjadi: fakta awal → mekanisme → akibat → alternatif penjelasan → bukti yang dibutuhkan."
            listOf("hitung", "berapa", "rumus", "formula").any(q::contains) ->
                "Untuk soal kuantitatif: tulis besaran yang diketahui → pilih rumus → substitusi → cek satuan → cek kewajaran hasil."
            listOf("debug", "error", "bug").any(q::contains) ->
                "Debugging: reproduksi → baca error terdekat → buat hipotesis kecil → uji satu perubahan → verifikasi regresi."
            else ->
                "Mode lokal XNAI: saya akan memecah masalah menjadi konteks, asumsi, bukti, hubungan sebab-akibat, lalu kesimpulan. Belum ada model cloud yang terpasang di build ini."
        }
    }
}
