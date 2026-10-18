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

    @Test
    fun extractStationQuery_handlesVariousInputs() {
        assertEquals("広島", extractStationQuery("広島駅"))
        assertEquals("新白島", extractStationQuery("新白島駅"))
        assertEquals("ひろしま", extractStationQuery("ひろしまえき"))
        assertEquals("hiroshima", extractStationQuery("hiroshimaeki"))
        assertEquals(null, extractStationQuery("駅"))
        assertEquals(null, extractStationQuery("えき"))
        assertEquals(null, extractStationQuery("eki"))
        assertEquals(null, extractStationQuery("セブンイレブン"))
    }

    @Test
    fun categoryPriority_ranksCategoriesCorrectly() {
        assertEquals(1, categoryPriority("railway:station"))
        assertEquals(1, categoryPriority("public_transport:station"))
        assertEquals(2, categoryPriority("place:city"))
        assertEquals(3, categoryPriority("shop:convenience"))
        assertEquals(3, categoryPriority("tourism:attraction"))
        assertEquals(4, categoryPriority("amenity:toilets"))
        assertEquals(5, categoryPriority("highway:bus_stop"))
        assertEquals(5, categoryPriority("amenity:bus"))
    }

    @Test
    fun clusterBusStops_clustersNearbySameNameStops() {
        val bus1 = SearchResult("広島駅", "highway:bus_stop", 34.3970, 132.4750, 100.0)
        val bus2 = SearchResult("広島駅", "highway:bus_stop", 34.3972, 132.4752, 120.0) // ~30m distance
        val bus3 = SearchResult("八丁堀", "highway:bus_stop", 34.3920, 132.4630, 1200.0)
        val station = SearchResult("広島", "railway:station", 34.3975, 132.4755, 90.0)

        val input = listOf(station, bus1, bus2, bus3)
        val clustered = clusterBusStops(input, clusterRadiusM = 300.0)

        // bus1 and bus2 should be clustered into 1 entry
        assertEquals(3, clustered.size)
        assertEquals(1, clustered.count { it.name == "広島駅" && it.category == "highway:bus_stop" })
        assertEquals(1, clustered.count { it.name == "八丁堀" })
        assertEquals(1, clustered.count { it.name == "広島" })
    }

    @Test
    fun rankSearchResults_prioritizesStationOverBusStop() {
        val bus = SearchResult("広島駅", "highway:bus_stop", 34.3970, 132.4750, 50.0)
        val station = SearchResult("広島", "railway:station", 34.3975, 132.4755, 100.0)
        val cafe = SearchResult("広島珈琲", "amenity:cafe", 34.3960, 132.4740, 80.0)

        val input = listOf(bus, cafe, station)
        val ranked = rankSearchResults(input, "広島駅", userLat = 34.390, userLon = 132.470)

        // When searching "広島駅", station "広島" matches stationBase and is railway:station, so it should be rank 1
        assertEquals("広島", ranked[0].name)
        assertEquals("railway:station", ranked[0].category)
    }

    @Test
    fun formatCategoryLabel_convertsOsmTagsToJapanese() {
        assertEquals("鉄道駅", formatCategoryLabel("railway:station"))
        assertEquals("バス停", formatCategoryLabel("highway:bus_stop"))
        assertEquals("コンビニ", formatCategoryLabel("shop:convenience"))
        assertEquals("自転車店", formatCategoryLabel("shop:bicycle"))
        assertEquals("パン屋", formatCategoryLabel("shop:bakery"))
        assertEquals("温泉・銭湯", formatCategoryLabel("amenity:public_bath"))
        assertEquals("トイレ", formatCategoryLabel("amenity:toilets"))
        assertEquals("カフェ", formatCategoryLabel("amenity:cafe"))
    }
}
