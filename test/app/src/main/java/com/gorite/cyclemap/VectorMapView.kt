package com.gorite.cyclemap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Googleマップ風の高品質な円形バッジアイコンを動的生成する
 * （白い外枠境界線＋カラー背景円＋中央の白Lucideアイコン）
 */
private fun createPoiBadgeBitmap(
    context: Context,
    @DrawableRes iconRes: Int,
    badgeColor: Int,
    sizeDp: Int = 26,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).roundToInt().coerceAtLeast(16)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = sizePx / 2f

    // 1. 白い外枠ボーダー
    paint.color = Color.WHITE
    paint.style = Paint.Style.FILL
    canvas.drawCircle(radius, radius, radius - 0.5f * density, paint)

    // 2. カラー背景円
    paint.color = badgeColor
    canvas.drawCircle(radius, radius, radius - 2.0f * density, paint)

    // 3. 中央アイコン（白）
    val iconSizePx = (sizeDp * 0.52f * density).roundToInt()
    val drawable = ContextCompat.getDrawable(context, iconRes)?.mutate()
    if (drawable != null) {
        drawable.setTint(Color.WHITE)
        val left = (sizePx - iconSizePx) / 2
        val top = (sizePx - iconSizePx) / 2
        drawable.setBounds(left, top, left + iconSizePx, top + iconSizePx)
        drawable.draw(canvas)
    }
    return bitmap
}

/**
 * OSMカテゴリおよび施設名から動的POI用のバッジ画像キーを決定する。
 */
private fun resolvePoiBadgeKey(category: String, name: String = ""): String {
    return when {
        category.startsWith("shop:convenience") -> "poi-badge-convenience"
        category.startsWith("railway:station") ||
        category.startsWith("railway:halt") ||
        category.startsWith("public_transport:station") -> "poi-badge-station"
        category.startsWith("amenity:toilets") -> "poi-badge-toilet"
        name.contains("道の駅") || category.startsWith("tourism:road_station") -> "poi-badge-road-station"
        category.startsWith("tourism:") -> "poi-badge-tourism"
        category.startsWith("amenity:restaurant") ||
        category.startsWith("amenity:cafe") ||
        category.startsWith("amenity:fast_food") -> "poi-badge-food"
        category.startsWith("shop:bicycle") -> "poi-badge-bike-shop"
        category.startsWith("amenity:hospital") ||
        category.startsWith("amenity:clinic") ||
        category.startsWith("amenity:doctors") -> "poi-badge-hospital"
        category.startsWith("leisure:park") -> "poi-badge-park"
        category.startsWith("amenity:drinking_water") ||
        category.startsWith("amenity:vending_machine") -> "poi-badge-water"
        category.startsWith("amenity:public_bath") -> "poi-badge-bath"
        category.startsWith("amenity:fuel") -> "poi-badge-fuel"
        else -> "poi-badge-default"
    }
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
                            val minLat = bounds.latitudeSouth
                            val maxLat = bounds.latitudeNorth
                            val minLon = bounds.longitudeWest
                            val maxLon = bounds.longitudeEast

                            val center = targetMap.cameraPosition.target ?: return
                            val dbFile = resolveSearchDbFile(context, center.latitude, center.longitude) ?: return

                            fetchPoisJob?.cancel()
                            fetchPoisJob = coroutineScope.launch(Dispatchers.IO) {
                                val spots = searchPoisInBounds(dbFile, minLat, minLon, maxLat, maxLon, limit = 80)
                                val features = spots.map { spot ->
                                    val feature = Feature.fromGeometry(Point.fromLngLat(spot.longitude, spot.latitude))
                                    feature.addStringProperty("name", spot.name)
                                    feature.addStringProperty("icon", resolvePoiBadgeKey(spot.category, spot.name))
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
                                            PropertyFactory.fillExtrusionColor("#E9EAEF"),
                                            PropertyFactory.fillExtrusionOpacity(0.95f),
                                            PropertyFactory.fillExtrusionVerticalGradient(true),
                                        )
                                    }
                                }
                            }
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

        // 3D 鳥瞰 (60°) / 2D 平面 (0°) 切替ボタン (左下に配置して右側のズーム操作バーと分離)
        Button(
            onClick = {
                val map = mapLibreMap ?: return@Button
                val targetTilt = if (currentTilt > 15.0) 0.0 else 60.0
                map.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder(map.cameraPosition)
                            .tilt(targetTilt)
                            .build()
                    ),
                    700
                )
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = if (isNavigationActive) 260.dp else 100.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentTilt > 15.0) androidx.compose.ui.graphics.Color(0xFFE91E63) else androidx.compose.ui.graphics.Color(0xFF1E88E5),
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = if (currentTilt > 15.0) "2D 平面" else "3D 鳥瞰 (60°)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}

/**
 * ルート描画および現在地・案内矢印描画用の GeoJSON ソース・レイヤーを初期化
 */
private fun setupRouteLayers(context: Context, style: Style) {
    // ナビ案内矢印アイコン (ic_nav_arrow) をスタイルに登録
    if (style.getImage("cyclemap-nav-arrow-icon") == null) {
        val arrowBitmap = drawableToBitmap(context, R.drawable.ic_nav_arrow, 96)
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

    // 現在地マーカー用レイヤー (地面に平行に描画)
    if (style.getSource("cyclemap-location-source") == null) {
        val locationSource = GeoJsonSource("cyclemap-location-source")
        style.addSource(locationSource)

        // 現在地外枠（白）: 地面に平行
        val outerCircle = CircleLayer("cyclemap-location-outer", "cyclemap-location-source").apply {
            setProperties(
                PropertyFactory.circleColor(Color.WHITE),
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP), // 地面に平行！
                PropertyFactory.circlePitchScale(Property.CIRCLE_PITCH_SCALE_MAP),
            )
            setFilter(Expression.eq(Expression.get("hasBearing"), Expression.literal(false)))
        }
        style.addLayer(outerCircle)

        // 現在地本体（青）: 地面に平行
        val innerCircle = CircleLayer("cyclemap-location-inner", "cyclemap-location-source").apply {
            setProperties(
                PropertyFactory.circleColor(Color.parseColor("#007AFF")),
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP), // 地面に平行！
                PropertyFactory.circlePitchScale(Property.CIRCLE_PITCH_SCALE_MAP),
            )
            setFilter(Expression.eq(Expression.get("hasBearing"), Expression.literal(false)))
        }
        style.addLayer(innerCircle)

        // 案内時・移動時の進行方向ナビ矢印マーカー: 地面に平行
        val arrowLayer = SymbolLayer("cyclemap-location-arrow", "cyclemap-location-source").apply {
            setProperties(
                PropertyFactory.iconImage("cyclemap-nav-arrow-icon"),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP), // 地図の回転に追従
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP), // 地面に平行に寝かせる！
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
 * Googleマップ風動的POI用のアイコン画像登録・ソース・シンボルレイヤーの初期化 (R2, R4)
 */
private fun setupDynamicPoiLayers(context: Context, style: Style) {
    // 主要カテゴリの円形バッジ画像をスタイルに登録 (R4)
    val badges = listOf(
        "poi-badge-convenience" to (R.drawable.ic_lucide_store to Color.parseColor("#1A73E8")),      // 青
        "poi-badge-station" to (R.drawable.ic_lucide_train_front to Color.parseColor("#1967D2")),   // 濃い青
        "poi-badge-toilet" to (R.drawable.ic_lucide_toilet to Color.parseColor("#00838F")),         // シアン
        "poi-badge-food" to (R.drawable.ic_lucide_utensils to Color.parseColor("#E8710A")),         // オレンジ
        "poi-badge-road-station" to (R.drawable.ic_lucide_camera to Color.parseColor("#0D9488")),   // ティール
        "poi-badge-tourism" to (R.drawable.ic_lucide_camera to Color.parseColor("#8E24AA")),        // 紫
        "poi-badge-bike-shop" to (R.drawable.ic_lucide_wrench to Color.parseColor("#0288D1")),      // スカイブルー
        "poi-badge-hospital" to (R.drawable.ic_lucide_hospital to Color.parseColor("#D93025")),     // 赤
        "poi-badge-park" to (R.drawable.ic_lucide_trees to Color.parseColor("#188038")),            // 緑
        "poi-badge-water" to (R.drawable.ic_lucide_droplets to Color.parseColor("#00ACC1")),        // 水色
        "poi-badge-bath" to (R.drawable.ic_lucide_droplets to Color.parseColor("#00897B")),         // ティール
        "poi-badge-fuel" to (R.drawable.ic_lucide_fuel to Color.parseColor("#F9AB00")),             // アンバー
        "poi-badge-default" to (R.drawable.ic_lucide_map_pin to Color.parseColor("#757575")),       // グレー
    )
    for ((key, pair) in badges) {
        if (style.getImage(key) == null) {
            val bitmap = createPoiBadgeBitmap(context, pair.first, pair.second)
            style.addImage(key, bitmap)
        }
    }

    // 動的POI GeoJSON ソースとシンボルレイヤーの登録 (R2)
    if (style.getSource("cyclemap-dynamic-pois") == null) {
        val poiSource = GeoJsonSource("cyclemap-dynamic-pois")
        style.addSource(poiSource)

        val poiSymbolLayer = SymbolLayer("cyclemap-dynamic-poi-symbols", "cyclemap-dynamic-pois").apply {
            setProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(false),
                PropertyFactory.iconIgnorePlacement(false),
                PropertyFactory.iconOptional(false),

                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textSize(11f),
                PropertyFactory.textColor(Color.parseColor("#202124")),
                PropertyFactory.textHaloColor(Color.WHITE),
                PropertyFactory.textHaloWidth(2.0f),
                PropertyFactory.textHaloBlur(0.5f),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textOffset(arrayOf(0f, 1.0f)),
                PropertyFactory.textPitchAlignment(Property.TEXT_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.textRotationAlignment(Property.TEXT_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.textAllowOverlap(false),
                PropertyFactory.textIgnorePlacement(false),
                PropertyFactory.textOptional(true),
                PropertyFactory.textMaxWidth(8f),
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
