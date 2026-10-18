package com.gorite.cyclemap.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutingTest {
    @Test
    fun findsLowestCostPath() {
        // Use distinct, geographically plausible coordinates so the A* heuristic is non-trivial 
        // (rather than always 0, which would degenerate to Dijkstra).
        val nodes = mapOf(
            1L to GraphNode(1, 34.178, 131.473), // Yamaguchi city center
            2L to GraphNode(2, 34.180, 131.477), // ~400m east
            3L to GraphNode(3, 34.175, 131.480), // ~700m southeast
            4L to GraphNode(4, 34.182, 131.485)  // ~1200m east
        )
        val graph = RoadGraph(
            nodes,
            mapOf(
                1L to listOf(GraphEdge(1, 2, 10.0), GraphEdge(1, 3, 20.0)),
                2L to listOf(GraphEdge(2, 4, 10.0)),
                3L to listOf(GraphEdge(3, 4, 5.0)),
            ),
        )

        val result = AStarRouter(graph).route(1, 4)

        assertEquals(listOf(1L, 2L, 4L), result.nodeIds)
        assertEquals(20.0, result.totalCost)
        assertTrue(result.isReachable)
    }

    @Test
    fun returnsUnreachableWhenNoPathExists() {
        val graph = RoadGraph(
            mapOf(
                1L to GraphNode(1, 34.178, 131.473),
                2L to GraphNode(2, 34.180, 131.477)
            ),
            emptyMap(),
        )
        val result = AStarRouter(graph).route(1, 2)
        assertTrue(!result.isReachable)
    }

    @Test
    fun haversineMeters_neverReturnsNanForAntipodalOrIdenticalPoints() {
        // Identical points -> distance 0.0
        val distIdentical = haversineMeters(34.178, 131.473, 34.178, 131.473)
        assertEquals(0.0, distIdentical, 1e-6)
        assertTrue(distIdentical.isFinite())

        // Antipodal points along equator -> approx half circumference of Earth ~ 20,015 km
        val distEquator = haversineMeters(0.0, 0.0, 0.0, 180.0)
        assertTrue(distEquator.isFinite())
        assertTrue(!distEquator.isNaN())
        assertEquals(Math.PI * 6_371_000.0, distEquator, 1000.0)

        // Antipodal points at poles
        val distPoles = haversineMeters(90.0, 0.0, -90.0, 0.0)
        assertTrue(distPoles.isFinite())
        assertTrue(!distPoles.isNaN())
        assertEquals(Math.PI * 6_371_000.0, distPoles, 1000.0)
    }
}
