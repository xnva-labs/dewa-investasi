package com.xnvalabs.xnai

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

/** Public-web research adapters. They retrieve small evidence packets, not arbitrary code. */
class XnaiResearchGateway(private val githubToken: () -> String?) {
    data class SearchResult(val source: String, val title: String, val url: String, val snippet: String)

    fun search(query: String, maxPerSource: Int = 3, budget: Int = 6): List<SearchResult> {
        if (query.isBlank() || budget <= 0) return emptyList()
        val per = maxOf(1, budget / 3)
        val out = mutableListOf<SearchResult>()
        safe { wikipedia(query, minOf(maxPerSource, per)) }?.let(out::addAll)
        if (out.size < budget) safe { crossref(query, minOf(maxPerSource, budget - out.size)) }?.let(out::addAll)
        if (out.size < budget) safe { github(query, minOf(maxPerSource, budget - out.size)) }?.let(out::addAll)
        return out.take(budget)
    }

    fun fetch(url: String, maxChars: Int = 80_000): String = safeString {
        val connection = open(url)
        connection.inputStream.bufferedReader().use { it.readText().take(maxChars) }
    } ?: ""

    private fun wikipedia(query: String, max: Int): List<SearchResult> {
        val api = "https://id.wikipedia.org/w/api.php?action=query&list=search&format=json&utf8=1&origin=*" +
            "&srlimit=$max&srsearch=" + encode(query)
        val o = JSONObject(httpGet(api))
        val arr = o.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val title = item.optString("title")
                add(SearchResult("Wikipedia Bahasa Indonesia", title, "https://id.wikipedia.org/wiki/${encodePath(title)}", item.optString("snippet").replace("<span class=\"searchmatch\">", "").replace("</span>", "")))
            }
        }
    }

    private fun crossref(query: String, max: Int): List<SearchResult> {
        val api = "https://api.crossref.org/works?rows=$max&query=" + encode(query)
        val items = JSONObject(httpGet(api)).optJSONObject("message")?.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val title = item.optJSONArray("title")?.optString(0).orEmpty()
                val url = item.optString("URL")
                if (title.isNotBlank()) add(SearchResult("Crossref", title, url, item.optString("abstract").take(900)))
            }
        }
    }

    private fun github(query: String, max: Int): List<SearchResult> {
        val api = "https://api.github.com/search/repositories?q=" + encode(query) + "&per_page=$max"
        val connection = open(api)
        githubToken()?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        val items = JSONObject(read(connection)).optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                add(SearchResult("GitHub", item.optString("full_name"), item.optString("html_url"), item.optString("description")))
            }
        }
    }

    private fun httpGet(url: String): String = read(open(url))

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json,text/plain;q=0.9,*/*;q=0.8")
        connection.setRequestProperty("User-Agent", "XNAI-Native/1.0")
        return connection
    }

    private fun read(c: HttpURLConnection): String {
        c.connect()
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        return (stream ?: return "").bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    private fun encodePath(value: String) = value.replace(" ", "_").replace("/", "%2F")

    private fun <T> safe(block: () -> List<T>): List<T>? = try { block() } catch (_: Throwable) { null }
    private fun safeString(block: () -> String): String? = try { block() } catch (_: Throwable) { null }
}
