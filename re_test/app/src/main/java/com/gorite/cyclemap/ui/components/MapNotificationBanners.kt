package com.gorite.cyclemap.ui.components

import com.gorite.cyclemap.R
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gorite.cyclemap.data.TileProgress
import com.gorite.cyclemap.tracking.GpsSignalStatus
import com.gorite.cyclemap.ui.theme.Motion

@Composable
internal fun MapNotificationBanners(
    modifier: Modifier = Modifier,
    effectiveAnimationEnabled: Boolean,
    isAreaSelectMode: Boolean,
    onCancelAreaSelect: () -> Unit,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    gpsStatus: GpsSignalStatus,
    hasReceivedFirstFix: Boolean,
    warningMessage: String?,
    onDismissWarning: () -> Unit,
    currentTileProgress: TileProgress?,
    onDismissTileProgress: () -> Unit,
    isSwitchingGraph: Boolean,
    isCalculatingRoute: Boolean,
    isLocationServiceEnabled: Boolean = true,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Area-select mode instruction banner
        AnimatedVisibility(
            visible = isAreaSelectMode,
            enter = Motion.bannerEnter(effectiveAnimationEnabled),
            exit = Motion.bannerExit(effectiveAnimationEnabled),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "ドラッグしてダウンロード範囲を選択",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancelAreaSelect) {
                        Text("キャンセル", color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }

        // Permission Request
        AnimatedVisibility(
            visible = !hasLocationPermission,
            enter = Motion.bannerEnter(effectiveAnimationEnabled),
            exit = Motion.bannerExit(effectiveAnimationEnabled),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "位置情報が必要です",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = onRequestPermission) {
                        Text("許可")
                    }
                }
            }
        }

        // 端末の位置情報サービスOFF通知バナー (設定アプリへの誘導)
        AnimatedVisibility(
            visible = hasLocationPermission && !isLocationServiceEnabled,
            enter = Motion.bannerEnter(effectiveAnimationEnabled),
            exit = Motion.bannerExit(effectiveAnimationEnabled),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "端末の位置情報がOFFです",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = {
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }) {
                        Text("設定を開く")
                    }
                }
            }
        }

        // Warning / Error notification
        AnimatedVisibility(
            visible = warningMessage != null,
            enter = Motion.bannerEnter(effectiveAnimationEnabled),
            exit = Motion.bannerExit(effectiveAnimationEnabled),
            modifier = Modifier.fillMaxWidth(),
        ) {
            warningMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDismissWarning) { Text("閉じる") }
                    }
                }
            }
        }

        // Tile Download Progress Card
        AnimatedVisibility(
            visible = currentTileProgress != null,
            enter = Motion.bannerEnter(effectiveAnimationEnabled),
            exit = Motion.bannerExit(effectiveAnimationEnabled),
            modifier = Modifier.fillMaxWidth(),
        ) {
            currentTileProgress?.let { progress ->
                val isDone = progress.completed >= progress.total
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (isDone) "${progress.prefName} 保存完了" else "${progress.prefName} (${progress.sourceName}) 保存中…",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${progress.completed} / ${progress.total}", style = MaterialTheme.typography.labelMedium)
                                if (isDone) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    TextButton(onClick = onDismissTileProgress) { Text("閉じる") }
                                }
                            }
                        }
                        if (!isDone) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { if (progress.total > 0) progress.completed.toFloat() / progress.total else 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        // Graph Switch Indicator
        AnimatedVisibility(
            visible = isSwitchingGraph,
            enter = Motion.bannerEnter(effectiveAnimationEnabled),
            exit = Motion.bannerExit(effectiveAnimationEnabled),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF1A73E8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("県データグラフを切替・読み込み中…", style = MaterialTheme.typography.bodySmall, color = Color(0xFF202124), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Route Calculation Indicator
        AnimatedVisibility(
            visible = isCalculatingRoute,
            enter = Motion.bannerEnter(effectiveAnimationEnabled),
            exit = Motion.bannerExit(effectiveAnimationEnabled),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF1A73E8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("最適サイクリングルートを探索中…", style = MaterialTheme.typography.bodySmall, color = Color(0xFF1A73E8), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
