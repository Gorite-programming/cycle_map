package com.gorite.cyclemap.ui.components

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.gorite.cyclemap.R
import com.gorite.cyclemap.utils.*
import com.gorite.cyclemap.search.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.data.FavoriteSpot
import com.gorite.cyclemap.data.FavoritesManager
import com.gorite.cyclemap.data.Fts5SupportDetector
import com.gorite.cyclemap.data.SearchDbSelection
import com.gorite.cyclemap.data.SearchDbSelector
import com.gorite.cyclemap.search.GenreDictionary
import com.gorite.cyclemap.search.SearchCandidate
import com.gorite.cyclemap.search.SearchRanking
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
        val genre = GenreDictionary.matchGenre(trimmed)
        val effectiveCategory = if (selectedCategory == SearchCategory.ALL && genre != null) {
            when (genre.id) {
                "convenience" -> SearchCategory.CONVENIENCE
                "station" -> SearchCategory.STATION
                "toilet" -> SearchCategory.TOILET
                "cafe_restaurant" -> SearchCategory.FOOD
                else -> selectedCategory
            }
        } else {
            selectedCategory
        }
        val found = withContext(Dispatchers.IO) {
            searchPlaces(
                dbFile = searchDb,
                query = trimmed,
                category = effectiveCategory,
                userLat = uLat,
                userLon = uLon,
            )
        }
        val ranked = if (trimmed.isNotEmpty() && found.isNotEmpty()) {
            val candidates = found.mapIndexed { idx, it ->
                SearchCandidate(
                    id = idx.toString(),
                    name = it.name,
                    searchText = "${it.name} ${it.category}",
                    category = it.category,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    distanceMeters = it.distanceMeters ?: 0.0,
                )
            }
            val rankedCandidates = SearchRanking.rankCandidates(candidates, trimmed, uLat, uLon)
            if (rankedCandidates.isNotEmpty()) {
                rankedCandidates.mapNotNull { cand ->
                    cand.id.toIntOrNull()?.let { found.getOrNull(it) }
                }
            } else {
                found
            }
        } else {
            found
        }
        results = ranked
        message = when {
            unsupportedPrefecture != null -> "${unsupportedPrefecture}の検索DBがありません"
            !searchDb.isFile -> "検索DBが見つかりません"
            ranked.isEmpty() -> "該当する目的地がありません"
            else -> null
        }
    }

    val focusRequester = remember { FocusRequester() }
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        contentVisible = true
    }

    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .navigationBarsPadding()
            .imePadding(),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // MapTopBar.kt (地図表示時の検索バー) と位置・サイズ・パディング・アイコン・フォント完全同一
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFFF1F3F4), // タップ時にグレーに変更
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左: 戻る矢印 (Google Maps 標準の太い ArrowBack、濃い黒 #1F1F1F でくっきり表示)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_google_arrow_back),
                            contentDescription = "戻る",
                            tint = Color(0xFF1F1F1F),
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // 中央: 「ここで検索」入力欄 (MapTopBar の Text(fontSize = 18.sp) と位置・サイズ完全一致)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 18.sp,
                                color = Color(0xFF1F1F1F),
                            ),
                            cursorBrush = SolidColor(Color(0xFF1A73E8)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                if (query.isEmpty()) {
                                    Text(
                                        text = "ここで検索",
                                        fontSize = 18.sp,
                                        color = Color(0xFF5F6368),
                                    )
                                }
                                innerTextField()
                            },
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 右: 音声マイク / クリアボタン (MapTopBar のマイク size(36.dp), icon 22.dp と完全同一、濃い黒 #1F1F1F)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (query.isNotEmpty()) {
                                    query = ""
                                } else {
                                    /* 音声検索 */
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (query.isNotEmpty()) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lucide_x),
                                contentDescription = "クリア",
                                tint = Color(0xFF1F1F1F),
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_google_mic),
                                contentDescription = "音声検索",
                                tint = Color(0xFF1F1F1F),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 下から出るアニメーションはこの枠の下のコンテンツにのみ付与
            AnimatedVisibility(
                visible = contentVisible,
                enter = slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // カテゴリフィルタタブ (Google スタイル: 白背景, 青インジケータ)
                    ScrollableTabRow(
                        selectedTabIndex = SearchCategory.entries.indexOf(selectedCategory),
                        edgePadding = 12.dp,
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
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color(0xFF1A73E8) else Color(0xFF5F6368),
                                        )
                                    },
                                )
                            }
                        }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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

                    // 現在地・件数ヘッダー
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
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
                            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                Text(
                                    "★ お気に入り (${favorites.size}件)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )

                                LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                                        tint = Color(0xFF9AA0A6),
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "目的地を検索してください",
                                        color = Color(0xFF5F6368),
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
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
            }
        }
    }
}
}
