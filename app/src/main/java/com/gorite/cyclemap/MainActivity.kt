package com.gorite.cyclemap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Debug
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.gorite.cyclemap.ui.theme.CycleMapTheme
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import com.gorite.cyclemap.routing.LazyMappedRoadGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

private enum class MapLayer(val label: String) {
    GSI("地理院地図"),
    OSM("OpenStreetMap"),
}

private val YAMAGUCHI_BOUNDS = BoundingBox(34.80, 132.20, 33.70, 130.70)
private const val DOWNLOAD_MIN_ZOOM = 10
// z15-z16 are supported by the downloader but intentionally opt-in: the full
// Yamaguchi bbox is ~89k tiles at z10-z16 and can exceed 1 GB.
private const val DOWNLOAD_MAX_ZOOM = 14

private data class DownloadProgress(val completed: Int, val total: Int)

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
}

@Composable
private fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var selectedLayer by remember { mutableStateOf(MapLayer.GSI) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var locationMarker by remember { mutableStateOf<Marker?>(null) }
    var hasCenteredOnLocation by remember { mutableStateOf(false) }
    var speedKmh by remember { mutableStateOf(0.0) }
    var headingDegrees by remember { mutableStateOf(0f) }
    var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var selectedMenu by remember { mutableStateOf("地図") }
    var mappedGraph by remember { mutableStateOf<LazyMappedRoadGraph?>(null) }
    var graphStatus by remember { mutableStateOf("ルートグラフを読み込み中…") }
    var destination by remember { mutableStateOf<GeoPoint?>(null) }
    var routeOverlay by remember { mutableStateOf<Polyline?>(null) }
    var routeStatus by remember { mutableStateOf<String?>(null) }
    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val latestGraph by rememberUpdatedState(mappedGraph)
    val latestLocation by rememberUpdatedState(currentLocation)
    val latestRouteOverlay by rememberUpdatedState(routeOverlay)

    LaunchedEffect(Unit) {
        val dataDir = cycleMapDataDir(context)
        configureOsmdroid(context, dataDir)
        val graphFile = File(dataDir, "yamaguchi.graph")
        val graphIndex = File(dataDir, "yamaguchi.graph.idx")
        val tileDatabase = File(File(dataDir, "tiles"), SqlTileWriter.DATABASE_FILENAME)
        val missingGraph = buildList {
            if (!graphFile.isFile) add("yamaguchi.graph")
            if (!graphIndex.isFile) add("yamaguchi.graph.idx")
        }
        val tileMissing = !tileDatabase.isFile
        if (missingGraph.isNotEmpty()) {
            val missing = (missingGraph + if (tileMissing) listOf("GSIタイル") else emptyList())
            graphStatus = "不足: ${missing.joinToString()}\n配置先: ${dataDir.absolutePath}"
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
            graphStatus = buildString {
                append("グラフ読込完了 ${result.second}ms / PSS差分 ${result.third / 1024}MiB")
                if (tileMissing) append("\nGSIタイル不足: ${dataDir.absolutePath}/tiles")
            }
            Log.i("CycleMapGraph", "mappedLoadMs=${result.second} pssDeltaKb=${result.third} nodes=${result.first.nodeCount} edges=${result.first.edgeCount}")
        }
    }

    DisposableEffect(mappedGraph) {
        onDispose { mappedGraph?.close() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    if (hasLocationPermission) {
        LocationUpdates(
            context = context,
            onLocationChanged = { currentLocation = it },
            onSpeedChanged = { speedKmh = it * 3.6 },
        )
    }

    CompassUpdates(context) { headingDegrees = it }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("CycleMap", modifier = Modifier.padding(24.dp))
                Divider()
                NavigationDrawerItem(
                    label = { Text("地図") },
                    selected = selectedMenu == "地図",
                    onClick = {
                        selectedMenu = "地図"
                        scope.launch { drawerState.close() }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("山口県をダウンロード") },
                    selected = selectedMenu == "ダウンロード",
                    onClick = {
                        selectedMenu = "ダウンロード"
                        if (!isDownloading) {
                            isDownloading = true
                            downloadProgress = DownloadProgress(0, totalYamaguchiTileCount())
                            Thread {
                                downloadYamaguchiGsiTiles(context) { progress ->
                                    (context as? ComponentActivity)?.runOnUiThread {
                                        downloadProgress = progress
                                        if (progress.completed >= progress.total) isDownloading = false
                                    }
                                }
                            }.start()
                        }
                        scope.launch { drawerState.close() }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("地図情報・ライセンス") },
                    selected = selectedMenu == "地図情報",
                    onClick = {
                        selectedMenu = "地図情報"
                        showLicense = true
                        scope.launch { drawerState.close() }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("設定") },
                    selected = selectedMenu == "設定",
                    onClick = {
                        selectedMenu = "設定"
                        showSettings = true
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
      Box(modifier = modifier.fillMaxSize()) {
        Button(
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(2f)
                .padding(12.dp),
            onClick = { scope.launch { drawerState.open() } },
        ) { Text("☰") }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapContext ->
                Configuration.getInstance().userAgentValue = mapContext.packageName
                configureOsmdroid(mapContext, cycleMapDataDir(mapContext))
                MapView(mapContext).apply {
                    setMultiTouchControls(true)
                    setScrollableAreaLimitDouble(YAMAGUCHI_BOUNDS)
                    setTileSource(gsiTileSource())
                    controller.setZoom(12.0)
                    controller.setCenter(GeoPoint(34.18, 131.47))
                    locationMarker = Marker(this).apply {
                        title = "現在地"
                        icon = ContextCompat.getDrawable(mapContext, R.drawable.ic_current_location)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    overlays.add(locationMarker)
                    overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean = false

                        override fun longPressHelper(p: GeoPoint): Boolean {
                            destination = p
                            routeStatus = "ルート計算中…"
                            val graph = latestGraph
                            if (graph == null) {
                                routeStatus = "グラフ未読込"
                            } else {
                                Thread {
                                    try {
                                        val start = latestLocation?.let { GeoPoint(it.latitude, it.longitude) }
                                            ?: GeoPoint(34.1785, 131.4737)
                                        val startIndex = graph.nearestNodeIndex(start.latitude, start.longitude)
                                        val goalIndex = graph.nearestNodeIndex(p.latitude, p.longitude)
                                        val result = graph.route(startIndex, goalIndex)
                                        check(result.isReachable) { "経路が見つかりません" }
                                        Log.i("CycleMapGraph", "route distanceMeters=${result.totalDistanceMeters} steps=${result.nodeIds.size - 1}")
                                        (context as? ComponentActivity)?.runOnUiThread {
                                            val view = mapView
                                            latestRouteOverlay?.let { view?.overlays?.remove(it) }
                                            val polyline = view?.let {
                                                Polyline(it).apply {
                                                    setPoints(result.coordinates.map { (lat, lon) -> GeoPoint(lat, lon) })
                                                    outlinePaint.color = android.graphics.Color.BLUE
                                                    outlinePaint.strokeWidth = 10f
                                                }
                                            }
                                            if (polyline != null) {
                                                view.overlays.add(polyline)
                                                routeOverlay = polyline
                                                view.invalidate()
                                            }
                                            routeStatus = "計算完了 ${"%.1f".format(result.totalDistanceMeters)}m / ${result.nodeIds.size - 1} steps"
                                        }
                                    } catch (t: Throwable) {
                                        Log.e("CycleMapGraph", "route failed", t)
                                        (context as? ComponentActivity)?.runOnUiThread { routeStatus = "ルート失敗: ${t.message}" }
                                    }
                                }.start()
                            }
                            return true
                        }
                    }))
                    mapView = this
                }
            },
            update = { view ->
                // Location animation below updates the marker on every frame.
            },
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "© OpenStreetMap contributors\n地理院タイル（国土地理院）",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Text(
                graphStatus,
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            routeStatus?.let {
                Text(
                    it,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (!hasLocationPermission) {
                Button(
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                ) { Text("現在地を許可") }
            }
            downloadProgress?.let { progress ->
                Text(
                    "GSI地図DL ${progress.completed}/${progress.total}",
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Text(
            text = "${"%.1f".format(Locale.US, speedKmh)} km/h",
            color = Color.White,
            fontSize = 24.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
      }
    }

    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text("地図情報・ライセンス") },
            text = {
                Text(
                    "© OpenStreetMap contributors\n" +
                        "ODbL 1.0\nhttps://www.openstreetmap.org/copyright\n\n" +
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
            text = { Text("設定項目はこれから追加します。") },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("閉じる") } },
        )
    }

    LaunchedEffect(currentLocation, mapView) {
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
            view.controller.setCenter(point)
            view.invalidate()
            if (value >= 1.0) break
        }
        hasCenteredOnLocation = true
    }

    LaunchedEffect(headingDegrees, mapView) {
        mapView?.let { view ->
            locationMarker?.rotation = headingDegrees
            view.setMapOrientation(-headingDegrees)
            view.invalidate()
        }
    }

    LaunchedEffect(selectedLayer, mapView) {
        mapView?.let { view ->
            view.setTileSource(
                when (selectedLayer) {
                    MapLayer.GSI -> gsiTileSource()
                    MapLayer.OSM -> osmTileSource()
                },
            )
            view.invalidate()
        }
    }

    DisposableEffect(mapView) {
        mapView?.onResume()
        onDispose { mapView?.onPause() }
    }
}

private fun gsiTileSource() = XYTileSource(
    "GSI Standard", 2, 18, 256, ".png",
    arrayOf("https://cyberjapandata.gsi.go.jp/xyz/std/"),
)

private fun cycleMapDataDir(context: Context): File =
    File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "CycleMap").apply { mkdirs() }

private fun configureOsmdroid(context: Context, dataDir: File) {
    val tileDir = File(dataDir, "tiles").apply { mkdirs() }
    Configuration.getInstance().apply {
        load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        setOsmdroidBasePath(dataDir)
        setOsmdroidTileCache(tileDir)
        userAgentValue = context.packageName
    }
}

private fun osmTileSource() = XYTileSource(
    "OpenStreetMap", 0, 19, 256, ".png",
    arrayOf("https://tile.openstreetmap.org/"),
)

private fun tileX(longitude: Double, zoom: Int): Int =
    kotlin.math.floor((longitude + 180.0) / 360.0 * (1 shl zoom)).toInt()

private fun tileY(latitude: Double, zoom: Int): Int =
    kotlin.math.floor(
        (1.0 - kotlin.math.asinh(kotlin.math.tan(Math.toRadians(latitude))) / Math.PI) /
            2.0 * (1 shl zoom),
    ).toInt()

private fun totalYamaguchiTileCount(): Int {
    var total = 0
    for (zoom in DOWNLOAD_MIN_ZOOM..DOWNLOAD_MAX_ZOOM) {
        val minX = tileX(YAMAGUCHI_BOUNDS.lonWest, zoom)
        val maxX = tileX(YAMAGUCHI_BOUNDS.lonEast, zoom)
        val minY = tileY(YAMAGUCHI_BOUNDS.latNorth, zoom)
        val maxY = tileY(YAMAGUCHI_BOUNDS.latSouth, zoom)
        total += (maxX - minX + 1) * (maxY - minY + 1)
    }
    return total
}

private fun downloadYamaguchiGsiTiles(
    context: Context,
    onProgress: (DownloadProgress) -> Unit,
) {
    configureOsmdroid(context, cycleMapDataDir(context))
    val source = gsiTileSource()
    val writer = SqlTileWriter()
    val total = totalYamaguchiTileCount()
    val completed = java.util.concurrent.atomic.AtomicInteger(0)
    val executor = Executors.newFixedThreadPool(8)
    for (zoom in DOWNLOAD_MIN_ZOOM..DOWNLOAD_MAX_ZOOM) {
        val minX = tileX(YAMAGUCHI_BOUNDS.lonWest, zoom)
        val maxX = tileX(YAMAGUCHI_BOUNDS.lonEast, zoom)
        val minY = tileY(YAMAGUCHI_BOUNDS.latNorth, zoom)
        val maxY = tileY(YAMAGUCHI_BOUNDS.latSouth, zoom)
        for (x in minX..maxX) for (y in minY..maxY) {
            executor.submit {
                val connection = (URL("https://cyberjapandata.gsi.go.jp/xyz/std/$zoom/$x/$y.png")
                    .openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "CycleMap/1.0")
                }
                try {
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.use { input ->
                            synchronized(writer) {
                                writer.saveFile(source, MapTileIndex.getTileIndex(zoom, x, y), input, null)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // A failed tile is counted so the UI can finish and the user can retry.
                } finally {
                    connection.disconnect()
                    onProgress(DownloadProgress(completed.incrementAndGet(), total))
                }
            }
        }
    }
    executor.shutdown()
    executor.awaitTermination(30, TimeUnit.MINUTES)
    writer.onDetach()
}

@SuppressLint("MissingPermission")
@Composable
private fun LocationUpdates(
    context: Context,
    onLocationChanged: (Location) -> Unit,
    onSpeedChanged: (Double) -> Unit,
) {
    DisposableEffect(context) {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        var previousLocation: Location? = null
        var smoothedSpeed = 0.0
        val processLocation: (Location) -> Unit = { newLocation ->
            val oldLocation = previousLocation
            if (oldLocation != null) {
                val elapsedSeconds = (newLocation.time - oldLocation.time) / 1_000.0
                val distanceMeters = oldLocation.distanceTo(newLocation).toDouble()
                val calculatedSpeed = if (elapsedSeconds > 0.0) distanceMeters / elapsedSeconds else Double.POSITIVE_INFINITY
                val isTooFast = calculatedSpeed > 60.0
                val isTooInaccurate = newLocation.hasAccuracy() && newLocation.accuracy > 100f
                val isOutOfOrder = elapsedSeconds <= 0.0
                if (!isTooFast && !isTooInaccurate && !isOutOfOrder) {
                    val rawSpeed = if (distanceMeters < 5.0) 0.0 else calculatedSpeed
                    smoothedSpeed = smoothedSpeed * 0.7 + rawSpeed * 0.3
                    onSpeedChanged(smoothedSpeed)
                    previousLocation = newLocation
                    onLocationChanged(newLocation)
                }
            } else {
                previousLocation = newLocation
                onLocationChanged(newLocation)
            }
        }
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(processLocation)
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
            .setMinUpdateIntervalMillis(250L)
            .setMaxUpdateDelayMillis(500L)
            .build()
        fusedClient.lastLocation.addOnSuccessListener { lastLocation ->
            if (lastLocation != null) {
                val ageMillis = System.currentTimeMillis() - lastLocation.time
                if (ageMillis in 0..120_000 && (!lastLocation.hasAccuracy() || lastLocation.accuracy <= 100f)) {
                    processLocation(lastLocation)
                }
            }
        }
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        onDispose { fusedClient.removeLocationUpdates(callback) }
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
