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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import com.gorite.cyclemap.routing.RoutePreference
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.R

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

private fun formatKm(meters: Double): String =
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
    NavigationBar(modifier = modifier.height(76.dp)) {
        CyclingTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        painterResource(tab.iconRes),
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = { Text(tab.label, fontSize = 12.sp) },
            )
        }
    }
}

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

// ---------------------------------------------------------------------------
// スポットパネル (サイクリスト向けPOIクイック検索・オフラインDB)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotPanelSheet(
    selectedCategory: SpotQuickCategory?,
    onCategorySelect: (SpotQuickCategory) -> Unit,
    isLoading: Boolean,
    spots: List<NearbySpot>,
    message: String?,
    onSpotClick: (NearbySpot) -> Unit,
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
            Text("スポット", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "トイレ・コンビニ・休憩場所をオフラインDBから検索",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SpotQuickCategory.entries.take(3).forEach { cat ->
                    SpotChip(cat, selectedCategory == cat, { onCategorySelect(cat) }, Modifier.weight(1f))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SpotQuickCategory.entries.drop(3).forEach { cat ->
                    SpotChip(cat, selectedCategory == cat, { onCategorySelect(cat) }, Modifier.weight(1f))
                }
            }
            HorizontalDivider()
            when {
                isLoading -> Text("検索中…", style = MaterialTheme.typography.bodyMedium)
                message != null -> Text(message, style = MaterialTheme.typography.bodyMedium)
                else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(spots, key = { "${it.name}:${it.latitude}:${it.longitude}" }) { spot ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSpotClick(spot) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(
                                    PoiCategory.forCategory(spot.category)?.iconRes
                                        ?: R.drawable.ic_lucide_map_pin,
                                ),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(spot.name, fontWeight = FontWeight.Medium)
                                Text(
                                    spot.category,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                formatKm(spot.distanceM),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotChip(
    cat: SpotQuickCategory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painterResource(cat.poi.iconRes),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(cat.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ---------------------------------------------------------------------------
// 記録パネル (GPX記録 + 走行履歴)
// ---------------------------------------------------------------------------

data class GpxHistoryEntry(
    val fileName: String,
    val dateText: String,
    val sizeKb: Long,
    val pointCount: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordPanelSheet(
    isRecording: Boolean,
    pointCount: Int,
    rideSummary: String?,
    history: List<GpxHistoryEntry>,
    onToggleRecord: () -> Unit,
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
            Text("記録", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(
                onClick = onToggleRecord,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                ),
            ) { Text(if (isRecording) "記録停止" else "GPX記録開始", fontWeight = FontWeight.Bold) }
            if (isRecording) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "記録中 (${pointCount} pts)",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            rideSummary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            HorizontalDivider()
            Text("走行履歴", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (history.isEmpty()) {
                Text(
                    "まだ記録がありません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(history, key = { it.fileName }) { entry ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(entry.fileName, fontWeight = FontWeight.Medium)
                            Text(
                                "${entry.dateText} · ${entry.sizeKb}KB · ${entry.pointCount}pts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 設定パネル
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanelSheet(
    autoReroute: Boolean,
    onAutoReroute: (Boolean) -> Unit,
    headingUp: Boolean,
    onHeadingUp: (Boolean) -> Unit,
    layerIndex: Int,
    layerLabels: List<String>,
    onLayerSelect: (Int) -> Unit,
    poiVisible: Boolean,
    onPoiVisible: (Boolean) -> Unit,
    poiSelected: Set<PoiCategory>,
    onPoiToggle: (PoiCategory) -> Unit,
    hasLocationPermission: Boolean,
    onManageData: () -> Unit,
    onLicense: () -> Unit,
    onVersion: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("設定", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("ナビ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SettingSwitch("自動リルート", autoReroute, onAutoReroute)
            SettingSwitch("ヘディングアップ", headingUp, onHeadingUp)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("地図レイヤー (オフライン・航空写真なし)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            layerLabels.forEachIndexed { index, label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLayerSelect(index) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = layerIndex == index, onClick = { onLayerSelect(index) })
                    Text(label, modifier = Modifier.padding(start = 8.dp))
                }
            }
            SettingSwitch("POI表示 (ズーム14以上)", poiVisible, onPoiVisible)
            if (poiVisible) {
                Text(
                    "表示カテゴリ (タップで切替)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // カテゴリ選択チップ (2列グリッド相当の折返しRow)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PoiCategory.entries.chunked(5).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { cat ->
                                PoiSelectChip(
                                    cat = cat,
                                    selected = cat in poiSelected,
                                    onClick = { onPoiToggle(cat) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(5 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("全般", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "位置情報: ${if (hasLocationPermission) "許可済み" else "未許可"} · 単位: km · テーマ: システム連動",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onManageData,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("オフラインデータ管理", fontWeight = FontWeight.Bold) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onLicense) { Text("ライセンス") }
                TextButton(onClick = onVersion) { Text("バージョン情報") }
            }
        }
    }
}

@Composable
private fun PoiSelectChip(
    cat: PoiCategory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painterResource(cat.iconRes),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(cat.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------------------------------------------------------------------------
// 見本準拠のダーク調サイコンUI (地図上の操作系 + ルート情報カード)
// ---------------------------------------------------------------------------

/** ダークネイビー基調の共通色。 */
val CyclingNavy = Color(0xFF0F1E33)
val CyclingNavySoft = Color(0xFF1B2F4D)
val CyclingSubText = Color(0xFF9FB3C8)
val CyclingPink = Color(0xFFEC407A)
val CyclingPinkLight = Color(0xFFF48FB1)

/** 秒 → "H:MM" (見本の残り時間表記)。 */
fun formatHMM(seconds: Double): String {
    if (!seconds.isFinite()) return "--:--"
    val totalMin = (seconds / 60).toInt().coerceAtLeast(0)
    return "${totalMin / 60}:${"%02d".format(totalMin % 60)}"
}

/** 上部サーチバー (メニュー + 県名バッジ＋地名 + 検索)。 */
@Composable
fun TopSearchBar(
    locationLabel: String,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DarkCircleButton(R.drawable.ic_lucide_menu, "メニュー", onMenuClick)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CyclingNavy.copy(alpha = 0.94f),
            shadowElevation = 6.dp,
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                val prefIndex = locationLabel.indexOf("県")
                if (prefIndex in 2..4) {
                    val prefName = locationLabel.substring(0, prefIndex + 1)
                    val restName = locationLabel.substring(prefIndex + 1)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CyclingPink.copy(alpha = 0.25f),
                        modifier = Modifier.padding(end = 6.dp),
                    ) {
                        Text(
                            prefName,
                            color = CyclingPinkLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        restName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (restName.length > 12) 13.sp else 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        locationLabel,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (locationLabel.length > 14) 13.sp else 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        DarkCircleButton(R.drawable.ic_lucide_search, "検索", onSearchClick)
    }
}

@Composable
fun DarkCircleButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Int = 22,
) {
    Surface(
        shape = CircleShape,
        color = CyclingNavy.copy(alpha = 0.94f),
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(iconSize.dp),
            )
        }
    }
}

/**
 * コンパスダイヤル。N/E/S/W付き円盤が端末方位に連動して回転する。
 * ヘディングアップ時は地図回転と整合するよう方位分だけ回す。
 */
@Composable
fun CompassDial(
    headingDegrees: Float,
    headingUp: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    // ヘディングアップ時は枠をルート色にし、地図上のGPS進行矢印と役割が違うことを示す
    // (ダイヤル=端末方位の北針、矢印=GPS進行方向)。ノースアップ時は通常枠。
    Surface(
        shape = CircleShape,
        color = CyclingNavy.copy(alpha = 0.94f),
        shadowElevation = 6.dp,
        border = if (headingUp) {
            androidx.compose.foundation.BorderStroke(2.dp, CyclingPink)
        } else {
            null
        },
        modifier = modifier,
    ) {
        Canvas(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
        ) {
            val rotation = -headingDegrees
            rotate(rotation) {
                val cx = size.width / 2
                val cy = size.height / 2
                val r = size.minDimension / 2 - 6.dp.toPx()
                // 北針 (赤い三角)
                val needle = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, cy - r - 2.dp.toPx())
                    lineTo(cx - 5.dp.toPx(), cy - 2.dp.toPx())
                    lineTo(cx + 5.dp.toPx(), cy - 2.dp.toPx())
                    close()
                }
                drawPath(needle, Color(0xFFE53935))
                // 南針 (シルバー/グレーの三角)
                val southNeedle = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, cy + r + 2.dp.toPx())
                    lineTo(cx - 5.dp.toPx(), cy + 2.dp.toPx())
                    lineTo(cx + 5.dp.toPx(), cy + 2.dp.toPx())
                    close()
                }
                drawPath(southNeedle, Color(0xFF90A4AE))
                // 方位文字
                val label = { text: String, color: Color, dx: Float, dy: Float ->
                    val layout = measurer.measure(
                        text,
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                        ),
                    )
                    drawText(
                        measurer,
                        text,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(
                            cx + dx - layout.size.width / 2,
                            cy + dy - layout.size.height / 2,
                        ),
                    )
                }
                label("N", Color.White, 0f, -r + 8.dp.toPx())
                label("E", CyclingSubText, r - 8.dp.toPx(), 0f)
                label("S", CyclingSubText, 0f, r - 8.dp.toPx())
                label("W", CyclingSubText, -(r - 8.dp.toPx()), 0f)
            }
        }
    }
}

/** 右側コントロール (レイヤ / 現在地 / + / − の一体ピル)。 */
@Composable
fun DarkControlStack(
    layerLabel: String,
    following: Boolean,
    onLayerClick: () -> Unit,
    onTargetClick: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
    canZoomIn: Boolean = true,
    canZoomOut: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CyclingNavy.copy(alpha = 0.94f),
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DarkStackButton(
                iconRes = R.drawable.ic_lucide_layers,
                description = "地図レイヤー切替",
                sublabel = layerLabel,
                onClick = onLayerClick,
            )
            DarkStackButton(
                iconRes = R.drawable.ic_lucide_locate_fixed,
                description = "現在地へ移動",
                onClick = onTargetClick,
                tint = if (following) Color.White else Color(0xFF64B5F6),
            )
            DarkStackRepeatButton(
                iconRes = R.drawable.ic_ms_add,
                description = "ズームイン",
                onAction = onZoomIn,
                enabled = canZoomIn,
            )
            DarkStackRepeatButton(
                iconRes = R.drawable.ic_ms_remove,
                description = "ズームアウト",
                onAction = onZoomOut,
                enabled = canZoomOut,
                showDivider = false,
            )
        }
    }
}

@Composable
private fun DarkStackRepeatButton(
    iconRes: Int,
    description: String,
    onAction: () -> Unit,
    enabled: Boolean = true,
    showDivider: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val currentOnAction by rememberUpdatedState(onAction)
    val isPressed = remember { mutableStateOf(false) }

    LaunchedEffect(isPressed.value, enabled) {
        if (isPressed.value && enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnAction()
            // 長押しリピート開始までの初回待機
            delay(400L)
            // 押し続けている間、高速に連続リピート
            while (isPressed.value && enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentOnAction()
                delay(180L)
            }
        }
    }

    val alpha = if (enabled) 1.0f else 0.35f

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp, 56.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (isPressed.value && enabled) {
                        Modifier.background(Color.White.copy(alpha = 0.15f))
                    } else {
                        Modifier
                    }
                )
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            isPressed.value = true
                            try {
                                awaitRelease()
                            } finally {
                                isPressed.value = false
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = description,
                tint = Color.White.copy(alpha = alpha),
                modifier = Modifier.size(28.dp),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.2f),
                thickness = 1.dp,
                modifier = Modifier.width(28.dp),
            )
        }
    }
}

@Composable
private fun DarkStackButton(
    iconRes: Int,
    description: String,
    onClick: () -> Unit,
    sublabel: String? = null,
    tint: Color = Color.White,
    showDivider: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier
                .size(60.dp, 56.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(26.dp),
            )
            sublabel?.let {
                Text(it, fontSize = 9.sp, color = tint, maxLines = 1)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.2f),
                thickness = 1.dp,
                modifier = Modifier.width(28.dp),
            )
        }
    }
}

/** 現在速度チップ (ルート中の地図オーバーレイ)。 */
@Composable
fun SpeedChip(speedKmh: Double, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CyclingNavy.copy(alpha = 0.94f),
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_lucide_gauge),
                contentDescription = "現在速度",
                tint = CyclingSubText,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("${"%.1f".format(java.util.Locale.US, speedKmh)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text("km/h", color = CyclingSubText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/**
 * ルート情報カードの中身 (見本準拠・ダーク)。
 * 呼び出し側Cardの container を [CyclingNavy] にすること。
 * [started] が false の間は案内開始前の確認表示 (標高グラフあり)。
 * 開始後は走行用ミニマム表示 (標高グラフ・勾配なし)。
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
            // 案内中は地図面積を確保するため1行ミニ表示にする (詳細は案内前の確認表示に残す)。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "目的地まで ${metrics.remainingM?.let { formatKm(it) } ?: "--"}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                    )
                    Text(
                        "残り ${metrics.remainingS?.let { formatHMM(it) } ?: "--:--"}",
                        color = CyclingSubText,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = CyclingNavySoft,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClearRoute),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_lucide_x),
                            contentDescription = "ルートを消去",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
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
                    painterResource(R.drawable.ic_lucide_map_pin),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("現在地", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "目的地まで ${metrics.remainingM?.let { formatKm(it) } ?: "--"}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = CyclingNavySoft,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClearRoute),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_lucide_x),
                            contentDescription = "ルートを消去",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RoutePreference.entries.forEach { pref ->
                val isSelected = pref == selectedPreference
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) CyclingPink else CyclingNavySoft,
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPreferenceChange(pref) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            pref.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else CyclingSubText,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
        ) {
            RouteStatCell(
                R.drawable.ic_lucide_clock,
                "残り時間",
                metrics.remainingS?.let { formatHMM(it) } ?: "--:--",
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
        if (!started) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_lucide_navigation),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("案内開始", fontWeight = FontWeight.Bold)
                }
                if (onDownloadCorridorTiles != null) {
                    OutlinedButton(
                        onClick = onDownloadCorridorTiles,
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_lucide_download),
                            contentDescription = "オフライン地図保存",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tileDownloadStatus ?: "地図保存", fontSize = 13.sp)
                    }
                }
            }
            if (profile.size >= 2) {
                ElevationProfileChart(
                    profile = profile,
                    totalDistanceM = totalDistanceM,
                    progressDistanceM = progressDistanceM,
                    lineColor = CyclingPink,
                    passedColor = Color.White,
                    gridColor = Color.White.copy(alpha = 0.18f),
                    labelColor = CyclingSubText,
                )
                Text(
                    "標高は推定表示",
                    color = CyclingSubText,
                    fontSize = 10.sp,
                )
            }
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
        color = Color.White.copy(alpha = 0.10f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = CyclingSubText, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = level.label,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "${formatGrade(grade)} · ${level.label}",
                    color = Color.White,
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
            tint = CyclingSubText,
            modifier = Modifier.size(20.dp),
        )
        Text(label, color = CyclingSubText, fontSize = 11.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
    }
}
