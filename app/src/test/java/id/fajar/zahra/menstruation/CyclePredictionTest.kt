package id.fajar.zahra.menstruation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CyclePredictionTest {
    @Test fun schedulesThreeDaysBeforeNextEstimatedStartAtNine() {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val now = formatter.parse("2026-10-10")!!.time + 12 * 60 * 60 * 1000L
        val result = Calendar.getInstance().apply { timeInMillis = CyclePrediction.reminderEpochMillis("2026-10-01", 28, now) }
        assertEquals("2026-10-26", formatter.format(result.time))
        assertEquals(9, result.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun rejectsOutOfRangeCycleLength() {
        var rejected = false
        try {
            CyclePrediction.reminderEpochMillis("2026-10-01", 5, 0L)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
