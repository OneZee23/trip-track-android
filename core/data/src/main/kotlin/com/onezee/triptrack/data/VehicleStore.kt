// iOS: SettingsManager.swift (the vehicle half: loadVehicles, vehicleFromEntity and every vehicle writer,
// migrateArchiveAway, migrateVehicleMapToOptIn, migrateDefaultVehicleName),
// APISyncTransport.uploadVehicle (marking synced), VehicleSyncPayload.applyGarageFields
package com.onezee.triptrack.data

import androidx.room.withTransaction
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.db.entity.VehicleEntity
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.logic.Drive2Link
import com.onezee.triptrack.logic.VehicleLevelSystem
import com.onezee.triptrack.logic.VehicleModTags
import com.onezee.triptrack.logic.hasPlate
import com.onezee.triptrack.model.DashboardUnits
import com.onezee.triptrack.model.Powertrain
import com.onezee.triptrack.model.SyncStatus
import com.onezee.triptrack.model.Vehicle
import com.onezee.triptrack.model.VehicleCardStyle
import com.onezee.triptrack.model.VehicleFinish
import com.onezee.triptrack.model.VehicleMod
import com.onezee.triptrack.model.VehicleSticker
import com.onezee.triptrack.model.VehicleSyncPayload
import com.onezee.triptrack.model.VehicleType
import java.time.Instant
import java.util.UUID

/** iOS: the garage half of SettingsManager. Every person's edit flips `pendingUpload` (0.8.3). */
interface VehicleStore {
    /** The WHOLE garage, sold cars included: old trips still find their car through it. */
    suspend fun vehicles(): List<Vehicle>
    suspend fun vehicle(id: UUID): Vehicle?
    suspend fun entity(id: UUID): VehicleEntity?
    suspend fun addVehicle(
        name: String,
        emoji: String,
        avatarStyle: String = DEFAULT_AVATAR_STYLE,
        type: VehicleType = VehicleType.car,
        plate: String = "",
        plateVisible: Boolean = false,
        visibleToOthers: Boolean = true,
    ): UUID
    suspend fun deleteVehicle(id: UUID): Boolean
    suspend fun setManualOdometer(vehicleId: UUID, km: Double?)
    suspend fun updateVehiclePassport(id: UUID, make: String, model: String, year: Int, bodyType: String, about: String = "")
    suspend fun updateVehicleGarage(
        id: UUID,
        drive2URL: String? = null,
        modTags: List<String>? = null,
        paintFinish: VehicleFinish? = null,
        mods: List<VehicleMod>? = null,
        catalogModelId: String? = null,
        catalogGenerationId: String? = null,
        catalogVariantId: String? = null,
    )
    suspend fun setVehicleSold(id: UUID, soldAt: Instant?)
    suspend fun updateVehicleVisibility(id: UUID, visibleToOthers: Boolean, plateVisible: Boolean, mapVisible: Boolean, photosVisible: Boolean)
    suspend fun updateVehicleIdentity(
        id: UUID,
        name: String,
        emoji: String,
        type: VehicleType,
        plate: String,
        plateVisible: Boolean,
        visibleToOthers: Boolean,
        avatarStyle: String,
    )
    suspend fun setDashboardUnits(vehicleId: UUID, units: DashboardUnits)
    suspend fun setCardStyle(vehicleId: UUID, style: VehicleCardStyle)
    suspend fun updateVehicleCurrency(id: UUID, symbol: String)
    suspend fun renameVehicle(id: UUID, name: String)
    suspend fun updateVehicleAvatar(id: UUID, emoji: String)
    suspend fun updateVehicleFuel(id: UUID, city: Double, highway: Double, price: Double)
    suspend fun setPowertrain(vehicleId: UUID, powertrain: Powertrain)
    suspend fun updateVehicleEnergy(id: UUID, consumption: Double, price: Double, rangeKm: Double)
    /** The upload's answer: the server version, and `synced` only if the row still has the sent stamp. */
    suspend fun markVehicleUploaded(id: UUID, conflictVersion: Int, sentLastModifiedAt: Instant?)
    suspend fun migrateArchiveAway(flags: DataFlags)
    suspend fun migrateVehicleMapToOptIn(flags: DataFlags)
    suspend fun migrateDefaultVehicleName(localizedName: String)

    companion object {
        /** iOS: VehicleAvatar.defaultStyle. */
        const val DEFAULT_AVATAR_STYLE = "car"
        const val ARCHIVE_REMOVED_KEY = "com.triptrack.settings.archiveRemovedV1"
        const val MAP_OPT_IN_KEY = "com.triptrack.settings.vehicleMapOptInV1"
    }
}

/**
 * The garage. Every writer that is a person's edit flips `pendingUpload` and, where iOS does, moves
 * `lastModifiedAt` (0.8.3): without the flag a pull would count the car as synced and lay the
 * server's values over the ones just typed, and the `!hasLocalEdits` gate of `applyRemoteVehicle`
 * would be dead, with nobody left to arm it. Writers that iOS leaves unflagged (rename, emoji)
 * stay so; their queued operation still carries the change.
 */
class RoomVehicleStore(
    private val db: TripTrackDatabase,
    private val repository: TripRepository,
    private val photos: VehiclePhotoStore,
    private val environment: DataEnvironment,
    private val sync: SyncRequestSink = SyncRequestSink.None,
) : VehicleStore {
    private val dao = db.vehicleDao()
    private val pendingUpload = SyncStatus.pendingUpload.rawValue

    /**
     * The WHOLE garage, sold cars included: four screens find the car of an OLD trip through it, and
     * a filter here would show «vehicle deleted» for a live car. Reward kilometres are read once for
     * the garage, not once per card.
     */
    override suspend fun vehicles(): List<Vehicle> {
        val rewardKm = repository.rewardKmByVehicle()
        return dao.vehicles().map { vehicle(it, rewardKm[it.id] ?: 0.0) }
    }

    override suspend fun vehicle(id: UUID): Vehicle? {
        val entity = dao.vehicle(id) ?: return null
        return vehicle(entity, repository.rewardKmByVehicle()[id] ?: 0.0)
    }

    override suspend fun entity(id: UUID): VehicleEntity? = dao.vehicle(id)

    /** iOS: addVehicle. Returns the new id. */
    override suspend fun addVehicle(
        name: String,
        emoji: String,
        avatarStyle: String,
        type: VehicleType,
        plate: String,
        plateVisible: Boolean,
        visibleToOthers: Boolean,
    ): UUID {
        val id = UUID.randomUUID()
        dao.upsertVehicle(
            VehicleEntity(
                id = id,
                name = name,
                avatarEmoji = emoji,
                avatarStyle = avatarStyle,
                vehicleType = type.rawValue,
                plate = plate,
                plateVisible = plateVisible,
                visibleToOthers = visibleToOthers,
                odometerKm = 0.0,
                vehicleLevel = 1,
                createdAt = environment.clock(),
            ),
        )
        sync.enqueue(SyncRequest(SyncEntityType.vehicle, id, SyncAction.upload))
        return id
    }

    /**
     * The row, its photos and their files: photo rows have no relationship with the car, so nothing
     * cascades. The caller also drops the car stereo binding and a selection that pointed at it.
     */
    override suspend fun deleteVehicle(id: UUID): Boolean {
        if (dao.vehicle(id) == null) return false
        dao.deleteVehicle(id)
        photos.deleteAll(id)
        sync.enqueue(SyncRequest(SyncEntityType.vehicle, id, SyncAction.delete))
        return true
    }

    /** Dashboard odometer; null clears it. The level is NOT recomputed: no levels for typed digits. */
    override suspend fun setManualOdometer(vehicleId: UUID, km: Double?) {
        edit(vehicleId, SyncAction.update) { it.copy(manualOdometerKm = km?.let { value -> maxOf(0.0, value) }).flagged() }
    }

    /** Make, model, year and body. Zero is «no year»; an implausible year is not stored. */
    override suspend fun updateVehiclePassport(id: UUID, make: String, model: String, year: Int, bodyType: String, about: String) {
        edit(id, SyncAction.upload) {
            it.copy(
                make = make,
                model = model,
                year = if (year in 1900..2100) year else 0,
                bodyType = bodyType,
                about = about,
            ).stamped()
        }
    }

    /**
     * The 0.9.1 garage fields. A null argument leaves the column; mods are replaced whole, at most
     * [VehicleMod.limit]. An invalid DRIVE2 link is stored as an explicit clear, as on iOS.
     */
    override suspend fun updateVehicleGarage(
        id: UUID,
        drive2URL: String?,
        modTags: List<String>?,
        paintFinish: VehicleFinish?,
        mods: List<VehicleMod>?,
        catalogModelId: String?,
        catalogGenerationId: String?,
        catalogVariantId: String?,
    ) {
        edit(id, SyncAction.upload) { e ->
            var v = e
            drive2URL?.let { v = v.copy(drive2URL = Drive2Link.normalized(it) ?: "") }
            modTags?.let { v = v.copy(modTagsJSON = VehicleModTags.encodeJSON(it)) }
            paintFinish?.let { v = v.copy(paintFinish = it.rawValue) }
            catalogModelId?.let { v = v.copy(catalogModelId = it) }
            catalogGenerationId?.let { v = v.copy(catalogGenerationId = it) }
            catalogVariantId?.let { v = v.copy(catalogVariantId = it) }
            mods?.let { v = v.copy(modsJSON = JsonColumns.encodeMods(it.take(VehicleMod.limit))) }
            v.stamped()
        }
    }

    /**
     * A sold car's biography is frozen: it takes no new trips. The caller moves a selection that
     * pointed at it, through the persisted settings (the recording start reads those).
     */
    override suspend fun setVehicleSold(id: UUID, soldAt: Instant?) {
        edit(id, SyncAction.upload) { it.copy(soldAt = soldAt).stamped() }
    }

    /** The four visibility axes; a returned visibility is a leak, so the flag matters most here. */
    override suspend fun updateVehicleVisibility(id: UUID, visibleToOthers: Boolean, plateVisible: Boolean, mapVisible: Boolean, photosVisible: Boolean) {
        edit(id, SyncAction.upload) {
            it.copy(
                visibleToOthers = visibleToOthers,
                plateVisible = plateVisible,
                mapVisible = mapVisible,
                photosVisible = photosVisible,
            ).stamped()
        }
    }

    /**
     * How the car looks and who sees it. `avatarStyle` has no default on purpose: a caller saying
     * nothing about the silhouette is not asking for a saloon. A type without a plate keeps none.
     */
    override suspend fun updateVehicleIdentity(
        id: UUID,
        name: String,
        emoji: String,
        type: VehicleType,
        plate: String,
        plateVisible: Boolean,
        visibleToOthers: Boolean,
        avatarStyle: String,
    ) {
        edit(id, SyncAction.upload) {
            it.copy(
                name = name,
                avatarEmoji = emoji,
                avatarStyle = avatarStyle,
                vehicleType = type.rawValue,
                plate = if (type.hasPlate) plate else "",
                plateVisible = if (type.hasPlate) plateVisible else false,
                visibleToOthers = visibleToOthers,
            ).stamped()
        }
    }

    /** What the dashboard shows. Storage stays metric: nothing is converted, only how it is read. */
    override suspend fun setDashboardUnits(vehicleId: UUID, units: DashboardUnits) {
        edit(vehicleId, SyncAction.update) { it.copy(dashboardUnits = units.rawValue).flagged() }
    }

    /** «No background» is NULL, not "": nothing chosen has one form. */
    override suspend fun setCardStyle(vehicleId: UUID, style: VehicleCardStyle) {
        edit(vehicleId, SyncAction.update) {
            it.copy(cardStyle = if (style == VehicleCardStyle.none) null else style.rawValue).flagged()
        }
    }

    override suspend fun updateVehicleCurrency(id: UUID, symbol: String) {
        edit(id, SyncAction.upload) { it.copy(fuelCurrency = symbol).stamped() }
    }

    override suspend fun renameVehicle(id: UUID, name: String) {
        edit(id, SyncAction.upload) { it.copy(name = name) }
    }

    override suspend fun updateVehicleAvatar(id: UUID, emoji: String) {
        edit(id, SyncAction.upload) { it.copy(avatarEmoji = emoji) }
    }

    /** Litres per 100 km and price per litre: every trip's cost is computed from them. */
    override suspend fun updateVehicleFuel(id: UUID, city: Double, highway: Double, price: Double) {
        edit(id, SyncAction.upload) {
            it.copy(cityConsumption = city, highwayConsumption = highway, fuelPrice = price).stamped()
        }
    }

    /** Typed numbers survive a type change: a hybrid set to «fuel» and back keeps its kWh. */
    override suspend fun setPowertrain(vehicleId: UUID, powertrain: Powertrain) {
        edit(vehicleId, SyncAction.update) { it.copy(powertrain = powertrain.rawValue).flagged() }
    }

    /** kWh per 100 km, price per kWh, range in km, already normalized by the field parser. */
    override suspend fun updateVehicleEnergy(id: UUID, consumption: Double, price: Double, rangeKm: Double) {
        edit(id, SyncAction.upload) {
            it.copy(electricConsumption = consumption, electricityPrice = price, electricRangeKm = rangeKm).flagged()
        }
    }

    /**
     * iOS: APISyncTransport.uploadVehicle on success. The server version is taken, but `synced` only
     * if the row still carries the stamp that was sent: an edit made while the request was in
     * flight has its own operation and must keep its flag and its time.
     */
    override suspend fun markVehicleUploaded(id: UUID, conflictVersion: Int, sentLastModifiedAt: Instant?) {
        db.withTransaction {
            val e = dao.vehicle(id) ?: return@withTransaction
            val unchanged = e.lastModifiedAt == sentLastModifiedAt
            dao.upsertVehicle(
                e.copy(
                    conflictVersion = conflictVersion,
                    syncStatus = if (unchanged) SyncStatus.synced.rawValue else e.syncStatus,
                ),
            )
        }
    }

    /**
     * iOS: migrateArchiveAway. The archive mechanic is gone, so everything that went there comes
     * back once; otherwise an archived car silently vanished from the recording picker.
     */
    override suspend fun migrateArchiveAway(flags: DataFlags) {
        if (flags.get(VehicleStore.ARCHIVE_REMOVED_KEY)) return
        db.withTransaction {
            for (row in dao.vehicles().filter { it.isArchived }) dao.upsertVehicle(row.copy(isArchived = false).stamped())
        }
        flags.set(VehicleStore.ARCHIVE_REMOVED_KEY, true)
    }

    /**
     * iOS: migrateVehicleMapToOptIn. Cars created before 0.6.4 lose their route map once, and the
     * change is flagged so it reaches the server and the other phone.
     */
    override suspend fun migrateVehicleMapToOptIn(flags: DataFlags) {
        if (flags.get(VehicleStore.MAP_OPT_IN_KEY)) return
        db.withTransaction {
            for (row in dao.vehicles().filter { it.mapVisible }) dao.upsertVehicle(row.copy(mapVisible = false).stamped())
        }
        flags.set(VehicleStore.MAP_OPT_IN_KEY, true)
    }

    /** iOS: migrateDefaultVehicleName. The invented first car's legacy name becomes the localized one. */
    override suspend fun migrateDefaultVehicleName(localizedName: String) {
        val first = dao.vehicles().firstOrNull() ?: return
        if (first.name !in LEGACY_NAMES) return
        dao.upsertVehicle(first.copy(name = localizedName))
    }

    private suspend fun edit(id: UUID, action: SyncAction, transform: (VehicleEntity) -> VehicleEntity) {
        val changed = db.withTransaction {
            val e = dao.vehicle(id) ?: return@withTransaction false
            dao.upsertVehicle(transform(e))
            true
        }
        if (changed) sync.enqueue(SyncRequest(SyncEntityType.vehicle, id, action))
    }

    private fun VehicleEntity.flagged(): VehicleEntity = copy(syncStatus = pendingUpload)

    private fun VehicleEntity.stamped(): VehicleEntity = copy(lastModifiedAt = environment.clock(), syncStatus = pendingUpload)

    /** iOS: SettingsManager.vehicleFromEntity. Unknown stored values read as the defaults, untouched. */
    private fun vehicle(e: VehicleEntity, rewardKm: Double): Vehicle {
        val stickers = if (e.stickersJSON.isNullOrEmpty()) emptyList()
        else JsonColumns.decodeStrings(e.stickersJSON).mapNotNull(VehicleSticker::fromRawValue)
        return Vehicle(
            id = e.id,
            name = e.name ?: "",
            avatarEmoji = e.avatarEmoji ?: "🏎️",
            avatarStyle = e.avatarStyle ?: VehicleStore.DEFAULT_AVATAR_STYLE,
            type = vehicleType(e.vehicleType),
            plate = e.plate ?: "",
            plateVisible = e.plateVisible,
            visibleToOthers = e.visibleToOthers,
            odometerKm = e.odometerKm,
            // Optional on purpose: «not entered» differs from zero, a legal odometer of a new car.
            manualOdometerKm = e.manualOdometerKm,
            rewardOdometerKm = rewardKm,
            // Derived, not read: the stored column is the old ten-rung curve. From REWARD km (0.8.0).
            level = VehicleLevelSystem.level(rewardKm),
            stickers = stickers,
            createdAt = e.createdAt ?: environment.clock(),
            cityConsumption = e.cityConsumption,
            highwayConsumption = e.highwayConsumption,
            fuelPrice = e.fuelPrice,
            fuelCurrency = e.fuelCurrency ?: environment.fuelCurrency(),
            dashboardUnits = DashboardUnits.parse(e.dashboardUnits) ?: DashboardUnits.app,
            powertrain = Powertrain.parse(e.powertrain) ?: Powertrain.fuel,
            electricConsumption = e.electricConsumption,
            electricityPrice = e.electricityPrice,
            electricRangeKm = e.electricRangeKm,
            cardStyle = e.cardStyle,
            drive2URL = e.drive2URL,
            modTags = VehicleModTags.decodeJSON(e.modTagsJSON),
            paintFinish = e.paintFinish,
            mods = JsonColumns.decodeMods(e.modsJSON),
            catalogModelId = e.catalogModelId,
            catalogGenerationId = e.catalogGenerationId,
            catalogVariantId = e.catalogVariantId,
            about = e.about ?: "",
            make = e.make ?: "",
            model = e.model ?: "",
            year = e.year,
            bodyType = e.bodyType ?: "",
            photosVisible = e.photosVisible,
            mapVisible = e.mapVisible,
            isArchived = e.isArchived,
            soldAt = e.soldAt,
        )
    }

    private companion object {
        val LEGACY_NAMES = setOf("Телега", "Telega")
    }
}

/**
 * iOS: VehicleSyncPayload.applyGarageFields(from:). NULL in a pre-v24 row is UNKNOWN and stays out
 * of the payload; an explicitly stored empty value is a clear and travels. Read from the row, never
 * from the non-null `Vehicle` snapshot, or an old device would wipe fields it never loaded.
 */
fun VehicleSyncPayload.withGarageFields(entity: VehicleEntity): VehicleSyncPayload = copy().apply {
    drive2Url = entity.drive2URL
    modTags = entity.modTagsJSON?.let(VehicleModTags::decodeJSON)
    paintFinish = entity.paintFinish
    mods = entity.modsJSON?.let(JsonColumns::decodeMods)
    catalogModelId = entity.catalogModelId
    catalogGenerationId = entity.catalogGenerationId
    catalogVariantId = entity.catalogVariantId
}
