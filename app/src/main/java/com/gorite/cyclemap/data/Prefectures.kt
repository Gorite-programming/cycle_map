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
    const val DOWNLOAD_MIN_ZOOM = 10
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
                markDownloaded(context, prefId, sourceType, 5771)
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
    fun download(
        context: Context,
        pref: Prefecture,
        sourceType: MapSourceType,
        source: XYTileSource,
        onProgress: (TileProgress) -> Unit,
    ) {
        val dataDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "CycleMap").apply { mkdirs() }
        val tileDir = File(dataDir, "tiles").apply { mkdirs() }
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            setOsmdroidBasePath(dataDir)
            setOsmdroidTileCache(tileDir)
            userAgentValue = context.packageName
        }

        val writer = SqlTileWriter()
        val total = PrefectureData.calculateTileCount(pref.bounds)
        val completed = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(8)

        val baseUrl = when (sourceType) {
            MapSourceType.GSI -> "https://cyberjapandata.gsi.go.jp/xyz/std"
            MapSourceType.OSM -> "https://tile.openstreetmap.org"
        }

        for (zoom in PrefectureData.DOWNLOAD_MIN_ZOOM..PrefectureData.DOWNLOAD_MAX_ZOOM) {
            val minX = PrefectureData.tileX(pref.bounds.lonWest, zoom)
            val maxX = PrefectureData.tileX(pref.bounds.lonEast, zoom)
            val minY = PrefectureData.tileY(pref.bounds.latNorth, zoom)
            val maxY = PrefectureData.tileY(pref.bounds.latSouth, zoom)
            for (x in minX..maxX) for (y in minY..maxY) {
                executor.submit {
                    val urlStr = "$baseUrl/$zoom/$x/$y.png"
                    try {
                        val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15_000
                            readTimeout = 30_000
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", "CycleMap/1.0 (Android; cycling navigator; personal use)")
                        }
                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                            connection.inputStream.use { input ->
                                synchronized(writer) {
                                    writer.saveFile(source, MapTileIndex.getTileIndex(zoom, x, y), input, null)
                                }
                            }
                        }
                        connection.disconnect()
                    } catch (_: Exception) {
                        // エラー時も進捗カウントを進める
                    } finally {
                        val current = completed.incrementAndGet()
                        onProgress(TileProgress(pref.name, sourceType.displayName, current, total))
                    }
                }
            }
        }

        executor.shutdown()
        executor.awaitTermination(30, TimeUnit.MINUTES)
        writer.onDetach()

        DownloadStatusManager.markDownloaded(context, pref.id, sourceType, total)
    }
}
