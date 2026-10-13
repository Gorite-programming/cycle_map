package com.gorite.cyclemap.routing

data class LaneHint(
    val direction: String, // "left", "through", "right", "slight_left", etc.
    val isActiveForRoute: Boolean = false
)

data class ExtendedInstruction(
    val base: RouteInstruction,
    val streetName: String? = null,
    val destinationSignpost: String? = null,
    val lanes: List<LaneHint>? = null,
    val secondManeuver: RouteInstruction? = null
)

object VoiceTriggerDistances {
    const val TRIGGER_FAR_METERS = 500.0
    const val TRIGGER_MEDIUM_METERS = 200.0
    const val TRIGGER_NEAR_METERS = 50.0

    fun shouldTriggerFar(distanceMeters: Double, lastAnnouncedMeters: Double): Boolean {
        return distanceMeters <= TRIGGER_FAR_METERS && lastAnnouncedMeters > TRIGGER_FAR_METERS
    }

    fun shouldTriggerMedium(distanceMeters: Double, lastAnnouncedMeters: Double): Boolean {
        return distanceMeters <= TRIGGER_MEDIUM_METERS && lastAnnouncedMeters > TRIGGER_MEDIUM_METERS
    }

    fun shouldTriggerNear(distanceMeters: Double, lastAnnouncedMeters: Double): Boolean {
        return distanceMeters <= TRIGGER_NEAR_METERS && lastAnnouncedMeters > TRIGGER_NEAR_METERS
    }

    /**
     * 連続する曲がり角 (consecutive turns) の連結案内文を生成
     */
    fun buildCompoundPhrase(
        first: RouteInstruction,
        distanceMeters: Double,
        second: RouteInstruction?
    ): String {
        val distText = if (distanceMeters < 100) "まもなく" else "約${distanceMeters.toInt()}メートル先"
        val firstTurn = turnName(first.type)
        if (second != null && (second.distanceFromStartMeters - first.distanceFromStartMeters) < 80.0) {
            val secondTurn = turnName(second.type)
            return "$distText、${firstTurn}です。その先、すぐに${secondTurn}です。"
        }
        return "$distText、${firstTurn}です。"
    }

    private fun turnName(type: InstructionType): String = when (type) {
        InstructionType.LEFT_TURN -> "左方向"
        InstructionType.RIGHT_TURN -> "右方向"
        InstructionType.SHARP_LEFT -> "斜め左方向"
        InstructionType.SHARP_RIGHT -> "斜め右方向"
        InstructionType.U_TURN_LEFT, InstructionType.U_TURN_RIGHT -> "Uターン"
        InstructionType.FORK_LEFT -> "左の分岐"
        InstructionType.FORK_RIGHT -> "右の分岐"
        InstructionType.ROUNDABOUT -> "ラウンドアバウト"
        else -> "道なり直進"
    }
}
