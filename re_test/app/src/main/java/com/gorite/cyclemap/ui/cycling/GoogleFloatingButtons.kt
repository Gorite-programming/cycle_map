package com.gorite.cyclemap.ui.cycling

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gorite.cyclemap.R

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
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 5.dp,
        modifier = modifier.size(42.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_layers),
                contentDescription = "地図レイヤー切替: $label",
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
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
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 5.dp,
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

/**
 * Google Maps スタイル ズームボタン (白丸 42dp, 拡大/縮小アイコン)
 */
@Composable
fun GoogleZoomButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 5.dp,
        modifier = modifier.size(42.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
