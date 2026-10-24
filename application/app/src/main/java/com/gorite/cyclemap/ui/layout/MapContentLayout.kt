package com.gorite.cyclemap.ui.layout

import com.gorite.cyclemap.*
import com.gorite.cyclemap.utils.*
import com.gorite.cyclemap.navigation.*
import android.Manifest
import android.content.Context
import android.graphics.drawable.Drawable
import android.location.Location
import com.gorite.cyclemap.ui.components.*
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.gorite.cyclemap.data.TileProgress
import com.gorite.cyclemap.routing.NavigationStats
import com.gorite.cyclemap.routing.OffRouteDetector
import com.gorite.cyclemap.routing.RouteInstruction
import com.gorite.cyclemap.routing.RoutePoint
import com.gorite.cyclemap.routing.RoutePreference
import com.gorite.cyclemap.routing.RouteProgress
import com.gorite.cyclemap.tracking.GpsSignalStatus
import com.gorite.cyclemap.ui.cycling.ElevationSample
import com.gorite.cyclemap.net.Link
import com.gorite.cyclemap.ui.cycling.CyclingTab
import com.gorite.cyclemap.ui.cycling.GoogleNavTab
import com.gorite.cyclemap.ui.cycling.NearbySpot
import com.gorite.cyclemap.ui.cycling.PoiCategory
import com.gorite.cyclemap.ui.cycling.QuickSpotType
import org.maplibre.android.maps.MapLibreMap
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
internal fun MapContentLayout(
    modifier: Modifier = Modifier,
    isDrawerOpenState: androidx.compose.runtime.MutableState<Boolean>,
    selectedMenu: String,
    onMenuSelect: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDownload: () -> Unit,
    onOpenLicense: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onOpenVersion: () -> Unit,
    systemInsets: PaddingValues,
    networkLink: Link = Link.Offline,
    // Map view container props
    mapView: MapView?,
    vectorMap: MapLibreMap?,
    selectedLayer: MapLayer,
    isAreaSelectMode: Boolean,
    onAreaSelectModeChange: (Boolean) -> Unit,
    areaDragState: AreaDragState,
    onAreaDragStateChange: (AreaDragState) -> Unit,
    onAreaSelected: (BoundingBox) -> Unit,
    navigationRoute: List<RoutePoint>,
    alternativeRoutes: List<List<GeoPoint>> = emptyList(),
    currentLocation: Location?,
    hasHeadingSensorFix: Boolean,
    headingDegrees: Float,
    isDarkTheme: Boolean,
    isLowPowerMode: Boolean,
    selectedPoiSpot: NearbySpot?,
    onSelectPoiSpot: (NearbySpot?) -> Unit,
    followLocation: Boolean,
    onFollowLocationChange: (Boolean) -> Unit,
    isNavigationActive: Boolean,
    poiVisible: Boolean,
    poiSelected: Set<PoiCategory>,
    navDotDrawable: Drawable?,
    onMapCreated: (MapView, Marker?, AccuracyCircleOverlay?) -> Unit,
    onMapDestroyed: () -> Unit = {},
    onPoiRefresh: () -> Unit,
    currentZoomLevel: Double,
    onZoomLevelChanged: (Double) -> Unit,
    onLongPress: (GeoPoint) -> Unit,
    currentVectorTilt: Double,
    onTiltChanged: (Double) -> Unit,
    currentVectorBearing: Double = 0.0,
    onBearingChanged: (Double) -> Unit = {},
    onVectorMapReady: (MapLibreMap) -> Unit,
    // Notification banners props
    effectiveAnimationEnabled: Boolean,
    hasLocationPermission: Boolean,
    permissionLauncher: ActivityResultLauncher<Array<String>>,
    gpsStatus: GpsSignalStatus,
    isLocationServiceEnabled: Boolean = true,
    hasReceivedFirstFix: Boolean,
    warningMessage: String?,
    onDismissWarning: () -> Unit,
    currentTileProgress: TileProgress?,
    onDismissTileProgress: () -> Unit,
    isSwitchingGraph: Boolean,
    isCalculatingRoute: Boolean,
    // Top overlays props
    onHomeClick: () -> Unit,
    onSelectQuickSpot: (QuickSpotType) -> Unit,
    onSelectCategory: (String) -> Unit,
    navigationProgress: RouteProgress?,
    navigationInstructions: List<RouteInstruction>,
    isRerouting: Boolean,
    offRouteDetector: OffRouteDetector,
    autoRerouteEnabled: Boolean,
    // Controls overlay props
    isHeadingUp: Boolean,
    onHeadingUpToggle: () -> Unit,
    onTargetClick: () -> Unit,
    toggleNextLayer: () -> Unit,
    // Bottom overlays props
    routeSummary: RouteSummary?,
    navStats: NavigationStats?,
    speedKmh: Double,
    isRecording: Boolean,
    gpxPointCount: Int,
    onWarningMessage: (String) -> Unit,
    onStopNavigation: () -> Unit,
    onClearRoute: () -> Unit,
    destination: GeoPoint?,
    realElevProfile: List<ElevationSample>,
    onStartNavigation: () -> Unit,
    downloadCorridorTiles: () -> Unit,
    corridorTileProgressText: String?,
    selectedRoutePreference: RoutePreference,
    onRoutePreferenceChange: (RoutePreference) -> Unit,
    isArrived: Boolean,
    locationLabel: String,
    googleNavTab: GoogleNavTab,
    onTabSelected: (GoogleNavTab) -> Unit,
    onExploreClick: () -> Unit,
    onSavedClick: () -> Unit,
    onRecordHistoryClick: () -> Unit,
    context: Context,
    isVoiceMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    weatherUiState: com.gorite.cyclemap.weather.WeatherUiState = com.gorite.cyclemap.weather.WeatherUiState.Unavailable,
    onRefreshWeather: () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val effectiveTopPadding = maxOf(systemInsets.calculateTopPadding(), statusBarTop)
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                MapViewsContainer(
                    modifier = Modifier.fillMaxSize(),
                    mapView = mapView,
                    vectorMap = vectorMap,
                    selectedLayer = selectedLayer,
                    isAreaSelectMode = isAreaSelectMode,
                    areaDragState = areaDragState,
                    onAreaDragStateChange = onAreaDragStateChange,
                    onAreaSelected = onAreaSelected,
                    navigationRoute = navigationRoute,
                    alternativeRoutes = alternativeRoutes,
                    destination = destination,
                    currentLocation = currentLocation,
                    hasHeadingSensorFix = hasHeadingSensorFix,
                    headingDegrees = headingDegrees,
                    isDarkTheme = isDarkTheme,
                    isLowPowerMode = isLowPowerMode,
                    selectedPoiSpot = selectedPoiSpot,
                    followLocation = followLocation,
                    isNavigationActive = isNavigationActive,
                    poiVisible = poiVisible,
                    poiSelected = poiSelected,
                    isLandscape = isLandscape,
                    systemInsets = systemInsets,
                    navDotDrawable = navDotDrawable,
                    gpsStatus = gpsStatus,
                    onMapCreated = onMapCreated,
                    onMapDestroyed = onMapDestroyed,
                    onUserPan = { onFollowLocationChange(false) },
                    onPoiRefresh = onPoiRefresh,
                    onZoomLevelChanged = onZoomLevelChanged,
                    onPoiSpotDeselect = { onSelectPoiSpot(null) },
                    onLongPress = onLongPress,
                    onTiltChanged = onTiltChanged,
                    onBearingChanged = onBearingChanged,
                    onPoiClick = onSelectPoiSpot,
                    onVectorMapReady = onVectorMapReady,
                )

                // DEF-01: 通知バナー群（通常時・案内時の共通表示）
                val notificationBanners: @Composable (Modifier) -> Unit = { bannerModifier ->
                    MapNotificationBanners(
                        modifier = bannerModifier,
                        effectiveAnimationEnabled = effectiveAnimationEnabled,
                        isAreaSelectMode = isAreaSelectMode,
                        onCancelAreaSelect = {
                            onAreaSelectModeChange(false)
                        },
                        hasLocationPermission = hasLocationPermission,
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ),
                            )
                        },
                        gpsStatus = gpsStatus,
                        hasReceivedFirstFix = hasReceivedFirstFix,
                        warningMessage = warningMessage,
                        onDismissWarning = onDismissWarning,
                        currentTileProgress = currentTileProgress,
                        onDismissTileProgress = onDismissTileProgress,
                        isSwitchingGraph = isSwitchingGraph,
                        isCalculatingRoute = isCalculatingRoute,
                        isLocationServiceEnabled = isLocationServiceEnabled,
                    )
                }

                MapTopOverlays(
                    isNavigationActive = isNavigationActive,
                    effectiveAnimationEnabled = effectiveAnimationEnabled,
                    effectiveTopPadding = effectiveTopPadding,
                    isLandscape = isLandscape,
                    systemInsets = systemInsets,
            onMenuClick = { isDrawerOpenState.value = true },
                    onSearchClick = onOpenSearch,
                    onAvatarClick = onOpenDeveloperOptions,
                    onHomeClick = onHomeClick,
                    onSelectQuickSpot = onSelectQuickSpot,
                    onSelectCategory = onSelectCategory,
                    notificationBannerGroup = notificationBanners,
                    navigationProgress = navigationProgress,
                    navigationInstructions = navigationInstructions,
                    gpsStatus = gpsStatus,
                    isRerouting = isRerouting,
                    offRouteDetector = offRouteDetector,
                    autoRerouteEnabled = autoRerouteEnabled,
                    networkLink = networkLink,
                    onStopNavigation = onStopNavigation,
                )

                // Right-Side Controls: Google Maps Style
                MapControlsOverlay(
                    isLandscape = isLandscape,
                    selectedLayer = selectedLayer,
                    networkLink = networkLink,
                    vectorMap = vectorMap,
                    mapView = mapView,
                    currentZoomLevel = currentZoomLevel,
                    currentVectorTilt = currentVectorTilt,
                    vectorBearing = currentVectorBearing,
                    headingDegrees = headingDegrees,
                    isHeadingUp = isHeadingUp,
                    onHeadingUpToggle = onHeadingUpToggle,
                    followLocation = followLocation,
                    onTargetClick = onTargetClick,
                    isNavigationActive = isNavigationActive,
                    isAreaSelectMode = isAreaSelectMode,
                    onAreaSelectToggle = {
                        val next = !isAreaSelectMode
                        onAreaSelectModeChange(next)
                    },
                    toggleNextLayer = toggleNextLayer,
                    onNavStartClick = onOpenSearch,
                    systemInsets = systemInsets,
                    effectiveTopPadding = effectiveTopPadding,
                )

                // Bottom Area: Active Navigation Footer or Dashboard
                MapBottomOverlays(
                    isNavigationActive = isNavigationActive,
                    routeSummary = routeSummary,
                    navStats = navStats,
                    speedKmh = speedKmh,
                    isRecording = isRecording,
                    gpxPointCount = gpxPointCount,
                    hasLocationPermission = hasLocationPermission,
                    onWarningMessage = onWarningMessage,
                    onStopNavigation = onStopNavigation,
                    onClearRoute = onClearRoute,
                    onShowLicense = onOpenLicense,
                    isLandscape = isLandscape,
                    systemInsets = systemInsets,
                    isCalculatingRoute = isCalculatingRoute,
                    isSwitchingGraph = isSwitchingGraph,
                    effectiveAnimationEnabled = effectiveAnimationEnabled,
                    destination = destination,
                    realElevProfile = realElevProfile,
                    navigationRoute = navigationRoute,
                    navigationProgress = navigationProgress,
                    onStartNavigation = onStartNavigation,
                    downloadCorridorTiles = downloadCorridorTiles,
                    corridorTileProgressText = corridorTileProgressText,
                    selectedRoutePreference = selectedRoutePreference,
                    onRoutePreferenceChange = onRoutePreferenceChange,
                    isArrived = isArrived,
                    locationLabel = locationLabel,
                    googleNavTab = googleNavTab,
                    onTabSelected = onTabSelected,
                    onExploreClick = onExploreClick,
                    onSavedClick = onSavedClick,
                    onRecordHistoryClick = onRecordHistoryClick,
                    context = context,
                    isVoiceMuted = isVoiceMuted,
                    onToggleMute = onToggleMute,
                    weatherUiState = weatherUiState,
                    onRefreshWeather = onRefreshWeather,
                )
            }
        }
        val show3dSimulationState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

        if (show3dSimulationState.value) {
            com.gorite.cyclemap.ui.simulation.Isometric3dSimulationDialog(
                onDismiss = { show3dSimulationState.value = false }
            )
        }

        MapDrawerOverlay(
            visible = isDrawerOpenState.value,
            onDismiss = { isDrawerOpenState.value = false },
        ) {
            MapDrawerContent(
                selectedMenu = selectedMenu,
                onClose = { isDrawerOpenState.value = false },
                onSearchClick = { isDrawerOpenState.value = false; onMenuSelect("検索"); onOpenSearch() },
                onDownloadClick = { isDrawerOpenState.value = false; onMenuSelect("ダウンロード"); onOpenDownload() },
                on3dSimulationClick = { isDrawerOpenState.value = false; onMenuSelect("3D"); show3dSimulationState.value = true },
                onLicenseClick = { isDrawerOpenState.value = false; onMenuSelect("ライセンス"); onOpenLicense() },
                onSettingsClick = { isDrawerOpenState.value = false; onMenuSelect("設定"); onOpenSettings() },
                onDeveloperOptionsClick = { isDrawerOpenState.value = false; onMenuSelect("開発者"); onOpenDeveloperOptions() },
                onVersionClick = { isDrawerOpenState.value = false; onMenuSelect("バージョン情報"); onOpenVersion() },
            )
        }
    }
}
