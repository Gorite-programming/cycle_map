package com.gorite.cyclemap

import com.gorite.cyclemap.power.CameraMotionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraMotionPolicyTest {

    @Test
    fun distanceMeters_calculatesAccurateDistance() {
        // 同一地点
        assertEquals(0.0, CameraMotionPolicy.distanceMeters(34.0, 132.0, 34.0, 132.0), 0.001)

        // 広島駅付近の微小移動 (約111m: 緯度0.001度)
        val dist = CameraMotionPolicy.distanceMeters(34.3965, 132.4596, 34.3975, 132.4596)
        assertTrue("Distance should be ~111m, was $dist", dist in 110.0..112.0)
    }

    @Test
    fun cameraMotionPolicy_stationarySuppressesJitter() {
        // 停止時 (速度 0.0 m/s): 2.5m 未満の微小なGPSドリフト揺らぎは抑止
        val decisionJitter = CameraMotionPolicy.evaluate(
            speedMps = 0.0f,
            distanceMeters = 1.8,
            elapsedMillisSinceLastUpdate = 5000L,
        )
        assertFalse("Stationary jitter under 2.5m must be suppressed", decisionJitter.shouldUpdateCamera)
        assertEquals("STATIONARY_JITTER_SUPPRESSED", decisionJitter.reason)
    }

    @Test
    fun cameraMotionPolicy_stationaryThrottlesFrequentUpdates() {
        // 停止時 (速度 0.0 m/s): 2.5m 以上の移動でも 2.5 秒未満の連続更新はデバウンス
        val decisionThrottled = CameraMotionPolicy.evaluate(
            speedMps = 0.1f,
            distanceMeters = 3.5,
            elapsedMillisSinceLastUpdate = 1200L,
        )
        assertFalse("Stationary move under 2.5s interval must be throttled", decisionThrottled.shouldUpdateCamera)
        assertEquals("STATIONARY_TIME_THROTTLED", decisionThrottled.reason)

        // 2.5 秒以上経過していれば更新許可
        val decisionAllowed = CameraMotionPolicy.evaluate(
            speedMps = 0.1f,
            distanceMeters = 3.5,
            elapsedMillisSinceLastUpdate = 2600L,
        )
        assertTrue("Stationary move after 2.5s must be updated", decisionAllowed.shouldUpdateCamera)
        assertEquals("STATIONARY_SIGNIFICANT_MOVE", decisionAllowed.reason)
        assertEquals(600, decisionAllowed.animationDurationMs)
    }

    @Test
    fun cameraMotionPolicy_lowSpeedCruisingDebouncesMicroMoves() {
        // 低速走行 (速度 1.5 m/s ≈ 5.4 km/h): 1.2m 未満かつ 1秒未満の小刻みな更新はデバウンス
        val decisionSmall = CameraMotionPolicy.evaluate(
            speedMps = 1.5f,
            distanceMeters = 0.8,
            elapsedMillisSinceLastUpdate = 600L,
        )
        assertFalse(decisionSmall.shouldUpdateCamera)
        assertEquals("LOW_SPEED_DEBOUNCED", decisionSmall.reason)

        // 低速走行で 1.2m 以上の前進または 1秒以上経過した場合はスムーズに更新
        val decisionForward = CameraMotionPolicy.evaluate(
            speedMps = 1.5f,
            distanceMeters = 2.0,
            elapsedMillisSinceLastUpdate = 600L,
        )
        assertTrue(decisionForward.shouldUpdateCamera)
        assertEquals("LOW_SPEED_CRUISING", decisionForward.reason)
        assertEquals(500, decisionForward.animationDurationMs)
    }

    @Test
    fun cameraMotionPolicy_cruisingIsResponsive() {
        // 通常巡航 (速度 5.0 m/s = 18 km/h): キビキビとカメラ追従 (400ms アニメーション)
        val decisionCruise = CameraMotionPolicy.evaluate(
            speedMps = 5.0f,
            distanceMeters = 2.5,
            elapsedMillisSinceLastUpdate = 500L,
        )
        assertTrue(decisionCruise.shouldUpdateCamera)
        assertEquals("CRUISING_RESPONSIVE", decisionCruise.reason)
        assertEquals(400, decisionCruise.animationDurationMs)
    }

    @Test
    fun cameraMotionPolicy_nullOrNegativeSpeedHandledGracefully() {
        // speed が null または負値の場合は 0.0f (停止) として安全に処理
        val decisionNull = CameraMotionPolicy.evaluate(
            speedMps = null,
            distanceMeters = 1.0,
            elapsedMillisSinceLastUpdate = 1000L,
        )
        assertFalse(decisionNull.shouldUpdateCamera)

        val decisionNegative = CameraMotionPolicy.evaluate(
            speedMps = -1.0f,
            distanceMeters = 1.0,
            elapsedMillisSinceLastUpdate = 1000L,
        )
        assertFalse(decisionNegative.shouldUpdateCamera)
    }
}
