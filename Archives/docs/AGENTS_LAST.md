# CycleMap AIエージェント固定指示文

## 1. 役割と目的
あなたは完全オフライン自転車ナビゲーションアプリ **CycleMap** の開発支援AIエージェントです。本プロジェクトは複数のAIエージェントが連携・交代で進めるため、以下の指示は**全作業の前提条件**であり、例外なく遵守してください。

## 2. 絶対遵守事項（Git・運用）

### 2.1 Git操作は「完全承認制」
- **エージェントは `git add` / `git commit` / `git push` を絶対に実行しない**。
- コード変更後は、変更内容の要約・diff・テスト結果を人間（Gorite）に報告し、コミットやプッシュについての**明示的な指示を待つ**。
- 「コミットしないでください」という指示が無くても、これがデフォルトの動作とする。

### 2.2 作業開始前の必須手順
- 作業着手前や時間が空いた場合は、必ず `git pull` を実行して最新のコード・状況を取り込む。
- 作業完了・コミット後（人間がコミットした後）は、速やかに `git push` するよう人間に促す。

### 2.3 系統（ブランチ/ディレクトリ）の識別
本プロジェクトには3つの系統が存在する。作業対象を誤らないこと。

| 系統 | パス | 状態 | エージェント作業可否 |
|------|------|------|----------------------|
| 現行本番 | `application/` | 保護系統。互換度≥90%達成までは破壊的変更禁止。 | 修正可（慎重に） |
| 旧プロトタイプ | `test/` | 凍結。参照用のみ。 | **新規開発禁止**。重大グ修正のみ。 |
| 新・正系統 | `re_test/` | 次期本番。MVI/UDF、MapLibre単一化、500行規則適用。 | 積極的に開発対象。 |

- `:routing-core` および `:osm-importer` の正の実体は `application/` が持つ。`re_test/` は Gradle の `projectDir` 参照でコードを共有する。**重複クローン・コピーは禁止**。

## 3. プロジェクト構成の認識

```
cycle_map/
├── application/          # Gradleプロジェクト(:app, :routing-core, :osm-importer)
│   └── gradlew は application/ から実行
├── test/                 # 凍結（MapLibre先行実験場）
├── re_test/              # 新・正系統（次期本番）
├── sandbox/data-tool/  # 検索DB生成ツール(Python+Streamlit)
├── build_prefectures.py  # 県別データパイプライン
└── scripts/push_data.sh  # 実機へのデータ転送+SHA-256検証
```

## 4. 作業開始前チェックリスト

- [ ] `git pull` 済みか。
- [ ] 作業対象が `application/` / `test/` / `re_test/` のどれか明確になっているか。
- [ ] `application/` 内作業ら `local.properties` の `sdk.dir` が存在するか（Windows/Mac共通）。
- [ ] 修正対象が「単体テストのみのクラス（UNWIRED）」でないか `check_wiring.sh` で確認済みか（`re_test/` 作業時）。
- [ ] 修正対象ファイルが500行以内に収まるか（`re_test/` のみ）。

## 5. ビルド・テスト要件

コード変更後は、対象モジュールに応じて以下を実行し、結果を報告に含めること。

```bash
cd application

# JVM単体テスト（必須）
./gradlew :routing-core:test :osm-importer:test

# Android単体テスト（app変更時）
./gradlew :app:testDebugUnitTest

# APKビルド（UI変更時）
./gradlew :app:assembleDebug
```

- **テスト全件PASS（281件）だけを報告してはならない**。「どのテストが何を検証しているか」「未接続（UNWIRED）クラスが増えていないか」も確認すること。
- `re_test/` 作業時は `scripts/check_wiring.sh` を実行し、`UNWIRED` / `OVER500` がないことを確認する。

## 6. 技術的制約と既知の問題（修正前に必ず確認）

- **データ分離**: ルーティング用データ（OSM `.graph`）と表示用データ（地理院タイル）は厳密に分離。Google/OSMラスターデータをグラフに混ぜない。
- **データ配置**: APKにデータを同梱しない。アプリは `getExternalFilesDir(DOCUMENTS)/CycleMap/` を参照。実機検証時は `scripts/push_data.sh` を使う。
- **FTS5非対応**: Android標準SQLiteはFTS5非対応実機が多い。検索DBは `search_text` 列（LIKE検索用）も保持して互換性を確保。
- **既知バグ（修正前に `application/bugs.md` を確認）**:
  - `LazyMappedRouting.readEdge()` は2GB超でオーバーフロー（`offset.toInt()`）。
  - `nearestNodeIndex` はO(n)線形走査。
  - Critical/Highの大半はコミット `3a6faf2` で修正済みのため、**盲目的な再修正を避ける**。
- **CyclingCostModel**: `motorway` を除外。最小倍率0.90はA*ヒューリスティックの下限。JVM側と `LazyMappedRoadGraph` で同期を保つ。
- **ルーティング**: 本番はA*のみ。`HsaRouting.kt` / `AltRouting.kt` は実験用。

## 7. ライセンス・帰属表示の遵守

- OSM（ODbL）、Overture Maps（CDLA-Permissive-2.0）、国交省（PDL1.0）、地理院、行政界ポリゴン（CC BY 4.0）の帰属表示は、オフライン時も含めて維持する。
- 新たに外部データやライブラリを導入する場合は、ライセンスの互換性を確認し、`application/DISTRIBUTION_NOTES.md` とアプリ内ライセンス画面への追記を人間に指示すること。

## 8. 作業完了時の報告フォーマット

作業完了後、以下を人間に告すること。

1. **変更概要**: 何を、なぜ、どう変更したか（3行程度）。
2. **変更対象ファイル一覧**: パス付き。
3. **diff**: `git diff` の主要部分（または変更箇所の説明）。
4. **テスト結果**: 実行したテストコマンドと結果（PASS/FAIL数）。
5. **未解決事項・リスク**:  knowingly 残した課題や、レビュー時に注目すべき点。
6. **次のアクション提案**: コミット指示待ち / 追加修正の必要性 / 他エージェントへの引き継ぎ事項。

## 9. 禁止事項

- 勝手な `git commit` / `git push`。
- `test/` への新規機能追加。
- `application/` への破壊的変更（互換度≥90%達成までは特に慎重）。
- 2GB超ファイルのメモリ上での一括読み込み（mmapを使用）。
- Windows環境でのシンボリックリンク依存操作（`build_search_db.py` 等はmacOS/Linux専用機能として認識）。
- テスト未実施での「完了」報告。

---

**本指示文は `AGENTS.md` および `README.md` を基に作成された、AIエージェント作業時の最的な固定指示です。**
