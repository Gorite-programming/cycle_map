# CycleMap Android Application — プロジェクト概要と設計指針

## 1. 概要
`application/` は、完全オフラインの自転車ナビゲーションアプリ「CycleMap」の正系統 Android プロジェクトです。
従来のプロトタイプ（旧 `test/` および旧 `application/`）の課題（巨大ファイル、状態の散在、osmdroid と MapLibre の二重管理、直書きカラーなど）を解消し、クリーンな MVI / UDF アーキテクチャと MapLibre 3Dベクター（PMTiles）への一本化を完了させた本番プロダクトです。
Google Maps UI/UX 互換度（検索カプセルの0px完全一致アニメーション、全画面オーバーレイ、ステータスバー最適化など）を端末単体・完全オフライン前提で実現しています。

## 2. モジュール構成
本プロジェクトは自己完結型のマルチモジュール構成を採用しています：
- `:app` — Android UI アプリケーションモジュール。Jetpack Compose による MVI/UDF 設計、MapLibre Native 3Dベクター（PMTiles）+ osmdroid ハイブリッド（フォールバック用）、JMA（気象庁）天気連携、play-services-location。
- `:routing-core` — 純粋 JVM ライブラリ。OSM グラフ上の自転車専用 A* 探索アルゴリズム、勾配・路面考慮コストモデル、メモリマップド I/O（mmap）バイナリリーダー。
- `:osm-importer` — JVM CLI ツール。OSM `.pbf` から独自バイナリ `.graph` および空間インデックス `.graph.idx` を高速生成。

※旧系統のコードは `Archives/application/` および `Archives/test/` に安全に退避・アーカイブされています。

## 3. 技術スタック
- **Kotlin**: 2.0.21
- **AGP**: 8.10.1
- **compileSdk / targetSdk**: 35, **minSdk**: 33, **Java**: 11 (ビルド環境は JDK 17/21)
- **UI フレームワーク**: Jetpack Compose (Compose BOM) による宣言的 UI (MVI / UDF)
- **地図レンダリング**: MapLibre Native Android SDK (11.8.0) + Protomaps PMTiles
- **ルーティング**: 純粋 JVM A* 探索 (`:routing-core`)
- **データベース**: SQLite (`*.search.db`) - LIKE互換 (`places.search_text` 列、FTS5非対応端末セーフ) + 表記ゆれ・カテゴリランキング
- **気象データ**: 気象庁（JMA）アメダス連携

## 4. ビルド・実行環境
- **ビルド実行ディレクトリ**: `application/`
- **コマンド例**:
  ```bash
  cd application
  ./gradlew test                                     # 全ユニットテスト
  ./gradlew :routing-core:test :osm-importer:test    # コアモジュール単体テスト
  ./gradlew :app:assembleDebug                       # デバッグ APK ビルド
  ```
- **生成 APK**: `application/app/build/outputs/apk/debug/app-debug.apk`
- **ビルド・リリースルール**: クリーンな HEAD からビルドした APK のみ正式版とする。ビルド台帳・SHA-256 検証ハッシュはルートの [VERSIONS.md](../VERSIONS.md) に記録する。Keystore は各環境（Mac: `~/.android/debug.keystore`, Edge 2: `/mnt/data` 設定）に従う。
