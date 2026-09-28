package com.gorite.cyclemap.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AltRoutingTest {
    private fun graph(): RoadGraph {
        val nodes = (1L..6L).associateWith { id ->
            GraphNode(id, 34.0 + id * 0.01, 131.0 + (id % 2) * 0.01)
        }
        return RoadGraph(
            nodes,
            mapOf(
                1L to listOf(GraphEdge(1, 2, 10.0), GraphEdge(1, 3, 25.0)),
                2L to listOf(GraphEdge(2, 3, 10.0), GraphEdge(2, 4, 40.0)),
                3L to listOf(GraphEdge(3, 4, 10.0), GraphEdge(3, 5, 30.0)),
                4L to listOf(GraphEdge(4, 5, 10.0), GraphEdge(4, 6, 30.0)),
                5L to listOf(GraphEdge(5, 6, 10.0)),
            ),
        )
    }

    @Test
    fun altAndBidirectionalVariantsPreserveBaselineRouteQuality() {
        val graph = graph()
        val baselineRouter = AStarRouter(graph)
        val baseline = baselineRouter.route(1L, 6L)
        val index = LandmarkIndex.build(graph, 4)
        val alt = AltRouter(graph, DistanceCostModel, index).route(1L, 6L)
        val bidirectional = BidirectionalRouter(graph, DistanceCostModel).route(1L, 6L)
        val bidirectionalAlt = BidirectionalRouter(graph, DistanceCostModel, index).route(1L, 6L)

        assertEquals(baseline.totalCost, alt.route.totalCost, "ALT path=${alt.route.nodeIds}")
        assertEquals(baseline.totalCost, bidirectional.route.totalCost, "bidirectional path=${bidirectional.route.nodeIds}")
        assertEquals(baseline.totalCost, bidirectionalAlt.route.totalCost, "bidirectional ALT path=${bidirectionalAlt.route.nodeIds}")
        assertTrue(alt.route.isReachable)
        assertTrue(bidirectional.route.isReachable)
        assertTrue(bidirectionalAlt.route.isReachable)
        assertTrue(index.estimatedBytes > 0L)
    }

    @Test
    fun variantBenchmarkReportsFourAlgorithmsAndSeparateErrors() {
        val report = benchmarkRoutingVariants(graph(), 1L, 6L, landmarkCounts = listOf(4))

        assertEquals(listOf("A*", "A*+ALT(4)", "Bidirectional A*", "Bidirectional A*+ALT(16)"), report.measurements.map { it.algorithm })
        assertTrue(report.measurements.all { it.reachable })
        assertTrue(report.measurements.all { it.distanceErrorPercent == 0.0 })
        assertTrue(report.measurements.all { it.costErrorPercent == 0.0 })
    }
}
