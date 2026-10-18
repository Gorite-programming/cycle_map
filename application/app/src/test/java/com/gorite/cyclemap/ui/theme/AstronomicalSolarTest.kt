package com.gorite.cyclemap.ui.theme

import com.gorite.cyclemap.ui.theme.SolarCalculator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AstronomicalSolarTest {

    @Test
    fun testSolarNoonAndDayNightCalculation() {
        val tokyoLat = 35.6895
        val tokyoLon = 139.6917
        val tokyoZone = ZoneId.of("Asia/Tokyo")

        // 2026年5月1日 12:00 JST (昼)
        val noon = ZonedDateTime.of(2026, 5, 1, 12, 0, 0, 0, tokyoZone)
        val isNoonNight = SolarCalculator.isNight(
            latitude = tokyoLat,
            longitude = tokyoLon,
            epochMillis = noon.toInstant().toEpochMilli(),
            zoneId = tokyoZone
        )
        assertFalse("正午12時は昼間であるべき", isNoonNight)

        // 2026年5月1日 23:00 JST (夜)
        val night = ZonedDateTime.of(2026, 5, 1, 23, 0, 0, 0, tokyoZone)
        val isMidnightNight = SolarCalculator.isNight(
            latitude = tokyoLat,
            longitude = tokyoLon,
            epochMillis = night.toInstant().toEpochMilli(),
            zoneId = tokyoZone
        )
        assertTrue("夜23時は夜間であるべき", isMidnightNight)
    }

    @Test
    fun testFallbackWhenCoordinatesNull() {
        val tokyoZone = ZoneId.of("Asia/Tokyo")

        // 座標なしで昼間 (14:00)
        val day = ZonedDateTime.of(2026, 5, 1, 14, 0, 0, 0, tokyoZone)
        assertFalse(SolarCalculator.isNight(null, null, day.toInstant().toEpochMilli(), tokyoZone))

        // 座標なしで夜間 (02:00)
        val night = ZonedDateTime.of(2026, 5, 1, 2, 0, 0, 0, tokyoZone)
        assertTrue(SolarCalculator.isNight(null, null, night.toInstant().toEpochMilli(), tokyoZone))
    }
}
