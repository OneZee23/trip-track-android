# w24-pro: PRO from a promo code

## Ported

| Android | iOS reference |
| --- | --- |
| `PlusEntitlementStore.kt`, `AppGraph.kt` | `PlusAccess.swift`, `PlusStore.swift`, app lifecycle refresh |
| `PlusAPI.kt`, `AuthDTOs.kt`, `FeatureApis.kt`, `ApiEndpoint.kt` | `PlusAPI.swift`, `AuthService.swift`; backend contract from the current iPhone client |
| `ProfileIdentityStore.kt`, `ProfileBackgroundColors.kt`, `MyProfileScreen.kt` | `ProfileBackground.swift`, `ProfileBackgroundPicker.swift`, profile identity handling |
| `MeViewModel.kt`, `ProfileHeroCard.kt`, `ProfileJournal.kt`, `MeTab.kt` | `ProfileView.swift`, `ProBadge`, `ProStatus` |
| `SettingsScreens.kt` | `PrivacySettingsView.swift` and `AuthService.pushPlusCosmetics` |
| `PromoCodeSheet.kt` | Android-only; iOS omits promo entry under App Review 3.1.1 |
| `ProManageSheet.kt` | `ProManageSheet.swift`, `ProManageModel.swift` |
| `GarageScreens.kt` | `VehicleStageState` and vehicle garage presentation in iOS |
| Android-only `tools/strings/android.json` entries and generated resources | iOS string tables for shared copy; Android-only sheet copy translated in all 13 locales |

The account-scoped preferences record stores account id, active state, `until`, `promoUntil` and fetch time. Auth account changes clear another account's record immediately; a signed-in account loads only its own cache and refreshes. Refresh runs at sign-in and app resume. Network errors retain the cache. A timer turns `isPlus` off at `until` without waiting for another request. `PLUS_UNAVAILABLE` falls back to `/auth/me.plusUntil`. Successful redemption saves the returned status and refreshes it from the server.

Paid profile backgrounds can be saved while PRO is active. When it expires the stored id remains, while the effective screen background returns to the free choice. The own-name badge follows the privacy preference and syncs through `showPlusBadge` in profile updates. The «Я» page exposes the date-only PRO row and a management sheet with the background choice and a second-code action. Promo entry is available from Settings for signed-in and signed-out users.

The Russian and English copy shipped as requested: «Промокод» / “Promo code”; «Код включит PRO на этом аккаунте.» / “The code turns on PRO for this account.”; «Активировать» / “Redeem”; «PRO включён» / “PRO is on”; «PRO до {date}» / “PRO until {date}”. Error and offline messages match the brief. The signed-out message is «Войдите, чтобы активировать код: PRO привязан к аккаунту.» / “Sign in to redeem a code: PRO belongs to your account.”

## Pass 2 corrections

- `ProfileBackgroundColors.kt` now matches all 18 iOS display names and all eight paid gradients. `MyProfileScreen.kt` draws the Storm contours, Carbon grid and Nebula stars over the gradient by calling `drawCover`; `ProfileHeroCard.kt` now enters its paid-background path. `MePresentationTests.everyProfileBackgroundKeepsItsIosNameAndGradient` checks all names and gradient availability. A paid Nebula hero screenshot covers the hero rendering path. iOS: `ProfileBackground.swift`.
- `ProfileJournal.kt` and `ProfileSections.kt` place the PRO row inside the expanded sections after Garage and before Clubs. The disclosure title adds «PRO» only when the row is shown. `MeScreenshotTests.proRow` records the expanded state in both themes. The no-trip profile keeps Garage, PRO, Clubs order. iOS: `ProfileView.swift`.
- The profile row date uses `dMMM`; «Ваш PRO» and the promo success date use `dMMMyyyy` through `LocalizedDateFormatter`. `ProManageSheet.kt` wraps «Ввести ещё код» in its own card. `MeScreenshotTests.proManage` and `PromoCodeShotTests.success` cover both themes. iOS: `ProStatus.swift`, `ProManageSheet.swift`.
- `SettingsScreens.kt` places «Промокод» after account and sync rows. The corresponding settings and privacy screenshot baselines were re-recorded in both themes.
- `PromoCodeSheet.kt` now creates the button's infinite transition only while busy. `PromoCodeShotTests` covers idle and busy states in both themes.
- `PlusEntitlementStore.refresh()` recalculates cached access before a network call. `PlusEntitlementStoreTests.refreshAfterCachedUntilExpiresDropsProBeforeOfflineRequest` verifies an offline refresh after expiry.
- `PromoCodeSheet.kt` displays the signed-out state and sign-in entry for `USER_NOT_AUTH` and `INVALID_REFRESH_TOKEN`. `PromoCodeErrorTests.expiredOrMissingSessionUsesTheSignedOutState` covers both cases; signed-out screenshots cover both themes.

## Remaining differences

- iOS `CosmeticRetentionStore` is not ported. Android follows the simple `ProfileBackground.effective(id:isPlus:)` rule and retains the stored background id.
- The management sheet currently shows the background choice and redeem action. Vehicle card platform, avatar frame and route line colour belong to wave 2; manual trip belongs to wave 3. Their management callbacks are reserved behind the internal feature flag.
- Android has no StoreKit or purchase controls. Promo entry remains Android-only.

## Verification

`python3 tools/strings/generate.py --check --ios ~/OneZeeProjects/trip-track-094`, `python3 tools/strings/validate.py`, and `python3 -m unittest discover -s tools/strings -p 'test_*.py'` passed (29 tests).

Roborazzi recording commands:

```sh
./gradlew --no-daemon --offline -Proborazzi.test.record=true :app:testDebugUnitTest \
  --tests 'com.onezee.triptrack.feature.me.MeScreenshotTests' \
  --tests 'com.onezee.triptrack.feature.settings.PromoCodeShotTests' \
  --tests 'com.onezee.triptrack.feature.settings.SettingsStackShotTests'
./gradlew --no-daemon --offline -Proborazzi.test.record=true :app:testDebugUnitTest \
  --tests 'com.onezee.triptrack.feature.trip.ui.HiddenPartsScreenshotTests.settingsPrivacy*' \
  --tests 'com.onezee.triptrack.feature.trip.ui.HiddenPartsCompactScreenshotTests.settingsPrivacy*'
./gradlew --no-daemon --offline -Proborazzi.test.record=true :app:testDebugUnitTest \
  --tests 'com.onezee.triptrack.feature.me.MeScreenshotTests.proRow*'
```

They recorded the profile PRO row, management sheet, paid background picker and hero, promo states, and changed settings/privacy layouts in both themes.

Final verification command `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home JAVA_TOOL_OPTIONS=-Duser.home=$HOME/.gradle GRADLE_USER_HOME=$HOME/.gradle ./gradlew --no-daemon --offline -Proborazzi.test.verify=true test lintDebug` passed: 2,400 tests, no failures, errors or skips; lint passed. `python3 tools/strings/generate.py --check --ios /Users/onezee/OneZeeProjects/trip-track-094`, `python3 tools/strings/validate.py` and `python3 -m unittest discover -s tools/strings -p 'test_*.py'` passed; the string tests ran 29 cases.
