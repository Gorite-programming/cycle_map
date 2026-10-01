# CycleMap バグレポート（残存・未修正課題）

> 初版調査: 2026-09-21  
> 全面再調査・更新: 2026-09-30  
> 対象: プロジェクト全体の残存課題・未修正バグ（計4件: BUG-71 〜 BUG-74）  
> ※ **過去の全70件の修正完了バグは [bugs_archive.md](bugs_archive.md) にアーカイブされています。**

---

## 0. 残存バグサマリー（計4件）

| ID | 状態 | 危険度 | ファイル / 領域 | タイトル・概要 |
|---|---|---|---|---|
| **BUG-71** | 🟡 回避策あり | 🔴 Critical | Windows環境 / Gradle | 日本語パス環境で Gradle テストワーカーが文字化けし全テストが `ClassNotFoundException` で失敗 |
| **BUG-72** | ✅ 修正済み | 🟠 High | `build_prefectures.py` | Windows 環境で `osm-importer` CLI 実行ファイル形式および `SOURCE_PBF` が見つからずエラー |
| **BUG-73** | ✅ 修正済み | 🟡 Medium | `NavigationHelper.kt` | `CompassUpdates` 内で `rememberUpdatedState` が欠落し古いコールバックを呼び出し続ける |
| **BUG-74** | ✅ 修正済み | 🟡 Medium | `CyclingData.kt` | 国土数値情報（医療3.0）由来の歯科（`amenity:dentist`）が `PoiCategory` に未定義で地図・スポット検索から欠落 |

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
