package com.gorite.cyclemap.real

import com.gorite.cyclemap.contract.LaneGuidanceProvider
import com.gorite.cyclemap.contract.LaneInfo
import com.gorite.cyclemap.contract.TurnType

/**
 * 契約 [LaneGuidanceProvider] の本番 (Real) 実装。
 * - OSM の turn:lanes タグ等から抽出された明示的な車線情報のみを提供
 * - 車線タグが存在しない一般区間では推測や偽装を行わず、正直に空リスト [emptyList] を返す (plans.md §6 P1-4)
 */
class RealLaneGuidanceProvider(
    // 車線情報テーブルまたはインメモリストア (edgeId -> 車線文字列 "left|through|right")
    private val laneDataSource: ((edgeId: Long) -> String?)? = null
) : LaneGuidanceProvider {

    override suspend fun getLaneHints(
        edgeId: Long,
        turnType: TurnType
    ): List<LaneInfo> {
        val rawTurnLanes = laneDataSource?.invoke(edgeId) ?: return emptyList()
        if (rawTurnLanes.isBlank()) return emptyList()

        // OSM turn:lanes はパイプ区切り (例: "left|through|through;right")
        val parts = rawTurnLanes.split("|")
        if (parts.isEmpty()) return emptyList()

        val hasDedicatedMatch = parts.any { part ->
            !part.contains(";") && isLaneRecommendedForTurn(mapTurnLaneToTurnType(part.trim()), turnType)
        }

        return parts.map { part ->
            val isComposite = part.contains(";")
            val parsedType = mapTurnLaneToTurnType(part.trim())
            val directions = part.split(";").map { mapTurnLaneToTurnType(it.trim()) }
            val supportsTurn = directions.any { isLaneRecommendedForTurn(it, turnType) }
            val isRecommended = if (hasDedicatedMatch) {
                !isComposite && supportsTurn
            } else {
                supportsTurn
            }
            LaneInfo(
                direction = parsedType,
                isRecommended = isRecommended
            )
        }
    }

    private fun mapTurnLaneToTurnType(laneString: String): TurnType {
        val primary = laneString.split(";").firstOrNull() ?: laneString
        return when (primary.lowercase()) {
            "left" -> TurnType.Left
            "slight_left" -> TurnType.SlightLeft
            "sharp_left" -> TurnType.SharpLeft
            "through" -> TurnType.Straight
            "right" -> TurnType.Right
            "slight_right" -> TurnType.SlightRight
            "sharp_right" -> TurnType.SharpRight
            "reverse" -> TurnType.Uturn
            else -> TurnType.Straight
        }
    }

    private fun isLaneRecommendedForTurn(laneDirection: TurnType, targetTurn: TurnType): Boolean {
        return when (targetTurn) {
            TurnType.Left, TurnType.SlightLeft, TurnType.SharpLeft ->
                laneDirection == TurnType.Left || laneDirection == TurnType.SlightLeft || laneDirection == TurnType.SharpLeft
            TurnType.Right, TurnType.SlightRight, TurnType.SharpRight ->
                laneDirection == TurnType.Right || laneDirection == TurnType.SlightRight || laneDirection == TurnType.SharpRight
            TurnType.Straight ->
                laneDirection == TurnType.Straight
            TurnType.Uturn ->
                laneDirection == TurnType.Uturn || laneDirection == TurnType.Left
            else ->
                laneDirection == TurnType.Straight
        }
    }
}
