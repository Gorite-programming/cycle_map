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
