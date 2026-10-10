package com.gorite.cyclemap.importer

import com.gorite.cyclemap.routing.GraphNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OsmImporterTest {
    @Test
    fun `access filtering does not discard road types`() {
        assertFalse(BicycleAccessFilter.isAllowed(mapOf("highway" to "primary", "bicycle" to "no")))
        assertTrue(BicycleAccessFilter.isAllowed(mapOf("highway" to "primary")))
        assertTrue(BicycleAccessFilter.isAllowed(mapOf("highway" to "track")))
    }

    @Test
    fun `graph keeps highway and applies one way direction`() {
        val nodes = mapOf(
            1L to GraphNode(1L, 34.0, 131.0),
            2L to GraphNode(2L, 34.001, 131.0),
            3L to GraphNode(3L, 34.002, 131.0),
        )
        val graph = OsmGraphBuilder().build(
            nodes,
            listOf(OsmWay(10L, longArrayOf(1, 2, 3), mapOf("highway" to "secondary", "oneway" to "yes"))),
        )

        val edges = graph.outgoing.values.flatten()
        assertEquals(2, edges.size)
        assertTrue(edges.all { it.roadType == "secondary" })
        assertTrue(edges.all { it.oneWay })
        assertTrue(edges.none { it.from == 2L && it.to == 1L })
    }

    @Test
    fun `japan bbox covers tokyo and okinawa`() {
        val tokyo = GraphNode(1L, 35.68, 139.76)
        val naha = GraphNode(2L, 26.21, 127.68)
        val yamaguchi = GraphNode(3L, 34.18, 131.47)
        assertTrue(GeoBBox.JAPAN.contains(tokyo))
        assertTrue(GeoBBox.JAPAN.contains(naha))
        assertTrue(GeoBBox.JAPAN.contains(yamaguchi))
        assertFalse(GeoBBox.YAMAGUCHI.contains(tokyo))
    }
}
