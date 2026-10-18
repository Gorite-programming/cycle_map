package com.gorite.cyclemap.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeadReckonerTest {

    @Test
    fun stopped_doesNotMove() {
        val start = RoutePoint(34.0, 131.0)
        val result = DeadReckoner.estimate(
            lastLocation = start,
            lastBearingDegrees = 90.0,
            speedMps = 0.3, // under 0.5 m/s threshold
            elapsedSeconds = 5.0,
        )

        assertEquals(start.latitude, result.location.latitude, 1e-9)
        assertEquals(start.longitude, result.location.longitude, 1e-9)
        assertEquals(0.0, result.distanceAdvancedMeters, 1e-9)
        assertFalse(result.isExpired)
    }

    @Test
    fun straight_movesAlongBearing() {
        val start = RoutePoint(34.0, 131.0)
        val speed = 5.0 // 5 m/s = 18 km/h
        val elapsed = 6.0 // 6 seconds -> 30 meters
        val bearing = 90.0 // East

        val result = DeadReckoner.estimate(
            lastLocation = start,
            lastBearingDegrees = bearing,
            speedMps = speed,
            elapsedSeconds = elapsed,
        )

        assertEquals(30.0, result.distanceAdvancedMeters, 1e-6)
        assertEquals(bearing, result.bearingDegrees, 1e-6)
        assertFalse(result.isExpired)

        val actualDistance = haversineMeters(start.latitude, start.longitude, result.location.latitude, result.location.longitude)
        assertEquals(30.0, actualDistance, 0.2) // within 20cm
        assertTrue(result.location.longitude > start.longitude)
    }

    @Test
    fun straight_capsAtMaxDuration() {
        val start = RoutePoint(34.0, 131.0)
        val speed = 5.0 // 5 m/s
        val elapsed = 20.0 // exceeds 15.0s default

        val result = DeadReckoner.estimate(
            lastLocation = start,
            lastBearingDegrees = 0.0,
            speedMps = speed,
            elapsedSeconds = elapsed,
        )

        // Capped at 15s * 5m/s = 75m
        assertEquals(75.0, result.distanceAdvancedMeters, 1e-6)
        assertTrue(result.isExpired)
    }

    @Test
    fun straight_capsAtMaxDistance() {
        val start = RoutePoint(34.0, 131.0)
        val speed = 20.0 // 20 m/s
        val elapsed = 10.0 // 200m -> capped at 150m

        val result = DeadReckoner.estimate(
            lastLocation = start,
            lastBearingDegrees = 0.0,
            speedMps = speed,
            elapsedSeconds = elapsed,
        )

        assertEquals(150.0, result.distanceAdvancedMeters, 1e-6)
        assertFalse(result.isExpired)
    }

    @Test
    fun alongRoute_followsTurnsAndUpdatesBearing() {
        // Route: (34.0, 131.0) -> East 100m -> then North 100m
        val p1 = RoutePoint(34.0, 131.0)
        // Approx 100m East: 1 deg lon at lat 34 is ~92300m -> 100m is ~0.001083 deg lon
        val p2 = RoutePoint(34.0, 131.001083)
        // Approx 100m North: 1 deg lat is ~111000m -> 100m is ~0.000901 deg lat
        val p3 = RoutePoint(34.000901, 131.001083)

        val route = listOf(p1, p2, p3)

        // Starting at 80m from start (on segment 1), advance 40m at 5 m/s (elapsed = 8.0s)
        // Target progress = 80 + 40 = 120m -> should be 20m into segment 2 (North)
        val result = DeadReckoner.estimate(
            lastLocation = RoutePoint(34.0, 131.000866),
            lastBearingDegrees = 90.0,
            speedMps = 5.0,
            elapsedSeconds = 8.0,
            route = route,
            lastRouteProgressMeters = 80.0,
        )

        assertEquals(40.0, result.distanceAdvancedMeters, 1e-6)
        // Bearing should now point North (~0 degrees)
        assertEquals(0.0, result.bearingDegrees, 1.0)
        assertTrue(result.location.latitude > 34.0)
        assertEquals(131.001083, result.location.longitude, 1e-5)
    }

    @Test
    fun alongRoute_stopsAtRouteFinish() {
        val p1 = RoutePoint(34.0, 131.0)
        val p2 = RoutePoint(34.001, 131.0)
        val route = listOf(p1, p2)
        val totalDistance = haversineMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

        // Advance 1000m on a ~111m route
        val result = DeadReckoner.estimate(
            lastLocation = p1,
            lastBearingDegrees = 0.0,
            speedMps = 15.0,
            elapsedSeconds = 10.0,
            route = route,
            lastRouteProgressMeters = 0.0,
        )

        // Should stop at p2 exactly
        assertEquals(p2.latitude, result.location.latitude, 1e-7)
        assertEquals(p2.longitude, result.location.longitude, 1e-7)
    }
}
