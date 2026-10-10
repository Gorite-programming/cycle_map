# CycleMap コアルーティング＆ナビゲーションロジック一覧

本ドキュメントは、CycleMapにおけるルート探索、コストモデル、メモリマップドデータ構造、案内指示（ターンバイターン）生成、自律航法（推測航法）、およびインポート処理などのコアルーティングロジックの配置ファイル・該当行番号・詳細仕様をまとめたものです。

---

## 1. コアルーティング・探索アルゴリズム本体 (`application/routing-core`)

純粋なJVMライブラリとして実装されている探索アルゴリズムおよびグラフ表現の中核部分です。

### 1.1 [Routing.kt](src/main/kotlin/com/gorite/cyclemap/routing/Routing.kt)
インメモリグラフ表現と純粋なA*探索アルゴリズムの基本実装。
* **9〜13行 (`GraphNode`)**: ノードデータ構造（OSM node ID, 緯度, 経度）。
* **15〜30行 (`GraphEdge`)**: 有向エッジデータ構造（始点, 終点, 幾何距離(m), 道路種別, 一方通行フラグ, 勾配(%), コスト倍率）。
* **32〜37行 (`RoadGraph`)**: ノードマップと各ノードからの出線リストを持つインメモリグラフ。
* **39〜44行 (`RouteResult`)**: 探索結果（ノードID列, 総コスト, 到達可否フラグ `isReachable`）。
* **46〜56行 (`EdgeCostModel`, `DistanceCostModel`)**: エッジコスト計算インターフェースおよび幾何距離基準モデル。
* **58〜77行 (`CyclingCostModel`)**: 自転車専用コストモデル。
  * 高速道路（`motorway`, `motorway_link`）を完全除外。
  * 道路種別ごとの優先度倍率（`primary`: 0.90 〜 `path`: 1.80）。
  * 勾配ペナルティ（$1.0 + |\text{grade}| \times 0.02$）。
  * A* ヒューリスティックの下限倍率として `0.90` を保証。
* **79〜136行 (`AStarRouter`)**: A* 最短経路探索アルゴリズム。
  * 優先度付きキュー（ヒューリスティック加算）を用いた探索。
  * 展開ノード数（`lastExpandedNodes`）の記録。
* **138〜146行 (`haversineMeters`)**: 2点間の地球大圏距離（球面三角法）計算式。

### 1.2 [LazyMappedRouting.kt](src/main/kotlin/com/gorite/cyclemap/routing/LazyMappedRouting.kt) （本番アプリ実機採用）
Android実機のメモリ制約に対応するため、メモリマップドファイル（mmap）から必要時にのみエッジを展開する高速グラフ実装。
* **18〜40行 (`LazyMappedRoadGraph`)**: `.graph` と `.idx`（バイナリインデックス）を保持するクラス定義。
* **46〜55行 (`nodeAt`, `nodeIndexOf`)**: インデックスとノードIDの双方向変換。
* **57〜95行 (`nearestNodeIndex`, `nearestNode`)**: 最寄りノード探索。
  * 三角関数負荷を削減するため、1パス目で等距円筒近似により暫定最良点を求め、2パス目でマージン内の候補のみHaversine計算を行う高速2パス走査。
* **96〜122行 (`outgoingBranches`)**: ノードから伸びる分岐枝（ターゲットインデックス, 道路種別, 距離）の取得。案内指示生成で使用。
* **124〜208行 (`route`)**: **実機ルーティング探索本体**。
  * A* 探索（優先度キュー）。
  * 探索バウンディングボックス（`SearchBounds`）による長距離の枝刈り。
  * 1,024ノード展開ごとのキャンセル検知（`isCancelled`）および最大展開ノード数（`maxExpandedNodes`）による打ち切り制御。
  * 優先方針（`RoutePreference`）に応じた勾配・幹線道路ペナルティの適用。
* **210〜248行 (`readEdge`)**: mmapバッファ上の指定オフセットからエッジレコードを直接デコード。
* **252〜266行 (`gradePenalty`)**: 優先方針ごとの勾配ペナルティ計算。
  * `RECOMMENDED`（おすすめ）: 勾配1%あたり+2%コスト。
  * `FLAT`（平坦優先）: 上り坂に急激なペナルティ（+12%）、下りも急坂は回避。
  * `HILL_CLIMB`（坂道優先）: 平坦道にペナルティを課し、上り坂を相対的に優先。
* **268〜277行 (`multiplier`, `isArterial`)**: 道路種別倍率および主要幹線道路の判定。
* **280〜340行 (`searchBoundsFor`, `load`)**: 探索bbox生成および `CYCLEMAP_GRAPH_V1` / `CYCLEMAP_INDEX_V2` のロード。
* **343〜348行 (`RoutePreference`)**: 探索方針enum（`RECOMMENDED`, `FLAT`, `HILL_CLIMB`）。

### 1.3 [MappedRouting.kt](src/main/kotlin/com/gorite/cyclemap/routing/MappedRouting.kt)
初期の全エッジ事前展開型 mmap グラフ実装（参照・比較用）。
* **13〜41行 (`MappedRoadGraph`, `nearestNode`)**: 線形走査による最寄りノード検索。
* **43〜97行 (`route`)**: 基本的な mmap 上での A* 探索。
* **134〜195行 (`load`)**: `CYCLEMAP_GRAPH_V1` のロードと静的配列への展開。
* **197〜208行 (`MappedRouteResult`)**: 探索結果（ノードID列, 総距離, 総コスト, 座標列, キャンセル/打ち切りフラグ）。
* **210〜241行 (`LongIntIndex`)**: プリミティブ配列を用いたオープンアドレス法ハッシュインデックス。

### 1.4 [HsaRouting.kt](src/main/kotlin/com/gorite/cyclemap/routing/HsaRouting.kt) （実験・ベンチマーク用）
長距離探索の高速化を検証するための階層的スケルトンA*（Hierarchical Skeleton A*）。
* **27〜109行 (`HsaOptions`)**: HSA* 探索パラメータ（モード O/B/F、ビーム幅、セグメント分割距離など）。
* **119〜142行 (`RoadHierarchy`, `SkeletonCostModel`)**: 道路階層レベルに応じたペナルティモデル。
* **144〜186行 (`SkeletonGenerator`)**: 骨格（スケルトン）ルートを生成し、分割アンカーノードを決定。
* **213〜359行 (`HsaStarRouter`)**: メモリグラフ上でのセグメント分割 A* 探索および品質ゲート判定・フォールバック。
* **416〜558行 (`MappedHsaRouter`)**: `LazyMappedRoadGraph` 上で動作する HSA* アダプター。
* **560〜627行 (`benchmarkMappedRouting`)**: A* と HSA* のメモリ使用量、展開ノード数、所要時間のベンチマーク計測。

### 1.5 [AltRouting.kt](src/main/kotlin/com/gorite/cyclemap/routing/AltRouting.kt) （実験・ベンチマーク用）
ランドマーク頂点と三角不等式を利用した下限ヒューリスティック強化アルゴリズム（ALT法）。
* **12〜205行 (`LandmarkIndex`)**: ランドマーク頂点の選定、双方向ダイクストラによる事前距離計算、ファイル保存/読込。
* **212〜248行 (`AltRouter`)**: ALT法による A* 探索。
* **250〜312行 (`BidirectionalRouter`)**: 双方向 A* / 双方向 ALT 探索。
* **378〜409行 (`benchmarkRoutingVariants`)**: A*、A*+ALT、双方向A* の比較ベンチマーク。

### 1.6 [GraphBinary.kt](src/main/kotlin/com/gorite/cyclemap/routing/GraphBinary.kt)
* **9〜33行 (`GraphBinaryReader`)**: `CYCLEMAP_GRAPH_V1` バイナリファイルを直接読み込み、`RoadGraph` を構築するリーダー。

### 1.7 [RoutingBenchmark.kt](src/main/kotlin/com/gorite/cyclemap/routing/RoutingBenchmark.kt)
* **44〜145行 (`benchmarkRoutingAlgorithms`)**: 同一クエリに対して A* および HSA*（各モード）を実行し、中央値実行時間、ヒープ増加量、展開ノード数を集計するベンチマーク実行部。

---

## 2. ナビゲーション・案内判定・進捗計算 (`application/routing-core`)

算出された経路に対する現在位置の追従、ターンバイターン案内指示の生成、および自律航法ロジック。

### 2.1 [NavigationGuidance.kt](src/main/kotlin/com/gorite/cyclemap/routing/NavigationGuidance.kt)
* **32〜56行 (`extractManeuvers`)**: ルート座標列の進行方位変化から簡易マニューバ（右折・左折・Uターン）を抽出。
* **58〜95行 (`calculateRouteProgress`)**: 現在位置からルートへの最近接投影を行い、走行済み距離、残距離、次回案内地点を算出。
* **99〜119行 (`projectOntoSegment`)**: 緯度経度を局所直交座標に投影し、線分セグメントへの正射影（最短距離点および内分比）を計算。
* **130〜140行 (`bearingBetween`, `normalizeBearingChange`)**: 2点間の方位角（度, 北=0）および角度変化量の正規化（$[-180^\circ, 180^\circ)$）。

### 2.2 [TurnClassifier.kt](src/main/kotlin/com/gorite/cyclemap/routing/TurnClassifier.kt)
交差点や分岐における詳細な幾何形状および道路種別遷移の分類エンジン。
* **85〜133行 (`classifyRoute`, `classifyMappedRoute`)**: グラフエッジとルートノード列から各交差点の入力データ（`JunctionInput`）を生成し分類を実行。
* **211〜317行 (`classifyJunction`)**: 各交差点の優先度付き分類ロジック。
  1. Uターン判定（転回角 $\ge 150^\circ$）
  2. ランプ出入判定（`*_link` 道路との出入）
  3. 側道出入判定（幹線道路 $\leftrightarrow$ `service` 道路）
  4. 合流判定（下位道路から上位道路への浅い角度での流入）
  5. 分岐・交差点判定（後方枝を除いた前方枝数による分岐）
* **319〜420行 (`classifyHairpinOrTurn`, `classifyFork`, `classifyThreeBranches`)**:
  * 急カーブ/ヘアピン、Y字路（対称性判定）、T字路、各種分岐（斜め分岐、上下分岐）、十字路の幾何判定。
* **423〜510行 (`detectCirculationEntries`)**: 同一符号の連続転回および出入アーム判定によるラウンドアバウト（環状交差点）検出。
* **513〜538行 (`mergeConsecutiveForks`)**: 近接する連続分岐（80m以内）を `CONSECUTIVE_FORK` に統合。

### 2.3 [Instruction.kt](src/main/kotlin/com/gorite/cyclemap/routing/Instruction.kt)
* **29〜62行 (`InstructionType`)**: 基本経路指示全29種類の定義（直進、左右折、急左右折、Uターン、各分岐、Y字/T字/十字/多差路、合流、ランプ、ラウンドアバウト等）。
* **68〜91行 (`RouteInstruction`)**: 案内地点ノードID、ルート内位置、転回角、分岐数、判定理由トークンを保持するデータ構造。
* **98〜136行 (`GuidanceConfig`)**: 判定に使用する各種閾値定義（直進 $20^\circ$、分岐 $40^\circ$、急カーブ $100^\circ$、Uターン $150^\circ$ など）。

### 2.4 [DeadReckoner.kt](src/main/kotlin/com/gorite/cyclemap/routing/DeadReckoner.kt)
トンネル内や高架下などでGPS信号がロストした際の推測航法（自律航法）ロジック。
* **33〜84行 (`DeadReckoner.estimate`)**: 直近の有効速度・方位・経過時間から現在位置を推測。
* **86〜123行 (`advanceAlongRoute`)**: 案内中ルートが存在する場合、ルートの線分に沿って目標距離だけ前進させた座標を算出。
* **125〜139行 (`projectStraight`)**: ルート外の場合の大圏直線推測航法。

### 2.5 [NavigationStats.kt](src/main/kotlin/com/gorite/cyclemap/routing/NavigationStats.kt)
* **40〜86行 (`computeNavigationStats`)**: 実効走行速度および到着予想時刻（ETA）の動的算出。
  * GPSジッターによるETAの急激な変動を防ぐため、走行開始からの「平均速度」を優先し、序盤のみ平滑化瞬間速度やデフォルト巡航速度（15 km/h）にフォールバック。
* **92〜129行 (`OffRouteDetector`)**: ルート逸脱検出器。
  * GPS瞬間ジャンプによる誤リルートを防ぐため、逸脱距離しきい値（50m）の連続超過カウント（3回連続）で確定し、復帰には20m以内のヒステリシスを設ける。

---

## 3. 道路網抽出・グラフ/インデックス生成 (`application/osm-importer`)

OSM PBFファイルから自転車走行可能道路を抽出し、バイナリグラフ（`.graph`）およびサイドカーインデックス（`.graph.idx`）を出力するCLIツール。

### 3.1 [OsmImporter.kt](osm-importer/src/main/kotlin/com/gorite/cyclemap/importer/OsmImporter.kt)
* **50〜61行 (`BicycleAccessFilter.isAllowed`)**: OSMタグから自転車通行禁止道路（`bicycle=no`, `access=private`, `motorway` 等）を除外。
* **199〜241行 (`NodeCoordStore`)**: 大規模PBF処理時のJavaヒープ枯渇を防ぐため、参照ノード座標のみを保持するプリミティブ並列配列ハッシュマップ。
* **247〜330行 (`TwoPassGraphBuilder.build`)**: 2パスストリーミングによる省メモリグラフビルダー。
  * Pass 1: 自転車通行可能Wayのみ走査し、参照されているNode IDセットを収集。
  * Pass 2: 必要なNodeの座標のみを抽出し、有向エッジ（一方通行判定、距離計算を含む）を構築。
* **604〜629行 (`GraphBinaryWriter.write`)**: `CYCLEMAP_GRAPH_V1` バイナリファイルのシリアライズ。
* **782〜832行 (`writeGraphIndex`)**: `CYCLEMAP_INDEX_V2` インデックスの書き出し（ノードごとの先頭エッジオフセット、エッジ数を記録し、mmap時のランダムアクセスを可能にする）。
* **838〜858行 (`runRouteSanityCheck`)**: グラフ生成後のA*疎通テスト。

---

## 4. アプリUI / 画面層での探索呼出・統合ロジック (`application/app`)

UI画面（Compose/osmdroid）とルーティングエンジンを接続するレイヤー。

### 4.1 [MapScreen.kt](app/src/main/java/com/gorite/cyclemap/MapScreen.kt)
* **707〜770行 (ルート探索スレッド)**:
  * 出発地・経由地・目的地の各ポイントを `nearestNodeIndex` で最近接ノードにスナップ。
  * 区間ごとに `graph.route(startIndex, goalIndex, preference, maxExpandedNodes=4_000_000)` を実行。
  * ジョブのキャンセルおよびノード展開数上限による打ち切り（`wasTruncated`）のハンドリング。
* **772〜797行**: 区間結果の結合（`MappedRouteResult`）およびバックグラウンドでの案内指示構築（`buildRouteInstructions`）。
* **1156〜1170行 / 1265〜1275行 / 1332〜1335行**:
  * 位置情報更新時の現在地追従と進捗計算（`calculateRouteProgress`）。
  * 残距離・ETA算出（`computeNavigationStats`）。
  * 逸脱検知時の自動リルート（`offRouteDetector`）。
* **1426〜1470行**: 開発者向け画面等からの HSA* ベンチマーク実行呼び出し（`benchmarkMappedRouting`）。

### 4.2 [NavigationHelper.kt](app/src/main/java/com/gorite/cyclemap/NavigationHelper.kt)
* **50〜102行 (`buildRouteInstructions`)**: `LazyMappedRoadGraph` から各ノードの分岐情報を抽出し、`TurnClassifier.classifyMappedRoute` を呼び出してルート全体の案内指示列を構築。
* **110〜206行 (`navigationInstruction`)**: ルート進捗距離と次回案内地点までの残距離に基づき、案内バナーに表示する矢印アイコンおよび案内テキスト（「まもなく右折です」等）を生成。直進直後の曲がり角を先読みする事前告知ロジックを含む。

### 4.3 [IconResolver.kt](app/src/main/java/com/gorite/cyclemap/routing/IconResolver.kt)
* **109〜145行 (`resolveIconResId`)**: `InstructionType` および転回角度から適切な VectorDrawable リソース（`ic_ms_*`）を解決。
* **155〜202行 (`getLabel`)**: 交差点形状と転回角度に応じたUI・音声案内ラベル文字列（「T字路を左折」「十字路を直進」など）を解決。

---

## 5. ミラーツリー（`test/` ディレクトリ）について

プロジェクトルート直下の `test/` ディレクトリ配下（`test/routing-core/...` や `test/osm-importer/...` 等）にも同一のソースコード群が存在します。これらは検証・テスト用の作業ミラーツリーであり、本番のGradleビルド（`:app`, `:routing-core`, `:osm-importer`）でコンパイル・実行される正体はすべて上記に記載した **`application/`** 配下のファイルとなります。
