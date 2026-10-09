package id.fajar.zahra.menstruation

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Approximate calendar reminder only; this function does not determine Islamic rulings or diagnose health. */
object CyclePrediction {
    fun reminderEpochMillis(startDate: String, cycleLengthDays: Int, nowMillis: Long = System.currentTimeMillis()): Long {
        require(cycleLengthDays in 15..60) { "Panjang siklus harus 15–60 hari." }
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val parsed = formatter.parse(startDate) ?: throw IllegalArgumentException("Tanggal harus memakai format YYYY-MM-DD.")
        require(formatter.format(parsed) == startDate) { "Tanggal tidak valid. Gunakan YYYY-MM-DD." }
        val today = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val nextStart = Calendar.getInstance().apply { time = parsed }
        while (nextStart.timeInMillis <= today.timeInMillis) nextStart.add(Calendar.DAY_OF_YEAR, cycleLengthDays)
        nextStart.add(Calendar.DAY_OF_YEAR, -3)
        nextStart.set(Calendar.HOUR_OF_DAY, 9); nextStart.set(Calendar.MINUTE, 0); nextStart.set(Calendar.SECOND, 0); nextStart.set(Calendar.MILLISECOND, 0)
        if (nextStart.timeInMillis <= nowMillis) nextStart.add(Calendar.DAY_OF_YEAR, cycleLengthDays)
        return nextStart.timeInMillis
    }
}
