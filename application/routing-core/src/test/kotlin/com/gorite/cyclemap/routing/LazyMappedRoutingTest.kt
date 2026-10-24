package com.gorite.cyclemap.routing

import java.io.File
import java.io.RandomAccessFile
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LazyMappedRoutingTest {
    private data class FixtureEdge(val target: Int, val distanceMeters: Double, val gradePercent: Double)

    private fun graphWith(lats: DoubleArray, lons: DoubleArray): LazyMappedRoadGraph {
        val n = lats.size
        val tmp = File.createTempFile("lazymapped-test", ".graph")
        tmp.deleteOnExit()
        val channel = RandomAccessFile(tmp, "r").channel
        val ctor = LazyMappedRoadGraph::class.java.getDeclaredConstructor(
            FileChannel::class.java,
            ByteBuffer::class.java,
            LongArray::class.java,
            DoubleArray::class.java,
            DoubleArray::class.java,
            LongArray::class.java,
            IntArray::class.java,
            IntArray::class.java,
            IntArray::class.java,
            Int::class.javaPrimitiveType,
        )
        ctor.isAccessible = true
        val graph = ctor.newInstance(
            channel,
            ByteBuffer.allocate(0),
            LongArray(n) { it.toLong() },
            lats,
            lons,
            LongArray(n),
            IntArray(n),
            IntArray(n),
            IntArray(0),
            0,
        ) as LazyMappedRoadGraph
        return graph
    }

    private fun graphWithAlternativeGrades(): LazyMappedRoadGraph {
        val edges = listOf(
            listOf(
                FixtureEdge(1, 115.0, 0.0),
                FixtureEdge(2, 105.0, 2.0),
                FixtureEdge(3, 90.0, 10.0),
            ),
            listOf(FixtureEdge(4, 115.0, 0.0)),
            listOf(FixtureEdge(4, 105.0, 2.0)),
            listOf(FixtureEdge(4, 90.0, 10.0)),
            emptyList(),
        )
        val bytes = ByteArrayOutputStream()
        val edgeStarts = LongArray(edges.size)
        val edgeFirstOrdinals = IntArray(edges.size)
        val edgeCounts = IntArray(edges.size)
        val edgeTargets = ArrayList<Int>()
        DataOutputStream(bytes).use { out ->
            edges.forEachIndexed { source, outgoing ->
                edgeStarts[source] = bytes.size().toLong()
                edgeFirstOrdinals[source] = edgeTargets.size
                edgeCounts[source] = outgoing.size
                outgoing.forEach { edge ->
                    out.writeLong(100L + source)
                    out.writeLong(100L + edge.target)
                    out.writeDouble(edge.distanceMeters)
                    val roadType = "residential".toByteArray(Charsets.UTF_8)
                    out.writeShort(roadType.size)
                    out.write(roadType)
                    out.writeByte(0) // not one-way metadata
                    out.writeByte(1) // grade is present
                    out.writeDouble(edge.gradePercent)
                    edgeTargets += edge.target
                }
            }
        }
        val tmp = File.createTempFile("lazymapped-preference", ".graph").apply { deleteOnExit() }
        val channel = RandomAccessFile(tmp, "r").channel
        val nodeIds = LongArray(edges.size) { 100L + it }
        val zeros = DoubleArray(edges.size) { 34.0 }
        val ctor = LazyMappedRoadGraph::class.java.getDeclaredConstructor(
            FileChannel::class.java,
            ByteBuffer::class.java,
            LongArray::class.java,
            DoubleArray::class.java,
            DoubleArray::class.java,
            LongArray::class.java,
            IntArray::class.java,
            IntArray::class.java,
            IntArray::class.java,
            Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        return ctor.newInstance(
            channel,
            ByteBuffer.wrap(bytes.toByteArray()),
            nodeIds,
            zeros,
            DoubleArray(edges.size) { 131.0 },
            edgeStarts,
            edgeFirstOrdinals,
            edgeCounts,
            edgeTargets.toIntArray(),
            edgeTargets.size,
        ) as LazyMappedRoadGraph
    }

    private fun bruteForce(lats: DoubleArray, lons: DoubleArray, lat: Double, lon: Double): Int {
        var best = 0
        var bestDistance = Double.POSITIVE_INFINITY
        for (i in lats.indices) {
            val d = haversineMeters(lat, lon, lats[i], lons[i])
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }

    @Test
    fun nearestNodeIndexMatchesBruteForce() {
        val random = Random(42)
        val n = 300
        val lats = DoubleArray(n) { 33.7 + random.nextDouble() * 1.1 }
        val lons = DoubleArray(n) { 130.7 + random.nextDouble() * 1.5 }
        graphWith(lats, lons).use { graph ->
            repeat(50) {
                val qLat = 33.7 + random.nextDouble() * 1.1
                val qLon = 130.7 + random.nextDouble() * 1.5
                assertEquals(bruteForce(lats, lons, qLat, qLon), graph.nearestNodeIndex(qLat, qLon))
            }
        }
    }

    @Test
    fun searchBoundsContainEndpoints() {
        val bounds = LazyMappedRoadGraph.searchBoundsFor(34.0, 131.0, 34.5, 131.5)
        assertTrue(bounds.contains(34.0, 131.0))
        assertTrue(bounds.contains(34.5, 131.5))
        assertTrue(bounds.contains(34.25, 131.25))
        assertTrue(bounds.maxLatitude - bounds.minLatitude > 0.5)
    }

    @Test
    fun sameStartAndGoalReturnsSingleNode() {
        graphWith(doubleArrayOf(34.0), doubleArrayOf(131.0)).use { graph ->
            val result = graph.route(0, 0)
            assertTrue(result.isReachable)
            assertEquals(listOf(0L), result.nodeIds)
        }
    }

    @Test
    fun maxExpandedNodesMustBePositive() {
        graphWith(doubleArrayOf(34.0, 34.1), doubleArrayOf(131.0, 131.1)).use { graph ->
            assertFailsWith<IllegalArgumentException> {
                graph.route(0, 1, maxExpandedNodes = 0)
            }
        }
    }

    @Test
    fun gradePenalty_asymmetricAndBounded() {
        val method = LazyMappedRoadGraph::class.java.getDeclaredMethod(
            "gradePenalty",
            Float::class.javaPrimitiveType,
            RoutePreference::class.java,
        )
        method.isAccessible = true

        graphWith(doubleArrayOf(34.0), doubleArrayOf(131.0)).use { graph ->
            fun penalty(grade: Float, pref: RoutePreference): Double =
                method.invoke(graph, grade, pref) as Double

            // NaN -> 1.0
            assertEquals(1.0, penalty(Float.NaN, RoutePreference.RECOMMENDED), 1e-6)

            // RECOMMENDED:
            val flat = penalty(0.0f, RoutePreference.RECOMMENDED)
            val gentleDown = penalty(-3.0f, RoutePreference.RECOMMENDED)
            val steepDown = penalty(-10.0f, RoutePreference.RECOMMENDED)
            val up = penalty(5.0f, RoutePreference.RECOMMENDED)

            assertEquals(1.0, flat, 1e-6)
            assertTrue(gentleDown < flat)
            assertTrue(gentleDown >= 0.95)
            assertTrue(steepDown > gentleDown)
            assertTrue(up > flat)

            // FLAT: 上り坂回避は上限5.0倍でクランプ
            val flatUp = penalty(15.0f, RoutePreference.FLAT)
            assertTrue(flatUp <= 5.0)
        }
    }

    @Test
    fun routePreference_selectsFlatRecommendedAndSteepAlternativesWhenAvailable() {
        graphWithAlternativeGrades().use { graph ->
            val flat = graph.route(0, 4, preference = RoutePreference.FLAT)
            val recommended = graph.route(0, 4, preference = RoutePreference.RECOMMENDED)
            val hill = graph.route(0, 4, preference = RoutePreference.HILL_CLIMB)

            assertEquals(listOf(100L, 101L, 104L), flat.nodeIds)
            assertEquals(listOf(100L, 102L, 104L), recommended.nodeIds)
            assertEquals(listOf(100L, 103L, 104L), hill.nodeIds)
        }
    }

    @Test
    fun nearestNodeIndexLargeGridMatchesBruteForce() {
        val random = Random(2026)
        val n = 5_000
        val lats = DoubleArray(n) { 33.8 + random.nextDouble() * 1.0 }
        val lons = DoubleArray(n) { 130.8 + random.nextDouble() * 1.4 }
        graphWith(lats, lons).use { graph ->
            repeat(150) {
                val qLat = 33.7 + random.nextDouble() * 1.2
                val qLon = 130.7 + random.nextDouble() * 1.6
                assertEquals(bruteForce(lats, lons, qLat, qLon), graph.nearestNodeIndex(qLat, qLon))
            }
        }
    }

    @Test
    fun costModeMultipliersPreserveAdmissibility() {
        val method = LazyMappedRoadGraph::class.java.getDeclaredMethod(
            "multiplier",
            Int::class.javaPrimitiveType,
            RouteCostMode::class.java,
        )
        method.isAccessible = true
        graphWith(doubleArrayOf(34.0), doubleArrayOf(131.0)).use { graph ->
            for (mode in RouteCostMode.entries) {
                for (typeCode in 0..10) {
                    val m = method.invoke(graph, typeCode, mode) as Double?
                    if (m != null) {
                        assertTrue(m >= 0.90, "Mode $mode type $typeCode multiplier $m must be >= 0.90")
                    }
                }
            }
        }
    }
}
