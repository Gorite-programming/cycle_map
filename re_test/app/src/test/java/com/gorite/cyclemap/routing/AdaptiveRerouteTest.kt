package com.gorite.cyclemap.routing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveRerouteTest {

    @Test
    fun triggersRerouteAfterThreeConsecutiveDeviations() = runBlocking {
        var routeComputed = false
        val manager = AdaptiveRerouteManager(
            scope = CoroutineScope(Dispatchers.Default),
            rerouteDistanceThreshold = 30.0,
            consecutiveSamples = 3,
            routerAction = { _, _ ->
                routeComputed = true
                listOf(10L, 20L, 30L)
            }
        )

        manager.setInitialRoute(listOf(1L, 2L, 3L))

        // 1st sample: 35m off track
        manager.onLocationUpdate(distanceToRouteMeters = 35.0, currentSpeedMps = 4.0f, currentNodeId = 10L, goalNodeId = 30L)
        assertFalse(manager.state.value.isOffRoute)

        // 2nd sample: 38m off track
        manager.onLocationUpdate(distanceToRouteMeters = 38.0, currentSpeedMps = 4.0f, currentNodeId = 10L, goalNodeId = 30L)
        assertFalse(manager.state.value.isOffRoute)

        // 3rd sample: 40m off track -> OffRoute triggered!
        manager.onLocationUpdate(distanceToRouteMeters = 40.0, currentSpeedMps = 4.0f, currentNodeId = 10L, goalNodeId = 30L)
        assertTrue(manager.state.value.isOffRoute)
        assertTrue(manager.state.value.isRerouting)
        // Previous route should still be preserved while reroute is computing (plans.md §6 P1-3)
        assertEquals(listOf(1L, 2L, 3L), manager.state.value.currentRouteNodeIds)

        // Await background calculation
        manager.currentRerouteJob?.join()

        assertTrue(routeComputed)
        assertFalse(manager.state.value.isRerouting)
        assertFalse(manager.state.value.isOffRoute)
        assertEquals(listOf(10L, 20L, 30L), manager.state.value.currentRouteNodeIds)
    }

    @Test
    fun cancelsPreviousRerouteJobWhenNewTriggerArrives() = runBlocking {
        var firstJobStarted = false
        var secondJobCompleted = false

        val manager = AdaptiveRerouteManager(
            scope = CoroutineScope(Dispatchers.Default),
            rerouteDistanceThreshold = 30.0,
            consecutiveSamples = 1, // 1回で即発火
            routerAction = { startId, _ ->
                if (startId == 10L) {
                    firstJobStarted = true
                    kotlinx.coroutines.delay(500) // 遅い旧ジョブ
                    listOf(10L, 20L)
                } else {
                    secondJobCompleted = true
                    listOf(startId, 99L)
                }
            }
        )

        manager.setInitialRoute(listOf(1L, 2L))

        // 1回目のトリガー (startId = 10L)
        manager.onLocationUpdate(distanceToRouteMeters = 35.0, currentSpeedMps = 4.0f, currentNodeId = 10L, goalNodeId = 30L)
        val firstJob = manager.currentRerouteJob

        // 直後に2回目のトリガー (startId = 50L) -> 1回目のジョブはキャンセルされるべき
        manager.onLocationUpdate(distanceToRouteMeters = 40.0, currentSpeedMps = 4.0f, currentNodeId = 50L, goalNodeId = 30L)
        val secondJob = manager.currentRerouteJob

        secondJob?.join()

        assertTrue(secondJobCompleted)
        assertTrue(firstJob?.isCancelled == true)
        assertEquals(listOf(50L, 99L), manager.state.value.currentRouteNodeIds)
    }
}
