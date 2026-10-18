package com.gorite.cyclemap

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.data.DownloadStatusManager
import com.gorite.cyclemap.data.Fts5SupportDetector
import com.gorite.cyclemap.data.MapSourceType
import com.gorite.cyclemap.data.Prefecture
import com.gorite.cyclemap.data.PrefectureData
import com.gorite.cyclemap.data.SearchDbSelection
import com.gorite.cyclemap.data.SearchDbSelector
import com.gorite.cyclemap.data.TileDownloader
import com.gorite.cyclemap.data.TileProgress
import com.gorite.cyclemap.ui.cycling.NearbySpot
import com.gorite.cyclemap.data.FavoritesManager
import com.gorite.cyclemap.data.FavoriteSpot
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.XYTileSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

private fun hasSearchTextColumn(db: SQLiteDatabase): Boolean {
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

private var hasShownFts5NoticeThisSession = false

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
            val stationBase = extractStationQuery(trimmed)
            if (stationBase != null) {
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

/**
 * 周辺POIをオフライン検索DBから取得する (ブロッキング・IOスレッドで呼ぶこと)。
 * ズームに応じた表示数制限は呼び出し側で行う。
 */
internal fun searchNearbyPlaces(
    dbFile: File,
    centerLat: Double,
    centerLon: Double,
    radiusMeters: Double,
    limit: Int = 40,
): List<NearbySpot> {
    if (!dbFile.isFile || radiusMeters <= 0) return emptyList()
    // 緯度経度のおおまかなbbox (余裕を持たせ、厳密距離はKotlin側で判定)
    val latDelta = radiusMeters / 111_320.0
    val lonDelta = radiusMeters / (111_320.0 * kotlin.math.cos(Math.toRadians(centerLat)).coerceAtLeast(0.2))
    val found = ArrayList<NearbySpot>(limit * 2)
    // LIMIT前にSQL側で近似距離順に並べ、真の最近傍の切り捨てを防ぐ (厳密な半径・ソートはKotlin側)。
    val orderNearby = NEARBY_ORDER_BY
    SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery(
            """
            SELECT name, category, lat, lon
            FROM places
            WHERE lat BETWEEN ? AND ?
              AND lon BETWEEN ? AND ?
              AND (
                category LIKE 'shop:convenience%' OR category LIKE 'amenity:toilets%' OR
                category LIKE 'railway:station%' OR category LIKE 'public_transport%' OR
                category LIKE 'amenity:bus%' OR category LIKE 'highway:bus_stop%' OR
                category LIKE 'amenity:restaurant%' OR category LIKE 'amenity:cafe%' OR
                category LIKE 'amenity:fast_food%' OR category LIKE 'tourism:%' OR
                category LIKE 'amenity:parking%' OR category LIKE 'amenity:hospital%' OR
                category LIKE 'amenity:clinic%' OR category LIKE 'amenity:doctors%' OR
                category LIKE 'amenity:dentist%' OR category LIKE 'amenity:fuel%' OR
                category LIKE 'leisure:park%' OR category LIKE 'amenity:drinking_water%' OR
                category LIKE 'amenity:vending_machine%' OR category LIKE 'amenity:place_of_worship%' OR
                category LIKE 'railway:halt%' OR
                category LIKE 'shop:bicycle%' OR category LIKE 'shop:bakery%' OR
                category LIKE 'amenity:public_bath%'
              )
            $orderNearby
            LIMIT ?
            """.trimIndent(),
            arrayOf(
                (centerLat - latDelta).toString(),
                (centerLat + latDelta).toString(),
                (centerLon - lonDelta).toString(),
                (centerLon + lonDelta).toString(),
                *(nearbyOrderArgs(centerLat, centerLon).toTypedArray()),
                (limit * 3).toString(),
            ),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val lat = cursor.getDouble(2)
                val lon = cursor.getDouble(3)
                val dist = haversineMeters(centerLat, centerLon, lat, lon)
                if (dist <= radiusMeters) {
                    found += NearbySpot(cursor.getString(0), cursor.getString(1), lat, lon, dist)
                }
            }
        }
    }
    return found.sortedBy { it.distanceM }.take(limit)
}

/**
 * サイクリスト向けクイックスポット (カテゴリキー指定) をオフラインDBから高速・高精度に検索する。
 */
internal fun searchNearbyQuickSpots(
    dbFile: File,
    centerLat: Double,
    centerLon: Double,
    radiusMeters: Double,
    categoryKeys: List<String>,
    limit: Int = 40,
): List<NearbySpot> {
    if (!dbFile.isFile || radiusMeters <= 0 || categoryKeys.isEmpty()) return emptyList()
    val latDelta = radiusMeters / 111_320.0
    val lonDelta = radiusMeters / (111_320.0 * kotlin.math.cos(Math.toRadians(centerLat)).coerceAtLeast(0.2))
    val found = ArrayList<NearbySpot>(limit * 2)
    val orderNearby = NEARBY_ORDER_BY

    val categoryClauses = categoryKeys.joinToString(" OR ") { "category LIKE ?" }
    val sql = """
        SELECT name, category, lat, lon
        FROM places
        WHERE lat BETWEEN ? AND ?
          AND lon BETWEEN ? AND ?
          AND ($categoryClauses)
        $orderNearby
        LIMIT ?
    """.trimIndent()

    val args = ArrayList<String>()
    args.add((centerLat - latDelta).toString())
    args.add((centerLat + latDelta).toString())
    args.add((centerLon - lonDelta).toString())
    args.add((centerLon + lonDelta).toString())
    categoryKeys.forEach { args.add("$it%") }
    args.addAll(nearbyOrderArgs(centerLat, centerLon))
    args.add((limit * 3).toString())

    SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val lat = cursor.getDouble(2)
                val lon = cursor.getDouble(3)
                val dist = haversineMeters(centerLat, centerLon, lat, lon)
                if (dist <= radiusMeters) {
                    found += NearbySpot(cursor.getString(0), cursor.getString(1), lat, lon, dist)
                }
            }
        }
    }
    return found.sortedBy { it.distanceM }.take(limit)
}

// ---------------------------------------------------------------------------
// 目的地検索ダイアログ
// ---------------------------------------------------------------------------

@Composable
internal fun DestinationSearchDialog(
    context: Context,
    liveLocation: Pair<Double, Double>?,
    onDismiss: () -> Unit,
    onResultSelected: (SearchResult) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SearchCategory.ALL) }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var showFts5Notice by remember { mutableStateOf(false) }

    val favoritesManager = remember(context) { FavoritesManager.getInstance(context) }
    val favorites by favoritesManager.favorites.collectAsState()

    // 現在地を取得（位置情報許可済みの場合）
    val userLocation = remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // live位置を優先し、無ければ最終既知位置を使う。
    val effectiveLocation = liveLocation ?: userLocation.value
    // 県別search.db選択。位置確定前は従来の search.db、未対応県では番兵＋県名を保持する。
    // /tmp 配下のテスト用DB参照は端末に存在しないため廃止した。
    // 内部番兵名はユーザー向け文言に出さない (BUG-33)。
    val searchDbInfo = remember(context, effectiveLocation) {
        val dataDir = cycleMapDataDir(context)
        val loc = effectiveLocation
        if (loc == null) {
            val f = SearchDbSelector.findFirstAvailableDb(dataDir) ?: File(dataDir, "search.db")
            Pair(f, null)
        } else {
            when (val sel = SearchDbSelector.select(loc.first, loc.second)) {
                is SearchDbSelection.Available -> {
                    val f = SearchDbSelector.resolveDbFile(dataDir, sel.fileName) ?: File(dataDir, sel.fileName)
                    f.also {
                        Log.i("CycleMapGeo", "POI_DB prefecture=${sel.prefectureName} file=${it.name} exists=${it.isFile}")
                    }.let { Pair(it, null) }
                }
                is SearchDbSelection.Unavailable -> {
                    Log.w("CycleMapGeo", "POI_DB reason=unsupported prefecture=${sel.prefectureName ?: "(unknown)"}")
                    Pair(File(dataDir, "search_unsupported.db"), sel.prefectureName ?: "この地域")
                }
            }
        }
    }
    val searchDb = searchDbInfo.first
    val unsupportedPrefecture = searchDbInfo.second

    // FTS5およびsearch_textの対応状況をチェック (旧DBのみセッション1回案内)
    LaunchedEffect(searchDb) {
        if (searchDb.isFile) {
            withContext(Dispatchers.IO) {
                runCatching {
                    SQLiteDatabase.openDatabase(searchDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                        val ftsSupported = Fts5SupportDetector.isSupported(db)
                        val hasSearchText = hasSearchTextColumn(db)
                        val shouldShow = shouldShowFtsNotice(
                            ftsSupported = ftsSupported,
                            hasSearchText = hasSearchText,
                            hasShownThisSession = hasShownFts5NoticeThisSession,
                        )
                        if (shouldShow) {
                            hasShownFts5NoticeThisSession = true
                            withContext(Dispatchers.Main) {
                                showFts5Notice = true
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (context.hasLocationPermission()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val lm = context.getSystemService(android.location.LocationManager::class.java)
                    val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (hasFine || hasCoarse) {
                        val loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                            ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                        loc?.let { userLocation.value = it.latitude to it.longitude }
                    }
                }
            }
        }
    }

    LaunchedEffect(query, selectedCategory, effectiveLocation) {
        val trimmed = query.trim()
        // カテゴリ未選択 + クエリ短い場合はリストクリア
        if (trimmed.length < 2 && selectedCategory == SearchCategory.ALL) {
            results = emptyList()
            message = when {
                unsupportedPrefecture != null -> "${unsupportedPrefecture}の検索DBがありません"
                searchDb.isFile -> null
                else -> "検索DBが見つかりません"
            }
            return@LaunchedEffect
        }
        val (uLat, uLon) = effectiveLocation?.let { it.first to it.second } ?: (null to null)
        val found = withContext(Dispatchers.IO) {
            searchPlaces(
                dbFile = searchDb,
                query = trimmed,
                category = selectedCategory,
                userLat = uLat,
                userLon = uLon,
            )
        }
        results = found
        message = when {
            unsupportedPrefecture != null -> "${unsupportedPrefecture}の検索DBがありません"
            !searchDb.isFile -> "検索DBが見つかりません"
            found.isEmpty() -> "該当する目的地がありません"
            else -> null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            decorFitsSystemWindows = false,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        title = { Text("目的地検索") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 520.dp),
            ) {
                // FTS5非対応時の案内バナー（セッション中1回のみ）
                if (showFts5Notice) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_lucide_info),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "旧形式の検索DBが使用されています。漢字または前方・部分一致で検索してください",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { showFts5Notice = false },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_lucide_x),
                                    contentDescription = "閉じる",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                // 検索テキストフィールド
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("施設名・地名を入力") },
                )
                Spacer(modifier = Modifier.height(8.dp))

                // カテゴリフィルタタブ
                ScrollableTabRow(
                    selectedTabIndex = SearchCategory.entries.indexOf(selectedCategory),
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SearchCategory.entries.forEach { cat ->
                        Tab(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            text = { Text(cat.label, fontSize = 12.sp) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // 現在地・件数ヘッダー
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (effectiveLocation != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(R.drawable.ic_lucide_map_pin),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "現在地から近い順",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Text(
                            "現在地なし",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (results.isNotEmpty()) {
                        Text(
                            "${results.size}件",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                message?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (query.isEmpty() && results.isEmpty()) {
                    if (favorites.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "★ お気に入り (${favorites.size}件)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(favorites, key = { it.id }) { fav ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable {
                                                onResultSelected(
                                                    SearchResult(
                                                        name = fav.name,
                                                        category = fav.category,
                                                        latitude = fav.latitude,
                                                        longitude = fav.longitude,
                                                        distanceMeters = null,
                                                    ),
                                                )
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(fav.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                Text(
                                                    fav.address ?: formatCategoryLabel(fav.category),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            IconButton(
                                                onClick = { favoritesManager.removeFavorite(fav.id) },
                                                modifier = Modifier.size(36.dp),
                                            ) {
                                                Icon(
                                                    painterResource(R.drawable.ic_star_filled),
                                                    contentDescription = "お気に入り解除",
                                                    tint = androidx.compose.ui.graphics.Color(0xFFFFB300),
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painterResource(R.drawable.ic_lucide_search),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "目的地を検索してください",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        items(results) { result ->
                            val isFav = favoritesManager.isFavorite(result.latitude, result.longitude)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onResultSelected(result) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(result.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                formatCategoryLabel(result.category),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f),
                                            )
                                            result.distanceMeters?.let { dist ->
                                                val distStr = if (dist >= 1000.0) {
                                                    String.format(Locale.US, "%.1f km", dist / 1000.0)
                                                } else {
                                                    String.format(Locale.US, "%.0f m", dist)
                                                }
                                                Text(
                                                    distStr,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            favoritesManager.toggleFavorite(
                                                FavoriteSpot(
                                                    id = "${result.name}_${result.latitude}_${result.longitude}",
                                                    name = result.name,
                                                    category = result.category,
                                                    latitude = result.latitude,
                                                    longitude = result.longitude,
                                                ),
                                            )
                                        },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            painterResource(if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline),
                                            contentDescription = if (isFav) "お気に入り解除" else "お気に入り追加",
                                            tint = if (isFav) androidx.compose.ui.graphics.Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        }
    },
    confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
    )
}

// ---------------------------------------------------------------------------
// 都道府県リストダイアログ
// ---------------------------------------------------------------------------

@Composable
internal fun PrefectureListDialog(
    context: Context,
    onDismiss: () -> Unit,
    onPrefectureSelected: (Prefecture) -> Unit,
) {
    var selectedRegionIndex by remember { androidx.compose.runtime.mutableIntStateOf(5) } // デフォルト中国地方
    var downloadStatus by remember { mutableStateOf<Map<String, Pair<Boolean, Boolean>>>(emptyMap()) }

    LaunchedEffect(context) {
        downloadStatus = withContext(Dispatchers.IO) {
            PrefectureData.ALL.associate { pref ->
                pref.id to (
                    DownloadStatusManager.isDownloaded(context, pref.id, MapSourceType.GSI) to
                        DownloadStatusManager.isDownloaded(context, pref.id, MapSourceType.OSM)
                    )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("都道府県を選択してDL") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(380.dp)) {
                // Region Tab
                ScrollableTabRow(
                    selectedTabIndex = selectedRegionIndex,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PrefectureData.REGIONS.forEachIndexed { index, regionName ->
                        Tab(
                            selected = selectedRegionIndex == index,
                            onClick = { selectedRegionIndex = index },
                            text = { Text(regionName, fontSize = 12.sp) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Prefecture List in selected region
                val currentRegion = PrefectureData.REGIONS[selectedRegionIndex]
                val prefectures = remember(currentRegion) {
                    PrefectureData.ALL.filter { it.region == currentRegion }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(prefectures) { pref ->
                        val status = downloadStatus[pref.id]
                        val isGsi = status?.first == true
                        val isOsm = status?.second == true
                        val tileCount = PrefectureData.calculateTileCount(pref.bounds)
                        val sizeMb = PrefectureData.estimateSizeMb(tileCount)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onPrefectureSelected(pref) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(pref.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("約 ${tileCount}枚 (%.1f MB)".format(Locale.US, sizeMb), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (isGsi) {
                                        Text(
                                            "地理院済",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    if (isOsm) {
                                        Text(
                                            "OSM済",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
    )
}

// ---------------------------------------------------------------------------
// タイルダウンロード起動
// ---------------------------------------------------------------------------

internal fun startPrefectureDownload(
    context: Context,
    pref: Prefecture,
    sourceType: MapSourceType,
    source: XYTileSource,
    onProgress: (TileProgress) -> Unit,
    onFinished: () -> Unit,
) {
    Thread {
        try {
            TileDownloader.download(context, pref, sourceType, source, onProgress)
        } finally {
            onFinished()
        }
    }.start()
}
