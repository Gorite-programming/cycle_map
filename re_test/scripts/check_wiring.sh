#!/usr/bin/env bash
# check_wiring.sh — 新規クラスが「定義ファイルとテスト以外」から参照されているかを機械的に検査する。
# 使い方: cd re_test && ./scripts/check_wiring.sh
# 終了コード: 未接続が1つでもあれば 1。加点の根拠に使うこと(未接続=得点なし)。
set -u
SRC="app/src/main/java"
CLASSES=(
  AdaptiveRerouteManager TravelTimeEstimator PenaltyMultiRouteFinder TravelMode
  OpeningHoursParser SearchRanking SearchNormalizer GenreDictionary
  OfflinePackageManager SolarCalculator OnlineFeatureGate ConnectivityState
  MapViewModel MapTopBar MapControls AccuracyCircleOverlay NetworkStatusIndicator ThreeStageBottomSheet RouteSelectionSheet
  GoogleNavHeaderView GoogleNavFooterView LaneHint TileSourceSelector
)
fail=0
for c in "${CLASSES[@]}"; do
  def=$(grep -rlE "(class|object|interface|fun|enum class) $c\b" "$SRC" | head -1)
  if [ -z "$def" ]; then echo "MISSING-DEF  $c"; fail=1; continue; fi
  users=$(grep -rlw "$c" "$SRC" | grep -v "^$def$" | wc -l | tr -d ' ')
  if [ "$users" -eq 0 ]; then echo "UNWIRED      $c  (defined in $(basename "$def"), no other main-source user)"; fail=1
  else echo "ok           $c  ($users user file(s))"; fi
done
echo "--- big files (limit 500 lines) ---"
find "$SRC" -name "*.kt" -print0 | xargs -0 wc -l | awk '$1>500 && $2!="total"{print "OVER500       "$1"  "$2; f=1} END{exit f}' || fail=1
exit $fail
