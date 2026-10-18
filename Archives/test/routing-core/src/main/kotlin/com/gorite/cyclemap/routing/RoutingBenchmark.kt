package com.gorite.cyclemap.routing

import java.util.Locale
import kotlin.math.roundToLong
import kotlin.system.measureNanoTime

data class RoutingBenchmarkSample(
    val elapsedMillis: Double,
    val reachable: Boolean,
    val totalCost: Double,
    val distanceMeters: Double,
    val peakHeapIncreaseBytes: Long,
    val expandedNodes: Int? = null,
    val segmentCount: Int? = null,
    val usedFallback: Boolean? = null,
)

data class RoutingBenchmarkResult(
    val algorithm: String,
    val samples: List<RoutingBenchmarkSample>,
) {
    val medianElapsedMillis: Long
        get() = samples.map { it.elapsedMillis }.sorted().let { it[it.size / 2].roundToLong() }

    val medianElapsedSeconds: Double
        get() = samples.map { it.elapsedMillis }.sorted().let { it[it.size / 2] / 1_000.0 }

    val medianDistanceMeters: Double
        get() = samples.map { it.distanceMeters }.sorted().let { it[it.size / 2] }

    val medianExpandedNodes: Int?
        get() = samples.mapNotNull { it.expandedNodes }.takeIf { it.isNotEmpty() }?.sorted()?.let { it[it.size / 2] }

    val medianPeakHeapIncreaseBytes: Long
        get() = samples.map { it.peakHeapIncreaseBytes }.sorted().let { it[it.size / 2] }
}

data class RoutingBenchmarkReport(
    val warmupRuns: Int,
    val measuredRuns: Int,
    val results: List<RoutingBenchmarkResult>,
)

/** Runs repeatable A-star and HSA-star measurements on one immutable graph and one query. */
fun benchmarkRoutingAlgorithms(
    graph: RoadGraph,
    startId: Long,
    goalId: Long,
    warmupRuns: Int = 1,
    measuredRuns: Int = 3,
    edgeCostModel: EdgeCostModel = DistanceCostModel,
    modes: List<HsaMode> = listOf(HsaMode.O, HsaMode.B, HsaMode.F),
): RoutingBenchmarkReport {
    require(warmupRuns >= 0) { "warmupRuns must not be negative" }
    require(measuredRuns >= 1) { "measuredRuns must be positive" }
    require(modes.isNotEmpty()) { "modes must not be empty" }

    val algorithms = listOf("A*") + modes.map { "HSA*-${it.name}" }
    repeat(warmupRuns) {
        runAStarSample(graph, startId, goalId, edgeCostModel)
        modes.forEach { runHsaSample(graph, startId, goalId, edgeCostModel, it) }
    }

    val samples = algorithms.associateWith { ArrayList<RoutingBenchmarkSample>(measuredRuns) }
    repeat(measuredRuns) {
        samples.getValue("A*") += runAStarSample(graph, startId, goalId, edgeCostModel)
        modes.forEach { mode ->
            samples.getValue("HSA*-${mode.name}") += runHsaSample(graph, startId, goalId, edgeCostModel, mode)
        }
    }
    return RoutingBenchmarkReport(
        warmupRuns = warmupRuns,
        measuredRuns = measuredRuns,
        results = algorithms.map { RoutingBenchmarkResult(it, samples.getValue(it)) },
    )
}

fun RoutingBenchmarkReport.formatTable(): String = buildString {
    appendLine("algorithm\tmedianSeconds\tmedianMemoryMiB\tmedianDistanceM\tmedianExpandedNodes\treachable\tsegments\tfallback")
    for (result in results) {
        val last = result.samples.last()
        appendLine(
            listOf(
                result.algorithm,
                "%.3f".format(Locale.US, result.medianElapsedSeconds),
                "%.1f".format(Locale.US, result.medianPeakHeapIncreaseBytes / (1024.0 * 1024.0)),
                result.medianDistanceMeters.roundToLong(),
                result.medianExpandedNodes ?: "-",
                last.reachable,
                last.segmentCount ?: "-",
                last.usedFallback ?: "-",
            ).joinToString("\t"),
        )
    }
}

private fun runAStarSample(
    graph: RoadGraph,
    startId: Long,
    goalId: Long,
    edgeCostModel: EdgeCostModel,
): RoutingBenchmarkSample {
    lateinit var result: RouteResult
    val before = usedHeapBytes()
    val sampler = HeapSampler().start()
    val elapsedNanos = measureNanoTime {
        result = AStarRouter(graph, edgeCostModel).route(startId, goalId)
    }
    val peakIncrease = sampler.stopAndGetPeak().minus(before).coerceAtLeast(0L)
    return RoutingBenchmarkSample(
        elapsedMillis = elapsedNanos / 1_000_000.0,
        reachable = result.isReachable,
        totalCost = result.totalCost,
        distanceMeters = routeDistanceMeters(graph, result.nodeIds),
        peakHeapIncreaseBytes = peakIncrease,
    )
}

private fun runHsaSample(
    graph: RoadGraph,
    startId: Long,
    goalId: Long,
    edgeCostModel: EdgeCostModel,
    mode: HsaMode,
): RoutingBenchmarkSample {
    val router = HsaStarRouter(graph, edgeCostModel)
    lateinit var result: RouteResult
    val before = usedHeapBytes()
    val sampler = HeapSampler().start()
    val elapsedNanos = measureNanoTime {
        result = router.route(startId, goalId, HsaOptions.fromMode(mode))
    }
    val peakIncrease = sampler.stopAndGetPeak().minus(before).coerceAtLeast(0L)
    val metrics = router.lastMetrics
    return RoutingBenchmarkSample(
        elapsedMillis = elapsedNanos / 1_000_000.0,
        reachable = result.isReachable,
        totalCost = result.totalCost,
        distanceMeters = routeDistanceMeters(graph, result.nodeIds),
        peakHeapIncreaseBytes = peakIncrease,
        expandedNodes = metrics.expandedNodes,
        segmentCount = metrics.segmentCount,
        usedFallback = metrics.usedFallback,
    )
}

private class HeapSampler {
    @Volatile private var running = false
    @Volatile private var peakBytes = 0L
    private var thread: Thread? = null

    fun start(): HeapSampler {
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

private fun routeDistanceMeters(graph: RoadGraph, nodeIds: List<Long>): Double {
    var total = 0.0
    for (index in 0 until nodeIds.lastIndex) {
        val edge = graph.outgoing[nodeIds[index]].orEmpty()
            .filter { it.to == nodeIds[index + 1] }
            .minByOrNull { it.distanceMeters }
            ?: return Double.POSITIVE_INFINITY
        total += edge.distanceMeters
    }
    return total
}