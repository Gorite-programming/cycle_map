# CycleMap バグレポート（残存・未修正課題）

> 初版調査: 2026-09-21  
> 全面再調査・更新: 2026-09-30 / 2026-10-09  
> 対象: プロジェクト全体の残存課題・未修正バグ（計15件: BUG-71 〜 BUG-85）  
> ※ **過去の全70件の修正完了バグは [bugs_archive.md](bugs_archive.md) にアーカイブされています。**

---

## 0. 残存バグサマリー（計15件）

| ID | 状態 | 危険度 | ファイル / 領域 | タイトル・概要 |
|---|---|---|---|---|
| **BUG-71** | 🟡 回避策あり | 🔴 Critical | Windows環境 / Gradle | 日本語パス環境で Gradle テストワーカーが文字化けし全テストが `ClassNotFoundException` で失敗 |
| **BUG-72** | ✅ 修正済み | 🟠 High | `build_prefectures.py` | Windows 環境で `osm-importer` CLI 実行ファイル形式および `SOURCE_PBF` が見つからずエラー |
| **BUG-73** | ✅ 修正済み | 🟡 Medium | `NavigationHelper.kt` | `CompassUpdates` 内で `rememberUpdatedState` が欠落し古いコールバックを呼び出し続ける |
| **BUG-74** | ✅ 修正済み | 🟡 Medium | `CyclingData.kt` | 国土数値情報（医療3.0）由来の歯科（`amenity:dentist`）が `PoiCategory` に未定義で地図・スポット検索から欠落 |
| **BUG-75** | ✅ 修正済み | 🔴 Critical | `LazyMappedRouting.kt` | `route()` において非幹線道路ペナルティがエッジ単位ではなく過去累積コスト全体に掛けられ A* 最適性が破綻 |
| **BUG-76** | ✅ 修正済み | 🟠 High | `build_search_db.py` / `build_prefectures.py` | フォールバック用 `build_search_db.py` に `search_text` 列が欠落しパイプライン検証で 100% 失敗 |
| **BUG-77** | ✅ 修正済み | 🟠 High | `build_prefectures.py` | Windows 環境で 0 バイトのシンボリックリンク `build_search_db.py` を呼び出して起動失敗 |
| **BUG-78** | ✅ 修正済み | 🟠 High | `Prefectures.kt` | `TileDownloader.downloadRouteCorridor` のスレッド同期欠落（DB破損リスク）と短時間タイムアウト（60秒） |
| **BUG-79** | ✅ 修正済み | 🟡 Medium | `NavigationHelper.kt` | 地磁気・回転センサーの `NaN` 未検査により平滑化方位（`smoothedHeading`）が永久汚染・追従停止 |
| **BUG-80** | ✅ 修正済み | 🟡 Medium | `AdministrativeBoundaries.kt` | デコード境界キャッシュキーに県名が含まれず、県境・同名行政区（広島市中区/岡山市中区等）で境界衝突 |
| **BUG-81** | ✅ 修正済み | 🟡 Medium | `GpxParser.kt` | ISO 8601 ミリ秒/タイムゾーン付加時のパース失敗で全ポイントが現在時刻で上書きされ走行記録が破損 |
| **BUG-82** | ✅ 修正済み | 🟡 Medium | `MapUtilities.kt` 等 | `haversineMeters` で浮動小数点丸め誤差により `a > 1.0` となった場合に `sqrt` が `NaN` を返し距離が破損 |
| **BUG-83** | ✅ 修正済み | 🟡 Medium | `LazyMappedRouting.kt` | `nearestNodeIndex` でノード数 0 の空グラフ読み込み時に境界外アクセスで確実にクラッシュ |
| **BUG-84** | ✅ 修正済み | 🟡 Medium | `ReverseGeocoder.kt` | 住所未判定エリアで `AddressDisplayController` の位置キャッシュが更新されず毎秒 DB 全件スキャンが発生 |
| **BUG-85** | ✅ 修正済み | 🟢 Low | `MapScreen.kt` | Compose `AndroidView(MapView)` のライフサイクルで `view.onDetach()` が呼ばれずメモリリークの原因 |

---

## 1. 残存バグ詳細

### BUG-71 🟡 回避策あり — 日本語パス環境で Gradle テストワーカーが文字化けし全テストが `ClassNotFoundException` で失敗

- **ファイル:** Windows 環境 / Gradle テスト実行プロセス / `application/`
- **現象:**  
  Windows 環境において、リポジトリが日本語フォルダ（例: `C:\Users\takum\OneDrive\デスクトップ\cycle_map\...`）配下に配置されている場合、Gradle のテストワーカープロセス起動時に引数ファイル（`@gradle-worker-classpath*.txt`）が UTF-8 で生成される。
  しかし、Java 21 の C ランチャー（`java.exe`）がシステム既定ロケール（MS932 / Windows-31J）で引数ファイルを読み込むため、クラスパス中の「デスクトップ」等の全角文字が「チEクトップ」等の不正なバイト列に化け、すべての単体テスト（`:routing-core:test`, `:osm-importer:test`, `:app:testDebugUnitTest`）が `java.lang.ClassNotFoundException` で失敗する。
- **影響:** Windows 環境での CLI 単体テストが実行不能となる。
- **回避策（確認済み）:**

  ```powershell
  # 仮想ドライブを割り当ててから実行（全テスト成功確認済み）
  subst V: "C:\Users\takum\OneDrive\デスクトップ\cycle_map"
  Set-Location V:\application
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  .\gradlew.bat :routing-core:test :osm-importer:test :app:testDebugUnitTest
  ```

- **恒久対策:** Gradle + Windows JVM の組み合わせ起因のため Gradle 設定では修正不可。半角英数のみのパス（例: `C:\projects\cycle_map`）にリポジトリを配置することを推奨。

---

### BUG-72 🟠 High — Windows 環境で `build_prefectures.py` の `osm-importer` CLI および `SOURCE_PBF` が実行・検出できない

- **ファイル:** `build_prefectures.py`
- **該当箇所:**
  ```python
  26: SOURCE_PBF = Path("japan-latest.osm.pbf")
  27: OSM_IMPORTER = Path("osm-importer/build/install/osm-importer/bin/osm-importer")
  ```
- **現象:**
  1. `OSM_IMPORTER` に拡張子のない Unix シェルスクリプトがハードコードされているため、Windows 上で `run_live` (`subprocess.Popen`) を呼ぶと `[WinError 193] %1 は有効な Win32 アプリケーションではありません` エラーとなり起動できない（Windows では `osm-importer.bat` が必要）。
  2. ルート直下の `japan-latest.osm.pbf` は macOS/Linux 向けシンボリックリンクであり、Windows 環境では機能しない。実体は `data/japan-latest.osm.pbf` に置かれるため、フォールバックなしでは `FileNotFoundError` となる。
- **修正方針:**
  - `os.name == "nt"` の場合は `osm-importer.bat` を選択し、かつ `application/osm-importer/build/install/...` の実体パスも探索する。
  - `SOURCE_PBF` は `data/japan-latest.osm.pbf` が存在する場合にそちらを優先するようパス解決を柔軟化する。

---

### BUG-73 🟡 Medium — `CompassUpdates` 内で `rememberUpdatedState` が欠落し古いコールバックを呼び出し続ける

- **ファイル:** `application/app/src/main/java/com/gorite/cyclemap/NavigationHelper.kt`
- **該当箇所:**
  ```kotlin
  @Composable
  internal fun CompassUpdates(context: Context, onHeadingChanged: (Float) -> Unit) {
      DisposableEffect(context) {
          ...
          val listener = object : SensorEventListener {
              override fun onSensorChanged(event: SensorEvent) {
                  ...
                  onHeadingChanged(smoothedHeading) // ← 初回生成時の古いラムダをキャプチャし続ける
              }
          }
  ```
- **現象:**  
  `ServiceLocationUpdates` では `val onLocation by rememberUpdatedState(onLocationChanged)` を適切に使用しているが、`CompassUpdates` では `rememberUpdatedState` が欠落している。親 Composable が Recompose されて新しい `onHeadingChanged` が渡されても、`context` が変化しない限り `DisposableEffect` は再実行されないため、センサーイベント発生時に古いラムダ（古い画面状態や破棄された参照）を呼び出し続け、メモリリークや状態不整合の温床となる。
- **修正方針:**
  ```kotlin
  val currentOnHeadingChanged by rememberUpdatedState(onHeadingChanged)
  // リスナー内:
  currentOnHeadingChanged(smoothedHeading)
  ```

---

### BUG-74 🟡 Medium — 国土数値情報（医療3.0）由来の歯科（`amenity:dentist`）が `PoiCategory` に未定義で地図・スポット検索から欠落

- **ファイル:** `application/app/src/main/java/com/gorite/cyclemap/ui/cycling/CyclingData.kt`
- **該当箇所:**
  ```kotlin
  HOSPITAL("病院", R.drawable.ic_lucide_hospital, "#D81B60", listOf("amenity:hospital", "amenity:clinic", "amenity:doctors"), false),
  ```
- **現象:**  
  `sandbox/data-tool` から生成される新版検索DBでは、国土数値情報（医療機関第3.0版）から歯科診療所が `amenity:dentist` として数千件出力されている。しかしアプリ側の `PoiCategory.HOSPITAL` の `prefixes` に `"amenity:dentist"` が含まれていないため、`PoiCategory.forCategory("amenity:dentist")` が `null` となり、地図上の常時表示アイコンや周辺スポット一覧（`searchNearbySpots`）で完全に無視されてしまう。
- **修正方針:**
  `PoiCategory.HOSPITAL` の prefixes に `"amenity:dentist"` を追加する。
  ```kotlin
  HOSPITAL("病院", R.drawable.ic_lucide_hospital, "#D81B60", listOf("amenity:hospital", "amenity:clinic", "amenity:doctors", "amenity:dentist"), false),
  ```

---

### BUG-75 ✅ 修正済み — `LazyMappedRoadGraph.route()` において非幹線道路ペナルティが過去累積コスト全体に掛けられ A* 最適性が破綻

- **ファイル:** `application/routing-core/src/main/kotlin/com/gorite/cyclemap/routing/LazyMappedRouting.kt`
- **該当箇所:**
  ```kotlin
  val arterialBias = if (arterialOnly && !isArterial(edge.highwayType)) arterialPenaltyMultiplier else 1.0f
  open += Entry(target, newCost * arterialBias + heuristic(targetLat, targetLon, targetIndex))
  ```
- **現象:**  
  長距離ルーティング高速化のための階層的探索（`arterialOnly = true`）において、非幹線道路の通過コストにペナルティ（例: 1.5倍）を課す意図で `arterialBias` が導入されている。しかし、`newCost` は「出発点から現在のノードまでの累積実コスト全体」である。
  直前のエッジ1本のコストではなく、過去の累積移動コスト全体に `arterialBias` を乗算して優先度キュー（`open`）の評価値としているため、すでに数十km走行した後に非幹線エッジを1本通るだけで、評価値が一気に「過去コスト×1.5倍」へと跳ね上がる。
  これにより：
  1. 優先度キューの評価値関数 $f(n)$ の単調増加性（Consistency）および下界性（Admissibility）が完全に崩壊する。
  2. ゴール直前でどうしても非幹線道路に入らざるを得ない区間（ラストワンマイル等）において、不当に巨大なペナルティが累積値全体に乗るため、遠回りの幹線道路を走り続ける不合理なルートが選択されたり、探索が極端に歪む。
- **修正方針:**
  ペナルティは累積コスト `newCost` 全体に乗算するのではなく、当該エッジの通行コスト（または当該エッジ追加分）に対してのみ適用するか、エッジ重み計算時（`val stepCost = edge.lengthMeters * ... * arterialBias`）に反映させる。

---

### BUG-76 ✅ 修正済み — フォールバック用 `build_search_db.py` の `places` スキーマに `search_text` 列が欠落しパイプライン検証で 100% 失敗

- **ファイル:** `application/osm-importer/build_search_db.py` / `build_prefectures.py`
- **該当箇所:**
  `build_search_db.py`:
  ```python
  cursor.execute(
      """
      CREATE TABLE places (
          id INTEGER PRIMARY KEY,
          osm_id INTEGER,
          name TEXT,
          category TEXT,
          lat REAL,
          lon REAL
      )
      """
  )
  ```
  `build_prefectures.py`:
  ```python
  REQUIRED_COLUMNS = {"id", "osm_id", "name", "category", "lat", "lon", "search_text"}
  ...
  missing_cols = REQUIRED_COLUMNS - actual_cols
  if missing_cols:
      raise ValueError(f"Missing required columns in places table: {missing_cols}")
  ```
- **現象:**  
  `build_prefectures.py` の `verify_search_db()` 関数は、SQLite の FTS5 非対応環境互換性確保のために `places` テーブルに `search_text` 列の存在を必須条件（`REQUIRED_COLUMNS`）として厳格にチェックしている。
  しかし、フォールバック用スクリプト `application/osm-importer/build_search_db.py`（およびルート直下のシンボリックリンク先）が生成する SQLite スキーマには `search_text` 列が存在せず、インデックスも `idx_places_search_text` ではなく旧仕様のままとなっている。
  このため、`build_prefectures.py --legacy-osm-search-db` を実行すると、DB 生成直後の検証フェーズで必ず `ValueError: Missing required columns in places table: {'search_text'}` がスローされ、パイプラインが 100% 停止する。
- **修正方針:**
  `application/osm-importer/build_search_db.py` のテーブル定義に `search_text TEXT` を追加し、データ挿入時に `name` の正規化文字列（ひらがな・カタカナ・ローマ字）を書き込むか、少なくとも `search_text` 列に `name` を格納してインデックスを作成するように改修する。

---

### BUG-77 ✅ 修正済み — Windows 環境で 0 バイトのシンボリックリンク `build_search_db.py` を呼び出してパイプラインが起動失敗

- **ファイル:** `build_prefectures.py`
- **該当箇所:**
  ```python
  BUILD_SEARCH_DB = Path("build_search_db.py")
  ...
  run_live([
      PYTHON, str(BUILD_SEARCH_DB),
      "--bbox", "none",
      str(pbf_path),
      str(db_path),
  ])
  ```
- **現象:**  
  リポジトリルートの `build_search_db.py` は Git 上でシンボリックリンクとしてコミットされており、Windows 環境で clone した場合、シンボリックリンクが展開されず「0 バイトのテキストファイル（リンク先文字列が入ったファイル）」となる。
  実体は `application/osm-importer/build_search_db.py` に存在するが、`build_prefectures.py` はルートの `build_search_db.py` を直接 Python インタプリタに渡して実行するため、Python は中身のない空ファイルを実行して即座に終了し、出力 DB が作成されずにエラーとなる。
- **修正方針:**
  BUG-72 と同様に、パス解決時に `application/osm-importer/build_search_db.py` が存在するかチェックし、実体が存在する場合はそちらを優先して呼び出すようにフォールバック処理を追加する。

---

### BUG-78 ✅ 修正済み — `TileDownloader.downloadRouteCorridor` のスレッド同期欠落（DB破損リスク）と短時間タイムアウト（60秒）

- **ファイル:** `application/app/src/main/java/com/gorite/cyclemap/data/Prefectures.kt`
- **該当箇所:**
  ```kotlin
  val executor = Executors.newFixedThreadPool(8)
  ...
  executor.submit {
      ...
      val data = connection.inputStream.use { it.readBytes() }
      writer.saveFile(tile, ByteArrayInputStream(data)) // 排他制御なし！
  }
  ...
  executor.shutdown()
  val finished = executor.awaitTermination(60, TimeUnit.SECONDS)
  // writer.onDetach() が呼ばれていない
  ```
- **現象:**  
  1. `writer.saveFile(tile, ...)` は内部で osmdroid の `SqliteArchiveTileWriter`（SQLite データベース）への書き込みを行う。固定スレッドプール（8 スレッド）から同時に `saveFile` を呼び出しているが、`synchronized` による同期ブロックが存在しない。複数のワーカースレッドが同一の SQLite 接続・トランザクションに対して並行書き込みを試みるため、`sqlite3_step: database table is locked` やデータベース破損（corruption）を引き起こす危険性がある。
  2. タイムアウトが `60, TimeUnit.SECONDS` 固定であるため、長距離ルート（数百〜数千タイル）や低速ネットワーク下において 60 秒で `awaitTermination` がタイムアウトし、バックグラウンドでダウンロード処理が続いているにもかかわらず呼び出し元には完了通知（`progress(1.0f)`）が返り、不完全な状態で終了する。
  3. 使用後に `writer.onDetach()` が呼ばれておらず、SQLite 接続やリソースがリークする。
- **修正方針:**
  - `writer.saveFile` 呼び出し箇所を `synchronized(writer)` 等で排他制御する。
  - ダウンロード総数に応じた適切なタイムアウト時間を設定するか、コルーチン（`Dispatchers.IO` + `Mutex` / `Channel`）に移行する。
  - 完了時に確実に `writer.onDetach()` を呼び出してリソースを解放する。

---

### BUG-79 ✅ 修正済み — 地磁気・回転センサーの `NaN` 未検査により平滑化方位（`smoothedHeading`）が永久汚染・追従停止

- **ファイル:** `application/app/src/main/java/com/gorite/cyclemap/NavigationHelper.kt`
- **該当箇所:**
  ```kotlin
  val orientation = FloatArray(3)
  SensorManager.getOrientation(rMatrix, orientation)
  val rawHeading = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
  // rawHeading が NaN の場合でもそのまま計算に進む
  val diff = (rawHeading - smoothedHeading + 540f) % 360f - 180f
  smoothedHeading = (smoothedHeading + diff * alpha + 360f) % 360f
  ```
- **現象:**  
  端末が極端な傾きになった場合やセンサー初期化中、ジンバルロック発生時などに、`SensorManager.getRotationMatrixFromVector` や `getOrientation` が特異点に達して `orientation[0]` に `Float.NaN` が返されることがある。
  このとき `rawHeading.isNaN()` または `!rawHeading.isFinite()` の事前チェックが存在しないため、`diff` と `smoothedHeading` に `NaN` が代入される。
  浮動小数点の性質上、一度 `smoothedHeading` が `NaN` になると、それ以降のフレームで正常な数値が渡されても `(NaN + diff * alpha)` の演算結果は永久に `NaN` のまま復帰できなくなり、コンパス・地図のヘディング追従が完全に停止する。
- **修正方針:**
  ```kotlin
  if (!rawHeading.isFinite()) return
  ```
  などの事前バリデーションを追加し、センサー値が非有限値（`NaN` / `Infinity`）の場合はフレームをスキップする。

---

### BUG-80 ✅ 修正済み — 行政界キャッシュキーに県名が含まれず、他県の同名行政区（広島市中区 vs 岡山市中区等）で境界ポリゴンが衝突

- **ファイル:** `application/app/src/main/java/com/gorite/cyclemap/data/AdministrativeBoundaries.kt`
- **該当箇所:**
  ```kotlin
  val cacheKey = "${boundary.name}_${boundary.adminLevel}"
  decodedBoundaryCache[cacheKey]?.let { return it }
  ```
- **現象:**  
  行政界ポリゴンのメモリキャッシュ `decodedBoundaryCache` のキーが `boundary.name + "_" + boundary.adminLevel` のみで生成されている。
  中国地方には、広島市「中区」「東区」「南区」「西区」と、岡山市「中区」「東区」「南区」のように、同一名称・同一 adminLevel（政令指定都市の行政区: admin_level 9）を持つ行政区が複数存在する。
  広島県と岡山県を切り替えた際、あるいは近接地域で探索した際に、岡山市のキャッシュキーで広島市のポリゴンが返される（またはその逆）衝突が発生し、誤った行政境界が描画・判定される。
- **修正方針:**
  キャッシュキーに親自治体名や都道府県名、または `boundary.osmId` / ユニーク識別子を含める：
  ```kotlin
  val cacheKey = "${boundary.prefecture}_${boundary.name}_${boundary.adminLevel}"
  // または osmId があれば "${boundary.osmId}"
  ```

---

### BUG-81 ✅ 修正済み — `GpxParser.kt` で ISO 8601 ミリ秒/タイムゾーン付加時のパース失敗により全時刻が現在時刻で上書きされ走行ログ破損

- **ファイル:** `application/app/src/main/java/com/gorite/cyclemap/tracking/GpxParser.kt`
- **該当箇所:**
  ```kotlin
  private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
  }
  ...
  val time = try {
      dateFormat.parse(timeStr)?.time ?: System.currentTimeMillis()
  } catch (_: Exception) {
      System.currentTimeMillis()
  }
  ```
- **現象:**  
  1. GPX 標準（ISO 8601）では、タイムスタンプにミリ秒が含まれる場合（`2026-10-09T12:00:00.000Z`）やオフセット表記の場合（`+09:00`）が一般的である。しかし `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")` はこれらのフォーマットをパースできず `ParseException` をスローする。
  2. 例外をキャッチした際に、フォールバックとして `System.currentTimeMillis()`（GPX ファイルをインポート・読み込んだ現在のリアルタイム時刻）を全トラックポイントに設定してしまう。
  結果として、過去の GPX ログをインポートした際、全ポイントが「いまインポートした同一ミリ秒」になり、走行時間・平均速度・移動ログが完全に破壊される。
  3. 加えて、トラックポイントの緯度経度取得失敗時に `lat ?: 0.0` / `lon ?: 0.0` とフォールバックしているため、不正な XML タグがあると日本からギニア湾（緯度0度・経度0度）への 12,000 km の瞬間ワープポイントが記録に混入する。
- **修正方針:**
  - `Instant.parse(timeStr)`（API 26+）等の標準 ISO-8601 パーサーを使用するか、複数フォーマットに対応したパーサーを実装する。
  - パース失敗時は `System.currentTimeMillis()` を代入せず、直前のトラックポイントの時刻を補間するか `null` / エラーとする。緯度経度欠損時は該当ポイントをスキップする。

---

### BUG-82 ✅ 修正済み — `haversineMeters` で浮動小数点丸め誤差により `a > 1.0` となった場合に `sqrt` が `NaN` を返し距離計算が破綻

- **ファイル:** `MapUtilities.kt` (L121), `GpxParser.kt` (L178), `Routing.kt` (L145), `ElevationRepository.kt` (L66)
- **該当箇所:**
  ```kotlin
  val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
  val c = 2 * atan2(sqrt(a), sqrt(1 - a))
  return EARTH_RADIUS_METERS * c
  ```
- **現象:**  
  地球の対蹠点（真裏）付近、または極めて離れた座標同士の距離計算において、浮動小数点演算（IEEE 754）の微小な丸め誤差により `a` の値が `1.0000000000000002` のように `1.0` をわずかに超える場合がある。
  このとき `1 - a` が `-2.22e-16` 等の負数となり、`sqrt(1 - a)` が `Double.NaN` を返すため、最終的な距離計算結果 `c` および戻り値が `Double.NaN` になる。
  この値がルーティングのコスト計算や GPS トラッキングの累積距離・標高積算に混入すると、距離表示やコスト比較が `NaN` に汚染されて機能不全を起こす。
- **修正方針:**
  `a` の値を `[0.0, 1.0]` の範囲にクランプする：
  ```kotlin
  val clampedA = a.coerceIn(0.0, 1.0)
  val c = 2 * atan2(sqrt(clampedA), sqrt(1.0 - clampedA))
  ```

---

### BUG-83 ✅ 修正済み — `LazyMappedRoadGraph.nearestNodeIndex` でノード数 0 の空グラフ読み込み時に境界外アクセスで確実にクラッシュ

- **ファイル:** `application/routing-core/src/main/kotlin/com/gorite/cyclemap/routing/LazyMappedRouting.kt`
- **該当箇所:**
  ```kotlin
  override fun nearestNodeIndex(lat: Double, lon: Double): Int {
      var bestDistSq = Double.MAX_VALUE
      var bestIndex = 0
      val seed = 0
      val seedDistSq = (lat - latitudes[seed]).pow(2) + (lon - longitudes[seed]).pow(2)
      ...
  ```
- **現象:**  
  グラフ生成途中の空ファイルや、ノードが存在しない破損した `.graph` ファイルが読み込まれた場合（`nodeCount == 0`），`latitudes` バッファの要素数が 0 であるにもかかわらず、無条件に `latitudes[seed]`（`seed = 0`）にアクセスする。
  `nodeCount == 0` の早期チェックが存在しないため、必ず `ArrayIndexOutOfBoundsException` またはバッファ境界外アクセス例外がスローされ、アプリが即時クラッシュする。
- **修正方針:**
  関数の冒頭で `if (nodeCount == 0) return -1` 等の空グラフガードを追加する。

---

### BUG-84 ✅ 修正済み — 住所未判定エリアで `AddressDisplayController` の位置キャッシュが更新されず毎秒 DB 全件スキャンが発生

- **ファイル:** `application/app/src/main/java/com/gorite/cyclemap/data/ReverseGeocoder.kt`
- **該当箇所:**
  ```kotlin
  val address = geocoder.getAddress(location.latitude, location.longitude)
  if (address != null) {
      lastQueryLat = location.latitude
      lastQueryLon = location.longitude
      _currentAddress.value = address
  }
  ```
- **現象:**  
  `AddressDisplayController` はバッテリー消費削減のため、前回のクエリ位置から 100m 以上移動した場合にのみリバースジオコーディングを実行する制御を入れている。
  しかし、県境の未収録エリア、山間部、海上などの「DB にヒットする住所が存在しないエリア（`address == null`）」を移動している場合、`lastQueryLat` と `lastQueryLon` が更新されない。
  そのため、GPS 位置が更新されるたび（毎秒）に「前回のクエリ位置からの距離判定」をすり抜け、毎秒バックグラウンドで重い SQLite クエリ（最近傍住所探索）が繰り返し実行され続け、端末のバッテリーを激しく浪費する。
- **修正方針:**
  クエリ実行後に `address` の成否にかかわらず `lastQueryLat` / `lastQueryLon` を現在地に更新する（または失敗時も一定時間・一定距離は再クエリを抑止する）。

---

### BUG-85 ✅ 修正済み — Compose `AndroidView(MapView)` のライフサイクルで `view.onDetach()` が呼ばれずメモリリークの原因

- **ファイル:** `application/app/src/main/java/com/gorite/cyclemap/MapScreen.kt`
- **該当箇所:**
  ```kotlin
  onRelease = { mapView ->
      mapView.onPause()
      // mapView.onDetach() が呼ばれていない
  }
  ```
- **現象:**  
  Compose の `AndroidView` 内で osmdroid の `MapView` をホストしているが、コンポーネント解放時（`onRelease`）において `mapView.onPause()` のみが呼ばれ、`mapView.onDetach()` が呼ばれていない。
  osmdroid の `MapView.onDetach()` は内部のタイルローダースレッドプール（`TileProvider`）のシャットダウン、タイルキャッシュ DB のクローズ、リスナー解除を担当しているため、これが呼ばれないと画面遷移やアクティビティ再生成時にバックグラウンドスレッドとコンテキスト参照が残留し、メモリリークの要因となる。
- **修正方針:**
  `onRelease` 内で `mapView.onPause()` に加えて `mapView.onDetach()` を明示的に呼び出す。

