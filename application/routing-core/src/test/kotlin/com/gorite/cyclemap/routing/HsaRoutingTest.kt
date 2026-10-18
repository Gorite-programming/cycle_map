package com.gorite.cyclemap.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HsaRoutingTest {
    @Test
    fun modeDefaultsAreDistinctAndValidated() {
        val optimal = HsaOptions.fromMode(HsaMode.O)
        val balanced = HsaOptions.fromMode(HsaMode.B)
        val fast = HsaOptions.fromMode(HsaMode.F)

        assertTrue(optimal.beamWidth > balanced.beamWidth)
        assertTrue(balanced.targetSegmentKm < fast.targetSegmentKm)
        assertTrue(fast.maxWorkers < balanced.maxWorkers)
        assertTrue(balanced.beamWidth <= balanced.maxCandidates)
    }

    @Test
    fun hsaAndAStarCanBeComparedOnTheSameGraph() {
        val nodes = (1L..5L).associateWith { GraphNode(it, 34.0 + it * 0.01, 131.0) }
        val graph = RoadGraph(
            nodes,
            mapOf(
                1L to listOf(GraphEdge(1, 2, 1.0), GraphEdge(1, 3, 5.0)),
                2L to listOf(GraphEdge(2, 3, 1.0)),
                3L to listOf(GraphEdge(3, 4, 1.0)),
                4L to listOf(GraphEdge(4, 5, 1.0)),
            ),
        )

        val comparison = compareAStarAndHsa(
            graph = graph,
            startId = 1L,
            goalId = 5L,
            options = HsaOptions.fromMode(
                HsaMode.F,
                minSegmentKm = 1.0,
                targetSegmentKm = 100.0,
                maxSegmentKm = 100.0,
            ),
        )

        assertEquals(1L, comparison.aStar.nodeIds.first())
        assertEquals(5L, comparison.aStar.nodeIds.last())
        assertTrue(comparison.hsa.isReachable)
        assertFalse(comparison.hsaMetrics.usedFallback)
        assertTrue(comparison.costDifferencePercent.isFinite())
        assertTrue(comparison.distanceDifferenceMeters.isFinite())
    }

    @Test
    fun hsaRoutesAcrossMultipleDistanceSegments() {
        val nodes = mapOf(
            1L to GraphNode(1L, 34.0, 131.0),
            2L to GraphNode(2L, 34.4, 131.0),
            3L to GraphNode(3L, 34.8, 131.0),
        )
        val graph = RoadGraph(
            nodes,
            mapOf(
                1L to listOf(GraphEdge(1, 2, 10.0)),
                2L to listOf(GraphEdge(2, 3, 10.0)),
            ),
        )

        val router = HsaStarRouter(graph)
        val result = router.route(
            1L,
            3L,
            HsaOptions.fromMode(
                HsaMode.F,
                minSegmentKm = 0.1,
                targetSegmentKm = 1.0,
                maxSegmentKm = 1.0,
                maxTimePerSegmentMs = 2_000,
            ),
        )

        assertEquals(listOf(1L, 2L, 3L), result.nodeIds)
        assertTrue(router.lastMetrics.usedFallback.not())
    }

    @Test
    fun benchmarkReportsAStarAndSelectedHsaModes() {
        val nodes = (1L..4L).associateWith { GraphNode(it, 34.0 + it * 0.001, 131.0) }
        val graph = RoadGraph(
            nodes,
            mapOf(
                1L to listOf(GraphEdge(1, 2, 10.0)),
                2L to listOf(GraphEdge(2, 3, 10.0)),
                3L to listOf(GraphEdge(3, 4, 10.0)),
            ),
        )

        val report = benchmarkRoutingAlgorithms(
            graph = graph,
            startId = 1L,
            goalId = 4L,
            warmupRuns = 0,
            measuredRuns = 2,
            modes = listOf(HsaMode.B, HsaMode.F),
        )

        assertEquals(listOf("A*", "HSA*-B", "HSA*-F"), report.results.map { it.algorithm })
        assertTrue(report.results.all { it.samples.size == 2 })
        assertTrue(report.results.all { it.samples.all { sample -> sample.reachable } })
        assertTrue(report.formatTable().contains("medianSeconds"))
    }
}