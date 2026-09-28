package com.gorite.cyclemap.verify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import com.gorite.cyclemap.data.AddressDisplayController
import com.gorite.cyclemap.data.AddressResult
import com.gorite.cyclemap.data.OfflineReverseGeocoder
import com.gorite.cyclemap.data.SearchDbReverseGeocoder
import com.gorite.cyclemap.data.findPrefectureName
import com.gorite.cyclemap.routing.LazyMappedRoadGraph
import com.gorite.cyclemap.routing.RoutePoint
import com.gorite.cyclemap.routing.TurnClassifier
import com.gorite.cyclemap.routing.bearingBetween
import com.gorite.cyclemap.routing.extractManeuvers
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 実機オフライン検証ハーネス (検証コード・ログのみ。本番ロジック・UIは一切触らない)。
 *
 * adb shell am broadcast -a com.gorite.cyclemap.VERIFY --es suite <geocoder|gate|routes|roundabout|hairpin|all>
 * 結果は Logcat (CycleMapVerify) と CycleMap/verify/<suite>-<timestamp>.log の両方に出す。
 */
class VerificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_VERIFY) return
        val rawSuite = intent.getStringExtra(EXTRA_SUITE) ?: "all"
        val suite = rawSuite.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")
        val pending = goAsync()
        executor.execute {
            try {
                VerificationRunner(context.applicationContext).run(suite)
            } catch (t: Throwable) {
                Log.e(TAG, "VERIFY_FAIL $suite: ${t.message}", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_VERIFY = "com.gorite.cyclemap.VERIFY"
        const val EXTRA_SUITE = "suite"
        const val TAG = "CycleMapVerify"
        private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    }
}

class VerificationRunner(private val context: Context) {
    private val lines = ArrayList<String>()
    private var logFile: File? = null

    private fun out(line: String) {
        lines += line
        Log.i(VerificationReceiver.TAG, line)
    }

    fun run(suite: String) {
        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (extDir == null) {
            Log.e(VerificationReceiver.TAG, "VERIFY_FAIL: External storage not available")
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = File(File(extDir, "CycleMap"), "verify")
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(VerificationReceiver.TAG, "VERIFY_WARN: Failed to create directories: ${dir.absolutePath}")
        }
        val suites = if (suite == "all") {
            listOf("geocoder", "gate", "routes", "roundabout", "hairpin", "guidance")
        } else {
            listOf(suite)
        }
        for (s in suites) {
            lines.clear()
            out("VERIFY_BEGIN suite=$s stamp=$stamp model=${android.os.Build.MODEL}")
            out(deviceState())
            try {
                when (s) {
                    "geocoder" -> runGeocoder()
                    "gate" -> runGate()
                    "routes" -> runRoutes()
                    "roundabout" -> runRoundabout()
                    "hairpin" -> runHairpin()
                    "guidance" -> runGuidance()
                    else -> out("unknown suite: $s")
                }
            } catch (t: Throwable) {
                out("VERIFY_ERROR $s: ${t.message}")
            }
            out("VERIFY_DONE suite=$s lines=${lines.size}")
            logFile = File(dir, "$s-$stamp.log")
            try {
                logFile?.writeText(lines.joinToString("\n") + "\n")
                Log.i(VerificationReceiver.TAG, "VERIFY_SAVED ${logFile?.absolutePath}")
            } catch (e: Exception) {
                Log.e(VerificationReceiver.TAG, "VERIFY_ERROR writing log: ${e.message}", e)
            }
        }
    }

    private fun dataDir(): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "CycleMap")

    private fun deviceState(): String {
        val cr = context.contentResolver
        val airplane = android.provider.Settings.Global.getInt(cr, android.provider.Settings.Global.AIRPLANE_MODE_ON, -1)
        val wifiOn = try {
            (context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager).isWifiEnabled
        } catch (_: Throwable) {
            null
        }
        val providers = android.provider.Settings.Secure.getString(cr, "location_providers_allowed")
        return "DEVICE airplane=$airplane wifi=$wifiOn locationProviders=$providers"
    }

    // -- 逆ジオコーディング実測 --------------------------------------------------

    private data class GeoPoint(val label: String, val lat: Double, val lon: Double)

    private val geoPoints = listOf(
        GeoPoint("広島駅周辺", 34.3976, 132.4756),
        GeoPoint("国泰寺町周辺", 34.3853, 132.4556),
        GeoPoint("井口周辺", 34.3650, 132.3600),
        GeoPoint("祇園周辺", 34.4300, 132.4600),
        GeoPoint("山間部・戸河内", 34.5745, 132.2280),
        GeoPoint("県境・神石高原", 34.8500, 133.2500),
        // BUG-22回帰点: 岡山・広島の両bboxに含まれ、面積では岡山が狭い。ポリゴンで広島に解決すること。
        GeoPoint("福山駅周辺", 34.4856, 133.3625),
    )

    private fun runGeocoder() {
        val dbFile = File(dataDir(), "search.db")
        out("GEO db=${dbFile.absolutePath} size=${dbFile.length()} exists=${dbFile.isFile}")
        if (!dbFile.isFile) {
            out("GEO FAIL: search.db missing")
            return
        }
        for (p in geoPoints) {
            // BUG-22: ポリゴン併用県判定の実機確認 (DB内容とは独立に判定だけ記録)。
            out("PREF point=${p.label} lat=${p.lat} lon=${p.lon} prefecture=${findPrefectureName(p.lat, p.lon) ?: "(null)"}")
            val geocoder = SearchDbReverseGeocoder(dbFile, findPrefectureName(p.lat, p.lon))
            // 初回 (ページキャッシュ冷) + 29回 (温) で min/avg/median/max。
            val samples = DoubleArray(30)
            var address = "?"
            var ok = false
            for (i in samples.indices) {
                val t0 = SystemClock.elapsedRealtimeNanos()
                val result = try {
                    geocoder.lookup(p.lat, p.lon)
                } catch (t: Throwable) {
                    out("GEO ERROR ${p.label}: ${t.message}")
                    null
                }
                samples[i] = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
                if (i == 0) {
                    address = result?.formattedAddress ?: "(null)"
                    ok = result != null
                }
            }
            val sorted = samples.sorted()
            val avg = samples.average()
            val median = (sorted[14] + sorted[15]) / 2.0
            out(
                "GEO point=${p.label} lat=${p.lat} lon=${p.lon} ok=$ok addr=$address " +
                    "min=${"%.2f".format(sorted.first())} avg=${"%.2f".format(avg)} " +
                    "median=${"%.2f".format(median)} max=${"%.2f".format(sorted.last())}ms n=30",
            )
        }
    }

    // -- 100mゲート確認 ----------------------------------------------------------

    private class CountingGeocoder(var address: String?) : OfflineReverseGeocoder {
        var calls = 0
        override fun lookup(latitude: Double, longitude: Double): AddressResult? {
            calls++
            return address?.let { AddressResult(prefecture = "広島県", city = "広島市", formattedAddress = it) }
        }
    }

    private fun runGate() {
        val controller = AddressDisplayController()
        val geocoder = CountingGeocoder("広島県広島市中区国泰寺町一丁目")
        // 1Hz × 60回: 同一地点に留まる → 検索は1回のみのはず。
        repeat(60) { controller.update(34.3853, 132.4556, 10f, geocoder) }
        out("GATE stationary60 calls=${geocoder.calls} displayed=${controller.displayed}")
        // 100m未満の微動 × 60回 → 追加検索なしのはず。
        var lat = 34.3853
        var lon = 132.4556
        repeat(60) {
            lat += 0.00001
            lon += 0.00001
            controller.update(lat, lon, 10f, geocoder)
        }
        out("GATE microMove60 calls=${geocoder.calls} displayed=${controller.displayed}")
        // 100m以上の移動 × 5回 → 各回検索されるはず (表示は2回連続で確定)。
        repeat(5) {
            lat += 0.002
            lon += 0.002
            controller.update(lat, lon, 10f, geocoder)
        }
        out("GATE bigMove5 calls=${geocoder.calls} displayed=${controller.displayed}")
        // 精度不良 × 60回 → 検索されないはず。
        repeat(60) { controller.update(lat + 0.005, lon + 0.005, 500f, geocoder) }
        out("GATE badAccuracy60 calls=${geocoder.calls} displayed=${controller.displayed}")
    }

    // -- ルート探索 ---------------------------------------------------------------

    private data class RouteCase(val label: String, val sLat: Double, val sLon: Double, val gLat: Double, val gLon: Double)

    private val routeCases = listOf(
        RouteCase("広島駅→紙屋町", 34.3976, 132.4756, 34.3939, 132.4592),
        RouteCase("海田→広島駅", 34.3720, 132.4230, 34.3976, 132.4756),
        RouteCase("井口→廿日市", 34.3650, 132.3600, 34.3487, 132.3312),
        RouteCase("祇園→伴", 34.4300, 132.4600, 34.4450, 132.4300),
        RouteCase("戸河内→加計", 34.5745, 132.2280, 34.6167, 132.3167),
    )

    private fun openHiroshimaGraph(): LazyMappedRoadGraph? {
        val graphFile = File(dataDir(), "Hiroshima.graph")
        out("GRAPH file=${graphFile.absolutePath} size=${graphFile.length()} exists=${graphFile.isFile}")
        if (!graphFile.isFile) {
            out("GRAPH FAIL: Hiroshima.graph missing")
            return null
        }
        val t0 = SystemClock.elapsedRealtimeNanos()
        val graph = LazyMappedRoadGraph.load(graphFile)
        out("GRAPH loaded nodes=${graph.nodeCount} edges=${graph.edgeCount} loadMs=${(SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000}")
        return graph
    }

    private fun gidx(graph: LazyMappedRoadGraph, id: Long): Int? = try {
        graph.nodeIndexOf(id)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun branchesOf(graph: LazyMappedRoadGraph, id: Long): List<TurnClassifier.MappedBranch> {
        val index = gidx(graph, id) ?: return emptyList()
        val from = graph.nodeAt(index)
        return graph.outgoingBranches(index).mapNotNull { b ->
            val to = graph.nodeAt(b.targetIndex)
            val bearing = bearingBetween(from.latitude, from.longitude, to.latitude, to.longitude)
                ?: return@mapNotNull null
            TurnClassifier.MappedBranch(bearing, b.roadTypeName, b.distanceMeters)
        }
    }

    private fun classifyOnDevice(
        graph: LazyMappedRoadGraph,
        nodeIds: List<Long>,
        points: List<RoutePoint>,
    ) = TurnClassifier.classifyMappedRoute(
        nodeIds = nodeIds,
        coordinates = points,
        branchesOf = { branchesOf(graph, it) },
        edgeTypes = (0 until nodeIds.lastIndex).map { i ->
            val a = gidx(graph, nodeIds[i])
            val b = gidx(graph, nodeIds[i + 1])
            if (a == null || b == null) "unknown"
            else graph.outgoingBranches(a).firstOrNull { it.targetIndex == b }?.roadTypeName ?: "unknown"
        },
    )

    private fun runRoutes() {
        val graph = openHiroshimaGraph() ?: return
        try {
            for (c in routeCases) {
                val t0 = SystemClock.elapsedRealtimeNanos()
                val start = graph.nearestNodeIndex(c.sLat, c.sLon)
                val goal = graph.nearestNodeIndex(c.gLat, c.gLon)
                val result = graph.route(start, goal)
                val searchMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
                if (!result.isReachable) {
                    out("ROUTE case=${c.label} FAIL unreachable")
                    continue
                }
                val points = result.coordinates.map { (lat, lon) -> RoutePoint(lat, lon) }
                val instructions = classifyOnDevice(graph, result.nodeIds, points)
                val maneuvers = extractManeuvers(points)
                out(
                    "ROUTE case=${c.label} nodes=${result.nodeIds.size} " +
                        "distM=${result.totalDistanceMeters.toInt()} searchMs=${"%.0f".format(searchMs)} " +
                        "instructions=${instructions.size} maneuvers=${maneuvers.size} err=none",
                )
                for (ins in instructions) {
                    out(
                        "  [${ins.type}] angle=${"%.1f".format(ins.turnAngleDegrees)} " +
                            "deg=${ins.junctionBranches} ${ins.incomingRoadType}->${ins.outgoingRoadType} ${ins.reason}",
                    )
                }
            }
        } finally {
            graph.close()
        }
    }

    // -- ラウンドアバウト分析 ---------------------------------------------------------

    private fun branchDetail(graph: LazyMappedRoadGraph, nodeId: Long): String {
        val index = gidx(graph, nodeId) ?: return "unknown-node"
        val from = graph.nodeAt(index)
        return graph.outgoingBranches(index).joinToString("; ") { b ->
            val to = graph.nodeAt(b.targetIndex)
            val bearing = bearingBetween(from.latitude, from.longitude, to.latitude, to.longitude)
            "to=(${to.latitude},${to.longitude}) brg=${if (bearing == null) "?" else "%.1f".format(bearing)} type=${b.roadTypeName}"
        }
    }

    private fun runRoundabout() {
        val graph = openHiroshimaGraph() ?: return
        try {
            // 真陽性候補: OSM junction=roundabout (centroid 34.511238,132.515424) を南北に通過。
            val t0 = SystemClock.elapsedRealtimeNanos()
            val start = graph.nearestNodeIndex(34.5160, 132.5154)
            val goal = graph.nearestNodeIndex(34.5065, 132.5154)
            val result = graph.route(start, goal)
            val searchMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
            out("RB trueCandidate reachable=${result.isReachable} nodes=${result.nodeIds.size} searchMs=${"%.0f".format(searchMs)}")
            // 真陽性確定用: リング東西アーム間を通過するルート (desktopで onRing=5 を確認済み)。
            val t1 = SystemClock.elapsedRealtimeNanos()
            val start2 = graph.nearestNodeIndex(34.51178, 132.51410)
            val goal2 = graph.nearestNodeIndex(34.51019, 132.51669)
            val result2 = graph.route(start2, goal2)
            out(
                "RB trueTraversal reachable=${result2.isReachable} nodes=${result2.nodeIds.size} " +
                    "searchMs=${(SystemClock.elapsedRealtimeNanos() - t1) / 1_000_000}",
            )
            if (result2.isReachable) {
                val points2 = result2.coordinates.map { (lat, lon) -> RoutePoint(lat, lon) }
                for (ins in classifyOnDevice(graph, result2.nodeIds, points2)) {
                    val n = graph.nodeAt(gidx(graph, ins.nodeId) ?: continue)
                    out(
                        "  [${ins.type}] angle=${"%.1f".format(ins.turnAngleDegrees)} " +
                            "at=${n.latitude},${n.longitude} deg=${ins.junctionBranches} ${ins.reason}",
                    )
                }
            }
            if (result.isReachable) {
                val points = result.coordinates.map { (lat, lon) -> RoutePoint(lat, lon) }
                var minDist = Double.MAX_VALUE
                for (p in points) {
                    val d = abs(p.latitude - 34.511238) * 111_320.0
                    val e = abs(p.longitude - 132.515424) * 111_320.0 * 0.82
                    val approx = kotlin.math.sqrt(d * d + e * e)
                    if (approx < minDist) minDist = approx
                }
                out("RB trueCandidate minDistToCentroidM=${minDist.toInt()}")
                val instructions = classifyOnDevice(graph, result.nodeIds, points)
                for (ins in instructions) {
                    val n = graph.nodeAt(gidx(graph, ins.nodeId) ?: continue)
                    out(
                        "  [${ins.type}] angle=${"%.1f".format(ins.turnAngleDegrees)} " +
                            "at=${n.latitude},${n.longitude} deg=${ins.junctionBranches} ${ins.reason}",
                    )
                    if (ins.type.name == "ROUNDABOUT") {
                        out("    branches: ${branchDetail(graph, ins.nodeId)}")
                    }
                }
            }
            // 既知の誤検出5地点: 最寄りノードの枝構造を記録 (分類器への入力相当)。
            val suspects = listOf(
                "海田起点" to (34.3729028 to 132.4223441),
                "広島駅南口" to (34.3975045 to 132.4754641),
                "廿日市終点" to (34.3492382 to 132.3314774),
                "山間A-1" to (34.572774 to 132.1307000),
                "山間A-2" to (34.576007 to 132.135436),
            )
            for ((label, ll) in suspects) {
                val idx = graph.nearestNodeIndex(ll.first, ll.second)
                val node = graph.nodeAt(idx)
                out("RB suspect=$label coord=${ll.first},${ll.second} nearest=(${node.latitude},${node.longitude})")
                out("  branches: ${branchDetail(graph, node.id)}")
            }
        } finally {
            graph.close()
        }
    }

    // -- 案内開始パリティ: 本番 (MainActivity) と同一の入口・同一パラメータで検証 --------
    // 本番: yamaguchi.graph + .idx を load → nearestNodeIndex×2 →
    //   graph.route(start, goal, maxExpandedNodes=4_000_000) → buildRouteInstructions相当。
    // UIスレッド・Polyline・state遷移は本番側の CycleMapRoute ログで確認する。

    private data class GuidanceCase(val label: String, val sLat: Double, val sLon: Double, val gLat: Double, val gLon: Double)

    private fun runGuidance() {
        val graphFile = File(dataDir(), "yamaguchi.graph")
        val graphIndex = File(dataDir(), "yamaguchi.graph.idx")
        out("GUIDE file=${graphFile.absolutePath} size=${graphFile.length()} idx=${graphIndex.length()}")
        if (!graphFile.isFile || !graphIndex.isFile) {
            out("GUIDE FAIL: production graph missing")
            return
        }
        val cases = listOf(
            // 山口市内 (本番グラフ圏内: 到達可能のはず)。
            GuidanceCase("山口市役所→山口駅", 34.1785, 131.4737, 34.1684, 131.4482),
            // 広島ペアを山口グラフで探索 (本番UI再現: 到達不能のはず)。
            GuidanceCase("広島駅→近傍(on-yamaguchi-graph)", 34.3976, 132.4756, 34.3981, 132.4744),
        )
        try {
            val graph = LazyMappedRoadGraph.load(graphFile, graphIndex)
            try {
                for (c in cases) {
                    out("GUIDE REQUEST case=${c.label} start=${c.sLat},${c.sLon} goal=${c.gLat},${c.gLon}")
                    val t0 = SystemClock.elapsedRealtimeNanos()
                    val start = graph.nearestNodeIndex(c.sLat, c.sLon)
                    val goal = graph.nearestNodeIndex(c.gLat, c.gLon)
                    out("GUIDE SNAP startNode=$start goalNode=$goal")
                    val result = graph.route(start, goal, maxExpandedNodes = 4_000_000)
                    val searchMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
                    out(
                        "GUIDE RESULT reachable=${result.isReachable} truncated=${result.wasTruncated} " +
                            "nodes=${result.nodeIds.size} searchMs=${"%.0f".format(searchMs)}",
                    )
                    if (!result.isReachable) {
                        out("GUIDE OUTCOME no-polyline no-instructions (本番では警告表示のみ)")
                        continue
                    }
                    val points = result.coordinates.map { (lat, lon) -> RoutePoint(lat, lon) }
                    val instructions = classifyOnDevice(graph, result.nodeIds, points)
                    val maneuvers = extractManeuvers(points)
                    out(
                        "GUIDE OUTCOME polylinePointCount=${points.size} " +
                            "instructionCount=${instructions.size} maneuverCount=${maneuvers.size} " +
                            "routeDisplayed=true相当",
                    )
                }
            } finally {
                graph.close()
            }
        } catch (t: Throwable) {
            out("GUIDE EXCEPTION ${t::class.java.simpleName}: ${t.message}")
        }
    }

    // -- ヘアピン探索 -----------------------------------------------------------------

    private fun runHairpin() {
        val graph = openHiroshimaGraph() ?: return
        try {
            val pairs = listOf(
                "戸河内→加計" to doubleArrayOf(34.5745, 132.2280, 34.6167, 132.3167),
                "大佐→恐羅漢" to doubleArrayOf(34.5500, 132.1300, 34.6000, 132.2000),
                "三段峡→匹見" to doubleArrayOf(34.6200, 132.1800, 34.6500, 132.2500),
            )
            for ((label, q) in pairs) {
                val start = graph.nearestNodeIndex(q[0], q[1])
                val goal = graph.nearestNodeIndex(q[2], q[3])
                val result = graph.route(start, goal)
                if (!result.isReachable) {
                    out("HAIRPIN case=$label UNREACHABLE")
                    continue
                }
                val points = result.coordinates.map { (lat, lon) -> RoutePoint(lat, lon) }
                val instructions = classifyOnDevice(graph, result.nodeIds, points)
                val top = instructions.sortedByDescending { abs(it.turnAngleDegrees) }.take(20)
                out("HAIRPIN case=$label nodes=${result.nodeIds.size} distM=${result.totalDistanceMeters.toInt()} instructions=${instructions.size}")
                for (ins in top) {
                    val n = graph.nodeAt(gidx(graph, ins.nodeId) ?: continue)
                    out(
                        "  [${ins.type}] angle=${"%.1f".format(ins.turnAngleDegrees)} " +
                            "at=${n.latitude},${n.longitude} ${ins.incomingRoadType}->${ins.outgoingRoadType} " +
                            "deg=${ins.junctionBranches} ${ins.reason}",
                    )
                }
            }
        } finally {
            graph.close()
        }
    }
}
