package com.gorite.cyclemap.ui.layout

import com.gorite.cyclemap.*
import com.gorite.cyclemap.navigation.*
import com.gorite.cyclemap.utils.*
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import com.gorite.cyclemap.ui.components.*
import com.gorite.cyclemap.ui.state.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.gorite.cyclemap.data.DownloadStatusManager
import com.gorite.cyclemap.data.MapSourceType
import com.gorite.cyclemap.routing.RoutePreference
import com.gorite.cyclemap.ui.RoutingBenchmarkRequest
import com.gorite.cyclemap.ui.cycling.CyclingTab
import com.gorite.cyclemap.ui.cycling.SpotQuickCategory
import com.gorite.cyclemap.ui.theme.LocalAnimationEnabled
import com.gorite.cyclemap.ui.theme.Motion
import com.gorite.cyclemap.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

@Composable
internal fun MapScreenScaffold(
    state: MapScreenState,
    modifier: Modifier = Modifier,
    systemInsets: PaddingValues,
    isDarkTheme: Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    permissionLauncher: ActivityResultLauncher<Array<String>>,
    orientationAnimator: MapOrientationAnimator,
    runRouteCalculation: (GeoPoint, GeoPoint, Boolean, List<GeoPoint>?, RoutePreference?) -> Unit,
    startRouteToDestination: (GeoPoint) -> Unit,
    handleSpotSelected: (GeoPoint) -> Unit,
    runRoutingBenchmark: (RoutingBenchmarkRequest) -> Unit,
    downloadCorridorTiles: () -> Unit,
    toggleNextLayer: () -> Unit,
    startNavigation: () -> Unit,
    clearRoute: () -> Unit,
    weatherUiState: com.gorite.cyclemap.weather.WeatherUiState = com.gorite.cyclemap.weather.WeatherUiState.Unavailable,
    onRefreshWeather: () -> Unit = {},
) {
    val context = state.context
    val scope = state.scope
    val effectiveAnimationEnabled = Motion.resolveEffectiveAnimation(
        state.animationEnabledState.value,
        Motion.isSystemAnimationEnabled(context),
    )
    val batteryStatus by state.batteryMonitor.status.collectAsState()
    val isLowPowerMode = batteryStatus.isLowPower

    CompositionLocalProvider(LocalAnimationEnabled provides effectiveAnimationEnabled) {
        MapContentLayout(
            modifier = modifier,
            scope = scope,
            drawerState = state.drawerState,
            selectedMenu = state.selectedMenuState.value,
            onMenuSelect = { state.selectedMenuState.value = it },
            onOpenSearch = { state.showDestinationSearchState.value = true },
            onOpenDownload = { state.showPrefectureListDialogState.value = true },
            onOpenLicense = { state.showLicenseState.value = true },
            onOpenSettings = { state.showCyclingSettingsState.value = true },
            onOpenDeveloperOptions = { state.showDeveloperOptionsState.value = true },
            onOpenVersion = { state.showVersionState.value = true },
            systemInsets = systemInsets,
            networkLink = state.connectivityState.link.collectAsState().value,
            mapView = state.mapViewState.value,
            vectorMap = state.vectorMapState.value,
            selectedLayer = state.selectedLayerState.value,
            isAreaSelectMode = state.isAreaSelectModeState.value,
            onAreaSelectModeChange = { state.isAreaSelectModeState.value = it },
            areaDragState = state.areaDragStateState.value,
            onAreaDragStateChange = { state.areaDragStateState.value = it },
            onAreaSelected = { bounds ->
                state.selectedAreaBoundsState.value = bounds
                state.showAreaDownloadConfirmDialogState.value = true
            },
            navigationRoute = state.navigationRouteState.value, alternativeRoutes = state.calculatedRouteOptionsState.value.filter { !it.isPrimary }.map { opt -> opt.points.map { GeoPoint(it.first, it.second) } },
            currentLocation = state.currentLocationState.value,
            hasHeadingSensorFix = state.hasHeadingSensorFixState.value,
            headingDegrees = state.headingDegreesState.floatValue,
            isDarkTheme = isDarkTheme,
            isLowPowerMode = isLowPowerMode,
            selectedPoiSpot = state.selectedPoiSpotState.value,
            onSelectPoiSpot = { spot ->
                state.selectedPoiSpotState.value = spot
                if (spot != null) {
                    state.showSpotPanelState.value = true
                }
            },
            followLocation = state.followLocationState.value,
            onFollowLocationChange = { state.followLocationState.value = it },
            isNavigationActive = state.isNavigationActiveState.value,
            poiVisible = state.poiVisibleState.value,
            poiSelected = state.poiSelectedState.value,
            navDotDrawable = state.navDotDrawable,
            onMapCreated = { view, marker, circle ->
                state.locationMarkerState.value = marker
                state.accuracyCircleState.value = circle
                state.mapViewState.value = view
            },
            onPoiRefresh = { state.poiRefreshTickState.intValue++ },
            currentZoomLevel = state.currentZoomLevelState.doubleValue,
            onZoomLevelChanged = { state.currentZoomLevelState.doubleValue = it },
            onLongPress = { handleSpotSelected(it) },
            currentVectorTilt = state.currentVectorTiltState.doubleValue,
            onTiltChanged = { state.currentVectorTiltState.doubleValue = it },
            onVectorMapReady = { state.vectorMapState.value = it },
            effectiveAnimationEnabled = effectiveAnimationEnabled,
            hasLocationPermission = state.hasLocationPermissionState.value,
            permissionLauncher = permissionLauncher,
            gpsStatus = state.gpsStatusState.value,
            isLocationServiceEnabled = state.isLocationServiceEnabledState.value,
            hasReceivedFirstFix = state.gpsHealthMonitor.hasReceivedFirstFix,
            warningMessage = state.warningMessageState.value,
            onDismissWarning = { state.warningMessageState.value = null },
            currentTileProgress = state.currentTileProgressState.value,
            onDismissTileProgress = { state.currentTileProgressState.value = null },
            isSwitchingGraph = state.isSwitchingGraphState.value,
            isCalculatingRoute = state.isCalculatingRouteState.value,
            onHomeClick = {
                state.currentLocationState.value?.let { loc ->
                    val start = GeoPoint(loc.latitude, loc.longitude)
                    val homeDest = GeoPoint(FALLBACK_LATITUDE, FALLBACK_LONGITUDE)
                    startRouteToDestination(homeDest)
                }
            },
            onSelectQuickSpot = { type ->
                state.activeQuickSpotTypeState.value = type
                state.showQuickSpotSheetState.value = true
            },
            onSelectCategory = { categoryId ->
                state.selectedPoiSpotState.value = null
                state.spotCategoryState.value = com.gorite.cyclemap.ui.cycling.mapCategoryChipIdToQuickCategory(categoryId)
                state.showSpotPanelState.value = true
            },
            navigationProgress = state.navigationProgressState.value,
            navigationInstructions = state.navigationInstructionsState.value,
            isRerouting = state.isReroutingState.value,
            offRouteDetector = state.offRouteDetector,
            autoRerouteEnabled = state.autoRerouteEnabledState.value,
            isHeadingUp = state.isHeadingUpState.value,
            onHeadingUpToggle = {
                state.isHeadingUpState.value = !state.isHeadingUpState.value
                if (!state.isHeadingUpState.value) {
                    orientationAnimator.reset()
                    state.updateNavArrow()
                }
            },
            onTargetClick = {
                state.followLocationState.value = true
                state.currentLocationState.value?.let { loc ->
                    if (state.selectedLayerState.value == MapLayer.VECTOR_LIBERTY) {
                        state.vectorMapState.value?.animateCamera(
                            org.maplibre.android.camera.CameraUpdateFactory.newLatLng(
                                org.maplibre.android.geometry.LatLng(loc.latitude, loc.longitude),
                            ),
                        )
                    } else {
                        state.mapViewState.value?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                    }
                }
            },
            toggleNextLayer = toggleNextLayer,
            routeSummary = state.routeSummaryState.value,
            navStats = state.navStatsState.value,
            speedKmh = state.speedKmhState.value,
            isRecording = state.isRecordingState.value,
            gpxPointCount = state.gpxPointCountState.value,
            onWarningMessage = { state.warningMessageState.value = it },
            onStopNavigation = {
                state.isNavigationActiveState.value = false
                clearRoute()
            },
            onClearRoute = clearRoute,
            destination = state.destinationState.value,
            realElevProfile = state.realElevProfileState.value,
            onStartNavigation = startNavigation,
            downloadCorridorTiles = downloadCorridorTiles,
            corridorTileProgressText = state.corridorTileProgressTextState.value,
            selectedRoutePreference = state.selectedRoutePreferenceState.value,
            onRoutePreferenceChange = { pref ->
                state.selectedRoutePreferenceState.value = pref
                val loc = state.currentLocationState.value
                val start = if (loc != null) GeoPoint(loc.latitude, loc.longitude) else GeoPoint(FALLBACK_LATITUDE, FALLBACK_LONGITUDE)
                state.destinationState.value?.let { dest ->
                    runRouteCalculation(start, dest, false, null, pref)
                }
            },
            isArrived = state.isArrivedState.value,
            locationLabel = state.locationLabelState.value,
            googleNavTab = state.googleNavTabState.value,
            onTabSelected = { state.googleNavTabState.value = it },
            onExploreClick = {
                state.showFavoritesOnlyInSheetState.value = false
                state.showSpotPanelState.value = true
            },
            onSavedClick = {
                state.showFavoritesOnlyInSheetState.value = true
                state.showSpotPanelState.value = true
            },
            onRecordHistoryClick = {
                state.showRecordPanelState.value = true
                scope.launch(Dispatchers.IO) {
                    val dataDir = cycleMapDataDir(context)
                    val loaded = loadGpxHistory(dataDir)
                    (context as? ComponentActivity)?.runOnUiThread { state.gpxHistoryState.value = loaded }
                }
            },
            context = context,
            isVoiceMuted = state.voiceGuidanceModeState.value == com.gorite.cyclemap.speech.VoiceGuidanceMode.OFF,
            onToggleMute = {
                val isCurrentlyMuted = state.voiceGuidanceModeState.value == com.gorite.cyclemap.speech.VoiceGuidanceMode.OFF
                val nextMode = if (isCurrentlyMuted) com.gorite.cyclemap.speech.VoiceGuidanceMode.VOICEVOX else com.gorite.cyclemap.speech.VoiceGuidanceMode.OFF
                state.voiceGuidanceModeState.value = nextMode
                state.viewModel.onIntent(com.gorite.cyclemap.ui.map.MapUiIntent.ToggleMute)
            },
            weatherUiState = weatherUiState,
            onRefreshWeather = onRefreshWeather,
        )
    }

    MapModalsHost(
        context = context,
        prefs = state.prefs,
        lifecycleOwner = state.lifecycleOwner,
        mapView = state.mapViewState.value,
        vectorMap = state.vectorMapState.value,
        spotCategory = state.spotCategoryState.value,
        showSpotPanel = state.showSpotPanelState.value,
        onCloseSpotPanel = {
            state.showSpotPanelState.value = false
            state.bottomTabState.value = CyclingTab.MAP
        },
        onSpotCategoryChange = { state.spotCategoryState.value = it },
        currentLocation = state.currentLocationState.value,
        spotLoading = state.spotLoadingState.value,
        onSpotLoadingChange = { state.spotLoadingState.value = it },
        spotMessage = state.spotMessageState.value,
        onSpotMessageChange = { state.spotMessageState.value = it },
        spotResults = state.spotResultsState.value,
        onSpotResultsChange = { state.spotResultsState.value = it },
        activeQuickSpotType = state.activeQuickSpotTypeState.value,
        onActiveQuickSpotTypeChange = { state.activeQuickSpotTypeState.value = it },
        showQuickSpotSheet = state.showQuickSpotSheetState.value,
        onCloseQuickSpotSheet = { state.showQuickSpotSheetState.value = false },
        quickSpotLoading = state.quickSpotLoadingState.value,
        onQuickSpotLoadingChange = { state.quickSpotLoadingState.value = it },
        quickSpotMessage = state.quickSpotMessageState.value,
        onQuickSpotMessageChange = { state.quickSpotMessageState.value = it },
        quickSpotResults = state.quickSpotResultsState.value,
        onQuickSpotResultsChange = { state.quickSpotResultsState.value = it },
        selectedPoiSpot = state.selectedPoiSpotState.value,
        onSelectPoiSpot = { spot ->
            state.selectedPoiSpotState.value = spot
            if (spot != null) {
                state.showSpotPanelState.value = true
            }
        },
        onStartNavigation = startNavigation,
        calculatedRoutes = state.calculatedRouteOptionsState.value,
        showRoutePanel = state.showRoutePanelState.value,
        onCloseRoutePanel = {
            state.showRoutePanelState.value = false
            state.bottomTabState.value = CyclingTab.MAP
        },
        realElevProfile = state.realElevProfileState.value,
        routeSummary = state.routeSummaryState.value,
        navigationRoute = state.navigationRouteState.value,
        navStats = state.navStatsState.value,
        onOpenSearchFromRoutePanel = {
            state.showRoutePanelState.value = false
            state.bottomTabState.value = CyclingTab.MAP
            state.showDestinationSearchState.value = true
        },
        onQuickSpotTypeSelectFromSpotPanel = { type ->
            state.showSpotPanelState.value = false
            state.bottomTabState.value = CyclingTab.MAP
            state.activeQuickSpotTypeState.value = type
            state.showQuickSpotSheetState.value = true
        },
        onRouteToSpot = { handleSpotSelected(it) },
        showFavoritesOnlyInSheet = state.showFavoritesOnlyInSheetState.value,
        showRecordPanel = state.showRecordPanelState.value,
        isRecording = state.isRecordingState.value,
        gpxPointCount = state.gpxPointCountState.value,
        navigationProgress = state.navigationProgressState.value,
        hasLocationPermission = state.hasLocationPermissionState.value,
        gpxTrackOverlay = state.gpxTrackOverlayState.value,
        onGpxTrackOverlayChanged = { state.gpxTrackOverlayState.value = it },
        onWarningMessage = { state.warningMessageState.value = it },
        onCloseRecordPanel = {
            state.showRecordPanelState.value = false
            state.bottomTabState.value = CyclingTab.MAP
        },
        showCyclingSettings = state.showCyclingSettingsState.value,
        animationEnabled = state.animationEnabledState.value,
        onAnimationEnabledChange = { state.animationEnabledState.value = it },
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        batteryStatus = batteryStatus,
        autoRerouteEnabled = state.autoRerouteEnabledState.value,
        onAutoRerouteEnabledChange = { state.autoRerouteEnabledState.value = it },
        isHeadingUp = state.isHeadingUpState.value,
        onHeadingUpChange = { state.isHeadingUpState.value = it },
        orientationAnimator = orientationAnimator,
        onUpdateNavArrow = { state.updateNavArrow() },
        selectedLayer = state.selectedLayerState.value,
        onSelectedLayerChange = { state.selectedLayerState.value = it },
        poiVisible = state.poiVisibleState.value,
        onPoiVisibleChange = { state.poiVisibleState.value = it },
        poiSelected = state.poiSelectedState.value,
        onPoiToggleCategory = { cat ->
            val cur = state.poiSelectedState.value
            state.poiSelectedState.value = if (cat in cur) cur - cat else cur + cat
        },
        voiceGuidanceMode = state.voiceGuidanceModeState.value,
        onVoiceGuidanceModeChange = { state.voiceGuidanceModeState.value = it },
        voiceNavigator = state.voiceNavigator,
        onOpenPrefectureList = {
            state.showCyclingSettingsState.value = false
            state.bottomTabState.value = CyclingTab.MAP
            state.showPrefectureListDialogState.value = true
        },
        onOpenLicense = { state.showLicenseState.value = true },
        onOpenVersion = { state.showVersionState.value = true },
        onOpenDeveloperOptions = {
            state.showCyclingSettingsState.value = false
            state.showDeveloperOptionsState.value = true
        },
        onCloseCyclingSettings = {
            state.showCyclingSettingsState.value = false
            state.bottomTabState.value = CyclingTab.MAP
        },
        showLicense = state.showLicenseState.value,
        onDismissLicense = { state.showLicenseState.value = false },
        showVersion = state.showVersionState.value,
        onDismissVersion = { state.showVersionState.value = false },
        showDeveloperOptions = state.showDeveloperOptionsState.value,
        permissionLauncher = permissionLauncher,
        isRoutingBenchmarkRunning = state.isRoutingBenchmarkRunningState.value,
        benchmarkResult = state.routingBenchmarkResultState.value,
        onRunRoutingBenchmark = runRoutingBenchmark,
        onCloseDeveloperOptions = {
            state.showDeveloperOptionsState.value = false
            state.selectedMenuState.value = "地図"
        },
        showAreaDownloadConfirmDialog = state.showAreaDownloadConfirmDialogState.value,
        selectedAreaBounds = state.selectedAreaBoundsState.value,
        isDownloading = state.isDownloadingState.value,
        onStartAreaDownload = { areaBounds, sourceType ->
            state.isDownloadingState.value = true
            state.showAreaDownloadConfirmDialogState.value = false
            state.isAreaSelectModeState.value = false
            state.selectedAreaBoundsState.value = null
            state.areaDragStateState.value = AreaDragState()
            startAreaDownload(
                context = context,
                bounds = areaBounds,
                areaLabel = "選択エリア",
                sourceType = sourceType,
                source = if (sourceType == MapSourceType.GSI) gsiTileSource() else osmTileSource(),
                minZoom = 15,
                maxZoom = 16,
                onProgress = { progress ->
                    (context as? ComponentActivity)?.runOnUiThread {
                        state.currentTileProgressState.value = progress
                    }
                },
                onFinished = {
                    (context as? ComponentActivity)?.runOnUiThread {
                        state.isDownloadingState.value = false
                    }
                },
            )
        },
        onDismissAreaDownload = {
            state.showAreaDownloadConfirmDialogState.value = false
            state.isAreaSelectModeState.value = false
            state.selectedAreaBoundsState.value = null
            state.areaDragStateState.value = AreaDragState()
        },
        showDestinationSearch = state.showDestinationSearchState.value,
        onDismissDestinationSearch = { state.showDestinationSearchState.value = false },
        onDestinationSelected = { spot ->
            state.showDestinationSearchState.value = false
            handleSpotSelected(spot)
        },
        pendingSelectedSpot = state.pendingSelectedSpotState.value,
        onAddWaypoint = { spot ->
            val newWaypoints = state.waypointsState.value + spot
            state.waypointsState.value = newWaypoints
            state.pendingSelectedSpotState.value = null
            val loc = state.currentLocationState.value
            val start = if (loc != null) GeoPoint(loc.latitude, loc.longitude) else GeoPoint(FALLBACK_LATITUDE, FALLBACK_LONGITUDE)
            state.destinationState.value?.let { dest ->
                runRouteCalculation(start, dest, false, newWaypoints, null)
            }
        },
        onChangeDestination = { spot ->
            state.waypointsState.value = emptyList()
            state.pendingSelectedSpotState.value = null
            startRouteToDestination(spot)
        },
        onDismissPendingSpot = { state.pendingSelectedSpotState.value = null },
        showPrefectureListDialog = state.showPrefectureListDialogState.value,
        onDismissPrefectureListDialog = { state.showPrefectureListDialogState.value = false },
        onPrefectureSelected = { pref ->
            state.selectedPrefectureForActionState.value = pref
            state.showPrefectureListDialogState.value = false
            val isGsiDownloaded = DownloadStatusManager.isDownloaded(context, pref.id, MapSourceType.GSI)
            val isOsmDownloaded = DownloadStatusManager.isDownloaded(context, pref.id, MapSourceType.OSM)
            if (isGsiDownloaded || isOsmDownloaded) {
                state.showAlreadyDownloadedDialogState.value = true
            } else {
                state.showDownloadConfirmDialogState.value = true
            }
        },
        showAlreadyDownloadedDialog = state.showAlreadyDownloadedDialogState.value,
        selectedPrefectureForAction = state.selectedPrefectureForActionState.value,
        onRedownloadPrefecture = {
            state.showAlreadyDownloadedDialogState.value = false
            state.showDownloadConfirmDialogState.value = true
        },
        onDismissAlreadyDownloaded = { state.showAlreadyDownloadedDialogState.value = false },
        showDownloadConfirmDialog = state.showDownloadConfirmDialogState.value,
        onDownloadPrefectureGsi = { pref ->
            state.isDownloadingState.value = true
            state.showDownloadConfirmDialogState.value = false
            startPrefectureDownload(
                context = context,
                pref = pref,
                sourceType = MapSourceType.GSI,
                source = gsiTileSource(),
                onProgress = { progress ->
                    (context as? ComponentActivity)?.runOnUiThread {
                        state.currentTileProgressState.value = progress
                    }
                },
                onFinished = {
                    (context as? ComponentActivity)?.runOnUiThread {
                        state.isDownloadingState.value = false
                    }
                },
            )
        },
        onDownloadPrefectureOsm = { pref ->
            state.isDownloadingState.value = true
            state.showDownloadConfirmDialogState.value = false
            startPrefectureDownload(
                context = context,
                pref = pref,
                sourceType = MapSourceType.OSM,
                source = osmTileSource(),
                onProgress = { progress ->
                    (context as? ComponentActivity)?.runOnUiThread {
                        state.currentTileProgressState.value = progress
                    }
                },
                onFinished = {
                    (context as? ComponentActivity)?.runOnUiThread {
                        state.isDownloadingState.value = false
                    }
                },
            )
        },
        onDismissDownloadConfirm = { state.showDownloadConfirmDialogState.value = false },
        followLocation = state.followLocationState.value,
        locationMarker = state.locationMarkerState.value,
        accuracyCircle = state.accuracyCircleState.value,
        appLifecycleState = state.appLifecycleStateState.value,
        effectiveAnimationEnabled = effectiveAnimationEnabled,
        navArrowCache = state.navArrowCache,
        poiRefreshTick = state.poiRefreshTickState.intValue,
        isNavigationActive = state.isNavigationActiveState.value,
        addressController = state.addressController,
        onLocationLabelChanged = { state.locationLabelState.value = it },
        headingDegrees = state.headingDegreesState.floatValue,
    )
}
