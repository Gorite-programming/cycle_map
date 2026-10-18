# CycleMap 全面的プロジェクト改修 実施計画書 (`cycle_map/re_test`) — 改訂版 r2

> r2 の変更点: 全面再構築 → **段階的置換(strangler)** へ変更 / osmdroid 撤去時のラスター・キャッシュ移行を明記 / コード複製の正規化(共有モジュール) / 未コミット作業の退避 / 高頻度状態の分離 / 数値の訂正 / 検証計画の強化 / バック 88% の余裕策

## 1. Goal Description (目標)

現行 `test/` の課題(4,133行の `MapScreen.kt` を含む巨大ファイル、`osmdroid` と `MapLibre` の二重管理、127箇所の散在した状態、`Color(0x…)` 直書き 232 箇所、直下の未整理画像群)を解消し、`plans.md` に基づく **Google Maps 互換度 全体 ≥90%**(フロント ≥92%、バック ≥88%)を完全オフライン前提で達成する。

`cycle_map/re_test` を新設し、**機能単位で `test/` から移植・置換**しながらクリーンな設計へ移行する(ゼロからの一括再構築はしない)。

---

## 2. User Review Required (合意事項)

> [!IMPORTANT]
> 確定済みの設計方針(/grill-me の合意):
> 1. **進め方**: `re_test/` 直下に 4 部構成ドキュメント(仕様・設計・ロードマップ・採点基準)+パリティ台帳を作成し、**承認後**に P0〜P7 を進める。
> 2. **地図エンジン**: `osmdroid` を撤廃し **MapLibre(3Dベクタータイル)に一本化**する。ただし §6 の代替・移行が完了するまで osmdroid を削除しない。
> 3. **モジュール構成**: `:app`, `:routing-core`, `:osm-importer`。`routing-core` と `osm-importer` は **複製せず共有**(§5)。
> 4. **UI**: MVI / UDF + Jetpack ViewModel。**1ファイル 500 行以内**。
>
> **要確認(承認前に人間が決める項目)**:
> - A. **【決定済 2026-10-12】正の系統** → 下記「§2-A 決定事項」を参照。
> - B. **【決定済 2026-10-12】ベクタータイルのオフライン配布** → 下記「§2-B 決定事項」を参照。
> - C. **【決定済 2026-10-12】既存 `tiles/cache.db` の扱い** → 下記「§2-C 決定事項」を参照。

### §2-A 決定事項: 正の系統(2026-10-12 確定)
- **`re_test` を新しい正系統**とする。
- **パリティ台帳(`parity_checklist.md`)の全項目がチェック済み**になり、採点台帳で全体 ≥90% を満たすまで、**`application/` を本番として維持**する(破壊的変更をしない)。
- 達成後に `application/` と `test/` を `re_test` で置き換える。`routing-core` と `osm-importer` は共有(§5)。置き換えの実行(ディレクトリ移動・削除・リリース)は**人間(Gorite)の承認後**のみ。
- 移行期間中、`test/` は参照用に凍結する(新規機能を足さない。重大バグ修正のみ)。
- 付随更新: `AGENTS.md` に `re_test/` の位置づけを追記する(人間の承認後)。

### §2-C 決定事項: 既存 `tiles/cache.db`(2026-10-12 確定)
- osmdroid 形式のキャッシュは**移行せず破棄**し、新方式で**再取得**する(経路回廊 z15–z16 のみのキャッシュで、全域取得は見送り済みのため)。
- 旧 `cache.db` は**自動削除しない**(ユーザーのデータ。容量が気になる場合のため設定画面に「旧キャッシュを削除」を設ける)。新方式は別ファイル(MBTiles)を使う。
- 初回起動時、旧 `cache.db` を検出したら**一度だけ**「地図キャッシュの方式が変わったため、経路回廊の再取得が必要です」と案内する(エラー扱いにしない。ナビはベクタータイルのみで継続可能)。
- ラスター(地理院等)の新キャッシュは MBTiles(`CycleMap/tiles/raster_<provider>.mbtiles`)とし、回廊取得は同期制御・タイムアウト・件数検証を行う(BUG-78 の再発防止)。


### §2-B 決定事項: ベクタータイル・フォント・アイコンのオフライン配布(2026-10-12 確定)

| 項目 | 決定 |
|---|---|
| 形式 | **PMTiles**(現行継続)。生成は `vector_test/pipeline/build_tiles.py`(Planetiler)を `build_prefectures.py` 系列へ統合 |
| 区切り | **県別** `<name>.pmtiles`。既存の県別パッケージ ZIP に同梱(ZIP は `.search.db` / `.graph` / `.graph.idx` / `.pmtiles` / `manifest.json` / `LICENSE.txt` の 6 点へ。`AGENTS.md` の「5点」不変条件を更新する) |
| 配置 | `getExternalFilesDir(DOCUMENTS)/CycleMap/<name>.pmtiles`(他の県別データと同じ階層)。APK 非同梱 |
| 県の切替 | `Prefectures.kt` の `RoutingGraphSelector` / `SearchDbSelector` と同様に `TileSourceSelector` を追加し、現在地の県へ自動切替 |
| フォント(glyphs)・アイコン(sprite) | **端末データフォルダ** `CycleMap/assets/{fonts,sprites}/` に配置。スタイル JSON はそこを参照 |
| 異常時 | PMTiles が無い県は、背景色+「この県の地図データ未導入」の小チップ+導入手順へ案内。**オンラインへ黙って切り替えない** |

**発見した問題(要修正)**: `protomaps_light.json` / `protomaps_dark.json` の `glyphs` と `sprite` が `https://protomaps.github.io/basemaps-assets/...` を指している。**機内モードでは文字ラベルとアイコンが表示されない恐れが高い**(タイル本体はローカル PMTiles で動く)。P3 の最初に以下を行う。
1. 日本語を含むフォントスタックの glyph PBF を生成(Noto Sans CJK JP など、**ライセンス OFL を `LICENSE.txt` に明記**)。全 range を入れると数十 MB になるため、必要な range のみに絞る。
2. sprite(`light` / `dark` の `.json` + `.png` + `@2x`)を `CycleMap/assets/sprites/` へ。Google のアイコンは使わない(`plans.md` §5-3)。
3. スタイルの `glyphs` / `sprite` を、実行時にローカルパスへ差し替える(PMTiles と同じ `__LOCAL_...__` プレースホルダ方式)。
4. 受入テスト: 機内モードで z5 / z10 / z14 / z17 に**ラベルとアイコンが出る**スクリーンショット(`plans.md` §5-5 と同じ)。

**既存コードの整理**: `VectorMapView.kt` L99〜105 にハードコードされた複数パス(`com.gorite.vectortest` など旧パッケージ名を含む)を廃止し、`CycleMap/` 直下の単一の探索ロジックにする。

---

## 3. 着手前の必須作業(P0 の前提)

1. **未コミット作業の退避**: `test/` に未コミット差分(約 1,400 行、`DayNightManager`、`power/`、`protomaps_dark.json`、`BatteryPolicyTest`/`CameraMotionPolicyTest`/`DayNightManagerTest` ほか未追跡ファイル)がある。**人間(Gorite)が確認してコミットまたは退避する**まで `re_test/` 作業へ進まない。エージェントは `git add/commit/push` を実行しない。
2. **機能パリティ台帳**を作成(`re_test/parity_checklist.md`): 現行 `test/` の**実機検証済み機能**(DEF-01〜11、検索、経路、ナビ、GPX記録、お気に入り、履歴、省電力、日夜切替、3D/2D、POI表示)を一覧化。**移植済み=チェック済み**でなければ、その機能の旧実装を削除しない。
3. `diff -r application/routing-core test/routing-core` で差分を把握し、`re_test` が取り込むベースを決める(§5)。

---

## 4. Proposed Changes (成果物計画)

```text
cycle_map/re_test/
├── README.md                  # [NEW] 概要、設計思想、環境要件、ビルド手順、application/test との関係
├── architecture_spec.md       # [NEW] MapLibre一本化、MVI、500行規則、パッケージ構造、状態分離
├── roadmap_p0_to_p7.md        # [NEW] P0〜P7 ロードマップ・受入条件・テスト計画
├── scorecard_90percent.md     # [NEW] 互換度採点台帳 (F1〜F9 / B1〜B9)
└── parity_checklist.md        # [NEW] 現行 test/ 機能の移植パリティ台帳(§3-2)
```

### [Component 1] `README.md`
- `application/`(本番)、`test/`(プロトタイプ)、`re_test/` の関係と**正の系統**(§2-A 決定(re_test が新正系統))。
- 技術スタック: Kotlin 2.0.21、AGP 8.10.1、Compose、**MapLibre Native 11.8.0**(`test/gradle/libs.versions.toml` の実値。変更時は同ファイルを正とする)、Protomaps、SQLite。
- ビルド(macOS / Linux / Edge 2)。クリーンな HEAD からのみ配布 APK を作る規則。

### [Component 2] `architecture_spec.md`
- **MapLibre 単一エンジン**: osmdroid 排除(§6 の条件付き)。スタイルは `protomaps_light/dark.json`。
- **MVI / UDF**: 状態は**1つにまとめない**。
  ```kotlin
  // 低頻度(画面構成): 再コンポーズを許容
  data class MapUiState(/* sheet, chips, selectedPlace, route選択, theme, linkState */)
  // 高頻度(GPS 1Hz, 方位 10-50Hz): 別 StateFlow、購読する Composable を最小化
  data class LocationUiState(/* position, speed, bearing, accuracy */)
  data class NavProgressState(/* distanceToTurn, eta, currentStep */)
  sealed interface MapUiIntent { /* ... */ }
  class MapViewModel : ViewModel()
  ```
  - 高頻度状態は `collectAsState` を末端の小さな Composable で行い、`MapScreen` 全体を再コンポーズさせない(`derivedStateOf` / `key` を併用)。
  - 方位は UI 側で ease 補間し、センサー NaN を弾く(BUG-79)。
- **ファイル分割(500行以内)**: `ui/map/{MapTopBar,MapControls,MapOverlays,MapSheets,NavigationScreen}.kt`、`ui/theme/`(トークンのみ)、`power/`、`theme/`(天文)、`net/`。
- **Z-order**: 道路 < ルート線 < 3D建物(または地面ドレープ) < ラベル < POI < 現在地。レイヤーIDは定数化し `StyleLayerOrderTest` で固定。
- **テーマトークン**: `Color(0x…)` 直書き禁止(現行 232 箇所は画面単位で置換)。文字列は `strings.xml`(現行 `Text("…")` 123 箇所)。
- **スレッド規律**: DB/ファイルは IO、`use {}`、`LaunchedEffect` の key 規律、ジョブキャンセル(`cautions.md` §2.5)。

### [Component 3] `roadmap_p0_to_p7.md`
`plans.md` と同一のフェーズ構成。各フェーズに**受入条件・テスト・パリティ台帳の更新**を紐付ける。
- **P0**: 共有モジュール設定、MVI 骨格、`net/ConnectivityState.kt`、テスト基盤(Compose UI Test / Paparazzi)。
- **P1**: `TravelMode{Bicycle,Walk,Car}`、代替経路(最大3、ペナルティ法)、`TravelTimeEstimator`、車線案内、リルート。A* の最小倍率下限(0.90)をモードごとに再計算。
- **P2**: `SearchHelper` 分割、オートコンプリート(<120ms)、ジャンル検索、`opening_hours` パーサ(30+ テスト)。
- **P3**: 道路階層・面色・独自スプライト・z5/z10/z14/z17 検証(オフライン/オンライン各)。
- **P4**: 3段シート、複数ルート比較、全幅緑ヘッダー、概要ビュー。
- **P5**: 日出没(NOAA)、`strings.xml`、フォント 1.3x 耐性。
- **P6**: 県別パッケージ管理、オンライン任意補完(3秒タイムアウト)、アトミック更新。
- **P7**: `OfflineParityTest`、`OnlineFallbackTest`、`LayoutStabilityTest`、`StyleLayerOrderTest`、`RouteGoldenTest`、`SearchLatencyTest`、Paparazzi。

### [Component 4] `scorecard_90percent.md`
- 重みは**正規化**: F 合計 82、B 合計 90。`F% = Σ/82`、`B% = Σ/90`、`全体 = (F%+B%)/2`。
- 得点 0 / 0.5 / 1 の客観的な判定基準、証跡(スクリーンショット・テストログ)の紐付け規則。
- **バック 88% の余裕策**: 目標値の計算上、バックは 88% ちょうどで余裕がない。**B5(目標 0.8)と B6(目標 0.7)は、いずれか一方でも未達なら全体 90% を割り得る**。したがって B1〜B4・B7〜B9 の達成を優先し、B5/B6 は「静的データで取れる範囲」(営業時間のオフライン判定、時間帯別速度)を先に完成させる。残りの項目での上積み余地(B3 を 0.95、B4 を 0.95)を台帳に併記する。
- 目標値での試算(参考): F ≈ 93.5%、B ≈ 88%、全体 ≈ 90.8%。

---

## 5. モジュール共有・コード複製の扱い

- `application/` / `test/` / `re_test/` の 3 系統に**同一ロジックを複製しない**。
- `routing-core` と `osm-importer` は、`application/` の実装を**正**とし、Gradle の `includeBuild` またはサブプロジェクト参照で共有する(複製コピーはしない)。
- テストの乖離: `application/routing-core` は **@Test 74 件**、`test/routing-core` は **69 件**(実測)。`re_test` は 74 件ベースで開始し、`test/` 側にだけあるテストは追加分として取り込む。取り込み後に全件成功を確認する。
- `.graph` / `.graph.idx` の形式変更はバージョン番号を上げ、旧版には明示エラーと再生成案内を出す。

---

## 6. osmdroid 撤廃の条件(依存機能の代替)

osmdroid は以下に使われている。**代替が動くことを確認するまで削除しない。**

| 依存機能 | MapLibre での代替 | 完了条件 |
|---|---|---|
| 地理院標準タイル | `raster` source(`{z}/{x}/{y}` + MBTiles/ローカルキャッシュ) | 機内モードで表示。帰属表示が常時出る |
| OpenCycleMap / 陰影起伏 | 同上(レイヤー切替) | 切替で崩れない。陰影起伏の空グリッド問題(DEF)を再発させない |
| `tiles/cache.db`(osmdroid 形式) | §2-C 決定: 移行せず破棄・再取得(MBTiles 新設) | 既存データで起動できる、または再取得手順を UI で案内 |
| 経路回廊タイル事前取得(`TileDownloader`) | 回廊の bbox を列挙して取得、ローカル MBTiles に書込 | 同期制御(BUG-78)、タイムアウト、SHA/件数検証 |
| タップ座標・マーカー・ルート線 | MapLibre `Symbol/Line` レイヤー、`screenLocationToLatLng` | パリティ台帳でチェック済み |

- **ベクタータイルのオフライン配布**は §2-B の決定後に仕様化する(データは APK に同梱しない)。決定前は P3 に着手しない。
- タイル欠損域は背景色(`#E8E8E8`)、低ズームのオーバーズーム。透明/黒の穴を出さない。

---

## 7. Verification Plan (検証計画)

### ドキュメント段階(承認前)
1. `re_test/` に 5 ファイル(README / architecture_spec / roadmap / scorecard / parity_checklist)が存在する。
2. 相互リンクが切れていない(`grep` で全 `](…)` を実在確認)。
3. **スコアカードの整合**: F 項目 9、B 項目 9、重み合計 82 / 90、試算が F≈93.5% / B≈88% / 全体≈90.8% と一致する。
4. `plans.md` の全フェーズ・全項目(F1〜F9、B1〜B9)・§2 のオフライン/オンライン表が漏れなく反映されている。
5. `cautions.md` の全節との対応表(`cautions → 反映先ファイル`)が README かロードマップにある。
6. 数値の裏取り: MapLibre バージョンは `libs.versions.toml`、テスト件数は `@Test` 件数(74 / 69)で再確認。

### 実装段階(承認後)
- 各フェーズ終了時: `./gradlew :routing-core:test :osm-importer:test :app:testDebugUnitTest` 全件成功、実機(Galaxy S21)をオフライン/オンライン両方で確認、スクリーンショットを `re_test/screenshots/` に保存、パリティ台帳と採点台帳を更新、人間へ報告して**コミット指示を待つ**。

---

## 8. リスクと対策まとめ

| リスク | 対策 |
|---|---|
| 検証済み機能の消失 | パリティ台帳+段階的置換 |
| osmdroid 撤去でラスター/キャッシュが壊れる | §6 の条件付き撤去 |
| コード 3 系統化 | 共有モジュール(§5)、正の系統の決定(§2-A) |
| 未コミット作業の喪失 | 人間によるコミット/退避(§3-1) |
| GPS 更新で全面再コンポーズ | 高頻度状態の分離(§4-2) |
| バック 88% の余裕不足 | B1〜B4/B7〜B9 優先、B5/B6 は静的データで先行 |
| 数値の誤り | 検証計画 §7-6 で裏取り |
