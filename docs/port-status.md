# Port status

Kept by the orchestrator. States: todo, in progress, done, diverged (see `divergences.md`).

| Area | iOS sources | Android | State |
|---|---|---|---|
| Gradle skeleton | `project.yml` | root build, modules | in progress |
| Domain types | `TripTrack/Models/` | `core/model` | in progress |
| Strings, 13 languages | `TripTrack/Localization/` | `tools/strings`, `core/designsystem` res | in progress |
| Map and fog | `Views/MyMap/FogMetal/`, `Services/Reveal/` | `spikes/fog`, then `core/map` | in progress |
| API contract | `Services/Sync/`, `APIClient`, `AuthService` | `docs/api-contract.md` | in progress |
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
