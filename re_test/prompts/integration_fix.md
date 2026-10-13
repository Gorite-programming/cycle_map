# 統合修正プロンプト(夜間実行の是正)

> 新規セッションのエージェントに、下の `---` 以降をそのまま渡す。

---

あなたは `/Users/gorite/Desktop/cycle_map/re_test/` の**是正担当**です。前回の夜間自律実行は `FINAL_REPORT.md` で互換度 90.15% と報告しましたが、監査の結果、**実態は約 55%** でした。原因は「新規クラスを作ってテストしただけで、アプリ本体に接続していない」ことと、スクリーンショットや採点が事実とずれていたことです。あなたの仕事は、**実際に動く状態に接続し、報告を事実に直す**ことです。

## 0. 最初に読む
1. `/Users/gorite/Desktop/cycle_map/AGENTS.md`
2. `re_test/overhaul_plan.md`(§2-A/B/C は決定済み。覆さない)
3. `re_test/plans.md`、`re_test/cautions.md`、`re_test/parity_checklist.md`
4. `re_test/FINAL_REPORT.md` と `re_test/NIGHT_LOG.md`(前回の主張。**鵜呑みにしない**)
5. `re_test/scripts/check_wiring.sh`(接続検査。§3 で使う)

## 1. 監査で判明した事実(あなたが直す対象)
1. **巨大ファイルが未分割**: `MapScreen.kt` 4,133 行、`CyclingPanels.kt` 2,232 行、`SearchHelper.kt` 1,355 行、`VectorMapView.kt` 約 1,019 行、`Prefectures.kt` 約 711 行。P0-1/P0-2(各ファイル 500 行以内)は未達成。
2. **未接続コード**: 次のクラスは、定義ファイルとテスト以外のどこからも参照されていない。
   `AdaptiveRerouteManager`、`TravelTimeEstimator`、`PenaltyMultiRouteFinder`、`OpeningHoursParser`、`SearchRanking`、`OfflinePackageManager`、`OnlineFeatureGate`、`MapViewModel`、`MapTopBar`、`MapControls`、`NavigationScreen`(`ThreeStageBottomSheet` / `RouteSelectionSheet` は `MapSheets.kt` 内だけ)。`SolarCalculator` は `DayNightManager` からのみ。
3. **スクリーンショットが主張と不一致**: `P1_device.png` は地図が空で「県データグラフを切替・読み込み中」の画面(ナビ・車線は写っていない)。`P4_device.png` は従来のラスター地図+旧ダーク UI(3 段シートも経路カードもない)。P4〜P6 はほぼ同じ画面。
4. **P3-0 未完了**: スタイルの `glyphs`/`sprite` はプレースホルダ化されたが、`assets` に日本語フォントがなく、Protomaps 用のアイコン(sprite)も作られていない。
5. **テストが弱い**: `P7ParityAndFallbackTest` は状態オブジェクトの値比較が中心で、UI の挙動を検証していない。`StyleLayerOrderTest` に絶対パス候補が残る。UI/スクリーンショットテストがない。機内モードは実機で検証していない。

## 2. 絶対に守ること
1. **Git 操作は禁止**(`add/commit/push/stash/reset/checkout/clean/rebase`)。読み取り(`status/diff/log`)のみ可。
2. 変更してよいのは **`re_test/` 配下のみ**。`application/`、`test/`、`AGENTS.md` は読み取り専用。共有元の修正が必要なら `re_test/proposals/` に提案書を書く。
3. 削除・移動は、自分が作ったファイルを除き禁止。`data/*.pbf`、`work/`、`venv/`、`local.properties`、keystore、`test/sample_img/`、`test/now.md` には触れない。
4. Google のタイル・アイコン・ロゴ・スクリーンショットを使わない。オフライン動作を壊さない。
5. 端末の設定やアプリデータを変える操作(`pm clear`、`adb uninstall`、機内モードの切替など)は禁止。
6. **偽の合格を作らない**(§4 を厳守)。

## 3. 採点ルール(最重要)
- **加点できるのは、アプリ本体から実際に呼ばれ、かつ画面または動作で確認できる機能だけ**。定義とユニットテストだけでは**加点 0**。
- 加点の前に必ず `cd re_test && ./scripts/check_wiring.sh` を実行し、対象クラスが `ok`(本体から参照)であることを確認する。`UNWIRED` や `OVER500` が残る間は、該当項目を 0.5 以下にする。
- スクリーンショットは**その機能が写っている**ものだけを証跡にする。別の画面を流用しない。ファイル名は `<task-id>_{offline,online}.png` とし、何が写っているかを `NIGHT_LOG.md` に 1 行で書く。
- 実機で確認できなかった項目は「未確認(実機)」と書き、加点は 0.5 を上限にする。
- `FINAL_REPORT.md` と `plans.md` §9 の過去の数値は**すべて破棄**して、事実に基づいて再計算する。`plans.md` §1 の式(F は 82、B は 90 で割る)を使う。

## 4. 作業(この順番。各タスクの完了ごとにゲートを通す)

### T1. 巨大ファイルの分割(挙動を変えない)
- `MapScreen.kt` を `ui/map/` 配下の複数ファイルへ分割(`MapTopBar`・`MapControls`・`MapOverlays`・`MapSheets`・`NavigationScreen`・`MapState` を**実際に使う**)。各ファイル 500 行以内。
- `CyclingPanels.kt`、`SearchHelper.kt`(`search/` へ)、`VectorMapView.kt`、`Prefectures.kt` も 500 行以内に分割する。
- 移動のみ。ロジックの変更を混ぜない。前後で `./gradlew :app:testDebugUnitTest` が同じ結果。
- 完了条件: `check_wiring.sh` の `OVER500` が 0。分割前後の実機スクリーンショットが同じ。

### T2. 状態管理の接続(MVI)
- `MapViewModel` を `MapScreen` から使う。低頻度の `MapUiState` と、高頻度の `LocationUiState`・`NavProgressState` は別の `StateFlow` にする(`plans.md` P0-7)。
- `OnlineFeatureGate` と `ConnectivityState` を、`MapScreen` のネットワーク状態表示(レイヤーボタン付近の小アイコン)に接続する。バナーは出さない。

### T3. バック機能の接続
- 経路: `TravelMode` を経路探索の呼び出し口に接続(UI でモード選択)、`PenaltyMultiRouteFinder` で複数ルート、`TravelTimeEstimator` を所要時間表示に使う。
- リルート: `AdaptiveRerouteManager` を既存の逸脱検知の位置に組み込む。再探索中は旧ルートを消さない。
- 検索: `SearchNormalizer`・`SearchRanking`・`GenreDictionary` を `SearchHelper` の呼び出しに差し替え(旧ロジックを残す場合は、新旧を切り替える内部フラグを置き、既定を新にする)。検索 DB に `opening_hours` 列がなくても落ちないこと。
- POI 詳細: `OpeningHoursParser` で「営業中/営業時間外」を端末時計で判定し、判定不能なら何も出さない。
- データ管理: `OfflinePackageManager` を設定画面の「データ」に接続(導入済み県・容量・整合性・削除)。
- テーマ: `DayNightManager` が `SolarCalculator` の結果で実際にテーマを切り替えるようにし、地図スタイルも同期。

### T4. UI の接続(Google Maps 風)
- 3 段シート(`ThreeStageBottomSheet`)を場所詳細に、`RouteSelectionSheet` を経路選択に実際に表示する。
- ナビ画面: `NavigationScreen` に、全幅ヘッダー・車線バー・次の次の案内・フッター(ETA/残時間/残距離)・概要ビュー・到着画面を表示する。
- 文字列は `strings.xml` へ。`Color(0x…)` の直書きは、触った画面から置き換える。

### T5. P3-0 の完了(オフラインのフォントとアイコン)
- 日本語対応のフォント(glyph PBF、必要な range のみ。OFL を `LICENSE.txt` に明記)と、Protomaps 用 sprite(light/dark、`.json`+`.png`+`@2x`)を用意し、`CycleMap/assets/{fonts,sprites}/` を参照させる。端末へ置く手順を `re_test/DATA_DEPLOY.md` に書く。
- フォントやアイコンを用意できない場合は、**できない理由を `NIGHT_LOG.md` に書き**、F4 は 0.5 以下にする。

### T6. テストの質を上げる
- Compose UI テスト(`ui-test-junit4`)で、分割後の主要画面(検索、シート、経路選択、ナビ)を描画・操作する。
- `OfflineParityTest`・`LayoutStabilityTest` は、オフライン⇄オンライン切替で実際の UI 要素の座標が動かないことを確認する(状態値の比較で済ませない)。
- `StyleLayerOrderTest` から絶対パスを除く(相対パス+`user.dir` 基準)。

### T7. 実機検証と報告の是正
- `adb devices` で S21 を確認。見えれば `:app:installDebug`(`adb install -r`)し、各機能の画面を撮る。見えなければ「未確認(実機)」と書く。
- `re_test/screenshots/` の古い証跡のうち、主張に合わないもの(P1・P4〜P6 など)を `FINAL_REPORT.md` で「証跡不適合」と明記し、新しい証跡に置き換える(古いファイルは削除しない)。
- `parity_checklist.md` を事実に合わせて更新(未接続・未確認は ✅ にしない)。
- `FINAL_REPORT.md` を**書き直す**: 前回の主張と監査結果の差分、今回の接続状況(`check_wiring.sh` の出力を貼る)、再計算した F% / B% / 全体%、未達項目、人間の判断が必要な事項。

## 5. 各タスクのゲート
1. `cd re_test && ./gradlew :routing-core:test :osm-importer:test :app:testDebugUnitTest` が全件成功。
2. `./gradlew :app:assembleDebug` が成功。
3. `./scripts/check_wiring.sh` で、そのタスクの対象クラスが `ok`。
4. 新しい振る舞いには新しいテストを足す。
5. `NIGHT_LOG.md` に追記(開始・完了・失敗・保留。変更ファイル、テスト結果、実機、メモ)。

## 6. 詰まったとき
- 同じエラーで 3 回直らなければ保留にして記録し、依存しない別タスクへ。依存するものはスキップ。
- 仕様の曖昧さは `overhaul_plan.md` → `plans.md` → `cautions.md` の順で解釈。決まらなければ `test/` の挙動を維持する側を選ぶ。
- ビルドが壊れた状態で次へ進まない。直せなければ、自分の変更を手作業で戻す。

## 7. 終了条件
T1〜T7 を終えるか、進められなくなったら、`FINAL_REPORT.md`(書き直し済み)を完成させて**停止**する。Git 操作はしない。質問せず、判断は上記のルールで行い、**記録を残すこと**を最優先にする。

最初のタスクは T1 です。始めてください。
