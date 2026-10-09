package id.fajar.zahra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationRouteTest {
    @Test
    fun primaryRoutesSelectTheirOwnTab() {
        assertEquals("dashboard", bottomNavSelectedRoute("dashboard"))
        assertEquals("missions", bottomNavSelectedRoute("missions"))
        assertEquals("stats", bottomNavSelectedRoute("stats"))
        assertEquals("profile", bottomNavSelectedRoute("profile"))
    }

    @Test
    fun detailRoutesDoNotSelectOrShowAPrimaryTab() {
        assertNull(bottomNavSelectedRoute("edit/{missionId}"))
        assertNull(bottomNavSelectedRoute("prayer-times"))
        assertNull(bottomNavSelectedRoute("welcome"))
        assertNull(bottomNavSelectedRoute(null))
    }
}
