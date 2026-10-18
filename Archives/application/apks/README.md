# CycleMap Debug APKs

このディレクトリには、実機テスト・検証用のデバッグ APK を配置しています。

## 運用ルール
- リポジトリの肥大化を防ぐため、**「最新の日時入り APK 1件」＋「latest（最新版リンク）」の計2件のみ**を保持・コミットします。
- 新しい APK を配置する際は、古い日時入り APK を `git rm` で削除してください。

## APK 仕様・インストール方法
- **署名**: デバッグ署名（Android Studio / Gradle デバッグキーストア）
- **インストール**:
  - **ADB 経由**: `adb install -r application/apks/cyclemap-debug-latest.apk`
  - **直接ダウンロード**: ブラウザで最新 APK の Raw URL からダウンロードして端末で「不明なアプリのインストール」を許可してインストール
