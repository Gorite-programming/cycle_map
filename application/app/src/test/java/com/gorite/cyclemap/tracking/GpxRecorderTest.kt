package com.gorite.cyclemap.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GpxRecorderTest {
    @Test
    fun emptyRecordingCannotCreateGpxFile() {
        val directory = Files.createTempDirectory("cyclemap-gpx-empty").toFile()
        val output = File(directory, "empty.gpx")

        try {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                GpxRecorder().writeToFile(output)
            }
            assertFalse(output.exists())
            assertFalse(File(directory, "empty.gpx.tmp").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun writerSerializesTrackPoint() {
        val directory = Files.createTempDirectory("cyclemap-gpx-valid").toFile()
        val output = File(directory, "track.gpx")
        val recorder = GpxRecorder().apply {
            addPoint(GpxPoint(34.0, 131.0, 10.0, 1_700_000_000_000L))
        }

        try {
            recorder.writeToFile(output)
            val xml = output.readText()
            assertTrue(output.exists())
            assertTrue(xml.contains("<trkpt lat=\"34.0000000\" lon=\"131.0000000\">"))
            assertTrue(xml.contains("<ele>10.0</ele>"))
            assertTrue(xml.contains("</trkseg>"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
