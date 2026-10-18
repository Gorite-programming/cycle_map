package com.gorite.cyclemap.ui.cycling

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.BuildConfig
import com.gorite.cyclemap.power.BatteryStatus
import com.gorite.cyclemap.speech.VoiceGuidanceMode
import com.gorite.cyclemap.ui.theme.LocalAnimationEnabled
import com.gorite.cyclemap.ui.theme.Motion
import com.gorite.cyclemap.ui.theme.ThemeMode

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
    voiceGuidanceMode: VoiceGuidanceMode = VoiceGuidanceMode.VOICEVOX,
    onVoiceGuidanceModeChange: (VoiceGuidanceMode) -> Unit = {},
    themeMode: ThemeMode = ThemeMode.AUTO,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    batteryStatus: BatteryStatus? = null,
    animationEnabled: Boolean = true,
    onAnimationEnabledChange: (Boolean) -> Unit = {},
    hasLocationPermission: Boolean,
    onManageData: () -> Unit,
    onLicense: () -> Unit,
    onVersion: () -> Unit,
    onDeveloperOptions: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("設定", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("ナビ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SettingSwitch("自動リルート", autoReroute, onAutoReroute)
            SettingSwitch("ヘディングアップ", headingUp, onHeadingUp)

            Spacer(modifier = Modifier.height(4.dp))
            Text("音声案内 (四国めたん)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VoiceGuidanceMode.entries.forEach { mode ->
                    val isSelected = mode == voiceGuidanceMode
                    val animEnabled = LocalAnimationEnabled.current
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        animationSpec = Motion.motionTween(Motion.DURATION_SHORT, enabled = animEnabled),
                        label = "VoiceModeBg",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = Motion.motionTween(Motion.DURATION_SHORT, enabled = animEnabled),
                        label = "VoiceModeContent",
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = bgColor,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onVoiceGuidanceModeChange(mode) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                when (mode) {
                                    VoiceGuidanceMode.VOICEVOX -> "めたん"
                                    VoiceGuidanceMode.OFF -> "なし"
                                },
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = contentColor,
                            )
                        }
                    }
                }
            }

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
            SettingSwitch("アニメーション効果", animationEnabled, onAnimationEnabledChange)

            Spacer(modifier = Modifier.height(2.dp))
            Text("テーマ（昼夜連動）", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    val isSelected = mode == themeMode
                    val animEnabled = LocalAnimationEnabled.current
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        animationSpec = Motion.motionTween(Motion.DURATION_SHORT, enabled = animEnabled),
                        label = "ThemeModeBg",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = Motion.motionTween(Motion.DURATION_SHORT, enabled = animEnabled),
                        label = "ThemeModeContent",
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = bgColor,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onThemeModeChange(mode) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                when (mode) {
                                    ThemeMode.AUTO -> "自動"
                                    ThemeMode.LIGHT -> "ライト"
                                    ThemeMode.DARK -> "ダーク"
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = contentColor,
                            )
                        }
                    }
                }
            }

            if (batteryStatus != null) {
                Spacer(modifier = Modifier.height(2.dp))
                val statusText = buildString {
                    append("バッテリー: ${batteryStatus.levelPercent}%")
                    if (batteryStatus.isCharging) append(" (充電中)")
                    if (batteryStatus.isPowerSaveMode) append(" · OS省電力モード")
                    if (batteryStatus.isLowPower) append(" · 省電力(3D→2D制限中)")
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (batteryStatus.isLowPower) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "位置情報: ${if (hasLocationPermission) "許可済み" else "未許可"} · 単位: km",
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
                if (onDeveloperOptions != null) {
                    TextButton(onClick = onDeveloperOptions) { Text("開発者") }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("For Developers", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "バージョン: ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "ビルド元コミット: ${BuildConfig.GIT_HASH}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "ビルド日時: ${BuildConfig.BUILD_TIME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    val animEnabled = LocalAnimationEnabled.current
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = Motion.motionTween(Motion.DURATION_SHORT, enabled = animEnabled),
        label = "PoiChipBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.motionTween(Motion.DURATION_SHORT, enabled = animEnabled),
        label = "PoiChipContent",
    )
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
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
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            Text(cat.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1, color = contentColor)
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
