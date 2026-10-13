package com.gorite.cyclemap

import android.content.Context
import android.location.Location
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.gorite.cyclemap.routing.DeadReckoner
import com.gorite.cyclemap.routing.Maneuver
import com.gorite.cyclemap.routing.NavigationStats
import com.gorite.cyclemap.routing.OffRouteDetector
import com.gorite.cyclemap.routing.RouteInstruction
import com.gorite.cyclemap.routing.RoutePoint
import com.gorite.cyclemap.routing.RouteProgress
import com.gorite.cyclemap.routing.calculateRouteProgress
import com.gorite.cyclemap.routing.computeNavigationStats
import com.gorite.cyclemap.speech.VoiceGuidanceNavigator
import com.gorite.cyclemap.tracking.AppLifecycleState
import com.gorite.cyclemap.tracking.GpsHealthMonitor
import com.gorite.cyclemap.tracking.GpsSignalStatus
import com.gorite.cyclemap.tracking.TrackingStateController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.osmdroid.util.GeoPoint

@Composable
internal fun MapTrackingEffects(
    hasLocationPermission: Boolean,
    appLifecycleState: AppLifecycleState,
    isNavigationActive: Boolean,
    isRecording: Boolean,
    trackingController: TrackingStateController,
    context: Context,
    gpsHealthMonitor: GpsHealthMonitor,
    gpsStatus: GpsSignalStatus,
    onGpsStatusChanged: (GpsSignalStatus) -> Unit,
    onLocationChanged: (Location) -> Unit,
    lastValidLocation: Location?,
    onLastValidLocationChanged: (Location) -> Unit,
    lastValidBearingDegrees: Float,
    onLastValidBearingDegreesChanged: (Float) -> Unit,
    isHeadingUp: Boolean,
    speedKmh: Double,
    onSpeedChanged: (Double) -> Unit,
    navSpeedMps: Double,
    onNavSpeedChanged: (Double) -> Unit,
    navArrowCache: NavArrowCache,
    orientationAnimator: MapOrientationAnimator,
    onRecordingPointsChanged: (Boolean, Int) -> Unit,
    navigationRoute: List<RoutePoint>,
    navigationManeuvers: List<Maneuver>,
    navigationInstructions: List<RouteInstruction>,
    onNavigationProgressChanged: (RouteProgress?) -> Unit,
    lastValidRouteProgressMeters: Double?,
    onLastValidRouteProgressMetersChanged: (Double?) -> Unit,
    navStartElapsedMs: Long,
    onNavStatsChanged: (NavigationStats?) -> Unit,
    isArrived: Boolean,
    onArrivedChanged: (Boolean) -> Unit,
    onNavigationActiveChanged: (Boolean) -> Unit,
    offRouteDetector: OffRouteDetector,
    voiceNavigator: VoiceGuidanceNavigator,
    isRerouting: Boolean,
    autoRerouteEnabled: Boolean,
    destination: GeoPoint?,
    lastRerouteElapsedMs: Long,
    onTriggerReroute: (start: GeoPoint, goal: GeoPoint) -> Unit,
    headingDegrees: Float,
    onHeadingChanged: (Float) -> Unit,
    onHeadingSensorFixChanged: (Boolean) -> Unit,
) {
    // 位置追跡サービス (Foreground Service) の起動・停止ポリシー制御
    LaunchedEffect(hasLocationPermission, appLifecycleState, isNavigationActive, isRecording) {
        if (!hasLocationPermission) {
            if (trackingController.isTrackingActive) {
                trackingController.update(AppLifecycleState.PAUSED_OR_STOPPED, isNavigating = false, isRecording = false)
            }
            return@LaunchedEffect
        }
        trackingController.update(
            lifecycleState = appLifecycleState,
            isNavigating = isNavigationActive,
            isRecording = isRecording,
        )
    }

    if (hasLocationPermission) {
        ServiceLocationUpdates(
            context = context,
            onLocationChanged = { location, recording, points ->
                gpsHealthMonitor.onValidFix()
                onGpsStatusChanged(gpsHealthMonitor.status)
                onLocationChanged(location)
                onLastValidLocationChanged(location)
                if (location.hasBearing() && location.bearing != 0f) {
                    onLastValidBearingDegreesChanged(location.bearing)
                }
                if (isHeadingUp && (speedKmh >= SPEED_HEADING_THRESHOLD_KMH || (location.hasSpeed() && location.speed >= SPEED_HEADING_THRESHOLD_MPS))) {
                    val moveBearing = if (location.hasBearing() && location.bearing != 0f) {
                        location.bearing
                    } else if (navArrowCache.courseDeg.isFinite()) {
                        navArrowCache.courseDeg
                    } else if (lastValidBearingDegrees != 0f) {
                        lastValidBearingDegrees
                    } else null
                    if (moveBearing != null) {
                        orientationAnimator.rotateTo(moveBearing)
                    }
                }
                onRecordingPointsChanged(recording, points)
                if (isNavigationActive && navigationRoute.isNotEmpty()) {
                    val progress = calculateRouteProgress(
                        RoutePoint(location.latitude, location.longitude),
                        navigationRoute,
                        navigationManeuvers,
                    )
                    onNavigationProgressChanged(progress)
                    if (progress != null) {
                        onLastValidRouteProgressMetersChanged(progress.distanceFromStartMeters)
                    }
                    val stats = computeNavigationStats(
                        progress = progress,
                        smoothedSpeedMps = navSpeedMps,
                        navStartElapsedRealtimeMs = navStartElapsedMs,
                        nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                        nowEpochMillis = System.currentTimeMillis(),
                        arrivalRadiusMeters = ARRIVAL_RADIUS_METERS,
                    )
                    onNavStatsChanged(stats)
                    if (stats?.isArrived == true) {
                        if (!isArrived) {
                            onArrivedChanged(true)
                            onNavigationActiveChanged(false)
                            offRouteDetector.reset()
                            voiceNavigator.onLocationUpdated(
                                traveledMeters = progress?.distanceFromStartMeters ?: 0.0,
                                remainingMeters = 0.0,
                                instructions = navigationInstructions,
                                isOffRoute = false,
                            )
                        }
                    } else if (progress != null && !isRerouting) {
                        val offRoute = offRouteDetector.update(progress.distanceToRouteMeters)
                        val remaining = (progress.routeDistanceMeters - progress.distanceFromStartMeters).coerceAtLeast(0.0)
                        voiceNavigator.onLocationUpdated(
                            traveledMeters = progress.distanceFromStartMeters,
                            remainingMeters = remaining,
                            instructions = navigationInstructions,
                            isOffRoute = offRoute,
                        )
                        if (offRoute && autoRerouteEnabled) {
                            val now = SystemClock.elapsedRealtime()
                            val dest = destination
                            if (dest != null && now - lastRerouteElapsedMs >= REROUTE_MIN_INTERVAL_MS) {
                                onTriggerReroute(
                                    GeoPoint(location.latitude, location.longitude),
                                    dest,
                                )
                            }
                        }
                    }
                }
            },
            onSpeedChanged = { speed ->
                onSpeedChanged(speed * 3.6)
                onNavSpeedChanged(speed)
            },
            onGpsStatusChanged = { status ->
                if (status == GpsSignalStatus.WEAK) {
                    gpsHealthMonitor.onAccuracyDegraded()
                    onGpsStatusChanged(gpsHealthMonitor.status)
                } else if (status == GpsSignalStatus.HEALTHY) {
                    gpsHealthMonitor.onValidFix()
                    onGpsStatusChanged(gpsHealthMonitor.status)
                }
            },
        )
    }

    // GPS 測位状態のタイムアウト監視および推測移動 (Dead Reckoning)
    // ナビ中以外でバックグラウンド/画面オフのときは動かさない (バッテリー保護)。ナビ中は画面オフでも継続。
    LaunchedEffect(hasLocationPermission, isNavigationActive, navigationRoute, appLifecycleState) {
        if (!hasLocationPermission) return@LaunchedEffect
        if (!isNavigationActive && appLifecycleState != AppLifecycleState.RESUMED) return@LaunchedEffect
        while (isActive) {
            delay(1000L)
            val updated = gpsHealthMonitor.tick()
            if (gpsStatus != updated) {
                onGpsStatusChanged(updated)
            }

            // WEAK 状態かつ直近の有効位置がある場合、最大15秒間推測移動を実行
            val validLoc = lastValidLocation
            if (updated == GpsSignalStatus.WEAK && validLoc != null && gpsHealthMonitor.hasReceivedFirstFix) {
                val elapsedSec = (SystemClock.elapsedRealtime() - gpsHealthMonitor.lastFixMs) / 1000.0
                val speed = navSpeedMps.takeIf { it.isFinite() && it >= 0.0 } ?: validLoc.speed.toDouble()
                val drResult = DeadReckoner.estimate(
                    lastLocation = RoutePoint(validLoc.latitude, validLoc.longitude),
                    lastBearingDegrees = if (lastValidBearingDegrees != 0f) lastValidBearingDegrees.toDouble() else headingDegrees.toDouble(),
                    speedMps = speed,
                    elapsedSeconds = elapsedSec,
                    route = if (isNavigationActive) navigationRoute else emptyList(),
                    lastRouteProgressMeters = if (isNavigationActive) lastValidRouteProgressMeters else null,
                )

                if (drResult.distanceAdvancedMeters > 0.0) {
                    val estLocation = Location(validLoc).apply {
                        latitude = drResult.location.latitude
                        longitude = drResult.location.longitude
                        bearing = drResult.bearingDegrees.toFloat()
                        accuracy = 100f
                        this.speed = speed.toFloat()
                        time = System.currentTimeMillis()
                    }
                    onLocationChanged(estLocation)

                    if (isNavigationActive && navigationRoute.isNotEmpty()) {
                        val progress = calculateRouteProgress(
                            drResult.location,
                            navigationRoute,
                            navigationManeuvers,
                        )
                        if (progress != null) {
                            onNavigationProgressChanged(progress)
                            val stats = computeNavigationStats(
                                progress = progress,
                                smoothedSpeedMps = navSpeedMps,
                                navStartElapsedRealtimeMs = navStartElapsedMs,
                                nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                                nowEpochMillis = System.currentTimeMillis(),
                                arrivalRadiusMeters = ARRIVAL_RADIUS_METERS,
                            )
                            onNavStatsChanged(stats)
                            val remaining = (progress.routeDistanceMeters - progress.distanceFromStartMeters).coerceAtLeast(0.0)
                            voiceNavigator.onLocationUpdated(
                                traveledMeters = progress.distanceFromStartMeters,
                                remainingMeters = remaining,
                                instructions = navigationInstructions,
                                isOffRoute = false,
                            )
                        }
                    }
                }
            }
        }
    }

    CompassUpdates(
        context = context,
        enabled = (appLifecycleState == AppLifecycleState.RESUMED),
    ) { heading ->
        onHeadingSensorFixChanged(true)
        onHeadingChanged(heading)
        if (isHeadingUp) {
            // 5km/h以上の走行中はGPS進行方向に従うため、低速・停止時のみ地磁気コンパスで回転
            if (speedKmh < SPEED_HEADING_THRESHOLD_KMH) {
                orientationAnimator.rotateTo(heading)
            }
        }
    }
}
