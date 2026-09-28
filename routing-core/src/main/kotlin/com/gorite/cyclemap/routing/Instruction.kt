package com.gorite.cyclemap.routing

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 案内カテゴリ。① BASIC が今回の実装対象。
 * ②〜⑩ (BICYCLE/CROSSING/STRUCTURE/SLOPE/SURFACE/HAZARD/NAV_STATE/COMPOSITE/POI)
 * は将来 [InstructionType] にエントリを追加するだけで拡張できる。
 */
enum class InstructionCategory {
    BASIC,
    BICYCLE,
    CROSSING,
    STRUCTURE,
    SLOPE,
    SURFACE,
    HAZARD,
    NAV_STATE,
    COMPOSITE,
    POI,
}

/**
 * ① 基本経路指示の全29種。UI は型を直接参照せず、必ず [IconResolver] 経由で
 * アイコン・文言に変換すること (Material Symbol → 独自SVG 差し替えに耐えるため)。
 */
enum class InstructionType(val category: InstructionCategory) {
    STRAIGHT(InstructionCategory.BASIC),
    LEFT_TURN(InstructionCategory.BASIC),
    RIGHT_TURN(InstructionCategory.BASIC),
    SHARP_LEFT(InstructionCategory.BASIC),
    SHARP_RIGHT(InstructionCategory.BASIC),
    U_TURN_LEFT(InstructionCategory.BASIC),
    U_TURN_RIGHT(InstructionCategory.BASIC),

    FORK_LEFT(InstructionCategory.BASIC),
    FORK_RIGHT(InstructionCategory.BASIC),
    DIAGONAL_FORK_LEFT(InstructionCategory.BASIC),
    DIAGONAL_FORK_RIGHT(InstructionCategory.BASIC),
    FORK_UP_LEFT(InstructionCategory.BASIC),
    FORK_DOWN_LEFT(InstructionCategory.BASIC),
    FORK_UP_RIGHT(InstructionCategory.BASIC),
    FORK_DOWN_RIGHT(InstructionCategory.BASIC),
    FORK_BOTH(InstructionCategory.BASIC),
    Y_JUNCTION(InstructionCategory.BASIC),
    T_JUNCTION(InstructionCategory.BASIC),
    CROSS_JUNCTION(InstructionCategory.BASIC),
    MULTI_JUNCTION(InstructionCategory.BASIC),

    MERGE(InstructionCategory.BASIC),
    LANE_INCREASE(InstructionCategory.BASIC),
    LANE_DECREASE(InstructionCategory.BASIC),
    SIDE_ROAD_ENTER(InstructionCategory.BASIC),
    SIDE_ROAD_EXIT(InstructionCategory.BASIC),

    ROUNDABOUT(InstructionCategory.BASIC),
    RAMP_ENTRY(InstructionCategory.BASIC),
    RAMP_EXIT(InstructionCategory.BASIC),
    CONSECUTIVE_FORK(InstructionCategory.BASIC),
}

/**
 * ルート上の1地点に対する案内。判定根拠 ([reason] と角度・分岐数) を保持し、
 * デバッグログ [debugLine] で「なぜこの案内か」を追跡できる。
 */
data class RouteInstruction(
    val type: InstructionType,
    val nodeId: Long,
    /** ルート内インデックス (nodeIds 基準)。 */
    val routeIndex: Int,
    val distanceFromStartMeters: Double,
    /** 符号付き転回角 (度, +が右)。[-180, 180) に正規化済み。 */
    val turnAngleDegrees: Double,
    /** 当該ノードからの全 outgoing edge 数 (Uターン戻り含む)。 */
    val junctionBranches: Int,
    val incomingRoadType: String,
    val outgoingRoadType: String,
    /** 判定理由 (短い機械可読トークン列, 例: "fork-ahead-other-behind")。 */
    val reason: String,
) {
    fun debugLine(
        currentEdge: String,
        nextEdge: String,
        junctionType: String,
        icon: String,
    ): String = "[Instruction] currentEdge=$currentEdge nextEdge=$nextEdge " +
        "angle=${"%.1f".format(turnAngleDegrees)} junctionType=$junctionType " +
        "instruction=$type icon=$icon reason=$reason"
}

/**
 * 角度・距離の全閾値を集約。判定ロジック内にハードコードしないこと。
 * 根拠: straight/uTurn は既存 extractManeuvers (20°/150°) と整合させた。
 * sharp は自転車の体感 (90°=直角交差より深い切り返し) を基準に 100°。
 */
data class GuidanceConfig(
    val straightMaxDegrees: Double = 20.0,
    /** 分岐として扱う上限。これを超える転回は通常の左右折。 */
    val forkMaxDegrees: Double = 40.0,
    /** 斜め分岐とみなす下限 (これ以下は通常分岐)。 */
    val diagonalForkMinDegrees: Double = 15.0,
    val sharpMinDegrees: Double = 100.0,
    val uTurnMinDegrees: Double = 150.0,
    /** 合流・緩いランプ出口とみなす上限。 */
    val mergeMaxDegrees: Double = 25.0,
    /** 分岐とみなす枝の広がり上限。これより開いた枝は側道扱い (直進時は案内抑制)。 */
    val forkSpreadMaxDegrees: Double = 60.0,
    /** T字とみなす枝角度の上限。これより開いた枝は裏枝扱い (DOWN分岐へ)。 */
    val tJunctionMaxDegrees: Double = 135.0,
    /** Y字の対称性判定 (左右枝の角度差がこれ以下ならY字)。 */
    val ySymmetryDegrees: Double = 15.0,
    /** 連続分岐とみなす案内間隔。 */
    val consecutiveForkMeters: Double = 80.0,
    /**
     * ラウンドアバウト周回検出: 同一符号の転回がこの回数以上連続し、合計がこの角度以上で、
     * 周回の両端いずれかに分岐 (出入アーム) があること。
     * 根拠: 広島実データの真ラウンドアバウトは同一符号 ~18° ×7連続・合計126° + 両端deg3。
     * 交差点での転回後の緩いワインディング (4連続・合計50〜108°・両端に分岐あり) との分離に
     * 回数5 + 合計100°を使う。ミニラウンドアバウト (4ノード以下) は対象外として残る。
     * 幾何的な近接だけではU字道路・起終点の取り回しと区別できないため使わない。
     */
    val circulationMinTurns: Int = 5,
    val circulationMinAngleDegrees: Double = 10.0,
    val circulationMinNetDegrees: Double = 100.0,
    /**
     * 周回検出: 中立ノード (転回が circulationMinAngleDegrees 未満) の連続許容数。
     * これを超えると周回はそこで分断される。無制限だと離れた同方向カーブが
     * 長い直進を挟んで融合し偽 ROUNDABOUT になる (山道ワインディングで確認)。
     * 実ラウンドアバウトのリング上ノードは各 ~17° で数えられるため、
     * 出入付近の数ノードのふらつき吸収に3で十分。
     */
    val circulationMaxNeutralGap: Int = 3,
    // uTurnCheckMeters and uTurnCloseMeters were removed as unused
)

/** 方位角 (度, 北=0 時計回り, [0, 360))。同一点なら null。 */
fun bearingBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double? {
    val latitude1 = Math.toRadians(lat1)
    val latitude2 = Math.toRadians(lat2)
    val deltaLongitude = Math.toRadians(lon2 - lon1)
    val y = sin(deltaLongitude) * cos(latitude2)
    val x = cos(latitude1) * sin(latitude2) - sin(latitude1) * cos(latitude2) * cos(deltaLongitude)
    if (x == 0.0 && y == 0.0) return null
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** [fromBearing] → [toBearing] の符号付き変化 (+が右)。[-180, 180) に正規化。 */
fun signedTurnDegrees(fromBearing: Double, toBearing: Double): Double =
    ((toBearing - fromBearing + 540.0) % 360.0) - 180.0
