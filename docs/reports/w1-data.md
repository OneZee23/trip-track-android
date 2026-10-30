# w1-data

`core:data` has the Room database for Core Data model v24 and the repository rules from the
iPhone store code. 224 Robolectric tests pass on an in-memory database. Branch `w1-data`, small
commits, not pushed.

The iOS working copy has `.xccurrentversion` pointing at v23. It is an uncommitted xcodegen
leftover (the "generate twice" trap in the iOS CLAUDE.md). HEAD `a155859d` says v24, and v24 is
what was ported.

## What was ported

| Android (`core/data/src/main/kotlin/com/onezee/triptrack/data/`) | iOS source |
|---|---|
| `db/entity/*` (15 entities), `db/TripTrackDatabase.kt`, `db/Converters.kt` | `TripTrack v24.xcdatamodel/contents`, `PersistenceController.swift` |
| `db/dao/*` (8 DAOs), `db/dao/WorldSql.kt` | the fetch requests of `TripRepository.swift`, `PlaceStore.swift`, `DiscoveryStore.swift`, `RevealedLayerStore.swift`, `TerritoryManager.swift`, `RoadCollectionManager.swift`, `SettingsManager.swift`, `VehiclePhotoStore.swift`, `SyncCoordinator.swift`; `TripConfirmation.notDraftPredicate`, `completedTripPredicate` |
| `TripRepository.kt`, `RoomTripRepository.kt` | `TripRepository.swift` (protocol and `CoreDataTripRepository`), `TripEntity+TrackPoints.swift`, `TripManager.setConfirmation/discardDraft/saveGeocodeCache/cachedLocalities`, `PhotoStorageService.pendingUploads/markUploaded/pendingOriginalUploads` |
| `PlaceStore.kt` | `PlaceStore.swift` |
| `DiscoveryStore.kt` | `Discoveries/DiscoveryStore.swift` |
| `VehicleStore.kt` | the vehicle half of `SettingsManager.swift`, `VehicleSyncPayload.applyGarageFields`, the synced marking in `APISyncTransport.uploadVehicle` |
| `VehiclePhotoStore.kt` | `VehiclePhotoStore.swift` |
| `SettingsStore.kt` | `SettingsManager.loadSettings/saveSettings`, `PersistenceController.migrateUserIdIfNeeded` |
| `MapStores.kt` (`TerritoryStore`, `RoadStore`, `RevealedTileStore`) | storage halves of `TerritoryManager.swift`, `RoadCollectionManager.swift`, `RevealedLayerStore.swift` |
| `LocalDataWipe.kt` | `LocalDataWipe.swift` |
| `PendingSyncRecovery.kt` | `SyncCoordinator.recoverPendingEntities`, `pendingJourneyOperations`, `pendingVehiclePhotoOperations` |
| `files/PhotoFiles.kt` | the file half of `PhotoStorageService.swift` and `VehiclePhotoStore.swift` |
| `JsonColumns.kt`, `ModelRules.kt` | the repository's JSON column coders; `Journey.contains`, `TripOrigin.earnsRewards`, `VehicleLevelSystem`, `VehicleFinish.parse`, `VehicleModTags`, `Drive2Link`, `VehicleType.init(storage:)`/`hasPlate` |
| `sync/SyncRequest.kt`, `DataEnvironment.kt`, `DataFlags.kt` | `SyncOperation.EntityType/Action`; the reads of `SettingsManager.shared`, `HomeSettings` and `UserDefaults` |

Every store is an interface with a Room implementation (`RoomTripRepository`, `RoomPlaceStore`,
`RoomDiscoveryStore` and so on). The rules below each have a test.

- Draft and soft-deleted trips stay out of every world read (lists, stats, journeys, the map, the
  places backfill, fog previews, territory, library size). One SQL constant carries the predicate.
- A local edit stamps `lastModifiedAt` and sets `pendingUpload` only when it can drain
  (`cloudSyncEnabled || !isPrivate`). Checkpoint, segment and energy-mode edits count as trip
  edits. Garage writers flag the car, and the writers that iOS stamps also move the time.
- A pull never overwrites an unsent edit or resurrects a pending delete. This covers the trip
  version guard, the per-field `hasLocalEdits` gates (source, energy mode, breaks, every vehicle
  gate), journeys skipping `pendingUpload`/`pendingDelete`, and settings whose progress only goes
  up while preferences follow `lastModifiedAt`. An unsent vehicle edit keeps its flag and time.
- An absent key and an explicit empty value stay distinct for track points, checkpoints,
  segments, breaks, transfer, `manualOdometerKm`, `soldAt` and the seven garage columns. The photo
  asymmetry described in `docs/api-contract.md` is kept as iOS has it.
- A mirrored trip is soft-deleted and an unsent one is deleted at once; a tombstone deletes only a
  mirrored trip. All three hard paths remove place passes and photo files, and the cascade removes
  points, photos and checkpoints.
- Deleting a checkpoint removes its segments, dangling segments are dropped on read, and the cap
  is 50.
- Relationless rows are deleted by name: place passes on every trip deletion path, car photos with
  their car, all of them in the account wipe. The wipe keeps the settings row and reopens the fog
  rebuild latch.
- Odometers are derived from trips with transfers excluded; car levels count recorded trips only.
- Latches record finished work only (territory backfill, the archive and map migrations, user id
  stamping).

## Tests

`core/data/src/test/kotlin/com/onezee/triptrack/data/`, Robolectric (`sdk=35`) on
`Room.inMemoryDatabaseBuilder`. Inputs and expected values follow the iOS cases. Where an iOS test
also drives a manager or HTTP, its store half was ported.

| Android class | iOS tests |
|---|---|
| `TripRepositoryTrackPointsReplaceTests` (3) | `TripRepositoryTrackPointsReplaceTests` |
| `CheckpointSyncFlagTests` (5) | `CheckpointSyncFlagTests` |
| `TripSegmentStoreTests` (16) | `TripSegmentStoreTests`, `TripSegmentSyncTests` |
| `TombstoneGuardTests` (11) | `TombstoneGuardTests`, `TripHardDeleteFilesTests` |
| `DraftIsolationTests` (10) | `DraftIsolationTests`, `TripConfirmationSchemaTests` |
| `RemoteSettingsMergeTests` (12) | `RemoteSettingsMergeTests`, `SettingsUnitWireTests` |
| `RemoteVehicleMergeTests` (19) | `RemoteVehicleMergeTests`, `VehicleEnergyWireTests`, `VehicleDashboardUnitsWireTests`, `GarageDataTests`, `VehicleStateTests` |
| `JourneyRepositoryTests` (17) | `JourneyRepositoryTests`, `JourneySyncConflictTests`, `JourneySyncRecoveryTests` |
| `PlaceStoreTests` (16) | `PlaceStoreTests` |
| `DiscoveryStoreTests` (18) | `DiscoveryStoreTests` |
| `VehicleOdometerTests` (16) | `VehicleOdometerTests`, `VehicleAssignmentTests`, `ManualTripRewardsTests`, `TransferAndOdometerTests` |
| `TripPullRulesTests` (25) | `RecordingBreaksPersistenceTests`, `TripOriginTests`, `VehicleEnergyWireTests`, `ManualTripSyncImportTests`, `PhotoMetadataTests`, `CompanionsCachePersistenceTests`, `CompanionsCacheSignOutTests` |
| `TripRepositoryEditTests` (20) | rules from the repository comments, with inputs from `PrivacyFlowE2ETests` and `SyncHealTests` |
| `VehicleStoreTests` (16) | `VehicleStateTests`, `VehicleSyncConflictTests`, `VehicleEnergyWireTests`, `GarageDataTests` |
| `LocalDataWipeTests` (7) | `LocalDataWipe.swift`, `BackfillLatchTests` (territory), `RevealedLayerStoreTests` (latch key, previews) |
| `SchemaContractTests` (13) | the v24 model read back through `PRAGMA`, `TrackPointOrderTests`, `GarageDataTests` (Drive2Link, tags) |

Commands, JDK 17 (`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`):

| Command | Result |
|---|---|
| `./gradlew :core:data:compileDebugKotlin` (first run, online) | PASS; downloaded Room 2.8.5, KSP 2.3.12 and the Room Gradle plugin |
| `./gradlew --no-daemon :core:data:testDebugUnitTest --tests '...TripRepositoryTrackPointsReplaceTests'` (online) | PASS, 3 tests; downloaded Robolectric 4.17 and its SDK 35 jar |
| `./gradlew --offline --no-daemon :core:data:test :core:data:testDebugUnitTest --rerun` | PASS, `BUILD SUCCESSFUL in 14s`, 16 suites, 224 tests, 0 failures, 0 errors, 0 skipped |

The builds use `--no-daemon` on purpose. Once, Gradle picked up a compatible idle daemon that
another agent's sandbox had started, and it could not write this checkout's `.gradle`
(`fileHashes.lock (Operation not permitted)`). A single-use daemon avoids that and leaves the
memory settings alone. On AGP 9 `:core:data:test` runs only the debug unit tests. `core:data`
compiles without Kotlin or KSP warnings.

## Libraries

Added to `gradle/libs.versions.toml` and used only by `core:data`: the `androidx.room` plugin, which
declares the schema directory as a task output so a build-cache hit still writes the schema;
`androidx.room:room-ktx` for `withTransaction`; `kotlinx-coroutines-test`. Room runtime and compiler,
KSP and Robolectric were already in the catalog.

## Divergences

`docs/divergences.md` has rows for the store baseline, column types, hidden keys, index names,
commit batching, store notifications, the photo write order, the model rules kept in `core:data`
and geohash injection. Two more:

- Reading a trip does not set `TrackPoint.recordingSegmentIndex`, which iOS `Trip.init` does. It is
  the wave 0 divergence again and waits for `RecordingBreaks.annotate` from `core:logic`.
- Changing the settings id deletes and reinserts the row, since the id is the primary key. One row
  remains either way.

`Trip`, `TrackPoint` and `TripPhoto` needed no new fields. The bookkeeping columns are read from the
entities, the way iOS reads them from managed objects. Tag length counts grapheme clusters with
`android.icu.text.BreakIterator`, as Swift's `prefix` does; the JVM iterator would split an emoji
from its skin tone.

## Left for other waves

- Building `TripSyncPayload` from a trip (`core:sync`) needs the movement split and privacy-zone
  trimming. `fetchTripDetail`, `fetchEntity` and `photoRows` give it the data.
- `PullApplier`, the queue, the transport and the `ManualTripTrackLoader` checks (`core:sync`).
- The recording writes of `TripManager` (start, batched points, finish, orphan recovery, manual
  trip, geocoded titles) for `core:location`. The DAOs already have the queries.
- Manager logic: place matching and suggestions, `JourneyManager`, the fog build, merge and latch
  of `RevealedLayerStore`, road matching, the gamification backfill, vehicle selection and the
  preferences side of units, `applyRemoteProfileProgress` (it needs `LevelSystem`).
- Flow queries for the UI, the thumbnail cache, and excluding `TripPhotos`/`VehiclePhotos` from
  backup in the app's backup rules.
- Once `core:logic` lands: switch `JsonColumns.normalizedBreaks` and the `GeohashOf` callers to it
  and move the `ModelRules.kt` helpers to their owners.
