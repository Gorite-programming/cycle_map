package com.gorite.cyclemap.routing

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.PriorityQueue
import kotlin.math.abs

/** Compact, mmap-backed graph for Android. The file remains the source of truth. */
class MappedRoadGraph private constructor(
    private val channel: FileChannel,
    private val mapped: ByteBuffer,
    private val nodeIds: LongArray,
    private val latitudes: DoubleArray,
    private val longitudes: DoubleArray,
    private val nodeIndex: LongIntIndex,
    private val edgeHead: IntArray,
    private val edgeTo: LongArray,
    private val edgeDistance: DoubleArray,
    private val edgeType: ByteArray,
    private val edgeGrade: FloatArray,
    private val edgeNext: IntArray,
) : Closeable {
    val nodeCount: Int get() = nodeIds.size
    val edgeCount: Int get() = edgeTo.size

    fun nearestNode(latitude: Double, longitude: Double): Long {
        var best = 0
        var bestDistance = Double.POSITIVE_INFINITY
        for (i in nodeIds.indices) {
            val d = haversineMeters(latitude, longitude, latitudes[i], longitudes[i])
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return nodeIds[best]
    }

    fun route(startId: Long, goalId: Long): MappedRouteResult {
        val start = nodeIndex.get(startId)
        val goal = nodeIndex.get(goalId)
        require(start >= 0 && goal >= 0) { "Unknown node" }
        if (start == goal) return MappedRouteResult(listOf(startId), 0.0, 0.0)

        data class Entry(val index: Int, val estimate: Double) : Comparable<Entry> {
            override fun compareTo(other: Entry): Int = estimate.compareTo(other.estimate)
        }
        val open = PriorityQueue<Entry>()
        val cost = HashMap<Int, Double>()
        val distance = HashMap<Int, Double>()
        val cameFrom = HashMap<Int, Int>()
        cost[start] = 0.0
        distance[start] = 0.0
        open += Entry(start, heuristic(start, goal))

        while (open.isNotEmpty()) {
            val current = open.remove().index
            if (current == goal) {
                val indices = ArrayList<Int>()
                var cursor = goal
                indices += cursor
                while (cameFrom.containsKey(cursor)) {
                    cursor = cameFrom.getValue(cursor)
                    indices += cursor
                }
                indices.reverse()
                return MappedRouteResult(
                    indices.map { nodeIds[it] },
                    distance.getValue(goal),
                    cost.getValue(goal),
                    indices.map { latitudes[it] to longitudes[it] },
                )
            }
            var edge = edgeHead[current]
            while (edge >= 0) {
                val multiplier = multiplier(edgeType[edge].toInt())
                if (multiplier != null) {
                    val target = nodeIndex.get(edgeTo[edge])
                    if (target >= 0) {
                        val combinedMultiplier = (multiplier * gradePenalty(edgeGrade[edge])).coerceAtLeast(0.90)
                        val newCost = cost.getValue(current) + edgeDistance[edge] * combinedMultiplier
                        if (newCost < cost.getOrDefault(target, Double.POSITIVE_INFINITY)) {
                            cost[target] = newCost
                            distance[target] = distance.getValue(current) + edgeDistance[edge]
                            cameFrom[target] = current
                            open += Entry(target, newCost + heuristic(target, goal))
                        }
                    }
                }
                edge = edgeNext[edge]
            }
        }
        return MappedRouteResult(emptyList(), Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
    }

    private fun heuristic(from: Int, to: Int): Double =
        haversineMeters(latitudes[from], longitudes[from], latitudes[to], longitudes[to]) * 0.90

    private fun gradePenalty(grade: Float): Double {
        if (grade.isNaN()) return 1.0
        val g = grade.toDouble()
        return when {
            g >= 0.0 -> 1.0 + (g * 0.02) + (g * g * 0.002)
            g >= -4.0 -> (1.0 + g * 0.02).coerceAtLeast(0.95)
            else -> 1.0 + abs(g + 4.0) * 0.03
        }
    }

    private fun multiplier(type: Int): Double? = when (type) {
        TYPE_MOTORWAY, TYPE_MOTORWAY_LINK -> null
        TYPE_PRIMARY -> 0.90
        TYPE_SECONDARY -> 0.95
        TYPE_TERTIARY -> 1.00
        TYPE_RESIDENTIAL -> 1.20
        TYPE_UNCLASSIFIED -> 1.25
        TYPE_SERVICE -> 1.35
        TYPE_TRACK -> 1.60
        TYPE_PATH -> 1.80
        else -> 1.15
    }

    override fun close() {
        channel.close()
    }

    companion object {
        private const val TYPE_UNKNOWN = 0
        private const val TYPE_MOTORWAY = 1
        private const val TYPE_MOTORWAY_LINK = 2
        private const val TYPE_PRIMARY = 3
        private const val TYPE_SECONDARY = 4
        private const val TYPE_TERTIARY = 5
        private const val TYPE_RESIDENTIAL = 6
        private const val TYPE_UNCLASSIFIED = 7
        private const val TYPE_SERVICE = 8
        private const val TYPE_TRACK = 9
        private const val TYPE_PATH = 10

        fun load(file: File): MappedRoadGraph {
            val channel = RandomAccessFile(file, "r").channel
            try {
                val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()).order(ByteOrder.BIG_ENDIAN)
                val headerLength = mapped.short.toInt() and 0xffff
                val headerBytes = ByteArray(headerLength)
                mapped.get(headerBytes)
                check(String(headerBytes, Charsets.UTF_8) == "CYCLEMAP_GRAPH_V1") { "Unsupported graph format" }
                val nodeCount = mapped.int
                val ids = LongArray(nodeCount)
                val lats = DoubleArray(nodeCount)
                val lons = DoubleArray(nodeCount)
                val index = LongIntIndex(nodeCount)
                repeat(nodeCount) { i ->
                    ids[i] = mapped.long
                    lats[i] = mapped.double
                    lons[i] = mapped.double
                    index.put(ids[i], i)
                }
                val edgeCount = mapped.int
                val heads = IntArray(nodeCount) { -1 }
                val to = LongArray(edgeCount)
                val distance = DoubleArray(edgeCount)
                val type = ByteArray(edgeCount)
                val grade = FloatArray(edgeCount) { Float.NaN }
                val next = IntArray(edgeCount)
                repeat(edgeCount) { i ->
                    val from = mapped.long
                    to[i] = mapped.long
                    distance[i] = mapped.double
                    val length = mapped.short.toInt() and 0xffff
                    val roadBytes = ByteArray(length)
                    mapped.get(roadBytes)
                    type[i] = roadTypeCode(String(roadBytes, Charsets.UTF_8)).toByte()
                    mapped.get() // oneway
                    if (mapped.get().toInt() != 0) grade[i] = mapped.double.toFloat()
                    val fromIndex = index.get(from)
                    next[i] = if (fromIndex >= 0) heads[fromIndex] else -1
                    if (fromIndex >= 0) heads[fromIndex] = i
                }
                return MappedRoadGraph(channel, mapped, ids, lats, lons, index, heads, to, distance, type, grade, next)
            } catch (t: Throwable) {
                channel.close()
                throw t
            }
        }

        private fun roadTypeCode(type: String): Int = when (type) {
            "motorway" -> TYPE_MOTORWAY
            "motorway_link" -> TYPE_MOTORWAY_LINK
            "primary" -> TYPE_PRIMARY
            "secondary" -> TYPE_SECONDARY
            "tertiary" -> TYPE_TERTIARY
            "residential" -> TYPE_RESIDENTIAL
            "unclassified" -> TYPE_UNCLASSIFIED
            "service" -> TYPE_SERVICE
            "track" -> TYPE_TRACK
            "path" -> TYPE_PATH
            else -> TYPE_UNKNOWN
        }
    }
}

data class MappedRouteResult(
    val nodeIds: List<Long>,
    val totalDistanceMeters: Double,
    val totalCost: Double,
    val coordinates: List<Pair<Double, Double>> = emptyList(),
    /** 探索がキャンセル要求で中断された。到達不能とは区別すること。 */
    val wasCancelled: Boolean = false,
    /** 展開ノード上限で打ち切られた。範囲を狭めて再試行できる。 */
    val wasTruncated: Boolean = false,
) {
    val isReachable: Boolean get() = nodeIds.isNotEmpty()
}

private class LongIntIndex(expectedSize: Int) {
    private val keys: LongArray
    private val values: IntArray
    private val occupied: BooleanArray
    private val mask: Int

    init {
        var capacity = 1
        while (capacity < expectedSize * 2) capacity = capacity shl 1
        keys = LongArray(capacity)
        values = IntArray(capacity) { -1 }
        occupied = BooleanArray(capacity)
        mask = capacity - 1
    }

    fun put(key: Long, value: Int) {
        val h = (key xor (key ushr 33)).toInt()
        var slot = h and mask
        val step = (((key ushr 16) xor key).toInt() and mask) or 1
        while (occupied[slot] && keys[slot] != key) {
            slot = (slot + step) and mask
        }
        keys[slot] = key
        values[slot] = value
        occupied[slot] = true
    }

    fun get(key: Long): Int {
        val h = (key xor (key ushr 33)).toInt()
        var slot = h and mask
        val step = (((key ushr 16) xor key).toInt() and mask) or 1
        while (occupied[slot]) {
            if (keys[slot] == key) return values[slot]
            slot = (slot + step) and mask
        }
        return -1
    }
}
