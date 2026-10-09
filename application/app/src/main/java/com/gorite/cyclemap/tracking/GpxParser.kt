package com.gorite.cyclemap.tracking

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
    internal fun parseTimestamp(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        try {
            return Instant.parse(trimmed).toEpochMilli()
        } catch (_: Exception) {}

        try {
            return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
        } catch (_: Exception) {}

        try {
            return ZonedDateTime.parse(trimmed).toInstant().toEpochMilli()
        } catch (_: Exception) {}

        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_DATE_TIME)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        } catch (_: Exception) {}

        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy/MM/dd HH:mm:ss",
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(trimmed)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }

        return null
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
        return parse(parser, sourceFile)
    }

    fun parse(parser: XmlPullParser, sourceFile: File): RideHistorySummary {
        val rawPoints = ArrayList<GpxTrackPoint>()
        var eventType = parser.eventType
        var currentLat: Double? = null
        var currentLon: Double? = null
        var currentEle: Double? = null
        var currentTime: Long? = null
        var insideTrkpt = false
        var currentTag = ""
        var lastValidTime = 0L
        val textBuffer = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    textBuffer.setLength(0)
                    if (currentTag == "trkpt") {
                        insideTrkpt = true
                        currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        currentEle = null
                        currentTime = null
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideTrkpt) {
                        textBuffer.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (insideTrkpt) {
                        val text = textBuffer.toString().trim()
                        when (parser.name) {
                            "ele" -> {
                                if (text.isNotEmpty()) {
                                    currentEle = text.toDoubleOrNull()
                                }
                            }
                            "time" -> {
                                if (text.isNotEmpty()) {
                                    currentTime = parseTimestamp(text)
                                }
                            }
                            "trkpt" -> {
                                val lat = currentLat
                                val lon = currentLon
                                if (lat != null && lon != null && lat.isFinite() && lon.isFinite() &&
                                    lat in -90.0..90.0 && lon in -180.0..180.0
                                ) {
                                    val timeMs = currentTime ?: lastValidTime.takeIf { it > 0L }
                                        ?: sourceFile.lastModified().takeIf { it > 0L } ?: 0L
                                    if (timeMs > 0L) {
                                        lastValidTime = timeMs
                                    }
                                    rawPoints.add(
                                        GpxTrackPoint(
                                            latitude = lat,
                                            longitude = lon,
                                            elevationMeters = currentEle,
                                            timestampMillis = timeMs,
                                        ),
                                    )
                                }
                                insideTrkpt = false
                            }
                        }
                    }
                    currentTag = ""
                    textBuffer.setLength(0)
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

                if (prev.timestampMillis > 0L && curr.timestampMillis > 0L) {
                    val dtSeconds = (curr.timestampMillis - prev.timestampMillis) / 1000.0
                    if (dtSeconds > 0.5) {
                        spd = (d / dtSeconds) * 3.6
                        if (spd in 1.0..90.0 && spd > maxSpd) {
                            maxSpd = spd
                        }
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

        val validTimePoints = computedPoints.filter { it.timestampMillis > 0L }
        val start = validTimePoints.firstOrNull()?.timestampMillis ?: sourceFile.lastModified().takeIf { it > 0L } ?: 0L
        val end = validTimePoints.lastOrNull()?.timestampMillis ?: start
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
        val clampedA = a.coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(clampedA), sqrt(1.0 - clampedA))
        return r * c
    }
}
