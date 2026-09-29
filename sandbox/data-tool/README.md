# 検索DB管理ツール (第1段階)

CycleMap (Android完全オフライン自転車ナビ) 用の検索DB (`*.search.db`) を、
OSM PBF から生成する Python ツール。第1段階では現行 (`build_search_db.py`)
の再現が目標。詳細仕様は `SPEC.md`。

## 構成

- `tools/core/` — ロジック (GUIに依存しない)。単体テスト可能。
  - `config.py` — パス・県定義 (中国5県)・カテゴリ対応表・県判定
  - `masterdb.py` — `master.db` (中間DB) のスキーマ・CRUD
  - `overlay.py` — 手動編集 (add/edit/hide) の記録と生成時合成
  - `sources/osm.py` — OSM PBF → 共通スキーマ
  - `export.py` — 県別 `.search.db` 生成
  - `validate.py` — 出力検証 (11項目) + 現行DB比較レポート
- `tools/cli.py` — core を呼ぶだけの CLI
- `tools/gui.py` — Streamlit GUI (ダッシュボード・取り込み・生成)
- `tests/` — pytest 単体テスト
- `data/` — 入力のコピー置き場 (git管理外)。`data/raw/` の内容は
  人間が用意した作業用コピーであり、本番データの原本ではない。
- `out/` — 生成物の出力先 (git管理外)

## セットアップ

```bash
cd sandbox/data-tool
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

`requirements.txt` の依存と理由:

- `osmium` — OSM PBF 読み込み (PyOsmium。`sources/osm.py` で使用)
- `streamlit` — GUI (`tools/gui.py`)
- `pytest` — 単体テスト (`tests/`)

## 使い方

```bash
.venv/bin/python -m tools.cli import-osm --pref hiroshima
.venv/bin/python -m tools.cli export --pref hiroshima
.venv/bin/python -m tools.cli validate --db out/hiroshima.search.db
.venv/bin/python -m tools.cli compare --pref hiroshima
.venv/bin/python -m pytest tests/ -q
.venv/bin/streamlit run tools/gui.py
```

`export --exclude-named` で `named` カテゴリを除外できる (既定は含める)。
`export --pykakasi` で pykakasi 読みを付与する (既定OFF。地名の誤変換あり。
詳細は `out/kana_sample.md`)。

## 出典表記

- OSMデータ: © OpenStreetMap contributors（ODbL）
- 国土数値情報を取り込んだDBには次が適用される（`meta.source_summary` にも記録）:
  - 出典：国土交通省国土数値情報ダウンロードサイト
  - 「国土数値情報（医療機関データ）」（国土交通省）（https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-P04-v3_0.html）をもとに作成
  - 「国土数値情報（学校データ）」（国土交通省）（https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-P29-v2_0.html）をもとに作成

## 注意: FTS の rebuild 禁止

第2段階の方式Aでは、FTS の `name` に別表記（ひらがな・ローマ字など）を
スペース区切りで追記している。`places.name`（表示用）は元の表記のまま。

外部コンテンツFTS（`content='places'`）のため、誰かが将来

```sql
INSERT INTO places_fts(places_fts) VALUES('rebuild');
```

を実行すると、`places.name` から再構築されて**別表記が消える**。
`rebuild` は使わないこと。別表記を戻す手順 = `export` の再実行
（決定的に再生成されるため、再投入はそれだけで足りる）。

## 注意

- `sandbox/data-tool/` 配下のみで作業する (`application/` は読まない・触らない)。
- 入力 (PBF・参照DB・行政界) は `data/raw/` のコピーを使う。原本を書き換えない。
- 出力 `places` の先頭7カラムと FTS 構成は変更禁止 (SPEC 3)。
