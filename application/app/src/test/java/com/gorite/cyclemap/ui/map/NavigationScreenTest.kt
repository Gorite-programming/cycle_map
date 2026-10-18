package com.gorite.cyclemap.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationScreenTest {

    @Test
    fun formatsDistanceCorrectly() {
        assertEquals("450 m", formatDistance(450.0))
        assertEquals("1.2 km", formatDistance(1200.0))
        assertEquals("15.0 km", formatDistance(15000.0))
    }

    @Test
    fun formatsDurationCorrectly() {
        assertEquals("5分", formatDuration(300.0))
        assertEquals("1時間15分", formatDuration(4500.0))
        assertEquals("2時間0分", formatDuration(7200.0))
    }

    @Test
    fun formatsEtaCorrectly() {
        val etaStr = formatEta(3600.0) // 1 hour ahead
        assertTrue(etaStr.contains("到着"))
        assertTrue(etaStr.matches(Regex("\\d{2}:\\d{2} 到着")))
    }
}
