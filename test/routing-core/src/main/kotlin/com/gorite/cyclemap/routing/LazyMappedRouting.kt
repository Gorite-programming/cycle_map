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
import kotlin.math.cos

private const val DEG_LAT_METERS = 111_320.0

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
    data class SearchBounds(
        val minLatitude: Double,
        val maxLatitude: Double,
        val minLongitude: Double,
        val maxLongitude: Double,
    ) {
        fun contains(latitude: Double, longitude: Double): Boolean =
            latitude in minLatitude..maxLatitude && longitude in minLongitude..maxLongitude
    }

    val nodeCount: Int get() = nodeIds.size

    @Volatile
    var lastExpandedNodes: Int = 0
        private set

    fun nodeAt(index: Int): GraphNode {
        require(index in nodeIds.indices) { "Unknown node index: $index" }
        return GraphNode(nodeIds[index], latitudes[index], longitudes[index])
    }

    fun nodeIndexOf(nodeId: Long): Int {
        val index = nodeIds.indexOf(nodeId)
        require(index >= 0) { "Unknown node id: $nodeId" }
        return index
    }

    fun nearestNodeIndex(latitude: Double, longitude: Double): Int {
        if (nodeCount == 0) return -1
        // 2パス探索: haversineは三角関数4回で重いため、1パス目で安価な
        // 等距円筒近似の最良点を求め、2パス目では近似距離が暫定最良を
        // 下回る候補にだけhaversineを計算する。結果は全走査と同一。
        val cosLat = cos(Math.toRadians(latitude))
        var seed = 0
        var seedApprox = Double.POSITIVE_INFINITY
        for (i in nodeIds.indices) {
            val approx = approxDistSq(latitude, longitude, latitudes[i], longitudes[i], cosLat)
            if (approx < seedApprox) { seedApprox = approx; seed = i }
        }
        var best = seed
        var bestDistance = haversineMeters(latitude, longitude, latitudes[seed], longitudes[seed])
        var bestDistSq = bestDistance * bestDistance
        for (i in nodeIds.indices) {
            if (i == seed) continue
            // 近似はhaversineの下限ではないが、暫定最良より明らかに遠い点の枝刈りには使える。
            // 境界付近の取りこぼしを避けるため2倍のマージンを取る。
            val approx = approxDistSq(latitude, longitude, latitudes[i], longitudes[i], cosLat)
            if (approx > bestDistSq * 4.0) continue
            val distance = haversineMeters(latitude, longitude, latitudes[i], longitudes[i])
            if (distance < bestDistance) {
                bestDistance = distance
                bestDistSq = distance * distance
                best = i
            }
        }
        return best
    }

    private fun approxDistSq(lat: Double, lon: Double, lat2: Double, lon2: Double, cosLat: Double): Double {
        val dLat = (lat2 - lat) * DEG_LAT_METERS
        val dLon = (lon2 - lon) * DEG_LAT_METERS * cosLat
        return dLat * dLat + dLon * dLon
    }

    fun nearestNode(latitude: Double, longitude: Double): Long = nodeIds[nearestNodeIndex(latitude, longitude)]

    /**
     * 案内生成用の読み取り専用ビュー (①基本経路指示)。
     * ルーティング探索には一切影響しない。呼び出し側で graphLock.read 保持下で使うこと。
     */
    data class JunctionBranch(val targetIndex: Int, val roadTypeName: String, val distanceMeters: Double)

    fun outgoingBranchCount(index: Int): Int {
        require(index in nodeIds.indices) { "Unknown node index: $index" }
        return edgeCounts[index]
    }

    fun outgoingBranches(index: Int): List<JunctionBranch> {
        require(index in nodeIds.indices) { "Unknown node index: $index" }
        val result = ArrayList<JunctionBranch>(edgeCounts[index])
        var position = edgeStarts[index]
        var ordinal = edgeFirstOrdinals[index]
        repeat(edgeCounts[index]) {
            val record = readEdge(position)
            position = record.nextOffset
            val target = edgeTargets[ordinal]
            if (target >= 0) {
                result += JunctionBranch(target, record.typeName, record.distance)
            }
            ordinal++
        }
        return result
    }

    fun route(
        start: Int,
        goal: Int,
        bounds: SearchBounds? = null,
        heuristicMultiplier: Double = 1.0,
        arterialOnly: Boolean = false,
        arterialPenaltyMultiplier: Double = 1.0,
        isCancelled: (() -> Boolean)? = null,
        maxExpandedNodes: Int = Int.MAX_VALUE,
        preference: RoutePreference = RoutePreference.RECOMMENDED,
    ): MappedRouteResult {
        require(start in nodeIds.indices && goal in nodeIds.indices) { "Unknown node index" }
        require(heuristicMultiplier >= 1.0) { "heuristicMultiplier must be at least 1.0" }
        require(arterialPenaltyMultiplier >= 1.0) { "arterialPenaltyMultiplier must be at least 1.0" }
        require(maxExpandedNodes > 0) { "maxExpandedNodes must be positive" }
        if (start == goal) {
            lastExpandedNodes = 0
            return MappedRouteResult(listOf(nodeIds[start]), 0.0, 0.0, listOf(latitudes[start] to longitudes[start]))
        }
        data class Entry(val index: Int, val estimate: Double) : Comparable<Entry> {
            override fun compareTo(other: Entry): Int = estimate.compareTo(other.estimate)
        }
        val open = PriorityQueue<Entry>()
        val cost = HashMap<Int, Double>()
        val distance = HashMap<Int, Double>()
        val cameFrom = HashMap<Int, Int>()
        var expandedNodes = 0
        cost[start] = 0.0; distance[start] = 0.0
        open += Entry(start, heuristic(start, goal, heuristicMultiplier))
        while (open.isNotEmpty()) {
            val current = open.remove().index
            expandedNodes++
            // 1024展開ごとにキャンセル・上限を判定 (毎回の判定コストを避ける)
            if ((expandedNodes and 1023) == 0) {
                if (isCancelled?.invoke() == true) {
                    lastExpandedNodes = expandedNodes
                    return MappedRouteResult(emptyList(), Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, wasCancelled = true)
                }
                if (expandedNodes >= maxExpandedNodes) {
                    lastExpandedNodes = expandedNodes
                    return MappedRouteResult(emptyList(), Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, wasTruncated = true)
                }
            }
            if (current == goal) {
                lastExpandedNodes = expandedNodes
                val path = ArrayList<Int>(); var cursor = goal; path += cursor
                while (cameFrom.containsKey(cursor)) { cursor = cameFrom.getValue(cursor); path += cursor }
                path.reverse()
                return MappedRouteResult(
                    path.map { nodeIds[it] }, distance.getValue(goal), cost.getValue(goal),
                    path.map { latitudes[it] to longitudes[it] },
                )
            }
            if (bounds != null && current != start &&
                !bounds.contains(latitudes[current], longitudes[current])
            ) {
                continue
            }
            var position = edgeStarts[current]
            var ordinal = edgeFirstOrdinals[current]
            repeat(edgeCounts[current]) {
                val record = readEdge(position)
                position = record.nextOffset
                val multiplier = multiplier(record.type)
                val target = edgeTargets[ordinal]
                if (multiplier != null && (!arterialOnly || isArterial(record.type) || current == start || target == goal)) {
                    if (target >= 0 && (target == goal || bounds == null ||
                        bounds.contains(latitudes[target], longitudes[target]))) {
                        val arterialBias = if (isArterial(record.type)) 1.0 else arterialPenaltyMultiplier
                        val combinedMultiplier = (multiplier * gradePenalty(record.grade, preference) * arterialBias).coerceAtLeast(0.90)
                        val stepCost = record.distance * combinedMultiplier
                        val newCost = cost.getValue(current) + stepCost
                        if (newCost < cost.getOrDefault(target, Double.POSITIVE_INFINITY)) {
                            cost[target] = newCost
                            distance[target] = distance.getValue(current) + record.distance
                            cameFrom[target] = current
                            open += Entry(target, newCost + heuristic(target, goal, heuristicMultiplier))
                        }
                    }
                }
                ordinal++
            }
        }
        lastExpandedNodes = expandedNodes
        return MappedRouteResult(emptyList(), Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
    }

    private data class EdgeRecord(
        val to: Long,
        val distance: Double,
        val type: Int,
        val typeName: String,
        val grade: Float,
        val nextOffset: Long,
    )

    private fun readEdge(offset: Long): EdgeRecord {
        // All position arithmetic stays in Long to handle files > 2 GB correctly.
        // ByteBuffer absolute-get methods accept Int indices; the cast is safe as long as
        // the mapped file fits in Int range, which is guaranteed by mmap (max 2 GB on
        // most JVMs). For files that could exceed that limit this would need chunked mapping,
        // but we keep the cast explicit and validated here.
        val p = offset
        val pi = p.toInt()
        val to = mapped.getLong(pi + 8)
        val distance = mapped.getDouble(pi + 16)
        val typeStartL = p + 24
        val typeStartI = typeStartL.toInt()
        val length = mapped.getShort(typeStartI).toInt() and 0xffff
        val road = ByteArray(length)
        // Use a duplicate to avoid mutating the shared buffer's position
        val slice = mapped.duplicate()
        slice.position(typeStartI + 2)
        slice.get(road)
        val typeName = String(road, Charsets.UTF_8)
        val type = roadTypeCode(typeName)
        val onewayFlagOffset = typeStartL + 2 + length      // points at the oneway byte
        val hasGradeFlagOffset = onewayFlagOffset + 1        // points at the hasGrade byte
        val gradeDataOffset = hasGradeFlagOffset + 1         // points at grade double (if present)
        val grade = if (mapped.get(hasGradeFlagOffset.toInt()).toInt() != 0) {
            mapped.getDouble(gradeDataOffset.toInt()).toFloat()
        } else Float.NaN
        val nextOffset = if (grade.isNaN()) gradeDataOffset else gradeDataOffset + 8
        return EdgeRecord(to, distance, type, typeName, grade, nextOffset)
    }

    private fun heuristic(from: Int, to: Int, multiplier: Double) =
        haversineMeters(latitudes[from], longitudes[from], latitudes[to], longitudes[to]) * 0.90 * multiplier

    private fun gradePenalty(grade: Float, preference: RoutePreference): Double {
        if (grade.isNaN()) return 1.0
        val g = grade.toDouble()
        return when (preference) {
            RoutePreference.RECOMMENDED -> when {
                g >= 0.0 -> 1.0 + (g * 0.02) + (g * g * 0.002) // 緩やかな上りは+2%、急勾配は二次関数的に増加
                g >= -4.0 -> (1.0 + g * 0.02).coerceAtLeast(0.95) // 緩やかな下りは軽快（最大-5%）
                else -> 1.0 + abs(g + 4.0) * 0.03 // -4%を超える急坂下りは減速ペナルティ
            }
            RoutePreference.FLAT -> when {
                // 上り坂を強く回避（上限5.0倍でクランプし探索空間の球状化を防止）
                g > 0.0 -> (1.0 + g * 0.10 + g * g * 0.01).coerceAtMost(5.0)
                g >= -5.0 -> (1.0 + g * 0.01).coerceAtLeast(0.95)
                else -> (1.0 + abs(g + 5.0) * 0.04).coerceAtMost(3.0)
            }
            RoutePreference.HILL_CLIMB -> when {
                // 上り坂を優先（ペナルティなし）
                g > 0.0 -> 1.0
                // 平坦および下り坂に相対ペナルティ（上限2.0倍）
                else -> (1.0 + (5.0 - abs(g)).coerceAtLeast(0.0) * 0.05).coerceAtMost(2.0)
            }
        }
    }

    private fun multiplier(type: Int): Double? = when (type) {
        TYPE_MOTORWAY, TYPE_MOTORWAY_LINK -> null
        TYPE_PRIMARY -> 0.90; TYPE_SECONDARY -> 0.95; TYPE_TERTIARY -> 1.00
        TYPE_RESIDENTIAL -> 1.20; TYPE_UNCLASSIFIED -> 1.25; TYPE_SERVICE -> 1.35
        TYPE_TRACK -> 1.60; TYPE_PATH -> 1.80; else -> 1.15
    }

    private fun isArterial(type: Int): Boolean =
        type == TYPE_PRIMARY || type == TYPE_SECONDARY || type == TYPE_TERTIARY

    override fun close() = graphChannel.close()

    companion object {
        /**
         * 起点・終点を含む探索bboxを余裕付きで作る。長距離の誤爆的迂回を切らないよう
         * マージンは直線距離の [marginFraction] + 最低 [minMarginMeters] と generous に取る。
         * 正当な迂回がbbox外に出る可能性は残るため、到達不能時はbboxなしで再試行すること。
         */
        fun searchBoundsFor(
            startLat: Double,
            startLon: Double,
            goalLat: Double,
            goalLon: Double,
            marginFraction: Double = 0.5,
            minMarginMeters: Double = 10_000.0,
        ): SearchBounds {
            val straight = haversineMeters(startLat, startLon, goalLat, goalLon)
            val margin = maxOf(straight * marginFraction, minMarginMeters)
            val midLat = Math.toRadians((startLat + goalLat) / 2.0)
            val latMarginDeg = margin / DEG_LAT_METERS
            val lonMarginDeg = margin / (DEG_LAT_METERS * cos(midLat).coerceAtLeast(0.2))
            return SearchBounds(
                minLatitude = minOf(startLat, goalLat) - latMarginDeg,
                maxLatitude = maxOf(startLat, goalLat) + latMarginDeg,
                minLongitude = minOf(startLon, goalLon) - lonMarginDeg,
                maxLongitude = maxOf(startLon, goalLon) + lonMarginDeg,
            )
        }
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

/** ルート探索の優先方針 (平坦優先・坂道優先など)。 */
enum class RoutePreference(val label: String) {
    RECOMMENDED("おすすめ"),
    FLAT("平坦優先"),
    HILL_CLIMB("坂道優先"),
}
