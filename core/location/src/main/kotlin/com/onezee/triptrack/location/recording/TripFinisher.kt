package com.onezee.triptrack.location.recording

import android.util.Log
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.location.world.TrackPostProcessing
import com.onezee.triptrack.location.world.TripNamer
import com.onezee.triptrack.location.world.TripWorldEntry
import com.onezee.triptrack.logic.reason
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripConfirmation
import com.onezee.triptrack.model.TripJunkClassifier
import java.time.Instant
import java.util.UUID

/** The notifications a finished or deleted trip touches (iOS: NotificationManager's draft half). */
interface TripNotices {
    /** «Trip recorded: 7.4 km. Yours?» with «Mine» and «Delete». */
    fun draftConfirmPrompt(tripId: UUID, metres: Double)
    fun clearDraftPrompt(tripId: UUID)
    /** «Recording a trip» belongs to every draft at once; cleared only when nothing records. */
    fun clearDraftStartedNotice()

    object None : TripNotices {
        override fun draftConfirmPrompt(tripId: UUID, metres: Double) = Unit
        override fun clearDraftPrompt(tripId: UUID) = Unit
        override fun clearDraftStartedNotice() = Unit
    }
}

/**
 * iOS: TripManager.deleteTrip, the one door for every way a trip is deleted: queued uploads are
 * cancelled, the raw journal goes (a draft may have been somebody else's drive), and the question
 * «Yours?» leaves with it.
 */
class TripDeletion(
    private val trips: TripRepository,
    private val rawFixLog: RawFixLog,
    private val sync: SyncRequestSink,
    private val notices: TripNotices,
    private val isRecording: () -> Boolean,
) {
    suspend fun delete(id: UUID) {
        // Before the row goes: an upload left in the queue would land a permanent orphan on the server.
        val photoIds = trips.fetchTripDetail(id)?.photos?.map { it.id }.orEmpty()
        sync.cancel(id, SyncEntityType.trip)
        photoIds.forEach { sync.cancel(it, SyncEntityType.photo) }
        rawFixLog.remove(id)
        notices.clearDraftPrompt(id)
        // Deleting an old draft must not clear the notice of a trip being recorded right now.
        if (!isRecording()) notices.clearDraftStartedNotice()
        trips.deleteTrip(id)
    }

    /** iOS: discardDraft. A confirmed trip is left alone. */
    suspend fun discardDraft(id: UUID): Boolean {
        if (trips.fetchEntity(id)?.confirmation != TripConfirmation.draft.rawValue) return false
        delete(id)
        return true
    }
}

/**
 * iOS: the tail of MapViewModel.stopRecording. Junk is deleted; a draft gets its question and stays
 * out of the world; a confirmed trip enters it. Post-processing and the name happen for drafts too.
 * Each step is best effort: a failure in one must not cost the trip the others.
 */
class TripFinisher(
    private val world: TripWorldEntry,
    private val postProcessing: TrackPostProcessing,
    private val namer: TripNamer?,
    private val deletion: TripDeletion,
    private val notices: TripNotices,
    private val clock: () -> Instant = Instant::now,
) {
    suspend fun finish(trip: Trip?): StopOutcome {
        if (trip == null) return StopOutcome.NotRecording
        return when (world.route(trip)) {
            TripWorldEntry.Route.discardJunk -> {
                val duration = trip.endDate?.let { secondsBetween(trip.startDate, it) } ?: secondsBetween(trip.startDate, clock())
                deletion.delete(trip.id)
                StopOutcome.Discarded(trip.id, TripJunkClassifier.reason(trip.distance, duration, trip.maxSpeed))
            }
            TripWorldEntry.Route.awaitConfirmation -> {
                notices.draftConfirmPrompt(trip.id, trip.distance)
                step("process") { postProcessing.processTrip(trip.id) }
                step("name") { namer?.name(trip.id, trip.startDate) }
                StopOutcome.AwaitingConfirmation(trip)
            }
            TripWorldEntry.Route.enterWorld -> {
                val rewards = world.rewards(trip)
                step("process") { postProcessing.processTrip(trip.id) }
                // On the FINAL track: filled gaps, no spikes.
                step("places") { world.placesAndReveal(trip) }
                step("name") { namer?.name(trip.id, trip.startDate) }
                StopOutcome.Saved(trip, rewards)
            }
        }
    }

    private suspend fun step(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "finish step $name failed", failure)
        }
    }

    private companion object {
        const val TAG = "TripFinisher"
    }
}
