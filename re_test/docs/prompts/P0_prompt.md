# P0 依頼文(別セッションのエージェント向け)

> このファイルの内容をそのまま、実装担当エージェントの最初の指示として渡す。

---

あなたは `cycle_map/re_test/` の **P0(土台整備)** を担当するエージェントです。

## 最初に読む(この順番)
1. `/Users/gorite/Desktop/cycle_map/AGENTS.md`(プロジェクト全体のルール)
2. `re_test/overhaul_plan.md`(合意事項。特に §2-A / §2-B / §2-C、§5、§6)
3. `re_test/plans.md`(指示書。今回は §0 絶対ルール、§2、§4 P0 を精読)
4. `re_test/cautions.md`(ロジック・UI の注意点)
5. `re_test/parity_checklist.md`(移植台帳)

## 今回のスコープ: P0 のみ
`plans.md` §4 の **P0-1〜P0-8**。P1 以降(経路・検索・地図スタイルなど)には**着手しない**。

## 進め方(2段階。段階1の後で必ず停止して報告)

### 段階1: 準備(コードを書かない)
1. `git status` で `test/` に未コミットの変更が残っていないか確認する。**残っていたら作業を止め**、一覧を報告して人間(Gorite)の対応を待つ。
2. `re_test/` に不足している文書を作成する: `README.md`、`architecture_spec.md`、`roadmap_p0_to_p7.md`、`scorecard_90percent.md`(内容は `overhaul_plan.md` §4 の各 Component に従う。数値は実測で裏取りする)。
3. `parity_checklist.md` §3 の「`test/app` のユニットテスト件数」を集計して追記する。
4. 以下を報告して**停止**し、人間の承認を待つ:
   - 作成した文書の一覧
   - `diff -r application/routing-core test/routing-core` の差分要約
   - P0-1〜P0-8 の具体的な作業手順と、リスク

### 段階2: P0 の実装(承認後のみ)
`plans.md` §4 の順に実施。1 タスク(P0-n)ごとに:
- `cd re_test && ./gradlew :routing-core:test :osm-importer:test :app:testDebugUnitTest` が**全件成功**すること。
- 失敗したら次へ進まない。原因と対処を報告する。

## 重要な制約
- **Git 操作は禁止**(`git add / commit / push / stash / reset` を含む)。完了時に変更ファイル一覧・diff 要約・テスト結果を報告し、指示を待つ。
- **`application/` と `test/` は変更しない**(読み取りのみ)。変更は `re_test/` 配下だけ。
- `routing-core` と `osm-importer` は**複製しない**(共有。`plans.md` P0-5)。
- `osmdroid` は、代替が動くことを確認するまで**削除しない**(P0-6)。
- 大容量データ(`data/*.pbf`、`work/`、`venv/`)、`local.properties`、keystore に触れない。
- ロジックを変える修正と、ファイル移動(分割)を**同じ作業に混ぜない**。分割は挙動を変えない。
- Google のタイル・アイコン・ロゴ等は使わない。
- 迷ったら**推測で進めず質問**する。

## 完了報告に含める項目
1. 変更ファイル一覧と差分の要約
2. テスト結果(件数と合否)
3. `parity_checklist.md` の更新内容
4. 気づいたリスク・未解決の点
5. 次に進める作業の提案(コミットの判断は人間)
