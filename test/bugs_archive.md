# CycleMap バグレポート（修正済みアーカイブ）

> 初版調査: 2026-09-21  
> 全面再調査・更新: 2026-09-26 / 2026-09-27  
> 対象: 修正・対応完了したバグ（計41件）のアーカイブ記録
> （未修正・残存しているバグについては [bugs.md](bugs.md) を参照）

---

## 0. 修正完了サマリー（計41件）

| ID | 状態 | 危険度 | ファイル | タイトル・概要 |
|----|------|--------|----------|----------------|
| BUG-01 | ✅ | 🔴 Critical | MainActivity.kt | `String.format` に Locale を誤渡しで距離表示が壊れる |
| BUG-02 | ✅ | 🔴 Critical | MainActivity.kt | `LaunchedEffect` のキーに `locationMarker` が欠落 |
| BUG-14 | 🟡緩和 | 🔴 Critical | LazyMappedRouting.kt | `readEdge()` の `Long→Int` キャストで 2GB 超ファイルがクラッシュ |
| BUG-22 | ✅ | 🟠 High | ReverseGeocoder.kt | bbox重複＋狭域優先で広島東部が岡山県に誤判定 |
| BUG-23 | ✅ | 🟠 High | MainActivity.kt | 先読み切替が計算中jobを殺しフラグ固着 |
| BUG-31 | ✅ | 🟠 High | MainActivity.kt | スポットパネルが `search.db` をハードコード |
| BUG-36 | ✅ | 🟠 High | TurnClassifier.kt | 無制限中立ブリッジが偽 ROUNDABOUT を作る |
| BUG-03 | ✅ | 🟠 High | MainActivity.kt | `startRouteToDestination` が古い `mapView` を参照 |
| BUG-04 | ✅ | 🟠 High | MainActivity.kt | `DisposableEffect` でルート計算中に `close()` が呼ばれクラッシュ |
| BUG-08 | ✅ | 🟠 High | MainActivity.kt | FTS5 JOIN の WHERE 句が LIKE フォールバックで不正に流用される |
| BUG-10 | ✅ | 🟠 High | LocationTrackingService.kt | `stopRecording()` の `recording=false` タイミングでデータ競合 |
| BUG-15 | ✅ | 🟠 High | MappedRouting.kt | `LongIntIndex` で `Long.MIN_VALUE` がセンチネルと衝突 |
| BUG-17 | ✅ | 🟠 High | OsmImporter.kt | `oneway=-1` エッジの `oneWay` フラグが矛盾 |
| BUG-64 | ✅ | 🟠 High | MainActivity.kt | 地図上POI常時表示が `search.db` をハードコードし県選択を無視 |
| BUG-65 | ✅ | 🟠 High | build_prefectures / Prefectures.kt | パイプライン生成ファイル名とアプリ要求ファイル名の命名不一致 |
| BUG-05 | ✅ | 🟡 Medium | LocationTrackingService.kt | 速度フィルタ上限 216 km/h はサイクリングに不適切 |
| BUG-06 | ✅ | 🟡 Medium | MainActivity.kt | `isDownloading` フラグの重複リセット競合 |
| BUG-11 | ✅ | 🟡 Medium | MainActivity.kt | 速度計算・スムージングが2箇所で重複 |
| BUG-12 | ✅ | 🟡 Medium | GpxRecorder.kt | 書き込み失敗時に壊れた GPX ファイルが残る |
| BUG-13 | ✅ | 🟡 Medium | GpxRecorder.kt | `SimpleDateFormat` がスレッドセーフでない |
| BUG-16 | 🟡緩和 | 🟡 Medium | LazyMappedRouting.kt | `nearestNodeIndex` が O(n) 線形探索で遅延 |
| BUG-24 | ✅ | 🟡 Medium | MainActivity.kt | `view-not-ready` パスがフラグ・参照・警告を残さない |
| BUG-25 | ✅ | 🟡 Medium | MainActivity.kt | 二重切替レース（フラグのセットが非同期） |
| BUG-32 | ✅ | 🟡 Medium | MainActivity.kt | 検索ダイアログの初回が古い位置で他県DBを引く |
| BUG-37 | ✅ | 🟡 Medium | ReverseGeocoder.kt | 行政界ポリゴン判定の導入により町・市・区のキメラを解消 |
| BUG-50 | ✅ | 🟡 Medium | LocationTrackingService.kt | 静止判定が走行中にも速度0を強制 |
| BUG-51 | ✅ | 🟡 Medium | LocationTrackingService.kt | 権限ガードなしで `SecurityException` |
| BUG-52 | ✅ | 🟡 Medium | LocationTrackingService.kt | 停止時の同期I/O・例外処理なし |
| BUG-53 | ✅ | 🟡 Medium | MainActivity.kt | ベンチマーク例外で実行中フラグが永久固着 |
| BUG-66 | ✅ | 🟡 Medium | MainActivity.kt | グラフ切替中のルート消去で保留リクエストが残留し再開 |
| BUG-67 | ✅ | 🟡 Medium | build_search_db.py / OsmImporter | `places` に (lat, lon) インデックスがなくフルテーブルスキャン |
| BUG-09 | ✅ | 🟢 Low | MainActivity.kt | `SearchCategory.values()` を deprecated な API で呼び出し |
| BUG-33 | ✅ | 🟢 Low | MainActivity.kt | 内部番兵名がユーザー向け文言に漏れる |
| BUG-34 | ✅ | 🟢 Low | MainActivity.kt | `LIMIT` 先・ソート後で最近傍が欠落 |
| BUG-35 | ✅ | 🟢 Low | MainActivity.kt | デッド条件式（両腕同一） |
| BUG-60 | ✅ | 🟢 Low | DeveloperOptionsScreen.kt | `HsaMode.values()` |
| BUG-61 | ✅ | 🟢 Low | DeveloperOptionsScreen.kt | システムサービス null 未検査 |
| BUG-63 | ✅ | 🟡 Medium | LocationTrackingService.kt | `onDestroy` で記録破棄 |
| BUG-68 | ✅ | 🟢 Low | MainActivity.kt | `runRouteCalculation` での `if (graph == null)` 到達不能デッドコード |
| BUG-69 | ✅ | 🟢 Low | MainActivity.kt | エリアDL開始時に `selectedAreaBounds` がクリアされず残存 |
| BUG-70 | ✅ | 🟢 Low | MainActivity.kt | ベンチマークログ保存時のI/O例外未処理と親ディレクトリ未検査 |

---

## 修正済みバグ詳細

## 1. `MainActivity.kt`

### BUG-01 🔴 Critical — `String.format` に `Locale` を誤渡し（距離表示が壊れる）

> ✅ 修正済み（2026-09-26 再確認）。`String.format(Locale.US, ...)` 形式に統一済み。
> 補足：旧形式 `"…".format(Locale.US, x)` も Kotlin 2.0.21 では正常動作することを JVM 実実行で確認
> （`"%.1f".format(Locale.US, 1.5)` → `"1.5"`、例外なし。実機の速度表示でも裏付け）。
> 残存する旧形式の呼び出し（MainActivity 6箇所・ElevationProfileChart・GpxRecorder・DeveloperOptionsScreen）は
> **バグではない**。再報告不要。

**ファイル:** `app/src/main/java/com/gorite/cyclemap/MainActivity.kt`

**該当箇所:**
```kotlin
// RouteSummary 表示部
val distStr = if (summary.distanceMeters >= 1000.0) {
    "%.2f km".format(Locale.US, summary.distanceMeters / 1000.0)
} else {
    "%.0f m".format(Locale.US, summary.distanceMeters)
}
```

**危険性:**  
Kotlin の `String.format(vararg args: Any?)` 拡張は `Locale` を受け取らない。`Locale.US` が `%` フォーマット引数の第1引数として扱われ、`summary.distanceMeters / 1000.0` は完全に無視される。結果として距離が `0.00 km` や `Locale` の `toString()` になりルート距離が正しく表示されない。

**同じ問題が存在する箇所:**
- `DestinationSearchDialog` 内の `distStr`（検索結果の距離表示）

**修正方法:**
```kotlin
// 正しい書き方
val distStr = if (summary.distanceMeters >= 1000.0) {
    String.format(Locale.US, "%.2f km", summary.distanceMeters / 1000.0)
} else {
    String.format(Locale.US, "%.0f m", summary.distanceMeters)
}
```

---

---

### BUG-02 🔴 Critical — `LaunchedEffect` のキーに `locationMarker` が欠落（古いマーカーを更新し続ける）

> ✅ 修正済み（2026-09-26 再確認）。現行キーは `LaunchedEffect(currentLocation, mapView, followLocation, locationMarker, accuracyCircle)`。

**ファイル:** `app/src/main/java/com/gorite/cyclemap/MainActivity.kt`

**該当箇所:**
```kotlin
LaunchedEffect(currentLocation, mapView, followLocation) {
    val targetLocation = currentLocation ?: return@LaunchedEffect
    val marker = locationMarker ?: return@LaunchedEffect  // ← キーに含まれていない
    ...
    while (true) {
        marker.position = point   // 古い marker を操作し続ける
        ...
    }
}
```

**危険性:**  
`locationMarker` が再生成されても `LaunchedEffect` は再起動されず、破棄済み・古いマーカーオブジェクトへ書き続ける。地図上の現在地マーカーが正しい位置に追従しなくなる（ゾンビアニメーション）。

**修正方法:**
```kotlin
LaunchedEffect(currentLocation, mapView, followLocation, locationMarker) {
```

---

---

### BUG-03 🟠 High — `startRouteToDestination` がクロージャで古い `mapView` を参照する

> ✅ 修正済み（2026-09-26 再確認）。`latestMapView by rememberUpdatedState(mapView)` を使用。

**ファイル:** `app/src/main/java/com/gorite/cyclemap/MainActivity.kt`

**該当箇所:**
```kotlin
val startRouteToDestination: (GeoPoint) -> Unit = route@{ point ->
    ...
    (context as? ComponentActivity)?.runOnUiThread {
        val view = mapView ?: return@runOnUiThread  // ← ラムダ生成時の古い mapView
```

**危険性:**  
`startRouteToDestination` ラムダは `remember` なしで定義されており、Recompose のたびに新しいラムダが生成される。しかし `AndroidView` の `factory` ブロック内から `MapEventsOverlay` に登録された参照は最初の生成時のラムダのままで、Recompose 後の新しいラムダに更新されない。このため `mapView` が null のまま古いクロージャが使われ、長押しルート設定が動作しないケースがある。`latestGraph` / `latestLocation` は `rememberUpdatedState` で対処されているが、`mapView` は対処されていない。

**修正方法:**
```kotlin
val latestMapView by rememberUpdatedState(mapView)
// startRouteToDestination 内で mapView の代わりに latestMapView を使う
```

---

---

### BUG-04 🟠 High — `DisposableEffect(mappedGraph)` でグラフを `close()` するとルート計算中にクラッシュ

> ✅ 修正済み（2026-09-26 再確認）。`cancelAndJoin()`＋`graphToDispose` による旧インスタンスclose。
> 残差：`join(2_000L)` タイムアウト後にスレッドが生き残ると理論上再発 → BUG-30 に分離記録。

**ファイル:** `app/src/main/java/com/gorite/cyclemap/MainActivity.kt`

**該当箇所:**
```kotlin
DisposableEffect(mappedGraph) {
    onDispose {
        routeJobRef.getAndSet(null)?.cancel()
        graphLock.write { mappedGraph?.close() }  // ← ルート計算スレッドが read ロック保持中にclose可能
    }
}
```

**危険性:**  
`routeJobRef.getAndSet(null)?.cancel()` はスレッドに中断シグナルを送るだけで、実際にスレッドが停止するまで待たない。スレッドが `graphLock.read { }` ブロック内にある間に `mappedGraph?.close()` が `FileChannel` を閉じると、`ByteBuffer.getLong()` などが `ClosedChannelException` または `IllegalStateException` を投げる。

**修正方法:**  
`cancel()` 後にスレッドの終了を `join()` するか、`close()` を `routeJob.thread.join()` の後に行う。または `close()` を `onCleared()` 相当のタイミングに移動する。

---

---

### BUG-05 🟡 Medium — `considerLocation` の速度フィルタ上限が 60 m/s（216 km/h）固定でサイクリングに不適切

> ⏳ 残存（2026-09-26 再確認）。`LocationTrackingService.kt:173` の `> 60.0` は不変。

**ファイル:** `app/src/main/java/com/gorite/cyclemap/tracking/LocationTrackingService.kt`

**該当箇所:**
```kotlin
val isTooFast = calculatedSpeed > 60.0  // 60 m/s = 216 km/h
```

**危険性:**  
自転車で通常ありえない速度（60 m/s）のフィルタは実質無効に等しく、GPSジャンプによる異常座標が記録に混入する。また `ServiceLocationUpdates` 側でも独立してスムージングを行っており速度計算が二重化している（BUG-11 参照）。

**修正方法:**  
自転車の現実的な上限（例: 25 m/s = 90 km/h）に変更し、速度計算ロジックをサービス側に一本化する。

---

---

### BUG-06 🟡 Medium — `isDownloading` フラグが非 UI スレッドから直接変更される

> ⏳ 残存（2026-09-26 再確認）。`onProgress` と `onFinished` の重複リセットが3箇所以上のDL開始箇所に拡散しているのを確認。

**ファイル:** `app/src/main/java/com/gorite/cyclemap/MainActivity.kt`

**該当箇所:**
```kotlin
startPrefectureDownload(
    ...
    onProgress = { progress ->
        (context as? ComponentActivity)?.runOnUiThread {
            currentTileProgress = progress
            if (progress.completed >= progress.total) isDownloading = false
        }
    },
    onFinished = {
        (context as? ComponentActivity)?.runOnUiThread {
            isDownloading = false
        }
    },
)
```

**危険性:**  
`onProgress` と `onFinished` の両方で `isDownloading = false` をセットしている（重複）。さらにダウンロード完了時に `onProgress` の `completed >= total` 判定が先に `false` にし、直後に `onFinished` でも `false` にするが、その間にユーザーが再度ダウンロードを開始した場合に `isDownloading` が上書きされて二重ダウンロードが発生する可能性がある。

---

---

## 2. `searchPlaces` 関数 / `DestinationSearchDialog`（MainActivity.kt 内）

### BUG-08 🟠 High — FTS5 JOIN クエリで `catWhereForFts` が `p.category` 参照のまま LIKE 補完に流用される

> ✅ 修正済み（2026-09-26 再確認）。カテゴリ句は FTS用・plain用に分離済み。

**ファイル:** `app/src/main/java/com/gorite/cyclemap/MainActivity.kt`

**該当箇所:**
```kotlin
val catWhereForFts = categoryWhere   // "AND (p.category LIKE ?)"

// FTS クエリ（OK: p はJOINされている）
db.rawQuery("""
    SELECT p.name, p.category, p.lat, p.lon
    FROM places_fts f JOIN places p ON p.id = f.rowid
    WHERE places_fts MATCH ? $catWhereForFts
    LIMIT ?
""".trimIndent(), ...)

// LIKE フォールバック（NG: p というエイリアスが存在しない）
val likeWhere = if (categoryWhere.isEmpty()) "" else
    categoryWhere.replace("p.category", "category")
```

**危険性:**  
`replace("p.category", "category")` の置換は機械的な文字列置換であり、将来 WHERE 句の構造が変わった場合に置換が部分的にしか効かなくなるリスクがある。また現在も `LIKE` フォールバックが `FTS5 未対応かつ ftsSucceeded=false` のパスで動作するとき、`categoryArgs` に含まれる末尾 `%` の付き方（`if (it.endsWith(":")) "$it%" else it`）が FTS5 パスと異なり、カテゴリ完全一致しか取れない場合がある。

---

---

### BUG-09 🟢 Low — `SearchCategory.values()` を deprecated な API で呼び出している

> ✅ 修正済み（2026-09-26 再確認）。`entries` 使用に統一。ただし同種の `HsaMode.values()` が DeveloperOptionsScreen に残存 → BUG-47。

**ファイル:** `app/src/main/java/com/gorite/cyclemap/MainActivity.kt`

**該当箇所:**
```kotlin
SearchCategory.values().indexOf(selectedCategory)
SearchCategory.values().forEachIndexed { index, cat -> ... }
```

**危険性:**  
Kotlin 1.9 以降 `enumValues()` / `entries` が推奨される。`values()` は毎回新しい配列を生成するためパフォーマンスロスがある（UI ごとに Recompose されるため、小さいが無駄なアロケーションが繰り返される）。

---

---

## 3. `LocationTrackingService.kt`

### BUG-10 🟠 High — `stopRecording()` でファイル保存前に `recording = false` をセットするとデータ競合

> ✅ 修正済み（2026-09-26 再確認）。snapshot→clear の順序に修正済み（同一スレッド動作のため残差なし）。

**ファイル:** `app/src/main/java/com/gorite/cyclemap/tracking/LocationTrackingService.kt`

**該当箇所:**
```kotlin
private fun stopRecording() {
    if (!recording) return
    recording = false          // ← ここで false にする
    val snapshot = recorder    // recorder は null になる前に読む
    recorder = null
    ...
    val output = snapshot?.writeToFile(...)  // ← IO は同スレッドなので安全だが…
```

**危険性:**  
`recording = false` にした直後から `onStartCommand` で `ACTION_START_RECORDING` を再度受け取ると `startRecording()` が呼ばれ `recorder` に新しい `GpxRecorder` が代入される。しかし直後に `recorder = null` が実行されてしまい、新しい記録セッションの `recorder` が即座に破棄される。

---

---

### BUG-11 🟡 Medium — 速度計算・スムージングが `LocationTrackingService` と `ServiceLocationUpdates` の2箇所で重複

> ✅ 修正済み（2026-09-26 再確認）。UI側は `newLocation.speed` を素通しし二重平滑化は解消。

**ファイル:** `MainActivity.kt` の `ServiceLocationUpdates` と `LocationTrackingService.kt` の `considerLocation`

**該当箇所:**  
- `considerLocation()` 内: `calculatedSpeed > 60.0` によるフィルタリング
- `ServiceLocationUpdates` 内: `smoothedSpeed = smoothedSpeed * 0.7 + rawSpeed * 0.3`

**危険性:**  
サービス側でフィルタリング済みの位置情報がUIに届き、UI側で再度速度計算を行う。サービスが `newLocation.hasSpeed()` の値を使い、UI側は `distanceTo` を使うため、GPS がハードウェア速度を返す場合とそうでない場合で表示値が異なる。スムージングが2段かかり、実際の速度より過度に遅延した表示になる。

---

---

## 4. `GpxRecorder.kt`

### BUG-12 🟡 Medium — `writeToFile` がエラー時でも空 GPX ファイルを作成する

> ⏳ 残存（2026-09-26 再確認）。非アトミック書き込みのまま。関連：停止時の同期I/O問題 → BUG-36。

**ファイル:** `app/src/main/java/com/gorite/cyclemap/tracking/GpxRecorder.kt`

**該当箇所:**
```kotlin
fun writeToFile(file: File): File {
    file.parentFile?.mkdirs()
    val snapshot = synchronized(points) { points.toList() }
    file.writer().use { it.write(GpxWriter.write(snapshot)) }  // 例外時でも空ファイルが残る
    return file
}
```

**危険性:**  
`GpxWriter.write()` または `file.writer()` が途中で例外を投げた場合、`file` は部分的に書き込まれた壊れた GPX ファイルとして残る（`use` がストリームを閉じるが、書き込み済みのデータは消えない）。その後サービスが `output.absolutePath` を SharedPreferences に保存し、次回起動時に破損ファイルを参照する。

**修正方法:**  
一時ファイルに書き込んでから `rename()` するアトミック書き込みパターンを使う。

---

---

### BUG-13 🟢 Low — `GpxWriter` の `gpxTimeFormat` がスレッドセーフでない

> ⏳ 残存（2026-09-26 再確認）。現状単一スレッドのため未顕在化。

**ファイル:** `app/src/main/java/com/gorite/cyclemap/tracking/GpxRecorder.kt`

**該当箇所:**
```kotlin
object GpxWriter {
    private val gpxTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    fun write(points: List<GpxPoint>): String = buildString {
        ...
        append(gpxTimeFormat.format(Date(p.timestampMillis)))
```

**危険性:**  
`SimpleDateFormat` はスレッドセーフではない。`GpxWriter` は `object` なのでシングルトンだが、`writeToFile` はバックグラウンドスレッド（`LocationTrackingService` のメインルーパー外）から呼ばれる可能性がある。現状は `stopRecording()` がメインスレッドで動くため顕在化していないが、将来コルーチンに移行したとき競合する。

---

---

## 5. `MappedRouting.kt` / `LazyMappedRouting.kt`

### BUG-14 🔴 Critical — `LazyMappedRoadGraph.readEdge()` のオフセット計算が脆弱でバイナリ破損時にクラッシュ

> 🟡 緩和済み（2026-09-26 再確認）。Long演算維持・明示的キャスト・`duplicate()` による共有position非破壊化。
> 残る2GB mmap上限は文書化済みで現行169MBでは無影響 → Critical解除。

**ファイル:** `routing-core/src/main/kotlin/com/gorite/cyclemap/routing/LazyMappedRouting.kt`

**該当箇所:**
```kotlin
private fun readEdge(offset: Long): EdgeRecord {
    val p = offset.toInt()   // ← Long → Int キャスト：ファイルが 2 GB 超でオーバーフロー
    val to = mapped.getLong(p + 8)
    val distance = mapped.getDouble(p + 16)
    val typeStart = p + 24
    val length = mapped.getShort(typeStart).toInt() and 0xffff
    val road = ByteArray(length)
    val saved = mapped.position()
    mapped.position(typeStart + 2); mapped.get(road); mapped.position(saved)
    ...
    var next = (typeStart + 2 + length + 2).toLong()
    val grade = if (mapped.get((typeStart + 2 + length + 1).toInt()).toInt() != 0) {
        val value = mapped.getDouble((typeStart + 2 + length + 2).toInt()).toFloat()
        next += 8
        value
    } else Float.NaN
    return EdgeRecord(to, distance, type, grade, offset + (next - p))  // ← next - p が負になりうる
}
```

**危険性:**  
1. `offset.toInt()` は `offset >= 2^31`（約 2GB）でオーバーフローし負のインデックスになる。現状の yamaguchi.graph（約 169MB）では問題ないが、japan スケールのグラフを使う場合にクラッシュする。
2. `offset + (next - p)` の計算で `next`（`Long`）と `p`（`Int`）の演算順序次第で意図しない値になる可能性がある。
3. `mapped.position(typeStart + 2)` が `ByteBuffer` の共有状態を変更するため、マルチスレッドから呼び出した場合に position が競合する（ただし現状はロック内で使われているため顕在化しにくい）。

---

---

### BUG-15 🟠 High — `LongIntIndex` でキー `Long.MIN_VALUE` が「空」センチネルと衝突する

> ✅ 修正済み（2026-09-26 再確認）。`occupied: BooleanArray` による分離済み。

**ファイル:** `routing-core/src/main/kotlin/com/gorite/cyclemap/routing/MappedRouting.kt`

**該当箇所:**
```kotlin
private class LongIntIndex(expectedSize: Int) {
    init {
        keys = LongArray(capacity) { Long.MIN_VALUE }  // 空スロットのマーカー
        ...
    }
    fun put(key: Long, value: Int) {
        var slot = (key xor (key ushr 33)).toInt() and mask
        while (keys[slot] != Long.MIN_VALUE && keys[slot] != key) ...
        keys[slot] = key  // ← key が Long.MIN_VALUE なら空スロットと区別できない
    }
}
```

**危険性:**  
OSM ノード ID に `Long.MIN_VALUE`（`-9223372036854775808`）が使われた場合（理論上ありうる）、空スロットと区別がつかず `put` がサイレントに無効になり、該当ノードがルート計算から消える。

---

---

### BUG-16 🟡 Medium — `nearestNodeIndex` が O(n) 線形探索でノード数十万規模では応答遅延

> 🟡 緩和済み（2026-09-26 再確認）。2パス＋三角関数なしの事前フィルタ。依然 O(n) のため Medium 維持。

**ファイル:** `routing-core/src/main/kotlin/com/gorite/cyclemap/routing/LazyMappedRouting.kt`

**該当箇所:**
```kotlin
fun nearestNodeIndex(latitude: Double, longitude: Double): Int {
    var best = 0
    var bestDistance = Double.POSITIVE_INFINITY
    for (i in nodeIds.indices) {   // 全ノードを線形探索
        val distance = haversineMeters(latitude, longitude, latitudes[i], longitudes[i])
        if (distance < bestDistance) { bestDistance = distance; best = i }
    }
    return best
}
```

**危険性:**  
yamaguchi.graph のノード数は約数十万。毎回 `haversineMeters`（sin/cos/atan2）を全ノード分計算するため、ルート検索開始時に数百ms〜秒単位のブロッキングが発生する。`graphLock.read` ブロック内で呼ばれているので UI スレッドへの影響はないが、ルート探索スレッドが長時間占有される。k-d ツリーや グリッドインデックスの導入が必要。

---

---

## 6. `OsmImporter.kt`

### BUG-17 🟠 High — `OsmGraphBuilder.build()` の `oneway` 判定が `-1` も `true` にするが逆走エッジを追加しない

> ✅ 修正済み（2026-09-26 再確認）。両ビルダーで逆方向エッジのフラグをクリア済み。

**ファイル:** `osm-importer/src/main/kotlin/com/gorite/cyclemap/importer/OsmImporter.kt`

**該当箇所:**
```kotlin
// OsmGraphBuilder
val oneway = way.tags["oneway"] in setOf("yes", "true", "1", "-1")  // "-1" も oneway=true
val reverseOnly = way.tags["oneway"] == "-1"
...
if (!reverseOnly) addEdge(outgoing, GraphEdge(fromId, toId, distance, roadType, oneway))
if (!oneway || reverseOnly) addEdge(outgoing, GraphEdge(toId, fromId, distance, roadType, oneway))
```

一方 `TwoPassGraphBuilder` の実装:
```kotlin
val onewayTag = way.tags["oneway"]
val oneway = onewayTag in setOf("yes", "true", "1", "-1")
val reverseOnly = onewayTag == "-1"
```

**危険性:**  
`oneway="-1"` は「逆方向のみ通行可（to→from）」の意味だが、両実装とも `reverseOnly` は `true` かつ `oneway` も `true` になる。`!reverseOnly` が `false` なので正方向エッジは追加されず、`!oneway || reverseOnly` = `false || true` = `true` なので逆方向エッジは追加される。ここまでは正しい。しかし `GraphEdge` に `oneWay = true` が付いているため、このエッジを受け取ったルーターが「一方通行エッジなので逆走は禁止」と解釈する実装になった場合に矛盾が生じる（現状のルーターはこのフラグを無視しているため顕在化していない）。

---

---

## 9. 県切替統合（MainActivity.kt、2026-09-24〜26追加分）

### BUG-22 🟠 High — bbox重複＋狭域優先で広島東部が岡山県に誤判定される

> ✅ 修正済み（2026-09-26）。`findPrefectureName` がbbox多重一致時に行政界ポリゴン
> （`data/ChugokuBoundaries.kt`、boundaries の中国5県GeoJSONから生成・全ring保持）で包含判定する。
> 単一一致・データ無し県は従来のbbox方式のまま。`RoutingGraphSelector`・`SearchDbSelector`・
> 目的地県判定・検証ハーネスは委譲のため自動反映。福山駅→広島県をJVMテストで確認。
> 残差：単一bbox一致（粗いbboxの県外はみ出し、例：岩国東端）は旧挙動のまま。沖合フォールバック維持のための意図的選択。

---

---

### BUG-23 🟠 High — 位置連動の先読み切替が計算中jobを殺し `isCalculatingRoute`/`isRerouting` が固まる

> ✅ 修正済み（2026-09-27）。切替開始時に殺す側で両フラグを回収（所有権の明確化）。
> 保留要求の再実行時に `runRouteCalculation` が立て直す。リルート抑止の固着も解消。

**該当箇所:** `MainActivity.kt` の `startGraphSwitch`（旧787 `cancelAndJoin`）＋取消パスの `return@Thread` 群（フラグリセットなし）。

**内容（修正前の記録）:** `LaunchedEffect(currentLocation)` の先読み切替は保留要求なしに先行jobをキャンセルする。殺されたjob側はフラグを戻さず、切替側も成功時しか後処理しないため、「探索中…」スピナーが残り続け、リルートjobだった場合は `isRerouting=true` 固定で以後すべての自動リルートが抑制される（`!isRerouting` ゲート）。復旧は手動操作のみ。

---

---

### BUG-24 🟡 Medium — `view-not-ready` パスがフラグ・job参照・警告のいずれも残さない

**該当箇所:** `MainActivity.kt:676-688`。兄弟分岐（切替・打切り・到達不能・成功・例外）は全てリセットするが本パスだけしない。

**内容:** 回転・バックグラウンド化等で計算完了時にviewが外れていると、スピナー固着＋無音（警告なし）でルートが出ない。

---

---

### BUG-25 🟡 Medium — 二重切替レース（`isSwitchingGraph` のセットが非同期）

> ✅ 修正済み（2026-09-27）。`scope.launch` 前の同期セットに変更（BUG-23修正に同梱）。

---

---

## 10. search.db / POI検索（2026-09-26追加分）

### BUG-31 🟠 High — スポットパネルが `"search.db"` をハードコードし県選択を無視する

> ✅ 修正済み（2026-09-26）。地図中心基準で `SearchDbSelector` を経由し、未対応県・欠如時は
> 「検索DBが見つかりません」で明示失敗する。

---

---

### BUG-32 🟡 Medium — 検索ダイアログの初回表示が古い/不明な位置で他県DBを引く

> ✅ 修正済み（2026-09-26）。`DestinationSearchDialog` に `liveLocation` 引数を追加し、
> `liveLocation ?: getLastKnownLocation` の実効位置でDB選択・検索・ヘッダー表示を行う。

---

---

### BUG-33 🟢 Low — 内部番兵 `search_unsupported.db` がユーザー向けメッセージに漏れる

> ✅ 修正済み（2026-09-26）。番兵は保持しつつ、表示は「○○県の検索DBがありません」／
> 「検索DBが見つかりません」に変更し、内部名・絶対パスを出さない。

---

---

### BUG-34 🟢 Low — `LIMIT` 先・距離ソート後のため真の最近傍が切り捨てられる

> ✅ 修正済み（2026-09-26）。カテゴリ分岐・`searchNearbyPlaces` の両方でSQL側に等距円筒近似の
> `ORDER BY`（`NEARBY_ORDER_BY`）を追加し、LIMIT前に近似距離順にする。厳密ソートはKotlin側に残置。
> 実DB検証：広島駅10km・コンビニ50件が真距離の昇順、最近8m。名称検索分岐は従来通り（遠方検索のため）。

---

---

### BUG-35 🟢 Low — デッド条件式（両腕が同一）

**該当箇所:** `MainActivity.kt:3028-3030`。`if (endsWith(":")) "$prefix%" else "$prefix%"`。無害だが意図不明。

---

---

## 11. 分類器・住所・表示（routing-core / data）

### BUG-36 🟠 High — 無制限の中立ブリッジが離れた同方向カーブを結合し偽 ROUNDABOUT を作る

> ✅ 修正済み（2026-09-27）。中立ノード連続数に上限 `circulationMaxNeutralGap=3` を追加し、
> 超過で周回を分断する。実リング（各ノード約17°で数えられる）は影響なし。
> 新テスト2件：長直進融合は非検出、上限内ギャップは検出を維持。旧挙動では前者が落ちることを確認済み
> （識別力あり）。

**該当箇所:** `routing-core/.../TurnClassifier.kt` の `detectCirculationEntries`＋`Instruction.kt` の新config。

**内容（修正前の記録）:** 10°未満ノードは `runMembers` を伸ばすが `runBig`/`runNet` に数えず、数・距離の上限もない。「5連続」の条件が骨抜きになり、3×15°＋長い直進＋3×20°（合計105°）で ROUNDABOUT になる。山岳ワインディングが到達可能な地形。

---

---

### BUG-37 🟡 Medium — 町・市・区のキメラ（境界付近で代表点の直線距離比較により別自治体/別区を誤判定）

> ✅ 修正済み（2026-09-27）。市・町（admin_level=7）および政令市区（admin_level=8）の行政界ポリゴン定義 `AdministrativeBoundaries.kt` を導入。
> `ReverseGeocoder.selectHierarchy` において、県→市町村→区の階層を Point-in-Polygon（面・内外判定）で包含判定し、町・丁目ノードも確定した区・市町村の境界内から優先選択するよう改修。
> 広島市西区井口五丁目（佐伯区役所1.58km vs 西区役所6kmで佐伯区と誤判定されていた問題）で正しく「広島県広島市西区井口五丁目」と判定されることを実機（Galaxy S21）画面およびユニットテストで実証済み。

**該当箇所:** `data/ReverseGeocoder.kt:84-140`、`data/AdministrativeBoundaries.kt`。

**内容（修正前の記録）:** 市境・区境付近で代表点ノードからの直線距離比較（Voronoi）を行っていたため、隣接自治体や隣接区の役所代表点に近い地点で「佐伯区井口五丁目」などのキメラ住所が発生していた。

---

---

## 12. tracking / 検証ハーネス / importer / その他

### BUG-50 🟡 Medium — 静止判定が走行中にも速度0を強制する

**該当箇所:** `tracking/LocationTrackingService.kt:186-191`。`distance < accuracy` で0固定。1Hz・15km/h（4.2m/fix）に対し精度5〜15mが普通のため、市街走行で速度が0に張り付く。EMA（0.7/0.3）でさらに尾を引く。

---

---

### BUG-51 🟡 Medium — `startTracking()` に権限ガードがなく `SecurityException` で死ぬ

**該当箇所:** `tracking/LocationTrackingService.kt:93-101`（`@SuppressLint("MissingPermission")`）。権限剥奪後の開始でサービスがクラッシュする。

---

---

### BUG-52 🟡 Medium — `stopRecording()` がメインスレッドで同期I/Oし例外処理なし

**該当箇所:** `tracking/LocationTrackingService.kt:115-135`。満杯/ removal 時に `onStartCommand` から例外→サービスクラッシュ＋通知なし。同一秒タイムスタンプの衝突上書きもあり。

---

---

### BUG-53 🟡 Medium — ベンチマーク例外で `isRoutingBenchmarkRunning` が永久 true

**該当箇所:** `MainActivity.kt:1068-1122`。`try/finally` がなく、実験コード由来の例外でボタンが「測定中…」のまま固まる（本番側の状態管理バグ）。

---

---

### BUG-60 🟢 Low — `HsaMode.values()`（deprecated）

**該当箇所:** `ui/DeveloperOptionsScreen.kt:116`。BUG-09 と同種の残り。

---

---

### BUG-61 🟢 Low — 開発者画面のシステムサービス null 未検査

**該当箇所:** `ui/DeveloperOptionsScreen.kt:286-287,324-325`。 exotic 端末で NPE しうる。

---

---

### BUG-63 🟢 Low — `onDestroy()` が記録中 recorder を破棄する

**該当箇所:** `tracking/LocationTrackingService.kt:151-157`。プロセスkill時は揮発データ消失（sticky 再起動でも戻らない）。

---

---

## 13. パイプライン統合・状態遷移・POI・インデックス（2026-09-27追加分）

### BUG-64 🟠 High — 地図上POIアイコン常時表示が `search.db` をハードコードし県選択を無視する

**該当箇所:** `MainActivity.kt:2564`。
```kotlin
val dbFile = File(cycleMapDataDir(context), "search.db")
val pois = withContext(Dispatchers.IO) {
    searchNearbyPlaces(dbFile, center.latitude, center.longitude, radius, limit = limit * 2)
```

**危険性:**  
スポットパネル（`SpotPanelSheet`）では BUG-31 で `SearchDbSelector` を経由するよう修正されたが、地図上に常時描画される POI マーカー（`LaunchedEffect(mapView, poiVisible, ...)` 内の 2564行目）では、依然として `File(cycleMapDataDir(context), "search.db")`（山口県用DB）が直接ハードコードされている。  
そのため、広島県など山口県以外の地図を表示・移動している時でも常に山口県の DB を読みに行き、他県の POI（トイレ、コンビニ、給水所等）が地図上に一切表示されない。また `search.db` が存在しない単県パッケージ環境では、毎回の地図移動・ズームごとにファイル不在エラーがログに多発する。

---

---

### BUG-65 🟠 High — パイプライン生成ファイル名とアプリ要求ファイル名の命名不一致（大文字小文字・プレフィックス）

**該当箇所:** `build_prefectures.py:47-51, 289-292` と `Prefectures.kt:380-383, 423-426`、`MainActivity.kt:832`。

**危険性:**  
データ生成パイプライン（`build_prefectures.py`）は中国5県をキャピタライズ名（`Yamaguchi`, `Hiroshima`, `Okayama` 等）で定義し、ZIP 内に `<name>.graph`、`<name>.graph.idx`、`<name>.search.db`（例: `Yamaguchi.graph`, `Yamaguchi.search.db`）を生成・格納する。  
しかしアプリ側の `RoutingGraphSelector` は山口県に対して `"yamaguchi.graph"`（先頭小文字）を要求し、`SearchDbSelector` は `"search.db"`（県名プレフィックスなし）を固定要求している。また `MainActivity.kt` の起動時初期ロードでも `"yamaguchi.graph"` がハードコードされている。  
このため、パイプラインで自動生成した `Yamaguchi.zip` をそのまま端末に展開（または将来のインストーラで配置）した場合、ファイル名不一致によりファイルが見つからず、山口県のルーティンググラフや検索DBの読み込みが失敗する。

---

---

### BUG-66 🟡 Medium — グラフ切替中にルート消去を行っても切替完了時に保留リクエストが自動実行される

**該当箇所:** `MainActivity.kt:1047-1074` (`clearRoute`) と `810-812` (`startGraphSwitch`)。

**危険性:**  
県グラフ切替処理（`startGraphSwitch`）が非同期で実行されている最中に、ユーザーがルート消去ボタンを押して `clearRoute()` を呼んでも、`clearRoute` は `pendingRouteRef.set(null)` を呼んでいない。  
そのため、バックグラウンドでのグラフ読み込み完了時に `startGraphSwitch` 内の `pendingRouteRef.getAndSet(null)?.let { req -> runRouteCalculation(...) }` が発火し、ユーザーが明示的にキャンセル・消去したはずの目的地・ルートが勝手に探索・描画されてしまう。

---

---

### BUG-67 🟡 Medium — `places` テーブルに `(lat, lon)` インデックスがなく全位置検索がフルテーブルスキャンになる

**該当箇所:** `build_search_db.py:285` および `osm-importer/src/main/kotlin/.../OsmImporter.kt:415`。

**危険性:**  
アプリ側では `searchNearbyPlaces`（周辺POI取得）や `ReverseGeocoder.reverseGeocode`（逆ジオコーディング）において、頻繁に（GPS受信ごと・地図移動ごと）`WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?` の範囲クエリを発行する。  
しかし `build_search_db.py` および `OsmImporter.kt` が生成する `places` テーブルには `places_osm_idx ON places(osm_type, osm_id)` しか存在せず、`(lat, lon)` に対するインデックスが存在しない。  
このため、数万〜数十万レコードを持つ `places` テーブル全体がクエリごとにフルスキャンされ、過度なCPU消費、バッテリ消耗、メインスレッドへの不要な待機遅延を引き起こす。

---

---

### BUG-68 🟢 Low — `runRouteCalculation` での `if (graph == null)` 判定が到達不能（常に false）

**該当箇所:** `MainActivity.kt:569`。コンパイラ警告: `Condition is always 'false'`。

**危険性:**  
531行目の `when (val selection = RoutingGraphSelector.select(...))` において、`Unavailable` 分岐では直ちに `return@calc` され、`Available` 分岐でも `if (loadedGraphName != selection.fileName || graph == null)` で `return@calc` されるため、`when` 式を抜けた時点で `graph != null` であることが確定している。直後の 569行目 `if (graph == null)` は論理的に到達不能であり、無駄なデッドコードとなっている。

---

---

### BUG-69 🟢 Low — エリアダウンロード開始時に `selectedAreaBounds` がクリアされず残存する

**該当箇所:** `MainActivity.kt:2203, 2237`。

**危険性:**  
エリアダウンロードダイアログでダウンロード開始ボタンを押した際、`isAreaSelectMode = false` や `areaDragState = AreaDragState()` はリセットされるが、`selectedAreaBounds` が `null` にリセットされない（キャンセルボタン処理では `selectedAreaBounds = null` されている）。次回エリア選択操作を行う際やダイアログ再オープン時に前回の領域矩形が残留する不整合の原因となる。

---

---

### BUG-70 🟢 Low — ベンチマークログ保存時のI/O例外未処理と親ディレクトリ作成未検査

**該当箇所:** `MainActivity.kt:3579-3604` (`saveRoutingBenchmarkLog`)。

**危険性:**  
`output.parentFile?.mkdirs()` の成否が確認されておらず、また `output.writeText(details)` が `try-catch` なしで直接呼ばれている。外部ストレージの空き容量不足や権限問題で書き込み例外が発生した場合、上位のベンチマークコルーチンがクラッシュし、BUG-53 の影響で `isRoutingBenchmarkRunning` が `true` のまま永久に固着する。

---

---
