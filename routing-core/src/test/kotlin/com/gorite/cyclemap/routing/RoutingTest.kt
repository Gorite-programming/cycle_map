package com.gorite.cyclemap.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutingTest {
    @Test
    fun findsLowestCostPath() {
        val nodes = (1L..4L).associateWith { GraphNode(it, 34.0, 131.0) }
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
            mapOf(1L to GraphNode(1, 34.0, 131.0), 2L to GraphNode(2, 34.1, 131.0)),
            emptyMap(),
        )
        val result = AStarRouter(graph).route(1, 2)
        assertTrue(!result.isReachable)
    }
}
