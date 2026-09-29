package com.gorite.cyclemap.routing

import kotlin.math.abs

/** ノードからの分岐候補1本。 */
data class BranchOption(
    val bearing: Double,
    val roadType: String,
    val isRouteChoice: Boolean,
    val distanceMeters: Double,
)

/** 1ジャンクション分の判定入力。方位はすべて度。 */
data class JunctionInput(
    val nodeId: Long,
    val routeIndex: Int,
    val distanceFromStartMeters: Double,
    /** 1つ前のルートノードID (戻り枝の特定用)。起点直後は null。 */
    val incomingNodeId: Long?,
    val incomingBearing: Double,
    val outgoingBearing: Double,
    val incomingRoadType: String,
    val outgoingRoadType: String,
    /** 全 outgoing edge (戻り枝・ルート選択枝を含む)。 */
    val options: List<BranchOption>,
)

/** OSM highway 文字列の分類ヘルパー。 */
internal object RoadTypes {
    fun isLink(type: String): Boolean = type.endsWith("_link")

    fun isArterial(type: String): Boolean =
        type in setOf("trunk", "primary", "secondary", "tertiary")

    /** 合流判定用の序列 (小さいほど上位)。link は本体と同格。 */
    fun rank(type: String): Int = when (type.removeSuffix("_link")) {
        "motorway" -> 0
        "trunk" -> 1
        "primary" -> 2
        "secondary" -> 3
        "tertiary" -> 4
        "unclassified" -> 5
        "residential" -> 6
        "service" -> 7
        "track" -> 8
        "path", "footway", "cycleway", "steps" -> 9
        else -> 5
    }
}

/**
 * Route → [RouteInstruction] 列。責務は判定のみで UI を知らない。
 *
 * 判定順序 (1ノード):
 * 1. Uターン (|turn| ≥ uTurn。左右は符号、厳密反転は LEFT に倒す)
 * 2. ランプ出入 (highway `*_link` の遷移。importer が roadType を保持するため利用可)
 * 3. 側道出入 (幹線 ↔ service の遷移)
 * 4. 合流 (下位 → 上位への緩い流入)
 * 5. 分岐・交差点 (戻り枝を除く前方枝数 F で分岐する)
 * ルート層: ラウンドアバウト (周回検出。幾何ループのみでは誤検出するため、
 * 同一符号の転回連続 + 出入アームを条件にする) と
 * 連続分岐 (近接する分岐系の統合) を後処理で適用。
 *
 * 注意: LANE_INCREASE/DECREASE はグラフに車線情報が無いため生成しない (将来用に型のみ存在)。
 */
object TurnClassifier {

    private const val U_TURN_THRESHOLD_DEGREES = 150.0
    /** 単一枝かつ道路種別が変化する場合のUターン判定閾値 (急カーブとしてではなく転回とみなすため浅めに設定) */
    private const val U_TURN_SINGLE_ARM_DEGREES = 100.0

    /** グラフ構造の最小ビュー。RoadGraph / LazyMappedRoadGraph の双方に適合する。 */
    interface GraphView {
        fun branches(nodeId: Long): List<RawBranch>
        fun roadType(fromId: Long, toId: Long): String
    }

    data class RawBranch(
        val targetNodeId: Long?,
        val bearing: Double,
        val roadType: String,
        val distanceMeters: Double,
    )

    fun classifyRoute(
        graph: RoadGraph,
        nodeIds: List<Long>,
        config: GuidanceConfig = GuidanceConfig(),
    ): List<RouteInstruction> {
        if (nodeIds.size < 3) return emptyList()
        val view = object : GraphView {
            override fun branches(nodeId: Long): List<RawBranch> {
                val from = graph.node(nodeId)
                return graph.outgoing[nodeId].orEmpty().mapNotNull { edge ->
                    val to = graph.node(edge.to)
                    val bearing = bearingBetween(from.latitude, from.longitude, to.latitude, to.longitude)
                        ?: return@mapNotNull null
                    RawBranch(edge.to, bearing, edge.roadType, edge.distanceMeters)
                }
            }

            override fun roadType(fromId: Long, toId: Long): String =
                graph.outgoing[fromId].orEmpty().firstOrNull { it.to == toId }?.roadType ?: "unknown"
        }
        val points = nodeIds.map { id ->
            val node = graph.node(id)
            RoutePoint(node.latitude, node.longitude)
        }
        return classifyInputs(buildInputs(view, nodeIds, points), config)
    }

    /**
     * 実機用: ノードID列と座標列から分類する。[branchesOf] は全 outgoing edge を返す。
     * ルート選択枝は次ノードIDの一致で特定し、ID不明枝のみ bearing で補完する。
     */
    fun classifyMappedRoute(
        nodeIds: List<Long>,
        coordinates: List<RoutePoint>,
        branchesOf: (nodeId: Long) -> List<MappedBranch>,
        config: GuidanceConfig = GuidanceConfig(),
        /** edgeTypes[i] = nodeIds[i]→nodeIds[i+1] の roadType。サイズ不一致時は無視される。 */
        edgeTypes: List<String> = emptyList(),
    ): List<RouteInstruction> {
        if (nodeIds.size < 3 || coordinates.size != nodeIds.size) return emptyList()
        val view = object : GraphView {
            override fun branches(nodeId: Long): List<RawBranch> =
                branchesOf(nodeId).map { RawBranch(null, it.bearing, it.roadType, it.distanceMeters) }

            override fun roadType(fromId: Long, toId: Long): String = "unknown"
        }
        val types = edgeTypes.takeIf { it.size == nodeIds.size - 1 }
        return classifyInputs(buildInputs(view, nodeIds, coordinates, types), config)
    }

    /** LazyMappedRoadGraph から取り出す分岐情報。 */
    data class MappedBranch(val bearing: Double, val roadType: String, val distanceMeters: Double)

    private fun buildInputs(
        view: GraphView,
        nodeIds: List<Long>,
        points: List<RoutePoint>,
        routeEdgeTypes: List<String>? = null,
    ): List<JunctionInput> {
        val cumulative = DoubleArray(nodeIds.size)
        for (i in 1 until nodeIds.size) {
            cumulative[i] = cumulative[i - 1] + haversineMeters(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude,
            )
        }
        return (1 until nodeIds.lastIndex).mapNotNull { i ->
            val incoming = bearingBetween(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude,
            ) ?: return@mapNotNull null
            val outgoing = bearingBetween(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude,
            ) ?: return@mapNotNull null
            val options = view.branches(nodeIds[i]).map { raw ->
                val isChoice = if (raw.targetNodeId != null) {
                    raw.targetNodeId == nodeIds[i + 1]
                } else {
                    abs(signedTurnDegrees(outgoing, raw.bearing)) <= 2.5
                }
                BranchOption(raw.bearing, raw.roadType, isChoice, raw.distanceMeters)
            }
            JunctionInput(
                nodeId = nodeIds[i],
                routeIndex = i,
                distanceFromStartMeters = cumulative[i],
                incomingNodeId = nodeIds[i - 1],
                incomingBearing = incoming,
                outgoingBearing = outgoing,
                incomingRoadType = routeEdgeTypes?.getOrNull(i - 1)
                    ?: view.roadType(nodeIds[i - 1], nodeIds[i]),
                outgoingRoadType = routeEdgeTypes?.getOrNull(i)
                    ?: view.roadType(nodeIds[i], nodeIds[i + 1]),
                options = options,
            )
        }
    }

    private fun classifyInputs(
        inputs: List<JunctionInput>,
        config: GuidanceConfig,
    ): List<RouteInstruction> {
        val circulation = detectCirculationEntries(inputs, config)
        val instructions = inputs.mapNotNull { input ->
            if (input.routeIndex in circulation.excluded) return@mapNotNull null
            if (input.routeIndex in circulation.entries) {
                return@mapNotNull RouteInstruction(
                    type = InstructionType.ROUNDABOUT,
                    nodeId = input.nodeId,
                    routeIndex = input.routeIndex,
                    distanceFromStartMeters = input.distanceFromStartMeters,
                    turnAngleDegrees = signedTurnDegrees(input.incomingBearing, input.outgoingBearing),
                    junctionBranches = input.options.size,
                    incomingRoadType = input.incomingRoadType,
                    outgoingRoadType = input.outgoingRoadType,
                    reason = "circulation-detected",
                )
            }
            classifyJunction(input, config)
        }
        return mergeConsecutiveForks(instructions, config)
    }

    // -- 単一ジャンクション ----------------------------------------------------

    fun classifyJunction(
        input: JunctionInput,
        config: GuidanceConfig = GuidanceConfig(),
    ): RouteInstruction? {
        val turn = signedTurnDegrees(input.incomingBearing, input.outgoingBearing)
        val magnitude = abs(turn)
        fun instruction(type: InstructionType, reason: String) = RouteInstruction(
            type = type,
            nodeId = input.nodeId,
            routeIndex = input.routeIndex,
            distanceFromStartMeters = input.distanceFromStartMeters,
            turnAngleDegrees = turn,
            junctionBranches = input.options.size,
            incomingRoadType = input.incomingRoadType,
            outgoingRoadType = input.outgoingRoadType,
            reason = reason,
        )

        // 1. Uターン (最優先・角度のみ)。左右は転回方向の符号で分け、
        // 厳密な反転 (±180°) は左側通行に合わせて LEFT に倒す。
        // 急カーブとの区別は classifyHairpinOrTurn (100–150°帯・同種道路継続) が担う。
        if (magnitude >= U_TURN_THRESHOLD_DEGREES) {
            val type = if (turn < 0.0) InstructionType.U_TURN_LEFT else InstructionType.U_TURN_RIGHT
            return instruction(type, "angle-reversal")
        }

        val incomingLink = RoadTypes.isLink(input.incomingRoadType)
        val outgoingLink = RoadTypes.isLink(input.outgoingRoadType)

        // 2. ランプ出入。
        if (!incomingLink && outgoingLink) {
            return instruction(InstructionType.RAMP_ENTRY, "onto-link")
        }
        if (incomingLink && !outgoingLink) {
            if (magnitude <= config.mergeMaxDegrees && input.options.size >= 2) {
                return instruction(InstructionType.MERGE, "ramp-merge")
            }
            return instruction(InstructionType.RAMP_EXIT, "off-link")
        }

        // 3. 側道出入 (幹線 ↔ service の明確な分岐のみ。緩い流入は合流扱い)。
        if (magnitude > config.mergeMaxDegrees &&
            RoadTypes.isArterial(input.incomingRoadType) && input.outgoingRoadType == "service"
        ) {
            return instruction(InstructionType.SIDE_ROAD_ENTER, "arterial-to-service")
        }
        if (magnitude > config.mergeMaxDegrees &&
            input.incomingRoadType == "service" && RoadTypes.isArterial(input.outgoingRoadType)
        ) {
            return instruction(InstructionType.SIDE_ROAD_EXIT, "service-to-arterial")
        }

        // 4. 合流 (下位 → 上位への緩い流入)。
        if (magnitude <= config.mergeMaxDegrees && input.options.size >= 2 &&
            RoadTypes.rank(input.incomingRoadType) > RoadTypes.rank(input.outgoingRoadType)
        ) {
            return instruction(InstructionType.MERGE, "minor-to-major")
        }

        // 5. 分岐・交差点。戻り枝 (来た方向) を除外した前方枝数 F で分岐する。
        val backTurnThreshold = 180.0 - config.straightMaxDegrees
        val forward = input.options.filter { option ->
            val optionTurn = signedTurnDegrees(input.incomingBearing, option.bearing)
            abs(optionTurn) < backTurnThreshold
        }
        val takenTurn = forward.firstOrNull { it.isRouteChoice }?.let {
            signedTurnDegrees(input.incomingBearing, it.bearing)
        } ?: turn
        val takenMagnitude = abs(takenTurn)

        // ルート選択枝が特定できない場合は角度帯のみで判定。純粋直進は通知しない。
        if (forward.none { it.isRouteChoice }) {
            if (takenMagnitude <= config.straightMaxDegrees) return null
            return instruction(classifyByAngleOnly(takenMagnitude, takenTurn), "angle-only")
        }

        // 直進維持で他枝がすべて側道程度に開く = そのまま通過。案内しない。
        // (分岐・十字路を直進で通過する場合は通知不要。曲がる場合のみ案内する)
        if (takenMagnitude <= config.straightMaxDegrees) {
            val others = forward.filter { !it.isRouteChoice }
            if (others.isNotEmpty() && others.all {
                    abs(signedTurnDegrees(input.incomingBearing, it.bearing)) > config.forkSpreadMaxDegrees
                }
            ) {
                return null
            }
        }

        return when (forward.size) {
            1 -> {
                // 案内不要な純粋継続 (分岐なし・同種道路・直進) は通知しない。
                if (takenMagnitude <= config.straightMaxDegrees &&
                    input.incomingRoadType == input.outgoingRoadType && input.options.size <= 2
                ) {
                    null
                } else {
                    instruction(classifyHairpinOrTurn(input, takenMagnitude, takenTurn, config), "single-branch")
                }
            }
            2 -> {
                val forkType = classifyFork(input, forward, takenTurn, config)
                if (forkType == null) null else instruction(forkType, "two-branches")
            }
            3 -> instruction(classifyThreeBranches(input, forward, takenTurn, config), "three-branches")
            else -> instruction(InstructionType.MULTI_JUNCTION, "many-branches")
        }
    }

    private fun classifyByAngleOnly(magnitude: Double, turn: Double): InstructionType = when {
        magnitude <= 20.0 -> InstructionType.STRAIGHT
        magnitude < 100.0 -> if (turn < 0.0) InstructionType.LEFT_TURN else InstructionType.RIGHT_TURN
        magnitude < U_TURN_THRESHOLD_DEGREES -> if (turn < 0.0) InstructionType.SHARP_LEFT else InstructionType.SHARP_RIGHT
        else -> if (turn < 0.0) InstructionType.U_TURN_LEFT else InstructionType.U_TURN_RIGHT
    }

    /** 前方枝が1本 (通常のカーブ・ヘアピン)。同種道路の継続は急カーブ扱い。 */
    private fun classifyHairpinOrTurn(
        input: JunctionInput,
        magnitude: Double,
        turn: Double,
        config: GuidanceConfig,
    ): InstructionType {
        if (magnitude <= config.straightMaxDegrees) return InstructionType.STRAIGHT
        if (magnitude < config.sharpMinDegrees) {
            return if (turn < 0.0) InstructionType.LEFT_TURN else InstructionType.RIGHT_TURN
        }
        // 深い切り返し: 同種道路が続く場合は道路形状由来とみなし SHARP、そうでなければ U。
        if (input.incomingRoadType == input.outgoingRoadType) {
            return if (turn < 0.0) InstructionType.SHARP_LEFT else InstructionType.SHARP_RIGHT
        }
        return if (magnitude >= U_TURN_SINGLE_ARM_DEGREES) {
            if (turn < 0.0) InstructionType.U_TURN_LEFT else InstructionType.U_TURN_RIGHT
        } else {
            if (turn < 0.0) InstructionType.SHARP_LEFT else InstructionType.SHARP_RIGHT
        }
    }

    /**
     * 前方枝が2本 (分岐 / Y字 / T字)。
     * 直進維持で非選択枝が側道程度に開いている場合は null (側道を過ぎるだけなので案内不要)。
     */
    private fun classifyFork(
        input: JunctionInput,
        forward: List<BranchOption>,
        takenTurn: Double,
        config: GuidanceConfig,
    ): InstructionType? {
        val takenMagnitude = abs(takenTurn)
        val other = forward.firstOrNull { !it.isRouteChoice }
            ?: return classifyByAngleOnly(takenMagnitude, takenTurn)
        val otherTurn = signedTurnDegrees(input.incomingBearing, other.bearing)
        val otherMagnitude = abs(otherTurn)

        // 直進維持かつ他枝が大きく開く = 交差点ではなく側道。案内しない。
        if (takenMagnitude <= config.straightMaxDegrees && otherMagnitude > config.forkSpreadMaxDegrees) {
            return null
        }

        // Y字: 両枝が前方・逆側・ほぼ対称 (T字より先に判定)。
        if (takenTurn * otherTurn < 0.0 && takenMagnitude <= config.forkSpreadMaxDegrees && otherMagnitude <= config.forkSpreadMaxDegrees &&
            abs(takenMagnitude - otherMagnitude) <= config.ySymmetryDegrees
        ) {
            return InstructionType.Y_JUNCTION
        }
        // T字: どちらの前方枝も直進圏外・かつ裏枝ほど開いていない (来た道を除き左右のみ)。
        if (takenMagnitude > config.straightMaxDegrees && takenMagnitude <= config.tJunctionMaxDegrees &&
            otherMagnitude > config.straightMaxDegrees && otherMagnitude <= config.tJunctionMaxDegrees
        ) {
            return InstructionType.T_JUNCTION
        }
        // 通常分岐: ルートが直進側を取る。
        if (takenMagnitude <= config.forkMaxDegrees && takenMagnitude <= otherMagnitude) {
            val sideLeft = takenTurn < 0.0 || (takenMagnitude <= 5.0 && otherTurn > 0.0)
            val otherAhead = otherMagnitude < 90.0
            // 非選択枝が後方に剥がれる = DOWN分岐 (斜め判定より優先)。
            if (!otherAhead) {
                return if (sideLeft) InstructionType.FORK_DOWN_LEFT else InstructionType.FORK_DOWN_RIGHT
            }
            val diagonal = takenMagnitude > config.diagonalForkMinDegrees
            return when {
                sideLeft && !diagonal -> InstructionType.FORK_UP_LEFT
                sideLeft -> InstructionType.DIAGONAL_FORK_LEFT
                !diagonal -> InstructionType.FORK_UP_RIGHT
                else -> InstructionType.DIAGONAL_FORK_RIGHT
            }
        }
        // 曲がり側を取る・大きく曲がる = 通常の左右折。
        return classifyByAngleOnly(takenMagnitude, takenTurn)
    }

    /** 前方枝が3本 (十字 / 左右両分岐)。 */
    private fun classifyThreeBranches(
        input: JunctionInput,
        forward: List<BranchOption>,
        takenTurn: Double,
        config: GuidanceConfig,
    ): InstructionType {
        val takenMagnitude = abs(takenTurn)
        val turns = forward.map { abs(signedTurnDegrees(input.incomingBearing, it.bearing)) }
        val hasLeft = forward.any { signedTurnDegrees(input.incomingBearing, it.bearing) < -config.straightMaxDegrees }
        val hasRight = forward.any { signedTurnDegrees(input.incomingBearing, it.bearing) > config.straightMaxDegrees }
        // 左右に浅い枝があり直進を維持 → 左右両分岐。
        if (takenMagnitude <= config.straightMaxDegrees && hasLeft && hasRight &&
            turns.all { it <= config.forkSpreadMaxDegrees }
        ) {
            return InstructionType.FORK_BOTH
        }
        return InstructionType.CROSS_JUNCTION
    }

    // -- ルート層 --------------------------------------------------------------

    private data class LoopDetection(val entries: Set<Int>, val excluded: Set<Int>)

    /**
     * 周回検出。同一符号の転回が連続し、両端いずれかに分岐 (進入・脱出アーム) がある場合のみ
     * ラウンドアバウトとみなす。幾何的な近接 (40–400mループ) だけでは U字道路・起終点の
     * 取り回し・二重線道路と区別できないことが広島実データで確認済みのため使わない。
     * 中立ノードの連続は circulationMaxNeutralGap で分断する (離れた同方向カーブの融合防止)。
     * Uターン帯 (|turn| ≥ uTurn) は周回を分断する (Uターン判定は別途・不変)。
     */
    private fun detectCirculationEntries(
        inputs: List<JunctionInput>,
        config: GuidanceConfig,
    ): LoopDetection {
        val entries = mutableSetOf<Int>()
        val excluded = mutableSetOf<Int>()
        val arms = inputs.associate { it.routeIndex to (it.options.size >= 3) }
        var runSign = 0
        var runBig = 0
        var runNet = 0.0
        var runNeutralGap = 0
        val runMembers = ArrayList<Int>()

        fun closeRun() {
            if (runBig >= config.circulationMinTurns &&
                abs(runNet) >= config.circulationMinNetDegrees &&
                runMembers.isNotEmpty()
            ) {
                val first = runMembers.first()
                val last = runMembers.last()
                val hasEntryArm = arms[first - 1] == true
                val hasExitArm = arms[last + 1] == true
                if (hasEntryArm || hasExitArm) {
                    entries += if (first - 1 >= 1) first - 1 else first
                    for (k in runMembers) excluded += k
                    val maxRouteIndex = inputs.lastOrNull()?.routeIndex ?: 0
                    if (last + 1 <= maxRouteIndex) excluded += last + 1
                }
            }
            runSign = 0
            runBig = 0
            runNet = 0.0
            runNeutralGap = 0
            runMembers.clear()
        }

        for (input in inputs) {
            val turn = signedTurnDegrees(input.incomingBearing, input.outgoingBearing)
            val magnitude = abs(turn)
            if (magnitude >= U_TURN_THRESHOLD_DEGREES) {
                closeRun()
                continue
            }
            if (magnitude < config.circulationMinAngleDegrees) {
                // 中立 (小さなふらつき・直進ノード): 上限まで周回を継続するが数えない。
                // 上限超過で分断し、離れた同方向カーブの融合 (偽 ROUNDABOUT) を防ぐ。
                if (runSign != 0) {
                    runNeutralGap++
                    if (runNeutralGap > config.circulationMaxNeutralGap) {
                        closeRun()
                    } else {
                        runMembers += input.routeIndex
                    }
                }
                continue
            }
            val sign = if (turn < 0.0) -1 else 1
            if (runSign == 0) {
                runSign = sign
                runBig = 1
                runNet = turn
                runNeutralGap = 0
                runMembers += input.routeIndex
            } else if (sign == runSign) {
                runBig++
                runNet += turn
                runNeutralGap = 0
                runMembers += input.routeIndex
            } else {
                closeRun()
                runSign = sign
                runBig = 1
                runNet = turn
                runMembers += input.routeIndex
            }
        }
        closeRun()
        return LoopDetection(entries, excluded)
    }

    /** 近接する分岐系案内を CONSECUTIVE_FORK に統合する (先頭を置換・後続は維持)。 */
    private fun mergeConsecutiveForks(
        instructions: List<RouteInstruction>,
        config: GuidanceConfig,
    ): List<RouteInstruction> {
        if (instructions.size < 2) return instructions
        val forkFamily = setOf(
            InstructionType.FORK_LEFT, InstructionType.FORK_RIGHT,
            InstructionType.DIAGONAL_FORK_LEFT, InstructionType.DIAGONAL_FORK_RIGHT,
            InstructionType.FORK_UP_LEFT, InstructionType.FORK_DOWN_LEFT,
            InstructionType.FORK_UP_RIGHT, InstructionType.FORK_DOWN_RIGHT,
            InstructionType.FORK_BOTH, InstructionType.Y_JUNCTION,
            InstructionType.T_JUNCTION, InstructionType.CROSS_JUNCTION,
            InstructionType.MULTI_JUNCTION,
        )
        val result = instructions.toMutableList()
        for (i in 0 until result.size - 1) {
            val first = result[i]
            val second = result[i + 1]
            if (first.type in forkFamily && second.type in forkFamily &&
                second.distanceFromStartMeters - first.distanceFromStartMeters <= config.consecutiveForkMeters
            ) {
                result[i] = first.copy(type = InstructionType.CONSECUTIVE_FORK, reason = "close-forks")
            }
        }
        return result
    }
}
