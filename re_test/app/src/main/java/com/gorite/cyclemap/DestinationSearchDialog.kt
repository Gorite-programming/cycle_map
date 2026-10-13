package com.gorite.cyclemap

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.gorite.cyclemap.data.FavoriteSpot
import com.gorite.cyclemap.data.FavoritesManager
import com.gorite.cyclemap.data.Fts5SupportDetector
import com.gorite.cyclemap.data.SearchDbSelection
import com.gorite.cyclemap.data.SearchDbSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

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
        containerColor = Color.White,
        titleContentColor = Color(0xFF202124),
        textContentColor = Color(0xFF3C4043),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        title = {
            Text(
                "目的地検索",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF202124),
            )
        },
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
                            containerColor = Color(0xFFFEF7E0),
                        ),
                        shape = RoundedCornerShape(10.dp),
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
                                tint = Color(0xFFE37400),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "旧形式の検索DBが使用されています。漢字または前方・部分一致で検索してください",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF202124),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { showFts5Notice = false },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_lucide_x),
                                    contentDescription = "閉じる",
                                    tint = Color(0xFF5F6368),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                // 検索テキストフィールド (Google スタイル: 薄グレー背景 #F1F3F4, ダークテキスト #202124)
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF1F3F4),
                        unfocusedContainerColor = Color(0xFFF1F3F4),
                        focusedTextColor = Color(0xFF202124),
                        unfocusedTextColor = Color(0xFF202124),
                        focusedPlaceholderColor = Color(0xFF5F6368),
                        unfocusedPlaceholderColor = Color(0xFF5F6368),
                        focusedIndicatorColor = Color(0xFF1A73E8),
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color(0xFF1A73E8),
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("施設名・地名を入力") },
                )
                Spacer(modifier = Modifier.height(8.dp))

                // カテゴリフィルタタブ (Google スタイル: 白背景, 青インジケータ)
                ScrollableTabRow(
                    selectedTabIndex = SearchCategory.entries.indexOf(selectedCategory),
                    edgePadding = 0.dp,
                    containerColor = Color.White,
                    contentColor = Color(0xFF1A73E8),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SearchCategory.entries.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Tab(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            text = {
                                Text(
                                    cat.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF1A73E8) else Color(0xFF5F6368),
                                )
                            },
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
                                    FavoriteSpotItemCard(
                                        spot = fav,
                                        onSpotSelected = { spot ->
                                            onResultSelected(
                                                SearchResult(
                                                    name = spot.name,
                                                    category = spot.category,
                                                    latitude = spot.latitude,
                                                    longitude = spot.longitude,
                                                    distanceMeters = null,
                                                ),
                                            )
                                        },
                                        onRemoveFavorite = {
                                            favoritesManager.removeFavorite(fav.id)
                                        },
                                    )
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
                                    tint = Color(0xFF9AA0A6)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "目的地を検索してください",
                                    color = Color(0xFF5F6368)
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        items(results) { result ->
                            SearchResultItemCard(
                                result = result,
                                isFavorite = favoritesManager.isFavorite(result.latitude, result.longitude),
                                onResultSelected = onResultSelected,
                                onToggleFavorite = {
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
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF1A73E8)),
            ) {
                Text("閉じる", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
    )
}
