package com.gorite.cyclemap.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 地図上部に表示するコンパクトな天気ピル
 */
@Composable
fun WeatherPill(
    uiState: WeatherUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    when (uiState) {
        is WeatherUiState.Fresh -> {
            WeatherPillContent(
                data = uiState.data,
                isOffline = false,
                onClick = { showDialog = true },
                modifier = modifier
            )
        }
        is WeatherUiState.Stale -> {
            WeatherPillContent(
                data = uiState.data,
                isOffline = uiState.isOffline,
                onClick = { showDialog = true },
                modifier = modifier
            )
        }
        is WeatherUiState.Error -> {
            if (uiState.cached != null) {
                WeatherPillContent(
                    data = uiState.cached,
                    isOffline = true,
                    onClick = { showDialog = true },
                    modifier = modifier
                )
            }
        }
        WeatherUiState.Loading, WeatherUiState.Unavailable -> {
            // 表示なし
        }
    }

    if (showDialog) {
        val data = when (uiState) {
            is WeatherUiState.Fresh -> uiState.data
            is WeatherUiState.Stale -> uiState.data
            is WeatherUiState.Error -> uiState.cached
            else -> null
        }
        if (data != null) {
            WeatherDetailDialog(
                data = data,
                isOffline = (uiState is WeatherUiState.Stale && uiState.isOffline) || uiState is WeatherUiState.Error,
                onDismiss = { showDialog = false },
                onRefresh = onRefresh
            )
        }
    }
}

@Composable
private fun WeatherPillContent(
    data: WeatherForecastData,
    isOffline: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val category = WeatherCategory.fromCode(data.weatherCode)
    val iconRes = when (category) {
        WeatherCategory.SUNNY -> R.drawable.ic_lucide_sun
        WeatherCategory.CLOUDY -> R.drawable.ic_lucide_cloud
        WeatherCategory.RAINY -> R.drawable.ic_lucide_droplets
        WeatherCategory.SNOWY -> R.drawable.ic_lucide_snowflake
        WeatherCategory.UNKNOWN -> R.drawable.ic_lucide_cloud
    }
    val iconTint = when (category) {
        WeatherCategory.SUNNY -> Color(0xFFF57C00)
        WeatherCategory.CLOUDY -> Color(0xFF78909C)
        WeatherCategory.RAINY -> Color(0xFF1976D2)
        WeatherCategory.SNOWY -> Color(0xFF00ACC1)
        WeatherCategory.UNKNOWN -> MaterialTheme.colorScheme.onSurface
    }

    val tempText = if (data.currentTemp != null) {
        "${data.currentTemp.roundToInt()}℃"
    } else {
        "—℃"
    }

    val popText = if (data.currentPop != null) {
        "${data.currentPop}%"
    } else {
        null
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = data.weatherText,
                tint = if (isOffline) iconTint.copy(alpha = 0.6f) else iconTint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = tempText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOffline) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
            )
            if (popText != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = popText,
                    fontSize = 12.sp,
                    color = if (isOffline) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            if (isOffline) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_cloud_off),
                    contentDescription = "オフライン",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * 天気詳細・予報ダイアログ
 */
@Composable
fun WeatherDetailDialog(
    data: WeatherForecastData,
    isOffline: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN) }
    val fetchedTimeStr = remember(data.fetchedAtMillis) {
        dateFormat.format(Date(data.fetchedAtMillis))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = data.targetAreaName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isOffline) "オフライン（キャッシュデータ）" else "最新予報",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 天気概況
                Text(
                    text = data.weatherText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // 時間帯別降水確率
                if (data.popList.isNotEmpty()) {
                    Text(
                        text = "降水確率",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    data.popList.forEach { pop ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = pop.timePeriod, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (pop.popPercent != null) "${pop.popPercent}%" else "—",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 気温情報
                if (data.tempList.isNotEmpty()) {
                    Text(
                        text = "予想気温",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    data.tempList.forEach { temp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = temp.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (temp.tempCelsius != null) "${temp.tempCelsius.roundToInt()}℃" else "—℃",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 取得・発表日時 & 帰属
                Text(
                    text = "データ取得: $fetchedTimeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (data.reportDatetime.isNotEmpty()) {
                    Text(
                        text = "気象庁発表: ${data.reportDatetime}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "出典：気象庁ホームページ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("閉じる")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = {
                onRefresh()
                onDismiss()
            }) {
                Text("更新")
            }
        }
    )
}
