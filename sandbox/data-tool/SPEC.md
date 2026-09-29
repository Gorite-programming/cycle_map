# 検索DB管理ツール 仕様書（SPEC.md）

対象: OpenCode（実装担当）
作成: Claude（指揮担当）
用途: CycleMap（Android完全オフライン自転車ナビ）用の検索DB（`*.search.db`）を、収集・編集・生成まで一貫して扱うPython+Streamlitツールの実装

---

## 0. 作業ルール（必ず守る）

1. **作業場所**: `sandbox/data-tool/` 配下のみ。これ以外のディレクトリ（特に `application/`）は読み書きしない。必要な情報はこのSPEC.mdに書いてある。
2. **本番データは触らない**: 入力データ（OSMのPBF、既存の`.search.db`など）は必ず**コピーして使う**。元ファイルを書き換えない・削除しない。
3. **出力の互換性は変更禁止**: 「3. 出力スキーマ」のカラム名・順序・型は変えてはならない。追加してよいのは末尾のカラムのみ。
4. **推測で進めない**: 仕様が曖昧・矛盾している場合は、実装を止めて質問する。
5. **段階的に進める**: 「9. 実装計画」の段階ごとに完了報告し、次へ進む前にテスト結果を報告する。
6. **依存は最小限に**: 標準ライブラリを優先。追加パッケージは `requirements.txt` に理由付きで記載。
7. **完了時に `HANDOFF.md` を書く**（書式は「11」）。

---

## 1. 目的と背景

- アプリ（Android/Kotlin）は、`places` テーブルと FTS5 の `places_fts` を持つSQLiteファイルを、端末の `.../CycleMap/` から読み込んで検索・周辺スポット表示・逆ジオコーディングに使う。
- 現行は `build_search_db.py` が OSM の PBF から生成している。POI（コンビニ・病院・学校など）が OSM に依存しており、日本での網羅性・表記に課題がある。
- 本ツールは、**日本特化の公的データを優先的に取り込み、不足分をOSMで補い、手動編集も反映して**、アプリ互換の検索DBを県ごとに生成する。
- 既存の `build_prefectures.py` は本ツールで**完全に置き換える**（ただし置き換えの実行は人間が行う。本ツールのスコープ外）。
- グラフ（`.graph` / `.idx`）は**本ツールの対象外**。編集も生成もしない。

---

## 2. 全体アーキテクチャ

```
[ソース層]                 [統合層]                    [出力層]
OSM PBF (POIのみ)  ──┐
国土数値情報 ────────┼→ 正規化 → 重複統合 → master.db ──→ 県別 .search.db
CSV/手入力 ──────────┘                        ↑
                                     手動編集（オーバーレイ）
```

- **master.db**: すべてのソースを共通スキーマで保持する中間DB（編集可能）。
- **オーバーレイ**: 手動編集（追加・修正・非表示）は別テーブルに持ち、再取り込みしても消えない。生成時に合成する。
- **出力**: アプリ用の `.search.db` は master.db + オーバーレイから**再生成**する（差分更新はしない）。

### ディレクトリ構成（この通りに作る）

```
sandbox/data-tool/
├── SPEC.md
├── HANDOFF.md              # 完了時に作成
├── requirements.txt
├── README.md
├── tools/
│   ├── core/               # ロジック（GUIに依存しない）
│   │   ├── config.py       # パス・県定義・カテゴリ対応表
│   │   ├── masterdb.py     # master.db スキーマ・CRUD
│   │   ├── overlay.py      # 手動編集の管理
│   │   ├── merge.py        # 重複統合ロジック
│   │   ├── export.py       # .search.db 生成
│   │   ├── validate.py     # 出力の検証（互換性チェック）
│   │   └── sources/
│   │       ├── osm.py      # OSM PBF → 共通スキーマ
│   │       ├── kokudo.py   # 国土数値情報 → 共通スキーマ
│   │       └── csvsrc.py   # CSV → 共通スキーマ
│   ├── cli.py              # coreを呼ぶだけのCLI
│   └── gui.py              # Streamlit（coreを呼ぶだけ）
├── tests/
├── data/                   # 入力のコピー置き場（git管理外）
│   ├── raw/
│   └── work/
└── out/                    # 生成物の出力先（git管理外）
```

**原則**: ロジックは `core/` にのみ書く。`cli.py` と `gui.py` は薄いラッパー。`core/` は単体でテストできること。

---

## 3. 出力スキーマ（アプリとの互換性・変更禁止）

アプリは以下を前提にしている。**これが最重要の制約。**

### 3.1 places テーブル

```sql
CREATE TABLE places (
    id       INTEGER PRIMARY KEY,
    osm_type TEXT    NOT NULL,   -- 互換のため維持。OSM以外は 'ext' を入れる
    osm_id   INTEGER NOT NULL,   -- OSM以外は 0 または連番でよい
    name     TEXT    NOT NULL,
    category TEXT    NOT NULL,
    lat      REAL    NOT NULL,
    lon      REAL    NOT NULL,
    -- ↓ ここから末尾追加カラム（アプリは読まないので追加してよい）
    address  TEXT,               -- 住所文字列（不明ならNULL）
    source   TEXT,               -- 'osm' / 'kokudo:<データ名>' / 'manual'
    kana     TEXT                -- 第2段階で使用。第1段階ではNULLでよい
);

CREATE INDEX places_osm_idx    ON places(osm_type, osm_id);
CREATE INDEX places_coords_idx ON places(lat, lon);   -- 必須（無いと範囲検索が全件スキャン）
```

### 3.2 FTS5

```sql
CREATE VIRTUAL TABLE places_fts USING fts5(
    name,
    category,
    content='places',
    content_rowid='id',
    tokenize='unicode61'
);
-- 構築:
INSERT INTO places_fts(rowid, name, category) SELECT id, name, category FROM places;
INSERT INTO places_fts(places_fts) VALUES('optimize');
```

- `places_fts.rowid` は `places.id` と一致させる。
- 第1段階では、FTSのカラム構成（`name`, `category`）を**変更しない**。第2段階の変更は「13. 第2段階」を参照。

### 3.3 アプリが発行するクエリ（互換性テストに使う）

アプリは必ず次の**4カラムをこの順で**読む（位置固定）: `name`, `category`, `lat`, `lon`。

```sql
-- (a) FTS検索
SELECT p.name, p.category, p.lat, p.lon
FROM places_fts f JOIN places p ON p.id = f.rowid
WHERE places_fts MATCH ? [AND (p.category LIKE 'shop:convenience%' OR ...)]
LIMIT ?;
-- MATCH文字列の例: "セブン* 広島*"（空白区切りで各語の前方一致）

-- (b) LIKE検索
SELECT name, category, lat, lon FROM places
WHERE name LIKE ? [AND (category LIKE '...%' OR ...)] LIMIT ?;

-- (c) カテゴリのみ・近い順
SELECT name, category, lat, lon FROM places
WHERE 1=1 [AND (category LIKE ...)] [AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?]
ORDER BY (lat-?)*(lat-?) + (lon-?)*(lon-?) LIMIT ?;

-- (d) 周辺スポット
SELECT name, category, lat, lon FROM places
WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?
  AND (category LIKE 'shop:convenience%' OR category LIKE 'amenity:toilets%' OR ...)
ORDER BY <距離> LIMIT ?;

-- (e) 逆ジオコーディング
SELECT name, category, lat, lon FROM places
WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? AND category LIKE 'place:%';
```

**重要**: (e) のため、`place:*` カテゴリ（`place:city`, `place:town`, `place:village`, `place:suburb`, `place:quarter`, `place:neighbourhood`, `place:county` 等）のデータは、公的データに置き換えず**OSM由来を必ず残す**こと（アプリの逆ジオコーディングが依存している）。

### 3.4 追加テーブル（アプリは読まない・ツール管理用）

```sql
CREATE TABLE meta (
    key   TEXT PRIMARY KEY,
    value TEXT
);
-- 入れるキー: schema_version, created_at, prefecture, bbox, source_summary(JSON), row_count, tool_version
```

---

## 4. カテゴリ体系

### 4.1 形式

カテゴリ文字列は **`<key>:<value>` のOSM形式**で出力する（アプリが前方一致 `LIKE 'xxx%'` で判定するため）。
公的データは、下表の対応表でOSM形式に変換して出力する。

### 4.2 現行アプリが認識しているカテゴリ（必ず対応する）

| アプリ側の分類 | 前方一致プレフィックス |
|---|---|
| コンビニ | `shop:convenience` |
| トイレ | `amenity:toilets` |
| 駅・バス停 | `railway:station`, `public_transport`, `amenity:bus`, `highway:bus_stop` |
| 飲食 | `amenity:restaurant`, `amenity:cafe`, `amenity:fast_food` |
| 観光・休憩 | `tourism:` |
| 駐車場 | `amenity:parking` |
| 病院 | `amenity:hospital`, `amenity:clinic`, `amenity:doctors` |
| ガソリン | `amenity:fuel` |
| 公園 | `leisure:park` |
| 給水 | `amenity:drinking_water` |
| （地名） | `place:*`（逆ジオコーディング用） |
| （その他） | `named`（名前付きの雑多な地点） |

### 4.3 取り込み優先度

1. **コンビニ**（`shop:convenience`）
2. **病院**（`amenity:hospital` / `amenity:clinic` / `amenity:doctors`）
3. **学校**（`amenity:school` / `amenity:kindergarten` / `amenity:university` / `amenity:college`）
4. 道の駅・観光施設（`tourism:*`）、バス停（`highway:bus_stop`）

### 4.4 named カテゴリ

現行DBの約半数を占める（橋名・交差点名など）。**削除しない**。ツール上で「含める/除外する」を**生成時にON/OFFできる**ようにする（デフォルトはON）。ONのときは現行と同じ扱い（`category = 'named'`）。

---

## 5. master.db（中間DB）

### 5.1 スキーマ（案。必要なら拡張してよいが、変更点はHANDOFFに書く）

```sql
CREATE TABLE poi (
    uid          INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT NOT NULL,
    category     TEXT NOT NULL,      -- OSM形式
    lat          REAL NOT NULL,
    lon          REAL NOT NULL,
    address      TEXT,
    prefecture   TEXT,               -- 県ID（例: 'hiroshima'）
    source       TEXT NOT NULL,      -- 'osm' / 'kokudo:medical' / 'manual' ...
    source_id    TEXT,               -- 元データ内のID（OSMなら 'node/123'）
    priority     INTEGER NOT NULL,   -- ソースの優先度（大きいほど優先）
    imported_at  TEXT NOT NULL,
    merged_into  INTEGER             -- 重複統合で吸収された場合、統合先のuid
);
CREATE INDEX poi_pref_cat ON poi(prefecture, category);
CREATE INDEX poi_coords   ON poi(lat, lon);

CREATE TABLE overlay (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    action      TEXT NOT NULL,       -- 'add' / 'edit' / 'hide'
    target_uid  INTEGER,             -- edit/hide の対象（poi.uid）。addはNULL
    -- add/edit で使う値（editは変更したい項目のみ非NULL）
    name        TEXT,
    category    TEXT,
    lat         REAL,
    lon         REAL,
    address     TEXT,
    prefecture  TEXT,
    note        TEXT,
    created_at  TEXT NOT NULL
);
```

### 5.2 オーバーレイの規則

- **手動編集は常に最優先**。再取り込みしても消えない（`poi`を入れ替えても`overlay`は残る）。
- `edit`/`hide` の対象は `poi.uid` で指す。ただし再取り込みで `uid` が変わる問題があるため、**対象の特定は `(source, source_id)` でも行えるようにする**こと（`overlay` に `target_source`, `target_source_id` を追加してよい）。再取り込み後も、同じ `(source, source_id)` に編集が再適用されること。
- `hide` = 生成時に除外する。データは消さない（復元可能）。
- 編集の前に、master.db の**自動バックアップ**を `data/work/backup/` に作る（直近N世代を保持）。

### 5.3 重複統合ルール

同一の施設が複数ソースに存在する場合の統合:

- **同一とみなす条件**: 同カテゴリ（または互換カテゴリ）、距離が閾値以内（既定: 50m、設定可）、名称が類似（正規化して比較。閾値は設定可）。
- **採用ルール**: `priority` が高いソースの値を採用。公的データ > OSM。ただし手動編集は常に最優先。
- 吸収された側は `merged_into` に統合先を記録（削除しない）。
- 統合結果は、GUI上で**確認・取り消しができる**こと（誤統合への対処）。

### 5.4 ソース優先度（既定値・設定可）

| ソース | priority |
|---|---|
| manual | 100 |
| kokudo:* | 50 |
| csv | 40 |
| osm | 10 |

**ただしコンビニは例外的に OSM が主データ**になる見込み（公的な網羅データが無いため）。カテゴリ×ソースごとに優先度を上書きできる設定を持つこと。

---

## 6. データソース

### 6.1 OSM（`sources/osm.py`）

- 入力: PBF（`osmium` CLI または PyOsmium を使用。既存環境は PyOsmium + osmium CLI）。
- 現行の `category_for` と**同じ規則**で `category` を決める（優先順位: `place` > `railway=station` > `amenity` > `shop` > `tourism` > `leisure` > `highway=bus_stop` > `public_transport` > `named`）。
  ```python
  def category_for(tags) -> str:
      v = tags.get("place")
      if v: return f"place:{v}"
      if tags.get("railway") == "station": return "railway:station"
      v = tags.get("amenity")
      if v: return f"amenity:{v}"
      v = tags.get("shop")
      if v: return f"shop:{v}"
      v = tags.get("tourism")
      if v: return f"tourism:{v}"
      v = tags.get("leisure")
      if v: return f"leisure:{v}"
      if tags.get("highway") == "bus_stop": return "highway:bus_stop"
      v = tags.get("public_transport")
      if v: return f"public_transport:{v}"
      return "named"
  ```
- `name` が空の要素は取り込まない（現行と同じ）。
- Way の座標は、まず現行同様（ノード座標の平均）でよい。改善するなら別途報告。
- 追加で取れるなら `addr:*` タグから `address` を組み立てる（第1段階では任意）。

### 6.2 国土数値情報（`sources/kokudo.py`）

- 入力: 国土数値情報のダウンロードファイル（GeoJSON / Shapefile 形式。データセットごとに異なる）。**ダウンロードはユーザーが手動で行う**（ツールは `data/raw/` に置かれたファイルを読む）。
- データセットごとに「アダプタ」を分ける（カラム名・座標系・文字コードが異なるため）。
- 第1段階の対象は**医療機関**、**学校**のみ。道の駅・観光施設・バス停は第3段階。
- 文字コード（Shift_JIS/UTF-8）と座標系（JGD2011等 → WGS84への変換）に注意。
- **ライセンス**: 各データセットの利用条件（出典表記など）を、取り込み時に `meta` と GUI に記録・表示する。不明なものは取り込まず質問する。
- 医療機関 → `amenity:hospital`（病院）/ `amenity:clinic`（診療所）/ `amenity:doctors` 等へ、データ内の区分で変換。対応表は `config.py` に置き、根拠をコメントに残す。
- 学校 → `amenity:school`（小中高）/ `amenity:kindergarten` / `amenity:university` / `amenity:college`。

### 6.3 CSV（`sources/csvsrc.py`）

- 手持ちの任意データを取り込む汎用アダプタ。
- 必須列: `name`, `category`, `lat`, `lon`。任意列: `address`, `prefecture`。
- GUIから列のマッピングを選べること（第2段階以降でよい）。

### 6.4 コンビニについて（重要）

- 公的な網羅データが存在しない。**OSMが主データ**。
- 各社の公式店舗一覧の**スクレイピングは行わない**（規約上のリスク）。
- 代わりに、**OSM側の品質改善**（名称の表記統一、ブランド名の正規化、重複除去）を第2段階で扱う。

---

## 7. 県の定義

- 対象: 中国5県（鳥取・島根・岡山・広島・山口）。将来拡張できる構造にする。
- 県ID・ファイル名は現行アプリと一致させる:

| 県 | 検索DBファイル名（現行） |
|---|---|
| 山口 | `search.db`（アプリ互換のため。`Yamaguchi.search.db` / `yamaguchi.search.db` も探索される） |
| 広島 | `Hiroshima.search.db` |
| その他 | `<県ID>.search.db`（命名規則は要相談。**勝手に決めず、質問する**） |

- 県ごとの範囲（bbox / 行政界）は `config.py` で管理。行政界GeoJSONの場所は `data/raw/boundaries/` にコピーして使う（無ければ質問する）。
- POIの県判定は、座標が入る行政界ポリゴン（なければbbox）で行う。

---

## 8. 画面仕様（Streamlit）

第1段階では**★の画面のみ**。他は段階に応じて追加。

1. **ダッシュボード ★**: 県ごとの状態（master.dbの件数、最後の取り込み・生成日時、`out/`のファイル有無・サイズ）。
2. **データ取り込み ★**: ソース選択（OSM / 国土数値情報 / CSV）、入力ファイル選択、実行、ログ、件数の増減。取り込み結果の `source` 別件数。
3. **POI閲覧・編集**: 表（県・カテゴリ・ソース・キーワード・`hidden`で絞り込み）、行の追加/修正/非表示、地図プレビュー（`st.map` 等）。編集は overlay に記録。
4. **重複統合の確認**: 統合候補の一覧、統合/取り消し。
5. **検索DB生成 ★**: 県選択、`named` の含める/除外トグル、生成実行、生成後の**検証結果表示**（「10. 検証」）。
6. **検索テスト**: 生成した `.search.db` に対し、アプリと同じ FTS クエリ（3.3 の (a)〜(d)）を実行して結果を表示。
7. **配置**: `out/` から任意フォルダへのコピー、`adb push` の実行（設定でパスを指定）。

**UIの原則**: 長時間処理はログをリアルタイム表示。失敗時は原因が分かるエラー表示。破壊的操作（DB削除、編集の全消去）は確認ダイアログ。

---

## 9. 実装計画（段階と完了条件）

各段階の終わりで、テスト結果を報告して止まること。次の段階へは指示を待つ。

### 第1段階: 現行の再現（master.db + OSM取り込み + 生成 + 検証）

**作るもの**: `config.py`, `masterdb.py`, `overlay.py`（骨格）, `sources/osm.py`, `export.py`, `validate.py`, `cli.py`、Streamlitの ★ 画面（ダッシュボード、取り込み、生成）。

**完了条件**:
- 広島県のPBFから、`Hiroshima.search.db` 相当のDBが生成できる
- **現行DBとの比較レポート**（次を出力する）:
  - 総件数、カテゴリ別件数の差分（現行: 総69,190件、`named` 34,876、`highway:bus_stop` 10,150、`shop:convenience` 800 など）
  - サンプル行の比較
  - 差が出る場合はその理由（バージョン差・PBFの日付差など）
- 「10. 検証」の全項目に合格

> 比較用の**現行DB（`Hiroshima.search.db`）は、人間が `data/raw/reference/` にコピーして渡す**。無ければ「比較用DBが無い」と報告して止まる。

### 第2段階: 表記ゆれ対応（漢字・ローマ字・カタカナ・ひらがな）

「13」を参照。

### 第3段階: 日本特化データの取り込み

**作るもの**: `sources/kokudo.py`（医療機関→学校の順）、`merge.py`（重複統合）、POI閲覧・編集画面、重複統合確認画面。

### 第4段階: 仕上げ

CSV取り込み、検索テスト画面、配置画面、README整備、HANDOFF最終化。

---

## 10. 検証（validate.py・生成のたびに実行）

生成した `.search.db` について、以下を自動チェックして結果を表示する。1つでも失敗すれば「不合格」として明示する。

1. `places` と `places_fts` が存在する
2. `places` のカラム名・順序が「3.1」と一致（先頭7カラムが `id, osm_type, osm_id, name, category, lat, lon`）
3. `places_coords_idx` と `places_osm_idx` が存在する
4. `places_fts` の行数 = `places` の行数、`rowid` が `places.id` と対応
5. `SELECT name, category, lat, lon FROM places LIMIT 1` が成功する
6. FTS検索（3.3 の (a)）が実行でき、既知の語（例: 「広島」）でヒットする
7. `category LIKE 'place:%'` の行が存在する（逆ジオコーディング用）
8. `lat` が 20〜46、`lon` が 122〜154 の範囲外の行が無い（日本国内の妥当性）
9. `name` が空文字の行が無い
10. `meta` テーブルが存在し、必須キーが揃っている
11. ファイルサイズを表示（参考値）

---

## 11. HANDOFF.md（完了時に必ず作成）

次の項目を、この順で書く。

1. 作ったもの一覧（ファイル・役割）
2. セットアップ・起動方法（コマンド）と `requirements.txt` の理由
3. 実行したテストと結果（第1段階の現行DB比較レポートを含む）
4. 仕様との差異（変えたこと、やらなかったこと、その理由）
5. 既知の制限・未実装
6. 本番側（`application/`）に必要な変更（あれば。例: `build_prefectures.py` の置き換え手順、アプリ側のカテゴリ追加）
7. 出力スキーマの互換性検証の結果
8. 取り込んだデータのライセンス・出典一覧

---

## 12. アプリ側への要望（OpenCodeは実装せず、HANDOFFに記載するだけ）

以下は、`application/` 側の変更が必要な事項。**OpenCodeは触らない**。気づいた点をHANDOFFの6に書く。

- 学校（`amenity:school` 等）や医院（`amenity:doctors`）は、`searchNearbyPlaces` の対象カテゴリに含まれていない可能性がある
- 新カテゴリを追加する場合、`PoiCategory`（アイコン・色）と `SearchCategory` の更新が必要

---

## 13. 第2段階の設計方針（第1段階の後に着手）

目的: 「セブンイレブン」「せぶんいれぶん」「seven」「セブン-イレブン」など、表記が違っても同じ店が見つかる。

- `places` の末尾に正規化用カラムを追加（例: `name_norm`, `kana`, `romaji`）。
- **FTSのカラム構成を変えるとアプリの `MATCH` 式に影響する**ため、次のいずれかで実現する（第2段階の着手時に、こちらと相談して決める）:
  - (A) FTSの `name` カラムに、正規化した別表記を**スペース区切りで追記**して格納する（アプリ変更不要。ただし表示名は `places.name` を使うので影響しない）
  - (B) FTSにカラムを追加する（アプリの `MATCH` 対象は全カラムになるため、アプリ変更不要の可能性が高いが、検証が必要）
- 正規化の要素: 全角/半角、ハイフン・長音・中黒の統一、カタカナ⇔ひらがな、漢字→読み（読み仮名の辞書やライブラリが必要。**追加依存は理由を添えて相談**）、ローマ字（ヘボン式）。
- 検証: 上記の表記の組み合わせで、同じ店がヒットするテストケースを作る。

---

## 14. 質問して止まるべきケース（例）

- 入力データ（PBF、行政界、現行DB）が `data/raw/` に無い
- 国土数値情報のデータセットのカラム定義・文字コード・ライセンスが不明
- 山口・広島以外の県の検索DBファイル名
- 3.1のスキーマと矛盾する要求に気づいた
- 追加の依存パッケージが必要になった
- 仕様に無い判断が必要になった
