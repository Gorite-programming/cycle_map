package com.gorite.cyclemap.ui.map

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gorite.cyclemap.R
import com.gorite.cyclemap.contract.EtaInfo
import com.gorite.cyclemap.contract.GuidanceStep
import com.gorite.cyclemap.contract.LaneInfo
import com.gorite.cyclemap.contract.TurnType

/**
 * 契約 GuidanceStep, LaneInfo, TurnType に準拠した全幅緑ヘッダー
 */
@Composable
fun GoogleNavHeaderV2(
    currentStep: GuidanceStep?,
    nextStep: GuidanceStep?,
    laneHints: List<LaneInfo>,
    isRerouting: Boolean,
    animationEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.25f
    val containerColor = if (isRerouting) MaterialTheme.colorScheme.error else if (isDarkTheme) Color(0xFF00695C) else GoogleGreen

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (isRerouting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_route),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.nav_recalculating),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else if (currentStep != null) {
                // 上部UI最上段: > > > T X E N が順次降下し、右へ流れてフェードアウト
                NextFlowAnimation(
                    animationEnabled = animationEnabled,
                    fadeColor = containerColor,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 大型ターンアイコン
                    Icon(
                        painter = painterResource(turnTypeToDrawableRes(currentStep.turnType)),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatDistance(currentStep.distanceToTurnMeters),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = currentStep.instructionText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        if (!currentStep.streetName.isNullOrEmpty()) {
                            Text(
                                text = currentStep.streetName,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD4EED8)
                            )
                        }
                    }
                }

                // 車線案内バー (Lane Guidance)
                if (laneHints.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 54.dp)
                    ) {
                        laneHints.forEach { lane ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (lane.isRecommended) Color.White else Color(0x55FFFFFF),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(turnTypeToDrawableRes(lane.direction)),
                                        contentDescription = stringResource(R.string.lane_guidance),
                                        tint = if (lane.isRecommended) GoogleGreen else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 次の案内 (Second instruction HUD)
                if (nextStep != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 54.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.next_turn) + ": ",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD4EED8)
                        )
                        Icon(
                            painter = painterResource(turnTypeToDrawableRes(nextStep.turnType)),
                            contentDescription = null,
                            tint = Color(0xFFD4EED8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = nextStep.instructionText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * 契約 EtaInfo に準拠したナビゲーションフッター
 */
@Composable
fun GoogleNavFooterV2(
    eta: EtaInfo?,
    remainingDistanceMeters: Double,
    remainingSeconds: Double,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF101114),
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val duration = formatDuration(eta?.remainingSeconds ?: remainingSeconds)
                val arrivalText = eta?.let {
                    val time = java.time.Instant.ofEpochMilli(it.arrivalEpochMillis)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalTime()
                    String.format("%02d:%02d", time.hour, time.minute)
                } ?: "到着時刻を計算中"

                Text(
                    text = duration,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFC928),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = formatDistance(if (eta != null) eta.remainingDistanceMeters else remainingDistanceMeters),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "・ $arrivalText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (eta != null && eta.currentSpeedKmh > 0) {
                        Text(
                            text = String.format("・ %.0f km/h", eta.currentSpeedKmh),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onToggleMute, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(if (isMuted) R.drawable.ic_lucide_volume_x else R.drawable.ic_lucide_volume_2),
                        contentDescription = if (isMuted) stringResource(R.string.nav_mute) else stringResource(R.string.nav_unmute),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Button(
                    onClick = onStopNavigation,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303134)),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_x),
                        contentDescription = stringResource(R.string.nav_stop),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * 横画面用1行HUDバー (ランドスケープ表示)
 */
@Composable
fun LandscapeNavHudBar(
    currentStep: GuidanceStep?,
    eta: EtaInfo?,
    remainingSeconds: Double,
    onStopNavigation: () -> Unit,
    animationEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = GoogleGreen,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentStep != null) {
                    NextFlowAnimation(
                        animationEnabled = animationEnabled,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Icon(
                        painter = painterResource(turnTypeToDrawableRes(currentStep.turnType)),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${formatDistance(currentStep.distanceToTurnMeters)} ${currentStep.instructionText}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val etaText = if (eta != null) {
                    val dt = java.time.Instant.ofEpochMilli(eta.arrivalEpochMillis)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalTime()
                    String.format("%02d:%02d 到着", dt.hour, dt.minute)
                } else {
                    formatEta(remainingSeconds)
                }
                Text(
                    text = etaText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onStopNavigation,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = stringResource(R.string.nav_stop),
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private fun turnTypeToDrawableRes(turn: TurnType): Int {
    return when (turn) {
        TurnType.Straight -> R.drawable.ic_ms_straight
        TurnType.Left -> R.drawable.ic_ms_turn_left
        TurnType.Right -> R.drawable.ic_ms_turn_right
        TurnType.SlightLeft -> R.drawable.ic_ms_turn_slight_left
        TurnType.SlightRight -> R.drawable.ic_ms_turn_slight_right
        TurnType.SharpLeft -> R.drawable.ic_ms_turn_sharp_left
        TurnType.SharpRight -> R.drawable.ic_ms_turn_sharp_right
        TurnType.Uturn -> R.drawable.ic_ms_u_turn_left
        TurnType.Roundabout -> R.drawable.ic_ms_roundabout_left
        TurnType.Arrive -> R.drawable.ic_destination
    }
}
