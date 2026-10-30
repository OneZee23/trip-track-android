// iOS: TripManager.swift (startTrip, backdateTrip, the batch save in handleNewLocation, the pause
// boundary in isPaused, stopTrip, cleanupOrphanedTrips, geocodeAndNameTrip, retryGeocoding),
// PostTripTrackProcessor.swift (processOnContext), GamificationManager.swift (saveTripXP and the
// vehicle half of processCompletedTrip)
package com.onezee.triptrack.data

import androidx.room.withTransaction
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.db.entity.TrackPointEntity
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.model.RoadFillState
import com.onezee.triptrack.model.SyncStatus
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.TripConfirmation
import java.time.Instant
import java.util.UUID

/**
 * A recording the process died in: a trip row without `endDate`, read at launch. The running
 * numbers are those of the last batch that reached the disk.
 */
data class RecordingOrphan(
    val id: UUID,
    val startDate: Instant,
    val distance: Double,
    val maxSpeed: Double,
    val averageSpeed: Double,
    val vehicleId: UUID?,
    val isTransfer: Boolean,
    val confirmation: TripConfirmation,
    val recordingBreaks: List<Instant>,
    val pointCount: Int,
    val lastPointAt: Instant?,
)

/** What `PostTripTrackProcessor` changed, written back in one transaction. */
data class TrackProcessingWrite(
    val tripId: UUID,
    val removedPointIds: List<UUID>,
    val addedPoints: List<TrackPoint>,
    val distance: Double,
    val maxSpeed: Double,
    val averageSpeed: Double,
    val previewPolyline: ByteArray?,
    val roadFillState: RoadFillState,
)

/**
 * The writes of a recording. Every method commits on return, like the rest of `core:data`; the
 * recorder batches the points itself (10 points or 15 seconds, as iOS batches its saves).
 */
interface RecordingStore {
    /** iOS: startTrip. A private row without `endDate`: that is what makes it a recording. */
    suspend fun createTrip(
        id: UUID,
        startDate: Instant,
        vehicleId: UUID?,
        isTransfer: Boolean,
        confirmation: TripConfirmation,
    )

    /** iOS: backdateTrip. An auto-start learns when the drive really began only after it starts. */
    suspend fun backdateStart(tripId: UUID, startDate: Instant)

    /** iOS: the batch save. Points and the running numbers reach the disk together. */
    suspend fun appendPoints(tripId: UUID, points: List<TrackPoint>, distance: Double, maxSpeed: Double, averageSpeed: Double)

    /**
     * iOS: `isPaused` didSet. Saved the moment the pause starts: a recalculation or a restart must
     * never count the paused stretch as driving.
     */
    suspend fun saveBreaks(tripId: UUID, breaks: List<Instant>)

    /** iOS: orderedTrackPoints. Time, then id. */
    suspend fun trackPoints(tripId: UUID): List<TrackPoint>

    /** iOS: stopTrip. End, final numbers and the preview polyline, in one write. */
    suspend fun finish(
        tripId: UUID,
        endDate: Instant,
        distance: Double,
        maxSpeed: Double,
        averageSpeed: Double,
        previewPolyline: ByteArray?,
    ): Boolean

    /** iOS: cleanupOrphanedTrips' fetch, `endDate == nil`. */
    suspend fun orphans(): List<RecordingOrphan>

    /** An empty or junk orphan disappears without a trace; its points go with the cascade. */
    suspend fun deleteOrphan(id: UUID)

    /** An orphan that is not resumed is closed at its last fix, so it can never come back as a zombie. */
    suspend fun closeOrphan(id: UUID, endDate: Instant, previewPolyline: ByteArray?)

    // MARK: Post-processing

    suspend fun unprocessedTripIds(): List<UUID>

    suspend fun isTrackProcessed(tripId: UUID): Boolean?

    /** A manual trip or a track of fewer than two points: nothing to clean, only the latch. */
    suspend fun markProcessed(tripId: UUID)

    /**
     * iOS: processOnContext's save. A filled gap is an edit of the trip: without `pendingUpload` the
     * next pull would bring the track back without its fill. True when the trip should be queued.
     */
    suspend fun applyTrackProcessing(write: TrackProcessingWrite): Boolean

    // MARK: Rewards and names

    /** iOS: GamificationManager.saveTripXP. */
    suspend fun saveXP(tripId: UUID, xp: Int)

    /** iOS: processCompletedTrip's vehicle write. Bookkeeping, not an edit: no sync flag. */
    suspend fun setVehicleProgress(vehicleId: UUID, odometerKm: Double, level: Int)

    /** iOS: geocodeAndNameTrip. False when the trip is gone. */
    suspend fun nameTrip(tripId: UUID, title: String, region: String?): Boolean

    suspend fun untitledTripIds(): List<UUID>
}

class RoomRecordingStore(
    private val db: TripTrackDatabase,
    private val environment: DataEnvironment,
) : RecordingStore {
    private val trips = db.tripDao()
    private val recording = db.recordingDao()
    private val vehicles = db.vehicleDao()

    private fun now(): Instant = environment.clock()

    override suspend fun createTrip(
        id: UUID,
        startDate: Instant,
        vehicleId: UUID?,
        isTransfer: Boolean,
        confirmation: TripConfirmation,
    ) {
        trips.upsertTrip(
            TripEntity(
                id = id,
                startDate = startDate,
                vehicleId = vehicleId,
                isTransfer = isTransfer,
                fuelCurrency = environment.fuelCurrency(),
                lastModifiedAt = now(),
                userId = environment.localUserId(),
                // New trips are private by default; the owner opens one from the trip screen.
                isPrivate = true,
                confirmation = confirmation.rawValue,
            ),
        )
    }

    override suspend fun backdateStart(tripId: UUID, startDate: Instant) {
        edit(tripId) { it.copy(startDate = startDate) }
    }

    override suspend fun appendPoints(
        tripId: UUID,
        points: List<TrackPoint>,
        distance: Double,
        maxSpeed: Double,
        averageSpeed: Double,
    ) {
        db.withTransaction {
            // A discarded recording takes no more points: the foreign key would refuse them anyway.
            val entity = trips.trip(tripId) ?: return@withTransaction
            if (points.isNotEmpty()) trips.insertTrackPoints(points.map { it.toEntity(tripId) })
            trips.upsertTrip(entity.copy(distance = distance, maxSpeed = maxSpeed, averageSpeed = averageSpeed))
        }
    }

    override suspend fun saveBreaks(tripId: UUID, breaks: List<Instant>) {
        edit(tripId) { it.copy(recordingBreaksJSON = JsonColumns.encodeBreaks(breaks), lastModifiedAt = now()) }
    }

    override suspend fun trackPoints(tripId: UUID): List<TrackPoint> = trips.orderedTrackPoints(tripId).map { row ->
        TrackPoint(
            id = row.id,
            latitude = row.latitude,
            longitude = row.longitude,
            altitude = row.altitude,
            speed = row.speed,
            course = row.course,
            horizontalAccuracy = row.horizontalAccuracy,
            timestamp = row.timestamp,
            isInterpolated = row.isInterpolated,
        )
    }

    override suspend fun finish(
        tripId: UUID,
        endDate: Instant,
        distance: Double,
        maxSpeed: Double,
        averageSpeed: Double,
        previewPolyline: ByteArray?,
    ): Boolean = db.withTransaction {
        val entity = trips.trip(tripId) ?: return@withTransaction false
        trips.upsertTrip(
            entity.copy(
                endDate = endDate,
                lastModifiedAt = now(),
                distance = distance,
                maxSpeed = maxSpeed,
                averageSpeed = averageSpeed,
                previewPolyline = previewPolyline ?: entity.previewPolyline,
            ),
        )
        true
    }

    override suspend fun orphans(): List<RecordingOrphan> = trips.unfinishedTrips().map { e ->
        RecordingOrphan(
            id = e.id,
            startDate = e.startDate,
            distance = e.distance,
            maxSpeed = e.maxSpeed,
            averageSpeed = e.averageSpeed,
            vehicleId = e.vehicleId,
            isTransfer = e.isTransfer,
            // An empty column reads as confirmed, like every trip before 0.8.1.
            confirmation = e.confirmation?.let(TripConfirmation::fromRawValue) ?: TripConfirmation.confirmed,
            recordingBreaks = JsonColumns.decodeBreaks(e.recordingBreaksJSON),
            pointCount = trips.countTrackPoints(e.id),
            lastPointAt = recording.lastPointTime(e.id),
        )
    }

    override suspend fun deleteOrphan(id: UUID) {
        trips.deleteTrip(id)
    }

    override suspend fun closeOrphan(id: UUID, endDate: Instant, previewPolyline: ByteArray?) {
        edit(id) { it.copy(endDate = endDate, previewPolyline = previewPolyline ?: it.previewPolyline) }
    }

    override suspend fun unprocessedTripIds(): List<UUID> = recording.unprocessedTripIds()

    override suspend fun isTrackProcessed(tripId: UUID): Boolean? = trips.trip(tripId)?.isTrackProcessed

    override suspend fun markProcessed(tripId: UUID) {
        edit(tripId) { it.copy(roadFillState = RoadFillState.done.rawValue, isTrackProcessed = true) }
    }

    override suspend fun applyTrackProcessing(write: TrackProcessingWrite): Boolean = db.withTransaction {
        val entity = trips.trip(write.tripId) ?: return@withTransaction false
        for (chunk in write.removedPointIds.chunked(CHUNK)) trips.deleteTrackPoints(chunk)
        if (write.addedPoints.isNotEmpty()) trips.insertTrackPoints(write.addedPoints.map { it.toEntity(write.tripId) })
        val filled = write.addedPoints.isNotEmpty()
        val flips = filled && TripRepository.flipsPendingUpload(entity.isPrivate, environment.cloudSyncEnabled())
        trips.upsertTrip(
            entity.copy(
                distance = write.distance,
                maxSpeed = write.maxSpeed,
                averageSpeed = write.averageSpeed,
                previewPolyline = write.previewPolyline ?: entity.previewPolyline,
                roadFillState = write.roadFillState.rawValue,
                isTrackProcessed = true,
                lastModifiedAt = now(),
                syncStatus = if (flips) SyncStatus.pendingUpload.rawValue else entity.syncStatus,
            ),
        )
        filled
    }

    override suspend fun saveXP(tripId: UUID, xp: Int) {
        edit(tripId) { it.copy(xpEarned = xp) }
    }

    override suspend fun setVehicleProgress(vehicleId: UUID, odometerKm: Double, level: Int) {
        db.withTransaction {
            val entity = vehicles.vehicle(vehicleId) ?: return@withTransaction
            vehicles.upsertVehicle(entity.copy(odometerKm = odometerKm, vehicleLevel = level))
        }
    }

    override suspend fun nameTrip(tripId: UUID, title: String, region: String?): Boolean = db.withTransaction {
        val entity = trips.trip(tripId) ?: return@withTransaction false
        trips.upsertTrip(entity.copy(title = title, region = region, lastModifiedAt = now()))
        true
    }

    override suspend fun untitledTripIds(): List<UUID> = recording.untitledTripIds()

    private suspend fun edit(id: UUID, transform: (TripEntity) -> TripEntity) {
        db.withTransaction {
            val entity = trips.trip(id) ?: return@withTransaction
            trips.upsertTrip(transform(entity))
        }
    }

    private fun TrackPoint.toEntity(tripId: UUID) = TrackPointEntity(
        id = id,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        speed = speed,
        course = course,
        horizontalAccuracy = horizontalAccuracy,
        timestamp = timestamp,
        isInterpolated = isInterpolated,
        tripId = tripId,
    )

    private companion object {
        const val CHUNK = 500
    }
}
