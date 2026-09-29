package com.gorite.cyclemap.routing

import java.util.PriorityQueue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
        val gradePenalty = edge.gradePercent?.let { 1.0 + (kotlin.math.abs(it) * 0.02) } ?: 1.0
        return edge.distanceMeters * (multipliers[edge.roadType] ?: 1.15) * gradePenalty
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
    return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
}
