package com.gorite.cyclemap.routing

import kotlin.math.abs

enum class TravelMode {
    Bicycle,
    Walk,
    Car
}

object WalkCostModel : EdgeCostModel {
    private val multipliers = mapOf(
        "pedestrian" to 0.90,
        "footway" to 0.90,
        "path" to 0.95,
        "steps" to 1.30,
        "residential" to 1.00,
        "living_street" to 1.00,
        "service" to 1.10,
        "tertiary" to 1.20,
        "secondary" to 1.40,
        "primary" to 1.60
    )

    override fun cost(edge: GraphEdge): Double? {
        // 歩行者は高速道路や自動車専用道路を通行不可
        if (edge.roadType == "motorway" || edge.roadType == "motorway_link" || edge.roadType == "trunk") {
            return null
        }
        val base = multipliers[edge.roadType] ?: 1.20
        val gradePenalty = edge.gradePercent?.let { grade ->
            // Tobler's Hiking function 風の勾配ペナルティ
            if (grade >= 0.0) 1.0 + grade * 0.035 else 1.0 + abs(grade) * 0.015
        } ?: 1.0
        return edge.distanceMeters * (base * gradePenalty).coerceAtLeast(0.90)
    }

    override fun minimumCostMultiplier(): Double = 0.90
}

object CarCostModel : EdgeCostModel {
    private val multipliers = mapOf(
        "motorway" to 0.90,
        "motorway_link" to 0.95,
        "trunk" to 0.95,
        "primary" to 1.00,
        "secondary" to 1.05,
        "tertiary" to 1.15,
        "unclassified" to 1.30,
        "residential" to 1.40,
        "service" to 1.60
    )

    override fun cost(edge: GraphEdge): Double? {
        // 自動車は階段・歩道・自転車道を通行不可
        if (edge.roadType == "steps" || edge.roadType == "footway" || edge.roadType == "cycleway" || edge.roadType == "pedestrian") {
            return null
        }
        val base = multipliers[edge.roadType] ?: 1.40
        return edge.distanceMeters * base.coerceAtLeast(0.90)
    }

    override fun minimumCostMultiplier(): Double = 0.90
}

object TravelCostModelFactory {
    fun forMode(mode: TravelMode): EdgeCostModel = when (mode) {
        TravelMode.Bicycle -> CyclingCostModel
        TravelMode.Walk -> WalkCostModel
        TravelMode.Car -> CarCostModel
    }
}

/**
 * ペナルティ法による複数経路探索 (最大3本)
 * plans.md §6 P1-1
 */
class PenaltyMultiRouteFinder(
    private val graph: RoadGraph
) {
    data class CandidateRoute(
        val index: Int,
        val nodeIds: List<Long>,
        val totalCost: Double,
        val distanceMeters: Double
    )

    fun findRoutes(
        startId: Long,
        goalId: Long,
        mode: TravelMode,
        maxRoutes: Int = 3
    ): List<CandidateRoute> {
        val baseCostModel = TravelCostModelFactory.forMode(mode)
        val penalizedEdges = mutableMapOf<Pair<Long, Long>, Double>()
        val results = mutableListOf<CandidateRoute>()

        for (i in 0 until maxRoutes) {
            val costModel = object : EdgeCostModel {
                override fun cost(edge: GraphEdge): Double? {
                    val baseCost = baseCostModel.cost(edge) ?: return null
                    val penalty = penalizedEdges[Pair(edge.from, edge.to)] ?: 1.0
                    return baseCost * penalty
                }

                override fun minimumCostMultiplier(): Double = baseCostModel.minimumCostMultiplier()
            }

            val router = AStarRouter(graph, costModel)
            val route = router.route(startId, goalId)
            if (!route.isReachable) break

            // 類似度判定: 既存の採用ルートと > 70% 重なる場合は棄却
            val currentNodes: Set<Long> = route.nodeIds.toSet()
            val isTooSimilar = results.any { existing ->
                val existingNodes: Set<Long> = existing.nodeIds.toSet()
                val intersection = currentNodes.intersect(existingNodes).size
                val currentSize = currentNodes.size
                val overlap = intersection.toDouble() / if (currentSize > 0) currentSize else 1
                overlap > 0.70
            }

            var totalDist = 0.0
            for (idx in 0 until route.nodeIds.size - 1) {
                val u: Long = route.nodeIds[idx]
                val v: Long = route.nodeIds[idx + 1]
                val edge = graph.outgoing[u]?.find { it.to == v }
                if (edge != null) {
                    totalDist += edge.distanceMeters
                    // 次の候補探索のため通過エッジに +30% のペナルティを課す
                    penalizedEdges[Pair(u, v)] = (penalizedEdges[Pair(u, v)] ?: 1.0) * 1.30
                }
            }

            if (!isTooSimilar || results.isEmpty()) {
                results.add(
                    CandidateRoute(
                        index = results.size,
                        nodeIds = route.nodeIds,
                        totalCost = route.totalCost,
                        distanceMeters = totalDist
                    )
                )
            }
        }

        return results
    }
}
