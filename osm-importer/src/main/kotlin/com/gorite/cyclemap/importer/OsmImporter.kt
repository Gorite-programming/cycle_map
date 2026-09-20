package com.gorite.cyclemap.importer

import com.gorite.cyclemap.routing.GraphEdge
import com.gorite.cyclemap.routing.GraphBinaryReader
import com.gorite.cyclemap.routing.GraphNode
import com.gorite.cyclemap.routing.AStarRouter
import com.gorite.cyclemap.routing.CyclingCostModel
import com.gorite.cyclemap.routing.RoadGraph
import crosby.binary.osmosis.OsmosisReader
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer
import org.openstreetmap.osmosis.core.domain.v0_6.Node
import org.openstreetmap.osmosis.core.domain.v0_6.Way
import org.openstreetmap.osmosis.core.task.v0_6.Sink
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class OsmWay(
    val id: Long,
    val nodeIds: LongArray,
    val tags: Map<String, String>,
)

object BicycleAccessFilter {
    private val explicitlyDenied = setOf("no", "private", "emergency")

    /** Extraction stage: only decide whether bicycles may use the way. */
    fun isAllowed(tags: Map<String, String>): Boolean {
        if (tags["highway"].isNullOrBlank()) return false
        if (tags["bicycle"] in explicitlyDenied) return false
        if (tags["access"] in explicitlyDenied) return false
        if (tags["vehicle"] in explicitlyDenied) return false
        return true
    }
}

data class GeoBBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
) {
    fun contains(node: GraphNode): Boolean =
        node.latitude in minLat..maxLat && node.longitude in minLon..maxLon

    companion object {
        val YAMAGUCHI = GeoBBox(33.70, 34.80, 130.70, 132.20)
        val JAPAN = GeoBBox(20.0, 46.2, 122.9, 154.0)
        val WORLD = GeoBBox(-90.0, 90.0, -180.0, 180.0)

        fun parse(name: String): GeoBBox = when (name.lowercase()) {
            "yamaguchi" -> YAMAGUCHI
            "japan" -> JAPAN
            "none", "world" -> WORLD
            else -> error("Unknown bbox '$name' (yamaguchi|japan|none)")
        }
    }
}

class OsmGraphBuilder(private val bbox: GeoBBox = GeoBBox.YAMAGUCHI) {
    fun build(nodes: Map<Long, GraphNode>, ways: List<OsmWay>): RoadGraph {
        val graphNodes = linkedMapOf<Long, GraphNode>()
        val outgoing = linkedMapOf<Long, MutableList<GraphEdge>>()

        for (way in ways) {
            if (!BicycleAccessFilter.isAllowed(way.tags)) continue
            val roadType = way.tags["highway"] ?: "unknown"
            val oneway = way.tags["oneway"] in setOf("yes", "true", "1", "-1")
            val reverseOnly = way.tags["oneway"] == "-1"
            for (index in 0 until way.nodeIds.size - 1) {
                val fromId = way.nodeIds[index]
                val toId = way.nodeIds[index + 1]
                val from = nodes[fromId] ?: continue
                val to = nodes[toId] ?: continue
                if (!bbox.contains(from) || !bbox.contains(to)) continue
                val distance = haversineMeters(from, to)
                graphNodes[fromId] = from
                graphNodes[toId] = to
                if (!reverseOnly) addEdge(outgoing, GraphEdge(fromId, toId, distance, roadType, oneway))
                if (!oneway || reverseOnly) addEdge(outgoing, GraphEdge(toId, fromId, distance, roadType, oneway))
            }
        }
        return RoadGraph(graphNodes, outgoing)
    }

    private fun addEdge(outgoing: MutableMap<Long, MutableList<GraphEdge>>, edge: GraphEdge) {
        outgoing.getOrPut(edge.from) { mutableListOf() }.add(edge)
    }

    private fun haversineMeters(from: GraphNode, to: GraphNode): Double =
        com.gorite.cyclemap.routing.haversineMeters(from.latitude, from.longitude, to.latitude, to.longitude)
}

class PbfReader {
    fun read(file: File): Pair<Map<Long, GraphNode>, List<OsmWay>> {
        val nodes = linkedMapOf<Long, GraphNode>()
        val ways = mutableListOf<OsmWay>()
        val sink = object : Sink {
            override fun initialize(metaData: Map<String, Any>) = Unit

            override fun process(entityContainer: EntityContainer) {
                when (val entity = entityContainer.entity) {
                    is Node -> nodes[entity.id] = GraphNode(entity.id, entity.latitude, entity.longitude)
                    is Way -> {
                        val tags = entity.tags.associate { it.key to it.value }
                        ways += OsmWay(
                            entity.id,
                            entity.wayNodes.map { it.nodeId }.toLongArray(),
                            tags,
                        )
                    }
                }
            }

            override fun complete() = Unit
            override fun close() = Unit
        }
        FileInputStream(file).use { input ->
            OsmosisReader(input).apply { setSink(sink) }.run()
        }
        return nodes to ways
    }
}

data class BicycleNetworkStats(
    val bicycleWays: Long,
    val referencedNodeIds: Long,
    val directedEdges: Long,
    val elapsedMs: Long,
    val peakHeapBytes: Long,
)

class LongOpenHashSet(initialPower: Int = 22) {
    private var keys = LongArray(1 shl initialPower)
    var size: Int = 0
        private set

    fun add(id: Long) {
        if (id == 0L) return
        ensureCapacity()
        var index = mix(id) and (keys.size - 1)
        while (true) {
            val existing = keys[index]
            if (existing == 0L) {
                keys[index] = id
                size++
                return
            }
            if (existing == id) return
            index = (index + 1) and (keys.size - 1)
        }
    }

    fun contains(id: Long): Boolean {
        if (id == 0L) return false
        var index = mix(id) and (keys.size - 1)
        while (true) {
            val existing = keys[index]
            if (existing == 0L) return false
            if (existing == id) return true
            index = (index + 1) and (keys.size - 1)
        }
    }

    private fun ensureCapacity() {
        if (size * 2 < keys.size) return
        val old = keys
        keys = LongArray(old.size * 2)
        size = 0
        for (value in old) if (value != 0L) add(value)
    }

    private fun mix(value: Long): Int = (value xor (value ushr 32)).toInt()
}

private fun readPbf(file: File, onEntity: (org.openstreetmap.osmosis.core.domain.v0_6.Entity) -> Unit) {
    val sink = object : Sink {
        override fun initialize(metaData: Map<String, Any>) = Unit
        override fun process(entityContainer: EntityContainer) = onEntity(entityContainer.entity)
        override fun complete() = Unit
        override fun close() = Unit
    }
    FileInputStream(file).use { input ->
        OsmosisReader(input).apply { setSink(sink) }.run()
    }
}

fun collectBicycleNetworkStats(file: File): BicycleNetworkStats {
    val peak = PeakHeapTracker().start()
    val start = System.nanoTime()
    val nodeIds = LongOpenHashSet()
    var bicycleWays = 0L
    var directedEdges = 0L
    readPbf(file) { entity ->
        if (entity is Way) {
            val tags = entity.tags.associate { it.key to it.value }
            if (!BicycleAccessFilter.isAllowed(tags)) return@readPbf
            bicycleWays++
            val ids = entity.wayNodes
            for (node in ids) nodeIds.add(node.nodeId)
            val segments = (ids.size - 1).coerceAtLeast(0).toLong()
            val oneway = tags["oneway"] in setOf("yes", "true", "1", "-1")
            directedEdges += if (oneway) segments else segments * 2L
        }
    }
    peak.stop()
    return BicycleNetworkStats(
        bicycleWays = bicycleWays,
        referencedNodeIds = nodeIds.size.toLong(),
        directedEdges = directedEdges,
        elapsedMs = (System.nanoTime() - start) / 1_000_000L,
        peakHeapBytes = peak.peakBytes,
    )
}

private class PeakHeapTracker {
    @Volatile private var running = false
    var peakBytes: Long = 0
        private set

    fun start(): PeakHeapTracker {
        running = true
        Thread {
            val runtime = Runtime.getRuntime()
            while (running) {
                val used = runtime.totalMemory() - runtime.freeMemory()
                if (used > peakBytes) peakBytes = used
                try {
                    Thread.sleep(200)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply { isDaemon = true; start() }
        return this
    }

    fun stop() {
        running = false
    }
}

class GraphBinaryWriter {
    fun write(graph: RoadGraph, output: File) {
        output.parentFile?.mkdirs()
        DataOutputStream(FileOutputStream(output)).use { out ->
            out.writeUTF("CYCLEMAP_GRAPH_V1")
            out.writeInt(graph.nodes.size)
            graph.nodes.values.forEach { node ->
                out.writeLong(node.id)
                out.writeDouble(node.latitude)
                out.writeDouble(node.longitude)
            }
            val edges = graph.outgoing.values.flatten()
            out.writeInt(edges.size)
            edges.forEach { edge ->
                out.writeLong(edge.from)
                out.writeLong(edge.to)
                out.writeDouble(edge.distanceMeters)
                out.writeUTF(edge.roadType)
                out.writeBoolean(edge.oneWay)
                val grade = edge.gradePercent
                out.writeBoolean(grade != null)
                if (grade != null) out.writeDouble(grade)
            }
        }
    }
}

fun main(args: Array<String>) {
    val rest = args.toMutableList()
    var statsOnly = false
    var bbox = GeoBBox.YAMAGUCHI
    while (rest.firstOrNull()?.startsWith("--") == true) {
        when (val flag = rest.removeAt(0)) {
            "--stats" -> statsOnly = true
            "--bbox" -> bbox = GeoBBox.parse(rest.removeAt(0))
            "--index" -> {
                require(rest.size == 1) { "Usage: osm-importer --index <graph>" }
                writeGraphIndex(File(rest[0]), File(rest[0] + ".idx"))
                return
            }
            "--route" -> {
                require(rest.size == 5) { "Usage: osm-importer --route <graph> <startLat> <startLon> <goalLat> <goalLon>" }
                runRouteSanityCheck(File(rest[0]), rest[1].toDouble(), rest[2].toDouble(), rest[3].toDouble(), rest[4].toDouble())
                return
            }
            else -> error("Unknown flag $flag")
        }
    }
    if (statsOnly) {
        require(rest.size == 1) { "Usage: osm-importer --stats [--bbox japan|yamaguchi|none] <input.osm.pbf>" }
        val input = File(rest[0])
        require(input.isFile) { "Input PBF does not exist: ${input.absolutePath}" }
        println("stats pbf=${input.absolutePath} sizeBytes=${input.length()} bbox=$bbox")
        val stats = collectBicycleNetworkStats(input)
        val estimatedGraphBytes = 32L + stats.referencedNodeIds * 24L + stats.directedEdges * 40L
        val estimatedIndexBytes = 16L + stats.referencedNodeIds * (8L + 4L + 4L) + 4L + stats.directedEdges * 4L
        println("bicycleWays=${stats.bicycleWays}")
        println("nodes=${stats.referencedNodeIds}")
        println("edges=${stats.directedEdges}")
        println("elapsedMs=${stats.elapsedMs}")
        println("peakHeapBytes=${stats.peakHeapBytes}")
        println("estimatedGraphBytes=$estimatedGraphBytes")
        println("estimatedIndexBytes=$estimatedIndexBytes")
        println("note=node/edge counts are bicycle-way references in the PBF; missing nodes and bbox clipping can reduce the written graph slightly")
        return
    }
    require(rest.size == 2) { "Usage: osm-importer [--bbox japan|yamaguchi|none] <input.osm.pbf> <output.graph>" }
    val input = File(rest[0])
    val output = File(rest[1])
    require(input.isFile) { "Input PBF does not exist: ${input.absolutePath}" }
    val peak = PeakHeapTracker().start()
    val start = System.nanoTime()
    val (nodes, ways) = PbfReader().read(input)
    val graph = OsmGraphBuilder(bbox).build(nodes, ways)
    GraphBinaryWriter().write(graph, output)
    peak.stop()
    val edgeCount = graph.outgoing.values.sumOf { it.size }
    val elapsedMs = (System.nanoTime() - start) / 1_000_000L
    println("nodes=${graph.nodes.size} edges=$edgeCount output=${output.absolutePath} elapsedMs=$elapsedMs peakHeapBytes=${peak.peakBytes}")
}

private fun writeGraphIndex(graphFile: File, indexFile: File) {
    DataInputStream(BufferedInputStream(FileInputStream(graphFile))).use { input ->
        val magicLength = input.readUnsignedShort()
        val magic = ByteArray(magicLength); input.readFully(magic)
        check(String(magic, Charsets.UTF_8) == "CYCLEMAP_GRAPH_V1")
        val nodeCount = input.readInt()
        val nodeIds = HashMap<Long, Int>(nodeCount * 2)
        repeat(nodeCount) { index ->
            nodeIds[input.readLong()] = index
            input.readDouble(); input.readDouble()
        }
        val edgeCount = input.readInt()
        val starts = LongArray(nodeCount) { -1L }
        val firstOrdinals = IntArray(nodeCount) { -1 }
        val counts = IntArray(nodeCount)
        val targets = IntArray(edgeCount) { -1 }
        var offset = 2L + magicLength + 4L + nodeCount * 24L + 4L
        repeat(edgeCount) { ordinal ->
            val recordStart = offset
            val from = input.readLong(); offset += 8
            val to = input.readLong(); offset += 8
            input.readDouble(); offset += 8
            val roadLength = input.readUnsignedShort(); offset += 2
            input.skipBytes(roadLength); offset += roadLength
            input.readBoolean(); offset++
            val hasGrade = input.readBoolean(); offset++
            if (hasGrade) { input.readDouble(); offset += 8 }
            nodeIds[from]?.let { nodeIndex ->
                if (starts[nodeIndex] < 0) starts[nodeIndex] = recordStart
                if (firstOrdinals[nodeIndex] < 0) firstOrdinals[nodeIndex] = ordinal
                counts[nodeIndex]++
            }
            targets[ordinal] = nodeIds[to] ?: -1
        }
        indexFile.parentFile?.mkdirs()
        DataOutputStream(FileOutputStream(indexFile)).use { output ->
            output.writeUTF("CYCLEMAP_INDEX_V2")
            output.writeInt(nodeCount)
            starts.forEach(output::writeLong)
            firstOrdinals.forEach(output::writeInt)
            counts.forEach(output::writeInt)
            output.writeInt(edgeCount)
            targets.forEach(output::writeInt)
        }
        println("index nodes=$nodeCount edges=$edgeCount output=${indexFile.absolutePath}")
    }
}

private fun runRouteSanityCheck(file: File, startLat: Double, startLon: Double, goalLat: Double, goalLon: Double) {
    val graph = GraphBinaryReader.read(file)
    fun nearest(lat: Double, lon: Double): GraphNode = graph.nodes.values.minBy {
        com.gorite.cyclemap.routing.haversineMeters(lat, lon, it.latitude, it.longitude)
    }
    val start = nearest(startLat, startLon)
    val goal = nearest(goalLat, goalLon)
    val result = AStarRouter(graph, CyclingCostModel).route(start.id, goal.id)
    check(result.isReachable) { "No route found" }
    val distance = result.nodeIds.zipWithNext().sumOf { (from, to) ->
        graph.outgoing.getValue(from).first { it.to == to }.distanceMeters
    }
    println("startNode=${start.id} goalNode=${goal.id} steps=${result.nodeIds.size - 1} distanceMeters=${"%.1f".format(distance)} cost=${"%.1f".format(result.totalCost)}")
}
