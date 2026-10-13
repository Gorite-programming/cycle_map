package com.gorite.cyclemap.routing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * RerouteSuccessTest: plans.md §6 P1-3, §8
 * 逸脱シミュレーションで再探索成功率 ≥ 98%、レイテンシ < 3秒を検証
 */
class RerouteSuccessTest {

    @Test
    fun testRerouteSuccessRateAndLatencyUnder3Seconds() = runBlocking {
        val totalTrials = 100
        var successCount = 0

        val elapsed = measureTimeMillis {
            for (trial in 1..totalTrials) {
                var rerouted = false
                val manager = AdaptiveRerouteManager(
                    scope = CoroutineScope(Dispatchers.Default),
                    rerouteDistanceThreshold = 30.0,
                    consecutiveSamples = 3,
                    routerAction = { current, goal ->
                        rerouted = true
                        listOf(current, current + 1, goal)
                    }
                )

                manager.setInitialRoute(listOf(1L, 2L, 3L))

                // 3回連続逸脱 (40m)
                for (s in 1..3) {
                    manager.onLocationUpdate(
                        distanceToRouteMeters = 40.0,
                        currentSpeedMps = 5.0f,
                        currentNodeId = 100L + trial,
                        goalNodeId = 3L
                    )
                }

                manager.currentRerouteJob?.join()
                if (rerouted && !manager.state.value.isOffRoute) {
                    successCount++
                }
            }
        }

        val successRate = (successCount.toDouble() / totalTrials) * 100.0
        assertTrue("Reroute success rate must be >= 98%, was $successRate%", successRate >= 98.0)
        assertTrue("Total 100 trials took $elapsed ms (under 3000ms target)", elapsed < 3000)
    }
}
