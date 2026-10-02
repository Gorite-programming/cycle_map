package com.gorite.cyclemap

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SearchHelperTest {

    @Test
    fun searchPlaces_returnsEmptyWhenFileDoesNotExist() {
        val nonExistent = File("/tmp/non_existent_search.db")
        val result = searchPlaces(nonExistent, "test")
        assertEquals(emptyList<SearchResult>(), result)
    }

    @Test
    fun buildFtsQuery_formatsPrefixQuery() {
        assertEquals("\"セブン\"* \"イレブン\"*", buildFtsQuery("セブン イレブン"))
        assertEquals("\"広島\"*", buildFtsQuery("広島"))
        assertEquals("\"cafe\"*", buildFtsQuery("cafe"))
        assertEquals("\"sebun'irebun\"*", buildFtsQuery("sebun'irebun"))
        assertEquals("\"ダブル\"\"クォート\"*", buildFtsQuery("ダブル\"クォート"))
    }

    @Test
    fun buildLikeTextConditions_withSearchTextColumn_singleWord() {
        val (whereClause, args) = buildLikeTextConditions("せぶん", "search_text")
        assertEquals("search_text LIKE ?", whereClause)
        assertEquals(listOf("%せぶん%"), args)
    }

    @Test
    fun buildLikeTextConditions_withSearchTextColumn_multiWordAndMatching() {
        // FTS5非対応時の複数単語AND結合 & 中間一致 (%word%)
        val (whereClause, args) = buildLikeTextConditions("せぶん いれぶん", "search_text")
        assertEquals("search_text LIKE ? AND search_text LIKE ?", whereClause)
        assertEquals(listOf("%せぶん%", "%いれぶん%"), args)
    }

    @Test
    fun buildLikeTextConditions_withLegacyNameColumn_multiWord() {
        // レガシーDB (search_textカラムなし) での name 検索
        val (whereClause, args) = buildLikeTextConditions("ローソン 広島", "name")
        assertEquals("name LIKE ? AND name LIKE ?", whereClause)
        assertEquals(listOf("%ローソン%", "%広島%"), args)
    }

    @Test
    fun shouldShowFtsNotice_returnsFalseWhenSearchTextAvailableEvenIfFts5Unsupported() {
        // FTS5非対応でも search_text カラムがあれば曖昧検索可能なのでバナーは表示しない
        val shouldShow = shouldShowFtsNotice(ftsSupported = false, hasSearchText = true, hasShownThisSession = false)
        assertEquals(false, shouldShow)
    }

    @Test
    fun shouldShowFtsNotice_returnsTrueOnlyWhenBothFtsAndSearchTextUnavailable() {
        // FTS5も非対応で search_text も無いレガシーDBの場合のみ通知
        val shouldShow = shouldShowFtsNotice(ftsSupported = false, hasSearchText = false, hasShownThisSession = false)
        assertEquals(true, shouldShow)

        // ただしセッション中に既に表示済みの場合は再表示しない
        val shouldShowSecondTime = shouldShowFtsNotice(ftsSupported = false, hasSearchText = false, hasShownThisSession = true)
        assertEquals(false, shouldShowSecondTime)
    }

    @Test
    fun shouldShowFtsNotice_returnsFalseWhenFtsSupported() {
        val shouldShow = shouldShowFtsNotice(ftsSupported = true, hasSearchText = false, hasShownThisSession = false)
        assertEquals(false, shouldShow)
    }

    @Test
    fun placesCacheAndNotice_resetSafely() {
        clearPlacesHasSearchTextCacheForTesting()
        resetFts5NoticeForTesting()
    }
}
