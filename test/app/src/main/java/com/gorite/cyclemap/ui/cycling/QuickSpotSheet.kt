package com.gorite.cyclemap.ui.cycling

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.R
import com.gorite.cyclemap.data.FavoriteSpot
import com.gorite.cyclemap.data.FavoritesManager
import com.gorite.cyclemap.formatCategoryLabel

/**
 * サイクリスト向けクイック補給・緊急スポット種別。
 * ワンタップで現在地または地図中心付近の最寄りを検索可能。
 */
enum class QuickSpotType(
    val label: String,
    @DrawableRes val iconRes: Int,
    val categoryKeys: List<String>,
    val nameFilter: String? = null,
) {
    CONVENIENCE(
        label = "コンビニ",
        iconRes = R.drawable.ic_lucide_store,
        categoryKeys = listOf("shop:convenience"),
    ),
    ROAD_STATION(
        label = "道の駅",
        iconRes = R.drawable.ic_lucide_map_pin,
        categoryKeys = listOf(
            "tourism:information",
            "amenity:parking_space",
            "amenity:parking",
            "amenity:rest_area",
            "tourism:road_station",
            "named",
        ),
        nameFilter = "道の駅",
    ),
    RESTROOM(
        label = "トイレ",
        iconRes = R.drawable.ic_lucide_toilet,
        categoryKeys = listOf("amenity:toilets"),
    ),
    WATER(
        label = "給水・自販機",
        iconRes = R.drawable.ic_lucide_droplets,
        categoryKeys = listOf("amenity:drinking_water", "amenity:vending_machine"),
    ),
    BIKE_SHOP(
        label = "自転車店",
        iconRes = R.drawable.ic_lucide_wrench,
        categoryKeys = listOf("shop:bicycle"),
    ),
    STATION(
        label = "鉄道駅",
        iconRes = R.drawable.ic_lucide_train_front,
        categoryKeys = listOf("railway:station", "railway:halt"),
    ),
    ;

    fun matches(category: String, name: String = ""): Boolean {
        val catMatches = categoryKeys.any { category.startsWith(it) }
        if (!catMatches) return false
        return if (nameFilter != null) name.contains(nameFilter) else true
    }

    companion object {
        fun forCategory(category: String, name: String = ""): QuickSpotType? =
            entries.firstOrNull { it.matches(category, name) }
    }
}

/**
 * MapScreen上部等に配置するクイックスポットフィルタの水平ピルボタン列。
 * サイクリストが走行中や停止時にグローブを装着していてもワンタップで押しやすい形状。
 */
@Composable
fun QuickSpotFilterRow(
    selectedType: QuickSpotType?,
    onSelectType: (QuickSpotType) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding,
    ) {
        items(QuickSpotType.entries, key = { it.name }) { type ->
            val isSelected = selectedType == type
            QuickSpotPill(
                type = type,
                isSelected = isSelected,
                onClick = { onSelectType(type) },
            )
        }
    }
}

@Composable
fun QuickSpotPill(
    type: QuickSpotType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isSelected) CyclingPink else CyclingNavy.copy(alpha = 0.94f)
    val contentColor = Color.White
    val borderStroke = if (isSelected) {
        BorderStroke(1.5.dp, CyclingPinkLight)
    } else {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = borderStroke,
        shadowElevation = if (isSelected) 6.dp else 4.dp,
        modifier = modifier.height(38.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(type.iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = type.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
            )
        }
    }
}

/**
 * 選択された QuickSpotType の周辺スポット一覧を表示するボトムシート。
 * 距離、スポット名、カテゴリバッジ、および「ここへ行く (Route)」ボタンを備える。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSpotBottomSheet(
    selectedType: QuickSpotType,
    onSelectType: (QuickSpotType) -> Unit,
    spots: List<NearbySpot>,
    isLoading: Boolean,
    errorMessage: String? = null,
    onSpotClick: (NearbySpot) -> Unit,
    onRouteClick: (NearbySpot) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val favoritesManager = remember(context) { FavoritesManager.getInstance(context) }
    val favorites by favoritesManager.favorites.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ヘッダー行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = CyclingPink.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(selectedType.iconRes),
                                contentDescription = null,
                                tint = CyclingPink,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "周辺の${selectedType.label}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            if (spots.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = CyclingPink.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = "${spots.size}件",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = CyclingPink,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = "最寄りから順に表示・ワンタップでルート案内",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_x),
                        contentDescription = "閉じる",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // カテゴリ切り替えピル行 (シート内でも素早く切替可能)
            QuickSpotFilterRow(
                selectedType = selectedType,
                onSelectType = onSelectType,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 0.dp),
            )

            HorizontalDivider()

            // スポット一覧エリア
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = CyclingPink,
                            )
                            Text(
                                text = "周辺の${selectedType.label}を検索中…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                errorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                spots.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(selectedType.iconRes),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = "周辺に${selectedType.label}は見つかりませんでした\n別のカテゴリを選択するか、地図を移動してください",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            spots,
                            key = { "${it.name}:${it.latitude}:${it.longitude}" },
                        ) { spot ->
                            val isFav = favorites.any {
                                kotlin.math.abs(it.latitude - spot.latitude) < 0.0001 &&
                                    kotlin.math.abs(it.longitude - spot.longitude) < 0.0001
                            }
                            QuickSpotCard(
                                spot = spot,
                                isFavorite = isFav,
                                onToggleFavorite = {
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
                                onSpotClick = { onSpotClick(spot) },
                                onRouteClick = { onRouteClick(spot) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSpotCard(
    spot: NearbySpot,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSpotClick: () -> Unit,
    onRouteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSpotClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // スポット情報 (名前・カテゴリ・距離)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = spot.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = formatKm(spot.distanceM),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = CyclingPink,
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatCategoryLabel(spot.category),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // アクションボタン列 (お気に入り + 「ここへ行く」ボタン)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline),
                        contentDescription = if (isFavorite) "お気に入り解除" else "お気に入り追加",
                        tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Button(
                    onClick = onRouteClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyclingPink,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_navigation),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ここへ行く",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
