package com.gorite.cyclemap.ui.state

import com.gorite.cyclemap.*
import com.gorite.cyclemap.utils.*
import com.gorite.cyclemap.navigation.*
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.SystemClock
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.gorite.cyclemap.data.AddressDisplayController
import com.gorite.cyclemap.data.ElevationRepository
import com.gorite.cyclemap.ui.cycling.ElevationSample
import com.gorite.cyclemap.data.MapSourceType
import com.gorite.cyclemap.data.Prefecture
import com.gorite.cyclemap.data.RoutingGraphSelection
import com.gorite.cyclemap.data.TileProgress
import com.gorite.cyclemap.power.BatteryMonitor
import com.gorite.cyclemap.power.BatteryStatus
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
import com.gorite.cyclemap.ui.theme.Motion
import com.gorite.cyclemap.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import org.maplibre.android.maps.MapLibreMap
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import com.gorite.cyclemap.net.ConnectivityState
import com.gorite.cyclemap.net.OnlineFeatureGate
import com.gorite.cyclemap.ui.map.MapViewModel
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock

internal class MapScreenState(
    val context: Context,
    val prefs: SharedPreferences,
    val scope: CoroutineScope,
    val lifecycleOwner: LifecycleOwner,
    val trackingController: TrackingStateController,
    val batteryMonitor: BatteryMonitor,
    val addressController: AddressDisplayController,
    val offRouteDetector: OffRouteDetector,
    val voiceNavigator: VoiceGuidanceNavigator,
    val elevationRepository: ElevationRepository,
    val gpsHealthMonitor: GpsHealthMonitor,
    val navArrowCache: NavArrowCache,
    val navArrowDrawable: Drawable?,
    val navDotDrawable: Drawable?,
    val drawerState: DrawerState,
    val isDrawerOpenState: MutableState<Boolean>,
    val graphLock: ReentrantReadWriteLock,
    val routeJobRef: AtomicReference<RouteCalculationJob?>,
    val pendingRouteRef: AtomicReference<RouteRequest?>,
    val switchRef: AtomicReference<((RoutingGraphSelection.Available) -> Unit)?>,
    val viewModel: MapViewModel,
    val connectivityState: ConnectivityState,
    val onlineFeatureGate: OnlineFeatureGate,
    // Mutable state holders
    val animationEnabledState: MutableState<Boolean>,
    val appLifecycleStateState: MutableState<AppLifecycleState>,
    val hasLocationPermissionState: MutableState<Boolean>,
    val isLocationServiceEnabledState: MutableState<Boolean>,
    val currentLocationState: MutableState<Location?>,
    val gpsStatusState: MutableState<GpsSignalStatus>,
    val lastValidLocationState: MutableState<Location?>,
    val lastValidRouteProgressMetersState: MutableState<Double?>,
    val lastValidBearingDegreesState: MutableFloatState,
    val selectedLayerState: MutableState<MapLayer>,
    val vectorMapState: MutableState<MapLibreMap?>,
    val mapViewState: MutableState<MapView?>,
    val locationMarkerState: MutableState<Marker?>,
    val accuracyCircleState: MutableState<AccuracyCircleOverlay?>,
    val destinationMarkerState: MutableState<Marker?>,
    val startMarkerState: MutableState<Marker?>,
    val waypointMarkersState: MutableState<List<Marker>>,
    val speedKmhState: MutableState<Double>,
    val headingDegreesState: MutableFloatState,
    val hasHeadingSensorFixState: MutableState<Boolean>,
    val isHeadingUpState: MutableState<Boolean>,
    val followLocationState: MutableState<Boolean>,
    val currentVectorTiltState: MutableDoubleState,
    val currentVectorBearingState: MutableDoubleState,
    val showPrefectureListDialogState: MutableState<Boolean>,
    val selectedPrefectureForActionState: MutableState<Prefecture?>,
    val showAlreadyDownloadedDialogState: MutableState<Boolean>,
    val showDownloadConfirmDialogState: MutableState<Boolean>,
    val targetDownloadSourceTypeState: MutableState<MapSourceType>,
    val currentTileProgressState: MutableState<TileProgress?>,
    val isDownloadingState: MutableState<Boolean>,
    val showLicenseState: MutableState<Boolean>,
    val showVersionState: MutableState<Boolean>,
    val showDeveloperOptionsState: MutableState<Boolean>,
    val bottomTabState: MutableState<CyclingTab>,
    val googleNavTabState: MutableState<GoogleNavTab>,
    val showRoutePanelState: MutableState<Boolean>,
    val showSpotPanelState: MutableState<Boolean>,
    val showFavoritesOnlyInSheetState: MutableState<Boolean>,
    val showRecordPanelState: MutableState<Boolean>,
    val showCyclingSettingsState: MutableState<Boolean>,
    val poiVisibleState: MutableState<Boolean>,
    val poiSelectedState: MutableState<Set<PoiCategory>>,
    val poiRefreshTickState: MutableIntState,
    val currentZoomLevelState: MutableDoubleState,
    val selectedPoiSpotState: MutableState<NearbySpot?>,
    val spotCategoryState: MutableState<SpotQuickCategory?>,
    val spotResultsState: MutableState<List<NearbySpot>>,
    val spotLoadingState: MutableState<Boolean>,
    val spotMessageState: MutableState<String?>,
    val activeQuickSpotTypeState: MutableState<QuickSpotType?>,
    val showQuickSpotSheetState: MutableState<Boolean>,
    val quickSpotResultsState: MutableState<List<NearbySpot>>,
    val quickSpotLoadingState: MutableState<Boolean>,
    val quickSpotMessageState: MutableState<String?>,
    val gpxHistoryState: MutableState<List<GpxHistoryEntry>>,
    val locationLabelState: MutableState<String>,
    val isRoutingBenchmarkRunningState: MutableState<Boolean>,
    val routingBenchmarkResultState: MutableState<String?>,
    val showDestinationSearchState: MutableState<Boolean>,
    val selectedMenuState: MutableState<String>,
    val mappedGraphState: MutableState<LazyMappedRoadGraph?>,
    val loadedGraphNameState: MutableState<String?>,
    val isSwitchingGraphState: MutableState<Boolean>,
    val warningMessageState: MutableState<String?>,
    val destinationState: MutableState<GeoPoint?>,
    val waypointsState: MutableState<List<GeoPoint>>,
    val pendingSelectedSpotState: MutableState<GeoPoint?>,
    val routeOverlayState: MutableState<Polyline?>,
    val gpxTrackOverlayState: MutableState<Polyline?>,
    val routeSummaryState: MutableState<RouteSummary?>,
    val calculatedRouteOptionsState: MutableState<List<com.gorite.cyclemap.contract.RouteOption>>,
    val selectedRoutePreferenceState: MutableState<RoutePreference>,
    val navigationRouteState: MutableState<List<RoutePoint>>,
    val navigationManeuversState: MutableState<List<Maneuver>>,
    val navigationInstructionsState: MutableState<List<RouteInstruction>>,
    val navigationProgressState: MutableState<RouteProgress?>,
    val isNavigationActiveState: MutableState<Boolean>,
    val isCalculatingRouteState: MutableState<Boolean>,
    val navStatsState: MutableState<NavigationStats?>,
    val navSpeedMpsState: MutableState<Double>,
    val navStartElapsedMsState: MutableState<Long>,
    val isReroutingState: MutableState<Boolean>,
    val isArrivedState: MutableState<Boolean>,
    val autoRerouteEnabledState: MutableState<Boolean>,
    val rerouteCountState: MutableIntState,
    val rerouteLimitWarnedState: MutableState<Boolean>,
    val lastRerouteElapsedMsState: MutableState<Long>,
    val realElevProfileState: MutableState<List<ElevationSample>>,
    val voiceGuidanceModeState: MutableState<VoiceGuidanceMode>,
    val isRecordingState: MutableState<Boolean>,
    val gpxPointCountState: MutableState<Int>,
    val gpxNotificationTextState: MutableState<String?>,
    val isDownloadingCorridorTilesState: MutableState<Boolean>,
    val corridorTileProgressTextState: MutableState<String?>,
    val isAreaSelectModeState: MutableState<Boolean>,
    val areaDragStateState: MutableState<AreaDragState>,
    val selectedAreaBoundsState: MutableState<BoundingBox?>,
    val showAreaDownloadConfirmDialogState: MutableState<Boolean>,
) {
    fun updateNavArrow() {
        val view = mapViewState.value ?: return
        val marker = locationMarkerState.value ?: return
        if (!marker.isFlat) marker.isFlat = true
        val location = currentLocationState.value
        val nowMs = SystemClock.elapsedRealtime()
        val fixAgeMs = nowMs - navArrowCache.lastFixElapsedMs
        val hwBearingOk = location != null && location.hasBearing() &&
            location.bearing in 0f..360f &&
            location.hasSpeed() && location.speed >= NAV_ARROW_MIN_SPEED_MPS &&
            fixAgeMs <= NAV_ARROW_BEARING_MAX_AGE_MS
        val courseAgeMs = nowMs - navArrowCache.courseElapsedMs
        val courseOk = navArrowCache.courseDeg.isFinite() &&
            courseAgeMs <= NAV_ARROW_BEARING_MAX_AGE_MS &&
            navArrowCache.courseSpeedMps.isFinite() &&
            navArrowCache.courseSpeedMps >= NAV_ARROW_MIN_SPEED_MPS
        val isMovingFast = (location != null && location.hasSpeed() && location.speed >= SPEED_HEADING_THRESHOLD_MPS) ||
            (navArrowCache.courseSpeedMps.isFinite() && navArrowCache.courseSpeedMps >= SPEED_HEADING_THRESHOLD_MPS) ||
            (speedKmhState.value >= SPEED_HEADING_THRESHOLD_KMH)

        val bearingDeg: Float? = when {
            hwBearingOk -> {
                val b = location!!.bearing
                navArrowCache.lastKnownBearingDeg = b
                b
            }
            courseOk -> {
                val b = navArrowCache.courseDeg
                navArrowCache.lastKnownBearingDeg = b
                b
            }
            isMovingFast && navArrowCache.lastKnownBearingDeg.isFinite() -> {
                navArrowCache.lastKnownBearingDeg
            }
            isHeadingUpState.value && headingDegreesState.floatValue.isFinite() -> {
                headingDegreesState.floatValue
            }
            navArrowCache.lastKnownBearingDeg.isFinite() -> {
                navArrowCache.lastKnownBearingDeg
            }
            headingDegreesState.floatValue.isFinite() && headingDegreesState.floatValue != 0f -> {
                headingDegreesState.floatValue
            }
            else -> null
        }
        if (bearingDeg != null) {
            var rotation = -bearingDeg
            rotation = ((rotation + 540f) % 360f) - 180f
            marker.rotation = rotation
            if (marker.icon !== navArrowDrawable) marker.icon = navArrowDrawable
        } else {
            marker.rotation = 0f
            if (marker.icon !== navDotDrawable) marker.icon = navDotDrawable
        }
        val lowAccuracy = location != null && location.hasAccuracy() &&
            location.accuracy > NAV_ARROW_LOW_ACCURACY_M
        marker.setAlpha(if (bearingDeg != null && lowAccuracy) NAV_ARROW_DIM_ALPHA else 1f)
        view.invalidate()
    }
}
