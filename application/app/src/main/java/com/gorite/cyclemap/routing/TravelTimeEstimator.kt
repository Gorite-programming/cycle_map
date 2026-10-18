package com.gorite.cyclemap.routing

import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.math.abs

class TravelTimeEstimator(
    private val trafficMultiplier: Double = 1.0 // オンライン交通補正 (未接続・失敗時は 1.0)
) {
    data class SpeedProfile(
        val baseSpeedMps: Double,
        val minSpeedMps: Double,
        val maxSpeedMps: Double
    )

    private val modeProfiles = mapOf(
        TravelMode.Bicycle to SpeedProfile(baseSpeedMps = 5.0, minSpeedMps = 2.0, maxSpeedMps = 8.5), // 18 km/h
        TravelMode.Walk to SpeedProfile(baseSpeedMps = 1.33, minSpeedMps = 0.8, maxSpeedMps = 1.8),   // 4.8 km/h
        TravelMode.Car to SpeedProfile(baseSpeedMps = 11.1, minSpeedMps = 4.0, maxSpeedMps = 25.0)    // 40 km/h
    )

    /**
     * 道路セグメントの走行秒数を算出
     */
    fun estimateSegmentSeconds(
        distanceMeters: Double,
        roadType: String,
        gradePercent: Double?,
        mode: TravelMode,
        dateTime: LocalDateTime = LocalDateTime.now()
    ): Double {
        if (distanceMeters <= 0.0) return 0.0
        val profile = modeProfiles[mode] ?: modeProfiles.getValue(TravelMode.Bicycle)

        val roadFactor = roadTypeFactor(roadType, mode)
        val gradeFactor = gradeFactor(gradePercent, mode)
        val timeFactor = timeOfDayFactor(dateTime, mode)
        val trafficFactor = trafficMultiplier.coerceIn(0.5, 2.0)

        val effectiveSpeedMps = (profile.baseSpeedMps * roadFactor * gradeFactor * timeFactor * trafficFactor)
            .coerceIn(profile.minSpeedMps, profile.maxSpeedMps)

        return distanceMeters / effectiveSpeedMps
    }

    /**
     * 全セグメントの推定所要時間 (秒)
     */
    fun estimateTotalSeconds(
        edges: List<GraphEdge>,
        mode: TravelMode,
        trafficLightCount: Int = 0,
        dateTime: LocalDateTime = LocalDateTime.now()
    ): Double {
        var totalSec = 0.0
        for (edge in edges) {
            totalSec += estimateSegmentSeconds(
                distanceMeters = edge.distanceMeters,
                roadType = edge.roadType,
                gradePercent = edge.gradePercent,
                mode = mode,
                dateTime = dateTime
            )
        }
        // 信号待ちペナルティ: 1箇所あたり平均15秒
        val signalPenaltySeconds = when (mode) {
            TravelMode.Bicycle -> 12.0 * trafficLightCount
            TravelMode.Walk -> 15.0 * trafficLightCount
            TravelMode.Car -> 20.0 * trafficLightCount
        }
        return totalSec + signalPenaltySeconds
    }

    private fun roadTypeFactor(roadType: String, mode: TravelMode): Double = when (mode) {
        TravelMode.Bicycle -> when (roadType) {
            "cycleway" -> 1.10
            "primary" -> 1.05
            "secondary" -> 1.00
            "tertiary" -> 0.95
            "residential" -> 0.90
            "track", "path" -> 0.70
            else -> 0.90
        }
        TravelMode.Walk -> when (roadType) {
            "pedestrian", "footway" -> 1.05
            "steps" -> 0.65
            else -> 1.00
        }
        TravelMode.Car -> when (roadType) {
            "motorway" -> 2.00 // 高速道路は倍速
            "trunk" -> 1.50
            "primary" -> 1.20
            "secondary" -> 1.00
            "residential" -> 0.75
            "service" -> 0.50
            else -> 0.80
        }
    }

    private fun gradeFactor(grade: Double?, mode: TravelMode): Double {
        if (grade == null) return 1.0
        return when (mode) {
            TravelMode.Bicycle -> when {
                grade > 0.0 -> (1.0 - (grade * 0.04)).coerceAtLeast(0.40) // 上りは減速
                grade in -4.0..0.0 -> (1.0 + (abs(grade) * 0.03)).coerceAtMost(1.30) // 緩やかな下りは加速
                else -> 1.05 // 急な下りはブレーキ
            }
            TravelMode.Walk -> when {
                grade > 0.0 -> (1.0 - (grade * 0.025)).coerceAtLeast(0.60)
                else -> (1.0 + (abs(grade) * 0.01)).coerceAtMost(1.10)
            }
            TravelMode.Car -> 1.0 // 自動車は勾配による速度影響が極めて軽微
        }
    }

    private fun timeOfDayFactor(dateTime: LocalDateTime, mode: TravelMode): Double {
        val hour = dateTime.hour
        val isWeekend = dateTime.dayOfWeek == DayOfWeek.SATURDAY || dateTime.dayOfWeek == DayOfWeek.SUNDAY

        return if (isWeekend) {
            if (hour in 10..17) 0.95 else 1.05
        } else {
            when (hour) {
                in 7..9 -> if (mode == TravelMode.Car) 0.75 else 0.88 // 通勤ラッシュ混雑
                in 17..19 -> if (mode == TravelMode.Car) 0.70 else 0.85 // 帰宅ラッシュ混雑
                in 21..24, in 0..5 -> 1.10 // 深夜空いている
                else -> 1.00
            }
        }
    }
}
