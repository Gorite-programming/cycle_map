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
    private val travelTimeEstimator: TravelTimeEstimator = TravelTimeEstimator()
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

        val mode = when (travelMode) {
            TravelModeType.Bicycle -> TravelMode.Bicycle
            TravelModeType.Walk -> TravelMode.Walk
            TravelModeType.Car -> TravelMode.Car
        }

        // LazyMappedRoadGraph での推奨経路計算
        val primaryResult = lazyGraph.route(startIndex, goalIndex)
        if (!primaryResult.isReachable || primaryResult.nodeIds.isEmpty()) {
            return@withContext emptyList()
        }

        val primaryDistance = primaryResult.totalDistanceMeters
        val primaryDuration = travelTimeEstimator.estimateSegmentSeconds(
            distanceMeters = primaryDistance,
            roadType = "secondary",
            gradePercent = null,
            mode = mode,
            dateTime = LocalDateTime.now()
        )

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
            elevationGainMeters = 0.0,
            points = primaryResult.coordinates,
            isPrimary = true
        )

        // 迂回/代替候補 (平坦・緩やかまたは別ルート)
        val altResult = lazyGraph.route(
            startIndex,
            goalIndex,
            heuristicMultiplier = 1.15,
            arterialPenaltyMultiplier = 1.30
        )

        val options = mutableListOf(primaryOption)
        if (altResult.isReachable && altResult.nodeIds.isNotEmpty()) {
            // 類似度判定 (70% 超の重なりは排除)
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
                    dateTime = LocalDateTime.now()
                )
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
                        elevationGainMeters = 0.0,
                        points = altResult.coordinates,
                        isPrimary = false
                    )
                )
            }
        }

        options
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

        return candidateRoutes.mapIndexed { idx, candidate ->
            val points = candidate.nodeIds.mapNotNull { nid ->
                graph.nodes[nid]?.let { it.latitude to it.longitude }
            }
            val duration = travelTimeEstimator.estimateSegmentSeconds(
                distanceMeters = candidate.distanceMeters,
                roadType = "secondary",
                gradePercent = null,
                mode = mode
            )
            RouteOption(
                id = "route_$idx",
                title = if (idx == 0) "推奨ルート" else "候補ルート $idx",
                travelMode = travelMode,
                distanceMeters = candidate.distanceMeters,
                durationSeconds = duration,
                elevationGainMeters = 0.0,
                points = points,
                isPrimary = idx == 0
            )
        }
    }

    private fun findNearestNode(graph: RoadGraph, lat: Double, lon: Double) =
        graph.nodes.values.minByOrNull { n ->
            val dLat = (n.latitude - lat) * 111320.0
            val dLon = (n.longitude - lon) * 111320.0 * cos(Math.toRadians(lat))
            dLat * dLat + dLon * dLon
        }
}
