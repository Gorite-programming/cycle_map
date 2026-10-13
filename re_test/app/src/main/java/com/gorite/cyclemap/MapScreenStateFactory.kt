package com.gorite.cyclemap

import android.content.Context
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gorite.cyclemap.data.AddressDisplayController
import com.gorite.cyclemap.data.ElevationRepository
import com.gorite.cyclemap.ui.cycling.ElevationSample
import com.gorite.cyclemap.ui.cycling.buildRouteElevationProfile
import com.gorite.cyclemap.data.MapSourceType
import com.gorite.cyclemap.data.Prefecture
import com.gorite.cyclemap.data.RoutingGraphSelection
import com.gorite.cyclemap.data.TileProgress
import com.gorite.cyclemap.power.BatteryMonitor
import com.gorite.cyclemap.routing.LazyMappedRoadGraph
import com.gorite.cyclemap.routing.Maneuver
import com.gorite.cyclemap.routing.NavigationStats
import com.gorite.cyclemap.routing.OffRouteDetector
import com.gorite.cyclemap.routing.RouteInstruction
import com.gorite.cyclemap.routing.RoutePoint
import com.gorite.cyclemap.routing.RoutePreference
import com.gorite.cyclemap.routing.RouteProgress
import com.gorite.cyclemap.speech.AndroidTextToSpeechEngine
import com.gorite.cyclemap.speech.HybridVoiceGuidanceEngine
import com.gorite.cyclemap.speech.ShikokuMetanAudioEngine
import com.gorite.cyclemap.speech.VoiceGuidanceMode
import com.gorite.cyclemap.speech.VoiceGuidanceNavigator
import com.gorite.cyclemap.tracking.AppLifecycleState
import com.gorite.cyclemap.tracking.GpsHealthMonitor
import com.gorite.cyclemap.tracking.GpsSignalStatus
import com.gorite.cyclemap.tracking.LocationTrackingService
import com.gorite.cyclemap.tracking.TrackingStateController
import com.gorite.cyclemap.ui.cycling.CyclingTab
import com.gorite.cyclemap.ui.cycling.GoogleNavTab
import com.gorite.cyclemap.ui.cycling.GpxHistoryEntry
import com.gorite.cyclemap.ui.cycling.NearbySpot
import com.gorite.cyclemap.ui.cycling.PoiCategory
import com.gorite.cyclemap.ui.cycling.QuickSpotType
import com.gorite.cyclemap.ui.cycling.SpotQuickCategory
import org.maplibre.android.maps.MapLibreMap
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock

@Composable
internal fun rememberMapScreenState(
    onNavigationStateChanged: (Boolean) -> Unit,
): MapScreenState {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("cyclemap_prefs", Context.MODE_PRIVATE) }
    val animationEnabledState = remember { mutableStateOf(prefs.getBoolean("animation_enabled", true)) }

    val batteryMonitor = remember(context) { BatteryMonitor(context) }
    DisposableEffect(batteryMonitor) {
        batteryMonitor.start()
        onDispose { batteryMonitor.stop() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val appLifecycleStateState = remember {
        mutableStateOf(
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                AppLifecycleState.RESUMED
            } else {
                AppLifecycleState.PAUSED_OR_STOPPED
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    appLifecycleStateState.value = AppLifecycleState.RESUMED
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    appLifecycleStateState.value = AppLifecycleState.PAUSED_OR_STOPPED
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val trackingController = remember(context) {
        TrackingStateController(
            onStartTracking = { LocationTrackingService.startTracking(context) },
            onStopTracking = { LocationTrackingService.stopTracking(context) },
        )
    }

    val hasLocationPermissionState = remember { mutableStateOf(context.hasLocationPermission()) }
    val currentLocationState = remember { mutableStateOf<android.location.Location?>(null) }
    val gpsHealthMonitor = remember { GpsHealthMonitor() }
    val gpsStatusState = remember { mutableStateOf(GpsSignalStatus.HEALTHY) }
    val lastValidLocationState = remember { mutableStateOf<android.location.Location?>(null) }
    val lastValidRouteProgressMetersState = remember { mutableStateOf<Double?>(null) }
    val lastValidBearingDegreesState = remember { mutableFloatStateOf(0f) }
    val selectedLayerState = remember { mutableStateOf(MapLayer.VECTOR_LIBERTY) }

    val vectorMapState = remember { mutableStateOf<MapLibreMap?>(null) }
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    val locationMarkerState = remember { mutableStateOf<Marker?>(null) }
    val accuracyCircleState = remember { mutableStateOf<AccuracyCircleOverlay?>(null) }
    val destinationMarkerState = remember { mutableStateOf<Marker?>(null) }
    val startMarkerState = remember { mutableStateOf<Marker?>(null) }
    val waypointMarkersState = remember { mutableStateOf(emptyList<Marker>()) }
    val speedKmhState = remember { mutableStateOf(0.0) }
    val headingDegreesState = remember { mutableFloatStateOf(0f) }
    val hasHeadingSensorFixState = remember { mutableStateOf(false) }
    val isHeadingUpState = remember { mutableStateOf(true) }
    val followLocationState = remember { mutableStateOf(true) }
    val currentVectorTiltState = remember { mutableDoubleStateOf(0.0) }

    val navArrowCache = remember { NavArrowCache() }
    val navArrowDrawable = remember(context) {
        ContextCompat.getDrawable(context, R.drawable.ic_nav_arrow)
    }
    val navDotDrawable = remember(context) {
        ContextCompat.getDrawable(context, R.drawable.ic_location_dot)
    }

    val showPrefectureListDialogState = remember { mutableStateOf(false) }
    val selectedPrefectureForActionState = remember { mutableStateOf<Prefecture?>(null) }
    val showAlreadyDownloadedDialogState = remember { mutableStateOf(false) }
    val showDownloadConfirmDialogState = remember { mutableStateOf(false) }
    val targetDownloadSourceTypeState = remember { mutableStateOf(MapSourceType.GSI) }

    val currentTileProgressState = remember { mutableStateOf<TileProgress?>(null) }
    val isDownloadingState = remember { mutableStateOf(false) }
    val showLicenseState = remember { mutableStateOf(false) }
    val showVersionState = remember { mutableStateOf(false) }
    val showDeveloperOptionsState = remember { mutableStateOf(false) }

    val bottomTabState = remember { mutableStateOf(CyclingTab.MAP) }
    val googleNavTabState = remember { mutableStateOf(GoogleNavTab.EXPLORE) }
    val showRoutePanelState = remember { mutableStateOf(false) }
    val showSpotPanelState = remember { mutableStateOf(false) }
    val showFavoritesOnlyInSheetState = remember { mutableStateOf(false) }
    val showRecordPanelState = remember { mutableStateOf(false) }
    val showCyclingSettingsState = remember { mutableStateOf(false) }
    val poiVisibleState = remember { mutableStateOf(true) }
    val poiSelectedState = remember { mutableStateOf(PoiCategory.defaults()) }
    val poiRefreshTickState = remember { mutableIntStateOf(0) }
    val currentZoomLevelState = remember { mutableDoubleStateOf(15.0) }
    val selectedPoiSpotState = remember { mutableStateOf<NearbySpot?>(null) }
    val spotCategoryState = remember { mutableStateOf<SpotQuickCategory?>(null) }
    val spotResultsState = remember { mutableStateOf<List<NearbySpot>>(emptyList()) }
    val spotLoadingState = remember { mutableStateOf(false) }
    val spotMessageState = remember { mutableStateOf<String?>(null) }
    val activeQuickSpotTypeState = remember { mutableStateOf<QuickSpotType?>(null) }
    val showQuickSpotSheetState = remember { mutableStateOf(false) }
    val quickSpotResultsState = remember { mutableStateOf<List<NearbySpot>>(emptyList()) }
    val quickSpotLoadingState = remember { mutableStateOf(false) }
    val quickSpotMessageState = remember { mutableStateOf<String?>(null) }
    val gpxHistoryState = remember { mutableStateOf<List<GpxHistoryEntry>>(emptyList()) }
    val locationLabelState = remember { mutableStateOf(AddressDisplayController.ADDRESS_LOADING) }
    val addressController = remember { AddressDisplayController() }
    val isRoutingBenchmarkRunningState = remember { mutableStateOf(false) }
    val routingBenchmarkResultState = remember { mutableStateOf<String?>(null) }
    val showDestinationSearchState = remember { mutableStateOf(false) }
    val selectedMenuState = remember { mutableStateOf("地図") }

    val mappedGraphState = remember { mutableStateOf<LazyMappedRoadGraph?>(null) }
    val loadedGraphNameState = remember { mutableStateOf<String?>(null) }
    val isSwitchingGraphState = remember { mutableStateOf(false) }
    val warningMessageState = remember { mutableStateOf<String?>(null) }
    val destinationState = remember { mutableStateOf<GeoPoint?>(null) }
    val waypointsState = remember { mutableStateOf(emptyList<GeoPoint>()) }
    val pendingSelectedSpotState = remember { mutableStateOf<GeoPoint?>(null) }
    val routeOverlayState = remember { mutableStateOf<Polyline?>(null) }
    val gpxTrackOverlayState = remember { mutableStateOf<Polyline?>(null) }
    val routeSummaryState = remember { mutableStateOf<RouteSummary?>(null) }
    val selectedRoutePreferenceState = remember { mutableStateOf(RoutePreference.RECOMMENDED) }
    val navigationRouteState = remember { mutableStateOf<List<RoutePoint>>(emptyList()) }
    val navigationManeuversState = remember { mutableStateOf(emptyList<Maneuver>()) }
    val navigationInstructionsState = remember { mutableStateOf(emptyList<RouteInstruction>()) }
    val navigationProgressState = remember { mutableStateOf<RouteProgress?>(null) }
    val isNavigationActiveState = remember { mutableStateOf(false) }
    LaunchedEffect(isNavigationActiveState.value) {
        onNavigationStateChanged(isNavigationActiveState.value)
    }

    val isCalculatingRouteState = remember { mutableStateOf(false) }
    val navStatsState = remember { mutableStateOf<NavigationStats?>(null) }
    val navSpeedMpsState = remember { mutableStateOf(0.0) }
    val navStartElapsedMsState = remember { mutableStateOf(0L) }
    val isReroutingState = remember { mutableStateOf(false) }
    val isArrivedState = remember { mutableStateOf(false) }
    val autoRerouteEnabledState = remember { mutableStateOf(true) }
    val rerouteCountState = remember { mutableIntStateOf(0) }
    val rerouteLimitWarnedState = remember { mutableStateOf(false) }
    val lastRerouteElapsedMsState = remember { mutableStateOf(0L) }
    val offRouteDetector = remember { OffRouteDetector() }
    val graphLock = remember { ReentrantReadWriteLock() }
    val routeJobRef = remember { AtomicReference<RouteCalculationJob?>(null) }

    val elevationRepository = remember(context) { ElevationRepository.getInstance(context) }
    val realElevProfileState = remember { mutableStateOf<List<ElevationSample>>(emptyList()) }
    LaunchedEffect(routeSummaryState.value, navigationRouteState.value) {
        val summary = routeSummaryState.value
        val navRoute = navigationRouteState.value
        if (summary != null && navRoute.isNotEmpty()) {
            val coords = navRoute.map { it.latitude to it.longitude }
            val initial = buildRouteElevationProfile(coords, summary.distanceMeters)
            realElevProfileState.value = initial
            val real = elevationRepository.getRouteElevationProfile(coords, summary.distanceMeters)
            if (real.isNotEmpty()) {
                realElevProfileState.value = real
            }
        } else {
            realElevProfileState.value = emptyList()
        }
    }

    val voiceGuidanceModeState = remember { mutableStateOf(VoiceGuidanceMode.VOICEVOX) }
    val metanAudioEngine = remember(context) { ShikokuMetanAudioEngine(context) }
    val voiceNavigator = remember(context, metanAudioEngine) {
        val tts = AndroidTextToSpeechEngine(context)
        val hybrid = HybridVoiceGuidanceEngine(metanAudioEngine, tts, voiceGuidanceModeState.value)
        VoiceGuidanceNavigator(hybrid)
    }
    LaunchedEffect(voiceGuidanceModeState.value) {
        voiceNavigator.setMode(voiceGuidanceModeState.value)
    }
    DisposableEffect(context) {
        onDispose {
            voiceNavigator.shutdown()
        }
    }

    val pendingRouteRef = remember { AtomicReference<RouteRequest?>(null) }
    val switchRef = remember { AtomicReference<((RoutingGraphSelection.Available) -> Unit)?>(null) }

    val isRecordingState = remember { mutableStateOf(false) }
    val gpxPointCountState = remember { mutableStateOf(0) }
    val gpxNotificationTextState = remember { mutableStateOf<String?>(null) }

    val isDownloadingCorridorTilesState = remember { mutableStateOf(false) }
    val corridorTileProgressTextState = remember { mutableStateOf<String?>(null) }

    val isAreaSelectModeState = remember { mutableStateOf(false) }
    val areaDragStateState = remember { mutableStateOf(AreaDragState()) }
    val selectedAreaBoundsState = remember { mutableStateOf<BoundingBox?>(null) }
    val showAreaDownloadConfirmDialogState = remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val viewModel = remember { com.gorite.cyclemap.ui.map.MapViewModel() }
    val connectivityState = remember(context) { com.gorite.cyclemap.net.ConnectivityState(context) }
    DisposableEffect(connectivityState) {
        onDispose {
            connectivityState.unregister()
        }
    }
    val onlineFeatureGate = remember(connectivityState) {
        com.gorite.cyclemap.net.OnlineFeatureGate(connectivityState.link)
    }

    return MapScreenState(
        context = context,
        prefs = prefs,
        scope = scope,
        lifecycleOwner = lifecycleOwner,
        trackingController = trackingController,
        batteryMonitor = batteryMonitor,
        addressController = addressController,
        offRouteDetector = offRouteDetector,
        voiceNavigator = voiceNavigator,
        elevationRepository = elevationRepository,
        gpsHealthMonitor = gpsHealthMonitor,
        navArrowCache = navArrowCache,
        navArrowDrawable = navArrowDrawable,
        navDotDrawable = navDotDrawable,
        drawerState = drawerState,
        graphLock = graphLock,
        routeJobRef = routeJobRef,
        pendingRouteRef = pendingRouteRef,
        switchRef = switchRef,
        viewModel = viewModel,
        connectivityState = connectivityState,
        onlineFeatureGate = onlineFeatureGate,
        animationEnabledState = animationEnabledState,
        appLifecycleStateState = appLifecycleStateState,
        hasLocationPermissionState = hasLocationPermissionState,
        currentLocationState = currentLocationState,
        gpsStatusState = gpsStatusState,
        lastValidLocationState = lastValidLocationState,
        lastValidRouteProgressMetersState = lastValidRouteProgressMetersState,
        lastValidBearingDegreesState = lastValidBearingDegreesState,
        selectedLayerState = selectedLayerState,
        vectorMapState = vectorMapState,
        mapViewState = mapViewState,
        locationMarkerState = locationMarkerState,
        accuracyCircleState = accuracyCircleState,
        destinationMarkerState = destinationMarkerState,
        startMarkerState = startMarkerState,
        waypointMarkersState = waypointMarkersState,
        speedKmhState = speedKmhState,
        headingDegreesState = headingDegreesState,
        hasHeadingSensorFixState = hasHeadingSensorFixState,
        isHeadingUpState = isHeadingUpState,
        followLocationState = followLocationState,
        currentVectorTiltState = currentVectorTiltState,
        showPrefectureListDialogState = showPrefectureListDialogState,
        selectedPrefectureForActionState = selectedPrefectureForActionState,
        showAlreadyDownloadedDialogState = showAlreadyDownloadedDialogState,
        showDownloadConfirmDialogState = showDownloadConfirmDialogState,
        targetDownloadSourceTypeState = targetDownloadSourceTypeState,
        currentTileProgressState = currentTileProgressState,
        isDownloadingState = isDownloadingState,
        showLicenseState = showLicenseState,
        showVersionState = showVersionState,
        showDeveloperOptionsState = showDeveloperOptionsState,
        bottomTabState = bottomTabState,
        googleNavTabState = googleNavTabState,
        showRoutePanelState = showRoutePanelState,
        showSpotPanelState = showSpotPanelState,
        showFavoritesOnlyInSheetState = showFavoritesOnlyInSheetState,
        showRecordPanelState = showRecordPanelState,
        showCyclingSettingsState = showCyclingSettingsState,
        poiVisibleState = poiVisibleState,
        poiSelectedState = poiSelectedState,
        poiRefreshTickState = poiRefreshTickState,
        currentZoomLevelState = currentZoomLevelState,
        selectedPoiSpotState = selectedPoiSpotState,
        spotCategoryState = spotCategoryState,
        spotResultsState = spotResultsState,
        spotLoadingState = spotLoadingState,
        spotMessageState = spotMessageState,
        activeQuickSpotTypeState = activeQuickSpotTypeState,
        showQuickSpotSheetState = showQuickSpotSheetState,
        quickSpotResultsState = quickSpotResultsState,
        quickSpotLoadingState = quickSpotLoadingState,
        quickSpotMessageState = quickSpotMessageState,
        gpxHistoryState = gpxHistoryState,
        locationLabelState = locationLabelState,
        isRoutingBenchmarkRunningState = isRoutingBenchmarkRunningState,
        routingBenchmarkResultState = routingBenchmarkResultState,
        showDestinationSearchState = showDestinationSearchState,
        selectedMenuState = selectedMenuState,
        mappedGraphState = mappedGraphState,
        loadedGraphNameState = loadedGraphNameState,
        isSwitchingGraphState = isSwitchingGraphState,
        warningMessageState = warningMessageState,
        destinationState = destinationState,
        waypointsState = waypointsState,
        pendingSelectedSpotState = pendingSelectedSpotState,
        routeOverlayState = routeOverlayState,
        gpxTrackOverlayState = gpxTrackOverlayState,
        routeSummaryState = routeSummaryState,
        selectedRoutePreferenceState = selectedRoutePreferenceState,
        navigationRouteState = navigationRouteState,
        navigationManeuversState = navigationManeuversState,
        navigationInstructionsState = navigationInstructionsState,
        navigationProgressState = navigationProgressState,
        isNavigationActiveState = isNavigationActiveState,
        isCalculatingRouteState = isCalculatingRouteState,
        navStatsState = navStatsState,
        navSpeedMpsState = navSpeedMpsState,
        navStartElapsedMsState = navStartElapsedMsState,
        isReroutingState = isReroutingState,
        isArrivedState = isArrivedState,
        autoRerouteEnabledState = autoRerouteEnabledState,
        rerouteCountState = rerouteCountState,
        rerouteLimitWarnedState = rerouteLimitWarnedState,
        lastRerouteElapsedMsState = lastRerouteElapsedMsState,
        realElevProfileState = realElevProfileState,
        voiceGuidanceModeState = voiceGuidanceModeState,
        isRecordingState = isRecordingState,
        gpxPointCountState = gpxPointCountState,
        gpxNotificationTextState = gpxNotificationTextState,
        isDownloadingCorridorTilesState = isDownloadingCorridorTilesState,
        corridorTileProgressTextState = corridorTileProgressTextState,
        isAreaSelectModeState = isAreaSelectModeState,
        areaDragStateState = areaDragStateState,
        selectedAreaBoundsState = selectedAreaBoundsState,
        showAreaDownloadConfirmDialogState = showAreaDownloadConfirmDialogState,
    )
}
