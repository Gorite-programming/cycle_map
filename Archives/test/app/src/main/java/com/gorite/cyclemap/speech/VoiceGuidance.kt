package com.gorite.cyclemap.speech

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.gorite.cyclemap.R
import com.gorite.cyclemap.routing.InstructionType
import com.gorite.cyclemap.routing.RouteInstruction
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
 * 音声案内の動作モード。
 * TTS は全廃され、四国めたん (VOICEVOX) MP3 の再生かオフの2択となります。
 */
enum class VoiceGuidanceMode(val label: String) {
    VOICEVOX("四国めたん (VOICEVOX)"),
    OFF("音声案内なし");

    companion object {
        fun fromString(value: String?): VoiceGuidanceMode = when (value?.uppercase()) {
            "OFF" -> OFF
            else -> VOICEVOX // 旧設定値 "SYSTEM_TTS" などは安全に VOICEVOX へ移行
        }
    }
}

/**
 * 案内トリガーの距離段階。
 */
enum class VoiceGuidanceStage {
    FAR,        // およそ300m前
    NEAR,       // およそ100m前
    IMMEDIATE,  // 直前 (およそ30m前)
    ARRIVED,    // 目的地到着
    REROUTE,    // ルート再探索
}

/**
 * 案内判定の閾値定数。後から容易に微調整可能。
 */
object VoiceGuidanceThresholds {
    const val DISTANCE_FAR_METERS = 300.0
    const val DISTANCE_NEAR_METERS = 100.0
    const val DISTANCE_IMMEDIATE_METERS = 30.0
    const val ARRIVAL_DISTANCE_METERS = 30.0
    const val FAR_TOLERANCE_METERS = 40.0   // 300m前案内を許容する範囲 [260, 340]
    const val NEAR_TOLERANCE_METERS = 20.0  // 100m前案内を許容する範囲 [80, 120]
}

/**
 * 発声すべき案内情報。
 */
data class VoiceCue(
    val stage: VoiceGuidanceStage,
    val instructionIndex: Int,
    val spokenText: String,
    val rawResId: Int? = null,
)

/**
 * 音声リソース再生機能のインターフェース。
 */
interface VoiceAudioPlayer {
    fun playResId(rawResId: Int): Boolean
    fun stop()
    fun isReady(): Boolean = true
}

/**
 * 四国めたん (VOICEVOX) の事前生成オーディオを再生するエンジン。
 * 完全オフライン・遅延ゼロ・超低負荷で動作する。
 */
class ShikokuMetanAudioEngine(
    private val context: Context,
) : VoiceAudioPlayer, TextToSpeechEngine {
    private var mediaPlayer: MediaPlayer? = null

    override fun playResId(rawResId: Int): Boolean {
        stop()
        return try {
            mediaPlayer = MediaPlayer.create(context, rawResId)?.apply {
                setOnCompletionListener {
                    it.release()
                    if (mediaPlayer == it) {
                        mediaPlayer = null
                    }
                }
                start()
            }
            mediaPlayer != null
        } catch (t: Throwable) {
            Log.e("MetanAudio", "Failed to play audio resource ID: $rawResId", t)
            false
        }
    }

    override fun speak(text: String): Boolean {
        stop()
        // テキストからカタログを逆引きして再生
        val entry = VoicePhraseCatalog.PHRASES.values.firstOrNull { it.text == text }
        return if (entry != null) {
            playResId(entry.rawResId)
        } else {
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
 * Android 標準 TextToSpeech (リソース欠落時の最後の安全網としてのみ保持)。
 * 通常のナビゲーションではすべて VOICEVOX MP3 がヒットするため到達しません。
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
        val utteranceId = "tts_fallback_${System.currentTimeMillis()}"
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

/**
 * 音声案内再生エンジン。
 * 全フレーズが四国めたん MP3 で再生されます。
 */
class HybridVoiceGuidanceEngine(
    private val metanEngine: ShikokuMetanAudioEngine,
    private val fallbackTtsEngine: AndroidTextToSpeechEngine? = null,
    var mode: VoiceGuidanceMode = VoiceGuidanceMode.VOICEVOX,
) {
    fun playCue(cue: VoiceCue): Boolean {
        if (mode == VoiceGuidanceMode.OFF) return false

        stop()

        // 1. VOICEVOX の MP3 リソースが存在すれば再生
        if (cue.rawResId != null && metanEngine.playResId(cue.rawResId)) {
            return true
        }

        // 2. 万一のリソース欠落時の最後の安全網 (通常は到達しない)
        return fallbackTtsEngine?.speak(cue.spokenText) ?: false
    }

    fun playResId(rawResId: Int): Boolean {
        if (mode == VoiceGuidanceMode.OFF) return false
        stop()
        return metanEngine.playResId(rawResId)
    }

    fun stop() {
        metanEngine.stop()
        fallbackTtsEngine?.stop()
    }

    fun shutdown() {
        metanEngine.shutdown()
        fallbackTtsEngine?.shutdown()
    }
}

/**
 * 開発者オプション用のテストコントローラー。
 * 全フレーズの試聴に対応。
 */
class VoiceTestController(
    private var player: VoiceAudioPlayer,
) {
    var lastSpokenText: String? = null
        private set

    fun setPlayer(newPlayer: VoiceAudioPlayer) {
        stop()
        this.player = newPlayer
    }

    fun playPhrase(entry: VoicePhraseCatalog.PhraseEntry): Boolean {
        if (!player.isReady()) return false
        val success = player.playResId(entry.rawResId)
        if (success) {
            lastSpokenText = entry.text
        }
        return success
    }

    fun playResId(rawResId: Int, text: String): Boolean {
        if (!player.isReady()) return false
        val success = player.playResId(rawResId)
        if (success) {
            lastSpokenText = text
        }
        return success
    }

    fun stop() {
        player.stop()
    }
}

/**
 * 案内トリガーの判定器（純粋ロジック・JVMテスト可能）。
 */
object VoiceTriggerEvaluator {

    /**
     * InstructionType と転回角から、VoicePhraseCatalog の基本キーを解決する。
     */
    fun resolvePhraseKey(type: InstructionType, turnAngleDegrees: Double = 0.0): String = when (type) {
        InstructionType.STRAIGHT -> "straight"
        InstructionType.LEFT_TURN -> "turn_left"
        InstructionType.RIGHT_TURN -> "turn_right"
        InstructionType.SHARP_LEFT -> "sharp_left"
        InstructionType.SHARP_RIGHT -> "sharp_right"
        InstructionType.U_TURN_LEFT -> "u_turn_left"
        InstructionType.U_TURN_RIGHT -> "u_turn_right"
        InstructionType.FORK_LEFT -> "fork_left"
        InstructionType.FORK_RIGHT -> "fork_right"
        InstructionType.DIAGONAL_FORK_LEFT -> "diagonal_fork_left"
        InstructionType.DIAGONAL_FORK_RIGHT -> "diagonal_fork_right"
        InstructionType.FORK_UP_LEFT -> "fork_up_left"
        InstructionType.FORK_DOWN_LEFT -> "fork_down_left"
        InstructionType.FORK_UP_RIGHT -> "fork_up_right"
        InstructionType.FORK_DOWN_RIGHT -> "fork_down_right"
        InstructionType.FORK_BOTH -> if (turnAngleDegrees < 0.0) "fork_both_left" else "fork_both_right"
        InstructionType.Y_JUNCTION -> if (turnAngleDegrees < 0.0) "y_junction_left" else "y_junction_right"
        InstructionType.T_JUNCTION -> when {
            turnAngleDegrees < -10.0 -> "t_junction_left"
            turnAngleDegrees > 10.0 -> "t_junction_right"
            else -> "t_junction"
        }
        InstructionType.CROSS_JUNCTION -> when {
            turnAngleDegrees < -45.0 -> "cross_left"
            turnAngleDegrees > 45.0 -> "cross_right"
            turnAngleDegrees < -10.0 -> "cross_slight_left"
            turnAngleDegrees > 10.0 -> "cross_slight_right"
            else -> "cross_straight"
        }
        InstructionType.MULTI_JUNCTION -> when {
            turnAngleDegrees < -45.0 -> "multi_left"
            turnAngleDegrees > 45.0 -> "multi_right"
            turnAngleDegrees < -10.0 -> "multi_slight_left"
            turnAngleDegrees > 10.0 -> "multi_slight_right"
            else -> "multi_straight"
        }
        InstructionType.MERGE -> "merge"
        InstructionType.LANE_INCREASE -> "lane_increase"
        InstructionType.LANE_DECREASE -> "lane_decrease"
        InstructionType.SIDE_ROAD_ENTER -> if (turnAngleDegrees < 0.0) "side_road_enter_left" else "side_road_enter_right"
        InstructionType.SIDE_ROAD_EXIT -> if (turnAngleDegrees < 0.0) "side_road_exit_left" else "side_road_exit_right"
        InstructionType.ROUNDABOUT -> "roundabout"
        InstructionType.RAMP_ENTRY -> if (turnAngleDegrees < 0.0) "ramp_entry_left" else "ramp_entry_right"
        InstructionType.RAMP_EXIT -> if (turnAngleDegrees < 0.0) "ramp_exit_left" else "ramp_exit_right"
        InstructionType.CONSECUTIVE_FORK -> "consecutive_fork"
    }

    /**
     * 現在の走行進捗から発声すべきVoiceCueを判定する。
     */
    fun evaluate(
        traveledMeters: Double,
        remainingMeters: Double?,
        instructions: List<RouteInstruction>,
        isOffRoute: Boolean,
        spokenKeys: Set<String>,
    ): VoiceCue? {
        // 1. ルート逸脱・再探索の判定
        if (isOffRoute) {
            val key = "REROUTE"
            if (!spokenKeys.contains(key)) {
                val phrase = VoicePhraseCatalog.PHRASES["reroute"]
                return VoiceCue(
                    stage = VoiceGuidanceStage.REROUTE,
                    instructionIndex = -1,
                    spokenText = phrase?.text ?: "ルートから外れました。再探索します",
                    rawResId = phrase?.rawResId,
                )
            }
            return null
        }

        // 2. 目的地到着の判定
        if (remainingMeters != null && remainingMeters <= VoiceGuidanceThresholds.ARRIVAL_DISTANCE_METERS) {
            val key = "ARRIVED"
            if (!spokenKeys.contains(key)) {
                val phrase = VoicePhraseCatalog.PHRASES["arrived"]
                return VoiceCue(
                    stage = VoiceGuidanceStage.ARRIVED,
                    instructionIndex = -1,
                    spokenText = phrase?.text ?: "目的地に到着しました",
                    rawResId = phrase?.rawResId,
                )
            }
            return null
        }

        // 3. 次の案内地点（RouteInstruction）の探索
        val nextInstructionIndex = instructions.indexOfFirst { it.distanceFromStartMeters > traveledMeters }
        if (nextInstructionIndex == -1) return null
        val nextInstruction = instructions[nextInstructionIndex]

        val distanceToNext = (nextInstruction.distanceFromStartMeters - traveledMeters).coerceAtLeast(0.0)

        // 4. 距離段階に応じたトリガー判定
        return when {
            // 直前 (約30m前)
            distanceToNext <= VoiceGuidanceThresholds.DISTANCE_IMMEDIATE_METERS -> {
                val key = "$nextInstructionIndex:IMMEDIATE"
                if (!spokenKeys.contains(key)) {
                    buildCue(nextInstructionIndex, nextInstruction, VoiceGuidanceStage.IMMEDIATE)
                } else null
            }
            // 約100m前
            distanceToNext <= VoiceGuidanceThresholds.DISTANCE_NEAR_METERS + VoiceGuidanceThresholds.NEAR_TOLERANCE_METERS &&
                distanceToNext >= VoiceGuidanceThresholds.DISTANCE_NEAR_METERS - VoiceGuidanceThresholds.NEAR_TOLERANCE_METERS -> {
                val key = "$nextInstructionIndex:NEAR"
                if (!spokenKeys.contains(key) && !spokenKeys.contains("$nextInstructionIndex:IMMEDIATE")) {
                    buildCue(nextInstructionIndex, nextInstruction, VoiceGuidanceStage.NEAR)
                } else null
            }
            // 約300m前
            distanceToNext <= VoiceGuidanceThresholds.DISTANCE_FAR_METERS + VoiceGuidanceThresholds.FAR_TOLERANCE_METERS &&
                distanceToNext >= VoiceGuidanceThresholds.DISTANCE_FAR_METERS - VoiceGuidanceThresholds.FAR_TOLERANCE_METERS -> {
                val key = "$nextInstructionIndex:FAR"
                if (!spokenKeys.contains(key) &&
                    !spokenKeys.contains("$nextInstructionIndex:NEAR") &&
                    !spokenKeys.contains("$nextInstructionIndex:IMMEDIATE")
                ) {
                    buildCue(nextInstructionIndex, nextInstruction, VoiceGuidanceStage.FAR)
                } else null
            }
            else -> null
        }
    }

    fun buildCue(
        index: Int,
        instruction: RouteInstruction,
        stage: VoiceGuidanceStage,
    ): VoiceCue {
        val phraseKey = resolvePhraseKey(instruction.type, instruction.turnAngleDegrees)
        val rawResId = VoicePhraseCatalog.getResId(phraseKey, stage)
        val text = VoicePhraseCatalog.getText(phraseKey, stage) ?: "まもなく案内地点です"

        return VoiceCue(
            stage = stage,
            instructionIndex = index,
            spokenText = text,
            rawResId = rawResId,
        )
    }

    fun makeKey(cue: VoiceCue): String = when (cue.stage) {
        VoiceGuidanceStage.REROUTE -> "REROUTE"
        VoiceGuidanceStage.ARRIVED -> "ARRIVED"
        else -> "${cue.instructionIndex}:${cue.stage.name}"
    }
}

/**
 * 本番ナビゲーション用音声案内マネージャー。
 */
class VoiceGuidanceNavigator(
    val engine: HybridVoiceGuidanceEngine,
) {
    private val spokenKeys = mutableSetOf<String>()
    var lastSpokenCue: VoiceCue? = null
        private set

    fun setMode(mode: VoiceGuidanceMode) {
        engine.mode = mode
    }

    fun onLocationUpdated(
        traveledMeters: Double,
        remainingMeters: Double?,
        instructions: List<RouteInstruction>,
        isOffRoute: Boolean = false,
    ) {
        val cue = VoiceTriggerEvaluator.evaluate(
            traveledMeters = traveledMeters,
            remainingMeters = remainingMeters,
            instructions = instructions,
            isOffRoute = isOffRoute,
            spokenKeys = spokenKeys,
        ) ?: return

        spokenKeys.add(VoiceTriggerEvaluator.makeKey(cue))
        lastSpokenCue = cue
        engine.playCue(cue)
    }

    fun reset() {
        spokenKeys.clear()
        lastSpokenCue = null
        engine.stop()
    }

    fun stop() {
        engine.stop()
    }

    fun shutdown() {
        engine.shutdown()
    }
}
