package com.gorite.cyclemap.power

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class CameraMotionDecision(
    val shouldUpdateCamera: Boolean,
    val animationDurationMs: Int,
    val reason: String,
)

/**
 * 停止時 (速度 0km/h) や低速巡航時における無駄なカメラ再計算・描画更新のデバウンス制御ポリシー。
 * Snapdragon 888 などの発熱とバッテリー浪費を抑制する。
 */
object CameraMotionPolicy {

    const val STATIONARY_SPEED_THRESHOLD_MPS = 0.5f // 約 1.8 km/h 以下を停止判定
    const val CRUISING_SPEED_THRESHOLD_MPS = 3.0f   // 約 10.8 km/h 以上を通常巡航判定

    const val MIN_STATIONARY_DISTANCE_METERS = 2.5   // 停止時のGPS揺らぎフィルタ (2.5m未満はスキップ)
    const val MIN_STATIONARY_INTERVAL_MS = 2500L     // 停止時の最小更新間隔 (2.5秒)

    const val MIN_LOW_SPEED_DISTANCE_METERS = 1.2    // 低速時の最小移動距離 (1.2m未満はデバウンス)
    const val MIN_LOW_SPEED_INTERVAL_MS = 1000L      // 低速時の最小更新間隔 (1秒)

    /**
     * 2点間の直線距離 (メートル) を計算 (Haversine法)
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // 地球半径 (m)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val clampedA = a.coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(clampedA), sqrt(1.0 - clampedA))
        return r * c
    }

    /**
     * カメラ更新を実行すべきかどうかを判定
     */
    fun evaluate(
        speedMps: Float?,
        distanceMeters: Double,
        elapsedMillisSinceLastUpdate: Long,
    ): CameraMotionDecision {
        val speed = speedMps?.takeIf { it >= 0f } ?: 0f

        return when {
            // 停止時 (0 km/h 〜 1.8 km/h)
            speed < STATIONARY_SPEED_THRESHOLD_MPS -> {
                if (distanceMeters < MIN_STATIONARY_DISTANCE_METERS) {
                    CameraMotionDecision(
                        shouldUpdateCamera = false,
                        animationDurationMs = 0,
                        reason = "STATIONARY_JITTER_SUPPRESSED",
                    )
                } else if (elapsedMillisSinceLastUpdate < MIN_STATIONARY_INTERVAL_MS) {
                    CameraMotionDecision(
                        shouldUpdateCamera = false,
                        animationDurationMs = 0,
                        reason = "STATIONARY_TIME_THROTTLED",
                    )
                } else {
                    CameraMotionDecision(
                        shouldUpdateCamera = true,
                        animationDurationMs = 600,
                        reason = "STATIONARY_SIGNIFICANT_MOVE",
                    )
                }
            }
            // 低速巡航時 (1.8 km/h 〜 10.8 km/h)
            speed < CRUISING_SPEED_THRESHOLD_MPS -> {
                if (distanceMeters < MIN_LOW_SPEED_DISTANCE_METERS && elapsedMillisSinceLastUpdate < MIN_LOW_SPEED_INTERVAL_MS) {
                    CameraMotionDecision(
                        shouldUpdateCamera = false,
                        animationDurationMs = 0,
                        reason = "LOW_SPEED_DEBOUNCED",
                    )
                } else {
                    CameraMotionDecision(
                        shouldUpdateCamera = true,
                        animationDurationMs = 500,
                        reason = "LOW_SPEED_CRUISING",
                    )
                }
            }
            // 通常・高速巡航時 (10.8 km/h 以上)
            else -> {
                CameraMotionDecision(
                    shouldUpdateCamera = true,
                    animationDurationMs = 400,
                    reason = "CRUISING_RESPONSIVE",
                )
            }
        }
    }
}
