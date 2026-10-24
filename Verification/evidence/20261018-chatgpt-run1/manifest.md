# 検証証跡マニフェスト

- `scorecard.md`: 第三者再現度の採点と所見
- `preflight/`: 端末、OS、対象コミット、初期ネットワーク、APKハッシュ
- `cyclemap-online/`: CycleMapオンラインの検索、経路、ナビ操作
- `cyclemap-offline/`: 機内モード状態での地図、検索、経路、ナビ、回廊、履歴、GPX
- `google-maps/`: 同一店舗名の検索、経路、ナビ比較
- `code-review/`: 報告対象コミットと現行HEADのGradleテスト、配線/ソース確認
- `final-state/`: 実機の設定復元後の状態

PNGは画面証跡、同名XMLは画面上のアクセシビリティ/UI階層取得結果。`foreground-corrections.txt` にアプリ切替時の取り違えを記録し、誤取得画像は証跡から除外した。

APK比較は両方同一SHA-256 (`e2da730237ad0bd50f6d86716132c770ce9ebe618a3b0e74350520fb32e6cbf3`) を確認後、端末からpullした一時コピーを削除した。ローカルビルドAPKは作業ツリーに残っている。
