# Port status

Kept by the orchestrator. States: todo, in progress, done, diverged (see `divergences.md`).

| Area | iOS sources | Android | State |
|---|---|---|---|
| Gradle skeleton | `project.yml` | root build, modules (AGP 9.5, Kotlin 2.4, JDK 17, compileSdk 37.2) | done |
| Domain types | `TripTrack/Models/` | `core/model`, 83 tests | done |
| Strings, 13 languages | `TripTrack/Localization/` | `tools/strings`, `core/designsystem` res and `AppStrings` facade: 175 of 180 logic functions ported, 93 tests, 10,647 Swift outputs matched | done |
| Map and fog | `Views/MyMap/FogMetal/`, `Services/Reveal/` | decided by `spikes/fog` (docs/fog-spike.md): MapLibre GL ES + OpenFreeMap + C++ custom layer; production `core/map` next | in progress |
| API contract | `Services/Sync/`, `APIClient`, `AuthService` | `docs/api-contract.md`, 90 routes, verified against backend master | done |
| Recording math (gates, Kalman, breaks, auto-trip policy, gap fill) | `Services/`, `Models/` | `core/logic`, 146 tests | done |
| Geo and place math (geohash, polyline, places, segments, moments) | `Services/`, `Models/` | `core/logic`, 140 tests; polyline byte-identical to Swift vectors | done |
| Journeys, history, Atlas data, regions | `JourneyManager`, `HistoryFolding`, `Services/Reveal/`, `RegionAtlas` | `core/logic`, 164 tests; MapRegions.json bundled | done |
| Units, Measure, energy, rewards, PRO gate | `DistanceUnit`, `Measure`, `EnergyEstimate`, `GamificationManager`, `PlusGate` | `core/logic`, 122 tests; 58 badge rules | done |
| Database | `Persistence/` | `core/data`: 15 Room tables (Core Data v24), stores, 224 tests | done |
| Network and auth | `APIClient`, `TokenStore`, `AuthService` | `core/network`, 80 tests; Keystore AES-GCM tokens; Google login call | done (device Keystore check pending) |
| Design system and tab bar | `Views/Theme/`, `CustomTabBar` | `core/designsystem`, `app` | in progress |
| Recording and auto-start | `TripManager`, `LocationManager`, `AutoTripService`, `AutoTripPolicy` | `core/location` | todo |
| Sync engine | `SyncQueue`, `Services/Sync/` | `core/sync` | todo |
| Trip list and detail | `Views/Feed`, `Views/TripDetail` | `app` | todo |
| Atlas | `Views/MyMap/` | `app`, `core/map` | todo |
| Places, journeys, segments | `PlaceManager`, `JourneyManager` | | todo |
| Profile, social, garage, settings | `Views/Profile`, `Views/Garage`, `Social/` | | todo |
| Release | | Play listing, closed test | todo |
