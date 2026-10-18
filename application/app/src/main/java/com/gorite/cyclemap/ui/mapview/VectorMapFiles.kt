package com.gorite.cyclemap.ui.mapview

import android.content.Context
import com.gorite.cyclemap.utils.cycleMapDataDir
import com.gorite.cyclemap.data.OfflinePackageManager
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
 * 端末内の PMTiles ファイルを探すヘルパー関数。
 * 現在地の県に対応するファイルだけを返し、未導入時に他県のタイルへフォールバックしない。
 */
fun resolveOfflinePmtilesPath(context: Context, lat: Double, lon: Double): String? {
    val dataDir = cycleMapDataDir(context)
    return TileSourceSelector.resolveTileForLocation(dataDir, lat, lon)?.absolutePath
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

/**
 * オフラインパッケージファイルの検証を行うヘルパー関数。
 */
fun verifyOfflineDataPackage(file: File, expectedSha256: String? = null): Boolean {
    if (!file.exists() || file.length() == 0L) return false
    return if (!expectedSha256.isNullOrBlank()) {
        OfflinePackageManager.verifyPackageIntegrity(file, expectedSha256)
    } else {
        true
    }
}

@Composable
internal fun OfflineVectorMissingBanner(
    prefectureName: String?,
    modifier: Modifier = Modifier,
) {
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
                text = "${prefectureName ?: "この地域"}の地図データが未導入です。\n端末ストレージ (Documents/CycleMap/ または tiles/) を確認してください。\n他県のデータや外部通信へは自動フォールバックしません。",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
            )
        }
    }
}

