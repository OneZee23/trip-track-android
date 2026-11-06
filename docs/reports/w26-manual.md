# Manual trip parity

## Ported

- `ManualTripRules.kt`, `ManualTripPreset.kt`, and `ManualTripSuggestions.kt` port `ManualTripBuilder`, `ManualTripDateDefaults`, `ManualTripDurationPolicy`, `ManualTripDurationText`, `ManualTripActiveField`, `ManualTripPreset`, `ManualTripFrequentPlaces`, and `ManualTripSuggestions`.
- `TripNamer.kt` uses the trip title reverse geocoder and geocode cache for map-picked names and local suggestions. It returns locality, then region, and leaves unknown places unnamed.
- `ManualTripSheet.kt` ports the two stages, inline point fields, local and remote suggestions, map selection, three ordered via stops, route preview, title, pixel date line, expandable date/time and vehicle controls, stat tiles, summary, 15-minute duration policy and route-length floor, save and return-trip setup. It uses the shared house date picker and existing MapLibre map.
- `JourneySheets.kt` exposes the shared house date picker to the manual-trip sheet.
- `ManualTripsApiTests.kt` covers the one-request route body with three intermediate stops and router error handling. The routing proxy and its documented divergence remain in `docs/divergences.md`.
- Manual-trip builder timestamps and metadata, date bounds, 15-minute duration policy, presets, frequent-place order, suggestion order and deduplication, title, summary, and active-field rules have unit coverage. `ManualTripNamerTests.kt` covers locality/region naming and cache reuse. Existing reward, badge, stats, and odometer tests cover manual-trip reward exclusion and odometer inclusion. `ManualTripRepositoryTests.kt` covers persistence and upload queueing.
- `ManualTripScreenshotTests.kt` records both stages in light and dark themes: empty, results, offline search, route with and without stops, each route error, and return-trip toast.
- Visible additions are generated from `tools/strings/android.json` across all 13 languages.

## Remaining differences

- Apple `MapKit` search, directions, route alternatives, geocoding, and map rendering are platform-only. Android uses the existing server search/routing proxy and MapLibre. The Android reverse geocoder follows the same cache naming rule; when it has no locality or region, the point stays unnamed. Route provider geometry and map appearance can differ from iOS.
- The router error `tooManyStops` uses iOS's generic failure copy. Android sends all selected stops in one server request, up to three vias.
- The screenshot harness seeds the return-trip toast as a standalone toast because the manual-trip modal covers underlying app toasts. The app flow still presents the normal return-trip toast after dismissing the trip sheet.

## Verification

- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home JAVA_TOOL_OPTIONS=-Duser.home=$HOME/.gradle GRADLE_USER_HOME=$HOME/.gradle ./gradlew --no-daemon --offline -Proborazzi.test.verify=true test lintDebug` — passed, 3,217 JVM tests with no failures or skips; lint passed.
- `python3 tools/strings/generate.py --check --ios /Users/onezee/OneZeeProjects/trip-track-094` — passed.
- `python3 tools/strings/validate.py` — passed for 13 languages.
- `python3 -m unittest discover -s tools/strings -p 'test_*.py'` — 29 tests passed.
- No emulator or adb was used.
