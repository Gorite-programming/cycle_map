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
import com.gorite.cyclemap.R
import com.gorite.cyclemap.routing.HsaMode
import com.gorite.cyclemap.speech.ShikokuMetanAudioEngine
import com.gorite.cyclemap.speech.VoicePhraseCatalog
import com.gorite.cyclemap.speech.VoiceTestController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
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

    var lastSpokenText by remember { mutableStateOf<String?>(null) }
    val metanEngine = remember(context) { ShikokuMetanAudioEngine(context) }
    val voiceController = remember(metanEngine) { VoiceTestController(metanEngine) }

    DisposableEffect(context) {
        onDispose {
            metanEngine.shutdown()
        }
    }

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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "音声案内テスト",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val isEngineReady = true
                                Text(
                                    if (isEngineReady) "● 準備完了 (オフライン)" else "○ 初期化中…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isEngineReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            // 音声エンジン切替タブ
                            Text(
                                "VOICEVOX「四国めたん（ノーマル）」の事前生成音声 (MP3) です。全143フレーズを完全オフライン・遅延ゼロで再生します。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )

                            // クイック試聴ボタン (代表的なフレーズ)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            voiceController.playResId(R.raw.voice_straight, "直進です")
                                            lastSpokenText = "直進です"
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("直進", fontSize = 13.sp)
                                    }
                                    Button(
                                        onClick = {
                                            voiceController.playResId(R.raw.voice_turn_left, "左折です")
                                            lastSpokenText = "左折です"
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("左折", fontSize = 13.sp)
                                    }
                                    Button(
                                        onClick = {
                                            voiceController.playResId(R.raw.voice_turn_right, "右折です")
                                            lastSpokenText = "右折です"
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("右折", fontSize = 13.sp)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            voiceController.playResId(R.raw.voice_now_t_junction_left, "まもなく、T字路を左折です")
                                            lastSpokenText = "まもなく、T字路を左折です"
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("まもなくT字路", fontSize = 12.sp, maxLines = 1)
                                    }
                                    Button(
                                        onClick = {
                                            voiceController.playResId(R.raw.voice_300m_turn_right, "およそ300メートル先、右折です")
                                            lastSpokenText = "およそ300メートル先、右折です"
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("300m先右折", fontSize = 12.sp, maxLines = 1)
                                    }
                                    Button(
                                        onClick = {
                                            voiceController.playResId(R.raw.voice_arrived, "目的地に到着しました")
                                            lastSpokenText = "目的地に到着しました"
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("到着", fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    lastSpokenText?.let { "発声中: 「$it」" } ?: "待機中",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(
                                    onClick = {
                                        voiceController.stop()
                                        lastSpokenText = null
                                    },
                                    enabled = lastSpokenText != null,
                                ) {
                                    Text("停止")
                                }
                            }
                        }
                    }

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

data class RoutingBenchmarkRequest(
    val mode: HsaMode,
    val startLatitude: Double,
    val startLongitude: Double,
    val goalLatitude: Double,
    val goalLongitude: Double,
)


