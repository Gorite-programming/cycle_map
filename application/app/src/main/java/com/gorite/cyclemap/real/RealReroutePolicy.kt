package com.gorite.cyclemap.real

import com.gorite.cyclemap.contract.ReroutePolicy
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 契約 [ReroutePolicy] の本番 (Real) 実装。
 * - 30m 逸脱閾値によるリルート要否判定 (plans.md §6 P1-3)
 * - ポリライン線分に対する直交最短距離の高速計算
 * - 150度以上の逆走検知によるリルート誘発
 */
class RealReroutePolicy : ReroutePolicy {

    override fun shouldReroute(
        currentLat: Double,
        currentLon: Double,
        currentHeading: Float?,
        currentRoutePoints: List<Pair<Double, Double>>,
        deviationThresholdMeters: Double
    ): Boolean {
        if (currentRoutePoints.size < 2) return false

        // 1. ルート線分への最短距離を探索
        var minDistanceMeters = Double.MAX_VALUE
        var nearestSegmentIndex = 0

        for (i in 0 until currentRoutePoints.size - 1) {
            val p1 = currentRoutePoints[i]
            val p2 = currentRoutePoints[i + 1]
            val dist = distanceToSegmentMeters(currentLat, currentLon, p1.first, p1.second, p2.first, p2.second)
            if (dist < minDistanceMeters) {
                minDistanceMeters = dist
                nearestSegmentIndex = i
            }
        }

        // 30m 以上離れている場合は即座にリルート対象
        if (minDistanceMeters >= deviationThresholdMeters) {
            return true
        }

        // 2. 進行方向の逆走検知 (ヘディングが150度以上逆向き)
        if (currentHeading != null && currentHeading.isFinite()) {
            val p1 = currentRoutePoints[nearestSegmentIndex]
            val p2 = currentRoutePoints[nearestSegmentIndex + 1]
            val segmentBearing = calculateBearingDegrees(p1.first, p1.second, p2.first, p2.second)
            val angleDiff = abs(normalizeAngle(currentHeading.toDouble() - segmentBearing))
            if (angleDiff > 150.0 && minDistanceMeters > 15.0) {
                // 逆走かつルート中心線から15m以上離れている場合はリルート
                return true
            }
        }

        return false
    }

    private fun distanceToSegmentMeters(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): Double {
        val cosLat = cos(Math.toRadians(pLat))
        val degToMeters = 111320.0

        val px = pLon * degToMeters * cosLat
        val py = pLat * degToMeters
        val ax = aLon * degToMeters * cosLat
        val ay = aLat * degToMeters
        val bx = bLon * degToMeters * cosLat
        val by = bLat * degToMeters

        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        if (lengthSq <= 0.0001) {
            val ex = px - ax
            val ey = py - ay
            return sqrt(ex * ex + ey * ey)
        }

        // 線分上の投影パラメータ t を [0, 1] でクランプ
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0.0, 1.0)
        val projX = ax + t * dx
        val projY = ay + t * dy

        val qx = px - projX
        val qy = py - projY
        return sqrt(qx * qx + qy * qy)
    }

    private fun calculateBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    private fun normalizeAngle(angle: Double): Double {
        var a = angle % 360.0
        if (a > 180.0) a -= 360.0
        if (a < -180.0) a += 360.0
        return a
    }
}
