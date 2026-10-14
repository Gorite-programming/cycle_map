package com.gorite.cyclemap.ui.cycling

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.R
import com.gorite.cyclemap.data.FavoriteSpot
import com.gorite.cyclemap.data.FavoritesManager
import com.gorite.cyclemap.search.formatCategoryLabel

// ---------------------------------------------------------------------------
// スポットパネル (サイクリスト向けPOIクイック検索・オフラインDB)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotPanelSheet(
    selectedCategory: SpotQuickCategory?,
    onCategorySelect: (SpotQuickCategory) -> Unit,
    isLoading: Boolean,
    spots: List<NearbySpot>,
    message: String?,
    onSpotClick: (NearbySpot) -> Unit,
    onDismiss: () -> Unit,
    onQuickSpotTypeSelect: ((QuickSpotType) -> Unit)? = null,
    onRouteToSpot: ((NearbySpot) -> Unit)? = null,
    initialShowFavoritesOnly: Boolean = false,
) {
    val context = LocalContext.current
    val favoritesManager = remember(context) { FavoritesManager.getInstance(context) }
    val favorites by favoritesManager.favorites.collectAsState()
    var showFavoritesOnly by remember(initialShowFavoritesOnly) { mutableStateOf(initialShowFavoritesOnly) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (showFavoritesOnly) "お気に入りスポット" else "周辺スポット",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = { showFavoritesOnly = !showFavoritesOnly }) {
                    Icon(
                        painter = painterResource(if (showFavoritesOnly) R.drawable.ic_lucide_store else R.drawable.ic_star_filled),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (showFavoritesOnly) MaterialTheme.colorScheme.primary else Color(0xFFFFB300),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showFavoritesOnly) "周辺検索へ" else "★ お気に入り (${favorites.size})")
                }
            }
            Text(
                if (showFavoritesOnly) "登録したお気に入りの場所一覧 (タップで目的地に設定)"
                else "トイレ・コンビニ・休憩場所をオフラインDBから検索",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!showFavoritesOnly) {
                if (onQuickSpotTypeSelect != null) {
                    Text(
                        "クイック補給・緊急スポット (ワンタップ検索)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    QuickSpotFilterRow(
                        selectedType = null,
                        onSelectType = { type ->
                            onQuickSpotTypeSelect(type)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "一般カテゴリ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SpotQuickCategory.entries.take(3).forEach { cat ->
                        SpotChip(cat, selectedCategory == cat, { onCategorySelect(cat) }, Modifier.weight(1f))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SpotQuickCategory.entries.drop(3).forEach { cat ->
                        SpotChip(cat, selectedCategory == cat, { onCategorySelect(cat) }, Modifier.weight(1f))
                    }
                }
            }
            HorizontalDivider()
            if (showFavoritesOnly) {
                if (favorites.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "お気に入りはまだ登録されていません。\nスポット一覧の★アイコンから登録できます。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        items(favorites, key = { it.id }) { fav ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onSpotClick(
                                            NearbySpot(
                                                name = fav.name,
                                                category = fav.category,
                                                latitude = fav.latitude,
                                                longitude = fav.longitude,
                                                distanceM = 0.0,
                                            ),
                                        )
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painterResource(
                                        PoiCategory.forCategory(fav.category)?.iconRes
                                            ?: R.drawable.ic_lucide_map_pin,
                                    ),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(fav.name, fontWeight = FontWeight.Medium)
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
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                when {
                    isLoading -> Text("検索中…", style = MaterialTheme.typography.bodyMedium)
                    message != null -> Text(message, style = MaterialTheme.typography.bodyMedium)
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(spots, key = { "${it.name}:${it.latitude}:${it.longitude}" }) { spot ->
                            val isFav = favoritesManager.isFavorite(spot.latitude, spot.longitude)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSpotClick(spot) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painterResource(
                                        PoiCategory.forCategory(spot.category)?.iconRes
                                            ?: R.drawable.ic_lucide_map_pin,
                                    ),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(spot.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        formatCategoryLabel(spot.category),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    formatKm(spot.distanceM),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                if (onRouteToSpot != null) {
                                    IconButton(
                                        onClick = { onRouteToSpot(spot) },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.ic_lucide_navigation),
                                            contentDescription = "ここへ行く",
                                            tint = CyclingPink,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        favoritesManager.toggleFavorite(
                                            FavoriteSpot(
                                                id = "${spot.name}_${spot.latitude}_${spot.longitude}",
                                                name = spot.name,
                                                category = spot.category,
                                                latitude = spot.latitude,
                                                longitude = spot.longitude,
                                            ),
                                        )
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        painterResource(if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline),
                                        contentDescription = if (isFav) "お気に入り解除" else "お気に入り追加",
                                        tint = if (isFav) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
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

@Composable
private fun SpotChip(
    cat: SpotQuickCategory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painterResource(cat.poi.iconRes),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(cat.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
