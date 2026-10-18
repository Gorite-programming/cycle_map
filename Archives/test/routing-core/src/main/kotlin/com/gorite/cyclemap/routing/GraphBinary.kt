package com.gorite.cyclemap.routing

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream

/** Reads the compact graph format emitted by osm-importer. */
object GraphBinaryReader {
    fun read(file: File): RoadGraph = DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
        check(input.readUTF() == "CYCLEMAP_GRAPH_V1") { "Unsupported graph format" }
        val nodeCount = input.readInt()
        val nodes = LinkedHashMap<Long, GraphNode>(nodeCount)
        repeat(nodeCount) {
            val id = input.readLong()
            nodes[id] = GraphNode(id, input.readDouble(), input.readDouble())
        }
        val edgeCount = input.readInt()
        val outgoing = HashMap<Long, MutableList<GraphEdge>>()
        repeat(edgeCount) {
            val from = input.readLong()
            val to = input.readLong()
            val distance = input.readDouble()
            val roadType = input.readUTF()
            val oneWay = input.readBoolean()
            val grade = if (input.readBoolean()) input.readDouble() else null
            outgoing.getOrPut(from) { ArrayList() }.add(
                GraphEdge(from, to, distance, roadType, oneWay, grade),
            )
        }
        RoadGraph(nodes, outgoing)
    }
}
