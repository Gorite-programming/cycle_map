package com.gorite.cyclemap.real

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealReroutePolicyTest {

    private val policy = RealReroutePolicy()

    // 経度方向に伸びる直線ルート (約 1km)
    private val straightRoute = listOf(
        34.390 to 132.450,
        34.390 to 132.460
    )

    @Test
    fun shouldReroute_withinThreshold_returnsFalse() {
        // ルート線分上から約 10m 離れた位置 (緯度方向に約 0.00009 度 = 約 10m)
        val lat = 34.39009
        val lon = 132.455
        val reroute = policy.shouldReroute(
            currentLat = lat,
            currentLon = lon,
            currentHeading = 90f,
            currentRoutePoints = straightRoute,
            deviationThresholdMeters = 30.0
        )
        assertFalse("10m の軽微なズレではリルートしないこと", reroute)
    }

    @Test
    fun shouldReroute_beyond30m_returnsTrue() {
        // ルート線分上から約 40m 離れた位置 (緯度方向に約 0.00036 度 = 約 40m)
        val lat = 34.39036
        val lon = 132.455
        val reroute = policy.shouldReroute(
            currentLat = lat,
            currentLon = lon,
            currentHeading = 90f,
            currentRoutePoints = straightRoute,
            deviationThresholdMeters = 30.0
        )
        assertTrue("30m 以上の逸脱でリルート判定となること", reroute)
    }

    @Test
    fun shouldReroute_reverseHeading_triggersReroute() {
        // ルート進行方向は東 (90度)。現在のヘディングが西 (270度 = 逆走)
        // 距離は 20m 離れている場合
        val lat = 34.39018 // 約 20m 離れ
        val lon = 132.455
        val reroute = policy.shouldReroute(
            currentLat = lat,
            currentLon = lon,
            currentHeading = 270f, // 180度逆走
            currentRoutePoints = straightRoute,
            deviationThresholdMeters = 30.0
        )
        assertTrue("逆走かつ中程度の乖離でリルート判定となること", reroute)
    }

    @Test
    fun shouldReroute_insufficientPoints_returnsFalse() {
        val reroute = policy.shouldReroute(
            currentLat = 34.390,
            currentLon = 132.450,
            currentHeading = 0f,
            currentRoutePoints = listOf(34.390 to 132.450),
            deviationThresholdMeters = 30.0
        )
        assertFalse("ルート点数が2未満の時は false を返すこと", reroute)
    }
}
