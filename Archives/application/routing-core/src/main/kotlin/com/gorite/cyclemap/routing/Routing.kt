package com.gorite.cyclemap.routing

import java.util.PriorityQueue
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class GraphNode(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
)

data class GraphEdge(
    val from: Long,
    val to: Long,
    val distanceMeters: Double,
    val roadType: String = "unknown",
    val oneWay: Boolean = false,
    val gradePercent: Double? = null,
    val costMultiplier: Double = 1.0,
) {
    init {
        require(distanceMeters >= 0.0) { "distanceMeters must be non-negative" }
        require(costMultiplier > 0.0) { "costMultiplier must be positive" }
    }

    val cost: Double get() = distanceMeters * costMultiplier
}

class RoadGraph(
    val nodes: Map<Long, GraphNode>,
    val outgoing: Map<Long, List<GraphEdge>>,
) {
    fun node(id: Long): GraphNode = nodes[id] ?: error("Unknown node: $id")
}

data class RouteResult(
    val nodeIds: List<Long>,
    val totalCost: Double,
) {
    val isReachable: Boolean get() = nodeIds.isNotEmpty()
}

fun interface EdgeCostModel {
    fun cost(edge: GraphEdge): Double?

    /** Admissible lower bound for cost per geometric meter. */
    fun minimumCostMultiplier(): Double = 0.0
}

object DistanceCostModel : EdgeCostModel {
    override fun cost(edge: GraphEdge): Double = edge.cost
    override fun minimumCostMultiplier(): Double = 0.0
}

object CyclingCostModel : EdgeCostModel {
    private val multipliers = mapOf(
        "primary" to 0.90,
        "secondary" to 0.95,
        "tertiary" to 1.00,
        "residential" to 1.20,
        "unclassified" to 1.25,
        "service" to 1.35,
        "track" to 1.60,
        "path" to 1.80,
    )

    override fun cost(edge: GraphEdge): Double? {
        if (edge.roadType == "motorway" || edge.roadType == "motorway_link") return null
        val gradePenalty = edge.gradePercent?.let { calculateGradePenalty(it) } ?: 1.0
        val baseMultiplier = multipliers[edge.roadType] ?: 1.15
        val effectiveMultiplier = (baseMultiplier * gradePenalty).coerceAtLeast(0.90)
        return edge.distanceMeters * effectiveMultiplier
    }

    private fun calculateGradePenalty(grade: Double): Double = when {
        grade >= 0.0 -> 1.0 + (grade * 0.02) + (grade * grade * 0.002) // 緩やかな上りは+2%、急勾配は二次関数的に増加
        grade >= -4.0 -> (1.0 + grade * 0.02).coerceAtLeast(0.95) // 緩やかな下りは軽快（最大-5%）
        else -> 1.0 + kotlin.math.abs(grade + 4.0) * 0.03 // -4%を超える急坂下りはブレーキ減速・危険度ペナルティ
    }

    override fun minimumCostMultiplier(): Double = 0.90
}

class AStarRouter(
    private val graph: RoadGraph,
    private val edgeCostModel: EdgeCostModel = DistanceCostModel,
) {
    var lastExpandedNodes: Int = 0
        private set

    private data class QueueEntry(val nodeId: Long, val estimatedTotal: Double) : Comparable<QueueEntry> {
        override fun compareTo(other: QueueEntry): Int = estimatedTotal.compareTo(other.estimatedTotal)
    }

    fun route(startId: Long, goalId: Long): RouteResult {
        lastExpandedNodes = 0
        graph.node(startId)
        graph.node(goalId)
        if (startId == goalId) return RouteResult(listOf(startId), 0.0)

        val open = PriorityQueue<QueueEntry>()
        val cameFrom = mutableMapOf<Long, Long>()
        val costSoFar = mutableMapOf(startId to 0.0)
        open += QueueEntry(startId, heuristic(startId, goalId))

        while (open.isNotEmpty()) {
            val current = open.remove().nodeId
            lastExpandedNodes++
            if (current == goalId) return buildResult(cameFrom, costSoFar.getValue(goalId), goalId)

            for (edge in graph.outgoing[current].orEmpty()) {
                val edgeCost = edgeCostModel.cost(edge) ?: continue
                val newCost = costSoFar.getValue(current) + edgeCost
                if (newCost < costSoFar.getOrDefault(edge.to, Double.POSITIVE_INFINITY)) {
                    costSoFar[edge.to] = newCost
                    cameFrom[edge.to] = current
                    open += QueueEntry(edge.to, newCost + heuristic(edge.to, goalId))
                }
            }
        }
        return RouteResult(emptyList(), Double.POSITIVE_INFINITY)
    }

    private fun buildResult(cameFrom: Map<Long, Long>, cost: Double, goalId: Long): RouteResult {
        val path = mutableListOf(goalId)
        var current = goalId
        while (cameFrom.containsKey(current)) {
            current = cameFrom.getValue(current)
            path += current
        }
        path.reverse()
        return RouteResult(path, cost)
    }

    private fun heuristic(fromId: Long, toId: Long): Double {
        val from = graph.node(fromId)
        val to = graph.node(toId)
        return haversineMeters(from.latitude, from.longitude, to.latitude, to.longitude) *
            edgeCostModel.minimumCostMultiplier()
    }
}

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    val clampedA = a.coerceIn(0.0, 1.0)
    return earthRadius * 2 * atan2(sqrt(clampedA), sqrt(1.0 - clampedA))
}

/**
 * WGS84回転楕円体に基づく高精度測地距離計算 (Lambert-Andoyer法)。
 * 反復を行わないため未収束リスクがなく、日本付近で誤差0.01%以下の高精度をO(1)で算出する。
 * ルート確定後の総距離表示や進捗距離の最終集計に使用する。
 */
fun geodesicDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    if (lat1 == lat2 && lon1 == lon2) return 0.0
    val a = 6_378_137.0 // WGS84 長半径 (m)
    val f = 1.0 / 298.257223563 // WGS84 扁平率
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val lambda1 = Math.toRadians(lon1)
    val lambda2 = Math.toRadians(lon2)

    val deltaLambda = lambda2 - lambda1
    val u1 = atan((1.0 - f) * tan(phi1))
    val u2 = atan((1.0 - f) * tan(phi2))

    val sinU1 = sin(u1); val cosU1 = cos(u1)
    val sinU2 = sin(u2); val cosU2 = cos(u2)
    val cosDeltaLambda = cos(deltaLambda)

    val cosD = (sinU1 * sinU2 + cosU1 * cosU2 * cosDeltaLambda).coerceIn(-1.0, 1.0)
    val d = acos(cosD)
    if (d == 0.0) return 0.0

    val sinD = sin(d)
    if (sinD == 0.0) return 0.0

    val p = (u1 + u2) / 2.0
    val q = (u2 - u1) / 2.0
    val sinP = sin(p); val cosP = cos(p)
    val sinQ = sin(q); val cosQ = cos(q)

    val cosHalfD = cos(d / 2.0)
    val sinHalfD = sin(d / 2.0)
    val denomX = (cosHalfD * cosHalfD).coerceAtLeast(1e-12)
    val denomY = (sinHalfD * sinHalfD).coerceAtLeast(1e-12)

    val x = (d - sinD) * (sinP * sinP * cosQ * cosQ) / denomX
    val y = (d + sinD) * (cosP * cosP * sinQ * sinQ) / denomY

    return a * (d - (f / 2.0) * (x + y))
}
