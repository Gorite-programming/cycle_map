package com.gorite.cyclemap.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TurnClassifier のテスト。座標はすべて異なる点を使う
 * (全ノード同一座標ではヒューリスティックが検証できない教訓から)。
 * 基準点は山口県央 (34.10, 131.40) 付近。0.001 ≒ 111m。
 *
 * 方位の約束: 北=0° 東=90° 南=180° 西=270°、turn は進行方向基準 (+が右)。
 * 以下のテストは南向き (bearing 180°) に B へ入り、B から転じる。
 * 南向きの場合、東 (lon+) が左・西 (lon-) が右になる。
 */
class TurnClassifierTest {

    private data class Pt(val id: Long, val lat: Double, val lon: Double)

    private var nextId = 1L
    private fun pt(lat: Double, lon: Double) = Pt(nextId++, lat, lon)

    /** 双方向 edge を張った小規模グラフを組み立てる。types は順方向の roadType 列。 */
    private fun buildGraph(
        route: List<Pt>,
        types: List<String>,
        extraEdges: List<Triple<Pt, Pt, String>> = emptyList(),
    ): RoadGraph {
        require(route.size - 1 == types.size)
        val nodes = route.associate { it.id to GraphNode(it.id, it.lat, it.lon) }.toMutableMap()
        val outgoing = mutableMapOf<Long, MutableList<GraphEdge>>()
        fun add(from: Pt, to: Pt, type: String) {
            val distance = haversineMeters(from.lat, from.lon, to.lat, to.lon)
            outgoing.getOrPut(from.id) { mutableListOf() } += GraphEdge(from.id, to.id, distance, type)
        }
        for (i in route.indices.drop(1)) {
            add(route[i - 1], route[i], types[i - 1])
            add(route[i], route[i - 1], types[i - 1])
        }
        for ((from, to, type) in extraEdges) {
            nodes.getOrPut(to.id) { GraphNode(to.id, to.lat, to.lon) }
            add(from, to, type)
        }
        return RoadGraph(nodes, outgoing)
    }

    private fun classify(graph: RoadGraph, route: List<Pt>): List<RouteInstruction> {
        val result = TurnClassifier.classifyRoute(graph, route.map { it.id })
        for (instruction in result) {
            println(
                instruction.debugLine(
                    currentEdge = "e${instruction.routeIndex - 1}->${instruction.routeIndex}",
                    nextEdge = "e${instruction.routeIndex}->${instruction.routeIndex + 1}",
                    junctionType = "deg${instruction.junctionBranches}",
                    icon = instruction.type.name.lowercase(),
                ),
            )
        }
        return result
    }

    @Test
    fun straightWithRoadTypeChange() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.099, 131.400)
        val graph = buildGraph(listOf(a, b, c), listOf("primary", "secondary"))
        val result = classify(graph, listOf(a, b, c))
        assertEquals(listOf(InstructionType.STRAIGHT), result.map { it.type })
    }

    @Test
    fun pureContinuationIsSuppressed() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.099, 131.4002) // 約 -9° の緩いカーブ
        val graph = buildGraph(listOf(a, b, c), listOf("residential", "residential"))
        assertTrue(classify(graph, listOf(a, b, c)).isEmpty())
    }

    @Test
    fun leftTurn() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.100, 131.401) // 東へ: turn -90°
        val graph = buildGraph(listOf(a, b, c), listOf("residential", "residential"))
        assertEquals(listOf(InstructionType.LEFT_TURN), classify(graph, listOf(a, b, c)).map { it.type })
    }

    @Test
    fun rightTurn() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.100, 131.399) // 西へ: turn +90°
        val graph = buildGraph(listOf(a, b, c), listOf("residential", "residential"))
        assertEquals(listOf(InstructionType.RIGHT_TURN), classify(graph, listOf(a, b, c)).map { it.type })
    }

    @Test
    fun sharpLeftOnSameRoad() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.1005, 131.4009) // turn 約 -124°
        val graph = buildGraph(listOf(a, b, c), listOf("residential", "residential"))
        val result = classify(graph, listOf(a, b, c))
        assertEquals(listOf(InstructionType.SHARP_LEFT), result.map { it.type })
    }

    @Test
    fun sharpRightOnSameRoad() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.1005, 131.3991) // turn 約 +124°
        val graph = buildGraph(listOf(a, b, c), listOf("residential", "residential"))
        val result = classify(graph, listOf(a, b, c))
        assertEquals(listOf(InstructionType.SHARP_RIGHT), result.map { it.type })
    }

    @Test
    fun uTurnLeft() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.1008, 131.4002) // turn 約 -168°
        val graph = buildGraph(listOf(a, b, c), listOf("residential", "tertiary"))
        assertEquals(listOf(InstructionType.U_TURN_LEFT), classify(graph, listOf(a, b, c)).map { it.type })
    }

    @Test
    fun uTurnRight() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.1008, 131.3998) // turn 約 +168°
        val graph = buildGraph(listOf(a, b, c), listOf("residential", "tertiary"))
        assertEquals(listOf(InstructionType.U_TURN_RIGHT), classify(graph, listOf(a, b, c)).map { it.type })
    }

    @Test
    fun forkUpLeft() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.099, 131.4002) // turn 約 -9° (東=左側)
        val d = pt(34.099, 131.3994) // 非選択枝 turn 約 +27° (非対称 → Y字にしない)
        val graph = buildGraph(
            listOf(a, b, c), listOf("primary", "primary"),
            extraEdges = listOf(Triple(b, d, "primary"), Triple(d, b, "primary")),
        )
        assertEquals(listOf(InstructionType.FORK_UP_LEFT), classify(graph, listOf(a, b, c)).map { it.type })
    }

    @Test
    fun forkUpRight() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.099, 131.3998) // turn 約 +9° (西=右側)
        val d = pt(34.099, 131.4006) // 非選択枝 turn 約 -27° (非対称 → Y字にしない)
        val graph = buildGraph(
            listOf(a, b, c), listOf("primary", "primary"),
            extraEdges = listOf(Triple(b, d, "primary"), Triple(d, b, "primary")),
        )
        assertEquals(listOf(InstructionType.FORK_UP_RIGHT), classify(graph, listOf(a, b, c)).map { it.type })
    }

    @Test
    fun diagonalForkLeft() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.099, 131.4004) // turn 約 -18°
        val d = pt(34.099, 131.3986) // 非選択枝 turn 約 +49° (非対称 → Y字にしない)
        val graph = buildGraph(
            listOf(a, b, c), listOf("primary", "primary"),
            extraEdges = listOf(Triple(b, d, "primary"), Triple(d, b, "primary")),
        )
        assertEquals(listOf(InstructionType.DIAGONAL_FORK_LEFT), classify(graph, listOf(a, b, c)).map { it.type })
    }

    @Test
    fun forkDownRight() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.099, 131.3995) // turn 約 +23° (右側へ)
        val d = pt(34.1012, 131.3992) // 非選択枝は後方に剥がれる (turn 約 +151°、T字域外)
        val graph = buildGraph(
            listOf(a, b, c), listOf("primary", "primary"),
            extraEdges = listOf(Triple(b, d, "primary"), Triple(d, b, "primary")),
        )
        assertEquals(listOf(InstructionType.FORK_DOWN_RIGHT), classify(graph, listOf(a, b, c)).map { it.type })
    }

    @Test
    fun yJunction() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.099, 131.4005) // turn 約 -23°
        val d = pt(34.099, 131.3995) // 非選択枝 turn 約 +23° (対称)
        val graph = buildGraph(
            listOf(a, b, c), listOf("primary", "primary"),
            extraEdges = listOf(Triple(b, d, "primary"), Triple(d, b, "primary")),
        )
        assertEquals(listOf(InstructionType.Y_JUNCTION), classify(graph, listOf(a, b, c)).map { it.type })
    }

    @Test
    fun tJunction() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.100, 131.401) // 東へ turn -90°
        val d = pt(34.100, 131.399) // 西へ turn +90°
        val graph = buildGraph(
            listOf(a, b, c), listOf("residential", "residential"),
            extraEdges = listOf(Triple(b, d, "residential"), Triple(d, b, "residential")),
        )
        assertEquals(listOf(InstructionType.T_JUNCTION), classify(graph, listOf(a, b, c)).map { it.type })
    }

    @Test
    fun crossJunction() {
        // 十字路を左折で通過する。
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.100, 131.401) // 東へ turn -90°
        val d = pt(34.100, 131.399)
        val e = pt(34.099, 131.400)
        val graph = buildGraph(
            listOf(a, b, c), listOf("residential", "residential"),
            extraEdges = listOf(
                Triple(b, d, "residential"), Triple(d, b, "residential"),
                Triple(b, e, "residential"), Triple(e, b, "residential"),
            ),
        )
        assertEquals(listOf(InstructionType.CROSS_JUNCTION), classify(graph, listOf(a, b, c)).map { it.type })
    }

    @Test
    fun straightThroughCrossIsSilent() {
        // 十字路を直進で通過する場合は案内しない (側道扱いの枝のみ)。
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.099, 131.400) // 直進
        val d = pt(34.100, 131.401)
        val e = pt(34.100, 131.399)
        val graph = buildGraph(
            listOf(a, b, c), listOf("residential", "residential"),
            extraEdges = listOf(
                Triple(b, d, "residential"), Triple(d, b, "residential"),
                Triple(b, e, "residential"), Triple(e, b, "residential"),
            ),
        )
        assertTrue(classify(graph, listOf(a, b, c)).isEmpty())
    }

    @Test
    fun forkBoth() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.099, 131.400) // 直進
        val d = pt(34.099, 131.3994) // turn 約 +27°
        val e = pt(34.099, 131.4006) // turn 約 -27°
        val graph = buildGraph(
            listOf(a, b, c), listOf("primary", "primary"),
            extraEdges = listOf(
                Triple(b, d, "primary"), Triple(d, b, "primary"),
                Triple(b, e, "primary"), Triple(e, b, "primary"),
            ),
        )
        assertEquals(listOf(InstructionType.FORK_BOTH), classify(graph, listOf(a, b, c)).map { it.type })
    }

    @Test
    fun multiJunction() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.099, 131.400)
        val others = listOf(
            pt(34.100, 131.401),
            pt(34.100, 131.399),
            pt(34.0995, 131.4005),
            pt(34.0995, 131.3995),
        )
        val extras = others.flatMap { listOf(Triple(b, it, "residential"), Triple(it, b, "residential")) }
        val graph = buildGraph(listOf(a, b, c), listOf("residential", "residential"), extras)
        assertEquals(listOf(InstructionType.MULTI_JUNCTION), classify(graph, listOf(a, b, c)).map { it.type })
    }

    @Test
    fun mergeFromMinorToMajor() {
        val a = pt(34.1010, 131.4000)
        val b = pt(34.1000, 131.4000)
        val c = pt(34.0990, 131.4002) // 緩い流入 (turn 約 -11°)
        val d = pt(34.0995, 131.3990)
        val graph = buildGraph(
            listOf(a, b, c), listOf("service", "primary"),
            extraEdges = listOf(Triple(b, d, "residential"), Triple(d, b, "residential")),
        )
        assertEquals(listOf(InstructionType.MERGE), classify(graph, listOf(a, b, c)).map { it.type })
    }

    @Test
    fun rampEntryAndExit() {
        // 入口: primary → primary_link
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.099, 131.4005)
        val graphIn = buildGraph(listOf(a, b, c), listOf("primary", "primary_link"))
        assertEquals(listOf(InstructionType.RAMP_ENTRY), classify(graphIn, listOf(a, b, c)).map { it.type })
        // 出口: primary_link → primary (turn 約 -45°)
        val d = pt(34.101, 131.410)
        val e = pt(34.100, 131.410)
        val f = pt(34.0993, 131.4107)
        val graphOut = buildGraph(listOf(d, e, f), listOf("primary_link", "primary"))
        assertEquals(listOf(InstructionType.RAMP_EXIT), classify(graphOut, listOf(d, e, f)).map { it.type })
    }

    @Test
    fun sideRoadEnterAndExit() {
        // 側道へ: 幹線から明確にそれる (turn 約 -33°)
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.099, 131.4008)
        val graphIn = buildGraph(listOf(a, b, c), listOf("primary", "service"))
        assertEquals(listOf(InstructionType.SIDE_ROAD_ENTER), classify(graphIn, listOf(a, b, c)).map { it.type })
        // 本線へ: 側道から明確に戻る (turn 約 +33°)
        val d = pt(34.101, 131.410)
        val e = pt(34.100, 131.410)
        val f = pt(34.099, 131.4092)
        val graphOut = buildGraph(listOf(d, e, f), listOf("service", "primary"))
        assertEquals(listOf(InstructionType.SIDE_ROAD_EXIT), classify(graphOut, listOf(d, e, f)).map { it.type })
    }

    @Test
    fun roundaboutLoop() {
        // 同一符号の転回 (~-50°) が5連続し、進入点 A にアーム枝がある = 周回として検出。
        // 進入は接線方向から入り、Uターン帯には触れない (実ラウンドアバウトの構造を再現)。
        val s = pt(34.10000, 131.39880)
        val a = pt(34.10000, 131.40000)
        val b = pt(34.10000, 131.40120)
        val c = pt(34.10080, 131.40200)
        val d = pt(34.10160, 131.40120)
        val e = pt(34.10160, 131.40000)
        val f = pt(34.10080, 131.39950)
        val g = pt(34.09990, 131.39990)
        val arm = pt(34.09950, 131.40000)
        val route = listOf(s, a, b, c, d, e, f, g)
        val graph = buildGraph(
            route,
            listOf("residential", "residential", "residential", "residential", "residential", "residential", "residential"),
            extraEdges = listOf(Triple(a, arm, "residential"), Triple(arm, a, "residential")),
        )
        val result = classify(graph, route)
        assertTrue(result.any { it.type == InstructionType.ROUNDABOUT }, "expected ROUNDABOUT, got $result")
        assertEquals(1, result.count { it.type == InstructionType.ROUNDABOUT })
    }

    @Test
    fun windingAfterTurnIsNotRoundabout() {
        // 交差点での転回後に同方向の緩いカーブが4連続してもラウンドアバウトにしない。
        // 祇園→伴ルートの実誤検出2件と同型 (回数5未満・合計108°以下)。
        val s = pt(34.10200, 131.40000)
        val a = pt(34.10100, 131.40000)
        val b = pt(34.10020, 131.39919)
        val c = pt(34.09976, 131.39805)
        val d = pt(34.09976, 131.39684)
        val e = pt(34.10020, 131.39570)
        val f = pt(34.10100, 131.39492)
        val g = pt(34.10180, 131.39414)
        val x = pt(34.10100, 131.40100)
        val route = listOf(s, a, b, c, d, e, f, g)
        val graph = buildGraph(
            route,
            listOf("residential", "residential", "residential", "residential", "residential", "residential", "residential"),
            extraEdges = listOf(Triple(a, x, "residential"), Triple(x, a, "residential")),
        )
        val result = classify(graph, route)
        assertTrue(result.none { it.type == InstructionType.ROUNDABOUT }, "unexpected ROUNDABOUT in $result")
        assertEquals(
            listOf(
                InstructionType.T_JUNCTION,
                InstructionType.RIGHT_TURN,
                InstructionType.RIGHT_TURN,
                InstructionType.RIGHT_TURN,
                InstructionType.RIGHT_TURN,
            ),
            result.map { it.type },
        )
    }

    @Test
    fun distantBendsFusedByLongStraightIsNotRoundabout() {
        // 3×-15° → 直進10ノード (約400m) → 3×-20°。合計6回・-105°で進入アーム付き。
        // 旧ロジックでは融合して ROUNDABOUT になった (山道ワインディングの誤検出と同型)。
        // 中立ギャップ上限 (3) で分断され、各3回ずつでは成立しない。
        // 先頭2点は直進アプローチ (進入アームを routeIndex>=1 に置くため)。
        val pts = listOf(
            pt(34.10000, 131.39920),
            pt(34.10000, 131.39960),
            pt(34.10000, 131.40000),
            pt(34.10000, 131.40043),
            pt(34.10009, 131.40085),
            pt(34.10027, 131.40123),
            pt(34.10053, 131.40154),
            pt(34.10078, 131.40184),
            pt(34.10103, 131.40215),
            pt(34.10129, 131.40246),
            pt(34.10154, 131.40276),
            pt(34.10180, 131.40307),
            pt(34.10205, 131.40338),
            pt(34.10231, 131.40368),
            pt(34.10256, 131.40399),
            pt(34.10281, 131.40430),
            pt(34.10307, 131.40460),
            pt(34.10339, 131.40479),
            pt(34.10375, 131.40483),
            pt(34.10410, 131.40471),
            pt(34.10445, 131.40460),
        )
        val entryArm = pt(34.09960, 131.40000)
        val armNode = pts[2]
        val graph = buildGraph(
            pts,
            List(pts.size - 1) { "residential" },
            extraEdges = listOf(Triple(armNode, entryArm, "residential"), Triple(entryArm, armNode, "residential")),
        )
        val result = classify(graph, pts)
        assertTrue(result.none { it.type == InstructionType.ROUNDABOUT }, "unexpected ROUNDABOUT in $result")
    }

    @Test
    fun shortNeutralGapStillCirculates() {
        // 3×-15° → 直進3ノード → 3×-20° (ギャップ3は上限内)。融合して ROUNDABOUT のまま。
        // 上限の境界動作を固定する。
        val pts = listOf(
            pt(34.10000, 131.39920),
            pt(34.10000, 131.39960),
            pt(34.10000, 131.40000),
            pt(34.10000, 131.40043),
            pt(34.10009, 131.40085),
            pt(34.10027, 131.40123),
            pt(34.10053, 131.40154),
            pt(34.10078, 131.40184),
            pt(34.10103, 131.40215),
            pt(34.10129, 131.40246),
            pt(34.10161, 131.40264),
            pt(34.10197, 131.40268),
            pt(34.10232, 131.40257),
            pt(34.10267, 131.40245),
        )
        val entryArm = pt(34.09960, 131.40000)
        val armNode = pts[2]
        val graph = buildGraph(
            pts,
            List(pts.size - 1) { "residential" },
            extraEdges = listOf(Triple(armNode, entryArm, "residential"), Triple(entryArm, armNode, "residential")),
        )
        val result = classify(graph, pts)
        assertTrue(result.any { it.type == InstructionType.ROUNDABOUT }, "expected ROUNDABOUT, got $result")
        assertEquals(1, result.count { it.type == InstructionType.ROUNDABOUT })
    }

    @Test
    fun circulationWithoutArmsIsNotRoundabout() {
        // 同一符号の転回が3連続しても、分岐 (アーム) がなければラウンドアバウトにしない。
        // U字道路・二重線道路の幾何ループ対策 (広島実データの誤検出5件と同型)。
        val s = pt(34.10100, 131.40000)
        val a = pt(34.10000, 131.40000)
        val b = pt(34.09905, 131.40054)
        val c = pt(34.09837, 131.40151)
        val d = pt(34.09810, 131.40273)
        val e = pt(34.09783, 131.40395)
        val route = listOf(s, a, b, c, d, e)
        val graph = buildGraph(
            route,
            listOf("residential", "residential", "residential", "residential", "residential"),
        )
        val result = classify(graph, route)
        assertTrue(result.none { it.type == InstructionType.ROUNDABOUT }, "unexpected ROUNDABOUT in $result")
        assertEquals(
            listOf(InstructionType.LEFT_TURN, InstructionType.LEFT_TURN, InstructionType.LEFT_TURN),
            result.map { it.type },
        )
    }

    @Test
    fun consecutiveForks() {
        // 約67m間隔で2つの分岐が連続する。
        val s = pt(34.1010, 131.4000)
        val f1 = pt(34.1000, 131.4000)
        val m = pt(34.0997, 131.4000)
        val f2 = pt(34.0994, 131.4000)
        val g = pt(34.0989, 131.4000)
        val x1 = pt(34.0995, 131.4004)
        val x2 = pt(34.0989, 131.4005)
        val graph = buildGraph(
            listOf(s, f1, m, f2, g),
            listOf("primary", "primary", "primary", "primary"),
            extraEdges = listOf(
                Triple(f1, x1, "primary"), Triple(x1, f1, "primary"),
                Triple(f2, x2, "primary"), Triple(x2, f2, "primary"),
            ),
        )
        val result = classify(graph, listOf(s, f1, m, f2, g))
        assertTrue(
            result.any { it.type == InstructionType.CONSECUTIVE_FORK },
            "expected CONSECUTIVE_FORK, got ${result.map { it.type }}",
        )
    }

    @Test
    fun angleNormalizationAtBoundaries() {
        // 0°/360°境界: 北北西 (350°) → 北北東 (10°) は右への20°。
        assertEquals(20.0, signedTurnDegrees(350.0, 10.0), 1e-9)
        assertEquals(-20.0, signedTurnDegrees(10.0, 350.0), 1e-9)
        // ±180°境界: 厳密な反転は -180° (左Uターン既定)。
        assertEquals(-180.0, signedTurnDegrees(0.0, 180.0), 1e-9)
    }

    @Test
    fun debugLineFormat() {
        val instruction = RouteInstruction(
            type = InstructionType.RIGHT_TURN,
            nodeId = 5678,
            routeIndex = 3,
            distanceFromStartMeters = 120.0,
            turnAngleDegrees = 82.4,
            junctionBranches = 4,
            incomingRoadType = "residential",
            outgoingRoadType = "residential",
            reason = "three-branches",
        )
        val line = instruction.debugLine("1234", "5678", "CROSS", "turn_right")
        assertTrue(line.startsWith("[Instruction]"))
        assertTrue("angle=82.4" in line)
        assertTrue("instruction=RIGHT_TURN" in line)
        assertTrue("icon=turn_right" in line)
        assertTrue("reason=three-branches" in line)
    }

    @Test
    fun singleNodeRoute_returnsEmptyInstructionList() {
        val a = pt(34.101, 131.400)
        val graph = RoadGraph(mapOf(a.id to GraphNode(a.id, a.lat, a.lon)), emptyMap())
        val result = classify(graph, listOf(a))
        assertTrue(result.isEmpty())
    }

    @Test
    fun forkLeftTurn() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.099, 131.4007) // turn 約 -35°
        val graph = buildGraph(listOf(a, b, c), listOf("residential", "residential"))
        val result = classify(graph, listOf(a, b, c))
        assertEquals(listOf(InstructionType.LEFT_TURN), result.map { it.type })
    }

    @Test
    fun forkRightTurn() {
        val a = pt(34.101, 131.400)
        val b = pt(34.100, 131.400)
        val c = pt(34.099, 131.3993) // turn 約 +35°
        val graph = buildGraph(listOf(a, b, c), listOf("residential", "residential"))
        val result = classify(graph, listOf(a, b, c))
        assertEquals(listOf(InstructionType.RIGHT_TURN), result.map { it.type })
    }

    @Test
    fun turnAngleNormalization_oppositeDirectionIsNegative180() {
        val turn = signedTurnDegrees(0.0, 180.0)
        assertEquals(-180.0, turn, 1e-9)
    }
}
