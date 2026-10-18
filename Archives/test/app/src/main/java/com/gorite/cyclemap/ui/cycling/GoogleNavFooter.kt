package com.gorite.cyclemap.ui.cycling

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.R
import com.gorite.cyclemap.routing.NavigationStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Google Maps スタイルのナビゲーション案内フッター。
 * 白背景、浮遊感のあるモダンなデザイン。
 * 1. ETA / 残り時間 / 残り距離 (大文字グリーン太字)
 * 2. 速度チップ (SpeedChip)
 * 3. GPX 記録トグル＆点数カウンター
 * 4. 法的帰属表示 (© OSM / 地理院)
 * 5. 案内終了ボタン (赤丸型 #D93025)
 * navStats が null の場合でも 15km/h 基準の暫定概算で安全にフォールバック表示。
 */
@Composable
internal fun GoogleNavFooter(
    navStats: NavigationStats?,
    totalDistanceMeters: Double,
    speedKmh: Double,
    isRecording: Boolean,
    gpxPointCount: Int,
    onToggleRecording: () -> Unit,
    onStopNavigation: () -> Unit,
    onShowLicense: () -> Unit,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // navStats が null の場合のフォールバック値計算 (15 km/h = 4.167 m/s)
    val fallbackSpeedMps = 15.0 / 3.6
    val remainingMeters = navStats?.remainingMeters ?: totalDistanceMeters
    val durationRemainingSeconds = navStats?.durationRemainingSeconds?.takeIf { it.isFinite() }
        ?: (remainingMeters / fallbackSpeedMps)
    val etaEpochMillis = navStats?.etaEpochMillis
        ?: (System.currentTimeMillis() + (durationRemainingSeconds * 1000).toLong())

    val etaText = formatNavEta(etaEpochMillis)
    val remainingTimeText = formatNavDuration(durationRemainingSeconds)
    val remainingDistanceText = formatNavRemaining(remainingMeters)

    if (isLandscape) {
        // 横画面: 1行展開のコンパクトバー (セクション 3.4 準拠)
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    SpeedChip(speedKmh = speedKmh)

                    Text(
                        text = etaText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F9D58),
                    )

                    Text(
                        text = "$remainingTimeText · $remainingDistanceText",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF202124),
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isRecording) Color(0xFFFCE8E6) else Color(0xFFF1F3F4),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onToggleRecording),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecording) Color(0xFFD93025) else Color(0xFF5F6368)),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRecording) "REC ${gpxPointCount}p" else "GPX",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecording) Color(0xFFD93025) else Color(0xFF3C4043),
                            )
                        }
                    }

                    Text(
                        text = "© OSM / 地理院",
                        fontSize = 11.sp,
                        color = Color(0xFF70757A),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onShowLicense)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onStopNavigation,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD93025)),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_x),
                        contentDescription = "案内終了",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                // 上段: 速度チップ & GPX記録ボタン
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 現在速度表示チップ
                    SpeedChip(speedKmh = speedKmh)

                    // GPX 記録トグルボタン
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isRecording) Color(0xFFFCE8E6) else Color(0xFFF1F3F4),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onToggleRecording),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecording) Color(0xFFD93025) else Color(0xFF5F6368)),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRecording) "REC ${gpxPointCount}p" else "GPX記録",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecording) Color(0xFFD93025) else Color(0xFF3C4043),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 中段: ETA / 残り時間 / 残り距離 & 案内終了ボタン
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ETA / 残り時間 / 残り距離 (Google Nav スタイル)
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.weight(1f),
                    ) {
                        // 特大グリーン ETA
                        Text(
                            text = etaText,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F9D58),
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // 残り時間 & 残り距離
                        Column(
                            modifier = Modifier.padding(bottom = 2.dp),
                        ) {
                            Text(
                                text = remainingTimeText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF202124),
                            )
                            Text(
                                text = remainingDistanceText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF5F6368),
                            )
                        }
                    }

                    // 赤色 案内終了ボタン (丸型✕)
                    Button(
                        onClick = onStopNavigation,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD93025)),
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lucide_x),
                            contentDescription = "案内終了",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 下段: 法的帰属表示 (© OSM / 地理院)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "© OSM / 地理院",
                        fontSize = 11.sp,
                        color = Color(0xFF70757A),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onShowLicense)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

private fun formatNavEta(epochMillis: Long): String {
    val sdf = SimpleDateFormat("H:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

private fun formatNavDuration(seconds: Double): String {
    val totalMins = (seconds / 60.0).roundToInt()
    return if (totalMins >= 60) {
        val hrs = totalMins / 60
        val mins = totalMins % 60
        "${hrs}時間${mins}分"
    } else {
        "${totalMins.coerceAtLeast(1)}分"
    }
}

private fun formatNavRemaining(meters: Double): String {
    return if (meters >= 1000.0) {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    } else {
        "${meters.roundToInt()} m"
    }
}
