package id.fajar.zahra.islamic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Public read-only Islamic text sources. Network calls always run off the UI thread. */
object IslamicContentRepository {
    data class Book(val id: String, val label: String)
    data class TextItem(
        val title: String,
        val arabic: String,
        val translation: String,
        val source: String,
        val reference: String = ""
    )

    val books = listOf(
        Book("bukhari", "Bukhari"),
        Book("muslim", "Muslim"),
        Book("tirmidzi", "Tirmidzi"),
        Book("nasai", "An-Nasa'i"),
        Book("abu-daud", "Abu Dawud"),
        Book("ibnu-majah", "Ibnu Majah"),
        Book("ahmad", "Ahmad"),
        Book("darimi", "Ad-Darimi"),
        Book("malik", "Malik")
    )

    val duaSources = listOf(
        "all" to "Semua",
        "harian" to "Harian",
        "quran" to "Al-Qur'an",
        "hadits" to "Hadis",
        "pilihan" to "Pilihan",
        "ibadah" to "Ibadah",
        "haji" to "Haji",
        "lainnya" to "Lainnya"
    )

    suspend fun getHadith(bookId: String, number: Int): TextItem = withContext(Dispatchers.IO) {
        require(books.any { it.id == bookId }) { "Kitab hadis tidak dikenal." }
        require(number in 1..100_000) { "Nomor hadis di luar rentang." }
        val root = getJson("https://api.hadith.gading.dev/books/$bookId/$number")
        val data = root.optJSONObject("data") ?: root
        val content = data.optJSONObject("contents")
            ?: data.optJSONObject("hadith")
            ?: findHadithInArray(data.optJSONArray("hadith"), number)
            ?: data
        val bookLabel = books.first { it.id == bookId }.label
        val apiArabic = firstNonBlank(content, "arab", "arabic", "ar")
        val translation = firstNonBlank(content, "id", "translation", "indonesia", "idn", "text")
        if (apiArabic.isBlank() && translation.isBlank()) {
            throw IllegalStateException("Teks hadis belum tersedia dari sumber. Coba nomor lain atau muat ulang.")
        }
        TextItem(
            title = "Hadis $bookLabel",
            arabic = apiArabic,
            translation = translation,
            source = "API Hadith Gading · kitab $bookLabel",
            reference = "Nomor ${content.optInt("number", number)}"
        )
    }

    suspend fun getDuas(source: String): List<TextItem> = withContext(Dispatchers.IO) {
        require(duaSources.any { it.first == source }) { "Kategori doa tidak dikenal." }
        val url = if (source == "all") "https://ournoor.com/api/v1/doa"
            else "https://ournoor.com/api/v1/doa?source=$source"
        val root = getJson(url)
        val array = root.optJSONArray("data") ?: root.optJSONObject("data")?.optJSONArray("data") ?: JSONArray()
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val title = firstNonBlank(item, "judul", "title", "name")
                val arabic = firstNonBlank(item, "arab", "arabic", "content")
                val translation = firstNonBlank(item, "indo", "translation", "arti", "meaning")
                if (title.isNotBlank() || arabic.isNotBlank() || translation.isNotBlank()) {
                    add(TextItem(
                        title = title.ifBlank { "Doa pilihan" },
                        arabic = arabic,
                        translation = translation,
                        source = "API OurNoor · ${item.optString("source", source)}"
                    ))
                }
            }
        }
    }

    private fun getJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Zahra-Android/0.18")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Sumber konten merespons HTTP $status.")
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun findHadithInArray(array: JSONArray?, number: Int): JSONObject? {
        if (array == null) return null
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (item.optInt("number", -1) == number) return item
        }
        return array.optJSONObject(0)
    }

    private fun firstNonBlank(item: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            val candidate = item.optString(key, "").trim()
            if (candidate.isNotBlank() && candidate != "null") return candidate
        }
        return ""
    }
}
