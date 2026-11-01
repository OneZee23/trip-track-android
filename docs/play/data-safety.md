# Google Play Data safety

Use the no-Sentry answers for this checkout. There is no Sentry dependency or initialization.
Recheck the final merged release and all active Play tracks before submitting: the form covers
every distributed version, not just the new upload. Optional collection still counts.

## Form overview

| Question | Answer for this Android build |
|---|---|
| Does the app collect or share any required user data types? | Yes |
| Is all collected user data encrypted in transit? | Yes. API, image downloads and map styles/tiles use HTTPS |
| Can users create an account? | Yes, through Google sign-in; select OAuth |
| Other account creation methods | None: no email/password, phone login or platform account linking |
| Can users request account deletion? | Yes, inside the app; publish the external request page before entering its URL |
| Can users request deletion of some data without deleting their account? | Yes: delete trips/photos/vehicles; “Clear server data” keeps the account; turn off optional usage counters to request their erasure |
| Independent security review / certification | No certification is established by this repository; do not claim one |
| Advertising or sale of data | None |

The external deletion page must name TripTrack, explain identity verification and deletion,
provide a working request mechanism, and state retention exceptions. Its URL is an owner
publication step, not an existing endpoint. See [console setup](console-setup.md).

## What leaves the phone

| Trigger | Data and destination | Cloud Sync dependency |
|---|---|---|
| Load a map, including a map in a trip | OpenFreeMap receives requested tile coordinates/zoom, network address and HTTP/device metadata. Tiles can reveal the viewed area, including a recorded route or home area | None; guest mode also loads maps |
| Generate a trip's place name | Android `Geocoder` receives coordinates through the phone's geocoding service. The implementation/provider depends on the device | None. This is a system-service exchange, not a TripTrack API upload |
| Choose Google sign-in | Credential Manager returns an ID token, display name and email. `/auth/google/login` receives the raw ID token, raw nonce, installation UUID, optional device name, Android platform and app version. Server verification obtains the Google subject and account email | None; choosing sign-in is optional |
| Signed-in profile setup or editing | Display name, avatar, language, bio, pinned public-trip IDs, selected vehicle ID, visibility choices, earned level/XP/streaks through `/profile/update`; `/auth/me` reads account data | Independent of Cloud Sync |
| Add a profile photo | JPEG thumbnail/original and optional capture time through `/profile/photos/upload`; delete/order requests | Independent of Cloud Sync; account required |
| Enable Cloud Sync and confirm | Private confirmed trips, route points, times, speed/altitude/accuracy, previews, titles/notes, photo metadata/files, checkpoints/segments, journeys, vehicles and settings through the sync queue | Off by default; sign-in and explicit enable required |
| Publish a trip or journey | Public trip/leg payloads and attached photos; public journey metadata. These go through the same queue even with Cloud Sync off | Publishing is a separate voluntary upload. UI is being completed in w4-social; this gate already exists |
| Turn on “Help improve TripTrack” | `/usage/snapshot`: consent-period random UUID, UTC consent/delivery/milestone days, existing-library flag and bounded counters for starts/saves/failures/Atlas opens/save-speed buckets. `/usage/erase` sends its deletion token | Off by default, independent of sign-in and Cloud Sync; release networking only |
| Export GPX/CSV or support logs | Files are handed to the app the user chooses through the system share sheet | User initiated; no automatic developer upload |
| Any TripTrack API request | Infrastructure sees the connecting network address; published policy retains server access logs up to 14 days | A network request is sufficient, including guest requests |

Evidence: `core/network` (`ApiClient`, `AuthDTOs`, `SyncDTOs`, `UsageDTOs`, `PhotoUploadRequest`),
`core/sync` (`SyncEnqueuer`, `APISyncTransport`, `TripSyncPayloadMapper`, `PhotoEncoder`),
`app/feature/account` (`GoogleCredentials`, `AuthService`, `AccountViewModel`),
`app/feature/me` (`ProfileIdentityStore`, `ProfilePhotoStore`, `MeFeatures`),
`app/feature/settings/ProductAnalytics.kt`, `core/location/.../TripNamer.kt`,
`core/map/.../MapHostState.kt`, `ui/TripMaps.kt`, and [API contract](../api-contract.md).
The usage pipeline can send consent metadata and zero counters now; recording/Atlas event
producers are still listed as unfinished in `docs/divergences.md`. Do not claim it is absent.

## Data-type answers without Sentry

“Required” below means an online path sends it without a separate collection switch; it does
not mean the offline diary requires an account. Choose non-ephemeral for persisted content,
identifiers, counters and logs. Do not claim ephemeral handling for third-party map requests:
provider error/security logs may persist them.

| Play category / data type | Collected | Shared | Optional or required | Purpose(s) |
|---|---|---|---|---|
| Location / Precise location | Yes | Yes, conservatively for map-area requests at close zoom | Required for online map requests; route uploads are separately optional | App functionality |
| Location / Approximate location | Yes | Yes, for map-area requests | Required for online maps; synced region names are optional | App functionality |
| Personal info / Name | Yes | No under the user-directed publication exception | Optional, sign-in/profile | Account management; App functionality |
| Personal info / Email address | Yes | No | Optional, sign-in | Account management |
| Personal info / User IDs | Yes | No under user-directed public profile actions | Optional, sign-in | Account management; App functionality; Fraud prevention, security and compliance |
| Photos and videos / Photos | Yes | No under user-directed publication / service-provider exceptions | Optional | App functionality |
| App activity / Other user-generated content | Yes | No under the same exceptions | Optional | App functionality; Account management |
| App activity / App interactions | Yes | No | Optional, usage-counters consent | Analytics |
| App info and performance / Diagnostics | Yes, save-failure and timing buckets | No | Optional, usage-counters consent | Analytics |
| App info and performance / Other app performance data | Yes, save-speed buckets | No | Optional, usage-counters consent | Analytics |
| Device or other IDs | Yes, installation UUID, consent-period token and request identifiers/network address | Yes, conservatively for independent map infrastructure | Required for online request metadata; account/analytics identifiers are optional | App functionality; Account management; Analytics; Fraud prevention, security and compliance |
| App info and performance / Crash logs | No | No | Not applicable | None |

These map-related “Shared: Yes” answers deliberately avoid assuming the public tile operator
is a contracted processor acting only on our instructions. Tile bounds are a conservative
location classification: they are the viewed area and need not be the phone's current location.
If the owner establishes a qualifying processor relationship or changes to a verified provider,
reassess sharing against the actual arrangement. An ordinary map pan is not treated as consent
to publish sensitive location. API hosting and photo storage process data for TripTrack;
intentional public-trip/profile posting and exports are user-directed transfers.

Other user-generated content includes titles, notes, captions, checkpoint names, journeys,
vehicle make/model/year/registration/about/modifications, and bio. A vehicle registration is
user content here, not a payment or government identity credential. Profile social features
merged later may add follows, reactions, comments, search and abuse reports: classify those
from the shipped code before upload (comments: Other in-app messages; searches: In-app search
history; follow relationships: Contacts/social graph; reports: Other user-generated content).
Do not pre-fill “No” for these after w4-social is merged.

Leave financial data, health/fitness, contacts/address-book, audio, videos, files/documents,
calendar, web browsing history and purchase history unselected for this checkout. GPX/CSV
exports do not scan or upload arbitrary documents. Activity Recognition supplies driving
transitions locally; TripTrack does not upload fitness measurements. No advertising ID is read.

The [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
requires declaring off-device processing, including SDKs and optional paths, and permits specific
service-provider/user-directed-sharing exceptions. The
[OpenFreeMap policy](https://openfreemap.org/privacy/) covers its public tile service: ordinary
access logs omit IP addresses, but errors can retain IP/URL for seven days and security incidents
can enable IP logging for up to 30 days. Do not describe guest mode as entirely offline.

## Privacy zone, retention and deletion

The home point/radius is manually chosen and the trimming switch starts off. When enabled,
`TripSyncPayloadMapper` removes outgoing route/preview points strictly inside the radius,
including intermediate visits, and omits both coordinate fields for affected photos/checkpoints.
A point exactly on the radius stays. Fewer than two remaining route points means an empty route
and no preview. Full local geometry and aggregate distance/time remain intact. Raster uploads
are re-encoded without EXIF/GPS; separate photo time/coordinates are still metadata, subject to
the zone. Names, timestamps, statistics and visible image content can still reveal a place.
The zone does not filter the map provider's tile requests or the Android geocoder.

Cloud Sync off stops private-library uploads, not profile uploads, published trips, map traffic
or separately consented analytics. Turning it off does not delete existing server copies.
Signing out also does not delete them; the app offers hiding published trips during sign-out.

In-app path: Me → Settings → Account & Sync → Delete account → confirm. The server request
must succeed before local cleanup. This checkout then wipes the local library as well as account
state (`AuthService.deleteAccount(wipeLocal = true)`, `LocalDataWipe`). “Clear server data” is a
separate action which disables Cloud Sync first. A failed delete keeps the local data and shows
an error. Uninstalling alone sends no account or analytics deletion request.

Published retention terms: active-account data remains while active; after three years of
inactivity deletion is discretionary with prior email notice. Individual deleted trips/photos/
vehicles are physically erased within 30 days; account deletion erases associated live server
data immediately. Encrypted backups rotate within 30 days, server access logs within 14 days,
abuse/moderation records within two years. A timestamp and opaque deletion-audit hash may
remain indefinitely. Analytics opt-out clears local counters, queues server erasure, and inactive
counter records/deletion markers expire after 90 days. These are the published service terms;
client code cannot verify deployed backup/log jobs.

## If Sentry is added before upload

Keep the preceding content/location answers. For an integration matching iPhone
`SentryService.swift` (crashes/hangs, session records, 10% traces, account UUID when signed in,
SDK installation ID and scrubbed device/OS/performance metadata), change these answers:

| Data type | Collected | Shared | Required/optional | Purpose |
|---|---|---|---|---|
| Crash logs | Yes | No if Sentry acts as our service provider | Required, including guest mode | Analytics |
| Diagnostics | Yes | No under that processor exception | Required | Analytics |
| Other app performance data | Yes | No under that processor exception | Required | Analytics |
| App interactions | Yes, session starts/stops | No under that processor exception | Required | Analytics |
| Device or other IDs | Yes, additionally Sentry installation ID | No for the Sentry transfer under that processor exception; retain map-sharing answer | Required | Analytics, plus existing purposes |
| User IDs | Yes, additionally account UUID on signed-in diagnostic events | No under that processor exception | Optional because sign-in is optional | Analytics, plus existing purposes |

All are non-ephemeral. Upload-time selection depends on the actual SDK configuration and a scrubbed
sample event, not merely the presence of the dependency. Check PII/IP collection, identifiers,
URLs/breadcrumbs, attachments, replay, screenshots, profiles and tracing before using these
answers. Any broader integration requires broader answers. Confirm region, processor agreement
and actual retention, and publish the matching diagnostics paragraph in both policy languages.
Sentry's [Android data collection reference](https://docs.sentry.io/platforms/android/data-management/data-collected/)
is the SDK checklist, not evidence that this Android app already sends diagnostics.

## Differences from iPhone privacy labels

Source: iPhone `docs/store/app-store-privacy-labels.md`, checked with the current code and
published English/Russian policy. Location, name/email, account/installation identifiers,
photos and other content have the same functional purposes. Google identity replaces Apple
identity, and credentials use Android Keystore instead of Keychain. Android maps use MapLibre
and OpenFreeMap; place naming uses Android Geocoder.

The iPhone labels document is stale: it says no crash/performance/product-interaction collection,
but the published policy describes Sentry and optional counters. Android currently has counters
but no Sentry. Follow this code-derived matrix, not those unchecked boxes. The published policy
also says local trips survive account deletion; both current iPhone `AuthService.swift` and
Android wipe them by default. The Android policy draft corrects that statement. Runtime activity,
Bluetooth and notification permissions are Android-specific; they do not by themselves imply
uploaded health data, nearby-device names or message content.
