// iOS: PersistenceController(inMemory: true) screen test fixtures.
package com.onezee.triptrack.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.onezee.triptrack.data.*
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.files.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.model.*
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// iOS: UserDefaults(suiteName:).
class MemoryPreferences : PresentationPreferences {
    private val values = mutableMapOf<String, String>()
    override fun string(key: String) = values[key]
    override fun set(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
abstract class WorldTest {
    protected lateinit var db: TripTrackDatabase
    protected lateinit var repo: RoomTripRepository
    protected lateinit var places: RoomPlaceStore
    protected lateinit var vehicles: RoomVehicleStore
    protected lateinit var photos: RoomVehiclePhotoStore
    protected val preferences = MemoryPreferences()
    protected val scheduler = TestCoroutineScheduler()
    protected val dispatcher = StandardTestDispatcher(scheduler)
    protected val viewModels = ViewModelStore()
    private val root = Files.createTempDirectory("world-test").toFile()
    protected val t0 = Instant.ofEpochSecond(1_760_000_000)
    protected val userId = UUID.randomUUID()
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TripTrackDatabase::class.java)
            .allowMainThreadQueries().setQueryCoroutineContext(dispatcher).build()
        val environment = DataEnvironment(localUserId = { userId }, clock = { t0 })
        repo = RoomTripRepository(db, environment, DirectoryTripPhotoFiles(root.resolve("trips")))
        places = RoomPlaceStore(db) { t0 }
        photos = RoomVehiclePhotoStore(db, DirectoryVehiclePhotoFiles(root.resolve("cars")))
        vehicles = RoomVehicleStore(db, repo, photos, environment)
    }
    @After fun close() { viewModels.clear(); scheduler.runCurrent(); db.close(); root.deleteRecursively() }
    protected inline fun <reified T : ViewModel> own(vm: T): T = ViewModelProvider(viewModels, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <M : ViewModel> create(modelClass: Class<M>): M = vm as M
    })[T::class.java]
    protected suspend fun insertTrip(start: Instant = t0, preview: ByteArray? = null, vehicleId: UUID? = null,
                                     title: String? = null, xp: Int = 0, isPrivate: Boolean = true): UUID {
        val id = UUID.randomUUID()
        db.tripDao().upsertTrip(TripEntity(id = id, startDate = start, endDate = start.plusSeconds(3600),
            distance = 1000.0, previewPolyline = preview, vehicleId = vehicleId, title = title, xpEarned = xp, isPrivate = isPrivate))
        return id
    }
}
