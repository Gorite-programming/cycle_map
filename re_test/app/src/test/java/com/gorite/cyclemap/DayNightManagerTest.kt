package com.gorite.cyclemap

import com.gorite.cyclemap.ui.theme.DayNightResolver
import com.gorite.cyclemap.ui.theme.SolarCalculator
import com.gorite.cyclemap.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DayNightManagerTest {

    private val tokyoZone = ZoneId.of("Asia/Tokyo")

    @Test
    fun solarCalculator_calculatesPlausibleSunriseAndSunsetInJapan() {
        // 2026年5月1日 12:00 JST, 広島 (34.3965, 132.4596)
        val ldt = LocalDateTime.of(2026, 5, 1, 12, 0)
        val zdt = ZonedDateTime.of(ldt, tokyoZone)

        val (sunrise, sunset) = SolarCalculator.calculateSunriseSunsetMinutes(34.3965, 132.4596, zdt)

        // 5月の広島: 日の出はおよそ 05:00〜05:30 (300〜330分)、日没はおよそ 18:45〜19:15 (1125〜1155分)
        assertTrue("Sunrise ($sunrise min) should be around 5am", sunrise in 280..360)
        assertTrue("Sunset ($sunset min) should be around 7pm", sunset in 1100..1200)
        assertTrue("Sunrise must precede sunset", sunrise < sunset)
    }

    @Test
    fun solarCalculator_determinesDayAndNightCorrectly() {
        // 昼 12:00 JST -> 昼 (not night)
        val noon = ZonedDateTime.of(2026, 5, 1, 12, 0, 0, 0, tokyoZone).toInstant().toEpochMilli()
        assertFalse(SolarCalculator.isNight(34.3965, 132.4596, noon, tokyoZone))

        // 深夜 01:00 JST -> 夜 (is night)
        val midnight = ZonedDateTime.of(2026, 5, 1, 1, 0, 0, 0, tokyoZone).toInstant().toEpochMilli()
        assertTrue(SolarCalculator.isNight(34.3965, 132.4596, midnight, tokyoZone))

        // 夜 23:00 JST -> 夜 (is night)
        val night = ZonedDateTime.of(2026, 5, 1, 23, 0, 0, 0, tokyoZone).toInstant().toEpochMilli()
        assertTrue(SolarCalculator.isNight(34.3965, 132.4596, night, tokyoZone))
    }

    @Test
    fun solarCalculator_fallbackWhenCoordinatesNull() {
        // 座標なし: 12:00 は昼、20:00 は夜、04:00 は夜
        val noon = ZonedDateTime.of(2026, 5, 1, 12, 0, 0, 0, tokyoZone).toInstant().toEpochMilli()
        val evening = ZonedDateTime.of(2026, 5, 1, 20, 0, 0, 0, tokyoZone).toInstant().toEpochMilli()
        val earlyMorning = ZonedDateTime.of(2026, 5, 1, 4, 0, 0, 0, tokyoZone).toInstant().toEpochMilli()

        assertFalse(SolarCalculator.isNight(null, null, noon, tokyoZone))
        assertTrue(SolarCalculator.isNight(null, null, evening, tokyoZone))
        assertTrue(SolarCalculator.isNight(null, null, earlyMorning, tokyoZone))
    }

    @Test
    fun themeMode_parsingHandlesAllValues() {
        assertEquals(ThemeMode.AUTO, ThemeMode.fromKey("AUTO"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("DARK"))
        assertEquals(ThemeMode.AUTO, ThemeMode.fromKey("unknown"))
        assertEquals(ThemeMode.AUTO, ThemeMode.fromKey(null))
    }

    @Test
    fun dayNightResolver_resolvesAccordingToModeAndEnvironment() {
        val noon = ZonedDateTime.of(2026, 5, 1, 12, 0, 0, 0, tokyoZone).toInstant().toEpochMilli()
        val midnight = ZonedDateTime.of(2026, 5, 1, 0, 0, 0, 0, tokyoZone).toInstant().toEpochMilli()

        // 常にライト
        assertFalse(DayNightResolver.resolveIsDark(ThemeMode.LIGHT, isSystemInDarkTheme = true, epochMillis = midnight, zoneId = tokyoZone))
        assertFalse(DayNightResolver.resolveIsDark(ThemeMode.LIGHT, isSystemInDarkTheme = false, epochMillis = midnight, zoneId = tokyoZone))

        // 常にダーク
        assertTrue(DayNightResolver.resolveIsDark(ThemeMode.DARK, isSystemInDarkTheme = false, epochMillis = noon, zoneId = tokyoZone))
        assertTrue(DayNightResolver.resolveIsDark(ThemeMode.DARK, isSystemInDarkTheme = true, epochMillis = noon, zoneId = tokyoZone))

        // 自動: システムがダークなら無条件でダーク
        assertTrue(DayNightResolver.resolveIsDark(ThemeMode.AUTO, isSystemInDarkTheme = true, epochMillis = noon, zoneId = tokyoZone))

        // 自動: システムがライトなら太陽高度/時刻で判定
        assertFalse(DayNightResolver.resolveIsDark(ThemeMode.AUTO, isSystemInDarkTheme = false, latitude = 34.3965, longitude = 132.4596, epochMillis = noon, zoneId = tokyoZone))
        assertTrue(DayNightResolver.resolveIsDark(ThemeMode.AUTO, isSystemInDarkTheme = false, latitude = 34.3965, longitude = 132.4596, epochMillis = midnight, zoneId = tokyoZone))
    }
}
