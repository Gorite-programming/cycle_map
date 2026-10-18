package com.gorite.cyclemap.ui.cycling

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.R
import com.gorite.cyclemap.ui.theme.LocalAnimationEnabled
import com.gorite.cyclemap.ui.theme.Motion
import kotlinx.coroutines.delay

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
        color = Color.White,
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
                tint = Color(0xFF3C4043),
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
    headingDegrees: () -> Float,
    headingUp: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    // ヘディングアップ時は枠をアクセントにし、ノースアップ時は通常枠。白背景Google Mapsスタイル
    Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 6.dp,
        border = if (headingUp) {
            BorderStroke(2.dp, Color(0xFFD93025))
        } else {
            BorderStroke(1.dp, Color(0xFFE0E0E0))
        },
        modifier = modifier,
    ) {
        Canvas(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
        ) {
            val rotation = -headingDegrees()
            rotate(rotation) {
                val cx = size.width / 2
                val cy = size.height / 2
                val r = size.minDimension / 2 - 6.dp.toPx()
                // 北針 (赤い三角: Google Red #D93025)
                val needle = Path().apply {
                    moveTo(cx, cy - r - 2.dp.toPx())
                    lineTo(cx - 5.dp.toPx(), cy - 2.dp.toPx())
                    lineTo(cx + 5.dp.toPx(), cy - 2.dp.toPx())
                    close()
                }
                drawPath(needle, Color(0xFFD93025))
                // 南針 (シルバー/グレーの三角)
                val southNeedle = Path().apply {
                    moveTo(cx, cy + r + 2.dp.toPx())
                    lineTo(cx - 5.dp.toPx(), cy + 2.dp.toPx())
                    lineTo(cx + 5.dp.toPx(), cy + 2.dp.toPx())
                    close()
                }
                drawPath(southNeedle, Color(0xFFBDC1C6))
                // 方位文字
                val label = { text: String, color: Color, dx: Float, dy: Float ->
                    val layout = measurer.measure(
                        text,
                        TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                        ),
                    )
                    drawText(
                        measurer,
                        text,
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                        ),
                        topLeft = Offset(
                            cx + dx - layout.size.width / 2,
                            cy + dy - layout.size.height / 2,
                        ),
                    )
                }
                label("N", Color(0xFFD93025), 0f, -r + 8.dp.toPx())
                label("E", Color(0xFF5F6368), r - 8.dp.toPx(), 0f)
                label("S", Color(0xFF5F6368), 0f, r - 8.dp.toPx())
                label("W", Color(0xFF5F6368), -(r - 8.dp.toPx()), 0f)
            }
        }
    }
}

@Composable
fun CompassDial(
    headingDegrees: Float,
    headingUp: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompassDial(
        headingDegrees = { headingDegrees },
        headingUp = headingUp,
        onClick = onClick,
        modifier = modifier,
    )
}

/** 右側コントロール (レイヤ / 現在地 / + / − の一体ピル)。横画面時は horizontal = true で横並びに切り替え可能。 */
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
    horizontal: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CyclingNavy.copy(alpha = 0.94f),
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        val buttons = @Composable {
            DarkStackButton(
                iconRes = R.drawable.ic_lucide_layers,
                description = "地図レイヤー切替",
                sublabel = layerLabel,
                onClick = onLayerClick,
                showDivider = !horizontal,
            )
            DarkStackButton(
                iconRes = R.drawable.ic_lucide_locate_fixed,
                description = "現在地へ移動",
                onClick = onTargetClick,
                tint = if (following) Color.White else Color(0xFF64B5F6),
                showDivider = !horizontal,
            )
            DarkStackRepeatButton(
                iconRes = R.drawable.ic_ms_add,
                description = "ズームイン",
                onAction = onZoomIn,
                enabled = canZoomIn,
                showDivider = !horizontal,
            )
            DarkStackRepeatButton(
                iconRes = R.drawable.ic_ms_remove,
                description = "ズームアウト",
                onAction = onZoomOut,
                enabled = canZoomOut,
                showDivider = false,
            )
        }
        if (horizontal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                buttons()
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                buttons()
            }
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
    val animEnabled = LocalAnimationEnabled.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed.value && enabled) 0.90f else 1.0f,
        animationSpec = Motion.motionTween(Motion.DURATION_SHORT, enabled = animEnabled),
        label = "RepeatBtnScale",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp, 56.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
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
    val isPressed = remember { mutableStateOf(false) }
    val animEnabled = LocalAnimationEnabled.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed.value) 0.90f else 1.0f,
        animationSpec = Motion.motionTween(Motion.DURATION_SHORT, enabled = animEnabled),
        label = "StackBtnScale",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier
                .size(60.dp, 56.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed.value = true
                            try {
                                awaitRelease()
                            } finally {
                                isPressed.value = false
                            }
                        },
                        onTap = { onClick() },
                    )
                },
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

/** 現在速度チップ (ルート中の地図オーバーレイ: Google Maps スタイル)。 */
@Composable
fun SpeedChip(speedKmh: Double, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
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
                tint = Color(0xFF5F6368),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("${"%.1f".format(java.util.Locale.US, speedKmh)}", color = Color(0xFF202124), fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text("km/h", color = Color(0xFF5F6368), fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}
