package com.gorite.vectortest

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import java.io.File

enum class MapStylePreset(val label: String, val styleUrl: String) {
    OFFLINE_LIBERTY(
        label = "★オフラインLiberty (23MB)",
        styleUrl = "",
    ),
    OFFLINE_GSI(
        label = "オフライン地理院 (45MB)",
        styleUrl = "",
    ),
    LIBERTY(
        label = "Liberty (オンライン)",
        styleUrl = "https://tiles.openfreemap.org/styles/liberty",
    ),
    DARK(
        label = "Dark (夜間)",
        styleUrl = "https://tiles.openfreemap.org/styles/dark",
    ),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MapLibre ネイティブエンジンの初期化
        MapLibre.getInstance(this)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VectorMapTestScreen()
                }
            }
        }
    }
}

/**
 * スタイルを MapLibreMap に適用する。
 * オフライン選択時はローカル PMTiles ファイルを動的にバインドしてロードする。
 */
fun applyMapStyle(context: Context, map: MapLibreMap, preset: MapStylePreset, onLoaded: () -> Unit = {}) {
    when (preset) {
        MapStylePreset.OFFLINE_LIBERTY -> {
            val osmFile = File(context.getExternalFilesDir("tiles"), "Hiroshima_osm.pmtiles")
            val baseJson = context.assets.open("styles/protomaps_light.json").bufferedReader().use { it.readText() }

            val localTilePath = if (osmFile.exists()) {
                "pmtiles://file://${osmFile.absolutePath}"
            } else {
                "https://build.protomaps.com/20261008.pmtiles"
            }

            val modifiedJson = baseJson.replace(
                "__LOCAL_PMTILES__",
                localTilePath,
            )

            map.setStyle(Style.Builder().fromJson(modifiedJson)) {
                onLoaded()
            }
        }
        MapStylePreset.OFFLINE_GSI -> {
            val pmtilesFile = File(context.getExternalFilesDir("tiles"), "Hiroshima.pmtiles")
            val baseJson = context.assets.open("styles/offline_hiroshima.json").bufferedReader().use { it.readText() }

            val localTilePath = if (pmtilesFile.exists()) {
                "pmtiles://file://${pmtilesFile.absolutePath}"
            } else {
                "pmtiles://https://cyberjapandata.gsi.go.jp/xyz/optimal_bvmap-v1/optimal_bvmap-v1.pmtiles"
            }

            val modifiedJson = baseJson.replace(
                "__LOCAL_PMTILES_URL__",
                localTilePath,
            )

            map.setStyle(Style.Builder().fromJson(modifiedJson)) {
                onLoaded()
            }
        }
        else -> {
            map.setStyle(preset.styleUrl) {
                onLoaded()
            }
        }
    }
}

@Composable
fun VectorMapTestScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val osmFile = remember(context) { File(context.getExternalFilesDir("tiles"), "Hiroshima_osm.pmtiles") }
    val isOsmAvailable = remember(osmFile) { osmFile.exists() && osmFile.length() > 0 }

    // オフライン Liberty が存在すれば最優先選択
    var selectedStyle by remember {
        mutableStateOf(if (isOsmAvailable) MapStylePreset.OFFLINE_LIBERTY else MapStylePreset.LIBERTY)
    }
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentZoom by remember { mutableDoubleStateOf(14.0) }
    var currentTilt by remember { mutableDoubleStateOf(0.0) }
    var currentBearing by remember { mutableDoubleStateOf(0.0) }
    var is3DMode by remember { mutableStateOf(false) }

    var lastExtruded by remember { mutableStateOf<Boolean?>(null) }

    val mapView = remember {
        MapView(context).apply {
            setMaximumFps(120)
            getMapAsync { map ->
                mapInstance = map
                // ズームイン時の低解像度タイル保持（空白・ちらつき防止）
                map.prefetchZoomDelta = 4
                map.uiSettings.isRotateGesturesEnabled = true
                map.uiSettings.isTiltGesturesEnabled = true

                applyMapStyle(context, map, selectedStyle) {
                    // 初期カメラ位置: 広島平和記念公園・広島城周辺
                    val initialPosition = CameraPosition.Builder()
                        .target(LatLng(34.3965, 132.4596))
                        .zoom(14.5)
                        .tilt(0.0)
                        .bearing(0.0)
                        .build()
                    map.cameraPosition = initialPosition
                }

                // カメラの動き（ズーム・回転・傾き）をリアルタイムで追跡
                map.addOnCameraMoveListener {
                    val pos = map.cameraPosition
                    currentZoom = pos.zoom
                    currentTilt = pos.tilt
                    currentBearing = pos.bearing

                    // 傾斜角 (Tilt) が 15° を超えたら 3D 押し出し、15° 以下なら 2D 平面図形にシームレス切替
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
            }
        }
    }

    // Android Lifecycle に応じた MapView の pause/resume
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    mapView.onDestroy()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // メインのベクター地図描画領域
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        // 画面上部：リアルタイム HUD & スタイル切替バー
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // HUD カード (現在のズーム・3D傾斜・方位角・オフライン状態)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Zoom: %.1f".format(currentZoom),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Tilt(3D): %.0f°".format(currentTilt),
                            color = if (currentTilt > 10.0) Color(0xFF00E676) else Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Angle: %.0f°".format(currentBearing),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (selectedStyle) {
                            MapStylePreset.OFFLINE_LIBERTY -> "● オフライン Liberty 稼働中 (OSM: ${osmFile.length() / 1024 / 1024}MB)"
                            MapStylePreset.OFFLINE_GSI -> "● オフライン地理院 稼働中"
                            else -> "○ オンラインストリーミング中 (${selectedStyle.label})"
                        },
                        color = when (selectedStyle) {
                            MapStylePreset.OFFLINE_LIBERTY -> Color(0xFF00E676)
                            MapStylePreset.OFFLINE_GSI -> Color(0xFF81D4FA)
                            else -> Color(0xFFFFB74D)
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // スタイル切替ボタングループ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MapStylePreset.entries.forEach { preset ->
                    val isSelected = preset == selectedStyle
                    FilledTonalButton(
                        onClick = {
                            selectedStyle = preset
                            val map = mapInstance ?: return@FilledTonalButton
                            applyMapStyle(context, map, preset)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isSelected) Color(0xFF1976D2) else Color.White.copy(alpha = 0.9f),
                            contentColor = if (isSelected) Color.White else Color.Black,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = preset.label.replace(Regex(" \\(.*\\)"), ""),
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // 画面右下：操作用クイックアクションボタン群
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 36.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // 3D/2D 切替ボタン
            Button(
                onClick = {
                    val map = mapInstance ?: return@Button
                    is3DMode = !is3DMode
                    val targetTilt = if (is3DMode) 60.0 else 0.0
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder(map.cameraPosition)
                                .tilt(targetTilt)
                                .build()
                        ),
                        800,
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (is3DMode) Color(0xFFE91E63) else Color(0xFF37474F)
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (is3DMode) "2D 平面" else "3D 鳥瞰 (60°)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // 北上リセットボタン (回転角を0°に戻す)
            if (kotlin.math.abs(currentBearing) > 5.0) {
                Button(
                    onClick = {
                        val map = mapInstance ?: return@Button
                        map.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder(map.cameraPosition)
                                    .bearing(0.0)
                                    .build()
                            ),
                            600,
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("北を上に (0°)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 主要スポットクイックジャンプ (広島エリア特化)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        // 広島平和記念公園・原爆ドーム
                        mapInstance?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(34.3929, 132.4526), 15.5),
                            1000
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("平和公園", color = Color.Black, fontSize = 11.sp)
                }
                Button(
                    onClick = {
                        // 広島駅周辺
                        mapInstance?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(34.3977, 132.4753), 15.0),
                            1000
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("広島駅", color = Color.Black, fontSize = 11.sp)
                }
                Button(
                    onClick = {
                        // 宮島・厳島神社
                        mapInstance?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(34.2959, 132.3197), 15.0),
                            1000
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("宮島", color = Color.Black, fontSize = 11.sp)
                }
            }
        }
    }
}
