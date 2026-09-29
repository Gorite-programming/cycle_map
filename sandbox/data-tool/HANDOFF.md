# HANDOFF.md（第1〜第3段階）

## 1. 作ったもの一覧（ファイル・役割）

| ファイル | 役割 |
|---|---|
| `tools/core/config.py` | パス・中国5県定義・カテゴリ対応表・ソース優先度・県判定・国土数値情報定義（データセット・コード対応表・出典） |
| `tools/core/masterdb.py` | `master.db`（単一DB、`data/work/master.db`）のスキーマ・CRUD・`replace_source`・スキーママイグレーション |
| `tools/core/overlay.py` | 手動編集の管理。add/edit/hide のCRUD、生成時合成（`resolve`）、`(source, source_id)` 再適用、自動バックアップ（直近5世代） |
| `tools/core/sources/osm.py` | OSM PBF → 共通スキーマ。`category_for` はSPEC 6.1通り。Way座標はノード平均。`addr:*`・読みタグを拾う |
| `tools/core/sources/kokudo.py` | 国土数値情報（医療→学校）→ 共通スキーマ。版・スキーマ・コード検証、休校除外、学校コード枝番 |
| `tools/core/merge.py` | 重複統合。候補検出（互換カテゴリ・50m・名称類似）＋適用/取消（`merged_into`） |
| `tools/core/normalize.py` | 表記ゆれ正規化（`normalize_variants`。NFKC・区切り除去・かな相互変換・ヘボン式ローマ字） |
| `tools/core/kana.py` | 読みの取得（OSMタグ由来・pykakasi生成） |
| `tools/core/export.py` | 県別 `.search.db` 再生成（FTS別表記・index・meta＋出典・`named`/pykakasiトグル） |
| `tools/core/validate.py` | 出力検証11項目＋現行DB比較レポート |
| `tools/cli.py` | `import-osm` / `import-kokudo` / `merge` / `export` / `validate` / `compare` の薄いラッパー |
| `tools/gui.py` | Streamlit 5画面（ダッシュボード・取り込み・POI閲覧編集・重複統合確認・生成） |
| `tests/`（11ファイル・66件） | core単体＋アプリ互換クエリ(a)〜(e)＋再適用＋FTS別表記＋アダプタ＋統合テスト |
| `requirements.txt` | 依存4件（理由付き1行コメント） |
| `README.md` | セットアップ・使い方・出典表記・rebuild禁止の注意 |
| `out/` | 生成物5県分＋検証レポート＋比較レポート。一覧は `out/SUMMARY.md` |
| `SPEC_stage3_addendum.md` | 第3段階の追記仕様（指揮担当作成） |

## 2. セットアップ・起動方法と `requirements.txt` の理由

```bash
cd sandbox/data-tool
python3 -m venv .venv            # 専用venv（../../venv は使わない）
.venv/bin/pip install -r requirements.txt
.venv/bin/python -m tools.cli import-osm --pref hiroshima
.venv/bin/python -m tools.cli import-kokudo --dataset medical --pref hiroshima
.venv/bin/python -m tools.cli import-kokudo --dataset school --pref hiroshima
.venv/bin/python -m tools.cli merge --pref hiroshima
.venv/bin/python -m tools.cli export --pref hiroshima   # 生成＋検証を表示
.venv/bin/python -m tools.cli validate --db out/hiroshima.search.db
.venv/bin/python -m tools.cli compare --pref hiroshima  # out/compare_hiroshima.md
.venv/bin/python -m pytest tests/ -q
.venv/bin/streamlit run tools/gui.py
```

動作確認環境: Python 3.9.6 / osmium 4.3.1 / pytest 8.4.2 / streamlit 1.50.0 / pykakasi 2.3.0。

依存の理由（`requirements.txt` にも記載）:

- `osmium` — OSM PBF読み込み（PyOsmium。`sources/osm.py` で使用）
- `streamlit` — GUI（`tools/gui.py`）
- `pytest` — 単体テスト（`tests/`）
- `pykakasi` — 漢字→読み生成（`core/kana.py`。タグ読みが無い行の補完用。誤変換があるため既定OFF）

点-in-ポリゴンは標準ライブラリの自前実装（レイキャスティング法）。`shapely` は入れていない。

## 3. 実行したテストと結果（現行DB比較レポートを含む）

- `pytest tests/ -q`: **66件全PASS**（第1段階20件＋第2段階29件＋第3段階17件）。
- 実データ取り込み（広島PBF）: `parsed: 69190` / `named: 34876`（現行総件数・named件数と完全一致）、`inserted: 68998`、`assigned_elsewhere: 192`。所要約2分37秒。
- `export --pref hiroshima`（第1段階時点）: 68,998件生成。検証11項目すべて合格（`place:*`=4285件、FTS「広島」ヒット、11.0MB）。
- `compare --pref hiroshima`: `out/compare_hiroshima.md` に出力。要点は次の通り。
  - 総件数: 現行 69,190 / 新 68,998 / 差 **-192**。
  - カテゴリ差分は `named` -175、`public_transport:stop_position` -7、`amenity:ferry_terminal` -6、他4カテゴリ各-1のみ。新規・消失カテゴリなし。
  - 差-192の内訳: 県境を跨ぐWay・ノード（`complete_ways` クリッピングの裾野）192件が県判定で他県側に帰属（島根136/山口36/岡山19/鳥取1）。フル解像度境界（`prefectures.geojson`）でも192件中0件が広島県内であり、簡易境界の誤判定ではない。192件はすべて参照DBに存在する（現行 `build_search_db.py` はPBF内を無フィルタで全件取り込むため）。`shop:convenience` の差は0件（800/800）、`place:*` の差は0件（4285/4285）。
- GUI起動確認: `streamlit run tools/gui.py` で HTTP 200、エラーなし。第2段階でAppTestによる5画面の実操作確認済み（ALL PASS）。
- 第2・第3段階の実績は「9」「10」に記載。最終生成物5県（合計238,307件）はいずれも検証11項目PASS（`out/validate_*.md`、`out/SUMMARY.md`）。

## 4. 仕様との差異（変えたこと、やらなかったこと、その理由）

- `prefecture_for_point` にbbox事前絞り込みを入れた（意味は同一、速度のため。ポリゴン→bboxの優先順位は不変）。
- `overlay` テーブルに `target_source` / `target_source_id` 列を追加した（SPEC 5.2で追加可。再取り込み後の再適用に使用し、テストで確認済み）。
- 比較レポート機能を `validate.py` に配置した（SPECのファイル一覧に `compare.py` が無いため。`cli.py` は薄いまま）。
- `import_osm` / `import_kokudo` はファイルの県に帰属した行のみ挿入し、他県判定の行は挿入しない（各県PBF・国土数値情報は県別配置のため、各県側に収まる。現行のような無フィルタ全件取り込みはしない）。
- `places` 末尾に `search_text TEXT` を追加（Antigravity側のFTS5可否対応のため。FTS投入文字列と同一内容。末尾追加のみで既存カラム不変。検証項目12で存在確認）。
- `named` 除外は生成時のみ（`--exclude-named` / GUIトグル、既定ON）。
- `address` は `addr:full` 優先、なければ `addr:*` 部品を連結、取れなければNULL（第1段階では任意のため）。国土数値情報の住所は所在地属性をそのまま格納（学校は市区町村名を除いた形）。
- やらなかったこと: 第4段階のすべて（CSV取り込み、検索テスト画面、配置画面）。

## 5. 既知の制限・未実装

- 県境付近のPOIは、現在は行政界で1県のみに帰属する。将来、県境をまたぐ検索を実装する場合は、県境からの距離で複数県のDBに重複して入れる設計が要る。
- OSMのrelationは取り込まない（node/wayのみ。現行と件数一致＝69,190＝で裏付け済み）。
- 取り込みは約2分半かかる（PyOsmiumパース＋純Python県判定のため）。
  メモリ・時間とも名前付きPOI件数にほぼ線形（広島実績: 69,190件で取込約2.5分・export数秒・pykakasi版約10秒）。
  exportは行データを3重（resolve辞書・records・fts_records）に保持するため、
  広島の数倍の県でも数百MBに収まる見込み。詳細は指揮担当への報告（2026-09-29）を参照。
- GUIの長時間処理はspinner＋完了後表示であり、真のリアルタイムログストリーミングではない。
- `MATCH` 構文上 `'` を含む語（例: `sebun'irebun…`）はそのままでは検索できない。アプリ側のクエリ組み立て時の課題として共有済み（Antigravityのフォールバック対応時）。`LIKE` 側はバインド変数で問題なし。
- 未実装: 第4段階のすべて（CSV取り込み、検索テスト画面、配置画面）。
- 生成物サイズ（5県合計約48MB）: 鳥取5.5 / 島根6.2 / 岡山13.3 / 広島16.1 / 山口7.2MB（＋広島pykakasi版19.2MBは実験物）。

## 6. 本番側（`application/`）に必要な変更（第1・第2段階の一覧）

本ツールは触っていない。以下は人間側の作業。アプリのコード変更が必要なものと運用で済むものを分けた。

### アプリのコード変更が必要なもの

- 検索DB名の小文字統一（例: `hiroshima.search.db`）に対応するため、アプリ側の探索名追加または配置時のリネーム運用が必要（現行は `search.db` / `Hiroshima.search.db` 等を探索）。
- 学校（`amenity:school` 等）・医院（`amenity:doctors`）は `searchNearbyPlaces` の対象外の可能性（SPEC 12の転記。要確認）。
- `amenity:dentist`（歯科診療所）は本ツールが5県すべてで出力する新規カテゴリ。`PoiCategory.HOSPITAL` のprefixesに無く、アイコン・検索対象の対応要否をアプリ側で判断すること。

### 運用で済むもの（アプリ変更不要）

- `build_prefectures.py` の置き換え実行（本ツールのスコープ外。人間が行う）。
- 追加カラム（`address/source/kana/search_text`）・`places_coords_idx`・`meta` は追加のみで既存クエリに影響しない。
- 第2段階のFTS別表記はアプリ変更不要（`MATCH` の対象が増えるだけで、表示は `places.name` のまま。ヒット数増加は仕様通り）。
- `places.search_text` はLIKEフォールバック専用（表示に使わない。FTS投入文字列と同一のため二重管理はしないこと。更新は `export` 再実行のみ）。
- `places.kana` はアプリは読まない（将来の読み表示用に予約）。`--pykakasi` 使用時のみ誤読混入の可能性があり、既定OFF運用とする。
- FTS の `rebuild` 禁止（ツール運用の注意。別表記が消える。復旧は `export` 再実行）。
- ファイルサイズ（5県合計約61MB。鳥取6.9/島根7.8/岡山16.7/広島20.6/山口9.0MB）は端末ストレージ上軽微と見込むが、配置時に確認すること。

## 7. 出力スキーマの互換性検証の結果

- `validate` 12項目は5県すべて合格（`out/validate_*.md`、`out/SUMMARY.md`）。項目12は `search_text` 存在確認（Antigravity対応で追加）。
- 先頭7カラム `id, osm_type, osm_id, name, category, lat, lon` はSPEC 3.1と完全一致。末尾4列（`address, source, kana, search_text`）のみ追加。
- FTS構成（`name, category`、`content='places'`、`unicode61`）は現行と同一。`rowid`＝`places.id` 対応を確認（orphan/missing 0）。
- アプリ発行クエリ(a)〜(e)をテストで実行し成功（`tests/test_export_validate.py`）。`place:*` は5県とも維持（広島4285/岡山5299/山口3604/鳥取2695/島根2309）で逆ジオコーディング用データを維持。
- 現行DBとの直接比較は広島のみ可能（参照DBが広島のみ）。結果は「3」の比較レポート参照。

## 8. 取り込んだデータのライセンス・出典一覧

- OSM PBF（`data/raw/*.osm.pbf`、5県分）: © OpenStreetMap contributors、ODbL。japan-latest（2026-09-20付）からの切り出し（人間提供）。アプリ・DBでの出典表記が必要。
- 行政界GeoJSON（`data/raw/boundaries/`）: 人間提供のコピー。ライセンス・出典の詳細は未整理。
- 国土数値情報（`data/raw/kokudo/`、医療第3.0版・学校第2.0版、5県分）: PDL1.0。出典表記はREADMEおよび各DBの `meta.source_summary` に記録済み（URL・年度・版）。
- CSVは未取り込みのため、追加の出典義務は発生していない。

## 9. 第2段階追記（表記ゆれ対応・方式A）

- 新規: `tools/core/normalize.py`（`normalize_variants` 中心。NFKC・中黒/ハイフン/長音除去・カタカナ⇔ひらがな・ヘボン式ローマ字の自前表）、`tools/core/kana.py`（タグ読み・pykakasi読み）、テスト `test_normalize.py`（18件）・`test_kana.py`・`test_stage2.py`。
- `requirements.txt` に `pykakasi` を追加（漢字→読み生成。タグ読みが無い行の補完用。誤変換があるため既定OFF。`out/kana_sample.md` に33件の精度サンプル）。
- `master.db` の `poi` に `kana` 列、`overlay` に `kana` 列を追加（既存DBは `init_db` / `ensure_schema` 時のマイグレーションで付与。SPEC 5.1の許容範囲）。
- `sources/osm.py` は `name:ja-Hira`（優先）/`name:ja_rm` を拾って `poi.kana` に格納する。広島PBFでは名前付きPOIのうち22,014件（約32%）にタグ読みがあった（`name:ja-Hira` はPBF全体で386,781オブジェクトに存在）。合成テストでも動作確認済み。
- `export.py` は FTS の `name` に別表記をスペース区切り追記（`places.name` は不変）。`--pykakasi`（既定OFF）でのみタグ無し行にpykakasi読みを付与。`places.kana` に採用した読みを格納。
- 全量実績: 既定生成は68,998件・14.5MB・検証PASS。`--pykakasi` 版は約10秒で生成（名前キャッシュあり）、タグ22,014件＋pykakasi46,983件、19.2MB、検証PASS（`out/hiroshima_pykakasi.search.db`）。スポット確認で「せぶん/ひろしまえき/とうじょう」の表記違い検索が動作すること、「三次市」がタグ読み（みよし）で正しく引けることを確認。
- CLI/GUIに `--pykakasi` / チェックボックス（既定OFF）を追加。GUIの県セレクト既定値を広島に変更（データの無い鳥取が先頭だったため）。
- **注意: FTS の rebuild 禁止**。外部コンテンツFTS（`content='places'`）のため、`INSERT INTO places_fts(places_fts) VALUES('rebuild')` を実行すると `places.name` から再構築されて別表記が消える。`rebuild` は使わないこと。別表記を戻す手順 = `export` の再実行（決定的に再生成される）。
- 既知の落とし穴: 外部コンテンツFTSは列読み出し時に content表の値を返すため、索引内容の検証は `MATCH` で行うこと（読み出し比較は不可。`tests/test_stage2.py` の `_fts_match` が正例）。
- pykakasi採用判断（指揮担当へ）: 地名で確定誤り約24%（`out/kana_sample.md`: ○24/×8/△1。系統は固有名読みと町サフィックスの「まち」化）のため、既定OFFを推奨・実装済み。ON/OFFは切替可能。

## 10. 第3段階追記（国土数値情報の取り込み・広島）

- 新規: `tools/core/sources/kokudo.py`（医療→学校アダプタ）、`tools/core/merge.py`（重複統合）、CLI `import-kokudo`/`merge`、GUIに国土数値情報取り込み・POI閲覧編集・重複統合確認を追加。テスト `test_kokudo.py`・`test_merge.py`（計66件全PASS）。
- 版の検証: 配置ファイルは医療P04-20_*/学校P29-21_*で、GML内スキーマ名も `KsjAppSchema-P04-v3_0` / `KsjAppSchema-P29-v2_0` のため第3.0版/第2.0版と確定。旧版（非商用）の混入なし。読むのはGeoJSONのみ（両方に配布あり）。新規依存なし。
- コードリスト照合（決め打ちなし）: MedClassCd（1=病院,2=診療所,3=歯科診療所）、SchoolClassCd-v2_0（13コード全対応。暫定3件は承認通り）、ClosedSchoolCode（0=調査なし,1=開校中,2=休校中）。広島実データに未知コードなし。
- `source_id` 基準: 医療は `medical:名称@住所`（P04に固有IDなし。広島で重複0件）。学校は `school:学校コード`＋分校・キャンパス重複時の決定論的枝番（`#2`…。例: 広島大学×10、F134210109797は原典側で3大学が同一コードのため枝番で分離）。
- 休校: P29_007==2は広島0件のため除外0件。==9（不明）7件は「休校を示さない」ため含めた（ログ・ここに記録）。
- 注意: P04は休止中の施設を含むが属性にフラグが無く除外不可。全件取り込みとした。
- 注意: `amenity:doctors`（医院）は国土数値情報に対応区分が無く、OSM由来262件が残存する。医院の公的カバーは課題として残る。
- 統合されずOSM単独で残った医療機関（広島実績: hospital 174 / clinic 31 / dentist 36 の計241件）は、国土数値情報側に対応物が見つからなかったOSM由来データであり、実在性・最新性はOSM側の品質に依存する（未検証）。
- 統合実績（承認方針(a)〜(d)通り）: 医療359件＋学校839件→残差分を追加ラウンドで適用し計1,226件、候補残0。生存kokudo 1,197行。GUI/CLI双方で取消可（`merge --undo`）。
- 生成: 73,475件（osm 67,772＋medical 4,291＋school 1,412）。検証11項目PASS。`meta.source_summary` に件数＋出典URL・年度・版・ライセンスを記録。READMEに出典表記を追加。
- 4県展開（鳥取・島根・岡山・山口）: 広島と同一手順で完了。想定外なし（未知コード・SHAPE不足・休校該当いずれも0件）。全県合計238,307件、統合計3,883件、5県とも検証PASS。内訳は指揮担当への完了報告（2026-09-29）を参照。
