# Port status

Kept by the orchestrator. States: todo, in progress, done, diverged (see `divergences.md`).

| Area | iOS sources | Android | State |
|---|---|---|---|
| Gradle skeleton | `project.yml` | root build, modules (AGP 9.5, Kotlin 2.4, JDK 17, compileSdk 37.2) | done |
| Domain types | `TripTrack/Models/` | `core/model`, 83 tests | done |
| Strings, 13 languages | `TripTrack/Localization/` | `tools/strings`, `core/designsystem` res: 1571 strings, 25 plurals per language; 180 functions with logic left for manual porting | done |
| Map and fog | `Views/MyMap/FogMetal/`, `Services/Reveal/` | `spikes/fog`, then `core/map` | in progress |
| API contract | `Services/Sync/`, `APIClient`, `AuthService` | `docs/api-contract.md`, 90 routes, verified against backend master | done |
| Algorithms (gates, Kalman, breaks, geohash, polyline) | `Services/`, `Models/` | `core/logic` | todo |
| Database | `Persistence/` | `core/data` | todo |
| Network and auth | `APIClient`, `TokenStore`, `AuthService` | `core/network` | todo |
| Design system and tab bar | `Views/Theme/`, `CustomTabBar` | `core/designsystem`, `app` | todo |
| Recording and auto-start | `TripManager`, `LocationManager`, `AutoTripService`, `AutoTripPolicy` | `core/location` | todo |
| Sync engine | `SyncQueue`, `Services/Sync/` | `core/sync` | todo |
| Trip list and detail | `Views/Feed`, `Views/TripDetail` | `app` | todo |
| Atlas | `Views/MyMap/` | `app`, `core/map` | todo |
| Places, journeys, segments | `PlaceManager`, `JourneyManager` | | todo |
| Profile, social, garage, settings | `Views/Profile`, `Views/Garage`, `Social/` | | todo |
| Release | | Play listing, closed test | todo |
