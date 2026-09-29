package com.gorite.cyclemap.routing

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class DeadReckoningResult(
    val location: RoutePoint,
    val bearingDegrees: Double,
    val distanceAdvancedMeters: Double,
    val isExpired: Boolean,
)

object DeadReckoner {
    const val DEFAULT_MAX_DURATION_SECONDS = 15.0
    const val DEFAULT_MAX_DISTANCE_METERS = 150.0
    const val MIN_SPEED_MPS = 0.5
    private const val EARTH_RADIUS = 6_371_000.0

    /**
     * GPS精度低下・ロスト時に、直近の有効情報から現在位置を推測計算する。
     *
     * @param lastLocation 直近の有効なGPS座標
     * @param lastBearingDegrees 直近の進行方位 (度, 0..360)
     * @param speedMps 直近の有効速度 (m/s)
     * @param elapsedSeconds 最後の有効GPS受信からの経過時間 (秒)
     * @param route 案内中ルートの座標列 (未案内時は空リスト)
     * @param lastRouteProgressMeters 直近のルート進捗距離 (m) (案内時のみ)
     * @param maxDurationSeconds 推測の最大許容時間 (秒)
     * @param maxDistanceMeters 推測の最大許容距離 (m)
     */
    fun estimate(
        lastLocation: RoutePoint,
        lastBearingDegrees: Double,
        speedMps: Double,
        elapsedSeconds: Double,
        route: List<RoutePoint> = emptyList(),
        lastRouteProgressMeters: Double? = null,
        maxDurationSeconds: Double = DEFAULT_MAX_DURATION_SECONDS,
        maxDistanceMeters: Double = DEFAULT_MAX_DISTANCE_METERS,
    ): DeadReckoningResult {
        val isExpired = elapsedSeconds > maxDurationSeconds
        val effectiveElapsed = elapsedSeconds.coerceAtMost(maxDurationSeconds).coerceAtLeast(0.0)

        // 停止中 (速度0.5m/s未満) なら進まない
        if (speedMps < MIN_SPEED_MPS || effectiveElapsed <= 0.0) {
            return DeadReckoningResult(
                location = lastLocation,
                bearingDegrees = lastBearingDegrees,
                distanceAdvancedMeters = 0.0,
                isExpired = isExpired,
            )
        }

        val targetDistance = (speedMps * effectiveElapsed).coerceAtMost(maxDistanceMeters)

        // ケース1: 案内中ルートが存在し、進捗距離が渡されている場合はルート沿いに進む
        if (route.size >= 2 && lastRouteProgressMeters != null) {
            val alongRouteResult = advanceAlongRoute(
                route = route,
                startProgressMeters = lastRouteProgressMeters,
                advanceMeters = targetDistance,
                fallbackBearing = lastBearingDegrees,
            )
            if (alongRouteResult != null) {
                return DeadReckoningResult(
                    location = alongRouteResult.first,
                    bearingDegrees = alongRouteResult.second,
                    distanceAdvancedMeters = targetDistance,
                    isExpired = isExpired,
                )
            }
        }

        // ケース2: ルートなし、または進捗計算不可の場合は直線大圏コースで推測
        val straightPoint = projectStraight(lastLocation, lastBearingDegrees, targetDistance)
        return DeadReckoningResult(
            location = straightPoint,
            bearingDegrees = lastBearingDegrees,
            distanceAdvancedMeters = targetDistance,
            isExpired = isExpired,
        )
    }

    private fun advanceAlongRoute(
        route: List<RoutePoint>,
        startProgressMeters: Double,
        advanceMeters: Double,
        fallbackBearing: Double,
    ): Pair<RoutePoint, Double>? {
        val cumulative = ArrayList<Double>(route.size)
        cumulative += 0.0
        for (i in 1 until route.size) {
            cumulative += cumulative.last() + haversineMeters(
                route[i - 1].latitude, route[i - 1].longitude,
                route[i].latitude, route[i].longitude,
            )
        }
        val totalDistance = cumulative.last()
        val targetProgress = (startProgressMeters + advanceMeters).coerceIn(0.0, totalDistance)

        // 対象セグメントの特定
        for (i in 0 until route.lastIndex) {
            val segStartDist = cumulative[i]
            val segEndDist = cumulative[i + 1]
            if (targetProgress <= segEndDist || i == route.lastIndex - 1) {
                val segLen = segEndDist - segStartDist
                val fraction = if (segLen > 0.0) {
                    ((targetProgress - segStartDist) / segLen).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
                val p1 = route[i]
                val p2 = route[i + 1]
                val lat = p1.latitude + (p2.latitude - p1.latitude) * fraction
                val lon = p1.longitude + (p2.longitude - p1.longitude) * fraction
                val bearing = bearingBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude) ?: fallbackBearing
                return Pair(RoutePoint(lat, lon), bearing)
            }
        }
        return null
    }

    private fun projectStraight(start: RoutePoint, bearingDeg: Double, distanceMeters: Double): RoutePoint {
        if (distanceMeters <= 0.0) return start
        val delta = distanceMeters / EARTH_RADIUS
        val theta = Math.toRadians(bearingDeg)
        val phi1 = Math.toRadians(start.latitude)
        val lambda1 = Math.toRadians(start.longitude)

        val sinPhi2 = sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta)
        val phi2 = asin(sinPhi2.coerceIn(-1.0, 1.0))
        val y = sin(theta) * sin(delta) * cos(phi1)
        val x = cos(delta) - sin(phi1) * sin(phi2)
        val lambda2 = lambda1 + atan2(y, x)

        return RoutePoint(Math.toDegrees(phi2), Math.toDegrees(lambda2))
    }
}
