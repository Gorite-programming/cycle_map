package com.gorite.cyclemap.utils

import com.gorite.cyclemap.R
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.gorite.cyclemap.search.formatCategoryLabel
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gorite.cyclemap.routing.RoutePreference
import com.gorite.cyclemap.ui.RoutingBenchmarkRequest
import com.gorite.cyclemap.ui.cycling.GpxHistoryEntry
import com.gorite.cyclemap.ui.cycling.NearbySpot
import com.gorite.cyclemap.ui.cycling.PoiCategory
import com.gorite.cyclemap.ui.cycling.formatKm
import org.osmdroid.util.GeoPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

// ---------------------------------------------------------------------------
// MapLayer / RouteSummary / Constants
// ---------------------------------------------------------------------------

internal enum class MapLayer(val label: String) {
    VECTOR_LIBERTY("★Libertyベクター"),
    GSI("地理院標準"),
    OSM("自転車向け"),
    TERRAIN("地形起伏"),
}

internal data class RouteSummary(val distanceMeters: Double, val stepCount: Int)

internal const val NAVIGATION_ZOOM = 16.0

// 回転式ナビ矢印・ヘディングアップ方位の調整値
internal const val SPEED_HEADING_THRESHOLD_KMH = 5.0
internal const val SPEED_HEADING_THRESHOLD_MPS = (5.0f / 3.6f) // 5.0 km/h = 1.389 m/s
internal const val NAV_ARROW_MIN_SPEED_MPS = SPEED_HEADING_THRESHOLD_MPS
internal const val NAV_ARROW_BEARING_MAX_AGE_MS = 5_000L
internal const val NAV_ARROW_LOW_ACCURACY_M = 50f
internal const val NAV_ARROW_DIM_ALPHA = 0.45f
// 連続fixから移動方向を推定する際の条件。GPS誤差に埋もれる微動はノイズとして捨てる。
internal const val NAV_COURSE_MIN_DIST_M = 5.0
internal const val NAV_COURSE_MAX_DT_MS = 10_000L

/**
 * ナビ矢印の方位管理ホルダー (Compose stateにしない。recomposeを起こさず地図invalidateのみで反映)。
 * ハードウェア bearing が無いfixでも、連続したGPS位置から移動方向 (コース) を推定して矢印を出す。
 */
internal class NavArrowCache {
    var lastFixElapsedMs: Long = 0L
    var courseDeg: Float = Float.NaN
    var courseElapsedMs: Long = 0L
    var courseSpeedMps: Float = Float.NaN
    var lastKnownBearingDeg: Float = Float.NaN
    private var baseLat = Double.NaN
    private var baseLon = Double.NaN
    private var baseElapsedMs: Long = 0L

    /** 新しいfixを食わせ、十分な移動があればコースを更新する。 */
    fun onFix(latitude: Double, longitude: Double, accuracyM: Float, nowMs: Long) {
        lastFixElapsedMs = nowMs
        if (!baseLat.isFinite()) {
            setBase(latitude, longitude, nowMs)
            return
        }
        val dtMs = nowMs - baseElapsedMs
        if (dtMs <= 0 || dtMs > NAV_COURSE_MAX_DT_MS) {
            // 基線が古すぎる (長時間静止など) → 今回を新たな基線にする
            setBase(latitude, longitude, nowMs)
            return
        }
        val distM = haversineMeters(baseLat, baseLon, latitude, longitude)
        val accuracy = if (accuracyM.isFinite()) accuracyM.toDouble() else 10.0
        // 誤差範囲内の微動ではコースを更新しない (基線は維持し、移動を蓄積する)
        if (distM >= maxOf(NAV_COURSE_MIN_DIST_M, accuracy * 2.0)) {
            courseDeg = initialBearingDeg(baseLat, baseLon, latitude, longitude).toFloat()
            courseElapsedMs = nowMs
            courseSpeedMps = (distM / dtMs * 1000.0).toFloat()
            setBase(latitude, longitude, nowMs)
        }
    }

    private fun setBase(latitude: Double, longitude: Double, nowMs: Long) {
        baseLat = latitude
        baseLon = longitude
        baseElapsedMs = nowMs
    }
}

// ナビ案内の調整値
internal const val ARRIVAL_RADIUS_METERS = 30.0
internal const val REROUTE_MIN_INTERVAL_MS = 6_000L
internal const val ROUTE_MAX_EXPANDED_NODES = 4_000_000
internal const val FALLBACK_LATITUDE = 34.1785 // 山口市役所 (GPS不通時の起点)
internal const val FALLBACK_LONGITUDE = 131.4737

/** 案内開始前の概算表示に使う想定速度。案内中のフォールバック (computeNavigationStats の既定値) と同一の15km/h。 */
internal const val PRE_START_ASSUMED_SPEED_MPS = 15_000.0 / 3_600.0

internal class RouteCalculationJob {
    private val cancelled = AtomicBoolean(false)
    var job: kotlinx.coroutines.Job? = null
    var thread: Thread? = null

    fun cancel() {
        cancelled.set(true)
        job?.cancel()
        thread?.interrupt()
    }

    /** キャンセルしてスレッド/ジョブが完全停止するまで待機する（最大 2 秒）。
     *  グラフの close() を呼ぶ前に必ず呼ぶこと。 */
    fun cancelAndJoin() {
        cancel()
        thread?.let {
            try {
                it.join(2_000L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        job?.let {
            try {
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(2_000L) {
                        it.join()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun isCancelled(): Boolean = cancelled.get() || (job?.isCancelled == true) || Thread.currentThread().isInterrupted
}

internal data class RouteRequest(
    val start: GeoPoint,
    val goal: GeoPoint,
    val isReroute: Boolean,
    val waypoints: List<GeoPoint>?,
    val preference: RoutePreference?,
)

// ---------------------------------------------------------------------------
// 小型ユーティリティ Composable
// ---------------------------------------------------------------------------

@Composable
internal fun NavStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun IconButton(
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

// ---------------------------------------------------------------------------
// ヘルパー関数
// ---------------------------------------------------------------------------

/** GPX履歴一覧を実ファイルから読み込む (ブロッキング・IOスレッドで呼ぶこと)。 */
internal fun loadGpxHistory(dataDir: File): List<GpxHistoryEntry> {
    val dir = File(dataDir, "gpx")
    if (!dir.isDirectory) return emptyList()
    return dir.listFiles { f -> f.isFile && f.name.endsWith(".gpx") }
        ?.sortedByDescending { it.lastModified() }
        ?.take(20)
        ?.map { file ->
            var points = 0
            runCatching {
                file.bufferedReader().useLines { lines ->
                    lines.forEach { line -> if (line.contains("<trkpt")) points++ }
                }
            }
            GpxHistoryEntry(
                fileName = file.name,
                dateText = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date(file.lastModified())),
                sizeKb = file.length() / 1024,
                pointCount = points,
            )
        } ?: emptyList()
}

internal fun saveRoutingBenchmarkLog(
    context: Context,
    request: RoutingBenchmarkRequest,
    resultText: String,
    samples: List<com.gorite.cyclemap.routing.MappedRoutingBenchmarkResult>,
): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val output = File(cycleMapDataDir(context), "benchmarks/hsa_${timestamp}.log")
    output.parentFile?.mkdirs()
    val details = buildString {
        appendLine("CycleMap routing benchmark")
        appendLine("timestamp=$timestamp")
        appendLine("mode=${request.mode}")
        appendLine("start=${request.startLatitude},${request.startLongitude}")
        appendLine("goal=${request.goalLatitude},${request.goalLongitude}")
        appendLine("graph=LazyMappedRoadGraph")
        appendLine("qualityGate=HSA cost <= A* cost * 1.05")
        appendLine()
        appendLine(resultText)
        appendLine()
        samples.forEach { sample ->
            appendLine(
                "${sample.algorithm}\tseconds=${sample.elapsedSeconds}\t" +
                    "heapIncreaseBytes=${sample.peakHeapIncreaseBytes}\t" +
                    "expandedNodes=${sample.expandedNodes}\t" +
                    "distanceMeters=${sample.route.totalDistanceMeters}\t" +
                    "cost=${sample.route.totalCost}\t" +
                    "segments=${sample.segmentCount}\t" +
                    "fallback=${sample.usedFallback}\t" +
                    "reason=${sample.fallbackReason ?: ""}",
            )
        }
    }
    output.writeText(details)
    Log.i("CycleMapBenchmark", "saved=${output.absolutePath}\n$resultText")
    return output
}

internal fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
internal fun PoiDetailCard(
    spot: NearbySpot,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onStartRoute: () -> Unit,
    onAddWaypoint: () -> Unit,
    onClose: () -> Unit,
    hasActiveRoute: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val cat = PoiCategory.forCategory(spot.category)
                    Surface(
                        shape = CircleShape,
                        color = Color(android.graphics.Color.parseColor(cat?.colorHex ?: "#1A73E8")),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(cat?.iconRes ?: R.drawable.ic_lucide_map_pin),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = spot.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatCategoryLabel(spot.category),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (spot.distanceM > 0.0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "・ " + formatKm(spot.distanceM),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_lucide_x),
                        contentDescription = "閉じる",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onStartRoute,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_lucide_navigation),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ここへ行く", fontWeight = FontWeight.Bold)
                }
                if (hasActiveRoute) {
                    OutlinedButton(
                        onClick = onAddWaypoint,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("経由地")
                    }
                }
                OutlinedButton(
                    onClick = onToggleFavorite,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        painterResource(if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline),
                        contentDescription = null,
                        tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isFavorite) "登録済み" else "お気に入り")
                }
            }
        }
    }
}

/**
 * 現在地精度円を描画する軽量・高信頼オーバーレイ。
 * Polygon による点の再生成や LinearRing のライフサイクル依存（detach時のnull破棄）を排除し、
 * Canvas.drawCircle によるダイレクト描画でクラッシュ耐性と描画性能を両立する。
 */
internal class AccuracyCircleOverlay : org.osmdroid.views.overlay.Overlay() {
    private var center: GeoPoint? = null
    private var accuracyMeters: Float = 0f
    private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#221A73E8")
        style = android.graphics.Paint.Style.FILL
    }
    private val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#661A73E8")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val screenPoint = android.graphics.Point()

    fun update(target: GeoPoint, accuracy: Float) {
        center = target
        accuracyMeters = accuracy
        isEnabled = accuracy in 1f..200f
    }

    override fun draw(pCanvas: android.graphics.Canvas?, pProjection: org.osmdroid.views.Projection?) {
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
