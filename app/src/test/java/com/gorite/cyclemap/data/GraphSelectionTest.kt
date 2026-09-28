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

    @Test
    fun findFirstAvailableGraph_testCases() {
        val tempDir = java.nio.file.Files.createTempDirectory("graph_test").toFile()
        try {
            // 1. 空のディレクトリ → null
            assertNull(RoutingGraphSelector.findFirstAvailableGraph(tempDir))

            // 2. .idx が欠けているグラフ (B.graph) は無視し、有効なペア (C.graph) が返る
            java.io.File(tempDir, "B.graph").createNewFile()
            val cGraph = java.io.File(tempDir, "C.graph").apply { createNewFile() }
            val cIdx = java.io.File(tempDir, "C.graph.idx").apply { createNewFile() }

            val res1 = RoutingGraphSelector.findFirstAvailableGraph(tempDir)
            assertEquals(cGraph, res1?.first)
            assertEquals(cIdx, res1?.second)

            // 3. 昇順で最先頭のペア (A.graph) を追加すると A.graph のペアが返る
            val aGraph = java.io.File(tempDir, "A.graph").apply { createNewFile() }
            val aIdx = java.io.File(tempDir, "A.graph.idx").apply { createNewFile() }

            val res2 = RoutingGraphSelector.findFirstAvailableGraph(tempDir)
            assertEquals(aGraph, res2?.first)
            assertEquals(aIdx, res2?.second)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
