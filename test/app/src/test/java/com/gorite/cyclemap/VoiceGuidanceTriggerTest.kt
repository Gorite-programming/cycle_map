package com.gorite.cyclemap

import com.gorite.cyclemap.routing.InstructionType
import com.gorite.cyclemap.routing.RouteInstruction
import com.gorite.cyclemap.speech.VoiceCue
import com.gorite.cyclemap.speech.VoiceGuidanceMode
import com.gorite.cyclemap.speech.VoiceGuidanceStage
import com.gorite.cyclemap.speech.VoiceGuidanceThresholds
import com.gorite.cyclemap.speech.VoiceTriggerEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceGuidanceTriggerTest {

    private fun createInstruction(
        type: InstructionType,
        distanceFromStart: Double,
        angleDegrees: Double = 0.0,
    ): RouteInstruction = RouteInstruction(
        type = type,
        nodeId = 100L,
        routeIndex = 1,
        distanceFromStartMeters = distanceFromStart,
        turnAngleDegrees = angleDegrees,
        junctionBranches = 3,
        incomingRoadType = "secondary",
        outgoingRoadType = "secondary",
        reason = "test-instruction",
    )

    @Test
    fun thresholds_areWellDefined() {
        assertEquals(300.0, VoiceGuidanceThresholds.DISTANCE_FAR_METERS, 0.001)
        assertEquals(100.0, VoiceGuidanceThresholds.DISTANCE_NEAR_METERS, 0.001)
        assertEquals(30.0, VoiceGuidanceThresholds.DISTANCE_IMMEDIATE_METERS, 0.001)
        assertEquals(30.0, VoiceGuidanceThresholds.ARRIVAL_DISTANCE_METERS, 0.001)
    }

    @Test
    fun triggerEvaluator_triggersFarStageAtAround300m() {
        val instruction = createInstruction(InstructionType.LEFT_TURN, 1000.0)
        val instructions = listOf(instruction)
        val spokenKeys = mutableSetOf<String>()

        // 700m地点走行（残り300m）
        val cue = VoiceTriggerEvaluator.evaluate(
            traveledMeters = 700.0,
            remainingMeters = 500.0,
            instructions = instructions,
            isOffRoute = false,
            spokenKeys = spokenKeys,
        )

        assertNotNull(cue)
        assertEquals(VoiceGuidanceStage.FAR, cue!!.stage)
        assertEquals("およそ300メートル先、左折です", cue.spokenText)
        assertEquals(R.raw.voice_300m_turn_left, cue.rawResId)
    }

    @Test
    fun triggerEvaluator_triggersNearStageAtAround100m() {
        val instruction = createInstruction(InstructionType.RIGHT_TURN, 1000.0)
        val instructions = listOf(instruction)
        val spokenKeys = mutableSetOf<String>()

        // 900m地点走行（残り100m）
        val cue = VoiceTriggerEvaluator.evaluate(
            traveledMeters = 900.0,
            remainingMeters = 300.0,
            instructions = instructions,
            isOffRoute = false,
            spokenKeys = spokenKeys,
        )

        assertNotNull(cue)
        assertEquals(VoiceGuidanceStage.NEAR, cue!!.stage)
        assertEquals("およそ100メートル先、右折です", cue.spokenText)
        assertEquals(R.raw.voice_100m_turn_right, cue.rawResId)
    }

    @Test
    fun triggerEvaluator_triggersImmediateStageWithMetanAudioRes() {
        val instruction = createInstruction(InstructionType.LEFT_TURN, 1000.0)
        val instructions = listOf(instruction)
        val spokenKeys = mutableSetOf<String>()

        // 980m地点走行（残り20m <= 30m）
        val cue = VoiceTriggerEvaluator.evaluate(
            traveledMeters = 980.0,
            remainingMeters = 100.0,
            instructions = instructions,
            isOffRoute = false,
            spokenKeys = spokenKeys,
        )

        assertNotNull(cue)
        assertEquals(VoiceGuidanceStage.IMMEDIATE, cue!!.stage)
        assertEquals("まもなく、左折です", cue.spokenText)
        assertEquals(R.raw.voice_now_turn_left, cue.rawResId)
    }

    @Test
    fun triggerEvaluator_preventsDuplicateSpokenStages() {
        val instruction = createInstruction(InstructionType.STRAIGHT, 500.0)
        val instructions = listOf(instruction)
        val spokenKeys = mutableSetOf<String>()

        // 1回目の300m前 (200m走行)
        val cue1 = VoiceTriggerEvaluator.evaluate(
            traveledMeters = 200.0,
            remainingMeters = 800.0,
            instructions = instructions,
            isOffRoute = false,
            spokenKeys = spokenKeys,
        )
        assertNotNull(cue1)
        spokenKeys.add(VoiceTriggerEvaluator.makeKey(cue1!!))

        // 2回目の300m前 (205m走行、まだFAR帯域内)
        val cue2 = VoiceTriggerEvaluator.evaluate(
            traveledMeters = 205.0,
            remainingMeters = 795.0,
            instructions = instructions,
            isOffRoute = false,
            spokenKeys = spokenKeys,
        )
        assertNull(cue2) // 重複発声が防止されていること

        // 100m前 (400m走行)
        val cue3 = VoiceTriggerEvaluator.evaluate(
            traveledMeters = 400.0,
            remainingMeters = 600.0,
            instructions = instructions,
            isOffRoute = false,
            spokenKeys = spokenKeys,
        )
        assertNotNull(cue3)
        assertEquals(VoiceGuidanceStage.NEAR, cue3!!.stage)
        spokenKeys.add(VoiceTriggerEvaluator.makeKey(cue3))

        // 直前 (480m走行)
        val cue4 = VoiceTriggerEvaluator.evaluate(
            traveledMeters = 480.0,
            remainingMeters = 520.0,
            instructions = instructions,
            isOffRoute = false,
            spokenKeys = spokenKeys,
        )
        assertNotNull(cue4)
        assertEquals(VoiceGuidanceStage.IMMEDIATE, cue4!!.stage)
        assertEquals(R.raw.voice_now_straight, cue4.rawResId)
    }

    @Test
    fun triggerEvaluator_triggersArrivalWhenWithinRadius() {
        val instruction = createInstruction(InstructionType.STRAIGHT, 100.0)
        val instructions = listOf(instruction)
        val spokenKeys = mutableSetOf<String>()

        val cue = VoiceTriggerEvaluator.evaluate(
            traveledMeters = 95.0,
            remainingMeters = 15.0, // <= 30m
            instructions = instructions,
            isOffRoute = false,
            spokenKeys = spokenKeys,
        )

        assertNotNull(cue)
        assertEquals(VoiceGuidanceStage.ARRIVED, cue!!.stage)
        assertEquals("目的地に到着しました", cue.spokenText)
        assertEquals(R.raw.voice_arrived, cue.rawResId)
    }

    @Test
    fun triggerEvaluator_triggersRerouteWhenOffRoute() {
        val instruction = createInstruction(InstructionType.RIGHT_TURN, 500.0)
        val instructions = listOf(instruction)
        val spokenKeys = mutableSetOf<String>()

        val cue = VoiceTriggerEvaluator.evaluate(
            traveledMeters = 200.0,
            remainingMeters = 400.0,
            instructions = instructions,
            isOffRoute = true,
            spokenKeys = spokenKeys,
        )

        assertNotNull(cue)
        assertEquals(VoiceGuidanceStage.REROUTE, cue!!.stage)
        assertEquals("ルートから外れました。再探索します", cue.spokenText)
        assertEquals(R.raw.voice_reroute, cue.rawResId)
    }

    @Test
    fun buildCue_supportsAll29InstructionTypesGracefully() {
        val instructions = InstructionType.entries.map { type ->
            createInstruction(type, 100.0, angleDegrees = 45.0)
        }

        val stages = listOf(VoiceGuidanceStage.FAR, VoiceGuidanceStage.NEAR, VoiceGuidanceStage.IMMEDIATE)

        instructions.forEachIndexed { index, inst ->
            for (stage in stages) {
                val cue = VoiceTriggerEvaluator.buildCue(
                    index = index,
                    instruction = inst,
                    stage = stage,
                )
                assertNotNull("Spoken text should not be null for ${inst.type} at $stage", cue.spokenText)
                assertNotNull("rawResId should not be null for ${inst.type} at $stage", cue.rawResId)
                assertTrue("rawResId should be a valid resource id (> 0)", cue.rawResId!! > 0)
            }
        }
    }

    @Test
    fun voicePhraseCatalog_containsAll143EntriesWithValidResources() {
        assertEquals(143, com.gorite.cyclemap.speech.VoicePhraseCatalog.PHRASES.size)
        com.gorite.cyclemap.speech.VoicePhraseCatalog.PHRASES.forEach { (id, entry) ->
            assertEquals("Phrase id must match key", id, entry.id)
            assertTrue("Filename must not be blank", entry.filename.isNotBlank())
            assertTrue("Spoken text must not be blank", entry.text.isNotBlank())
            assertTrue("rawResId must be valid (> 0)", entry.rawResId > 0)
        }
    }
}
