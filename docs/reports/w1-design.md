# w1-design

`core:designsystem` and the app shell. Branch `w1-design`, not pushed. Every test passes offline:
172 in `core:designsystem` (81 of them new) and 15 in `app`.

## Ported

| iOS | Android (`core/designsystem/.../designsystem/` unless noted) |
|---|---|
| `Views/Theme/AppTheme.swift`: `AppTheme`, `AppTheme.Colors`, adaptive statics | `theme/AppTheme.kt`: fixed accents in `AppTheme`, `AppColors.light` / `.dark` |
| `Views/MyMap/AtlasTheme.swift` | `theme/AtlasTheme.kt`; the tab bar and the sheet take their tokens from it |
| `Views/Theme/AppFont.swift`: `Font.inter`, `interScaled`, `InterFace`, `AppType` | `theme/AppFont.kt`, `theme/AppType.kt`; Inter weights 400 to 800 and Press Start 2P in `res/font`, OFL texts in `assets/licenses` |
| `Views/Theme/ThemeManager.swift` | `theme/ThemeManager.kt` (`ThemeMode`, key `appThemeMode`), `theme/TripTrackTheme.kt` |
| `AppTheme.swift`: `SurfaceCard`, `GlassBackground`, `GlassPill`, `PressableCardStyle`, `HoldableCardStyle`, `CardPressResponse`, `Haptics` | `components/Surfaces.kt`, `components/CardPress.kt`, `theme/Haptics.kt` |
| `Components/AppConfirmDialog.swift` | `components/AppConfirmDialog.kt`: `AppConfirmDialog`, `AppConfirm` by flag and by item |
| `Components/ActionPopoverList.swift` | `components/ActionPopoverList.kt`: `ActionPopoverList`, anchored `ActionPopover` |
| `Components/ContentSizedSheet.swift` | `components/ContentSizedSheet.kt` |
| `Profile/SettingsOptionPicker.swift`; `SheetCloseCircle` from `Garage/GarageComponents.swift` | `components/SettingsOptionPicker.kt` |
| `Components/ToastView.swift` | `components/Toast.kt`: `ToastView`, `ToastHost` |
| `Components/NavCircleIcon.swift` | `components/NavCircleIcon.kt` |
| `Plus/ProBadge.swift`: `ProBadge`, `PlusBadgeVisibility` | `components/ProBadge.kt` |
| `Components/EdgeScrims.swift` | `components/EdgeScrims.kt` |
| `Navigation/CustomTabBar.swift`, `App/AppTab.swift`, `hideAppTabBar` in `ContentView.swift` | `navigation/CustomTabBar.kt` (`TabBarMetrics`), `navigation/AppTab.kt`, `navigation/TabBarVisibility.kt` |
| `ContentView.swift`, `Localization/LanguageManager.swift` | `app`: `MainActivity`, `shell/AppShell.kt`, `shell/LanguageManager.kt`, `shell/TabStore.kt`, `shell/PlaceholderScreens.kt` |

Also new: a house `Text` reading `LocalTextStyle` and `LocalContentColor`, `AppGlyph` (the SF Symbols
the components use, redrawn), `iosShadow` (Core Animation radius treated as sigma),
`ContinuousRoundedRectangle` for the iOS `.continuous` corners of the dialog, its buttons and the
toast, `AppRadius` and `AppSpacing`, and `endonym` / `badge` on `AppLanguage`.

`MainActivity` is an `AppCompatActivity`, drawn edge to edge. The status bar glyphs, the navigation
bar and the window ground follow the in-app theme. The ported bar sits over five tabs. Лента, Атлас
and Места are titled placeholders. Запись hides the bar and has the iOS dark back disc. Я holds
the language and theme rows of the iOS settings card, with the house picker. The language is
Android's per-app language through AppCompat and `android:localeConfig`. App bundle language splits
are off, so the picker can switch to a language the phone never downloaded.

## Tests

From the checkout root, with `JAVA_HOME` set to the Homebrew JDK 17:

```sh
./gradlew --no-daemon --offline :core:designsystem:testDebugUnitTest :app:testDebugUnitTest
./gradlew --no-daemon --offline :app:assembleDebug :core:designsystem:lintDebug :app:lintDebug
python3 tools/strings/generate.py --check
```

All three pass. Lint has no errors. Its warnings predate this task: strings (typos, plural candidates,
`getIdentifier`), the missing launcher icon and target 36.

Screenshots: Roborazzi on Robolectric SDK 35 with native graphics, Russian copy. Each test run
compares against the committed images: 36 in `core/designsystem/src/test/screenshots` (every
component in light and dark, the tab bar at Pixel 7 size) and 4 in `app/src/test/screenshots` (Лента
and Я at Pixel 7 size). Record again with `-Proborazzi.test.record=true`. Swapping two reference
images made the test fail, so the comparison is live. Robolectric draws no system bars.

Behaviour tests:

- Dialog: an answer dismisses before its handler runs, Cancel is last and says «Отмена», the scrim is
  hidden from TalkBack and has no click action, the kind picks the colour, a double tap runs the
  handler once, the item variant hands the item over, back closes, the window is plain and full
  screen and keeps the theme's status bar glyphs.
- Sheet and picker: a pick reports itself then closes, the chosen row is selected, close and scrim
  dismiss, a list over eight rows scrolls, the sheet enters from below.
- Toast: leaves after 2.5 s, undo after 3.5 s, undo runs and dismisses, polite live region.
- Popover: a row closes the popover then acts, a tap beside it closes it.
- Tab bar: `CustomTabBarLiftTests` numbers, `AppTabTests` raw values and the «groups» fallback,
  selection, reselect, the record disk has no label, unseen drafts are spoken, `HideAppTabBar`.
- Theme: the `ThemeMode` store, `AppType` roles against Я (ExtraBold only for the title and the tile
  number), fixed and scaled sizes under a 2× font scale, bundled fonts, continuous corners, shadow
  conversion, hold timing, the `PlusBadgeVisibility` table, endonyms.
- App, on the real `MainActivity`: tabs switch and are remembered, Запись hides the bar and goes
  back, system back returns to Лента, the theme picker repaints the bars and the window, the
  language picker sets the per-app language and closes in the same frame.

Each test that guards a fix from this task failed when I put the bug back.

## Differences from iOS

Fifteen rows in `docs/divergences.md`: text sizing, glass without blur, redrawn symbols, the dialog
(double-tap guard, no wait queue), modal windows and their glyphs, the popover order, the sheet,
hold timing, «Remove animations» as reduce motion, haptics constants, the tab bar gap over
three-button navigation, 48 dp touch targets, per-tab state and back to Лента, the theme cross-fade,
and the per-app language that stays unsaved until chosen.

## Left

- `themePickerFootnote` says «iPhone» in every language. It needs Android wording through
  `tools/strings`; I did not edit generated strings.
- Handjet is not copied: nothing ported uses it yet (LVL pill, trip card).
- The placeholders stay until their screens are ported. The Я rows move into the settings sheet.
- Nothing was checked on a device (no emulator, by instruction): real insets, the gap over
  three-button navigation, dialog and sheet windows over the status bar, haptics, the popover near
  a screen edge.
- The per-app language below Android 13 (AppCompat's own store) is not exercised. Robolectric runs
  SDK 35 only: SDK 36 needs Java 21 and no older runtime is installed.
- Dragging a scrolled-to-top list down scrolls it and does not close the sheet. The header drags
  the sheet.
- No launcher icon yet.

## Build notes

- Catalog: `appcompat` 1.7.1, `compose-ui-test-junit4` (from the BOM), `roborazzi-compose`. Fetched
  once online into `~/.gradle`: Roborazzi, Compose ui-test, androidx.test, espresso, kotlinx-io,
  `lifecycle-livedata` for AppCompat. Offline builds work from that cache.
- No Roborazzi Gradle plugin: its report pulls tens of megabytes of webjars through the proxy. The
  test tasks set `roborazzi.test.verify` and `roborazzi.test.record` the way the plugin does, so the
  same `-P` switch works if the plugin is added later.

Privacy check (`git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'`):
no hits.
