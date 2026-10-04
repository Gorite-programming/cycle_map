package com.gorite.cyclemap.data

import android.content.Context
import android.os.Environment
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class Prefecture(
    val id: String,
    val name: String,
    val region: String,
    val bounds: BoundingBox,
)

enum class MapSourceType(val id: String, val displayName: String) {
    GSI("gsi", "地理院地図（標準）"),
    OSM("osm", "OpenStreetMap"),
}

data class TileProgress(
    val prefName: String,
    val sourceName: String,
    val completed: Int,
    val total: Int,
)

object PrefectureData {
    /**
     * 低ズーム (z7-) まで事前取得する。z7–z9は1県あたり数十枚程度と軽量だが、
     * ズームアウト時の地図表示をキャッシュで即時描画できる効果が大きい。
     * z5–z6は全国で十数枚のため表示時に取得する。
     */
    const val DOWNLOAD_MIN_ZOOM = 7
    const val DOWNLOAD_MAX_ZOOM = 14

    val REGIONS = listOf("北海道", "東北", "関東", "中部", "近畿", "中国", "四国", "九州・沖縄")

    val ALL: List<Prefecture> = listOf(
        // 北海道
        Prefecture("hokkaido", "北海道", "北海道", BoundingBox(45.55, 145.82, 41.35, 139.33)),

        // 東北
        Prefecture("aomori", "青森県", "東北", BoundingBox(41.56, 141.68, 40.22, 139.50)),
        Prefecture("iwate", "岩手県", "東北", BoundingBox(40.45, 142.07, 38.74, 140.66)),
        Prefecture("miyagi", "宮城県", "東北", BoundingBox(39.00, 141.67, 37.77, 140.27)),
        Prefecture("akita", "秋田県", "東北", BoundingBox(40.52, 140.99, 38.97, 139.70)),
        Prefecture("yamagata", "山形県", "東北", BoundingBox(39.16, 140.65, 37.74, 139.53)),
        Prefecture("fukushima", "福島県", "東北", BoundingBox(37.97, 141.04, 36.79, 139.16)),

        // 関東
        Prefecture("ibaraki", "茨城県", "関東", BoundingBox(36.94, 140.85, 35.74, 139.69)),
        Prefecture("tochigi", "栃木県", "関東", BoundingBox(37.15, 140.29, 36.20, 139.33)),
        Prefecture("gunma", "群馬県", "関東", BoundingBox(37.06, 139.66, 35.98, 138.39)),
        Prefecture("saitama", "埼玉県", "関東", BoundingBox(36.28, 139.90, 35.75, 138.71)),
        Prefecture("chiba", "千葉県", "関東", BoundingBox(36.10, 140.88, 34.90, 139.74)),
        Prefecture("tokyo", "東京都", "関東", BoundingBox(35.90, 139.92, 35.51, 138.94)),
        Prefecture("kanagawa", "神奈川県", "関東", BoundingBox(35.67, 139.78, 35.13, 138.91)),

        // 中部
        Prefecture("niigata", "新潟県", "中部", BoundingBox(38.56, 140.03, 36.73, 137.64)),
        Prefecture("toyama", "富山県", "中部", BoundingBox(36.98, 137.76, 36.27, 136.77)),
        Prefecture("ishikawa", "石川県", "中部", BoundingBox(37.52, 137.37, 36.07, 136.24)),
        Prefecture("fukui", "福井県", "中部", BoundingBox(36.30, 136.83, 35.35, 135.45)),
        Prefecture("yamanashi", "山梨県", "中部", BoundingBox(35.97, 139.14, 35.17, 138.18)),
        Prefecture("nagano", "長野県", "中部", BoundingBox(37.03, 138.75, 35.19, 137.32)),
        Prefecture("gifu", "岐阜県", "中部", BoundingBox(36.47, 137.65, 35.13, 136.27)),
        Prefecture("shizuoka", "静岡県", "中部", BoundingBox(35.64, 139.15, 34.59, 137.48)),
        Prefecture("aichi", "愛知県", "中部", BoundingBox(35.42, 137.84, 34.58, 136.67)),

        // 近畿
        Prefecture("mie", "三重県", "近畿", BoundingBox(35.25, 136.98, 33.72, 135.85)),
        Prefecture("shiga", "滋賀県", "近畿", BoundingBox(35.70, 136.45, 34.78, 135.85)),
        Prefecture("kyoto", "京都府", "近畿", BoundingBox(35.78, 136.04, 34.78, 134.86)),
        Prefecture("osaka", "大阪府", "近畿", BoundingBox(35.05, 135.75, 34.27, 135.09)),
        Prefecture("hyogo", "兵庫県", "近畿", BoundingBox(35.68, 135.47, 34.15, 134.25)),
        Prefecture("nara", "奈良県", "近畿", BoundingBox(34.78, 136.14, 33.86, 135.53)),
        Prefecture("wakayama", "和歌山県", "近畿", BoundingBox(34.38, 135.94, 33.43, 135.06)),

        // 中国
        Prefecture("tottori", "鳥取県", "中国", BoundingBox(35.60, 134.44, 35.13, 133.17)),
        Prefecture("shimane", "島根県", "中国", BoundingBox(36.35, 133.39, 34.30, 131.67)),
        Prefecture("okayama", "岡山県", "中国", BoundingBox(35.35, 134.41, 34.30, 133.26)),
        Prefecture("hiroshima", "広島県", "中国", BoundingBox(35.10, 133.47, 34.03, 132.03)),
        Prefecture("yamaguchi", "山口県", "中国", BoundingBox(34.80, 132.20, 33.70, 130.70)),

        // 四国
        Prefecture("tokushima", "徳島県", "四国", BoundingBox(34.24, 134.69, 33.54, 133.71)),
        Prefecture("kagawa", "香川県", "四国", BoundingBox(34.57, 134.44, 34.01, 133.45)),
        Prefecture("ehime", "愛媛県", "四国", BoundingBox(34.31, 133.69, 32.89, 132.01)),
        Prefecture("kochi", "高知県", "四国", BoundingBox(33.88, 134.31, 32.70, 132.48)),

        // 九州・沖縄
        Prefecture("fukuoka", "福岡県", "九州・沖縄", BoundingBox(33.97, 131.06, 33.05, 130.00)),
        Prefecture("saga", "佐賀県", "九州・沖縄", BoundingBox(33.62, 130.54, 32.96, 129.74)),
        Prefecture("nagasaki", "長崎県", "九州・沖縄", BoundingBox(34.72, 130.40, 32.57, 128.59)),
        Prefecture("kumamoto", "熊本県", "九州・沖縄", BoundingBox(33.19, 131.32, 32.08, 129.98)),
        Prefecture("oita", "大分県", "九州・沖縄", BoundingBox(33.74, 132.09, 32.71, 130.84)),
        Prefecture("miyazaki", "宮崎県", "九州・沖縄", BoundingBox(32.84, 131.88, 31.35, 130.70)),
        Prefecture("kagoshima", "鹿児島県", "九州・沖縄", BoundingBox(32.31, 131.19, 30.98, 129.98)),
        Prefecture("okinawa", "沖縄県", "九州・沖縄", BoundingBox(27.09, 128.33, 26.07, 127.64)),
    )

    fun tileX(longitude: Double, zoom: Int): Int =
        kotlin.math.floor((longitude + 180.0) / 360.0 * (1 shl zoom)).toInt()

    fun tileY(latitude: Double, zoom: Int): Int =
        kotlin.math.floor(
            (1.0 - kotlin.math.asinh(kotlin.math.tan(Math.toRadians(latitude))) / Math.PI) /
                2.0 * (1 shl zoom),
        ).toInt()

    fun calculateTileCount(bounds: BoundingBox, minZoom: Int = DOWNLOAD_MIN_ZOOM, maxZoom: Int = DOWNLOAD_MAX_ZOOM): Int {
        var total = 0
        for (zoom in minZoom..maxZoom) {
            val minX = tileX(bounds.lonWest, zoom)
            val maxX = tileX(bounds.lonEast, zoom)
            val minY = tileY(bounds.latNorth, zoom)
            val maxY = tileY(bounds.latSouth, zoom)
            total += (maxX - minX + 1) * (maxY - minY + 1)
        }
        return total
    }

    fun estimateSizeMb(tileCount: Int): Double = tileCount * 0.025 // 約25KB/tile
}

object DownloadStatusManager {
    private const val PREF_NAME = "tile_downloads_status"

    fun isDownloaded(context: Context, prefId: String, sourceType: MapSourceType): Boolean {
        // 山口県のGSIが既存DBにある場合は初期状態でtrue扱いにする
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.contains("${prefId}_${sourceType.id}")) {
            return prefs.getBoolean("${prefId}_${sourceType.id}", false)
        }
        if (prefId == "yamaguchi" && sourceType == MapSourceType.GSI) {
            val tileFile = File(File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "CycleMap/tiles"), SqlTileWriter.DATABASE_FILENAME)
            if (tileFile.isFile && tileFile.length() > 50_000_000L) {
                val count = PrefectureData.ALL.firstOrNull { it.id == "yamaguchi" }?.let { PrefectureData.calculateTileCount(it.bounds) } ?: 0
                markDownloaded(context, prefId, sourceType, count)
                return true
            }
        }
        return false
    }

    fun markDownloaded(context: Context, prefId: String, sourceType: MapSourceType, tileCount: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("${prefId}_${sourceType.id}", true)
            .putLong("${prefId}_${sourceType.id}_time", System.currentTimeMillis())
            .putInt("${prefId}_${sourceType.id}_count", tileCount)
            .apply()
    }

    fun getDownloadInfo(context: Context, prefId: String, sourceType: MapSourceType): Pair<Long, Int>? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!isDownloaded(context, prefId, sourceType)) return null
        val time = prefs.getLong("${prefId}_${sourceType.id}_time", 0L)
        val count = prefs.getInt("${prefId}_${sourceType.id}_count", 0)
        return Pair(time, count)
    }
}

object TileDownloader {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_RETRIES = 1
    private const val THREADS = 8

    fun download(
        context: Context,
        pref: Prefecture,
        sourceType: MapSourceType,
        source: XYTileSource,
        onProgress: (TileProgress) -> Unit,
    ) {
        downloadTiles(
            context = context,
            label = pref.name,
            bounds = pref.bounds,
            sourceType = sourceType,
            source = source,
            minZoom = PrefectureData.DOWNLOAD_MIN_ZOOM,
            maxZoom = PrefectureData.DOWNLOAD_MAX_ZOOM,
            onProgress = onProgress,
            onDone = { total -> DownloadStatusManager.markDownloaded(context, pref.id, sourceType, total) },
        )
    }

    /**
     * 任意の BoundingBox と zoom 範囲でタイルをダウンロードする。
     * エリア選択 (z15-z16) 用途など、都道府県バウンダリに縛られない任意範囲ダウンロードに使用する。
     * 既存の osmdroid SQLite キャッシュに書き込むため、通常レイヤーのタイルと共存する。
     */
    fun downloadArea(
        context: Context,
        bounds: BoundingBox,
        areaLabel: String,
        sourceType: MapSourceType,
        source: XYTileSource,
        minZoom: Int,
        maxZoom: Int,
        onProgress: (TileProgress) -> Unit,
    ) {
        downloadTiles(
            context = context,
            label = areaLabel,
            bounds = bounds,
            sourceType = sourceType,
            source = source,
            minZoom = minZoom,
            maxZoom = maxZoom,
            onProgress = onProgress,
            onDone = null,
        )
    }

    /**
     * 経路沿いの回廊タイルを事前ダウンロードする (AGENTS.md 方針合致)。
     * 経路ポリラインから bufferTiles (既定1タイル=数百m〜1km) の幅に含まれる
     * 指定ズーム範囲 (既定14〜16) のタイルのみを抽出し、高速・最小容量でキャッシュする。
     */
    fun downloadRouteCorridor(
        context: Context,
        routePoints: List<Pair<Double, Double>>, // (lat, lon)
        label: String = "経路回廊",
        sourceType: MapSourceType = MapSourceType.GSI,
        source: XYTileSource,
        minZoom: Int = 14,
        maxZoom: Int = 16,
        bufferTiles: Int = 1,
        onProgress: (TileProgress) -> Unit,
        onDone: ((Int) -> Unit)? = null,
    ) {
        if (routePoints.isEmpty()) {
            onDone?.invoke(0)
            return
        }

        val dataDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "CycleMap").apply { mkdirs() }
        val tileDir = File(dataDir, "tiles").apply { mkdirs() }
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            setOsmdroidBasePath(dataDir)
            setOsmdroidTileCache(tileDir)
            userAgentValue = "CycleMap/1.0 (Android; cycling navigator; personal use)"
        }

        val baseUrl = when (source.name()) {
            "GSI Relief" -> "https://cyberjapandata.gsi.go.jp/xyz/relief"
            "OpenStreetMap" -> "https://tile.openstreetmap.org"
            else -> when (sourceType) {
                MapSourceType.GSI -> "https://cyberjapandata.gsi.go.jp/xyz/std"
                MapSourceType.OSM -> "https://tile.openstreetmap.org"
            }
        }

        val jobSet = LinkedHashSet<TileJob>()
        for (zoom in minZoom..maxZoom) {
            val zoomJobs = HashSet<TileJob>()
            for ((lat, lon) in routePoints) {
                val cx = PrefectureData.tileX(lon, zoom)
                val cy = PrefectureData.tileY(lat, zoom)
                for (dx in -bufferTiles..bufferTiles) {
                    for (dy in -bufferTiles..bufferTiles) {
                        zoomJobs.add(TileJob(zoom, cx + dx, cy + dy))
                    }
                }
            }
            jobSet.addAll(zoomJobs)
        }
        val jobs = jobSet.toList()
        val total = jobs.size
        if (total == 0) {
            onDone?.invoke(0)
            return
        }

        val cached = readCachedTileKeys(File(tileDir, SqlTileWriter.DATABASE_FILENAME), source.name())
        val writer = SqlTileWriter()
        val completed = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(THREADS)

        fun report() {
            val current = completed.incrementAndGet()
            onProgress(TileProgress(label, sourceType.displayName, current, total))
        }

        for (job in jobs) {
            val tileIndex = MapTileIndex.getTileIndex(job.zoom, job.x, job.y)
            if (tileIndex in cached) {
                report()
                continue
            }
            executor.submit {
                val urlStr = "$baseUrl/${job.zoom}/${job.x}/${job.y}.png"
                var attempt = 0
                var saved = false
                while (!saved && attempt <= MAX_RETRIES) {
                    if (attempt > 0) Thread.sleep(300L * attempt)
                    var connection: HttpURLConnection? = null
                    try {
                        connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                            connectTimeout = CONNECT_TIMEOUT_MS
                            readTimeout = READ_TIMEOUT_MS
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", "CycleMap/1.0 (Android; cycling navigator; personal use)")
                        }
                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                            connection.inputStream.use { input ->
                                writer.saveFile(source, tileIndex, input, null)
                            }
                            saved = true
                        }
                    } catch (_: Exception) {
                        attempt++
                    } finally {
                        connection?.disconnect()
                    }
                }
                report()
            }
        }
        executor.shutdown()
        try {
            executor.awaitTermination(60, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {}
        onDone?.invoke(total)
    }

    private data class TileJob(val zoom: Int, val x: Int, val y: Int)

    /**
     * ダウンロード共通コア。
     * - 低ズームから順に取得 (ズームアウト表示が先に使える)
     * - 同一ズーム内は中心から外向きに取得 (体感完了を早める)
     * - キャッシュ済みはスキップ (中断後の再開・重複DLを高速化)
     * - 失敗時は1回だけリトライ
     */
    private fun downloadTiles(
        context: Context,
        label: String,
        bounds: BoundingBox,
        sourceType: MapSourceType,
        source: XYTileSource,
        minZoom: Int,
        maxZoom: Int,
        onProgress: (TileProgress) -> Unit,
        onDone: ((Int) -> Unit)?,
    ) {
        val dataDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "CycleMap").apply { mkdirs() }
        val tileDir = File(dataDir, "tiles").apply { mkdirs() }
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            setOsmdroidBasePath(dataDir)
            setOsmdroidTileCache(tileDir)
            userAgentValue = context.packageName
        }

        val baseUrl = when (source.name()) {
            "GSI Relief" -> "https://cyberjapandata.gsi.go.jp/xyz/relief"
            "OpenStreetMap" -> "https://tile.openstreetmap.org"
            else -> when (sourceType) {
                MapSourceType.GSI -> "https://cyberjapandata.gsi.go.jp/xyz/std"
                MapSourceType.OSM -> "https://tile.openstreetmap.org"
            }
        }

        // 低ズーム→高ズーム、各ズーム内は中心out順
        val jobs = ArrayList<TileJob>()
        for (zoom in minZoom..maxZoom) {
            val minX = PrefectureData.tileX(bounds.lonWest, zoom)
            val maxX = PrefectureData.tileX(bounds.lonEast, zoom)
            val minY = PrefectureData.tileY(bounds.latNorth, zoom)
            val maxY = PrefectureData.tileY(bounds.latSouth, zoom)
            val cx = (minX + maxX) / 2.0
            val cy = (minY + maxY) / 2.0
            val level = ArrayList<TileJob>()
            for (x in minX..maxX) for (y in minY..maxY) level += TileJob(zoom, x, y)
            level.sortBy { (it.x - cx) * (it.x - cx) + (it.y - cy) * (it.y - cy) }
            jobs += level
        }
        val total = jobs.size

        // キャッシュ済みタイルのスナップショット (1クエリ)。失敗時は空扱いで全DLする。
        val cached = readCachedTileKeys(File(tileDir, SqlTileWriter.DATABASE_FILENAME), source.name())
        val writer = SqlTileWriter()
        val completed = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(THREADS)

        fun report() {
            val current = completed.incrementAndGet()
            onProgress(TileProgress(label, sourceType.displayName, current, total))
        }

        for (job in jobs) {
            val tileIndex = MapTileIndex.getTileIndex(job.zoom, job.x, job.y)
            if (tileIndex in cached) {
                report()
                continue
            }
            executor.submit {
                val urlStr = "$baseUrl/${job.zoom}/${job.x}/${job.y}.png"
                var attempt = 0
                var saved = false
                while (!saved && attempt <= MAX_RETRIES) {
                    if (attempt > 0) Thread.sleep(500L * attempt)
                    var connection: HttpURLConnection? = null
                    try {
                        connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                            connectTimeout = CONNECT_TIMEOUT_MS
                            readTimeout = READ_TIMEOUT_MS
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", "CycleMap/1.0 (Android; cycling navigator; personal use)")
                        }
                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                            connection.inputStream.use { input ->
                                synchronized(writer) {
                                    writer.saveFile(source, tileIndex, input, null)
                                }
                            }
                            saved = true
                        }
                    } catch (_: Exception) {
                        // リトライまたはスキップ (進捗は進める)
                    } finally {
                        connection?.disconnect()
                    }
                    attempt++
                }
                report()
            }
        }

        executor.shutdown()
        executor.awaitTermination(60, TimeUnit.MINUTES)
        writer.onDetach()
        onDone?.invoke(total)
    }

    /**
     * 指定ソースのキャッシュ済みタイルキー一覧を取得する。
     * osmdroid SQLite (`tiles(key, provider, tile)`) を直接参照する。
     */
    private fun readCachedTileKeys(dbFile: File, providerName: String): Set<Long> {
        if (!dbFile.isFile) return emptySet()
        val keys = HashSet<Long>()
        try {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
            ).use { db ->
                db.rawQuery("SELECT key FROM tiles WHERE provider = ?", arrayOf(providerName)).use { cursor ->
                    while (cursor.moveToNext()) keys += cursor.getLong(0)
                }
            }
        } catch (_: Exception) {
            return emptySet()
        }
        return keys
    }
}

/** routing用県グラフの選択結果。 */
sealed interface RoutingGraphSelection {
    /** 対応グラフあり。fileName は CycleMap データ dir 直下の実ファイル名。 */
    data class Available(
        val prefectureId: String,
        val prefectureName: String,
        val fileName: String,
    ) : RoutingGraphSelection

    /** 対応グラフなし (未対応県・県判定不能)。他県グラフの使い回しは禁止のため呼び出し側は失敗させる。 */
    data class Unavailable(val prefectureName: String?) : RoutingGraphSelection
}

/**
 * 現在地→県→routing用グラフの選択 (純粋関数・JVMテスト可能)。
 * 県判定は既存 [findPrefectureName]、県定義は既存 [PrefectureData.ALL] を再利用する。
 */
object RoutingGraphSelector {
    /**
     * 配置済みの県グラフ (県ID → ファイル名)。
     * ファイル命名は生成パイプライン由来で一貫しないため (Hiroshima.graph に対し yamaguchi.graph)、
     * 推測せず対応表に固定する。県追加時は実ファイル名で1行追加する。
     */
    private val SUPPORTED_FILES = mapOf(
        "yamaguchi" to "yamaguchi.graph",
        "hiroshima" to "Hiroshima.graph",
        "okayama" to "Okayama.graph",
        "shimane" to "Shimane.graph",
        "tottori" to "Tottori.graph",
    )

    fun select(latitude: Double, longitude: Double): RoutingGraphSelection {
        val name = findPrefectureName(latitude, longitude)
        val pref = PrefectureData.ALL.firstOrNull { it.name == name }
        val file = pref?.let { SUPPORTED_FILES[it.id] }
        return if (pref != null && file != null) {
            RoutingGraphSelection.Available(pref.id, pref.name, file)
        } else {
            RoutingGraphSelection.Unavailable(name)
        }
    }

    /** 対応する .graph.idx のファイル名。 */
    fun indexFileName(graphFileName: String): String = "$graphFileName.idx"

    /**
     * 実在するグラフファイルとインデックスファイルのペアを解決する。
     * パイプライン生成物 (Yamaguchi.graph) と既存配備 (yamaguchi.graph) の
     * 大文字小文字の違いをフォールバック解決する (BUG-65)。
     */
    fun resolveGraphFiles(dataDir: File, fileName: String): Pair<File, File>? {
        val primaryGraph = File(dataDir, fileName)
        val primaryIdx = File(dataDir, indexFileName(fileName))
        if (primaryGraph.isFile && primaryIdx.isFile) return primaryGraph to primaryIdx

        val altName = if (fileName.startsWith("yamaguchi", ignoreCase = true)) {
            if (fileName.startsWith("yamaguchi")) "Yamaguchi.graph" else "yamaguchi.graph"
        } else {
            val capitalized = fileName.replaceFirstChar { it.uppercase() }
            if (capitalized == fileName) fileName.replaceFirstChar { it.lowercase() } else capitalized
        }
        val altGraph = File(dataDir, altName)
        val altIdx = File(dataDir, indexFileName(altName))
        if (altGraph.isFile && altIdx.isFile) return altGraph to altIdx

        return null
    }

    /**
     * 配置済みのグラフファイル (.graph) と対応するインデックス (.graph.idx) のペアのうち、
     * ファイル名昇順で最初に見つかった有効ペアを返す。
     */
    fun findFirstAvailableGraph(dataDir: File): Pair<File, File>? {
        val files = dataDir.listFiles() ?: return null
        val graphFiles = files.filter { it.isFile && it.name.endsWith(".graph", ignoreCase = true) }
            .sortedBy { it.name }
        for (graphFile in graphFiles) {
            val resolved = resolveGraphFiles(dataDir, graphFile.name)
            if (resolved != null) return resolved
        }
        return null
    }
}

/** 検索DBの選択結果。 */
sealed interface SearchDbSelection {
    /** 対応DBあり。fileName は CycleMap データ dir 直下の実ファイル名。 */
    data class Available(
        val prefectureId: String,
        val prefectureName: String,
        val fileName: String,
    ) : SearchDbSelection

    /** 対応DBなし (未対応県・県判定不能)。他県DBの使い回しは禁止のため呼び出し側は失敗させる。 */
    data class Unavailable(val prefectureName: String?) : SearchDbSelection
}

/**
 * 現在地→県→検索DB (search.db系) の選択 (純粋関数・JVMテスト可能)。
 * 県判定は既存 [findPrefectureName]、県定義は既存 [PrefectureData.ALL] を再利用する。
 */
object SearchDbSelector {
    /**
     * 配置済みの県別検索DB (県ID → ファイル名)。
     * yamaguchi は既配備の search.db (山口県内容) をそのまま使う。
     * hiroshima はパイプライン命名 (Hiroshima.search.db) に従う。県追加時は実ファイル名で1行追加する。
     */
    private val SUPPORTED_FILES = mapOf(
        "yamaguchi" to "search.db",
        "hiroshima" to "Hiroshima.search.db",
        "okayama" to "Okayama.search.db",
        "shimane" to "Shimane.search.db",
        "tottori" to "Tottori.search.db",
    )

    fun select(latitude: Double, longitude: Double): SearchDbSelection {
        val name = findPrefectureName(latitude, longitude)
        val pref = PrefectureData.ALL.firstOrNull { it.name == name }
        val file = pref?.let { SUPPORTED_FILES[it.id] }
        return if (pref != null && file != null) {
            SearchDbSelection.Available(pref.id, pref.name, file)
        } else {
            SearchDbSelection.Unavailable(name)
        }
    }

    /**
     * 実在する検索DBファイルを解決する。
     * パイプライン生成物 (Yamaguchi.search.db) と既存配備 (search.db) の
     * プレフィックス・大文字小文字の違いをフォールバック解決する (BUG-65)。
     */
    fun resolveDbFile(dataDir: File, fileName: String): File? {
        val primary = File(dataDir, fileName)
        if (primary.isFile) return primary

        val candidates = when (fileName) {
            "search.db" -> listOf("Yamaguchi.search.db", "yamaguchi.search.db")
            "Yamaguchi.search.db", "yamaguchi.search.db" -> listOf("search.db")
            else -> {
                val cap = fileName.replaceFirstChar { it.uppercase() }
                if (cap == fileName) listOf(fileName.replaceFirstChar { it.lowercase() }) else listOf(cap)
            }
        }
        for (cand in candidates) {
            val f = File(dataDir, cand)
            if (f.isFile) return f
        }
        return null
    }

    /**
     * 配置済みの検索DB (.search.db または search.db) のうち、
     * ファイル名昇順で最初に見つかった実在ファイルを返す。
     */
    fun findFirstAvailableDb(dataDir: File): File? {
        val files = dataDir.listFiles() ?: return null
        val dbFiles = files.filter { file ->
            file.isFile && (file.name.endsWith(".search.db", ignoreCase = true) || file.name.equals("search.db", ignoreCase = true))
        }.sortedBy { it.name }
        for (dbFile in dbFiles) {
            val resolved = resolveDbFile(dataDir, dbFile.name)
            if (resolved != null) return resolved
        }
        return null
    }
}
