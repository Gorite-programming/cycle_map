package com.gorite.cyclemap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Paint
import androidx.annotation.DrawableRes
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gorite.cyclemap.data.SearchDbSelection
import com.gorite.cyclemap.data.SearchDbSelector
import com.gorite.cyclemap.ui.cycling.NearbySpot
import com.gorite.cyclemap.ui.cycling.PoiCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.osmdroid.util.GeoPoint
import java.io.File
import kotlin.math.roundToInt

/**
 * 広島エリアのオフラインベクターデータ有効範囲 (BBox)
 */
private val HIROSHIMA_VECTOR_BOUNDS = object {
    val minLat = 34.15
    val maxLat = 34.65
    val minLon = 132.15
    val maxLon = 132.65
    fun contains(lat: Double, lon: Double): Boolean =
        lat in minLat..maxLat && lon in minLon..maxLon
}

/**
 * 端末内の PMTiles ファイルを探すヘルパー関数。
 * CycleMap のデータディレクトリ、アプリ個別ファイル領域、テストアプリの領域を順次探索する。
 */
fun resolveOfflinePmtilesPath(context: Context): String? {
    val candidates = listOf(
        File(cycleMapDataDir(context), "tiles/Hiroshima_osm.pmtiles"),
        File(context.getExternalFilesDir("tiles"), "Hiroshima_osm.pmtiles"),
        File("/sdcard/Android/data/com.gorite.vectortest/files/tiles/Hiroshima_osm.pmtiles"),
        File("/sdcard/Android/data/com.gorite.cyclemap/files/Documents/CycleMap/tiles/Hiroshima_osm.pmtiles"),
        File("/storage/emulated/0/Android/data/com.gorite.vectortest/files/tiles/Hiroshima_osm.pmtiles"),
        File("/storage/emulated/0/Android/data/com.gorite.cyclemap.test/files/tiles/Hiroshima_osm.pmtiles"),
        File("/storage/emulated/0/Android/data/com.gorite.cyclemap/files/tiles/Hiroshima_osm.pmtiles"),
    )
    for (candidate in candidates) {
        if (candidate.exists() && candidate.length() > 0) {
            return candidate.absolutePath
        }
    }
    return null
}

/**
 * 端末内の検索DBファイル (.search.db) を探すヘルパー関数。
 * 指定座標に対応する県DBを優先し、フォールバックとして Hiroshima.search.db や配置済みDBを探索する。
 */
fun resolveSearchDbFile(context: Context, lat: Double, lon: Double): File? {
    val dataDir = cycleMapDataDir(context)
    val sel = SearchDbSelector.select(lat, lon)
    if (sel is SearchDbSelection.Available) {
        val f = SearchDbSelector.resolveDbFile(dataDir, sel.fileName)
        if (f != null && f.exists() && f.length() > 0) return f
    }
    val candidates = listOf(
        SearchDbSelector.findFirstAvailableDb(dataDir),
        File(dataDir, "Hiroshima.search.db"),
        File(context.getExternalFilesDir("Documents/CycleMap"), "Hiroshima.search.db"),
        File("/sdcard/Android/data/com.gorite.cyclemap/files/Documents/CycleMap/Hiroshima.search.db"),
        File("/storage/emulated/0/Android/data/com.gorite.cyclemap/files/Documents/CycleMap/Hiroshima.search.db"),
        File("/sdcard/Android/data/com.gorite.cyclemap.test/files/Documents/CycleMap/Hiroshima.search.db"),
        File("/storage/emulated/0/Android/data/com.gorite.cyclemap.test/files/Documents/CycleMap/Hiroshima.search.db"),
    )
    for (candidate in candidates) {
        if (candidate != null && candidate.exists() && candidate.length() > 0) {
            return candidate
        }
    }
    return null
}

/**
 * VectorDrawable を Bitmap に変換するヘルパー関数
 */
private fun drawableToBitmap(context: Context, drawableId: Int, sizePx: Int, tintColor: Int? = null): Bitmap {
    val drawable = ContextCompat.getDrawable(context, drawableId)?.mutate()
        ?: return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    if (tintColor != null) {
        drawable.setTint(tintColor)
    }
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

/**
 * CycleMap-Test 用の MapLibre ベクター地図コンポーネント。
 * 本家 Liberty スタイルの完全オフライン描画、2D/3D シームレス切替、ルート描画に対応。
 */
@Composable
internal fun VectorMapView(
    modifier: Modifier = Modifier,
    routePoints: List<GeoPoint> = emptyList(),
    currentLocation: GeoPoint? = null,
    bearing: Float? = null,
    selectedPoiSpot: NearbySpot? = null,
    followLocation: Boolean = true,
    isNavigationActive: Boolean = false,
    poiVisible: Boolean = true,
    poiSelected: Set<PoiCategory> = PoiCategory.defaults(),
    cameraPadding: PaddingValues = PaddingValues(0.dp),
    onTiltChanged: (Double) -> Unit = {},
    onPoiClick: (NearbySpot) -> Unit = {},
    onUserPan: () -> Unit = {},
    onMapReady: (MapLibreMap) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentTilt by remember { mutableDoubleStateOf(0.0) }
    var lastExtruded by remember { mutableStateOf(false) }
    var offlineDataMissing by remember { mutableStateOf(false) }
    var fetchPoisJob by remember { mutableStateOf<Job?>(null) }
    var triggerPoisRef by remember { mutableStateOf<(() -> Unit)?>(null) }

    val mapView = remember {
        val options = MapLibreMapOptions.createFromAttributes(context)
            .localIdeographFontFamilyEnabled(true)
            .localIdeographFontFamily("sans-serif")
        MapView(context, options).apply {
            onCreate(Bundle())
        }
    }

    // ライフサイクル管理
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            fetchPoisJob?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        mapLibreMap = map
                        onMapReady(map)

                        // 過剰なタイル事前読み込みを抑制（メモリ逼迫・OOM防止）
                        map.prefetchZoomDelta = 2

                        // 2本指上下移動によるチルト（表示角度）変更ジェスチャーを有効化
                        map.uiSettings.isTiltGesturesEnabled = true
                        map.uiSettings.isRotateGesturesEnabled = true
                        map.uiSettings.isScrollGesturesEnabled = true
                        map.uiSettings.isZoomGesturesEnabled = true
                        map.setMinPitchPreference(0.0)
                        map.setMaxPitchPreference(60.0)

                        // DEF-02: ロゴおよびアトリビューション表示が最下部HUDに隠れないよう下部マージンを設定
                        val density = context.resources.displayMetrics.density
                        val bottomMarginPx = (96 * density).toInt()
                        val sideMarginPx = (16 * density).toInt()
                        map.uiSettings.setLogoMargins(sideMarginPx, 0, 0, bottomMarginPx)
                        map.uiSettings.setAttributionMargins(sideMarginPx + (80 * density).toInt(), 0, 0, bottomMarginPx)

                        // 2本指上下移動（Shoveジェスチャー）の感度・許容角度を最適化
                        map.gesturesManager?.let { gm ->
                            gm.shoveGestureDetector?.let { detector ->
                                detector.isEnabled = true
                                detector.maxShoveAngle = 45f
                            }
                        }

                        // 初期カメラ位置（現在地が有効データ範囲内なら現在地、範囲外またはnullなら広島中心部）
                        val initialTarget = if (currentLocation != null &&
                            HIROSHIMA_VECTOR_BOUNDS.contains(currentLocation.latitude, currentLocation.longitude)
                        ) {
                            LatLng(currentLocation.latitude, currentLocation.longitude)
                        } else {
                            LatLng(34.3965, 132.4596)
                        }
                        map.cameraPosition = CameraPosition.Builder()
                            .target(initialTarget)
                            .zoom(14.5)
                            .tilt(0.0)
                            .bearing(0.0)
                            .build()

                        // オフライン Liberty スタイルを適用 (完全オフラインフェイルセーフ)
                        val pmtilesPath = resolveOfflinePmtilesPath(context)
                        if (pmtilesPath == null) {
                            // オンラインへフォールバックせず安全に中断
                            offlineDataMissing = true
                            return@getMapAsync
                        }

                        // 表示中領域のPOIをオフラインDBから動的フェッチして描画するヘルパー
                        fun triggerFetchPois(targetMap: MapLibreMap) {
                            if (!poiVisible || isNavigationActive) {
                                fetchPoisJob?.cancel()
                                targetMap.getStyle { style ->
                                    style.getSourceAs<GeoJsonSource>("cyclemap-dynamic-pois")
                                        ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                                }
                                return
                            }

                            val zoom = targetMap.cameraPosition.zoom
                            if (zoom < 14.0) {
                                fetchPoisJob?.cancel()
                                targetMap.getStyle { style ->
                                    style.getSourceAs<GeoJsonSource>("cyclemap-dynamic-pois")
                                        ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                                }
                                return
                            }

                            val bounds = targetMap.projection.visibleRegion.latLngBounds
                            val latSpan = (bounds.latitudeNorth - bounds.latitudeSouth).coerceAtLeast(0.001)
                            val lonSpan = (bounds.longitudeEast - bounds.longitudeWest).coerceAtLeast(0.001)

                            // 画面外 20% のバッファを含めてパン時のチラつきを解消
                            val minLat = bounds.latitudeSouth - latSpan * 0.20
                            val maxLat = bounds.latitudeNorth + latSpan * 0.20
                            val minLon = bounds.longitudeWest - lonSpan * 0.20
                            val maxLon = bounds.longitudeEast + lonSpan * 0.20

                            val center = targetMap.cameraPosition.target ?: return
                            val dbFile = resolveSearchDbFile(context, center.latitude, center.longitude) ?: return

                            val categoryPrefixes = poiSelected.flatMap { it.prefixes }

                            fetchPoisJob?.cancel()
                            fetchPoisJob = coroutineScope.launch(Dispatchers.IO) {
                                val spots = searchPoisInBounds(
                                    dbFile = dbFile,
                                    minLat = minLat,
                                    minLon = minLon,
                                    maxLat = maxLat,
                                    maxLon = maxLon,
                                    categoryPrefixes = categoryPrefixes,
                                    limit = 300,
                                )
                                val features = spots.mapNotNull { spot ->
                                    val cat = PoiCategory.forCategory(spot.category) ?: return@mapNotNull null
                                    // ズーム14〜15は重要カテゴリのみ表示
                                    if (zoom < 15.0 && !cat.important) return@mapNotNull null

                                    val feature = Feature.fromGeometry(Point.fromLngLat(spot.longitude, spot.latitude))
                                    // ズーム15以上で名称ラベル表示
                                    val displayName = if (zoom >= 15.0) spot.name else ""
                                    feature.addStringProperty("name", displayName)
                                    feature.addStringProperty("rawName", spot.name)
                                    feature.addStringProperty("category", spot.category)
                                    feature.addStringProperty("iconKey", "poi-pin-${cat.name}")
                                    val colorInt = poiTextColor(cat)
                                    val hexColor = String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and colorInt)
                                    feature.addStringProperty("textColor", hexColor)
                                    feature
                                }
                                withContext(Dispatchers.Main) {
                                    targetMap.getStyle { style ->
                                        style.getSourceAs<GeoJsonSource>("cyclemap-dynamic-pois")
                                            ?.setGeoJson(FeatureCollection.fromFeatures(features))
                                    }
                                }
                            }
                        }
                        triggerPoisRef = { triggerFetchPois(map) }

                        // メインスレッドでのファイルI/Oを回避（Coroutines + Dispatchers.IO）
                        coroutineScope.launch {
                            val finalStyleJson = withContext(Dispatchers.IO) {
                                val baseJson = context.assets.open("styles/protomaps_light.json")
                                    .bufferedReader().use { it.readText() }
                                baseJson.replace("__LOCAL_PMTILES__", "pmtiles://file://$pmtilesPath")
                            }

                            withContext(Dispatchers.Main) {
                                map.setStyle(Style.Builder().fromJson(finalStyleJson)) { style ->
                                    // ルート表示用 GeoJson レイヤーおよび現在地・案内矢印マーカーを登録
                                    setupRouteLayers(context, style)
                                    // Googleマップ風動的POIレイヤーを登録
                                    setupDynamicPoiLayers(context, style)
                                    // 初期表示時のPOIを描画
                                    triggerFetchPois(map)
                                }
                            }
                        }

                        // カメラ停止時リスナー (R3): 現在画面内のPOIをオフラインDBから動的フェッチ
                        map.addOnCameraIdleListener {
                            triggerFetchPois(map)
                        }

                        // カメラ変更リスナー: 傾斜角に応じて 2D/3D をヒステリシス制御で切り替え
                        // 17°超で 3D 押し出し、13°未満で 2D 平面図形に切替。13°〜17°は現在の状態を維持してチャタリングを完全防止
                        map.addOnCameraMoveListener {
                            val pos = map.cameraPosition
                            currentTilt = pos.tilt
                            onTiltChanged(pos.tilt)

                            val currentExtruded = lastExtruded
                            val shouldExtrude = when {
                                pos.tilt > 17.0 -> true
                                pos.tilt < 13.0 -> false
                                else -> currentExtruded
                            }
                            if (shouldExtrude != lastExtruded) {
                                lastExtruded = shouldExtrude
                                map.getStyle { style ->
                                    val b2d = style.getLayer("buildings-2d")
                                    val b3d = style.getLayer("buildings-3d")
                                    if (b2d != null && b3d != null) {
                                        b2d.setProperties(
                                            PropertyFactory.visibility(
                                                if (shouldExtrude) Property.NONE else Property.VISIBLE
                                            )
                                        )
                                        b3d.setProperties(
                                            PropertyFactory.visibility(
                                                if (shouldExtrude) Property.VISIBLE else Property.NONE
                                            ),
                                            PropertyFactory.fillExtrusionColor("#F0F1F5"),
                                            PropertyFactory.fillExtrusionOpacity(0.95f),
                                            PropertyFactory.fillExtrusionVerticalGradient(true),
                                        )
                                    }
                                }
                            }
                        }

                        // POIタップ検出リスナー (Googleマップ風の下部詳細カード連携)
                        map.addOnMapClickListener { latLng ->
                            val screenPoint = map.projection.toScreenLocation(latLng)
                            val density = context.resources.displayMetrics.density
                            val touchSlop = 24 * density
                            val rect = android.graphics.RectF(
                                screenPoint.x - touchSlop,
                                screenPoint.y - touchSlop,
                                screenPoint.x + touchSlop,
                                screenPoint.y + touchSlop,
                            )
                            val features = map.queryRenderedFeatures(rect, "cyclemap-dynamic-poi-symbols")
                            if (features.isNotEmpty()) {
                                val feat = features[0]
                                val name = feat.getStringProperty("rawName") ?: feat.getStringProperty("name") ?: ""
                                val category = feat.getStringProperty("category") ?: ""
                                val geom = feat.geometry() as? Point
                                if (geom != null) {
                                    val spot = NearbySpot(
                                        name = name,
                                        category = category,
                                        latitude = geom.latitude(),
                                        longitude = geom.longitude(),
                                        distanceM = 0.0,
                                    )
                                    onPoiClick(spot)
                                    return@addOnMapClickListener true
                                }
                            }
                            false
                        }

                        // ユーザーのドラッグ操作検知 (追従解除用)
                        map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                            override fun onMoveBegin(detector: org.maplibre.android.gestures.MoveGestureDetector) {
                                onUserPan()
                            }
                            override fun onMove(detector: org.maplibre.android.gestures.MoveGestureDetector) {}
                            override fun onMoveEnd(detector: org.maplibre.android.gestures.MoveGestureDetector) {}
                        })
                    }
                }
            }
        )

        // オフラインデータ未検出時の警告オーバーレイ (完全オフラインフェイルセーフ)
        if (offlineDataMissing) {
            Card(
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFD32F2F)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 80.dp, start = 16.dp, end = 16.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⚠ オフラインベクターデータ未検出",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hiroshima_osm.pmtiles が配置されていません。\n端末ストレージ (Documents/CycleMap/tiles/ 等) を確認してください。\n※完全オフライン運用の安全のため、外部通信への自動フォールバックは行いません。",
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // ルート更新の反映
        LaunchedEffect(routePoints, mapLibreMap) {
            val map = mapLibreMap ?: return@LaunchedEffect
            map.getStyle { style ->
                updateRouteLine(style, routePoints)
            }
        }

        // 現在地および案内矢印の更新反映
        LaunchedEffect(currentLocation, bearing, mapLibreMap) {
            val map = mapLibreMap ?: return@LaunchedEffect
            map.getStyle { style ->
                updateLocationPoint(style, currentLocation, bearing)
            }
        }

        // 現在地追従（followLocation が有効な場合）
        LaunchedEffect(currentLocation, followLocation, mapLibreMap) {
            val map = mapLibreMap ?: return@LaunchedEffect
            val loc = currentLocation ?: return@LaunchedEffect
            if (followLocation) {
                map.easeCamera(
                    CameraUpdateFactory.newLatLng(LatLng(loc.latitude, loc.longitude)),
                    500
                )
            }
        }

        // DEF-07: POI選択時のカメラ移動およびハイライトピン描画
        LaunchedEffect(selectedPoiSpot, mapLibreMap) {
            val map = mapLibreMap ?: return@LaunchedEffect
            val spot = selectedPoiSpot
            if (spot == null) {
                map.getStyle { style ->
                    style.getSourceAs<GeoJsonSource>("cyclemap-poi-source")
                        ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                }
                return@LaunchedEffect
            }
            map.animateCamera(
                CameraUpdateFactory.newLatLng(LatLng(spot.latitude, spot.longitude)),
                800,
            )
            map.getStyle { style ->
                val point = Point.fromLngLat(spot.longitude, spot.latitude)
                val feature = Feature.fromGeometry(point).apply {
                    addStringProperty("title", spot.name)
                }
                style.getSourceAs<GeoJsonSource>("cyclemap-poi-source")
                    ?.setGeoJson(FeatureCollection.fromFeature(feature))
            }
        }

        // POI設定変更・ナビ状態切替時の動的POI再フェッチ
        LaunchedEffect(poiVisible, isNavigationActive, poiSelected, triggerPoisRef) {
            triggerPoisRef?.invoke()
        }

        // ナビ案内時等のカメラパディング連携 (自車位置の前方視界確保)
        val density = LocalDensity.current
        LaunchedEffect(cameraPadding, mapLibreMap, density) {
            val map = mapLibreMap ?: return@LaunchedEffect
            val leftPx = with(density) { cameraPadding.calculateLeftPadding(LayoutDirection.Ltr).roundToPx() }
            val topPx = with(density) { cameraPadding.calculateTopPadding().roundToPx() }
            val rightPx = with(density) { cameraPadding.calculateRightPadding(LayoutDirection.Ltr).roundToPx() }
            val bottomPx = with(density) { cameraPadding.calculateBottomPadding().roundToPx() }
            map.setPadding(leftPx, topPx, rightPx, bottomPx)
        }
    }
}

/**
 * ルート描画および現在地・案内矢印描画用の GeoJSON ソース・レイヤーを初期化
 */
private fun setupRouteLayers(context: Context, style: Style) {
    // ナビ案内矢印アイコン (ic_nav_arrow) & 現在地ドット (ic_location_dot) をスタイルに登録
    if (style.getImage("cyclemap-location-dot-icon") == null) {
        val dotBitmap = drawableToBitmap(context, R.drawable.ic_location_dot, 72)
        style.addImage("cyclemap-location-dot-icon", dotBitmap)
    }
    if (style.getImage("cyclemap-nav-arrow-icon") == null) {
        val arrowBitmap = drawableToBitmap(context, R.drawable.ic_nav_arrow, 84)
        style.addImage("cyclemap-nav-arrow-icon", arrowBitmap)
    }

    if (style.getSource("cyclemap-route-source") == null) {
        val routeSource = GeoJsonSource("cyclemap-route-source")
        style.addSource(routeSource)

        // ① 通常のルート線（100%不透明度、地面レベル） -> 3D建物の下に潜り込ませる
        val casingLayer = LineLayer("cyclemap-route-casing", "cyclemap-route-source").apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#0D47A1")), // 濃い青
                PropertyFactory.lineWidth(9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        val routeLayer = LineLayer("cyclemap-route-line", "cyclemap-route-source").apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#00B0FF")), // 鮮やかなシアン
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }

        val hasBuilding3d = style.getLayer("buildings-3d") != null
        if (hasBuilding3d) {
            style.addLayerBelow(casingLayer, "buildings-3d")
            style.addLayerBelow(routeLayer, "buildings-3d")
        } else {
            style.addLayer(casingLayer)
            style.addLayer(routeLayer)
        }

        // ② 建物越し用のX-Rayルート線（50%薄め） -> 3D建物の最前面に配置
        // 建物がない場所では下層の100%線と重なって100%になり、建物の奥にあるときだけ50%に透けて見える
        val xrayRouteLayer = LineLayer("cyclemap-route-xray", "cyclemap-route-source").apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#00B0FF")),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineOpacity(0.50f), // 建物越しは50%に薄める
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        style.addLayer(xrayRouteLayer)
    }

    // 現在地マーカー用レイヤー (3Dチルト時も縮小せず、画面正面を向いて適正サイズを維持)
    if (style.getSource("cyclemap-location-source") == null) {
        val locationSource = GeoJsonSource("cyclemap-location-source")
        style.addSource(locationSource)

        // 停止時・方位なし: 標準青丸ドット (ic_location_dot)
        val dotLayer = SymbolLayer("cyclemap-location-dot", "cyclemap-location-source").apply {
            setProperties(
                PropertyFactory.iconImage("cyclemap-location-dot-icon"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT), // チルト時も正面を向く！
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(0.6f),
            )
            setFilter(Expression.eq(Expression.get("hasBearing"), Expression.literal(false)))
        }
        style.addLayer(dotLayer)

        // 移動時・案内時: ナビ矢印マーカー (ic_nav_arrow)
        val arrowLayer = SymbolLayer("cyclemap-location-arrow", "cyclemap-location-source").apply {
            setProperties(
                PropertyFactory.iconImage("cyclemap-nav-arrow-icon"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP), // 地図の北基準で追従
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT), // チルト時も縮まない！
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(0.6f),
            )
            setFilter(Expression.eq(Expression.get("hasBearing"), Expression.literal(true)))
        }
        style.addLayer(arrowLayer)
    }

    // DEF-07: POIハイライトピン用アイコン・ソース・レイヤーの登録
    if (style.getImage("cyclemap-poi-pin-icon") == null) {
        val pinBitmap = drawableToBitmap(context, R.drawable.ic_lucide_map_pin, 96, Color.parseColor("#E91E63"))
        style.addImage("cyclemap-poi-pin-icon", pinBitmap)
    }
    if (style.getSource("cyclemap-poi-source") == null) {
        val poiSource = GeoJsonSource("cyclemap-poi-source")
        style.addSource(poiSource)

        val poiLayer = SymbolLayer("cyclemap-poi-pin", "cyclemap-poi-source").apply {
            setProperties(
                PropertyFactory.iconImage("cyclemap-poi-pin-icon"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(1.0f),
            )
        }
        style.addLayer(poiLayer)
    }
}

/**
 * 現在地座標および進行方向を GeoJSON に更新
 */
private fun updateLocationPoint(style: Style, location: GeoPoint?, bearing: Float?) {
    val source = style.getSourceAs<GeoJsonSource>("cyclemap-location-source") ?: return
    if (location == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }
    val point = Point.fromLngLat(location.longitude, location.latitude)
    val hasBearing = bearing != null && bearing.isFinite()
    val feature = Feature.fromGeometry(point).apply {
        addNumberProperty("bearing", (bearing ?: 0f).toDouble())
        addBooleanProperty("hasBearing", hasBearing)
    }
    source.setGeoJson(FeatureCollection.fromFeature(feature))
}

/**
 * ルート座標リストを GeoJSON Feature に変換してスタイルへ更新
 */
private fun updateRouteLine(style: Style, points: List<GeoPoint>) {
    val source = style.getSourceAs<GeoJsonSource>("cyclemap-route-source") ?: return
    if (points.size < 2) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }

    val coordinates = points.map { pt ->
        Point.fromLngLat(pt.longitude, pt.latitude)
    }
    val lineString = LineString.fromLngLats(coordinates)
    val feature = Feature.fromGeometry(lineString)
    source.setGeoJson(FeatureCollection.fromFeature(feature))
}

/**
 * 標準地図と完全同一の Googleマップ風しずく型ピン画像をスタイルに登録する
 */
private fun setupDynamicPoiLayers(context: Context, style: Style) {
    // 全13カテゴリのしずく型ピン画像をスタイルに登録
    for (category in PoiCategory.entries) {
        val key = "poi-pin-${category.name}"
        if (style.getImage(key) == null) {
            val visual = googlePoiMarker(context, category, "", showLabel = false)
            val drawable = visual.drawable
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            style.addImage(key, bitmap)
        }
    }

    // 動的POI GeoJSON ソースとシンボルレイヤーの登録
    if (style.getSource("cyclemap-dynamic-pois") == null) {
        val poiSource = GeoJsonSource("cyclemap-dynamic-pois")
        style.addSource(poiSource)

        val poiSymbolLayer = SymbolLayer("cyclemap-dynamic-poi-symbols", "cyclemap-dynamic-pois").apply {
            setProperties(
                PropertyFactory.iconImage(Expression.get("iconKey")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(false),
                PropertyFactory.iconIgnorePlacement(false),
                PropertyFactory.iconOptional(false),

                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textSize(10.5f),
                PropertyFactory.textColor(Expression.get("textColor")),
                PropertyFactory.textHaloColor(Color.WHITE),
                PropertyFactory.textHaloWidth(2.2f),
                PropertyFactory.textHaloBlur(0.3f),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_LEFT),
                PropertyFactory.textOffset(arrayOf(1.0f, -0.9f)),
                PropertyFactory.textPitchAlignment(Property.TEXT_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.textRotationAlignment(Property.TEXT_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.textAllowOverlap(false),
                PropertyFactory.textIgnorePlacement(false),
                PropertyFactory.textOptional(true),
                PropertyFactory.textMaxWidth(7.5f),
            )
        }

        val poiPinLayer = style.getLayer("cyclemap-poi-pin")
        if (poiPinLayer != null) {
            style.addLayerBelow(poiSymbolLayer, "cyclemap-poi-pin")
        } else {
            style.addLayer(poiSymbolLayer)
        }
    }
}
