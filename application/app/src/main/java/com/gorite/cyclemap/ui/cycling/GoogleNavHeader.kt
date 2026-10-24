package com.gorite.cyclemap.ui.cycling

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.navigation.NavInstruction
import com.gorite.cyclemap.R
import com.gorite.cyclemap.routing.IconResolver
import com.gorite.cyclemap.routing.RouteInstruction
import com.gorite.cyclemap.ui.theme.Motion
import kotlin.math.roundToInt

/**
 * Google Maps スタイルのナビゲーション案内ヘッダー。
 * 深緑背景 (#0F9D58)、極大白文字マニューバ案内文、高コントラスト。
 * 横画面時は幅を制限したコンパクトカードとして表示し、地図視界を確保する。
 */
@Composable
internal fun GoogleNavHeader(
    currentGuide: NavInstruction?,
    distanceMeters: Double?,
    nextAfterThis: RouteInstruction?,
    distanceToNextAfterThis: Double?,
    isRerouting: Boolean,
    isOffRoute: Boolean,
    autoRerouteEnabled: Boolean,
    effectiveAnimationEnabled: Boolean,
    isLandscape: Boolean = false,
    statusBarTopPadding: androidx.compose.ui.unit.Dp = 0.dp,
    notificationBannerGroup: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = if (isLandscape) {
            modifier.widthIn(max = 380.dp)
        } else {
            modifier.fillMaxWidth()
        },
        horizontalAlignment = if (isLandscape) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.25f
        val rerouteColor = if (isDarkTheme) Color(0xFF0D652D) else Color(0xFF1E8E3E)
        val navCardColor = if (isDarkTheme) Color(0xFF0D652D) else Color(0xFF0F9D58)

        // 1. リルート中インジケータ
        AnimatedVisibility(
            visible = isRerouting,
            enter = Motion.bannerEnter(effectiveAnimationEnabled),
            exit = Motion.bannerExit(effectiveAnimationEnabled),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = rerouteColor),
                shape = if (isLandscape) RoundedCornerShape(16.dp) else RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = if (isLandscape) 12.dp else (statusBarTopPadding + 12.dp),
                            bottom = 12.dp,
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "ルートを再探索中…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }

        // 2. マニューバ案内カード (深緑 Google Nav パネル: 夜間 #0D652D / 昼間 #0F9D58)
        if (!isRerouting && currentGuide != null && distanceMeters != null) {
            AnimatedContent(
                targetState = currentGuide,
                transitionSpec = { Motion.bannerContentTransform(effectiveAnimationEnabled) },
                label = "GoogleNavGuideTransition",
                modifier = Modifier.fillMaxWidth(),
            ) { guide ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = navCardColor),
                    shape = if (isLandscape) RoundedCornerShape(16.dp) else RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = if (isLandscape) 10.dp else (statusBarTopPadding + 10.dp),
                                bottom = if (isLandscape) 10.dp else 14.dp,
                            ),
                    ) {
                        com.gorite.cyclemap.ui.map.NextFlowAnimation(
                            animationEnabled = effectiveAnimationEnabled,
                            fadeColor = navCardColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                        // 進行方向アイコン
                        Icon(
                            painter = painterResource(guide.arrowIcon),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .padding(end = if (isLandscape) 10.dp else 14.dp)
                                .size(if (isLandscape) 36.dp else 48.dp),
                        )

                        // 距離 & 案内テキスト
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.Start,
                            ) {
                                Text(
                                    text = formatGuideDistance(distanceMeters),
                                    fontSize = if (isLandscape) 20.sp else 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "先",
                                    fontSize = if (isLandscape) 12.sp else 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                            }

                            Text(
                                text = guide.text,
                                fontSize = if (isLandscape) 15.sp else if (guide.text.length > 14) 17.sp else 20.sp,
                                lineHeight = if (isLandscape) 19.sp else if (guide.text.length > 14) 22.sp else 25.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            // 2つ先の案内
                            nextAfterThis?.let { nextStep ->
                                val label = IconResolver.getLabel(nextStep.type, nextStep.turnAngleDegrees)
                                val distText = distanceToNextAfterThis?.let { formatGuideDistance(it) } ?: ""
                                Text(
                                    text = "その先 $distText: $label",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

        // 3. ルート逸脱バナー (MED-10: ステータスバーと重ならないよう上部パディング考慮)
        val hasTopCard = isRerouting || currentGuide != null
        AnimatedVisibility(
            visible = isOffRoute && !isRerouting,
            enter = Motion.bannerEnter(effectiveAnimationEnabled),
            exit = Motion.bannerExit(effectiveAnimationEnabled),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD93025)),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (!hasTopCard && !isLandscape) statusBarTopPadding else 0.dp),
            ) {
                Text(
                    text = if (!autoRerouteEnabled) "ルートから外れています（自動リルートOFF）" else "ルートから外れています",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        // 4. GPS警告などの通知バナースタック
        notificationBannerGroup?.invoke()
    }
}

private fun formatGuideDistance(meters: Double): String {
    return if (meters >= 1000.0) {
        String.format(java.util.Locale.US, "%.1fkm", meters / 1000.0)
    } else {
        "${meters.roundToInt()}m"
    }
}
