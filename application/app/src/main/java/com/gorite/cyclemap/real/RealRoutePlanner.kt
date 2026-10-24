package com.gorite.cyclemap.real

import com.gorite.cyclemap.contract.RouteOption
import com.gorite.cyclemap.contract.RoutePlanner
import com.gorite.cyclemap.contract.TravelModeType
import com.gorite.cyclemap.routing.AStarRouter
import com.gorite.cyclemap.routing.CyclingCostModel
import com.gorite.cyclemap.routing.EdgeCostModel
import com.gorite.cyclemap.routing.GraphEdge
import com.gorite.cyclemap.routing.LazyMappedRoadGraph
import com.gorite.cyclemap.routing.PenaltyMultiRouteFinder
import com.gorite.cyclemap.routing.RoadGraph
import com.gorite.cyclemap.routing.TravelCostModelFactory
import com.gorite.cyclemap.routing.TravelMode
import com.gorite.cyclemap.routing.TravelTimeEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 契約 [RoutePlanner] の本番 (Real) 実装。
 * - モード別コストモデル (Bicycle / Walk / Car) と A* ヒューリスティック下限 (0.90) の保証
 * - ペナルティ法による複数経路探索 (最大3本、類似度 > 70% 排除)
 * - [TravelTimeEstimator] による時間帯別所要時間・平均速度の算出
 * - 空グラフでの安全ガード
 */
class RealRoutePlanner(
    private val graphProvider: () -> LazyMappedRoadGraph?,
    private val memoryGraphProvider: (() -> RoadGraph)? = null,
    private val travelTimeEstimator: TravelTimeEstimator = TravelTimeEstimator(),
    private val elevationRepository: com.gorite.cyclemap.data.ElevationRepository? = null,
) : RoutePlanner {

    override suspend fun planRoutes(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        travelMode: TravelModeType
    ): List<RouteOption> = withContext(Dispatchers.IO) {
        val memoryGraph = memoryGraphProvider?.invoke()
        if (memoryGraph != null) {
            return@withContext planWithMemoryGraph(memoryGraph, startLat, startLon, destLat, destLon, travelMode)
        }

        val lazyGraph = graphProvider() ?: return@withContext emptyList()
        if (lazyGraph.nodeCount == 0) return@withContext emptyList()

        val startIndex = lazyGraph.nearestNodeIndex(startLat, startLon)
        val goalIndex = lazyGraph.nearestNodeIndex(destLat, destLon)
        if (startIndex < 0 || goalIndex < 0) return@withContext emptyList()

        val costMode = when (travelMode) {
            TravelModeType.Bicycle -> com.gorite.cyclemap.routing.RouteCostMode.BICYCLE
            TravelModeType.Walk -> com.gorite.cyclemap.routing.RouteCostMode.WALK
            TravelModeType.Car -> com.gorite.cyclemap.routing.RouteCostMode.CAR
        }

        // LazyMappedRoadGraph での推奨経路計算
        val primaryResult = lazyGraph.route(startIndex, goalIndex, costMode = costMode)
        if (!primaryResult.isReachable || primaryResult.nodeIds.isEmpty()) {
            return@withContext emptyList()
        }

        buildOptionsFromPrimary(
            lazyGraph = lazyGraph,
            startIndex = startIndex,
            goalIndex = goalIndex,
            primaryResult = primaryResult,
            travelMode = travelMode,
        )
    }

    fun buildOptionsFromPrimary(
        lazyGraph: LazyMappedRoadGraph,
        startIndex: Int,
        goalIndex: Int,
        primaryResult: com.gorite.cyclemap.routing.MappedRouteResult,
        travelMode: TravelModeType = TravelModeType.Bicycle,
        isCancelled: (() -> Boolean)? = null,
        maxExpandedNodes: Int = Int.MAX_VALUE,
    ): List<RouteOption> {
        if (!primaryResult.isReachable || primaryResult.nodeIds.isEmpty()) return emptyList()
        val mode = when (travelMode) {
            TravelModeType.Bicycle -> TravelMode.Bicycle
            TravelModeType.Walk -> TravelMode.Walk
            TravelModeType.Car -> TravelMode.Car
        }
        val costMode = when (travelMode) {
            TravelModeType.Bicycle -> com.gorite.cyclemap.routing.RouteCostMode.BICYCLE
            TravelModeType.Walk -> com.gorite.cyclemap.routing.RouteCostMode.WALK
            TravelModeType.Car -> com.gorite.cyclemap.routing.RouteCostMode.CAR
        }
        val now = LocalDateTime.now()
        val primaryDistance = primaryResult.totalDistanceMeters
        val primaryDuration = travelTimeEstimator.estimateSegmentSeconds(
            distanceMeters = primaryDistance,
            roadType = "secondary",
            gradePercent = null,
            mode = mode,
            dateTime = now
        )

        val primaryElevGain = calculateElevationGain(primaryResult.coordinates, primaryDistance)

        val primaryOption = RouteOption(
            id = "route_primary",
            title = when (travelMode) {
                TravelModeType.Bicycle -> "主要サイクリングルート (推奨)"
                TravelModeType.Walk -> "最短徒歩ルート (推奨)"
                TravelModeType.Car -> "主要幹線ルート (推奨)"
            },
            travelMode = travelMode,
            distanceMeters = primaryDistance,
            durationSeconds = primaryDuration,
            elevationGainMeters = primaryElevGain,
            points = primaryResult.coordinates,
            isPrimary = true
        )

        val options = mutableListOf(primaryOption)
        if (isCancelled?.invoke() == true || startIndex < 0 || goalIndex < 0) {
            return options
        }

        // 迂回/代替候補 (平坦・緩やかまたは別ルート)
        val altResult = lazyGraph.route(
            startIndex,
            goalIndex,
            heuristicMultiplier = 1.15,
            arterialPenaltyMultiplier = 1.30,
            isCancelled = isCancelled,
            maxExpandedNodes = maxExpandedNodes,
            costMode = costMode,
        )

        if (altResult.isReachable && altResult.nodeIds.isNotEmpty()) {
            val primaryNodes = primaryResult.nodeIds.toSet()
            val altNodes = altResult.nodeIds.toSet()
            val intersection = primaryNodes.intersect(altNodes).size
            val overlap = intersection.toDouble() / if (altNodes.isNotEmpty()) altNodes.size else 1

            if (overlap <= 0.70) {
                val altDistance = altResult.totalDistanceMeters
                val altDuration = travelTimeEstimator.estimateSegmentSeconds(
                    distanceMeters = altDistance,
                    roadType = "tertiary",
                    gradePercent = null,
                    mode = mode,
                    dateTime = now
                )
                val altElevGain = calculateElevationGain(altResult.coordinates, altDistance)
                options.add(
                    RouteOption(
                        id = "route_alternative",
                        title = when (travelMode) {
                            TravelModeType.Bicycle -> "裏道・平坦優先ルート"
                            TravelModeType.Walk -> "生活道路優先ルート"
                            TravelModeType.Car -> "一般道路優先ルート"
                        },
                        travelMode = travelMode,
                        distanceMeters = altDistance,
                        durationSeconds = altDuration,
                        elevationGainMeters = altElevGain,
                        points = altResult.coordinates,
                        isPrimary = false
                    )
                )
            }
        }

        return options
    }

    private fun planWithMemoryGraph(
        graph: RoadGraph,
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        travelMode: TravelModeType
    ): List<RouteOption> {
        if (graph.nodes.isEmpty()) return emptyList()

        val startNode = findNearestNode(graph, startLat, startLon) ?: return emptyList()
        val destNode = findNearestNode(graph, destLat, destLon) ?: return emptyList()

        val mode = when (travelMode) {
            TravelModeType.Bicycle -> TravelMode.Bicycle
            TravelModeType.Walk -> TravelMode.Walk
            TravelModeType.Car -> TravelMode.Car
        }

        val finder = PenaltyMultiRouteFinder(graph)
        val candidateRoutes = finder.findRoutes(startNode.id, destNode.id, mode, maxRoutes = 3)
        val now = LocalDateTime.now()

        return candidateRoutes.mapIndexed { idx, candidate ->
            val points = candidate.nodeIds.mapNotNull { nid ->
                graph.nodes[nid]?.let { it.latitude to it.longitude }
            }
            var totalDuration = 0.0
            var totalElevGain = 0.0
            for (i in 0 until candidate.nodeIds.size - 1) {
                val u = candidate.nodeIds[i]
                val v = candidate.nodeIds[i + 1]
                val edge = graph.outgoing[u]?.firstOrNull { it.to == v }
                if (edge != null) {
                    totalDuration += travelTimeEstimator.estimateSegmentSeconds(
                        distanceMeters = edge.distanceMeters,
                        roadType = edge.roadType,
                        gradePercent = edge.gradePercent,
                        mode = mode,
                        dateTime = now,
                    )
                    val g = edge.gradePercent
                    if (g != null && g > 0.0) {
                        totalElevGain += edge.distanceMeters * (g / 100.0)
                    }
                }
            }
            if (totalDuration <= 0.0) {
                totalDuration = travelTimeEstimator.estimateSegmentSeconds(
                    distanceMeters = candidate.distanceMeters,
                    roadType = "secondary",
                    gradePercent = null,
                    mode = mode,
                    dateTime = now,
                )
            }
            RouteOption(
                id = "route_$idx",
                title = if (idx == 0) "推奨ルート" else "候補ルート $idx",
                travelMode = travelMode,
                distanceMeters = candidate.distanceMeters,
                durationSeconds = totalDuration,
                elevationGainMeters = totalElevGain,
                points = points,
                isPrimary = idx == 0
            )
        }
    }

    private fun calculateElevationGain(coordinates: List<Pair<Double, Double>>, totalDistanceM: Double): Double {
        if (coordinates.size < 2 || totalDistanceM <= 0.0) return 0.0
        val profile = com.gorite.cyclemap.ui.cycling.buildRouteElevationProfile(coordinates, totalDistanceM)
        return com.gorite.cyclemap.ui.cycling.elevationGain(profile, totalDistanceM) ?: 0.0
    }

    private fun findNearestNode(graph: RoadGraph, lat: Double, lon: Double) =
        graph.nodes.values.minByOrNull { n ->
            val dLat = (n.latitude - lat) * 111320.0
            val dLon = (n.longitude - lon) * 111320.0 * cos(Math.toRadians(lat))
            dLat * dLat + dLon * dLon
        }
}
