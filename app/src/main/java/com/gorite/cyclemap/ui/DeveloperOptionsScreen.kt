package com.gorite.cyclemap.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import com.gorite.cyclemap.routing.HsaMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

@Composable
fun DeveloperOptionsScreen(
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    isBenchmarkRunning: Boolean,
    benchmarkResult: String?,
    onRunRoutingBenchmark: (RoutingBenchmarkRequest) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val readings = rememberSensorReadings(context, hasLocationPermission)
    var selectedBenchmarkMode by remember { mutableStateOf(HsaMode.B) }
    var startLatitude by remember { mutableStateOf("34.1785") }
    var startLongitude by remember { mutableStateOf("131.4737") }
    var goalLatitude by remember { mutableStateOf("34.1700") }
    var goalLongitude by remember { mutableStateOf("132.2200") }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("開発者オプション", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("センサーの生データを確認できます", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = onClose, modifier = Modifier.height(48.dp)) {
                        Text("閉じる", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("ルーティング実機ベンチマーク", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "mmapグラフでA*と選択したHSA*を比較します。始点・終点を変更できます。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            ScrollableTabRow(selectedTabIndex = selectedBenchmarkMode.ordinal) {
                                HsaMode.entries.forEach { mode ->
                                    Tab(
                                        selected = selectedBenchmarkMode == mode,
                                        onClick = { selectedBenchmarkMode = mode },
                                        text = { Text("HSA*-${mode.name}") },
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = startLatitude,
                                    onValueChange = { startLatitude = it },
                                    label = { Text("始点 緯度") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = startLongitude,
                                    onValueChange = { startLongitude = it },
                                    label = { Text("始点 経度") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = goalLatitude,
                                    onValueChange = { goalLatitude = it },
                                    label = { Text("終点 緯度") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = goalLongitude,
                                    onValueChange = { goalLongitude = it },
                                    label = { Text("終点 経度") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Button(
                                onClick = {
                                    val request = runCatching {
                                        RoutingBenchmarkRequest(
                                            mode = selectedBenchmarkMode,
                                            startLatitude = startLatitude.toDouble(),
                                            startLongitude = startLongitude.toDouble(),
                                            goalLatitude = goalLatitude.toDouble(),
                                            goalLongitude = goalLongitude.toDouble(),
                                        )
                                    }.getOrNull() ?: return@Button
                                    onRunRoutingBenchmark(request)
                                },
                                enabled = !isBenchmarkRunning,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (isBenchmarkRunning) "測定中…" else "ベンチマーク実行")
                            }
                            benchmarkResult?.let {
                                Text(
                                    it,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    SensorCard("加速度計", "TYPE_ACCELEROMETER", readings.accelerometer)
                    SensorCard("ジャイロスコープ", "TYPE_GYROSCOPE", readings.gyroscope)
                    SensorCard("地磁気センサー", "TYPE_MAGNETIC_FIELD", readings.magnetometer)
                    SensorCard("近接センサー", "TYPE_PROXIMITY", readings.proximity)
                    SensorCard("光（照度）センサー", "TYPE_LIGHT", readings.light)
                    SensorCard("気圧計", "TYPE_PRESSURE", readings.pressure)
                    GpsCard(
                        reading = readings.gps,
                        hasPermission = hasLocationPermission,
                        onRequestPermission = onRequestLocationPermission,
                    )
                    SensorCard("重力", "TYPE_GRAVITY", readings.gravity)
                    SensorCard("線形加速度", "TYPE_LINEAR_ACCELERATION", readings.linearAcceleration)
                    SensorCard("回転ベクトル", "TYPE_ROTATION_VECTOR", readings.rotationVector)
                }
            }
        }
    }
}

@Composable
private fun SensorCard(title: String, typeName: String, reading: SensorReading) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(typeName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!reading.available) {
                Text("この端末では未搭載です", color = MaterialTheme.colorScheme.error)
            } else {
                Text(reading.summary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                reading.details.forEach { line ->
                    Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun GpsCard(
    reading: GpsReading,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("GPS / GNSS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("LocationManager GPS_PROVIDER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!hasPermission) {
                Text("位置情報の許可が必要です")
                Button(onClick = onRequestPermission) { Text("許可する") }
            } else {
                Text(reading.summary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                reading.details.forEach { line ->
                    Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private data class SensorReading(
    val available: Boolean,
    val summary: String = "",
    val details: List<String> = emptyList(),
)

private data class GpsReading(
    val summary: String,
    val details: List<String> = emptyList(),
)

data class RoutingBenchmarkRequest(
    val mode: HsaMode,
    val startLatitude: Double,
    val startLongitude: Double,
    val goalLatitude: Double,
    val goalLongitude: Double,
)

private class SensorReadingsState {
    var accelerometer by mutableStateOf(SensorReading(false))
    var gyroscope by mutableStateOf(SensorReading(false))
    var magnetometer by mutableStateOf(SensorReading(false))
    var proximity by mutableStateOf(SensorReading(false))
    var light by mutableStateOf(SensorReading(false))
    var pressure by mutableStateOf(SensorReading(false))
    var gravity by mutableStateOf(SensorReading(false))
    var linearAcceleration by mutableStateOf(SensorReading(false))
    var rotationVector by mutableStateOf(SensorReading(false))
    var gps by mutableStateOf(GpsReading("取得待機中…"))
}

@Composable
private fun rememberSensorReadings(context: Context, hasLocationPermission: Boolean): SensorReadingsState {
    val readings = remember { SensorReadingsState() }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
            ?: return@DisposableEffect onDispose { }
        val sensors = mapOf(
            Sensor.TYPE_ACCELEROMETER to { r: SensorReading -> readings.accelerometer = r },
            Sensor.TYPE_GYROSCOPE to { r: SensorReading -> readings.gyroscope = r },
            Sensor.TYPE_MAGNETIC_FIELD to { r: SensorReading -> readings.magnetometer = r },
            Sensor.TYPE_PROXIMITY to { r: SensorReading -> readings.proximity = r },
            Sensor.TYPE_LIGHT to { r: SensorReading -> readings.light = r },
            Sensor.TYPE_PRESSURE to { r: SensorReading -> readings.pressure = r },
            Sensor.TYPE_GRAVITY to { r: SensorReading -> readings.gravity = r },
            Sensor.TYPE_LINEAR_ACCELERATION to { r: SensorReading -> readings.linearAcceleration = r },
            Sensor.TYPE_ROTATION_VECTOR to { r: SensorReading -> readings.rotationVector = r },
        )
        sensors.forEach { (type, _) ->
            if (sensorManager.getDefaultSensor(type) == null) {
                applySensor(readings, type, SensorReading(false))
            }
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                applySensor(readings, event.sensor.type, formatSensorEvent(event))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.keys.forEach { type ->
            sensorManager.getDefaultSensor(type)?.let { sensor ->
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    DisposableEffect(context, hasLocationPermission) {
        if (!hasLocationPermission) {
            readings.gps = GpsReading("位置情報の許可がありません")
            return@DisposableEffect onDispose { }
        }
        val locationManager = context.getSystemService(LocationManager::class.java)
            ?: return@DisposableEffect onDispose { readings.gps = GpsReading("位置情報サービスが利用できません") }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            readings.gps = GpsReading("端末のGPSがオフです", listOf("設定から位置情報をオンにしてください"))
        }
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                readings.gps = formatGps(location, readings.gps)
            }

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) {
                    readings.gps = GpsReading("端末のGPSがオフです")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        var satelliteCount = 0
        var satellitesUsed = 0
        val gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                satelliteCount = status.satelliteCount
                satellitesUsed = (0 until status.satelliteCount).count { status.usedInFix(it) }
                val current = readings.gps
                readings.gps = current.copy(
                    details = current.details.filterNot { it.startsWith("衛星") } +
                        "衛星: 使用 $satellitesUsed / 捕捉 $satelliteCount",
                )
            }
        }
        startGpsUpdates(context, locationManager, locationListener, gnssCallback)
        onDispose {
            locationManager.removeUpdates(locationListener)
            locationManager.unregisterGnssStatusCallback(gnssCallback)
        }
    }

    return readings
}

@SuppressLint("MissingPermission")
private fun startGpsUpdates(
    context: Context,
    locationManager: LocationManager,
    locationListener: LocationListener,
    gnssCallback: GnssStatus.Callback,
) {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!granted) return
    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, locationListener)
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { locationListener.onLocationChanged(it) }
    }
    if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2_000L, 0f, locationListener)
    }
    locationManager.registerGnssStatusCallback(context.mainExecutor, gnssCallback)
}

private fun applySensor(readings: SensorReadingsState, type: Int, reading: SensorReading) {
    when (type) {
        Sensor.TYPE_ACCELEROMETER -> readings.accelerometer = reading
        Sensor.TYPE_GYROSCOPE -> readings.gyroscope = reading
        Sensor.TYPE_MAGNETIC_FIELD -> readings.magnetometer = reading
        Sensor.TYPE_PROXIMITY -> readings.proximity = reading
        Sensor.TYPE_LIGHT -> readings.light = reading
        Sensor.TYPE_PRESSURE -> readings.pressure = reading
        Sensor.TYPE_GRAVITY -> readings.gravity = reading
        Sensor.TYPE_LINEAR_ACCELERATION -> readings.linearAcceleration = reading
        Sensor.TYPE_ROTATION_VECTOR -> readings.rotationVector = reading
    }
}

private fun formatSensorEvent(event: SensorEvent): SensorReading {
    val v = event.values
    return when (event.sensor.type) {
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION -> {
            val mag = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())
            SensorReading(
                available = true,
                summary = "合成: ${fmt(mag)} m/s²",
                details = listOf("X: ${fmt(v[0])}  Y: ${fmt(v[1])}  Z: ${fmt(v[2])}"),
            )
        }
        Sensor.TYPE_GYROSCOPE -> SensorReading(
            available = true,
            summary = "角速度 rad/s",
            details = listOf("X: ${fmt(v[0])}  Y: ${fmt(v[1])}  Z: ${fmt(v[2])}"),
        )
        Sensor.TYPE_MAGNETIC_FIELD -> {
            val mag = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())
            SensorReading(
                available = true,
                summary = "磁場: ${fmt(mag)} µT",
                details = listOf("X: ${fmt(v[0])}  Y: ${fmt(v[1])}  Z: ${fmt(v[2])}"),
            )
        }
        Sensor.TYPE_PROXIMITY -> {
            val near = v[0] < event.sensor.maximumRange
            SensorReading(
                available = true,
                summary = if (near) "NEAR ${fmt(v[0])} cm" else "FAR ${fmt(v[0])} cm",
                details = listOf("最大距離: ${fmt(event.sensor.maximumRange)} cm"),
            )
        }
        Sensor.TYPE_LIGHT -> SensorReading(
            available = true,
            summary = "${fmt(v[0])} lx",
        )
        Sensor.TYPE_PRESSURE -> {
            val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, v[0])
            SensorReading(
                available = true,
                summary = "${fmt(v[0])} hPa",
                details = listOf("海面気圧換算高度: ${fmt(altitude)} m"),
            )
        }
        Sensor.TYPE_ROTATION_VECTOR -> SensorReading(
            available = true,
            summary = "x,y,z,w",
            details = listOf(v.take(4).joinToString("  ") { fmt(it) }),
        )
        else -> SensorReading(true, v.joinToString("  ") { fmt(it) })
    }
}

private val gpsTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.JAPAN)

private fun formatGps(location: Location, previous: GpsReading): GpsReading {
    val satellites = previous.details.filter { it.startsWith("衛星") }
    return GpsReading(
        summary = "${fmt(location.latitude, 6)}, ${fmt(location.longitude, 6)}",
        details = listOfNotNull(
            "provider: ${location.provider ?: "-"}",
            "高度: ${if (location.hasAltitude()) "${fmt(location.altitude)} m" else "-"}",
            "精度: ${if (location.hasAccuracy()) "${fmt(location.accuracy)} m" else "-"}",
            "速度: ${if (location.hasSpeed()) "${fmt(location.speed * 3.6)} km/h" else "-"}",
            "方位: ${if (location.hasBearing()) "${fmt(location.bearing)}°" else "-"}",
            "時刻: ${gpsTimeFormat.format(Date(location.time))}",
        ) + satellites,
    )
}

private fun fmt(value: Float, digits: Int = 3): String = "%.${digits}f".format(Locale.US, value)
private fun fmt(value: Double, digits: Int = 3): String = "%.${digits}f".format(Locale.US, value)
