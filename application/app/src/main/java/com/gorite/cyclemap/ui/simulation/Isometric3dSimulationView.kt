package com.gorite.cyclemap.ui.simulation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gorite.cyclemap.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * ジオラマのテーマ種別
 */
enum class DioramaTheme(val label: String) {
    NATURE_CABIN("山と川・ログハウス"),
    SNOW_MOUNTAIN("雪山ジオラマ"),
}

/**
 * アイソメトリック 3D ジオラマシミュレーション画面。
 *
 * - 新テーマ: 「山と川、ログハウス」
 *   - 緑豊かなボクセルの山肌、山から流れ下る青い清流（川と滝）
 *   - 温かみのある木造ログハウス、煙突からポッポと昇る煙のアニメーション
 *   - 川のせせらぎ・水流のきらめきエフェクト
 *   - 深緑の針葉樹、上空を流れる白い雲
 * - 3D 空間チルト（回転・傾き）、パン、ピンチズーム操作対応
 * - 旧「雪山ジオラマ」とのワンタップ切り替え対応
 */
@Composable
fun Isometric3dSimulationDialog(
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
        ),
    ) {
        Isometric3dSimulationScreen(onClose = onDismiss)
    }
}

@Composable
fun Isometric3dSimulationScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTheme by remember { mutableStateOf(DioramaTheme.NATURE_CABIN) }

    // 3D 視点操作用ステート
    var rotX by remember { mutableFloatStateOf(0f) }
    var rotY by remember { mutableFloatStateOf(0f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1.0f) }

    // アニメーション用タイマー
    val transition = rememberInfiniteTransition(label = "IsoSimAnimLoop")
    val smokeProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "SmokeProgress",
    )
    val riverShimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "RiverShimmerProgress",
    )
    val cloudProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "CloudProgress",
    )

    // テーマごとの背景色
    val bgColor = if (selectedTheme == DioramaTheme.NATURE_CABIN) {
        Color(0xFFC2C5F8) // 自然山のパステルラベンダー空
    } else {
        Color(0xFFD4D6ED) // 雪山の淡いグレーラベンダー
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = bgColor,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
        ) {
            // 3D ジオラマコンテナ (ドラッグで3Dチルト、ピンチでズーム)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.7f, 3.2f)
                            if (zoom != 1f) {
                                // 2本指ズーム時のパン追従
                                panX += pan.x
                                panY += pan.y
                            } else {
                                // 1本指ドラッグ時は3Dチルト回転 (MED-11)
                                rotY = (rotY + pan.x * 0.12f).coerceIn(-30f, 30f)
                                rotX = (rotX - pan.y * 0.12f).coerceIn(-25f, 25f)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (selectedTheme == DioramaTheme.NATURE_CABIN) {
                    // -----------------------------------------------------------
                    // 1. 新テーマ: 「山と川、ログハウス」
                    // -----------------------------------------------------------
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth(0.96f)
                            .aspectRatio(1376f / 768f)
                            .graphicsLayer {
                                this.rotationX = rotX
                                this.rotationY = rotY
                                this.scaleX = scale
                                this.scaleY = scale
                                this.translationX = panX
                                this.translationY = panY
                                this.cameraDistance = 18f * density
                            },
                    ) {
                        // マスターアセット (緑の山、川、ログハウス)
                        Image(
                            painter = painterResource(R.drawable.img_isometric_nature_mountain),
                            contentDescription = "3D Isometric Nature Mountain with River and Log Cabin",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )

                        // リアルタイム・エフェクトオーバーレイ
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // ログハウス煙突の相対位置: X=868/1376, Y=295/768
                            val chimneyX = size.width * (868f / 1376f)
                            val chimneyY = size.height * (295f / 768f)

                            // 1. ログハウス煙突から立ち昇る煙 (4つのスモークパフ)
                            for (i in 0..3) {
                                val phase = (smokeProgress + i * 0.25f) % 1.0f
                                val curY = chimneyY - (phase * size.height * 0.11f)
                                val curX = chimneyX + sin(phase * 4.5f) * (size.width * 0.007f)
                                val radius = (size.width * 0.006f) + (phase * size.width * 0.010f)
                                val alpha = (0.75f * (1f - phase)).coerceIn(0f, 0.75f)

                                drawCircle(
                                    color = Color.White.copy(alpha = alpha),
                                    radius = radius,
                                    center = Offset(curX, curY),
                                )
                            }

                            // 2. 山から流れる川のせせらぎ・きらめきエフェクト (山頂付近から池への流路)
                            val riverSparkles = listOf(
                                Offset(size.width * 0.44f, size.height * 0.42f),
                                Offset(size.width * 0.50f, size.height * 0.49f),
                                Offset(size.width * 0.58f, size.height * 0.53f),
                                Offset(size.width * 0.65f, size.height * 0.58f),
                                Offset(size.width * 0.72f, size.height * 0.63f),
                            )
                            for ((idx, pos) in riverSparkles.withIndex()) {
                                val phase = (riverShimmer + idx * 0.2f) % 1.0f
                                val sparkleAlpha = (sin(phase * Math.PI.toFloat()) * 0.7f).coerceIn(0f, 0.7f)
                                drawCircle(
                                    color = Color(0xFFE0F7FA).copy(alpha = sparkleAlpha),
                                    radius = size.width * 0.005f,
                                    center = pos,
                                )
                            }
                        }
                    }
                } else {
                    // -----------------------------------------------------------
                    // 2. 前テーマ: 「雪山ジオラマ」
                    // -----------------------------------------------------------
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth(0.96f)
                            .aspectRatio(1024f / 689f)
                            .graphicsLayer {
                                this.rotationX = rotX
                                this.rotationY = rotY
                                this.scaleX = scale
                                this.scaleY = scale
                                this.translationX = panX
                                this.translationY = panY
                                this.cameraDistance = 18f * density
                            },
                    ) {
                        val boxW = maxWidth
                        val boxH = maxHeight

                        Image(
                            painter = painterResource(R.drawable.img_isometric_3d_island),
                            contentDescription = "3D Isometric Snow Island",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )

                        // 煙突の煙
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val basePxX = size.width * (542f / 1024f)
                            val basePxY = size.height * (308f / 689f)

                            for (i in 0..3) {
                                val phase = (smokeProgress + i * 0.25f) % 1.0f
                                val currentY = basePxY - (phase * size.height * 0.085f)
                                val currentX = basePxX + sin(phase * 4f) * (size.width * 0.006f)
                                val radius = (size.width * 0.007f) + (phase * size.width * 0.009f)
                                val alpha = (0.75f * (1f - phase)).coerceIn(0f, 0.75f)

                                drawCircle(
                                    color = Color.White.copy(alpha = alpha),
                                    radius = radius,
                                    center = Offset(currentX, currentY),
                                )
                            }
                        }

                        // 雲1
                        val cloud1Drift = (sin(cloudProgress * 2f * Math.PI.toFloat()) * (boxW.value * 0.015f)).dp
                        Image(
                            painter = painterResource(R.drawable.img_iso_cloud_1),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth(0.137f)
                                .offset(
                                    x = (boxW * (440f / 1024f)) + cloud1Drift,
                                    y = boxH * (70f / 689f),
                                )
                                .graphicsLayer {
                                    this.translationX = rotY * 0.6f
                                    this.translationY = -rotX * 0.6f
                                },
                            contentScale = ContentScale.FillWidth,
                        )

                        // 雲2
                        val cloud2Drift = (cos(cloudProgress * 2f * Math.PI.toFloat()) * (boxW.value * 0.012f)).dp
                        Image(
                            painter = painterResource(R.drawable.img_iso_cloud_2),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth(0.127f)
                                .offset(
                                    x = (boxW * (585f / 1024f)) + cloud2Drift,
                                    y = boxH * (130f / 689f),
                                )
                                .graphicsLayer {
                                    this.translationX = rotY * 0.5f
                                    this.translationY = -rotX * 0.5f
                                },
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }

            // 上部ヘッダー (タイトル・テーマ切り替え・閉じる・リセット)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lucide_mountain),
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "3D Simulation",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2232),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 視点リセットボタン
                    IconButton(
                        onClick = {
                            rotX = 0f
                            rotY = 0f
                            panX = 0f
                            panY = 0f
                            scale = 1.0f
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.92f), CircleShape),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lucide_undo_2),
                            contentDescription = "視点リセット",
                            tint = Color(0xFF333333),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 閉じるボタン
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.92f), CircleShape),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lucide_x),
                            contentDescription = "閉じる",
                            tint = Color(0xFF333333),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // テーマ切り替えチップ
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DioramaTheme.entries.forEach { theme ->
                        val isSelected = selectedTheme == theme
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedTheme = theme
                                rotX = 0f
                                rotY = 0f
                                panX = 0f
                                panY = 0f
                                scale = 1.0f
                            },
                            label = {
                                Text(
                                    text = theme.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2E7D32),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.85f),
                                labelColor = Color(0xFF333333),
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }

            // 下部操作ヒント
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.88f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "ドラッグで 3D チルト回転 ・ ピンチでズーム",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF455A64),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
