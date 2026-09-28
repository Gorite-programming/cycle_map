package com.gorite.cyclemap.ui.cycling

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.R
import com.gorite.cyclemap.tracking.GpxParser
import com.gorite.cyclemap.tracking.RideHistorySummary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

val CyclingCyan = Color(0xFF00E5FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideHistorySheet(
    isRecording: Boolean,
    pointCount: Int,
    rideSummary: String?,
    gpxDir: File,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onShowTrackOnMap: (RideHistorySummary) -> Unit,
    onDismiss: () -> Unit,
) {
    val histories = remember(isRecording) {
        if (!gpxDir.exists()) emptyList()
        else {
            gpxDir.listFiles { file -> file.extension.lowercase() == "gpx" }
                ?.sortedByDescending { it.lastModified() }
                ?.mapNotNull { GpxParser.parse(it) }
                ?: emptyList()
        }
    }

    var selectedRide by remember { mutableStateOf<RideHistorySummary?>(null) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("走行記録 (GPX)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            // 現在の記録ステータスカード
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isRecording) CyclingPink.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                if (isRecording) "記録中..." else "未記録",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecording) CyclingPink else MaterialTheme.colorScheme.onSurface,
                            )
                            if (isRecording) {
                                Text("$pointCount 地点取得", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (isRecording) {
                            Button(
                                onClick = onStopRecording,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CyclingPink),
                            ) {
                                Text("記録停止・保存")
                            }
                        } else {
                            Button(onClick = onStartRecording) {
                                Text("記録開始")
                            }
                        }
                    }
                    rideSummary?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 過去のライド履歴セクション
            Text("過去のライド履歴 (${histories.size}件)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (histories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("保存された走行ログがありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(histories) { ride ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedRide = ride },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        dateFormat.format(ride.startTime),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            "%.2f km".format(Locale.US, ride.totalDistanceMeters / 1000.0),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            formatDuration(ride.totalDurationSeconds),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            "平均 %.1f km/h".format(Locale.US, ride.averageSpeedKmh),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Icon(
                                    painterResource(R.drawable.ic_lucide_arrow_right),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ライド詳細ダイアログ
    selectedRide?.let { ride ->
        RideDetailDialog(
            ride = ride,
            onShowOnMap = {
                onShowTrackOnMap(ride)
                selectedRide = null
                onDismiss()
            },
            onDismiss = { selectedRide = null },
        )
    }
}

@Composable
fun RideDetailDialog(
    ride: RideHistorySummary,
    onShowOnMap: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.JAPAN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("ライド詳細", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    dateFormat.format(ride.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 統計グリッド
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatPill("総距離", "%.2f km".format(Locale.US, ride.totalDistanceMeters / 1000.0), CyclingPink)
                    StatPill("走行時間", formatDuration(ride.totalDurationSeconds), MaterialTheme.colorScheme.primary)
                    StatPill("獲得標高", "+%.0f m".format(Locale.US, ride.elevationGainMeters), CyclingCyan)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatPill("平均速度", "%.1f km/h".format(Locale.US, ride.averageSpeedKmh), MaterialTheme.colorScheme.onSurface)
                    StatPill("最高速度", "%.1f km/h".format(Locale.US, ride.maxSpeedKmh), MaterialTheme.colorScheme.onSurface)
                    StatPill("地点数", "${ride.points.size} 点", MaterialTheme.colorScheme.onSurfaceVariant)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 速度推移グラフ
                Text("速度プロファイル (km/h)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (ride.points.size >= 2) {
                    SpeedProfileChart(
                        points = ride.points,
                        maxSpeed = (ride.maxSpeedKmh * 1.1).coerceAtLeast(20.0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                    )
                } else {
                    Text("データ不足", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = onShowOnMap) {
                Icon(painterResource(R.drawable.ic_lucide_map), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("地図に軌跡を表示")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
    )
}

@Composable
private fun StatPill(label: String, value: String, accentColor: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.width(88.dp),
    ) {
        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accentColor)
        }
    }
}

@Composable
private fun SpeedProfileChart(
    points: List<com.gorite.cyclemap.tracking.GpxTrackPoint>,
    maxSpeed: Double,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.background(Color(0xFF1E2430), RoundedCornerShape(8.dp))) {
        val w = size.width
        val h = size.height
        val pad = 12f

        val totalDist = points.last().distanceMetersFromStart.coerceAtLeast(1.0)
        val path = Path()

        points.forEachIndexed { i, p ->
            val x = pad + (p.distanceMetersFromStart / totalDist).toFloat() * (w - 2 * pad)
            val y = (h - pad) - (p.speedKmh / maxSpeed).toFloat().coerceIn(0f, 1f) * (h - 2 * pad)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = CyclingCyan,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
