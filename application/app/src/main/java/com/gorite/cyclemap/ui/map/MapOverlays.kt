package com.gorite.cyclemap.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

@Composable
fun NavStatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * 現在地精度円オーバーレイ (osmdroid 互換用)
 */
class AccuracyCircleOverlay : Overlay() {
    private var center: GeoPoint? = null
    private var accuracyMeters: Float = 0f
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#221A73E8")
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#661A73E8")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val screenPoint = Point()

    fun update(target: GeoPoint, accuracy: Float) {
        center = target
        accuracyMeters = accuracy
        isEnabled = accuracy in 1f..200f
    }

    override fun draw(pCanvas: Canvas?, pProjection: Projection?) {
        if (!isEnabled || pCanvas == null || pProjection == null) return
        val c = center ?: return
        pProjection.toPixels(c, screenPoint)
        val radiusPx = pProjection.metersToPixels(accuracyMeters)
        if (radiusPx in 1f..10000f) {
            pCanvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), radiusPx, fillPaint)
            pCanvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), radiusPx, strokePaint)
        }
    }
}
