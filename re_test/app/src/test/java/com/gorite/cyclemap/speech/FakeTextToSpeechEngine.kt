package com.gorite.cyclemap.speech

class FakeTextToSpeechEngine(
    var ready: Boolean = true,
) : TextToSpeechEngine {
    val spokenTexts = mutableListOf<String>()
    var stopCalled: Boolean = false

    override fun speak(text: String): Boolean {
        if (!ready) return false
        spokenTexts.add(text)
        return true
    }

    override fun stop() {
        stopCalled = true
    }

    override fun isReady(): Boolean = ready
}

class FakeVoiceAudioPlayer(
    var ready: Boolean = true,
) : VoiceAudioPlayer {
    val playedResIds = mutableListOf<Int>()
    var stopCalled: Boolean = false

    override fun playResId(rawResId: Int): Boolean {
        if (!ready) return false
        playedResIds.add(rawResId)
        return true
    }

    override fun stop() {
        stopCalled = true
    }

    override fun isReady(): Boolean = ready
}
