package com.gorite.cyclemap.search

data class GenreCategory(
    val id: String,
    val canonicalName: String,
    val synonyms: List<String>,
    val osmFilterCategories: List<String>
)

object GenreDictionary {

    val CATEGORIES = listOf(
        GenreCategory(
            id = "convenience",
            canonicalName = "コンビニ",
            synonyms = listOf("コンビニ", "こんびに", "convenience", "7-eleven", "セブン", "ローソン", "ファミマ"),
            osmFilterCategories = listOf("convenience")
        ),
        GenreCategory(
            id = "station",
            canonicalName = "駅",
            synonyms = listOf("駅", "えき", "station", "jr", "電停"),
            osmFilterCategories = listOf("station", "halt")
        ),
        GenreCategory(
            id = "rest_area",
            canonicalName = "道の駅",
            synonyms = listOf("道の駅", "みちのえき", "rest_area", "service_area", "sa", "pa"),
            osmFilterCategories = listOf("rest_area")
        ),
        GenreCategory(
            id = "toilet",
            canonicalName = "トイレ",
            synonyms = listOf("トイレ", "といれ", "お手洗い", "便所", "toilet", "wc"),
            osmFilterCategories = listOf("toilets")
        ),
        GenreCategory(
            id = "cafe_restaurant",
            canonicalName = "食事・カフェ",
            synonyms = listOf("食事", "カフェ", "かふぇ", "restaurant", "cafe", "喫茶", "うどん", "ラーメン"),
            osmFilterCategories = listOf("restaurant", "cafe", "fast_food")
        ),
        GenreCategory(
            id = "bicycle_parking",
            canonicalName = "駐輪場",
            synonyms = listOf("駐輪場", "ちゅうりんじょう", "自転車置場", "bicycle_parking"),
            osmFilterCategories = listOf("bicycle_parking")
        ),
        GenreCategory(
            id = "hospital",
            canonicalName = "病院・クリニック",
            synonyms = listOf("病院", "びょういん", "クリニック", "hospital", "clinic", "医院"),
            osmFilterCategories = listOf("hospital", "clinic", "doctors")
        )
    )

    /**
     * クエリ文字列（例: "近くのコンビニ", "広島駅", "トイレ"）からジャンルカテゴリを検出
     */
    fun matchGenre(query: String): GenreCategory? {
        val normalized = SearchNormalizer.normalize(query).replace("近くの", "").replace("ちかくの", "").trim()
        if (normalized.isEmpty()) return null

        for (cat in CATEGORIES) {
            for (syn in cat.synonyms) {
                val normSyn = SearchNormalizer.normalize(syn)
                if (normalized == normSyn || normalized.startsWith(normSyn) || normalized.endsWith(normSyn) || normalized.contains(normSyn)) {
                    return cat
                }
            }
        }
        return null
    }
}
