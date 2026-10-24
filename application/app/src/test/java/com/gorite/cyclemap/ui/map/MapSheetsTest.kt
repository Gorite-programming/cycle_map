package com.gorite.cyclemap.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapSheetsTest {

    @Test
    fun testBottomSheetAnchorHeights() {
        assertEquals(180, BottomSheetAnchor.Peek.heightDp.value.toInt())
        assertEquals(340, BottomSheetAnchor.Half.heightDp.value.toInt())
        assertEquals(560, BottomSheetAnchor.Full.heightDp.value.toInt())
        assertTrue(BottomSheetAnchor.Peek.heightDp < BottomSheetAnchor.Half.heightDp)
        assertTrue(BottomSheetAnchor.Half.heightDp < BottomSheetAnchor.Full.heightDp)
    }

    @Test
    fun testRouteCandidateProperties() {
        val candidate1 = RouteCandidate(
            index = 0,
            title = "おすすめルート (平坦)",
            distanceMeters = 5400.0,
            durationSeconds = 1200.0,
            elevationGainMeters = 25.0,
            isPrimary = true
        )
        val candidate2 = RouteCandidate(
            index = 1,
            title = "最短ルート",
            distanceMeters = 4800.0,
            durationSeconds = 1150.0,
            elevationGainMeters = 45.0,
            isPrimary = false
        )

        assertEquals(0, candidate1.index)
        assertTrue(candidate1.isPrimary)
        assertEquals(5400.0, candidate1.distanceMeters, 0.1)

        assertEquals(1, candidate2.index)
        assertEquals(false, candidate2.isPrimary)
        assertEquals(4800.0, candidate2.distanceMeters, 0.1)
    }
}
