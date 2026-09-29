package com.gorite.cyclemap.ui.cycling

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 標高グラフ (横軸=距離, 縦軸=標高)。
 * Canvas直接描画で軽量にし、再描画はプロファイル・進捗更新時のみ。
 * ダークモードは呼び出し側の配色で追従する。
 */
@Composable
fun ElevationProfileChart(
    profile: List<ElevationSample>,
    totalDistanceM: Double,
    progressDistanceM: Double?,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    passedColor: Color = MaterialTheme.colorScheme.tertiary,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val measurer = rememberTextMeasurer()
    val points = remember(profile) { profile }
    if (points.size < 2 || totalDistanceM <= 0.0) return

    val minElev = remember(points) { points.minOf { it.elevationM } }
    val maxElev = remember(points) { points.maxOf { it.elevationM } }
    val span = (maxElev - minElev).coerceAtLeast(10.0)
    // 上下に余白を取った表示レンジ
    val lo = minElev - span * 0.1
    val hi = maxElev + span * 0.15

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
    ) {
        val leftPad = 44.dp.toPx()
        val rightPad = 8.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val w = size.width - leftPad - rightPad
        val h = size.height - topPad - bottomPad
        if (w <= 0f || h <= 0f) return@Canvas

        fun xOf(d: Double) = leftPad + (d / totalDistanceM * w).toFloat().coerceIn(0f, w)
        fun yOf(e: Double) = (topPad + (1 - (e - lo) / (hi - lo)) * h).toFloat()

        // 横グリッド + 標高ラベル (3本)
        val labelStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 10.sp,
            color = labelColor,
        )
        listOf(0.0, 0.5, 1.0).forEach { f ->
            val e = lo + (hi - lo) * f
            val y = yOf(e)
            drawLine(gridColor, Offset(leftPad, y), Offset(leftPad + w, y), strokeWidth = 1f)
            drawText(
                measurer,
                "${e.roundToInt()}m",
                style = labelStyle,
                topLeft = Offset(0f, y - 8.dp.toPx()),
            )
        }

        // 距離ラベル (0 / 中間 / 合計)
        val kmTotal = totalDistanceM / 1000.0
        val midKm = kmTotal / 2
        drawText(measurer, "0km", style = labelStyle, topLeft = Offset(leftPad, topPad + h + 4.dp.toPx()))
        drawText(
            measurer,
            "${"%.1f".format(Locale.US, midKm)}km",
            style = labelStyle,
            topLeft = Offset(leftPad + w / 2 - 14.dp.toPx(), topPad + h + 4.dp.toPx()),
        )
        val totalLabel = "${"%.1f".format(Locale.US, kmTotal)}km"
        drawText(
            measurer,
            totalLabel,
            style = labelStyle,
            topLeft = Offset(leftPad + w - 30.dp.toPx(), topPad + h + 4.dp.toPx()),
        )

        // 全体エリア + ライン
        val area = Path().apply {
            moveTo(xOf(0.0), yOf(points.first().elevationM))
            points.forEach { lineTo(xOf(it.distanceM), yOf(it.elevationM)) }
            lineTo(xOf(totalDistanceM), topPad + h)
            lineTo(xOf(0.0), topPad + h)
            close()
        }
        drawPath(area, lineColor.copy(alpha = 0.18f))
        val line = Path().apply {
            moveTo(xOf(0.0), yOf(points.first().elevationM))
            points.forEach { lineTo(xOf(it.distanceM), yOf(it.elevationM)) }
        }
        drawPath(line, lineColor, style = Stroke(width = 2.5.dp.toPx()))

        // 走行済み区間の強調 + 現在位置
        progressDistanceM?.let { prog ->
            val p = prog.coerceIn(0.0, totalDistanceM)
            val doneLine = Path().apply {
                moveTo(xOf(0.0), yOf(points.first().elevationM))
                points.filter { it.distanceM <= p }.forEach { lineTo(xOf(it.distanceM), yOf(it.elevationM)) }
                elevationAt(points, p)?.let { lineTo(xOf(p), yOf(it)) }
            }
            drawPath(doneLine, passedColor, style = Stroke(width = 4.dp.toPx()))
            val cx = xOf(p)
            drawLine(
                passedColor,
                Offset(cx, topPad),
                Offset(cx, topPad + h),
                strokeWidth = 1.5.dp.toPx(),
            )
            elevationAt(points, p)?.let { e ->
                val cy = yOf(e)
                // 現在地ピン (外枠リング + 白芯 + 標高吹き出し)
                drawCircle(passedColor.copy(alpha = 0.35f), radius = 9.dp.toPx(), center = Offset(cx, cy))
                drawCircle(passedColor, radius = 6.dp.toPx(), center = Offset(cx, cy))
                drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(cx, cy))
                
                // 標高バブル数値
                val elevText = "${e.roundToInt()}m"
                drawText(
                    measurer,
                    elevText,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 10.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = Color.White,
                    ),
                    topLeft = Offset((cx - 12.dp.toPx()).coerceIn(leftPad, leftPad + w - 30.dp.toPx()), (cy - 18.dp.toPx()).coerceAtLeast(topPad)),
                )
            }
        }
    }
}
