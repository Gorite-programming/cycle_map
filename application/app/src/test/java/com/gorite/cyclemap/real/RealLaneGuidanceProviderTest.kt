package com.gorite.cyclemap.real

import com.gorite.cyclemap.contract.TurnType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealLaneGuidanceProviderTest {

    @Test
    fun getLaneHints_noDataAvailable_returnsEmptyListHonestly() = runBlocking {
        val provider = RealLaneGuidanceProvider(laneDataSource = null)
        val hints = provider.getLaneHints(12345L, TurnType.Left)
        assertTrue("データが存在しない場合は正直に空リストを返すこと", hints.isEmpty())
    }

    @Test
    fun getLaneHints_threeLanes_leftTurnRecommended() = runBlocking {
        val provider = RealLaneGuidanceProvider(
            laneDataSource = { edgeId ->
                if (edgeId == 100L) "left|through|right" else null
            }
        )

        val hints = provider.getLaneHints(100L, TurnType.Left)
        assertEquals(3, hints.size)
        // 1番目: left (推奨)
        assertEquals(TurnType.Left, hints[0].direction)
        assertTrue(hints[0].isRecommended)
        // 2番目: through (非推奨)
        assertEquals(TurnType.Straight, hints[1].direction)
        assertFalse(hints[1].isRecommended)
        // 3番目: right (非推奨)
        assertEquals(TurnType.Right, hints[2].direction)
        assertFalse(hints[2].isRecommended)
    }

    @Test
    fun getLaneHints_compositeLanes_straightRecommended() = runBlocking {
        val provider = RealLaneGuidanceProvider(
            laneDataSource = { "through|through;right" }
        )

        val hints = provider.getLaneHints(200L, TurnType.Straight)
        assertEquals(2, hints.size)
        assertTrue(hints[0].isRecommended)
        assertFalse(hints[1].isRecommended)
    }
}
