package com.gorite.cyclemap.ui.cycling

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.R
import com.gorite.cyclemap.routing.RoutePreference

/**
 * ルート情報カードの中身 (Google Maps 風ライトスタイルプレビュー)。
 * [started] が false の間は案内開始前の確認表示 (選好切替、標高グラフあり)。
 */
@Composable
fun RouteInfoCardContent(
    metrics: RideMetrics,
    profile: List<ElevationSample>,
    totalDistanceM: Double,
    progressDistanceM: Double?,
    started: Boolean,
    onStart: () -> Unit,
    onClearRoute: () -> Unit,
    modifier: Modifier = Modifier,
    onDownloadCorridorTiles: (() -> Unit)? = null,
    tileDownloadStatus: String? = null,
    selectedPreference: RoutePreference = RoutePreference.RECOMMENDED,
    onPreferenceChange: (RoutePreference) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (started) {
            // 案内中は GoogleNavFooter が主役になるため、ここでは何もしない
            return@Column
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_lucide_navigation),
                    contentDescription = null,
                    tint = Color(0xFF1A73E8),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("ルートプレビュー", color = Color(0xFF202124), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    metrics.remainingM?.let { formatKm(it) } ?: "--",
                    color = Color(0xFF1A73E8),
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF1F3F4),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClearRoute),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_lucide_x),
                            contentDescription = "ルートを消去",
                            tint = Color(0xFF5F6368),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // ルート選好切替チップ (推奨 / 平坦 / 最短)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoutePreference.entries.forEach { pref ->
                val isSelected = pref == selectedPreference
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color(0xFFE8F0FE) else Color(0xFFF8F9FA),
                    border = if (isSelected) {
                        BorderStroke(1.5.dp, Color(0xFF1A73E8))
                    } else {
                        BorderStroke(1.dp, Color(0xFFDADCE0))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPreferenceChange(pref) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            pref.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF1A73E8) else Color(0xFF3C4043),
                        )
                    }
                }
            }
        }

        // 基本指標セル (残り時間 / 予想平均速度 / 獲得標高)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        ) {
            RouteStatCell(
                R.drawable.ic_lucide_clock,
                "予想時間",
                metrics.remainingS?.let { formatRemainingTime(it) } ?: "--",
                Modifier.weight(1f),
            )
            RouteStatCell(
                R.drawable.ic_lucide_gauge,
                "平均速度",
                metrics.averageKmh?.let { "%.1fkm/h".format(java.util.Locale.US, it) } ?: "--",
                Modifier.weight(1f),
            )
            RouteStatCell(
                R.drawable.ic_lucide_trending_up,
                "獲得標高",
                metrics.elevationGainM?.let { "${it.toInt()}m" } ?: "--",
                Modifier.weight(1f),
            )
        }

        // アクションボタン列 (ナビ開始 + 回廊タイル保存)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
            ) {
                Icon(
                    painterResource(R.drawable.ic_lucide_navigation),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("ナビ開始", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
            if (onDownloadCorridorTiles != null) {
                OutlinedButton(
                    onClick = onDownloadCorridorTiles,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1A73E8)),
                    border = BorderStroke(1.dp, Color(0xFFDADCE0)),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_lucide_download),
                        contentDescription = "オフライン地図保存",
                        tint = Color(0xFF1A73E8),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tileDownloadStatus ?: "回廊保存", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A73E8))
                }
            }
        }

        if (profile.size >= 2) {
            ElevationProfileChart(
                profile = profile,
                totalDistanceM = totalDistanceM,
                progressDistanceM = progressDistanceM,
                lineColor = Color(0xFF1A73E8),
                passedColor = Color(0xFF0F9D58),
                gridColor = Color(0xFFE8EAED),
                labelColor = Color(0xFF5F6368),
            )
            Text(
                "標高プロファイル（推定）",
                color = Color(0xFF5F6368),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun GradePill(label: String, grade: Double, modifier: Modifier = Modifier) {
    val level = gradeLevelOf(grade)
    val iconRes = when (level) {
        GradeLevel.STEEP_UP, GradeLevel.GENTLE_UP -> R.drawable.ic_lucide_trending_up
        GradeLevel.DOWN -> R.drawable.ic_lucide_trending_down
        GradeLevel.FLAT -> R.drawable.ic_lucide_minus
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF1F3F4),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = Color(0xFF5F6368), fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = level.label,
                    tint = Color(0xFF202124),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "${formatGrade(grade)} · ${level.label}",
                    color = Color(0xFF202124),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun RouteStatCell(iconRes: Int, label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = Color(0xFF5F6368),
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = Color(0xFF5F6368), fontSize = 11.sp)
        Text(value, color = Color(0xFF202124), fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
    }
}
