package com.gorite.cyclemap.search

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.gorite.cyclemap.data.Fts5SupportDetector
import com.gorite.cyclemap.utils.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------
// 検索データモデル
// ---------------------------------------------------------------------------

internal data class SearchResult(
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double? = null,
)

/** カテゴリフィルタタブの定義 */
internal enum class SearchCategory(val label: String, val categoryPrefixes: List<String>) {
    ALL("すべて", emptyList()),
    CONVENIENCE("コンビニ", listOf("shop:convenience")),
    BICYCLE("自転車", listOf("shop:bicycle")),
    STATION("駅・バス停", listOf("railway:station", "public_transport:station", "amenity:bus_station", "highway:bus_stop", "public_transport:stop_position")),
    TOILET("トイレ", listOf("amenity:toilets")),
    FOOD("飲食", listOf("amenity:restaurant", "amenity:cafe", "amenity:fast_food")),
    TOURISM("観光", listOf("tourism:")),
    BATH("温泉", listOf("amenity:public_bath")),
}

/**
 * OSMカテゴリ文字列を直感的な日本語ラベルに変換する。
 */
fun formatCategoryLabel(category: String): String {
    return when {
        category.startsWith("railway:station") -> "鉄道駅"
        category.startsWith("railway:halt") -> "駅(無人駅)"
        category.startsWith("public_transport:station") -> "駅・ターミナル"
        category.startsWith("highway:bus_stop") -> "バス停"
        category.startsWith("amenity:bus") -> "バスターミナル"
        category.startsWith("public_transport:stop_position") -> "停留所"
        category.startsWith("shop:convenience") -> "コンビニ"
        category.startsWith("shop:supermarket") -> "スーパー"
        category.startsWith("shop:bicycle") -> "自転車店"
        category.startsWith("shop:bakery") -> "パン屋"
        category.startsWith("amenity:public_bath") -> "温泉・銭湯"
        category.startsWith("amenity:toilets") -> "トイレ"
        category.startsWith("amenity:drinking_water") -> "給水スポット"
        category.startsWith("amenity:vending_machine") -> "自販機"
        category.startsWith("tourism:road_station") -> "道の駅"
        category.startsWith("amenity:place_of_worship") -> "寺社・休憩所"
        category.startsWith("amenity:restaurant") -> "飲食店"
        category.startsWith("amenity:cafe") -> "カフェ"
        category.startsWith("amenity:fast_food") -> "ファストフード"
        category.startsWith("tourism:attraction") -> "観光名所"
        category.startsWith("tourism:viewpoint") -> "展望台"
        category.startsWith("tourism:museum") -> "博物館・美術館"
        category.startsWith("tourism:") -> "観光"
        category.startsWith("amenity:parking") -> "駐車場"
        category.startsWith("amenity:hospital") -> "総合病院"
        category.startsWith("amenity:clinic") || category.startsWith("amenity:doctors") -> "クリニック"
        category.startsWith("amenity:dentist") -> "歯科医院"
        category.startsWith("amenity:fuel") -> "ガソリンスタンド"
        category.startsWith("leisure:park") -> "公園"
        category.startsWith("amenity:drinking_water") -> "給水スポット"
        category.startsWith("amenity:post_office") -> "郵便局"
        category.startsWith("amenity:bank") || category.startsWith("amenity:atm") -> "銀行・ATM"
        category.startsWith("amenity:school") -> "学校"
        category.startsWith("amenity:university") || category.startsWith("amenity:college") -> "大学・高専"
        category.startsWith("amenity:library") -> "図書館"
        category.startsWith("amenity:police") -> "警察・交番"
        category.startsWith("amenity:fire_station") -> "消防署"
        category.startsWith("place:city") || category.startsWith("place:town") -> "市区町村"
        else -> category
    }
}

/**
 * 「〜駅」「〜えき」「〜eki」検索クエリからベース駅名（例: "広島駅" → "広島"）を抽出する。
 */
@VisibleForTesting
internal fun extractStationQuery(query: String): String? {
    val trimmed = query.trim()
    return when {
        trimmed.endsWith("駅") && trimmed.length > 1 -> trimmed.removeSuffix("駅").trim()
        trimmed.endsWith("えき") && trimmed.length > 2 -> trimmed.removeSuffix("えき").trim()
        trimmed.lowercase().endsWith("eki") && trimmed.length > 3 -> trimmed.dropLast(3).trim()
        else -> null
    }
}

/**
 * カテゴリの優先度ランク（数値が小さいほど高優先度）。
 */
@VisibleForTesting
internal fun categoryPriority(category: String): Int {
    return when {
        category.startsWith("railway:station") ||
            category.startsWith("public_transport:station") -> 1 // 鉄道駅・ターミナル
        category.startsWith("place:") -> 2 // 自治体・地名
        category.startsWith("tourism:") ||
            category.startsWith("shop:") ||
            category.startsWith("amenity:restaurant") ||
            category.startsWith("amenity:cafe") ||
            category.startsWith("amenity:fast_food") ||
            category.startsWith("amenity:hospital") ||
            category.startsWith("amenity:clinic") -> 3 // 施設・店舗
        category.startsWith("amenity:toilets") ||
            category.startsWith("amenity:drinking_water") ||
            category.startsWith("leisure:park") -> 4 // トイレ・給水・公園
        category.startsWith("highway:bus_stop") ||
            category.startsWith("amenity:bus") ||
            category.startsWith("public_transport:stop_position") -> 5 // バス停
        else -> 4
    }
}

/**
 * 同一名称で近接（デフォルト300m以内）するバス停を代表1件にクラスタリング（集約）する。
 */
@VisibleForTesting
internal fun clusterBusStops(
    items: List<SearchResult>,
    clusterRadiusM: Double = 300.0,
): List<SearchResult> {
    val isBusStop = { r: SearchResult ->
        r.category.startsWith("highway:bus_stop") ||
            r.category.startsWith("amenity:bus") ||
            r.category.startsWith("public_transport:stop_position")
    }

    val nonBusStops = mutableListOf<SearchResult>()
    val busStops = mutableListOf<SearchResult>()

    for (item in items) {
        if (isBusStop(item)) {
            busStops.add(item)
        } else {
            nonBusStops.add(item)
        }
    }

    val clusteredBusStops = mutableListOf<SearchResult>()
    for (bus in busStops) {
        val existing = clusteredBusStops.firstOrNull {
            it.name == bus.name && haversineMeters(it.latitude, it.longitude, bus.latitude, bus.longitude) <= clusterRadiusM
        }
        if (existing == null) {
            clusteredBusStops.add(bus)
        }
    }

    return nonBusStops + clusteredBusStops
}

/**
 * 検索クエリとの一致度、カテゴリ優先度、距離を総合してソートする。
 */
@VisibleForTesting
internal fun rankSearchResults(
    items: List<SearchResult>,
    query: String,
    userLat: Double?,
    userLon: Double?,
): List<SearchResult> {
    val trimmed = query.trim()
    val stationBase = extractStationQuery(trimmed)

    return items.sortedWith { a, b ->
        // 1. クエリ完全一致 または 駅名一致（「広島駅」検索で駅名「広島」の鉄道駅）を最優先
        val aExactMatch = a.name.equals(trimmed, ignoreCase = true) ||
            (stationBase != null && a.name.equals(stationBase, ignoreCase = true) && a.category.startsWith("railway:station"))
        val bExactMatch = b.name.equals(trimmed, ignoreCase = true) ||
            (stationBase != null && b.name.equals(stationBase, ignoreCase = true) && b.category.startsWith("railway:station"))
        if (aExactMatch != bExactMatch) {
            return@sortedWith if (aExactMatch) -1 else 1
        }

        // 2. カテゴリ優先度 (鉄道駅=1 > 観光・店舗=3 > バス停=5)
        val aCat = categoryPriority(a.category)
        val bCat = categoryPriority(b.category)
        if (aCat != bCat) {
            return@sortedWith aCat.compareTo(bCat)
        }

        // 3. 距離（近い順）
        val aDist = a.distanceMeters ?: Double.MAX_VALUE
        val bDist = b.distanceMeters ?: Double.MAX_VALUE
        aDist.compareTo(bDist)
    }
}

private val placesHasSearchTextCache = ConcurrentHashMap<String, Boolean>()

@VisibleForTesting
internal fun clearPlacesHasSearchTextCacheForTesting() {
    placesHasSearchTextCache.clear()
}

internal fun hasSearchTextColumn(db: SQLiteDatabase): Boolean {
    val path = db.path ?: ""
    if (path.isEmpty()) {
        return checkSearchTextColumnDirectly(db)
    }
    return placesHasSearchTextCache.computeIfAbsent(path) {
        checkSearchTextColumnDirectly(db)
    }
}

private fun checkSearchTextColumnDirectly(db: SQLiteDatabase): Boolean {
    return try {
        db.rawQuery("PRAGMA table_info(places)", null).use { cursor ->
            val nameCol = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameCol >= 0 && cursor.getString(nameCol) == "search_text") {
                    return true
                }
            }
        }
        false
    } catch (e: Exception) {
        Log.w("CycleMap", "Failed to check table_info for places: ${e.message}")
        false
    }
}

internal var hasShownFts5NoticeThisSession = false

@VisibleForTesting
internal fun resetFts5NoticeForTesting() {
    hasShownFts5NoticeThisSession = false
}

/**
 * FTS5/曖昧検索の注意バナーを表示すべきかを判定する。
 * - FTS5対応端末、または FTS5非対応でも検索DBに search_text 列（ひらがな・カタカナ・ローマ字対応）が存在する場合は false。
 * - FTS5非対応かつ search_text 列が無い旧形式DBの場合のみ、セッション中1回 true を返す。
 */
@VisibleForTesting
internal fun shouldShowFtsNotice(
    ftsSupported: Boolean,
    hasSearchText: Boolean,
    hasShownThisSession: Boolean,
): Boolean {
    if (ftsSupported) return false
    if (hasSearchText) return false
    return !hasShownThisSession
}

@VisibleForTesting
internal fun buildFtsQuery(trimmed: String): String {
    return trimmed
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { "\"${it.replace("\"", "\"\"")}\"*" }
}

@VisibleForTesting
internal fun buildLikeTextConditions(trimmed: String, targetColumn: String): Pair<String, List<String>> {
    val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
    val whereClause = if (words.isEmpty()) {
        "$targetColumn LIKE ?"
    } else {
        words.joinToString(" AND ") { "$targetColumn LIKE ?" }
    }
    val args = if (words.isEmpty()) {
        listOf("%$trimmed%")
    } else {
        words.map { "%$it%" }
    }
    return whereClause to args
}

// ---------------------------------------------------------------------------
// 場所・POI検索
// ---------------------------------------------------------------------------

/**
 * 場所を検索する。
 * @param dbFile 検索DB
 * @param query 検索クエリ（空文字の場合はカテゴリ一覧表示）
 * @param category カテゴリフィルタ（ALL以外はカテゴリ絞り込み）
 * @param userLat 現在地緯度（null時は距離計算しない）
 * @param userLon 現在地経度（null時は距離計算しない）
 * @param limit 最大件数
 */
internal fun searchPlaces(
    dbFile: File,
    query: String,
    category: SearchCategory = SearchCategory.ALL,
    userLat: Double? = null,
    userLon: Double? = null,
    limit: Int = 50,
): List<SearchResult> {
    if (!dbFile.isFile) return emptyList()

    // カテゴリWHERE句を組み立て
    val categoryWhere: String        // FTS JOIN クエリ用 (p.category)
    val categoryWherePlain: String   // plain places クエリ用 (category、エイリアスなし)
    val categoryArgs: List<String>
    if (category == SearchCategory.ALL || category.categoryPrefixes.isEmpty()) {
        categoryWhere = ""
        categoryWherePlain = ""
        categoryArgs = emptyList()
    } else {
        val clauses = category.categoryPrefixes.joinToString(" OR ") { "p.category LIKE ?" }
        val clausesPlain = category.categoryPrefixes.joinToString(" OR ") { "category LIKE ?" }
        categoryWhere = "AND ($clauses)"
        categoryWherePlain = "AND ($clausesPlain)"
        // LIKE 用引数: すべての prefix に % を付ける
        categoryArgs = category.categoryPrefixes.map { "$it%" }
    }

    val results = linkedMapOf<String, SearchResult>()

    SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            // 現在地がある場合は距離順 (NEARBY_ORDER_BY) でソートしてから LIMIT を適用。
            // ※ bbox による範囲絞り込みは遠方目的地を壊さないため適用しないが、
            //    距離順ソートは名称検索にも適用することで近傍のチェーン店（セブン等）を確実に拾う。
            val orderArgs = if (userLat != null && userLon != null) {
                nearbyOrderArgs(userLat, userLon)
            } else {
                emptyList()
            }
            val orderBy = if (orderArgs.isNotEmpty()) NEARBY_ORDER_BY else ""

            val ftsSupported = Fts5SupportDetector.isSupported(db)
            var ftsSucceeded = false
            if (ftsSupported) {
                try {
                    val ftsQuery = buildFtsQuery(trimmed)
                    val catWhereForFts = categoryWhere // p.category を参照するWHERE句

                    db.rawQuery(
                        """
                        SELECT p.name, p.category, p.lat, p.lon
                        FROM places_fts f
                        JOIN places p ON p.id = f.rowid
                        WHERE places_fts MATCH ? $catWhereForFts
                        $orderBy
                        LIMIT ?
                        """.trimIndent(),
                        (listOf(ftsQuery) + categoryArgs + orderArgs + listOf(limit.toString())).toTypedArray(),
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val r = SearchResult(cursor.getString(0), cursor.getString(1), cursor.getDouble(2), cursor.getDouble(3))
                            results["${r.name}:${r.latitude}:${r.longitude}"] = r
                        }
                    }
                    ftsSucceeded = true
                } catch (e: SQLiteException) {
                    // FTS5未対応デバイスまたはエラー → LIKE検索へフォールバック
                    Log.w("CycleMap", "FTS5 query failed, falling back to LIKE search: ${e.message}")
                }
            }

            // FTS5未対応 or FTS5クエリ失敗 or 件数不足 → LIKE補完
            if (!ftsSucceeded || results.size < limit) {
                val remaining = if (ftsSucceeded) limit - results.size else limit
                val hasSearchText = hasSearchTextColumn(db)
                val targetColumn = if (hasSearchText) "search_text" else "name"

                // FTS5非対応時は複数単語をANDで繋ぎ、各単語の中間一致 (%word%) で検索する。
                val (textWhereClause, textArgs) = buildLikeTextConditions(trimmed, targetColumn)

                db.rawQuery(
                    """
                    SELECT name, category, lat, lon
                    FROM places
                    WHERE ($textWhereClause) $categoryWherePlain
                    $orderBy
                    LIMIT ?
                    """.trimIndent(),
                    (textArgs + categoryArgs + orderArgs + listOf(remaining.toString())).toTypedArray(),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val r = SearchResult(cursor.getString(0), cursor.getString(1), cursor.getDouble(2), cursor.getDouble(3))
                        results["${r.name}:${r.latitude}:${r.longitude}"] = r
                    }
                }
            }

            // 「〜駅」検索時、OSM上で「広島」のように駅名のみで登録されている鉄道駅を救済
            // DEF-09: カテゴリが未指定（ALL）または鉄道駅（STATION）のときのみ救済を実行する
            val stationBase = extractStationQuery(trimmed)
            if (stationBase != null && (category == SearchCategory.ALL || category == SearchCategory.STATION)) {
                val stationWhereFts = "AND (p.category LIKE 'railway:station%' OR p.category LIKE 'public_transport:station%')"
                val stationWherePlain = "AND (category LIKE 'railway:station%' OR category LIKE 'public_transport:station%')"
                if (ftsSupported) {
                    try {
                        val stationFtsQuery = buildFtsQuery(stationBase)
                        db.rawQuery(
                            """
                            SELECT p.name, p.category, p.lat, p.lon
                            FROM places_fts f
                            JOIN places p ON p.id = f.rowid
                            WHERE places_fts MATCH ? $stationWhereFts
                            $orderBy
                            LIMIT 10
                            """.trimIndent(),
                            (listOf(stationFtsQuery) + orderArgs).toTypedArray(),
                        ).use { cursor ->
                            while (cursor.moveToNext()) {
                                val r = SearchResult(cursor.getString(0), cursor.getString(1), cursor.getDouble(2), cursor.getDouble(3))
                                results["${r.name}:${r.latitude}:${r.longitude}"] = r
                            }
                        }
                    } catch (e: SQLiteException) {
                        Log.w("CycleMap", "Station FTS query failed: ${e.message}")
                    }
                }
                val hasSearchText = hasSearchTextColumn(db)
                val targetColumn = if (hasSearchText) "search_text" else "name"
                val (stTextWhereClause, stTextArgs) = buildLikeTextConditions(stationBase, targetColumn)
                db.rawQuery(
                    """
                    SELECT name, category, lat, lon
                    FROM places
                    WHERE ($stTextWhereClause) $stationWherePlain
                    $orderBy
                    LIMIT 10
                    """.trimIndent(),
                    (stTextArgs + orderArgs).toTypedArray(),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val r = SearchResult(cursor.getString(0), cursor.getString(1), cursor.getDouble(2), cursor.getDouble(3))
                        results["${r.name}:${r.latitude}:${r.longitude}"] = r
                    }
                }
            }
        } else if (category != SearchCategory.ALL && category.categoryPrefixes.isNotEmpty()) {
            // クエリ未入力でカテゴリ選択中 → 近傍カテゴリ一覧を表示。
            // 遠方行を拾わないよう現在地bboxで事前絞りする (逆ジオコーディングと同型の BETWEEN 方式)。
            // 位置不明時は従来通り。名称検索 (上2分岐) には bbox を適用しない (遠方目的地の検索を壊さないため)。距離順ソートは名称検索にも適用する。
            val bboxArgs = if (userLat != null && userLon != null) {
                nearbyBboxArgs(userLat, userLon, NEARBY_CATEGORY_BBOX_HALF_M)
            } else {
                emptyList()
            }
            val bboxWhere = if (bboxArgs.isNotEmpty()) "AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?" else ""
            // LIMIT前にSQL側で近似距離順に並べ、真の最近傍の切り捨てを防ぐ (厳密ソートはKotlin側)。
            val orderArgs = if (userLat != null && userLon != null) {
                nearbyOrderArgs(userLat, userLon)
            } else {
                emptyList()
            }
            val orderBy = if (orderArgs.isNotEmpty()) NEARBY_ORDER_BY else ""
            db.rawQuery(
                """
                SELECT name, category, lat, lon
                FROM places
                WHERE 1=1 $categoryWherePlain $bboxWhere
                $orderBy
                LIMIT ?
                """.trimIndent(),
                (categoryArgs + bboxArgs + orderArgs + listOf(limit.toString())).toTypedArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val r = SearchResult(cursor.getString(0), cursor.getString(1), cursor.getDouble(2), cursor.getDouble(3))
                    results["${r.name}:${r.latitude}:${r.longitude}"] = r
                }
            }
        }
    }

    // 距離計算
    val withDistance = results.values.map { r ->
        if (userLat != null && userLon != null) {
            val dist = haversineMeters(userLat, userLon, r.latitude, r.longitude)
            r.copy(distanceMeters = dist)
        } else {
            r
        }
    }

    // バス停クラスタリング（同名・300m以内を代表1件に集約）
    val clustered = clusterBusStops(withDistance)

    // クエリ一致度・カテゴリ優先度・距離によるランキング
    val ranked = rankSearchResults(clustered, query, userLat, userLon)
    return ranked.take(limit)
}
