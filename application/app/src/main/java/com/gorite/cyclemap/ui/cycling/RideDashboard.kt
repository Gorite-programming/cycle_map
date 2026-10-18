package com.gorite.cyclemap.ui.cycling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// ダッシュボード (現在速度 / 残り距離 + 残り時間 / 平均 / 獲得標高 / 現在標高 + 勾配)
// ---------------------------------------------------------------------------

@Composable
fun RideDashboard(
    metrics: RideMetrics,
    hasRoute: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            // 現在速度
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = metrics.speedKmh?.let { "%.1f".format(java.util.Locale.US, it) } ?: "--",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "km/h",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 7.dp),
                )
            }
            // 残り距離
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "残り距離",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = metrics.remainingM?.let { formatKm(it) } ?: "--",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MiniStat("残り時間", metrics.remainingS?.let { formatMinutes(it) } ?: "--")
            MiniStat(
                "平均速度",
                metrics.averageKmh?.let { "%.1fkm/h".format(java.util.Locale.US, it) } ?: "--",
            )
            MiniStat(
                "獲得標高",
                metrics.elevationGainM?.let { "${it.toInt()}m" } ?: "--",
            )
            MiniStat(
                "現在標高",
                metrics.currentElevationM?.let { "${it.toInt()}m" } ?: "--",
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        // 勾配ピル (上り/下りが直感的に分かる)
        metrics.gradePct?.let { grade ->
            val level = gradeLevelOf(grade)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when (level) {
                    GradeLevel.STEEP_UP -> MaterialTheme.colorScheme.errorContainer
                    GradeLevel.GENTLE_UP -> MaterialTheme.colorScheme.secondaryContainer
                    GradeLevel.DOWN -> MaterialTheme.colorScheme.tertiaryContainer
                    GradeLevel.FLAT -> MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("現在の勾配", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${level.symbol} ${formatGrade(grade)} · ${level.label}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        if (!hasRoute) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "ルート未設定 — 地図を長押しで目的地を設定",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

internal fun formatKm(meters: Double): String =
    if (meters >= 1000.0) "%.2fkm".format(java.util.Locale.US, meters / 1000.0)
    else "${meters.toInt()}m"

private fun formatMinutes(seconds: Double): String {
    if (!seconds.isFinite()) return "--"
    val min = (seconds / 60).toInt()
    if (min < 1) return "まもなく"
    return if (min >= 60) "${min / 60}時間${min % 60}分" else "${min}分"
}

// ---------------------------------------------------------------------------
// 下部ナビゲーション (5タブ・大きめタップ対象)
// ---------------------------------------------------------------------------

@Composable
fun CyclingBottomBar(
    selected: CyclingTab,
    onSelect: (CyclingTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.height(68.dp),
        containerColor = Color.White,
        contentColor = Color(0xFF202124),
        tonalElevation = 6.dp,
    ) {
        CyclingTab.entries.forEach { tab ->
            val isSelected = selected == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        painterResource(tab.iconRes),
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = {
                    Text(
                        tab.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF1A73E8),
                    selectedTextColor = Color(0xFF1A73E8),
                    unselectedIconColor = Color(0xFF5F6368),
                    unselectedTextColor = Color(0xFF5F6368),
                    indicatorColor = Color(0xFFE8F0FE),
                ),
            )
        }
    }
}
