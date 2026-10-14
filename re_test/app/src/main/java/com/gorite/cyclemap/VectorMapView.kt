package com.gorite.cyclemap

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gorite.cyclemap.data.TileSourceSelection
import com.gorite.cyclemap.data.TileSourceSelector
import com.gorite.cyclemap.power.CameraMotionPolicy
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
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.osmdroid.util.GeoPoint



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
    speedMps: Float? = null,
    isNightMode: Boolean = false,
    isPowerSaveActive: Boolean = false,
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

    // AndroidView factory 内の長期生存コールバックから最新値を参照するための状態ホルダー
    val currentPowerSaveActive by rememberUpdatedState(isPowerSaveActive)
    val currentNightMode by rememberUpdatedState(isNightMode)
    val currentPoiVisible by rememberUpdatedState(poiVisible)
    val currentNavigationActive by rememberUpdatedState(isNavigationActive)
    val currentPoiSelected by rememberUpdatedState(poiSelected)
    val currentOnTiltChanged by rememberUpdatedState(onTiltChanged)
    val currentOnPoiClick by rememberUpdatedState(onPoiClick)
    val currentOnUserPan by rememberUpdatedState(onUserPan)

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentTilt by remember { mutableDoubleStateOf(0.0) }
    var lastExtruded by remember { mutableStateOf(false) }
    var offlineDataMissing by remember { mutableStateOf(false) }
    var offlineMissingPrefecture by remember { mutableStateOf<String?>(null) }
    var fetchPoisJob by remember { mutableStateOf<Job?>(null) }
    var triggerPoisRef by remember { mutableStateOf<(() -> Unit)?>(null) }

    // カメラ更新およびPOIフェッチのデバウンス制御用状態
    var lastFollowLocation by remember { mutableStateOf(followLocation) }
    var lastCameraLat by remember { mutableDoubleStateOf(Double.NaN) }
    var lastCameraLon by remember { mutableDoubleStateOf(Double.NaN) }
    var lastCameraUpdateTime by remember { mutableLongStateOf(0L) }
    var lastPoiFetchCenter by remember { mutableStateOf<LatLng?>(null) }
    var lastPoiFetchZoom by remember { mutableDoubleStateOf(-1.0) }

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

                        // 現在地の県を優先し、未導入なら別県のタイルへ切り替えない。
                        val initialTarget = if (currentLocation != null) {
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
                        val tileSelection = TileSourceSelector.select(initialTarget.latitude, initialTarget.longitude)
                        val pmtilesPath = resolveOfflinePmtilesPath(context, initialTarget.latitude, initialTarget.longitude)
                        if (pmtilesPath == null) {
                            // オンラインへフォールバックせず安全に中断
                            offlineMissingPrefecture = when (tileSelection) {
                                is TileSourceSelection.Available -> tileSelection.prefectureName
                                is TileSourceSelection.Unavailable -> tileSelection.prefectureName
                            }
                            offlineDataMissing = true
                            return@getMapAsync
                        }

                        // 表示中領域のPOIをオフラインDBから動的フェッチして描画するヘルパー
                        fun triggerFetchPois(targetMap: MapLibreMap) {
                            if (!currentPoiVisible || currentNavigationActive) {
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

                            val categoryPrefixes = currentPoiSelected.flatMap { it.prefixes }

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
                                val features = createPoiFeatures(spots, zoom, currentNightMode)
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
                                buildVectorStyleJson(context, isNightMode, pmtilesPath)
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

                        // カメラ停止時リスナー (R3): 現在画面内のPOIをオフラインDBから動的フェッチ (デバウンス制御)
                        map.addOnCameraIdleListener {
                            val target = map.cameraPosition.target
                            val zoom = map.cameraPosition.zoom
                            val last = lastPoiFetchCenter
                            if (last != null && target != null &&
                                CameraMotionPolicy.distanceMeters(last.latitude, last.longitude, target.latitude, target.longitude) < 20.0 &&
                                kotlin.math.abs(zoom - lastPoiFetchZoom) < 0.2
                            ) {
                                return@addOnCameraIdleListener // 停止中・微小移動時の無駄なDBクエリ・GPU再描画を抑止
                            }
                            lastPoiFetchCenter = target
                            lastPoiFetchZoom = zoom
                            triggerFetchPois(map)
                        }

                        // カメラ変更リスナー: 傾斜角に応じて 2D/3D をヒステリシス制御で切り替え
                        // 省電力モード時は強制的に2D平面表示を維持 (Snapdragon 888の発熱・電力消費を抑制)
                        // 通常時は 17°超で 3D 押し出し、13°未満で 2D 平面図形に切替。13°〜17°は現在の状態を維持
                        map.addOnCameraMoveListener {
                            val pos = map.cameraPosition
                            currentTilt = pos.tilt
                            onTiltChanged(pos.tilt)

                            val currentExtruded = lastExtruded
                            val shouldExtrude = if (isPowerSaveActive) {
                                false
                            } else {
                                when {
                                    pos.tilt > 17.0 -> true
                                    pos.tilt < 13.0 -> false
                                    else -> currentExtruded
                                }
                            }
                            if (shouldExtrude != lastExtruded) {
                                lastExtruded = shouldExtrude
                                map.getStyle { style ->
                                    updateBuildingExtrusion(style, shouldExtrude, isNightMode)
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
            OfflineVectorMissingBanner(
                prefectureName = offlineMissingPrefecture,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }


        // ルート更新の反映
        LaunchedEffect(routePoints, mapLibreMap) {
            val map = mapLibreMap ?: return@LaunchedEffect
            map.getStyle { style ->
                updateRouteLine(style, routePoints)
            }
        }

        // 現在地および案内矢印の更新反映
        LaunchedEffect(currentLocation, bearing, isNavigationActive, mapLibreMap) {
            val map = mapLibreMap ?: return@LaunchedEffect
            map.getStyle { style ->
                updateLocationPoint(style, currentLocation, bearing, isNavigationActive)
            }
        }

        // 昼夜連動ナイトモードの動的切替反映
        LaunchedEffect(isNightMode, mapLibreMap) {
            val map = mapLibreMap ?: return@LaunchedEffect
            map.getStyle { style ->
                applyNightModeColors(style, isNightMode)
            }
        }

        // 省電力モード時の 3D -> 2D 自動フォールバック即時反映
        LaunchedEffect(isPowerSaveActive, mapLibreMap) {
            val map = mapLibreMap ?: return@LaunchedEffect
            if (isPowerSaveActive && lastExtruded) {
                lastExtruded = false
                map.getStyle { style ->
                    val b2d = style.getLayer("buildings-2d")
                    val b3d = style.getLayer("buildings-3d")
                    b2d?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
                    b3d?.setProperties(PropertyFactory.visibility(Property.NONE))
                }
            }
        }

        // 現在地追従（停止時・低速巡航時のデバウンス制御）
        LaunchedEffect(currentLocation, followLocation, speedMps, mapLibreMap) {
            val map = mapLibreMap ?: return@LaunchedEffect
            val loc = currentLocation ?: return@LaunchedEffect
            if (!followLocation) return@LaunchedEffect

            val now = android.os.SystemClock.elapsedRealtime()
            val dist = if (!lastCameraLat.isNaN()) {
                CameraMotionPolicy.distanceMeters(lastCameraLat, lastCameraLon, loc.latitude, loc.longitude)
            } else {
                Double.MAX_VALUE
            }
            val elapsed = now - lastCameraUpdateTime

            val decision = CameraMotionPolicy.evaluate(
                speedMps = speedMps,
                distanceMeters = dist,
                elapsedMillisSinceLastUpdate = elapsed,
            )

            if (decision.shouldUpdateCamera) {
                lastCameraLat = loc.latitude
                lastCameraLon = loc.longitude
                lastCameraUpdateTime = now
                map.easeCamera(
                    CameraUpdateFactory.newLatLng(LatLng(loc.latitude, loc.longitude)),
                    decision.animationDurationMs
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

