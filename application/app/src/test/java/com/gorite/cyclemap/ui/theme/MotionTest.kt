package com.gorite.cyclemap.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {

    @Test
    fun durations_areWithinSpecifiedGuidelines() {
        // 出入り 200〜300ms 目安
        assertTrue("DURATION_MEDIUM must be between 200 and 300", Motion.DURATION_MEDIUM in 200..300)
        // 小さな要素 150ms 前後
        assertTrue("DURATION_SHORT must be around 150ms", Motion.DURATION_SHORT in 100..200)
        // 案内バナー切替は 200ms 以内
        assertTrue("DURATION_BANNER must be <= 200ms", Motion.DURATION_BANNER <= 200)
        assertTrue("DURATION_BANNER must be positive", Motion.DURATION_BANNER > 0)
        // 画面遷移 300ms 前後
        assertTrue("DURATION_LONG must be around 300ms", Motion.DURATION_LONG in 250..400)
    }

    @Test
    fun resolveEffectiveAnimation_requiresBothUserAndSystemEnabled() {
        assertTrue(Motion.resolveEffectiveAnimation(userEnabled = true, systemEnabled = true))
        assertFalse(Motion.resolveEffectiveAnimation(userEnabled = true, systemEnabled = false))
        assertFalse(Motion.resolveEffectiveAnimation(userEnabled = false, systemEnabled = true))
        assertFalse(Motion.resolveEffectiveAnimation(userEnabled = false, systemEnabled = false))
    }

    @Test
    fun duration_returnsZeroWhenDisabled() {
        assertEquals(0, Motion.duration(Motion.DURATION_MEDIUM, enabled = false))
        assertEquals(0, Motion.duration(Motion.DURATION_SHORT, enabled = false))
        assertEquals(Motion.DURATION_MEDIUM, Motion.duration(Motion.DURATION_MEDIUM, enabled = true))
        assertEquals(Motion.DURATION_SHORT, Motion.duration(Motion.DURATION_SHORT, enabled = true))
    }

    @Test
    fun motionTween_returnsSnapWhenDisabled() {
        val specDisabled = Motion.motionTween<Float>(enabled = false)
        assertTrue("Spec should be SnapSpec when disabled", specDisabled is SnapSpec)

        val specEnabled = Motion.motionTween<Float>(enabled = true)
        assertTrue("Spec should be TweenSpec when enabled", specEnabled is TweenSpec)
    }

    @Test
    fun transitions_returnNoneWhenDisabled() {
        assertEquals(EnterTransition.None, Motion.bannerEnter(enabled = false))
        assertEquals(ExitTransition.None, Motion.bannerExit(enabled = false))
        assertEquals(EnterTransition.None, Motion.topBarEnter(enabled = false))
        assertEquals(ExitTransition.None, Motion.topBarExit(enabled = false))
        assertEquals(EnterTransition.None, Motion.bottomBarEnter(enabled = false))
        assertEquals(ExitTransition.None, Motion.bottomBarExit(enabled = false))
        assertEquals(EnterTransition.None, Motion.fadeEnter(enabled = false))
        assertEquals(ExitTransition.None, Motion.fadeExit(enabled = false))
    }

    @Test
    fun transitions_returnActiveTransitionsWhenEnabled() {
        assertNotEquals(EnterTransition.None, Motion.bannerEnter(enabled = true))
        assertNotEquals(ExitTransition.None, Motion.bannerExit(enabled = true))
        assertNotEquals(EnterTransition.None, Motion.topBarEnter(enabled = true))
        assertNotEquals(ExitTransition.None, Motion.topBarExit(enabled = true))
        assertNotEquals(EnterTransition.None, Motion.bottomBarEnter(enabled = true))
        assertNotEquals(ExitTransition.None, Motion.bottomBarExit(enabled = true))
        assertNotEquals(EnterTransition.None, Motion.fadeEnter(enabled = true))
        assertNotEquals(ExitTransition.None, Motion.fadeExit(enabled = true))
    }
}
