# Sign-in with Google

Android signs in with Google only; the iPhone keeps Sign in with Apple only. They are separate
TripTrack accounts: no email login, no linking, no sync between an iPhone and an Android phone
(owner, 30 October 2026; `docs/divergences.md`). After the login the session is the iPhone's:
`x-access-token`, `/auth/refresh`, `/auth/logout`, `/auth/delete-account`, the same soft expiry.

## How it works

1. The app makes a random raw nonce (`GoogleNonce`) for each attempt.
2. Credential Manager is asked for a Google ID token with the **SHA-256 hex** of that nonce:
   - the «Sign in with Google» button uses `GetSignInWithGoogleOption` (every Google account on the
     phone, plus «add account»);
   - the «sign in again» card after a soft expiry uses `GetGoogleIdOption`, first limited to accounts
     that already signed in to TripTrack with auto-select, then every account, then the button's
     picker.
3. The raw ID token and the **raw** nonce go to `POST /auth/google/login` (`GoogleLoginRequest`,
   with `localUserId`, `deviceName` = the phone model, `platform` = `android`, `appVersion`). The
   server checks the signature, issuer, audience (`GOOGLE_CLIENT_IDS`), expiry, nonce and a
   verified email, and answers the same `LoginResponse` as the Apple login.

Code: `app/.../feature/account/` (`GoogleCredentials.kt`, `AuthService.kt`), the call itself in
`core:network` (`ApiClient.loginGoogle`). The serverClientId is the **Web** client ID, read at build
time into `BuildConfig.GOOGLE_WEB_CLIENT_ID`.

## What the owner has to do before it works on a phone

### 1. Google Auth Platform (Google Cloud Console)

1. A Google Cloud project for TripTrack → Google Auth Platform → Get started: app name, support
   email, audience **External**, contact email.
2. Branding: logo, home page, the privacy policy link. Google shows the app name on its sign-in
   sheet only after brand verification. The privacy policy has to cover Google sign-in (the
   account's email and name are stored); the current documents were written for the iPhone app.
3. Audience: **Publish app** (In production). Data access: nothing to add; `openid`, `email` and
   `profile` need no verification and no test-user list.
4. Clients → Create client → **Web application** («TripTrack server»). No redirect URIs or
   origins. Its Client ID is both:
   - `GOOGLE_WEB_CLIENT_ID` of the Android build (step 3 below), the Credential Manager
     serverClientId;
   - `GOOGLE_CLIENT_IDS` of the backend (step 2 below).
   The client secret is not needed anywhere; do not store it.
5. Clients → Create client → **Android**, one client per package and SHA-1:
   - `com.onezee.triptrack` with the SHA-1 of the **app signing key** from Play Console (Test and
     release → App integrity → App signing), once Play App Signing is on;
   - `com.onezee.triptrack` with the SHA-1 of the **upload key**, for release builds installed
     outside Play;
   - `com.onezee.triptrack.dev` with the SHA-1 of the **debug key** of every machine that builds
     debug APKs: `./gradlew :app:signingReport`, or
     `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`.

   Neither the app nor the server needs the Android client IDs: Google recognises the app by its
   package and signature. A missing or wrong pair is the usual reason the sheet says there are no
   accounts, or Credential Manager fails with a developer-configuration error.

### 2. Backend

Deploy the branch `work/identity` of the backend with
`GOOGLE_CLIENT_IDS=<web client id>.apps.googleusercontent.com` (several values comma-separated).
The order (schema SQL first, then the image), the nginx rate-limit line, the checks on production and
the rollback are in the backend's `docs/google-login.md` §6. Until then `POST /auth/google/login`
does not exist on production and every attempt ends in «Couldn't sign in with Google».

Check that the server is configured (this spends one of the five login attempts per hour per IP):

```bash
curl -s https://api.trip-track.app/auth/google/login \
  -H 'Content-Type: application/json' \
  -d '{"identityToken":"x","localUserId":"00000000-0000-4000-8000-000000000000"}'
```

`INVALID_GOOGLE_TOKEN` means `GOOGLE_CLIENT_IDS` is set; `GOOGLE_LOGIN_NOT_CONFIGURED` means it
did not reach the container.

### 3. The Android build

The Web client ID is a build input that never goes into git. Gradle reads it, in this order, from a
Gradle property, the environment, then `local.properties` (gitignored):

```properties
# local.properties
GOOGLE_WEB_CLIENT_ID=<web client id>.apps.googleusercontent.com
```

or `GOOGLE_WEB_CLIENT_ID=… ./gradlew :app:assembleDebug`, or `-PGOOGLE_WEB_CLIENT_ID=…` (CI). A
value that is not a Web client ID (`*.apps.googleusercontent.com`) fails the build. Without it the
app builds and runs, and the sign-in button explains in a **debug** build that the ID is missing; a
release build shows the ordinary «Couldn't sign in with Google».

## Testing

### On a phone or an emulator

- The phone needs Google Play services and a Google account. On an emulator use a «Google APIs» or
  «Google Play» system image and add an account in the system settings.
- Debug builds are `com.onezee.triptrack.dev` and need the `.dev` Android client with the SHA-1 of
  the debug key of the machine that built the APK.
- Where to sign in: «Я» → the «Your trips on every device» card. After a sign-in the card becomes
  the «Account & Sync» row with its live sync marker; the screen has the Cloud Sync switch,
  per-item status with «Retry now», the public profile switch, the blocked list, «Sign out» (with
  «Hide public and sign out» when trips are public), «Clear server data» and «Delete account».

### Against a local backend

1. Run the backend on the Mac from the `work/identity` branch: `.env` from `.env.example` with
   `GOOGLE_CLIENT_IDS` set to the same Web client ID (and the local Postgres it describes), then
   `yarn local:start:dev`. The example `.env` listens on port 3003.
2. Forward the phone's or emulator's localhost to the Mac: `adb reverse tcp:3003 tcp:3003`.
3. Build the debug app against it:

   ```properties
   # local.properties
   TRIPTRACK_API_BASE_URL=http://localhost:3003
   ```

   Debug builds allow plain HTTP to `localhost` only (`app/src/debug/res/xml/network_security_config.xml`);
   release builds ignore `TRIPTRACK_API_BASE_URL` and always use `https://api.trip-track.app`.

The token store is scoped by API host, so a session made against the local backend never meets the
production one, and switching back is a sign-in, not a corrupted session.

### Against a staging backend

`TRIPTRACK_API_BASE_URL=https://<staging host>` in `local.properties` (HTTPS; the staging server needs
the same `GOOGLE_CLIENT_IDS`).

### Without Google at all

The sign-in state machine is tested without Google and without a server: `AuthServiceTests` uses a
fake `GoogleCredentialSource` and MockWebServer (new and returning accounts, a closed sheet, network
and token errors, soft expiry and silent recovery, the three hard sign-outs, a different account
after a soft expiry). Run `./gradlew :app:testDebugUnitTest --tests '*AuthServiceTests*'`.

## Troubleshooting

| What you see | Likely cause |
|---|---|
| Debug note «Google sign-in is not set up in this build» | `GOOGLE_WEB_CLIENT_ID` missing from `local.properties` / environment |
| The Google sheet says there is no account, or a developer-configuration error | No Android client for this package + SHA-1 (debug key of this machine for `.dev`) |
| «Couldn't sign in with Google» right after choosing the account | `/auth/google/login` not deployed (HTTP 404), `GOOGLE_LOGIN_NOT_CONFIGURED`, or `INVALID_GOOGLE_TOKEN` because `GOOGLE_CLIENT_IDS` does not contain the Web client ID used by the build |
| Works on one machine's debug build, not another's | Each machine's debug keystore has its own SHA-1 and needs its own `.dev` Android client |
