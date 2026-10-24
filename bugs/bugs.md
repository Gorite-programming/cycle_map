# CycleMap 全ファイル精査レポート — コードバグおよびUI/UX懸念点一覧

- **精査日**: 2026-10-24
- **対象リポジトリ**: `/Users/gorite/Desktop/cycle_map`（`application/`、ルートパイプライン、`scripts/`、`sandbox/data-tool/` 全ファイル）
- **ベースコミット**: `f2bd0da` + 作業ツリー内の未コミット変更（計39ファイル）
- **備考**: 旧系統の改修済みバグ（`Archives/application/bugs.md` / `bugs_archive.md` 記載の `BUG-01`〜`BUG-85` のうち現行コードで解消済みのもの）は除外し、**現行コードに実在するバグ・退行・未配線ロジック・UI/UX懸念点**のみを4段階の重要度（`Critical` / `High` / `Medium` / `Low`）に分類して記載しています。

---

## 概要サマリーテーブル

| ID | 重要度 | 区分 | 対象ファイル (行番号) | 概要 |
| :--- | :---: | :---: | :--- | :--- |
| **CRIT-01** | 🔴 Critical | Code Bug / UI-UX | `MapViewsContainer.kt` (L219–274) | エリア選択ドラッグで状態が初期値にリセットされ、かつベクター地図時に誤ったProjectionを参照するためエリアDLが100%動作不能 |
| **CRIT-02** | 🔴 Critical | Code Bug / UI-UX | `MapGraphEffects.kt` (L87–97, L168–175), `MapRouteCalculation.kt` (L83, L199) | 県グラフ自動切替時の競合により保留ルートの探索ジョブが即キャンセルされ、スピナーが永久停止 |
| **CRIT-03** | 🔴 Critical | Code Bug | `MapScreen.kt` (L197–213) | 同一ノードへの1点ルート（出発地≒目的地）で `LatLngBounds.Builder.build()` が例外を投げメインスレッドクラッシュ |
| **CRIT-04** | 🔴 Critical | Code Bug (Pipeline) | `OsmImporter.kt` (L756–776), `build_prefectures.py` (L508–516) | `osm-importer` の標準実行が `.graph.idx` を生成せず、`build_prefectures.py --force` が常に異常終了 |
| **HIGH-01** | 🟠 High | Code Bug / UI-UX | `Prefectures.kt` (L307–336, L422–453), `MapRoutingActions.kt` (L97–115) | `TileDownloader` がHTTP通信失敗・オフライン時も進捗を進め「100%ダウンロード済み」と誤記録・表示 |
| **HIGH-02** | 🟠 High | Code Bug / UI-UX | `VectorMapView.kt` (L162–186), `PrefectureSelectors.kt` (L187–228) | GPS未測位起動時や `Hiroshima_osm.pmtiles` 不在時にベクター地図の初期化が中断し操作不能に陥る |
| **HIGH-03** | 🟠 High | Code Bug / UI-UX | `MapViewsContainer.kt` (L80–159), `MapRouteCalculation.kt` (L135–169, L335–351) | ベクター⇔ラスター地図切替でルート線・ピン・カメラ位置が消失し、`CycleMapView.onDetach()` 未呼び出しでメモリリーク |
| **HIGH-04** | 🟠 High | Code Bug / UI-UX | `RideHistoryView.kt` (L78–86, L262), `MapSettingsDialogs.kt` (L112–144), `MapScreenScaffold.kt` (L211–218) | GPX履歴シートがメインスレッドで全GPXを同期パースし、かつ標準のベクター地図で「地図に表示」が一切機能しない |
| **HIGH-05** | 🟠 High | Code Bug / UI-UX | `LocationTrackingService.kt` (L214–272), `GpsHealthMonitor.kt` (L18–65) | `GpsHealthMonitor` がサービスに未接続のため `GpsSignalStatus.LOST` が永久に発火せず、GPSロスト警告バナーが死んでいる |
| **HIGH-06** | 🟠 High | Code Bug | `LazyMappedRouting.kt` (L216), `HsaRouting.kt` (L44), `RealRoutePlanner.kt` (L131) | `arterialPenaltyMultiplier` の適用対象（幹線 vs 非幹線）が `HsaOptions` と `RealRoutePlanner` で逆転 |
| **HIGH-07** | 🟠 High | Code Bug | `MapScreen.kt` (L292–308) | `viewModel.updateNavProgress` に常に `inst.firstOrNull()` / `inst.getOrNull(1)` を渡しており、VM側の案内ステップが進まない |
| **HIGH-08** | 🟠 High | UI-UX / Code Bug | `MapControlsOverlay.kt` (L63–83, L145–192, L228) | 縦画面で2D/3D切替ボタン・エリア選択ボタン・長押し連続ズームが欠落し、ベクター地図の回転・ズーム状態が非リアクティブ |
| **MED-01** | 🟡 Medium | Code Bug | `ReverseGeocoder.kt` (L148–152) | 政令指定都市の区ポリゴン検索で `prefectureName` / `parentCity` を絞っておらず、広島市と岡山市の同名区（中区・東区・南区）を誤判定 |
| **MED-02** | 🟡 Medium | Code Bug / UI-UX | `VectorMapThemeHelper.kt` (L21–67), `VectorMapStyles.kt` (L413) | 昼夜モード動的切替時に地図の道路・地名・POIラベル文字色とハロー色が更新されず視認性が崩壊 |
| **MED-03** | 🟡 Medium | Code Bug / UI-UX | `MainActivity.kt` (L42, L59–67) | `isSystemInDarkTheme()` を取得しながら `DayNightResolver.resolveIsDark` に `false` をハードコード |
| **MED-04** | 🟡 Medium | Code Bug | `ElevationRepository.kt` (L88–108, L182–187) | 標高グラフのサンプリングが線形補間なしで階段状になり、かつ地理院DEMの欠損値（`0x800000`）を標高 `0.0m` の「DEM実測」として誤処理 |
| **MED-05** | 🟡 Medium | Code Bug | `VoiceGuidance.kt` (L333–360, L456–473) | ルート逸脱復帰後に `"REROUTE"` キーがクリアされず2回目以降の逸脱音声が鳴らない。また逸脱中は到着音声もブロックされる |
| **MED-06** | 🟡 Medium | Code Bug / UI-UX | `JmaWeatherParser.kt` (L128–140), `WeatherRepository.kt` (L155–220) | 気象庁JSONの日中最高気温（`09:00`定義）を「朝の最低気温」と誤表示。またキャッシュキーが県単位で南部/北部の予報が混ざる |
| **MED-07** | 🟡 Medium | Code Bug | `MapLifecycleEffects.kt` (L215–223), `MapOverlayHelper.kt` (L110, L219) | ラスター地図のPOIピンタップ時に `cat.name.lowercase()` を渡すため `PoiCategory.forCategory()` が `null` になりアイコン・色が壊れる |
| **MED-08** | 🟡 Medium | Code Bug / UI-UX | `VectorMapView.kt` (L115, L411–439) | `lastFollowLocation` が未使用で、停止中に地図をパンした後 `followLocation` が復帰しても距離差 `0m` と判定され現在地に戻らない |
| **MED-09** | 🟡 Medium | UI-UX / Code Bug | `MapScreenStateFactory.kt` (L127, L139, L170–171, L219, L245) | 音声案内モード・自動リルート・ヘディングアップ・地図レイヤー・POI表示設定が `SharedPreferences` に保存されず再起動・回転で消失 |
| **MED-10** | 🟡 Medium | UI-UX | `MapPanelsHost.kt` (L68–99, L250–289), `MapSheets.kt` (L86–95), `GoogleNavHeader.kt` (L207–228) | `ThreeStageBottomSheet` Peek固定高によるボタン見切れ、コース逸脱バナーのステータスバー重なり、未配線 `RoutePanelSheet` の重複 |
| **MED-11** | 🟡 Medium | Code Bug / UI-UX | `Isometric3dSimulationView.kt` (L159–173) | 同一 `Box` 上で `detectTransformGestures` が1本指ドラッグを消費するため、後続の `detectDragGestures`（3D角度回転）が動かない |
| **MED-12** | 🟡 Medium | Code Bug | `Routing.kt` (L101–128), `MappedRouting.kt` (L30–98, L227), `AltRouting.kt` (L276), `RealRoutePlanner.kt` (L116, L162) | `AStarRouter` / `MappedRoadGraph` の古いキュー要素再展開、空グラフクラッシュ、`BidirectionalRouter` の $O(V^2)$ 走査、代替ルート獲得標高 `0.0` 固定 |
| **MED-13** | 🟡 Medium | Code Bug (Pipeline) | `build_search_db.py` (L176, L343), `overture.py` (L236–253), `merge.py` (L197) | フォールバック検索DBの `search_text` 未正規化、Overture取込時の県ポリゴン判定欠落、`merge.py` の漢数字「十→1」変換による一丁目/十丁目の誤統合 |
| **LOW-01** | 🟢 Low | UI-UX | `TopSearchBar.kt` (L47, L166–232), `MapTopOverlays.kt` (L75–78), `LocationSummarySheet.kt` (L52) | 本番UIに個人名 `"拓真"`、ダミーチップ `"🚗 自宅・4 分"` / `"🔍 マップに相談"`、フォールバック地名 `"井口五丁目"` がハードコード |
| **LOW-02** | 🟢 Low | UI-UX | `DestinationSearchDialog.kt` (L327–333), `QuickSpotSheet.kt` (L153, L259, L361), `DeveloperOptionsScreen.kt` (L140) | 検索ダイアログの無反応マイクボタン、`QuickSpotBottomSheet` のダークモード配色崩れ、`isEngineReady = true` ハードコード |
| **LOW-03** | 🟢 Low | Code Bug / Cleanup | `MapGraphEffects.kt` (L178–207), `MapTrackingEffects.kt` (L85–103), `ui/map/*` | `BroadcastReceiver` 内の古いラムダ捕捉、および本番未接続の重複UIコンポーネント群（`NavigationScreen.kt` 等）の混在 |

---

## 🔴 Critical（クラッシュ・主要機能の完全停止・パイプライン破綻）

### CRIT-01: エリア選択ダウンロードのドラッグ状態リセットおよび誤ったProjection参照によりエリア選択が100%機能しない
- **区分**: Code Bug / UI-UX
- **場所**: [MapViewsContainer.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/layout/MapViewsContainer.kt#L219-L274)
- **詳細**:
  1. `pointerInput(isAreaSelectMode)` ブロック内で、値渡しパラメータの `areaDragState` を `rememberUpdatedState` なしで直接参照しています。
     - `onDrag`（L232）で `onAreaDragStateChange(areaDragState.copy(currentX = ..., currentY = ...))` を呼ぶ際、`areaDragState` はクロージャ生成時の初期値 `AreaDragState(0f, 0f, 0f, 0f, active = false)` のまま固定されているため、ドラッグした瞬間に `anchorX = 0f, anchorY = 0f, active = false` に上書きされます。
     - `onDragEnd`（L239）でも `val drag = areaDragState` が初期値（全座標 `0f`）を読むため、`if ((right - left) > 20f && (bottom - top) > 20f)` が常に `false` となり、`onAreaSelected(bounds)` に絶対到達しません。
  2. さらに `onDragEnd`（L246）は座標変換に `latestMapViewForArea?.projection`（osmdroid の `CycleMapView`）のみを使用しています。デフォルトの `MapLayer.VECTOR_LIBERTY` 選択時は `CycleMapView` がマウントされておらず `null`（または過去に表示していた別の場所のProjection）になるため、仮にドラッグ状態を修正してもベクター地図上ではエリア選択が動作しません。

### CRIT-02: 県グラフ切替時の `DisposableEffect(mappedGraph)` との競合により保留ルート計算が即キャンセルされスピナーが永久停止する
- **区分**: Code Bug / UI-UX
- **場所**:
  - [MapGraphEffects.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/effects/MapGraphEffects.kt#L87-L97)
  - [MapGraphEffects.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/effects/MapGraphEffects.kt#L168-L175)
  - [MapRouteCalculation.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/navigation/MapRouteCalculation.kt#L83-L93)
  - [MapRouteCalculation.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/navigation/MapRouteCalculation.kt#L199)
- **詳細**:
  1. 別県の目的地へルート探索を行う際、`startGraphSwitch` 内のメインスレッドコールバックで `onMappedGraphChanged(loaded.first)`（L87）を呼び、その直後の同一フレーム内（L95–97）で `pendingRouteRef.getAndSet(null)?.let { runRouteCalculation(...) }` を実行して新しい `routeJob` を `routeJobRef` にセットし、`isCalculatingRoute = true` にします。
  2. しかし次の Compose フレームで `mappedGraph` ステートの変更に伴う `DisposableEffect(mappedGraph)`（L168–175）の `onDispose`（旧グラフのクリーンアップ）が走り、`val job = routeJobRef.getAndSet(null); job?.cancel()` を実行してしまいます。これにより、**今しがた新グラフ用に開始したばかりの `routeJob` がキャンセルされます**。
  3. さらに `executeRouteCalculation`（`MapRouteCalculation.kt` L199 等）では、`if (routeJob.isCancelled()) return@launch` で早期リターンする際、`onCalculatingRouteChanged(false)` を呼ばずに終了するため、`isCalculatingRouteState` が `true` のまま永久にロックされ、「最適サイクリングルートを探索中…」の画面から抜け出せなくなります。

### CRIT-03: 出発地と目的地が同一ノードにスナップされた場合（1点ルート）、`LatLngBounds.Builder.build()` が例外を投げてアプリがクラッシュする
- **区分**: Code Bug
- **場所**: [MapScreen.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/screens/MapScreen.kt#L197-L213)
- **詳細**:
  - 現在地付近をタップしてルート探索を実行し、出発地と目的地が同一グラフノード（`startIndex == goalIndex`）にスナップされた場合、`LazyMappedRoadGraph.route()` は要素数1の座標リストを返します。
  - `MapScreen.kt` L197 の `LaunchedEffect(state.routePointsState.value.size, state.isNavigatingState.value)` は `pts.isNotEmpty()` のみをチェックして `LatLngBounds.Builder().include(...).build()` を呼び出します。
  - MapLibre の `LatLngBounds.Builder.build()` は含まれる点が2点未満の場合に `InvalidLatLngBoundsException` をスローするため、UIスレッドで未捕捉例外が発生しアプリが即座にクラッシュします。

### CRIT-04: `OsmImporter.kt` の標準グラフ生成モードが `.graph.idx` を出力せず、`build_prefectures.py --force` が必ず失敗する
- **区分**: Code Bug (Data Pipeline)
- **場所**:
  - [OsmImporter.kt](file:///Users/gorite/Desktop/cycle_map/application/osm-importer/src/main/kotlin/com/gorite/cyclemap/importer/OsmImporter.kt#L756-L776)
  - [build_prefectures.py](file:///Users/gorite/Desktop/cycle_map/build_prefectures.py#L508-L516)
- **詳細**:
  - `AGENTS.md` および `build_prefectures.py`（L509–516）は、`osm-importer --bbox none <in.osm.pbf> <out.graph>` の1回の呼び出しで `<out.graph>` と `<out.graph.idx>` の両方が生成されることを前提としており、実行直後に `if not graph.exists() or not idx.exists(): raise RuntimeError(...)` で検証しています。
  - しかし現行の `OsmImporter.kt` の `main()`（L766–767）は `GraphBinaryWriter().write(graph, output)` のみを呼び、`writeGraphIndex(output, File(output.path + ".idx"))` を呼び出していません（`writeGraphIndex` は `--index` フラグ指定時のみ実行される実装になっています）。
  - このため、`./gradlew :osm-importer:installDist` で CLI を更新した後に `build_prefectures.py --force` を実行すると、すべての県で `.graph.idx` が生成されず 100% 失敗します。

---

## 🟠 High（データ不整合・主要UIの欠落・メモリリーク・無言の失敗）

### HIGH-01: `TileDownloader` がオフラインやHTTPエラー時でもダウンロード成功としてカウントし、0枚取得でも「ダウンロード済み」と記録・表示する
- **区分**: Code Bug / UI-UX
- **場所**:
  - [Prefectures.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/data/Prefectures.kt#L307-L336)
  - [Prefectures.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/data/Prefectures.kt#L422-L453)
  - [MapRoutingActions.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/navigation/MapRoutingActions.kt#L97-L115)
- **詳細**:
  - `TileDownloader.downloadTiles`（L422–453）および `downloadRouteCorridor`（L307–336）において、各タイルのHTTP取得がタイムアウト・機内モード・HTTPエラー等で2回とも失敗し `saved == false` のまま終わった場合でも、`report()`（`completed.incrementAndGet()`）を呼び出しています。
  - ワーカースレッド終了後、実際に保存できた枚数や失敗枚数を一切確認せずに `onDone?.invoke(total)` を呼び出すため、**機内モード下で1枚もタイルを取得できなくても `DownloadStatusManager.markDownloaded` が呼ばれて「ダウンロード済み」として永続化**され、ルート回廊取得も `"保存完了 (N枚)"` と表示されます。

### HIGH-02: GPS未測位での初回起動時や `Hiroshima_osm.pmtiles` 不在時にベクター地図の初期化が早期リターンし、地図操作イベントが一切登録されない
- **区分**: Code Bug / UI-UX
- **場所**:
  - [VectorMapView.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/mapview/VectorMapView.kt#L162-L186)
  - [PrefectureSelectors.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/data/PrefectureSelectors.kt#L187-L228)
- **詳細**:
  1. `RoutingGraphSelector` や `SearchDbSelector` には配置済みファイルを自動探索する `findFirstAvailableGraph` / `findFirstAvailableDb` がありますが、`TileSourceSelector` にはフォールバック探索がありません。また `resolveTileFile`（`PrefectureSelectors.kt` L214–224）は広島県について `Hiroshima_osm.pmtiles` のみをチェックし、`build_prefectures.py` がパッケージ化する `Hiroshima.pmtiles` / `hiroshima.pmtiles` をチェックしません。
  2. アプリ起動直後（GPS測位前）は `initialTarget` が広島市（`34.3965, 132.4596`）になるため、端末に山口県のデータ（`yamaguchi.pmtiles`）しか入っていない場合や `Hiroshima.pmtiles` というファイル名で配置されている場合、`resolveOfflinePmtilesPath` が `null` を返します。
  3. すると `VectorMapView.kt` L176–182 で `offlineDataMissing = true` にして `return@getMapAsync` で早期リターンしてしまい、カメラ移動リスナー・タップリスナー・長押しリスナーが一切登録されず、後からGPSが測位されてもベクター地図が機能しない状態になります。

### HIGH-03: ベクター地図⇔ラスター地図（地理院・OSM・地形）切替時にルート線・マーカー・カメラ位置が失われ、`CycleMapView` がリークする
- **区分**: Code Bug / UI-UX
- **場所**:
  - [MapViewsContainer.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/layout/MapViewsContainer.kt#L80-L159)
  - [MapRouteCalculation.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/navigation/MapRouteCalculation.kt#L135-L169)
  - [MapRouteCalculation.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/navigation/MapRouteCalculation.kt#L335-L351)
- **詳細**:
  1. デフォルトの `MapLayer.VECTOR_LIBERTY` 表示中は `CycleMapView`（osmdroid）がコンポジションに存在せず `state.mapViewState.value == null` です。この状態でルートを計算すると、osmdroid 用の `routeLine`（`Polyline`）や `goalMarker` / `startMarker` / `waypointMarkers` の生成がスキップされます。
  2. その後レイヤーを「地理院標準」「自転車向け」「地形起伏」に切り替えると、`CycleMapView` が新規生成されますが、初期座標が山口市（`34.18, 131.47`、ズーム13.0）にハードコードされており、探索済みのルート線やピンも一切描画されません。
  3. 逆にラスター地図から `VECTOR_LIBERTY` に戻す際、`AndroidView` の `onRelease`（L156）で `view.onPause()` のみを呼び `view.onDetach()` を呼んでおらず（過去の `BUG-85` の退行）、タイルプロバイラスレッドとビットマップがメモリリークします。また `state.mapViewState.value = null` に戻していないため、破棄済み `CycleMapView` インスタンスがステートに残り続けます。

### HIGH-04: 走行履歴（`RideHistorySheet`）がメインスレッドで全GPXファイルを同期XMLパースし、かつ標準のベクター地図で「地図に表示」が機能しない
- **区分**: Code Bug / UI-UX
- **場所**:
  - [RideHistoryView.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/cycling/RideHistoryView.kt#L78-L86)
  - [MapSettingsDialogs.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/components/MapSettingsDialogs.kt#L112-L144)
  - [MapScreenScaffold.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/layout/MapScreenScaffold.kt#L211-L218)
- **詳細**:
  1. `RideHistorySheet`（`RideHistoryView.kt` L78–86）はコンポジション内の `remember(gpxDir, refreshTrigger)` で `gpxDir.listFiles()?.mapNotNull { GpxParser.parse(it) }` を同期実行しています。GPXファイルが増えるとシートを開いた瞬間にUIスレッドが数秒間フリーズ（ANRの危険）します。なお、`MapScreenScaffold.kt`（L211–218）で `Dispatchers.IO` を使って `state.gpxHistoryState.value` に読み込んでいる処理は、どこからも参照されないデッドコードになっています。
  2. 走行履歴詳細で「地図に表示」ボタンを押した際の `onShowTrackOnMap`（`MapSettingsDialogs.kt` L112–144）は、osmdroid の `mapView` にのみ `Polyline` を追加しています。デフォルトの `MapLayer.VECTOR_LIBERTY` では `mapView == null`（または非表示）であり、`VectorMapView` にはGPXトラック描画レイヤーが存在しないため、ボタンを押してもシートが閉じるだけで地図上に何も表示されません。

### HIGH-05: `LocationTrackingService` に `GpsHealthMonitor` が組み込まれておらず、`GpsSignalStatus.LOST`（トンネル・屋内でのGPSロスト警告）が絶対に発火しない
- **区分**: Code Bug / UI-UX
- **場所**:
  - [LocationTrackingService.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/tracking/LocationTrackingService.kt#L214-L272)
  - [GpsHealthMonitor.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/tracking/GpsHealthMonitor.kt#L18-L65)
- **詳細**:
  - `GpsHealthMonitor` クラスは5秒間測位なしで `WEAK`、15秒間測位なしで `LOST` に遷移する `tick()` メソッドを備えており、UI側にも `GpsLostWarningBanner`（`MapNotificationBanners.kt` L95）が用意されています。
  - しかし `LocationTrackingService` 内で `GpsHealthMonitor` は一切インスタンス化・呼び出しされておらず、定期タイマーも存在しません。`LocationTrackingService` は `onLocationResult` が届いたときに `accuracy > 100f` なら `WEAK`、それ以外は `HEALTHY` を通知するだけなので、トンネルや屋内でGPS信号が完全に途絶えてコールバック自体が来なくなると、`lastStatus` は永久に `HEALTHY` のまま維持され、`GpsSignalStatus.LOST` や推測航法（`DeadReckoner`）は本番環境で一切動作しません。

### HIGH-06: `LazyMappedRouting.kt` の `arterialPenaltyMultiplier` の意味が `HsaRouting.kt` と `RealRoutePlanner.kt` の間で逆転している
- **区分**: Code Bug
- **場所**:
  - [LazyMappedRouting.kt](file:///Users/gorite/Desktop/cycle_map/application/routing-core/src/main/kotlin/com/gorite/cyclemap/routing/LazyMappedRouting.kt#L216)
  - [HsaRouting.kt](file:///Users/gorite/Desktop/cycle_map/application/routing-core/src/main/kotlin/com/gorite/cyclemap/routing/HsaRouting.kt#L44)
  - [RealRoutePlanner.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/real/RealRoutePlanner.kt#L131)
- **詳細**:
  - `LazyMappedRouting.kt` L216 では `val arterialBias = if (isArterial(record.type)) arterialPenaltyMultiplier else 1.0` となっており、`arterialPenaltyMultiplier > 1.0` のときに**幹線道路（primary/secondary/tertiary）にペナルティを課す**実装になっています。
  - `RealRoutePlanner.kt` L131（裏道ルート探索）はこの仕様（幹線道路を避けるため `1.30` を指定）を前提としていますが、`HsaOptions`（`HsaRouting.kt` L44、HSA\* の階層プリパス）では幹線道路へ誘導するために非幹線道路へペナルティを課す意図で `prepassArterialPenaltyMultiplier`（1.12〜1.35）を渡しています。結果として、HSA\* プリパスでは幹線道路側がペナルティを受けて非幹線道路ばかりを探索する逆転現象が起きています。

### HIGH-07: `MapScreen.kt` の `viewModel.updateNavProgress` が常にルートの最初の案内（index 0, 1）を渡し続けている
- **区分**: Code Bug
- **場所**: [MapScreen.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/screens/MapScreen.kt#L292-L308)
- **詳細**:
  - `MapScreen.kt` L301–307 で走行進捗が更新された際、`val inst = state.navigationInstructionsState.value` に対して `progress.distanceFromStartMeters` によるフィルタリングを行わず、常に `inst.firstOrNull()` と `inst.getOrNull(1)` を `viewModel.updateNavProgress(...)` に渡しています。
  - このため、`MapViewModel` 内の `currentInstruction` / `secondaryInstruction` および `laneGuidanceProvider` の車線案内は、どれだけ走行しても出発直後の最初の交差点から一切進みません。

### HIGH-08: 縦画面（Portrait）で2D/3D切替ボタン・エリア選択ボタン・長押し連続ズームが欠落し、ベクター地図のコンパス回転・ズーム可否がリアクティブに更新されない
- **区分**: UI-UX / Code Bug
- **場所**: [MapControlsOverlay.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/layout/MapControlsOverlay.kt#L63-L83) および [L145-L228](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/layout/MapControlsOverlay.kt#L145-L228)
- **詳細**:
  1. `MapControlsOverlay` は `isLandscape == true` のときのみ `GoogleMapsControlColumn`（2D/3D切替・エリア選択・`GoogleZoomPill` を含む）を描画し、通常の縦画面（`!isLandscape`、L145–192）では独自にボタンを並べています。しかし縦画面側には **`onToggleTiltAction`（2D/3Dボタン）と `onAreaSelectToggle`（エリア選択ボタン）が完全に欠落**しており、ズームボタンも長押し連続ズーム非対応の `GoogleZoomButton` が使われています。
  2. L63–83 で `val vectorMap = state.vectorMapState.value` から `vectorMap.cameraPosition.bearing` や `zoom` を直接読み取っていますが、`cameraPosition` は Compose の `State` ではないため、ベクター地図を回転・ズームしても `MapControlsOverlay` が再コンポーズされず、コンパスの向きやズームボタンの有効/無効状態が更新されません。
  3. L228（ナビ中の縦画面コンパス）では `.rotate(-mapBearing)` に `mapBearing.isNaN()` ガードが無く、`mapBearing` が `NaN` の場合に描画例外を引き起こす恐れがあります（L99・L156 にはガードあり）。

---

## 🟡 Medium（機能不具合・表示崩れ・設定揮発・リソース管理）

### MED-01: `ReverseGeocoder.kt` の政令指定都市・行政区ポリゴン参照で県名・親市名を絞っておらず、広島市と岡山市の同名区を取り違える
- **区分**: Code Bug
- **場所**: [ReverseGeocoder.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/data/ReverseGeocoder.kt#L148-L152)
- **詳細**:
  - `AdministrativeBoundaries.ALL.firstOrNull { it.adminLevel == 8 && it.name == resolvedWard.name }` で区の境界ポリゴンを取得していますが、`AdministrativeBoundaries` に岡山市の区が追加された場合や他県データ検索時に、「中区」「東区」「南区」が広島市と岡山市の両方に存在するため、`it.prefectureName == prefectureName && it.parentCity == resolvedCity.name` で絞り込まないと別都市の区ポリゴンで内外判定してしまいます。

### MED-02: 昼夜モードの動的切替（`VectorMapThemeHelper`）で道路ラベル・地名・POIラベルの文字色とハロー色が更新されない
- **区分**: Code Bug / UI-UX
- **場所**:
  - [VectorMapThemeHelper.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/mapview/VectorMapThemeHelper.kt#L21-L67)
  - [VectorMapStyles.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/mapview/VectorMapStyles.kt#L413)
- **詳細**:
  - アプリ起動後にテーマ設定や時間帯変化で `isNightMode` が切り替わった際、`VectorMapThemeHelper.applyNightModeColors` は背景・水域・建物・道路のポリゴン/ライン色のみを変更し、`SymbolLayer`（地名ラベル・道路番号・動的POIラベル `cyclemap-dynamic-poi-symbols`）の `textColor` / `textHaloColor` を一切更新しません。
  - その結果、昼モードで起動してから夜モードに切り替えると「暗い地図背景に黒い文字」、夜モードで起動してから昼モードに切り替えると「白い地図背景に白い文字」となり、文字が読めなくなります。

### MED-03: `MainActivity.kt` で OS のダークモード判定 `isSystemInDarkTheme()` が無視されている
- **区分**: Code Bug / UI-UX
- **場所**: [MainActivity.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/MainActivity.kt#L42-L67)
- **詳細**:
  - L42 で `val isSystemDark = isSystemInDarkTheme()` を取得しているにもかかわらず、L62 の `DayNightResolver.resolveIsDark(...)` 呼び出しでは `isSystemInDarkTheme = false` をハードコードして渡しています。

### MED-04: `ElevationRepository.kt` でルート標高サンプリングが線形補間されておらず、かつ地理院DEMの欠損ピクセル（`0x800000`）を標高 `0.0m` の実測値として扱う
- **区分**: Code Bug
- **場所**: [ElevationRepository.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/data/ElevationRepository.kt#L88-L108) および [L182-L187](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/data/ElevationRepository.kt#L182-L187)
- **詳細**:
  1. `getRouteElevationProfile`（L88–108）は等間隔 `targetDist` に対して `cumDist[coordIdx + 1] < targetDist` でインデックスを進めた後、`coordinates[coordIdx]` と `coordinates[coordIdx + 1]` の間を線形補間せず `coordinates[coordIdx]` の座標をそのまま使っています。長い直線道路では同じノード座標が連続サンプリングされ、標高グラフが不自然な階段状になります。
  2. `decodeElevationFromBitmap`（L182–187）は国土地理院DEM PNGの無効値（No-data: `(r, g, b) = (128, 0, 0)` すなわち `xVal == 0x800000`）を `0.0` メートルとして返しています。内陸や山間部でDEM欠損ピクセルに当たると、標高が突然 `0m` に急落し、`realDemHits` も加算されて「DEM実測」と表示されます（欠損時は `null` を返して前後の標高で補間すべきです）。

### MED-05: `VoiceGuidance.kt` で一度コース逸脱すると同じルート上で2回目以降の逸脱音声が鳴らず、逸脱中は目的地到着音声もブロックされる
- **区分**: Code Bug
- **場所**: [VoiceGuidance.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/speech/VoiceGuidance.kt#L333-L360) および [L456-L473](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/speech/VoiceGuidance.kt#L456-L473)
- **詳細**:
  1. `VoiceGuidanceNavigator.onLocationUpdated` は `isOffRoute == true` のときに `"REROUTE"` を `spokenKeys` に追加しますが、ユーザーが元のルートに自力復帰して `isOffRoute == false` になった際に `spokenKeys.remove("REROUTE")` を行いません。そのため、リルート無し（または自動リルートOFF時）に再度コースを外れても2回目以降の逸脱警告音声が鳴りません。
  2. `VoiceTriggerEvaluator.evaluate`（L333–345）は `if (isOffRoute)` 分岐が `ARRIVED` 判定（L348）より先にあり、`spokenKeys.contains("REROUTE")` の場合に `return null` で即終了するため、ゴール直前でルートから50m以上離れた状態でゴール半径30m以内に到達すると「目的地に到着しました」が発声されません。

### MED-06: `JmaWeatherParser.kt` が気象庁の日中最高気温（`09:00` 定義）を「朝の最低気温」と誤分類し、`WeatherRepository` が県単位キーで南部/北部のキャッシュを上書きする
- **区分**: Code Bug / UI-UX
- **場所**:
  - [JmaWeatherParser.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/weather/JmaWeatherParser.kt#L128-L140)
  - [WeatherRepository.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/weather/WeatherRepository.kt#L155-L220)
- **詳細**:
  1. 気象庁予報JSON（`timeSeries[2]`）では、朝の最低気温の `timeDefine` は `00:00:00+09:00`（`hour == 0`）、日中の最高気温の `timeDefine` は `09:00:00+09:00`（`hour == 9`）として配信されます。しかし `formatTempLabel`（L132–134）が `in 0..9 -> "朝の最低気温"` としているため、`09:00` の日中最高気温まで「朝の最低気温」と表示されます（日中発表分では2行とも「朝の最低気温」と並びます）。
  2. `WeatherRepository.saveCache`（L206）は `KEY_OFFICE_PREFIX + data.officeCode`（県単位）でキャッシュを保存しており、`refresh()`（L158）や通信失敗時フォールバック（L188, L192）で `cached.class10Code == area.class10Code` を確認していません。そのため、同一県内の別一次細分区域（例：広島県南部⇔北部）へ移動した際に別地域のキャッシュが表示されます。

### MED-07: ラスター地図のPOIマーカータップ時に `cat.name.lowercase()` を渡すため `PoiCategory.forCategory()` が失敗し、`googlePoiMarker` キャッシュも昼夜を考慮しない
- **区分**: Code Bug
- **場所**:
  - [MapLifecycleEffects.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/effects/MapLifecycleEffects.kt#L215-L223)
  - [MapOverlayHelper.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/utils/MapOverlayHelper.kt#L110-L112) および [L219](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/utils/MapOverlayHelper.kt#L219)
- **詳細**:
  1. `MapLifecycleEffects.kt` L217 で osmdroid のPOIピンをタップした際、`NearbySpot(spot.name, cat.name.lowercase(), ...)` のように `"convenience"` 等を渡しています。しかし `PoiCategory.forCategory` や `formatCategoryLabel` は `"shop:convenience"` のようなOSM形式プレフィックスを要求するため、詳細カード上でカテゴリ名が生の英語（`"convenience"`）になり、アイコンと色もデフォルトにフォールバックします（`spot.category` をそのまま渡すべきです）。
  2. `MapOverlayHelper.kt` L219 の `googlePoiMarker` は `poiTextColor(category)` を常に `isNight = false`（昼固定）で呼び出しており、キャッシュキーにも `isNight` が含まれないため夜間モードでも昼用の暗い文字色で描画されます。

### MED-08: `VectorMapView.kt` で停止中に地図をドラッグした後、`followLocation` 再開だけではカメラが現在地に戻らない
- **区分**: Code Bug / UI-UX
- **場所**: [VectorMapView.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/mapview/VectorMapView.kt#L115) および [L411-L439](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/mapview/VectorMapView.kt#L411-L439)
- **詳細**:
  - `VectorMapView.kt` L115 で定義されている `lastFollowLocation` は一度も読み書きされていません。
  - `lastCameraLat` / `lastCameraLon` はカメラ中心座標ではなく「前回カメラを更新したときのGPS座標」を保持しているため、信号待ち等で停止中に地図を手動パンして `followLocation = false` になった後、`onTargetClick` を経由せずに `followLocation = true` に戻った場合、`dist = haversineMeters(lastCameraLat, lastCameraLon, loc.latitude, loc.longitude)` が `0.0m` となり、`CameraMotionPolicy.shouldUpdateCamera` がカメラ移動をスキップしてしまいます。

### MED-09: 主要なユーザー設定（音声案内・自動リルート・ヘディングアップ・地図レイヤー・POI表示）が永続化されていない
- **区分**: UI-UX / Code Bug
- **場所**: [MapScreenStateFactory.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/state/MapScreenStateFactory.kt#L127-L246)
- **詳細**:
  - `themeMode` や `routePreference` は `SharedPreferences("cyclemap_ui_prefs")` に保存されていますが、`voiceGuidanceModeState`（L245）、`autoRerouteEnabledState`（L127）、`isHeadingUpState`（L139）、`selectedLayerState`（L170）、`poiVisibleState` / `poiSelectedState`（L171–174）はすべてプレーンな `remember { mutableStateOf(...) }` で初期化されており、アプリ再起動や画面回転（`configChanges` 対象外の再生成時）ですべて初期値にリセットされます。

### MED-10: ボトムシートの固定高によるUI見切れ、コース逸脱バナーのステータスバー重なり、未配線パネルの重複
- **区分**: UI-UX
- **場所**:
  - [MapSheets.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/map/MapSheets.kt#L86-L95)
  - [GoogleNavHeader.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/cycling/GoogleNavHeader.kt#L207-L228)
  - [MapPanelsHost.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/layout/MapPanelsHost.kt#L68-L99) および [L250-L289](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/layout/MapPanelsHost.kt#L250-L289)
- **詳細**:
  1. `ThreeStageBottomSheet`（`MapSheets.kt` L86–95）は `BottomSheetAnchor.Peek` の高さが `120.dp` に固定されスクロールも無効なため、`PlaceDetailContent`（施設名＋営業状態＋距離＋住所＋アクションボタン行で最低約 `170.dp` 必要）を表示すると下部の「経路」「ナビ開始」ボタンが見切れて押せなくなります。
  2. `GoogleNavHeader.kt` L207–228 で `currentGuide == null && isOffRoute && !isRerouting` のときに表示される赤色の「コースから外れました」バナーには `statusBarTopPadding` が適用されておらず、エッジツーエッジ表示でシステムステータスバーの裏に重なります。
  3. `MapPanelsHost.kt` L68–99 の `RoutePanelSheet` は全コールバックに空の `{}` が渡された未配線コードであり、`RouteSelectionSheet`（L250–289）と役割が重複しています。

### MED-11: `Isometric3dSimulationView.kt` で `detectTransformGestures` が1本指ドラッグを消費し、3Dチルト回転（`detectDragGestures`）が機能しない
- **区分**: Code Bug / UI-UX
- **場所**: [Isometric3dSimulationView.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/simulation/Isometric3dSimulationView.kt#L159-L173)
- **詳細**:
  - 同一の `Box` に対して `.pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> ... } }` と `.pointerInput(Unit) { detectDragGestures { change, dragAmount -> ... } }` を直列にチェインしています。`detectTransformGestures` は1本指のドラッグでも `pan` としてポインタイベントを消費するため、後段の `detectDragGestures` による `rotX` / `rotY`（視点回転）が動作しません。

### MED-12: `routing-core` / `RealRoutePlanner` のアルゴリズム・境界条件バグ
- **区分**: Code Bug
- **場所**:
  - [Routing.kt](file:///Users/gorite/Desktop/cycle_map/application/routing-core/src/main/kotlin/com/gorite/cyclemap/routing/Routing.kt#L112-L127)
  - [MappedRouting.kt](file:///Users/gorite/Desktop/cycle_map/application/routing-core/src/main/kotlin/com/gorite/cyclemap/routing/MappedRouting.kt#L30-L98) および [L227](file:///Users/gorite/Desktop/cycle_map/application/routing-core/src/main/kotlin/com/gorite/cyclemap/routing/MappedRouting.kt#L227)
  - [AltRouting.kt](file:///Users/gorite/Desktop/cycle_map/application/routing-core/src/main/kotlin/com/gorite/cyclemap/routing/AltRouting.kt#L276) および [L308-L316](file:///Users/gorite/Desktop/cycle_map/application/routing-core/src/main/kotlin/com/gorite/cyclemap/routing/AltRouting.kt#L308-L316)
  - [RealRoutePlanner.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/real/RealRoutePlanner.kt#L116) および [L162](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/real/RealRoutePlanner.kt#L162)
- **詳細**:
  1. `AStarRouter.route`（`Routing.kt` L113）および `MappedRoadGraph.route`（`MappedRouting.kt` L65）は、優先度付きキューから取り出したエントリが既に短いコストで更新済みの古いエントリ（stale entry）かどうかを判定せずに再展開しています（`LazyMappedRoadGraph` にはガードあり）。
  2. `MappedRoadGraph.nearestNode`（`MappedRouting.kt` L34）は空グラフ（`nodeCount == 0`）で `bestIndex = 0` のまま `nodeAt(0)` を呼び `IndexOutOfBoundsException` を投げます。また `LongIntIndex`（L227）の `1 shl power` は `expectedSize > 268,435,456` で符号ビットにオーバーフローし `NegativeArraySizeException` になります。
  3. `BidirectionalRouter.route`（`AltRouting.kt` L276）はループ毎ステップで `minOpenG`（キュー全要素の線形走査）を2回呼ぶため $O(V^2)$ の計算量になっています。
  4. `RealRoutePlanner.kt`（L116, L162）は候補ルートの `elevationGainMeters` にすべて `0.0` をハードコードしています。

### MED-13: データパイプライン（`build_search_db.py` / `overture.py` / `merge.py`）の検索正規化・県境界フィルタ・住所正規化バグ
- **区分**: Code Bug (Data Pipeline)
- **場所**:
  - [build_search_db.py](file:///Users/gorite/Desktop/cycle_map/application/osm-importer/build_search_db.py#L176) および [L343](file:///Users/gorite/Desktop/cycle_map/application/osm-importer/build_search_db.py#L343)
  - [overture.py](file:///Users/gorite/Desktop/cycle_map/sandbox/data-tool/tools/core/sources/overture.py#L236-L253)
  - [merge.py](file:///Users/gorite/Desktop/cycle_map/sandbox/data-tool/tools/core/merge.py#L197)
- **詳細**:
  1. フォールバック用の `application/osm-importer/build_search_db.py`（L176, L343）は `search_text` カラムに生の `name` をそのまま格納しています。しかしアプリ側の `SearchHelper.kt`（L368–372）は `search_text` カラムが存在するとクエリを `SearchNormalizer.normalize`（カタカナ→ひらがな、英大文字→小文字）に変換して `search_text LIKE '%ろーそん%'` を実行するため、フォールバックDBに対してカタカナや英大文字で検索するとLIKE検索が0件になります。
  2. `sandbox/data-tool/tools/core/sources/overture.py`（L236–253）は矩形 `bbox` でS3から取得した全POIに対し、`config.prefecture_for_point(lat, lon)` による行政界ポリゴン判定を行わずに無条件で `"prefecture": prefecture_id` を付与しています。そのため隣接県や瀬戸内海対岸（愛媛県等）のPOIが混入します。
  3. `sandbox/data-tool/tools/core/merge.py`（L197）の `normalize_addr_key` で `str.maketrans("０１２３４５６７８９一二三四五六七八九十", "01234567891234567891")` としているため、「十」が「1」に変換され、「十丁目」と「一丁目」がどちらも `"1丁目"` になり、同一チェーンの別店舗（1丁目店と10丁目店）が誤って同一住所ゴーストとして統合・削除されます。

---

## 🟢 Low（ハードコードされたモック表示・軽微なUI/UX懸念・デッドコード）

### LOW-01: 本番UIに個人名「拓真」、ダミーチップ「🚗 自宅・4 分」「🔍 マップに相談」、特定地名「井口五丁目」がハードコードされている
- **区分**: UI-UX
- **場所**:
  - [TopSearchBar.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/cycling/TopSearchBar.kt#L47) および [L197-L232](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/cycling/TopSearchBar.kt#L197-L232)
  - [MapTopOverlays.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/layout/MapTopOverlays.kt#L75-L78)
  - [LocationSummarySheet.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/cycling/LocationSummarySheet.kt#L52)
- **詳細**:
  1. `TopSearchBar.kt` L47 と `MapTopOverlays.kt` L78 で、検索バー右端のアバター文字に個人名 `"拓真"` がハードコードされており、かつタップすると開発者オプション（`onOpenDeveloperOptions`）が開く仕様になっています。
  2. `TopSearchBar.kt` L197–232 の `CategoryChipRow` 先頭に、自転車ナビにもかかわらず自動車アイコンと固定分数が入った `"🚗 自宅・4 分"` および `"🔍 マップに相談"` というモックチップがハードコードされており、どちらもタップすると通常の目的地検索ダイアログが開くだけです。
  3. `LocationSummarySheet.kt` L52 の `extractShortLocalityName` は住所が空文字のときに `"井口五丁目"`（広島市西区の特定町名）を返すハードコードになっています。

### LOW-02: 検索ダイアログの無反応マイクボタン、`QuickSpotBottomSheet` のダークモード文字色崩れ、開発者画面のハードコード
- **区分**: UI-UX
- **場所**:
  - [DestinationSearchDialog.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/components/DestinationSearchDialog.kt#L327-L333)
  - [QuickSpotSheet.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/cycling/QuickSpotSheet.kt#L153-L361)
  - [DeveloperOptionsScreen.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/DeveloperOptionsScreen.kt#L140)
- **詳細**:
  1. `DestinationSearchDialog.kt` L327–333 の音声検索（マイク）ボタンは `clickable { /* 音声検索 */ }` と空実装になっており、タップしても何も起きません。
  2. `QuickSpotSheet.kt` L153 はシート背景色を `containerColor = Color.White` に固定している一方、L259・L361 のローディング/空状態テキストには `MaterialTheme.colorScheme.onSurfaceVariant` を使用しているため、ダークモード時に白背景に白文字となり読めなくなります。
  3. `DeveloperOptionsScreen.kt` L140 は `val isEngineReady = true` をハードコードしています。

### LOW-03: `BroadcastReceiver` の古いラムダ捕捉および本番未接続の重複UIファイル群
- **区分**: Code Bug / Cleanup
- **場所**:
  - [MapGraphEffects.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/effects/MapGraphEffects.kt#L178-L207)
  - [MapTrackingEffects.kt](file:///Users/gorite/Desktop/cycle_map/application/app/src/main/java/com/gorite/cyclemap/ui/effects/MapTrackingEffects.kt#L85-L103)
  - `ui/map/NavigationScreen.kt`, `ui/cycling/CyclingControls.kt`, `ui/cycling/CyclingPanels.kt`, `ui/cycling/RouteInfoPanels.kt`
- **詳細**:
  1. `MapGraphEffects.kt`（オフラインパッケージ展開完了レシーバ）と `MapTrackingEffects.kt`（GPX保存完了レシーバ）は `DisposableEffect(context)` 内で `onStartGraphSwitch` 等のコールバックを `rememberUpdatedState` なしで直接キャプチャしています。
  2. `ui/map/NavigationScreen.kt` や `ui/cycling/CyclingControls.kt` 等は本番の `MapScreen.kt` から参照されておらず、現行の `ui/layout/` + `ui/cycling/Google*` 系と重複してメンテナンスコストを増大させています。

---

## 🛠️ 修正案リスト（優先度順アクションプラン）

### Phase 1: Critical バグの即時修正
- [x] 1. **[CRIT-01] エリア選択ドラッグの修正 (`MapViewsContainer.kt`)**
   - `localDrag` を導入し、`onDragStart` / `onDrag` / `onDragEnd` 内で同期的に更新して状態リセットを解消。
   - `selectedLayer == MapLayer.VECTOR_LIBERTY` の場合は `currentVectorMap?.projection?.fromScreenLocation(PointF(x, y))` を用いて `BoundingBox` を算出。
- [x] 2. **[CRIT-02] 県グラフ切替時のルート探索キャンセル競合の修正 (`MapGraphEffects.kt`, `MapRouteCalculation.kt`)**
   - `DisposableEffect(mappedGraph)` の `onDispose` から進行中ルート探索ジョブのキャンセルを削除し、古いグラフの `close()` のみ実行。
   - `executeRouteCalculation` の `finally` で `routeJobRef.get() === routeJob` の場合のみ `onCalculatingRouteChanged(false)` を呼ぶよう保護。
- [x] 3. **[CRIT-03] 1点ルートでの `LatLngBounds` クラッシュ防止 (`MapScreen.kt`)**
   - `MapScreen.kt` でユニーク座標数が2点未満またはスパン極小の場合に `CameraUpdateFactory.newLatLngZoom(..., 16.0)` に安全にフォールバック。
- [x] 4. **[CRIT-04] `OsmImporter.kt` の `.graph.idx` 自動生成 (`OsmImporter.kt`)**
   - `OsmImporter.kt` で `GraphBinaryWriter().write(graph, output)` 直後にサイドカー `.graph.idx` も自動生成。

### Phase 2: High バグの修正
- [x] 5. **[HIGH-01] `TileDownloader` の成功/失敗カウント分離 (`Prefectures.kt`, `MapRoutingActions.kt`)**
   - `successCount` と `failCount` を分離集計し、失敗時は「ダウンロード済み」と誤記録せず通信エラーをユーザーへ通知。
- [x] 6. **[HIGH-02] ベクター地図の初回PMTilesフォールバック解決 (`PrefectureSelectors.kt`, `VectorMapView.kt`)**
   - `TileSourceSelector` に `findFirstAvailableTile(dataDir)` を追加。PMTiles 不在時でもリスナー登録を維持し後からタイルロード可能に。
- [x] 7. **[HIGH-03] 地図レイヤー切替時の状態同期と `CycleMapView` 解放 (`MapViewsContainer.kt`, `MapRouteCalculation.kt`, `MapScreenScaffold.kt`, `MapScreen.kt`)**
   - `CycleMapView` の `onPause` / `onDetach` 呼び出しおよび overlay 解放を追加。ベクター⇔ラスター切替時のルート線・マーカー・カメラ位置を再構築。
- [x] 8. **[HIGH-04] GPX履歴の非同期読み込みとベクター地図へのGPXトラック描画対応 (`RideHistoryView.kt`, `VectorMapView.kt`, `MapSettingsDialogs.kt`)**
   - GPX パースを `produceState` + `Dispatchers.IO` で非同期化。ベクター地図上に `GeoJsonSource` / `LineLayer` によるトラック表示・バウンドズームを実装。
- [x] 9. **[HIGH-05] `LocationTrackingService` への `GpsHealthMonitor` 統合 (`LocationTrackingService.kt`)**
   - `LocationTrackingService` に `GpsHealthMonitor`（引数なしの `elapsedRealtime` 基準）を統合し、定期チェック用コールバックを確実に解除。
- [x] 10. **[HIGH-06] `arterialPenaltyMultiplier` の意味の分離 (`LazyMappedRouting.kt`, `HsaRouting.kt`)**
    - `arterialPenaltyMultiplier`（幹線ペナルティ）と `nonArterialPenaltyMultiplier`（非幹線ペナルティ）パラメータを明確に分離。
- [x] 11. **[HIGH-07] `viewModel.updateNavProgress` への次回案内ステップ受け渡し修正 (`MapScreen.kt`)**
    - `upcoming = inst.filter { it.distanceFromStartMeters > progress.distanceFromStartMeters }` により適切な次回・次々回ステップを渡すよう修正。
- [x] 12. **[HIGH-08] 縦画面の地図コントロール統一とカメラ状態のリアクティブ化 (`MapControlsOverlay.kt`, `VectorMapView.kt`)**
    - 縦画面に2D/3D・エリア選択・長押しズームピルを追加し、ベクター地図の bearing/zoom をリアクティブ更新・NaN ガード。

### Phase 3: Medium / Low バグおよびUI/UX改善
- [x] 13. **[MED-01]** `ReverseGeocoder.kt` の区ポリゴン検索に `prefectureName` および `parentCity` 条件を追加。
- [x] 14. **[MED-02 / MED-03]** `VectorMapThemeHelper.applyNightModeColors` で全 `SymbolLayer` の文字色/ハロー色を更新。`MainActivity.kt` で `isSystemDark` を反映。
- [x] 15. **[MED-04]** `ElevationRepository.kt` でルート標高サンプリングの線形補間を実装、DEM欠損値 `0x800000` を `null` として欠損補間に委ねる。
- [x] 16. **[MED-05]** `VoiceGuidanceNavigator` でルート復帰時に `"REROUTE"` をクリア。到着判定（`ARRIVED`）を最優先評価。
- [x] 17. **[MED-06]** `JmaWeatherParser.kt` で日中最高気温判定（9〜17時）を修正、`WeatherRepository` のキャッシュキーを `officeCode + "_" + class10Code` に細分化。
- [x] 18. **[MED-07 / MED-08 / MED-09]** ラスターPOIマーカーのカテゴリ引き継ぎ修正。`VectorMapView` でカメラ目標距離10m超過時または追従トグル直後の強制センタリングを実装。主要設定を `SharedPreferences` に永続化。
- [x] 19. **[MED-10 / MED-11]** `ThreeStageBottomSheet` の Peek 高さを 180dp に拡大、`GoogleNavHeader` 逸脱バナーにステータスバー余白を追加、`Isometric3dSimulationView` のドラッグ＆ピンチ操作を統合。
- [x] 20. **[MED-12 / MED-13]** `AStarRouter` / `MappedRoadGraph` に stale entry スキップ・キャパシティオーバーフローガード・動的獲得標高計算を追加。パイプラインの検索正規化・Overture県境界フィルタ・漢数字正規化（一丁目/十丁目）を修正。
- [x] 21. **[LOW-01 / LOW-02 / LOW-03]** `"拓真"`・`"🚗 自宅・4 分"`・`"🔍 マップに相談"`・`"井口五丁目"` を除去。未実装マイクボタン非表示、`QuickSpotBottomSheet` テーマ色統一、TTS初期化待機バッファリング、GPXコールバックの `rememberUpdatedState` 捕捉を実装。
