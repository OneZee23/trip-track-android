# Permissions and Play declarations

Release manifest checked on 31 October 2026, target SDK 36, min SDK 29:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew --no-daemon --offline :app:processReleaseMainManifest
```

Read `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`
and `app/build/intermediates/manifest_merge_blame_file/release/processReleaseMainManifest/manifest-merger-blame-release-report.txt`.
Build passed. Rebuild after the other branches are merged; SDK/library changes can add permissions.
The complete `uses-permission` set contains 17 entries:

| Permission | Source and visible reason | Runtime / Play declaration |
|---|---|---|
| `android.permission.ACCESS_FINE_LOCATION` | Recording and MapLibre: record route, speed/distance, show location on a map | Runtime. Precise and coarse are requested together. Covered by location declaration/Data safety |
| `android.permission.ACCESS_COARSE_LOCATION` | Recording and MapLibre: system offers approximate location; it is insufficient for an accurate driving track | Runtime; same request. Approximate-only users get a settings explanation, no repeated prompt |
| `android.permission.ACCESS_BACKGROUND_LOCATION` | Recording: automatically detect/start a trip with TripTrack out of view and continue its route | Runtime on Android 10; settings route on Android 11+. Background location declaration and video required |
| `android.permission.FOREGROUND_SERVICE` | Recording: keep a user-visible recording service alive | Normal; covered by the type-specific FGS declaration |
| `android.permission.FOREGROUND_SERVICE_LOCATION` | `RecordingService`, `foregroundServiceType="location"`: ongoing trip recording with notification | Normal; location FGS declaration and demonstration required for target SDK 34+ |
| `android.permission.POST_NOTIFICATIONS` | Recording progress/actions, auto-record reminders and saved-draft notices | Runtime on Android 13+; no separate restricted-permission declaration |
| `android.permission.ACCESS_NETWORK_STATE` | Recording, sync and MapLibre: know when naming, sync and maps have a connection | Normal; no separate declaration |
| `android.permission.ACTIVITY_RECOGNITION` | Recording: recognise vehicle movement for opt-in auto-record; no step/fitness upload | Runtime; no background-location-style declaration of its own |
| `android.permission.BLUETOOTH` (`maxSdkVersion=30`) | Recording: detect the linked stereo on Android 10/11 | Normal, legacy; no separate declaration |
| `android.permission.BLUETOOTH_CONNECT` | Recording: read paired stereo names and receive connection changes on Android 12+ | Runtime Nearby devices; requested when configuring a stereo; no separate Play declaration |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Recording and WorkManager: restore enabled auto-record monitoring and scheduled work after reboot/update | Normal; no separate declaration. Does not mean silently recording immediately at boot |
| `android.permission.INTERNET` | Network, sync and MapLibre: map tiles, sign-in, chosen cloud operations | Normal; no separate declaration; disclose transmitted data |
| `android.permission.WAKE_LOCK` | WorkManager 2.12.0: finish scheduled sync work without the CPU sleeping mid-operation | Normal; no separate declaration |
| `android.permission.USE_BIOMETRIC` | Transitive AndroidX Biometric 1.1.0 through credential libraries; TripTrack itself presents no biometric enrolment/unlock feature | Normal; no separate declaration. No biometric templates are exposed to TripTrack |
| `android.permission.USE_FINGERPRINT` | Same Biometric library, legacy compatibility | Normal; no separate declaration; no app-authored fingerprint collection |
| `android.permission.ACCESS_WIFI_STATE` | MapLibre Native OpenGL 13.7.1 | Normal network-state access for the map library; no Wi-Fi scan UI or nearby access-point upload in app code; no separate declaration |
| `com.onezee.triptrack.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX Core 1.18.0: protect internal dynamic broadcasts | App-defined signature permission, not a user request; no declaration |

MapLibre itself declares `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
`ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` and an optional Wi-Fi hardware feature.
The final manifest coalesces duplicates. Optional GPS/Bluetooth/Wi-Fi hardware features and the
`androidx.car.app.connection` provider query are not permissions. Service/receiver guards such as
`BIND_JOB_SERVICE`, `DUMP` and the Google sign-in revocation permission restrict callers; TripTrack
does not request them as `uses-permission` entries.

There is no camera, microphone, broad photo/storage access, Bluetooth scan, contacts, advertising
ID, exact-alarm, full-screen-intent, battery-exemption request or billing permission. Imported
photos use a system content picker. There is no photo/video permission declaration to submit.
WorkManager's merged `SystemForegroundService` has no location type; app recording uses its own
`RecordingService`. Do not declare dataSync, health or connectedDevice FGS types for this build.

## Prominent disclosure audit

Google's [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en-GB)
requires a clear disclosure within the app before sensitive access and affirmative consent before
the runtime request. The [background-location guidance](https://support.google.com/googleplay/android-developer/answer/9799150)
adds a specific description of background location and its feature.

| Requirement | Shipped path and result |
|---|---|
| In the app's normal flow | Onboarding page 3 of 5 explains location; page 4 explains background recording. The recording Permissions screen has its own explanations |
| Before the system request/settings | `OnboardingPermissionCoordinator.proceed` and recording `PermissionFlow.begin` run only after Continue. Android 11+ opens settings after the background explanation; Android 10 uses a native runtime prompt |
| App/data named | Updated copy names TripTrack and precise location |
| Collection while closed/not in use | Updated copy explicitly says both “closed” and “not in use” in every language |
| Concrete purpose | Automatic start and route recording; physical activity has its own driving-detection explanation |
| Affirmative action / denial | Continue starts the relevant system step; denial advances onboarding and is remembered in the permission ledger |
| Visible, readable text | Normal screen body, scrollable above a pinned Continue. No legal-link-only disclosure; the permission page also links policy/terms |

Before this task, onboarding said “Location access … helps detect drives when the app isn't
open”; recording reused the iPhone phrase “minimized or screen locked”. Neither consistently
spelled out collection, precise data, automatic start and closed/not-in-use access together.
Android-only overrides now replace `onboardingBackgroundSub` and
`permission_NSLocationAlwaysAndWhenInUseUsageDescription` in `tools/strings/android.json` for all
13 languages. Both flows use those existing keys; no Kotlin permission sequencing changed.

English background disclosure:

> TripTrack collects precise location to start and record trips automatically, even when the app is closed or not in use. Trips are uploaded only if you publish them or enable Cloud Sync.

Onboarding additionally says physical activity helps detect driving and each request may be
allowed or denied. Android 11+ appends instructions for “Allow all the time”. Location denial
prevents automatic route recording; the rest of the diary remains accessible. This audit does
not certify a Play approval or demonstrate OEM background behaviour on a real phone.

## Background location declaration

Select one core feature: automatic road-trip recording. Paste the following in the matching
purpose/feature fields; adjust field placement to the Console form, not the claim.

Main app purpose:

> TripTrack is a personal road-trip diary. It records a drive's route, distance, duration and speed so the user can revisit the drive on a map with photos and notes.

Feature requiring background location:

> Automatic trip recording. After the user enables auto-record and grants location access, TripTrack can start a driving trip while the app is closed or not in use, using vehicle-motion or a linked car stereo connection as the trigger. It records precise location along that drive in a location foreground service with an ongoing notification and Pause, Resume and Finish actions. Foreground-only location cannot start a route when the user gets into the car without opening TripTrack, and would miss the beginning of the drive. Recording is stored on the phone; private trips upload only after the user enables Cloud Sync. Publishing a trip is a separate choice. Location is not used for advertising.

Do not list Atlas, geocoding and photo placement as additional background features. They do not
justify this permission. The current activity/stereo behaviour must be verified on the final
merged release, including the 0.9.3 trigger-mode settings.

## Location foreground service declaration

For `FOREGROUND_SERVICE_LOCATION`, select “Background Location Updates: User-initiated location
sharing”, whose documented examples include vehicle activity tracking. Explain the local diary
use below. Do not select geofencing or claim turn-by-turn navigation.

Description:

> The location foreground service records a road trip requested with Start or through the user's enabled automatic-recording setting. Recording must continue while the phone is locked or another app is visible so the saved route and distance cover the whole drive. An ongoing notification shows recording state, distance and speed/time and provides Pause, Resume and Finish. Finishing ends the service. Delaying or interrupting this work loses route points and leaves an incomplete trip; it cannot be deferred to a later background job.

User benefit: a complete trip history without keeping the app on screen. Work is user-perceptible
and stoppable. For automatic starts the user has explicitly enabled that mode beforehand; it is
not described as a fresh visible tap for every service start. See Google's
[FGS declaration guidance](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en).

## Declaration video script

Capture the final Play-signed release on a real phone, in English, with synthetic trip data and
accounts hidden. A passenger/camera operator records; the driver does not interact with the
phone while driving. Use readable screen capture plus a short physical-phone view for locking
and the stereo trigger. No simulator/mock-location UI is evidence of a real background start.
Upload an accessible unlisted video and paste its URL in each declaration. Do not put private
accounts or the reviewer video in this public repository. The background-location guide recommends
a concise demonstration around 30 seconds; keep disclosure and permission text readable.

Background-location clip, approximately 30 seconds (use an additional longer uncut capture if
readability needs it):

| Shot | Approximate duration | What the reviewer sees |
|---|---|---|
| 1 | 0–5 s | Onboarding background page with the full prominent disclosure. Scroll if needed and hold the precise-location, closed/not-in-use and purpose sentences. Tap Continue |
| 2 | 5–9 s | Native app Location settings: choose Allow all the time with precise location enabled, return; show the physical-activity runtime request and grant it |
| 3 | 9–14 s | Finish onboarding/notification request. Open Me → Settings → Auto-record, enable it, link a paired stereo, and choose the supported trigger mode. Do not jump past a permission request without showing it |
| 4 | 14–20 s | Return Home so TripTrack is out of view; connect the linked stereo. Show the ongoing recording notification appearing from that trigger. If vehicle motion needs time, retain a labelled elapsed-time cut and an uncut reference |
| 5 | 20–25 s | Phone locked/other app visible while the passenger shows distance/time increasing in the ongoing notification |
| 6 | 25–30 s | Return to TripTrack, finish, open the saved route and show that it includes the background interval |

FGS clip, approximately 30 seconds:

| Shot | What to show |
|---|---|
| 1 | The in-app Start action begins a recording; location disclosure/permission precedes it if not already granted |
| 2 | Minimise TripTrack; expand the ongoing notification, hold its live distance/speed/clock and Pause/Finish actions in view |
| 3 | Pause from the notification, show paused state, Resume, then show the recording continuing with the phone locked |
| 4 | Finish from the notification, return to the saved trip, and show that the ongoing notification is gone |

Also record refusal of location, approximate-only permission, revoked background access, process
recovery, screen lock and stereo disconnect as test evidence. These checks support the release;
they need not all be in the declaration clip. Google checks consistency between video, actual
binary, prominent disclosure, policy, listing and the single declared feature.
