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
