# TripTrack for Android: port plan

Started 30 October 2026. The goal is a native Android app with the same behaviour, numbers, wire
contract, strings and look as the iPhone app, on the same backend and the same accounts: a person
who moves from an iPhone to an Android phone keeps their trips, atlas, places and garage.

## Stack

| Area | Choice | Why |
|---|---|---|
| Language | Kotlin, JDK 21 toolchain, latest stable AGP and Gradle, version catalog | Standard for a native Android app |
| SDK levels | minSdk 29 (Android 10), target and compile 36 | Android 10 is where background location and activity recognition became runtime permissions; it covers almost every active phone |
| UI | Jetpack Compose with a design system ported from iOS (`AppTheme`, `AppType`, Inter) | The app must look like TripTrack, not like a Material sample |
| Async | Coroutines and Flow | |
| DI | Manual `AppGraph` built in `Application` | No code generation; mirrors the iOS singletons and keeps builds fast |
| Storage | Room (KSP) mirroring the Core Data model v24, exported schemas, tested migrations | |
| Network | OkHttp and kotlinx.serialization behind a thin `ApiClient` mirroring iOS `APIClient` | The envelope, error codes and refresh logic are ported, not reinvented |
| Background | Foreground service of type `location` for recording, WorkManager for sync | Android 14+ rules |
| Auto-start | Activity Recognition `IN_VEHICLE`, Bluetooth A2DP connection of the chosen car stereo, Android Auto `CarConnection` | Equivalents of the iOS motion, audio-route and CarPlay triggers |
| Map | Decided by the fog spike (`docs/fog-spike.md`) | Candidates: MapLibre Native with OpenFreeMap vector tiles; Google Maps SDK (needs a billing-enabled Cloud project) |
| Tests | JUnit 4 and kotlin.test on the JVM, Robolectric where Android types are needed, Roborazzi for screenshots | Agents can verify UI without an emulator |
| Ids | `applicationId` `com.onezee.triptrack`, debug suffix `.dev` | Debug and release install side by side, like the iOS `.dev` bundle |

## Modules

```
app/                 Application, MainActivity, navigation, AppGraph, feature screens (a package per feature)
core/model/          domain types, pure Kotlin (JVM module)
core/logic/          algorithms ported one to one with their tests, pure Kotlin (JVM module)
core/data/           Room database, DAOs, repositories
core/network/        ApiClient, DTOs of the wire contract, token storage
core/sync/           sync queue, push and pull, appliers
core/location/       recording service, location providers, auto-start triggers
core/designsystem/   theme, typography, components, string resources
core/map/            map host and fog renderer
tools/strings/       generator from the iOS strings to Android resources
spikes/              throwaway prototypes with their own Gradle builds
```

Feature screens start in `app/`, one package each. They move into modules if build times demand it.

## Waves

| Wave | Work | State |
|---|---|---|
| 0 | Gradle skeleton and domain types; strings generator; fog and map spike; API contract document | running |
| 1 | `core/logic`; `core/data`; `core/network` with the auth client; design system and the tab shell | next |
| 2 | Recording (service, Kalman, gates, drafts, auto-start); sync engine; sign-in (Google and Apple web flow, backend endpoint on a branch); trip list and trip detail; Atlas on the chosen map stack | |
| 3 | Places, journeys, checkpoints and segments, photos, profile and social feed, garage, settings, manual trip, notifications | |
| 4 | Release: Play listing, background location declaration and video, closed test (12 testers for 14 days, required for this personal Play account), production | |

Each task runs in its own clone under `~/OneZeeProjects/trip-track-android-wt/<task>` on its own
branch. The orchestrator reviews, runs the tests, merges into `master` and pushes.

## Decisions taken

- Android starts without in-app purchases. Google Play stopped paying developers whose payout
  bank is in Russia (December 2024), and RuStore pays only an ИП or a company (February 2026).
  PRO bought on iPhone is honoured through the account. Purchases on Android come later, with the
  payout channel the owner chooses.
- Sign-in: Google (Credential Manager, ID token checked by the backend) and Apple through the web
  flow, so iPhone accounts keep working. The backend needs a Google login endpoint; it is prepared
  on a branch and deployed by the owner.
- CarPlay maps to Android Auto later; widgets and Live Activities map to an ongoing notification
  with live numbers, which the foreground service needs anyway.

## Open questions for the owner

- Which Android phones are available for road tests (a Samsung or a Xiaomi matters: they stop
  background apps most aggressively).
- Map tiles: free OpenFreeMap tiles, or Google Maps with a billing account. The spike will show
  whether either one meets the fog quality bar.
