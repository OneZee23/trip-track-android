package com.onezee.triptrack.location.world

import com.onezee.triptrack.data.RecordingStore
import com.onezee.triptrack.data.TrackProcessingWrite
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.location.recording.RecordingEnd
import com.onezee.triptrack.logic.PostTripTrackProcessor
import com.onezee.triptrack.logic.invalidatePreviewCache
import com.onezee.triptrack.logic.withRecordingBreaks
import com.onezee.triptrack.model.TripOrigin
import com.onezee.triptrack.model.Trip
import java.util.UUID

/**
 * iOS: PostTripTrackProcessor.swift, the service half. Spikes out, straight fills in, preview and
 * statistics again, `isTrackProcessed` latched. The geometry is core:logic's
 * `PostTripTrackProcessor.process`; this writes back only what changed, so a long drive is not
 * deleted and re-inserted point by point.
 *
 * A road for the fills is asked later by the road gap filler (not on Android yet): the fill state
 * stays `pending` for it.
 */
class TrackPostProcessing(
    private val trips: TripRepository,
    private val recording: RecordingStore,
    private val sync: SyncRequestSink,
) {
    suspend fun processTrip(tripId: UUID) {
        // Processed once; a second call (launch sweep after the finish) changes nothing.
        if (recording.isTrackProcessed(tripId) != false) return
        val loaded = trips.fetchTripDetail(tripId) ?: return
        val trip = loaded.withRecordingBreaks(loaded.recordingBreaks)
        // A built line is not a GPS recording: no spikes, no gaps to fill.
        if (trip.source == TripOrigin.manual || trip.trackPoints.count { !it.isInterpolated } < 2) {
            recording.markProcessed(tripId)
            return
        }
        // Spike removal always keeps both ends, so at least two real points remain.
        val processed = PostTripTrackProcessor.process(trip)
        val before = trip.trackPoints.mapTo(HashSet()) { it.id }
        val after = processed.trackPoints.mapTo(HashSet()) { it.id }
        val write = TrackProcessingWrite(
            tripId = tripId,
            removedPointIds = trip.trackPoints.filter { it.id !in after }.map { it.id },
            addedPoints = processed.trackPoints.filter { it.id !in before },
            distance = processed.distance,
            maxSpeed = processed.maxSpeed,
            averageSpeed = processed.averageSpeed,
            previewPolyline = RecordingEnd.previewPolyline(processed.trackPoints),
            roadFillState = processed.roadFillState,
        )
        val filled = recording.applyTrackProcessing(write)
        // The cache held the old shape until the next launch.
        Trip.invalidatePreviewCache(tripId)
        // A track edit is a trip edit: otherwise a pull would bring the track back without the fill.
        if (filled) sync.enqueue(SyncRequest(SyncEntityType.trip, tripId, SyncAction.update))
    }

    /** iOS: processUnprocessedTrips, at launch: trips whose processing a dead process never finished. */
    suspend fun processUnprocessedTrips() {
        for (id in recording.unprocessedTripIds()) processTrip(id)
    }
}
