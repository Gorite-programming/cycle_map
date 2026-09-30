package com.gorite.cyclemap

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.gorite.cyclemap.routing.IconResolver
import com.gorite.cyclemap.routing.InstructionType
import com.gorite.cyclemap.routing.LazyMappedRoadGraph
import com.gorite.cyclemap.routing.RouteInstruction
import com.gorite.cyclemap.routing.RoutePoint
import com.gorite.cyclemap.routing.RouteProgress
import com.gorite.cyclemap.routing.TurnClassifier
import com.gorite.cyclemap.routing.bearingBetween
import com.gorite.cyclemap.tracking.GpsSignalStatus
import com.gorite.cyclemap.tracking.LocationTrackingService
import com.gorite.cyclemap.tracking.locationServiceConnection
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// ナビ案内バナー用データ
// ---------------------------------------------------------------------------

/** 案内バナー用の矢印アイコン＋文言。距離帯ごとに表現を変え、単調な繰り返しを避ける。 */
internal data class NavInstruction(val arrowIcon: Int, val text: String)

// ---------------------------------------------------------------------------
// ルート指示生成
// ---------------------------------------------------------------------------

/**
 * ①基本経路指示の構築。graphLock.read 保持下・バックグラウンドスレッドで呼ぶこと。
 * 判定理由は Logcat (CycleMapInstruction) に1ルート1回だけ出す。
 */
internal fun buildRouteInstructions(
    graph: LazyMappedRoadGraph?,
    nodeIds: List<Long>,
    coordinates: List<Pair<Double, Double>>,
): List<RouteInstruction> {
    if (graph == null || nodeIds.size < 3 || coordinates.size != nodeIds.size) return emptyList()
    val points = coordinates.map { (lat, lon) -> RoutePoint(lat, lon) }
    // グラフ内インデックスは nodeIndexOf で引く (ルート内位置とは別物。混同注意)。
    fun graphIndexOf(nodeId: Long): Int? = try {
        graph.nodeIndexOf(nodeId)
    } catch (e: IllegalArgumentException) {
        null
    }
    fun branchesOf(nodeId: Long): List<TurnClassifier.MappedBranch> {
        val index = graphIndexOf(nodeId) ?: return emptyList()
        val from = graph.nodeAt(index)
        return graph.outgoingBranches(index).mapNotNull { branch ->
            val to = graph.nodeAt(branch.targetIndex)
            val bearing = bearingBetween(from.latitude, from.longitude, to.latitude, to.longitude)
                ?: return@mapNotNull null
            TurnClassifier.MappedBranch(bearing, branch.roadTypeName, branch.distanceMeters)
        }
    }
    // ルート上の edge の roadType (ランプ・側道・合流判定用)。
    val edgeTypes = (0 until nodeIds.lastIndex).map { i ->
        val fromIndex = graphIndexOf(nodeIds[i])
        val toIndex = graphIndexOf(nodeIds[i + 1])
        if (fromIndex == null || toIndex == null) {
            "unknown"
        } else {
            graph.outgoingBranches(fromIndex).firstOrNull { it.targetIndex == toIndex }?.roadTypeName
                ?: "unknown"
        }
    }
    val instructions = TurnClassifier.classifyMappedRoute(
        nodeIds = nodeIds,
        coordinates = points,
        branchesOf = ::branchesOf,
        edgeTypes = edgeTypes,
    )
    for (instruction in instructions) {
        Log.d(
            "CycleMapInstruction",
            instruction.debugLine(
                currentEdge = nodeIds.getOrNull(instruction.routeIndex - 1)?.toString() ?: "-",
                nextEdge = instruction.nodeId.toString(),
                junctionType = "deg${instruction.junctionBranches}",
                icon = IconResolver.materialIconName(instruction.type),
            ),
        )
    }
    return instructions
}

// ---------------------------------------------------------------------------
// ナビ案内バナー文言生成
// ---------------------------------------------------------------------------

private const val ARRIVAL_RADIUS_METERS_NAV = 30.0

internal fun navigationInstruction(
    progress: RouteProgress?,
    remainingMeters: Double?,
    richInstructions: List<RouteInstruction> = emptyList(),
    arrivalRadiusMeters: Double = ARRIVAL_RADIUS_METERS_NAV,
): NavInstruction {
    val toNext = progress?.distanceToNextManeuverMeters
    val distance = toNext ?: remainingMeters ?: return NavInstruction(R.drawable.ic_lucide_arrow_up, "このまま直進です")
    if (remainingMeters != null && remainingMeters <= arrivalRadiusMeters) {
        return NavInstruction(R.drawable.ic_lucide_flag, "目的地に到着しました")
    }
    // ①基本経路指示があれば優先する。無ければ従来の Maneuver 表示にフォールバック。
    val traveled = progress?.distanceFromStartMeters ?: 0.0
    val richNext = richInstructions.firstOrNull { it.distanceFromStartMeters > traveled }
    if (richNext != null) {
        val richDistance = (richNext.distanceFromStartMeters - traveled).coerceAtLeast(0.0)
        // BUG-39 fix: STRAIGHTの場合、直後にある非STRAIGHT指示を先読みし事前告知する。
        // 30m先STRAIGHT＋60m先右折のようなケースで右折の告知が隠れるのを防ぐ。
        if (richNext.type == InstructionType.STRAIGHT) {
            val upcoming = richInstructions.firstOrNull {
                it.distanceFromStartMeters > traveled && it.type != InstructionType.STRAIGHT
            }
            if (upcoming != null) {
                val upcomingDist = (upcoming.distanceFromStartMeters - traveled).coerceAtLeast(0.0)
                if (upcomingDist < 200.0) {
                    val label = IconResolver.getLabel(upcoming.type)
                    val text = when {
                        upcomingDist >= 100.0 -> "${upcomingDist.roundToInt()}m先${label}です"
                        upcomingDist >= 30.0 -> "まもなく${label}です"
                        else -> "${label}です"
                    }
                    return NavInstruction(IconResolver.resolveIconResId(upcoming.type, upcoming.turnAngleDegrees), text)
                }
            }
            val text = when (((richDistance / 250).toInt()) % 3) {
                0 -> "このまま直進です"
                1 -> "道なりに直進してください"
                else -> "この先しばらく直進です"
            }
            return NavInstruction(IconResolver.resolveIconResId(InstructionType.STRAIGHT), text)
        }
        if (richDistance >= 500.0) {
            val text = when (((richDistance / 250).toInt()) % 3) {
                0 -> "このまま直進です"
                1 -> "道なりに直進してください"
                else -> "この先しばらく直進です"
            }
            return NavInstruction(IconResolver.resolveIconResId(InstructionType.STRAIGHT), text)
        }
        val label = IconResolver.getLabel(richNext.type)
        val text = when {
            richDistance >= 100.0 -> "${richDistance.roundToInt()}m先${label}です"
            richDistance >= 30.0 -> "まもなく${label}です"
            else -> "${label}です"
        }
        return NavInstruction(IconResolver.resolveIconResId(richNext.type, richNext.turnAngleDegrees), text)
    }
    val type = progress?.nextManeuver?.type
    if (type == null || distance >= 500.0) {
        val text = when (((distance / 250).toInt()) % 3) {
            0 -> "このまま直進です"
            1 -> "道なりに直進してください"
            else -> "この先しばらく直進です"
        }
        return NavInstruction(R.drawable.ic_lucide_arrow_up, text)
    }
    return when (type) {
        com.gorite.cyclemap.routing.ManeuverType.RIGHT -> NavInstruction(
            R.drawable.ic_lucide_arrow_right,
            when {
                distance >= 300.0 -> "${distance.roundToInt()}m先を右折です"
                distance >= 100.0 ->
                    if ((distance.toInt() / 50) % 2 == 0) "まもなく右折です" else "${distance.roundToInt()}m先、右方向です"
                else ->
                    if ((distance.toInt() / 30) % 2 == 0) "まもなく右へお進みください" else "右方向です"
            },
        )
        com.gorite.cyclemap.routing.ManeuverType.LEFT -> NavInstruction(
            R.drawable.ic_lucide_arrow_left,
            when {
                distance >= 300.0 -> "${distance.roundToInt()}m先を左折です"
                distance >= 100.0 ->
                    if ((distance.toInt() / 50) % 2 == 0) "まもなく左折です" else "${distance.roundToInt()}m先、左方向です"
                else ->
                    if ((distance.toInt() / 30) % 2 == 0) "まもなく左へお進みください" else "左方向です"
            },
        )
        com.gorite.cyclemap.routing.ManeuverType.U_TURN -> NavInstruction(
            R.drawable.ic_lucide_undo_2,
            when {
                distance >= 100.0 -> "${distance.roundToInt()}m先でUターンです"
                distance >= 30.0 -> "まもなくUターンです"
                else -> "ここでUターンしてください"
            },
        )
    }
}

// ---------------------------------------------------------------------------
// サービス位置情報更新 Composable
// ---------------------------------------------------------------------------

@Composable
internal fun ServiceLocationUpdates(
    context: Context,
    onLocationChanged: (location: Location, recording: Boolean, recordedPoints: Int) -> Unit,
    onSpeedChanged: (Double) -> Unit,
    onGpsStatusChanged: (GpsSignalStatus) -> Unit = {},
) {
    val onLocation by rememberUpdatedState(onLocationChanged)
    val onSpeed by rememberUpdatedState(onSpeedChanged)
    val onStatus by rememberUpdatedState(onGpsStatusChanged)
    DisposableEffect(context) {
        LocationTrackingService.startTracking(context)
        var serviceRef: LocationTrackingService? = null
        val locationListener: (Location) -> Unit = { newLocation ->
            onSpeed(newLocation.speed.toDouble())
            val service = serviceRef
            onLocation(newLocation, service?.recording == true, service?.recordedPoints ?: 0)
        }
        val statusListener: (GpsSignalStatus) -> Unit = { status ->
            onStatus(status)
        }
        val connection = locationServiceConnection { service ->
            serviceRef = service
            service.addListener(locationListener)
            service.addStatusListener(statusListener)
        }
        LocationTrackingService.bind(context, connection)
        onDispose {
            serviceRef?.removeListener(locationListener)
            serviceRef?.removeStatusListener(statusListener)
            runCatching { context.unbindService(connection) }
        }
    }
}

// ---------------------------------------------------------------------------
// コンパス更新 Composable
// ---------------------------------------------------------------------------

@Composable
internal fun CompassUpdates(context: Context, onHeadingChanged: (Float) -> Unit) {
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        var smoothedHeading = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rotationMatrix = FloatArray(9)
                val remappedMatrix = FloatArray(9)
                val orientation = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                val wm = context.getSystemService(android.view.WindowManager::class.java)
                @Suppress("DEPRECATION")
                val displayRotation = wm?.defaultDisplay?.rotation ?: android.view.Surface.ROTATION_0
                val (axisX, axisY) = when (displayRotation) {
                    android.view.Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    android.view.Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    android.view.Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
                SensorManager.getOrientation(remappedMatrix, orientation)
                val rawHeading = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f)
                val delta = ((rawHeading - smoothedHeading + 540f) % 360f) - 180f
                smoothedHeading = (smoothedHeading + delta * 0.25f + 360f) % 360f
                onHeadingChanged(smoothedHeading)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (rotationSensor != null) {
            sensorManager?.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager?.unregisterListener(listener) }
    }
}
