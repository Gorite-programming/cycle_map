package com.gorite.cyclemap.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.R
import kotlinx.coroutines.delay

@Composable
fun MapControls(
    bearingDeg: Float,
    onResetNorth: () -> Unit,
    onLayerClick: () -> Unit,
    onMyLocationClick: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleTilt: (() -> Unit)? = null,
    is3D: Boolean = false,
    onAreaSelectToggle: (() -> Unit)? = null,
    isAreaSelectMode: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // コンパス (地図回転時のみ表示、BUG-79: NaNガード)
        val safeBearing = if (bearingDeg.isNaN()) 0f else bearingDeg
        val isRotated = Math.abs(safeBearing) > 2.0f
        AnimatedVisibility(
            visible = isRotated,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FloatingCircleButton(
                onClick = onResetNorth,
                contentDescription = "北を上に"
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_compass),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(-safeBearing),
                    tint = Color(0xFFD32F2F)
                )
            }
        }

        // レイヤー切替ボタン
        FloatingCircleButton(
            onClick = onLayerClick,
            contentDescription = "地図レイヤー切替"
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_layers),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Google Maps 風 3D / 2D 切替ボタン
        if (onToggleTilt != null) {
            FloatingCircleButton(
                onClick = onToggleTilt,
                contentDescription = if (is3D) "2D表示に切替" else "3D表示に切替"
            ) {
                Text(
                    text = if (is3D) "2D" else "3D",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (is3D) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // エリアダウンロード枠線ボタン
        if (onAreaSelectToggle != null) {
            FloatingCircleButton(
                onClick = onAreaSelectToggle,
                contentDescription = "エリア選択ダウンロード"
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_square_dashed),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isAreaSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 現在地ボタン
        FloatingCircleButton(
            onClick = onMyLocationClick,
            contentDescription = "現在地"
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_navigation),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // ズームイン・ズームアウトボタン (長押しリピート対応)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 5.dp
        ) {
            Column {
                RepeatActionButton(
                    iconRes = R.drawable.ic_ms_add,
                    contentDescription = "拡大",
                    onAction = onZoomIn
                )
                RepeatActionButton(
                    iconRes = R.drawable.ic_lucide_minus,
                    contentDescription = "縮小",
                    onAction = onZoomOut
                )
            }
        }
    }
}

@Composable
fun FloatingCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 5.dp,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun RepeatActionButton(
    iconRes: Int,
    contentDescription: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnAction by rememberUpdatedState(onAction)

    LaunchedEffect(isPressed) {
        if (isPressed) {
            currentOnAction()
            delay(400) // 初回長押しディレイ
            while (isPressed) {
                currentOnAction()
                delay(120) // リピート間隔
            }
        }
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
