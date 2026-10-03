package com.gorite.cyclemap

import com.gorite.cyclemap.speech.FakeVoiceAudioPlayer
import com.gorite.cyclemap.speech.VoicePhraseCatalog
import com.gorite.cyclemap.speech.VoiceTestController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTestControllerTest {

    @Test
    fun playPhrase_playsResourceWhenPlayerReady() {
        val fakePlayer = FakeVoiceAudioPlayer(ready = true)
        val controller = VoiceTestController(fakePlayer)

        val straightEntry = VoicePhraseCatalog.PHRASES["straight_now"]!!
        val success = controller.playPhrase(straightEntry)

        assertTrue(success)
        assertEquals("まもなく、直進です", controller.lastSpokenText)
        assertEquals(listOf(straightEntry.rawResId), fakePlayer.playedResIds)

        val leftEntry = VoicePhraseCatalog.PHRASES["turn_left_now"]!!
        controller.playPhrase(leftEntry)
        assertEquals("まもなく、左折です", controller.lastSpokenText)
        assertEquals(listOf(straightEntry.rawResId, leftEntry.rawResId), fakePlayer.playedResIds)
    }

    @Test
    fun playResId_playsSpecifiedResourceId() {
        val fakePlayer = FakeVoiceAudioPlayer(ready = true)
        val controller = VoiceTestController(fakePlayer)

        val success = controller.playResId(R.raw.voice_arrived, "目的地に到着しました")
        assertTrue(success)
        assertEquals("目的地に到着しました", controller.lastSpokenText)
        assertEquals(listOf(R.raw.voice_arrived), fakePlayer.playedResIds)
    }

    @Test
    fun playPhrase_failsWhenPlayerNotReady() {
        val fakePlayer = FakeVoiceAudioPlayer(ready = false)
        val controller = VoiceTestController(fakePlayer)

        val straightEntry = VoicePhraseCatalog.PHRASES["straight_now"]!!
        val success = controller.playPhrase(straightEntry)
        assertFalse(success)
        assertNull(controller.lastSpokenText)
        assertTrue(fakePlayer.playedResIds.isEmpty())
    }

    @Test
    fun stop_delegatesToPlayer() {
        val fakePlayer = FakeVoiceAudioPlayer(ready = true)
        val controller = VoiceTestController(fakePlayer)

        controller.stop()
        assertTrue(fakePlayer.stopCalled)
    }

    @Test
    fun setPlayer_stopsOldPlayerAndUsesNewPlayer() {
        val oldPlayer = FakeVoiceAudioPlayer(ready = true)
        val newPlayer = FakeVoiceAudioPlayer(ready = true)
        val controller = VoiceTestController(oldPlayer)

        val straightEntry = VoicePhraseCatalog.PHRASES["straight_now"]!!
        controller.playPhrase(straightEntry)
        assertEquals(listOf(straightEntry.rawResId), oldPlayer.playedResIds)

        controller.setPlayer(newPlayer)
        assertTrue(oldPlayer.stopCalled)

        val leftEntry = VoicePhraseCatalog.PHRASES["turn_left_now"]!!
        controller.playPhrase(leftEntry)
        assertEquals(listOf(leftEntry.rawResId), newPlayer.playedResIds)
        assertEquals(listOf(straightEntry.rawResId), oldPlayer.playedResIds)
    }
}
