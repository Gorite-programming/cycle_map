package com.gorite.cyclemap

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Debug
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.gorite.cyclemap.data.DownloadStatusManager
import com.gorite.cyclemap.data.MapSourceType
import com.gorite.cyclemap.data.Prefecture
import com.gorite.cyclemap.data.PrefectureData
import com.gorite.cyclemap.data.TileDownloader
import com.gorite.cyclemap.data.TileProgress
import com.gorite.cyclemap.routing.LazyMappedRoadGraph
import com.gorite.cyclemap.tracking.LocationTrackingService
import com.gorite.cyclemap.tracking.locationServiceConnection
import com.gorite.cyclemap.ui.DeveloperOptionsScreen
import com.gorite.cyclemap.ui.theme.CycleMapTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class MapLayer(val label: String) {
    GSI("地理院"),
    OSM("OSM"),
}

private data class RouteSummary(val distanceMeters: Double, val stepCount: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CycleMapTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MapScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) LocationTrackingService.stopTracking(this)
        super.onDestroy()
    }
}

@Composable
private fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var selectedLayer by remember { mutableStateOf(MapLayer.GSI) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var locationMarker by remember { mutableStateOf<Marker?>(null) }
    var destinationMarker by remember { mutableStateOf<Marker?>(null) }
    var speedKmh by remember { mutableStateOf(0.0) }
    var headingDegrees by remember { mutableFloatStateOf(0f) }
    var isHeadingUp by remember { mutableStateOf(true) }
    var followLocation by remember { mutableStateOf(true) }

    // Dialog & Download States
    var showPrefectureListDialog by remember { mutableStateOf(false) }
    var selectedPrefectureForAction by remember { mutableStateOf<Prefecture?>(null) }
    var showAlreadyDownloadedDialog by remember { mutableStateOf(false) }
    var showDownloadConfirmDialog by remember { mutableStateOf(false) }
    var targetDownloadSourceType by remember { mutableStateOf(MapSourceType.GSI) }

    var currentTileProgress by remember { mutableStateOf<TileProgress?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDeveloperOptions by remember { mutableStateOf(false) }
    var selectedMenu by remember { mutableStateOf("地図") }

    var mappedGraph by remember { mutableStateOf<LazyMappedRoadGraph?>(null) }
    var warningMessage by remember { mutableStateOf<String?>(null) }
    var destination by remember { mutableStateOf<GeoPoint?>(null) }
    var routeOverlay by remember { mutableStateOf<Polyline?>(null) }
    var routeSummary by remember { mutableStateOf<RouteSummary?>(null) }
    var isCalculatingRoute by remember { mutableStateOf(false) }

    var isRecording by remember { mutableStateOf(false) }
    var gpxPointCount by remember { mutableStateOf(0) }
    var gpxNotificationText by remember { mutableStateOf<String?>(null) }

    val drawerState = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val latestGraph by rememberUpdatedState(mappedGraph)
    val latestLocation by rememberUpdatedState(currentLocation)
    val latestRouteOverlay by rememberUpdatedState(routeOverlay)
    val latestDestMarker by rememberUpdatedState(destinationMarker)

    // Load graph & tile check
    LaunchedEffect(Unit) {
        val dataDir = cycleMapDataDir(context)
        configureOsmdroid(context, dataDir)
        val graphFile = File(dataDir, "yamaguchi.graph")
        val graphIndex = File(dataDir, "yamaguchi.graph.idx")
        val missingGraph = buildList {
            if (!graphFile.isFile) add("yamaguchi.graph")
            if (!graphIndex.isFile) add("yamaguchi.graph.idx")
        }
        if (missingGraph.isNotEmpty()) {
            warningMessage = "ルートグラフが見つかりません:\n${missingGraph.joinToString()}\n配置先: ${dataDir.absolutePath}"
        } else {
            val result = withContext(Dispatchers.IO) {
                val before = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                val start = SystemClock.elapsedRealtime()
                val graph = LazyMappedRoadGraph.load(graphFile, graphIndex)
                val elapsed = SystemClock.elapsedRealtime() - start
                val after = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                Triple(graph, elapsed, after.totalPss - before.totalPss)
            }
            mappedGraph = result.first
            Log.i("CycleMapGraph", "mappedLoadMs=${result.second} pssDeltaKb=${result.third} nodes=${result.first.nodeCount} edges=${result.first.edgeCount}")
        }
    }

    DisposableEffect(mappedGraph) {
        onDispose { mappedGraph?.close() }
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    if (hasLocationPermission) {
        ServiceLocationUpdates(
            context = context,
            onLocationChanged = { location, recording, points ->
                currentLocation = location
                isRecording = recording
                if (recording) gpxPointCount = points
            },
            onSpeedChanged = { speedKmh = it * 3.6 },
        )
    }

    CompassUpdates(context) { headingDegrees = it }

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
                        label = { Text("🗺️ 地図に戻る") },
                        selected = selectedMenu == "地図",
                        onClick = {
                            selectedMenu = "地図"
                            scope.launch { drawerState.close() }
                        },
                    )
                    NavigationDrawerItem(
                        label = { Text("📥 地図ダウンロード（47都道府県）") },
                        selected = selectedMenu == "ダウンロード",
                        onClick = {
                            selectedMenu = "ダウンロード"
                            showPrefectureListDialog = true
                            scope.launch { drawerState.close() }
                        },
                    )
                    NavigationDrawerItem(
                        label = { Text("ℹ️ 地図情報・ライセンス") },
                        selected = selectedMenu == "地図情報",
                        onClick = {
                            selectedMenu = "地図情報"
                            showLicense = true
                            scope.launch { drawerState.close() }
                        },
                    )
                    NavigationDrawerItem(
                        label = { Text("⚙️ 設定") },
                        selected = selectedMenu == "設定",
                        onClick = {
                            selectedMenu = "設定"
                            showSettings = true
                            scope.launch { drawerState.close() }
                        },
                    )
                    NavigationDrawerItem(
                        label = { Text("🛠 開発者オプション") },
                        selected = selectedMenu == "開発者",
                        onClick = {
                            selectedMenu = "開発者"
                            showDeveloperOptions = true
                            scope.launch { drawerState.close() }
                        },
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        },
    ) {
        Box(modifier = modifier.fillMaxSize()) {
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
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)
                        setTilesScaledToDpi(false)
                        minZoomLevel = 5.0
                        maxZoomLevel = 18.0
                        isHorizontalMapRepetitionEnabled = false
                        isVerticalMapRepetitionEnabled = false
                        isFlingEnabled = true
                        overlayManager.tilesOverlay.apply {
                            loadingBackgroundColor = android.graphics.Color.parseColor("#F4F1EA")
                            loadingLineColor = android.graphics.Color.parseColor("#C5CBD3")
                        }
                        setTileSource(gsiTileSource())
                        controller.setZoom(13.0)
                        controller.setCenter(GeoPoint(34.18, 131.47))
                        locationMarker = Marker(this).apply {
                            title = "現在地"
                            icon = ContextCompat.getDrawable(mapContext, R.drawable.ic_current_location)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        }
                        onUserPan = { followLocation = false }
                        overlays.add(locationMarker)
                        overlays.add(
                            MapEventsOverlay(
                                object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean = false

                                    override fun longPressHelper(p: GeoPoint): Boolean {
                                        destination = p
                                        isCalculatingRoute = true
                                        val graph = latestGraph
                                        if (graph == null) {
                                            isCalculatingRoute = false
                                            warningMessage = "ルートグラフが読み込まれていません"
                                            return true
                                        }

                                        (context as? ComponentActivity)?.runOnUiThread {
                                            val view = mapView ?: return@runOnUiThread
                                            latestDestMarker?.let { view.overlays.remove(it) }
                                            val destM = Marker(view).apply {
                                                position = p
                                                title = "目的地"
                                                icon = ContextCompat.getDrawable(mapContext, R.drawable.ic_destination)
                                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                            }
                                            view.overlays.add(destM)
                                            destinationMarker = destM
                                            view.invalidate()
                                        }

                                        Thread {
                                            try {
                                                val start = latestLocation?.let { GeoPoint(it.latitude, it.longitude) }
                                                    ?: GeoPoint(34.1785, 131.4737)
                                                val startIndex = graph.nearestNodeIndex(start.latitude, start.longitude)
                                                val goalIndex = graph.nearestNodeIndex(p.latitude, p.longitude)
                                                val result = graph.route(startIndex, goalIndex)
                                                check(result.isReachable) { "有効な自転車ルートが見つかりませんでした" }

                                                (context as? ComponentActivity)?.runOnUiThread {
                                                    val view = mapView
                                                    latestRouteOverlay?.let { view?.overlays?.remove(it) }
                                                    val polyline = view?.let {
                                                        Polyline(it).apply {
                                                            setPoints(result.coordinates.map { (lat, lon) -> GeoPoint(lat, lon) })
                                                            outlinePaint.color = android.graphics.Color.parseColor("#1976D2")
                                                            outlinePaint.strokeWidth = 14f
                                                        }
                                                    }
                                                    if (polyline != null) {
                                                        view.overlays.add(polyline)
                                                        routeOverlay = polyline
                                                        view.invalidate()
                                                    }
                                                    routeSummary = RouteSummary(result.totalDistanceMeters, result.nodeIds.size - 1)
                                                    isCalculatingRoute = false
                                                }
                                            } catch (t: Throwable) {
                                                Log.e("CycleMapGraph", "route failed", t)
                                                (context as? ComponentActivity)?.runOnUiThread {
                                                    warningMessage = "ルート探索失敗: ${t.message}"
                                                    isCalculatingRoute = false
                                                }
                                            }
                                        }.start()
                                        return true
                                    }
                                },
                            ),
                        )
                        mapView = this
                    }
                },
                update = { _ -> },
            )

            // Top-Left: Menu Button
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 16.dp)
                    .zIndex(2f),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 6.dp,
            ) {
                IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.size(48.dp),
                ) {
                    Text("☰", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Right-Side Controls: Map Controls & Zoom & Location FAB stack
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 16.dp)
                    .zIndex(2f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End,
            ) {
                // Layer switch toggle
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 4.dp,
                    modifier = Modifier.clickable {
                        selectedLayer = if (selectedLayer == MapLayer.GSI) MapLayer.OSM else MapLayer.GSI
                    },
                ) {
                    Text(
                        text = selectedLayer.label,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Heading Up / North Up toggle
                Surface(
                    shape = CircleShape,
                    color = if (isHeadingUp) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 4.dp,
                ) {
                    IconButton(
                        onClick = {
                            isHeadingUp = !isHeadingUp
                            if (!isHeadingUp) {
                                mapView?.setMapOrientation(0f)
                                mapView?.invalidate()
                            }
                        },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Text(
                            text = if (isHeadingUp) "▲" else "N",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (isHeadingUp) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Zoom In (+) Button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 4.dp,
                ) {
                    IconButton(
                        onClick = { mapView?.controller?.zoomIn() },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Text(
                            text = "＋",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Zoom Out (-) Button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 4.dp,
                ) {
                    IconButton(
                        onClick = { mapView?.controller?.zoomOut() },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Text(
                            text = "－",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Center / Return to Current Location button
                Surface(
                    shape = if (!followLocation) RoundedCornerShape(22.dp) else CircleShape,
                    color = if (followLocation) MaterialTheme.colorScheme.surface.copy(alpha = 0.92f) else MaterialTheme.colorScheme.primary,
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                followLocation = true
                                currentLocation?.let { loc ->
                                    mapView?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                                }
                            }
                            .padding(horizontal = if (!followLocation) 12.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "⌖",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (followLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        if (!followLocation) {
                            Text(
                                text = "現在地へ",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                    }
                }
            }

            // Top Center: Notifications / Warning Banner / Download Progress
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.9f)
                    .padding(top = 16.dp)
                    .zIndex(2f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Permission Request
                if (!hasLocationPermission) {
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

                // Warning / Error notification
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

                // Prefecture Tile Download Progress Card
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
                                    if (isDone) "✅ ${progress.prefName} 保存完了" else "📥 ${progress.prefName} (${progress.sourceName}) 保存中…",
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

            // Bottom-Right: Attribution Link (Legal requirement)
            Text(
                text = "© OSM / 地理院タイル",
                fontSize = 10.sp,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 180.dp, end = 8.dp)
                    .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .clickable { showLicense = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .zIndex(1f),
            )

            // Bottom Cycling Dashboard (HUD Card)
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .zIndex(2f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    // Route Calculation Indicator
                    AnimatedVisibility(
                        visible = isCalculatingRoute,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
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

                    // Route Summary Section (When route is active)
                    routeSummary?.let { summary ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("目的地までの距離", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val distStr = if (summary.distanceMeters >= 1000.0) {
                                    "%.2f km".format(Locale.US, summary.distanceMeters / 1000.0)
                                } else {
                                    "%.0f m".format(Locale.US, summary.distanceMeters)
                                }
                                Text(
                                    distStr,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    mapView?.let { view ->
                                        routeOverlay?.let { view.overlays.remove(it) }
                                        destinationMarker?.let { view.overlays.remove(it) }
                                        view.invalidate()
                                    }
                                    routeOverlay = null
                                    destinationMarker = null
                                    routeSummary = null
                                    destination = null
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("ルート解除")
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                    }

                    // Main HUD: Speedometer & GPX Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left: Large Speedometer
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "%.1f".format(Locale.US, speedKmh),
                                fontSize = 42.sp,
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
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

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
                    Text("保存範囲: ズームレベル 10〜14")
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
                        onClick = {
                            showDownloadConfirmDialog = false
                            startPrefectureDownload(context, pref, MapSourceType.GSI, gsiTileSource()) { progress ->
                                (context as? ComponentActivity)?.runOnUiThread {
                                    currentTileProgress = progress
                                    if (progress.completed >= progress.total) isDownloading = false
                                }
                            }
                        },
                    ) {
                        Text("🇯🇵 地理院地図（標準）をDL")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showDownloadConfirmDialog = false
                            startPrefectureDownload(context, pref, MapSourceType.OSM, osmTileSource()) { progress ->
                                (context as? ComponentActivity)?.runOnUiThread {
                                    currentTileProgress = progress
                                    if (progress.completed >= progress.total) isDownloading = false
                                }
                            }
                        },
                    ) {
                        Text("🌍 OpenStreetMapをDL")
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
            title = { Text("アプリ情報・ライセンス") },
            text = {
                Text(
                    "CycleMap App\n" +
                        "© 2026 Gorite. All rights reserved.\n\n" +
                        "【使用している地図データ・ライブラリ】\n" +
                        "© OpenStreetMap contributors\n" +
                        "ODbL 1.0 (https://www.openstreetmap.org/copyright)\n\n" +
                        "地理院タイル（国土地理院）\nhttps://maps.gsi.go.jp/development/\n\n" +
                        "地理院タイル利用規約\nhttps://maps.gsi.go.jp/help/termsofuse.html\n\n" +
                        "地図表示：osmdroid (Apache License 2.0)\n" +
                        "位置情報：Google Play services Location",
                )
            },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text("閉じる") } },
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("設定") },
            text = { Text("設定機能（音声案内・単位・テーマ設定など）は今後のアップデートで追加されます。") },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("閉じる") } },
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
            onClose = {
                showDeveloperOptions = false
                selectedMenu = "地図"
            },
        )
    }

    // Location Animation & Map Centering
    LaunchedEffect(currentLocation, mapView, followLocation) {
        val targetLocation = currentLocation ?: return@LaunchedEffect
        val marker = locationMarker ?: return@LaunchedEffect
        val view = mapView ?: return@LaunchedEffect
        val target = GeoPoint(targetLocation.latitude, targetLocation.longitude)
        val start = marker.position
        val durationNanos = 900_000_000L
        val startTime = withFrameNanos { it }
        while (true) {
            val elapsed = withFrameNanos { it } - startTime
            val value = (elapsed.toDouble() / durationNanos).coerceIn(0.0, 1.0)
            val point = GeoPoint(
                start.latitude + (target.latitude - start.latitude) * value,
                start.longitude + (target.longitude - start.longitude) * value,
            )
            marker.position = point
            if (followLocation) {
                view.controller.setCenter(point)
            }
            view.invalidate()
            if (value >= 1.0) break
        }
    }

    // Compass Orientation
    LaunchedEffect(headingDegrees, mapView, isHeadingUp) {
        mapView?.let { view ->
            locationMarker?.rotation = headingDegrees
            if (isHeadingUp) {
                view.setMapOrientation(-headingDegrees)
            }
            view.invalidate()
        }
    }

    // Tile Source Switching
    LaunchedEffect(selectedLayer, mapView) {
        mapView?.let { view ->
            val source = when (selectedLayer) {
                MapLayer.GSI -> gsiTileSource()
                MapLayer.OSM -> osmTileSource()
            }
            view.setTileSource(source)
            view.minZoomLevel = source.minimumZoomLevel.toDouble()
            view.maxZoomLevel = source.maximumZoomLevel.toDouble()
            // GSI labels stay sharp at native 256px; OSM text is small so scale to DPI.
            view.setTilesScaledToDpi(selectedLayer == MapLayer.OSM)
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

    DisposableEffect(mapView) {
        mapView?.onResume()
        onDispose { mapView?.onPause() }
    }
}

@Composable
private fun PrefectureListDialog(
    context: Context,
    onDismiss: () -> Unit,
    onPrefectureSelected: (Prefecture) -> Unit,
) {
    var selectedRegionIndex by remember { mutableIntStateOf(5) } // デフォルト中国地方

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("都道府県を選択してDL") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(380.dp)) {
                // Region Tab
                ScrollableTabRow(
                    selectedTabIndex = selectedRegionIndex,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PrefectureData.REGIONS.forEachIndexed { index, regionName ->
                        Tab(
                            selected = selectedRegionIndex == index,
                            onClick = { selectedRegionIndex = index },
                            text = { Text(regionName, fontSize = 12.sp) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Prefecture List in selected region
                val currentRegion = PrefectureData.REGIONS[selectedRegionIndex]
                val prefectures = remember(currentRegion) {
                    PrefectureData.ALL.filter { it.region == currentRegion }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(prefectures) { pref ->
                        val isGsi = DownloadStatusManager.isDownloaded(context, pref.id, MapSourceType.GSI)
                        val isOsm = DownloadStatusManager.isDownloaded(context, pref.id, MapSourceType.OSM)
                        val tileCount = PrefectureData.calculateTileCount(pref.bounds)
                        val sizeMb = PrefectureData.estimateSizeMb(tileCount)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onPrefectureSelected(pref) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(pref.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("約 ${tileCount}枚 (%.1f MB)".format(Locale.US, sizeMb), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (isGsi) {
                                        Text(
                                            "地理院済",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    if (isOsm) {
                                        Text(
                                            "OSM済",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
    )
}

private fun startPrefectureDownload(
    context: Context,
    pref: Prefecture,
    sourceType: MapSourceType,
    source: XYTileSource,
    onProgress: (TileProgress) -> Unit,
) {
    Thread {
        TileDownloader.download(context, pref, sourceType, source, onProgress)
    }.start()
}

@Composable
private fun IconButton(
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

private fun gsiTileSource() = XYTileSource(
    "GSI Standard", 5, 18, 256, ".png",
    arrayOf("https://cyberjapandata.gsi.go.jp/xyz/std/"),
)

private fun osmTileSource() = XYTileSource(
    "OpenStreetMap", 5, 19, 256, ".png",
    arrayOf(
        "https://a.tile.openstreetmap.org/",
        "https://b.tile.openstreetmap.org/",
        "https://c.tile.openstreetmap.org/",
    ),
)

private fun cycleMapDataDir(context: Context): File =
    File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "CycleMap").apply { mkdirs() }

private fun configureOsmdroid(context: Context, dataDir: File) {
    val tileDir = File(dataDir, "tiles").apply { mkdirs() }
    Configuration.getInstance().apply {
        load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        osmdroidBasePath = dataDir
        osmdroidTileCache = tileDir
        userAgentValue = "CycleMap/1.0 (Android; cycling navigator; personal use)"
        tileDownloadThreads = 4
        tileFileSystemThreads = 8
        tileDownloadMaxQueueSize = 40
        tileFileSystemMaxQueueSize = 40
        cacheMapTileCount = 16.toShort()
        cacheMapTileOvershoot = 2.toShort()
        expirationOverrideDuration = 30L * 24 * 60 * 60 * 1000
        tileFileSystemCacheMaxBytes = 800L * 1024 * 1024
        tileFileSystemCacheTrimBytes = 700L * 1024 * 1024
        isMapViewHardwareAccelerated = true
    }
}

@Composable
private fun ServiceLocationUpdates(
    context: Context,
    onLocationChanged: (location: Location, recording: Boolean, recordedPoints: Int) -> Unit,
    onSpeedChanged: (Double) -> Unit,
) {
    val onLocation by rememberUpdatedState(onLocationChanged)
    val onSpeed by rememberUpdatedState(onSpeedChanged)
    DisposableEffect(context) {
        LocationTrackingService.startTracking(context)
        var previousLocation: Location? = null
        var smoothedSpeed = 0.0
        var serviceRef: LocationTrackingService? = null
        val listener: (Location) -> Unit = { newLocation ->
            val oldLocation = previousLocation
            if (oldLocation != null) {
                val elapsedSeconds = (newLocation.time - oldLocation.time) / 1_000.0
                val distanceMeters = oldLocation.distanceTo(newLocation).toDouble()
                val rawSpeed = if (elapsedSeconds > 0.0 && distanceMeters >= 5.0) distanceMeters / elapsedSeconds else 0.0
                smoothedSpeed = smoothedSpeed * 0.7 + rawSpeed * 0.3
                onSpeed(smoothedSpeed)
            }
            previousLocation = newLocation
            val service = serviceRef
            onLocation(newLocation, service?.recording == true, service?.recordedPoints ?: 0)
        }
        val connection = locationServiceConnection { service ->
            serviceRef = service
            service.addListener(listener)
        }
        LocationTrackingService.bind(context, connection)
        onDispose {
            serviceRef?.removeListener(listener)
            runCatching { context.unbindService(connection) }
        }
    }
}

@Composable
private fun CompassUpdates(context: Context, onHeadingChanged: (Float) -> Unit) {
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        var smoothedHeading = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rotationMatrix = FloatArray(9)
                val orientation = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val rawHeading = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f)
                val delta = ((rawHeading - smoothedHeading + 540f) % 360f) - 180f
                smoothedHeading = (smoothedHeading + delta * 0.15f + 360f) % 360f
                onHeadingChanged(smoothedHeading)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** MapView that keeps one-finger pans instead of letting Compose/drawer intercept them. */
private class CycleMapView(context: Context) : MapView(context) {
    var onUserPan: (() -> Unit)? = null
    private var downX = 0f
    private var downY = 0f
    private var notifiedPan = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                notifiedPan = false
            }
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!notifiedPan && event.pointerCount == 1) {
                    val slop = ViewConfiguration.get(context).scaledTouchSlop
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (dx * dx + dy * dy > slop * slop) {
                        notifiedPan = true
                        onUserPan?.invoke()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
