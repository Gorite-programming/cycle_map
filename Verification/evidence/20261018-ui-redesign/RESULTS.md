# UI redesign implementation evidence (2026-10-18)

## Changes included

- Reduced dynamic POI clutter using prominence and zoom thresholds; reduced the number of emitted map POIs.
- Added theme-specific map-label text colors and stronger label halos; reduced locality and minor-road label sizes at close zooms.
- Replaced the default Material drawer with a custom sliding drawer, dimmed scrim, tap-outside dismissal, and back-button dismissal.
- Standardized floating map controls on white surfaces and made sheet height and drag thresholds responsive to screen density.
- Replaced fixed light-only colors in search cards, destination search, route information panels, and charts with Material theme roles.

## Build and device checks

- `cd application && ./gradlew :app:assembleDebug` — passed after the UI changes.
- `git diff --check` — passed.
- Samsung device (`R5CR70NDMCM`): installed and launched the debug APK; drawer open and dismissal were observed; floating controls and search UI were inspected.
- Device settings after inspection: location enabled, airplane mode off, Wi-Fi on. No device settings were changed.
- No unit tests were run as part of this UI redesign pass.

## Screenshots

- [CycleMap main map](main-p1-p2-final.png)
- [CycleMap search screen](search-final.png)
- [CycleMap drawer](drawer-final.png)
- [CycleMap after back dismissal](main-after-back.png)
- [Google Maps on the same device](google-main-comparison.png)
- [Main map after the follow-up fixes](followup-main.png)
- [Expanded location and weather panel after an upward drag](followup-summary-drag.png)
- [Location and weather panel after a downward drag](followup-summary-collapse.png)
- [Route preview with destination pin and current location visible](final-preview.png)
- [Active navigation after the HUD and location marker changes](final-navigation-active.png)

The Google Maps capture uses its light map while CycleMap uses its current dark map, so it is useful for comparing layout and control placement but is not a same-style visual comparison. The latest CycleMap map capture still shows dense base-map place labels; label sizing and POI filtering reduce the clutter, but do not eliminate it.

## Follow-up fixes for the newly reported issues

- MapLibre two-finger pitch now accepts a wider shove angle and lower movement threshold. Compass-camera updates are suspended during active map gestures so they cannot fight the user's tilt/rotate/pan gesture. These two-finger/compass combinations were not directly exercised on-device.
- Route preview disables current-location camera follow and fits the route with extra top/bottom space for the search bar and preview card. On the Samsung device, both the destination pin and current-location marker were visible above the preview panel in `final-preview.png`.
- Navigation header/footer now have screen-edge margins; the active-navigation target is shifted toward the lower half of the viewport. The small directional triangle was replaced with a blue-ring location puck, and the instruction text no longer repeats the distance. `final-navigation-active.png` records a route started on the device; no moving-bike or changing-GPS trace was available.
- Lane guidance is capped at five lanes and hidden while GPS is weak/lost or the next turn is more than 600m away. Lane refresh is keyed to the maneuver rather than every GPS progress update.
- The place/weather panel now expands on an upward drag and collapses on a downward drag. This interaction was checked on the Samsung device and captured in `followup-summary-drag.png`.
- `cd application && ./gradlew :app:assembleDebug` — passed after these follow-up changes.
- Two-finger pitch with dynamic compass follow still needs direct device confirmation. GPS-weak lane suppression was implemented from signal status but not observed during a deliberately degraded GPS session.
