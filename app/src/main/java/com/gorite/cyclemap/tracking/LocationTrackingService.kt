package com.gorite.cyclemap.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.gorite.cyclemap.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Owns fused location for the whole app. GPX recording is an optional consumer of
 * the same stream and does not start or stop the location provider.
 */
class LocationTrackingService : Service() {
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val listeners = CopyOnWriteArrayList<(Location) -> Unit>()
    private val statusListeners = CopyOnWriteArrayList<(GpsSignalStatus) -> Unit>()
    private val binder = LocalBinder()
    private var recorder: GpxRecorder? = null
    private var tracking = false
    private var previousLocation: Location? = null
    private var smoothedSpeed = 0.0

    @Volatile var lastStatus: GpsSignalStatus = GpsSignalStatus.HEALTHY
        private set

    @Volatile var recording: Boolean = false
        private set

    @Volatile var lastLocation: Location? = null
        private set

    val recordedPoints: Int get() = recorder?.pointCount ?: 0

    inner class LocalBinder : Binder() {
        val service: LocationTrackingService get() = this@LocationTrackingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_TRACKING -> stopTracking()
            ACTION_START_RECORDING -> {
                ensureTracking()
                startRecording()
            }
            ACTION_STOP_RECORDING -> stopRecording()
            else -> ensureTracking()
        }
        return START_STICKY
    }

    fun addListener(listener: (Location) -> Unit) {
        listeners += listener
        lastLocation?.let(listener)
    }

    fun removeListener(listener: (Location) -> Unit) {
        listeners -= listener
    }

    fun addStatusListener(listener: (GpsSignalStatus) -> Unit) {
        statusListeners += listener
        listener(lastStatus)
    }

    fun removeStatusListener(listener: (GpsSignalStatus) -> Unit) {
        statusListeners -= listener
    }

    private fun notifyStatus(status: GpsSignalStatus) {
        lastStatus = status
        statusListeners.forEach { it(status) }
    }

    private fun ensureTracking() {
        if (tracking) {
            startForeground(NOTIFICATION_ID, trackingNotification())
            return
        }
        startTracking()
    }

    private fun startTracking() {
        val hasFine = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            android.util.Log.e("CycleMapTracking", "Location permission not granted, aborting startTracking")
            stopSelf()
            return
        }

        tracking = true
        startForeground(NOTIFICATION_ID, trackingNotification())
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(250L)
            .setMaxUpdateDelayMillis(1_000L)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            fusedClient.lastLocation.addOnSuccessListener { last ->
                if (last != null) considerLocation(last)
            }
        } catch (se: SecurityException) {
            android.util.Log.e("CycleMapTracking", "SecurityException requesting location updates", se)
            stopSelf()
        }
    }

    private fun startRecording() {
        if (recording) return
        recorder = GpxRecorder()
        recording = true
        startForeground(NOTIFICATION_ID, trackingNotification())
        sendBroadcast(Intent(ACTION_RECORDING_STARTED).setPackage(packageName))
    }

    private fun stopRecording() {
        if (!recording) return
        val snapshot = recorder
        recorder = null
        recording = false
        val outputDir = File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "CycleMap/gpx")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date())
        val points = snapshot?.pointCount ?: 0

        Thread {
            try {
                val output = snapshot?.writeToFile(File(outputDir, "cyclemap_$timestamp.gpx"))
                if (output != null) {
                    getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putString(KEY_LAST_FILE, output.absolutePath).apply()
                    sendBroadcast(
                        Intent(ACTION_RECORDING_SAVED).setPackage(packageName)
                            .putExtra(EXTRA_PATH, output.absolutePath)
                            .putExtra(EXTRA_POINTS, points),
                    )
                }
            } catch (t: Throwable) {
                android.util.Log.e("CycleMapTracking", "Failed to save GPX file", t)
            }
        }.start()

        startForeground(NOTIFICATION_ID, trackingNotification())
    }

    private fun stopTracking() {
        stopRecordingIfNeeded()
        fusedClient.removeLocationUpdates(callback)
        tracking = false
        previousLocation = null
        smoothedSpeed = 0.0
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopRecordingIfNeeded() {
        if (recording) stopRecording()
    }

    override fun onDestroy() {
        stopRecordingIfNeeded()
        fusedClient.removeLocationUpdates(callback)
        tracking = false
        previousLocation = null
        smoothedSpeed = 0.0
        super.onDestroy()
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::considerLocation)
        }
    }

    private fun considerLocation(newLocation: Location) {
        val isTooInaccurate = newLocation.hasAccuracy() && newLocation.accuracy > 100f
        if (isTooInaccurate) {
            notifyStatus(GpsSignalStatus.WEAK)
            return
        }

        val oldLocation = previousLocation
        var speed = 0.0
        if (oldLocation != null) {
            val elapsedSeconds = (newLocation.time - oldLocation.time) / 1_000.0
            val distanceMeters = oldLocation.distanceTo(newLocation).toDouble()
            val calculatedSpeed = if (elapsedSeconds > 0.0) distanceMeters / elapsedSeconds else Double.POSITIVE_INFINITY
            
            val isTooFast = calculatedSpeed > 25.0
            val isOutOfOrder = elapsedSeconds <= 0.0
            if (isTooFast || isOutOfOrder) return

            // 1. Calculate raw speed
            val rawSpeed = when {
                newLocation.hasSpeed() -> newLocation.speed.toDouble()
                elapsedSeconds > 0.0 && distanceMeters >= 1.0 -> distanceMeters / elapsedSeconds
                else -> 0.0
            }

            // 2. Stationary detection (Noise gate)
            // BUG-50 fix: Do not compare distanceMeters directly against accuracy,
            // because at 15 km/h (4.2 m/fix) typical GPS accuracy (5-15 m) will constantly zero out speed.
            val isStationary = if (newLocation.hasSpeed()) {
                rawSpeed < 0.5
            } else {
                distanceMeters < 1.0 || rawSpeed < 0.5
            }
            val finalRawSpeed = if (isStationary) 0.0 else rawSpeed

            // 3. Exponential Moving Average Smoothing
            smoothedSpeed = smoothedSpeed * 0.7 + finalRawSpeed * 0.3
            speed = smoothedSpeed
        } else {
            // First location
            val rawSpeed = if (newLocation.hasSpeed()) newLocation.speed.toDouble() else 0.0
            smoothedSpeed = if (rawSpeed < 0.5) 0.0 else rawSpeed
            speed = smoothedSpeed
        }

        // Override speed on the location object so all downstream consumers use the filtered/smoothed speed
        newLocation.speed = speed.toFloat()

        notifyStatus(GpsSignalStatus.HEALTHY)
        previousLocation = newLocation
        lastLocation = newLocation
        if (recording) recorder?.addPoint(newLocation)
        listeners.forEach { it(newLocation) }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "CycleMap location", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun trackingNotification(): Notification {
        val title = if (recording) "CycleMap GPX記録中" else "CycleMap 位置追跡中"
        val text = if (recording) "位置情報を記録しています" else "画面オフ中も現在地を更新します"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_current_location)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START_TRACKING = "com.gorite.cyclemap.LOCATION_START"
        const val ACTION_STOP_TRACKING = "com.gorite.cyclemap.LOCATION_STOP"
        const val ACTION_START_RECORDING = "com.gorite.cyclemap.GPX_START"
        const val ACTION_STOP_RECORDING = "com.gorite.cyclemap.GPX_STOP"
        const val ACTION_RECORDING_STARTED = "com.gorite.cyclemap.GPX_STARTED"
        const val ACTION_RECORDING_SAVED = "com.gorite.cyclemap.GPX_SAVED"
        const val EXTRA_PATH = "path"
        const val EXTRA_POINTS = "points"
        private const val CHANNEL_ID = "cyclemap_location"
        private const val NOTIFICATION_ID = 4201
        private const val PREFERENCES = "gpx_recording"
        private const val KEY_LAST_FILE = "last_file"

        fun startTracking(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LocationTrackingService::class.java).setAction(ACTION_START_TRACKING),
            )
        }

        fun stopTracking(context: Context) {
            context.startService(Intent(context, LocationTrackingService::class.java).setAction(ACTION_STOP_TRACKING))
        }

        fun startRecording(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LocationTrackingService::class.java).setAction(ACTION_START_RECORDING),
            )
        }

        fun stopRecording(context: Context) {
            context.startService(Intent(context, LocationTrackingService::class.java).setAction(ACTION_STOP_RECORDING))
        }

        fun bind(context: Context, connection: ServiceConnection) {
            context.bindService(Intent(context, LocationTrackingService::class.java), connection, Context.BIND_AUTO_CREATE)
        }
    }
}

fun locationServiceConnection(onConnected: (LocationTrackingService) -> Unit): ServiceConnection =
    object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            onConnected((binder as LocationTrackingService.LocalBinder).service)
        }

        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }
