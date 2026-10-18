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

    @Test
    fun cyclingCostModel_asymmetricGradePenaltyAndMinimumClamp() {
        val flatPrimary = GraphEdge(1, 2, 100.0, "primary", gradePercent = 0.0)
        val uphillPrimary = GraphEdge(1, 2, 100.0, "primary", gradePercent = 5.0)
        val gentleDownhillPrimary = GraphEdge(1, 2, 100.0, "primary", gradePercent = -3.0)
        val steepDownhillPrimary = GraphEdge(1, 2, 100.0, "primary", gradePercent = -10.0)

        val costFlat = CyclingCostModel.cost(flatPrimary)!!
        val costUphill = CyclingCostModel.cost(uphillPrimary)!!
        val costGentleDown = CyclingCostModel.cost(gentleDownhillPrimary)!!
        val costSteepDown = CyclingCostModel.cost(steepDownhillPrimary)!!

        // 平坦: 100 * 0.90 * 1.0 = 90.0
        assertEquals(90.0, costFlat, 1e-6)

        // 上りはコスト増
        assertTrue(costUphill > costFlat)

        // 緩やかな下りは平坦よりも低コスト（ただし下限0.90を下回らないようクランプ）
        // 0.90 * (1.0 - 0.06) = 0.846 -> clamp to 0.90
        assertEquals(90.0, costGentleDown, 1e-6)

        // 急坂下りはブレーキ減速ペナルティで緩い下りよりコスト増
        assertTrue(costSteepDown > costGentleDown)

        // どのような下り坂でも minimumCostMultiplier (0.90 * 距離) 以上を保証
        val crazyDownhill = GraphEdge(1, 2, 100.0, "primary", gradePercent = -30.0)
        assertTrue(CyclingCostModel.cost(crazyDownhill)!! >= 100.0 * CyclingCostModel.minimumCostMultiplier())
    }

    @Test
    fun geodesicDistanceMeters_accurateAndHandlesEdgeCases() {
        // 同一地点
        assertEquals(0.0, geodesicDistanceMeters(34.178, 131.473, 34.178, 131.473), 1e-6)

        // 山口駅〜防府駅間 (~14.7km)
        // 緯度経度: (34.1718, 131.4700) -> (34.0560, 131.5694)
        val geoDist = geodesicDistanceMeters(34.1718, 131.4700, 34.0560, 131.5694)
        val sphereDist = haversineMeters(34.1718, 131.4700, 34.0560, 131.5694)

        // 両者とも約15.7kmで、差は0.3%（楕円体補正による数十メートル以内）
        assertEquals(sphereDist, geoDist, sphereDist * 0.005)
        assertTrue(geoDist > 15_000.0 && geoDist < 16_500.0)
    }
}
