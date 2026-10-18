package com.gorite.cyclemap.tracking

/**
 * アプリの画面表示状態。
 */
enum class AppLifecycleState {
    RESUMED,
    PAUSED_OR_STOPPED,
}

/**
 * バッテリー最適化のための位置追跡制御ポリシー。
 *
 * 1. 前面表示中 (RESUMED): 常に追跡 (高精度)
 * 2. ナビ中 または GPX記録中: バックグラウンドでも追跡を継続 (Foreground Service維持)
 * 3. 何もしていないバックグラウンド (非ナビ・非記録): 追跡を停止し、通知も消去
 */
object TrackingPolicy {
    fun shouldTrack(
        lifecycleState: AppLifecycleState,
        isNavigating: Boolean,
        isRecording: Boolean,
    ): Boolean {
        return when {
            lifecycleState == AppLifecycleState.RESUMED -> true
            isNavigating || isRecording -> true
            else -> false
        }
    }
}

/**
 * 位置追跡サービスの起動・停止状態の遷移を管理するコントローラー。
 * 状態遷移ロジックを一元化し、単体テスト可能にする。
 */
class TrackingStateController(
    private val onStartTracking: () -> Unit,
    private val onStopTracking: () -> Unit,
) {
    var isTrackingActive = false
        private set

    fun update(
        lifecycleState: AppLifecycleState,
        isNavigating: Boolean,
        isRecording: Boolean,
    ) {
        val shouldTrack = TrackingPolicy.shouldTrack(lifecycleState, isNavigating, isRecording)
        if (shouldTrack && !isTrackingActive) {
            isTrackingActive = true
            onStartTracking()
        } else if (!shouldTrack && isTrackingActive) {
            isTrackingActive = false
            onStopTracking()
        }
    }
}
