# TripTrack for Android: port plan

Started 30 October 2026. The goal is a native Android app with the same behaviour, numbers, wire
contract, strings and look as the iPhone app, on the same backend and the same accounts: a person
who moves from an iPhone to an Android phone keeps their trips, atlas, places and garage.

## Stack

| Area | Choice | Why |
|---|---|---|
| Language | Kotlin, JDK 17 toolchain, latest stable AGP and Gradle, version catalog | Standard for a native Android app |
| SDK levels | minSdk 29 (Android 10), targetSdk 36, compileSdk 37.2 | Android 10 adds background location and activity recognition runtime permissions; Compose requires compile SDK 37 |
| UI | Jetpack Compose with a design system ported from iOS (`AppTheme`, `AppType`, Inter) | The app must look like TripTrack, not like a Material sample |
| Async | Coroutines and Flow | |
| DI | Manual `AppGraph` built in `Application` | No code generation; mirrors the iOS singletons and keeps builds fast |
| Storage | Room (KSP) mirroring the Core Data model v24, exported schemas, tested migrations | |
| Network | OkHttp and kotlinx.serialization behind a thin `ApiClient` mirroring iOS `APIClient` | The envelope, error codes and refresh logic are ported, not reinvented |
| Background | Foreground service of type `location` for recording, WorkManager for sync | Android 14+ rules |
| Auto-start | Activity Recognition `IN_VEHICLE`, Bluetooth A2DP connection of the chosen car stereo, Android Auto `CarConnection` | Equivalents of the iOS motion, audio-route and CarPlay triggers |
| Map | MapLibre Native, OpenGL ES build pinned to 13.7.1, OpenFreeMap vector tiles; the fog is a C++ custom layer running the iOS 0.8.0 Metal passes on GLES 3.0 | Decided by the fog spike (`docs/fog-spike.md`): the fog draws in the map's own frame, costs 0.07–0.22 ms per frame; no key, no billing |
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
| 2 | Recording (service, Kalman, gates, drafts, auto-start); sync engine; Google sign-in (backend endpoint on a branch); trip list and trip detail; Atlas on the chosen map stack | |
| 3 | Places, journeys, checkpoints and segments, photos, profile and social feed, garage, settings, manual trip, notifications | |
| 4 | Release: Play listing, background location declaration and video, closed test (12 testers for 14 days, required for this personal Play account), production | |

Each task runs in its own clone under `~/OneZeeProjects/trip-track-android-wt/<task>` on its own
branch. The orchestrator reviews, runs the tests, merges into `master` and pushes.

## Decisions taken

- Accounts (owner, 30 October 2026): Android signs in with Google only, iOS keeps Sign in with Apple
  only. They are separate accounts: no email login, no linking of sign-in methods and no sync
  between an iPhone and an Android phone for now. The backend gets a Google login endpoint on a
  branch; the owner deploys it. Storage is chosen so that linking can be added later.
- Android starts without in-app purchases, and PRO is hidden the way the iOS app hides it in the
  Russian storefront. Google Play stopped paying developers whose payout bank is in Russia
  (December 2024), and RuStore pays only an ИП or a company (February 2026).
- Sync inside Android works like on iOS: publishing and the feed go through the same queue, and
  Cloud Sync between two Android phones of the same Google account stays opt-in.
- CarPlay maps to Android Auto later; widgets and Live Activities map to an ongoing notification
  with live numbers, which the foreground service needs anyway.

## Open questions for the owner

- Which Android phones are available for road tests (a Samsung or a Xiaomi matters: they stop
  background apps most aggressively).
- Map tiles: free OpenFreeMap tiles, or Google Maps with a billing account. The spike will show
  whether either one meets the fog quality bar.
