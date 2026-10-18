package com.gorite.cyclemap.ui.theme

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoField
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 画面および地図のテーマモード設定。
 * 「自動（昼夜連動）/ 常にライト / 常にダーク」
 */
enum class ThemeMode(val label: String) {
    AUTO("自動（昼夜連動）"),
    LIGHT("常にライト"),
    DARK("常にダーク");

    companion object {
        fun fromKey(key: String?): ThemeMode {
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: AUTO
        }
    }
}

/**
 * 完全オフライン用の太陽高度・日出・日没時刻計算機。
 * NOAA の近似計算アルゴリズムに基づき、GPS緯度経度から日出・日没時刻を計算する。
 */
object SolarCalculator {

    /**
     * 指定された日時・座標における日出・日没時刻（午前0時からの経過分: 0..1440）を計算する。
     * @return Pair(sunriseMinutes, sunsetMinutes) 例: Pair(360, 1080) -> 06:00, 18:00
     */
    fun calculateSunriseSunsetMinutes(
        latitude: Double,
        longitude: Double,
        zonedDateTime: ZonedDateTime,
    ): Pair<Int, Int> {
        val dayOfYear = zonedDateTime.get(ChronoField.DAY_OF_YEAR)
        val zoneOffsetHours = zonedDateTime.offset.totalSeconds / 3600.0

        val radPerDeg = Math.PI / 180.0
        val degPerRad = 180.0 / Math.PI

        // 太陽赤緯 (declination) の概算 (度)
        val declinationDeg = -23.44 * cos(2.0 * Math.PI / 365.0 * (dayOfYear + 10))
        val declinationRad = declinationDeg * radPerDeg
        val safeLat = latitude.coerceIn(-89.99, 89.99)
        val latRad = safeLat * radPerDeg

        // 日出・日没時の太陽天頂角 (-0.833度: 太陽見かけ視半径 + 大気差屈折)
        val zenithRad = 90.833 * radPerDeg
        val cosHourAngle = (cos(zenithRad) - sin(latRad) * sin(declinationRad)) /
                (cos(latRad) * cos(declinationRad))

        if (cosHourAngle > 1.0) {
            // 極夜: 太陽が昇らない
            return Pair(-1, -1)
        }
        if (cosHourAngle < -1.0) {
            // 白夜: 太陽が沈まない
            return Pair(0, 1440)
        }

        val hourAngleDeg = acos(cosHourAngle) * degPerRad
        val halfDayHours = hourAngleDeg / 15.0

        // 均時差 (Equation of Time, 分) の補正
        val b = 2.0 * Math.PI / 365.0 * (dayOfYear - 81)
        val eotMinutes = 9.87 * sin(2.0 * b) - 7.53 * cos(b) - 1.5 * sin(b)

        // 南中時刻 (現地標準時での時間)
        val solarNoonHours = 12.0 - ((longitude - 15.0 * zoneOffsetHours) / 15.0) - (eotMinutes / 60.0)

        val sunriseHours = solarNoonHours - halfDayHours
        val sunsetHours = solarNoonHours + halfDayHours

        val sunriseMinutes = ((sunriseHours * 60.0).roundToInt()).coerceIn(0, 1440)
        val sunsetMinutes = ((sunsetHours * 60.0).roundToInt()).coerceIn(0, 1440)

        return Pair(sunriseMinutes, sunsetMinutes)
    }

    /**
     * 指定された日時・座標が夜間（日没〜日の出の間）かどうかを判定する。
     * 座標が null の場合は、標準的な夜間時間帯（18:00〜06:00）でフォールバック判定する。
     */
    fun isNight(
        latitude: Double?,
        longitude: Double?,
        epochMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zoneId)
        val currentMinutesOfDay = zonedDateTime.hour * 60 + zonedDateTime.minute

        if (latitude == null || longitude == null) {
            // フォールバック: 18:00〜06:00 を夜間判定
            return currentMinutesOfDay < 6 * 60 || currentMinutesOfDay >= 18 * 60
        }

        val (sunrise, sunset) = calculateSunriseSunsetMinutes(latitude, longitude, zonedDateTime)
        if (sunrise == -1 && sunset == -1) return true // 極夜
        if (sunrise == 0 && sunset == 1440) return false // 白夜

        return currentMinutesOfDay < sunrise || currentMinutesOfDay >= sunset
    }
}

/**
 * テーマ設定、システムダークモード、現在地座標、日時から夜間（ダークテーマ）か否かを決定するリゾルバ。
 */
object DayNightResolver {
    fun resolveIsDark(
        mode: ThemeMode,
        isSystemInDarkTheme: Boolean,
        latitude: Double? = null,
        longitude: Double? = null,
        epochMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        return when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AUTO -> {
                if (isSystemInDarkTheme) {
                    true
                } else {
                    SolarCalculator.isNight(latitude, longitude, epochMillis, zoneId)
                }
            }
        }
    }
}
