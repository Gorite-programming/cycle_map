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
}
