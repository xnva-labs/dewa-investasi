package id.fajar.zahra.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class RepeatRulesTest {
    @Test fun dailyAddsOneDay(){
        val c=Calendar.getInstance().apply{set(2026,Calendar.OCTOBER,4,12,0,0);set(Calendar.MILLISECOND,0)}
        val next=RepeatRules.next(c.timeInMillis,RepeatRules.DAILY)!!
        val out=Calendar.getInstance().apply{timeInMillis=next}
        assertEquals(Calendar.OCTOBER, out.get(Calendar.MONTH));assertEquals(5,out.get(Calendar.DAY_OF_MONTH));assertEquals(12,out.get(Calendar.HOUR_OF_DAY))
    }
    @Test fun weeklyAddsSevenDays(){
        val c=Calendar.getInstance().apply{set(2026,Calendar.OCTOBER,4,12,0,0);set(Calendar.MILLISECOND,0)}
        val next=RepeatRules.next(c.timeInMillis,RepeatRules.WEEKLY)!!
        val out=Calendar.getInstance().apply{timeInMillis=next}
        assertEquals(11,out.get(Calendar.DAY_OF_MONTH))
    }
    @Test fun noneDoesNotRepeat(){ assertNull(RepeatRules.next(System.currentTimeMillis(),RepeatRules.NONE)) }

    @Test fun dawudAlternatesEveryTwoDays() {
        val from = 1_800_000_000_000L
        assertEquals(from + 2L * 24 * 60 * 60 * 1000, RepeatRules.next(from, RepeatRules.DAWUD))
    }
}
