package com.onezee.triptrack.feature.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.feature.trip.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// iOS: DraftDecisionQueue.swift. Enqueue must persist before returning; the service owns peek/remove.
enum class DraftDecision { confirm, discard }

// iOS: DraftDecisionQueue.swift
interface DraftDecisionQueue {
    suspend fun enqueue(id: UUID, decision: DraftDecision)
    suspend fun decisionsQueued()
}

// iOS: DraftsBadge.swift (drafts.seenAt)
interface DraftsSeenStore {
    suspend fun seenAt(): Instant?
    suspend fun markSeen(at: Instant)
}

// iOS: DraftsGrouping.swift
enum class DraftBucket { today, yesterday, earlier }

// iOS: DraftsGrouping.swift (Group)
data class DraftGroup(val bucket: DraftBucket, val trips: List<TripSummary>)

// iOS: DraftsBadge.swift
object DraftsBadge {
    fun hasUnseen(drafts: List<TripSummary>, seenAt: Instant?): Boolean = drafts.any { it.startDate > (seenAt ?: Instant.MIN) }
}

// iOS: DraftsGrouping.swift
object DraftsGrouping {
    fun build(trips: List<TripSummary>, now: Instant, zone: ZoneId): List<DraftGroup> {
        val today = now.atZone(zone).toLocalDate()
        val grouped = trips.sortedByDescending { it.startDate }.groupBy { trip ->
            when (trip.startDate.atZone(zone).toLocalDate()) {
                today -> DraftBucket.today
                today.minusDays(1) -> DraftBucket.yesterday
                else -> DraftBucket.earlier
            }
        }
        return DraftBucket.entries.mapNotNull { bucket -> grouped[bucket]?.let { DraftGroup(bucket, it) } }
    }
}

// iOS: DraftsLayout.swift. Geometry is in logical points; safe areas are supplied by the UI.
data class DraftsLayout(val height: Double, val safeTop: Double, val safeBottom: Double, val width: Double = 390.0) {
    val tabBarTop: Double get() = height - 22 - 68
    val headerBottom: Double get() = safeTop + 46
    val buttonTop: Double get() = tabBarTop - 64
    val listBottomInset: Double get() = 68.0 + 22 + 12 + 52 + 12
    val emptyTop: Double get() = height * 0.30
    val deleteButtonWidth: Double get() = (width - 32) * 0.4
    val dialogTop: Double get() = height / 2 - 120
    val tripMapHeight: Double get() = height * 0.45
}

// iOS: DraftsListView.swift
data class DraftsState(val drafts: List<TripSummary> = emptyList(), val groups: List<DraftGroup> = emptyList(),
    val selecting: Boolean = false, val selected: Set<UUID> = emptySet(), val hasUnseen: Boolean = false,
    val isLoading: Boolean = false, val isDeciding: Boolean = false, val confirmation: HouseConfirmation? = null,
    val message: TripMessage? = null)

// iOS: DraftsListView.swift, DraftsBadge.swift
class DraftsViewModel(
    private val repository: TripRepository,
    private val decisionQueue: DraftDecisionQueue,
    private val seenStore: DraftsSeenStore,
    private val worker: CoroutineDispatcher,
    private val clock: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
    changes: Flow<Unit> = emptyFlow(),
) : ViewModel() {
    private val mutable = MutableStateFlow(DraftsState())
    val state = mutable.asStateFlow()
    private var loadJob: Job? = null
    private val pendingIds = mutableSetOf<UUID>()
    init { viewModelScope.launch { changes.collect { refresh() } } }

    fun refresh(): Job {
        loadJob?.cancel()
        mutable.update { it.copy(isLoading = true) }
        return viewModelScope.launch {
            try {
                val (drafts, seen) = withContext(worker) { repository.fetchDraftTrips().map(TripSummary::from) to seenStore.seenAt() }
                publish(drafts.filterNot { it.id in pendingIds })
                mutable.update { it.copy(isLoading = false, hasUnseen = DraftsBadge.hasUnseen(it.drafts, seen), message = null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(isLoading = false, message = TripMessage.loadFailed) } }
        }.also { loadJob = it }
    }

    fun opened(): Job = viewModelScope.launch {
        try {
            withContext(worker) { seenStore.markSeen(clock()) }
            mutable.update { it.copy(hasUnseen = false) }
            refresh()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.update { it.copy(message = TripMessage.actionFailed) } }
    }
    fun setSelecting(selecting: Boolean) { mutable.update { it.copy(selecting = selecting, selected = emptySet()) } }
    fun toggle(id: UUID) { if (state.value.drafts.any { it.id == id }) mutable.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) } }
    fun selectAll() { mutable.update { it.copy(selecting = true, selected = it.drafts.map { trip -> trip.id }.toSet()) } }
    fun confirm(ids: List<UUID> = state.value.selected.toList()): Job? = decide(ids, DraftDecision.confirm)
    fun askDelete(ids: List<UUID> = state.value.selected.toList()) {
        val present = ids.distinct().filter { id -> state.value.drafts.any { it.id == id } }
        if (present.isNotEmpty()) mutable.update { it.copy(confirmation = HouseConfirmation(titleKey = "draftsDeleteTitle", messageKey = "draftsDeleteBody", destructiveKey = "delete", itemIds = present)) }
    }
    fun cancelDelete() { mutable.update { it.copy(confirmation = null) } }
    fun acceptDelete(confirmationId: UUID): Job? {
        val confirmation = state.value.confirmation?.takeIf { it.id == confirmationId } ?: return null
        mutable.update { it.copy(confirmation = null) }
        return decide(confirmation.itemIds, DraftDecision.discard)
    }

    private fun decide(ids: List<UUID>, decision: DraftDecision): Job? {
        if (state.value.isDeciding) return null
        val present = ids.distinct().filter { id -> state.value.drafts.any { it.id == id } }
        if (present.isEmpty()) return null
        mutable.update { it.copy(isDeciding = true) }
        return viewModelScope.launch {
            try {
                // Remove only durable decisions: a failed persistence write must leave its question visible.
                for (id in present) {
                    withContext(worker) { decisionQueue.enqueue(id, decision) }
                    pendingIds.add(id)
                    publish(state.value.drafts.filterNot { it.id == id })
                }
                withContext(worker) { decisionQueue.decisionsQueued() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(message = TripMessage.actionFailed) } }
            finally { mutable.update { it.copy(isDeciding = false) } }
        }
    }
    private fun publish(drafts: List<TripSummary>) {
        mutable.update { it.copy(drafts = drafts, groups = DraftsGrouping.build(drafts, clock(), zone),
            selected = it.selected.intersect(drafts.map { trip -> trip.id }.toSet()), selecting = it.selecting && drafts.isNotEmpty()) }
    }
}
