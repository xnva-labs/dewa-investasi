package com.xnvalabs.investmenttracker.data.remote

import android.util.Xml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder

data class NewsHeadline(
    val title: String,
    val source: String,
    val pubDate: String,
    val link: String
)

/**
 * Google News RSS — GRATIS, TANPA API key. Mengumpulkan berita dari ribuan
 * media sekaligus (global maupun lokal Indonesia) berdasarkan kata kunci,
 * jadi otomatis "semua sumber" tanpa perlu hardcode satu-satu RSS outlet.
 *
 * Dokumentasi endpoint ini tidak resmi dari Google (tidak ada developer
 * docs formal), tapi formatnya sudah stabil bertahun-tahun dan dipakai
 * luas: https://news.google.com/rss/search?q=...&hl=...&gl=...&ceid=...
 */
object GoogleNewsRss {
    private val client = OkHttpClient()

    /**
     * [locale] "id" = berita Bahasa Indonesia (cocok saham IDX/lokal),
     * "en" = berita global Bahasa Inggris (cocok saham US, crypto).
     * "when:3d" membatasi ke berita 3 hari terakhir saja (paling krusial/baru).
     */
    suspend fun search(query: String, locale: String = "id", maxItems: Int = 8): List<NewsHeadline> =
        withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode("$query when:3d", "UTF-8")
                val hl: String; val gl: String; val ceid: String
                if (locale == "id") {
                    hl = "id"; gl = "ID"; ceid = "ID:id"
                } else {
                    hl = "en-US"; gl = "US"; ceid = "US:en"
                }
                val url = "https://news.google.com/rss/search?q=$encodedQuery&hl=$hl&gl=$gl&ceid=$ceid"

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body()?.string() ?: return@withContext emptyList()

                parseRssItems(body, maxItems)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }

    private fun parseRssItems(xml: String, maxItems: Int): List<NewsHeadline> {
        val results = mutableListOf<NewsHeadline>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var inItem = false
        var title = ""
        var link = ""
        var pubDate = ""
        var source = ""

        while (eventType != XmlPullParser.END_DOCUMENT && results.size < maxItems) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "item" -> { inItem = true; title = ""; link = ""; pubDate = ""; source = "" }
                        "title" -> if (inItem) title = safeNextText(parser)
                        "link" -> if (inItem) link = safeNextText(parser)
                        "pubDate" -> if (inItem) pubDate = safeNextText(parser)
                        "source" -> if (inItem) source = safeNextText(parser)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && inItem) {
                        if (title.isNotBlank()) {
                            results.add(
                                NewsHeadline(
                                    title = title,
                                    source = source.ifBlank { "Google News" },
                                    pubDate = pubDate,
                                    link = link
                                )
                            )
                        }
                        inItem = false
                    }
                }
                else -> Unit
            }
            eventType = try { parser.next() } catch (e: Exception) { XmlPullParser.END_DOCUMENT }
        }
        return results
    }

    private fun safeNextText(parser: XmlPullParser): String =
        try { parser.nextText().trim() } catch (e: Exception) { "" }
}
