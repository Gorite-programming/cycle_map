package com.gorite.cyclemap.weather

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRepositoryTest {

    @Test
    fun isFresh_returnsTrueWithinThreeHours() {
        val now = System.currentTimeMillis()
        val data = WeatherForecastData(
            officeCode = "350000",
            class10Code = "350020",
            amedasCode = "51106",
            targetAreaName = "山口県 (中部)",
            weatherText = "晴れ",
            weatherCode = "100",
            currentPop = 0,
            currentTemp = 20.0,
            reportDatetime = "2026-10-16T17:00:00+09:00",
            fetchedAtMillis = now - (2 * 60 * 60 * 1000L) // 2時間前
        )
        assertTrue("2時間前はFresh", data.isFresh())
    }

    @Test
    fun isFresh_returnsFalseAfterThreeHours() {
        val now = System.currentTimeMillis()
        val data = WeatherForecastData(
            officeCode = "350000",
            class10Code = "350020",
            amedasCode = "51106",
            targetAreaName = "山口県 (中部)",
            weatherText = "晴れ",
            weatherCode = "100",
            currentPop = 0,
            currentTemp = 20.0,
            reportDatetime = "2026-10-16T17:00:00+09:00",
            fetchedAtMillis = now - (3 * 60 * 60 * 1000L + 1000L) // 3時間1秒前
        )
        assertFalse("3時間経過後はStale", data.isFresh())
    }

    @Test
    fun weatherCategory_mappings() {
        assertTrue(WeatherCategory.fromCode("100") == WeatherCategory.SUNNY)
        assertTrue(WeatherCategory.fromCode("101") == WeatherCategory.SUNNY)
        assertTrue(WeatherCategory.fromCode("200") == WeatherCategory.CLOUDY)
        assertTrue(WeatherCategory.fromCode("300") == WeatherCategory.RAINY)
        assertTrue(WeatherCategory.fromCode("400") == WeatherCategory.SNOWY)
        assertTrue(WeatherCategory.fromCode("999") == WeatherCategory.UNKNOWN)
    }
}
