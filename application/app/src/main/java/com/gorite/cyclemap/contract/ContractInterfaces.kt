package com.gorite.cyclemap.contract

interface RoutePlanner {
    suspend fun planRoutes(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        mode: TravelModeType
    ): List<RouteOption>
}

interface ReroutePolicy {
    fun shouldReroute(
        currentLat: Double,
        currentLon: Double,
        currentHeading: Float?,
        currentRoutePoints: List<Pair<Double, Double>>,
        deviationThresholdMeters: Double
    ): Boolean
}

interface PlaceDetailProvider {
    suspend fun getPlaceDetail(placeId: String): PlaceDetail?
}

interface LaneGuidanceProvider {
    suspend fun getLaneHints(
        edgeId: Long,
        turnType: TurnType
    ): List<LaneInfo>
}
