package com.gorite.cyclemap.routing

import java.util.PriorityQueue
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.system.measureNanoTime

enum class HsaMode {
    O,
    B,
    F,
}

data class HsaModeDefaults(
    val beamWidth: Int,
    val maxCandidates: Int,
    val minSegmentKm: Double,
    val targetSegmentKm: Double,
    val maxSegmentKm: Double,
    val boundaryRadiusKm: Double,
    val boundaryCandidates: Int,
    val refineRadiusKm: Double,
    val maxWorkers: Int,
)

data class HsaOptions(
    val mode: HsaMode,
    val beamWidth: Int,
    val maxCandidates: Int,
    val minSegmentKm: Double,
    val targetSegmentKm: Double,
    val maxSegmentKm: Double,
    val boundaryRadiusKm: Double,
    val boundaryCandidates: Int,
    val refineRadiusKm: Double,
    val maxWorkers: Int,
    val maxSegments: Int = 32,
    val maxExpansionsPerSegment: Int = 500_000,
    val maxTimePerSegmentMs: Long = 2_000,
    val cMin: Double = 1.0,
    val prepassHeuristicMultiplier: Double = 1.6,
    val prepassArterialOnly: Boolean = false,
    val prepassArterialPenaltyMultiplier: Double = 1.0,
) {
    init {
        require(beamWidth >= 1) { "beamWidth must be positive" }
        require(maxCandidates >= 1) { "maxCandidates must be positive" }
        require(beamWidth <= maxCandidates) { "beamWidth must not exceed maxCandidates" }
        require(minSegmentKm > 0.0) { "minSegmentKm must be positive" }
        require(minSegmentKm <= targetSegmentKm) { "minSegmentKm must not exceed targetSegmentKm" }
        require(targetSegmentKm <= maxSegmentKm) { "targetSegmentKm must not exceed maxSegmentKm" }
        require(boundaryRadiusKm >= 0.0) { "boundaryRadiusKm must not be negative" }
        require(boundaryCandidates >= 1) { "boundaryCandidates must be positive" }
        require(refineRadiusKm >= 0.0) { "refineRadiusKm must not be negative" }
        require(maxWorkers >= 1) { "maxWorkers must be positive" }
        require(maxSegments >= 1) { "maxSegments must be positive" }
        require(maxExpansionsPerSegment >= 1) { "maxExpansionsPerSegment must be positive" }
        require(maxTimePerSegmentMs >= 1L) { "maxTimePerSegmentMs must be positive" }
        require(cMin > 0.0) { "cMin must be positive" }
        require(prepassHeuristicMultiplier >= 1.0) { "prepassHeuristicMultiplier must be at least 1.0" }
    }

    companion object {
        fun fromMode(
            mode: HsaMode,
            beamWidth: Int? = null,
            maxCandidates: Int? = null,
            minSegmentKm: Double? = null,
            targetSegmentKm: Double? = null,
            maxSegmentKm: Double? = null,
            boundaryRadiusKm: Double? = null,
            boundaryCandidates: Int? = null,
            refineRadiusKm: Double? = null,
            maxWorkers: Int? = null,
            maxSegments: Int = 32,
            maxExpansionsPerSegment: Int = 500_000,
            maxTimePerSegmentMs: Long = 2_000,
            cMin: Double = 1.0,
            prepassHeuristicMultiplier: Double = 1.6,
            prepassArterialOnly: Boolean = false,
            prepassArterialPenaltyMultiplier: Double = 1.0,
        ): HsaOptions {
            val defaults = when (mode) {
                HsaMode.O -> HsaModeDefaults(5, 8, 10.0, 45.0, 70.0, 2.0, 5, 2.0, 3)
                HsaMode.B -> HsaModeDefaults(4, 6, 15.0, 45.0, 75.0, 2.0, 3, 1.5, 3)
                HsaMode.F -> HsaModeDefaults(2, 3, 20.0, 60.0, 90.0, 2.0, 1, 0.5, 2)
            }
            return HsaOptions(
                mode = mode,
                beamWidth = beamWidth ?: defaults.beamWidth,
                maxCandidates = maxCandidates ?: defaults.maxCandidates,
                minSegmentKm = minSegmentKm ?: defaults.minSegmentKm,
                targetSegmentKm = targetSegmentKm ?: defaults.targetSegmentKm,
                maxSegmentKm = maxSegmentKm ?: defaults.maxSegmentKm,
                boundaryRadiusKm = boundaryRadiusKm ?: defaults.boundaryRadiusKm,
                boundaryCandidates = boundaryCandidates ?: defaults.boundaryCandidates,
                refineRadiusKm = refineRadiusKm ?: defaults.refineRadiusKm,
                maxWorkers = maxWorkers ?: defaults.maxWorkers,
                maxSegments = maxSegments,
                maxExpansionsPerSegment = maxExpansionsPerSegment,
                maxTimePerSegmentMs = maxTimePerSegmentMs,
                cMin = cMin,
                prepassHeuristicMultiplier = prepassHeuristicMultiplier,
                prepassArterialOnly = prepassArterialOnly,
                prepassArterialPenaltyMultiplier = prepassArterialPenaltyMultiplier,
            )
        }
    }
}

enum class RoadLevel {
    OTHER,
    MUNICIPAL_WIDE,
    PREFECTURAL,
    NATIONAL,
}

object RoadHierarchy {
    fun level(edge: GraphEdge): RoadLevel = when (edge.roadType) {
        "motorway", "motorway_link", "trunk", "primary" -> RoadLevel.NATIONAL
        "secondary" -> RoadLevel.PREFECTURAL
        "tertiary" -> RoadLevel.MUNICIPAL_WIDE
        else -> RoadLevel.OTHER
    }

    fun penalty(level: RoadLevel): Double = when (level) {
        RoadLevel.NATIONAL -> 0.00
        RoadLevel.PREFECTURAL -> 0.03
        RoadLevel.MUNICIPAL_WIDE -> 0.06
        RoadLevel.OTHER -> 0.12
    }
}

private class SkeletonCostModel(private val baseModel: EdgeCostModel) : EdgeCostModel {
    override fun cost(edge: GraphEdge): Double? {
        val base = baseModel.cost(edge) ?: return null
        return base + edge.distanceMeters * RoadHierarchy.penalty(RoadHierarchy.level(edge))
    }

    override fun minimumCostMultiplier(): Double = baseModel.minimumCostMultiplier()
}

private class SkeletonGenerator(
    private val graph: RoadGraph,
    private val baseModel: EdgeCostModel,
) {
    fun generate(startId: Long, goalId: Long, options: HsaOptions): List<Long>? {
        val skeleton = AStarRouter(graph, SkeletonCostModel(baseModel)).route(startId, goalId)
        if (!skeleton.isReachable) return null
        val segmentCount = segmentCount(skeleton.nodeIds, options)
        if (segmentCount <= 1) return listOf(startId, goalId)

        val cumulative = cumulativeDistances(skeleton.nodeIds)
        val total = cumulative.last()
        return buildList {
            add(startId)
            for (index in 1 until segmentCount) {
                val target = total * index / segmentCount
                val nodeIndex = cumulative.indexOfFirst { it >= target }
                    .coerceAtLeast(1)
                    .coerceAtMost(skeleton.nodeIds.lastIndex)
                add(skeleton.nodeIds[nodeIndex])
            }
            add(goalId)
        }.distinct()
    }

    private fun segmentCount(nodeIds: List<Long>, options: HsaOptions): Int {
        val distanceKm = cumulativeDistances(nodeIds).last() / 1_000.0
        if (distanceKm <= options.minSegmentKm) return 1
        return (ceil(distanceKm / options.targetSegmentKm).toInt() + 2)
            .coerceIn(1, options.maxSegments)
    }

    private fun cumulativeDistances(nodeIds: List<Long>): List<Double> {
        val result = ArrayList<Double>(nodeIds.size)
        result += 0.0
        for (index in 1 until nodeIds.size) {
            val from = graph.node(nodeIds[index - 1])
            val to = graph.node(nodeIds[index])
            result += result.last() + haversineMeters(from, to)
        }
        return result
    }
}

data class HsaSearchMetrics(
    val elapsedMillis: Long,
    val expandedNodes: Int,
    val segmentCount: Int,
    val usedFallback: Boolean,
    val fallbackReason: String? = null,
)

data class RoutingComparison(
    val aStar: RouteResult,
    val hsa: RouteResult,
    val aStarElapsedMillis: Long,
    val hsaMetrics: HsaSearchMetrics,
    val distanceDifferenceMeters: Double,
    val costDifferencePercent: Double,
)

/**
 * Phase 1 HSA* implementation for comparison with the existing plain A*.
 *
 * It keeps the graph read-only, plans distance-based segments dynamically, and
 * uses a bounded A* inside each segment. If an automatically selected boundary
 * cannot be connected, it falls back to a complete A* so the API remains safe
 * while the later skeleton/beam/boundary-repair phases are added.
 */
class HsaStarRouter(
    private val graph: RoadGraph,
    private val edgeCostModel: EdgeCostModel = DistanceCostModel,
) {
    @Volatile
    var lastMetrics: HsaSearchMetrics = HsaSearchMetrics(0L, 0, 0, false)
        private set

    fun route(startId: Long, goalId: Long, options: HsaOptions = HsaOptions.fromMode(HsaMode.B)): RouteResult {
        graph.node(startId)
        graph.node(goalId)
        var result = RouteResult(emptyList(), Double.POSITIVE_INFINITY)
        var expandedNodes = 0
        var usedFallback = false
        var fallbackReason: String? = null
        val startedAt = System.nanoTime()

        if (startId == goalId) {
            result = RouteResult(listOf(startId), 0.0)
        } else {
            val baseline = AStarRouter(graph, edgeCostModel).route(startId, goalId)
            if (!baseline.isReachable) {
                lastMetrics = HsaSearchMetrics(0L, 0, 0, false)
                return baseline
            }
            val anchors = SkeletonGenerator(graph, edgeCostModel).generate(startId, goalId, options)
            val segmentCount = anchors?.size?.minus(1) ?: 0
            val routeParts = ArrayList<List<Long>>()
            var current = startId

            if (anchors == null) {
                usedFallback = true
                fallbackReason = "skeleton-unreachable"
            }
            for (next in anchors?.drop(1).orEmpty()) {
                val segment = boundedAStar(current, next, options)
                expandedNodes += segment.expandedNodes
                if (segment.result == null) {
                    usedFallback = true
                    fallbackReason = "segment-limit"
                    break
                }
                routeParts += segment.result.nodeIds
                current = next
            }

            if (!usedFallback && current == goalId) {
                val nodeIds = routeParts.flatMapIndexed { index, part ->
                    if (index == 0) part else part.drop(1)
                }
                val candidateCost = pathCost(nodeIds)
                if (candidateCost > baseline.totalCost * 1.05) {
                    usedFallback = true
                    fallbackReason = "quality-gate"
                    result = baseline
                } else {
                    result = RouteResult(nodeIds, candidateCost)
                }
            } else {
                usedFallback = true
                result = baseline
            }
            lastMetrics = HsaSearchMetrics(
                elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L,
                expandedNodes = expandedNodes,
                segmentCount = segmentCount,
                usedFallback = usedFallback,
                fallbackReason = fallbackReason,
            )
            return result
        }

        lastMetrics = HsaSearchMetrics(
            elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L,
            expandedNodes = 0,
            segmentCount = 0,
            usedFallback = false,
        )
        return result
    }

    private data class BoundedResult(val result: RouteResult?, val expandedNodes: Int)

    private data class QueueEntry(val nodeId: Long, val estimatedCost: Double) : Comparable<QueueEntry> {
        override fun compareTo(other: QueueEntry): Int = estimatedCost.compareTo(other.estimatedCost)
    }

    private fun boundedAStar(startId: Long, goalId: Long, options: HsaOptions): BoundedResult {
        val startedAt = System.nanoTime()
        val open = PriorityQueue<QueueEntry>()
        val cameFrom = mutableMapOf<Long, Long>()
        val costSoFar = mutableMapOf(startId to 0.0)
        var expanded = 0
        open += QueueEntry(startId, heuristic(startId, goalId, options.cMin))

        while (open.isNotEmpty()) {
            if (expanded >= options.maxExpansionsPerSegment || elapsedMillis(startedAt) > options.maxTimePerSegmentMs) {
                return BoundedResult(null, expanded)
            }
            val current = open.remove().nodeId
            expanded++
            if (current == goalId) {
                val path = buildPath(cameFrom, goalId)
                return BoundedResult(RouteResult(path, costSoFar.getValue(goalId)), expanded)
            }
            for (edge in graph.outgoing[current].orEmpty()) {
                val edgeCost = edgeCostModel.cost(edge) ?: continue
                val newCost = costSoFar.getValue(current) + edgeCost
                if (newCost < costSoFar.getOrDefault(edge.to, Double.POSITIVE_INFINITY)) {
                    costSoFar[edge.to] = newCost
                    cameFrom[edge.to] = current
                    open += QueueEntry(edge.to, newCost + heuristic(edge.to, goalId, options.cMin))
                }
            }
        }
        return BoundedResult(null, expanded)
    }

    private fun heuristic(fromId: Long, toId: Long, cMin: Double): Double =
        haversineMeters(graph.node(fromId), graph.node(toId)) *
            minOf(cMin, edgeCostModel.minimumCostMultiplier())

    private fun buildPath(cameFrom: Map<Long, Long>, goalId: Long): List<Long> {
        val path = mutableListOf(goalId)
        var current = goalId
        while (cameFrom.containsKey(current)) {
            current = cameFrom.getValue(current)
            path += current
        }
        path.reverse()
        return path
    }

    private fun pathCost(nodeIds: List<Long>): Double {
        var total = 0.0
        for (index in 0 until nodeIds.lastIndex) {
            val edge = graph.outgoing[nodeIds[index]].orEmpty()
                .filter { it.to == nodeIds[index + 1] }
                .minByOrNull { edgeCostModel.cost(it) ?: Double.POSITIVE_INFINITY }
                ?: return Double.POSITIVE_INFINITY
            total += edgeCostModel.cost(edge) ?: return Double.POSITIVE_INFINITY
        }
        return total
    }

    private fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000L
}

fun compareAStarAndHsa(
    graph: RoadGraph,
    startId: Long,
    goalId: Long,
    options: HsaOptions = HsaOptions.fromMode(HsaMode.B),
    edgeCostModel: EdgeCostModel = DistanceCostModel,
): RoutingComparison {
    lateinit var aStarResult: RouteResult
    val aStarElapsed = measureNanoTime {
        aStarResult = AStarRouter(graph, edgeCostModel).route(startId, goalId)
    } / 1_000_000L
    val hsaRouter = HsaStarRouter(graph, edgeCostModel)
    val hsaResult = hsaRouter.route(startId, goalId, options)
    val aStarDistance = routeDistance(graph, aStarResult.nodeIds)
    val hsaDistance = routeDistance(graph, hsaResult.nodeIds)
    val costDifferencePercent = if (aStarResult.totalCost.isFinite() && aStarResult.totalCost != 0.0) {
        ((hsaResult.totalCost - aStarResult.totalCost) / aStarResult.totalCost) * 100.0
    } else {
        Double.NaN
    }
    return RoutingComparison(
        aStar = aStarResult,
        hsa = hsaResult,
        aStarElapsedMillis = aStarElapsed,
        hsaMetrics = hsaRouter.lastMetrics,
        distanceDifferenceMeters = hsaDistance - aStarDistance,
        costDifferencePercent = costDifferencePercent,
    )
}

private fun routeDistance(graph: RoadGraph, nodeIds: List<Long>): Double {
    var total = 0.0
    for (index in 0 until nodeIds.lastIndex) {
        total += graph.outgoing[nodeIds[index]].orEmpty()
            .filter { it.to == nodeIds[index + 1] }
            .minOfOrNull { it.distanceMeters } ?: return Double.POSITIVE_INFINITY
    }
    return total
}

private fun haversineMeters(from: GraphNode, to: GraphNode): Double =
    haversineMeters(from.latitude, from.longitude, to.latitude, to.longitude)

data class MappedRoutingBenchmarkResult(
    val algorithm: String,
    val elapsedSeconds: Double,
    val peakHeapIncreaseBytes: Long,
    val route: MappedRouteResult,
    val segmentCount: Int = 0,
    val usedFallback: Boolean = false,
    val fallbackReason: String? = null,
    val expandedNodes: Int = 0,
)

/** HSA phase 1 adapter for the mmap graph used by the Android app. */
class MappedHsaRouter(private val graph: LazyMappedRoadGraph) {
    var lastSegmentCount: Int = 0
        private set
    var lastUsedFallback: Boolean = false
        private set
    var lastFallbackReason: String? = null
        private set
    var lastExpandedNodes: Int = 0
        private set

    fun route(startIndex: Int, goalIndex: Int, options: HsaOptions = HsaOptions.fromMode(HsaMode.B)): MappedRouteResult {
        var expandedNodes = 0
        lastSegmentCount = 0
        lastUsedFallback = false
        lastFallbackReason = null
        if (startIndex == goalIndex) {
            lastExpandedNodes = 0
            return graph.route(startIndex, goalIndex)
        }

        val prepass = graph.route(
            startIndex,
            goalIndex,
            heuristicMultiplier = options.prepassHeuristicMultiplier,
            arterialOnly = options.prepassArterialOnly,
            arterialPenaltyMultiplier = options.prepassArterialPenaltyMultiplier,
        )
        expandedNodes += graph.lastExpandedNodes
        if (!prepass.isReachable) {
            return fallback(startIndex, goalIndex, expandedNodes, "prepass-unreachable")
        }
        val anchors = selectMappedAnchors(prepass, options)
        lastSegmentCount = anchors.size - 1

        val parts = ArrayList<MappedRouteResult>()
        for ((from, to) in anchors.zipWithNext()) {
            val part = graph.route(from, to, segmentBounds(prepass, from, to, options))
            expandedNodes += graph.lastExpandedNodes
            if (!part.isReachable) {
                return fallback(startIndex, goalIndex, expandedNodes, "segment-unreachable")
            }
            parts += part
        }
        if (parts.isEmpty()) {
            return fallback(startIndex, goalIndex, expandedNodes, "empty-segments")
        }
        val candidate = MappedRouteResult(
            nodeIds = parts.flatMapIndexed { index, part -> if (index == 0) part.nodeIds else part.nodeIds.drop(1) },
            totalDistanceMeters = parts.sumOf { it.totalDistanceMeters },
            totalCost = parts.sumOf { it.totalCost },
            coordinates = parts.flatMapIndexed { index, part -> if (index == 0) part.coordinates else part.coordinates.drop(1) },
        )
        val baseline = graph.route(startIndex, goalIndex)
        expandedNodes += graph.lastExpandedNodes
        if (!baseline.isReachable || candidate.totalCost > baseline.totalCost * 1.05) {
            lastExpandedNodes = expandedNodes
            lastUsedFallback = true
            lastFallbackReason = "quality-gate"
            return baseline
        }
        lastExpandedNodes = expandedNodes
        return candidate
    }

    private fun fallback(
        startIndex: Int,
        goalIndex: Int,
        expandedNodes: Int,
        reason: String,
    ): MappedRouteResult {
        val baseline = graph.route(startIndex, goalIndex)
        lastExpandedNodes = expandedNodes + graph.lastExpandedNodes
        lastUsedFallback = true
        lastFallbackReason = reason
        return baseline
    }

    private fun segmentBounds(
        route: MappedRouteResult,
        from: Int,
        to: Int,
        options: HsaOptions,
    ): LazyMappedRoadGraph.SearchBounds? {
        val fromNodeId = graph.nodeAt(from).id
        val toNodeId = graph.nodeAt(to).id
        val fromPosition = route.nodeIds.indexOf(fromNodeId)
        val toPosition = route.nodeIds.indexOf(toNodeId)
        if (fromPosition < 0 || toPosition < fromPosition || toPosition >= route.coordinates.size) return null

        var minLatitude = Double.POSITIVE_INFINITY
        var maxLatitude = Double.NEGATIVE_INFINITY
        var minLongitude = Double.POSITIVE_INFINITY
        var maxLongitude = Double.NEGATIVE_INFINITY
        for (position in fromPosition..toPosition) {
            val (latitude, longitude) = route.coordinates[position]
            minLatitude = minOf(minLatitude, latitude)
            maxLatitude = maxOf(maxLatitude, latitude)
            minLongitude = minOf(minLongitude, longitude)
            maxLongitude = maxOf(maxLongitude, longitude)
        }
        val latitudePadding = options.boundaryRadiusKm / 111.0
        val longitudeScale = kotlin.math.cos(Math.toRadians((minLatitude + maxLatitude) / 2.0))
            .coerceAtLeast(0.1)
        val longitudePadding = options.boundaryRadiusKm / (111.0 * longitudeScale)
        return LazyMappedRoadGraph.SearchBounds(
            minLatitude - latitudePadding,
            maxLatitude + latitudePadding,
            minLongitude - longitudePadding,
            maxLongitude + longitudePadding,
        )
    }

    private fun selectMappedAnchors(route: MappedRouteResult, options: HsaOptions): List<Int> {
        val segmentCount = if (route.totalDistanceMeters / 1_000.0 <= options.minSegmentKm) {
            1
        } else {
            (ceil(route.totalDistanceMeters / 1_000.0 / options.targetSegmentKm).toInt() + 2)
                .coerceIn(1, options.maxSegments)
        }
        if (segmentCount <= 1) {
            return listOf(graph.nodeIndexOf(route.nodeIds.first()), graph.nodeIndexOf(route.nodeIds.last()))
        }
        val cumulative = ArrayList<Double>(route.coordinates.size)
        cumulative += 0.0
        for (index in 1 until route.coordinates.size) {
            val (fromLat, fromLon) = route.coordinates[index - 1]
            val (toLat, toLon) = route.coordinates[index]
            cumulative += cumulative.last() + haversineMeters(fromLat, fromLon, toLat, toLon)
        }
        val total = cumulative.last()
        return buildList {
            add(graph.nodeIndexOf(route.nodeIds.first()))
            for (index in 1 until segmentCount) {
                val target = total * index / segmentCount
                val nodeIndex = cumulative.indexOfFirst { it >= target }
                    .coerceAtLeast(1)
                    .coerceAtMost(route.nodeIds.lastIndex)
                add(graph.nodeIndexOf(route.nodeIds[nodeIndex]))
            }
            add(graph.nodeIndexOf(route.nodeIds.last()))
        }.distinct()
    }
}

fun benchmarkMappedRouting(
    graph: LazyMappedRoadGraph,
    startIndex: Int,
    goalIndex: Int,
    options: HsaOptions = HsaOptions.fromMode(HsaMode.B),
): List<MappedRoutingBenchmarkResult> {
    fun measure(name: String, block: () -> MappedRouteResult): MappedRoutingBenchmarkResult {
        val before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val sampler = ThreadHeapSampler().start()
        lateinit var route: MappedRouteResult
        val startedAt = System.nanoTime()
        route = block()
        val elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0
        val peak = sampler.stopAndGetPeak().minus(before).coerceAtLeast(0L)
        return MappedRoutingBenchmarkResult(name, elapsedSeconds, peak, route)
    }

    val aStar = measure("A*") { graph.route(startIndex, goalIndex) }
        .copy(expandedNodes = graph.lastExpandedNodes)
    val hsaRouter = MappedHsaRouter(graph)
    val hsa = measure("HSA*-${options.mode.name}") {
        hsaRouter.route(startIndex, goalIndex, options)
    }.copy(
        segmentCount = hsaRouter.lastSegmentCount,
        usedFallback = hsaRouter.lastUsedFallback,
        fallbackReason = hsaRouter.lastFallbackReason,
        expandedNodes = hsaRouter.lastExpandedNodes,
    )
    return listOf(aStar, hsa)
}

private class ThreadHeapSampler {
    @Volatile private var running = false
    @Volatile private var peakBytes = 0L
    private var thread: Thread? = null

    fun start(): ThreadHeapSampler {
        peakBytes = usedHeapBytes()
        running = true
        thread = Thread {
            while (running) {
                peakBytes = maxOf(peakBytes, usedHeapBytes())
                try {
                    Thread.sleep(5L)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        return this
    }

    fun stopAndGetPeak(): Long {
        running = false
        thread?.interrupt()
        thread?.join(100L)
        return maxOf(peakBytes, usedHeapBytes())
    }
}

private fun usedHeapBytes(): Long {
    val runtime = Runtime.getRuntime()
    return runtime.totalMemory() - runtime.freeMemory()
}
