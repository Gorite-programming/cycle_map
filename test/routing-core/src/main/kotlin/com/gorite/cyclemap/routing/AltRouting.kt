package com.gorite.cyclemap.routing

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.system.measureNanoTime

/** Immutable landmark distances aligned with one compact node ordinal array. */
class LandmarkIndex private constructor(
    val nodeIds: LongArray,
    private val fromLandmarks: Array<FloatArray>,
    private val toLandmarks: Array<FloatArray>,
    val buildElapsedMillis: Long,
) {
    val landmarkCount: Int get() = fromLandmarks.size
    val estimatedBytes: Long
        get() = nodeIds.size * 8L + landmarkCount * nodeIds.size * 8L

    private val ordinals = HashMap<Long, Int>(nodeIds.size * 2)

    init {
        nodeIds.forEachIndexed { index, id -> ordinals[id] = index }
    }

    fun lowerBound(fromId: Long, toId: Long): Double {
        val from = ordinals[fromId] ?: return 0.0
        val to = ordinals[toId] ?: return 0.0
        var bound = 0.0
        for (landmark in fromLandmarks.indices) {
            val landmarkTo = fromLandmarks[landmark][to].toDouble()
            val landmarkFrom = fromLandmarks[landmark][from].toDouble()
            val fromLandmark = toLandmarks[landmark][from].toDouble()
            val toLandmark = toLandmarks[landmark][to].toDouble()
            if (landmarkTo.isFinite() && landmarkFrom.isFinite()) {
                bound = max(bound, landmarkTo - landmarkFrom)
            }
            if (fromLandmark.isFinite() && toLandmark.isFinite()) {
                bound = max(bound, fromLandmark - toLandmark)
            }
        }
        return max(0.0, bound)
    }

    fun lowerBoundReverse(fromId: Long, toId: Long): Double {
        val from = ordinals[fromId] ?: return 0.0
        val to = ordinals[toId] ?: return 0.0
        var bound = 0.0
        for (landmark in fromLandmarks.indices) {
            val reverseLandmarkTo = toLandmarks[landmark][to].toDouble()
            val reverseLandmarkFrom = toLandmarks[landmark][from].toDouble()
            val reverseFromLandmark = fromLandmarks[landmark][from].toDouble()
            val reverseToLandmark = fromLandmarks[landmark][to].toDouble()
            if (reverseLandmarkTo.isFinite() && reverseLandmarkFrom.isFinite()) {
                bound = max(bound, reverseLandmarkTo - reverseLandmarkFrom)
            }
            if (reverseFromLandmark.isFinite() && reverseToLandmark.isFinite()) {
                bound = max(bound, reverseFromLandmark - reverseToLandmark)
            }
        }
        return max(0.0, bound)
    }

    fun write(file: File) {
        file.parentFile?.mkdirs()
        DataOutputStream(BufferedOutputStream(file.outputStream())).use { output ->
            output.writeUTF("CYCLEMAP_ALT_V1")
            output.writeInt(nodeIds.size)
            output.writeInt(landmarkCount)
            nodeIds.forEach(output::writeLong)
            for (landmark in 0 until landmarkCount) {
                fromLandmarks[landmark].forEach(output::writeFloat)
                toLandmarks[landmark].forEach(output::writeFloat)
            }
        }
    }

    companion object {
        fun build(
            graph: RoadGraph,
            landmarkCount: Int,
            edgeCostModel: EdgeCostModel = DistanceCostModel,
        ): LandmarkIndex {
            require(landmarkCount >= 1) { "landmarkCount must be positive" }
            val startedAt = System.nanoTime()
            val nodeIds = graph.nodes.keys.toLongArray()
            val landmarks = selectLandmarks(graph, nodeIds, landmarkCount)
            val from = Array(landmarks.size) { FloatArray(nodeIds.size) { Float.POSITIVE_INFINITY } }
            val to = Array(landmarks.size) { FloatArray(nodeIds.size) { Float.POSITIVE_INFINITY } }
            val ordinals = HashMap<Long, Int>(nodeIds.size * 2)
            nodeIds.forEachIndexed { index, id -> ordinals[id] = index }
            val reverse = Array(nodeIds.size) { ArrayList<GraphEdge>() }
            graph.outgoing.values.forEach { edges ->
                edges.forEach { edge ->
                    val target = ordinals[edge.to] ?: return@forEach
                    reverse[target] += edge.copy(from = edge.to, to = edge.from)
                }
            }
            landmarks.forEachIndexed { index, landmark ->
                val landmarkOrdinal = ordinals.getValue(landmark)
                val forwardDistances = dijkstraIndexed(graph, nodeIds, ordinals, landmarkOrdinal, edgeCostModel)
                val reverseDistances = dijkstraIndexed(reverse, nodeIds, ordinals, landmarkOrdinal, edgeCostModel)
                nodeIds.indices.forEach { ordinal ->
                    from[index][ordinal] = forwardDistances[ordinal].toFloat()
                    to[index][ordinal] = reverseDistances[ordinal].toFloat()
                }
            }
            return LandmarkIndex(
                nodeIds = nodeIds,
                fromLandmarks = from,
                toLandmarks = to,
                buildElapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L,
            )
        }

        fun read(file: File): LandmarkIndex {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                check(input.readUTF() == "CYCLEMAP_ALT_V1")
                val nodeCount = input.readInt()
                val landmarkCount = input.readInt()
                val nodeIds = LongArray(nodeCount) { input.readLong() }
                val from = Array(landmarkCount) { FloatArray(nodeCount) }
                val to = Array(landmarkCount) { FloatArray(nodeCount) }
                for (landmark in 0 until landmarkCount) {
                    for (index in 0 until nodeCount) from[landmark][index] = input.readFloat()
                    for (index in 0 until nodeCount) to[landmark][index] = input.readFloat()
                }
                return LandmarkIndex(nodeIds, from, to, 0L)
            }
        }

        private fun selectLandmarks(graph: RoadGraph, nodeIds: LongArray, count: Int): LongArray {
            if (nodeIds.size <= count) return nodeIds.copyOf()
            val sampleStep = max(1, nodeIds.size / (count * 64))
            val candidates = nodeIds.filterIndexed { index, _ -> index % sampleStep == 0 }
            val selected = ArrayList<Long>(count)
            selected += nodeIds.first()
            while (selected.size < count) {
                var best = candidates.first()
                var bestDistance = Double.NEGATIVE_INFINITY
                for (candidate in candidates) {
                    if (candidate in selected) continue
                    val candidateNode = graph.node(candidate)
                    val distance = selected.minOf { selectedId ->
                        haversineMeters(candidateNode.latitude, candidateNode.longitude,
                            graph.node(selectedId).latitude, graph.node(selectedId).longitude)
                    }
                    if (distance > bestDistance) {
                        best = candidate
                        bestDistance = distance
                    }
                }
                selected += best
            }
            return selected.toLongArray()
        }

        private fun dijkstraIndexed(
            graph: RoadGraph,
            nodeIds: LongArray,
            ordinals: Map<Long, Int>,
            start: Int,
            model: EdgeCostModel,
        ): DoubleArray = dijkstraIndexed(
            adjacency = Array(nodeIds.size) { index -> graph.outgoing[nodeIds[index]].orEmpty() },
            nodeIds = nodeIds,
            ordinals = ordinals,
            start = start,
            model = model,
        )

        private fun dijkstraIndexed(
            adjacency: Array<out List<GraphEdge>>,
            nodeIds: LongArray,
            ordinals: Map<Long, Int>,
            start: Int,
            model: EdgeCostModel,
        ): DoubleArray {
            data class Entry(val ordinal: Int, val cost: Double) : Comparable<Entry> {
                override fun compareTo(other: Entry): Int = cost.compareTo(other.cost)
            }
            val distances = DoubleArray(nodeIds.size) { Double.POSITIVE_INFINITY }
            val queue = PriorityQueue<Entry>()
            distances[start] = 0.0
            queue += Entry(start, 0.0)
            while (queue.isNotEmpty()) {
                val entry = queue.remove()
                if (entry.cost != distances[entry.ordinal]) continue
                for (edge in adjacency[entry.ordinal]) {
                    val edgeCost = model.cost(edge) ?: continue
                    val nextCost = entry.cost + edgeCost
                    val target = ordinals[edge.to] ?: continue
                    if (nextCost < distances[target]) {
                        distances[target] = nextCost
                        queue += Entry(target, nextCost)
                    }
                }
            }
            return distances
        }
    }
}

data class RoutingSearchResult(
    val route: RouteResult,
    val expandedNodes: Int,
)

class AltRouter(
    private val graph: RoadGraph,
    private val edgeCostModel: EdgeCostModel,
    private val landmarks: LandmarkIndex,
) {
    fun route(startId: Long, goalId: Long): RoutingSearchResult {
        graph.node(startId); graph.node(goalId)
        if (startId == goalId) return RoutingSearchResult(RouteResult(listOf(startId), 0.0), 0)
        data class Entry(val nodeId: Long, val g: Double, val f: Double) : Comparable<Entry> {
            override fun compareTo(other: Entry): Int = f.compareTo(other.f)
        }
        val open = PriorityQueue<Entry>()
        val gScore = HashMap<Long, Double>()
        val cameFrom = HashMap<Long, Long>()
        var expanded = 0
        gScore[startId] = 0.0
        open += Entry(startId, 0.0, landmarks.lowerBound(startId, goalId))
        while (open.isNotEmpty()) {
            val entry = open.remove()
            if (entry.g != gScore[entry.nodeId]) continue
            expanded++
            if (entry.nodeId == goalId) {
                return RoutingSearchResult(buildRoute(cameFrom, goalId, entry.g), expanded)
            }
            for (edge in graph.outgoing[entry.nodeId].orEmpty()) {
                val edgeCost = edgeCostModel.cost(edge) ?: continue
                val next = entry.g + edgeCost
                if (next < gScore.getOrDefault(edge.to, Double.POSITIVE_INFINITY)) {
                    gScore[edge.to] = next
                    cameFrom[edge.to] = entry.nodeId
                    open += Entry(edge.to, next, next + landmarks.lowerBound(edge.to, goalId))
                }
            }
        }
        return RoutingSearchResult(RouteResult(emptyList(), Double.POSITIVE_INFINITY), expanded)
    }
}

class BidirectionalRouter(
    private val graph: RoadGraph,
    private val edgeCostModel: EdgeCostModel,
    private val landmarks: LandmarkIndex? = null,
) {
    private data class SearchEntry(
        val nodeId: Long,
        val g: Double,
        val estimate: Double,
        val backward: Boolean,
    ) : Comparable<SearchEntry> {
        override fun compareTo(other: SearchEntry): Int = estimate.compareTo(other.estimate)
    }

    fun route(startId: Long, goalId: Long): RoutingSearchResult {
        graph.node(startId); graph.node(goalId)
        if (startId == goalId) return RoutingSearchResult(RouteResult(listOf(startId), 0.0), 0)
        val reverse = reverseGraph(graph)
        val forward = HashMap<Long, Double>(); val backward = HashMap<Long, Double>()
        val fromParent = HashMap<Long, Long>(); val toParent = HashMap<Long, Long>()
        val openForward = PriorityQueue<SearchEntry>(); val openBackward = PriorityQueue<SearchEntry>()
        forward[startId] = 0.0; backward[goalId] = 0.0
        openForward += SearchEntry(startId, 0.0, forwardEstimate(startId, goalId), false)
        openBackward += SearchEntry(goalId, 0.0, backwardEstimate(goalId, startId), true)
        var best = Double.POSITIVE_INFINITY; var meeting: Long? = null; var expanded = 0
        while (openForward.isNotEmpty() && openBackward.isNotEmpty()) {
            if (minOpenG(openForward, forward) + minOpenG(openBackward, backward) >= best) break
            val expandForward = openForward.peek().estimate <= openBackward.peek().estimate
            val queue = if (expandForward) openForward else openBackward
            val distances = if (expandForward) forward else backward
            val other = if (expandForward) backward else forward
            val current = queue.remove()
            if (current.g != distances[current.nodeId]) continue
            expanded++
            if (other.containsKey(current.nodeId)) {
                val candidate = current.g + other.getValue(current.nodeId)
                if (candidate < best) { best = candidate; meeting = current.nodeId }
            }
            val adjacency = if (expandForward) graph.outgoing else reverse.outgoing
            val parent = if (expandForward) fromParent else toParent
            for (edge in adjacency[current.nodeId].orEmpty()) {
                val edgeCost = edgeCostModel.cost(edge) ?: continue
                val next = current.g + edgeCost
                if (next < distances.getOrDefault(edge.to, Double.POSITIVE_INFINITY)) {
                    distances[edge.to] = next
                    parent[edge.to] = current.nodeId
                    val estimate = next + if (expandForward) forwardEstimate(edge.to, goalId) else backwardEstimate(edge.to, startId)
                    queue += SearchEntry(edge.to, next, estimate, !expandForward)
                }
            }
        }
        val meet = meeting ?: return RoutingSearchResult(RouteResult(emptyList(), Double.POSITIVE_INFINITY), expanded)
        return RoutingSearchResult(buildBidirectionalRoute(fromParent, toParent, meet, best), expanded)
    }

    private fun forwardEstimate(node: Long, goal: Long): Double = landmarks?.lowerBound(node, goal) ?: 0.0
    private fun backwardEstimate(node: Long, start: Long): Double = landmarks?.lowerBoundReverse(node, start) ?: 0.0

    private fun minOpenG(queue: PriorityQueue<SearchEntry>, distances: Map<Long, Double>): Double {
        var minG = Double.POSITIVE_INFINITY
        for (entry in queue) {
            if (entry.g == distances[entry.nodeId] && entry.g < minG) {
                minG = entry.g
            }
        }
        return minG
    }
}

data class RoutingVariantMeasurement(
    val algorithm: String,
    val elapsedMillis: Double,
    val expandedNodes: Int,
    val peakHeapIncreaseBytes: Long,
    val reachable: Boolean,
    val distanceMeters: Double,
    val totalCost: Double,
    val distanceErrorPercent: Double,
    val costErrorPercent: Double,
    val indexSizeBytes: Long = 0L,
    val indexBuildMillis: Long = 0L,
)

data class RoutingVariantBenchmark(
    val startId: Long,
    val goalId: Long,
    val baselineDistanceMeters: Double,
    val baselineCost: Double,
    val measurements: List<RoutingVariantMeasurement>,
)

data class RoutingBenchmarkCase(
    val label: String,
    val startId: Long,
    val goalId: Long,
)

data class RoutingBenchmarkCaseResult(
    val label: String,
    val benchmark: RoutingVariantBenchmark,
)

fun benchmarkRoutingCases(
    graph: RoadGraph,
    cases: List<RoutingBenchmarkCase>,
    edgeCostModel: EdgeCostModel = DistanceCostModel,
): List<RoutingBenchmarkCaseResult> = cases.map { query ->
    RoutingBenchmarkCaseResult(
        label = query.label,
        benchmark = benchmarkRoutingVariants(graph, query.startId, query.goalId, edgeCostModel),
    )
}

fun RoutingVariantBenchmark.formatTable(): String = buildString {
    appendLine("algorithm\telapsedSeconds\texpandedNodes\theapIncreaseBytes\tdistanceMeters\tcost\tdistanceErrorPercent\tcostErrorPercent\tindexSizeBytes\tindexBuildMillis")
    measurements.forEach { measurement ->
        appendLine(
            listOf(
                measurement.algorithm,
                "%.3f".format(java.util.Locale.US, measurement.elapsedMillis / 1_000.0),
                measurement.expandedNodes,
                measurement.peakHeapIncreaseBytes,
                "%.1f".format(java.util.Locale.US, measurement.distanceMeters),
                "%.1f".format(java.util.Locale.US, measurement.totalCost),
                "%.3f".format(java.util.Locale.US, measurement.distanceErrorPercent),
                "%.3f".format(java.util.Locale.US, measurement.costErrorPercent),
                measurement.indexSizeBytes,
                measurement.indexBuildMillis,
            ).joinToString("\t"),
        )
    }
}

fun benchmarkRoutingVariants(
    graph: RoadGraph,
    startId: Long,
    goalId: Long,
    edgeCostModel: EdgeCostModel = DistanceCostModel,
    landmarkCounts: List<Int> = listOf(4, 8, 16),
): RoutingVariantBenchmark {
    val baseline = timedVariant("A*") {
        val router = AStarRouter(graph, edgeCostModel)
        val route = router.route(startId, goalId)
        RoutingSearchResult(route, router.lastExpandedNodes)
    }
    val baselineDistance = routeDistanceMetersForAlt(graph, baseline.result.route.nodeIds)
    val baselineCost = baseline.result.route.totalCost
    val measurements = ArrayList<RoutingVariantMeasurement>()
    measurements += baseline.measurement(graph, baselineDistance, baselineCost)
    for (count in landmarkCounts) {
        val buildStarted = System.nanoTime()
        val index = LandmarkIndex.build(graph, count, edgeCostModel)
        val buildMillis = (System.nanoTime() - buildStarted) / 1_000_000L
        val alt = timedVariant("A*+ALT($count)") { AltRouter(graph, edgeCostModel, index).route(startId, goalId) }
        measurements += alt.measurement(graph, baselineDistance, baselineCost, index.estimatedBytes, buildMillis)
    }
    val bidirectional = timedVariant("Bidirectional A*") { BidirectionalRouter(graph, edgeCostModel).route(startId, goalId) }
    measurements += bidirectional.measurement(graph, baselineDistance, baselineCost)
    val index = LandmarkIndex.build(graph, 16, edgeCostModel)
    val bidirectionalAlt = timedVariant("Bidirectional A*+ALT(16)") {
        BidirectionalRouter(graph, edgeCostModel, index).route(startId, goalId)
    }
    measurements += bidirectionalAlt.measurement(graph, baselineDistance, baselineCost, index.estimatedBytes, index.buildElapsedMillis)
    return RoutingVariantBenchmark(startId, goalId, baselineDistance, baselineCost, measurements)
}

private data class TimedVariant(val result: RoutingSearchResult, val elapsedMillis: Double, val peakHeapBytes: Long, val name: String)

private fun timedVariant(name: String, block: () -> RoutingSearchResult): TimedVariant {
    val before = usedHeapBytesForAlt()
    val sampler = AltHeapSampler().start()
    lateinit var result: RoutingSearchResult
    val started = System.nanoTime()
    result = block()
    val peak = sampler.stop()
    return TimedVariant(result, (System.nanoTime() - started) / 1_000_000.0, (peak - before).coerceAtLeast(0L), name)
}

private fun TimedVariant.measurement(
    graph: RoadGraph,
    baselineDistance: Double,
    baselineCost: Double,
    indexSize: Long = 0L,
    indexBuildMillis: Long = 0L,
): RoutingVariantMeasurement {
    val distance = routeDistanceMetersForAlt(graph, result.route.nodeIds)
    return RoutingVariantMeasurement(
        algorithm = name,
        elapsedMillis = elapsedMillis,
        expandedNodes = result.expandedNodes,
        peakHeapIncreaseBytes = peakHeapBytes,
        reachable = result.route.isReachable,
        distanceMeters = distance,
        totalCost = result.route.totalCost,
        distanceErrorPercent = errorPercent(distance, baselineDistance),
        costErrorPercent = errorPercent(result.route.totalCost, baselineCost),
        indexSizeBytes = indexSize,
        indexBuildMillis = indexBuildMillis,
    )
}

private fun buildRoute(parent: Map<Long, Long>, goal: Long, cost: Double): RouteResult {
    val path = mutableListOf(goal)
    var current = goal
    while (parent.containsKey(current)) { current = parent.getValue(current); path += current }
    path.reverse()
    return RouteResult(path, cost)
}

private fun buildBidirectionalRoute(
    fromParent: Map<Long, Long>,
    toParent: Map<Long, Long>,
    meeting: Long,
    cost: Double,
): RouteResult {
    val path = mutableListOf(meeting)
    var current = meeting
    while (fromParent.containsKey(current)) { current = fromParent.getValue(current); path += current }
    path.reverse()
    current = meeting
    while (toParent.containsKey(current)) { current = toParent.getValue(current); path += current }
    return RouteResult(path, cost)
}

private fun reverseGraph(graph: RoadGraph): RoadGraph {
    val outgoing = HashMap<Long, MutableList<GraphEdge>>(graph.nodes.size * 2)
    graph.nodes.keys.forEach { outgoing[it] = ArrayList() }
    graph.outgoing.values.flatten().forEach { edge ->
        outgoing.getValue(edge.to).add(edge.copy(from = edge.to, to = edge.from))
    }
    return RoadGraph(graph.nodes, outgoing)
}

private fun errorPercent(value: Double, baseline: Double): Double =
    if (!value.isFinite() || !baseline.isFinite() || baseline == 0.0) Double.NaN else (value - baseline) / baseline * 100.0

private fun routeDistanceMetersForAlt(graph: RoadGraph, nodeIds: List<Long>): Double {
    var total = 0.0
    for (index in 0 until nodeIds.lastIndex) {
        val edge = graph.outgoing[nodeIds[index]].orEmpty().firstOrNull { it.to == nodeIds[index + 1] }
            ?: return Double.POSITIVE_INFINITY
        total += edge.distanceMeters
    }
    return total
}
private fun usedHeapBytesForAlt(): Long {
    val runtime = Runtime.getRuntime()
    return runtime.totalMemory() - runtime.freeMemory()
}

private class AltHeapSampler {
    @Volatile private var running = false
    @Volatile private var peak = 0L
    private var thread: Thread? = null

    fun start(): AltHeapSampler {
        peak = usedHeapBytesForAlt()
        running = true
        thread = Thread {
            while (running) {
                peak = max(peak, usedHeapBytesForAlt())
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

    fun stop(): Long {
        running = false
        thread?.interrupt()
        thread?.join(100L)
        return max(peak, usedHeapBytesForAlt())
    }
}
