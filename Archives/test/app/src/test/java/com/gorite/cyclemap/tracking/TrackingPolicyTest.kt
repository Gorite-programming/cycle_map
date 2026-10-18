package com.gorite.cyclemap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingPolicyTest {

    @Test
    fun testForegroundAlwaysTracks() {
        // 前面表示中 (RESUMED) はナビ・記録の有無にかかわらず常に追跡
        assertTrue(TrackingPolicy.shouldTrack(AppLifecycleState.RESUMED, isNavigating = false, isRecording = false))
        assertTrue(TrackingPolicy.shouldTrack(AppLifecycleState.RESUMED, isNavigating = true, isRecording = false))
        assertTrue(TrackingPolicy.shouldTrack(AppLifecycleState.RESUMED, isNavigating = false, isRecording = true))
        assertTrue(TrackingPolicy.shouldTrack(AppLifecycleState.RESUMED, isNavigating = true, isRecording = true))
    }

    @Test
    fun testBackgroundNavigatingContinuesTracking() {
        // バックグラウンド・画面オフでもナビ中なら追跡を継続 (音声案内のため)
        assertTrue(TrackingPolicy.shouldTrack(AppLifecycleState.PAUSED_OR_STOPPED, isNavigating = true, isRecording = false))
        assertTrue(TrackingPolicy.shouldTrack(AppLifecycleState.PAUSED_OR_STOPPED, isNavigating = true, isRecording = true))
    }

    @Test
    fun testBackgroundRecordingContinuesTracking() {
        // バックグラウンド・画面オフでもGPX記録中なら追跡を継続
        assertTrue(TrackingPolicy.shouldTrack(AppLifecycleState.PAUSED_OR_STOPPED, isNavigating = false, isRecording = true))
    }

    @Test
    fun testBackgroundIdleStopsTracking() {
        // ナビも記録もしていない状態でバックグラウンドに回ったときは追跡を停止 (バッテリー節約)
        assertFalse(TrackingPolicy.shouldTrack(AppLifecycleState.PAUSED_OR_STOPPED, isNavigating = false, isRecording = false))
    }

    @Test
    fun testTrackingStateControllerTransitions() {
        var startCount = 0
        var stopCount = 0
        val controller = TrackingStateController(
            onStartTracking = { startCount++ },
            onStopTracking = { stopCount++ },
        )

        // 1. 初期状態: 停止
        assertFalse(controller.isTrackingActive)

        // 2. 前面表示 (RESUMED, 非ナビ, 非記録) -> 追跡開始
        controller.update(AppLifecycleState.RESUMED, isNavigating = false, isRecording = false)
        assertTrue(controller.isTrackingActive)
        assertEquals(1, startCount)
        assertEquals(0, stopCount)

        // 重複更新しても再始動は呼ばれない
        controller.update(AppLifecycleState.RESUMED, isNavigating = false, isRecording = false)
        assertEquals(1, startCount)

        // 3. バックグラウンド移行 (非ナビ, 非記録) -> 追跡停止 (通知消去)
        controller.update(AppLifecycleState.PAUSED_OR_STOPPED, isNavigating = false, isRecording = false)
        assertFalse(controller.isTrackingActive)
        assertEquals(1, startCount)
        assertEquals(1, stopCount)

        // 4. バックグラウンドのままナビ開始 -> 追跡再開 (音声案内のため)
        controller.update(AppLifecycleState.PAUSED_OR_STOPPED, isNavigating = true, isRecording = false)
        assertTrue(controller.isTrackingActive)
        assertEquals(2, startCount)
        assertEquals(1, stopCount)

        // 5. バックグラウンドのままナビ終了 -> 追跡停止
        controller.update(AppLifecycleState.PAUSED_OR_STOPPED, isNavigating = false, isRecording = false)
        assertFalse(controller.isTrackingActive)
        assertEquals(2, startCount)
        assertEquals(2, stopCount)

        // 6. バックグラウンドのままGPX記録開始 -> 追跡再開
        controller.update(AppLifecycleState.PAUSED_OR_STOPPED, isNavigating = false, isRecording = true)
        assertTrue(controller.isTrackingActive)
        assertEquals(3, startCount)
        assertEquals(2, stopCount)

        // 7. バックグラウンドのままGPX記録停止 -> 追跡停止
        controller.update(AppLifecycleState.PAUSED_OR_STOPPED, isNavigating = false, isRecording = false)
        assertFalse(controller.isTrackingActive)
        assertEquals(3, startCount)
        assertEquals(3, stopCount)

        // 8. アプリ前面復帰 (RESUMED) -> 自動で追跡再開
        controller.update(AppLifecycleState.RESUMED, isNavigating = false, isRecording = false)
        assertTrue(controller.isTrackingActive)
        assertEquals(4, startCount)
        assertEquals(3, stopCount)
    }
}
