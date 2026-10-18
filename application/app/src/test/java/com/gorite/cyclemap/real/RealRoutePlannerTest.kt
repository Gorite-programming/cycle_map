package com.gorite.cyclemap.real

import com.gorite.cyclemap.contract.TravelModeType
import com.gorite.cyclemap.routing.GraphEdge
import com.gorite.cyclemap.routing.GraphNode
import com.gorite.cyclemap.routing.RoadGraph
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealRoutePlannerTest {

    @Test
    fun planRoutes_emptyGraph_returnsEmptyListWithoutException() = runBlocking {
        val emptyGraph = RoadGraph(emptyMap(), emptyMap())
        val planner = RealRoutePlanner(
            graphProvider = { null },
            memoryGraphProvider = { emptyGraph }
        )

        val routes = planner.planRoutes(
            startLat = 34.39, startLon = 132.45,
            destLat = 34.40, destLon = 132.46,
            travelMode = TravelModeType.Bicycle
        )
        assertTrue(routes.isEmpty())
    }

    @Test
    fun planRoutes_travelModes_returnValidOptions() = runBlocking {
        // ノード 1 -> 2 -> 3
        val n1 = GraphNode(1L, 34.390, 132.450)
        val n2 = GraphNode(2L, 34.395, 132.455)
        val n3 = GraphNode(3L, 34.400, 132.460)
        val nodes = mapOf(1L to n1, 2L to n2, 3L to n3)

        val e1 = GraphEdge(1L, 2L, 600.0, "secondary", false, null)
        val e2 = GraphEdge(2L, 3L, 600.0, "secondary", false, null)
        val outgoing = mapOf(
            1L to listOf(e1),
            2L to listOf(e2),
            3L to emptyList()
        )
        val graph = RoadGraph(nodes, outgoing)

        val planner = RealRoutePlanner(
            graphProvider = { null },
            memoryGraphProvider = { graph }
        )

        // 自転車
        val bikeRoutes = planner.planRoutes(34.390, 132.450, 34.400, 132.460, TravelModeType.Bicycle)
        assertFalse(bikeRoutes.isEmpty())
        assertEquals(TravelModeType.Bicycle, bikeRoutes.first().travelMode)
        assertTrue(bikeRoutes.first().isPrimary)
        assertTrue(bikeRoutes.first().durationSeconds > 0)

        // 徒歩
        val walkRoutes = planner.planRoutes(34.390, 132.450, 34.400, 132.460, TravelModeType.Walk)
        assertFalse(walkRoutes.isEmpty())
        assertEquals(TravelModeType.Walk, walkRoutes.first().travelMode)
        assertTrue(walkRoutes.first().durationSeconds > bikeRoutes.first().durationSeconds)

        // 自動車
        val carRoutes = planner.planRoutes(34.390, 132.450, 34.400, 132.460, TravelModeType.Car)
        assertFalse(carRoutes.isEmpty())
        assertEquals(TravelModeType.Car, carRoutes.first().travelMode)
        assertTrue(carRoutes.first().durationSeconds < bikeRoutes.first().durationSeconds)
    }

    @Test
    fun planRoutes_duplicateRouteExclusion_avoidsOverlappingRoutes() = runBlocking {
        // 2つの並行経路: 1->2->3 と 1->4->3
        val n1 = GraphNode(1L, 34.390, 132.450)
        val n2 = GraphNode(2L, 34.395, 132.455)
        val n3 = GraphNode(3L, 34.400, 132.460)
        val n4 = GraphNode(4L, 34.395, 132.445)
        val nodes = mapOf(1L to n1, 2L to n2, 3L to n3, 4L to n4)

        val e1 = GraphEdge(1L, 2L, 600.0, "secondary", false, null)
        val e2 = GraphEdge(2L, 3L, 600.0, "secondary", false, null)
        val e3 = GraphEdge(1L, 4L, 650.0, "tertiary", false, null)
        val e4 = GraphEdge(4L, 3L, 650.0, "tertiary", false, null)

        val outgoing = mapOf(
            1L to listOf(e1, e3),
            2L to listOf(e2),
            4L to listOf(e4),
            3L to emptyList()
        )
        val graph = RoadGraph(nodes, outgoing)

        val planner = RealRoutePlanner(
            graphProvider = { null },
            memoryGraphProvider = { graph }
        )

        val routes = planner.planRoutes(34.390, 132.450, 34.400, 132.460, TravelModeType.Bicycle)
        assertTrue(routes.size in 1..2)
        if (routes.size > 1) {
            assertFalse(routes[0].id == routes[1].id)
        }
    }
}
