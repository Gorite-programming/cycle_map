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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gorite.cyclemap.R

// ---------------------------------------------------------------------------
// ルートパネル
// ---------------------------------------------------------------------------

data class SavedRoute(
    val name: String,
    val distanceM: Double,
    val gainM: Int,
)

fun mockSavedRoutes(): List<SavedRoute> = listOf(
    SavedRoute("山口市〜秋吉台 カルストライド", 42_000.0, 620),
    SavedRoute("周防大島 みかん海道一周", 68_000.0, 890),
    SavedRoute("錦帯橋〜岩国城 ヒルクライム", 18_500.0, 410),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePanelSheet(
    hasRoute: Boolean,
    totalDistanceM: Double?,
    remainingText: String,
    durationText: String,
    gainM: Int?,
    maxGradePct: Double?,
    maxDownhillPct: Double?,
    onSearchClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("ルート", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(
                onClick = onSearchClick,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_lucide_search),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("目的地を検索", fontWeight = FontWeight.Bold)
            }
            if (hasRoute) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("現在のルート", style = MaterialTheme.typography.labelMedium)
                        RouteDetailRow(R.drawable.ic_lucide_route, "総距離", totalDistanceM?.let { formatKm(it) } ?: "--")
                        RouteDetailRow(R.drawable.ic_lucide_map_pin, "残り", remainingText)
                        RouteDetailRow(R.drawable.ic_lucide_clock, "残り時間", durationText)
                        RouteDetailRow(R.drawable.ic_lucide_trending_up, "獲得標高(推定)", gainM?.let { "${it}m" } ?: "--")
                        RouteDetailRow(R.drawable.ic_lucide_mountain, "最大勾配(推定)", maxGradePct?.let { formatGrade(it) } ?: "--")
                        RouteDetailRow(R.drawable.ic_lucide_trending_down, "最大下り(推定)", maxDownhillPct?.let { formatGrade(it) } ?: "--")
                    }
                }
            }
            HorizontalDivider()
            Text("保存済みルート", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            // TODO: 保存済みルートの永続化 (DataStore/DB) に接続する。現在はモック表示。
            mockSavedRoutes().forEach { route ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(route.name, fontWeight = FontWeight.Medium)
                        Text(
                            "${formatKm(route.distanceM.toDouble())} · 上り${route.gainM}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onSearchClick) { Text("開く") }
                }
            }
        }
    }
}

@Composable
private fun RouteDetailRow(iconRes: Int, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
