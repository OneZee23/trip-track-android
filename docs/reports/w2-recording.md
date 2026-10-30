# w2-recording

The recording side is built on top of `core:logic` and `core:data`: a foreground service of type
`location` with raw GNSS fixes, the TripManager port with batch saves, pauses, crash recovery and
drafts, and the finish through the post-processor and the world door. Auto-start listens to
Activity Recognition, the car stereo and Android Auto. There is a permission sequence and a
recording-health screen. 75 Robolectric tests cover it; nothing has run on a phone or an emulator
yet. Branch `w2-recording`, small commits, not pushed.

## What was built

`core/location/src/main/kotlin/com/onezee/triptrack/location/`:

| Android | iOS source |
|---|---|
| `recording/TripRecorder.kt` | `TripManager.swift` (startTrip, handleNewLocation's batch save, isPaused, stopTrip, cleanupOrphanedTrips, adoptRecoverableOrphan, recordingFinishPreview), the recording half of `MapViewModel.swift` (startRecording, togglePause, stopRecording, StartRefusal) |
| `recording/RecordingEnd.kt` | `trimmedEndDate`, `recordingEndDate`, `finalRecordingStats`, `generatePreviewPolyline` |
| `recording/OrphanTriage.kt` | `cleanupOrphanedTrips` (6 h restorable age, 15 min silent resume) |
| `recording/LiveSpeed.kt` | MapViewModel's speedometer (EMA 0.3, two stills snap to zero, decay after 2 s, stale after 10 s) |
| `recording/RawFixLog.kt` | `RawFixLog.swift` (header, line format, 14 days, 20 MB per trip, resume appends) |
| `recording/TripFinisher.kt` | the tail of `stopRecording`, `TripManager.deleteTrip`, `discardDraft` |
| `recording/RecorderState.kt` | state, `StartRefusal`, `PauseSource`, persisted pause session |
| `world/TripWorldEntry.kt` | `TripWorldEntry.swift` (route, rewards, placesAndReveal), `PlaceManager.process(tripId:)` |
| `world/TripRewards.kt` | `GamificationManager.processCompletedTrip`, `saveTripXP`, BadgeManager's ledger keys |
| `world/StoreRoadCollector.kt` | `RoadCollectionManager.processTrip` |
| `world/TrackPostProcessing.kt` | the service half of `PostTripTrackProcessor.swift`, `processUnprocessedTrips` |
| `world/TripNamer.kt` | `geocodeAndNameTrip`, `retryGeocodingForUntitledTrips`, the geocode cache |
| `drafts/DraftDecisions.kt` | `DraftDecisionQueue.swift` (same key, peek/remove one at a time), `MapViewModel.applyDraftDecisions` |
| `auto/AutoTripController.kt` | `AutoTripService.swift` |
| `auto/AutoRecordSettings.kt` | SettingsManager's auto-record keys (`autoRecordMode`, `autoStopTimeout`, `savedBluetoothDevices`) |
| `auto/ActivityTransitions.kt`, `AutoTripReceiver.kt`, `CarConnectionWatcher.kt` | `MotionDetector.swift`, `AudioRouteDetector.swift`; CarPlay's counterpart |
| `provider/PlatformLocationSource.kt` | `RealGPSProvider.swift`, `LocationManager.swift` |
| `service/RecordingService.kt`, `RecordingNotifications.kt`, `NotificationActionReceiver.kt` | `LiveActivityManager.swift`, `NotificationManager.swift`, the Live Activity intents |
| `permissions/PermissionPlan.kt` | `OnboardingPermissionCoordinator.swift`, `OnboardingLocationRequest` |
| `permissions/RecordingHealth.kt` | none (Android only) |
| `LocationGraph.kt` | the singletons that wire the above |

The recorder runs every state change under one lock on one thread, the counterpart of the main
actor. The math is `RecordingMath` unchanged. Points are written every 10 stored points or 15
seconds, counted on stored points as iOS counts them. A pause writes the boundary and the pending
points at once. The stop takes the trimmed tail, then the caller's hint, then now, recomputes the
odometer through `TripDistanceGate`, writes the preview and hands the trip to the finisher outside
the lock: junk is deleted with its journal, a draft gets «Trip recorded … Yours?» and stays out of
the world, a confirmed trip gets rewards, road card and the sync upload, then post-processing,
places and its name. The service stays foreground until that finish ends.

Recovery at launch deletes empty and junk orphans, closes all but the newest at their last fix,
and offers the newest if it is under six hours old. Under fifteen minutes it resumes without a
question (the service is `START_STICKY`, so a killed drive comes back as one trip); older ones wait
for «Continue» or «Finish and save» on the record screen and block auto-starts meanwhile.

Draft decisions from the notification are written to the queue before anything else, then
applied. A dead process is rebuilt by the Application, recovery runs, and the queue is applied;
the next launch applies anything left. A decision for the trip still recording waits.

Auto-start follows AutoTripService rule by rule: «Remind» records a draft at once, «who started it
stops it», a paused trip is never stopped automatically, the stop question with Stop now/Continue,
the 3-minute timer, the 15-minute stale split on reconnect, the 30-second trigger dedup and the
5-second event debounce. The vehicle linked to a saved stereo becomes the trip's car only if it may
still take trips. Activity transitions, boot and app updates re-register.

## Location source decision

Recording uses the platform `GPS_PROVIDER`: raw fixes, one second, distance filter 0, no Google
Play services. Reasons:

- The iOS pipeline is the app's own: Kalman, the continuity gate, the two anchors, the 65 m
  odometer door. It expects raw input with a real Doppler speed and a per-fix accuracy. The fused
  provider would feed it positions it already smoothed and speeds it estimated, filtered twice.
- iOS sets no distance filter while recording (slow yard manoeuvres, and the background rules of
  iOS 16.4). Zero metres here, one second matching CoreLocation's cadence.
- The platform provider exists on every phone, including Huawei without Google services.

CoreLocation blends Wi-Fi and cell into its fixes, so iOS records some coarse fixes (65 to 200 m)
in tunnels and car parks. GPS alone records fewer, and the straight fill at the finish closes more
gaps. A road replacement for fills (RoadGapFiller) is not ported yet. Idle mode adds the network
provider for a quick first position. Fix times are the phone clock minus the fix's age on the
monotonic clock, so GNSS time never disagrees with the trip dates.

Activity Recognition still needs Play services (`play-services-location` 21.4.0), because Android
has no platform equivalent. On a phone without Google services the stereo and Android Auto still
trigger. Android Auto is read through the `CarConnection` provider protocol directly; the Car App
library would bring Guava, media and lifecycle for one query.

## Changes outside core:location

- `core:logic`: `GeometryUtils.simplifyRDP` (iterative, same points as the recursive Swift),
  `RecordingMath.takeTrackPoints()` (the batch drain; without it every fix copied the whole drive),
  `RoadCollection` and `SimilarRecordedTrips` with `RoadRarity.from` and `RoadLevel.from`.
  `TrackTestKit` and `SyntheticDrives` moved to a `testFixtures` source set so `core:location`
  replays the same drives; `Noise` and `assertNear` stay in the test sources.
- `core:data`: new `RecordingStore.kt` (`RoomRecordingStore`) and `RecordingDao`, registered in
  `TripTrackDatabase` with one line. No schema change.
- `app`: `AppGraph` builds the database and stores and the `LocationGraph`; the Application loads
  the settings row (the identity) before the launch recovery; `MainActivity` shows the minimal
  record screen and calls `onAppForeground`. These three files will conflict with the shell and
  sync work and need a merge by hand.
- `gradle/libs.versions.toml`: `play-services-location`.

## UI

`app/.../recording/RecordingScreens.kt`, plain foundation widgets until the design system lands:
the record screen (distance, speed, driving time, GPS state, start, pause/resume, finish, the
recovery question), the permission pages, the recording health and the auto-record mode with the
stereo picker (paired audio devices, car kits first). All words come from the generated iOS strings
except the Android-only ones in `core/location/src/main/res` (English and Russian).

Permissions go one at a time: explanation with one Continue, then the system's question. Precise
location, background location (Android 11+ opens its settings page), activity recognition,
notifications (13+), nearby devices (12+). A refusal moves on; nothing is asked twice; background
location is skipped when precise location was denied. The health screen lists what can stop a
recording (location off, precise location off, notifications, battery optimisation, background
restriction, battery saver, and with auto-record on: background location, activity, Bluetooth)
with a settings link each, plus Samsung, Xiaomi and Huawei steps with their own settings screens
and the app details page as the fallback.

## Tests

| Class | What it holds |
|---|---|
| `RecordingLifecycleTests` (16) | start stamps only a recordable car, refusals, batches of 10 and 15 s, raw journal verdicts, pause boundary and no distance across it, driving clock without pauses, finish into the world, junk deleted with its journal, draft held back, the city drive identical to the pure `RecordingMath` + post-processor replay, the tunnel filled and queued, road card on the second trip |
| `RecoveryAndDraftTests` (12) | fresh resume, older ones asked and blocking starts, Finish and save, six-hour close, empty/junk/older orphans, paused comes back paused, the queue's key and order, «Mine» and «Delete» after process death, no decision for the recording trip |
| `AutoTripControllerTests` (18) | stereo start in auto and remind, unknown device and off mode, refused start and refused service said aloud, paused never stopped, pause cancels the timer, short trip asked then stopped, long trip stopped at once, remind asks only, a draft stopped by the app's rules, Stop now on a paused trip, inactivity after ten minutes and Continue, 30 s dedup with backdating, one reminder per session, stale split on reconnect, Android Auto |
| `PermissionPlanTests` (10) | the sequence on Android 10, 12 and 14, denial skips background, nothing asked twice, double tap and late answers, health issues, vendors |
| `RecordingPiecesTests` (8) | `RecoveryWindowTests`, trimmed tail, end clamps, speedometer |
| `RawFixLogTests` (6) | `RawFixLogTests.swift` |
| `RecordingTextsTests` (5) | notification words in English, Russian and miles, the journal under `noBackupFilesDir`, auto-record keys |

Commands (JDK 17, `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`):

| Command | Result |
|---|---|
| `./gradlew --no-daemon :core:location:compileDebugKotlin` (once online) | downloaded `play-services-location` 21.4.0 and its dependencies |
| `./gradlew --no-daemon :core:location:testDebugUnitTest`, `:app:assembleDebug` (once online each) | fetched `concurrent-futures` 1.0.0 and the `activity` 1.13.0 aar that were not cached |
| `./gradlew --no-daemon --offline :core:logic:test :core:data:testDebugUnitTest :core:location:testDebugUnitTest :core:location:lintDebug :app:assembleDebug --rerun-tasks` | PASS in 159 s: core:logic 588 tests, core:data 224, core:location 75, 0 failures, 0 skipped; lint «No issues found»; `app-debug.apk` built |

## Not done

- RoadGapFiller (a road instead of the straight fill) needs a routing service; fills stay `pending`.
- The fog ingest at the finish is the `RevealIngest` hook, a no-op until `core:map` provides it.
- Checkpoints during recording, their place registration and the pending place history.
- The GPS watchdog, the lifecycle marks of the raw journal other than «launch», the scheduled
  auto-stop fallback notification. See `docs/divergences.md` for these and the other differences.
- Translations of the Android-only strings beyond English and Russian.
- The stereo picker does not link a stereo to a car yet (the iOS garage does it).

## Check on a real phone

1. A drive with the screen off for 30 minutes or more: track continuity, the ongoing notification,
   battery use, on a Samsung and a Xiaomi with default battery settings and then after the health
   screen's steps.
2. Kill the app mid-drive (swipe away, and `adb shell am kill`): the service restarts and the trip
   continues as one; after 20 minutes it asks instead.
3. Pause and resume from the notification on the lock screen; Finish from the notification.
4. Auto-start in «Auto» and «Remind» from the car stereo's Bluetooth with the app closed, with and
   without «Allow all the time»; the refusal notification when the system blocks the start.
5. Activity Recognition on a real drive: how late IN_VEHICLE arrives and whether the backdated
   start is right.
6. Android Auto: whether the `CarConnection` provider answers with the app in the background.
7. A tunnel and an underground car park: how many coarse fixes the GPS provider gives compared with
   the iPhone on the same road, and how the straight fill looks.
8. «Mine» and «Delete» on the draft question after the app was removed from recents.
9. Reboot during a recording, and an app update: the auto-start registrations come back.
10. The background location settings page on Android 11+, and the precise/approximate choice on 12+.
