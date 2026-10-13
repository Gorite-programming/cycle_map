package com.gorite.cyclemap

import android.Manifest
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.gorite.cyclemap.routing.RoutePoint
import com.gorite.cyclemap.routing.RoutePreference
import com.gorite.cyclemap.routing.calculateRouteProgress
import com.gorite.cyclemap.ui.RoutingBenchmarkRequest
import com.gorite.cyclemap.ui.theme.ThemeMode
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

// ---------------------------------------------------------------------------
// MapScreen Composable
// ---------------------------------------------------------------------------

@Composable
internal fun MapScreen(
    modifier: Modifier = Modifier,
    systemInsets: PaddingValues = PaddingValues(),
    themeMode: ThemeMode = ThemeMode.AUTO,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    isDarkTheme: Boolean = false,
    onLocationUpdate: ((android.location.Location) -> Unit)? = null,
    onNavigationStateChanged: (Boolean) -> Unit = {},
) {
    val state = rememberMapScreenState(
        onNavigationStateChanged = onNavigationStateChanged,
    )

    val orientationAnimator = remember {
        MapOrientationAnimator(
            getMapView = { state.mapViewState.value },
            getVectorMap = { state.vectorMapState.value },
            onOrientationUpdated = { state.updateNavArrow() },
        )
    }

    val latestGraph by rememberUpdatedState(state.mappedGraphState.value)
    val latestLocation by rememberUpdatedState(state.currentLocationState.value)
    val latestRouteOverlay by rememberUpdatedState(state.routeOverlayState.value)
    val latestDestMarker by rememberUpdatedState(state.destinationMarkerState.value)
    val latestStartMarker by rememberUpdatedState(state.startMarkerState.value)
    val latestWaypoints by rememberUpdatedState(state.waypointsState.value)
    val latestWaypointMarkers by rememberUpdatedState(state.waypointMarkersState.value)
    val latestRoutePreference by rememberUpdatedState(state.selectedRoutePreferenceState.value)
    val latestMapView by rememberUpdatedState(state.mapViewState.value)

    val routeExecutionContext = RouteExecutionContext(
        context = state.context,
        routeJobRef = state.routeJobRef,
        graphLock = state.graphLock,
        pendingRouteRef = state.pendingRouteRef,
        getGraph = { latestGraph },
        getLoadedGraphName = { state.loadedGraphNameState.value },
        isSwitchingGraph = { state.isSwitchingGraphState.value },
        onSwitchGraph = { state.switchRef.get()?.invoke(it) },
        getMapView = { latestMapView },
        getDestMarker = { latestDestMarker },
        getStartMarker = { latestStartMarker },
        getWaypointMarkers = { latestWaypointMarkers },
        getRouteOverlay = { latestRouteOverlay },
        getWaypoints = { latestWaypoints },
        getRoutePreference = { latestRoutePreference },
        offRouteDetector = state.offRouteDetector,
        onDestinationChanged = { state.destinationState.value = it },
        onWaypointsChanged = { state.waypointsState.value = it },
        onStartMarkerChanged = { state.startMarkerState.value = it },
        onDestMarkerChanged = { state.destinationMarkerState.value = it },
        onWaypointMarkersChanged = { state.waypointMarkersState.value = it },
        onCalculatingRouteChanged = { state.isCalculatingRouteState.value = it },
        onReroutingChanged = { state.isReroutingState.value = it },
        onWarningMessageChanged = { state.warningMessageState.value = it },
        onRouteCalculated = { overlay, route, maneuvers, instructions, summary, isReroute ->
            state.routeOverlayState.value = overlay
            state.navigationRouteState.value = route
            state.navigationManeuversState.value = maneuvers
            state.navigationInstructionsState.value = instructions
            state.navigationProgressState.value = null
            state.navStatsState.value = null
            state.offRouteDetector.reset()
            if (!isReroute) {
                state.isNavigationActiveState.value = false
                state.isArrivedState.value = false
                state.rerouteCountState.intValue = 0
                state.rerouteLimitWarnedState.value = false
                state.lastRerouteElapsedMsState.value = 0L
                state.offRouteDetector.reset()
            } else {
                state.navStartElapsedMsState.value = SystemClock.elapsedRealtime()
                state.rerouteCountState.intValue++
            }
            state.routeSummaryState.value = summary
            if (state.warningMessageState.value?.contains("グラフに切替中です") == true) {
                state.warningMessageState.value = null
            }
        },
    )

    val runRouteCalculation: (start: GeoPoint, goal: GeoPoint, isReroute: Boolean, waypointsOverride: List<GeoPoint>?, preferenceOverride: RoutePreference?) -> Unit = { start, goal, isReroute, waypointsOverride, preferenceOverride ->
        executeRouteCalculation(routeExecutionContext, start, goal, isReroute, waypointsOverride, preferenceOverride)
    }

    val startRouteToDestination: (GeoPoint) -> Unit = { point ->
        val loc = latestLocation
        val start = if (loc != null) {
            GeoPoint(loc.latitude, loc.longitude)
        } else {
            GeoPoint(FALLBACK_LATITUDE, FALLBACK_LONGITUDE)
        }
        Log.i(
            "CycleMapRoute",
            "DESTINATION_SET dest=${point.latitude},${point.longitude} " +
                "currentLocation=${if (loc != null) "${loc.latitude},${loc.longitude} acc=${loc.accuracy}" else "null->FALLBACK $FALLBACK_LATITUDE,$FALLBACK_LONGITUDE"}",
        )
        runRouteCalculation(start, point, false, null, null)
    }

    val handleSpotSelected: (GeoPoint) -> Unit = { point ->
        if (state.destinationState.value != null) {
            state.pendingSelectedSpotState.value = point
        } else {
            startRouteToDestination(point)
        }
    }

    MapGraphEffects(
        context = state.context,
        scope = state.scope,
        routeJobRef = state.routeJobRef,
        graphLock = state.graphLock,
        pendingRouteRef = state.pendingRouteRef,
        switchRef = state.switchRef,
        mappedGraph = state.mappedGraphState.value,
        onMappedGraphChanged = { state.mappedGraphState.value = it },
        loadedGraphName = state.loadedGraphNameState.value,
        onLoadedGraphNameChanged = { state.loadedGraphNameState.value = it },
        isSwitchingGraph = state.isSwitchingGraphState.value,
        onSwitchingGraphChanged = { state.isSwitchingGraphState.value = it },
        onCalculatingRouteChanged = { state.isCalculatingRouteState.value = it },
        onReroutingChanged = { state.isReroutingState.value = it },
        onWarningMessageChanged = { state.warningMessageState.value = it },
        currentLocation = state.currentLocationState.value,
        onLocationUpdate = onLocationUpdate,
        runRouteCalculation = runRouteCalculation,
        onGpxRecordingStarted = {
            state.isRecordingState.value = true
            state.gpxPointCountState.value = 0
            state.gpxNotificationTextState.value = "記録を開始しました"
        },
        onGpxRecordingSaved = { points, fileName ->
            state.isRecordingState.value = false
            state.gpxPointCountState.value = points
            state.gpxNotificationTextState.value = "GPX保存完了 ($points 点): $fileName"
        },
    )

    LaunchedEffect(state.navigationRouteState.value, state.mapViewState.value, state.isNavigationActiveState.value) {
        val navRoute = state.navigationRouteState.value
        if (navRoute.isEmpty()) return@LaunchedEffect
        val view = state.mapViewState.value ?: return@LaunchedEffect
        if (state.isNavigationActiveState.value) return@LaunchedEffect
        withFrameNanos { }
        val points = navRoute.map { GeoPoint(it.latitude, it.longitude) }
        val bounds = BoundingBox.fromGeoPoints(points)
        view.zoomToBoundingBox(bounds, true, 64)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        state.hasLocationPermissionState.value = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    MapTrackingEffects(
        hasLocationPermission = state.hasLocationPermissionState.value,
        appLifecycleState = state.appLifecycleStateState.value,
        isNavigationActive = state.isNavigationActiveState.value,
        isRecording = state.isRecordingState.value,
        trackingController = state.trackingController,
        context = state.context,
        gpsHealthMonitor = state.gpsHealthMonitor,
        gpsStatus = state.gpsStatusState.value,
        onGpsStatusChanged = { state.gpsStatusState.value = it },
        onLocationChanged = { state.currentLocationState.value = it },
        lastValidLocation = state.lastValidLocationState.value,
        onLastValidLocationChanged = { state.lastValidLocationState.value = it },
        lastValidBearingDegrees = state.lastValidBearingDegreesState.floatValue,
        onLastValidBearingDegreesChanged = { state.lastValidBearingDegreesState.floatValue = it },
        isHeadingUp = state.isHeadingUpState.value,
        speedKmh = state.speedKmhState.value,
        onSpeedChanged = { state.speedKmhState.value = it },
        navSpeedMps = state.navSpeedMpsState.value,
        onNavSpeedChanged = { state.navSpeedMpsState.value = it },
        navArrowCache = state.navArrowCache,
        orientationAnimator = orientationAnimator,
        onRecordingPointsChanged = { recording, points ->
            state.isRecordingState.value = recording
            if (recording) state.gpxPointCountState.value = points
        },
        navigationRoute = state.navigationRouteState.value,
        navigationManeuvers = state.navigationManeuversState.value,
        navigationInstructions = state.navigationInstructionsState.value,
        onNavigationProgressChanged = { state.navigationProgressState.value = it },
        lastValidRouteProgressMeters = state.lastValidRouteProgressMetersState.value,
        onLastValidRouteProgressMetersChanged = { state.lastValidRouteProgressMetersState.value = it },
        navStartElapsedMs = state.navStartElapsedMsState.value,
        onNavStatsChanged = { state.navStatsState.value = it },
        isArrived = state.isArrivedState.value,
        onArrivedChanged = { state.isArrivedState.value = it },
        onNavigationActiveChanged = { state.isNavigationActiveState.value = it },
        offRouteDetector = state.offRouteDetector,
        voiceNavigator = state.voiceNavigator,
        isRerouting = state.isReroutingState.value,
        autoRerouteEnabled = state.autoRerouteEnabledState.value,
        destination = state.destinationState.value,
        lastRerouteElapsedMs = state.lastRerouteElapsedMsState.value,
        onTriggerReroute = { start, goal ->
            state.lastRerouteElapsedMsState.value = SystemClock.elapsedRealtime()
            runRouteCalculation(start, goal, true, null, null)
        },
        headingDegrees = state.headingDegreesState.floatValue,
        onHeadingChanged = { state.headingDegreesState.floatValue = it },
        onHeadingSensorFixChanged = { state.hasHeadingSensorFixState.value = it },
    )

    // MVI StateFlow 同期: 高頻度位置情報
    LaunchedEffect(state.currentLocationState.value, state.headingDegreesState.floatValue) {
        val loc = state.currentLocationState.value
        if (loc != null) {
            state.viewModel.updateLocation(
                lat = loc.latitude,
                lon = loc.longitude,
                alt = if (loc.hasAltitude()) loc.altitude else 0.0,
                speed = if (loc.hasSpeed()) loc.speed else 0f,
                bearing = if (loc.hasBearing()) loc.bearing else state.headingDegreesState.floatValue,
                accuracy = if (loc.hasAccuracy()) loc.accuracy else 0f,
            )
        }
    }

    // MVI StateFlow 同期: ナビゲーション進捗
    LaunchedEffect(state.navigationProgressState.value, state.isReroutingState.value) {
        val progress = state.navigationProgressState.value
        val inst = state.navigationInstructionsState.value
        if (progress != null) {
            val remainingDistance = maxOf(0.0, progress.routeDistanceMeters - progress.distanceFromStartMeters)
            val distanceToTurn = progress.distanceToNextManeuverMeters ?: 0.0
            val remainingSec = remainingDistance / 4.16 // 平均15km/h換算
            state.viewModel.updateNavProgress(
                distanceToTurn = distanceToTurn,
                remainingDistance = remainingDistance,
                remainingSec = remainingSec,
                currentInst = inst.firstOrNull(),
                nextInst = inst.getOrNull(1),
                isRerouting = state.isReroutingState.value,
            )
        }
    }

    // MVI StateFlow 同期: ネットワーク状態
    val currentNetworkLink by state.connectivityState.link.collectAsState()
    LaunchedEffect(currentNetworkLink) {
        state.viewModel.onIntent(com.gorite.cyclemap.ui.map.MapUiIntent.UpdateNetworkLink(currentNetworkLink))
    }

    val startNavigation: () -> Unit = {
        val loc = state.currentLocationState.value
        val navRoute = state.navigationRouteState.value
        val navInst = state.navigationInstructionsState.value
        val dest = state.destinationState.value
        Log.i(
            "CycleMapRoute",
            "GUIDANCE_START currentLocation=${if (loc != null) "${loc.latitude},${loc.longitude} acc=${loc.accuracy}" else "(null)"} " +
                "destination=${dest?.let { "${it.latitude},${it.longitude}" } ?: "(null)"} " +
                "routeNodeCount=${navRoute.size} instructionCount=${navInst.size} " +
                "wasActive=${state.isNavigationActiveState.value}",
        )
        if (navRoute.isNotEmpty()) {
            state.followLocationState.value = true
            state.navStartElapsedMsState.value = SystemClock.elapsedRealtime()
            state.rerouteCountState.intValue = 0
            state.rerouteLimitWarnedState.value = false
            state.lastRerouteElapsedMsState.value = 0L
            state.isArrivedState.value = false
            state.navStatsState.value = null
            state.offRouteDetector.reset()
            state.voiceNavigator.reset()
            state.isNavigationActiveState.value = true
            state.mapViewState.value?.controller?.setZoom(NAVIGATION_ZOOM)
            loc?.let { location ->
                state.mapViewState.value?.let { view ->
                    view.controller.animateTo(GeoPoint(location.latitude, location.longitude))
                }
                state.navigationProgressState.value = calculateRouteProgress(
                    RoutePoint(location.latitude, location.longitude),
                    navRoute,
                    state.navigationManeuversState.value,
                )
            }
            Log.i("CycleMapRoute", "GUIDANCE_STATE isNavigationActive=true routeDisplayed=true")
            state.viewModel.onIntent(com.gorite.cyclemap.ui.map.MapUiIntent.StartNavigation)
        } else {
            Log.e("CycleMapRoute", "ROUTE_FAILURE reason=start-with-empty-route")
            state.warningMessageState.value = "ルートがまだ確定していません。探索完了をお待ちください"
        }
    }

    val clearRoute: () -> Unit = {
        state.viewModel.onIntent(com.gorite.cyclemap.ui.map.MapUiIntent.StopNavigation)
        executeClearRoute(
            routeJobRef = state.routeJobRef,
            pendingRouteRef = state.pendingRouteRef,
            mapView = state.mapViewState.value,
            routeOverlay = state.routeOverlayState.value,
            gpxTrackOverlay = state.gpxTrackOverlayState.value,
            destinationMarker = state.destinationMarkerState.value,
            startMarker = state.startMarkerState.value,
            waypointMarkers = state.waypointMarkersState.value,
            offRouteDetector = state.offRouteDetector,
            voiceNavigator = state.voiceNavigator,
            onResetStates = {
                state.routeOverlayState.value = null
                state.gpxTrackOverlayState.value = null
                state.destinationMarkerState.value = null
                state.startMarkerState.value = null
                state.waypointMarkersState.value = emptyList()
                state.waypointsState.value = emptyList()
                state.routeSummaryState.value = null
                state.destinationState.value = null
                state.navigationRouteState.value = emptyList()
                state.navigationManeuversState.value = emptyList()
                state.navigationInstructionsState.value = emptyList()
                state.navigationProgressState.value = null
                state.navStatsState.value = null
                state.isNavigationActiveState.value = false
                state.isCalculatingRouteState.value = false
                state.isReroutingState.value = false
                state.isArrivedState.value = false
                state.rerouteCountState.intValue = 0
                state.rerouteLimitWarnedState.value = false
                state.lastRerouteElapsedMsState.value = 0L
                state.isDownloadingCorridorTilesState.value = false
                state.corridorTileProgressTextState.value = null
            },
        )
    }

    val toggleNextLayer: () -> Unit = {
        val nextLayer = determineNextLayer(state.context, state.selectedLayerState.value)
        state.selectedLayerState.value = nextLayer
        val mode = when (nextLayer) {
            MapLayer.VECTOR_LIBERTY -> com.gorite.cyclemap.ui.map.MapLayerMode.Vector3D
            MapLayer.GSI -> com.gorite.cyclemap.ui.map.MapLayerMode.GsiStandard
            MapLayer.OSM -> com.gorite.cyclemap.ui.map.MapLayerMode.OpenCycleMap
            MapLayer.TERRAIN -> com.gorite.cyclemap.ui.map.MapLayerMode.Hillshade
        }
        state.viewModel.onIntent(com.gorite.cyclemap.ui.map.MapUiIntent.SwitchLayer(mode))
    }

    val downloadCorridorTiles: () -> Unit = {
        if (!state.isDownloadingCorridorTilesState.value && state.navigationRouteState.value.isNotEmpty()) {
            state.isDownloadingCorridorTilesState.value = true
            executeDownloadCorridorTiles(
                context = state.context,
                navigationRoute = state.navigationRouteState.value,
                selectedLayer = state.selectedLayerState.value,
                onProgressUpdate = { state.corridorTileProgressTextState.value = it },
                onFinished = { state.isDownloadingCorridorTilesState.value = false },
            )
        }
    }

    val runRoutingBenchmark: (RoutingBenchmarkRequest) -> Unit = { request ->
        if (!state.isRoutingBenchmarkRunningState.value) {
            state.isRoutingBenchmarkRunningState.value = true
            state.routingBenchmarkResultState.value = null
            executeRoutingBenchmark(
                context = state.context,
                scope = state.scope,
                request = request,
                graphLock = state.graphLock,
                mappedGraph = state.mappedGraphState.value,
                onResult = { state.routingBenchmarkResultState.value = it },
                onFinished = { state.isRoutingBenchmarkRunningState.value = false },
            )
        }
    }

    MapScreenScaffold(
        state = state,
        modifier = modifier,
        systemInsets = systemInsets,
        isDarkTheme = isDarkTheme,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        permissionLauncher = permissionLauncher,
        orientationAnimator = orientationAnimator,
        runRouteCalculation = runRouteCalculation,
        startRouteToDestination = startRouteToDestination,
        handleSpotSelected = handleSpotSelected,
        runRoutingBenchmark = runRoutingBenchmark,
        downloadCorridorTiles = downloadCorridorTiles,
        toggleNextLayer = toggleNextLayer,
        startNavigation = startNavigation,
        clearRoute = clearRoute,
    )
}
