package com.onezee.triptrack.location.drafts

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.location.recording.TripDeletion
import com.onezee.triptrack.location.recording.TripNotices
import com.onezee.triptrack.location.world.TripWorldEntry
import com.onezee.triptrack.location.world.TripRewardsResult
import com.onezee.triptrack.logic.withRecordingBreaks
import com.onezee.triptrack.model.TripConfirmation
import com.onezee.triptrack.model.Trip
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** iOS: DraftDecision. Raw values are stored. */
enum class DraftDecision(val rawValue: String) {
    confirm("confirm"),
    discard("discard");

    companion object {
        fun fromRawValue(value: String): DraftDecision? = entries.find { it.rawValue == value }
    }
}

/**
 * iOS: DraftDecisionQueue.swift. Decisions that survive a restart: «Mine» and «Delete» on the
 * notification are pressed when the app is not on screen, often not even in memory. Ordered by the
 * time they were queued; the latest decision for a trip wins without moving it.
 */
interface DraftDecisionQueue {
    fun enqueue(tripId: UUID, decision: DraftDecision)
    /** The oldest decision not applied yet, left in the queue. */
    fun peek(): Pair<UUID, DraftDecision>?
    /** One entry, after its decision was applied or knowingly dropped. */
    fun remove(tripId: UUID)
    /** Everything at once, emptying the queue. */
    fun drain(): List<Pair<UUID, DraftDecision>>
}

class PreferencesDraftDecisionQueue(private val preferences: SharedPreferences) : DraftDecisionQueue {
    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    private val pairs = ListSerializer(ListSerializer(String.serializer()))

    @Synchronized
    override fun enqueue(tripId: UUID, decision: DraftDecision) {
        val list = read().toMutableList()
        val index = list.indexOfFirst { it.first == tripId.toString() }
        if (index >= 0) list[index] = list[index].first to decision.rawValue else list += tripId.toString() to decision.rawValue
        write(list)
    }

    @Synchronized
    override fun peek(): Pair<UUID, DraftDecision>? = read().firstNotNullOfOrNull(::parse)

    @Synchronized
    override fun remove(tripId: UUID) {
        write(read().filter { it.first != tripId.toString() })
    }

    @Synchronized
    override fun drain(): List<Pair<UUID, DraftDecision>> {
        val list = read()
        write(emptyList())
        return list.mapNotNull(::parse)
    }

    private fun parse(entry: Pair<String, String>): Pair<UUID, DraftDecision>? {
        val id = runCatching { UUID.fromString(entry.first) }.getOrNull() ?: return null
        val decision = DraftDecision.fromRawValue(entry.second) ?: return null
        return id to decision
    }

    private fun read(): List<Pair<String, String>> {
        val text = preferences.getString(KEY, null) ?: return emptyList()
        return runCatching { Json.decodeFromString(pairs, text) }.getOrDefault(emptyList())
            .mapNotNull { pair -> if (pair.size == 2) pair[0] to pair[1] else null }
    }

    private fun write(list: List<Pair<String, String>>) {
        val editor = preferences.edit()
        if (list.isEmpty()) editor.remove(KEY) else editor.putString(KEY, Json.encodeToString(pairs, list.map { listOf(it.first, it.second) }))
        // Commit: the receiver that queued it may be the last thing this process does.
        @SuppressLint("ApplySharedPref")
        editor.commit()
    }

    companion object {
        const val FILE_NAME = "triptrack.drafts"
        // The iOS UserDefaults key.
        const val KEY = "draftTrips.pendingDecisions"
    }
}

/**
 * iOS: MapViewModel.applyDraftDecisions. The queue is worked one entry at a time (peek, apply,
 * remove), so a process killed in the middle loses nothing: the guards inside `setConfirmation`
 * and `discardDraft` make a repeated decision harmless. A decision never touches the trip that is
 * still recording; it waits until the recording ends.
 */
class DraftDecisions(
    private val queue: DraftDecisionQueue,
    private val trips: TripRepository,
    private val world: TripWorldEntry,
    private val deletion: TripDeletion,
    private val notices: TripNotices,
    private val sync: SyncRequestSink,
    private val activeTripId: () -> UUID?,
    private val rebuildTerritory: suspend () -> Unit = {},
    private val onConfirmed: (Trip, TripRewardsResult) -> Unit = { _, _ -> },
    private val onDiscarded: (UUID) -> Unit = {},
) {
    private val mutex = Mutex()

    /** Returns the trips whose decision was applied. */
    suspend fun apply(): List<UUID> = mutex.withLock {
        val resolved = mutableListOf<UUID>()
        while (true) {
            val (id, decision) = queue.peek() ?: break
            if (id == activeTripId()) break
            try {
                val applied = when (decision) {
                    DraftDecision.confirm -> confirm(id)
                    DraftDecision.discard -> deletion.discardDraft(id).also { if (it) onDiscarded(id) }
                }
                if (applied) resolved += id
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "draft decision $decision failed", failure)
            } finally {
                queue.remove(id)
            }
        }
        resolved
    }

    private suspend fun confirm(id: UUID): Boolean {
        // False: not a draft or already confirmed; entering the world twice is not allowed.
        if (!trips.setConfirmation(TripConfirmation.confirmed, id)) return false
        val trip = trips.fetchTripDetail(id)?.let { it.withRecordingBreaks(it.recordingBreaks) } ?: return false
        val rewards = world.rewards(trip)
        onConfirmed(trip, rewards)
        // The draft's photos never reached the queue: the sync gate turned them away.
        trip.photos.forEach { sync.enqueue(SyncRequest(SyncEntityType.photo, it.id, SyncAction.upload)) }
        world.placesAndReveal(trip)
        rebuildTerritory()
        notices.clearDraftPrompt(id)
        // «Recording a trip» is one notice for every draft: not while another trip records.
        if (activeTripId() == null) notices.clearDraftStartedNotice()
        return true
    }

    private companion object {
        const val TAG = "DraftDecisions"
    }
}
