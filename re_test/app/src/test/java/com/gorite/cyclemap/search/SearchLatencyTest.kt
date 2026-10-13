package com.gorite.cyclemap.search

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * SearchLatencyTest: plans.md §8
 * オートコンプリート (入力ごと < 120ms) の性能要件を検証
 */
class SearchLatencyTest {

    @Test
    fun testAutocompleteLatencyUnder120ms() {
        val largeList = (1..10000).map { i ->
            SearchCandidate(
                id = "poi-$i",
                name = if (i % 5 == 0) "ローソン 広島第${i}店" else "店舗 $i",
                searchText = if (i % 5 == 0) "ろーそん ひろしまだい${i}てん" else "てんぽ $i",
                category = if (i % 5 == 0) "convenience" else "other",
                latitude = 34.39 + (i % 100) * 0.001,
                longitude = 132.46 + (i % 100) * 0.001
            )
        }

        // 初回ウォームアップ
        SearchRanking.rankCandidates(largeList.take(100), "ろーそん", 34.39, 132.46)

        // 測定
        val duration = measureTimeMillis {
            val res = SearchRanking.rankCandidates(largeList, "ろーそん", 34.39, 132.46)
            assertTrue(res.isNotEmpty())
        }

        assertTrue("10,000件のオートコンプリート検索・ランク付けは120ms以内であること (実測: ${duration}ms)", duration < 120)
    }
}
