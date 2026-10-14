package com.gorite.cyclemap.ui.cycling

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.R

/**
 * フル住所（例: "広島県広島市西区井口五丁目"）から Google Maps 風の短い町名＋丁目（例: "井口五丁目"）を抽出する。
 */
internal fun extractShortLocalityName(raw: String): String {
    var s = raw.trim()
    if (s.isEmpty()) return "井口五丁目"
    // 都道府県を除去
    s = s.replace(Regex("^(東京都|北海道|(?:京都|大阪)府|.{2,3}県)"), "")
    // 政令市・市区町村・郡を除去
    s = s.replace(Regex("^[^0-9一二三四五六七八九十]+?[市区町村郡]"), "")
    // 政令市の区がもう一段ある場合を除去 (例: 広島市西区 -> 西区)
    s = s.replace(Regex("^[^0-9一二三四五六七八九十]{1,5}区"), "")
    // 丁目以降の番地・号などを除去 (丁目は保持する)
    s = s.replace(Regex("([一二三四五六七八九十0-9０-９]+丁目).*$"), "$1")
    // 丁目がなく番地等がある場合は番地以降を除去
    if (!s.contains("丁目")) {
        s = s.replace(Regex("[0-9０-９ー−番地号-].*$"), "")
    }
    return s.trim().ifEmpty { raw.takeLast(6) }
}

@Composable
fun LocationSummarySheet(
    locationName: String,
    modifier: Modifier = Modifier,
    temperatureText: String = "23°C",
    aqiText: String = "AQI",
    onClick: () -> Unit = {},
) {
    val shortName = extractShortLocalityName(locationName)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ドラッグハンドル
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFC4C7C5)),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 左側: Google Maps 準拠の地名 ("井口五丁目")
                Text(
                    text = shortName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(12.dp))

                // 右側: 天気・気温バッジ (オレンジ太陽丸 + 気温)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // 太陽アイコン (オレンジ)
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF9AB00)),
                        )
                        Text(
                            text = temperatureText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

enum class GoogleNavTab {
    EXPLORE,   // 「スポット」
    SAVED,     // 「保存済み」
    RECORD,    // 「記録」
}

@Composable
fun GoogleNavTabBar(
    selectedTab: GoogleNavTab,
    onTabSelected: (GoogleNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(62.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            GoogleNavTabItem(
                label = "スポット",
                iconRes = R.drawable.ic_google_spot_pin,
                isSelected = selectedTab == GoogleNavTab.EXPLORE,
                onClick = { onTabSelected(GoogleNavTab.EXPLORE) },
            )
            GoogleNavTabItem(
                label = "保存済み",
                iconRes = R.drawable.ic_google_bookmark,
                isSelected = selectedTab == GoogleNavTab.SAVED,
                onClick = { onTabSelected(GoogleNavTab.SAVED) },
            )
            GoogleNavTabItem(
                label = "記録",
                iconRes = R.drawable.ic_lucide_history,
                isSelected = selectedTab == GoogleNavTab.RECORD,
                onClick = { onTabSelected(GoogleNavTab.RECORD) },
            )
        }
    }
}

@Composable
fun GoogleNavTabItem(
    label: String,
    iconRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillColor = Color(0xFFC2E7FF)
    val selectedIconTint = Color(0xFF004A77)
    val unselectedTint = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isSelected) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = pillColor,
                tonalElevation = 0.dp,
                modifier = Modifier.size(width = 60.dp, height = 30.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = label,
                        tint = selectedIconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Box(
                modifier = Modifier.size(width = 60.dp, height = 30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = label,
                    tint = unselectedTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = unselectedTint,
            )
        }
    }
}
