package com.gorite.cyclemap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 県別グラフ選択 (RoutingGraphSelector) の検証。
 * 座標は実測値 (広島駅・山口市役所・東京駅)。他県グラフの使い回しはしない。
 */
class GraphSelectionTest {

    @Test
    fun hiroshimaStation_selectsHiroshimaGraph() {
        val sel = RoutingGraphSelector.select(34.3976, 132.4756)
        assertTrue(sel is RoutingGraphSelection.Available)
        sel as RoutingGraphSelection.Available
        assertEquals("hiroshima", sel.prefectureId)
        assertEquals("広島県", sel.prefectureName)
        assertEquals("Hiroshima.graph", sel.fileName)
        assertEquals("Hiroshima.graph.idx", RoutingGraphSelector.indexFileName(sel.fileName))
    }

    @Test
    fun yamaguchiCityHall_selectsYamaguchiGraph() {
        val sel = RoutingGraphSelector.select(34.1785, 131.4737)
        assertTrue(sel is RoutingGraphSelection.Available)
        sel as RoutingGraphSelection.Available
        assertEquals("yamaguchi", sel.prefectureId)
        assertEquals("山口県", sel.prefectureName)
        assertEquals("yamaguchi.graph", sel.fileName)
    }

    @Test
    fun unsupportedPrefecture_isUnavailable() {
        // 東京駅: 県判定はできるが対応グラフが無い → 他県流用せず明示失敗。
        val sel = RoutingGraphSelector.select(35.6812, 139.7671)
        assertTrue(sel is RoutingGraphSelection.Unavailable)
        assertEquals("東京都", (sel as RoutingGraphSelection.Unavailable).prefectureName)
    }

    @Test
    fun outsideJapan_isUnavailableWithNull() {
        val sel = RoutingGraphSelector.select(30.0, 135.0)
        assertTrue(sel is RoutingGraphSelection.Unavailable)
        assertNull((sel as RoutingGraphSelection.Unavailable).prefectureName)
    }
}
