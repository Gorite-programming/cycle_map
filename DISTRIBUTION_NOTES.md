# 配布時のライセンス・クレジットメモ

CycleMapは無料配布できる。ただし、使用するデータ・ライブラリごとの利用条件を満たすこと。
このメモは実装時の確認用であり、最終的な配布前に各公式規約を再確認する。

## アプリ自体の著作権

```text
CycleMap App
© 2026 Gorite. All rights reserved.
```

## アプリ内に表示する候補クレジット

```text
© OpenStreetMap contributors
データはOpen Database License (ODbL) 1.0に基づき利用
https://www.openstreetmap.org/copyright

地理院タイル（国土地理院）
https://maps.gsi.go.jp/development/

音声：VOICEVOX:（使用したキャラクター名）
```

表示場所は、地図画面の隅または「情報／ライセンス」画面とする。オフラインでも表示できるよう、URLとライセンス情報をアプリ内に同梱する。

現在の実装では、地図画面の隅と「地図情報・ライセンス」画面の両方にOSM/GSIの出典を表示する。

## OpenStreetMap

- OSMデータはOpen Database License (ODbL) 1.0。
- `© OpenStreetMap contributors` の表示と、OSM著作権ページへのリンクが必要。
- OSMから作った道路グラフを配布する場合は、派生データベースとしての扱いとODbLのshare-alike条件を確認する。
- `tile.openstreetmap.org`から大量のタイルを一括取得して再配布する用途には使わない。配布用には、自前で許諾を得たタイル／ベクターデータ配信またはOSM PBFから生成したデータを使う。

公式：<https://www.openstreetmap.org/copyright>

## 国土地理院（GSI）

- 地理院タイルごとに利用条件が異なるため、使用するタイル（標準地図、淡色地図、陰影起伏、DEM等）の個別条件を確認する。
- 原則として「国土地理院」または「地理院タイル」等の出典を表示し、地理院タイル一覧へのリンクを付ける。
- 編集・加工した場合は、その旨を明示する。
- 基本測量成果に該当するデータは、測量法上の申請が必要になる場合がある。

公式：<https://maps.gsi.go.jp/development/>  
利用規約：<https://maps.gsi.go.jp/help/termsofuse.html>

## VOICEVOX

- VOICEVOX本体のクレジットに加え、使用した各キャラクターの利用規約を確認する。
- 基本形は `VOICEVOX:キャラクター名` を表示する。
- キャラクターごとに商用利用、二次配布、クレジット省略などの条件が異なる場合がある。

公式Q&A：<https://voicevox.hiroshiba.jp/qa/>

## OSSライブラリ

- osmdroid（Apache License 2.0）のNOTICE／ライセンスを配布物に含める。
- Kotlin、AndroidX、Google Play services Locationなど、依存ライブラリのNOTICEをrelease APKまたは「ライセンス」画面から確認できるようにする。
- A*ルーティングコアと自作コードのライセンスは、配布前に明示する（個人利用のみか、第三者配布も許可するかを決める）。

## 地図マーカーのアイコン方針

- UIアイコン → 既存の Lucide 由来 `ic_lucide_*` を継続する。
- 地図上の現在地・進行方向・ナビ矢印 → 自作 VectorDrawable（`ic_nav_arrow.xml`、`ic_location_dot.xml`）。PNG を方向分だけ量産しない。1種のベクター＋回転角で全方向を表現する。
- 将来の地図イベントマーカー（橋・歩道・自転車道・坂・踏切・注意・路面状態）→ Material Symbols / Font Awesome Free 等のフリー資産から VectorDrawable XML に変換して `app/src/main/res/drawable/` に同梱する。命名は既存に合わせ `ic_ms_bridge.xml` 等とする。
- 完全オフラインアプリのため実行時ダウンロードは禁止。追加は XML ベクターのみ（PNG は原則不可）。
- 外部資産を追加した場合は、そのライセンス（Apache-2.0 / OFL / CC-BY 等）を本ファイルに追記する。

### 経路案内アイコン `ic_ms_*` (2026-09-25 追加)

- 出典: Google Material Symbols Rounded 400 (`@material-symbols/svg-400`)。公式 codepoints と名前突合済み。
- ライセンス: Apache License 2.0。NOTICE 同梱条件に従い、本ファイルに記録する。
- 同梱物: `straight` / `turn_left` / `turn_right` / `turn_sharp_left` / `turn_sharp_right` /
  `turn_slight_left` / `turn_slight_right` / `u_turn_left` / `u_turn_right` /
  `fork_left` / `fork_right` / `merge` / `call_split` / `call_merge` /
  `ramp_left` / `ramp_right` / `roundabout_left` (`roundabout` 単体名は公式に存在しないため左側通行用) /
  `alt_route` / `add` の19点を `ic_ms_<name>.xml` として VectorDrawable 化
  (960グリッド → `translateY=960` の group で変換、パスデータは公式SVGそのまま)。
- 解決は `app/.../routing/IconResolver.kt` に集約。判定 (`routing-core` の `TurnClassifier`) と表示の差し替えは同ファイルのみで完結する。

## Google関連

- `FusedLocationProviderClient`は位置情報APIとして利用するだけにし、Google Mapsの地図タイル・道路データを抽出、保存、再配布しない。
- Google MapsデータをOSMや自作道路グラフへ混ぜない。

## 配布前チェック

- [ ] 地図画面にOSMとGSIのクレジットが表示される
- [ ] オフライン時も「ライセンス」画面が開ける
- [ ] OSM由来の道路グラフの出典と取得日を記録している
- [ ] 使用VOICEVOXキャラクターの規約を確認した
- [ ] OSS NOTICEをAPKまたは同梱ファイルに含めた
- [ ] 配布するデータパックのライセンスと再配布条件を確認した
- [ ] Google Maps由来のデータが混入していない

## OSMグラフ生成（山口県）

`osm-importer` は `.osm.pbf` を入力に取り、道路の通行可否だけを抽出段階で判定する。
グラフ化後も `highway`（primary/secondary/tertiary/residential/track等）と `oneway` を保持し、
`routing-core` の `CyclingCostModel` が道路種別の優先度を適用する。`motorway` はコスト計算で除外する。

```bash
./gradlew :osm-importer:installDist
osm-importer/build/install/osm-importer/bin/osm-importer \
  <yamaguchi.osm.pbf> <yamaguchi.graph>
```

## 広島県データパック (2026-09-21 生成・開発用、APK非同梱)

- 生成: `venv/bin/python build_prefectures.py --only Hiroshima`
  (`osmium extract --polygon boundaries/Hiroshima.geojson --strategy complete_ways` →
  `build_search_db.py` → `osm-importer --bbox none`、code `3a6faf2` 時点の importer)。
  元PBFは `data/japan-latest.osm.pbf` (2026-09-20取得、約2.5GB)。
- 成果物 (`work/Hiroshima/`、git管理外・APK非同梱):
  `Hiroshima.osm.pbf` 75MB / `Hiroshima.graph` 196MB (nodes=1,954,471 edges=4,091,435) /
  `Hiroshima.graph.idx` 48MB / `Hiroshima.search.db` 9.5MB (places 69,190件、うち place:* 4,285件)。
- 出典・ライセンス: © OpenStreetMap contributors、Open Database License (ODbL) 1.0。
  `.graph` / `.search.db` はいずれもOSM由来の派生データベース。データパックとして再配布する場合は
  ODbL の share-alike 条件 (派生DBの同条件公開) を満たすこと。クレジット表示は地図隅+ライセンス画面
  (配布前チェック参照)。`packages/<name>.zip` の3点 (`search.db`+`graph`+`idx`) を単位とする。

## オフライン逆ジオコーディング用データ

- 方式: 県別 `search.db` (OSM由来) の `places` テーブルを bbox (`lat/lon BETWEEN`) で事前絞り込みし、
  候補列から `selectHierarchy` (純粋関数) で行政階層として矛盾しない住所を選ぶ。
  選び順: 町 (GPS最近傍) → 区 (町位置の最近傍) → 市 (区の親市。`WARD_PARENT_CITY` に登録の政令市区のみ。
  未登録の区はGPS最近傍) → 郡 (市位置の最近傍。町・村制の市のみ表示)。各レベル独立の最近傍は使わない
  (市と区を別自治体から混ぜる誤りの原因だったため。2026-09-26 修正)。
- 精度: 丁目まで (番地以下は `sanitizeTownName` で落とす)。例: 広島県広島市中区国泰寺町一丁目。
  町名が区名を内包する場合 (東城町+東城町新免) は区名を省略する。
- 既知の限界 (点データ由来・DB変更なしで解消不可): 区・市ノードは役所等の代表点のため、
  広島駅周辺の区は幾何的に東区になる (町ノード818m vs 南区1887m)。南区表示には町→区の親子情報が要る。
  `isHierarchyConsistent` で市-区の混在は検出可能。
- 出典・ライセンス: 同上 (ODbL 1.0、© OpenStreetMap contributors)。Nominatim等のオンラインAPI不使用。
- APKへの同梱: しない。`CycleMap/search.db` としてアプリ外部ストレージに配置する運用
  (県別 `<name>.search.db` を `search.db` 名で配備)。都道府県判定 (`findPrefectureName`) は
  bbox重なり解消のため最狭区域優先に変更済み。県境の屈曲部 (例: 岩国東端) はbbox近似の限界として残る。
- GSIデータは道路グラフ・逆ジオコーディングには不使用 (表示タイルのみ)。GSI個別条件は従来通り確認する。
