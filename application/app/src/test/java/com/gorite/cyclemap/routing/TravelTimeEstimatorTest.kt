package com.gorite.cyclemap.routing

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TravelTimeEstimatorTest {

    @Test
    fun estimatesBicycleTravelTimeWithinRealisticBounds() {
        val estimator = TravelTimeEstimator()
        // 10km flat secondary road for bicycle at 18km/h (5 m/s) should take ~2000s (~33 min)
        val edges = listOf(
            GraphEdge(from = 1, to = 2, distanceMeters = 10000.0, roadType = "secondary", gradePercent = 0.0)
        )
        val seconds = estimator.estimateTotalSeconds(
            edges = edges,
            mode = TravelMode.Bicycle,
            trafficLightCount = 5,
            dateTime = LocalDateTime.of(2026, 10, 14, 14, 0) // Wednesday 14:00 (midday)
        )

        // 10000m / 5.0m/s = 2000s + 5*12s = 2060s (~34.3 min)
        // Error within ±15% of 2000s
        assertTrue("Expected ~2060s, got $seconds", seconds in 1750.0..2350.0)
    }

    @Test
    fun uphillGradeIncreasesTravelTime() {
        val estimator = TravelTimeEstimator()
        val flatSec = estimator.estimateSegmentSeconds(
            distanceMeters = 1000.0,
            roadType = "secondary",
            gradePercent = 0.0,
            mode = TravelMode.Bicycle
        )
        val steepUphillSec = estimator.estimateSegmentSeconds(
            distanceMeters = 1000.0,
            roadType = "secondary",
            gradePercent = 7.0,
            mode = TravelMode.Bicycle
        )
        assertTrue(steepUphillSec > flatSec)
    }

    @Test
    fun walkVsCarRatioIsRealistic() {
        val estimator = TravelTimeEstimator()
        val edge = GraphEdge(from = 1, to = 2, distanceMeters = 3000.0, roadType = "secondary")
        val walkSec = estimator.estimateTotalSeconds(listOf(edge), TravelMode.Walk)
        val carSec = estimator.estimateTotalSeconds(listOf(edge), TravelMode.Car)

        // Walking 3km should take ~37 min (2200s), Car should take ~4-5 min (270s)
        assertTrue(walkSec > carSec * 4.0)
    }
}
