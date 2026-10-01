package com.xnvalabs.investmenttracker.data.remote

import com.xnvalabs.investmenttracker.BuildConfig
import kotlinx.coroutines.CancellationException

data class NewsAnalysisResult(
    val headlines: List<NewsHeadline>,
    val aiSummary: String
)

/**
 * Gabungan berita real-time (Google News RSS, sumber ID + global sekaligus)
 * dengan analisis AI (Gemini). AI HANYA boleh memakai fakta dari headline
 * yang dikirim — lihat SYSTEM_PROMPT — supaya jawabannya grounded ke berita
 * asli, bukan karangan/halusinasi, dan TIDAK PERNAH kasih target harga pasti.
 */
class AiAnalysisRepository(
    private val geminiApi: GeminiApi = NetworkModule.geminiApi,
    private val geminiKey: String = BuildConfig.GEMINI_API_KEY
) {
    val isConfigured: Boolean get() = geminiKey.isNotBlank()

    /** Cari & analisis berita soal [query] (nama/ticker aset). Selalu cari di 2 locale (ID + global) sekaligus. */
    suspend fun analyze(query: String): NewsAnalysisResult? {
        if (!isConfigured) return null
        return try {
            val idNews = GoogleNewsRss.search(query, locale = "id", maxItems = 6)
            val enNews = GoogleNewsRss.search(query, locale = "en", maxItems = 6)
            val allNews = (idNews + enNews).distinctBy { it.title.take(60) }

            if (allNews.isEmpty()) {
                return NewsAnalysisResult(
                    headlines = emptyList(),
                    aiSummary = "Tidak ditemukan berita terbaru soal \"$query\" dalam 3 hari terakhir. Coba nama/ticker lain, atau berita memang lagi sepi soal aset ini."
                )
            }

            val prompt = buildPrompt(query, allNews)
            val response = geminiApi.generateContent(
                model = "gemini-2.5-flash-lite",
                apiKey = geminiKey,
                request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)))),
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(SYSTEM_PROMPT)))
                )
            )
            val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?.trim().takeUnless { it.isNullOrBlank() }
                ?: "AI tidak memberi jawaban (kemungkinan diblokir filter konten). Coba kata kunci lain."

            NewsAnalysisResult(allNews, text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun buildPrompt(query: String, news: List<NewsHeadline>): String {
        val newsText = news.mapIndexed { i, item ->
            "${i + 1}. [${item.source}] ${item.title}"
        }.joinToString("\n")
        return "Aset/ticker yang ditanya: $query\n\nDaftar berita terbaru (3 hari terakhir):\n$newsText"
    }

    private companion object {
        const val SYSTEM_PROMPT = """
Kamu asisten analisis berita finansial di dalam aplikasi tracker investasi pribadi (bukan aplikasi trading/broker). Aturan ketat, WAJIB dipatuhi:

1. HANYA gunakan fakta dari daftar berita yang diberikan user di bawah. JANGAN memakai pengetahuan umum lain soal harga, prediksi, atau target saham/aset ini.
2. JANGAN PERNAH memberi angka target harga spesifik, atau persentase kenaikan/penurunan yang dijanjikan pasti terjadi. Kalau berita menyebut angka (mis. target analis), boleh dikutip sebagai FAKTA BERITA, tapi jangan disajikan seolah itu ramalanmu sendiri.
3. Setiap poin analisis HARUS menunjuk ke sumber beritanya (nomor urut dan/atau nama outlet).
4. Kalau berita yang diberikan sedikit, tidak relevan, atau saling bertentangan, katakan itu terus terang. Jangan mengarang informasi yang tidak ada di daftar berita.
5. Jawab dalam Bahasa Indonesia yang santai tapi jelas, ringkas.
6. WAJIB tutup jawaban dengan kalimat persis ini di baris terakhir: "Ini bukan saran keuangan — analisis kasar dari berita publik, bukan prediksi harga yang pasti."

Format jawaban:
FAKTOR PENDUKUNG NAIK:
- poin (sumber: nomor/outlet)

FAKTOR PENDUKUNG TURUN:
- poin (sumber: nomor/outlet)

RINGKASAN SINGKAT:
1-2 kalimat kesimpulan netral, tanpa rekomendasi beli/jual.
"""
    }
}
