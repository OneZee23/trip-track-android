# w3-signin

Google sign-in, the account and Cloud Sync screens, the first-run onboarding, and the sync engine
wired into the app. Branch `w3-signin`, 16 commits, not pushed.

## What was ported

| Android (`app/.../`) | iOS |
|---|---|
| `feature/account/GoogleCredentials.kt`: `GoogleCredentialSource`, `CredentialManagerGoogleSource`, `GoogleSignInFlow` | `Views/Auth/AppleSignInButton.swift` (`AppleAuthorizationRunner`, `startAuthorization`), replaced by Credential Manager |
| `feature/account/AuthService.kt`: session state, sign-in, name resolution, profile name backfill, `/auth/me`, public profile switch, the three hard sign-outs, soft expiry and recovery, `SessionEvents` | `Services/AuthService.swift` (`handleAuthorization`, `prepareForIdentity`, `signOut`, `clearLocalIdentity`, `deleteAccount`, `sessionExpired`, `sessionRecovered`, `reviveSessionIfPossible`, `refreshMe`, `setPublicProfile`, `pushDisplayNameToServer`, `updateUserName`) |
| `feature/account/AccountScopedState.kt`: `AccountSettings`, the account purge, demotion of never-uploaded public trips and journeys | `AuthService.purgeAccountScopedState`, `demotePendingPublicTripsToPrivate`, `demotePendingPublicJourneysToPrivate` |
| `feature/account/AccountServerActions.kt` | `AuthService.publishedTripCount`, `unpublishAllPublicTrips`, `unpublishAllPublicJourneys`, `wipeServerData`, `forgetServerDiscoveries`, `wipeServerVehicles` |
| `feature/account/AccountFormat.kt`: `AccountFormat`, `DisplayNames`, `RandomDisplayName`, `ProfileSyncLatch` | `AccountSyncComponents.swift` (`AccountFormat`), `ContentFilter.sanitize`/`clampedDisplayName`, `Utils/RandomDisplayName.swift`, `Services/ProfileSyncLatch.swift` |
| `feature/account/SyncState.kt`: `SyncState`, `RelativeTripDate` | `SyncStatusMarker.swift` (`SyncState`), `RelativeTripDate.swift` |
| `feature/account/SyncHost.kt`: one `SyncEngine` per account, the app's `SyncRequestSink`, `AccountSyncWorkerFactory` | service wiring in `TripTrackApp`, `AuthService.performFirstSync` |
| `feature/account/AccountViewModel.kt`, `SyncCounts.kt` | `CloudSyncView.swift` and `SyncStatusSheetView.swift` state (`SyncCountSnapshot`, `loadSnapshot`) |
| `feature/account/CloudSyncScreen.kt` (with `SyncStatusMarker`), `SyncStatusSheet.kt`, `BlockedListSheet.kt` | `CloudSyncView.swift`, `SyncStatusSheetView.swift`, `SyncStatusMarker.swift`, `BlockedListView.swift` |
| `feature/account/SignInViews.kt`: `GoogleSignInButton`, `SignInPromptSheet`, `SignInIdleRing`, `GuestSignInCard`, `LegalLinks` | `AppleSignInButton.swift`, `SignInPromptSheet.swift`, `ProfileView.guestSignInCard`, `AppConfig.termsURL/privacyPolicyURL` |
| `feature/account/AccountComponents.kt`: `AccountSettingsRow`, `AccountSectionLabel`, `HouseSwitch`, `Spinner`, the row glyphs | `AccountSyncComponents.swift`, SwiftUI `Toggle`/`ProgressView`, SF Symbols of these screens |
| `feature/account/AccountEntry.kt` | the guest card on «Я» and the «Аккаунт и синхронизация» row of `ProfileSettingsSheet` |
| `feature/onboarding/OnboardingPermissions.kt`: `OnboardingStore`, `OnboardingPermissionCoordinator` | `@AppStorage("hasCompletedOnboarding")`, `OnboardingPermissionCoordinator.swift`, `OnboardingLocationRequest.swift` |
| `feature/onboarding/OnboardingScreen.kt`, `OnboardingRoute.kt` | `Views/Onboarding/OnboardingView.swift` (five pages, demo card, consent line, pagination) |

Assets copied from the iPhone asset catalogue as nodpi pixel art: `pixel_car`, `onboard_road`
(`loading_road`), `onboard_memory`, `onboard_fog`, `onboard_track`, `onboard_notify`. The Google «G»
is a vector drawable.

### Sign-in

- Credential Manager 1.6.0 with `googleid` 1.2.1 (`credentials-play-services-auth` for the Play
  services provider), downloaded once online. The button asks `GetSignInWithGoogleOption`; the «sign
  in again» card asks `GetGoogleIdOption` limited to authorized accounts with auto-select, then every
  account, then the button's picker. One raw nonce per attempt: Google gets its SHA-256 hex, the
  server the raw value (`GoogleNonce`, `GoogleLoginRequest`).
- The Web client ID is `BuildConfig.GOOGLE_WEB_CLIENT_ID`, read from a Gradle property, the
  `GOOGLE_WEB_CLIENT_ID` environment variable or `local.properties`; a malformed value fails the
  build, a missing one shows a developer note on the sign-in button in debug builds
  (`android_sign_in_not_configured`) and the ordinary failure in release builds. Nothing is
  committed. `BuildConfig.API_BASE_URL` takes `TRIPTRACK_API_BASE_URL` in debug builds only.
- Sessions follow «Сессия не умирает сама (0.7.0)»: the three hard sign-outs (button, delete
  account, `USER_BANNED`) share one cleanup; soft expiry keeps library, queue, consent, name and
  refresh token and shows the «sign in again» card; launch and foreground revive it silently, and a
  successful refresh also orders a sync pass. Nothing about the previous identity is touched before
  the login answers with success; a different account after a soft expiry purges the old account's
  state (queue first, then consent, globe opt-in, name latch, companions cache, never-uploaded
  public trips and journeys); a guest's first sign-in purges nothing. Hard sign-outs also clear the
  Credential Manager state.

### Sync wiring

- `AppGraph.syncHost` is the app's `SyncRequestSink` (was `SyncRequestSink.None`): the trip and
  vehicle repositories and the recording graph write through it into the signed-in account's
  `SyncEngine` and its gates. Requests made while the session is read wait for the engine.
- The engine is created for the account the session holds (launch, sign-in, unlock) and closed at
  a hard sign-out, which also cancels the unique WorkManager work. `TripTrackApplication` is the
  WorkManager `Configuration.Provider`; the app manifest removes the default initializer, and the
  worker resolves the CURRENT account's coordinator (a guest's pass succeeds at once).
- Triggers: `MainActivity.onStart/onStop` → `syncHost.onForeground/onBackground` (immediate pass,
  300-second timer, persistent wake-up) and `auth.onForeground` (re-read a deferred session,
  revive a soft expiry); local edits through the enqueuer's own `onLocalChange`; network return
  in `SyncScheduler`; `ACTION_USER_UNLOCKED` re-reads the session.
- Cloud Sync switch: «Account & Sync» → `SettingsManager.setCloudSync` → `SyncSettingsEffects`,
  now following the current account's engine (`engine: () -> SyncEngine?`). First enable asks the
  GDPR consent once per account; off empties the queue.
- `SettingsManager` now lives in `AppGraph` (Main dispatcher, Room world changes). The earlier
  `CloudSyncViewModel` held a single fixed queue and could not follow per-account engines; it is
  replaced by `AccountViewModel`, and its test moved with it.
- Photo files moved to `noBackupFilesDir` in `AppGraph`, the directory the sync transport reads.
- `ApiProfilePrivacyService` decoded profile-update answers as `JsonObject`; the server answers
  `{"status":"ok"}` without a payload, which only `EmptyResponse` decodes. Fixed.

### Onboarding

Five pages as on iOS, explicit navigation, pagination dots, the drawn example card through
`Measure`, the consent line with both links on the three permission pages. Questions go one at a
time into the recording's own `PermissionLedger`, so the record screen never repeats them: precise
and approximate location together, then «all the time» (Android 11+ opens its settings page; the
page says so) and physical activity, then notifications on Android 13+. Sign-in is not an
onboarding page because iOS has none: it asks only where an account is needed (the card on «Я», the
prompt sheet for guest actions). `MainActivity` shows the onboarding until
`hasCompletedOnboarding`; finishing opens Лента and re-arms auto-record.

### For the next agents

- Guest actions that need an account: `SignInPromptSheet(SignInAction.publish|react|…, graph.auth,
  onDismissRequest, onAuthenticated)`.
- View models that gate on an account: `graph.auth.state.value.signedInAccountId` (null while
  signed out or soft-expired). `JourneyManager(repository, graph.sync, signedIn = { graph.auth.state.value.isSignedIn }, cloudSyncEnabled = { graph.accountSettings.cloudSyncEnabled })`.
- Account-owned caches (feed, inbox, companions, profile journal and photos):
  `graph.accountState.register { … }` runs at every hard sign-out and account switch.
- Profile data from `/auth/me` (progress, journal, cosmetics): `graph.auth.addMeListener { … }`.
  The full profile push belongs with «Я»; `AuthService.backfillNameIfNeeded` covers the name
  (also the engine's per-pass `syncAccountProfile`), `updateUserName` the rename.
- Tests that open `MainActivity` must start past onboarding: chain `OnboardingDone()` outside the
  compose rule (as `AppShellTests` and `ShellScreenshotTests` now do).

## Tests

Commands from the checkout, JDK 17
(`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`):

| Command | Result |
|---|---|
| `./gradlew --no-daemon :app:compileDebugKotlin` (online once, new dependencies) | BUILD SUCCESSFUL |
| `./gradlew --no-daemon :app:testDebugUnitTest --tests '*AppShellTests*'` (online once, test runtime) | 5 failed before `OnboardingDone`: the shell now opens on the onboarding |
| `./gradlew --no-daemon --offline :app:testDebugUnitTest --tests '*AccountScreenshotTests*' --tests '*ShellScreenshotTests*' -Proborazzi.test.record=true` | 30 + 4 screenshots recorded (light and dark) |
| `./gradlew --no-daemon --offline :app:testDebugUnitTest :core:network:testDebugUnitTest` | PASS: app 238, network 81; 0 failures, 0 skipped |
| `./gradlew --no-daemon --offline :app:compileReleaseKotlin` | BUILD SUCCESSFUL |
| `./gradlew --no-daemon --offline :app:assembleDebug` | BUILD SUCCESSFUL; the merged manifest has no `WorkManagerInitializer` |
| `./gradlew --no-daemon --offline :app:lintDebug` | 7 errors, all `LocalContextGetResourceValueCall` in `recording/RecordingScreens.kt` (from w2-recording, untouched); none in this work |

New tests (115 in app, 1 in network):

| Class | What it holds | iOS |
|---|---|---|
| `AuthServiceTests` (28) | new account (raw nonce to the server, its SHA-256 to Google, Google name, push of the name, engine then first sync, globe opt-in read back), returning account keeps its server name, generated name, closed sheet, network error and HTTP 503 and `INVALID_GOOGLE_TOKEN` keep the previous session, no client ID asks nothing, returning flow fallbacks and one nonce per attempt, a different account purges (queue before the new engine), the same account keeps its state, a guest purges nothing, soft expiry keeps everything, silent recovery orders a pass, locked store at launch, sign-out (logout with the token, everything cleared, demotion, companions cache), sign-out after expiry skips logout, «Hide public and sign out» order, delete account (server first, then the wipe; refusal keeps all), ban, `/auth/me`, the public switch payload, name backfill at launch | `AuthSessionExpiryTests`, `CompanionsCacheSignOutTests` |
| `SyncHostTests` (9) | requests before the account reach its engine, a guest has none, one engine per account and sign-out closes it, foreground orders an immediate pass, a local change wakes the worker, the privacy gate still decides, published trips and journeys reach the engine through the gates and hiding queues `unpublish`, first sync mirrors only with Cloud Sync on, the guest worker pass | `SyncQueueTests` wiring, `DraftSyncGateTests` gate shape |
| `AccountViewModelTests` (6) | first enable asks consent once and off clears the queue (moved from `SettingsManagerTests`), the sign-out question by published count, sign-out closes, refused deletion stays, partial wipe reported and consent kept, blocked count | `CloudSyncView` decisions |
| `OnboardingTests` (10) | refusal advances once, background then activity in order, double tap, nothing left to ask, late answers, never asked twice, precise with approximate, the store key | `OnboardingPermissionTests` |
| `AccountHelpersTests` (32) | masked email, generated names, the latch, display-name clamp, sync state | `AccountFormatTests`, `RandomDisplayNameTests`, `ProfileSyncLatchTests` |
| `AccountScreenshotTests` (15 × 2 themes) | account screen, its error state, sync status, sign-in sheet (idle, error, loading), guest and expired cards, blocked list and its error, five onboarding pages | |
| `TokenStoreTests` (+1) | the user name survives a soft expiry and leaves with the session | |

`ShellScreenshotTests` Я images were re-recorded: the placeholder now has the sign-in card under the
app settings.

## Not done, and why

- Real Google sign-in is not verified on a phone: there is no Web client ID yet and the backend
  branch `work/identity` is not deployed. `docs/sign-in.md` lists what the owner sets up.
- FCM registration and the inbox refresh after sign-in: the backend has no FCM transport.
- Name and bio editing do not live in «Account & Sync» on iOS (name in «Мой профиль», bio in the
  journal), so they stay with the «Я» work; `AuthService.updateUserName` is ready for it.
- The full profile push (level and XP merge, streaks, cosmetics, bio and pins) belongs to «Я»;
  only the name backfill and its latch are here.
- The real-trip variant of the onboarding example card (needs the share poster projection).
- WorkManager does not run work under Robolectric without `work-testing`, which is not in the
  catalogue: the worker's decision is tested as a function, scheduling through unique-work state.

Found while working, outside this task:

- `recording/RecordingScreens.kt` asks `ACCESS_FINE_LOCATION` alone; from Android 12 the system
  ignores that request without `ACCESS_COARSE_LOCATION` (the onboarding asks both). It also has the
  7 lint errors above.
- `SettingsManager` keeps the car stereos under the default preferences while `LocationGraph`
  keeps its own list (`triptrack.autorecord`); `AppGraph` bridges SettingsManager → recording
  through `RecordingSettings`, but the settings work has to make one of them the owner.
- The onboarding background text is the iOS copy and names «Движение и фитнес»; Android calls it
  physical activity. It needs Android-specific copy.
- The privacy policy and terms links are the iPhone documents; Play needs a policy that covers
  Google sign-in.

## Differences from the iPhone app

All in `docs/divergences.md`: the two Credential Manager requests, name ranking at sign-in, no
credential-state probe, email held in memory, recovery ordering a sync pass, one engine per account,
photo files in `noBackupFilesDir`, Credential Manager state cleared at sign-out, the Android
permission sequence, the drawn example card, the account screens as house sheets with text-only
states in the blocked list, the Google button, and the account entry on the «Я» placeholder.

The emulator was not started; no production request was made. `git grep` for addresses and keys and
a scan for client IDs found nothing.
