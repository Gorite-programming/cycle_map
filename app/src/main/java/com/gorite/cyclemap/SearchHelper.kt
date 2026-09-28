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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import com.gorite.cyclemap.data.MapSourceType
import com.gorite.cyclemap.data.Prefecture
import com.gorite.cyclemap.data.PrefectureData
import com.gorite.cyclemap.data.SearchDbSelection
import com.gorite.cyclemap.data.SearchDbSelector
import com.gorite.cyclemap.data.TileDownloader
import com.gorite.cyclemap.data.TileProgress
import com.gorite.cyclemap.ui.cycling.NearbySpot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.XYTileSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    STATION("駅・バス停", listOf("railway:station", "public_transport:station", "amenity:bus_station", "highway:bus_stop", "public_transport:stop_position")),
    TOILET("トイレ", listOf("amenity:toilets")),
    FOOD("飲食", listOf("amenity:restaurant", "amenity:cafe", "amenity:fast_food")),
    TOURISM("観光", listOf("tourism:")),
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
            // FTS5検索（デバイスのSQLiteがFTS5未対応の場合はLIKEにフォールバック）
            var ftsSucceeded = false
            try {
                val ftsQuery = trimmed
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { "${it.replace("\"", "\"\"")}*" }

                val catWhereForFts = categoryWhere // p.category を参照するWHERE句

                db.rawQuery(
                    """
                    SELECT p.name, p.category, p.lat, p.lon
                    FROM places_fts f
                    JOIN places p ON p.id = f.rowid
                    WHERE places_fts MATCH ? $catWhereForFts
                    LIMIT ?
                    """.trimIndent(),
                    (listOf(ftsQuery) + categoryArgs + listOf(limit.toString())).toTypedArray(),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val r = SearchResult(cursor.getString(0), cursor.getString(1), cursor.getDouble(2), cursor.getDouble(3))
                        results["${r.name}:${r.latitude}:${r.longitude}"] = r
                    }
                }
                ftsSucceeded = true
            } catch (_: SQLiteException) {
                // FTS5未対応デバイス → LIKE検索のみで継続
                Log.w("CycleMap", "FTS5 not available, falling back to LIKE search")
            }

            // FTS5未対応 or 件数不足 → LIKE補完
            if (!ftsSucceeded || results.size < limit) {
                val remaining = if (ftsSucceeded) limit - results.size else limit
                db.rawQuery(
                    """
                    SELECT name, category, lat, lon
                    FROM places
                    WHERE name LIKE ? $categoryWherePlain
                    LIMIT ?
                    """.trimIndent(),
                    (listOf("%$trimmed%") + categoryArgs + listOf(remaining.toString())).toTypedArray(),
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
            // 位置不明時は従来通り。名称検索 (上2分岐) には適用しない
            // (遠方目的地の検索を壊さないため)。
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

    // 距離計算と近い順ソート
    val withDistance = results.values.map { r ->
        if (userLat != null && userLon != null) {
            val dist = haversineMeters(userLat, userLon, r.latitude, r.longitude)
            r.copy(distanceMeters = dist)
        } else {
            r
        }
    }
    return if (userLat != null && userLon != null) {
        withDistance.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
    } else {
        withDistance
    }
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
                category LIKE 'amenity:restaurant%' OR category LIKE 'amenity:cafe%' OR
                category LIKE 'amenity:fast_food%' OR category LIKE 'railway:station%' OR
                category LIKE 'tourism:%' OR category LIKE 'amenity:parking%' OR
                category LIKE 'amenity:hospital%' OR category LIKE 'amenity:clinic%' OR
                category LIKE 'amenity:doctors%' OR
                category LIKE 'amenity:fuel%' OR category LIKE 'leisure:park%' OR
                category LIKE 'amenity:drinking_water%'
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
        title = { Text("目的地検索") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(480.dp)) {
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
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(results) { result ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onResultSelected(result) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(result.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        result.category,
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
