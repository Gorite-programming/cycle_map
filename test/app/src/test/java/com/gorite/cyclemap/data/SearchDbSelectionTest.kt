package com.gorite.cyclemap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 検索DB選択 (SearchDbSelector) の検証。
 * routingグラフ選択と同型：対応県→実ファイル名、未対応県→他県流用なし。
 */
class SearchDbSelectionTest {

    @Test
    fun hiroshimaStation_selectsHiroshimaSearchDb() {
        val sel = SearchDbSelector.select(34.3976, 132.4756)
        assertTrue(sel is SearchDbSelection.Available)
        sel as SearchDbSelection.Available
        assertEquals("hiroshima", sel.prefectureId)
        assertEquals("広島県", sel.prefectureName)
        assertEquals("Hiroshima.search.db", sel.fileName)
    }

    @Test
    fun yamaguchiCityHall_selectsLegacySearchDb() {
        val sel = SearchDbSelector.select(34.1785, 131.4737)
        assertTrue(sel is SearchDbSelection.Available)
        sel as SearchDbSelection.Available
        assertEquals("yamaguchi", sel.prefectureId)
        assertEquals("search.db", sel.fileName)
    }

    @Test
    fun unsupportedPrefecture_isUnavailable() {
        val sel = SearchDbSelector.select(35.6812, 139.7671)
        assertTrue(sel is SearchDbSelection.Unavailable)
        assertEquals("東京都", (sel as SearchDbSelection.Unavailable).prefectureName)
    }

    @Test
    fun findFirstAvailableDb_testCases() {
        val tempDir = java.nio.file.Files.createTempDirectory("searchdb_test").toFile()
        try {
            // 1. 空のディレクトリ → null
            org.junit.Assert.assertNull(SearchDbSelector.findFirstAvailableDb(tempDir))

            // 2. B.search.db を作成 → B.search.db
            val bDb = java.io.File(tempDir, "B.search.db").apply { createNewFile() }
            assertEquals(bDb, SearchDbSelector.findFirstAvailableDb(tempDir))

            // 3. 昇順で先頭の A.search.db を作成 → A.search.db
            val aDb = java.io.File(tempDir, "A.search.db").apply { createNewFile() }
            assertEquals(aDb, SearchDbSelector.findFirstAvailableDb(tempDir))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
