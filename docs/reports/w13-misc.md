# w13-misc

## Ported

- `ProfileView.swift`, `JourneySuggester.swift`, `JourneySuggestionCards.swift`: «Я» infers a home candidate from the trip library, remembers «Нет» with a 30-day delay and «Да» permanently, then suggests eligible chains. Combining opens the existing journey composer with the chain preselected; it creates nothing before the person saves.
- `VehiclePhotosView.swift`: after the first saved car photo, a per-vehicle one-time question can enable the existing `photosVisible` field.
- `SettingsHintPopover.swift`, `ProfileSettingsSheet.swift`, `AdminModel.swift`, `AdminPanelView.swift`: added the anchored hint bubble and the server-gated «Админ» card/panel. The panel calls `/admin/summary`, `/admin/subscriptions`, `/admin/events`, `/admin/flags`, and `/admin/flag`; authorization remains server-side.
- `BluetoothScanSheet.swift`: the stereo picker shows its Bluetooth-off state and links to Android Bluetooth settings.
- `TrackingView.swift`: the recording map shows the existing car loader until the Android map host reports its first camera state.
- `OnboardingView.swift`: the example card uses the latest completed local trip's preview polyline, distance and average speed when available; otherwise it keeps the sample trip.

## Strings and API

No string keys were added. The question, prompt, Bluetooth-off, onboarding and admin copy already exists in generated Android resources for all 13 languages. Admin endpoints and DTOs were already declared; `AdminApi` now exposes the same calls used by iOS.

## Tests and verification

- Existing `JourneySuggesterTests` (20 cases) cover home ask timing, including the 30-day re-ask threshold and permanent acceptance, and journey chain eligibility and selection.
- Added `AdminAccessTests`: 1 test covers unloaded, non-admin and admin states.
- Added seven light/dark Roborazzi screenshot groups (14 images) in `MeScreenshotTests` for the home/journey prompts, Bluetooth-off, admin card and hint, photo visibility question, recording map loading, and onboarding's own-trip example. The test source compiles, but this environment did not render new baselines: Robolectric failed to resolve its Maven runtime in `--offline` mode.
- `./gradlew --no-daemon --offline :app:compileDebugKotlin` passed.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --offline -Proborazzi.test.record=true :app:testDebugUnitTest` compiled the tests and ran 336; 108 failed before test bodies with `MavenDependencyResolver` / `FileNotFoundException` while loading Robolectric artifacts.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --offline :app:testDebugUnitTest --tests com.onezee.triptrack.feature.settings.AdminAccessTests` passed.
- Required `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --offline -Proborazzi.test.verify=true test lintDebug` failed in `:core:sync:testDebugUnitTest` (12 failures among 54) and `:core:map:testDebugUnitTest` (2 failures among 147), all from Robolectric's offline `MavenDependencyResolver` / `FileNotFoundException`. Gradle stopped before `lintDebug` in this combined invocation.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --offline lintDebug` passed separately.

## Differences from iOS

- The home prompt falls back to coordinates when Android's local geocode cache has no locality name. Prompt entry/exit uses Compose animation and Android haptics.
- The journey suggestion dismissal is stored by the last trip ID in Android preferences. The chain's rules and composer selections use the existing shared logic.
- The admin panel uses Android row formatting and locale data. Calls are protected by the server on every request.
- Map readiness uses `MapHostState.camera`; MapLibre startup does not expose MapKit's exact first-render callback to this screen.
- The onboarding example draws the stored preview polyline on Compose Canvas, not an iOS off-screen map snapshot.
- Roborazzi screenshots are authored for both themes, but verified image baselines remain unrecorded because the required offline Robolectric runtime is missing.
