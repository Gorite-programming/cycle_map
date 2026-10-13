# CycleMap re_test — プロジェクト概要と設計指針

## 1. 概要
`re_test` は、完全オフラインの自転車ナビゲーションアプリ「CycleMap」の新しい正系統プロジェクトです (`overhaul_plan.md` §2-A 決定)。
従来のプロトタイプ (`test/`) の課題（巨大ファイル、状態の散在、osmdroid と MapLibre の二重管理、直書きカラーなど）を解消し、クリーンな MVI / UDF アーキテクチャと MapLibre (3Dベクター) への一本化を進め、Google Maps 互換度 全体 ≥90% (フロント ≥92%、バック ≥88%) を完全オフライン前提で達成することを目的とします。

## 2. 系統とモジュール関係
- **正の系統**: `re_test` が将来の本番となる新正系統。
- **本番維持**: `application/` は、`parity_checklist.md` の全項目がチェックされ全体 ≥90% に達するまで破壊的変更を行わず本番として維持。
- **共有モジュール**: `:routing-core` および `:osm-importer` は `application/` の実装を正とし、Gradle のサブプロジェクト参照（`project(":routing-core").projectDir = file("../application/routing-core")`）により共有（コードの複製は行わない）。
- **プロトタイプ凍結**: `test/` は参照用として凍結。

## 3. 技術スタック
- **Kotlin**: 2.0.21
- **AGP**: 8.10.1
- **compileSdk / targetSdk**: 35, **minSdk**: 33, **Java**: 11
- **Jetpack Compose**: Compose BOM による宣言的UI (MVI / UDF)
- **地図レンダリング**: MapLibre Native Android SDK (11.8.0) + Protomaps PMTiles
- **ルーティング**: 純粋 JVM A* 探索 (`:routing-core`)
- **データベース**: SQLite (`*.search.db`) - LIKE互換 (`places.search_text` 列、FTS5非対応端末セーフ)

## 4. ビルド・実行環境
- ビルド実行ディレクトリ: `re_test/`
- コマンド例:
  ```bash
  cd re_test
  ./gradlew :routing-core:test :osm-importer:test :app:testDebugUnitTest
  ./gradlew :app:assembleDebug
  ```
- **ビルド・リリースルール**: クリーンな HEAD からビルドした APK のみ正式版とする。Keystore は各環境（Mac: `~/.android/debug.keystore`, Edge 2: `/mnt/data` 設定）に従う。
