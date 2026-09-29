package com.gorite.cyclemap.tracking

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GpxTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?,
    val timestampMillis: Long,
    val speedKmh: Double = 0.0,
    val distanceMetersFromStart: Double = 0.0,
)

data class RideHistorySummary(
    val file: File,
    val fileName: String,
    val startTime: Date,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Long,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val elevationGainMeters: Double,
    val points: List<GpxTrackPoint>,
)

object GpxParser {
    private val isoFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    fun parse(file: File): RideHistorySummary? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            file.inputStream().use { parse(it, file) }
        } catch (_: Exception) {
            null
        }
    }

    fun parse(inputStream: InputStream, sourceFile: File): RideHistorySummary {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        val rawPoints = ArrayList<GpxTrackPoint>()
        var eventType = parser.eventType
        var currentLat = 0.0
        var currentLon = 0.0
        var currentEle: Double? = null
        var currentTime = 0L
        var insideTrkpt = false
        var currentTag = ""

        val formatter = isoFormat.get()!!

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "trkpt") {
                        insideTrkpt = true
                        currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                        currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                        currentEle = null
                        currentTime = 0L
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideTrkpt) {
                        val text = parser.text.trim()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "ele" -> currentEle = text.toDoubleOrNull()
                                "time" -> {
                                    try {
                                        currentTime = formatter.parse(text)?.time ?: 0L
                                    } catch (_: Exception) {
                                        currentTime = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "trkpt" && insideTrkpt) {
                        rawPoints.add(
                            GpxTrackPoint(
                                latitude = currentLat,
                                longitude = currentLon,
                                elevationMeters = currentEle,
                                timestampMillis = if (currentTime > 0L) currentTime else System.currentTimeMillis(),
                            ),
                        )
                        insideTrkpt = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        // 統計計算 (距離、速度、獲得標高)
        var totalDist = 0.0
        var gain = 0.0
        var maxSpd = 0.0
        val computedPoints = ArrayList<GpxTrackPoint>(rawPoints.size)

        for (i in rawPoints.indices) {
            val curr = rawPoints[i]
            var spd = 0.0
            if (i > 0) {
                val prev = rawPoints[i - 1]
                val d = haversineMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
                totalDist += d

                // 標高差 (プラスのみ合算)
                if (prev.elevationMeters != null && curr.elevationMeters != null) {
                    val diff = curr.elevationMeters - prev.elevationMeters
                    if (diff > 0.0) gain += diff
                }

                val dtSeconds = (curr.timestampMillis - prev.timestampMillis) / 1000.0
                if (dtSeconds > 0.5) {
                    spd = (d / dtSeconds) * 3.6
                    if (spd in 1.0..90.0 && spd > maxSpd) {
                        maxSpd = spd
                    }
                }
            }
            computedPoints.add(
                curr.copy(
                    speedKmh = spd,
                    distanceMetersFromStart = totalDist,
                ),
            )
        }

        val start = computedPoints.firstOrNull()?.timestampMillis ?: sourceFile.lastModified()
        val end = computedPoints.lastOrNull()?.timestampMillis ?: sourceFile.lastModified()
        val durSec = ((end - start) / 1000L).coerceAtLeast(0L)
        val avgSpd = if (durSec > 0) (totalDist / durSec) * 3.6 else 0.0

        return RideHistorySummary(
            file = sourceFile,
            fileName = sourceFile.name,
            startTime = Date(start),
            totalDistanceMeters = totalDist,
            totalDurationSeconds = durSec,
            averageSpeedKmh = avgSpd,
            maxSpeedKmh = maxSpd,
            elevationGainMeters = gain,
            points = computedPoints,
        )
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
