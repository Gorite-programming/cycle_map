package com.gorite.cyclemap

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gorite.cyclemap.data.DownloadStatusManager
import com.gorite.cyclemap.data.MapSourceType
import com.gorite.cyclemap.data.Prefecture
import com.gorite.cyclemap.data.PrefectureData
import com.gorite.cyclemap.data.TileProgress
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AreaDownloadConfirmDialog(
    context: Context,
    areaBounds: BoundingBox,
    isDownloading: Boolean,
    onStartDownload: (MapSourceType) -> Unit,
    onDismiss: () -> Unit,
) {
    val areaTileCount = remember(areaBounds) {
        PrefectureData.calculateTileCount(areaBounds, minZoom = 15, maxZoom = 16)
    }
    val areaSizeMb = remember(areaTileCount) { PrefectureData.estimateSizeMb(areaTileCount) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("エリアダウンロード (z15-z16)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("選択エリアの高解像度タイルをダウンロードします。")
                Text("ズームレベル: 15〜16（高解像度）")
                Text("タイル枚数: 約 $areaTileCount 枚")
                Text("推定サイズ: 約 ${"%.1f".format(Locale.US, areaSizeMb)} MB")
                HorizontalDivider()
                Text("ダウンロードする地図の種類を選択してください:", fontWeight = FontWeight.Medium)
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDownloading,
                    onClick = { onStartDownload(MapSourceType.GSI) },
                ) {
                    Text("地理院地図（標準）をDL")
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDownloading,
                    onClick = { onStartDownload(MapSourceType.OSM) },
                ) {
                    Text("OpenStreetMapをDL")
                }
                TextButton(
                    modifier = Modifier.align(Alignment.End),
                    onClick = onDismiss,
                ) {
                    Text("キャンセル")
                }
            }
        },
    )
}

@Composable
internal fun WaypointOrDestinationDialog(
    spot: GeoPoint,
    onAddWaypoint: () -> Unit,
    onChangeDestination: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("地点の指定", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "現在すでにルートが設定されています。\nこの地点を経由地に追加しますか？それとも新しい目的地にしますか？",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onAddWaypoint) {
                Text("経由地に追加")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("キャンセル")
                }
                TextButton(onClick = onChangeDestination) {
                    Text("目的地に変更")
                }
            }
        },
    )
}

@Composable
internal fun AlreadyDownloadedPrefectureDialog(
    context: Context,
    pref: Prefecture,
    onRedownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val gsiInfo = DownloadStatusManager.getDownloadInfo(context, pref.id, MapSourceType.GSI)
    val osmInfo = DownloadStatusManager.getDownloadInfo(context, pref.id, MapSourceType.OSM)
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ダウンロード状況: ${pref.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("この都道府県の地図データは既に端末に保存されています。")
                HorizontalDivider()
                Text("• 地理院地図: " + if (gsiInfo != null) "保存済 (${dateFormat.format(Date(gsiInfo.first))})" else "未保存")
                Text("• OSM: " + if (osmInfo != null) "保存済 (${dateFormat.format(Date(osmInfo.first))})" else "未保存")
            }
        },
        confirmButton = {
            TextButton(onClick = onRedownload) {
                Text("再ダウンロード / 追加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        },
    )
}

@Composable
internal fun PrefectureDownloadConfirmDialog(
    pref: Prefecture,
    isDownloading: Boolean,
    onDownloadGsi: () -> Unit,
    onDownloadOsm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val totalTiles = remember(pref) { PrefectureData.calculateTileCount(pref.bounds) }
    val sizeMb = remember(totalTiles) { PrefectureData.estimateSizeMb(totalTiles) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${pref.name} の地図ダウンロード") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("保存範囲: ズームレベル 7〜14（低ズーム含む）")
                Text("タイル枚数: 約 ${totalTiles} 枚 (約 %.1f MB)".format(Locale.US, sizeMb))
                HorizontalDivider()
                Text("ダウンロードする地図の種類を選択してください:", fontWeight = FontWeight.Medium)
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDownloading,
                    onClick = onDownloadGsi,
                ) {
                    Text("地理院地図（標準）をDL")
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDownloading,
                    onClick = onDownloadOsm,
                ) {
                    Text("OpenStreetMapをDL")
                }
                TextButton(
                    modifier = Modifier.align(Alignment.End),
                    onClick = onDismiss,
                ) {
                    Text("キャンセル")
                }
            }
        },
    )
}

@Composable
internal fun LicenseDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("地図情報・ライセンス") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "CycleMap App\n" +
                        "© 2026 Gorite. All rights reserved.",
                    fontWeight = FontWeight.Bold,
                )
                HorizontalDivider()
                Text("【地図・POIデータ・音声出典】", fontWeight = FontWeight.Bold)

                Text(
                    "■ OpenStreetMap\n" +
                        "© OpenStreetMap contributors\n" +
                        "ODbL 1.0 (https://www.openstreetmap.org/copyright)",
                )

                Text(
                    "■ 地理院タイル（国土地理院）\n" +
                        "https://maps.gsi.go.jp/development/\n" +
                        "利用規約: https://maps.gsi.go.jp/help/termsofuse.html",
                )

                Text(
                    "■ Overture Maps Foundation (Places)\n" +
                        "Overture Maps Foundation, overturemaps.org\n" +
                        "Contains data from Overture Maps Foundation, licensed under CDLA-Permissive-2.0 (https://overturemaps.org)",
                )

                Text(
                    "■ 国土数値情報（国土交通省）\n" +
                        "「国土数値情報（医療機関データ 第3.0版、学校データ 第2.0版）」（国土交通省）（https://nlftp.mlit.go.jp/ksj/）をもとに加工して作成",
                )

                Text(
                    "■ 音声案内\n" +
                        "VOICEVOX:四国めたん\n" +
                        "https://voicevox.hiroshiba.jp/\n" +
                        "音源利用規約: https://zunko.jp/con_ongen_kiyaku.html",
                )

                Text(
                    "■ ライブラリ\n" +
                        "ベクター地図表示：MapLibre Native Android SDK (BSD 2-Clause License)\n" +
                        "ラスター地図表示：osmdroid (Apache License 2.0)\n" +
                        "位置情報：Google Play services Location",
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("【MapLibre Native Android SDK ライセンス】", fontWeight = FontWeight.Bold)
                Text(
                    "Copyright (c) MapLibre contributors\n\n" +
                        "Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:\n\n" +
                        "1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.\n\n" +
                        "2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.",
                    style = MaterialTheme.typography.bodySmall,
                )

                HorizontalDivider()
                Text("【CDLA-Permissive-2.0 全文】", fontWeight = FontWeight.Bold)
                Text(
                    "Community Data License Agreement – Permissive – Version 2.0\n\n" +
                        "This is the Community Data License Agreement – Permissive, Version 2.0 (the “agreement”). Data Provider(s) and Data Recipient(s) agree as follows:\n\n" +
                        "1. Provision of the Data.\n" +
                        "1.1. A Data Recipient may use, modify, and share the Data made available by Data Provider(s) under this agreement if that Data Recipient follows the terms of this agreement.\n" +
                        "1.2. This agreement does not impose any restriction on a Data Recipient’s use, modification, or sharing of any portions of the Data that are in the public domain or that may be used, modified, or shared under any other legal exception or limitation.\n\n" +
                        "2. Conditions for Sharing Data.\n" +
                        "2.1. A Data Recipient may share Data, with or without modifications, so long as the Data Recipient makes available the text of this agreement with the shared Data.\n\n" +
                        "3. No Restrictions on Results.\n" +
                        "3.1. This agreement does not impose any restriction or obligations with respect to the use, modification, or sharing of Results.\n\n" +
                        "4. No Warranty; Limitation of Liability.\n" +
                        "4.1. All Data Recipients receive the Data subject to the following terms: THE DATA IS PROVIDED ON AN “AS IS” BASIS, WITHOUT REPRESENTATIONS, WARRANTIES OR CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED INCLUDING, WITHOUT LIMITATION, ANY WARRANTIES OR CONDITIONS OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. NO DATA PROVIDER SHALL HAVE ANY LIABILITY FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING WITHOUT LIMITATION LOST PROFITS), HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE DATA OR RESULTS, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.\n\n" +
                        "5. Definitions.\n" +
                        "5.1. “Data” means the material received by a Data Recipient under this agreement.\n" +
                        "5.2. “Data Provider” means any person who is the source of Data provided under this agreement and in reliance on a Data Recipient’s agreement to its terms.\n" +
                        "5.3. “Data Recipient” means any person who receives Data directly or indirectly from a Data Provider under this agreement.\n" +
                        "5.4. “Results” means any work, analysis, data, or product that a Data Recipient creates using the Data, provided that such work, analysis, data, or product does not include more than a de minimis portion of the Data.\n" +
                        "5.5. “Use” means using, copying, modifying, preparing derivative works, or otherwise exploiting the Data.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

@Composable
internal fun VersionDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("バージョン情報") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("CycleMap ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                Text("コミット: ${BuildConfig.GIT_HASH}")
                Text("ビルド日時: ${BuildConfig.BUILD_TIME}")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}
