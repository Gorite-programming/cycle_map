package com.gorite.cyclemap.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RouteGoldenTest: plans.md §6 P1-1, §8
 * 3モード(Bicycle, Walk, Car) × 固定地点ペアの結果をゴールデン値(コスト・距離・ノード列)と検証
 */
class RouteGoldenTest {

    private fun createGoldenGraph(): RoadGraph {
        // グラフ構成:
        // 1(始点) -> 2(motorway) -> 5 -> 6(終点)
        // 1 -> 3(cycleway/footway) -> 5 -> 6
        // 1 -> 4(secondary) -> 5 -> 6
        val nodes = mapOf(
            1L to GraphNode(1L, 34.390, 132.450),
            2L to GraphNode(2L, 34.395, 132.455),
            3L to GraphNode(3L, 34.392, 132.452),
            4L to GraphNode(4L, 34.388, 132.453),
            5L to GraphNode(5L, 34.394, 132.460),
            6L to GraphNode(6L, 34.396, 132.465)
        )
        val outgoing = mapOf(
            1L to listOf(
                GraphEdge(1L, 2L, 800.0, roadType = "motorway"),
                GraphEdge(1L, 3L, 750.0, roadType = "cycleway"),
                GraphEdge(1L, 4L, 900.0, roadType = "secondary")
            ),
            2L to listOf(GraphEdge(2L, 5L, 800.0, roadType = "motorway")),
            3L to listOf(GraphEdge(3L, 5L, 750.0, roadType = "footway")),
            4L to listOf(GraphEdge(4L, 5L, 900.0, roadType = "secondary")),
            5L to listOf(GraphEdge(5L, 6L, 500.0, roadType = "residential"))
        )
        return RoadGraph(nodes, outgoing)
    }

    @Test
    fun testBicycleGoldenRoute() {
        val graph = createGoldenGraph()
        val finder = PenaltyMultiRouteFinder(graph)
        val routes = finder.findRoutes(1L, 6L, TravelMode.Bicycle, maxRoutes = 2)

        assertTrue("Bicycle should find at least 1 valid route", routes.isNotEmpty())
        val primary = routes.first()
        // 自転車は motorway (2) を除外し、secondary (4) を通る安全ルートを選択
        assertEquals(listOf(1L, 4L, 5L, 6L), primary.nodeIds)
        assertEquals(2300.0, primary.distanceMeters, 1.0)
    }

    @Test
    fun testWalkGoldenRoute() {
        val graph = createGoldenGraph()
        val finder = PenaltyMultiRouteFinder(graph)
        val routes = finder.findRoutes(1L, 6L, TravelMode.Walk, maxRoutes = 2)

        assertTrue("Walk should find at least 1 route", routes.isNotEmpty())
        val primary = routes.first()
        // 徒歩は secondary (4) の高ペナルティを避け、cycleway/footway (3) を通る歩行者ルートを選択
        assertEquals(listOf(1L, 3L, 5L, 6L), primary.nodeIds)
        assertEquals(2000.0, primary.distanceMeters, 1.0)
    }

    @Test
    fun testCarGoldenRoute() {
        val graph = createGoldenGraph()
        val finder = PenaltyMultiRouteFinder(graph)
        val routes = finder.findRoutes(1L, 6L, TravelMode.Car, maxRoutes = 2)

        assertTrue("Car should find at least 1 route", routes.isNotEmpty())
        val primary = routes.first()
        // 車は cycleway/footway (3) を通れず、高速道路 motorway (2) を最優先
        assertEquals(listOf(1L, 2L, 5L, 6L), primary.nodeIds)
        assertEquals(2100.0, primary.distanceMeters, 1.0)
    }
}
