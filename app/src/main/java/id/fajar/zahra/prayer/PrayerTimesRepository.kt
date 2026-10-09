package id.fajar.zahra.prayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Jadwal harian Cirebon dari API AlAdhan; jam mengikuti zona waktu perangkat. */
object PrayerTimesRepository {
    data class PrayerTime(val name: String, val time: String, val epochMillis: Long)
    data class DayTimes(val dateLabel: String, val prayers: List<PrayerTime>, val imsak: PrayerTime, val maghrib: PrayerTime)

    suspend fun fetchCirebon(): DayTimes = withContext(Dispatchers.IO) {
        val connection = (URL("https://api.aladhan.com/v1/timingsByCity?city=Cirebon&country=Indonesia&method=20").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000; requestMethod = "GET"
        }
        try {
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONObject("data")
            val timings = root.getJSONObject("timings")
            val dateLabel = root.getJSONObject("date").getString("readable")
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            fun time(key: String, label: String): PrayerTime {
                val hhmm = timings.getString(key).take(5)
                val millis = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$day $hhmm")?.time ?: System.currentTimeMillis()
                return PrayerTime(label, hhmm, millis)
            }
            val fajr = time("Fajr", "Subuh")
            val dhuhr = time("Dhuhr", "Dzuhur")
            val asr = time("Asr", "Ashar")
            val maghrib = time("Maghrib", "Maghrib")
            val isha = time("Isha", "Isya")
            DayTimes(dateLabel, listOf(fajr, dhuhr, asr, maghrib, isha), time("Imsak", "Sahur"), maghrib)
        } finally { connection.disconnect() }
    }
}
