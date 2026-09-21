# CycleMap バグレポート

> 調査日: 2026-09-21  
> 対象: application/ 配下の全ソースコード  
> (Kotlin: app / routing-core / osm-importer、Python: build_search_db.py)

---

## 危険度の凡例

| 危険度 | 意味 |
|--------|------|
| 🔴 **Critical** | アプリクラッシュ・データ消失・セキュリティ侵害に直結 |
| 🟠 **High**     | 機能不全・不正な結果を引き起こす可能性が高い |
| 🟡 **Medium**   | 条件次第で不具合が起きるが回避策がある |
| 🟢 **Low**      | 軽微・潜在的なリスク止まり |

---

## 1. `MainActivity.kt`

### BUG-01 🔴 Critical — `String.format` に `Locale` を誤渡し（距離表示が壊れる）

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

### BUG-02 🔴 Critical — `LaunchedEffect` のキーに `locationMarker` が欠落（古いマーカーを更新し続ける）

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

### BUG-03 🟠 High — `startRouteToDestination` がクロージャで古い `mapView` を参照する

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

### BUG-04 🟠 High — `DisposableEffect(mappedGraph)` でグラフを `close()` するとルート計算中にクラッシュ

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

### BUG-05 🟡 Medium — `considerLocation` の速度フィルタ上限が 60 m/s（216 km/h）固定でサイクリングに不適切

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

### BUG-06 🟡 Medium — `isDownloading` フラグが非 UI スレッドから直接変更される

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

### BUG-07 🟢 Low — `cacheMapTileCount` を `Short` にキャストしているがフィールド型との整合性が不明確

**ファイル:** `app/src/main/java/com/gorite/cyclemap/MainActivity.kt`

**該当箇所:**
```kotlin
cacheMapTileCount = 512.toShort()
cacheMapTileOvershoot = 8.toShort()
```

**危険性:**  
現在の値（512 / 8）は `Short` の範囲内（最大 32767）だが、将来的に大きな値を設定したときに `Short` の符号付きオーバーフローで負数になりサイレントに誤動作する。

---

## 2. `searchPlaces` 関数 / `DestinationSearchDialog`（MainActivity.kt 内）

### BUG-08 🟠 High — FTS5 JOIN クエリで `catWhereForFts` が `p.category` 参照のまま LIKE 補完に流用される

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

### BUG-09 🟢 Low — `SearchCategory.values()` を deprecated な API で呼び出している

**ファイル:** `app/src/main/java/com/gorite/cyclemap/MainActivity.kt`

**該当箇所:**
```kotlin
SearchCategory.values().indexOf(selectedCategory)
SearchCategory.values().forEachIndexed { index, cat -> ... }
```

**危険性:**  
Kotlin 1.9 以降 `enumValues()` / `entries` が推奨される。`values()` は毎回新しい配列を生成するためパフォーマンスロスがある（UI ごとに Recompose されるため、小さいが無駄なアロケーションが繰り返される）。

---

## 3. `LocationTrackingService.kt`

### BUG-10 🟠 High — `stopRecording()` でファイル保存前に `recording = false` をセットするとデータ競合

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

### BUG-11 🟡 Medium — 速度計算・スムージングが `LocationTrackingService` と `ServiceLocationUpdates` の2箇所で重複

**ファイル:** `MainActivity.kt` の `ServiceLocationUpdates` と `LocationTrackingService.kt` の `considerLocation`

**該当箇所:**  
- `considerLocation()` 内: `calculatedSpeed > 60.0` によるフィルタリング
- `ServiceLocationUpdates` 内: `smoothedSpeed = smoothedSpeed * 0.7 + rawSpeed * 0.3`

**危険性:**  
サービス側でフィルタリング済みの位置情報がUIに届き、UI側で再度速度計算を行う。サービスが `newLocation.hasSpeed()` の値を使い、UI側は `distanceTo` を使うため、GPS がハードウェア速度を返す場合とそうでない場合で表示値が異なる。スムージングが2段かかり、実際の速度より過度に遅延した表示になる。

---

## 4. `GpxRecorder.kt`

### BUG-12 🟡 Medium — `writeToFile` がエラー時でも空 GPX ファイルを作成する

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

### BUG-13 🟢 Low — `GpxWriter` の `gpxTimeFormat` がスレッドセーフでない

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

## 5. `MappedRouting.kt` / `LazyMappedRouting.kt`

### BUG-14 🔴 Critical — `LazyMappedRoadGraph.readEdge()` のオフセット計算が脆弱でバイナリ破損時にクラッシュ

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

### BUG-15 🟠 High — `LongIntIndex` でキー `Long.MIN_VALUE` が「空」センチネルと衝突する

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

### BUG-16 🟡 Medium — `nearestNodeIndex` が O(n) 線形探索でノード数十万規模では応答遅延

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

## 6. `OsmImporter.kt`

### BUG-17 🟠 High — `OsmGraphBuilder.build()` の `oneway` 判定が `-1` も `true` にするが逆走エッジを追加しない

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

### BUG-18 🟡 Medium — `SearchIndexWriter.lastInsertRowId` が毎行 SELECT を発行し書き込みが O(n²) になる

**ファイル:** `osm-importer/src/main/kotlin/com/gorite/cyclemap/importer/OsmImporter.kt`

**該当箇所:**
```kotlin
private fun lastInsertRowId(connection: Connection): Long =
    connection.createStatement().use { statement ->
        statement.executeQuery("SELECT last_insert_rowid()").use { result ->
            result.next()
            result.getLong(1)
        }
    }
```

**危険性:**  
レコード1件ごとに `Statement` を生成して `SELECT last_insert_rowid()` を実行している。places が数百万件になると SQLite の statement 生成コストが支配的になり、インポートが著しく遅くなる。JDBC の `Statement.RETURN_GENERATED_KEYS` または `PreparedStatement.getGeneratedKeys()` を使うべき。

---

## 7. `build_search_db.py`

### BUG-19 🟡 Medium — `LongSet._resize()` が空スロット判定に `EMPTY = -(1 << 62)` を使うが、OSM ノード ID がこの値と衝突しうる

**ファイル:** `osm-importer/build_search_db.py`

**該当箇所:**
```python
class LongSet:
    EMPTY = -(1 << 62)  # = -4611686018427387904

    def add(self, key: int) -> None:
        ...
        while True:
            existing = self._keys[slot]
            if existing == self.EMPTY:   # ← EMPTY と衝突したら無限ループ
```

**危険性:**  
OSM のノード ID 空間は 0〜2^63-1 だが、Python の `array.array('q')` は `int64` の符号付き整数。理論上 `-4611686018427387904` に等しいノード ID が存在した場合、`EMPTY` と区別がつかず無限ループ（または `size` が過小評価される）になる。現実の OSM データでは負の ID は稀だが、ゼロは歴史的に使われたことがあるため完全に安全とは言えない。

---

### BUG-20 🟢 Low — `Pass1Handler._flush_ways()` の `_way_fh` が例外時に閉じられない場合がある

**ファイル:** `osm-importer/build_search_db.py`

**該当箇所:**
```python
try:
    p1 = Pass1Handler(bbox, args.output, way_cache)
    osmium.apply(..., p1)
    p1.close()   # ← osmium.apply() で例外が飛ぶと呼ばれない
    ...
finally:
    if os.path.exists(way_cache):
        os.remove(way_cache)
```

**危険性:**  
`osmium.apply()` が例外を投げた場合、`p1.close()` が呼ばれないため `_way_fh` ファイルハンドラが開いたままになる。`finally` でキャッシュファイルを削除しようとしても、Windows では開いているファイルの削除に失敗する（macOS/Linux では問題なし）。また DB 接続 `self._con` も閉じられずに残る。

**修正方法:**
```python
try:
    p1 = Pass1Handler(...)
    try:
        osmium.apply(..., p1)
    finally:
        p1.close()
```

---

## 8. テスト不足

### BUG-21 🟡 Medium — `RoutingTest` の座標が全ノード同一地点（ヒューリスティックが常に 0）

**ファイル:** `routing-core/src/test/kotlin/com/gorite/cyclemap/routing/RoutingTest.kt`

**該当箇所:**
```kotlin
val nodes = (1L..4L).associateWith { GraphNode(it, 34.0, 131.0) }  // 全ノードが同じ座標
```

**危険性:**  
ヒューリスティック値が常に 0 になるため、A* は事実上 Dijkstra として動作しテストされている。将来ヒューリスティック関数にバグが混入しても、このテストでは検出できない。

---

## バグ一覧（サマリー）

| ID | 危険度 | ファイル | タイトル |
|----|--------|----------|----------|
| BUG-01 | 🔴 Critical | MainActivity.kt | `String.format` に Locale を誤渡しで距離表示が壊れる |
| BUG-02 | 🔴 Critical | MainActivity.kt | `LaunchedEffect` のキーに `locationMarker` が欠落 |
| BUG-14 | 🔴 Critical | LazyMappedRouting.kt | `readEdge()` の `Long→Int` キャストで 2GB 超ファイルがクラッシュ |
| BUG-03 | 🟠 High | MainActivity.kt | `startRouteToDestination` が古い `mapView` を参照 |
| BUG-04 | 🟠 High | MainActivity.kt | `DisposableEffect` でルート計算中に `close()` が呼ばれクラッシュ |
| BUG-08 | 🟠 High | MainActivity.kt | FTS5 JOIN の WHERE 句が LIKE フォールバックで不正に流用される |
| BUG-10 | 🟠 High | LocationTrackingService.kt | `stopRecording()` の `recording=false` タイミングでデータ競合 |
| BUG-15 | 🟠 High | MappedRouting.kt | `LongIntIndex` で `Long.MIN_VALUE` がセンチネルと衝突 |
| BUG-17 | 🟠 High | OsmImporter.kt | `oneway=-1` エッジの `oneWay` フラグが矛盾 |
| BUG-05 | 🟡 Medium | LocationTrackingService.kt | 速度フィルタ上限 216 km/h はサイクリングに不適切 |
| BUG-06 | 🟡 Medium | MainActivity.kt | `isDownloading` フラグの重複リセット競合 |
| BUG-11 | 🟡 Medium | MainActivity.kt | 速度計算・スムージングが2箇所で重複 |
| BUG-12 | 🟡 Medium | GpxRecorder.kt | 書き込み失敗時に壊れた GPX ファイルが残る |
| BUG-13 | 🟡 Medium | GpxRecorder.kt | `SimpleDateFormat` がスレッドセーフでない |
| BUG-16 | 🟡 Medium | LazyMappedRouting.kt | `nearestNodeIndex` が O(n) 線形探索で遅延 |
| BUG-18 | 🟡 Medium | OsmImporter.kt | `lastInsertRowId` が毎行 SELECT を発行し O(n²) |
| BUG-19 | 🟡 Medium | build_search_db.py | `LongSet` の EMPTY センチネル値が OSM ID と衝突しうる |
| BUG-20 | 🟢 Low | build_search_db.py | 例外時に `_way_fh` が閉じられない |
| BUG-07 | 🟢 Low | MainActivity.kt | `cacheMapTileCount` の `Short` キャストが将来オーバーフロー |
| BUG-09 | 🟢 Low | MainActivity.kt | `SearchCategory.values()` を deprecated な API で呼び出し |
| BUG-21 | 🟡 Medium | RoutingTest.kt | テストノードが同一座標でヒューリスティックが検証されない |
