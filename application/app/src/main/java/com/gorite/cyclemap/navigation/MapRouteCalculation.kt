package com.gorite.cyclemap.navigation

import com.gorite.cyclemap.*
import com.gorite.cyclemap.utils.*
import android.content.Context
import android.graphics.Paint
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.gorite.cyclemap.data.RoutingGraphSelection
import com.gorite.cyclemap.data.RoutingGraphSelector
import com.gorite.cyclemap.data.findPrefectureName
import com.gorite.cyclemap.routing.LazyMappedRoadGraph
import com.gorite.cyclemap.routing.Maneuver
import com.gorite.cyclemap.routing.OffRouteDetector
import com.gorite.cyclemap.routing.RouteInstruction
import com.gorite.cyclemap.routing.RoutePoint
import com.gorite.cyclemap.routing.RoutePreference
import com.gorite.cyclemap.routing.extractManeuvers
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

internal class RouteExecutionContext(
    val context: Context,
    val routeJobRef: AtomicReference<RouteCalculationJob?>,
    val graphLock: ReentrantReadWriteLock,
    val pendingRouteRef: AtomicReference<RouteRequest?>,
    val getGraph: () -> LazyMappedRoadGraph?,
    val getLoadedGraphName: () -> String?,
    val isSwitchingGraph: () -> Boolean,
    val onSwitchGraph: (RoutingGraphSelection.Available) -> Unit,
    val getMapView: () -> MapView?,
    val getDestMarker: () -> Marker?,
    val getStartMarker: () -> Marker?,
    val getWaypointMarkers: () -> List<Marker>,
    val getRouteOverlay: () -> Polyline?,
    val getWaypoints: () -> List<GeoPoint>,
    val getRoutePreference: () -> RoutePreference,
    val offRouteDetector: OffRouteDetector,
    val onDestinationChanged: (GeoPoint) -> Unit,
    val onWaypointsChanged: (List<GeoPoint>) -> Unit,
    val onStartMarkerChanged: (Marker?) -> Unit,
    val onDestMarkerChanged: (Marker?) -> Unit,
    val onWaypointMarkersChanged: (List<Marker>) -> Unit,
    val onCalculatingRouteChanged: (Boolean) -> Unit,
    val onReroutingChanged: (Boolean) -> Unit,
    val onWarningMessageChanged: (String?) -> Unit,
    val onRouteCalculated: (
        overlay: Polyline?,
        route: List<RoutePoint>,
        maneuvers: List<Maneuver>,
        instructions: List<RouteInstruction>,
        summary: RouteSummary,
        isReroute: Boolean,
    ) -> Unit,
    val onRouteOptionsCalculated: ((List<com.gorite.cyclemap.contract.RouteOption>) -> Unit)? = null,
)

internal fun executeRouteCalculation(
    env: RouteExecutionContext,
    start: GeoPoint,
    goal: GeoPoint,
    isReroute: Boolean,
    waypointsOverride: List<GeoPoint>?,
    preferenceOverride: RoutePreference?,
) {
    env.routeJobRef.getAndSet(null)?.cancel()
    if (!isReroute) {
        env.onDestinationChanged(goal)
    }
    if (waypointsOverride != null) {
        env.onWaypointsChanged(waypointsOverride)
    }
    env.onCalculatingRouteChanged(true)
    if (isReroute) {
        env.onReroutingChanged(true)
    }
    val graph = env.getGraph()
    Log.i(
        "CycleMapRoute",
        "ROUTE_REQUEST isReroute=$isReroute start=${start.latitude},${start.longitude} " +
            "goal=${goal.latitude},${goal.longitude} graphLoaded=${graph != null}",
    )
    when (val selection = RoutingGraphSelector.select(start.latitude, start.longitude)) {
        is RoutingGraphSelection.Unavailable -> {
            env.onCalculatingRouteChanged(false)
            env.onReroutingChanged(false)
            val prefLabel = selection.prefectureName ?: "判定不能地域"
            env.onWarningMessageChanged("${prefLabel}のルートグラフがありません")
            Log.e("CycleMapRoute", "ROUTE_FAILURE reason=graph_not_available prefecture=$prefLabel")
            return
        }
        is RoutingGraphSelection.Available -> {
            val goalPref = findPrefectureName(goal.latitude, goal.longitude)
            Log.i(
                "CycleMapRoute",
                "GRAPH_SELECT location=${start.latitude},${start.longitude} " +
                    "prefecture=${selection.prefectureName} file=${selection.fileName} " +
                    "loaded=${env.getLoadedGraphName()} goalPrefecture=${goalPref ?: "(unknown)"}",
            )
            if (goalPref != null && goalPref != selection.prefectureName) {
                Log.w(
                    "CycleMapRoute",
                    "CROSS_PREFECTURE note=unsupported startPref=${selection.prefectureName} goalPref=$goalPref",
                )
            }
            if (env.getLoadedGraphName() != selection.fileName || graph == null) {
                env.pendingRouteRef.set(RouteRequest(start, goal, isReroute, waypointsOverride, preferenceOverride))
                if (!env.isSwitchingGraph()) env.onSwitchGraph(selection)
                env.onCalculatingRouteChanged(false)
                env.onReroutingChanged(false)
                env.onWarningMessageChanged("${selection.prefectureName}のグラフに切替中です")
                Log.i(
                    "CycleMapRoute",
                    "ROUTE_FAILURE reason=graph-switching prefecture=${selection.prefectureName} " +
                        "file=${selection.fileName}",
                )
                return
            }
        }
    }

    val currentWaypoints = waypointsOverride ?: env.getWaypoints()
    if (!isReroute) {
        (env.context as? ComponentActivity)?.runOnUiThread {
            val view = env.getMapView() ?: return@runOnUiThread
            env.getDestMarker()?.let { view.overlays.remove(it) }
            env.getStartMarker()?.let { view.overlays.remove(it) }
            env.getWaypointMarkers().forEach { view.overlays.remove(it) }
            val startM = Marker(view).apply {
                position = start
                title = "スタート"
                icon = startPointDrawable(env.context)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            view.overlays.add(startM)
            env.onStartMarkerChanged(startM)

            val wpMarkers = currentWaypoints.mapIndexed { idx, wp ->
                Marker(view).apply {
                    position = wp
                    title = "経由地 ${idx + 1}"
                    icon = waypointDrawable(env.context, idx + 1)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
            }
            wpMarkers.forEach { view.overlays.add(it) }
            env.onWaypointMarkersChanged(wpMarkers)

            val destM = Marker(view).apply {
                position = goal
                title = "目的地"
                icon = ContextCompat.getDrawable(env.context, R.drawable.ic_destination)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            view.overlays.add(destM)
            env.onDestMarkerChanged(destM)
            view.controller.animateTo(goal)
            view.invalidate()
        }
    }

    val routeJob = RouteCalculationJob()
    env.routeJobRef.set(routeJob)
    val jobGraphName = env.getLoadedGraphName()
    val jobGraph = graph
    routeJob.job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            if (routeJob.isCancelled()) return@launch

            val stops = ArrayList<GeoPoint>().apply {
                add(start)
                addAll(currentWaypoints)
                add(goal)
            }
            val allNodeIds = ArrayList<Long>()
            val allCoordinates = ArrayList<Pair<Double, Double>>()
            var totalDist = 0.0
            var totalCostVal = 0.0
            var isAllReachable = true
            var isAnyTruncated = false
            var firstStartIndex = -1
            var lastGoalIndex = -1

            val pref = preferenceOverride ?: env.getRoutePreference()
            for (i in 0 until stops.size - 1) {
                if (routeJob.isCancelled()) return@launch
                val legStart = stops[i]
                val legGoal = stops[i + 1]
                val legResult = env.graphLock.read {
                    if (routeJob.isCancelled()) return@launch
                    val startIndex = graph.nearestNodeIndex(legStart.latitude, legStart.longitude)
                    if (routeJob.isCancelled()) return@launch
                    val goalIndex = graph.nearestNodeIndex(legGoal.latitude, legGoal.longitude)
                    if (i == 0) firstStartIndex = startIndex
                    if (i == stops.size - 2) lastGoalIndex = goalIndex
                    Log.i(
                        "CycleMapRoute",
                        "ROUTE_SNAP leg=$i startNode=$startIndex goalNode=$goalIndex pref=$pref",
                    )
                    if (routeJob.isCancelled()) return@launch
                    graph.route(
                        startIndex,
                        goalIndex,
                        isCancelled = { routeJob.isCancelled() },
                        maxExpandedNodes = ROUTE_MAX_EXPANDED_NODES,
                        preference = pref,
                    )
                }
                if (routeJob.isCancelled() || legResult.wasCancelled) return@launch
                if (!legResult.isReachable) {
                    isAllReachable = false
                    break
                }
                if (legResult.wasTruncated) isAnyTruncated = true
                totalDist += legResult.totalDistanceMeters
                totalCostVal += legResult.totalCost

                if (allCoordinates.isEmpty()) {
                    allCoordinates.addAll(legResult.coordinates)
                    allNodeIds.addAll(legResult.nodeIds)
                } else {
                    if (legResult.coordinates.isNotEmpty()) {
                        allCoordinates.addAll(legResult.coordinates.drop(1))
                    }
                    if (legResult.nodeIds.isNotEmpty()) {
                        allNodeIds.addAll(legResult.nodeIds.drop(1))
                    }
                }
            }

            val result = com.gorite.cyclemap.routing.MappedRouteResult(
                nodeIds = if (isAllReachable) allNodeIds else emptyList(),
                totalDistanceMeters = totalDist,
                totalCost = totalCostVal,
                coordinates = if (isAllReachable) allCoordinates else emptyList(),
                wasCancelled = false,
                wasTruncated = isAnyTruncated,
            )
            if (routeJob.isCancelled()) return@launch
            if (result.wasCancelled) return@launch

            val routeOptions = if (!isReroute && result.isReachable) {
                env.graphLock.read {
                    if (routeJob.isCancelled()) emptyList()
                    else {
                        val planner = com.gorite.cyclemap.real.RealRoutePlanner(graphProvider = { jobGraph })
                        planner.buildOptionsFromPrimary(
                            lazyGraph = jobGraph,
                            startIndex = if (currentWaypoints.isEmpty()) firstStartIndex else -1,
                            goalIndex = if (currentWaypoints.isEmpty()) lastGoalIndex else -1,
                            primaryResult = result,
                            travelMode = com.gorite.cyclemap.contract.TravelModeType.Bicycle,
                            isCancelled = { routeJob.isCancelled() },
                            maxExpandedNodes = ROUTE_MAX_EXPANDED_NODES,
                        )
                    }
                }
            } else {
                emptyList()
            }

            Log.i(
                "CycleMapRoute",
                "ROUTE_RESULT pref=$pref success=${result.isReachable} truncated=${result.wasTruncated} " +
                    "routeNodeCount=${result.nodeIds.size} routeDistanceM=${result.totalDistanceMeters.toInt()} " +
                    "routeCost=${"%.2f".format(java.util.Locale.US, result.totalCost)} pathHash=${result.nodeIds.hashCode()}",
            )
            val richInstructions = env.graphLock.read {
                if (routeJob.isCancelled()) return@read emptyList<RouteInstruction>()
                try {
                    buildRouteInstructions(graph, result.nodeIds, result.coordinates)
                } catch (t: Throwable) {
                    Log.w("CycleMapInstruction", "rich instruction build failed, using maneuvers", t)
                    emptyList()
                }
            }

            (env.context as? ComponentActivity)?.runOnUiThread {
                if (routeJob.isCancelled() || env.routeJobRef.get() !== routeJob) return@runOnUiThread
                if (env.getLoadedGraphName() != jobGraphName || env.getGraph() !== jobGraph) {
                    Log.e("CycleMapRoute", "ROUTE_FAILURE reason=graph-changed jobGraph=$jobGraphName now=${env.getLoadedGraphName()}")
                    env.onCalculatingRouteChanged(false)
                    env.onReroutingChanged(false)
                    env.routeJobRef.compareAndSet(routeJob, null)
                    return@runOnUiThread
                }
                if (result.wasTruncated) {
                    Log.e("CycleMapRoute", "ROUTE_FAILURE reason=truncated-expanded-nodes")
                    env.onWarningMessageChanged("探索範囲が大きすぎて打ち切りました。近めの目的地でお試しください")
                    env.onCalculatingRouteChanged(false)
                    env.onReroutingChanged(false)
                    env.routeJobRef.compareAndSet(routeJob, null)
                    return@runOnUiThread
                }
                if (!result.isReachable) {
                    Log.e("CycleMapRoute", "ROUTE_FAILURE reason=unreachable isReroute=$isReroute")
                    env.onWarningMessageChanged(
                        if (isReroute) {
                            "再探索で有効なルートが見つかりませんでした。元のルートを維持します"
                        } else {
                            "有効な自転車ルートが見つかりませんでした"
                        },
                    )
                    env.onCalculatingRouteChanged(false)
                    env.onReroutingChanged(false)
                    env.routeJobRef.compareAndSet(routeJob, null)
                    return@runOnUiThread
                }
                val activity = env.context as? ComponentActivity
                if (activity?.isFinishing == true || activity?.isDestroyed == true) {
                    Log.e(
                        "CycleMapRoute",
                        "ROUTE_FAILURE reason=activity-not-ready finishing=${activity?.isFinishing} destroyed=${activity?.isDestroyed}",
                    )
                    env.onCalculatingRouteChanged(false)
                    env.onReroutingChanged(false)
                    env.routeJobRef.compareAndSet(routeJob, null)
                    return@runOnUiThread
                }
                env.onCalculatingRouteChanged(false)
                env.onReroutingChanged(false)
                env.onWarningMessageChanged(null)
                env.routeJobRef.compareAndSet(routeJob, null)

                val view = env.getMapView()
                val polyline: Polyline? = if (view != null && view.isAttachedToWindow) {
                    env.getRouteOverlay()?.let { view.overlays?.remove(it) }
                    Polyline(view).apply {
                        setPoints(result.coordinates.map { (lat, lon) -> GeoPoint(lat, lon) })
                        outlinePaint.color = android.graphics.Color.parseColor("#333333")
                        outlinePaint.strokeWidth = 24f
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.isAntiAlias = true
                    }.also {
                        view.overlays.add(it)
                        view.invalidate()
                    }
                } else {
                    null
                }

                val navigationRoute = result.coordinates.map { (lat, lon) -> RoutePoint(lat, lon) }
                val navigationManeuvers = extractManeuvers(navigationRoute)
                val routeSummary = RouteSummary(result.totalDistanceMeters, result.nodeIds.size - 1)

                env.onRouteOptionsCalculated?.invoke(routeOptions)

                env.onRouteCalculated(
                    polyline,
                    navigationRoute,
                    navigationManeuvers,
                    richInstructions,
                    routeSummary,
                    isReroute,
                )

                Log.i(
                    "CycleMapRoute",
                    "ROUTE_DISPLAYED graph=$jobGraphName polylinePointCount=${result.coordinates.size} " +
                        "instructionCount=${richInstructions.size} maneuverCount=${navigationManeuvers.size} " +
                        "routeDisplayed=true",
                )
            }
        } catch (t: Throwable) {
            if (routeJob.isCancelled()) return@launch
            Log.e("CycleMapGraph", "route failed", t)
            Log.e("CycleMapRoute", "ROUTE_FAILURE reason=exception exception=${t::class.java.simpleName}:${t.message}")
            (env.context as? ComponentActivity)?.runOnUiThread {
                if (routeJob.isCancelled() || env.routeJobRef.get() !== routeJob) return@runOnUiThread
                env.onWarningMessageChanged("ルート探索失敗: ${t.message}")
                env.onCalculatingRouteChanged(false)
                env.onReroutingChanged(false)
                env.routeJobRef.compareAndSet(routeJob, null)
            }
        } finally {
            (env.context as? ComponentActivity)?.runOnUiThread {
                if (env.routeJobRef.get() === routeJob) {
                    env.routeJobRef.compareAndSet(routeJob, null)
                    env.onCalculatingRouteChanged(false)
                    env.onReroutingChanged(false)
                }
            }
        }
    }
}
