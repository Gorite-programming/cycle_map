package com.gorite.cyclemap

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import com.gorite.cyclemap.ui.theme.Motion
import com.gorite.cyclemap.ui.theme.LocalAnimationEnabled
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gorite.cyclemap.data.AddressDisplayController
import com.gorite.cyclemap.data.DownloadStatusManager
import com.gorite.cyclemap.data.MapSourceType
import com.gorite.cyclemap.data.PrefectureData
import com.gorite.cyclemap.data.RoutingGraphSelection
import com.gorite.cyclemap.data.RoutingGraphSelector
import com.gorite.cyclemap.data.SearchDbReverseGeocoder
import com.gorite.cyclemap.data.SearchDbSelection
import com.gorite.cyclemap.data.SearchDbSelector
import com.gorite.cyclemap.data.TileDownloader
import com.gorite.cyclemap.data.TileProgress
import com.gorite.cyclemap.data.findPrefectureName
import com.gorite.cyclemap.routing.IconResolver
import com.gorite.cyclemap.routing.InstructionType
import com.gorite.cyclemap.routing.LazyMappedRoadGraph
import com.gorite.cyclemap.routing.NavigationStats
import com.gorite.cyclemap.routing.OffRouteDetector
import com.gorite.cyclemap.routing.RouteInstruction
import com.gorite.cyclemap.routing.RoutePoint
import com.gorite.cyclemap.routing.RouteProgress
import com.gorite.cyclemap.routing.HsaMode
import com.gorite.cyclemap.routing.HsaOptions
import com.gorite.cyclemap.routing.benchmarkMappedRouting
import com.gorite.cyclemap.routing.calculateRouteProgress
import com.gorite.cyclemap.routing.computeNavigationStats
import com.gorite.cyclemap.routing.extractManeuvers
import com.gorite.cyclemap.routing.DeadReckoner
import com.gorite.cyclemap.routing.MappedRouteResult
import com.gorite.cyclemap.routing.Maneuver
import com.gorite.cyclemap.routing.ManeuverType
import com.gorite.cyclemap.routing.RoutePreference
import com.gorite.cyclemap.speech.AndroidTextToSpeechEngine
import com.gorite.cyclemap.speech.HybridVoiceGuidanceEngine
import com.gorite.cyclemap.speech.ShikokuMetanAudioEngine
import com.gorite.cyclemap.speech.VoiceGuidanceMode
import com.gorite.cyclemap.speech.VoiceGuidanceNavigator
import com.gorite.cyclemap.tracking.GpsHealthMonitor
import com.gorite.cyclemap.tracking.GpsSignalStatus
import com.gorite.cyclemap.tracking.LocationTrackingService
import com.gorite.cyclemap.tracking.AppLifecycleState
import com.gorite.cyclemap.tracking.TrackingStateController
import com.gorite.cyclemap.ui.DeveloperOptionsScreen
import com.gorite.cyclemap.ui.RoutingBenchmarkRequest
import com.gorite.cyclemap.ui.cycling.CompassDial
import com.gorite.cyclemap.ui.cycling.CyclingBottomBar
import com.gorite.cyclemap.ui.cycling.CyclingNavy
import com.gorite.cyclemap.ui.cycling.CyclingPink
import com.gorite.cyclemap.ui.cycling.CyclingPinkLight
import com.gorite.cyclemap.ui.cycling.CyclingSubText
import com.gorite.cyclemap.ui.cycling.CyclingTab
import com.gorite.cyclemap.ui.cycling.DarkControlStack
import com.gorite.cyclemap.ui.cycling.ElevationProfileChart
import com.gorite.cyclemap.ui.cycling.GpxHistoryEntry
import com.gorite.cyclemap.ui.cycling.NearbySpot
import com.gorite.cyclemap.ui.cycling.PoiCategory
import com.gorite.cyclemap.ui.cycling.RecordPanelSheet
import com.gorite.cyclemap.ui.cycling.RideHistorySheet
import com.gorite.cyclemap.ui.cycling.RideDashboard
import com.gorite.cyclemap.ui.cycling.RideMetrics
import com.gorite.cyclemap.ui.cycling.RouteInfoCardContent
import com.gorite.cyclemap.ui.cycling.RoutePanelSheet
import com.gorite.cyclemap.ui.cycling.SettingsPanelSheet
import com.gorite.cyclemap.ui.cycling.SpeedChip
import com.gorite.cyclemap.ui.cycling.SpotPanelSheet
import com.gorite.cyclemap.ui.cycling.TopSearchBar
import com.gorite.cyclemap.ui.cycling.SpotQuickCategory
import com.gorite.cyclemap.ui.cycling.buildRouteElevationProfile
import com.gorite.cyclemap.ui.cycling.elevationAt
import com.gorite.cyclemap.ui.cycling.elevationGain
import com.gorite.cyclemap.ui.cycling.forwardGradeAt
import com.gorite.cyclemap.ui.cycling.gradeAt
import com.gorite.cyclemap.ui.cycling.gradeSummary
import com.gorite.cyclemap.ui.cycling.mockElevationProfile
import com.gorite.cyclemap.ui.theme.CycleMapTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// MapLayer / RouteSummary / Constants
// ---------------------------------------------------------------------------

private enum class MapLayer(val label: String) {
    GSI("地理院標準"),
    OSM("自転車向け"),
    TERRAIN("地形起伏"),
}

private data class RouteSummary(val distanceMeters: Double, val stepCount: Int)

private const val NAVIGATION_ZOOM = 16.0

// 回転式ナビ矢印の調整値
// osmdroid Marker は isFlat = true のとき、MapView.dispatchDraw の Canvas 回転に乗るため、
// 地図上の真北に対して時計回りに bearingDeg 度向けるには marker.rotation = -bearingDeg とする。
// これにより、ノースアップでもコンパス動的追従 (ヘディングアップ) でも、道路・進行方向に完全に一致する。
private const val NAV_ARROW_MIN_SPEED_MPS = 1.0f
private const val NAV_ARROW_BEARING_MAX_AGE_MS = 5_000L
private const val NAV_ARROW_LOW_ACCURACY_M = 50f
private const val NAV_ARROW_DIM_ALPHA = 0.45f
// 連続fixから移動方向を推定する際の条件。GPS誤差に埋もれる微動はノイズとして捨てる。
private const val NAV_COURSE_MIN_DIST_M = 5.0
private const val NAV_COURSE_MAX_DT_MS = 10_000L

/**
 * ナビ矢印の方位管理ホルダー (Compose stateにしない。recomposeを起こさず地図invalidateのみで反映)。
 * ハードウェア bearing が無いfixでも、連続したGPS位置から移動方向 (コース) を推定して矢印を出す。
 */
private class NavArrowCache {
    var lastFixElapsedMs: Long = 0L
    var courseDeg: Float = Float.NaN
    var courseElapsedMs: Long = 0L
    var courseSpeedMps: Float = Float.NaN
    var lastKnownBearingDeg: Float = Float.NaN
    private var baseLat = Double.NaN
    private var baseLon = Double.NaN
    private var baseElapsedMs: Long = 0L

    /** 新しいfixを食わせ、十分な移動があればコースを更新する。 */
    fun onFix(latitude: Double, longitude: Double, accuracyM: Float, nowMs: Long) {
        lastFixElapsedMs = nowMs
        if (!baseLat.isFinite()) {
            setBase(latitude, longitude, nowMs)
            return
        }
        val dtMs = nowMs - baseElapsedMs
        if (dtMs <= 0 || dtMs > NAV_COURSE_MAX_DT_MS) {
            // 基線が古すぎる (長時間静止など) → 今回を新たな基線にする
            setBase(latitude, longitude, nowMs)
            return
        }
        val distM = haversineMeters(baseLat, baseLon, latitude, longitude)
        val accuracy = if (accuracyM.isFinite()) accuracyM.toDouble() else 10.0
        // 誤差範囲内の微動ではコースを更新しない (基線は維持し、移動を蓄積する)
        if (distM >= maxOf(NAV_COURSE_MIN_DIST_M, accuracy * 2.0)) {
            courseDeg = initialBearingDeg(baseLat, baseLon, latitude, longitude).toFloat()
            courseElapsedMs = nowMs
            courseSpeedMps = (distM / dtMs * 1000.0).toFloat()
            setBase(latitude, longitude, nowMs)
        }
    }

    private fun setBase(latitude: Double, longitude: Double, nowMs: Long) {
        baseLat = latitude
        baseLon = longitude
        baseElapsedMs = nowMs
    }
}

// ナビ案内の調整値
private const val ARRIVAL_RADIUS_METERS = 30.0
private const val REROUTE_MIN_INTERVAL_MS = 10_000L
private const val REROUTE_MAX_COUNT = 5
private const val ROUTE_MAX_EXPANDED_NODES = 4_000_000
private const val FALLBACK_LATITUDE = 34.1785 // 山口市役所 (GPS不通時の起点)
private const val FALLBACK_LONGITUDE = 131.4737

/** 案内開始前の概算表示に使う想定速度。案内中のフォールバック (computeNavigationStats の既定値) と同一の15km/h。 */
private const val PRE_START_ASSUMED_SPEED_MPS = 15_000.0 / 3_600.0

private class RouteCalculationJob {    private val cancelled = AtomicBoolean(false)
    lateinit var thread: Thread

    fun cancel() {
        cancelled.set(true)
        if (::thread.isInitialized) thread.interrupt()
    }

    /** キャンセルしてスレッドが完全停止するまで待機する（最大 2 秒）。
     *  グラフの close() を呼ぶ前に必ず呼ぶこと。 */
    fun cancelAndJoin() {
        cancel()
        if (::thread.isInitialized) {
            try {
                thread.join(2_000L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    fun isCancelled(): Boolean = cancelled.get() || Thread.currentThread().isInterrupted
}

private data class RouteRequest(
    val start: GeoPoint,
    val goal: GeoPoint,
    val isReroute: Boolean,
    val waypoints: List<GeoPoint>?,
    val preference: RoutePreference?,
)

// ---------------------------------------------------------------------------
// MapScreen Composable
// ---------------------------------------------------------------------------

@Composable
internal fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("cyclemap_prefs", Context.MODE_PRIVATE) }
    var animationEnabled by remember {
        mutableStateOf(prefs.getBoolean("animation_enabled", true))
    }
    val systemAnimEnabled = remember(context) { Motion.isSystemAnimationEnabled(context) }
    val effectiveAnimationEnabled = Motion.resolveEffectiveAnimation(animationEnabled, systemAnimEnabled)

    val lifecycleOwner = LocalLifecycleOwner.current
    var appLifecycleState by remember {
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
                    appLifecycleState = AppLifecycleState.RESUMED
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    appLifecycleState = AppLifecycleState.PAUSED_OR_STOPPED
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

    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var currentLocation by remember { mutableStateOf<android.location.Location?>(null) }
    val gpsHealthMonitor = remember { GpsHealthMonitor() }
    var gpsStatus by remember { mutableStateOf(GpsSignalStatus.HEALTHY) }
    var lastValidLocation by remember { mutableStateOf<android.location.Location?>(null) }
    var lastValidRouteProgressMeters by remember { mutableStateOf<Double?>(null) }
    var lastValidBearingDegrees by remember { mutableFloatStateOf(0f) }
    var selectedLayer by remember { mutableStateOf(MapLayer.GSI) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var locationMarker by remember { mutableStateOf<Marker?>(null) }
    var accuracyCircle by remember { mutableStateOf<Polygon?>(null) }
    var destinationMarker by remember { mutableStateOf<Marker?>(null) }
    var speedKmh by remember { mutableStateOf(0.0) }
    var headingDegrees by remember { mutableFloatStateOf(0f) }
    var isHeadingUp by remember { mutableStateOf(true) }
    var followLocation by remember { mutableStateOf(true) }

    // 回転式ナビ矢印用 (recomposeを起こさない保持。更新は地図invalidateのみ)
    val navArrowCache = remember { NavArrowCache() }
    val navArrowDrawable = remember(context) {
        ContextCompat.getDrawable(context, R.drawable.ic_nav_arrow)
    }
    val navDotDrawable = remember(context) {
        ContextCompat.getDrawable(context, R.drawable.ic_location_dot)
    }
    // ナビ矢印の更新 (位置更新・コンパス回転アニメーション・方角変更から呼び出される)。
    // isFlat = true によりマーカーは地図上に平らに描画され、Canvas回転と同期する。
    // 方位源は ハードGPS bearing ＞ 推定コース ＞ コンパス動的時(端末heading) ＞ 直前有効進行方向。
    val updateNavArrow: () -> Unit = arrowUpdate@{
        val view = mapView ?: return@arrowUpdate
        val marker = locationMarker ?: return@arrowUpdate
        if (!marker.isFlat) marker.isFlat = true
        val location = currentLocation
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
            isHeadingUp && headingDegrees.isFinite() -> {
                headingDegrees
            }
            navArrowCache.lastKnownBearingDeg.isFinite() -> {
                navArrowCache.lastKnownBearingDeg
            }
            headingDegrees.isFinite() && headingDegrees != 0f -> {
                headingDegrees
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
    val latestNavArrow by rememberUpdatedState(updateNavArrow)
    val orientationAnimator = remember { MapOrientationAnimator({ mapView }, { latestNavArrow() }) }

    // Dialog & Download States
    var showPrefectureListDialog by remember { mutableStateOf(false) }
    var selectedPrefectureForAction by remember { mutableStateOf<com.gorite.cyclemap.data.Prefecture?>(null) }
    var showAlreadyDownloadedDialog by remember { mutableStateOf(false) }
    var showDownloadConfirmDialog by remember { mutableStateOf(false) }
    var targetDownloadSourceType by remember { mutableStateOf(MapSourceType.GSI) }

    var currentTileProgress by remember { mutableStateOf<TileProgress?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    var showVersion by remember { mutableStateOf(false) }
    var showDeveloperOptions by remember { mutableStateOf(false) }
    // サイクリングUI: 下部タブ + 各パネル
    var bottomTab by remember { mutableStateOf(CyclingTab.MAP) }
    var showRoutePanel by remember { mutableStateOf(false) }
    var showSpotPanel by remember { mutableStateOf(false) }
    var showRecordPanel by remember { mutableStateOf(false) }
    var showCyclingSettings by remember { mutableStateOf(false) }
    var poiVisible by remember { mutableStateOf(true) }
    var poiSelected by remember { mutableStateOf(PoiCategory.defaults()) }
    var poiRefreshTick by remember { mutableIntStateOf(0) }
    var currentZoomLevel by remember { mutableDoubleStateOf(15.0) }
    var startMarker by remember { mutableStateOf<Marker?>(null) }
    var spotCategory by remember { mutableStateOf<SpotQuickCategory?>(null) }
    var spotResults by remember { mutableStateOf<List<NearbySpot>>(emptyList()) }
    var spotLoading by remember { mutableStateOf(false) }
    var spotMessage by remember { mutableStateOf<String?>(null) }
    var gpxHistory by remember { mutableStateOf<List<GpxHistoryEntry>>(emptyList()) }
    var locationLabel by remember { mutableStateOf(AddressDisplayController.ADDRESS_LOADING) }
    // オフライン逆ジオコーディングの安定化コントローラ (住所判定ロジックは data 層に分離)
    val addressController = remember { AddressDisplayController() }
    var isRoutingBenchmarkRunning by remember { mutableStateOf(false) }
    var routingBenchmarkResult by remember { mutableStateOf<String?>(null) }
    var showDestinationSearch by remember { mutableStateOf(false) }
    var selectedMenu by remember { mutableStateOf("地図") }

    var mappedGraph by remember { mutableStateOf<LazyMappedRoadGraph?>(null) }
    var warningMessage by remember { mutableStateOf<String?>(null) }
    var destination by remember { mutableStateOf<GeoPoint?>(null) }
    var routeOverlay by remember { mutableStateOf<Polyline?>(null) }
    var gpxTrackOverlay by remember { mutableStateOf<Polyline?>(null) }
    var routeSummary by remember { mutableStateOf<RouteSummary?>(null) }
    var selectedRoutePreference by remember { mutableStateOf(RoutePreference.RECOMMENDED) }
    var waypoints by remember { mutableStateOf(emptyList<GeoPoint>()) }
    var waypointMarkers by remember { mutableStateOf(emptyList<Marker>()) }
    var pendingSelectedSpot by remember { mutableStateOf<GeoPoint?>(null) }
    var navigationRoute by remember { mutableStateOf<List<RoutePoint>>(emptyList()) }
    var navigationManeuvers by remember { mutableStateOf(emptyList<com.gorite.cyclemap.routing.Maneuver>()) }
    // ①基本経路指示: TurnClassifier による rich 案内。空なら従来の Maneuver 表示にフォールバック。
    var navigationInstructions by remember { mutableStateOf(emptyList<RouteInstruction>()) }
    var navigationProgress by remember { mutableStateOf<RouteProgress?>(null) }
    var isNavigationActive by remember { mutableStateOf(false) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    // ナビ案内セッション状態
    var navStats by remember { mutableStateOf<NavigationStats?>(null) }
    var navSpeedMps by remember { mutableStateOf(0.0) }
    var navStartElapsedMs by remember { mutableStateOf(0L) }
    var isRerouting by remember { mutableStateOf(false) }
    var isArrived by remember { mutableStateOf(false) }
    var autoRerouteEnabled by remember { mutableStateOf(true) }
    var rerouteCount by remember { mutableIntStateOf(0) }
    var rerouteLimitWarned by remember { mutableStateOf(false) }
    var lastRerouteElapsedMs by remember { mutableStateOf(0L) }
    val offRouteDetector = remember { OffRouteDetector() }
    val graphLock = remember { ReentrantReadWriteLock() }
    val routeJobRef = remember { AtomicReference<RouteCalculationJob?>(null) }

    // 音声案内ナビゲーター (VOICEVOX四国めたん + Android標準TTSのハイブリッド)
    var voiceGuidanceMode by remember { mutableStateOf(VoiceGuidanceMode.VOICEVOX) }
    val metanAudioEngine = remember(context) { ShikokuMetanAudioEngine(context) }
    val voiceNavigator = remember(context, metanAudioEngine) {
        val tts = AndroidTextToSpeechEngine(context)
        val hybrid = HybridVoiceGuidanceEngine(metanAudioEngine, tts, voiceGuidanceMode)
        VoiceGuidanceNavigator(hybrid)
    }

    LaunchedEffect(voiceGuidanceMode) {
        voiceNavigator.setMode(voiceGuidanceMode)
    }

    DisposableEffect(context) {
        onDispose {
            voiceNavigator.shutdown()
        }
    }

    // 県別グラフ選択の状態。loadedGraphName が現在 routing に使う実ファイル。
    var loadedGraphName by remember { mutableStateOf<String?>(null) }
    var isSwitchingGraph by remember { mutableStateOf(false) }
    val pendingRouteRef = remember { AtomicReference<RouteRequest?>(null) }
    // runRouteCalculation より後で定義する startGraphSwitch への参照 (相互参照のため)。
    val switchRef = remember { AtomicReference<((RoutingGraphSelection.Available) -> Unit)?>(null) }

    var isRecording by remember { mutableStateOf(false) }
    var gpxPointCount by remember { mutableStateOf(0) }
    var gpxNotificationText by remember { mutableStateOf<String?>(null) }

    // 経路回廊タイル事前ダウンロード状態
    var isDownloadingCorridorTiles by remember { mutableStateOf(false) }
    var corridorTileProgressText by remember { mutableStateOf<String?>(null) }

    // Area-select state for high-zoom (z15-z16) tile download
    var isAreaSelectMode by remember { mutableStateOf(false) }
    var areaDragState by remember { mutableStateOf(AreaDragState()) }
    var selectedAreaBounds by remember { mutableStateOf<BoundingBox?>(null) }
    var showAreaDownloadConfirmDialog by remember { mutableStateOf(false) }

    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val latestGraph by rememberUpdatedState(mappedGraph)
    val latestLocation by rememberUpdatedState(currentLocation)
    val latestRouteOverlay by rememberUpdatedState(routeOverlay)
    val latestDestMarker by rememberUpdatedState(destinationMarker)
    val latestStartMarker by rememberUpdatedState(startMarker)
    val latestWaypoints by rememberUpdatedState(waypoints)
    val latestWaypointMarkers by rememberUpdatedState(waypointMarkers)
    val latestRoutePreference by rememberUpdatedState(selectedRoutePreference)
    val latestMapView by rememberUpdatedState(mapView)
    /**
     * ルート計算の共通実行部。通常探索と自動リルートの両方から使う。
     * @param isReroute trueの場合は目的地マーカー・案内状態を維持し、現在地起点で再探索する。
     */
    val runRouteCalculation: (start: GeoPoint, goal: GeoPoint, isReroute: Boolean, waypointsOverride: List<GeoPoint>?, preferenceOverride: RoutePreference?) -> Unit = calc@{ start, goal, isReroute, waypointsOverride, preferenceOverride ->
        routeJobRef.getAndSet(null)?.cancel()
        if (!isReroute) {
            destination = goal
        }
        if (waypointsOverride != null) {
            waypoints = waypointsOverride
        }
        isCalculatingRoute = true
        if (isReroute) {
            isRerouting = true
        }
        val graph = latestGraph
        Log.i(
            "CycleMapRoute",
            "ROUTE_REQUEST isReroute=$isReroute start=${start.latitude},${start.longitude} " +
                "goal=${goal.latitude},${goal.longitude} graphLoaded=${graph != null}",
        )
        // 県別グラフ選択: routing開始点の県を要求する (現在地の県=graphの県を保証)。
        // 他県グラフの使い回しは禁止。県境跨ぎは別タスク (到達不能時は既存警告)。
        when (val selection = RoutingGraphSelector.select(start.latitude, start.longitude)) {
            is RoutingGraphSelection.Unavailable -> {
                isCalculatingRoute = false
                isRerouting = false
                val prefLabel = selection.prefectureName ?: "判定不能地域"
                warningMessage = "${prefLabel}のルートグラフがありません"
                Log.e("CycleMapRoute", "ROUTE_FAILURE reason=graph_not_available prefecture=$prefLabel")
                return@calc
            }
            is RoutingGraphSelection.Available -> {
                val goalPref = findPrefectureName(goal.latitude, goal.longitude)
                Log.i(
                    "CycleMapRoute",
                    "GRAPH_SELECT location=${start.latitude},${start.longitude} " +
                        "prefecture=${selection.prefectureName} file=${selection.fileName} " +
                        "loaded=$loadedGraphName goalPrefecture=${goalPref ?: "(unknown)"}",
                )
                if (goalPref != null && goalPref != selection.prefectureName) {
                    Log.w(
                        "CycleMapRoute",
                        "CROSS_PREFECTURE note=unsupported startPref=${selection.prefectureName} goalPref=$goalPref",
                    )
                }
                if (loadedGraphName != selection.fileName || graph == null) {
                    pendingRouteRef.set(RouteRequest(start, goal, isReroute, waypointsOverride, preferenceOverride))
                    if (!isSwitchingGraph) switchRef.get()?.invoke(selection)
                    isCalculatingRoute = false
                    isRerouting = false
                    warningMessage = "${selection.prefectureName}のグラフに切替中です"
                    Log.i(
                        "CycleMapRoute",
                        "ROUTE_FAILURE reason=graph-switching prefecture=${selection.prefectureName} " +
                            "file=${selection.fileName}",
                    )
                    return@calc
                }
            }
        }

        val currentWaypoints = waypointsOverride ?: latestWaypoints
        if (!isReroute) {
            (context as? ComponentActivity)?.runOnUiThread {
                val view = latestMapView ?: return@runOnUiThread
                latestDestMarker?.let { view.overlays.remove(it) }
                latestStartMarker?.let { view.overlays.remove(it) }
                latestWaypointMarkers.forEach { view.overlays.remove(it) }
                val startM = Marker(view).apply {
                    position = start
                    title = "スタート"
                    icon = startPointDrawable(context)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                view.overlays.add(startM)
                startMarker = startM

                val wpMarkers = currentWaypoints.mapIndexed { idx, wp ->
                    Marker(view).apply {
                        position = wp
                        title = "経由地 ${idx + 1}"
                        icon = waypointDrawable(context, idx + 1)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                }
                wpMarkers.forEach { view.overlays.add(it) }
                waypointMarkers = wpMarkers

                val destM = Marker(view).apply {
                    position = goal
                    title = "目的地"
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_destination)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                view.overlays.add(destM)
                destinationMarker = destM
                view.controller.animateTo(goal)
                view.invalidate()
            }
        }

        val routeJob = RouteCalculationJob()
        routeJobRef.set(routeJob)
        // 切替後に古いgraphの結果をUIへ反映しないための世代札。
        val jobGraphName = loadedGraphName
        val jobGraph = graph
        routeJob.thread = Thread {
            try {
                if (routeJob.isCancelled()) return@Thread
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

                val pref = preferenceOverride ?: latestRoutePreference
                for (i in 0 until stops.size - 1) {
                    if (routeJob.isCancelled()) return@Thread
                    val legStart = stops[i]
                    val legGoal = stops[i + 1]
                    val legResult = graphLock.read {
                        if (routeJob.isCancelled()) return@Thread
                        val startIndex = graph.nearestNodeIndex(legStart.latitude, legStart.longitude)
                        if (routeJob.isCancelled()) return@Thread
                        val goalIndex = graph.nearestNodeIndex(legGoal.latitude, legGoal.longitude)
                        Log.i(
                            "CycleMapRoute",
                            "ROUTE_SNAP leg=$i startNode=$startIndex goalNode=$goalIndex pref=$pref",
                        )
                        if (routeJob.isCancelled()) return@Thread
                        graph.route(
                            startIndex,
                            goalIndex,
                            isCancelled = { routeJob.isCancelled() },
                            maxExpandedNodes = ROUTE_MAX_EXPANDED_NODES,
                            preference = pref,
                        )
                    }
                    if (routeJob.isCancelled() || legResult.wasCancelled) return@Thread
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
                    nodeIds = allNodeIds,
                    totalDistanceMeters = totalDist,
                    totalCost = totalCostVal,
                    coordinates = allCoordinates,
                    wasCancelled = false,
                    wasTruncated = isAnyTruncated,
                )
                if (routeJob.isCancelled()) return@Thread
                if (result.wasCancelled) return@Thread
                Log.i(
                    "CycleMapRoute",
                    "ROUTE_RESULT success=${result.isReachable} truncated=${result.wasTruncated} " +
                        "routeNodeCount=${result.nodeIds.size} routeDistanceM=${result.totalDistanceMeters.toInt()}",
                )
                // ①基本経路指示をバックグラウンドで構築 (graphLock.read は取得済み扱いで再取得)。
                val richInstructions = graphLock.read {
                    if (routeJob.isCancelled()) return@read emptyList<RouteInstruction>()
                    try {
                        // 探索時と同じ graph インスタンスを使う (latestGraph ではなく capture 済み graph)。
                        buildRouteInstructions(graph, result.nodeIds, result.coordinates)
                    } catch (t: Throwable) {
                        Log.w("CycleMapInstruction", "rich instruction build failed, using maneuvers", t)
                        emptyList()
                    }
                }

                (context as? ComponentActivity)?.runOnUiThread {
                    if (routeJob.isCancelled() || routeJobRef.get() !== routeJob) return@runOnUiThread
                    if (loadedGraphName != jobGraphName || mappedGraph !== jobGraph) {
                        Log.e("CycleMapRoute", "ROUTE_FAILURE reason=graph-changed jobGraph=$jobGraphName now=$loadedGraphName")
                        isCalculatingRoute = false
                        isRerouting = false
                        routeJobRef.compareAndSet(routeJob, null)
                        return@runOnUiThread
                    }
                    if (result.wasTruncated) {
                        Log.e("CycleMapRoute", "ROUTE_FAILURE reason=truncated-expanded-nodes")
                        warningMessage = "探索範囲が大きすぎて打ち切りました。近めの目的地でお試しください"
                        isCalculatingRoute = false
                        isRerouting = false
                        routeJobRef.compareAndSet(routeJob, null)
                        return@runOnUiThread
                    }
                    if (!result.isReachable) {
                        Log.e("CycleMapRoute", "ROUTE_FAILURE reason=unreachable isReroute=$isReroute")
                        warningMessage = if (isReroute) {
                            "再探索で有効なルートが見つかりませんでした。元のルートを維持します"
                        } else {
                            "有効な自転車ルートが見つかりませんでした"
                        }
                        isCalculatingRoute = false
                        isRerouting = false
                        routeJobRef.compareAndSet(routeJob, null)
                        return@runOnUiThread
                    }
                    val activity = context as? ComponentActivity
                    val view = latestMapView
                    if (activity?.isFinishing == true || activity?.isDestroyed == true ||
                        view == null || !view.isAttachedToWindow
                    ) {
                        Log.e(
                            "CycleMapRoute",
                            "ROUTE_FAILURE reason=view-not-ready finishing=${activity?.isFinishing} " +
                                "destroyed=${activity?.isDestroyed} viewNull=${view == null} " +
                                "attached=${view?.isAttachedToWindow}",
                        )
                        isCalculatingRoute = false
                        isRerouting = false
                        routeJobRef.compareAndSet(routeJob, null)
                        return@runOnUiThread
                    }
                    isCalculatingRoute = false
                    isRerouting = false
                    warningMessage = null
                    routeJobRef.compareAndSet(routeJob, null)
                    latestRouteOverlay?.let { view?.overlays?.remove(it) }
                    val polyline = Polyline(view).apply {
                        setPoints(result.coordinates.map { (lat, lon) -> GeoPoint(lat, lon) })
                        outlinePaint.color = android.graphics.Color.parseColor("#EC407A")
                        outlinePaint.strokeWidth = 24f // サイクリング中の高視認性向上 (14f -> 24f)
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.isAntiAlias = true
                    }
                    view.overlays.add(polyline)
                    routeOverlay = polyline
                    view.invalidate()
                    navigationRoute = result.coordinates.map { (lat, lon) -> RoutePoint(lat, lon) }
                    navigationManeuvers = extractManeuvers(navigationRoute)
                    navigationInstructions = richInstructions
                    navigationProgress = null
                    navStats = null
                    offRouteDetector.reset()
                    if (!isReroute) {
                        // ルート確定: 案内は開始ボタンで開始する (自動開始しない)。
                        // 開始前はルート詳細 (標高グラフ付き) を確認できる。
                        isNavigationActive = false
                        isArrived = false
                        rerouteCount = 0
                        rerouteLimitWarned = false
                        lastRerouteElapsedMs = 0L
                        offRouteDetector.reset()
                    } else {
                        // リルート成功: 新ルート起点で平均速度の計測をやり直す
                        navStartElapsedMs = SystemClock.elapsedRealtime()
                        rerouteCount++ // BUG-30 fix: 成功時にカウント
                    }
                    routeSummary = RouteSummary(result.totalDistanceMeters, result.nodeIds.size - 1)
                    // BUG-28 fix: 成功時に切替中警告をクリア
                    if (warningMessage?.contains("グラフに切替中です") == true) {
                        warningMessage = null
                    }
                    Log.i(
                        "CycleMapRoute",
                        "ROUTE_DISPLAYED graph=$jobGraphName polylinePointCount=${result.coordinates.size} " +
                            "instructionCount=${richInstructions.size} maneuverCount=${navigationManeuvers.size} " +
                            "routeDisplayed=true guidanceState=isNavigationActive=$isNavigationActive",
                    )
                    isCalculatingRoute = false
                    isRerouting = false
                    routeJobRef.compareAndSet(routeJob, null)
                }
            } catch (t: Throwable) {
                if (routeJob.isCancelled()) return@Thread
                Log.e("CycleMapGraph", "route failed", t)
                Log.e("CycleMapRoute", "ROUTE_FAILURE reason=exception exception=${t::class.java.simpleName}:${t.message}")
                (context as? ComponentActivity)?.runOnUiThread {
                    if (routeJob.isCancelled() || routeJobRef.get() !== routeJob) return@runOnUiThread
                    warningMessage = "ルート探索失敗: ${t.message}"
                    isCalculatingRoute = false
                    isRerouting = false
                    routeJobRef.compareAndSet(routeJob, null)
                }
            }
        }
        routeJob.thread.start()
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
        if (destination != null) {
            pendingSelectedSpot = point
        } else {
            startRouteToDestination(point)
        }
    }

    /**
     * 県グラフ切替。routing要求時 (runRouteCalculation) と現在地更新時 (先読み) から呼ばれる。
     * - 二重起動しない (isSwitchingGraph。launch前の同期セットで判定～起動の隙を塞ぐ)。
     * - 切替開始時点で計算中フラグの所有権を引き取る (殺されるjob側は黙って死ぬため。BUG-23)。
     *   保留要求の再実行時に runRouteCalculation が立て直す。
     * - 先行routingは cancelAndJoin で完全停止してから旧graphを外す。
     * - 旧graphの close は DisposableEffect(mappedGraph) の dispose に任せ、二重closeしない。
     * - 成功時は loadedGraphName 更新→保留要求を再実行。失敗時は保留を破棄して明示失敗。
     */
    val startGraphSwitch: (RoutingGraphSelection.Available) -> Unit = switch@{ selection ->
        if (isSwitchingGraph || loadedGraphName == selection.fileName) return@switch
        val dataDir = cycleMapDataDir(context)
        val resolvedFiles = RoutingGraphSelector.resolveGraphFiles(dataDir, selection.fileName)
        if (resolvedFiles == null) {
            pendingRouteRef.set(null)
            warningMessage = "${selection.prefectureName}のグラフがありません:\n${selection.fileName}\n配置先: ${dataDir.absolutePath}"
            Log.e(
                "CycleMapRoute",
                "ROUTE_FAILURE reason=graph_not_available prefecture=${selection.prefectureName} file=${selection.fileName}",
            )
            return@switch
        }
        val (graphFile, graphIndex) = resolvedFiles
        // 同期確定: 以降の要求・位置更新はこの切替が終わるまで待つ (BUG-25 の二重起動も塞ぐ)。
        isSwitchingGraph = true
        // 殺す側がフラグを回収する。jobスレッドは cancel 後に黙って抜けるだけ。
        isCalculatingRoute = false
        isRerouting = false
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
                // BUG-27 fix: mappedGraphとloadedGraphNameの更新をMainスレッドで同一タイミングに固定
                graphLock.write { mappedGraph = loaded.first }
                loadedGraphName = selection.fileName
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
                warningMessage = "${selection.prefectureName}のグラフを読み込めませんでした: ${t.message}"
                Log.e(
                    "CycleMapRoute",
                    "ROUTE_FAILURE reason=graph-load-failed file=${selection.fileName} " +
                        "exception=${t::class.java.simpleName}:${t.message}",
                )
            } finally {
                isSwitchingGraph = false
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
            warningMessage = "ルートグラフが見つかりません\n配置先: ${dataDir.absolutePath}"
            Log.e("CycleMapRoute", "GRAPH_SELECT startup loaded=false reason=missing dir=${dataDir.absolutePath}")
        } else {
            val (graphFile, graphIndex) = resolvedFiles
            isSwitchingGraph = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val before = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                    val start = SystemClock.elapsedRealtime()
                    val graph = LazyMappedRoadGraph.load(graphFile, graphIndex)
                    val elapsed = SystemClock.elapsedRealtime() - start
                    val after = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                    Triple(graph, elapsed, after.totalPss - before.totalPss)
                }
                mappedGraph = result.first
                loadedGraphName = graphFile.name
                Log.i("CycleMapGraph", "mappedLoadMs=${result.second} pssDeltaKb=${result.third} nodes=${result.first.nodeCount} edges=${result.first.edgeCount}")
                Log.i("CycleMapRoute", "GRAPH_SELECT startup file=${graphFile.name} loaded=true nodes=${result.first.nodeCount}")
            } catch (t: Throwable) {
                warningMessage = "ルートグラフを読み込めませんでした: ${t.message}"
                Log.e("CycleMapRoute", "ROUTE_FAILURE reason=graph-load-failed file=${graphFile.name} exception=${t::class.java.simpleName}:${t.message}")
            } finally {
                isSwitchingGraph = false
            }
        }
    }

    // 現在地の県に応じた先読み切替 (本命は routing要求時の判定。ここでは位置確定を待って寄せる)。
    LaunchedEffect(currentLocation) {
        val loc = currentLocation ?: return@LaunchedEffect
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
                // routing要求時に graph_not_available で明示失敗させる。ここでは何もしない。
            }
        }
    }

    DisposableEffect(mappedGraph) {
        // キー変化時はキャプチャした旧インスタンスだけを閉じる (現stateを読むと新graphを誤って閉じる)。
        val graphToDispose = mappedGraph
        onDispose {
            // BUG-26 fix: Mainスレッドで 2秒ブロックする cancelAndJoin ではなく cancel() で即時解除
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
                        isRecording = true
                        gpxPointCount = 0
                        gpxNotificationText = "記録を開始しました"
                    }
                    LocationTrackingService.ACTION_RECORDING_SAVED -> {
                        isRecording = false
                        val path = intent.getStringExtra(LocationTrackingService.EXTRA_PATH).orEmpty()
                        val points = intent.getIntExtra(LocationTrackingService.EXTRA_POINTS, 0)
                        gpxPointCount = points
                        val fileName = File(path).name
                        gpxNotificationText = "GPX保存完了 ($points 点): $fileName"
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

    LaunchedEffect(navigationRoute, mapView, isNavigationActive) {
        if (navigationRoute.isEmpty()) return@LaunchedEffect
        val view = mapView ?: return@LaunchedEffect
        // 案内中はapply側でNAVIGATION_ZOOMへ寄せているため全体表示ズームはしない
        if (isNavigationActive) return@LaunchedEffect
        withFrameNanos { }
        val points = navigationRoute.map { GeoPoint(it.latitude, it.longitude) }
        val bounds = BoundingBox.fromGeoPoints(points)
        view.zoomToBoundingBox(bounds, true, 64)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

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
                gpsStatus = gpsHealthMonitor.status
                currentLocation = location
                lastValidLocation = location
                if (location.hasBearing() && location.bearing != 0f) {
                    lastValidBearingDegrees = location.bearing
                }
                isRecording = recording
                if (recording) gpxPointCount = points
                if (isNavigationActive && navigationRoute.isNotEmpty()) {
                    val progress = calculateRouteProgress(
                        RoutePoint(location.latitude, location.longitude),
                        navigationRoute,
                        navigationManeuvers,
                    )
                    navigationProgress = progress
                    if (progress != null) {
                        lastValidRouteProgressMeters = progress.distanceFromStartMeters
                    }
                    navStats = computeNavigationStats(
                        progress = progress,
                        smoothedSpeedMps = navSpeedMps,
                        navStartElapsedRealtimeMs = navStartElapsedMs,
                        nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                        nowEpochMillis = System.currentTimeMillis(),
                        arrivalRadiusMeters = ARRIVAL_RADIUS_METERS,
                    )
                    if (navStats?.isArrived == true) {
                        if (!isArrived) {
                            isArrived = true
                            isNavigationActive = false
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
                            if (dest != null && rerouteCount < REROUTE_MAX_COUNT &&
                                now - lastRerouteElapsedMs >= REROUTE_MIN_INTERVAL_MS
                            ) {
                                lastRerouteElapsedMs = now
                                runRouteCalculation(
                                    GeoPoint(location.latitude, location.longitude),
                                    dest,
                                    true,
                                    null,
                                    null,
                                )
                            } else if (dest != null && rerouteCount >= REROUTE_MAX_COUNT && !rerouteLimitWarned) {
                                rerouteLimitWarned = true
                                warningMessage = "自動リルートの上限に達しました。手動で目的地を再設定してください"
                            }
                        }
                    }
                }
            },
            onSpeedChanged = { speed ->
                speedKmh = speed * 3.6
                navSpeedMps = speed
            },
            onGpsStatusChanged = { status ->
                if (status == GpsSignalStatus.WEAK) {
                    gpsHealthMonitor.onAccuracyDegraded()
                    gpsStatus = gpsHealthMonitor.status
                } else if (status == GpsSignalStatus.HEALTHY) {
                    gpsHealthMonitor.onValidFix()
                    gpsStatus = gpsHealthMonitor.status
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
                gpsStatus = updated
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
                    val estLocation = android.location.Location(validLoc).apply {
                        latitude = drResult.location.latitude
                        longitude = drResult.location.longitude
                        bearing = drResult.bearingDegrees.toFloat()
                        accuracy = 100f
                        this.speed = speed.toFloat()
                        time = System.currentTimeMillis()
                    }
                    currentLocation = estLocation

                    if (isNavigationActive && navigationRoute.isNotEmpty()) {
                        val progress = calculateRouteProgress(
                            drResult.location,
                            navigationRoute,
                            navigationManeuvers,
                        )
                        if (progress != null) {
                            navigationProgress = progress
                            navStats = computeNavigationStats(
                                progress = progress,
                                smoothedSpeedMps = navSpeedMps,
                                navStartElapsedRealtimeMs = navStartElapsedMs,
                                nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                                nowEpochMillis = System.currentTimeMillis(),
                                arrivalRadiusMeters = ARRIVAL_RADIUS_METERS,
                            )
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
        headingDegrees = heading
        if (isHeadingUp) {
            orientationAnimator.rotateTo(heading)
        }
    }

    val startNavigation: () -> Unit = {
        val loc = currentLocation
        Log.i(
            "CycleMapRoute",
            "GUIDANCE_START currentLocation=${if (loc != null) "${loc.latitude},${loc.longitude} acc=${loc.accuracy}" else "(null)"} " +
                "destination=${destination?.let { "${it.latitude},${it.longitude}" } ?: "(null)"} " +
                "routeNodeCount=${navigationRoute.size} instructionCount=${navigationInstructions.size} " +
                "wasActive=$isNavigationActive",
        )
        if (navigationRoute.isNotEmpty()) {
            followLocation = true
            navStartElapsedMs = SystemClock.elapsedRealtime()
            rerouteCount = 0
            rerouteLimitWarned = false
            lastRerouteElapsedMs = 0L
            isArrived = false
            navStats = null
            offRouteDetector.reset()
            voiceNavigator.reset()
            isNavigationActive = true
            mapView?.controller?.setZoom(NAVIGATION_ZOOM)
            currentLocation?.let { location ->
                mapView?.let { view ->
                    view.controller.animateTo(GeoPoint(location.latitude, location.longitude))
                }
                navigationProgress = calculateRouteProgress(
                    RoutePoint(location.latitude, location.longitude),
                    navigationRoute,
                    navigationManeuvers,
                )
            }
            Log.i("CycleMapRoute", "GUIDANCE_STATE isNavigationActive=true routeDisplayed=true")
        } else {
            // 以前は無音no-opだったため「押しても何も起きない」に見えた。理由を明示する。
            Log.e("CycleMapRoute", "ROUTE_FAILURE reason=start-with-empty-route")
            warningMessage = "ルートがまだ確定していません。探索完了をお待ちください"
        }
    }

    val clearRoute: () -> Unit = {
        routeJobRef.getAndSet(null)?.cancel()
        pendingRouteRef.set(null)
        mapView?.let { view ->
            routeOverlay?.let { view.overlays.remove(it) }
            gpxTrackOverlay?.let { view.overlays.remove(it) }
            destinationMarker?.let { view.overlays.remove(it) }
            startMarker?.let { view.overlays.remove(it) }
            waypointMarkers.forEach { view.overlays.remove(it) }
            view.invalidate()
        }
        routeOverlay = null
        gpxTrackOverlay = null
        destinationMarker = null
        startMarker = null
        waypointMarkers = emptyList()
        waypoints = emptyList()
        routeSummary = null
        destination = null
        navigationRoute = emptyList()
        navigationManeuvers = emptyList()
        navigationInstructions = emptyList()
        navigationProgress = null
        navStats = null
        isNavigationActive = false
        isCalculatingRoute = false
        isRerouting = false
        isArrived = false
        rerouteCount = 0
        rerouteLimitWarned = false
        lastRerouteElapsedMs = 0L
        offRouteDetector.reset()
        voiceNavigator.reset()
        isDownloadingCorridorTiles = false
        corridorTileProgressText = null
    }

    val downloadCorridorTiles: () -> Unit = {
        if (!isDownloadingCorridorTiles && navigationRoute.isNotEmpty()) {
            isDownloadingCorridorTiles = true
            corridorTileProgressText = "準備中…"
            val points = navigationRoute.map { Pair(it.latitude, it.longitude) }
            val source = when (selectedLayer) {
                MapLayer.GSI -> gsiTileSource()
                MapLayer.OSM -> osmTileSource()
                MapLayer.TERRAIN -> gsiReliefTileSource()
            }
            val sourceType = when (selectedLayer) {
                MapLayer.GSI -> com.gorite.cyclemap.data.MapSourceType.GSI
                MapLayer.OSM -> com.gorite.cyclemap.data.MapSourceType.OSM
                MapLayer.TERRAIN -> com.gorite.cyclemap.data.MapSourceType.GSI
            }
            Thread {
                try {
                    TileDownloader.downloadRouteCorridor(
                        context = context,
                        routePoints = points,
                        label = "ルート地図",
                        sourceType = sourceType,
                        source = source,
                        minZoom = 14,
                        maxZoom = 16,
                        bufferTiles = 1,
                        onProgress = { progress ->
                            corridorTileProgressText = "${progress.completed}/${progress.total}"
                        },
                        onDone = { total ->
                            corridorTileProgressText = "保存完了 (${total}枚)"
                            isDownloadingCorridorTiles = false
                        },
                    )
                } catch (e: Exception) {
                    Log.e("CycleMap", "Corridor tile download failed", e)
                    corridorTileProgressText = "失敗"
                    isDownloadingCorridorTiles = false
                }
            }.start()
        }
    }

    val runRoutingBenchmark: (RoutingBenchmarkRequest) -> Unit = { request ->
        if (!isRoutingBenchmarkRunning) {
            isRoutingBenchmarkRunning = true
            routingBenchmarkResult = null
            scope.launch {
                try {
                    val result = withContext(Dispatchers.Default) {
                        graphLock.read {
                            mappedGraph?.let { graph ->
                                val startIndex = graph.nearestNodeIndex(request.startLatitude, request.startLongitude)
                                val goalIndex = graph.nearestNodeIndex(request.goalLatitude, request.goalLongitude)
                                benchmarkMappedRouting(
                                    graph = graph,
                                    startIndex = startIndex,
                                    goalIndex = goalIndex,
                                    options = HsaOptions.fromMode(request.mode),
                                )
                            }
                        }
                    }
                    val sampleText = result?.joinToString("\n") { sample ->
                        val memoryMb = sample.peakHeapIncreaseBytes / (1024.0 * 1024.0)
                        "${sample.algorithm}: %.3fs / %.1fMB / %.1fkm".format(
                            Locale.US,
                            sample.elapsedSeconds,
                            memoryMb,
                            sample.route.totalDistanceMeters / 1_000.0,
                        ) + if (sample.algorithm.startsWith("HSA")) {
                            " / segments=${sample.segmentCount} fallback=${sample.usedFallback}" +
                                sample.fallbackReason?.let { " reason=$it" }.orEmpty()
                        } else {
                            ""
                        } + " / expanded=${sample.expandedNodes}"
                    } ?: "ルートグラフが読み込まれていません"
                    val deltaText = result?.firstOrNull { it.algorithm == "A*" }?.let { aStar ->
                        result.firstOrNull { it.algorithm.startsWith("HSA") }?.let { hsa ->
                            "差分: 時間 %+.3fs / メモリ %+.1fMB / 距離 %+.1fkm / 展開 %+.0f".format(
                                Locale.US,
                                hsa.elapsedSeconds - aStar.elapsedSeconds,
                                (hsa.peakHeapIncreaseBytes - aStar.peakHeapIncreaseBytes) / (1024.0 * 1024.0),
                                (hsa.route.totalDistanceMeters - aStar.route.totalDistanceMeters) / 1_000.0,
                                (hsa.expandedNodes - aStar.expandedNodes).toDouble(),
                            )
                        }
                    }
                    val resultText = listOfNotNull(sampleText, deltaText).joinToString("\n")
                    val logPath = result?.let { samples ->
                        saveRoutingBenchmarkLog(context, request, resultText, samples)
                    }
                    routingBenchmarkResult = if (logPath != null) {
                        "$resultText\nログ保存: ${logPath.name}"
                    } else {
                        resultText
                    }
                } catch (t: Throwable) {
                    Log.e("CycleMapBenchmark", "Benchmark failed", t)
                    routingBenchmarkResult = "ベンチマーク失敗: ${t.message}"
                } finally {
                    isRoutingBenchmarkRunning = false
                }
            }
        }
    }

    CompositionLocalProvider(LocalAnimationEnabled provides effectiveAnimationEnabled) {
        ModalNavigationDrawer(
            drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "CycleMap",
                        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Button(
                        onClick = { scope.launch { drawerState.close() } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(52.dp),
                    ) {
                        Text("メニューを閉じる", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    NavigationDrawerItem(
                        label = { Text("目的地検索") },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_lucide_search),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        selected = selectedMenu == "検索",
                        onClick = {
                            selectedMenu = "検索"
                            showDestinationSearch = true
                            scope.launch { drawerState.close() }
                        },
                    )
                    NavigationDrawerItem(
                        label = { Text("地図ダウンロード（47都道府県）") },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_lucide_download),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        selected = selectedMenu == "ダウンロード",
                        onClick = {
                            selectedMenu = "ダウンロード"
                            showPrefectureListDialog = true
                            scope.launch { drawerState.close() }
                        },
                    )
                    NavigationDrawerItem(
                        label = { Text("ライセンス") },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_lucide_scale),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        selected = selectedMenu == "ライセンス",
                        onClick = {
                            selectedMenu = "ライセンス"
                            showLicense = true
                            scope.launch { drawerState.close() }
                        },
                    )
                    NavigationDrawerItem(
                        label = { Text("設定") },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_lucide_settings),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        selected = selectedMenu == "設定",
                        onClick = {
                            selectedMenu = "設定"
                            showCyclingSettings = true
                            scope.launch { drawerState.close() }
                        },
                    )
                    NavigationDrawerItem(
                        label = { Text("開発者オプション") },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_lucide_wrench),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        selected = selectedMenu == "開発者",
                        onClick = {
                            selectedMenu = "開発者"
                            showDeveloperOptions = true
                            scope.launch { drawerState.close() }
                        },
                    )
                    NavigationDrawerItem(
                        label = { Text("バージョン情報") },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_lucide_info),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        selected = selectedMenu == "バージョン情報",
                        onClick = {
                            selectedMenu = "バージョン情報"
                            showVersion = true
                            scope.launch { drawerState.close() }
                        },
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        },
    ) {
        Column(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
            // MapView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapContext ->
                    Configuration.getInstance().userAgentValue = mapContext.packageName
                    configureOsmdroid(mapContext, cycleMapDataDir(mapContext))
                    CycleMapView(mapContext).apply {
                        isClickable = true
                        isFocusable = true
                        isNestedScrollingEnabled = false
                        // HARDWARE: GPU合成でポリゴン描画を最小化
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)
                        setTilesScaledToDpi(true) // 高DPI画面(S21等)で背景道路・文字を太く拡大描画
                        minZoomLevel = 2.0
                        maxZoomLevel = 21.0
                        isHorizontalMapRepetitionEnabled = false
                        isVerticalMapRepetitionEnabled = false
                        isFlingEnabled = true
                        overlayManager.tilesOverlay.apply {
                            loadingBackgroundColor = android.graphics.Color.parseColor("#F4F1EA")
                            loadingLineColor = android.graphics.Color.parseColor("#C5CBD3")
                            // 読み込み中タイルを薄く表示（ポリゴン化を抑制）
                            setLoadingDrawable(null)
                        }
                        setTileSource(gsiTileSource())
                        // メモリキャッシュ容量をConfigurationと合わせて拡張
                        tileProvider.ensureCapacity(512)
                        controller.setZoom(13.0)
                        controller.setCenter(GeoPoint(34.18, 131.47))
                        locationMarker = Marker(this).apply {
                            title = "現在地"
                            // 初期はドットのみ。矢印への切替は updateNavArrow が行う。
                            icon = navDotDrawable
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            isFlat = true
                        }
                        // Googleマップ風の精度円 (薄青フィルタ+青枠)。現在地ドットの下に重ねる。
                        accuracyCircle = Polygon().apply {
                            fillPaint.color = android.graphics.Color.parseColor("#221A73E8")
                            outlinePaint.color = android.graphics.Color.parseColor("#661A73E8")
                            outlinePaint.strokeWidth = 3f
                            isVisible = false
                        }
                        onUserPan = { followLocation = false }
                        // POIオーバーレイ更新用: ズーム・スクロール確定でリフレッシュ要求
                        addMapListener(
                            object : MapListener {
                                override fun onScroll(event: ScrollEvent?): Boolean {
                                    poiRefreshTick++
                                    return true
                                }

                                override fun onZoom(event: ZoomEvent?): Boolean {
                                    poiRefreshTick++
                                    event?.zoomLevel?.let { currentZoomLevel = it }
                                    return true
                                }
                            },
                        )
                        overlays.add(accuracyCircle)
                        overlays.add(locationMarker)
                        overlays.add(
                            MapEventsOverlay(
                                object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean = false

                                    override fun longPressHelper(p: GeoPoint): Boolean {
                                        if (areaSelectMode) return false
                                        handleSpotSelected(p)
                                        return true
                                    }
                                },
                            ),
                        )
                        mapView = this
                    }
                },
                update = { view ->
                    (view as? CycleMapView)?.areaSelectMode = isAreaSelectMode
                },
            )

            // Area-select touch capture + rectangle drawing overlay (Compose layer, above map)
            if (isAreaSelectMode) {
                val latestMapViewForArea by rememberUpdatedState(mapView)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .pointerInput(isAreaSelectMode) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    areaDragState = AreaDragState(
                                        anchorX = offset.x,
                                        anchorY = offset.y,
                                        currentX = offset.x,
                                        currentY = offset.y,
                                        active = true,
                                    )
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    areaDragState = areaDragState.copy(
                                        currentX = change.position.x,
                                        currentY = change.position.y,
                                    )
                                },
                                onDragEnd = {
                                    val view = latestMapViewForArea
                                    if (view != null) {
                                        val drag = areaDragState
                                        val left = minOf(drag.anchorX, drag.currentX)
                                        val right = maxOf(drag.anchorX, drag.currentX)
                                        val top = minOf(drag.anchorY, drag.currentY)
                                        val bottom = maxOf(drag.anchorY, drag.currentY)
                                        // Only accept drag with at least 20px in each axis
                                        if ((right - left) > 20f && (bottom - top) > 20f) {
                                            val topLeftGeo = view.projection.fromPixels(left.toInt(), top.toInt()) as GeoPoint
                                            val bottomRightGeo = view.projection.fromPixels(right.toInt(), bottom.toInt()) as GeoPoint
                                            selectedAreaBounds = BoundingBox(
                                                topLeftGeo.latitude,       // latNorth
                                                bottomRightGeo.longitude,  // lonEast
                                                bottomRightGeo.latitude,   // latSouth
                                                topLeftGeo.longitude,      // lonWest
                                            )
                                            showAreaDownloadConfirmDialog = true
                                        }
                                    }
                                    areaDragState = areaDragState.copy(active = false)
                                },
                                onDragCancel = {
                                    areaDragState = areaDragState.copy(active = false)
                                },
                            )
                        }
                        .drawWithContent {
                            drawContent()
                            if (areaDragState.active) {
                                val drag = areaDragState
                                val left = minOf(drag.anchorX, drag.currentX)
                                val right = maxOf(drag.anchorX, drag.currentX)
                                val top = minOf(drag.anchorY, drag.currentY)
                                val bottom = maxOf(drag.anchorY, drag.currentY)
                                // Semi-transparent fill
                                drawRect(
                                    color = Color(0x330066FF),
                                    topLeft = Offset(left, top),
                                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                )
                                // Solid border
                                drawRect(
                                    color = Color(0xFF0066FF),
                                    topLeft = Offset(left, top),
                                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }
                        },
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !isNavigationActive,
                enter = Motion.topBarEnter(effectiveAnimationEnabled),
                exit = Motion.topBarExit(effectiveAnimationEnabled),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .zIndex(2f),
            ) {
                // Top Search Bar (menu + 地名 + 検索)
                TopSearchBar(
                    locationLabel = locationLabel,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSearchClick = { showDestinationSearch = true },
                )
            }

            val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

            // Right-Side Controls: Compass + Layers/Target/Zoom stack (dark)
            // 横画面時は上部に横並び配置して、下部パネルとの潜り込み・重なりを完全に防止する
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            end = 12.dp,
                            top = if (isNavigationActive) 12.dp else 68.dp,
                        )
                        .zIndex(2f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Area-select (high-zoom download) toggle button (案内中は非表示)
                    if (!isNavigationActive) {
                        Surface(
                            shape = if (isAreaSelectMode) RoundedCornerShape(22.dp) else CircleShape,
                            color = if (isAreaSelectMode) MaterialTheme.colorScheme.tertiary else CyclingNavy.copy(alpha = 0.94f),
                            shadowElevation = 6.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        isAreaSelectMode = !isAreaSelectMode
                                        if (!isAreaSelectMode) {
                                            areaDragState = AreaDragState()
                                            selectedAreaBounds = null
                                        }
                                    }
                                    .padding(horizontal = if (isAreaSelectMode) 10.dp else 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    modifier = Modifier.size(48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_lucide_square_dashed),
                                        contentDescription = "エリア選択",
                                        tint = if (isAreaSelectMode) MaterialTheme.colorScheme.onTertiary else Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                if (isAreaSelectMode) {
                                    Text(
                                        text = "選択中",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    CompassDial(
                        headingDegrees = { headingDegrees },
                        headingUp = isHeadingUp,
                        onClick = {
                            isHeadingUp = !isHeadingUp
                            if (!isHeadingUp) {
                                mapView?.setMapOrientation(0f)
                                updateNavArrow()
                            }
                        },
                    )

                    val canZoomIn = currentZoomLevel < (mapView?.maxZoomLevel ?: 21.0)
                    val canZoomOut = currentZoomLevel > (mapView?.minZoomLevel ?: 2.0)
                    DarkControlStack(
                        layerLabel = when (selectedLayer) {
                            MapLayer.GSI -> "標準"
                            MapLayer.OSM -> "自転車"
                            MapLayer.TERRAIN -> "地形"
                        },
                        following = followLocation,
                        canZoomIn = canZoomIn,
                        canZoomOut = canZoomOut,
                        horizontal = true,
                        onLayerClick = {
                            val layers = MapLayer.entries
                            selectedLayer = layers[(layers.indexOf(selectedLayer) + 1) % layers.size]
                        },
                        onTargetClick = {
                            followLocation = true
                            currentLocation?.let { loc ->
                                mapView?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                            }
                        },
                        onZoomIn = { mapView?.controller?.zoomIn(150L) },
                        onZoomOut = { mapView?.controller?.zoomOut(150L) },
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .zIndex(2f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    CompassDial(
                        headingDegrees = { headingDegrees },
                        headingUp = isHeadingUp,
                        onClick = {
                            isHeadingUp = !isHeadingUp
                            if (!isHeadingUp) {
                                mapView?.setMapOrientation(0f)
                                updateNavArrow()
                            }
                        },
                    )
                    val canZoomIn = currentZoomLevel < (mapView?.maxZoomLevel ?: 21.0)
                    val canZoomOut = currentZoomLevel > (mapView?.minZoomLevel ?: 2.0)
                    DarkControlStack(
                        layerLabel = when (selectedLayer) {
                            MapLayer.GSI -> "標準"
                            MapLayer.OSM -> "自転車"
                            MapLayer.TERRAIN -> "地形"
                        },
                        following = followLocation,
                        canZoomIn = canZoomIn,
                        canZoomOut = canZoomOut,
                        horizontal = false,
                        onLayerClick = {
                            val layers = MapLayer.entries
                            selectedLayer = layers[(layers.indexOf(selectedLayer) + 1) % layers.size]
                        },
                        onTargetClick = {
                            followLocation = true
                            currentLocation?.let { loc ->
                                mapView?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                            }
                        },
                        onZoomIn = { mapView?.controller?.zoomIn(150L) },
                        onZoomOut = { mapView?.controller?.zoomOut(150L) },
                    )

                    // Area-select (high-zoom download) toggle button (案内中は非表示)
                    if (!isNavigationActive) {
                        Surface(
                            shape = if (isAreaSelectMode) RoundedCornerShape(22.dp) else CircleShape,
                            color = if (isAreaSelectMode) MaterialTheme.colorScheme.tertiary else CyclingNavy.copy(alpha = 0.94f),
                            shadowElevation = 6.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        isAreaSelectMode = !isAreaSelectMode
                                        if (!isAreaSelectMode) {
                                            areaDragState = AreaDragState()
                                            selectedAreaBounds = null
                                        }
                                    }
                                    .padding(horizontal = if (isAreaSelectMode) 10.dp else 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    modifier = Modifier.size(48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_lucide_square_dashed),
                                        contentDescription = "エリア選択",
                                        tint = if (isAreaSelectMode) MaterialTheme.colorScheme.onTertiary else Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                if (isAreaSelectMode) {
                                    Text(
                                        text = "選択中",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Top Center: Notifications / Warning Banner / Download Progress
            // (通常時は上部サーチバーの下(76dp)、案内中は最上部(12dp)に配置)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = if (isNavigationActive) 12.dp else 76.dp)
                    .zIndex(2f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isNavigationActive) {
                    AnimatedVisibility(
                        visible = isRerouting,
                        enter = Motion.bannerEnter(effectiveAnimationEnabled),
                        exit = Motion.bannerExit(effectiveAnimationEnabled),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "ルートを再探索中…",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }

                    val progress = navigationProgress
                    val distanceMeters = progress?.distanceToNextManeuverMeters
                        ?: progress?.let { (it.routeDistanceMeters - it.distanceFromStartMeters).coerceAtLeast(0.0) }
                    if (!isRerouting && distanceMeters != null) {
                        val remaining = progress?.let { (it.routeDistanceMeters - it.distanceFromStartMeters).coerceAtLeast(0.0) }
                        val guide = navigationInstruction(progress, remaining, navigationInstructions, ARRIVAL_RADIUS_METERS)
                        // 2つ先の案内 (その先) を取得してプレビュー表示
                        val traveled = progress?.distanceFromStartMeters ?: 0.0
                        val futureInstructions = navigationInstructions.filter { it.distanceFromStartMeters > traveled }
                        val nextAfterThis = futureInstructions.getOrNull(1)

                        AnimatedContent(
                            targetState = guide,
                            transitionSpec = { Motion.bannerContentTransform(effectiveAnimationEnabled) },
                            label = "NavGuideTransition",
                            modifier = Modifier.fillMaxWidth(),
                        ) { currentGuide ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.96f)),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painterResource(currentGuide.arrowIcon),
                                        contentDescription = null,
                                        tint = Color(0xFFFFD600),
                                        modifier = Modifier
                                            .padding(end = 14.dp)
                                            .size(52.dp),
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            currentGuide.text,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        )
                                        Text(
                                            "${distanceMeters.roundToInt()}m",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFD600),
                                        )
                                        nextAfterThis?.let { nextStep ->
                                            val label = IconResolver.getLabel(nextStep.type, nextStep.turnAngleDegrees)
                                            val stepDist = (nextStep.distanceFromStartMeters - traveled).coerceAtLeast(0.0).roundToInt()
                                            Text(
                                                "その先 (${stepDist}m): ${label}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFE2E8F0),
                                                modifier = Modifier.padding(top = 2.dp),
                                            )
                                        }
                                        navStats?.let { stats ->
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "残り ${formatRemaining(stats.remainingMeters)} · " +
                                                    "到着 ${formatEta(stats.etaEpochMillis)} · " +
                                                    "あと${formatDuration(stats.durationRemainingSeconds)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFFCBD5E1),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = offRouteDetector.isOffRoute && !isRerouting,
                        enter = Motion.bannerEnter(effectiveAnimationEnabled),
                        exit = Motion.bannerExit(effectiveAnimationEnabled),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (!autoRerouteEnabled) "ルートから外れています（自動リルートOFF）" else "ルートから外れています",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            )
                        }
                    }
                    // 現在速度チップ (ルート中はカード外で常時確認)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        SpeedChip(speedKmh = speedKmh)
                    }
                }

                // Area-select mode instruction banner
                AnimatedVisibility(
                    visible = isAreaSelectMode,
                    enter = Motion.bannerEnter(effectiveAnimationEnabled),
                    exit = Motion.bannerExit(effectiveAnimationEnabled),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "ドラッグしてダウンロード範囲を選択",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                isAreaSelectMode = false
                                areaDragState = AreaDragState()
                                selectedAreaBounds = null
                            }) { Text("キャンセル", color = MaterialTheme.colorScheme.onTertiaryContainer) }
                        }
                    }
                }

                // Permission Request
                AnimatedVisibility(
                    visible = !hasLocationPermission,
                    enter = Motion.bannerEnter(effectiveAnimationEnabled),
                    exit = Motion.bannerExit(effectiveAnimationEnabled),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "位置情報が必要です",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            FilledTonalButton(
                                onClick = {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ),
                                    )
                                },
                            ) { Text("許可") }
                        }
                    }
                }

                // GPS 精度低下・ロスト通知バナー (フェーズ1)
                AnimatedVisibility(
                    visible = gpsStatus != GpsSignalStatus.HEALTHY && hasLocationPermission && gpsHealthMonitor.hasReceivedFirstFix,
                    enter = Motion.bannerEnter(effectiveAnimationEnabled),
                    exit = Motion.bannerExit(effectiveAnimationEnabled),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val isLost = gpsStatus == GpsSignalStatus.LOST
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLost) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isLost) "GPS信号を受信できません（GPSロスト）" else "GPS信号が弱まっています（推測移動中）",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isLost) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Warning / Error notification
                AnimatedVisibility(
                    visible = warningMessage != null,
                    enter = Motion.bannerEnter(effectiveAnimationEnabled),
                    exit = Motion.bannerExit(effectiveAnimationEnabled),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    warningMessage?.let { msg ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { warningMessage = null }) { Text("閉じる") }
                            }
                        }
                    }
                }

                // Prefecture Tile Download Progress Card
                AnimatedVisibility(
                    visible = currentTileProgress != null,
                    enter = Motion.bannerEnter(effectiveAnimationEnabled),
                    exit = Motion.bannerExit(effectiveAnimationEnabled),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    currentTileProgress?.let { progress ->
                        val isDone = progress.completed >= progress.total
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        if (isDone) "${progress.prefName} 保存完了" else "${progress.prefName} (${progress.sourceName}) 保存中…",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${progress.completed} / ${progress.total}", style = MaterialTheme.typography.labelMedium)
                                        if (isDone) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            TextButton(onClick = { currentTileProgress = null }) { Text("閉じる") }
                                        }
                                    }
                                }
                                if (!isDone) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { if (progress.total > 0) progress.completed.toFloat() / progress.total else 0f },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // (帰属表示はダッシュボード内のフッターに統合)

            // Bottom Cycling Dashboard (HUD Card)
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .zIndex(2f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (routeSummary != null) {
                        CyclingNavy.copy(alpha = 0.96f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = if (isLandscape) 8.dp else 16.dp),
                ) {
                    // Graph Switch Indicator
                    AnimatedVisibility(
                        visible = isSwitchingGraph,
                        enter = Motion.bannerEnter(effectiveAnimationEnabled),
                        exit = Motion.bannerExit(effectiveAnimationEnabled),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CyclingPink)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("県データグラフを切替・読み込み中…", style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Route Calculation Indicator
                    AnimatedVisibility(
                        visible = isCalculatingRoute,
                        enter = Motion.bannerEnter(effectiveAnimationEnabled),
                        exit = Motion.bannerExit(effectiveAnimationEnabled),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("最適サイクリングルートを探索中…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Route Summary Section (When route is active) — 見本準拠ダークカード
                    routeSummary?.let { summary ->
                        val elevProfile = remember(summary, navigationRoute) {
                            buildRouteElevationProfile(navigationRoute.map { it.latitude to it.longitude }, summary.distanceMeters)
                        }
                        val progressM = navigationProgress?.distanceFromStartMeters
                        val rideMetrics = navStats?.let { stats ->
                            RideMetrics(
                                speedKmh = speedKmh,
                                totalDistanceM = summary.distanceMeters,
                                remainingM = stats.remainingMeters,
                                elapsedS = stats.elapsedSeconds,
                                remainingS = stats.durationRemainingSeconds.takeIf { it.isFinite() },
                                averageKmh = stats.effectiveSpeedMps * 3.6,
                                elevationGainM = progressM?.let { elevationGain(elevProfile, it) },
                                currentElevationM = progressM?.let { elevationAt(elevProfile, it) },
                                gradePct = progressM?.let { gradeAt(elevProfile, it) },
                                forwardGradePct = progressM?.let { forwardGradeAt(elevProfile, it) },
                            )
                        } ?: RideMetrics(
                            // 案内開始前でも概算を表示する (開始後の navStats 系には触らない)。
                            // 残り=総距離、時間=総距離÷想定速度 (案内中のフォールバックと同一の15km/h)、
                            // 標高・勾配=ルート起点の値。
                            speedKmh = speedKmh,
                            totalDistanceM = summary.distanceMeters,
                            remainingM = summary.distanceMeters,
                            elapsedS = 0.0,
                            remainingS = summary.distanceMeters / PRE_START_ASSUMED_SPEED_MPS,
                            averageKmh = PRE_START_ASSUMED_SPEED_MPS * 3.6,
                            elevationGainM = elevationGain(elevProfile, 0.0),
                            currentElevationM = elevationAt(elevProfile, 0.0),
                            gradePct = gradeAt(elevProfile, 0.0),
                            forwardGradePct = forwardGradeAt(elevProfile, 0.0),
                        )
                        RouteInfoCardContent(
                            metrics = rideMetrics,
                            profile = elevProfile,
                            totalDistanceM = summary.distanceMeters,
                            progressDistanceM = progressM,
                            started = isNavigationActive,
                            onStart = startNavigation,
                            onClearRoute = clearRoute,
                            modifier = Modifier.padding(bottom = 4.dp),
                            onDownloadCorridorTiles = downloadCorridorTiles,
                            tileDownloadStatus = corridorTileProgressText,
                            selectedPreference = selectedRoutePreference,
                            onPreferenceChange = { pref ->
                                selectedRoutePreference = pref
                                val loc = latestLocation
                                val start = if (loc != null) GeoPoint(loc.latitude, loc.longitude) else GeoPoint(FALLBACK_LATITUDE, FALLBACK_LONGITUDE)
                                destination?.let { dest ->
                                    runRouteCalculation(start, dest, false, null, pref)
                                }
                            },
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }

                    // Arrival Card
                    AnimatedVisibility(
                        visible = isArrived,
                        enter = Motion.bannerEnter(effectiveAnimationEnabled),
                        exit = Motion.bannerExit(effectiveAnimationEnabled),
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "目的地に到着しました",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                Button(onClick = clearRoute, shape = RoundedCornerShape(12.dp)) {
                                    Text("終了")
                                }
                            }
                        }
                    }

                    // Main HUD: Speedometer & GPX Controls
                    // ルート設定中はダッシュボード側に速度表示があるためGPX操作のみ
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left: Large Speedometer (ルート未設定時のみ)
                        if (routeSummary == null) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "%.1f".format(Locale.US, speedKmh),
                                    fontSize = if (isLandscape) 32.sp else 42.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "km/h",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                        } else {
                            Text(
                                text = "© OSM / 地理院",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showLicense = true }
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                            )
                        }

                        // Right: GPX Controls
                        Column(horizontalAlignment = Alignment.End) {
                            Button(
                                onClick = {
                                    if (!hasLocationPermission) {
                                        warningMessage = "GPX記録には位置情報の許可が必要です"
                                    } else if (isRecording) {
                                        LocationTrackingService.stopRecording(context)
                                    } else {
                                        LocationTrackingService.startRecording(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                ),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isRecording) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color.White),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("記録停止", fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("GPX記録", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (isRecording) {
                                Text(
                                    text = "記録中 (${gpxPointCount} pts)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp, end = 4.dp),
                                )
                            }
                            gpxNotificationText?.let { text ->
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp, end = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            }

            // サイクリング下部ナビ (地図/ルート/スポット/記録/設定) - ナビ案内中は自動格納してフルスクリーンHUD化
            androidx.compose.animation.AnimatedVisibility(
                visible = !isNavigationActive,
                enter = Motion.bottomBarEnter(effectiveAnimationEnabled),
                exit = Motion.bottomBarExit(effectiveAnimationEnabled),
            ) {
                CyclingBottomBar(
                    selected = bottomTab,
                    onSelect = { tab ->
                        bottomTab = tab
                        when (tab) {
                            CyclingTab.MAP -> {
                                showRoutePanel = false
                                showSpotPanel = false
                                showRecordPanel = false
                                showCyclingSettings = false
                            }
                            CyclingTab.ROUTE -> showRoutePanel = true
                            CyclingTab.SPOT -> showSpotPanel = true
                            CyclingTab.RECORD -> {
                                showRecordPanel = true
                                scope.launch(Dispatchers.IO) {
                                    val dataDir = cycleMapDataDir(context)
                                    val loaded = loadGpxHistory(dataDir)
                                    (context as? ComponentActivity)?.runOnUiThread { gpxHistory = loaded }
                                }
                            }
                            CyclingTab.SETTINGS -> showCyclingSettings = true
                        }
                    },
                )
            }
        }
    }

    // --- DIALOGS ---

    // スポット検索 (カテゴリ選択→周辺のオフラインDB検索)
    LaunchedEffect(spotCategory, showSpotPanel) {
        val cat = spotCategory
        if (!showSpotPanel || cat == null) return@LaunchedEffect
        spotLoading = true
        spotMessage = null
        val center = currentLocation?.let { it.latitude to it.longitude }
            ?: (mapView?.mapCenter as? GeoPoint)?.let { it.latitude to it.longitude }
        if (center == null) {
            spotLoading = false
            spotMessage = "現在地が取得できません"
            return@LaunchedEffect
        }
        val prefixes = cat.poi.prefixes
        // 県別search.db選択 (ダイアログ側と同型。地図中心基準で他県表示中も正しいDBを引く)。
        val dbSelection = SearchDbSelector.select(center.first, center.second)
        val dataDir = cycleMapDataDir(context)
        val dbFile = when (dbSelection) {
            is SearchDbSelection.Available -> SearchDbSelector.resolveDbFile(dataDir, dbSelection.fileName)
            is SearchDbSelection.Unavailable -> null
        }
        if (dbSelection is SearchDbSelection.Available) {
            Log.i(
                "CycleMapGeo",
                "POI_DB panel prefecture=${dbSelection.prefectureName} file=${dbSelection.fileName} exists=${dbFile?.isFile}",
            )
        }
        val found = if (dbFile != null && dbFile.isFile) {
            withContext(Dispatchers.IO) {
                searchNearbyPlaces(dbFile, center.first, center.second, 3000.0, limit = 60)
                    .filter { spot -> prefixes.any { spot.category.startsWith(it) } }
                    .take(30)
            }
        } else {
            emptyList()
        }
        spotResults = found
        spotLoading = false
        spotMessage = if (dbFile == null || !dbFile.isFile) {
            "検索DBが見つかりません"
        } else if (found.isEmpty()) {
            "周辺に${cat.label}が見つかりませんでした"
        } else {
            null
        }
    }

    if (showRoutePanel) {
        val routeProfile = remember(routeSummary) {
            routeSummary?.let { mockElevationProfile(it.distanceMeters) } ?: emptyList()
        }
        val routeGradeSummary = remember(routeProfile) {
            routeProfile.takeIf { it.size >= 2 }?.let { gradeSummary(it) }
        }
        RoutePanelSheet(
            hasRoute = routeSummary != null,
            totalDistanceM = routeSummary?.distanceMeters,
            remainingText = navStats?.let { formatRemaining(it.remainingMeters) }
                ?: routeSummary?.let { formatRemaining(it.distanceMeters) } ?: "--",
            durationText = navStats?.let { formatDuration(it.durationRemainingSeconds) } ?: "--",
            gainM = routeProfile.takeIf { it.size >= 2 }?.let {
                elevationGain(it, routeSummary?.distanceMeters ?: 0.0)?.toInt()
            },
            maxGradePct = routeGradeSummary?.maxUphillPct,
            maxDownhillPct = routeGradeSummary?.maxDownhillPct,
            onSearchClick = {
                showRoutePanel = false
                bottomTab = CyclingTab.MAP
                showDestinationSearch = true
            },
            onDismiss = {
                showRoutePanel = false
                bottomTab = CyclingTab.MAP
            },
        )
    }

    if (showSpotPanel) {
        SpotPanelSheet(
            selectedCategory = spotCategory,
            onCategorySelect = { spotCategory = it },
            isLoading = spotLoading,
            spots = spotResults,
            message = spotMessage,
            onSpotClick = { spot ->
                showSpotPanel = false
                bottomTab = CyclingTab.MAP
                handleSpotSelected(GeoPoint(spot.latitude, spot.longitude))
            },
            onDismiss = {
                showSpotPanel = false
                bottomTab = CyclingTab.MAP
            },
        )
    }

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
                    warningMessage = "GPX記録には位置情報の許可が必要です"
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
                    gpxTrackOverlay = polyline
                    if (ride.points.isNotEmpty()) {
                        val minLat = ride.points.minOf { it.latitude }
                        val maxLat = ride.points.maxOf { it.latitude }
                        val minLon = ride.points.minOf { it.longitude }
                        val maxLon = ride.points.maxOf { it.longitude }
                        view.zoomToBoundingBox(BoundingBox(maxLat, maxLon, minLat, minLon), true, 64)
                    }
                    view.invalidate()
                }
                showRecordPanel = false
                bottomTab = CyclingTab.MAP
            },
            onDeleteRide = {
                mapView?.let { view ->
                    gpxTrackOverlay?.let {
                        view.overlays.remove(it)
                        gpxTrackOverlay = null
                        view.invalidate()
                    }
                }
            },
            onDismiss = {
                showRecordPanel = false
                bottomTab = CyclingTab.MAP
            },
        )
    }

    if (showCyclingSettings) {
        SettingsPanelSheet(
            animationEnabled = animationEnabled,
            onAnimationEnabledChange = { enabled ->
                animationEnabled = enabled
                prefs.edit().putBoolean("animation_enabled", enabled).apply()
            },
            autoReroute = autoRerouteEnabled,
            onAutoReroute = { autoRerouteEnabled = it },
            headingUp = isHeadingUp,
            onHeadingUp = {
                isHeadingUp = it
                if (!it) {
                    mapView?.setMapOrientation(0f)
                    updateNavArrow()
                }
            },
            layerIndex = MapLayer.entries.indexOf(selectedLayer),
            layerLabels = MapLayer.entries.map { it.label },
            onLayerSelect = { selectedLayer = MapLayer.entries[it] },
            poiVisible = poiVisible,
            onPoiVisible = { poiVisible = it },
            poiSelected = poiSelected,
            onPoiToggle = { cat ->
                poiSelected = if (cat in poiSelected) poiSelected - cat else poiSelected + cat
            },
            voiceGuidanceMode = voiceGuidanceMode,
            onVoiceGuidanceModeChange = { mode ->
                voiceGuidanceMode = mode
                voiceNavigator.setMode(mode)
            },
            hasLocationPermission = hasLocationPermission,
            onManageData = {
                showCyclingSettings = false
                bottomTab = CyclingTab.MAP
                showPrefectureListDialog = true
            },
            onLicense = { showLicense = true },
            onVersion = { showVersion = true },
            onDeveloperOptions = {
                showCyclingSettings = false
                showDeveloperOptions = true
            },
            onDismiss = {
                showCyclingSettings = false
                bottomTab = CyclingTab.MAP
            },
        )
    }

    // Area download confirmation dialog (high-zoom z15-z16 for user-selected bbox)
    if (showAreaDownloadConfirmDialog && selectedAreaBounds != null) {
        val areaBounds = selectedAreaBounds!!
        val areaTileCount = remember(areaBounds) {
            PrefectureData.calculateTileCount(areaBounds, minZoom = 15, maxZoom = 16)
        }
        val areaSizeMb = remember(areaTileCount) { PrefectureData.estimateSizeMb(areaTileCount) }

        AlertDialog(
            onDismissRequest = {
                showAreaDownloadConfirmDialog = false
                isAreaSelectMode = false
                selectedAreaBounds = null
                areaDragState = AreaDragState()
            },
            title = { Text("エリアダウンロード (z15-z16)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("選択エリアの高解像度タイルをダウンロードします。")
                    Text("ズームレベル: 15〜16（高解像度）")
                    Text("タイル枚数: 約 $areaTileCount 枚")
                    Text("推定サイズ: 約 ${"%.1f".format(Locale.US, areaSizeMb)} MB")
                    HorizontalDivider()
                    Text("ダウンロードする地図の種類を選択してください:", fontWeight = FontWeight.Medium)
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isDownloading,
                        onClick = {
                            if (isDownloading) return@Button
                            isDownloading = true
                            showAreaDownloadConfirmDialog = false
                            isAreaSelectMode = false
                            selectedAreaBounds = null
                            areaDragState = AreaDragState()
                            val downloadBounds = areaBounds
                            startAreaDownload(
                                context = context,
                                bounds = downloadBounds,
                                areaLabel = "選択エリア",
                                sourceType = MapSourceType.GSI,
                                source = gsiTileSource(),
                                minZoom = 15,
                                maxZoom = 16,
                                onProgress = { progress ->
                                    (context as? ComponentActivity)?.runOnUiThread {
                                        currentTileProgress = progress
                                    }
                                },
                                onFinished = {
                                    (context as? ComponentActivity)?.runOnUiThread {
                                        isDownloading = false
                                    }
                                },
                            )
                        },
                    ) {
                        Text("地理院地図（標準）をDL")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isDownloading,
                        onClick = {
                            if (isDownloading) return@Button
                            isDownloading = true
                            showAreaDownloadConfirmDialog = false
                            isAreaSelectMode = false
                            selectedAreaBounds = null
                            areaDragState = AreaDragState()
                            val downloadBounds = areaBounds
                            startAreaDownload(
                                context = context,
                                bounds = downloadBounds,
                                areaLabel = "選択エリア",
                                sourceType = MapSourceType.OSM,
                                source = osmTileSource(),
                                minZoom = 15,
                                maxZoom = 16,
                                onProgress = { progress ->
                                    (context as? ComponentActivity)?.runOnUiThread {
                                        currentTileProgress = progress
                                    }
                                },
                                onFinished = {
                                    (context as? ComponentActivity)?.runOnUiThread {
                                        isDownloading = false
                                    }
                                },
                            )
                        },
                    ) {
                        Text("OpenStreetMapをDL")
                    }
                    TextButton(
                        modifier = Modifier.align(Alignment.End),
                        onClick = {
                            showAreaDownloadConfirmDialog = false
                            isAreaSelectMode = false
                            selectedAreaBounds = null
                            areaDragState = AreaDragState()
                        },
                    ) {
                        Text("キャンセル")
                    }
                }
            },
        )
    }

    if (showDestinationSearch) {
        DestinationSearchDialog(
            context = context,
            // live位置を併用する (getLastKnownLocationの一回切りだけだと初回が古い/不明になる)。
            liveLocation = currentLocation?.let { it.latitude to it.longitude },
            onDismiss = { showDestinationSearch = false },
            onResultSelected = { result ->
                showDestinationSearch = false
                handleSpotSelected(GeoPoint(result.latitude, result.longitude))
            },
        )
    }

    if (pendingSelectedSpot != null) {
        val spot = pendingSelectedSpot!!
        AlertDialog(
            onDismissRequest = { pendingSelectedSpot = null },
            title = { Text("地点の指定", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "現在すでにルートが設定されています。\nこの地点を経由地に追加しますか？それとも新しい目的地にしますか？",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newWaypoints = waypoints + spot
                        waypoints = newWaypoints
                        pendingSelectedSpot = null
                        val loc = latestLocation
                        val start = if (loc != null) GeoPoint(loc.latitude, loc.longitude) else GeoPoint(FALLBACK_LATITUDE, FALLBACK_LONGITUDE)
                        destination?.let { dest ->
                            runRouteCalculation(start, dest, false, newWaypoints, null)
                        }
                    },
                ) {
                    Text("経由地に追加")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingSelectedSpot = null }) {
                        Text("キャンセル")
                    }
                    TextButton(
                        onClick = {
                            waypoints = emptyList()
                            pendingSelectedSpot = null
                            startRouteToDestination(spot)
                        },
                    ) {
                        Text("目的地に変更")
                    }
                }
            },
        )
    }

    // 1. Prefecture List Dialog (47 Prefectures grouped by Region)
    if (showPrefectureListDialog) {
        PrefectureListDialog(
            context = context,
            onDismiss = { showPrefectureListDialog = false },
            onPrefectureSelected = { pref ->
                selectedPrefectureForAction = pref
                showPrefectureListDialog = false
                val isGsiDownloaded = DownloadStatusManager.isDownloaded(context, pref.id, MapSourceType.GSI)
                val isOsmDownloaded = DownloadStatusManager.isDownloaded(context, pref.id, MapSourceType.OSM)
                if (isGsiDownloaded || isOsmDownloaded) {
                    showAlreadyDownloadedDialog = true
                } else {
                    showDownloadConfirmDialog = true
                }
            },
        )
    }

    // 2. Already Downloaded Dialog
    if (showAlreadyDownloadedDialog && selectedPrefectureForAction != null) {
        val pref = selectedPrefectureForAction!!
        val gsiInfo = DownloadStatusManager.getDownloadInfo(context, pref.id, MapSourceType.GSI)
        val osmInfo = DownloadStatusManager.getDownloadInfo(context, pref.id, MapSourceType.OSM)
        val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN) }

        AlertDialog(
            onDismissRequest = { showAlreadyDownloadedDialog = false },
            title = { Text("ダウンロード状況: ${pref.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("この都道府県の地図データは既に端末に保存されています。")
                    HorizontalDivider()
                    Text("• 地理院地図: " + if (gsiInfo != null) "保存済 (${dateFormat.format(Date(gsiInfo.first))})" else "未保存")
                    Text("• OSM: " + if (osmInfo != null) "保存済 (${dateFormat.format(Date(osmInfo.first))})" else "未保存")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAlreadyDownloadedDialog = false
                        showDownloadConfirmDialog = true
                    },
                ) {
                    Text("再ダウンロード / 追加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlreadyDownloadedDialog = false }) {
                    Text("閉じる")
                }
            },
        )
    }

    // 3. Download Confirm / Source Selection Dialog
    if (showDownloadConfirmDialog && selectedPrefectureForAction != null) {
        val pref = selectedPrefectureForAction!!
        val totalTiles = remember(pref) { PrefectureData.calculateTileCount(pref.bounds) }
        val sizeMb = remember(totalTiles) { PrefectureData.estimateSizeMb(totalTiles) }

        AlertDialog(
            onDismissRequest = { showDownloadConfirmDialog = false },
            title = { Text("${pref.name} の地図ダウンロード") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("保存範囲: ズームレベル 7〜14（低ズーム含む）")
                    Text("タイル枚数: 約 ${totalTiles} 枚 (約 %.1f MB)".format(Locale.US, sizeMb))
                    HorizontalDivider()
                    Text("ダウンロードする地図の種類を選択してください:", fontWeight = FontWeight.Medium)
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isDownloading,
                        onClick = {
                            if (isDownloading) return@Button
                            isDownloading = true
                            showDownloadConfirmDialog = false
                            startPrefectureDownload(
                                context = context,
                                pref = pref,
                                sourceType = MapSourceType.GSI,
                                source = gsiTileSource(),
                                onProgress = { progress ->
                                    (context as? ComponentActivity)?.runOnUiThread {
                                        currentTileProgress = progress
                                    }
                                },
                                onFinished = {
                                    (context as? ComponentActivity)?.runOnUiThread {
                                        isDownloading = false
                                    }
                                },
                            )
                        },
                    ) {
                        Text("地理院地図（標準）をDL")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isDownloading,
                        onClick = {
                            if (isDownloading) return@Button
                            isDownloading = true
                            showDownloadConfirmDialog = false
                            startPrefectureDownload(
                                context = context,
                                pref = pref,
                                sourceType = MapSourceType.OSM,
                                source = osmTileSource(),
                                onProgress = { progress ->
                                    (context as? ComponentActivity)?.runOnUiThread {
                                        currentTileProgress = progress
                                    }
                                },
                                onFinished = {
                                    (context as? ComponentActivity)?.runOnUiThread {
                                        isDownloading = false
                                    }
                                },
                            )
                        },
                    ) {
                        Text("OpenStreetMapをDL")
                    }
                    TextButton(
                        modifier = Modifier.align(Alignment.End),
                        onClick = { showDownloadConfirmDialog = false },
                    ) {
                        Text("キャンセル")
                    }
                }
            },
        )
    }

    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text("地図情報・ライセンス") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "CycleMap App\n" +
                            "© 2026 Gorite. All rights reserved.",
                        fontWeight = FontWeight.Bold,
                    )
                    HorizontalDivider()
                    Text("【地図・POIデータ・音声出典】", fontWeight = FontWeight.Bold)

                    Text(
                        "■ OpenStreetMap\n" +
                            "© OpenStreetMap contributors\n" +
                            "ODbL 1.0 (https://www.openstreetmap.org/copyright)",
                    )

                    Text(
                        "■ 地理院タイル（国土地理院）\n" +
                            "https://maps.gsi.go.jp/development/\n" +
                            "利用規約: https://maps.gsi.go.jp/help/termsofuse.html",
                    )

                    Text(
                        "■ Overture Maps Foundation (Places)\n" +
                            "Overture Maps Foundation, overturemaps.org\n" +
                            "Contains data from Overture Maps Foundation, licensed under CDLA-Permissive-2.0 (https://overturemaps.org)",
                    )

                    Text(
                        "■ 国土数値情報（国土交通省）\n" +
                            "「国土数値情報（医療機関データ 第3.0版、学校データ 第2.0版）」（国土交通省）（https://nlftp.mlit.go.jp/ksj/）をもとに加工して作成",
                    )

                    Text(
                        "■ 音声案内\n" +
                            "VOICEVOX:四国めたん\n" +
                            "https://voicevox.hiroshiba.jp/\n" +
                            "音源利用規約: https://zunko.jp/con_ongen_kiyaku.html",
                    )

                    Text(
                        "■ ライブラリ\n" +
                            "地図表示：osmdroid (Apache License 2.0)\n" +
                            "位置情報：Google Play services Location",
                    )

                    HorizontalDivider()
                    Text("【CDLA-Permissive-2.0 全文】", fontWeight = FontWeight.Bold)
                    Text(
                        "Community Data License Agreement – Permissive – Version 2.0\n\n" +
                            "This is the Community Data License Agreement – Permissive, Version 2.0 (the “agreement”). Data Provider(s) and Data Recipient(s) agree as follows:\n\n" +
                            "1. Provision of the Data.\n" +
                            "1.1. A Data Recipient may use, modify, and share the Data made available by Data Provider(s) under this agreement if that Data Recipient follows the terms of this agreement.\n" +
                            "1.2. This agreement does not impose any restriction on a Data Recipient’s use, modification, or sharing of any portions of the Data that are in the public domain or that may be used, modified, or shared under any other legal exception or limitation.\n\n" +
                            "2. Conditions for Sharing Data.\n" +
                            "2.1. A Data Recipient may share Data, with or without modifications, so long as the Data Recipient makes available the text of this agreement with the shared Data.\n\n" +
                            "3. No Restrictions on Results.\n" +
                            "3.1. This agreement does not impose any restriction or obligations with respect to the use, modification, or sharing of Results.\n\n" +
                            "4. No Warranty; Limitation of Liability.\n" +
                            "4.1. All Data Recipients receive the Data subject to the following terms: THE DATA IS PROVIDED ON AN “AS IS” BASIS, WITHOUT REPRESENTATIONS, WARRANTIES OR CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED INCLUDING, WITHOUT LIMITATION, ANY WARRANTIES OR CONDITIONS OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. NO DATA PROVIDER SHALL HAVE ANY LIABILITY FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING WITHOUT LIMITATION LOST PROFITS), HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE DATA OR RESULTS, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.\n\n" +
                            "5. Definitions.\n" +
                            "5.1. “Data” means the material received by a Data Recipient under this agreement.\n" +
                            "5.2. “Data Provider” means any person who is the source of Data provided under this agreement and in reliance on a Data Recipient’s agreement to its terms.\n" +
                            "5.3. “Data Recipient” means any person who receives Data directly or indirectly from a Data Provider under this agreement.\n" +
                            "5.4. “Results” means any work, analysis, data, or product that a Data Recipient creates using the Data, provided that such work, analysis, data, or product does not include more than a de minimis portion of the Data.\n" +
                            "5.5. “Use” means using, copying, modifying, preparing derivative works, or otherwise exploiting the Data.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text("閉じる") } },
        )
    }

    // (設定はサイクリング設定パネル SettingsPanelSheet に統合)

    if (showVersion) {
        AlertDialog(
            onDismissRequest = { showVersion = false },
            title = { Text("バージョン情報") },
            text = { Text("Ver. α 1.0.0\nナビ案内の強化") },
            confirmButton = { TextButton(onClick = { showVersion = false }) { Text("閉じる") } },
        )
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
            benchmarkResult = routingBenchmarkResult,
            onRunRoutingBenchmark = runRoutingBenchmark,
            onClose = {
                showDeveloperOptions = false
                selectedMenu = "地図"
            },
        )
    }
    }

    // Location Animation & Map Centering
    LaunchedEffect(currentLocation, mapView, followLocation, locationMarker, accuracyCircle, appLifecycleState, effectiveAnimationEnabled) {
        val targetLocation = currentLocation ?: return@LaunchedEffect
        val marker = locationMarker ?: return@LaunchedEffect
        val view = mapView ?: return@LaunchedEffect
        val target = GeoPoint(targetLocation.latitude, targetLocation.longitude)
        // タップで座標・精度を確認できる (通常時はドットのみで情報を詰め込まない)
        marker.snippet = "%.5f, %.5f".format(Locale.US, targetLocation.latitude, targetLocation.longitude) +
            (if (targetLocation.hasAccuracy()) " (±${targetLocation.accuracy.toInt()}m)" else "")
        accuracyCircle?.let { circle ->
            if (targetLocation.hasAccuracy() && targetLocation.accuracy in 1f..200f) {
                circle.points = Polygon.pointsAsCircle(target, targetLocation.accuracy.toDouble())
                circle.isVisible = true
            } else {
                circle.isVisible = false
            }
        }
        // 矢印の向き・表示切替は位置アニメーション(900msループ)と分離して1回だけ更新する。
        // 端末コンパスは使わず、GPS bearing または連続fixの推定コースのみ。地図invalidateだけで反映しrecomposeしない。
        navArrowCache.onFix(
            targetLocation.latitude,
            targetLocation.longitude,
            if (targetLocation.hasAccuracy()) targetLocation.accuracy else Float.NaN,
            SystemClock.elapsedRealtime(),
        )
        latestNavArrow()

        // アプリが前面 (RESUMED) でないとき、およびアニメーション無効時は即時スナップ (GPU/CPU浪費防止)
        val shouldAnimate = appLifecycleState == AppLifecycleState.RESUMED && effectiveAnimationEnabled
        if (!shouldAnimate) {
            marker.position = target
            if (followLocation) {
                view.controller.setCenter(target)
            }
            view.invalidate()
            return@LaunchedEffect
        }

        val start = marker.position
        val durationNanos = 900_000_000L
        val minFrameIntervalNanos = 33_333_333L // 最大30fpsに間引き、120Hz/60Hzの毎フレーム全画面再描画を抑止
        val startTime = withFrameNanos { it }
        var lastRenderNanos = startTime
        while (true) {
            val frameTime = withFrameNanos { it }
            val elapsed = frameTime - startTime
            val value = (elapsed.toDouble() / durationNanos).coerceIn(0.0, 1.0)
            val isFinal = value >= 1.0

            if (isFinal || (frameTime - lastRenderNanos) >= minFrameIntervalNanos) {
                val point = GeoPoint(
                    start.latitude + (target.latitude - start.latitude) * value,
                    start.longitude + (target.longitude - start.longitude) * value,
                )
                marker.position = point
                if (followLocation) {
                    view.controller.setCenter(point)
                }
                view.invalidate()
                lastRenderNanos = frameTime
            }
            if (isFinal) break
        }
    }

    // POIオーバーレイ (オフラインDB・選択カテゴリ・ズーム別ルール・件数上限)。
    // z14: 重要POIのみ / z15+: 選択全種。スクロール連打ではdebounce。
    LaunchedEffect(poiVisible, poiSelected, poiRefreshTick, currentLocation, mapView, isNavigationActive) {
        val view = mapView ?: return@LaunchedEffect
        if (!poiVisible || poiSelected.isEmpty() || isNavigationActive) {
            view.overlays.removeAll { it is Marker && (it.title?.startsWith("POI:") == true) }
            view.invalidate()
            return@LaunchedEffect
        }
        delay(500L)
        val zoom = view.zoomLevelDouble
        // z14未満はゴチャつき防止で非表示
        if (zoom < 14.0) {
            view.overlays.removeAll { it is Marker && (it.title?.startsWith("POI:") == true) }
            view.invalidate()
            return@LaunchedEffect
        }
        val center = view.mapCenter as? GeoPoint ?: return@LaunchedEffect
        // ズームアウト時は重要POI (駅・観光) のみ、件数も絞る
        val zoomedOut = zoom < 15.0
        val activeCategories = if (zoomedOut) poiSelected.filter { it.important }.toSet() else poiSelected
        if (activeCategories.isEmpty()) {
            view.overlays.removeAll { it is Marker && (it.title?.startsWith("POI:") == true) }
            view.invalidate()
            return@LaunchedEffect
        }
        val radius = if (zoom >= 16.0) 800.0 else if (zoom >= 15.0) 1500.0 else 3000.0
        val limit = if (zoomedOut) 15 else 40
        val dbSelection = SearchDbSelector.select(center.latitude, center.longitude)
        val dataDir = cycleMapDataDir(context)
        val dbFile = when (dbSelection) {
            is SearchDbSelection.Available -> SearchDbSelector.resolveDbFile(dataDir, dbSelection.fileName)
            is SearchDbSelection.Unavailable -> null
        }
        val pois = if (dbFile != null && dbFile.isFile) {
            withContext(Dispatchers.IO) {
                // バス停 (highway:bus_stop) の密集で画面が圧迫されるのを防ぎ、駅 (railway:station) を優先
                val rawList = searchNearbyPlaces(dbFile, center.latitude, center.longitude, radius, limit = limit * 4)
                    .mapNotNull { spot ->
                        if (spot.category.startsWith("highway:bus_stop") && zoom < 16.5) return@mapNotNull null
                        val cat = PoiCategory.forCategory(spot.category) ?: return@mapNotNull null
                        if (cat !in activeCategories) null else spot to cat
                    }
                    .sortedWith(
                        // 駅・重要スポットを最優先し、同一重要度内では距離順
                        compareByDescending<Pair<NearbySpot, PoiCategory>> { (_, cat) -> cat.important }
                            .thenBy { (spot, _) -> spot.distanceM }
                    )
                rawList
            }
        } else {
            emptyList()
        }
        view.overlays.removeAll { it is Marker && (it.title?.startsWith("POI:") == true) }
        val showLabel = zoom >= 15.0
        val density = context.resources.displayMetrics.density
        val projection = view.projection
        val screenBounds = Rect(0, 0, view.width.coerceAtLeast(1080), view.height.coerceAtLeast(1920))
        val placedRects = ArrayList<Rect>()
        val point = Point()

        for ((poi, cat) in pois) {
            if (placedRects.size >= limit) break
            val geo = GeoPoint(poi.latitude, poi.longitude)
            projection.toPixels(geo, point)

            // 画面外はスキップ
            if (!screenBounds.contains(point.x, point.y)) continue

            val visual = googlePoiMarker(context, cat, poi.name, showLabel = showLabel)
            val w = visual.drawable.intrinsicWidth
            val h = visual.drawable.intrinsicHeight
            val left = (point.x - w * visual.anchorU).toInt()
            val top = (point.y - h * visual.anchorV).toInt()
            val right = left + w
            val bottom = top + h
            // Googleマップ風の適度な余白 (マージン) を持った衝突判定矩形
            val margin = (5f * density).toInt()
            val poiRect = Rect(left - margin, top - margin, right + margin, bottom + margin)

            // 画面空間衝突判定 (Screen-Space Collision Detection): 既に配置されたPOIと重なる場合はスキップ
            val collides = placedRects.any { Rect.intersects(it, poiRect) }
            if (collides) continue

            placedRects.add(poiRect)
            view.overlays.add(
                Marker(view).apply {
                    position = geo
                    title = "POI:${poi.name}"
                    snippet = cat.label
                    icon = visual.drawable
                    setAnchor(visual.anchorU, visual.anchorV)
                },
            )
        }
        view.invalidate()
    }

    // 上部サーチバーの地名表示 (周辺の代表スポット名・debounce付き)
    // 上部住所表示 (オフライン逆ジオコーディング。GPS実測のみ・debounce付き)。
    // 判定ロジックは data/ReverseGeocoder.kt に分離。UIは文字列表示だけを行う。
    // search.db は県別に選択する (単一DBの使い回しはしない。routingグラフと同型)。
    LaunchedEffect(currentLocation, mapView) {
        delay(1_000L)
        val location = currentLocation
        if (location == null) {
            locationLabel = AddressDisplayController.ADDRESS_LOADING
            return@LaunchedEffect
        }
        val dbSelection = SearchDbSelector.select(location.latitude, location.longitude)
        val dbFile = when (dbSelection) {
            is SearchDbSelection.Available -> File(cycleMapDataDir(context), dbSelection.fileName)
            is SearchDbSelection.Unavailable -> null
        }
        if (dbSelection is SearchDbSelection.Available) {
            Log.i(
                "CycleMapGeo",
                "GEO_DB location=${location.latitude},${location.longitude} " +
                    "prefecture=${dbSelection.prefectureName} file=${dbSelection.fileName} exists=${dbFile?.isFile}",
            )
        } else {
            val unsupportedName = (dbSelection as? SearchDbSelection.Unavailable)?.prefectureName
            Log.w(
                "CycleMapGeo",
                "GEO_DB location=${location.latitude},${location.longitude} " +
                    "reason=unsupported prefecture=${unsupportedName ?: "(unknown)"}",
            )
        }
        val accuracy = if (location.hasAccuracy()) location.accuracy else Float.NaN
        val prefectureName = (dbSelection as? SearchDbSelection.Available)?.prefectureName
        locationLabel = withContext(Dispatchers.IO) {
            if (dbFile != null && dbFile.isFile && prefectureName != null) {
                val geocoder = SearchDbReverseGeocoder(dbFile, prefectureName)
                addressController.update(location.latitude, location.longitude, accuracy, geocoder)
            } else {
                AddressDisplayController.ADDRESS_UNKNOWN
            }
        }
    }

    // Compass Orientation: 端末コンパスは地図回転にだけ使う。矢印の向きには使わない。
    // (端末を回すと矢印が勝手に回る問題の分離)。矢印は GPS bearing から updateNavArrow が合成する。
    LaunchedEffect(isHeadingUp, mapView) {
        if (isHeadingUp) {
            orientationAnimator.rotateTo(headingDegrees)
        } else {
            orientationAnimator.reset()
        }
    }

    // Tile Source Switching
    LaunchedEffect(selectedLayer, mapView) {
        mapView?.let { view ->
            val source = when (selectedLayer) {
                MapLayer.GSI -> gsiTileSource()
                MapLayer.OSM -> osmTileSource()
                MapLayer.TERRAIN -> gsiReliefTileSource()
            }
            view.setTileSource(source)
            view.minZoomLevel = source.minimumZoomLevel.toDouble()
            view.maxZoomLevel = source.maximumZoomLevel.toDouble()
            view.tileProvider.ensureCapacity(512)
            // 全レイヤーで等倍表示 (DPI拡大は256pxタイルがぼやけるため使わない。GSIと同様)。
            // OSMの文字は小さくなるが高dpiでも鮮明さを優先する。
            view.setTilesScaledToDpi(false)
            view.overlayManager.tilesOverlay.apply {
                loadingBackgroundColor = android.graphics.Color.parseColor(
                    if (selectedLayer == MapLayer.GSI) "#F4F1EA" else "#E7EDF2",
                )
                loadingLineColor = android.graphics.Color.parseColor("#C5CBD3")
            }
            if (view.zoomLevelDouble > view.maxZoomLevel) {
                view.controller.setZoom(view.maxZoomLevel)
            } else if (view.zoomLevelDouble < view.minZoomLevel) {
                view.controller.setZoom(view.minZoomLevel)
            }
            view.invalidate()
        }
    }

    DisposableEffect(mapView, lifecycleOwner) {
        val view = mapView ?: return@DisposableEffect onDispose { }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            view.onResume()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> view.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.onPause()
        }
    }
}

// ---------------------------------------------------------------------------
// 小型ユーティリティ Composable
// ---------------------------------------------------------------------------

@Composable
internal fun NavStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------
// ヘルパー関数
// ---------------------------------------------------------------------------

/** GPX履歴一覧を実ファイルから読み込む (ブロッキング・IOスレッドで呼ぶこと)。 */
internal fun loadGpxHistory(dataDir: File): List<GpxHistoryEntry> {
    val dir = File(dataDir, "gpx")
    if (!dir.isDirectory) return emptyList()
    return dir.listFiles { f -> f.isFile && f.name.endsWith(".gpx") }
        ?.sortedByDescending { it.lastModified() }
        ?.take(20)
        ?.map { file ->
            var points = 0
            runCatching {
                file.bufferedReader().useLines { lines ->
                    lines.forEach { line -> if (line.contains("<trkpt")) points++ }
                }
            }
            GpxHistoryEntry(
                fileName = file.name,
                dateText = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date(file.lastModified())),
                sizeKb = file.length() / 1024,
                pointCount = points,
            )
        } ?: emptyList()
}

internal fun saveRoutingBenchmarkLog(
    context: Context,
    request: RoutingBenchmarkRequest,
    resultText: String,
    samples: List<com.gorite.cyclemap.routing.MappedRoutingBenchmarkResult>,
): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val output = File(cycleMapDataDir(context), "benchmarks/hsa_${timestamp}.log")
    output.parentFile?.mkdirs()
    val details = buildString {
        appendLine("CycleMap routing benchmark")
        appendLine("timestamp=$timestamp")
        appendLine("mode=${request.mode}")
        appendLine("start=${request.startLatitude},${request.startLongitude}")
        appendLine("goal=${request.goalLatitude},${request.goalLongitude}")
        appendLine("graph=LazyMappedRoadGraph")
        appendLine("qualityGate=HSA cost <= A* cost * 1.05")
        appendLine()
        appendLine(resultText)
        appendLine()
        samples.forEach { sample ->
            appendLine(
                "${sample.algorithm}\tseconds=${sample.elapsedSeconds}\t" +
                    "heapIncreaseBytes=${sample.peakHeapIncreaseBytes}\t" +
                        "expandedNodes=${sample.expandedNodes}\t" +
                    "distanceMeters=${sample.route.totalDistanceMeters}\t" +
                    "cost=${sample.route.totalCost}\t" +
                    "segments=${sample.segmentCount}\t" +
                    "fallback=${sample.usedFallback}\t" +
                    "reason=${sample.fallbackReason ?: ""}",
            )
        }
    }
    output.writeText(details)
    Log.i("CycleMapBenchmark", "saved=${output.absolutePath}\n$resultText")
    return output
}

internal fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
