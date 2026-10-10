package com.gorite.cyclemap.routing

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LazyMappedRoutingTest {
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
}
