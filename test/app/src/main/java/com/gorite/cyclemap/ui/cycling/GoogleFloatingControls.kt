package com.gorite.cyclemap.ui.cycling

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.R
import com.gorite.cyclemap.ui.theme.LocalAnimationEnabled
import com.gorite.cyclemap.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * Google マップ風の白背景・丸型フローティングボタン。
 */
@Composable
fun GoogleFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    contentDescription: String? = null,
    tint: Color = Color(0xFF3C4043),
    containerColor: Color = Color.White,
    content: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        shadowElevation = 6.dp,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (content != null) {
                content()
            } else if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * ズーム操作用ボタン (400ms長押し後180ms間隔でリピートするロジックを完全継承)。
 */
@Composable
fun GoogleZoomRepeatButton(
    iconRes: Int,
    contentDescription: String,
    onAction: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
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
        label = "GoogleZoomBtnScale",
    )

    Box(
        modifier = modifier
            .size(48.dp, 44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isPressed.value && enabled) {
                    Modifier.background(Color(0xFFE8EAED))
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
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color(0xFF3C4043).copy(alpha = alpha),
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * ズームイン/ズームアウトを上下に連結した Google マップ風の白ピルカード。
 */
@Composable
fun GoogleZoomPill(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    canZoomIn: Boolean = true,
    canZoomOut: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 6.dp,
        modifier = modifier.width(48.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GoogleZoomRepeatButton(
                iconRes = R.drawable.ic_ms_add,
                contentDescription = "ズームイン",
                onAction = onZoomIn,
                enabled = canZoomIn,
            )
            HorizontalDivider(
                color = Color(0xFFE8EAED),
                thickness = 1.dp,
                modifier = Modifier.width(32.dp),
            )
            GoogleZoomRepeatButton(
                iconRes = R.drawable.ic_ms_remove,
                contentDescription = "ズームアウト",
                onAction = onZoomOut,
                enabled = canZoomOut,
            )
        }
    }
}

/**
 * Google マップ風 右側コントロール列（縦画面時は縦積み Column、横画面時は横並び Row）。
 * 上から順に:
 * 1. コンパス
 * 2. 3D / 2D 切替ボタン
 * 3. 地図レイヤー切替ボタン
 * 4. 現在地 (GPS) ボタン
 * 5. ズーム一対 (+ / -)
 * 6. エリア選択ボタン (案内中は非表示)
 */
@Composable
fun GoogleMapsControlColumn(
    headingDegrees: () -> Float,
    isHeadingUp: Boolean,
    onCompassClick: () -> Unit,
    currentTilt: Double,
    onToggleTilt: () -> Unit,
    layerLabel: String,
    onLayerClick: () -> Unit,
    following: Boolean,
    onTargetClick: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    canZoomIn: Boolean = true,
    canZoomOut: Boolean = true,
    isNavigationActive: Boolean = false,
    isAreaSelectMode: Boolean = false,
    onAreaSelectToggle: () -> Unit = {},
    isLandscape: Boolean = false,
    onNavStartClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (isLandscape) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isNavigationActive) {
                // エリア選択
                Surface(
                    shape = if (isAreaSelectMode) RoundedCornerShape(22.dp) else CircleShape,
                    color = if (isAreaSelectMode) MaterialTheme.colorScheme.primary else Color.White,
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onAreaSelectToggle)
                            .padding(horizontal = if (isAreaSelectMode) 10.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_lucide_square_dashed),
                                contentDescription = "エリア選択",
                                tint = if (isAreaSelectMode) Color.White else Color(0xFF3C4043),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        if (isAreaSelectMode) {
                            Text(
                                text = "選択中",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                }
            }

            // コンパス
            CompassDial(
                headingDegrees = headingDegrees,
                headingUp = isHeadingUp,
                onClick = onCompassClick,
            )

            // 3D / 2D トグル
            GoogleFloatingActionButton(
                onClick = onToggleTilt,
                contentDescription = if (currentTilt > 15.0) "2D表示へ切替" else "3D表示へ切替",
            ) {
                Text(
                    text = if (currentTilt > 15.0) "2D" else "3D",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (currentTilt > 15.0) Color(0xFF1A73E8) else Color(0xFF3C4043),
                )
            }

            // レイヤー切替
            GoogleFloatingActionButton(
                onClick = onLayerClick,
                iconRes = R.drawable.ic_lucide_layers,
                contentDescription = "地図レイヤー切替: $layerLabel",
                tint = Color(0xFF3C4043),
            )

            // 現在地 (GPS)
            GoogleFloatingActionButton(
                onClick = onTargetClick,
                iconRes = R.drawable.ic_lucide_locate_fixed,
                contentDescription = "現在地へ移動",
                tint = if (following) Color(0xFF1A73E8) else Color(0xFF5F6368),
            )

            // ズームイン/アウト (横画面時は横並び)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier.height(48.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GoogleZoomRepeatButton(
                        iconRes = R.drawable.ic_ms_add,
                        contentDescription = "ズームイン",
                        onAction = onZoomIn,
                        enabled = canZoomIn,
                        modifier = Modifier.size(44.dp, 48.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(Color(0xFFE8EAED))
                    )
                    GoogleZoomRepeatButton(
                        iconRes = R.drawable.ic_ms_remove,
                        contentDescription = "ズームアウト",
                        onAction = onZoomOut,
                        enabled = canZoomOut,
                        modifier = Modifier.size(44.dp, 48.dp),
                    )
                }
            }

            // 深緑ナビ開始FAB (↱)
            if (!isNavigationActive && onNavStartClick != null) {
                NavStartFAB(onClick = onNavStartClick)
            }
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // 1. コンパス
            CompassDial(
                headingDegrees = headingDegrees,
                headingUp = isHeadingUp,
                onClick = onCompassClick,
            )

            // 2. 3D / 2D トグル
            GoogleFloatingActionButton(
                onClick = onToggleTilt,
                contentDescription = if (currentTilt > 15.0) "2D表示へ切替" else "3D表示へ切替",
            ) {
                Text(
                    text = if (currentTilt > 15.0) "2D" else "3D",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (currentTilt > 15.0) Color(0xFF1A73E8) else Color(0xFF3C4043),
                )
            }

            // 3. レイヤー切替
            GoogleFloatingActionButton(
                onClick = onLayerClick,
                iconRes = R.drawable.ic_lucide_layers,
                contentDescription = "地図レイヤー切替: $layerLabel",
                tint = Color(0xFF3C4043),
            )

            // 4. 現在地 (GPS)
            GoogleFloatingActionButton(
                onClick = onTargetClick,
                iconRes = R.drawable.ic_lucide_locate_fixed,
                contentDescription = "現在地へ移動",
                tint = if (following) Color(0xFF1A73E8) else Color(0xFF5F6368),
            )

            // 5. ズーム一対 (+ / -)
            GoogleZoomPill(
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                canZoomIn = canZoomIn,
                canZoomOut = canZoomOut,
            )

            // 6. エリア選択ボタン (案内中は非表示)
            if (!isNavigationActive) {
                Surface(
                    shape = if (isAreaSelectMode) RoundedCornerShape(22.dp) else CircleShape,
                    color = if (isAreaSelectMode) MaterialTheme.colorScheme.primary else Color.White,
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onAreaSelectToggle)
                            .padding(horizontal = if (isAreaSelectMode) 10.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_lucide_square_dashed),
                                contentDescription = "エリア選択",
                                tint = if (isAreaSelectMode) Color.White else Color(0xFF3C4043),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        if (isAreaSelectMode) {
                            Text(
                                text = "選択中",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                }
            }

            // 7. 深緑ナビ開始 FAB (↱)
            if (!isNavigationActive && onNavStartClick != null) {
                NavStartFAB(onClick = onNavStartClick)
            }
        }
    }
}

/**
 * Google Maps 公式スタイルの深緑ナビ開始 FAB (↱)。
 * - サイズ: 56dp x 56dp
 * - 形状: 角丸 16dp (RoundedCornerShape(16.dp))
 * - 背景色: 深緑ティール #006A60
 * - 影: 6dp
 * - アイコン: 白色 斜め右矢印 ↱ (28dp)
 */
@Composable
fun NavStartFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF006A60),
        shadowElevation = 6.dp,
        modifier = modifier.size(56.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_google_nav_arrow),
                contentDescription = "ナビ・ルート探索を開始",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Google Maps スタイル 右上レイヤー切替ボタン (白丸 40dp, レイヤーアイコン)
 */
@Composable
fun GoogleLayerButton(
    onClick: () -> Unit,
    label: String = "レイヤー",
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 3.dp,
        modifier = modifier.size(40.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_layers),
                contentDescription = "地図レイヤー切替: $label",
                tint = Color(0xFF3C4043),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Google Maps スタイル 右下GPSボタン (白丸 48dp, ターゲットアイコン)
 */
@Composable
fun GoogleGpsButton(
    following: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = modifier.size(48.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_locate_fixed),
                contentDescription = "現在地へ移動",
                tint = if (following) Color(0xFF1A73E8) else Color(0xFF5F6368),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
