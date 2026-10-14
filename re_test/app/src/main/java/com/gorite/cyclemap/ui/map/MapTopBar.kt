package com.gorite.cyclemap.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gorite.cyclemap.R
import com.gorite.cyclemap.net.Link

data class CategoryChipData(
    val id: String,
    val label: String,
    val iconRes: Int
)

val DEFAULT_CATEGORY_CHIPS = listOf(
    CategoryChipData("convenience", "コンビニ", R.drawable.ic_lucide_store),
    CategoryChipData("restaurant", "食事・カフェ", R.drawable.ic_lucide_utensils),
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
        // Google Maps 完全再現: カプセル型検索バー (≡ + ここで検索 + 雲/オフライン + 🎙️ + アバター)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .clickable { onSearchClick() },
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左: ハンバーガーメニュー
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 中央: 検索プレースホルダー
                Text(
                    text = queryText.ifEmpty { placeholderText },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (queryText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // 右群: 通信状態 + 音声マイク + ユーザーアバター
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NetworkStatusIndicator(networkLink)

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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Google アバター (紫丸型)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF7B1FA2))
                            .clickable { onAvatarClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userAvatarText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // GPS弱信号の小バナー (DEF-01: チップに被らないようレイアウト内に配置)
        AnimatedVisibility(
            visible = isGpsWeak,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFF3CD)
            ) {
                Text(
                    text = "GPS信号が微弱です（屋内またはトンネル）",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF856404),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        // 通知バナー群 (DEF-01: 検索バー直下に挿入)
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
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onCategoryClick(chip.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(chip.iconRes),
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = chip.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
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
