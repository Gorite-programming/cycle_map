package com.gorite.cyclemap.ui.cycling

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.R

// ---------------------------------------------------------------------------
// Google Maps 完全再現: 上部白カプセル検索バー (SCR-01, SCR-02, SCR-10, SCR-11)
// ---------------------------------------------------------------------------

@Composable
fun TopSearchBar(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    locationLabel: String? = null,
    onMicClick: () -> Unit = onSearchClick,
    onAvatarClick: () -> Unit = {},
    userAvatarText: String = "",
    placeholderText: String = "ここで検索",
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左端: ハンバーガーメニューアイコン ≡
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMenuClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_menu),
                    contentDescription = "メニュー",
                    tint = Color(0xFF5F6368),
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 中央: 「ここで検索」
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onSearchClick),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = placeholderText,
                    color = Color(0xFF5F6368),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 右端: 音声認識マイク 🎙️
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMicClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_google_mic),
                    contentDescription = "音声検索",
                    tint = Color(0xFF5F6368),
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // アバターボタン (アイコン表示)
            Surface(
                shape = CircleShape,
                color = Color(0xFF1A73E8),
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAvatarClick),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_person),
                        contentDescription = "ユーザーメニュー",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Google Maps 完全再現: 上部水平スクロール カテゴリチップ列 (SCR-01, SCR-02)
// ---------------------------------------------------------------------------

@Composable
fun CategoryChipRow(
    onConsultClick: () -> Unit,
    onHomeClick: () -> Unit,
    onSelectQuickSpot: (QuickSpotType) -> Unit,
    modifier: Modifier = Modifier,
    onSelectCategory: (String) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. "🍴 レストラン"
        item(key = "chip_restaurant") {
            GoogleCategoryChip(
                iconRes = R.drawable.ic_lucide_utensils,
                label = "レストラン",
                onClick = { onSelectCategory("restaurant") },
            )
        }

        // 4. "🏪 コンビニ"
        item(key = "chip_convenience") {
            GoogleCategoryChip(
                iconRes = R.drawable.ic_lucide_store,
                label = "コンビニ",
                onClick = { onSelectQuickSpot(QuickSpotType.CONVENIENCE) },
            )
        }

        // 5. "🚩 道の駅"
        item(key = "chip_roadside") {
            GoogleCategoryChip(
                iconRes = R.drawable.ic_lucide_flag,
                label = "道の駅",
                onClick = { onSelectQuickSpot(QuickSpotType.ROAD_STATION) },
            )
        }

        // 6. "🚻 トイレ"
        item(key = "chip_toilet") {
            GoogleCategoryChip(
                iconRes = R.drawable.ic_lucide_toilet,
                label = "トイレ",
                onClick = { onSelectQuickSpot(QuickSpotType.RESTROOM) },
            )
        }

        // 7. "🚴 自転車店"
        item(key = "chip_bikeshop") {
            GoogleCategoryChip(
                iconRes = R.drawable.ic_lucide_wrench,
                label = "自転車店",
                onClick = { onSelectQuickSpot(QuickSpotType.BIKE_SHOP) },
            )
        }

        // 8. "💧 給水"
        item(key = "chip_water") {
            GoogleCategoryChip(
                iconRes = R.drawable.ic_lucide_droplets,
                label = "給水スポット",
                onClick = { onSelectQuickSpot(QuickSpotType.WATER) },
            )
        }

        // 9. "🚉 駅"
        item(key = "chip_station") {
            GoogleCategoryChip(
                iconRes = R.drawable.ic_lucide_train_front,
                label = "駅",
                onClick = { onSelectQuickSpot(QuickSpotType.STATION) },
            )
        }
    }
}

@Composable
fun GoogleCategoryChip(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFDADCE0)),
        shadowElevation = 2.dp,
        modifier = modifier.height(36.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = Color(0xFF5F6368),
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF202124),
            )
        }
    }
}
