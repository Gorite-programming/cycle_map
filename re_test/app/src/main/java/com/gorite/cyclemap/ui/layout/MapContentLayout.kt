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
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalNavigationDrawer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
internal fun MapContentLayout(
    modifier: Modifier = Modifier,
    scope: CoroutineScope,
    drawerState: DrawerState,
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
    onPoiRefresh: () -> Unit,
    currentZoomLevel: Double,
    onZoomLevelChanged: (Double) -> Unit,
    onLongPress: (GeoPoint) -> Unit,
    currentVectorTilt: Double,
    onTiltChanged: (Double) -> Unit,
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
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            MapDrawerContent(
                selectedMenu = selectedMenu,
                onClose = { scope.launch { drawerState.close() } },
                onSearchClick = {
                    onMenuSelect("検索")
                    onOpenSearch()
                    scope.launch { drawerState.close() }
                },
                onDownloadClick = {
                    onMenuSelect("ダウンロード")
                    onOpenDownload()
                    scope.launch { drawerState.close() }
                },
                onLicenseClick = {
                    onMenuSelect("ライセンス")
                    onOpenLicense()
                    scope.launch { drawerState.close() }
                },
                onSettingsClick = {
                    onMenuSelect("設定")
                    onOpenSettings()
                    scope.launch { drawerState.close() }
                },
                onDeveloperOptionsClick = {
                    onMenuSelect("開発者")
                    onOpenDeveloperOptions()
                    scope.launch { drawerState.close() }
                },
                onVersionClick = {
                    onMenuSelect("バージョン情報")
                    onOpenVersion()
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val effectiveTopPadding = maxOf(systemInsets.calculateTopPadding(), statusBarTop)
        Column(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                MapViewsContainer(
                    modifier = Modifier.fillMaxSize(),
                    mapView = mapView,
                    selectedLayer = selectedLayer,
                    isAreaSelectMode = isAreaSelectMode,
                    areaDragState = areaDragState,
                    onAreaDragStateChange = onAreaDragStateChange,
                    onAreaSelected = onAreaSelected,
                    navigationRoute = navigationRoute,
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
                    onUserPan = { onFollowLocationChange(false) },
                    onPoiRefresh = onPoiRefresh,
                    onZoomLevelChanged = onZoomLevelChanged,
                    onPoiSpotDeselect = { onSelectPoiSpot(null) },
                    onLongPress = onLongPress,
                    onTiltChanged = onTiltChanged,
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
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSearchClick = onOpenSearch,
                    onAvatarClick = onOpenDeveloperOptions,
                    onHomeClick = onHomeClick,
                    onSelectQuickSpot = onSelectQuickSpot,
                    onSelectCategory = onSelectCategory,
                    notificationBannerGroup = notificationBanners,
                    navigationProgress = navigationProgress,
                    navigationInstructions = navigationInstructions,
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
                )
            }
        }
    }
}
