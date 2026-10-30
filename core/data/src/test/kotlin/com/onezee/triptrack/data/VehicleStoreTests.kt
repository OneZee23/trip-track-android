// iOS: VehicleStateTests.swift, VehicleSyncConflictTests.swift (an edit during the upload),
// VehicleDashboardUnitsWireTests.swift and VehicleEnergyWireTests.swift (the writers), GarageDataTests.swift,
// VehiclePhotoRecoveryTests.swift (the store half)
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.entity.VehicleEntity
import com.onezee.triptrack.data.db.entity.VehiclePhotoEntity
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.model.DashboardUnits
import com.onezee.triptrack.model.Powertrain
import com.onezee.triptrack.model.VehicleCardStyle
import com.onezee.triptrack.model.VehicleFinish
import com.onezee.triptrack.model.VehicleMod
import com.onezee.triptrack.model.VehicleType
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test

class VehicleStoreTests : RoomTestCase() {
    private lateinit var photos: RoomVehiclePhotoStore
    private lateinit var store: RoomVehicleStore

    @Before
    fun makeStores() {
        photos = RoomVehiclePhotoStore(db, vehiclePhotos, sink)
        store = RoomVehicleStore(db, repo, photos, environment, sink)
    }

    private suspend fun entity(id: UUID): VehicleEntity = assertNotNull(db.vehicleDao().vehicle(id))

    private suspend fun markSynced(id: UUID, at: Instant = Instant.EPOCH) {
        db.vehicleDao().upsertVehicle(entity(id).copy(syncStatus = synced, lastModifiedAt = at))
    }

    private fun edit(name: String, block: suspend () -> Unit): Pair<String, suspend () -> Unit> = name to block

    @Test
    fun testANewCarHasTheModelDefaults() = test {
        val id = store.addVehicle(name = "Polo", emoji = "🚗")
        val e = entity(id)
        assertEquals("car", e.avatarStyle)
        assertEquals("car", e.vehicleType)
        assertEquals("app", e.dashboardUnits)
        assertEquals("fuel", e.powertrain)
        assertEquals(10.0, e.cityConsumption)
        assertEquals(6.0, e.highwayConsumption)
        assertEquals(56.0, e.fuelPrice)
        assertEquals(true, e.mapVisible)
        assertEquals(false, e.photosVisible)
        assertEquals(1, e.vehicleLevel)
        assertEquals(pendingUpload, e.syncStatus)
        assertEquals(listOf(SyncRequest(SyncEntityType.vehicle, id, SyncAction.upload)), sink.enqueued)
        val vehicle = assertNotNull(store.vehicle(id))
        assertEquals("€", vehicle.fuelCurrency, "no own symbol: the app's currency")
        assertEquals(DashboardUnits.app, vehicle.dashboardUnits)
    }

    /**
     * Every person's edit flips `pendingUpload` (0.8.3): without it the pull lays the server's
     * values over the ones just typed, and the `!hasLocalEdits` gate has nobody to arm it.
     */
    @Test
    fun testEveryEditOfFiguresAndPrivacyFlipsPendingUpload() = test {
        val id = store.addVehicle(name = "Car", emoji = "🚗")
        val edits = listOf(
            edit("fuel") { store.updateVehicleFuel(id, 9.0, 5.0, 61.0) },
            edit("currency") { store.updateVehicleCurrency(id, "₸") },
            edit("identity") { store.updateVehicleIdentity(id, "Car", "🚗", VehicleType.car, "A1", true, false, "crossover") },
            edit("visibility") { store.updateVehicleVisibility(id, false, false, false, true) },
            edit("passport") { store.updateVehiclePassport(id, "Lada", "Vesta", 2016, "sedan") },
            edit("garage") { store.updateVehicleGarage(id, modTags = listOf("CarPlay")) },
            edit("sold") { store.setVehicleSold(id, Instant.now()) },
            edit("dashboard") { store.setDashboardUnits(id, DashboardUnits.metric) },
            edit("powertrain") { store.setPowertrain(id, Powertrain.pluginHybrid) },
            edit("energy") { store.updateVehicleEnergy(id, 18.0, 8.0, 50.0) },
            edit("card") { store.setCardStyle(id, VehicleCardStyle.gold) },
            edit("manual odometer") { store.setManualOdometer(id, 142_000.0) },
        )
        for ((name, edit) in edits) {
            markSynced(id)
            edit()
            assertEquals(pendingUpload, entity(id).syncStatus, "$name did not flip pendingUpload")
        }
    }

    /** The writers iOS stamps move `lastModifiedAt`; the ones it does not stamp leave it. */
    @Test
    fun testStampedWritersMoveTheTimeAndTheOthersDoNot() = test {
        val id = store.addVehicle(name = "Car", emoji = "🚗")
        markSynced(id)
        store.updateVehicleFuel(id, 9.0, 5.0, 61.0)
        assertTrue(entity(id).lastModifiedAt!! > Instant.EPOCH)

        markSynced(id)
        store.setDashboardUnits(id, DashboardUnits.imperial)
        assertEquals(Instant.EPOCH, entity(id).lastModifiedAt)
        assertEquals("imperial", entity(id).dashboardUnits)
    }

    @Test
    fun testPassportRejectsImplausibleYearsAndPlatelessTypesKeepNoPlate() = test {
        val id = store.addVehicle(name = "Car", emoji = "🚗", plate = "A123AA777", plateVisible = true)
        store.updateVehiclePassport(id, "Lada", "Vesta", 1, "sedan")
        assertEquals(0, entity(id).year, "an empty field is more honest than «1» in a passport")
        store.updateVehiclePassport(id, "Lada", "Vesta", 2016, "sedan")
        assertEquals(2016, entity(id).year)

        store.updateVehicleIdentity(id, "Велик", "🚲", VehicleType.bicycle, "A123AA777", true, true, "bike")
        assertEquals("", entity(id).plate, "a bicycle keeps no plate, not even a hidden one")
        assertEquals(false, entity(id).plateVisible)
    }

    @Test
    fun testGarageFieldsAreNormalizedAndModsCapped() = test {
        val id = store.addVehicle(name = "Car", emoji = "🚗")
        val many = (0 until VehicleMod.limit + 5).map { VehicleMod(title = "m$it", date = "2026-01-01") }
        store.updateVehicleGarage(
            id, drive2URL = " https://drive2.ru/r/volkswagen/polo/123/?utm=1 ", modTags = listOf("  CarPlay ", "CarPlay", "Шумка"),
            paintFinish = VehicleFinish.matte, mods = many, catalogModelId = "volkswagen-polo",
        )
        val e = entity(id)
        assertEquals("https://www.drive2.ru/r/volkswagen/polo/123/", e.drive2URL)
        assertEquals(listOf("CarPlay", "Шумка"), VehicleModTags.decodeJSON(e.modTagsJSON))
        assertEquals("matte", e.paintFinish)
        assertEquals(VehicleMod.limit, JsonColumns.decodeMods(e.modsJSON).size)
        assertEquals("volkswagen-polo", e.catalogModelId)

        store.updateVehicleGarage(id, drive2URL = "https://example.com/")
        assertEquals("", entity(id).drive2URL, "an invalid link is an explicit clear")
        assertEquals("matte", entity(id).paintFinish, "a null argument leaves the column")
    }

    @Test
    fun testCardStyleNoneIsNull() = test {
        val id = store.addVehicle(name = "Car", emoji = "🚗")
        store.setCardStyle(id, VehicleCardStyle.carbon)
        assertEquals("card_carbon", entity(id).cardStyle)
        store.setCardStyle(id, VehicleCardStyle.none)
        assertNull(entity(id).cardStyle, "«nothing chosen» has one form")
    }

    /** A negative dashboard reading is clamped; null clears the typed odometer. */
    @Test
    fun testManualOdometerClampsAndClears() = test {
        val id = store.addVehicle(name = "Car", emoji = "🚗")
        store.setManualOdometer(id, -5.0)
        assertEquals(0.0, entity(id).manualOdometerKm)
        store.setManualOdometer(id, null)
        assertNull(entity(id).manualOdometerKm)
    }

    /**
     * An edit made while the upload was in flight has its own operation: the answer takes the
     * server version but must not mark the newer edit synced.
     */
    @Test
    fun testGarageEditDuringUploadKeepsItsPendingStatusAndTimestamp() = test {
        val id = store.addVehicle(name = "Car", emoji = "🚗")
        val sent = Instant.ofEpochSecond(1_760_000_000)
        db.vehicleDao().upsertVehicle(entity(id).copy(lastModifiedAt = sent))

        store.updateVehicleGarage(id, modTags = listOf("CarPlay"), paintFinish = VehicleFinish.matte)
        val editedAt = entity(id).lastModifiedAt
        store.markVehicleUploaded(id, conflictVersion = 4, sentLastModifiedAt = sent)

        assertEquals(pendingUpload, entity(id).syncStatus)
        assertEquals(editedAt, entity(id).lastModifiedAt)
        assertEquals(4, entity(id).conflictVersion)
        assertEquals("matte", entity(id).paintFinish)

        store.markVehicleUploaded(id, conflictVersion = 5, sentLastModifiedAt = editedAt)
        assertEquals(synced, entity(id).syncStatus, "the answer to the latest edit marks it synced")
    }

    /** The whole garage, sold cars included: old trips still find their car. */
    @Test
    fun testTheFullListStillHoldsSoldVehicles() = test {
        val a = store.addVehicle(name = "Первая", emoji = "🚗")
        val b = store.addVehicle(name = "Проданная", emoji = "🚗")
        store.setVehicleSold(b, Instant.now())
        assertEquals(setOf(a, b), store.vehicles().map { it.id }.toSet())
        assertNull(repo.recordableVehicleId(b))
        assertEquals(a, repo.recordableVehicleId(a))
    }

    /** The archive mechanic is gone: a car archived before comes back once, flagged for upload. */
    @Test
    fun testAVehicleArchivedBeforeTheChangeComesBack() = test {
        val stuck = UUID.randomUUID()
        db.vehicleDao().upsertVehicle(VehicleEntity(id = stuck, name = "Из архива", isArchived = true, syncStatus = synced))
        val flags = DataFlags.InMemory()

        store.migrateArchiveAway(flags)

        assertEquals(false, entity(stuck).isArchived)
        assertEquals(pendingUpload, entity(stuck).syncStatus)
        assertEquals(stuck, repo.recordableVehicleId(stuck))
        assertTrue(flags.get(VehicleStore.ARCHIVE_REMOVED_KEY))
    }

    @Test
    fun testExistingVehiclesLoseTheirRouteMapOnce() = test {
        val old = UUID.randomUUID()
        db.vehicleDao().upsertVehicle(VehicleEntity(id = old, mapVisible = true, syncStatus = synced))
        val flags = DataFlags.InMemory()
        store.migrateVehicleMapToOptIn(flags)
        assertEquals(false, entity(old).mapVisible)
        assertEquals(pendingUpload, entity(old).syncStatus, "the switched-off map has to reach the other phone")

        val fresh = store.addVehicle(name = "New", emoji = "🚗")
        store.migrateVehicleMapToOptIn(flags)
        assertEquals(true, entity(fresh).mapVisible, "the migration does not run twice")
    }

    @Test
    fun testTheLegacyDefaultNameIsLocalized() = test {
        val id = UUID.randomUUID()
        db.vehicleDao().upsertVehicle(VehicleEntity(id = id, name = "Телега"))
        store.migrateDefaultVehicleName("Моя машина")
        assertEquals("Моя машина", entity(id).name)
    }

    // MARK: Deleting a car takes its photos (no cascade reaches them)

    @Test
    fun testDeletingAVehicleTakesItsPhotosAndFiles() = test {
        val id = store.addVehicle(name = "Car", emoji = "🚗")
        val first = assertNotNull(photos.add(byteArrayOf(1, 2, 3), id))
        val second = assertNotNull(photos.add(byteArrayOf(4, 5, 6), id))
        assertTrue(first.isMain, "the first photo becomes the main one")
        assertTrue(!second.isMain)

        store.deleteVehicle(id)

        assertNull(db.vehicleDao().vehicle(id))
        assertTrue(db.vehicleDao().photos(id).isEmpty())
        assertTrue(!vehiclePhotos.exists(first.filename))
        assertTrue(!vehiclePhotos.exists(second.filename))
        assertTrue(SyncRequest(SyncEntityType.vehicle, id, SyncAction.delete) in sink.enqueued)
    }

    @Test
    fun testDeletingTheMainPhotoPromotesTheNextOne() = test {
        val id = store.addVehicle(name = "Car", emoji = "🚗")
        val first = assertNotNull(photos.add(byteArrayOf(1), id))
        val second = assertNotNull(photos.add(byteArrayOf(2), id))
        db.vehicleDao().upsertPhoto(db.vehicleDao().photo(second.id)!!.copy(timestamp = first.timestamp.plusSeconds(1)))

        photos.delete(first.id, id)

        assertEquals(second.id, photos.mainPhoto(id)?.id)
        assertEquals(second.id, photos.mainPhotos()[id]?.id)
        assertTrue(SyncRequest(SyncEntityType.vehiclePhoto, second.id, SyncAction.update) in sink.enqueued)
        assertTrue(sink.enqueued.none { it.action == SyncAction.delete && it.entityId == first.id }, "the server never saw the first photo")
    }

    /** A row without a file and without a server copy is a tombstone: gone on read, not a grey tile. */
    @Test
    fun testATombstonePhotoIsRemovedOnRead() = test {
        val id = store.addVehicle(name = "Car", emoji = "🚗")
        val dead = UUID.randomUUID()
        val restorable = UUID.randomUUID()
        db.vehicleDao().upsertPhoto(VehiclePhotoEntity(id = dead, vehicleId = id, filename = "gone.jpg", timestamp = Instant.now()))
        db.vehicleDao().upsertPhoto(VehiclePhotoEntity(id = restorable, vehicleId = id, filename = "gone2.jpg", timestamp = Instant.now(), remoteURL = "vehicles/x"))

        assertEquals(listOf(restorable), photos.photos(id).map { it.id })
        assertNull(db.vehicleDao().photo(dead))
    }

    /** A tombstone from the other phone: local photos go, nothing is queued for the server. */
    @Test
    fun testDeleteAllLocallyQueuesNothing() = test {
        val id = store.addVehicle(name = "Car", emoji = "🚗")
        val photo = assertNotNull(photos.add(byteArrayOf(1), id))
        db.vehicleDao().upsertPhoto(db.vehicleDao().photo(photo.id)!!.copy(remoteURL = "r", thumbnailURL = "t"))
        sink.enqueued.clear()

        photos.deleteAllLocally(id)

        assertTrue(db.vehicleDao().photos(id).isEmpty())
        assertTrue(!vehiclePhotos.exists(photo.filename))
        assertTrue(sink.enqueued.isEmpty())
    }
}
