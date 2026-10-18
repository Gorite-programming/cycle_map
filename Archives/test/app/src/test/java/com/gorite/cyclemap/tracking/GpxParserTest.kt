package com.gorite.cyclemap.tracking

import org.junit.Assert.assertEquals
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class GpxParserTest {

    private data class Token(
        val eventType: Int,
        val name: String = "",
        val text: String = "",
        val attributes: Map<String, String> = emptyMap(),
    )

    private fun createFakeParser(tokens: List<Token>): XmlPullParser {
        var index = 0
        val handler = InvocationHandler { _, method, args ->
            val currentToken = tokens.getOrElse(index) { Token(XmlPullParser.END_DOCUMENT) }
            when (method.name) {
                "getEventType" -> currentToken.eventType
                "getName" -> currentToken.name
                "getText" -> currentToken.text
                "getAttributeValue" -> {
                    val attrName = args?.getOrNull(1) as? String
                    currentToken.attributes[attrName]
                }
                "next" -> {
                    if (index < tokens.size - 1) index++
                    tokens[index].eventType
                }
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            XmlPullParser::class.java.classLoader,
            arrayOf(XmlPullParser::class.java),
            handler,
        ) as XmlPullParser
    }

    @Test
    fun parseTimestamp_handlesVariousIso8601Formats() {
        // 2026-02-09T08:32:52Z -> epoch millis
        val expected = 1770625972000L

        // Standard UTC with Z
        val t1 = GpxParser.parseTimestamp("2026-02-09T08:32:52Z")
        assertEquals(expected, t1)

        // With milliseconds
        val t2 = GpxParser.parseTimestamp("2026-02-09T08:32:52.123Z")
        assertEquals(expected + 123L, t2)

        // Timezone offset +09:00 (17:32:52 JST is 08:32:52 UTC)
        val t3 = GpxParser.parseTimestamp("2026-02-09T17:32:52+09:00")
        assertEquals(expected, t3)

        // Timezone offset with milliseconds
        val t4 = GpxParser.parseTimestamp("2026-02-09T17:32:52.456+09:00")
        assertEquals(expected + 456L, t4)

        // Without timezone offset (assumes UTC)
        val t5 = GpxParser.parseTimestamp("2026-02-09T08:32:52")
        assertEquals(expected, t5)
    }

    @Test
    fun parse_skipsInvalidOrMissingCoordinates() {
        val tokens = listOf(
            Token(XmlPullParser.START_DOCUMENT),
            // Valid point 1
            Token(XmlPullParser.START_TAG, name = "trkpt", attributes = mapOf("lat" to "34.1", "lon" to "131.4")),
            Token(XmlPullParser.START_TAG, name = "time"),
            Token(XmlPullParser.TEXT, text = "2026-02-09T08:00:00Z"),
            Token(XmlPullParser.END_TAG, name = "time"),
            Token(XmlPullParser.END_TAG, name = "trkpt"),
            // Invalid lat
            Token(XmlPullParser.START_TAG, name = "trkpt", attributes = mapOf("lat" to "invalid", "lon" to "131.5")),
            Token(XmlPullParser.START_TAG, name = "time"),
            Token(XmlPullParser.TEXT, text = "2026-02-09T08:00:10Z"),
            Token(XmlPullParser.END_TAG, name = "time"),
            Token(XmlPullParser.END_TAG, name = "trkpt"),
            // Missing lat
            Token(XmlPullParser.START_TAG, name = "trkpt", attributes = mapOf("lon" to "131.6")),
            Token(XmlPullParser.START_TAG, name = "time"),
            Token(XmlPullParser.TEXT, text = "2026-02-09T08:00:20Z"),
            Token(XmlPullParser.END_TAG, name = "time"),
            Token(XmlPullParser.END_TAG, name = "trkpt"),
            // Valid point 2
            Token(XmlPullParser.START_TAG, name = "trkpt", attributes = mapOf("lat" to "34.2", "lon" to "131.5")),
            Token(XmlPullParser.START_TAG, name = "time"),
            Token(XmlPullParser.TEXT, text = "2026-02-09T08:00:30Z"),
            Token(XmlPullParser.END_TAG, name = "time"),
            Token(XmlPullParser.END_TAG, name = "trkpt"),
            Token(XmlPullParser.END_DOCUMENT),
        )

        val parser = createFakeParser(tokens)
        val summary = GpxParser.parse(parser, File("test.gpx"))

        assertEquals(2, summary.points.size)
        assertEquals(34.1, summary.points[0].latitude, 1e-4)
        assertEquals(34.2, summary.points[1].latitude, 1e-4)
    }

    @Test
    fun parse_maintainsPreviousTimestampWhenMissingOrUnparseable() {
        val t0 = 1770624000000L // 2026-02-09T08:00:00Z
        val t2 = t0 + 20_000L // 2026-02-09T08:00:20Z

        val tokens = listOf(
            Token(XmlPullParser.START_DOCUMENT),
            // Point 1 with valid time
            Token(XmlPullParser.START_TAG, name = "trkpt", attributes = mapOf("lat" to "34.1", "lon" to "131.4")),
            Token(XmlPullParser.START_TAG, name = "time"),
            Token(XmlPullParser.TEXT, text = "2026-02-09T08:00:00Z"),
            Token(XmlPullParser.END_TAG, name = "time"),
            Token(XmlPullParser.END_TAG, name = "trkpt"),
            // Point 2 with corrupted time
            Token(XmlPullParser.START_TAG, name = "trkpt", attributes = mapOf("lat" to "34.15", "lon" to "131.45")),
            Token(XmlPullParser.START_TAG, name = "time"),
            Token(XmlPullParser.TEXT, text = "corrupted-date"),
            Token(XmlPullParser.END_TAG, name = "time"),
            Token(XmlPullParser.END_TAG, name = "trkpt"),
            // Point 3 with valid time
            Token(XmlPullParser.START_TAG, name = "trkpt", attributes = mapOf("lat" to "34.2", "lon" to "131.5")),
            Token(XmlPullParser.START_TAG, name = "time"),
            Token(XmlPullParser.TEXT, text = "2026-02-09T08:00:20Z"),
            Token(XmlPullParser.END_TAG, name = "time"),
            Token(XmlPullParser.END_TAG, name = "trkpt"),
            Token(XmlPullParser.END_DOCUMENT),
        )

        val parser = createFakeParser(tokens)
        val summary = GpxParser.parse(parser, File("test.gpx"))

        assertEquals(3, summary.points.size)
        assertEquals(t0, summary.points[0].timestampMillis)
        // Point 2 maintains previous point's timestamp (t0) instead of System.currentTimeMillis()
        assertEquals(t0, summary.points[1].timestampMillis)
        assertEquals(t2, summary.points[2].timestampMillis)
        assertEquals(20L, summary.totalDurationSeconds)
    }
}
