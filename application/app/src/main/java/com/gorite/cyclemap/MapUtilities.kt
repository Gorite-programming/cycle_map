package com.gorite.cyclemap

import android.content.Context
import android.os.Environment
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.XYTileSource
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

import org.osmdroid.util.MapTileIndex

// ---------------------------------------------------------------------------
// タイルソース定義 (最大ズーム21までのデジタル拡大・高画質タイル表示対応)
// ---------------------------------------------------------------------------

/**
 * 通常の配信上限 (maxOnlineZoomLevel) を超えるズームレベル (最大21) での
 * デジタル拡大 (overscaling) およびローカル高解像度タイルの表示を可能にするタイルソース。
 * オンライン配信が無い上位ズームでは空文字を返すことで無駄なHTTP通信を遮断する。
 */
open class OverzoomingTileSource(
    aName: String,
    aZoomMinLevel: Int,
    aZoomMaxLevel: Int,
    val maxOnlineZoomLevel: Int,
    aTileSizePixels: Int,
    aImageFilenameEnding: String,
    aBaseUrl: Array<String>,
) : XYTileSource(aName, aZoomMinLevel, aZoomMaxLevel, aTileSizePixels, aImageFilenameEnding, aBaseUrl) {

    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        if (zoom > maxOnlineZoomLevel) {
            return ""
        }
        return super.getTileURLString(pMapTileIndex)
    }
}

internal fun gsiTileSource() = OverzoomingTileSource(
    "GSI Standard", 2, 21, 18, 256, ".png",
    arrayOf("https://cyberjapandata.gsi.go.jp/xyz/std/"),
)

internal fun osmTileSource() = OverzoomingTileSource(
    "OpenStreetMap", 2, 21, 19, 256, ".png",
    arrayOf(
        "https://a.tile.openstreetmap.org/",
        "https://b.tile.openstreetmap.org/",
        "https://c.tile.openstreetmap.org/",
    ),
)

/** 地理院 陰影起伏図 (等高線・地形の把握用。航空写真ではない)。 */
internal fun gsiReliefTileSource() = OverzoomingTileSource(
    "GSI Relief", 2, 21, 15, 256, ".png",
    arrayOf("https://cyberjapandata.gsi.go.jp/xyz/relief/"),
)

// ---------------------------------------------------------------------------
// アプリデータディレクトリ
// ---------------------------------------------------------------------------

internal fun cycleMapDataDir(context: Context): File =
    File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "CycleMap").apply { mkdirs() }

// ---------------------------------------------------------------------------
// osmdroid 設定
// ---------------------------------------------------------------------------

internal fun configureOsmdroid(context: Context, dataDir: File) {
    val tileDir = File(dataDir, "tiles").apply { mkdirs() }
    SqlTileWriter.setCleanupOnStart(false)
    Configuration.getInstance().apply {
        load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        osmdroidBasePath = dataDir
        osmdroidTileCache = tileDir
        userAgentValue = "CycleMap/1.0 (Android; cycling navigator; personal use)"

        // ---------------------------------------------------------------
        // スレッド数最適化
        // ダウンロードスレッドを絞ることで、osmdroidの内部FIFOキューが
        // 画面中心に近いタイルを先に処理できる（多すぎると帯域を無駄に使う）
        // ファイルシステムスレッドもSQLiteのシリアルI/Oに合わせて絞る
        // ---------------------------------------------------------------
        tileDownloadThreads = 2          // 6→2: 画面中心優先、帯域集中
        tileFileSystemThreads = 4        // 8→4: SQLite読み取りの競合を減らす
        tileDownloadMaxQueueSize = 40    // キューを短くして古いリクエストをドロップしやすく
        tileFileSystemMaxQueueSize = 40

        // ---------------------------------------------------------------
        // メモリキャッシュ拡大
        // 256px タイル × 4byte/px = 256KB/枚。
        // 画面が約20枚 + ズーム前後 + オーバーシュートを考慮して512枚確保。
        // これにより同じ領域を再スクロールしたときにディスクI/Oが不要になる。
        // ---------------------------------------------------------------
        cacheMapTileCount = 512   // BUG-07 fix: Short不要。osmdroidのsetterはint受付。
        cacheMapTileOvershoot = 8  // BUG-07 fix: Short不要。

        expirationOverrideDuration = 30L * 24 * 60 * 60 * 1000
        tileFileSystemCacheMaxBytes = 800L * 1024 * 1024
        tileFileSystemCacheTrimBytes = 700L * 1024 * 1024
        isMapViewHardwareAccelerated = true
    }
}

// ---------------------------------------------------------------------------
// 地理計算ユーティリティ
// ---------------------------------------------------------------------------

/** ハーバーサイン距離計算（メートル） */
internal fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon / 2).let { it * it }
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

/** 2点間の初方位 (北=0°・時計回り)。連続GPS fixからの移動方向推定用。 */
internal fun initialBearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dLambda = Math.toRadians(lon2 - lon1)
    val y = Math.sin(dLambda) * Math.cos(phi2)
    val x = Math.cos(phi1) * Math.sin(phi2) -
        Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda)
    return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
}

/** カテゴリ一覧 (近傍POI) 検索のbbox半辺 (m)。他県行の混入を防ぐための事前絞り。 */
internal const val NEARBY_CATEGORY_BBOX_HALF_M = 10_000.0

/**
 * 近傍検索用のbbox引数 (南緯, 北緯, 西経, 東経) を文字列化して返す。
 * 換算は逆ジオコーディング (ReverseGeocoder) と同一。
 */
internal fun nearbyBboxArgs(latitude: Double, longitude: Double, halfMeters: Double): List<String> {
    val latDelta = halfMeters / 111_320.0
    val lonDelta = halfMeters / (111_320.0 * Math.cos(Math.toRadians(latitude)).coerceAtLeast(0.2))
    return listOf(
        (latitude - latDelta).toString(),
        (latitude + latDelta).toString(),
        (longitude - lonDelta).toString(),
        (longitude + lonDelta).toString(),
    )
}

/** 近傍順ソート用の近似距離 ORDER BY 句 (等距円筒近似。厳密値はKotlin側で再計算)。 */
internal const val NEARBY_ORDER_BY =
    "ORDER BY ((lat - ?) * (lat - ?) + ((lon - ?) * ?) * ((lon - ?) * ?))"

/** [NEARBY_ORDER_BY] の引数 (lat, lat, lon, cosLat, lon, cosLat)。 */
internal fun nearbyOrderArgs(latitude: Double, longitude: Double): List<String> {
    val cosLat = Math.cos(Math.toRadians(latitude)).toString()
    val lat = latitude.toString()
    val lon = longitude.toString()
    return listOf(lat, lat, lon, cosLat, lon, cosLat)
}

// ---------------------------------------------------------------------------
// 表示フォーマット
// ---------------------------------------------------------------------------

/** 距離表示（1km以上はkm、未満はm）。BUG-01対策として String.format(Locale, ...) 形式を使う。 */
internal fun formatRemaining(meters: Double): String =
    if (meters >= 1000.0) {
        String.format(Locale.US, "%.2f km", meters / 1000.0)
    } else {
        String.format(Locale.US, "%.0f m", meters.coerceAtLeast(0.0))
    }

/** 所要時間表示。「1時間5分」「12分」「まもなく」「--」。 */
internal fun formatDuration(seconds: Double): String {
    if (!seconds.isFinite()) return "--"
    val minutes = (seconds / 60).roundToInt()
    if (minutes < 1) return "まもなく"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) "${hours}時間${rest}分" else "${minutes}分"
}

/** 到着予想時刻表示（HH:mm）。算出不能時は \"--:--\"。 */
internal fun formatEta(epochMillis: Long?): String {
    if (epochMillis == null) return "--:--"
    return java.text.SimpleDateFormat("HH:mm", Locale.JAPAN).format(java.util.Date(epochMillis))
}
