package com.gorite.cyclemap.speech

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.gorite.cyclemap.R
import java.util.Locale

/**
 * 音声合成エンジンの抽象インターフェース。
 */
interface TextToSpeechEngine {
    fun speak(text: String): Boolean
    fun stop()
    fun isReady(): Boolean
}

/**
 * 開発者オプション用の音声テストフレーズ。
 */
enum class VoiceTestPhrase(val label: String, val text: String) {
    STRAIGHT("直進", "直進です"),
    LEFT("左折", "左折です"),
    RIGHT("右折", "右折です"),
}

/**
 * 音声エンジンの種類。
 */
enum class VoiceEngineType(val label: String) {
    VOICEVOX_METAN("四国めたん (VOICEVOX)"),
    SYSTEM_TTS("Android標準 TTS"),
}

/**
 * 音声テストのコントローラー。
 */
class VoiceTestController(
    private var engine: TextToSpeechEngine,
) {
    var lastSpokenText: String? = null
        private set

    fun setEngine(newEngine: TextToSpeechEngine) {
        stop()
        this.engine = newEngine
    }

    fun playPhrase(phrase: VoiceTestPhrase): Boolean {
        val success = engine.speak(phrase.text)
        if (success) {
            lastSpokenText = phrase.text
        }
        return success
    }

    fun stop() {
        engine.stop()
    }
}

/**
 * 四国めたん (VOICEVOX) の事前生成オーディオを再生するエンジン。
 * 完全オフライン・遅延ゼロ・超低負荷で動作する。
 */
class ShikokuMetanAudioEngine(
    private val context: Context,
) : TextToSpeechEngine {
    private var mediaPlayer: MediaPlayer? = null

    override fun speak(text: String): Boolean {
        stop()
        val rawResId = when (text) {
            VoiceTestPhrase.STRAIGHT.text -> R.raw.voice_straight
            VoiceTestPhrase.LEFT.text -> R.raw.voice_turn_left
            VoiceTestPhrase.RIGHT.text -> R.raw.voice_turn_right
            else -> return false
        }
        return try {
            mediaPlayer = MediaPlayer.create(context, rawResId).apply {
                setOnCompletionListener {
                    it.release()
                    if (mediaPlayer == it) {
                        mediaPlayer = null
                    }
                }
                start()
            }
            true
        } catch (t: Throwable) {
            Log.e("MetanAudio", "Failed to play audio resource", t)
            false
        }
    }

    override fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (t: Throwable) {
            // ignore
        }
        mediaPlayer = null
    }

    override fun isReady(): Boolean = true

    fun shutdown() {
        stop()
    }
}

/**
 * Android 標準 TextToSpeech を利用した実装クラス。
 */
class AndroidTextToSpeechEngine(
    context: Context,
    private val onReadyChanged: ((Boolean) -> Unit)? = null,
) : TextToSpeechEngine, TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready: Boolean = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val langResult = tts?.setLanguage(Locale.JAPAN)
            ready = !(langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED)
            if (!ready) {
                Log.w("AndroidTTS", "Japanese TTS not supported or missing data: $langResult")
            }
        } else {
            ready = false
            Log.e("AndroidTTS", "TTS initialization failed with code: $status")
        }
        onReadyChanged?.invoke(ready)
    }

    override fun speak(text: String): Boolean {
        if (!ready || tts == null) {
            Log.w("AndroidTTS", "Cannot speak, engine not ready (ready=$ready)")
            return false
        }
        val utteranceId = "tts_voice_test_${System.currentTimeMillis()}"
        val res = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        return res == TextToSpeech.SUCCESS
    }

    override fun stop() {
        tts?.stop()
    }

    override fun isReady(): Boolean = ready

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
