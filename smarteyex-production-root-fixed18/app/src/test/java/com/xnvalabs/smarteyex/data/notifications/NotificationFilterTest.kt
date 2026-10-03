package com.xnvalabs.smarteyex.data.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFilterTest {
    private fun read(
        pkg: String = "com.whatsapp",
        ongoing: Boolean = false,
        summary: Boolean = false,
        category: String? = "msg",
        message: String = "Budi — halo",
    ) = NotificationFilter.shouldRead(pkg, "com.xnvalabs.smarteyex", ongoing, summary, category, message)

    @Test fun normalMessageIsRead() = assertTrue(read())

    @Test fun ownNotificationsOngoingSummariesAndBlankAreSkipped() {
        assertFalse(read(pkg = "com.xnvalabs.smarteyex"))
        assertFalse(read(ongoing = true))
        assertFalse(read(summary = true))
        assertFalse(read(message = "  "))
    }

    @Test fun progressAndTransportCategoriesAreSkipped() {
        assertFalse(read(category = "progress"))
        assertFalse(read(category = "transport"))
        assertTrue(read(category = null))
        assertTrue(read(category = "email"))
    }

    @Test fun deduperSuppressesRepeatsInsideWindowOnly() {
        val d = RecentDeduper(windowMs = 1000, capacity = 4)
        assertFalse(d.isDuplicate("a", 0))
        assertTrue(d.isDuplicate("a", 500))
        assertFalse(d.isDuplicate("b", 600))
        assertFalse(d.isDuplicate("a", 2000))
    }

    @Test fun deduperStaysBounded() {
        val d = RecentDeduper(windowMs = 60_000, capacity = 3)
        for (i in 0 until 10) d.isDuplicate("k$i", i.toLong())
        assertFalse("oldest keys were evicted", d.isDuplicate("k0", 20))
        assertTrue(d.isDuplicate("k9", 21))
    }
}
