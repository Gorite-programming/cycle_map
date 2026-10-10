package com.gorite.cyclemap

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.osmdroid.util.GeoPoint
import java.io.File

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
    )
    for (candidate in candidates) {
        if (candidate.exists() && candidate.length() > 0) {
            return candidate.absolutePath
        }
    }
    return null
}

/**
 * CycleMap-Test 用の MapLibre ベクター地図コンポーネント。
 * 本家 Liberty スタイルの完全オフライン描画、2D/3D シームレス切替、ルート描画に対応。
 */
@Composable
fun VectorMapView(
    modifier: Modifier = Modifier,
    routePoints: List<GeoPoint> = emptyList(),
    currentLocation: GeoPoint? = null,
    followLocation: Boolean = true,
    isNavigationActive: Boolean = false,
    onUserPan: () -> Unit = {},
    onMapReady: (MapLibreMap) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentTilt by remember { mutableDoubleStateOf(0.0) }
    var lastExtruded by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
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

                        // 初期カメラ位置（現在地があれば現在地、なければ広島城周辺）
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

                        // オフライン Liberty スタイルを適用
                        val pmtilesPath = resolveOfflinePmtilesPath(context)
                        val tileUrl = if (pmtilesPath != null) {
                            "pmtiles://file://$pmtilesPath"
                        } else {
                            "https://build.protomaps.com/20261008.pmtiles"
                        }

                        val baseJson = context.assets.open("styles/protomaps_light.json").bufferedReader().use { it.readText() }
                        val finalStyleJson = baseJson.replace("__LOCAL_PMTILES__", tileUrl)

                        map.setStyle(Style.Builder().fromJson(finalStyleJson)) { style ->
                            // ルート表示用 GeoJson レイヤーを登録
                            setupRouteLayers(style)
                        }

                        // カメラ変更リスナー: 傾斜角に応じて 2D/3D を自動切り替え
                        map.addOnCameraMoveListener {
                            val pos = map.cameraPosition
                            currentTilt = pos.tilt

                            val shouldExtrude = pos.tilt > 15.0
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
                                            )
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

        // ルート更新の反映
        LaunchedEffect(routePoints, mapLibreMap) {
            val map = mapLibreMap ?: return@LaunchedEffect
            map.getStyle { style ->
                updateRouteLine(style, routePoints)
            }
        }

        // 現在地更新の反映
        LaunchedEffect(currentLocation, mapLibreMap) {
            val map = mapLibreMap ?: return@LaunchedEffect
            map.getStyle { style ->
                updateLocationPoint(style, currentLocation)
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
 * ルート描画および現在地描画用の GeoJSON ソース・レイヤーを初期化
 */
private fun setupRouteLayers(style: Style) {
    if (style.getSource("cyclemap-route-source") == null) {
        val routeSource = GeoJsonSource("cyclemap-route-source")
        style.addSource(routeSource)

        // ルートのフチ取り（ケーシング）
        val casingLayer = LineLayer("cyclemap-route-casing", "cyclemap-route-source").apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#0D47A1")), // 濃い青
                PropertyFactory.lineWidth(9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        style.addLayer(casingLayer)

        // ルート本体（鮮やかなシアンブルー）
        val routeLayer = LineLayer("cyclemap-route-line", "cyclemap-route-source").apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#00B0FF")), // 鮮やかなシアン
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        style.addLayer(routeLayer)
    }

    // 現在地マーカー用レイヤー
    if (style.getSource("cyclemap-location-source") == null) {
        val locationSource = GeoJsonSource("cyclemap-location-source")
        style.addSource(locationSource)

        // 現在地外枠（白）
        val outerCircle = org.maplibre.android.style.layers.CircleLayer("cyclemap-location-outer", "cyclemap-location-source").apply {
            setProperties(
                PropertyFactory.circleColor(Color.WHITE),
                PropertyFactory.circleRadius(10f),
            )
        }
        style.addLayer(outerCircle)

        // 現在地本体（青）
        val innerCircle = org.maplibre.android.style.layers.CircleLayer("cyclemap-location-inner", "cyclemap-location-source").apply {
            setProperties(
                PropertyFactory.circleColor(Color.parseColor("#007AFF")),
                PropertyFactory.circleRadius(7f),
            )
        }
        style.addLayer(innerCircle)
    }
}

/**
 * 現在地座標を GeoJSON に更新
 */
private fun updateLocationPoint(style: Style, location: GeoPoint?) {
    val source = style.getSourceAs<GeoJsonSource>("cyclemap-location-source") ?: return
    if (location == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }
    val point = Point.fromLngLat(location.longitude, location.latitude)
    source.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(point)))
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
