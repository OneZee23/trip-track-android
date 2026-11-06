# w25-showcase: PRO cosmetics on Android

## Ported

| Android | iOS reference |
| --- | --- |
| `core/model/.../AvatarFrame.kt`, `feature/me/ProfileIdentityStore.kt`, `MyProfileScreen.kt`, `ProfileHeroCard.kt`, `feature/social/ui/SocialComponents.kt` | `Views/Profile/AvatarFrame.swift`, own-profile chooser in `MyProfileView.swift`, `AuthService.pushPlusCosmetics`, profile and public profile rendering |
| `core/model/.../VehicleCardStyle.kt`, `VehicleFinish.kt`, `feature/garage/VehicleCatalogViewModel.kt`, `VehicleEditSheet.kt`, `core/data/VehicleStore.kt`, `feature/garage/VehicleStage.kt`, profile and public vehicle render paths | `Views/Garage/VehicleCardStyle.swift`, `Models/VehicleFinish.swift`, `VehicleGarageCustomization.swift`, platform/finish controls in `VehicleEditFormView.swift`, vehicle stage |
| `core/model/.../RouteLineStyle.kt`, `core/map/.../RouteAppearance.kt`, `AppGraph.kt`, trip, recording, journey, atlas and passport map screens | `Models/RouteLineStyle.swift`, `AppPreferencesView.swift`, `RouteMapView` route renderer and live recording map |
| `core/model/.../ProShowcase.kt`, `feature/me/ProShowcaseSheet.kt`, `ProManageSheet.kt`, `MeTab.kt`, `SettingsScreens.kt` | `Views/Plus/ProShowcase.swift`, `ProShowcaseSheet.swift`, `ProShowcaseTileView.swift`, `ProShowcaseHost.swift`, `ProManageSheet.swift`, `afterProManage` |
| `core/model/.../ProShowcaseTests.kt`, `ProfileWireTests.kt`, `StorageContractTests.kt`, `core/map/.../RouteLineStyleAppearanceTests.kt`, `MeScreenshotTests.kt`, `GarageScreenshotTests.kt` | `ProShowcaseTests`, `ProShowcaseTileLabelTests`, `PublicCosmeticsTests`, effective-rule and vehicle wire tests |

The account now chooses avatar frames, vehicle card platforms and finishes, profile backgrounds, and phone-local route colours from the PRO showcase. Expired PRO choices remain stored and render with the free style. Avatar frames use the existing `/auth/profile-update` request and the vehicle platform and finish use vehicle sync. PRO management rows show each current choice and open its showcase after the management sheet closes. The internal future-feature gate and English placeholders are removed; the manual-trip row remains for wave 3.

Route styles feed the trip map, speedless foreign and preview routes, live recording map, journey, Atlas, hidden-parts editor, trip completion summary and vehicle passport. Unknown-speed gap dashes remain grey, matching the existing route-gap contract.

Android reuses generated iOS strings; no new Android-only visible copy or dependencies were needed. Compose sheets, Canvas previews and MapLibre layers use the Android house components; native font and drawing rasterization can differ by a few pixels from SwiftUI/MapKit.

## Verification

String generation check and validation passed:

```sh
python3 tools/strings/generate.py --check --ios /Users/onezee/OneZeeProjects/trip-track-094
python3 tools/strings/validate.py
python3 -m unittest discover -s tools/strings -p 'test_*.py' -v
```

Roborazzi recording passed with the full test suite:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home JAVA_TOOL_OPTIONS=-Duser.home=$HOME/.gradle GRADLE_USER_HOME=$HOME/.gradle ./gradlew --no-daemon --offline -Proborazzi.test.record=true test
```

Recorded light and dark images cover all four showcase kinds, the avatar-frame chooser, vehicle platform and finish choosers, and a trip route drawn with the selected Ice colour.

Final verification:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home JAVA_TOOL_OPTIONS=-Duser.home=$HOME/.gradle GRADLE_USER_HOME=$HOME/.gradle ./gradlew --no-daemon --offline -Proborazzi.test.verify=true test lintDebug
```

Result: 3,199 tests passed, with no failures, errors or skips; `lintDebug` passed.

## Pass 2 review fixes (6 Nov 2026)

Applied the review against iOS 0.9.4:

- `ProShowcaseSheet.kt` now renders the profile strip in both background and frame showcases: 120 high, 16 radius, current account name (guest name when blank), avatar emoji on a translucent disc, white 2 dp ring and selected 3 dp frame. Reference: `ProShowcaseTileView.swift` (`ProShowcaseProfilePreview`, `ProDemoData.currentName`).
- The vehicle preview uses `VehicleStage` at 210 dp for the vehicle being edited, the selected platform and effective finish. It falls back to the first garage vehicle, then a blue pixel car, with `proDemoCarEmptyTitle` when no vehicle is present. Reference: `ProShowcaseTileView.swift` (`ProShowcaseVehiclePreview`).
- `ProManageSheet.kt` receives the current platform and route line labels, with the localized default labels when the choice is empty. Reference: `ProManageModel.swift`.
- The finish row shows its effective finish; its chooser title uses `garageFinish`. References: `VehicleGarageCustomization.swift`, `VehicleEditFormView.swift`.
- The own-profile frame row opens the frame showcase. The vehicle form's platform row opens that vehicle's showcase and saves a picked platform through the edit model. The plain avatar-frame and platform list choosers are removed; the finish chooser remains. References: `MyProfileView.swift`, `VehicleEditFormView.swift`.
- The vehicle form now edits three `modTags`, limiting each to 24 grapheme clusters and saving through the existing vehicle sync field. Inactive PRO shows a locked tag row without purchase UI. `VehicleModTags.effective` still hides tags after expiry while preserving their stored values. References: `VehicleGarageCustomization.swift`, `VehicleModTags.swift`.
- Added `VehicleModTagsTests` for grapheme limits, sanitation, and expiry. Re-recorded only the changed Roborazzi screenshots: PRO management; background, frame and vehicle showcases; finish chooser; and the vehicle form's engine section after its locked row was added. Removed obsolete avatar-frame and platform chooser screenshot tests/baselines.

The tag fields were not inspected with an on-screen keyboard because this pass explicitly forbids emulator and adb use. Compose text rendering and vehicle stage rasterization can differ slightly from SwiftUI; no other intentional behavior differences were introduced in this pass.

Pass-two string checks passed:

```sh
python3 tools/strings/generate.py --check --ios /Users/onezee/OneZeeProjects/trip-track-094
python3 tools/strings/validate.py
python3 -m unittest discover -s tools/strings -p 'test_*.py' -v
```

The changed screenshots were recorded with:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home JAVA_TOOL_OPTIONS=-Duser.home=$HOME/.gradle GRADLE_USER_HOME=$HOME/.gradle ./gradlew --no-daemon --offline -Proborazzi.test.record=true :app:testDebugUnitTest --tests 'com.onezee.triptrack.feature.garage.GarageScreenshotTests.formEngine*' --tests 'com.onezee.triptrack.feature.garage.GarageScreenshotTests.vehicleFinishChooser*' --tests 'com.onezee.triptrack.feature.me.MeScreenshotTests.proManage*' --tests 'com.onezee.triptrack.feature.me.MeScreenshotTests.proShowcaseVehicle*' --tests 'com.onezee.triptrack.feature.me.MeScreenshotTests.proShowcaseBackground*' --tests 'com.onezee.triptrack.feature.me.MeScreenshotTests.proShowcaseFrame*'
```

Final verification passed: 3,197 tests, no failures, errors or skips; `lintDebug` passed. `git diff --check` passed.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home JAVA_TOOL_OPTIONS=-Duser.home=$HOME/.gradle GRADLE_USER_HOME=$HOME/.gradle ./gradlew --no-daemon --offline -Proborazzi.test.verify=true test lintDebug
```
