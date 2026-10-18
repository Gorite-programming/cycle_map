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

    @Test
    fun offRouteDetector_detectsLargeDeviationFromPath() {
        val route = listOf(
            point(34.0000, 131.0000),
            point(34.0010, 131.0000),
        )
        // 離脱点 (約100m以上離れた点)
        val farPoint = point(34.0005, 131.0010)
        val progress = calculateRouteProgress(farPoint, route)
        assertNotNull(progress)
        assertTrue(progress.distanceToRouteMeters > 50.0)
    }

    @Test
    fun extractManeuvers_slightTurnThreshold() {
        // 約 30 度の曲がり
        val points = listOf(
            point(0.0000, 0.0000),
            point(0.0010, 0.0000),
            point(0.0020, 0.0005),
        )
        val maneuvers = extractManeuvers(points)
        assertEquals(1, maneuvers.size)
        assertEquals(ManeuverType.RIGHT, maneuvers[0].type)
    }

    @Test
    fun calculateRouteProgress_atExactGoalNode() {
        val route = listOf(
            point(0.0000, 0.0000),
            point(0.0010, 0.0000),
        )
        val maneuvers = extractManeuvers(route)
        val progress = calculateRouteProgress(point(0.0010, 0.0000), route, maneuvers)
        assertNotNull(progress)
        assertTrue(progress.distanceToRouteMeters < 0.1)
        assertNull(progress.nextManeuver)
    }

    private fun point(latitude: Double, longitude: Double) = RoutePoint(latitude, longitude)
}