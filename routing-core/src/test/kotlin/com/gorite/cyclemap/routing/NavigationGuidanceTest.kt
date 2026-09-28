package com.gorite.cyclemap.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationGuidanceTest {
    @Test
    fun extractsClockwiseTurnAsRight() {
        val points = listOf(
            point(0.0000, 0.0000),
            point(0.0010, 0.0000),
            point(0.0010, 0.0010),
        )

        val maneuvers = extractManeuvers(points)

        assertEquals(listOf(ManeuverType.RIGHT), maneuvers.map { it.type })
    }

    @Test
    fun extractsRightLeftAndUTurnWithTwentyDegreeThreshold() {
        val points = listOf(
            point(0.0000, 0.0000),
            point(0.0000, 0.0010),
            point(0.0010, 0.0010),
            point(0.0010, 0.0000),
            point(0.0010, 0.0010),
        )

        val maneuvers = extractManeuvers(points)

        assertEquals(3, maneuvers.size)
        assertEquals(ManeuverType.LEFT, maneuvers[0].type)
        assertEquals(ManeuverType.LEFT, maneuvers[1].type)
        assertEquals(ManeuverType.U_TURN, maneuvers[2].type)
    }

    @Test
    fun ignoresSmallBearingChanges() {
        val points = listOf(
            point(0.0000, 0.0000),
            point(0.0000, 0.0010),
            point(0.0001, 0.0020),
        )

        assertTrue(extractManeuvers(points).isEmpty())
    }

    @Test
    fun snapsLocationToNearestSegmentAndFindsNextManeuver() {
        val route = listOf(
            point(0.0000, 0.0000),
            point(0.0000, 0.0010),
            point(0.0010, 0.0010),
        )
        val maneuvers = extractManeuvers(route)
        val progress = calculateRouteProgress(point(0.0000, 0.0005), route, maneuvers)

        assertNotNull(progress)
        assertTrue(progress.distanceFromStartMeters in 50.0..60.0)
        assertTrue(progress.distanceToRouteMeters < 1.0)
        assertEquals(ManeuverType.LEFT, progress.nextManeuver?.type)
        assertTrue(progress.distanceToNextManeuverMeters!! in 50.0..60.0)
    }

    @Test
    fun handlesEmptyAndSinglePointRoutes() {
        assertNull(calculateRouteProgress(point(0.0, 0.0), emptyList()))

        val progress = calculateRouteProgress(point(0.0, 0.001), listOf(point(0.0, 0.0)))
        assertNotNull(progress)
        assertEquals(0.0, progress.routeDistanceMeters)
        assertNull(progress.nextManeuver)
    }

    private fun point(latitude: Double, longitude: Double) = RoutePoint(latitude, longitude)
}