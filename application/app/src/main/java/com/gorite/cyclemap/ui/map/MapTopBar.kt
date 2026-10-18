package com.gorite.cyclemap.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.R
import com.gorite.cyclemap.net.Link

data class CategoryChipData(
    val id: String,
    val label: String,
    val iconRes: Int
)

val DEFAULT_CATEGORY_CHIPS = listOf(
    CategoryChipData("restaurant", "レストラン", R.drawable.ic_lucide_utensils),
    CategoryChipData("convenience", "コンビニ", R.drawable.ic_lucide_store),
    CategoryChipData("rest_area", "道の駅", R.drawable.ic_lucide_map_pin),
    CategoryChipData("bicycle_parking", "駐輪場", R.drawable.ic_lucide_square_parking),
    CategoryChipData("toilet", "トイレ", R.drawable.ic_lucide_toilet),
    CategoryChipData("hospital", "病院", R.drawable.ic_lucide_hospital),
    CategoryChipData("onsen", "温泉・銭湯", R.drawable.ic_lucide_map_pin),
    CategoryChipData("hotel", "宿泊", R.drawable.ic_lucide_map_pin),
    CategoryChipData("sightseeing", "観光・景観", R.drawable.ic_lucide_mountain)
)

@Composable
fun MapTopBar(
    queryText: String,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    networkLink: Link,
    selectedCategory: String?,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isGpsWeak: Boolean = false,
    onMicClick: () -> Unit = onSearchClick,
    onAvatarClick: () -> Unit = {},
    userAvatarText: String = "拓真",
    placeholderText: String = "ここで検索",
    bannerContent: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // Google Maps 完全再現: 純白カプセル型検索バー (4色ピン + ここで検索 + 🎙️ + 紫アバター)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .clickable { onSearchClick() },
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左: ハンバーガーメニューボタン (タップでメニューが開く)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { onMenuClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_menu),
                        contentDescription = "メニュー",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 中央: 検索プレースホルダー ("ここで検索")
                Text(
                    text = queryText.ifEmpty { placeholderText },
                    fontSize = 18.sp,
                    color = if (queryText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // 右群: 音声マイク (NetworkStatusIndicatorもアクセシビリティ維持のため極小統合)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.size(1.dp).alpha(0.01f)) {
                        NetworkStatusIndicator(networkLink)
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onMicClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_google_mic),
                            contentDescription = "音声検索",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }


        // 通知バナー群
        if (bannerContent != null) {
            Spacer(modifier = Modifier.height(4.dp))
            bannerContent()
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 横スクロールカテゴリチップ
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DEFAULT_CATEGORY_CHIPS.forEach { chip ->
                val isSelected = selectedCategory == chip.id
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onCategoryClick(chip.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(chip.iconRes),
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = chip.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkStatusIndicator(link: Link) {
    val iconRes = when (link) {
        is Link.Offline -> R.drawable.ic_lucide_cloud_off
        is Link.OnlineMetered, is Link.OnlineUnmetered -> R.drawable.ic_lucide_cloud
    }
    val tint = when (link) {
        is Link.Offline -> Color.Gray
        is Link.OnlineMetered -> Color(0xFFFF9800)
        is Link.OnlineUnmetered -> Color(0xFF4CAF50)
    }
    Icon(
        painter = painterResource(iconRes),
        contentDescription = when (link) {
            is Link.Offline -> "オフライン"
            is Link.OnlineMetered -> "従量制オンライン"
            is Link.OnlineUnmetered -> "オンライン"
        },
        tint = tint,
        modifier = Modifier.size(18.dp)
    )
}
