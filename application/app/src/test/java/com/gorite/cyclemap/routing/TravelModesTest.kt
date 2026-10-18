package com.gorite.cyclemap.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelModesTest {

    @Test
    fun walkCostModelExcludesMotorwayAndIncludesPedestrian() {
        val motorwayEdge = GraphEdge(from = 1, to = 2, distanceMeters = 100.0, roadType = "motorway")
        assertNull(WalkCostModel.cost(motorwayEdge))

        val footwayEdge = GraphEdge(from = 1, to = 2, distanceMeters = 100.0, roadType = "footway")
        val footCost = WalkCostModel.cost(footwayEdge)
        assertNotNull(footCost)
        assertTrue(footCost!! >= 90.0) // 100 * 0.90
        assertEquals(0.90, WalkCostModel.minimumCostMultiplier(), 1e-6)
    }

    @Test
    fun carCostModelExcludesFootwayAndIncludesMotorway() {
        val footwayEdge = GraphEdge(from = 1, to = 2, distanceMeters = 100.0, roadType = "footway")
        assertNull(CarCostModel.cost(footwayEdge))

        val motorwayEdge = GraphEdge(from = 1, to = 2, distanceMeters = 100.0, roadType = "motorway")
        val motorCost = CarCostModel.cost(motorwayEdge)
        assertNotNull(motorCost)
        assertEquals(0.90, CarCostModel.minimumCostMultiplier(), 1e-6)
    }

    @Test
    fun penaltyMultiRouteFinderReturnsMultipleDistinctRoutes() {
        // Simple diamond graph:
        //       2 (secondary)
        //     /   \
        // 1 (start) 4 (goal)
        //     \   /
        //       3 (tertiary)
        val nodes = mapOf(
            1L to GraphNode(1L, 34.0, 132.0),
            2L to GraphNode(2L, 34.01, 132.01),
            3L to GraphNode(3L, 33.99, 132.01),
            4L to GraphNode(4L, 34.0, 132.02)
        )
        val outgoing = mapOf(
            1L to listOf(
                GraphEdge(1L, 2L, 1000.0, roadType = "secondary"),
                GraphEdge(1L, 3L, 1000.0, roadType = "tertiary")
            ),
            2L to listOf(GraphEdge(2L, 4L, 1000.0, roadType = "secondary")),
            3L to listOf(GraphEdge(3L, 4L, 1000.0, roadType = "tertiary"))
        )
        val graph = RoadGraph(nodes, outgoing)
        val finder = PenaltyMultiRouteFinder(graph)

        val routes = finder.findRoutes(1L, 4L, TravelMode.Bicycle, maxRoutes = 2)
        assertTrue(routes.size >= 2)
        assertEquals(listOf(1L, 2L, 4L), routes[0].nodeIds)
        assertEquals(listOf(1L, 3L, 4L), routes[1].nodeIds)
    }
}
