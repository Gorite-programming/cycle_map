package com.gorite.cyclemap.routing

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.PriorityQueue
import kotlin.math.abs

/** V1 graph reader that maps the file and decodes edge records only when routing. */
class LazyMappedRoadGraph private constructor(
    private val graphChannel: FileChannel,
    private val mapped: ByteBuffer,
    private val nodeIds: LongArray,
    private val latitudes: DoubleArray,
    private val longitudes: DoubleArray,
    private val edgeStarts: LongArray,
    private val edgeFirstOrdinals: IntArray,
    private val edgeCounts: IntArray,
    private val edgeTargets: IntArray,
    val edgeCount: Int,
) : AutoCloseable {
    val nodeCount: Int get() = nodeIds.size

    fun nearestNodeIndex(latitude: Double, longitude: Double): Int {
        var best = 0
        var bestDistance = Double.POSITIVE_INFINITY
        for (i in nodeIds.indices) {
            val distance = haversineMeters(latitude, longitude, latitudes[i], longitudes[i])
            if (distance < bestDistance) { bestDistance = distance; best = i }
        }
        return best
    }

    fun nearestNode(latitude: Double, longitude: Double): Long = nodeIds[nearestNodeIndex(latitude, longitude)]

    fun route(start: Int, goal: Int): MappedRouteResult {
        require(start in nodeIds.indices && goal in nodeIds.indices) { "Unknown node index" }
        if (start == goal) return MappedRouteResult(listOf(nodeIds[start]), 0.0, 0.0, listOf(latitudes[start] to longitudes[start]))
        data class Entry(val index: Int, val estimate: Double) : Comparable<Entry> {
            override fun compareTo(other: Entry): Int = estimate.compareTo(other.estimate)
        }
        val open = PriorityQueue<Entry>()
        val cost = HashMap<Int, Double>()
        val distance = HashMap<Int, Double>()
        val cameFrom = HashMap<Int, Int>()
        cost[start] = 0.0; distance[start] = 0.0
        open += Entry(start, heuristic(start, goal))
        while (open.isNotEmpty()) {
            val current = open.remove().index
            if (current == goal) {
                val path = ArrayList<Int>(); var cursor = goal; path += cursor
                while (cameFrom.containsKey(cursor)) { cursor = cameFrom.getValue(cursor); path += cursor }
                path.reverse()
                return MappedRouteResult(
                    path.map { nodeIds[it] }, distance.getValue(goal), cost.getValue(goal),
                    path.map { latitudes[it] to longitudes[it] },
                )
            }
            var position = edgeStarts[current]
            var ordinal = edgeFirstOrdinals[current]
            repeat(edgeCounts[current]) {
                val record = readEdge(position)
                position = record.nextOffset
                val multiplier = multiplier(record.type)
                if (multiplier != null) {
                    val target = edgeTargets[ordinal]
                    if (target >= 0) {
                        val newCost = cost.getValue(current) + record.distance * multiplier * gradePenalty(record.grade)
                        if (newCost < cost.getOrDefault(target, Double.POSITIVE_INFINITY)) {
                            cost[target] = newCost
                            distance[target] = distance.getValue(current) + record.distance
                            cameFrom[target] = current
                            open += Entry(target, newCost + heuristic(target, goal))
                        }
                    }
                }
                ordinal++
            }
        }
        return MappedRouteResult(emptyList(), Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
    }

    private data class EdgeRecord(val to: Long, val distance: Double, val type: Int, val grade: Float, val nextOffset: Long)

    private fun readEdge(offset: Long): EdgeRecord {
        val p = offset.toInt()
        val to = mapped.getLong(p + 8)
        val distance = mapped.getDouble(p + 16)
        val typeStart = p + 24
        val length = mapped.getShort(typeStart).toInt() and 0xffff
        val road = ByteArray(length)
        val saved = mapped.position()
        mapped.position(typeStart + 2); mapped.get(road); mapped.position(saved)
        val type = roadTypeCode(String(road, Charsets.UTF_8))
        var next = (typeStart + 2 + length + 2).toLong()
        val grade = if (mapped.get((typeStart + 2 + length + 1).toInt()).toInt() != 0) {
            val value = mapped.getDouble((typeStart + 2 + length + 2).toInt()).toFloat()
            next += 8
            value
        } else Float.NaN
        return EdgeRecord(to, distance, type, grade, offset + (next - p))
    }

    private fun heuristic(from: Int, to: Int) = haversineMeters(latitudes[from], longitudes[from], latitudes[to], longitudes[to])
    private fun gradePenalty(grade: Float) = if (grade.isNaN()) 1.0 else 1.0 + abs(grade.toDouble()) * 0.02
    private fun multiplier(type: Int): Double? = when (type) {
        TYPE_MOTORWAY, TYPE_MOTORWAY_LINK -> null
        TYPE_PRIMARY -> 0.90; TYPE_SECONDARY -> 0.95; TYPE_TERTIARY -> 1.00
        TYPE_RESIDENTIAL -> 1.20; TYPE_UNCLASSIFIED -> 1.25; TYPE_SERVICE -> 1.35
        TYPE_TRACK -> 1.60; TYPE_PATH -> 1.80; else -> 1.15
    }

    override fun close() = graphChannel.close()

    companion object {
        private const val TYPE_MOTORWAY = 1; private const val TYPE_MOTORWAY_LINK = 2
        private const val TYPE_PRIMARY = 3; private const val TYPE_SECONDARY = 4; private const val TYPE_TERTIARY = 5
        private const val TYPE_RESIDENTIAL = 6; private const val TYPE_UNCLASSIFIED = 7; private const val TYPE_SERVICE = 8
        private const val TYPE_TRACK = 9; private const val TYPE_PATH = 10

        fun load(file: File, indexFile: File = File(file.path + ".idx")): LazyMappedRoadGraph {
            require(indexFile.isFile) { "Missing graph index: ${indexFile.absolutePath}" }
            val channel = RandomAccessFile(file, "r").channel
            try {
                val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()).order(ByteOrder.BIG_ENDIAN)
                val headerLength = mapped.short.toInt() and 0xffff
                val header = ByteArray(headerLength); mapped.get(header)
                check(String(header, Charsets.UTF_8) == "CYCLEMAP_GRAPH_V1")
                val nodeCount = mapped.int
                val ids = LongArray(nodeCount); val lats = DoubleArray(nodeCount); val lons = DoubleArray(nodeCount)
                repeat(nodeCount) { i -> ids[i] = mapped.long; lats[i] = mapped.double; lons[i] = mapped.double }
                DataInputStream(BufferedInputStream(FileInputStream(indexFile))).use { input ->
                    check(input.readUTF() == "CYCLEMAP_INDEX_V2")
                    check(input.readInt() == nodeCount)
                    val starts = LongArray(nodeCount) { input.readLong() }
                    val first = IntArray(nodeCount) { input.readInt() }
                    val counts = IntArray(nodeCount) { input.readInt() }
                    val edgeCount = input.readInt()
                    val targets = IntArray(edgeCount) { input.readInt() }
                    return LazyMappedRoadGraph(channel, mapped, ids, lats, lons, starts, first, counts, targets, edgeCount)
                }
            } catch (t: Throwable) { channel.close(); throw t }
        }
        private fun roadTypeCode(type: String): Int = when (type) {
            "motorway" -> TYPE_MOTORWAY; "motorway_link" -> TYPE_MOTORWAY_LINK; "primary" -> TYPE_PRIMARY
            "secondary" -> TYPE_SECONDARY; "tertiary" -> TYPE_TERTIARY; "residential" -> TYPE_RESIDENTIAL
            "unclassified" -> TYPE_UNCLASSIFIED; "service" -> TYPE_SERVICE; "track" -> TYPE_TRACK; "path" -> TYPE_PATH
            else -> 0
        }
    }
}
