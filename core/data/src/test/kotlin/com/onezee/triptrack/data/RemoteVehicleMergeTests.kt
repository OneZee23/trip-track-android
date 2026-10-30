// iOS: RemoteVehicleMergeTests.swift, VehicleEnergyWireTests.swift (pull half), GarageDataTests.swift (store half),
// VehicleDashboardUnitsWireTests.swift (pull half), VehicleStateTests.swift (sold/unsold over the wire)
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.entity.VehicleEntity
import com.onezee.triptrack.model.DashboardUnits
import com.onezee.triptrack.model.Powertrain
import com.onezee.triptrack.model.VehicleMod
import com.onezee.triptrack.model.VehicleSyncPayload
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

/**
 * «The server says car» and «the server has no opinion» are different, and an unsent local edit
 * beats the pull wherever a returned value would be a leak or a wrong number.
 */
class RemoteVehicleMergeTests : RoomTestCase() {
    private suspend fun makeLocal(id: UUID, style: String = "scooter", type: String = "moped", pending: Boolean = false): VehicleEntity {
        val e = VehicleEntity(
            id = id, name = "Vespa", avatarEmoji = "car_blue", avatarStyle = style, vehicleType = type,
            odometerKm = 120.0, vehicleLevel = 2, lastModifiedAt = Instant.now(),
            syncStatus = if (pending) pendingUpload else synced,
        )
        db.vehicleDao().upsertVehicle(e)
        return e
    }

    private suspend fun stored(id: UUID): VehicleEntity = assertNotNull(db.vehicleDao().vehicle(id))

    private fun payload(
        id: UUID,
        avatarStyle: String? = null,
        city: Double = 0.0,
        highway: Double = 0.0,
        fuelPrice: Double = 0.0,
        powertrain: Powertrain? = null,
        electricConsumption: Double? = null,
        electricityPrice: Double? = null,
        electricRangeKm: Double? = null,
        dashboardUnits: DashboardUnits? = null,
        make: String? = null,
        model: String? = null,
        year: Int? = null,
    ) = VehicleSyncPayload(
        id = id, name = "Vespa", avatarEmoji = "car_blue", odometerKm = 120.0, level = 2, stickersJson = null,
        cityConsumption = city, highwayConsumption = highway, fuelPrice = fuelPrice, conflictVersion = 1,
        lastModifiedAt = Instant.now(), avatarStyle = avatarStyle, powertrain = powertrain,
        electricConsumption = electricConsumption, electricityPrice = electricityPrice, electricRangeKm = electricRangeKm,
        dashboardUnits = dashboardUnits, make = make, model = model, year = year,
    )

    // MARK: Silhouette (RemoteVehicleMergeTests)

    @Test
    fun testUnsentLocalSilhouetteWins() = test {
        val id = UUID.randomUUID()
        makeLocal(id, style = "crossover", pending = true)
        repo.applyRemoteVehicle(payload(id, avatarStyle = "car"))
        assertEquals("crossover", stored(id).avatarStyle)
    }

    @Test
    fun testSilentServerDoesNotResetSilhouette() = test {
        val id = UUID.randomUUID()
        makeLocal(id)
        repo.applyRemoteVehicle(payload(id, avatarStyle = null))
        assertEquals("scooter", stored(id).avatarStyle)
    }

    @Test
    fun testServerValueOverwritesLocalSilhouette() = test {
        val id = UUID.randomUUID()
        makeLocal(id)
        repo.applyRemoteVehicle(payload(id, avatarStyle = "motorcycle"))
        assertEquals("motorcycle", stored(id).avatarStyle)
    }

    @Test
    fun testServerCanDeliberatelySayCar() = test {
        val id = UUID.randomUUID()
        makeLocal(id)
        repo.applyRemoteVehicle(payload(id, avatarStyle = VehicleStore.DEFAULT_AVATAR_STYLE))
        assertEquals(VehicleStore.DEFAULT_AVATAR_STYLE, stored(id).avatarStyle)
    }

    /** A first arrival from a silent server still ends up drawable: the column default is «car». */
    @Test
    fun testNewVehicleFromSilentServerIsStillDrawable() = test {
        val id = UUID.randomUUID()
        repo.applyRemoteVehicle(payload(id, avatarStyle = null))
        assertEquals("car", stored(id).avatarStyle)
        assertEquals(synced, stored(id).syncStatus, "a new row is not a local edit")
    }

    @Test
    fun testSilentServerLeavesOtherOptionalFieldsAlone() = test {
        val id = UUID.randomUUID()
        makeLocal(id)
        db.vehicleDao().upsertVehicle(stored(id).copy(plate = "A123AA777", plateVisible = true, visibleToOthers = false))

        repo.applyRemoteVehicle(payload(id, avatarStyle = null))

        assertEquals("moped", stored(id).vehicleType)
        assertEquals("A123AA777", stored(id).plate)
        assertEquals(true, stored(id).plateVisible)
        assertEquals(false, stored(id).visibleToOthers)
    }

    // MARK: Energy and fuel (VehicleEnergyWireTests)

    private suspend fun insertHybrid(id: UUID, pending: Boolean) {
        db.vehicleDao().upsertVehicle(
            VehicleEntity(
                id = id, name = "Стенд", powertrain = Powertrain.pluginHybrid.rawValue, electricConsumption = 18.0,
                electricityPrice = 8.0, electricRangeKm = 50.0, cityConsumption = 9.1, highwayConsumption = 5.4,
                fuelPrice = 61.0, syncStatus = if (pending) pendingUpload else synced,
            ),
        )
    }

    @Test
    fun testPullWithoutKeysKeepsLocalEnergy() = test {
        val id = UUID.randomUUID()
        insertHybrid(id, pending = false)
        repo.applyRemoteVehicle(payload(id, city = 10.0, highway = 6.0, fuelPrice = 56.0))
        assertEquals("pluginHybrid", stored(id).powertrain)
        assertEquals(50.0, stored(id).electricRangeKm)
    }

    /** A returned range is a WRONG split of every hybrid trip, not a nuisance: the unsent edit wins. */
    @Test
    fun testLocalEditBeatsThePull() = test {
        val id = UUID.randomUUID()
        insertHybrid(id, pending = true)
        repo.applyRemoteVehicle(
            payload(id, city = 10.0, highway = 6.0, fuelPrice = 56.0, powertrain = Powertrain.fuel, electricConsumption = 0.0, electricityPrice = 0.0, electricRangeKm = 0.0),
        )
        assertEquals("pluginHybrid", stored(id).powertrain, "the pull undid the local powertrain")
        assertEquals(50.0, stored(id).electricRangeKm, "the pull zeroed the local range")
    }

    @Test
    fun testPullUpdatesASyncedVehicle() = test {
        val id = UUID.randomUUID()
        insertHybrid(id, pending = false)
        repo.applyRemoteVehicle(payload(id, city = 10.0, highway = 6.0, fuelPrice = 56.0, powertrain = Powertrain.electric, electricConsumption = 20.0, electricityPrice = 9.0, electricRangeKm = 0.0))
        assertEquals("electric", stored(id).powertrain)
        assertEquals(20.0, stored(id).electricConsumption)
    }

    @Test
    fun testLocalFuelEditBeatsThePull() = test {
        val id = UUID.randomUUID()
        insertHybrid(id, pending = true)
        repo.applyRemoteVehicle(payload(id, city = 10.0, highway = 6.0, fuelPrice = 56.0))
        assertEquals(9.1, stored(id).cityConsumption, "the pull put the server's consumption over the typed one")
        assertEquals(61.0, stored(id).fuelPrice, "the pull put the server's price over the typed one")
    }

    @Test
    fun testPullUpdatesFuelOfASyncedVehicle() = test {
        val id = UUID.randomUUID()
        insertHybrid(id, pending = false)
        repo.applyRemoteVehicle(payload(id, city = 10.0, highway = 6.0, fuelPrice = 56.0))
        assertEquals(10.0, stored(id).cityConsumption)
        assertEquals(56.0, stored(id).fuelPrice)
    }

    /** An unsent edit keeps its flag AND its time, or the second pull undoes what the first kept. */
    @Test
    fun testPullOverAPendingEditKeepsItsFlagAndTime() = test {
        val id = UUID.randomUUID()
        insertHybrid(id, pending = true)
        val stamp = Instant.ofEpochSecond(1_760_000_000)
        db.vehicleDao().upsertVehicle(stored(id).copy(lastModifiedAt = stamp))
        repo.applyRemoteVehicle(payload(id).copy(conflictVersion = 7))
        assertEquals(pendingUpload, stored(id).syncStatus)
        assertEquals(stamp, stored(id).lastModifiedAt)
        assertEquals(7, stored(id).conflictVersion, "the version is taken all the same")
    }

    // MARK: Dashboard units (VehicleDashboardUnitsWireTests)

    @Test
    fun testAServerWithoutTheColumnDoesNotResetTheChoice() = test {
        val id = UUID.randomUUID()
        makeLocal(id)
        db.vehicleDao().upsertVehicle(stored(id).copy(dashboardUnits = "metric"))
        repo.applyRemoteVehicle(payload(id, dashboardUnits = null))
        assertEquals("metric", stored(id).dashboardUnits)
    }

    @Test
    fun testAChoiceThatHasNotLeftYetSurvivesAPull() = test {
        val id = UUID.randomUUID()
        makeLocal(id, pending = true)
        db.vehicleDao().upsertVehicle(stored(id).copy(dashboardUnits = "metric"))
        repo.applyRemoteVehicle(payload(id, dashboardUnits = DashboardUnits.app))
        assertEquals("metric", stored(id).dashboardUnits, "the pull overtook the queue and undid the choice")
    }

    @Test
    fun testAKnownServerUnitIsAppliedToASyncedCar() = test {
        val id = UUID.randomUUID()
        makeLocal(id)
        repo.applyRemoteVehicle(payload(id, dashboardUnits = DashboardUnits.imperial))
        assertEquals("imperial", stored(id).dashboardUnits)
    }

    // MARK: Odometer and sale: the key decides, not the value

    @Test
    fun testManualOdometerAndSaleFollowTheKeyNotTheValue() = test {
        val id = UUID.randomUUID()
        makeLocal(id)
        db.vehicleDao().upsertVehicle(stored(id).copy(manualOdometerKm = 142_000.0, soldAt = Instant.ofEpochSecond(1_760_000_000)))

        repo.applyRemoteVehicle(payload(id))
        assertEquals(142_000.0, stored(id).manualOdometerKm, "an absent key is not a clear")
        assertNotNull(stored(id).soldAt)

        repo.applyRemoteVehicle(payload(id).copy(manualOdometerKnown = true, soldAtKnown = true))
        assertNull(stored(id).manualOdometerKm, "an explicit null is a clear made on the other device")
        assertNull(stored(id).soldAt, "an explicit null undoes the sale")
    }

    // MARK: Garage (GarageDataTests)

    @Test
    fun testRemoteGarageKeepsPendingEditsAndUnderstandsSilenceAndClear() = test {
        for (pending in listOf(false, true)) {
            val id = UUID.randomUUID()
            db.vehicleDao().upsertVehicle(
                VehicleEntity(
                    id = id, name = "Car", avatarEmoji = "🚗", drive2URL = "https://www.drive2.ru/r/vw/polo/123/",
                    paintFinish = "matte", modTagsJSON = "[\"CarPlay\"]", modsJSON = "[]", catalogModelId = "old",
                    make = "Volkswagen", model = "Polo", year = 2018, avatarStyle = "crossover",
                    syncStatus = if (pending) pendingUpload else synced, lastModifiedAt = Instant.now(),
                ),
            )
            repo.applyRemoteVehicle(
                VehicleSyncPayload(
                    id = id, name = "Car", avatarEmoji = "🚗", odometerKm = 0.0, level = 1, stickersJson = null,
                    cityConsumption = 10.0, highwayConsumption = 6.0, fuelPrice = 1.0, conflictVersion = 1, lastModifiedAt = Instant.now(),
                ),
            )
            assertEquals("matte", stored(id).paintFinish)
            assertEquals("old", stored(id).catalogModelId)

            val p = VehicleSyncPayload(
                id = id, name = "Car", avatarEmoji = "🚗", odometerKm = 0.0, level = 1, stickersJson = null,
                cityConsumption = 10.0, highwayConsumption = 6.0, fuelPrice = 1.0, conflictVersion = 1, lastModifiedAt = Instant.now(),
                avatarStyle = "car", make = "Lada", model = "Vesta", year = 2016,
                drive2Url = "", modTags = emptyList(), paintFinish = "gloss",
                mods = listOf(VehicleMod(title = "Fresh", date = "2026-01-01")), catalogModelId = "new",
            )
            repo.applyRemoteVehicle(p)
            // Passport and catalog ids are one edit: a pull must not return the old make/year under
            // the new, still unsent catalog ids.
            val e = stored(id)
            assertEquals(if (pending) "Volkswagen" else "Lada", e.make)
            assertEquals(if (pending) "Polo" else "Vesta", e.model)
            assertEquals(if (pending) 2018 else 2016, e.year)
            assertEquals(if (pending) "crossover" else "car", e.avatarStyle)
            assertEquals(if (pending) "https://www.drive2.ru/r/vw/polo/123/" else "", e.drive2URL)
            assertEquals(if (pending) "matte" else "gloss", e.paintFinish)
            assertEquals(if (pending) "old" else "new", e.catalogModelId)
            assertEquals(if (pending) listOf("CarPlay") else emptyList(), VehicleModTags.decodeJSON(e.modTagsJSON))
            assertEquals(if (pending) 0 else 1, JsonColumns.decodeMods(e.modsJSON).size)
            assertEquals(if (pending) pendingUpload else synced, e.syncStatus)
        }
    }

    /** An invalid link from the server is stored as a clear; a valid one is normalized. */
    @Test
    fun testRemoteDrive2LinkIsNormalizedOrCleared() = test {
        val id = UUID.randomUUID()
        makeLocal(id)
        repo.applyRemoteVehicle(payload(id).copy().apply { drive2Url = "http://m.drive2.ru/r/lada/vesta/42/logbook/?x=1" })
        assertEquals("https://www.drive2.ru/r/lada/vesta/42/", stored(id).drive2URL)
        repo.applyRemoteVehicle(payload(id).copy().apply { drive2Url = "https://evil.example/r/a/b/1/" })
        assertEquals("", stored(id).drive2URL)
        repo.applyRemoteVehicle(payload(id).copy().apply { paintFinish = "future-finish" })
        assertEquals("", stored(id).drive2URL)
        assertNull(stored(id).paintFinish, "an unknown finish is preserved, here: never set")
    }

    @Test
    fun testUploadDistinguishesUnknownColumnsFromExplicitEmptyValues() {
        val base = VehicleSyncPayload(
            id = UUID.randomUUID(), name = "Car", avatarEmoji = "🚗", odometerKm = 0.0, level = 1, stickersJson = null,
            cityConsumption = 10.0, highwayConsumption = 6.0, fuelPrice = 1.0, conflictVersion = 1, lastModifiedAt = Instant.now(),
        )
        val unknown = base.withGarageFields(VehicleEntity(id = base.id))
        assertNull(unknown.drive2Url)
        assertNull(unknown.modTags)
        assertNull(unknown.paintFinish)
        assertNull(unknown.mods)
        assertNull(unknown.catalogModelId)

        val cleared = base.withGarageFields(
            VehicleEntity(id = base.id, drive2URL = "", modTagsJSON = "[]", modsJSON = "[]", catalogModelId = "", paintFinish = "gloss"),
        )
        assertEquals("", cleared.drive2Url)
        assertEquals(emptyList(), cleared.modTags)
        assertEquals(emptyList(), cleared.mods)
        assertEquals("", cleared.catalogModelId)
        assertEquals("gloss", cleared.paintFinish)
    }
}
