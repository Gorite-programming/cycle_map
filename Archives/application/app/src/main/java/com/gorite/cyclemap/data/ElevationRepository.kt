package com.gorite.cyclemap.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.gorite.cyclemap.ui.cycling.ElevationSample
import com.gorite.cyclemap.ui.cycling.buildRouteElevationProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

/**
 * 国土地理院 標高タイル (DEM10B PNG) のキャッシュおよび標高算出リポジトリ。
 * タイル仕様: https://maps.gsi.go.jp/development/demtile.html
 * ズームレベル14のPNGタイルからピクセルのRGB値をデコードして正確な標高 (m) を算出する。
 */
class ElevationRepository(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "dem_tiles").apply { mkdirs() }
    private val memoryCache = LruCache<String, Bitmap>(64)

    /**
     * 指定された緯度・経度の標高 (m) を取得する。
     * キャッシュにあれば同期的に即座に返し、なければnullを返す。
     */
    fun getCachedElevation(lat: Double, lon: Double): Double? {
        val (tx, ty) = toTileCoord(lat, lon, DEM_ZOOM)
        val key = "${DEM_ZOOM}_${tx}_${ty}"
        val bitmap = getTileBitmap(tx, ty, downloadIfMissing = false) ?: return null
        return decodeElevationFromBitmap(bitmap, lat, lon, tx, ty, DEM_ZOOM)
    }

    /**
     * ルート全体の標高プロファイルを非同期に実データ（国土地理院DEM10B）から算出する。
     * 未キャッシュタイルはバックグラウンドでダウンロード・保存する。
     * 完全オフライン等で取得できない区間は地形モデルでスムーズに補間する。
     */
    suspend fun getRouteElevationProfile(
        coordinates: List<Pair<Double, Double>>,
        totalDistanceM: Double,
    ): List<ElevationSample> = withContext(Dispatchers.IO) {
        if (coordinates.size < 2 || totalDistanceM <= 0.0) {
            return@withContext buildRouteElevationProfile(coordinates, totalDistanceM)
        }

        val sampleCount = (totalDistanceM / 50.0).roundToInt().coerceIn(30, 300)
        val step = totalDistanceM / sampleCount

        // 累積距離配列
        val cumDists = DoubleArray(coordinates.size)
        var dAcc = 0.0
        for (i in 1 until coordinates.size) {
            val (lat1, lon1) = coordinates[i - 1]
            val (lat2, lon2) = coordinates[i]
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val clampedA = a.coerceIn(0.0, 1.0)
            val d = 6371000.0 * 2 * atan2(sqrt(clampedA), sqrt(1.0 - clampedA))
            dAcc += d
            cumDists[i] = dAcc
        }

        // ルート上の必要なDEMタイルのインデックスを事前収集
        val tileCoords = mutableSetOf<Pair<Int, Int>>()
        for (coord in coordinates) {
            tileCoords.add(toTileCoord(coord.first, coord.second, DEM_ZOOM))
        }

        // バックグラウンドでタイル取得（上限30枚で過剰ダウンロードを防止）
        tileCoords.take(30).forEach { (tx, ty) ->
            getTileBitmap(tx, ty, downloadIfMissing = true)
        }

        val fallbackProfile = buildRouteElevationProfile(coordinates, totalDistanceM)
        val result = ArrayList<ElevationSample>(sampleCount + 1)
        var coordIdx = 0

        for (s in 0..sampleCount) {
            val dist = s * step
            while (coordIdx < cumDists.size - 1 && cumDists[coordIdx + 1] < dist) {
                coordIdx++
            }
            val (lat, lon) = coordinates[coordIdx]
            val (tx, ty) = toTileCoord(lat, lon, DEM_ZOOM)
            val bitmap = getTileBitmap(tx, ty, downloadIfMissing = false)
            val realElev = if (bitmap != null) {
                decodeElevationFromBitmap(bitmap, lat, lon, tx, ty, DEM_ZOOM)
            } else null

            val finalElev = realElev ?: fallbackProfile.getOrNull(s)?.elevationM ?: 10.0
            result.add(ElevationSample(dist, finalElev.coerceAtLeast(0.0)))
        }

        result
    }

    private fun getTileBitmap(tx: Int, ty: Int, downloadIfMissing: Boolean): Bitmap? {
        val key = "${DEM_ZOOM}_${tx}_${ty}"
        memoryCache.get(key)?.let { return it }

        val diskFile = File(cacheDir, "$key.png")
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val bmp = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bmp != null) {
                    memoryCache.put(key, bmp)
                    return bmp
                }
            } catch (e: Exception) {
                diskFile.delete()
            }
        }

        if (!downloadIfMissing) return null

        // 国土地理院 DEM10B タイルからダウンロード
        val urlStr = "https://cyberjapandata.gsi.go.jp/xyz/dem10b_png/$DEM_ZOOM/$tx/$ty.png"
        try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 4000
            conn.setRequestProperty("User-Agent", "CycleMap/1.0 (Android; Offline Cycling Navigation)")
            if (conn.responseCode == 200) {
                conn.inputStream.use { input ->
                    val bytes = input.readBytes()
                    FileOutputStream(diskFile).use { it.write(bytes) }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        memoryCache.put(key, bmp)
                        return bmp
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "DEM tile download skipped or failed: $key (${e.message})")
        }
        return null
    }

    private fun decodeElevationFromBitmap(
        bmp: Bitmap,
        lat: Double,
        lon: Double,
        tileX: Int,
        tileY: Int,
        zoom: Int,
    ): Double? {
        val n = 1 shl zoom
        val worldX = (lon + 180.0) / 360.0 * n
        val latRad = Math.toRadians(lat)
        val worldY = (1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * n

        val px = ((worldX - tileX) * 256.0).toInt().coerceIn(0, 255)
        val py = ((worldY - tileY) * 256.0).toInt().coerceIn(0, 255)

        val pixel = bmp.getPixel(px, py)
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF

        // 地理院DEM PNG仕様:
        // x = 2^16 * R + 2^8 * G + B
        // x < 2^23: h = x * 0.01
        // x = 2^23: 無効値 (海面・データなし)
        // x > 2^23: h = (x - 2^24) * 0.01
        val xVal = (r shl 16) or (g shl 8) or b
        return when {
            xVal == 0x800000 -> 0.0 // 海面
            xVal < 0x800000 -> xVal * 0.01
            else -> (xVal - 0x1000000) * 0.01
        }
    }

    private fun toTileCoord(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val n = 1 shl zoom
        val tx = ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = Math.toRadians(lat)
        val ty = ((1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
        return Pair(tx, ty)
    }

    companion object {
        private const val TAG = "ElevationRepo"
        private const val DEM_ZOOM = 14

        @Volatile
        private var instance: ElevationRepository? = null

        fun getInstance(context: Context): ElevationRepository {
            return instance ?: synchronized(this) {
                instance ?: ElevationRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
