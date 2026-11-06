# TripTrack API contract for Android

Backend verified against `origin/master` commit `614f3675af41a1758f32a41bad6382fb99468c85` (26 October 2026), read-only checkout `~/OneZeeProjects/trip-track-backend-identity`.

The iPhone client defines the wire contract. Reference revisions: iOS `a155859d`, backend `614f367`, inspected on 30 October 2026. Paths below are relative to `API_BASE_URL`; the public API host named in the iOS source is `https://api.trip-track.app`. No production requests were made. Backend presence here means presence in that local checkout, not confirmation of a deployment.

Sources: iOS `TripTrack/Networking/{APIClient,APIEndpoint,APIEnvelope,APIError}.swift`, `Models/Sync/`, `Models/Social/`, `Services/Sync/`, `Services/AuthService.swift`, `Services/TokenStore.swift`, `Services/KeychainHelper.swift`, `Services/Social/`, `Services/Plus/`, `Services/RemoteConfig/`, `Services/{RemotePhotoStorage,ProfilePhotoStore,PushNotificationManager,ProductAnalytics}.swift`, `Views/Admin/AdminModel.swift`, and the feature decisions in `CLAUDE.md`. Backend references are under `src/modules/` and `src/common/` in `trip-track-backend-identity`; config/admin/usage/profile sources and backend `docs/` are cited in their sections.

## Transport and shared rules

### JSON, auth and dates

- JSON requests use `Content-Type: application/json`. JSON responses use the envelope below, including GET and multipart responses; usage success returns an empty HTTP 204. There is no JSON-RPC `id`, `method` or `params` wrapper.
- Auth means the raw access token in `x-access-token`, without a `Bearer ` prefix. `JWT` below requires it. `Guest` allows no token; when signed in, iOS usually sends a token and enables refresh. A supplied invalid token is rejected, rather than silently treated as a guest. `Public` calls explicitly omit auth.
- All ordinary API success and application errors use HTTP **200**. Other HTTP statuses are transport errors, except throttle detection. The usage endpoints return HTTP 204 on success and use global HTTP exception handling for failures, outside the JSON-RPC route decorators.
- Dates are ISO-8601 strings. Send UTC with milliseconds, e.g. `2026-04-29T08:33:35.049Z`. Accept with/without milliseconds and timezone offsets as well as `Z` (`Utils/ISODate.swift`, `ISODateTests`). Calendar days such as a modification's `date` use `YYYY-MM-DD`, without timezone conversion.
- `uuid` means a UUID string. Generate stable IDs locally; never generate a new ID just because a request failed. Use lowercase for vehicle/profile photo multipart IDs, pin IDs and cursor IDs. Some iOS JSON payloads use uppercase UUID text; decoders must accept both.
- `number` means a finite JSON number; `int` means an integral JSON number. Distances/altitudes/accuracy are metres, speeds m/s, durations seconds, coordinates WGS84 degrees unless a field explicitly says otherwise. Garage odometers and range use kilometres even when the UI uses miles. Fuel uses litres and consumption L/100 km; electric consumption uses kWh/100 km.

Schemas below use `field: type` for a required key and `field?: type` for an optional key. `| null` marks a meaningful explicit null. For response optionals, iOS's ordinary `decodeIfPresent` accepts either absent or null as unknown unless a presence rule is stated. Unknown JSON fields are ignored. Do not apply one global null/empty rule across all DTOs. Current iOS encoders omit nil request optionals unless explicit-null encoding is stated below. Android must do the same: backend @IsOptional often accepts null without normalizing it to absence, and service code can then fail or use a different default (for example null pagination limits). For optional request fields without a documented null operation, omit the key instead of sending null. Empty strings/arrays are only a clear operation where explicitly stated; otherwise they are literal values subject to that endpoint's validation.

### Envelope and errors

```json
{"status":"ok","payload":{"value":42}}
```

```json
{"status":"error","code":"CONFLICT_DETECTED","message":"conflict","serverVersion":5,"serverLastModifiedAt":"2026-04-19T10:30:45.000Z"}
```

These are literal synthetic fixtures from `TripTrackTests/Sync/APIClientEnvelopeTests.swift`; `value` is a test payload, not an application endpoint. `APIEnvelope` has `status: "ok"|"error"`, optional `payload`, `code`, `message`, `serverVersion: int`, `serverLastModifiedAt: date`. A missing/null payload is accepted only when the caller expects `EmptyResponse`; otherwise it is a decoding failure. Void backend handlers commonly serialize as `{"status":"ok"}`. `{}` is the empty request body.

| Code or failure | iPhone reaction, inherited by endpoints below |
|---|---|
| `USER_NOT_AUTH` | For an authenticated request, single-flight refresh, then resend once. If the resend is rejected, ask refresh again to establish session health; return the request error without a third resend. A late expired access token alone does not prove session death. |
| `INVALID_REFRESH_TOKEN` | Soft-expire session, ask for sign-in; retain local library, queue, consent, identity and refresh token. |
| `INVALID_APPLE_TOKEN` | Login fails; retain the previous identity until login succeeds. |
| `USER_BANNED` | Emit ban notification and sign out; the regular ban path clears tokens/account state. |
| `CONFLICT_DETECTED` | Typed conflict with optional version/date. Sync resolution is specified per entity below. |
| `VALIDATION_FAILED` | Typed validation failure. Normal API has no validation retry loop; sync retains the failed operation. |
| `TOO_MANY_REQUESTS`, HTTP 429 | Typed throttle; no immediate APIClient retry. Non-200 Nest bodies with `statusCode:429` or a throttler message also map here. Higher-level queues may schedule a later retry. |
| `TRIP_NOT_FOUND`, `VEHICLE_NOT_FOUND`, `JOURNEY_NOT_FOUND`, `PHOTO_NOT_FOUND` | Typed missing entity. Delete/conflict handling below determines whether it is already-successful deletion or lost access. |
| Other `code` | Preserve code/message as `unknownServer`. Call sites also recognize e.g. `USER_NOT_FOUND`, `COMMENT_NOT_FOUND`, `PROFILE_PHOTO_LIMIT`, `PLUS_BELONGS_TO_ANOTHER`. |
| `UNKNOWN_SERVER_ERROR` | One envelope retry after 1 second for GET, ordinary POST and multipart. Excluded for `singleAttempt` POST. Shares the already-retried flag with auth retry. |
| Non-200, non-throttle | `invalidHTTPStatus`; do not treat HTML/errors from a proxy as an envelope. |
| Invalid JSON/status/date or missing required payload | Decoding error; retain pending local work. |

Backend `JsonRpcExceptionFilter` currently returns only `status/code/message` for `AppError`, dropping conflict version/date even though the error objects have them. The batch `conflicts` array retains them. Android must accept absent metadata and resolve by fetching the entity, not depend on the fixture's extra keys always arriving.

### Retry policies used in endpoint tables

`POST` means APIClient's ordinary JSON POST policy: three total attempts for a wire body below 10,000 bytes, two for a larger body, only on connection lost, timeout, cannot connect or offline errors. Retries wait 0.8 then 1.2 seconds and use fresh sessions. Other transport errors, including cancellation, return immediately. Raw JSON of at least 4,096 bytes is gzipped when possible (`Content-Encoding: gzip`); the 10,000-byte decision uses the compressed wire size. Request timeout is 30 seconds for raw JSON below 4,096 bytes, 90 seconds otherwise. Do not budget a sync chunk from its gzip size.

`GET` has no general network retry ladder; it has the shared auth and one-second envelope retry above. `Multipart` uses three total network attempts with the same 0.8/1.2-second waits; shared auth/envelope retry applies, and there is no custom timeout in that path. `Once` is JSON POST with one transport attempt and no `UNKNOWN_SERVER_ERROR` retry; auth refresh/resend still applies. Comments use this because the server generates their IDs.

`Sync` adds a persistent-entity queue over POST/Multipart: process the per-operation path in priority order (settings, vehicles, journeys, trips, photos, discoveries; vehiclePhoto is unlisted and sorts after listed types); failed operations remain visible. Auto-retry eligible operations have `retryCount < 5`, wait `2^maxRetryCount` seconds for the batch, then retry. After five failures they wait for manual retry, which resets the count. Never drop the entity at the cap. Queue operations are reconstructed from pending entities after process death; the queue itself is in memory. The trip batch fast path runs before the per-operation loop when at least two trip upload operations are queued, so it can precede a queued vehicle/settings write; do not assume strict dependencies across that fast path. Full sync runs on foreground, network return and a 300-second foreground timer. Failed reads are retried by a later sync pass. No APIClient policy parses `Retry-After`.

All endpoint rows inherit the envelope, auth-error and validation behavior above. Additional errors and idempotency are stated per row or area. A replay-safe state change may still increment `conflictVersion`; replay safety does not promise an identical response.

## Web routing for manual trips

The Android client does not call Geoapify directly. These authenticated endpoints proxy the provider and verify PRO server-side; provider keys stay on the server. The client reads capabilities once per signed-in session and refreshes them after a PRO entitlement change. `manualTrips` belongs to the website editor and does not gate the Android manual-trip sheet; Android requires both `search` and `routing`.

| Method and path | Request | Payload | Additional errors |
|---|---|---|---|
| POST `/web-routing/capabilities` | `{}` | `{manualTrips:boolean,search:boolean,routing:boolean}` | — |
| POST `/web-routing/search` | `{query:string,language:string}`; query length 3…200; language `en`, `ru`, `de`, `es`, `fr`, `it`, `pl`, `id`, `tr`, `uk`, `kk` or `pt` | `{places:[{name:string,latitude:number,longitude:number}]}`; at most six | `PRO_REQUIRED`, `MANUAL_TRIPS_DISABLED`, `ROUTING_NOT_CONFIGURED`, `ROUTING_UNAVAILABLE`, `TOO_MANY_REQUESTS` |
| POST `/web-routing/route` | `{waypoints:[[latitude,longitude],...]}`; start, up to three stops, end | `{coordinates:[[latitude,longitude],...],distance:number,durationSeconds:number}` | `ROUTE_NOT_FOUND`, `PRO_REQUIRED`, `MANUAL_TRIPS_DISABLED`, `ROUTING_NOT_CONFIGURED`, `ROUTING_UNAVAILABLE`, `TOO_MANY_REQUESTS` |

Business errors use HTTP 200 and the ordinary `{status:"error",code}` envelope. Android maps `ROUTE_NOT_FOUND` to the no-route state, `PRO_REQUIRED` to the locked path and other route codes to the generic failure state. Only transport `offline` is retryable; throttling shows the generic failure line. Never display provider/server messages. Filipino UI sends language `en`. Results and the route map carry the Geoapify / OpenStreetMap attribution string.

## Auth and session

Sources: `AuthDTOs.swift`, `AuthService.swift`, `TokenStore.swift`, `KeychainHelper.swift`; backend `auth/auth.controller.ts`, `auth/dto/`, `auth/services/{apple-auth,token}.service.ts`.

| Method and path | Auth | Request | Payload | Retry / idempotency / additional errors |
|---|---|---|---|---|
| POST `/auth/login` | Public | `LoginRequest` | `LoginResponse` | POST; same Apple subject resolves same account, but repeated login can mint another session. `INVALID_APPLE_TOKEN`; 5/hour. |
| POST `/auth/refresh` | Public | `{refreshToken:string}` required, max 4,096 chars | `{accessToken:string,refreshToken:string}` required | Special refresh policy below. `INVALID_REFRESH_TOKEN`; 20/min. |
| POST `/auth/logout` | JWT | `{}` | Empty | POST; revokes current device session. Local sign-out proceeds even if request fails. Repeating after revocation may require auth again. |
| POST `/auth/delete-account` | JWT | `{}` | Empty | POST; deletes account/server data. Clear local state after success according to the user's wipe choice. A repeated request may be unauthenticated; 3/hour. |
| POST `/auth/me` | JWT | `{}` | `Me` | POST read; replay-safe; 60/min. Profile section specifies all fields, including optional `plusUntil`. |

`LoginRequest`:

| Field | Type / presence | Meaning |
|---|---|---|
| `identityToken` | required string, max 8,192 chars | iOS sends **base64 of the UTF-8 compact Apple JWT bytes**, not the raw JWT. Backend now also accepts a raw compact JWT for web clients. Signature, issuer and configured audience must verify. |
| `localUserId` | required uuid | Stable per-install local identity, not `account.id`. Login binds/registers this device; do not substitute an account ID. |
| `deviceName` | optional string, max 128 | iOS device name. No Android platform/model fields exist yet. |
| `nonce` | optional string in DTO, max 256 | Raw nonce; iOS gives Apple its SHA-256 hash. Backend hashes raw nonce and compares to JWT claim. A JWT with a nonce claim requires the matching raw nonce. Legacy JWTs without the claim are still accepted. |

`LoginResponse`: required `accessToken:string`, `refreshToken:string`, `account:Account`, `isNewAccount:bool`. `Account`: required `id:uuid`, `avatarEmoji:string`; nullable/optional `displayName:string`, `email:string`, `showOnPublicMap:bool` (older server may omit the last). Store tokens and signed-in identity only after login succeeds. Apple name/email may only arrive on first authorization; display name is subsequently written through profile-update.

Refresh is single-flight across all in-flight requests. Each refresh POST is one transport attempt. Connection-lost gets one prompt retry after 1 second using the same old refresh token; timeout does not, to avoid holding all requests for another full timeout. Transient failures preserve the session and arm recovery at sequential delays 5, 30 and 120 seconds. Stop on success, session-generation change or missing refresh token; an unavailable secure store waits for another tick. A newer session must never accept an old refresh result.

Backend rotation returns the same successor refresh token on replay. A predecessor remains replayable for 60 seconds after rotation once the successor is used, or up to 30 days while the successor was never used (lost response). Refresh validity comes from DB row creation time plus configured TTL, not a client-decoded JWT expiry. iOS comments describe a 15-minute access token; Android must not hardcode deployment TTLs.

Definitive refresh rejection soft-expires; network/decoding/transport/unknown-server/throttle and HTTP 5xx retain session and schedule recovery. Other raw HTTP 4xx expire. Soft expiry deletes only access token/signed-in marker, retains refresh token and account/local state, and attempts revival on launch/foreground. Explicit sign-out, account deletion and ban fully clear credentials.

Secure storage semantics to port: distinguish value, missing and temporarily unavailable. A locked store after reboot is not sign-out. Scope credentials/JWS by app variant and API host; do not sync or back them up across devices. Updating a stored credential must preserve the old value if the write fails. Keychain APIs themselves are iOS-only.

Tests: `Sync/APIClientTokenRefreshTests`, `Sync/APIClientKeychainUnavailableTests`, `AuthSessionExpiryTests`, `KeychainHelperTests`, `KeychainWriteTests`.

## Sync push and pull

Sources: `Models/Sync/{SyncPullRequest,SyncPullResponse,SyncPushModels}.swift`, `Services/Sync/{SyncCoordinator,APISyncTransport,PullApplier,SyncChunkBudget,SyncEnqueuer,LastSyncedAtStore}.swift`, `Services/SyncQueue.swift`, `Persistence/TripRepository.swift`; backend `sync/`.

| Method and path | Auth | Request | Payload | Retry / idempotency |
|---|---|---|---|---|
| POST `/sync/pull` | JWT | `{lastSyncedAt?:date,entityTypes?:string[]}` | `Pull` | POST read, later sync pass on failure; 30/min. |
| POST `/sync/push` | JWT | iOS sends `{trips:Trip[]}` only | `PushResult` | Sync; stable entity IDs, per-item conflict resolution; 10/min. A failed batch is not proof that nothing committed. |
| POST `/sync/manifest` | JWT | `{}` | `{trips:uuid[],vehicles:uuid[],photos:uuid[],journeys?:uuid[],truncated?:bool}` | POST read; 10/min. |

`entityTypes` accepts only `trip`, `vehicle`, `photo`, `settings`, `journey`, `discovery`. Missing/null means all; `[]` requests no entity sections. Missing/null `lastSyncedAt` means full pull. Send server-returned time, never the phone's current clock. Backend delta predicates use `last_modified_at > since OR updated_at > since`.

`Pull`:

| Field | Type / presence | Meaning |
|---|---|---|
| `trips` | required `{upserted:Trip[],deleted:uuid[]}` | Metadata-only: no full track, photos, checkpoints or segments; also omits maxAltitude/drivingTime/stoppedTime present in detail (`SyncService.serializeTrip`). |
| `vehicles` | required `{upserted:Vehicle[],deleted:uuid[]}` | Whole vehicle metadata. |
| `photos` | required `{upserted:Photo[],deleted:uuid[]}` | Trip photo metadata/object keys; only photos owned by the account whose parent trip is also owned by it. Companion photos use social reads. |
| `settings` | optional Settings / null | No change, no row or excluded type means null. |
| `journeys` | optional `{upserted:Journey[],deleted:uuid[]}` | Missing/null means old server, preserve local. |
| `discoveries` | optional `{upserted:Discovery[],deleted?:string[]}` | Deleted keys are strings, never UUIDs; currently always empty and iOS does not apply individual discovery tombstones. |
| `serverTime` | required string containing date | Cursor; advance only after applying a successfully decoded response and parsing this date. |
| `ownedCounts` | optional `{trips:int,vehicles:int,photos:int,journeys?:int}` | Live server-owned counts, independent of selected entity types. |

Literal legacy payload fixture, `JourneySyncPayloadTests.testPullWithoutJourneysSectionDecodes`:

```json
{"trips":{"upserted":[],"deleted":[]},"vehicles":{"upserted":[],"deleted":[]},"photos":{"upserted":[],"deleted":[]},"settings":null,"serverTime":"2026-09-09T10:00:00.000Z"}
```

`PushResult`: backend returns required `trips` and `vehicles` arrays of `{id:uuid,conflictVersion:int,status:"created"|"updated"|"conflict",serverCreatedAt?:date}`; `deletions:[{id:uuid,success:bool}]`; optional `settings:{conflictVersion:int}`; `conflicts:[{entityType:string,entityId:uuid,serverVersion:int,serverLastModifiedAt:date}]`; `serverTime:date`. `serverCreatedAt` is returned for successful trips. iOS decodes only optional `trips`; it tolerates the rest as unknown fields. Unknown statuses, missing acknowledgements and whole-batch failures stay unhandled and fall back to per-trip upsert. A conflict is handled only after fetching/applying detail succeeds.

Backend also accepts optional `vehicles:Vehicle[]`, `deletedTripIds:uuid-v4[]`, `deletedVehicleIds:uuid-v4[]`, `deletedPhotoIds:uuid-v4[]`, `settings:Settings`. iOS does not send them through batch push; blobs and deletes use their own routes. There is no journey batch field. Missing/null/empty arrays perform no corresponding operation. Processing order is settings, vehicles, trips, deletions; earlier entities can commit before a later error. Do not retry a batch as though it were an atomic transaction.

Chunk packing uses `maxTrips = 25`, `maxBytes = 16 * 1024 * 1024` of estimated **uncompressed** JSON. Estimate each trip as `8192 + 320*trackPoints + 480*photos + 520*checkpoints + 200*segments`. Before adding a trip, flush a nonempty batch if already at 25 or the added estimate would exceed the budget. A single over-budget trip goes alone; the protocol cannot split one trip. Backend global JSON limit is 40 MiB after inflation; HTTP 413 is a transport failure, not a reason to silently truncate a route. `/plus` has its own 64 KiB parser limit; `/usage` has an 8 KiB limit (`src/main.ts`). `SyncChunkBudgetTests` checks estimates against real JSON, an oversized trip alone, byte packing and the count cap.

### Merge, deletion and privacy gates

- Pending local edits are not general permission to overwrite with a pull. Trip apply skips a pendingUpload/pendingDelete row when its local conflictVersion is at least the incoming version. Incoming newer versions can pass that guard; source/energy/break fields have additional pending-edit protection. Vehicle and journey apply protect their pending edits/deletes. Vehicle/journey conflict recovery first clears pending state, then applies the fetched server copy. Trip conflict recovery calls applyRemoteTrip directly; its pending/version guard still applies, so do not claim an unconditional overwrite.
- Trip tombstones delete only a mirrored local row (`serverCreatedAt != nil`). An intentionally unpublished/local-only trip survives its old server tombstone. Suppress its cascading photo tombstones too. Preserve `serverCreatedAt` from upload/detail/pull; for older incoming trips use `lastModifiedAt` as the mirror stamp. Never assume a pulled trip was never uploaded.
- Apply vehicles, photos, settings, journeys and discoveries in the same pull pass. Vehicle deletion also clears local photo files/rows, Bluetooth binding and photo visibility consent. Recompute affected manual-trip vehicle odometers after both trips and vehicles are applied.
- Sync cursor belongs to account **and local store identity**. A restored/reset database cannot reuse a cursor from the previous store. If server count exceeds local trip count, iOS performs one full heal per store, requesting trip/vehicle/photo/journey/discovery together. Do not advance a global cursor past deliberately filtered-out entity types that still need importing.
- If server has fewer entities than locally synced live entities, fetch manifest and requeue missing IDs. Manifest caps each type at 5,000; `truncated:true` forbids reconciliation. iOS reconciles trips/vehicles/photos, not journeys, despite backend also returning journey IDs.
- Require sign-in before queueing. Draft trips and their photos never upload even with Cloud Sync enabled. Draft confirmation is local and has no field in `Trip` wire payload.
- Cloud Sync on mirrors eligible data. Off: no pull, heal or manifest reconciliation; only explicitly public trips/journeys, their permitted photo work, and unpublishing/deleting existing server copies leave the phone. Vehicles, vehicle photos, settings and discoveries stay local. Recheck privacy at execution because it can change while queued.
- Profile identity/progress, bio, pins, profile photos, push registration and PRO attach are independent of the trip-library Cloud Sync switch.

Tests: `TombstoneGuardTests`, `DraftSyncGateTests`, `SyncQueueTests`, `SyncHealTests`, `LastSyncedAtStoreTests`, `VehicleSyncConflictTests`, `JourneySyncConflictTests`, `JourneySyncRecoveryTests`, `PullApplierStatsCacheTests`, `ManualTripSyncImportTests`, `RevealedLayerSyncTests`.

## Trips

| Method and path | Auth | Request | Payload | Retry / idempotency / additional errors |
|---|---|---|---|---|
| POST `/trips/upsert` | JWT owner | `Trip` below | `{id:uuid,conflictVersion:int,serverCreatedAt:date}` | Sync; stable ID upsert, can resurrect soft-deleted trip. `CONFLICT_DETECTED`, `TRIP_NOT_FOUND` for foreign-owned ID. |
| POST `/trips/detail` | JWT owner | `{id:uuid,includeTrackPoints:bool}`; iOS conflict/hydration sends true; backend defaults omitted bool to false | `Trip` with track when requested and checkpoints/segments when loaded | POST read; `TRIP_NOT_FOUND`; 120/min. |
| POST `/trips/delete` | JWT owner | `{id:uuid,conflictVersion:int}` | Empty | Sync; already-missing trip is treated as success by transport. Soft-delete cascades photos; no deletion version check in inspected service. |

`Trip` keys, shared by upload, detail and pull. Required means required in the iOS DTO, even if not applicable to every server response:

| Field | Type / presence | Units / values / semantics |
|---|---|---|
| `id` | required uuid | Local stable identity. |
| `title` | optional string | Max 200; default display name is computed locally when absent. |
| `description` | optional string | Max 2,000. |
| `startDate` | required date | Trip start. |
| `endDate` | optional date | Unknown/ongoing. |
| `distance` | required number | Metres, full local measured trip even after privacy trimming. |
| `maxSpeed`, `averageSpeed` | required number each | m/s. |
| `fuelUsed` | required number | Litres. |
| `elevation` | required number | Metres gained. |
| `maxAltitude` | optional number | Metres. |
| `drivingTime`, `stoppedTime` | optional int each | Seconds, local measured movement split. |
| `region` | optional string | Region label. |
| `isPrivate` | required bool | Trip publication flag, separate from account profile visibility. |
| `vehicleId` | optional uuid | No assigned vehicle when absent/null; transfer is a separate field. |
| `isTransfer` | optional bool | Passenger trip; preserve local if server omits. Excluded from vehicle odometer. |
| `fuelCurrency` | optional string | Stored currency symbol, no locale conversion. |
| `previewPolyline` | optional base64 string | Binary paired Float32 lat/lon, eight bytes per point, little-endian as emitted by iPhone; **not** Google's encoded polyline. Invalid length decodes to no coordinates. |
| `badgesJson` | optional string | JSON encoded as a string, containing stored badge data. Preserve spelling/case; not a nested object. |
| `xpEarned` | optional int | Missing decodes to no supplied value; local applier uses zero. |
| `conflictVersion` | required int | Initial local version, server returns accepted version. |
| `lastModifiedAt` | required date | Client edit time, participates in conflict arbitration. |
| `serverCreatedAt` | optional date | Server mirror stamp; upload may omit. |
| `trackPoints` | optional TrackPoint[] | Present upload/detail; absent pull preserves local track. `[]` explicitly replaces with no points. See backend mismatch below. |
| `photos` | optional TripPhotoMetadata[] | Metadata, not blobs; backend upserts rows, does not replace/delete rows absent from this list. |
| `checkpoints` | optional Checkpoint[] | Full replacement when present; absent/null preserves. |
| `segments` | optional Segment[] | Full replacement when present; absent/null preserves. |
| `source` | optional string | `recorded`, `manual`; absent preserves existing local, unknown incoming string decodes as `recorded`. |
| `energyMode` | optional string on read; always sent by current mapper | `auto`, `electric`, `fuel`. Send `auto` explicitly to reset choice. Absent/unknown incoming value preserves local mode. |
| `recordingBreaks` | optional date[] | Absent/null preserves; `[]` clears. Sorted unique pause boundaries, max 4,096; too many rejects rather than truncates. |

`TrackPoint`: required `id:uuid`, `latitude:number`, `longitude:number`, `altitude:number`, `speed:number`, `course:number`, `horizontalAccuracy:number`, `timestamp:date`, `isInterpolated:bool`. Course is degrees. Negative speed `-1` marks road fill; coarse/unmeasured points and road fill do not produce movement metrics. `recordingSegmentIndex` is derived from breaks and is never transmitted. A point at a break timestamp belongs to the next segment; do not join distance, movement time, replay or route strokes across an explicit pause.

`TripPhotoMetadata`: required `id:uuid`, `filename:string`, `timestamp:date`, `sortOrder:int`; optional `caption:string`, `capturedAt:date`, `exifLatitude:number`, `exifLongitude:number`. `timestamp` is when the photo entered the library, not necessarily capture time. Preserve a locally known capture time/coordinate over server echo. iOS sends coordinates as a complete pair; backend TripPhotoMetadataDto validates the two optional numbers independently.

Backend accepts points without nested class-validator allocation; its service validates finite numbers, IDs/dates, deduplicates and drops invalid points. Metadata photos truncate to 500 and checkpoints to 1,000 before nested validation. IDs are deduplicated case-insensitively, with the last occurrence winning; existing point IDs on another trip are skipped by `ON CONFLICT (id) DO NOTHING` (`TripsService.buildTrackPointRows`, `insertTrackPoints`, `upsertPhotoMetadata`). These caps are server behavior, not permission for Android to silently discard local data.

An existing trip with identical version and `lastModifiedAt <= server time` is a no-op. With differing versions, an older/equal client time conflicts; a newer client edit can win and increment the version. On conflict iOS fetches full `/trips/detail` and applies server state; on upsert `TRIP_NOT_FOUND` it purges the stale local row. A lost response can therefore cause a later version conflict rather than an identical acknowledgement.

Current backend always deletes track rows before inserting the incoming points on an accepted update, even if `trackPoints` is absent/null. Current iOS deliberately omits points for an unhydrated manual trip to preserve the route. This is a real contract mismatch, not a safe metadata-only update; see the final section. iOS manual routing itself uses MapKit locally and normal upsert; it does not call backend `/trips/manual/create`, `/trips/list`, or web routing endpoints.

Another existing asymmetry: the iOS applier treats `photos` missing/null as an empty server ID set and removes non-localOnly photo rows, then the pull's separate photo section can restore metadata. Checkpoints/segments/track do not use this rule. Do not generalize their absence handling to photos. Detail currently does not include a photo list, while the separate social photo endpoint supplies the gallery.

### Privacy zone

Trim only the outgoing payload, preserving the full local track, preview and metrics. Remove all coordinates strictly **inside** the manually selected radius, including interior visits, independent of `isPrivate`. A coordinate exactly on the radius remains. If fewer than two route points remain, send `trackPoints:[]` and omit preview. Backend maps omitted preview to null, so an old full preview is cleared. Strip both EXIF coordinate keys and both checkpoint coordinate keys when the location is inside the zone; preserve photo/checkpoint identity, time, names and distances. Never replace a hidden coordinate with an invented zero point in JSON. Raster uploads must contain no EXIF/GPS.

When a preview already exists locally and the zone is on, do not replace it with the server's trimmed echo. A new phone can only recover the trimmed route. Changes to the zone trigger republishing affected published trips. For a manual route not yet downloaded, hydrate it before a zone-trimmed public upload; a failed hydration retains pending work. Older CLAUDE notes saying checkpoint coordinates cannot be trimmed were superseded by the optional-coordinate change in 0.8.3.

Tests: `Sync/TripSyncPayloadMapperTests`, `HomePrivacyTrimTests`, `PrivacyZoneDisciplineTests`, `RecordingBreaksPersistenceTests`, `ManualTripHydrationBudgetTests`, `ManualTripSyncImportTests`, `PhotoMetadataTests`.

## Checkpoints and segments

They have no independent endpoints. Send them inside `/trips/upsert` or each `/sync/push.trips[]` entry. Editing either marks the parent trip pendingUpload and updates its edit time; otherwise the next authoritative list can erase the local edit.

| Checkpoint field | Type / presence | Meaning |
|---|---|---|
| `id` | required uuid | Stable checkpoint identity. |
| `timestamp` | required date | Moment on the trip. |
| `latitude`, `longitude` | optional number each | Coordinate pair; absent/null after home trimming. Keep an existing local coordinate; a new checkpoint without a pair has no map location. |
| `distanceFromStart` | required number | Metres accumulated by the trip odometer to this checkpoint. |
| `elapsedFromStart` | required number | Seconds from this leg's start. |
| `name` | optional string | Max 200. Absent means local generated label, never persist a translated default name. |
| `photoId` | optional uuid | Cover photo. |
| `photoIds` | optional uuid[] | Ordered manually attached photos; absent on older peers means no manual attachments. |
| `placeId` | optional uuid | Local place association; preserve a known local association over missing server value. Place matching does not mark trip pendingUpload. |
| `sortOrder` | required int in iOS; optional number in backend | Current mapper sends array index. Backend defaults to position. |

`Segment`: required `id:uuid`, `fromCheckpointId:uuid`, `toCheckpointId:uuid`; optional `name:string` max 200. No distance, duration, coordinates or independent conflictVersion. Compute length and elapsed time from endpoints, ordered by elapsed time. Both endpoints must belong to the same trip. Deleting a checkpoint removes attached segments locally. Missing/null checkpoint/segment arrays preserve existing lists; an explicit `[]` clears them. Backend deduplicates IDs and compares endpoint UUIDs case-insensitively, discards self-links/dangling segments and caps segments at **50**. It checks endpoint IDs against checkpoints in the **same request**; sending segments without checkpoints discards every segment. A detail response sends both keys together; ordinary delta pull sends neither.

Photo attachment resolution remains local: cover and explicit photoIds first; otherwise capture time within ±15 minutes, then a photo coordinate within 300 metres, then no attachment. One photo belongs to at most one checkpoint. This association is not an extra server relation.

Tests: `CheckpointSyncFlagTests`, `TripCheckpointPhotosTests`, `TripSegmentSyncTests`, `SegmentEnqueueTests`, `TripSegmentStoreTests`, `TripSegmentMetricsTests`; backend `trips/dto/trip-upsert.dto.spec.ts` and `TripsService.buildSegments`.

## Photos

Sources: `Services/Sync/{R2PhotoStorage,APISyncTransport,VehiclePhotoRestore,CompanionPhotoUploadService}.swift`, `Services/{RemotePhotoStorage,ProfilePhotoStore}.swift`, `Models/{ProfilePhotoDTO,PhotoUploadStatus}.swift`; backend `photos/`, `vehicles/vehicle-photos.service.ts`.

### Trip and garage photo endpoints

| Method and path | Auth | Request | Payload | Retry / idempotency / additional errors |
|---|---|---|---|---|
| POST `/photos/upload` | JWT owner or accepted companion | Multipart `TripUpload` | `UploadAck` | Multipart + Sync for own photos; same photoId/type replaces same object. `TRIP_NOT_FOUND`, `TRIP_NOT_PUBLIC`, `PHOTO_NOT_FOUND` (also denied access), `INVALID_PHOTO_FORMAT`; 30/min. |
| POST `/photos/url` | JWT photo owner in current backend | `{photoId:uuid,type:"thumbnail"\|"original"}` both required | `{url:string,expiresAt:date-string}` | POST read; `PHOTO_NOT_FOUND` if no such variant/access. |
| POST `/photos/delete` | JWT; authorized owner/uploader rules | `{photoId:uuid}` | Empty | Sync or direct POST; missing photo is an empty success; denied access uses `PHOTO_NOT_FOUND`. |
| POST `/vehicles/photos/upload` | JWT vehicle owner | Multipart `VehicleUpload` | `UploadAck` | Multipart + Sync; replay by photoId/type. `VEHICLE_NOT_FOUND`, `PHOTO_NOT_FOUND`, format/size errors; 30/min. |
| POST `/vehicles/photos/list` | JWT owner | `{}` | `{photos:OwnedVehiclePhoto[]}` | POST read, restore after pull with Cloud Sync on. Absence from list does not delete a local photo. |
| POST `/vehicles/photos/delete` | JWT owner | `{photoId:uuid}` | Empty | Sync; missing photo is an empty success, foreign-owned photo returns `PHOTO_NOT_FOUND`. |
| POST `/vehicles/photos/main` | JWT owner | `{vehicleId:uuid,photoId:uuid}` | Empty | Sync/POST; replay-safe main selection; `PHOTO_NOT_FOUND` for missing/wrong vehicle/access. |

Multipart has one binary part named `file`, filename `photo.jpg`, MIME `image/jpeg`, plus text fields. IDs/date/booleans are text, not nested JSON. Backend file limit is 15 MiB. `PHOTO_TOO_LARGE` is a defined code but the inspected multipart controllers delegate the limit to Multer; the JSON-RPC filter can surface that exception as `UNKNOWN_SERVER_ERROR` rather than the typed size code. Services also accept PNG, HEIC and WebP MIME types; iOS uploads JPEG. Do not infer successful decode from MIME alone. `type` is required and only `thumbnail` or `original`.

- `TripUpload`: required `tripId:uuid`, `photoId:uuid`, `type:string`, `timestamp:date`; optional `caption:string`. Backend permits missing timestamp and uses server time, but iOS always sends it. No EXIF coordinate fields in multipart; they travel in trip metadata after trimming.
- `VehicleUpload`: required current iOS `vehicleId:uuid`, `photoId:uuid`, `type:string`, `isMain:"true"|"false"`, `takenAt:date`. Backend makes last two optional. Send both UUIDs lowercase (regression in `VehiclePhotoUploadWireTests`).
- `UploadAck`: required `photoId:uuid`, `url:string`, `uploadStatus:int`. **`url` is an object storage key, not a display URL.** Status 1 means one variant exists, 2 both; the client enum is 0 localOnly, 1 uploading, 2 uploaded, 3 failed. Preserve separate per-variant completion state.
- `OwnedVehiclePhoto`: required `id:uuid`, `vehicleId:uuid`, `isMain:bool`; optional/null `url:string`, `thumbUrl:string`, `takenAt:date`. Here `url` and `thumbUrl` are signed display/download URLs, not keys.
- Pull `Photo`: required `id:uuid`, `tripId:uuid`, `filename:string`, `timestamp:date`, `sortOrder:int`, `uploadStatus:int`, `lastModifiedAt:date`; optional/null `caption:string`, `remoteUrl:string`, `thumbnailUrl:string`. These URL-named fields are stored object keys in this backend; ask `/photos/url` to download them.

Send thumbnail before original. Common raster preparation: longest side 200 px at JPEG quality 0.5 and 1,440 px at quality 0.7, no upscaling; remove EXIF/GPS/IPTC/XMP. The app stores successful thumbnail completion before starting original. A failed original must remain detectable/requeue on later sync, including when the app launches already on Wi-Fi. The owner path actually marks local uploadStatus=2 and syncStatus=synced once thumbnail exists; missing remoteURL is the remaining original-upload debt. Backend uploadStatus=2 still means both variants. Do not use the local status alone to infer original completion. Current uploads allow original over cellular too, to avoid a public photo remaining a thumbnail forever. An original path can choose smaller re-encoded bytes over the stored original.

Object URLs expire after about one hour; `/photos/url.expiresAt` is authoritative. Re-fetch social/garage/profile photo lists when opening a screen, and preserve keys separately from signed URLs. The APIClient byte download path accepts HTTPS only on `.r2.cloudflarestorage.com`, `.r2.dev`, `trip-track.app` and its subdomains, requires HTTP 200 and sends no API token. Some older iOS rescue/restore paths download directly through URLSession; Android should centralize equivalent access without sending credentials to object storage.

Accepted companions can upload their own photos into another person's trip; upload ownership is separate from trip ownership. A pending invitation grants no extra access: a private-trip invitee gets only the preview, while public trips remain readable as a viewer (`TripAccessService.resolveTripAccess`). Removing a companion removes companion access and their contributed photos; a public trip remains publicly readable. Such photos do not become an owned trip in the contributor's sync pull. CompanionPhotoUploadService has no persistent retry queue: it fails if thumbnail fails; if thumbnail succeeds and original fails, it returns degraded success. Social gallery only lists photos with a thumbnail. An original-only upload is therefore invisible. `/photos/url` is currently scoped to the photo uploader account; other trip viewers use signed URLs from the social photo list.

### Profile photos

Backend: `profile/profile.controller.ts` (`ProfileController`, delete/order DTOs), `profile/profile-photos.service.ts` (`ProfilePhotosService`, `profilePhotos`), `profile/profile-photo-upload.filter.ts` (`ProfilePhotoUploadFilter`). All four routes exist on master.

| Method and path | Auth | Request | Payload | Retry / idempotency / errors |
|---|---|---|---|---|
| POST `/profile/photos/upload` | JWT | Multipart: required lowercase `photoId:uuid`, `type:"thumbnail"\|"original"`, JPEG `file`; optional `takenAt:date` text | `UploadAck` | Multipart; keyed by ID/variant; 30/min. `PROFILE_PHOTO_LIMIT`, `INVALID_PHOTO_FORMAT`, `PHOTO_TOO_LARGE`, `PHOTO_NOT_FOUND` for foreign ownership. Limit error sets local warning; other errors keep debt. |
| POST `/profile/photos/list` | JWT | `{}` | `{photos:ProfilePhoto[]}` | POST read. |
| POST `/profile/photos/delete` | JWT | `{photoId:uuid}` lowercase | Empty | POST, persistent pending deletions; missing/already-deleted ID succeeds, foreign ownership returns `PHOTO_NOT_FOUND`. |
| POST `/profile/photos/order` | JWT | `{photoIds:uuid[]}` ordered, lowercase; max 20 IDs | Empty | POST; repeatable desired order; unknown/foreign/deleted IDs are ignored. Send only after both variants of every locally added photo succeeded. |

`ProfilePhoto`: required `id:uuid`; optional `thumbnailUrl:string`, `originalUrl:string`, `takenAt:string` (date), `sortOrder:int`. Malformed optional values decode as unknown rather than breaking the account response. Signed display URLs arrive from list, `/auth/me.photos` and public profile `.photos`; an upload ack's key is insufficient for display. List includes unfinished live uploads and `sortOrder`; me/public profile include only rows with both variants (`uploadStatus=2`) and a thumbnail, ordered by sortOrder then createdAt, at most nine, without sortOrder. Upload accepts JPEG/PNG/HEIC/WebP up to 15 MiB; its filter maps Multer oversize to `PHOTO_TOO_LARGE`. Invalid takenAt is ignored; omitted/invalid dates preserve an existing value. Upload revives a deleted ID when the nine-live-photo cap allows it. Reorder changes only listed live owned rows; `[]` does not clear/delete the gallery.

Limit nine photos. The store persists photo metadata, variant acknowledgements, pending deletions and orderDirty independently of Cloud Sync. Delete first, list/restore, upload missing variants, send order last. Unknown IDs in order can be ignored with success by the expected server, so clearing orderDirty before uploads finish would lose the order. Capture mutation/upload revisions when starting a request: stale snapshots may add missing remote photos, but cannot remove photos or undo a newer reorder. A fresh list can remove fully uploaded or remote-only photos absent from the server; keep unfinished local uploads and local deletion tombstones. Account-generation guards reject old-account responses/imports. Wipe clears files, metadata and cache.

Tests: `ProfilePhotoStoreTests`, `VehiclePhotoUploadWireTests`, `VehiclePhotoRecoveryTests`, `CompanionPhotoUploadTests`, `Sync/MultipartFormDataBuilderTests`, `PhotoMetadataTests`. The wire fixture in `VehiclePhotoUploadWireTests` intentionally has an invalid upload response ID and tests request casing only; do not use it as a response schema.

## Journeys

Sources: `JourneySyncPayload.swift`, `JourneyManager.swift`, `APISyncTransport.swift`; backend `journeys/`, `social/dto/journey.dto.ts`.

| Method and path | Auth | Request | Payload | Retry / idempotency / errors |
|---|---|---|---|---|
| POST `/journeys/upsert` | JWT owner | `Journey` | `{id:uuid,conflictVersion:int}` | Sync; upsert by stable ID; `CONFLICT_DETECTED`. |
| POST `/journeys/list` | JWT owner | `{}` | `{journeys:Journey[]}` | POST read; full live list, no `/journeys/detail`. |
| POST `/journeys/delete` | JWT owner | `{id:uuid}` | Empty | Sync; `JOURNEY_NOT_FOUND` treated as already deleted. |
| POST `/social/journey` | Guest | `{journeyId:uuid}` | `{journey:JourneyHead,legs:FeedTrip[]}` | POST read; `JOURNEY_NOT_FOUND` also masks inaccessible/private journeys. |
| POST `/social/share-journey` | JWT | `{journeyId:uuid,expiresInDays?:int}` (1..365) | `Share` | POST; each call creates a link, not guaranteed idempotent; 10/min. |
| GET `/users/{id}/journeys` | Guest | UUID path; optional query `cursor:string`, `limit:int` (default 50, clamp 1..200) | `{journeys:PublicJourney[],nextCursor?:string}` | GET read; `USER_NOT_FOUND`. |

`Journey`: required `id:uuid`, `startDate:date`, `excludedTripIds:uuid[]`, `isPrivate:bool`, `conflictVersion:int`, `lastModifiedAt:date`; optional `title:string` max 200, `endDate:date`, `coverPhotoId:uuid`, `serverCreatedAt:date`. Backend also makes excludedTripIds optional. No `tripIds` and no trip `journeyId`: membership is owned trips whose startDate lies inside the inclusive window, minus exclusions. End absent/null represents an open journey. Aggregate kilometres/time/regions from legs, not extra stored totals.

Backend update preserves omitted title/endDate/coverPhotoId/exclusions, explicit null clears nullable values and null exclusions becomes `[]`. Current iOS synthesized Codable **omits nil** title/endDate/coverPhotoId, so clearing an existing value is not reliably transmitted; do not claim it sends null. `excludedTripIds:[]` does explicitly clear exclusions. This mismatch needs a coordinated fix below.

On conflict fetch list, find the row, clear pending state and apply server copy. A missing row in that list is not a tombstone: leave the local journey; later pull/delete/retry settles it. Journey delete does not delete its trips.

Publishing makes each private leg public through its own trip upsert, then publishes the journey. This is not atomic. Hiding changes only the journey, leaving legs public. Journey `.unpublish` is an upsert with `isPrivate:true`; trip `.unpublish` with Cloud Sync off is server delete after rescuing any server-only photo files and archiving available discussion, keeping the local trip. Sign-out hide order is journeys before trips.

`JourneyHead`: required `id:uuid`, `startDate:date`, `isPrivate:bool`, `legCount:int`, `author:Author`; optional/null `title:string`, `endDate:date`, `coverPhotoId:uuid`. `PublicJourney`: required `id:uuid`, `startDate:date`, `legCount:int`, `legs:PublicTrip[]`; optional/null `title:string`, `endDate:date`, `coverPhotoId:uuid`. `PublicTrip` is defined with public profiles below. `Share` has required `shareUrl:string`, `shareCode:string`, optional/null `expiresAt:date`; absent expiresInDays means no expiry. Other viewers only receive public legs; the owner can receive private legs too (`SocialService.getJourney`, `loadLegs`); private journey membership never leaks via a feed trip's `journey` key.

Literal synthetic payload fixture from `PublicJourneyDTOTests.testPublicJourneysResponseDecodes`:

```json
{"journeys":[{"id":"33333333-3333-4333-8333-333333333333","title":null,"startDate":"2026-09-12T00:00:00.000Z","endDate":"2026-09-17T00:00:00.000Z","coverPhotoId":null,"legCount":1,"legs":[{"id":"11111111-1111-4111-8111-111111111111","startDate":"2026-09-13T10:00:00.000Z","endDate":"2026-09-13T12:00:00.000Z","distance":480000,"duration":7200,"region":"Краснодарский край","previewPolyline":null}]}],"nextCursor":null}
```

Tests: `JourneySyncPayloadTests`, `JourneyPublishTests`, `JourneySyncConflictTests`, `JourneySyncRecoveryTests`, `SyncEnqueuerJourneyTests`, `PublicJourneyDTOTests`, `PublicJourneyAdapterTests`.

## Vehicles and garage

| Method and path | Auth | Request | Payload | Retry / idempotency / errors |
|---|---|---|---|---|
| POST `/vehicles/upsert` | JWT owner | `Vehicle` | `{id:uuid,conflictVersion:int}` | Sync; stable ID, `CONFLICT_DETECTED`. |
| POST `/vehicles/list` | JWT owner | `{}` | `{vehicles:Vehicle[]}` | POST read; full garage; no `/vehicles/detail`. |
| POST `/vehicles/delete` | JWT owner | `{id:uuid}` | Empty | Sync; `VEHICLE_NOT_FOUND` treated as success. |
| GET `/users/{id}/garage` | Guest | UUID path, no body | `{vehicles:PublicVehicle[]}` | GET read; `USER_NOT_FOUND` masks private/inaccessible profile. |
| GET `/users/{id}/vehicles/{vehicleId}` | Guest | UUID paths, no body | `PublicVehicle` | GET read; `USER_NOT_FOUND` for hidden/missing/foreign vehicle. |

`Vehicle` (iOS `Models/Sync/VehicleSyncPayload.swift`):

| Field | Type / presence | Units / allowed values / semantics |
|---|---|---|
| `id` | required uuid | Stable identity. |
| `name`, `avatarEmoji` | required string each | Avatar may be an emoji or a bundled asset slug; use the iOS shared VehicleAvatar asset vocabulary without renaming it. |
| `odometerKm` | required number | TripTrack kilometres; never miles. |
| `manualOdometerKm` | always encoded number or **null** by current iOS | Dashboard odometer km. Missing on an old response means unknown/preserve; null explicitly clears. `manualOdometerKnown` is decode-only presence state, never a wire key. |
| `level` | required int | Derived garage level. |
| `stickersJson` | optional string | JSON inside a string. |
| `cityConsumption`, `highwayConsumption` | required number each | L/100 km. |
| `fuelPrice` | required number | Currency units per litre. |
| `conflictVersion` | required int | Optimistic version. |
| `lastModifiedAt` | required date | Edit time. |
| `vehicleType` | optional string | `car`, `moto`, `moped`, `bicycle`; unknown local value renders as car. |
| `avatarStyle`, `bodyType` | optional string each | Silhouette/passport body identifiers; styles: `car`, `hatchback`, `crossover`, `pickup`, `van`, `convertible`, `sports`, `motorcycle`, `scooter`, `bicycle` from shared iOS VehicleAvatar. Unknown style falls back to the type's default. Backend accepts strings, not an enum whitelist. |
| `plate` | optional string | Registration text; `""` clears. |
| `plateVisible`, `visibleToOthers` | optional bool each | Plate visibility and whole vehicle visibility; absent preserves. |
| `fuelCurrency` | optional string | Stored symbol; normalized nonempty bounded value, invalid means preserve. |
| `dashboardUnits` | optional string | `app`, `metric`, `imperial`; trim/lowercase; unknown or wrong response type means preserve local choice. |
| `cardStyle` | optional string | `""`, `card_carbon`, `card_gold`, `card_neon`, `card_racing`, `card_chrome`, `card_matte`, `card_camo`, `card_sunset`; unknown is ignored by backend. |
| `powertrain` | optional string | `fuel`, `electric`, `pluginHybrid`; backend trim/case normalization ignores unknown strings. Wrong request types fail DTO validation; malformed response values preserve local choice. |
| `electricConsumption` | optional number | kWh/100 km; zero means unset. |
| `electricityPrice` | optional number | Currency units per kWh; zero means unset. |
| `electricRangeKm` | optional number | Km; zero means unset. Backend ignores negative numbers; non-number request values fail DTO validation. |
| `about`, `make`, `model` | optional string each | Passport values; absent preserves. |
| `year` | optional int | Model year. |
| `mapVisible`, `photosVisible`, `isArchived` | optional bool each | Separate public surfaces/archive state; absent preserves. |
| `soldAt` | always encoded date or **null** by current iOS | Null returns car from sold state. Absent on old response preserves local; `soldAtKnown` is local decoder state. |
| `drive2Url` | optional string | Garage 0.9.1, absent preserves, `""` clears. Normalize a Drive2 `/r/{make}/{model}/{id}/` page; invalid nonempty input stores null. |
| `modTags` | optional string[] | At most 3 trimmed unique tags, 24 characters each. `[]` clears. |
| `paintFinish` | optional string | `gloss`, `matte`, `pearl`, `chrome`; current picker offers gloss/matte. Preserve unknown stored value; paid display may fall back. |
| `mods` | optional VehicleMod[] | Whole modification journal; `[]` clears. |
| `catalogModelId`, `catalogGenerationId`, `catalogVariantId` | optional string each | Stable catalog IDs, absent preserves, `""` clears. Freeform/old catalog selection or identity edits clear IDs explicitly. |

`VehicleMod`: required `id:uuid`, `title:string`, `date:string` calendar day, `photoIds:uuid[]`; optional `odometerKm:number`. iOS limits 100 modifications, six photos per modification; newest calendar day first, ties by UUID string. No UTC shift of the entered day. The seven 0.9.1 garage keys from drive2Url through catalogVariantId persist on master and are returned by list/pull (`VehicleUpsertRequestDto`, `VehicleEntity`, `VehiclesService.mapDtoToEntity`/`serializeVehicle`, `vehicle-garage.ts`). The DTO allows arbitrary input for these keys; `mapGarageFields` normalizes them. Omitted/null keys preserve stored values. A nonempty invalid drive2Url stores null; valid links normalize to HTTPS www.drive2.ru/com with no query/fragment, max 200 characters. Tags trim, remove control characters, truncate to 24 Unicode characters, deduplicate and keep three. paintFinish accepts the four listed values, `""` stores gloss, unknown values preserve. Catalog IDs accept `""` or 1..120 lowercase letters/digits/hyphens; invalid values preserve. Mods keep the first 100 valid unique UUIDs, trim/truncate titles to 80 Unicode characters, require a real YYYY-MM-DD date, retain only finite nonnegative odometerKm, and keep six unique valid photo UUIDs. Invalid entries are skipped; an input array that normalizes to [] clears. Backend preserves request order; iOS orders mods by day/UUID.

Null rules are field-specific. Current iOS emits explicit null only for manualOdometerKm and soldAt; ordinary other nil optionals are omitted. Its remote merger uses presence to preserve old-server unknowns. Backend currently maps *missing* manualOdometerKm to null too, unlike the iOS receive rule; Android should always send its known value or explicit null, and backend should fix legacy omission handling. SoldAt uses proper omission-preserving update semantics.

On conflict, fetch `/vehicles/list`, clear pending state and apply that row. A missing row in the list does not authorize inferred deletion. On success, adopt returned conflictVersion but mark synced only if lastModifiedAt still matches the uploaded snapshot; a newer local edit stays pending. Odometer changes and vehicle identity edits must mark pendingUpload too.

`PublicVehicle`: required `id:uuid`, `name:string`, `avatarEmoji:string`, `level:int`, `odometerKm:number`. Optional/null: `avatarStyle:string`, `vehicleType:string`, `make:string`, `model:string`, `year:int`, `bodyType:string`, `about:string`, `isArchived:bool`, `soldAt:date`, `plate:string`, `mapVisible:bool`, `photosVisible:bool`, `createdAt:date`, `mainPhoto:string` signed original when available, otherwise thumbnail, `photos:PublicVehiclePhoto[]`, `cardStyle:string`, `tripPhotos:GarageTripPhoto[]`, `drive2Url:string`, `modTags:string[]`, `paintFinish:string`, `mods:PublicVehicleMod[]`, `catalogModelId:string`, `catalogGenerationId:string`, `catalogVariantId:string`. Additive malformed optional fields are ignored. List/detail have different subsets; do not require detail-only fields on garage rows.

`PublicVehiclePhoto`: required `id:uuid`, `isMain:bool`; optional/null `url:string`, `thumbUrl:string`. `GarageTripPhoto`: required `id:uuid`, `timestamp:date`; optional/null `url:string`, `thumbUrl:string`. `PublicVehicleMod`: required `id:uuid`, `title:string`, `date:string`; optional `odometerKm:number`, `photos:PublicVehiclePhoto[]`. Master returns the 0.9.1 fields on garage/detail. Garage rows include mods without photos and mainPhoto; detail returns the first 12 vehicle photos plus referenced mod photos (up to 600), and up to 600 tripPhotos newest first. Trip photos must belong to the vehicle owner and a live trip with a thumbnail; other viewers receive only public-trip photos. photosVisible gates all three photo surfaces. `publicGarageFields` suppresses modTags and falls back to gloss without active PRO, retaining stored choices; mod text and Drive2/catalog fields remain visible (`SocialService.getGarage`, `getVehicle`, `publicVehicleTripPhotos`; `publicVehicleMods`).

The public shape deliberately excludes consumption, fuel prices and manual odometer. Whole vehicle visibility, map visibility, photos visibility and plate visibility are different permissions. Hidden vehicle responds like missing; closed photos produce no main photo/empty photo list; never download then hide forbidden media. Public paid styling is server-filtered by entitlement without erasing the owner's stored choice.

Tests: `VehicleSyncPayloadTests` (including card-style cases), `VehicleSyncConflictTests`, `RemoteVehicleMergeTests`, `GarageDataTests`, `TransferAndOdometerTests`, `VehicleOdometerTests`, `VehiclePhotoRecoveryTests`.

## Profile and settings

### Account profile

| Method and path | Auth | Request | Payload | Retry / idempotency |
|---|---|---|---|---|
| POST `/auth/profile-update` | JWT | `ProfileUpdate` | Empty | POST; field patch, replay-safe values, 60/min. |
| POST `/settings/upsert` | JWT | `Settings` | `{conflictVersion:int}` | Sync; account settings row, `CONFLICT_DETECTED`; iOS leaves that conflict for a later pull. |

`ProfileUpdate` fields are **all optional**. Nil is omitted, not encoded as null. Only send fields this action intends to write.

| Field | Type / limits | Clearing / rule |
|---|---|---|
| `displayName` | string, max 30 | Sanitized, 2..30 nonblank chars, contains a letter; reject mixed Latin/Cyrillic in one component, five repeated identical chars and four punctuation chars in a row. Empty sanitized value is no change. |
| `avatarEmoji` | string, max 32 | Emoji-like 1..16 nonspace/nonunderscore chars or known pixel slugs `pixel_car_orange`, `pixel_car_green`, `pixel_car_black`, `pixel_car_gray`, `pixel_car_blue`, `pixel_car_red`. |
| `bio` | string, max 160 after sanitation | `""` clears. Remove invisible/control chars, normalize line endings/space and excess blank lines. Explicit null also clears on backend; current iOS sender uses empty string. |
| `pinnedTripIds` | ordered uuid[] strings | `[]` clears; omitted/null/non-array preserves. `sanitizePinnedTripIds` filters valid UUIDs, lowercases/deduplicates and considers at most 20; `AuthService.updateProfile` stores the first three live owned IDs, including private/incomplete trips. `SocialService.getPinnedTrips` displays only completed public live owned trips, preserving pin order. |
| `profileBackground` | string, max 32 | `""` removes; enum below. |
| `profileLevel` | int 1..110 | Lifetime maximum. |
| `profileXp` | int 0..10,000,000 | Lifetime maximum; absent does not mean zero. |
| `currentStreak` | int 0..10,000 | Can decrease. |
| `bestStreak` | int 0..10,000 | Lifetime maximum. |
| `activeVehicleId` | UUID string or empty | `""` (backend also accepts null) clears. Unknown/foreign/unsynced vehicle is silently skipped, so sync vehicle first. |
| `language` | string matching lowercase `[a-z]{2,3}` | Current tags: en, ru, de, es, fr, it, pl, id, tr, fil, uk, kk, pt. No closed server whitelist. |
| `showOnPublicMap` | bool | Website globe opt-in, separate from social visibility. |
| `isPublic` | bool | Only explicit visibility action writes it. Profile sync must not re-send a stale mirror. |
| `countersPublic`, `statsPublic`, `mapPublic`, `achievementsPublic` | bool each | Only the explicitly changed block is written; missing preserves. |
| `avatarFrame` | string, max 32 | `""` removes; unknown ignored server-side. |
| `showPlusBadge` | bool | Badge preference, not payment status. False must not turn off paid cosmetics. |

Background values: `""`, `sunset`, `ocean`, `forest`, `mountain`, `midnight`, `dawn`, `copper`, `slate`, `aurora`, `sand`, `plus_nebula`, `plus_lava`, `plus_glacier`, `plus_neon`, `plus_carbon`, `plus_gold`, `plus_tropic`, `plus_storm`. Avatar frames: `""`, `frame_gold`, `frame_carbon`, `frame_neon`, `frame_chrome`, `frame_laurel`, `frame_flame`. Unknown client display values fall back safely; retain stored premium choices after entitlement expires. These cosmetics travel through profile-update/me, never through Settings.

`Me` response from `/auth/me`: only `isPublic:bool` is required by iOS. Optional/null `id:uuid`, `email:string`, `displayName:string`, `avatarEmoji:string`, `profileBackground:string`, `avatarFrame:string`, `showPlusBadge:bool`, `profileLevel:int`, `profileXp:int`, `showOnPublicMap:bool`, `countersPublic:bool`, `statsPublic:bool`, `mapPublic:bool`, `achievementsPublic:bool`, `notifyReactions:bool`, `notifyFollows:bool`, `notifyComments:bool`, `notifyWeeklyRecap:bool`, `createdAt:string` date and `plusUntil:date|null`. New journal fields: `bio:string|null`, `photos:ProfilePhoto[]`, `pinnedTripIds:uuid[]`. Presence of bio is tracked separately: absent/malformed means do not change; null means clear. `bioProvided` is local decode state, never a wire key. Malformed optional journal fields do not break me. The backend also returns `notifyCompanions:bool`. `AuthService.me` returns photos through `profilePhotos`; `serializeMe` returns stored pinnedTripIds (or []).

Successful `/auth/me` decode is the iOS capability check for profile privacy. Missing block flags mean default-open for display, but if **all** block flags are absent, don't offer apparently working block-privacy switches. Read and monotonically merge server level/XP before the first progress push, including with Cloud Sync off. If this read fails, skip progress instead of overwriting an existing account with fresh-install defaults. Bio/pins write only when dirty, one request at a time; acknowledge a dirty revision only if no newer edit occurred. Retain pins whose trips are not on this phone; remove only trips known locally to be private/incomplete/deleted. Wait until locally known pinned trips are uploaded before treating pin submission as final, because the expected server ignores unknown IDs.

Literal fixture from `ProfileJournalWireTests.testMeJournalAndNullBio`:

```json
{"isPublic":true,"bio":null,"photos":[],"pinnedTripIds":[]}
```

This is an explicit clear. The fixture `{"isPublic":true}` in the same file means none of those journal fields were supplied. The tested outgoing clear is `{"bio":"","pinnedTripIds":[]}`.

### Settings row

`Settings`: required `id:uuid` (localUserId), `avatarEmoji:string`, `themeMode:string`, `language:string`, `distanceUnit:string`, `volumeUnit:string`, `fuelConsumption:number`, `fuelPrice:number`, `fuelCurrency:string`, `profileLevel:int`, `profileXp:int`, `currentStreak:int`, `bestStreak:int`, `conflictVersion:int`, `lastModifiedAt:date`; optional `selectedVehicleId:uuid`, `lastTripDate:date`. Current theme values are `system`, `light`, `dark`; distance `km`/`miles`, volume `liters`/`gallons`; raw stored numeric consumption/price remain metric. Backend validates these unit/language/theme fields as strings; the clients own the recognized values and old-value fallback. Response pull has the same shape with nullable optional values.

No avatarFrame, showPlusBadge, bio, pins, privacy-zone center, Bluetooth binding, purchase entitlement or notification permission in this row. `selectedVehicleId` is settings state, whereas `activeVehicleId` is the public account's selected car. iOS omits nil selectedVehicleId/lastTripDate; do not infer an explicit clear from those omitted keys. Progress merge preserves lifetime maxima; cosmetic selection and dirty local fields have their own reconciliation rules.

Auto-record preferences stay on the phone: `autoRecordMode`, `autoStopTimeout`, `savedBluetoothDevices` and (iOS 0.9.3) `autoRecordTrigger`. The trigger stores `motionOrStereo` or `stereoOnly`; missing, empty and unknown values use `motionOrStereo`. None of these keys enters a settings upload or pull.

Tests: `SettingsSyncPayloadTests`, `SettingsUnitWireTests`, `RemoteSettingsMergeTests`, `ProfileProgressSyncTests`, `ProfileSyncLatchTests`, `ProfileJournalWireTests`, `VisibilityWireTests`, `ProfileBackfillTests`, `ProfilePhotoStoreTests`.

## Social feed and public profiles

Sources: `Models/Social/{SocialDTOs,GarageDTOs,CompanionsDTOs,NotificationsDTOs}.swift`, `Services/Social/`, `Services/CompanionsStore.swift`, public profile views/pager; backend `social/{social.controller,social.service}.ts`, `social/controllers/users.controller.ts`, `social/dto/`, `companions/`, `groups/`.

### Feed, people and actions

All requests here are JSON objects. Required/optional types are part of each cell; `{}` means no fields.

| Method and path | Auth | Request | Payload | Retry / idempotency / additional errors |
|---|---|---|---|---|
| POST `/social/feed` | Guest | `{limit?:int,cursor?:string,type?:"all"\|"following"}` | `{trips:FeedTrip[],nextCursor?:string}` | POST read. Default limit 20, max 50. |
| POST `/social/trip` | Guest | `{tripId:uuid,includeTrack:bool}` (iOS default false; optional backend) | `{item:FeedTrip,track?:ReplayPoint[]}` | POST read; `TRIP_NOT_FOUND`/access failure. |
| POST `/social/trip/photos` | Guest | `{tripId:uuid}` | `{photos:SocialPhoto[]}` | POST read; trip access gate; re-fetch signed URLs per open. |
| POST `/social/follow` | JWT | `{targetAccountId:uuid}` | `{following:bool}` true | POST; duplicate returns `ALREADY_FOLLOWING`, self `CANNOT_FOLLOW_SELF`, target `USER_NOT_FOUND`. Not strict acknowledgement-idempotent. |
| POST `/social/unfollow` | JWT | `{targetAccountId:uuid}` | `{following:bool}` false | POST; replay-safe delete relation. |
| POST `/social/followers` | Guest | `{accountId?:uuid,limit?:int,offset?:int}` | `{users:Author[],total:int}` | POST read; default account is caller if authenticated, default page 20/max 100. Guest should supply accountId. |
| POST `/social/following` | Guest | Same as followers | `{users:Author[],total:int}` | POST read, same pagination. |
| POST `/social/search` | Guest | `{query:string,limit?:int}`; query max 64 | `{users:Author[]}` | POST read; default 20, clamp 1..50; blank/short query follows server search rules. |
| POST `/social/suggested` | Guest | `{limit?:int}` | `{users:SuggestedUser[]}` | POST read; default 10, clamp 1..20. |
| POST `/social/react` | JWT | `{tripId:uuid,emoji:string}` | `{reacted:bool}` true | POST; one reaction per account/trip, updates emoji. `CANNOT_REACT_SELF`, `TRIP_NOT_FOUND`, `TRIP_NOT_PUBLIC`. |
| POST `/social/unreact` | JWT | `{tripId:uuid}` | `{reacted:bool}` false | POST; replay-safe remove. |
| POST `/social/reactions` | Guest | `{tripId:uuid}` | `{reactions:[{user:Author,emoji:string,createdAt:date}]}` | POST read. |
| POST `/social/share` | JWT | `{tripId:uuid,expiresInDays?:int}`; 1..365 | `Share` | POST, creates a new code per invocation; no request idempotency key. Missing expiry means no expiry. `TRIP_NOT_FOUND`, `TRIP_NOT_PUBLIC`. |
| POST `/social/block` | JWT | `{targetAccountId:uuid}` | `{blocked:bool}` true | POST, repeatable relation; self uses `CANNOT_FOLLOW_SELF`, missing target `USER_NOT_FOUND`. |
| POST `/social/unblock` | JWT | `{targetAccountId:uuid}` | `{blocked:bool}` false | POST, repeatable remove. |
| POST `/social/blocked` | JWT | `{}` | `{users:BlockedUser[]}` | POST read. |
| POST `/social/report` | JWT | `{targetType:string,targetId:uuid,reason:string,notes?:string}` | `{reported:bool}` | POST; backend appends a report, no stable client request ID; repeat can duplicate. |

Social controller quota is 60/min per route unless overridden; comment-create and share-journey are 10/min. iOS uses ordinary POST for share/report despite their non-idempotence: document that limitation, don't assume the retry ladder deduplicates them. Optimistic follow/reaction/block state rolls back when the action fails. Empty feed/error retains caches and displays retry state; on an empty first page after transient error/cancellation iOS permits two extra attempts 1.5 seconds apart while online, stopping if a newer load has started. Backend following feed includes followed authors and excludes the viewer's own trips; the older iOS request comment still says own trips are included. Following feed with no followed content stays empty; never top it up with discovery.

Reaction wire values offered today: `🔥`, `🤯`, `🏁`, `🛣️`, `🌅`, `👍`. Backend also accepts legacy `❤️`, `🏎️`, `🗺️`; display merges them to `👍`, `🏁`, `🛣️`, including variation-selector tolerance. Never rewrite stored emoji keys opportunistically. `ReportReason`: `spam`, `harassment`, `hate`, `nudity`, `violence`, `illegal`, `impersonation`, `other`. `targetType`: backend accepts `user`, `trip`, `comment`, `vehicle`, `vehicle_photo` (the comment on the older iOS request struct only lists the first two). Notes max 500.

`Author`: required `id:uuid`, `profileLevel:int`; optional/null `displayName:string`, `avatarEmoji:string`, `isPlus:bool`, `avatarFrame:string`. `isPlus` controls the badge, not all paid appearance.

`FeedTrip`:

| Field | Type / presence | Meaning |
|---|---|---|
| `id`, `author`, `startDate` | required uuid, Author, date | Trip identity, account and time. |
| `distance`, `duration` | required number, int | Metres and wall-clock seconds. Feed average speed is distance/duration in m/s; zero duration gives zero. |
| `photoCount`, `reactionCount` | required int each | Counts. |
| `reactionBreakdown` | required `{emoji:string,count:int}[]` | Aggregate tallies. |
| `badgeIds` | required string[] | Stable stored achievement IDs. |
| `title`, `description`, `region` | optional/null string each | Text. |
| `endDate` | optional/null date | Finish. |
| `maxSpeed`, `elevation`, `maxAltitude` | optional/null number each | m/s, metres gained, metres peak. |
| `drivingTime`, `stoppedTime` | optional/null int each | Seconds, may be missing on older records. |
| `isPrivate` | optional/null bool | Missing reads as public in social adapter; explicit true must survive for companion trips. |
| `previewPolyline` | optional/null base64 string | Same binary Float32 format as owned trips. |
| `firstPhotoThumbnail` | optional/null string | Signed display URL. |
| `vehicle` | optional/null `{id?:uuid,name:string,avatarEmoji:string,avatarStyle?:string}` | Only a visible vehicle; id optional on older backend. |
| `myReaction` | optional/null string | Viewer's reaction. |
| `commentCount` | optional/null int | Missing displays zero; Swift's `commentCountRaw` is not a wire key. |
| `journey` | optional/null `{id:uuid,title?:string,startDate:date,endDate?:date}` | Public journey containing this leg. Preserve across optimistic card rebuilds. |
| `source` | optional/null string | recorded/manual; unknown parses recorded. |

`ReplayPoint`: required `lat:number`, `lon:number`, `speed:number` (m/s), `t:date`. Optional track key is absent when not requested; explicit `[]` means requested but no points. Backend samples towards 400 points and preserves the last; this is a replay sample, not the full owned track. It does not carry checkpoints or full point accuracy/altitude. `SocialPhoto`: required `id:uuid`, `timestamp:date`; optional/null `caption:string`, `thumbnailUrl:string`, `originalUrl:string`. Either variant may be unavailable independently. `BlockedUser`: required `id:uuid`, `profileLevel:int`; optional `displayName:string`, `avatarEmoji:string`, `blockedAt:string` date.

`SuggestedUser`: required `id:uuid`, `profileLevel:int`; optional/null `displayName:string`, `avatarEmoji:string`, `totalKm:number`, `matchReason:string`, `isPlus:bool`, `avatarFrame:string`. Match reasons recognized for display are sharedRegion/shared_region (also sharedregions), nearby and popular, case/underscore tolerant. Unknown reason (backend can send `new`) omits rationale text. Kilometres here are already divided by 1,000.

### Comments

| Method and path | Auth | Request | Payload | Retry / idempotency / errors |
|---|---|---|---|---|
| POST `/social/comment` | JWT | `{tripId:uuid,text:string,parentId?:uuid}` | `{id:uuid,createdAt:date}` | **Once**, no automatic transport/server retry. Non-idempotent insert; 10/min. |
| POST `/social/comments` | Guest | `{tripId:uuid,limit?:int,cursor?:string}` | `{comments:Comment[],nextCursor?:string}` | POST read; backend default 30 (iOS supplies 20), clamp 1..50. |
| POST `/social/comment/delete` | JWT author or trip owner | `{commentId:uuid}` | `{deleted:bool}` | POST; `COMMENT_NOT_FOUND` on repeat/missing is treated as success by store. |

Text is trimmed, required nonempty, max 500. Parent must be on the same trip; replies to replies are attached to the root, only one nesting level. Additional errors: `TRIP_NOT_FOUND`, `TRIP_NOT_PUBLIC`, `COMMENT_NOT_FOUND`; invalid body gives `VALIDATION_FAILED`. A private accepted-companion trip is viewable but does not gain public comments/reactions/share affordances. iOS swaps optimistic temp ID/date for server values; failed create removes the optimistic row and preserves error state, throttle gets its own message. A lost create response is uncertain, so manual retry can still duplicate; Android must not add automatic retries. HTTP 404 reading comments marks this feature unavailable on that server.

`Comment`: required `id:uuid`, `user:Author`, `text:string`, `createdAt:date`, `isMine:bool`; optional/null `parentId:uuid`, `replyToName:string`. Use server isMine; deletion also permits the trip owner. Cursor is base64url of `(createdAt ISO date)|id`, opaque to callers; absence/empty starts page one, null/absent nextCursor finishes. This differs from feed cursors.

### Public profiles

| Method and path | Auth | Request | Payload | Retry / idempotency / errors |
|---|---|---|---|---|
| GET `/users/{id}/profile` | Guest | UUID path | `PublicProfile` | GET read, `USER_NOT_FOUND` masks privacy/block. |
| GET `/users/{id}/trips` | Guest | UUID path; optional query `cursor:string`, `limit:int`, `vehicleId:uuid` | `{trips:PublicTrip[],nextCursor?:string}` | GET read; default 200, clamp 1..500; `USER_NOT_FOUND` also for hidden/wrong vehicle. |
| GET `/users/{id}/profile-trips` | Guest | UUID path; optional query `cursor:string`, `limit:int` | `{trips:FeedTrip[],nextCursor?:string}` | GET read; default 20, clamp 1..50; `USER_NOT_FOUND` for missing/inaccessible profile. iOS asks for 20 per page, deduplicates IDs and stops on absent/empty/repeated cursor; a failed page keeps shown cards and ends pagination until refresh. |
| GET `/users/{id}/badges` | Guest | UUID path, no body | `{badges:string[]}` newest earned first | GET read; `USER_NOT_FOUND`; hidden achievements return [] to other viewers. |

Public user routes currently have 60/min limits. `UsersController.profileTrips`/`badges` and `SocialService.getProfileTrips`/`getUserBadges` implement history and badges. History includes live public trips after profile visibility/block checks; map/stats block flags do not suppress these cards. Badges deduplicate stored badge IDs from all live trips (public and private), ordered by trip startDate descending; recentBadges only inspects the last 50 trips and keeps 20 IDs.

`PublicTrip`: required `id:uuid`, `startDate:date`, `distance:number` metres; optional/null `endDate:date`, `duration:int` seconds **driving time**, `region:string`, `previewPolyline:string`; master also returns `title:string|null` (`SocialService.toPublicTrip`), which current iOS PublicTrip ignores. No author/reactions in this light map DTO. For another viewer, closing both mapPublic and statsPublic returns an empty /users/{id}/trips page (`SocialService.getPublicTrips`); owner reads bypass that block gate. Public-trip and public-journey cursors use literal `startDate.toISOString()|lowercase-uuid`, descending `(start_date,id)` order. Feed uses the same format. Percent-encode query values exactly once, including `|`, `:` and `+`; join path and query separately. Invalid cursor dates fall back to now, future dates clamp to now, bad ID half is dropped. Treat a server nextCursor as opaque.

`PublicProfile` required fields: `id:uuid`, `profileLevel:int`, `currentStreak:int`, `bestStreak:int`, `stats:ProfileStats`, `recentBadges:string[]`, `recentTrips:RecentTrip[]`, `followerCount:int`, `followingCount:int`. Optional/null `displayName:string`, `avatarEmoji:string`, `profileBackground:string`, `activeVehicle:ActiveVehicle`, `isFollowing:bool`, `bio:string`, `visibility:Visibility`, `finds:Find[]`, `isPlus:bool`, `avatarFrame:string`, `hasPlus:bool`, `profileXp:int`, `photos:ProfilePhoto[]`, `pinnedTrips:RecentTrip[]`.

- `ProfileStats`: required `totalKm:number` (already km), `tripCount:int`, `regionsCount:int`, `publicTripCount:int`. tripCount includes private trips when counters are visible; don't equate it with publicTripCount.
- `Visibility`: optional `counters:bool`, `stats:bool`, `map:bool`, `achievements:bool`; each absent/null flag defaults true, and a missing whole block means open. Hidden blocks arrive with filtered/zeroed content; use flags to omit the surfaces, not label them as zero activity.
- `ActiveVehicle`: required `id:uuid`, `name:string`, `level:int`, `odometerKm:number`, `avatarEmoji:string`; optional/null `avatarStyle:string`. Not the full owner garage DTO.
- `RecentTrip`: required `id:uuid`, `startDate:date`, `distance:number`; optional `title:string`, `region:string`, `previewPolyline:string`, plus `author:Author`, `description:string`, `endDate:date`, `duration:int`, `maxSpeed:number`, `elevation:number`, `maxAltitude:number`, `drivingTime:int`, `stoppedTime:int`, `isPrivate:bool`, `photoCount:int`, `firstPhotoThumbnail:string`, `vehicle:FeedVehicle`, `reactionCount:int`, `reactionBreakdown:Tally[]`, `myReaction:string`, `badgeIds:string[]`, `commentCount:int`, legacy `averageSpeed:number` **km/h**. Modern server builds feed-shaped rows; legacy rows need profile author fallback, zero counts and duration from positive drivingTime or start/end. These rows do not carry journey/source in this Swift type. RecentTrips is a ten-row preview; profile-trips continues it.
- `Find`: required `secretId:string`, `kind:string`, `foundAt:date`; optional `symbol:string`, `rarity:string`, `first:bool` (default false). Unknown kind renders generic secret, unknown symbol generic, unknown rarity no label. Skip malformed individual finds, not the whole profile. Public finds include verified secrets and all riddles (riddles are not server-verified), at most 50 newest (`secrets/secret-finds.ts`, `profileFinds`); achievements visibility also controls finds.

`hasPlus` signals active entitlement; `isPlus` signals badge visibility. Use hasPlus when present and isPlus only as an old-server fallback for paid cosmetics. Master returns hasPlus, numeric profileXp, photos and pinnedTrips (`SocialService.getProfile`); hiding the badge preserves active paid cosmetics. Profile photos use the completed-gallery filter and pins use the completed/public filter described above.

Private profiles are visible to the owner and permitted followers according to backend access checks; blocked relations mask them as missing. Closing a profile does not retroactively hide already-public feed trips. Vehicle visibility and account globe opt-in remain separate. GET error handling must preserve `USER_NOT_FOUND` even though APIError stores it as unknownServer.

Tests: `PublicJourneyDTOTests`, `PublicJourneyAdapterTests`, `SocialFeedVehicleDecodingTests`, `SocialProfileFindsTests`, `ProfileJournalWireTests`, `ProfileTripsPagerTests`, `ProfileUnavailableTests`, `PublicAchievementsCatalogueTests`.

### Companions

| Method and path | Auth | Request | Payload | Retry / idempotency |
|---|---|---|---|---|
| POST `/companions/list` | Guest | `{tripId:uuid}` | `{items:Companion[],isOwnerView:bool}` | POST read. Owner sees pending/declined; others only accepted. |
| POST `/companions/candidates` | JWT owner | `{tripId:uuid,query?:string,cursor?:string}` | `{items:Candidate[],nextCursor?:string}` | POST read, 30-row page. Query max 60, cursor max 500. |
| POST `/companions/invite` | JWT owner | `{tripId:uuid,accountId:uuid}` | `{companion:{accountId:uuid,status:int}}` | POST; duplicate invite is an error; 10/min. |
| POST `/companions/respond` | JWT invitee | `{tripId:uuid,accept:bool}` | `{status:int}` | POST; state transition from pending, repeat can return invite-not-found. |
| POST `/companions/remove` | JWT owner or self | `{tripId:uuid,accountId:uuid}` | `{ok:bool}` | POST; repeat can return invite-not-found. |
| POST `/companions/invite-preview` | JWT pending invitee | `{tripId:uuid}` | `InvitePreview` | POST read; preview only while invitation is pending. |
| POST `/companions/my-trips` | JWT | `{cursor?:string}` max 500 | `{items:FeedTrip[],nextCursor?:string}` | POST read; accepted trips, feed cursor. |

All other companion routes use 60/min. `Companion`: required `accountId:uuid`, `status:int`; optional/null `displayName:string`, `avatarEmoji:string`. Status raw values: 0 pending, 1 accepted, 2 declined. `Candidate`: required **`id`**:uuid and `profileLevel:int`; optional/null `displayName:string`, `avatarEmoji:string`. Candidates use `id`, while roster/invite/preview use `accountId`. Candidate cursor is base64url `displayName|id`, not the feed format.

`InvitePreview` has exactly these five keys: `driver?:{accountId:uuid,displayName?:string,avatarEmoji?:string}`, required `startDate:date`, `distance:number` metres, optional/null `duration:number` seconds driving time and `region:string`. Driver can be null; duration can be null. There is no driver profileLevel, no route, gallery or trip ID disclosure inside that payload. Omit a missing duration line instead of displaying zero.

Extra codes: `CANNOT_INVITE_SELF`, `COMPANION_ALREADY_INVITED`, `COMPANION_LIMIT_REACHED`, `COMPANION_NOT_FOLLOWED`, `COMPANION_INVITE_NOT_FOUND`, `COMPANION_FORBIDDEN`, `TRIP_NOT_FOUND`. At most ten pending/accepted companions per trip. Only followed eligible accounts can be invited; blocks/ineligible accounts use the not-followed failure. UI mutations retain/restore previous state on failed response, with store-specific handling for stale roster state. `/companions/my-trips` preserves isPrivate=true and zeroes public interaction counts for private trips (`CompanionsService.myTrips`); `/social/trip` uses the ordinary feed-item counts, so UI must also honor isPrivate. Do not offer public discussion/share just because a trip is viewable. These mutations have no separate client idempotency key; a lost success response may become an already-invited/missing-invite error on APIClient's ordinary resend.

`CompanionsDTOTests` includes recorded live-server fixture identifiers. They were inspected for shape, but are intentionally not reproduced here. Its nullable driver/duration, id-vs-accountId and integer status cases are required Android coverage. Related tests: `CompanionsStore{Candidates,Respond,Remove,Persistence,Clear}Tests`, `CompanionsCacheSignOutTests`.

### Clubs waitlist

| Method and path | Auth | Request | Payload | Retry / idempotency |
|---|---|---|---|---|
| POST `/groups/waitlist` | Public in iOS; backend Guest | `{deviceId:uuid}` | `Waitlist` | POST read; cache/poll no faster than 300 seconds. |
| POST `/groups/waitlist/join` | Public in iOS; backend Guest | `{deviceId:uuid,clubKey?:string}` | `Waitlist` | POST; unique device/scope, repeatable join. |
| POST `/groups/waitlist/leave` | Public in iOS; backend Guest | `{deviceId:uuid,clubKey?:string}` | `Waitlist` | POST; repeatable leave. |

30/min. `deviceId` is localUserId, not account ID. Missing/null clubKey addresses Clubs as a whole; allowed club keys `miata`, `vag`, `trucking`, `offroad`, `subaru`, string length 1..32; empty string fails validation. `Waitlist`: required `total:int`, `clubs:object<string,int>`, `joined:bool`, `joinedClubs:string[]`. Apply only the latest started request's response; a late status read must not undo a join. Preserve cache on failure and show failed state. Guest interest is counted without promising a push token already exists; iOS asks notification permission on join, not leave.

## Discoveries

Discoveries are also part of remote catalogs, sync and public profiles, but their reveal is a separate account write. Sources: `Networking/SecretsAPI.swift`, `Services/Discoveries/{CachedSecretCatalog,DiscoveryReveal}.swift`, `Models/Discovery.swift`; backend `secrets/`.

| Method and path | Auth | Request | Payload | Retry / idempotency / errors |
|---|---|---|---|---|
| GET `/secrets/catalog` | Public | optional query `version:string`; omit empty version | `SecretCatalog` | GET; cache, 60/min. |
| POST `/secrets/reveal` | JWT | `{id:string,kind:"secret"\|"riddle",tripId?:uuid}` | `Reveal` | POST + discovery queue; same account/kind/key does not create a second find. `SECRET_NOT_FOUND`; 30/min. |
| POST `/secrets/forget-all` | JWT | `{}` | `{deleted:int}` (iOS ignores via EmptyResponse) | POST; repeatable delete all finds, 5/min. |

`id` is a catalog string or `<type>:<geohash7>`, max 64, **not** the derived local UUID. Reveal optional tripId supplies track evidence. Without uploaded evidence a find can exist but remain unverified. iOS only enqueues discovery reveal when signed in with Cloud Sync on; catalog is public regardless. Failed reveal keeps the locally found item and is retried through the discovery sync operation. Local foundAt/trip identity follows first-find-wins; a remote response enriches, not re-dates it.

`SecretCatalog`: required `version:string`; optional `unchanged:bool`, `salt:string`, `secrets:SecretRecord[]`. Same version returns version + unchanged=true, without salt/list; retain both old salt and old hashes together. Full response requires salt and list. The decoder accepts an empty list as a changed response; CachedSecretCatalog stores its version but treats that empty cache as unusable and reads the bundled fallback. Refresh is at most once per 24 hours when a cache exists. This differs from preserving a prior nonempty catalog, so match the store behavior as well as the DTO. `SecretRecord`: required `id:string`, `hashes:uint32[]`; optional `reach:number` metres (default 0), `symbol:string` (unknown/missing generic), `polygon:bool` (default false). Never turn a hash into an exact secret coordinate; this catalog deliberately does not disclose one. Hash input is UTF-8 salt concatenated with geohash7; take the first four SHA-256 bytes as unsigned big-endian. Frozen SecretCatalogTests vector: salt `tt-secrets-v1` + cell `ubcr4xk` -> 2566105652 (`98f3aa34`).

`Reveal`: required `id:string`, `kind:string`, `verified:bool`; optional/null `title:string`, `story:string`, `symbol:string`, `foundAt:date`, `finders:int`, `first:{displayName?:string,foundAt:date}`, `rarity:string`. Backend currently supplies foundAt/finders/rarity even though iOS tolerates absence. Rarity: `few`, `tens`, `hundreds`, `many`; unknown means no label. Known SealSymbol wire strings: `mountain.2`, `light.beacon.max`, `flag.2.crossed`, `ferry`, `water.waves`, `road.lanes`, `binoculars`, `telescope`, `water.waves.and.arrow.down`, `arrow.up.left.and.arrow.down.right`, `scope`, `triangle`, `map`, `mountain.2.fill`, `moon.stars`, `globe`, `seal`, `seal.komsomolsky`; these are raw values, not Swift case names. A private first-finder profile hides displayName. Riddle title/story/symbol are null server-side (bundle owns text); current backend does not verify riddles like secrets.

Pull `Discovery`: required `secretId:string`, `kind:string`, `foundAt:date`; optional/null `symbol:string`, `verified:bool`, `tripId:uuid`, `latitude:number`, `longitude:number`, `title:string`, `story:string`. Unknown kind is skipped. Verified complete nonzero valid coordinates win; otherwise derive a riddle's center from its geohash7 key. Without a coordinate/trip, enrich an existing row but don't invent a new local location. The local UUID is UUID v5 from namespace `5a3c8b2e-7f10-4c6e-9d21-0e6b3a9f4c11` and UTF-8 `"<kind>:<key>"`; port that function unchanged, not a random ID or the API secret key.

Tests: `SecretCatalogTests`, `CachedSecretCatalogTests`, `DiscoveryIdentityTests`, `DiscoveryRevealTests`, `DiscoveryForgetAllTests`, `PullApplierDiscoveryTests`, `SocialProfileFindsTests`. `TripTrackTests/Fixtures/Secrets-komsomolsky.json` is a catalog fixture; it is not an authenticated account response.

## PRO and admin

### Subscription account attachment

| Method and path | Auth | Request | Payload | Retry / idempotency / errors |
|---|---|---|---|---|
| POST `/plus/attach` | JWT | `{signedTransaction:string}` required by client, max 20,000 | `PlusStatus` | POST + attach queue below, 10/min; `PLUS_BELONGS_TO_ANOTHER`, `PLUS_TRANSACTION_INVALID`, `PLUS_UNAVAILABLE`. |
| POST `/plus/status` | JWT | `{}` | `PlusStatus` | POST read, 30/min; `PLUS_UNAVAILABLE` means the caller may read `plusUntil` from `/auth/me`. |
| POST `/promo/redeem` | JWT | `{"code":string}` required | `{redemption:{durationDays:int,startsAt:date,endsAt:date},plus:PlusStatus}` | POST; send trimmed text as entered. Backend normalizes case, spaces, dashes and look-alike letters. Repeating after a dropped connection is safe. HTTP 200 error codes: `PROMO_CODE_NOT_FOUND`, `PROMO_CODE_ALREADY_REDEEMED`, `PROMO_CODE_INACTIVE`, `PROMO_CODE_EXPIRED`, `PROMO_CODE_USED_UP`, `TOO_MANY_REQUESTS`, `VALIDATION_FAILED`. An already-redeemed response triggers a status refresh. |

`PlusStatus`: required `active:bool`; optional/null `until:date`, `productId:string`, `promoUntil:date`; `isTrial:bool` defaults false when omitted by an old server. Literal fixture from `PlusAttachTests.testAShortAnswerStillDecodes`: `{"active":true}`. Missing signedTransaction is allowed by backend DTO only so service can return its own purchase error; a bare transactionId is not ownership proof and is not supported.

Recognized subscriptions: `com.onezee.TripTrack.pro.yearly`, `.pro.monthly`, legacy `.plus.yearly`, `.plus.monthly` under the same `com.onezee.TripTrack` prefix. Send the original verified StoreKit JWS, never reconstructed claims. Server validates the signed transaction/product/account binding; the subscription row is keyed by originalTransactionId. Refund/revocation overrides expiry; grace period still grants access until graceUntil. `until` is the farthest serving entitlement date across account subscriptions. Turning auto-renew off does not revoke paid time.

PRO attach queue (`PlusAttachQueue.swift`):

- Deduplicate by **transaction.id**, not originalID; each renewal needs its own JWS. Only signed-in clients drain, regardless of Cloud Sync. Persist signatures in the host/app-scoped protected vault, attempts/build history separately. An attach failure never invalidates the iOS StoreKit entitlement or displays a sync error.
- Up to five transport calls per drain; inner waits `min(2^attempt,32)` seconds for zero-based attempt (1, 2, 4, 8 before the next call). The POST's own retry policy also applies. After transient exhaustion, persist notBefore = now + `min(60 * 2^(attempts-1),86400)` seconds: one minute, two, four, etc., capped at a day. Relaunch must preserve debt.
- Only `VALIDATION_FAILED`, `PLUS_BELONGS_TO_ANOTHER`, or raw HTTP 400/409/422 are semantic rejection in the **actual code**. Keep pending, suppress retries for this build, try again on a later build. Drop signature/item after three distinct rejected builds. `PLUS_TRANSACTION_INVALID` is currently an unknown code and follows transient deferral, despite its name.
- Network, 5xx, 404/410, 408/429, 401/403 and `PLUS_UNAVAILABLE` defer; never mark a temporarily missing route delivered. Pending cap 50 drops oldest, sent-key history cap 200. Missing JWS after host change drops orphan bookkeeping.

Android has no in-app purchase/StoreKit flow. It honors full PRO for the signed-in account from `/plus/status`, falling back to `/auth/me.plusUntil` only for `PLUS_UNAVAILABLE`. The cache stores account ID, active, `until`, `promoUntil` and fetch time; sign-in and foreground refresh it, account changes clear it, and expiry closes access locally without a request. Promo redemption refreshes it. Never enqueue fabricated Apple JWS or infer local StoreKit access.

`/plus/forget` exists backend-side but current iOS does not call it (its APIEndpoint has no constant). `/plus/apple` is an Apple server-notification webhook, not a phone endpoint; its non-envelope HTTP contract does not apply to attach/status.

Tests: `PlusAttachTests`, `ProStatusTests`, `SettingsSyncPayloadTests`, `VehicleSyncPayloadTests`; backend `plus/` specs.

### Admin

All five routes exist (`admin/admin.controller.ts`, `AdminController`; `admin/admin.service.ts`, `AdminService`; backend `docs/admin.md`). JWT authentication precedes `AdminGuard`, which checks account.isAdmin in the database on every request and returns `ADMIN_ONLY` through the HTTP-200 error envelope. Default quota 30/min; flag writes 10/min. Do not authorize by showing/hiding the admin entry alone.

| Method and path | Request | Payload | Retry / idempotency |
|---|---|---|---|
| POST `/admin/summary` | `{}` | `AdminSummary` | POST read. |
| POST `/admin/subscriptions` | `{}` | `{items:AdminSubscription[]}` | POST read. |
| POST `/admin/events` | `{}` | `{items:AdminEvent[]}` | POST read. |
| POST `/admin/flags` | `{}` | `{items:AdminFlag[]}` | POST read. |
| POST `/admin/flag` | `{name:string,enabled:bool}` both required | `{items:AdminFlag[]}` | POST, repeatable desired state; name matches `[a-z][a-zA-Z0-9_]{0,63}`, enabled must be bool; 10/min. |

`AdminSummary`: required int `accounts`, `accountsNew7d`, `accountsActive7d`, `proActive`, `proTrial`, `proPaid`, `trips`, `tripsManual`; master also returns `capturedAt:date`. accounts/new/active exclude banned/admin accounts; active means successful authenticated API activity in seven days, including background sync, not foreground usage. PRO counts use Production active/grace subscription rows, excluding attached admins; proPaid = proActive - proTrial. `AdminSubscription`: required `txTail:string`, `product:string`, `status:string` (`active`, `grace`, `expired`, `revoked`, `unattached`), `isTrial:bool`, `autoRenew:bool`, `createdAt:date`, `attached:bool`, `isAdmin:bool`, `trips:int`; optional/null `expiresAt:date`, `language:string`, `accountCreatedAt:date`. `AdminEvent`: required `type:string`, `at:date`; optional/null `txTail:string`, `subtype:string`, `outcome:string`. `AdminFlag`: required `name:string`, optional/null `enabled:bool`, `builtIn:bool`. Effective value is enabled ?? builtIn; if both unknown, disable the switch rather than guessing false.

Subscriptions return up to 200 Production rows newest first, prefixed by up to 50 unattached purchases; events return the latest 50 Production notifications. Flags combine stored values with built-ins discoveries=false, riddleHints=false, fogMetal=true. Any syntactically valid flag name can be written, including names outside these built-ins; setFlag updates only that key on the ios document and clears its server cache. Load failures retain failed UI state; flag writes optimistically switch and roll back on failure. No names/email/full transaction IDs in these responses. The client defines no extra semantic retry for ADMIN_ONLY. Test: `AdminFlagTests`.

## Remote config

Sources: `Services/RemoteConfig/{RemoteConfig,RemoteConfigStore,RemoteCatalogs}.swift`, `Networking/APIEndpoint.swift`. All three routes exist (`app-config/app-config.controller.ts`, `AppConfigController`; `AppConfigService`; `CatalogsService`; backend `docs/app-config.md`), public with 60/min quotas. Config currently always selects the ios document; there is no Android platform selection.

| Method and path | Auth | Request | Payload | Retry / idempotency |
|---|---|---|---|---|
| GET `/app/config` | Public | optional query `version:string`, omit empty | `{version:string,unchanged?:bool,config?:Config}` | GET read; refresh interval six hours when cache exists. |
| GET `/app/catalog/riddles` | Public | optional query `version:string`, omit empty | `{version:string,unchanged?:bool,catalog?:JSON}` | GET read; once per 24 hours per catalog. |
| GET `/app/catalog/vehicles` | Public | Same | Same envelope | GET read; once per 24 hours per catalog. |

`Config`: `flags?:object<string,bool>` (default `{}`), `update?:UpdatePolicy` (default empty), `announcements?:Announcement[]` (default `[]`). Current consumers recognize `discoveries`, `riddleHints`, `fogMetal`. Unknown flags are ignored by consumers; missing flag means use built-in value, not false. Unknown fields do not break decoding.

`UpdatePolicy`: optional/null `recommended:string`, `required:string`, `message:LocalizedText`. Versions compare numeric dot-separated components, padding missing components with zero. Below required means a non-dismissible update card; recording/app still works. Below recommended means dismissible card. This is a UI notice, not server account denial.

`Announcement`: required `id:string`, `placement:"feed"|"me"`, `title:LocalizedText`, `body:LocalizedText`; optional `action:{label:LocalizedText,url:string}`, `startsAt:date`, `endsAt:date`, `minVersion:string`, `maxVersion:string`. `LocalizedText` is a direct map language->string, with nonempty `en` required, not `{values:...}`. Use requested language then en fallback. Bounds are inclusive; skip malformed individual announcements/unknown placements instead of failing the entire config.

Full changed responses require config/catalog content. `unchanged:true` retains cached content and advances last-refresh time. Failure/missing content retains cache/bundle and does not advance successful-refresh time. Cache files/version swap atomically. Catalog download persists arbitrary JSON; the owning catalog decoder decides validity. New catalog takes effect on the next launch.

`catalog` for vehicles is the older `VehicleCatalog` shape: required `{version:int,makes:[{name:string,aliases?:string[],models:[{name:string,bodies:string[],aliases?:string[]}]}]}`. It is **not** the new schema-2 CarCatalog with generation/variant IDs, which is currently bundled only. Riddles catalog has required `riddles:[{id:string,t:string,c:[number,number],n:string,r?:string}]`; `c` is latitude, longitude, `n` is name, `r` is regionId, and `t` follows RiddleType (`pass`, `border`, `ferry`, `lighthouse`, `dam`, `bridge`, `viewpoint`, `observatory`, `seaRoad`, `extreme`, `centre`, `tripoint`). A bad downloaded catalog falls back to the bundle. Unknown enum handling belongs to that catalog, not the arbitrary-JSON transport.

Backend `app-config.document.ts.sanitize` keeps boolean flags with the same name pattern as AdminFlagRequestDto, numeric versions with 1..4 components, and at most the first 20 announcements with unique valid IDs (`[a-z0-9][a-z0-9_.-]{0,63}`). Localized texts require en and keep supported languages only, trimming/truncating each to 2,000 characters. Invalid dates/actions/fields are omitted; action URLs must start https:// or triptrack:// with no whitespace. Config caches for 60 seconds per platform and falls back to the previous document or defaults on DB read failure. Its version is the first 16 hex characters of SHA-256 of sanitized JSON. Catalogs load once per process, versioned by the first 16 hex characters of SHA-256 of raw file bytes; unknown/unreadable catalog raises `CATALOG_NOT_FOUND` in an HTTP-200 error envelope despite the error class's 404 httpStatus. The client uses shared GET errors and silent fallback. Nearby test coverage: `CachedSecretCatalogTests`, `RiddleCatalogTests`, `VehicleCatalogTests`; no dedicated RemoteConfig test file was present.

## Push and notification inbox

Sources: `PushNotificationManager.swift`, `Models/Social/NotificationsDTOs.swift`, `NotificationsInboxStore.swift`, `Views/Notifications/NotificationPreferencesView.swift`, `Views/Profile/NotificationSwitches.swift`; backend `auth/dto/{device-token,notification-prefs}.dto.ts`, `notifications/`.

| Method and path | Auth | Request | Payload | Retry / idempotency |
|---|---|---|---|---|
| POST `/auth/device-token` | JWT | `{apnsToken:string,localUserId:uuid-v4,environment:"sandbox"\|"production"}` all required | Empty | POST, repeatable device token replacement; 30/min. APNs text max 256. |
| POST `/auth/notification-prefs/get` | JWT | `{}` | `NotificationPrefs` | POST read, 60/min. |
| POST `/auth/notification-prefs/update` | JWT | `PrefsPatch` | `NotificationPrefs` | POST, replay-safe field patch; 30/min. |
| POST `/notifications/feed` | JWT | `{limit?:int,cursor?:date-string}`; limit 1..100 | `{items:Notification[],nextCursor?:string}` | POST read, 60/min. |
| POST `/notifications/unread-count` | JWT | `{}` | `{count:int}` | POST read, 60/min. |
| POST `/notifications/mark-read` | JWT | `{ids?:uuid-v4[]}` max 200 | Empty | POST, repeatable state write; 60/min. Missing ids means all, **[] is no-op**; do not send null (current service dereferences ids.length). |

APNs token is lowercase hex from bytes. Debug uses sandbox, release production. Register after sign-in, token refresh and new permission grant; collapse concurrent registrations. Remember last successfully synced token, clear that marker on account sign-out so the new account re-registers. Failed sync leaves token pending for the next natural registration attempt. Backend binds by localUserId and owning account, not arbitrary token claim; absent/mismatched device is currently a silent empty success, so login/device creation must precede this call. Backend has no FCM token field.

`PrefsPatch`: optional bool `notifyReactions`, `notifyFollows`, `notifyComments`, `notifyWeeklyRecap`, `notifyCompanions`, `notifyNewAccounts`. Absent means no change; send false explicitly to disable. iOS omits nil; explicit null passes optional DTO validation but is not a supported bool patch and can fail persistence. Non-admin notifyNewAccounts is ignored. `NotificationPrefs`: required bool `notifyReactions`, `notifyFollows`, `notifyWeeklyRecap`; optional bool `notifyComments`, `notifyCompanions`, `isAdmin`, `notifyNewAccounts` in iOS (backend returns all). Missing comments/companions defaults true; absent isAdmin does not authorize admin. Load/save failures keep a recoverable toggle UI; there is no separate automatic mutation queue.

`Notification`: required `id:uuid`, `kind:string`, `isRead:bool`, `createdAt:date`; optional/null `tripId:uuid`, `tripTitle:string`, `emoji:string`, `commentId:uuid`, `commentText:string`, `isFollowing:bool`, `actor:Author`, `badgeId:string`. Recognized kind values `reaction`, `follow`, `comment`, `companion_invite`, `companion_accepted`, `achievement`; preserve unknown raw kind and render a generic row. Missing actor is legitimate for system notifications. Pending companion invite must not expose the trip's title/route. Achievement badgeId localizes on the client.

Inbox cursor is a timestamp, unlike feed's date|UUID and comments' base64url cursor. Preserve it as returned; do not append UUID yourself. Mark-read uses batches of at most 200; empty batches must never mark everything. The inbox store optimistically updates and retains/reconciles failures, while fresh feed/count requests recover server state.

APNs payload is a separate envelope from HTTP: backend dispatcher adds `aps` with a category plus top-level app data such as `tripId` and `actorAccountId`. Categories and extra fields are specified below; Android needs the same data semantics through FCM, not Apple's aps transport.

### Push payload categories

Backend `infra/push/push.service.ts` sends title/body through `aps.alert`, default sound and `aps.category`, with a 24-hour delivery TTL. App extras live outside aps. Current categories/extra keys:

| Category | Extras | iOS receive behavior |
|---|---|---|
| `REACTION` | `actorAccountId:uuid`, `tripId:uuid`, `emoji:string` | Banner/sound in foreground, refresh inbox; tap opens trip. |
| `COMMENT` | `actorAccountId:uuid`, `tripId:uuid` | Same; no commentId in current push payload. |
| `FOLLOW` | `actorAccountId:uuid` | Banner/sound, refresh inbox; tap opens feed. |
| `COMPANION_INVITE` | `tripId:uuid` | Banner/sound, refresh inbox; tap feed/invitation surface, never private trip detail before acceptance. |
| `COMPANION_ACCEPTED` | `tripId:uuid` | Banner/sound, refresh inbox; tap owner's trip. |
| `ACHIEVEMENT` | `tripId:uuid`, `badgeId:string` | Backend can send it; current iOS generic delegate has no matching explicit tap/banner/inbox-refresh branch. Inbox still supports achievement rows. |
| `WEEKLY_RECAP` | none in current dispatch | Backend can send it; current iOS has no explicit tap/banner branch. |
| `NEW_ACCOUNT` | `accountId:uuid`, `totalAccounts:int` | Admin banner/sound; no inbox refresh or explicit detail tap branch. |
| `PRO_EVENT` | `kind:string` (`trial`, `paid`, `converted`, `renewed`, `autoRenewOff`, `autoRenewOn`, `billingFailed`, `expired`, `refunded`, `tip`) | Master sends Production purchase events to admins with notifyNewAccounts=true, without an inbox row (`plus/admin-purchase-push.service.ts`, `AdminPurchasePushService.notifyAdmins`); current iOS has no explicit category branch. |

Push is a hint to fetch authoritative data, never a substitute for authenticated access. There is no `type` key in these current backend dispatches; category is the routing discriminator. Keep unknown categories harmless. Android must not turn a pending invite into full-trip access by routing directly to its tripId.

## Opt-in usage counters

`Services/ProductAnalytics.swift` makes two additional direct requests outside APIClient. Both exist in `usage/usage.controller.ts` (`UsageController`) and `usage/usage.service.ts` (`UsageService`), with 30/min per-route quotas and an 8 KiB JSON limit. No JWT/account ID, Cloud Sync requirement, gzip or JSON-RPC envelope. Success is strictly HTTP 204 with no body; every other status/error leaves debt for foreground/network restoration. Changes coalesce for two seconds, one request in flight; no immediate retry ladder.

| Method and path | Auth | Request | Response / idempotency |
|---|---|---|---|
| POST `/usage/snapshot` | Public | `UsageSnapshot` | HTTP 204. Replace counters by token + revision, never add them again on retry. |
| POST `/usage/erase` | Public | `{token:string}` required | HTTP 204. Repeatable deletion, tombstone prevents delayed snapshots resurrecting consent data. |

`UsageSnapshot`: required `schema:int` (1), `token:string` (random lowercase UUID per consent period), `revision:int`, `cohortDay:string`, `observedDay:string` (UTC YYYY-MM-DD), `hadTrips:bool`, `starts:int`, `saves:int`, `saveFailures:int`, `atlasOpens:int`, `fastSaves:int`, `mediumSaves:int`, `slowSaves:int`; optional `firstSaveDay:string`, `secondSaveDay:string`, `proOffers:int`, `proBuyTaps:int`, `proPurchases:int`. No account/trip/device IDs, locations, routes or screen identities. These counters are independent of login. Erases go before snapshots; a consent change during an in-flight snapshot schedules erase afterward. Keep original cohort/milestone dates but refresh observedDay when delivering offline counts. Backend `usage/usage.dto.ts` validates token as UUID v4, schema exactly 1, revision 1..2,000,000,000, and every counter 0..1,000,000. Unknown fields reject (whitelist + forbidNonWhitelisted). Days must be real calendar days; cohort <= observed, observed within today-30 days through today+1 day UTC; saves + saveFailures <= starts; fast + medium + slow = saves. firstSaveDay is required iff saves >= 1, secondSaveDay iff saves >= 2; each lies between cohort and observed, with second >= first. PRO purchases <= buy taps <= offers (missing counts use zero). `UsageService.snapshot` hashes the lowercase token with SHA-256 and replaces counters only for a strictly newer revision on a non-revoked row; erase stores a revoked tombstone and clears counters. Its daily expire job removes rows/tombstones inactive for 90 days. Validation/throttle/parser failures are outside JSON-RPC route handling; they do not return the success 204, so iOS retains debt.

## What Android needs that the backend lacks

The remaining gaps below are verified against master. New routes and fixes are proposals; none were implemented in this task. Keep existing iOS keys/units/envelopes intact and add behavior on a backend branch with cross-device fixtures before enabling Android writes.

### Google sign-in and identity linking

There is no Google verifier/login endpoint or provider-neutral identity table on master: `src/modules/auth/auth.controller.ts` (`AuthController`) exposes only the Apple login; `src/modules/auth/auth.service.ts` (`AuthService.login`) verifies through AppleAuthService and finds accounts by `src/modules/auth/entities/account.entity.ts` (`AccountEntity.appleSubject`). `src/modules/auth/auth.module.ts` (`AuthModule`) registers no Google verifier/identity service. Add public `POST /auth/google/login` with required `identityToken:string` (raw Google ID token), `localUserId:uuid`, `nonce:string`, optional `deviceName:string`, `platform:"android"`, `appVersion:string`, `buildNumber:string`. Return the existing LoginResponse envelope and use the same refresh/session/device flow. Verify signature, issuer, configured server audience, expiry and nonce on the backend; ignore client-decoded identity claims. Proposed errors: `INVALID_GOOGLE_TOKEN`, `IDENTITY_ALREADY_LINKED`, existing throttle/auth codes.

Store unique `(provider,subject)` identities referencing the same account; migrate existing appleSubject into this identity model while preserving account UUID. Do not auto-merge by email, including Apple's relay address. Add JWT-protected `POST /auth/identities/link/google` accepting a freshly verified token + nonce; require fresh proof of the existing account before linking and reject an identity already owned elsewhere. Test that an Apple user who links Google sees the same trips/garage/PRO on Android, and that signing in with an unrelated Google identity cannot acquire that account. Product must offer Apple sign-in directly even when Google is unlinked.

### Apple web flow and redirect

The existing `/auth/login` verifier already accepts raw web JWTs and base64 iOS JWTs, but allowed audiences come from configured Apple bundle/service IDs. No backend Apple browser start, callback or Android return exchange exists: `src/modules/auth/auth.controller.ts` (`AuthController.login`) takes a token directly, and `src/modules/auth/services/apple-auth.service.ts` (`AppleAuthService.verifyIdentityToken`) verifies it without an authorization-code exchange. Backend `docs/web-login.md` describes the existing website login via Apple JS and the same /auth/login route; that working web login is separate from the proposed Android return flow. Register an Apple Services ID and a fixed HTTPS return URL on the public API, e.g. proposed `https://api.trip-track.app/auth/apple/callback`; configure the Services ID with Apple's application grouping so an existing native Apple identity resolves to its original account, and explicitly test subject/account continuity. Do not assume equal email means equal Apple user.

Proposed flow:

1. Android opens a Custom Tab to public `GET /auth/apple/start` with `localUserId`, one allowlisted Android return target, and an S256 code challenge. Server creates a short-lived single-use state/nonce transaction and sends the hashed nonce in Apple's authorization request.
2. Public `POST /auth/apple/callback` accepts Apple's form-urlencoded `code`, `id_token`, `state` (or `error`, optional first-authorization `user`). Validate state, code/token, audience and nonce; exchange authorization code server-side. Never accept a caller-supplied arbitrary redirect URI or put refresh/access tokens in a URL.
3. Redirect only to a verified Android App Link, proposed `https://trip-track.app/auth/android-return?code=<single-use-code>&state=<state>`. Host must publish Android association data for release/debug choices explicitly. The returned code is opaque, short-lived and bound to the original challenge/local device.
4. Android calls public JSON `POST /auth/apple/exchange` with `{code,codeVerifier,localUserId}` and receives LoginResponse. Same device registration/account UUID/PRO semantics as native login. Reject reused/expired/mismatched exchange codes with a dedicated typed error; a lost exchange response needs bounded, same-transaction replay semantics so it does not strand a successful login.

This flow needs no embedded Apple client secret and does not reuse a previous native raw nonce. Keep `/auth/login` working for iOS. The exact proposed URLs are public route designs, not evidence those routes exist now.

### FCM alongside APNs

Master only supports APNs: `src/modules/auth/dto/device-token.dto.ts` (`DeviceTokenRequestDto`) accepts apnsToken/environment, `src/modules/auth/entities/device.entity.ts` (`DeviceEntity`) stores APNs fields, and `src/infra/push/push.service.ts` (`PushService.sendToAccount`/`providerFor`) dispatches only through apn.Provider. `src/modules/auth/auth.service.ts` (`AuthService.registerDeviceToken`) silently returns for a missing/foreign localUserId; `AuthController` exposes no token-unregistration route.

Extend device registration additively or add `POST /auth/device-token/v2`. Proposed authenticated body: `{localUserId:uuid,provider:"apns"|"fcm",token:string,platform:"ios"|"android",environment?:"sandbox"|"production",appVersion?:string,buildNumber?:string}`; environment required for APNs, inapplicable to FCM. Keep the old apnsToken endpoint for iOS. Validate bounded opaque FCM token strings, never apply APNs hex/256-char assumptions to FCM.

Persist provider/platform per installation, preserve multiple devices per account, authenticate ownership, and return explicit `DEVICE_NOT_REGISTERED`/`DEVICE_NOT_OWNED` instead of today's silent success. Add authenticated token unregistration for logout/permission withdrawal, with repeatable missing-token success. On provider invalid-token response retire that installation token; do not delete other devices. Send the same categories and extras through FCM data, with appropriate notification title/body and no private invite geometry. Account notification prefs apply to both providers. Test APNs and FCM delivery to one account, token rotation, reinstall, logout/relogin, and a pending invitation tap.

### Platform, version and user-agent fields

Evidence: `src/modules/auth/dto/login.dto.ts` (`LoginRequestDto`), `src/modules/auth/dto/device-token.dto.ts` (`DeviceTokenRequestDto`) and `src/modules/auth/entities/device.entity.ts` (`DeviceEntity`) define no platform/appVersion/buildNumber/osVersion fields. Login/device rows currently have localUserId/deviceName/APNs environment; iOS APIClient sends no explicit platform/app-version headers. Add optional `platform`, `appVersion`, `buildNumber`, `osVersion` to login/device metadata, accepting omission from old iOS. Android should send an app User-Agent such as `TripTrack-Android/<version> (<build>; Android <major>)`; do not use a user-customized device name as an analytics dimension or log credentials. Treat every field as untrusted metadata, not entitlement/auth proof.

Add platform-aware config update policies and action/store links, e.g. optional `/app/config?platform=android&appVersion=...`, while retaining the current ios config for old clients. `src/modules/app-config/app-config.controller.ts` (`AppConfigController.config`) hardcodes service.config('ios', version), and `src/modules/admin/admin.service.ts` (`AdminService.setFlag`) edits only id=ios; Android policies and links are not selected today. Android and iOS version numbering need not share a required-minimum threshold. Explicitly return/record the applied platform so caches cannot mix policies across builds.

### Cross-device data-loss fixes before write parity

| Gap verified on master | Evidence (file and symbol) | Concrete proposal and acceptance case |
|---|---|---|
| Absent manual `trackPoints` erases server route | `src/modules/trips/trips.service.ts`, `TripsService.upsert`: accepted updates unconditionally delete TrackPointEntity rows before insertTrackPoints, which returns for absent/null/empty input. | Delete/reinsert only for a non-null array. Omitted/null preserves; [] clears. Test a summary-imported manual trip, title-only update, then full detail on another device retaining identical points. Keep home-trimmed explicit [] destructive. |
| Pull omits checkpoints/segments, extended metrics and photo capture metadata | `src/modules/sync/sync.service.ts`, `SyncService.serializeTrip` omits checkpoints/segments/maxAltitude/drivingTime/stoppedTime; serializePhoto omits capturedAt/exifLatitude/exifLongitude. `src/modules/trips/trips.service.ts`, `TripsService.serializeTrip` returns checkpoints/segments when loaded and extended metrics, but no photos. | Return missing metadata or advertise hydration and fetch detail for imported/changed trips. Return relation keys only when loaded. Tests must recover checkpoints, segments and extended metrics on a fresh phone. Return capture metadata on owned photo reads after privacy rules; preserve more precise local values. |
| iOS missing `photos` deletes synced local photo rows (client mismatch) | Backend `src/modules/trips/trips.service.ts`, `TripsService.serializeTrip`, and `src/modules/sync/sync.service.ts`, `SyncService.serializeTrip`, omit photos. iOS `TripTrack/Persistence/TripRepository.swift`, `CoreDataTripRepository.applyRemoteTrip`, builds serverIds from p.photos ?? []. | Coordinate appliers: omission preserves; [] is authoritative empty only for a response claiming the full gallery. Add pull/detail fixtures with pending/local-only/remote-only photos, including companion photos on an owned trip. This needs a client fix even if backend photo rows remain intact. |
| Conflict metadata discarded by JsonRpcExceptionFilter | `src/common/json-rpc/json-rpc.exception-filter.ts`, `JsonRpcExceptionFilter.responseFor`, returns only status/code/message for AppError; batch metadata remains in `src/modules/sync/sync.service.ts`, `SyncService.push`. | Include optional serverVersion/serverLastModifiedAt in the existing error envelope. Keep Android tolerant of absence; test batch and per-trip paths. |
| Journey nil fields are omitted by iOS although backend requires null to clear (client mismatch) | `src/modules/journeys/journeys.service.ts`, `JourneysService.mapDtoToEntity`, preserves undefined and clears explicit null title/endDate/coverPhotoId. iOS `TripTrack/Models/Sync/JourneySyncPayload.swift`, `JourneySyncPayload`, uses synthesized Codable, omitting nil. | Coordinate encoders to send explicit null on clearing, preserving omission for unknown/unmodified. Test clearing an end/cover on one phone and list/pull on the other. Backend already supports the required clear operation. |
| Old-client missing manualOdometerKm clears backend value | `src/modules/vehicles/vehicles.service.ts`, `VehiclesService.mapDtoToEntity`, always assigns dto.manualOdometerKm ?? null. | Make omission preserve; explicit null clears. Keep Android presence flags and explicit current-value/null writes. Test omitted, zero and null separately. |
| Sync cursor captured after unbounded parallel reads | `src/modules/sync/sync.service.ts`, `SyncService.pull`, awaits independent Promise.all reads and then creates serverTime; pullTrips/pullVehicles/pullPhotos/pullJourneys have no upper time bound, snapshot or pagination. | Add a consistent upper watermark/snapshot and bounded pagination; pages share a watermark and the cursor advances after the entire snapshot applies. Concurrent mutations can otherwise land behind the end-time cursor. Do not invent pagination for the current route. |

The geometry fixes are prerequisites for safe metadata-only/manual-trip and fresh-device parity.

### Capability discovery

`src/modules/app-config/app-config.controller.ts` (`AppConfigController`) exposes config/catalog only, with version/unchanged responses from `AppConfigService`/`CatalogsService`; no capability schema or endpoint exists. An additive public `/app/capabilities` could advertise Android auth/push/platform support as it ships. Missing capabilities must preserve cached/local state. Existing profile photos, pins, garage fields, history/badges, hasPlus/profileXp, config/admin and usage already work on this master and need no replacement endpoints.

### Android PRO and remaining transport limits

`/plus/status` exists and can honor iPhone purchases without Android billing (`src/modules/plus/plus.controller.ts`, `PlusController.status`; `src/modules/plus/plus.service.ts`, `PlusService.status`). Android caching is client work, not a missing backend purchase API. Define an Android account entitlement cache keyed by account/API host with `active`, `until`, `productId`, `isTrial` and successful-fetch time. Refresh on sign-in/foreground and never derive access from public isPlus or a fabricated StoreKit transaction. Agree the offline expiry/revalidation policy with the product owner; no new billing provider is required for launch. `src/modules/plus/plus-status.ts` (`PlusStatusView`) has no entitlement revision/serverTime; add one if deterministic cache ordering is needed. Keep paid selections separate from their current display eligibility.

Comments have no idempotency key and share/report can duplicate: `src/modules/social/dto/comment.dto.ts` (`CommentCreateRequestDto`), `share.dto.ts` (`ShareRequestDto`), `block.dto.ts` (`ReportRequestDto`) define no client request ID; `src/modules/social/social.service.ts` (`SocialService.comment`/`share`/`report`) creates new rows/codes per invocation. Large single-trip uploads cannot be resumed: `src/modules/trips/dto/trip-upsert.dto.ts` (`TripUpsertRequestDto`) and `src/modules/trips/trips.controller.ts` (`TripsController`) expose whole-trip upsert, with no chunk/upload-session fields or routes. Proposed additive clientRequestId support for create-comment/share/report should return the original outcome on same-account replay; until then preserve comment Once behavior and document uncertain delivery. For oversized trips, introduce a versioned resumable upload protocol rather than chopping arrays inside the existing replace-all upsert. Backend webhook and web-only/manual routing routes need no Android calls just because they exist; match the iPhone behavior unless a recorded Android divergence changes the feature design.
