package com.gorite.cyclemap.ui.cycling

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.R

/**
 * Google Maps 完全再現: 地名・天候サマリーシート (SCR-01, SCR-02)
 * - 白角丸シート (topStart = 20dp, topEnd = 20dp) + ドラッグハンドル (32dp x 4dp #DADCE0)
 * - 左側: 現在地名 (20sp ボールド #202124、例: "井口明神")
 * - 右側: 三日月アイコン 🌙「25℃」+ 黄色丸インジケータ「AQI」
 */
@Composable
fun LocationSummarySheet(
    locationName: String,
    modifier: Modifier = Modifier,
    temperatureText: String = "25℃",
    aqiText: String = "AQI",
    onClick: () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ドラッグハンドル
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 左側: 現在地名 (井口明神 等)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = locationName.ifEmpty { "井口明神" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "タップして周辺スポット・詳細情報を表示",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 右側: 天候 🌙 25℃ + AQI 黄色ドット (Google Maps 公式ピルコンテナ #F1F3F4)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F3F4),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // 三日月 + 気温
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_google_moon),
                                contentDescription = "天候",
                                tint = Color(0xFF5F6368),
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = temperatureText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF3C4043),
                            )
                        }

                        // 中間ドット
                        Text(
                            text = "•",
                            color = Color(0xFFBDC1C6),
                            fontSize = 11.sp,
                        )

                        // AQI 黄色ドット
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFBBC04)), // Google Yellow AQI dot
                            )
                            Text(
                                text = aqiText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF5F6368),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

/**
 * Google Maps 下部ナビゲーションタブ
 */
enum class GoogleNavTab {
    EXPLORE,   // 「スポット」
    SAVED,     // 「保存済み」
    RECORD,    // 「記録」
}

/**
 * Google Maps 完全再現: 3タブナビゲーションバー (SCR-01, SCR-02)
 * - 高さ 56dp, 白背景, 上部ボーダー線 #E8EAED
 * - タブ 1:「スポット」(水色ピル選択インジケータ #C2E7FF + 青ピン + 青文字)
 * - タブ 2:「保存済み」(ブックマークアイコン)
 * - タブ 3:「記録」(GPX走行ログ履歴)
 */
@Composable
fun GoogleNavTabBar(
    selectedTab: GoogleNavTab,
    onTabSelected: (GoogleNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                // タブ 1: スポット
                GoogleNavTabItem(
                    label = "スポット",
                    iconRes = R.drawable.ic_google_explore,
                    isSelected = selectedTab == GoogleNavTab.EXPLORE,
                    onClick = { onTabSelected(GoogleNavTab.EXPLORE) },
                )

                // タブ 2: 保存済み
                GoogleNavTabItem(
                    label = "保存済み",
                    iconRes = R.drawable.ic_google_bookmark,
                    isSelected = selectedTab == GoogleNavTab.SAVED,
                    onClick = { onTabSelected(GoogleNavTab.SAVED) },
                )

                // タブ 3: 記録
                GoogleNavTabItem(
                    label = "記録",
                    iconRes = R.drawable.ic_lucide_history,
                    isSelected = selectedTab == GoogleNavTab.RECORD,
                    onClick = { onTabSelected(GoogleNavTab.RECORD) },
                )
            }
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isSelected) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(width = 56.dp, height = 28.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            Box(
                modifier = Modifier.size(width = 56.dp, height = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
