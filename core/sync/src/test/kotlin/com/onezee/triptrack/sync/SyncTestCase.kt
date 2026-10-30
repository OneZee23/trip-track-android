package com.onezee.triptrack.sync

import android.content.Context
import com.onezee.triptrack.data.*
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.files.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// iOS: PersistenceController(inMemory:) + MockURLProtocol; Android uses real Room and HTTP.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
abstract class SyncTestCase {
    protected lateinit var db: TripTrackDatabase
    protected lateinit var repo: RoomTripRepository
    protected lateinit var vehicles: RoomVehicleStore
    protected lateinit var vehiclePhotos: RoomVehiclePhotoStore
    protected lateinit var tripFiles: DirectoryTripPhotoFiles
    protected lateinit var vehicleFiles: DirectoryVehiclePhotoFiles
    protected lateinit var server: MockWebServer
    protected lateinit var client: ApiClient
    protected lateinit var directory: File
    protected lateinit var scope: CoroutineScope
    protected var cloud = true
    protected var authorized = true
    protected var zone: PrivacyZoneSpec? = null
    protected val localUser = UUID.randomUUID()
    protected var account: UUID? = UUID.randomUUID()
    protected val t0: Instant = Instant.ofEpochSecond(1_760_000_000)
    protected val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun setupStore() {
        directory = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        db = TripTrackDatabase.inMemory(context)
        tripFiles = DirectoryTripPhotoFiles(File(directory, "TripPhotos"))
        vehicleFiles = DirectoryVehiclePhotoFiles(File(directory, "VehiclePhotos"))
        val environment = DataEnvironment({ cloud }, { localUser }, { zone != null }, clock = { t0 })
        repo = RoomTripRepository(db, environment, tripFiles)
        vehiclePhotos = RoomVehiclePhotoStore(db, vehicleFiles)
        vehicles = RoomVehicleStore(db, repo, vehiclePhotos, environment)
        server = MockWebServer().apply { start() }
        val vault = object : SecureVault {
            var values = mapOf(TokenStore.accessTokenKey to "test-access", TokenStore.refreshTokenKey to "test-refresh", TokenStore.accountIdKey to account.toString(), TokenStore.signedInKey to "true")
            override fun read() = SecureRead.Value(values)
            override fun write(values: Map<String, String>) { this.values = values }
        }
        client = ApiClient(TokenStore(vault).apply { hydrate() }, server.url("/").toString(), pause = {})
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    @After fun closeStore() { scope.cancel(); client.close(); server.close(); db.close(); directory.deleteRecursively() }
    protected fun transport(encoder: PhotoEncoder = RasterPhotoEncoder(), archive: suspend (UUID) -> Unit = {}, r2: R2PhotoStorage = R2PhotoStorage(client)) = APISyncTransport(
        client, db, repo, vehicles, tripFiles, vehicleFiles, { cloud }, { zone },
        ManualTripTrackLoader(repo, client, { account }, { cloud }), archive, {}, encoder, { t0 }, r2,
    )
    protected fun applier(purge: suspend (UUID) -> Unit = {}, invalidate: () -> Unit = {}) =
        PullApplier(repo, RoomDiscoveryStore(db), vehiclePhotos, purge, invalidate)
    protected fun ok(payload: String = "{}") = MockResponse.Builder().body("{\"status\":\"ok\",\"payload\":$payload}").build()
    protected fun error(code: String) = MockResponse.Builder().body("{\"status\":\"error\",\"code\":\"$code\",\"message\":\"test\"}").build()
    protected inline fun <reified T> okValue(value: T) = ok(NetworkJson.wire.encodeToString(kotlinx.serialization.serializer<T>(), value))
    protected fun tripPayload(id: UUID = UUID.randomUUID(), version: Int = 1) = TripSyncPayload(
        id = id, startDate = t0, endDate = t0.plusSeconds(3600), distance = 1234.5, maxSpeed = 20.0, averageSpeed = 10.0,
        fuelUsed = 0.5, elevation = 10.0, isPrivate = false, conflictVersion = version, lastModifiedAt = t0,
    )
    protected fun response(trips: List<TripSyncPayload> = emptyList(), deleted: List<UUID> = emptyList(),
        photos: List<PhotoSyncPayload> = emptyList(), deletedPhotos: List<UUID> = emptyList(),
        cars: List<VehicleSyncPayload> = emptyList(), deletedCars: List<UUID> = emptyList(),
        journeys: List<JourneySyncPayload>? = null, settings: SettingsSyncPayload? = null,
        discoveries: List<DiscoverySyncPayload>? = null, counts: SyncPullResponse.OwnedCounts? = null) = SyncPullResponse(
            SyncPullResponse.TripsSection(trips, deleted), SyncPullResponse.VehiclesSection(cars, deletedCars), SyncPullResponse.PhotosSection(photos, deletedPhotos),
            settings, "2026-09-16T12:00:00.000Z", counts, journeys?.let { SyncPullResponse.JourneysSection(it, emptyList()) },
            discoveries?.let { SyncPullResponse.DiscoveriesSection(it) },
        )
}
