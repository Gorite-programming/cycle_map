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

class OsmGraphBuilder {
    companion object {
        // Yamaguchi prefecture working area. Edges are kept only when both endpoints
        // are inside this box; the importer can therefore safely consume a larger extract.
        private const val MIN_LAT = 33.70
        private const val MAX_LAT = 34.80
        private const val MIN_LON = 130.70
        private const val MAX_LON = 132.20
    }

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
                if (!inYamaguchi(from) || !inYamaguchi(to)) continue
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

    private fun inYamaguchi(node: GraphNode): Boolean =
        node.latitude in MIN_LAT..MAX_LAT && node.longitude in MIN_LON..MAX_LON

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
    if (args.firstOrNull() == "--index") {
        require(args.size == 2) { "Usage: osm-importer --index <graph>" }
        writeGraphIndex(File(args[1]), File(args[1] + ".idx"))
        return
    }
    if (args.firstOrNull() == "--route") {
        require(args.size == 6) { "Usage: osm-importer --route <graph> <startLat> <startLon> <goalLat> <goalLon>" }
        runRouteSanityCheck(File(args[1]), args[2].toDouble(), args[3].toDouble(), args[4].toDouble(), args[5].toDouble())
        return
    }
    require(args.size == 2) { "Usage: osm-importer <input.osm.pbf> <output.graph>" }
    val input = File(args[0])
    val output = File(args[1])
    require(input.isFile) { "Input PBF does not exist: ${input.absolutePath}" }
    val (nodes, ways) = PbfReader().read(input)
    val graph = OsmGraphBuilder().build(nodes, ways)
    GraphBinaryWriter().write(graph, output)
    val edgeCount = graph.outgoing.values.sumOf { it.size }
    println("nodes=${graph.nodes.size} edges=$edgeCount output=${output.absolutePath}")
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
