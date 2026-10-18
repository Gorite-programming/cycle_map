package com.gorite.cyclemap.fakes

import com.gorite.cyclemap.contract.BusinessStatus
import com.gorite.cyclemap.contract.EtaInfo
import com.gorite.cyclemap.contract.GuidanceStep
import com.gorite.cyclemap.contract.LaneGuidanceProvider
import com.gorite.cyclemap.contract.LaneInfo
import com.gorite.cyclemap.contract.PlaceDetail
import com.gorite.cyclemap.contract.PlaceDetailProvider
import com.gorite.cyclemap.contract.ReroutePolicy
import com.gorite.cyclemap.contract.RouteOption
import com.gorite.cyclemap.contract.RoutePlanner
import com.gorite.cyclemap.contract.TravelModeType
import com.gorite.cyclemap.contract.TurnType

class FakeRoutePlanner : RoutePlanner {
    override suspend fun planRoutes(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        mode: TravelModeType
    ): List<RouteOption> {
        val speedFactor = when (mode) {
            TravelModeType.Bicycle -> 1.0
            TravelModeType.Walk -> 0.3
            TravelModeType.Car -> 2.5
        }
        val baseDuration = 1200.0 / speedFactor

        return listOf(
            RouteOption(
                id = "route_primary",
                title = "主要ルート (推奨)",
                travelMode = mode,
                distanceMeters = 4200.0,
                durationSeconds = baseDuration,
                elevationGainMeters = 35.0,
                points = listOf(Pair(fromLat, fromLon), Pair((fromLat + toLat) / 2, (fromLon + toLon) / 2), Pair(toLat, toLon)),
                isPrimary = true
            ),
            RouteOption(
                id = "route_flat",
                title = "平坦ルート (坂少なめ)",
                travelMode = mode,
                distanceMeters = 4800.0,
                durationSeconds = baseDuration * 1.15,
                elevationGainMeters = 12.0,
                points = listOf(Pair(fromLat, fromLon), Pair(toLat, toLon)),
                isPrimary = false
            )
        )
    }
}

class FakePlaceDetailProvider : PlaceDetailProvider {
    override suspend fun getPlaceDetail(placeId: String): PlaceDetail {
        return PlaceDetail(
            id = placeId,
            name = "サンプルスポット",
            category = "コンビニ・休憩所",
            address = "広島市中区基町 1-1",
            distanceMeters = 350.0,
            lat = 34.3965,
            lon = 132.4596,
            businessStatus = BusinessStatus.Open,
            openingHoursText = "24時間営業",
            phone = "082-000-0000",
            website = "https://example.com"
        )
    }
}

class FakeLaneGuidanceProvider : LaneGuidanceProvider {
    override suspend fun getLaneHints(edgeId: Long, turnType: TurnType): List<LaneInfo> {
        return listOf(
            LaneInfo(direction = TurnType.Straight, isRecommended = false),
            LaneInfo(direction = TurnType.Left, isRecommended = true),
            LaneInfo(direction = TurnType.Right, isRecommended = false)
        )
    }
}

class FakeReroutePolicy : ReroutePolicy {
    override fun shouldReroute(
        currentLat: Double,
        currentLon: Double,
        currentHeading: Float?,
        currentRoutePoints: List<Pair<Double, Double>>,
        deviationThresholdMeters: Double
    ): Boolean {
        return false
    }
}
