package com.gorite.cyclemap.tracking

import android.location.Location
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Collects timestamped track points and serializes them as GPX 1.1.
 * Pure formatting logic lives in [GpxWriter] so it can be unit-tested on the JVM.
 */
class GpxRecorder {
    private val points = java.util.Collections.synchronizedList(mutableListOf<GpxPoint>())

    val pointCount: Int get() = points.size

    fun addPoint(location: Location) {
        points.add(
            GpxPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                elevationMeters = if (location.hasAltitude()) location.altitude else null,
                timestampMillis = location.time,
            ),
        )
    }

    fun writeToFile(file: File): File {
        file.parentFile?.mkdirs()
        val snapshot = synchronized(points) { points.toList() }
        val tmpFile = File(file.parentFile, "${file.name}.tmp")
        try {
            tmpFile.writer().use { it.write(GpxWriter.write(snapshot)) }
            if (!tmpFile.renameTo(file)) {
                tmpFile.copyTo(file, overwrite = true)
                tmpFile.delete()
            }
        } catch (t: Throwable) {
            tmpFile.delete()
            throw t
        }
        return file
    }
}

data class GpxPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?,
    val timestampMillis: Long,
)

object GpxWriter {
    private val gpxTimeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    fun write(points: List<GpxPoint>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append('\n')
        append("<gpx version=\"1.1\" creator=\"CycleMap\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
        append('\n')
        append("  <trk><name>CycleMap recording</name><trkseg>")
        append('\n')
        val formatter = checkNotNull(gpxTimeFormat.get())
        for (p in points) {
            append("    <trkpt lat=\"").append(formatCoord(p.latitude))
                .append("\" lon=\"").append(formatCoord(p.longitude)).append("\">")
            if (p.elevationMeters != null) {
                append("<ele>").append("%.1f".format(Locale.US, p.elevationMeters)).append("</ele>")
            }
            append("<time>").append(formatter.format(Date(p.timestampMillis))).append("</time>")
            append("</trkpt>")
            append('\n')
        }
        append("  </trkseg></trk>")
        append('\n')
        append("</gpx>")
        append('\n')
    }

    private fun formatCoord(value: Double): String = "%.7f".format(Locale.US, value)
}
