package com.gorite.cyclemap.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationStatsTest {
    private fun progress(fromStart: Double, total: Double = 1_000.0) = RouteProgress(
        distanceFromStartMeters = fromStart,
        routeDistanceMeters = total,
        distanceToRouteMeters = 5.0,
        nextManeuver = null,
        distanceToNextManeuverMeters = null,
    )

    @Test
    fun computesRemainingAndEtaFromAverageSpeed() {
        // 平均速度ベース: 400m / 120s = 3.33m/s → 残り600m = 180s
        val stats = computeNavigationStats(
            progress = progress(fromStart = 400.0),
            smoothedSpeedMps = 9.0, // 平均が確定すれば瞬間速度は無視される
            navStartElapsedRealtimeMs = 0L,
            nowElapsedRealtimeMs = 120_000L,
            nowEpochMillis = 1_000_000L,
        )
        assertNotNull(stats)
        assertEquals(600.0, stats.remainingMeters, 1e-9)
        assertEquals(120.0, stats.elapsedSeconds, 1e-9)
        assertEquals(400.0 / 120.0, stats.effectiveSpeedMps, 1e-9)
        assertEquals(180.0, stats.durationRemainingSeconds, 1e-9)
        assertEquals(1_000_000L + 180_000L, stats.etaEpochMillis)
        assertFalse(stats.isArrived)
    }

    @Test
    fun usesInstantSpeedOnlyBeforeAverageIsReady() {
        // 経過10秒未満は平均が未確定のため瞬間速度を使う
        val stats = computeNavigationStats(
            progress = progress(fromStart = 10.0),
            smoothedSpeedMps = 5.0,
            navStartElapsedRealtimeMs = 0L,
            nowElapsedRealtimeMs = 5_000L,
            nowEpochMillis = 1_000_000L,
        )
        assertNotNull(stats)
        assertEquals(5.0, stats.effectiveSpeedMps, 1e-9)
    }

    @Test
    fun holdsEffectiveSpeedWhenStoppedWithLastSpeed() {
        // 信号待ちなどで停止中(0.0m/s)でも、直前の実効速度(4.0m/s)をホールドしてETA急変を防ぐ
        val stats = computeNavigationStats(
            progress = progress(fromStart = 400.0),
            smoothedSpeedMps = 0.0,
            navStartElapsedRealtimeMs = 0L,
            nowElapsedRealtimeMs = 150_000L,
            nowEpochMillis = 1_000_000L,
            lastEffectiveSpeedMps = 4.0,
        )
        assertNotNull(stats)
        assertEquals(4.0, stats.effectiveSpeedMps, 1e-9)
        assertEquals(600.0 / 4.0, stats.durationRemainingSeconds, 1e-9)
    }

    @Test
    fun fallsBackToDefaultSpeedWhenAverageTooLow() {
        // 長時間停止で平均が極端に低く、直前速度もない場合はデフォルト巡航速度に倒す
        val stats = computeNavigationStats(
            progress = progress(fromStart = 10.0),
            smoothedSpeedMps = 0.0,
            navStartElapsedRealtimeMs = 0L,
            nowElapsedRealtimeMs = 600_000L,
            nowEpochMillis = 0L,
        )
        assertNotNull(stats)
        assertEquals(15_000.0 / 3_600.0, stats.effectiveSpeedMps, 1e-9)
        assertTrue(stats.durationRemainingSeconds.isFinite())
        assertNotNull(stats.etaEpochMillis)
    }

    @Test
    fun detectsArrivalWithinRadius() {
        val stats = computeNavigationStats(
            progress = progress(fromStart = 985.0),
            smoothedSpeedMps = 4.0,
            navStartElapsedRealtimeMs = 0L,
            nowElapsedRealtimeMs = 60_000L,
            nowEpochMillis = 0L,
            arrivalRadiusMeters = 30.0,
        )
        assertNotNull(stats)
        assertTrue(stats.isArrived)
        assertEquals(0.0, stats.durationRemainingSeconds, 1e-9)
    }

    @Test
    fun returnsNullWithoutProgress() {
        assertNull(
            computeNavigationStats(
                progress = null,
                smoothedSpeedMps = 5.0,
                navStartElapsedRealtimeMs = 0L,
                nowElapsedRealtimeMs = 0L,
                nowEpochMillis = 0L,
            ),
        )
    }
}

class OffRouteDetectorTest {
    @Test
    fun firesAfterConsecutiveBreaches() {
        val detector = OffRouteDetector(offRouteDistanceMeters = 50.0, requiredConsecutive = 3)
        assertFalse(detector.update(60.0))
        assertFalse(detector.update(70.0))
        assertTrue(detector.update(80.0))
        assertTrue(detector.isOffRoute)
    }

    @Test
    fun ignoresMomentaryGpsJump() {
        val detector = OffRouteDetector(offRouteDistanceMeters = 50.0, requiredConsecutive = 3)
        assertFalse(detector.update(200.0))
        assertFalse(detector.update(5.0))
        assertFalse(detector.update(200.0))
        assertFalse(detector.isOffRoute)
    }

    @Test
    fun recoversWithHysteresis() {
        val detector = OffRouteDetector(
            offRouteDistanceMeters = 50.0,
            requiredConsecutive = 2,
            recoverDistanceMeters = 20.0,
        )
        detector.update(60.0)
        assertTrue(detector.update(60.0))
        // 中間距離では確定状態を維持する
        assertTrue(detector.update(30.0))
        assertFalse(detector.update(10.0))
    }

    @Test
    fun resetClearsState() {
        val detector = OffRouteDetector(requiredConsecutive = 1)
        assertTrue(detector.update(999.0))
        detector.reset()
        assertFalse(detector.isOffRoute)
        assertFalse(detector.update(1.0))
    }
}
