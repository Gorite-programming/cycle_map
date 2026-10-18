package com.gorite.cyclemap.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaneAndGuidanceTest {

    @Test
    fun voiceTriggerDistancesCheck() {
        // Crossing 500m threshold from 520m to 490m
        assertTrue(VoiceTriggerDistances.shouldTriggerFar(490.0, 520.0))
        assertFalse(VoiceTriggerDistances.shouldTriggerFar(480.0, 490.0))

        // Crossing 200m threshold
        assertTrue(VoiceTriggerDistances.shouldTriggerMedium(195.0, 210.0))

        // Crossing 50m threshold
        assertTrue(VoiceTriggerDistances.shouldTriggerNear(45.0, 55.0))
    }

    @Test
    fun buildsCompoundPhraseForConsecutiveTurns() {
        val first = RouteInstruction(
            type = InstructionType.LEFT_TURN,
            nodeId = 1L,
            routeIndex = 5,
            distanceFromStartMeters = 500.0,
            turnAngleDegrees = -90.0,
            junctionBranches = 3,
            incomingRoadType = "secondary",
            outgoingRoadType = "residential",
            reason = "left-turn"
        )
        val second = RouteInstruction(
            type = InstructionType.RIGHT_TURN,
            nodeId = 2L,
            routeIndex = 6,
            distanceFromStartMeters = 550.0, // only 50m away (< 80m consecutive)
            turnAngleDegrees = 90.0,
            junctionBranches = 3,
            incomingRoadType = "residential",
            outgoingRoadType = "residential",
            reason = "right-turn"
        )

        val phrase = VoiceTriggerDistances.buildCompoundPhrase(first, 200.0, second)
        assertTrue(phrase.contains("約200メートル先、左方向です"))
        assertTrue(phrase.contains("その先、すぐに右方向です"))
    }
}
