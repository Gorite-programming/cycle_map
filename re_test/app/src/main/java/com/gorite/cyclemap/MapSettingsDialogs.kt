package com.gorite.cyclemap

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Paint
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import com.gorite.cyclemap.power.BatteryStatus
import com.gorite.cyclemap.routing.NavigationStats
import com.gorite.cyclemap.routing.RouteProgress
import com.gorite.cyclemap.speech.VoiceGuidanceMode
import com.gorite.cyclemap.speech.VoiceGuidanceNavigator
import com.gorite.cyclemap.tracking.LocationTrackingService
import com.gorite.cyclemap.ui.DeveloperOptionsScreen
import com.gorite.cyclemap.ui.RoutingBenchmarkRequest
import com.gorite.cyclemap.ui.cycling.PoiCategory
import com.gorite.cyclemap.ui.cycling.RideHistorySheet
import com.gorite.cyclemap.ui.cycling.SettingsPanelSheet
import com.gorite.cyclemap.ui.theme.ThemeMode
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale

@Composable
internal fun MapSettingsDialogs(
    context: Context,
    prefs: SharedPreferences,
    // Record panel
    showRecordPanel: Boolean,
    isRecording: Boolean,
    gpxPointCount: Int,
    navStats: NavigationStats?,
    navigationProgress: RouteProgress?,
    hasLocationPermission: Boolean,
    mapView: MapView?,
    gpxTrackOverlay: Polyline?,
    onGpxTrackOverlayChanged: (Polyline?) -> Unit,
    onWarningMessage: (String) -> Unit,
    onCloseRecordPanel: () -> Unit,
    // Cycling settings
    showCyclingSettings: Boolean,
    animationEnabled: Boolean,
    onAnimationEnabledChange: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    batteryStatus: BatteryStatus,
    autoRerouteEnabled: Boolean,
    onAutoRerouteEnabledChange: (Boolean) -> Unit,
    isHeadingUp: Boolean,
    onHeadingUpChange: (Boolean) -> Unit,
    orientationAnimator: MapOrientationAnimator,
    onUpdateNavArrow: () -> Unit,
    selectedLayer: MapLayer,
    onSelectedLayerChange: (MapLayer) -> Unit,
    poiVisible: Boolean,
    onPoiVisibleChange: (Boolean) -> Unit,
    poiSelected: Set<PoiCategory>,
    onPoiToggleCategory: (PoiCategory) -> Unit,
    voiceGuidanceMode: VoiceGuidanceMode,
    onVoiceGuidanceModeChange: (VoiceGuidanceMode) -> Unit,
    voiceNavigator: VoiceGuidanceNavigator,
    onOpenPrefectureList: () -> Unit,
    onOpenLicense: () -> Unit,
    onOpenVersion: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onCloseCyclingSettings: () -> Unit,
    // License & Version
    showLicense: Boolean,
    onDismissLicense: () -> Unit,
    showVersion: Boolean,
    onDismissVersion: () -> Unit,
    // Developer Options
    showDeveloperOptions: Boolean,
    permissionLauncher: ActivityResultLauncher<Array<String>>,
    isRoutingBenchmarkRunning: Boolean,
    benchmarkResult: String?,
    onRunRoutingBenchmark: (RoutingBenchmarkRequest) -> Unit,
    onCloseDeveloperOptions: () -> Unit,
) {
    if (showRecordPanel) {
        val stats = navStats
        val gpxDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "CycleMap/gpx")
        RideHistorySheet(
            isRecording = isRecording,
            pointCount = gpxPointCount,
            rideSummary = stats?.let {
                "今回: ${formatRemaining((navigationProgress?.routeDistanceMeters ?: 0.0).coerceAtLeast(it.remainingMeters))}中 " +
                    "${formatRemaining((navigationProgress?.distanceFromStartMeters ?: 0.0))}走行 · " +
                    "${formatDuration(it.elapsedSeconds)}経過 · 平均${"%.1f".format(Locale.US, it.effectiveSpeedMps * 3.6)}km/h"
            },
            gpxDir = gpxDir,
            onStartRecording = {
                if (!hasLocationPermission) {
                    onWarningMessage("GPX記録には位置情報の許可が必要です")
                } else {
                    LocationTrackingService.startRecording(context)
                }
            },
            onStopRecording = {
                LocationTrackingService.stopRecording(context)
            },
            onShowTrackOnMap = { ride ->
                mapView?.let { view ->
                    gpxTrackOverlay?.let { view.overlays.remove(it) }
                    val polyline = Polyline(view).apply {
                        setPoints(ride.points.map { GeoPoint(it.latitude, it.longitude) })
                        outlinePaint.color = android.graphics.Color.parseColor("#00E5FF")
                        outlinePaint.strokeWidth = 14f
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.isAntiAlias = true
                    }
                    view.overlays.add(polyline)
                    onGpxTrackOverlayChanged(polyline)
                    if (ride.points.isNotEmpty()) {
                        val minLat = ride.points.minOf { it.latitude }
                        val maxLat = ride.points.maxOf { it.latitude }
                        val minLon = ride.points.minOf { it.longitude }
                        val maxLon = ride.points.maxOf { it.longitude }
                        view.zoomToBoundingBox(BoundingBox(maxLat, maxLon, minLat, minLon), true, 64)
                    }
                    view.invalidate()
                }
                onCloseRecordPanel()
            },
            onDeleteRide = {
                mapView?.let { view ->
                    gpxTrackOverlay?.let {
                        view.overlays.remove(it)
                        onGpxTrackOverlayChanged(null)
                        view.invalidate()
                    }
                }
            },
            onDismiss = onCloseRecordPanel,
        )
    }

    if (showCyclingSettings) {
        SettingsPanelSheet(
            animationEnabled = animationEnabled,
            onAnimationEnabledChange = { enabled ->
                onAnimationEnabledChange(enabled)
                prefs.edit().putBoolean("animation_enabled", enabled).apply()
            },
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            batteryStatus = batteryStatus,
            autoReroute = autoRerouteEnabled,
            onAutoReroute = onAutoRerouteEnabledChange,
            headingUp = isHeadingUp,
            onHeadingUp = {
                onHeadingUpChange(it)
                if (!it) {
                    orientationAnimator.reset()
                    onUpdateNavArrow()
                }
            },
            layerIndex = MapLayer.entries.indexOf(selectedLayer),
            layerLabels = MapLayer.entries.map { it.label },
            onLayerSelect = { onSelectedLayerChange(MapLayer.entries[it]) },
            poiVisible = poiVisible,
            onPoiVisible = onPoiVisibleChange,
            poiSelected = poiSelected,
            onPoiToggle = onPoiToggleCategory,
            voiceGuidanceMode = voiceGuidanceMode,
            onVoiceGuidanceModeChange = { mode ->
                onVoiceGuidanceModeChange(mode)
                voiceNavigator.setMode(mode)
            },
            hasLocationPermission = hasLocationPermission,
            onManageData = onOpenPrefectureList,
            onLicense = onOpenLicense,
            onVersion = onOpenVersion,
            onDeveloperOptions = onOpenDeveloperOptions,
            onDismiss = onCloseCyclingSettings,
        )
    }

    if (showLicense) {
        LicenseDialog(onDismiss = onDismissLicense)
    }

    if (showVersion) {
        VersionDialog(onDismiss = onDismissVersion)
    }

    if (showDeveloperOptions) {
        DeveloperOptionsScreen(
            hasLocationPermission = hasLocationPermission,
            onRequestLocationPermission = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ),
                )
            },
            isBenchmarkRunning = isRoutingBenchmarkRunning,
            benchmarkResult = benchmarkResult,
            onRunRoutingBenchmark = onRunRoutingBenchmark,
            onClose = onCloseDeveloperOptions,
        )
    }
}
