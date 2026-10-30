// iOS: PlaceStore.swift (protocol PlaceStore, CoreDataPlaceStore)
package com.onezee.triptrack.data

import androidx.room.withTransaction
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.db.entity.PlaceEntity
import com.onezee.triptrack.data.db.entity.PlacePassEntity
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Place
import com.onezee.triptrack.model.PlacePass
import java.time.Instant
import java.util.UUID

/** iOS: the `(place:, isNew:)` tuple of `upsertPlace`. */
data class UpsertedPlace(val place: Place, val isNew: Boolean)

/**
 * Places and passes, their own store: no sync and no relationships with trips. `PlaceEntity` and
 * `PlacePassEntity` are relationless like `JourneyEntity`, so a trip deletion calls
 * [deletePasses] explicitly and the account wipe names both tables.
 */
interface PlaceStore {
    suspend fun fetchPlaces(): List<Place>
    suspend fun fetchPlace(id: UUID): Place?
    /** One cell, one place. An existing place gets a name only while it has none: the first name stays. */
    suspend fun upsertPlace(cell: String, coordinate: GeoPoint, name: String?): UpsertedPlace
    suspend fun recomputeCentroid(placeId: UUID, coordinates: List<GeoPoint>)
    /** True when the place was unnamed and took the name; a deleted place's tombstone creates nothing. */
    suspend fun adoptName(name: String, placeId: UUID): Boolean
    /** A name by hand overwrites any; blank is unnamed again. The geocoder (adoptName) never undoes it. */
    suspend fun rename(placeId: UUID, name: String?)
    /** The place and its passes. Checkpoints keep their `placeId` as a tombstone. */
    suspend fun deletePlace(id: UUID)
    suspend fun passes(placeId: UUID): List<PlacePass>
    /** One read for a list of places, each group newest first; places without passes are absent. */
    suspend fun passes(placeIds: List<UUID>): Map<UUID, List<PlacePass>>
    suspend fun passesOfTrip(tripId: UUID): List<PlacePass>
    suspend fun passCount(placeId: UUID): Int
    /** The passes of one (place, trip) pair are replaced whole: a repeated match never doubles history. */
    suspend fun replacePasses(placeId: UUID, tripId: UUID, passes: List<PlacePass>)
    suspend fun deletePasses(tripId: UUID)
}

/** iOS: CoreDataPlaceStore. */
class RoomPlaceStore(
    private val db: TripTrackDatabase,
    private val clock: () -> Instant = Instant::now,
) : PlaceStore {
    private val dao = db.placeDao()

    private fun place(e: PlaceEntity) = Place(
        id = e.id, cell = e.cell, latitude = e.latitude, longitude = e.longitude, name = e.name, createdAt = e.createdAt,
    )

    private fun pass(e: PlacePassEntity) = PlacePass(
        id = e.id, placeId = e.placeId, tripId = e.tripId, timestamp = e.timestamp,
        elapsedFromStart = e.elapsedFromStart, distanceFromStart = e.distanceFromStart, course = e.course,
    )

    override suspend fun fetchPlaces(): List<Place> = dao.places().map(::place)

    override suspend fun fetchPlace(id: UUID): Place? = dao.place(id)?.let(::place)

    override suspend fun upsertPlace(cell: String, coordinate: GeoPoint, name: String?): UpsertedPlace =
        db.withTransaction {
            val id = Place.id(cell)
            val existing = dao.place(id)
            if (existing != null) {
                val named = if (existing.name == null && !name.isNullOrEmpty()) existing.copy(name = name) else existing
                if (named !== existing) dao.upsertPlace(named)
                return@withTransaction UpsertedPlace(place(named), isNew = false)
            }
            val created = PlaceEntity(
                id = id,
                cell = cell,
                latitude = coordinate.latitude,
                longitude = coordinate.longitude,
                name = name?.takeIf { it.isNotEmpty() },
                createdAt = clock(),
            )
            dao.upsertPlace(created)
            UpsertedPlace(place(created), isNew = true)
        }

    override suspend fun recomputeCentroid(placeId: UUID, coordinates: List<GeoPoint>) {
        if (coordinates.isEmpty()) return
        db.withTransaction {
            val e = dao.place(placeId) ?: return@withTransaction
            dao.upsertPlace(
                e.copy(
                    latitude = coordinates.sumOf { it.latitude } / coordinates.size,
                    longitude = coordinates.sumOf { it.longitude } / coordinates.size,
                ),
            )
        }
    }

    override suspend fun adoptName(name: String, placeId: UUID): Boolean = db.withTransaction {
        if (name.isEmpty()) return@withTransaction false
        val e = dao.place(placeId) ?: return@withTransaction false
        if (e.name != null) return@withTransaction false
        dao.upsertPlace(e.copy(name = name))
        true
    }

    override suspend fun rename(placeId: UUID, name: String?) {
        db.withTransaction {
            val e = dao.place(placeId) ?: return@withTransaction
            val trimmed = name?.trim()
            dao.upsertPlace(e.copy(name = if (trimmed.isNullOrEmpty()) null else trimmed))
        }
    }

    override suspend fun deletePlace(id: UUID) {
        db.withTransaction {
            if (dao.place(id) == null) return@withTransaction
            dao.deletePassesOfPlace(id)
            dao.deletePlace(id)
        }
    }

    override suspend fun passes(placeId: UUID): List<PlacePass> = dao.passesOfPlace(placeId).map(::pass)

    override suspend fun passes(placeIds: List<UUID>): Map<UUID, List<PlacePass>> {
        if (placeIds.isEmpty()) return emptyMap()
        val grouped = linkedMapOf<UUID, MutableList<PlacePass>>()
        // Each chunk is newest first; merging chunks of different places keeps every group ordered.
        for (chunk in placeIds.distinct().chunked(CHUNK)) {
            for (row in dao.passesOfPlaces(chunk)) grouped.getOrPut(row.placeId) { mutableListOf() }.add(pass(row))
        }
        return grouped
    }

    override suspend fun passesOfTrip(tripId: UUID): List<PlacePass> = dao.passesOfTrip(tripId).map(::pass)

    override suspend fun passCount(placeId: UUID): Int = dao.countPasses(placeId)

    override suspend fun replacePasses(placeId: UUID, tripId: UUID, passes: List<PlacePass>) {
        db.withTransaction {
            dao.deletePasses(placeId, tripId)
            if (passes.isNotEmpty()) {
                dao.insertPasses(
                    passes.map {
                        PlacePassEntity(
                            id = it.id, placeId = placeId, tripId = tripId, timestamp = it.timestamp,
                            elapsedFromStart = it.elapsedFromStart, distanceFromStart = it.distanceFromStart, course = it.course,
                        )
                    },
                )
            }
        }
    }

    override suspend fun deletePasses(tripId: UUID) {
        dao.deletePassesOfTrip(tripId)
    }

    private companion object {
        const val CHUNK = 500
    }
}
