package com.gorite.cyclemap.navigation

import com.gorite.cyclemap.*
import com.gorite.cyclemap.utils.*
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.Toast
import com.gorite.cyclemap.data.MapSourceType
import com.gorite.cyclemap.data.TileDownloader
import com.gorite.cyclemap.routing.HsaOptions
import com.gorite.cyclemap.routing.LazyMappedRoadGraph
import com.gorite.cyclemap.routing.OffRouteDetector
import com.gorite.cyclemap.routing.RoutePoint
import com.gorite.cyclemap.routing.benchmarkMappedRouting
import com.gorite.cyclemap.speech.VoiceGuidanceNavigator
import com.gorite.cyclemap.ui.RoutingBenchmarkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

internal fun executeClearRoute(
    routeJobRef: AtomicReference<RouteCalculationJob?>,
    pendingRouteRef: AtomicReference<RouteRequest?>,
    mapView: MapView?,
    routeOverlay: Polyline?,
    gpxTrackOverlay: Polyline?,
    destinationMarker: Marker?,
    startMarker: Marker?,
    waypointMarkers: List<Marker>,
    offRouteDetector: OffRouteDetector,
    voiceNavigator: VoiceGuidanceNavigator,
    onResetStates: () -> Unit,
) {
    routeJobRef.getAndSet(null)?.cancel()
    pendingRouteRef.set(null)
    mapView?.let { view ->
        routeOverlay?.let { view.overlays.remove(it) }
        gpxTrackOverlay?.let { view.overlays.remove(it) }
        destinationMarker?.let { view.overlays.remove(it) }
        startMarker?.let { view.overlays.remove(it) }
        waypointMarkers.forEach { view.overlays.remove(it) }
        view.invalidate()
    }
    offRouteDetector.reset()
    voiceNavigator.reset()
    onResetStates()
}

internal fun determineNextLayer(
    context: Context,
    currentLayer: MapLayer,
): MapLayer {
    val layers = MapLayer.entries
    var nextIndex = (layers.indexOf(currentLayer) + 1) % layers.size
    if (layers[nextIndex] == MapLayer.TERRAIN) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isOnline = cm?.activeNetwork?.let { net ->
            cm.getNetworkCapabilities(net)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } ?: false
        if (!isOnline) {
            Toast.makeText(context, "陰影起伏図はオフライン未取得です（標準地図を表示します）", Toast.LENGTH_SHORT).show()
            nextIndex = (nextIndex + 1) % layers.size
        }
    }
    return layers[nextIndex]
}

internal fun executeDownloadCorridorTiles(
    context: Context,
    navigationRoute: List<RoutePoint>,
    selectedLayer: MapLayer,
    onProgressUpdate: (String) -> Unit,
    onFinished: () -> Unit,
) {
    if (navigationRoute.isEmpty()) return
    onProgressUpdate("準備中…")
    val points = navigationRoute.map { Pair(it.latitude, it.longitude) }
    val source = when (selectedLayer) {
        MapLayer.VECTOR_LIBERTY -> gsiTileSource()
        MapLayer.GSI -> gsiTileSource()
        MapLayer.OSM -> osmTileSource()
        MapLayer.TERRAIN -> gsiReliefTileSource()
    }
    val sourceType = when (selectedLayer) {
        MapLayer.VECTOR_LIBERTY -> MapSourceType.VECTOR_LIBERTY
        MapLayer.GSI -> MapSourceType.GSI
        MapLayer.OSM -> MapSourceType.OSM
        MapLayer.TERRAIN -> MapSourceType.GSI
    }
    Thread {
        try {
            TileDownloader.downloadRouteCorridor(
                context = context,
                routePoints = points,
                label = "ルート地図",
                sourceType = sourceType,
                source = source,
                minZoom = 14,
                maxZoom = 16,
                bufferTiles = 1,
                onProgress = { progress ->
                    onProgressUpdate("${progress.completed}/${progress.total}")
                },
                onDone = { total ->
                    onProgressUpdate("保存完了 (${total}枚)")
                    onFinished()
                },
            )
        } catch (e: Exception) {
            Log.e("CycleMap", "Corridor tile download failed", e)
            onProgressUpdate("失敗")
            onFinished()
        }
    }.start()
}

internal fun executeRoutingBenchmark(
    context: Context,
    scope: CoroutineScope,
    request: RoutingBenchmarkRequest,
    graphLock: ReentrantReadWriteLock,
    mappedGraph: LazyMappedRoadGraph?,
    onResult: (String) -> Unit,
    onFinished: () -> Unit,
) {
    scope.launch {
        try {
            val result = withContext(Dispatchers.Default) {
                graphLock.read {
                    mappedGraph?.let { graph ->
                        val startIndex = graph.nearestNodeIndex(request.startLatitude, request.startLongitude)
                        val goalIndex = graph.nearestNodeIndex(request.goalLatitude, request.goalLongitude)
                        benchmarkMappedRouting(
                            graph = graph,
                            startIndex = startIndex,
                            goalIndex = goalIndex,
                            options = HsaOptions.fromMode(request.mode),
                        )
                    }
                }
            }
            val sampleText = result?.joinToString("\n") { sample ->
                val memoryMb = sample.peakHeapIncreaseBytes / (1024.0 * 1024.0)
                "${sample.algorithm}: %.3fs / %.1fMB / %.1fkm".format(
                    Locale.US,
                    sample.elapsedSeconds,
                    memoryMb,
                    sample.route.totalDistanceMeters / 1_000.0,
                ) + if (sample.algorithm.startsWith("HSA")) {
                    " / segments=${sample.segmentCount} fallback=${sample.usedFallback}" +
                        sample.fallbackReason?.let { " reason=$it" }.orEmpty()
                } else {
                    ""
                } + " / expanded=${sample.expandedNodes}"
            } ?: "ルートグラフが読み込まれていません"
            val deltaText = result?.firstOrNull { it.algorithm == "A*" }?.let { aStar ->
                result.firstOrNull { it.algorithm.startsWith("HSA") }?.let { hsa ->
                    "差分: 時間 %+.3fs / メモリ %+.1fMB / 距離 %+.1fkm / 展開 %+.0f".format(
                        Locale.US,
                        hsa.elapsedSeconds - aStar.elapsedSeconds,
                        (hsa.peakHeapIncreaseBytes - aStar.peakHeapIncreaseBytes) / (1024.0 * 1024.0),
                        (hsa.route.totalDistanceMeters - aStar.route.totalDistanceMeters) / 1_000.0,
                        (hsa.expandedNodes - aStar.expandedNodes).toDouble(),
                    )
                }
            }
            val resultText = listOfNotNull(sampleText, deltaText).joinToString("\n")
            val logPath = result?.let { samples ->
                saveRoutingBenchmarkLog(context, request, resultText, samples)
            }
            val finalOutput = if (logPath != null) {
                "$resultText\nログ保存: ${logPath.name}"
            } else {
                resultText
            }
            onResult(finalOutput)
        } catch (t: Throwable) {
            Log.e("CycleMapBenchmark", "Benchmark failed", t)
            onResult("ベンチマーク失敗: ${t.message}")
        } finally {
            onFinished()
        }
    }
}
