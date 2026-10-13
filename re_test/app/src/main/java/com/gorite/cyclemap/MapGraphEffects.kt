package com.gorite.cyclemap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.gorite.cyclemap.data.RoutingGraphSelection
import com.gorite.cyclemap.data.RoutingGraphSelector
import com.gorite.cyclemap.routing.LazyMappedRoadGraph
import com.gorite.cyclemap.routing.RoutePreference
import com.gorite.cyclemap.tracking.LocationTrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

@Composable
internal fun MapGraphEffects(
    context: Context,
    scope: CoroutineScope,
    routeJobRef: AtomicReference<RouteCalculationJob?>,
    graphLock: ReentrantReadWriteLock,
    pendingRouteRef: AtomicReference<RouteRequest?>,
    switchRef: AtomicReference<((RoutingGraphSelection.Available) -> Unit)?>,
    mappedGraph: LazyMappedRoadGraph?,
    onMappedGraphChanged: (LazyMappedRoadGraph?) -> Unit,
    loadedGraphName: String?,
    onLoadedGraphNameChanged: (String?) -> Unit,
    isSwitchingGraph: Boolean,
    onSwitchingGraphChanged: (Boolean) -> Unit,
    onCalculatingRouteChanged: (Boolean) -> Unit,
    onReroutingChanged: (Boolean) -> Unit,
    onWarningMessageChanged: (String?) -> Unit,
    currentLocation: Location?,
    onLocationUpdate: ((Location) -> Unit)?,
    runRouteCalculation: (start: GeoPoint, goal: GeoPoint, isReroute: Boolean, waypointsOverride: List<GeoPoint>?, preferenceOverride: RoutePreference?) -> Unit,
    onGpxRecordingStarted: () -> Unit,
    onGpxRecordingSaved: (points: Int, fileName: String) -> Unit,
) {
    /**
     * 県グラフ切替。routing要求時 (runRouteCalculation) と現在地更新時 (先読み) から呼ばれる。
     */
    val startGraphSwitch: (RoutingGraphSelection.Available) -> Unit = switch@{ selection ->
        if (isSwitchingGraph || loadedGraphName == selection.fileName) return@switch
        val dataDir = cycleMapDataDir(context)
        val resolvedFiles = RoutingGraphSelector.resolveGraphFiles(dataDir, selection.fileName)
        if (resolvedFiles == null) {
            pendingRouteRef.set(null)
            onWarningMessageChanged("${selection.prefectureName}のグラフがありません:\n${selection.fileName}\n配置先: ${dataDir.absolutePath}")
            Log.e(
                "CycleMapRoute",
                "ROUTE_FAILURE reason=graph_not_available prefecture=${selection.prefectureName} file=${selection.fileName}",
            )
            return@switch
        }
        val (graphFile, graphIndex) = resolvedFiles
        onSwitchingGraphChanged(true)
        onCalculatingRouteChanged(false)
        onReroutingChanged(false)
        Log.i("CycleMapRoute", "GRAPH_SWITCH_BEGIN to=${selection.fileName} prefecture=${selection.prefectureName}")
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    routeJobRef.getAndSet(null)?.cancelAndJoin()
                    val t0 = SystemClock.elapsedRealtime()
                    val g = LazyMappedRoadGraph.load(graphFile, graphIndex)
                    val elapsed = SystemClock.elapsedRealtime() - t0
                    Pair(g, elapsed)
                }
                val previous = loadedGraphName
                graphLock.write { onMappedGraphChanged(loaded.first) }
                onLoadedGraphNameChanged(selection.fileName)
                Log.i(
                    "CycleMapRoute",
                    "GRAPH_SWITCH from=${previous ?: "(none)"} to=${selection.fileName} " +
                        "prefecture=${selection.prefectureName} loadMs=${loaded.second} " +
                        "nodes=${loaded.first.nodeCount} edges=${loaded.first.edgeCount}",
                )
                pendingRouteRef.getAndSet(null)?.let { req ->
                    runRouteCalculation(req.start, req.goal, req.isReroute, req.waypoints, req.preference)
                }
            } catch (t: Throwable) {
                pendingRouteRef.set(null)
                onWarningMessageChanged("${selection.prefectureName}のグラフを読み込めませんでした: ${t.message}")
                Log.e(
                    "CycleMapRoute",
                    "ROUTE_FAILURE reason=graph-load-failed file=${selection.fileName} " +
                        "exception=${t::class.java.simpleName}:${t.message}",
                )
            } finally {
                onSwitchingGraphChanged(false)
            }
        }
    }
    switchRef.set(startGraphSwitch)

    // Load graph & tile check (起動時: 配置済み最初のグラフ。現在地が他県なら後続の位置効果で切替)。
    LaunchedEffect(Unit) {
        val dataDir = cycleMapDataDir(context)
        configureOsmdroid(context, dataDir)
        val resolvedFiles = RoutingGraphSelector.findFirstAvailableGraph(dataDir)
        if (resolvedFiles == null) {
            onWarningMessageChanged("ルートグラフが見つかりません\n配置先: ${dataDir.absolutePath}")
            Log.e("CycleMapRoute", "GRAPH_SELECT startup loaded=false reason=missing dir=${dataDir.absolutePath}")
        } else {
            val (graphFile, graphIndex) = resolvedFiles
            onSwitchingGraphChanged(true)
            try {
                val result = withContext(Dispatchers.IO) {
                    val before = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                    val start = SystemClock.elapsedRealtime()
                    val graph = LazyMappedRoadGraph.load(graphFile, graphIndex)
                    val elapsed = SystemClock.elapsedRealtime() - start
                    val after = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                    Triple(graph, elapsed, after.totalPss - before.totalPss)
                }
                onMappedGraphChanged(result.first)
                onLoadedGraphNameChanged(graphFile.name)
                Log.i("CycleMapGraph", "mappedLoadMs=${result.second} pssDeltaKb=${result.third} nodes=${result.first.nodeCount} edges=${result.first.edgeCount}")
                Log.i("CycleMapRoute", "GRAPH_SELECT startup file=${graphFile.name} loaded=true nodes=${result.first.nodeCount}")
            } catch (t: Throwable) {
                onWarningMessageChanged("ルートグラフを読み込めませんでした: ${t.message}")
                Log.e("CycleMapRoute", "ROUTE_FAILURE reason=graph-load-failed file=${graphFile.name} exception=${t::class.java.simpleName}:${t.message}")
            } finally {
                onSwitchingGraphChanged(false)
            }
        }
    }

    // 現在地の県に応じた先読み切替
    LaunchedEffect(currentLocation) {
        val loc = currentLocation ?: return@LaunchedEffect
        onLocationUpdate?.invoke(loc)
        if (isSwitchingGraph) return@LaunchedEffect
        when (val sel = RoutingGraphSelector.select(loc.latitude, loc.longitude)) {
            is RoutingGraphSelection.Available -> {
                if (sel.fileName != loadedGraphName && (mappedGraph != null || loadedGraphName == null)) {
                    Log.i(
                        "CycleMapRoute",
                        "GRAPH_SELECT location=${loc.latitude},${loc.longitude} prefecture=${sel.prefectureName} " +
                            "file=${sel.fileName} trigger=location-update",
                    )
                    switchRef.get()?.invoke(sel)
                }
            }
            is RoutingGraphSelection.Unavailable -> {
                // routing要求時に graph_not_available で明示失敗させる
            }
        }
    }

    DisposableEffect(mappedGraph) {
        val graphToDispose = mappedGraph
        onDispose {
            val job = routeJobRef.getAndSet(null)
            job?.cancel()
            graphLock.write { graphToDispose?.close() }
        }
    }

    // GPX BroadcastReceiver
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    LocationTrackingService.ACTION_RECORDING_STARTED -> {
                        onGpxRecordingStarted()
                    }
                    LocationTrackingService.ACTION_RECORDING_SAVED -> {
                        val path = intent.getStringExtra(LocationTrackingService.EXTRA_PATH).orEmpty()
                        val points = intent.getIntExtra(LocationTrackingService.EXTRA_POINTS, 0)
                        val fileName = File(path).name
                        onGpxRecordingSaved(points, fileName)
                    }
                }
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(LocationTrackingService.ACTION_RECORDING_STARTED)
                addAction(LocationTrackingService.ACTION_RECORDING_SAVED)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
}
