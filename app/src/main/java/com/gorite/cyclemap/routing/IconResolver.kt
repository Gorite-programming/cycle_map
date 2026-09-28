package com.gorite.cyclemap.routing

import com.gorite.cyclemap.R

/**
 * InstructionType → アイコン (res/drawable) の解決。
 * UI は InstructionType を直接アイコン化せず、必ずここを経由すること。
 * Material Symbol → CycleMap 独自SVG への差し替えはこのファイルのみで完結する。
 *
 * アイコン実体は Material Symbols Rounded 400 の公式SVGを
 * VectorDrawable化した `ic_ms_*` (Apache License 2.0、詳細は DISTRIBUTION_NOTES.md)。
 * 公式に存在しない案内 (交差点図形など) は [isPlaceholder] が true の代替表示。
 * 存在確認は公式 codepoints と突合済み。未確認名を推測で追加しないこと。
 */
object IconResolver {

    private val iconMap = mapOf(
        InstructionType.STRAIGHT to R.drawable.ic_ms_straight,
        InstructionType.LEFT_TURN to R.drawable.ic_ms_turn_left,
        InstructionType.RIGHT_TURN to R.drawable.ic_ms_turn_right,
        InstructionType.SHARP_LEFT to R.drawable.ic_ms_turn_sharp_left,
        InstructionType.SHARP_RIGHT to R.drawable.ic_ms_turn_sharp_right,
        InstructionType.U_TURN_LEFT to R.drawable.ic_ms_u_turn_left,
        InstructionType.U_TURN_RIGHT to R.drawable.ic_ms_u_turn_right,
        InstructionType.FORK_LEFT to R.drawable.ic_ms_fork_left,
        InstructionType.FORK_RIGHT to R.drawable.ic_ms_fork_right,
        InstructionType.DIAGONAL_FORK_LEFT to R.drawable.ic_ms_turn_slight_left,
        InstructionType.DIAGONAL_FORK_RIGHT to R.drawable.ic_ms_turn_slight_right,
        InstructionType.FORK_UP_LEFT to R.drawable.ic_ms_fork_left,
        InstructionType.FORK_DOWN_LEFT to R.drawable.ic_ms_fork_left,
        InstructionType.FORK_UP_RIGHT to R.drawable.ic_ms_fork_right,
        InstructionType.FORK_DOWN_RIGHT to R.drawable.ic_ms_fork_right,
        InstructionType.FORK_BOTH to R.drawable.ic_ms_call_split,
        InstructionType.Y_JUNCTION to R.drawable.ic_ms_call_split,
        InstructionType.T_JUNCTION to R.drawable.ic_ms_call_split,
        InstructionType.CROSS_JUNCTION to R.drawable.ic_ms_add,
        InstructionType.MULTI_JUNCTION to R.drawable.ic_ms_add,
        InstructionType.MERGE to R.drawable.ic_ms_merge,
        InstructionType.LANE_INCREASE to R.drawable.ic_ms_call_split,
        InstructionType.LANE_DECREASE to R.drawable.ic_ms_call_merge,
        InstructionType.SIDE_ROAD_ENTER to R.drawable.ic_ms_turn_slight_right,
        InstructionType.SIDE_ROAD_EXIT to R.drawable.ic_ms_turn_slight_left,
        InstructionType.ROUNDABOUT to R.drawable.ic_ms_roundabout_left,
        InstructionType.RAMP_ENTRY to R.drawable.ic_ms_ramp_right,
        InstructionType.RAMP_EXIT to R.drawable.ic_ms_ramp_left,
        InstructionType.CONSECUTIVE_FORK to R.drawable.ic_ms_alt_route,
    )

    /** 公式 Material Symbols 名。代替表示のものも「最も近い公式名」を返す。 */
    private val materialNameMap = mapOf(
        InstructionType.STRAIGHT to "straight",
        InstructionType.LEFT_TURN to "turn_left",
        InstructionType.RIGHT_TURN to "turn_right",
        InstructionType.SHARP_LEFT to "turn_sharp_left",
        InstructionType.SHARP_RIGHT to "turn_sharp_right",
        InstructionType.U_TURN_LEFT to "u_turn_left",
        InstructionType.U_TURN_RIGHT to "u_turn_right",
        InstructionType.FORK_LEFT to "fork_left",
        InstructionType.FORK_RIGHT to "fork_right",
        InstructionType.DIAGONAL_FORK_LEFT to "turn_slight_left",
        InstructionType.DIAGONAL_FORK_RIGHT to "turn_slight_right",
        InstructionType.FORK_UP_LEFT to "fork_left",
        InstructionType.FORK_DOWN_LEFT to "fork_left",
        InstructionType.FORK_UP_RIGHT to "fork_right",
        InstructionType.FORK_DOWN_RIGHT to "fork_right",
        InstructionType.FORK_BOTH to "call_split",
        InstructionType.Y_JUNCTION to "call_split",
        InstructionType.T_JUNCTION to "call_split",
        InstructionType.CROSS_JUNCTION to "add",
        InstructionType.MULTI_JUNCTION to "add",
        InstructionType.MERGE to "merge",
        InstructionType.LANE_INCREASE to "call_split",
        InstructionType.LANE_DECREASE to "call_merge",
        InstructionType.SIDE_ROAD_ENTER to "turn_slight_right",
        InstructionType.SIDE_ROAD_EXIT to "turn_slight_left",
        InstructionType.ROUNDABOUT to "roundabout_left",
        InstructionType.RAMP_ENTRY to "ramp_right",
        InstructionType.RAMP_EXIT to "ramp_left",
        InstructionType.CONSECUTIVE_FORK to "alt_route",
    )

    /**
     * 独自SVG待ちの代替表示か。以下は Material に正確な図形が無いため、
     * 正確な InstructionType の生成を優先し表示のみ代替している。
     */
    fun isPlaceholder(type: InstructionType): Boolean = when (type) {
        InstructionType.DIAGONAL_FORK_LEFT,
        InstructionType.DIAGONAL_FORK_RIGHT,
        InstructionType.FORK_UP_LEFT,
        InstructionType.FORK_DOWN_LEFT,
        InstructionType.FORK_UP_RIGHT,
        InstructionType.FORK_DOWN_RIGHT,
        InstructionType.FORK_BOTH,
        InstructionType.Y_JUNCTION,
        InstructionType.T_JUNCTION,
        InstructionType.CROSS_JUNCTION,
        InstructionType.MULTI_JUNCTION,
        InstructionType.LANE_INCREASE,
        InstructionType.LANE_DECREASE,
        InstructionType.SIDE_ROAD_ENTER,
        InstructionType.SIDE_ROAD_EXIT,
        InstructionType.RAMP_ENTRY,
        InstructionType.RAMP_EXIT,
        InstructionType.CONSECUTIVE_FORK,
        -> true
        else -> false
    }

    fun resolveIconResId(type: InstructionType): Int =
        iconMap[type] ?: R.drawable.ic_ms_straight

    /**
     * 方向を考慮したアイコン解決。
     * ランプ・側道は転回角度の符号で左右を決定する (BUG-38 fix)。
     */
    fun resolveIconResId(type: InstructionType, turnAngleDegrees: Double): Int {
        return when (type) {
            InstructionType.RAMP_ENTRY -> if (turnAngleDegrees < 0.0) R.drawable.ic_ms_ramp_left else R.drawable.ic_ms_ramp_right
            InstructionType.RAMP_EXIT -> if (turnAngleDegrees < 0.0) R.drawable.ic_ms_ramp_left else R.drawable.ic_ms_ramp_right
            InstructionType.SIDE_ROAD_ENTER -> if (turnAngleDegrees < 0.0) R.drawable.ic_ms_turn_slight_left else R.drawable.ic_ms_turn_slight_right
            InstructionType.SIDE_ROAD_EXIT -> if (turnAngleDegrees < 0.0) R.drawable.ic_ms_turn_slight_left else R.drawable.ic_ms_turn_slight_right
            else -> resolveIconResId(type)
        }
    }

    /** デバッグログ用の公式アイコン名。 */
    fun materialIconName(type: InstructionType): String =
        materialNameMap[type] ?: "straight"

    fun getLabel(type: InstructionType): String = when (type) {
        InstructionType.STRAIGHT -> "直進"
        InstructionType.LEFT_TURN -> "左折"
        InstructionType.RIGHT_TURN -> "右折"
        InstructionType.SHARP_LEFT -> "急左折"
        InstructionType.SHARP_RIGHT -> "急右折"
        InstructionType.U_TURN_LEFT -> "左Uターン"
        InstructionType.U_TURN_RIGHT -> "右Uターン"
        InstructionType.FORK_LEFT -> "左分岐"
        InstructionType.FORK_RIGHT -> "右分岐"
        InstructionType.DIAGONAL_FORK_LEFT -> "左斜め分岐"
        InstructionType.DIAGONAL_FORK_RIGHT -> "右斜め分岐"
        InstructionType.FORK_UP_LEFT -> "左上分岐"
        InstructionType.FORK_DOWN_LEFT -> "左下分岐"
        InstructionType.FORK_UP_RIGHT -> "右上分岐"
        InstructionType.FORK_DOWN_RIGHT -> "右下分岐"
        InstructionType.FORK_BOTH -> "左右両分岐"
        InstructionType.Y_JUNCTION -> "Y字交差点"
        InstructionType.T_JUNCTION -> "T字路"
        InstructionType.CROSS_JUNCTION -> "十字路"
        InstructionType.MULTI_JUNCTION -> "多差路"
        InstructionType.MERGE -> "合流"
        InstructionType.LANE_INCREASE -> "車線増加"
        InstructionType.LANE_DECREASE -> "車線減少"
        InstructionType.SIDE_ROAD_ENTER -> "側道へ"
        InstructionType.SIDE_ROAD_EXIT -> "側道から本線へ"
        InstructionType.ROUNDABOUT -> "ラウンドアバウト"
        InstructionType.RAMP_ENTRY -> "ランプ入口"
        InstructionType.RAMP_EXIT -> "ランプ出口"
        InstructionType.CONSECUTIVE_FORK -> "連続分岐"
    }
}
