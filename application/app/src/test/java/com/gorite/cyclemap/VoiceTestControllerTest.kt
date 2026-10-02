package com.gorite.cyclemap

import com.gorite.cyclemap.speech.FakeTextToSpeechEngine
import com.gorite.cyclemap.speech.VoiceTestController
import com.gorite.cyclemap.speech.VoiceTestPhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTestControllerTest {

    @Test
    fun voiceTestPhrases_containRequiredThreePhrases() {
        assertEquals(3, VoiceTestPhrase.entries.size)
        assertEquals("直進です", VoiceTestPhrase.STRAIGHT.text)
        assertEquals("左折です", VoiceTestPhrase.LEFT.text)
        assertEquals("右折です", VoiceTestPhrase.RIGHT.text)
    }

    @Test
    fun playPhrase_speaksTextWhenEngineReady() {
        val fakeEngine = FakeTextToSpeechEngine(ready = true)
        val controller = VoiceTestController(fakeEngine)

        val success = controller.playPhrase(VoiceTestPhrase.STRAIGHT)
        assertTrue(success)
        assertEquals("直進です", controller.lastSpokenText)
        assertEquals(listOf("直進です"), fakeEngine.spokenTexts)

        controller.playPhrase(VoiceTestPhrase.LEFT)
        assertEquals("左折です", controller.lastSpokenText)
        assertEquals(listOf("直進です", "左折です"), fakeEngine.spokenTexts)

        controller.playPhrase(VoiceTestPhrase.RIGHT)
        assertEquals("右折です", controller.lastSpokenText)
        assertEquals(listOf("直進です", "左折です", "右折です"), fakeEngine.spokenTexts)
    }

    @Test
    fun playPhrase_failsWhenEngineNotReady() {
        val fakeEngine = FakeTextToSpeechEngine(ready = false)
        val controller = VoiceTestController(fakeEngine)

        val success = controller.playPhrase(VoiceTestPhrase.STRAIGHT)
        assertFalse(success)
        assertNull(controller.lastSpokenText)
        assertTrue(fakeEngine.spokenTexts.isEmpty())
    }

    @Test
    fun stop_delegatesToEngine() {
        val fakeEngine = FakeTextToSpeechEngine(ready = true)
        val controller = VoiceTestController(fakeEngine)

        controller.stop()
        assertTrue(fakeEngine.stopCalled)
    }

    @Test
    fun setEngine_stopsOldEngineAndUsesNewEngine() {
        val oldEngine = FakeTextToSpeechEngine(ready = true)
        val newEngine = FakeTextToSpeechEngine(ready = true)
        val controller = VoiceTestController(oldEngine)

        controller.playPhrase(VoiceTestPhrase.STRAIGHT)
        assertEquals(listOf("直進です"), oldEngine.spokenTexts)

        controller.setEngine(newEngine)
        assertTrue(oldEngine.stopCalled)

        controller.playPhrase(VoiceTestPhrase.LEFT)
        assertEquals(listOf("左折です"), newEngine.spokenTexts)
        assertEquals(listOf("直進です"), oldEngine.spokenTexts)
    }
}
