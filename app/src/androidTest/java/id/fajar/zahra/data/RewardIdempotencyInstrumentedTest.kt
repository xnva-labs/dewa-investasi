package id.fajar.zahra.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.fajar.zahra.core.RewardGuard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RewardIdempotencyInstrumentedTest {
    @Test fun uniqueSourceKeyBlocksDuplicateLedgerRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ZahraDatabase::class.java).allowMainThreadQueries().build()
        try {
            val first = db.pointDao().addIfNew(
                PointLedgerEntity(
                    sourceType = "MISSION",
                    sourceId = 7L,
                    sourceKey = RewardGuard.oneTimeMissionKey(7L),
                    label = "Test mission",
                    points = 20
                )
            )
            val duplicate = db.pointDao().addIfNew(
                PointLedgerEntity(
                    sourceType = "MISSION",
                    sourceId = 7L,
                    sourceKey = RewardGuard.oneTimeMissionKey(7L),
                    label = "Test mission duplicate",
                    points = 20
                )
            )
            assertTrue(first > 0L)
            assertEquals(0L, duplicate)
            assertEquals(20, db.pointDao().getTotal())
        } finally {
            db.close()
        }
    }
}
