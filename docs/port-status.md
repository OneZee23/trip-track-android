# Port status

Kept by the orchestrator. States: todo, in progress, done, diverged (see `divergences.md`).

| Area | iOS sources | Android | State |
|---|---|---|---|
| Gradle skeleton | `project.yml` | root build, modules (AGP 9.5, Kotlin 2.4, JDK 17, compileSdk 37.2) | done |
| Domain types | `TripTrack/Models/` | `core/model`, 83 tests | done |
| Strings, 13 languages | `TripTrack/Localization/` | `tools/strings`, `core/designsystem` res and `AppStrings` facade: 175 of 180 logic functions ported, 93 tests, 10,647 Swift outputs matched | done |
| Map and fog | `Views/MyMap/FogMetal/`, `Services/Reveal/` | `core/map`: MapLibre 13.7.1 GL ES + OpenFreeMap, C++ fog layer (mist, night, cells, 3 LODs, live reveal), MapHost with iOS camera rules, routes and markers; 54 tests; header pin check | done (GPU timing and pixel read-back on a phone pending) |
| API contract | `Services/Sync/`, `APIClient`, `AuthService` | `docs/api-contract.md`, 90 routes, verified against backend master | done |
| Recording math (gates, Kalman, breaks, auto-trip policy, gap fill) | `Services/`, `Models/` | `core/logic`, 146 tests | done |
| Geo and place math (geohash, polyline, places, segments, moments) | `Services/`, `Models/` | `core/logic`, 140 tests; polyline byte-identical to Swift vectors | done |
| Journeys, history, Atlas data, regions | `JourneyManager`, `HistoryFolding`, `Services/Reveal/`, `RegionAtlas` | `core/logic`, 164 tests; MapRegions.json bundled | done |
| Units, Measure, energy, rewards, PRO gate | `DistanceUnit`, `Measure`, `EnergyEstimate`, `GamificationManager`, `PlusGate` | `core/logic`, 122 tests; 58 badge rules | done |
| Database | `Persistence/` | `core/data`: 15 Room tables (Core Data v24), stores, 224 tests | done |
| Network and auth | `APIClient`, `TokenStore`, `AuthService` | `core/network`, 80 tests; Keystore AES-GCM tokens; Google login call | done (device Keystore check pending) |
| Design system and tab bar | `Views/Theme/`, `CustomTabBar` | `core/designsystem`, `app`: theme, Inter, house components, tab bar, five-tab shell, language and theme pickers; 81 tests and 40 screenshots | done |
| Recording and auto-start | `TripManager`, `LocationManager`, `AutoTripService`, `AutoTripPolicy` | `core/location`: foreground service with live notification, raw GPS + own Kalman, crash recovery, drafts, Activity Recognition, car stereo, Android Auto, permissions and recording health; 75 tests | done (real-phone checks pending) |
| Sync engine | `SyncQueue`, `Services/Sync/` | `core/sync`: queue, gates, push/pull, photos, WorkManager; 123 tests | done (AppGraph wiring with the screens) |
| Trip list and detail | `Views/Feed`, `Views/TripDetail` | feed, trip detail (live map hero, fullscreen, moments, photos, checkpoints, segments, charts, export), journeys, drafts; 34 screenshots; debug SeedActivity | done (replay, trip edit, comments, share later) |
| Atlas | `Views/MyMap/` | Atlas tab: sheet (summary, list, region, cards), home, «Вид карты», beta, share poster; RevealedLayerStore and territory backfill wired | done (region labels, someone else's map, photo pin grouping later) |
| Places, journeys, segments | `PlaceManager`, `JourneyManager` | Places tab: panel, modal search, suggestions, empty states, place screen; journeys in the trip screens | done (checkpoint → place creation, offline paper tiles later) |
| Profile, social, garage, settings | `Views/Profile`, `Views/Garage`, `Social/` | Me (hero with the road scene, journal, badges), garage with passport and catalog, settings; 20 screenshots | done (Me → trip/journey linking, auxiliary profile screens, photo zoom next) |
| Sign-in, account, Cloud Sync, onboarding | `AuthService`, `CloudSyncView`, `Views/Onboarding` | `app/feature/account`, `onboarding`: Google sign-in (Credential Manager), session rules, account and sync screens, sync wiring, five-page onboarding; 115 tests | done (needs Google OAuth clients and the backend branch deployed) |
| Release | | Play listing, closed test | todo |
