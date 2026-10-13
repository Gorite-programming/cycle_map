package com.gorite.cyclemap

import android.content.Context
import com.gorite.cyclemap.data.SearchDbSelection
import com.gorite.cyclemap.data.SearchDbSelector
import com.gorite.cyclemap.data.TileSourceSelection
import com.gorite.cyclemap.data.TileSourceSelector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * 広島エリアのオフラインベクターデータ有効範囲 (BBox)
 */
internal object HiroshimaVectorBounds {
    const val MIN_LAT = 34.15
    const val MAX_LAT = 34.65
    const val MIN_LON = 132.15
    const val MAX_LON = 132.65
    fun contains(lat: Double, lon: Double): Boolean =
        lat in MIN_LAT..MAX_LAT && lon in MIN_LON..MAX_LON
}


/**
 * 端末内の PMTiles ファイルを探すヘルパー関数。
 * CycleMap のデータディレクトリ、アプリ個別ファイル領域、テストアプリの領域を順次探索する。
 */
fun resolveOfflinePmtilesPath(context: Context, lat: Double? = null, lon: Double? = null): String? {
    val dataDir = cycleMapDataDir(context)
    if (lat != null && lon != null) {
        val sel = TileSourceSelector.select(lat, lon)
        if (sel is TileSourceSelection.Available) {
            val f = TileSourceSelector.resolveTileFile(dataDir, sel.fileName)
            if (f != null && f.exists() && f.length() > 0) return f.absolutePath
        }
    }
    val fallback = TileSourceSelector.findFirstAvailableTile(dataDir)
        ?: TileSourceSelector.resolveTileFile(dataDir, "Hiroshima_osm.pmtiles")
    return fallback?.takeIf { it.exists() && it.length() > 0 }?.absolutePath
}

/**
 * 端末内の検索DBファイル (.search.db) を探すヘルパー関数。
 * 指定座標に対応する県DBを優先し、フォールバックとして Hiroshima.search.db や配置済みDBを探索する。
 */
fun resolveSearchDbFile(context: Context, lat: Double, lon: Double): File? {
    val dataDir = cycleMapDataDir(context)
    val sel = SearchDbSelector.select(lat, lon)
    if (sel is SearchDbSelection.Available) {
        val f = SearchDbSelector.resolveDbFile(dataDir, sel.fileName)
        if (f != null && f.exists() && f.length() > 0) return f
    }
    val candidates = listOf(
        SearchDbSelector.findFirstAvailableDb(dataDir),
        File(dataDir, "Hiroshima.search.db"),
        File(context.getExternalFilesDir("Documents/CycleMap"), "Hiroshima.search.db"),
        File("/sdcard/Android/data/com.gorite.cyclemap/files/Documents/CycleMap/Hiroshima.search.db"),
        File("/storage/emulated/0/Android/data/com.gorite.cyclemap/files/Documents/CycleMap/Hiroshima.search.db"),
        File("/sdcard/Android/data/com.gorite.cyclemap.test/files/Documents/CycleMap/Hiroshima.search.db"),
        File("/storage/emulated/0/Android/data/com.gorite.cyclemap.test/files/Documents/CycleMap/Hiroshima.search.db"),
    )
    for (candidate in candidates) {
        if (candidate != null && candidate.exists() && candidate.length() > 0) {
            return candidate
        }
    }
    return null
}

@Composable
internal fun OfflineVectorMissingBanner(modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 152.dp, start = 16.dp, end = 16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "⚠ オフラインベクターデータ未検出",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Hiroshima_osm.pmtiles が配置されていません。\n端末ストレージ (Documents/CycleMap/tiles/ 等) を確認してください。\n※完全オフライン運用の安全のため、外部通信への自動フォールバックは行いません。",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
            )
        }
    }
}


