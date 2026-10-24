package com.gorite.cyclemap.ui.map

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ルート案内時・上部UI最上段の独自アニメーションバナー。
 *
 * 1. N
 * 2. E
 * 3. X
 * 4. T
 * 5. 左の >
 * 6. 中央の >
 * 7. 一番右の >
 * を順次上からアニメーションで降ろし、全員が揃った後に右へ移動させフェードアウトする。
 *
 * @param modifier 修飾子 (fillMaxWidth 推奨、上下マージン 0)
 * @param animationEnabled アニメーション有効/無効フラグ (無効時は静止表示)
 * @param textColor 文字色 (デフォルト: 白)
 */
@Composable
fun NextFlowAnimation(
    modifier: Modifier = Modifier,
    animationEnabled: Boolean = true,
    fadeColor: Color = Color.Transparent, // 互換性のための引数 (不要だが既存シグネチャを保護)
    textColor: Color = Color.White,
) {
    // 表示要素: "N", "E", "X", "T", ">", ">", ">" (全7要素)
    val items = listOf("N", "E", "X", "T", ">", ">", ">")
    // ドロップ順: 1:一番右>(idx 6), 2:中>(idx 5), 3:左>(idx 4), 4:T(idx 3), 5:X(idx 2), 6:E(idx 1), 7:N(idx 0)
    // order = 6 - index (0..6)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidthDp = maxWidth

        if (!animationEnabled) {
            // アニメーション無効時は左端にすべて静止表示
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, char ->
                    Text(
                        text = char,
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                    )
                    if (index == 3) {
                        Spacer(modifier = Modifier.width(4.dp))
                    } else if (index in 4..5) {
                        Spacer(modifier = Modifier.width(1.dp))
                    } else if (index in 0..2) {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                }
            }
        } else {
            val infiniteTransition = rememberInfiniteTransition(label = "NextDropAndSlideLoop")
            val progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "NextDropAndSlideProgress"
            )

            // イージング定義
            val dropEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f) // すっと落ちてピタッと着地
            val slideEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f) // 右へ加速スライド

            // スライド＆フェードアウト (progress 0.50 .. 0.88)
            val slideProgress = ((progress - 0.50f) / 0.38f).coerceIn(0f, 1f)
            val slideX = if (progress >= 0.50f) {
                slideEasing.transform(slideProgress) * (totalWidthDp.value * 0.85f)
            } else {
                0f
            }
            val slideAlpha = when {
                progress < 0.50f -> 1.0f
                progress <= 0.88f -> (1.0f - slideProgress).coerceIn(0f, 1f)
                else -> 0f // 0.88 .. 1.00 はブランク
            }

            Row(
                modifier = Modifier
                    .offset(x = slideX.dp)
                    .graphicsLayer {
                        alpha = slideAlpha
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, char ->
                    // 降ろす順序: N -> E -> X -> T -> > -> > -> > (index 0..6)
                    val dropOrder = index
                    val startProgress = dropOrder * 0.042f
                    val dropDuration = 0.14f

                    val charDropProgress = ((progress - startProgress) / dropDuration).coerceIn(0f, 1f)
                    val dropEased = dropEasing.transform(charDropProgress)

                    // 上から降りてくる (Y: -28dp -> 0dp)
                    val charY = -28f * (1f - dropEased)
                    val charAlpha = if (progress < startProgress) 0f else dropEased

                    Text(
                        text = char,
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.graphicsLayer {
                            translationY = charY.dp.toPx()
                            alpha = charAlpha
                        }
                    )

                    // スペーシング (NEXT と >>> の間を少し広げる)
                    if (index == 3) {
                        Spacer(modifier = Modifier.width(4.dp))
                    } else if (index in 4..5) {
                        Spacer(modifier = Modifier.width(1.dp))
                    } else if (index in 0..2) {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F9D58)
@Composable
private fun NextFlowAnimationPreview() {
    Box(modifier = Modifier.padding(16.dp)) {
        NextFlowAnimation(animationEnabled = true)
    }
}
