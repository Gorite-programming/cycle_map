package com.gorite.cyclemap.routing

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
)

enum class ManeuverType {
    RIGHT,
    LEFT,
    U_TURN,
}

data class Maneuver(
    val type: ManeuverType,
    val distanceFromStartMeters: Double,
)

data class RouteProgress(
    val distanceFromStartMeters: Double,
    val routeDistanceMeters: Double,
    val distanceToRouteMeters: Double,
    val nextManeuver: Maneuver?,
    val distanceToNextManeuverMeters: Double?,
)

fun extractManeuvers(
    points: List<RoutePoint>,
    turnThresholdDegrees: Double = 20.0,
    uTurnThresholdDegrees: Double = 150.0,
): List<Maneuver> {
    if (points.size < 3) return emptyList()

    val cumulative = cumulativeDistances(points)
    return buildList {
        for (index in 1 until points.lastIndex) {
            val incoming = bearingBetween(points[index - 1], points[index]) ?: continue
            val outgoing = bearingBetween(points[index], points[index + 1]) ?: continue
            val change = normalizeBearingChange(outgoing - incoming)
            val magnitude = abs(change)
            if (magnitude < turnThresholdDegrees) continue

            val type = when {
                magnitude >= uTurnThresholdDegrees -> ManeuverType.U_TURN
                change > 0.0 -> ManeuverType.RIGHT
                else -> ManeuverType.LEFT
            }
            add(Maneuver(type, cumulative[index]))
        }
    }
}

fun calculateRouteProgress(
    location: RoutePoint,
    route: List<RoutePoint>,
    maneuvers: List<Maneuver> = extractManeuvers(route),
): RouteProgress? {
    if (route.isEmpty()) return null
    if (route.size == 1) {
        return RouteProgress(0.0, 0.0, haversineMeters(location, route[0]), maneuvers.firstOrNull(), null)
    }

    val cumulative = cumulativeDistances(route)
    var bestDistance = Double.POSITIVE_INFINITY
    var bestProgress = 0.0

    for (index in 0 until route.lastIndex) {
        val start = route[index]
        val end = route[index + 1]
        val segmentLength = cumulative[index + 1] - cumulative[index]
        if (segmentLength == 0.0) continue

        val projection = projectOntoSegment(location, start, end)
        if (projection.distanceMeters < bestDistance) {
            bestDistance = projection.distanceMeters
            bestProgress = cumulative[index] + segmentLength * projection.fraction
        }
    }

    val routeDistance = cumulative.last()
    val progress = bestProgress.coerceIn(0.0, routeDistance)
    val next = maneuvers.firstOrNull { it.distanceFromStartMeters > progress }
    return RouteProgress(
        distanceFromStartMeters = progress,
        routeDistanceMeters = routeDistance,
        distanceToRouteMeters = bestDistance,
        nextManeuver = next,
        distanceToNextManeuverMeters = next?.let { (it.distanceFromStartMeters - progress).coerceAtLeast(0.0) },
    )
}

private data class Projection(val fraction: Double, val distanceMeters: Double)

private fun projectOntoSegment(point: RoutePoint, start: RoutePoint, end: RoutePoint): Projection {
    val referenceLatitude = Math.toRadians(point.latitude)
    val metersPerLatitude = 6_371_000.0 * Math.PI / 180.0
    val metersPerLongitude = metersPerLatitude * cos(referenceLatitude)
    fun x(p: RoutePoint) = (p.longitude - point.longitude) * metersPerLongitude
    fun y(p: RoutePoint) = (p.latitude - point.latitude) * metersPerLatitude

    val startX = x(start)
    val startY = y(start)
    val deltaX = x(end) - startX
    val deltaY = y(end) - startY
    val lengthSquared = deltaX * deltaX + deltaY * deltaY
    val fraction = if (lengthSquared == 0.0) 0.0 else {
        ((-startX * deltaX) + (-startY * deltaY)) / lengthSquared
    }.coerceIn(0.0, 1.0)
    val projected = RoutePoint(
        latitude = start.latitude + (end.latitude - start.latitude) * fraction,
        longitude = start.longitude + (end.longitude - start.longitude) * fraction,
    )
    return Projection(fraction, haversineMeters(point, projected))
}

private fun cumulativeDistances(points: List<RoutePoint>): List<Double> {
    val result = ArrayList<Double>(points.size)
    result += 0.0
    for (index in 1 until points.size) {
        result += result.last() + haversineMeters(points[index - 1], points[index])
    }
    return result
}

private fun bearingBetween(from: RoutePoint, to: RoutePoint): Double? {
    val latitude1 = Math.toRadians(from.latitude)
    val latitude2 = Math.toRadians(to.latitude)
    val deltaLongitude = Math.toRadians(to.longitude - from.longitude)
    val y = sin(deltaLongitude) * cos(latitude2)
    val x = cos(latitude1) * sin(latitude2) - sin(latitude1) * cos(latitude2) * cos(deltaLongitude)
    if (x == 0.0 && y == 0.0) return null
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

private fun normalizeBearingChange(change: Double): Double = ((change + 540.0) % 360.0) - 180.0

private fun haversineMeters(from: RoutePoint, to: RoutePoint): Double =
    haversineMeters(from.latitude, from.longitude, to.latitude, to.longitude)