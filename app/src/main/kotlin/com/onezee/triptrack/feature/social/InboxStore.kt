// iOS: NotificationsInboxStore.swift, CompanionsStore.swift (the invite half: respond,
// respondedStatus, invitePreview, myTrips), NotificationInviteRowModel.swift
package com.onezee.triptrack.feature.social

import android.content.SharedPreferences
import androidx.core.content.edit
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.NetworkJson
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

data class InboxState(
    val items: List<NotificationItem> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val loadedOnce: Boolean = false,
    val loadFailed: Boolean = false,
)

/**
 * iOS: NotificationsInboxStore. Pull-based: refreshed when the inbox opens, by pull-to-refresh, on
 * the way back to the app (the count only) and when a push lands. The server stays authoritative
 * for `isRead`; this state is what the screen draws.
 *
 * Push delivery itself (FCM) comes later: [SocialFeature.pushArrived] / [SocialFeature.pushTapped]
 * are the seam a messaging service calls.
 */
class InboxStore(private val api: SocialApi, private val signedIn: () -> Boolean, private val worker: CoroutineDispatcher) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(InboxState())
    val state: StateFlow<InboxState> = mutable.asStateFlow()
    private var nextCursor: String? = null
    private var generation = 0
    private var loadingMore = false
    private val seenCursors = mutableSetOf<String>()
    private val reportedSeen = mutableSetOf<UUID>()
    /** Concurrent callers wait on the running refresh instead of interleaving two responses. */
    private var activeRefresh: Deferred<Unit>? = null
    private var pendingSeen = mutableSetOf<UUID>()
    private var seenFlush: Job? = null

    suspend fun refresh() {
        activeRefresh?.let { return it.await() }
        if (!signedIn()) { clear(); return }
        val task = scope.async { performRefresh() }
        activeRefresh = task
        try { task.await() } finally { if (activeRefresh === task) activeRefresh = null }
    }

    private suspend fun performRefresh() {
        val gen = ++generation
        mutable.update { it.copy(isLoading = true, loadFailed = false) }
        try {
            val (feed, count) = withContext(worker) {
                coroutineScope {
                    val feed = async { api.notifications(null) }
                    val count = async { api.unreadCount() }
                    feed.await() to count.await()
                }
            }
            if (gen != generation || !signedIn()) return
            nextCursor = feed.nextCursor?.takeIf { it.isNotEmpty() }
            seenCursors.clear()
            nextCursor?.let(seenCursors::add)
            reportedSeen.clear()
            mutable.update { it.copy(items = feed.items.distinctBy { row -> row.id }, hasMore = nextCursor != null, unreadCount = count) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (gen == generation) mutable.update { it.copy(loadFailed = true) } }
        finally { if (gen == generation) mutable.update { it.copy(isLoading = false, loadedOnce = true) } }
    }

    /** The cheap ping on the way back to the app: the count is honest without reloading the list. */
    suspend fun refreshUnreadOnly() {
        if (!signedIn()) return
        val gen = generation
        try { val count = withContext(worker) { api.unreadCount() }; if (gen == generation && signedIn()) mutable.update { it.copy(unreadCount = count) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {}
    }

    /**
     * The next page once the LAST loaded row shows. The buffered list is trimmed from the head
     * above [MAX_BUFFERED] so a long session stays bounded; pull-to-refresh starts over.
     */
    suspend fun loadMoreIfNeeded(current: NotificationItem) {
        val cursor = nextCursor ?: return
        if (loadingMore || state.value.isLoading || !state.value.hasMore || state.value.items.lastOrNull()?.id != current.id) return
        val gen = generation
        loadingMore = true
        try {
            val page = withContext(worker) { api.notifications(cursor) }
            if (gen != generation || !signedIn()) return
            nextCursor = page.nextCursor?.takeIf { it.isNotEmpty() && seenCursors.add(it) }
            mutable.update { old -> old.copy(items = (old.items + page.items).distinctBy { it.id }.takeLast(MAX_BUFFERED), hasMore = nextCursor != null) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {}
        finally { loadingMore = false }
    }

    /** Optimistic; put back on failure unless a refresh already replaced the row. */
    suspend fun markRead(item: NotificationItem) {
        val original = state.value.items.firstOrNull { it.id == item.id } ?: return
        if (original.isRead || item.id in reportedSeen) return
        val gen = generation
        mutable.update { old -> old.copy(items = old.items.map { if (it.id == item.id) it.copy(isRead = true) else it }, unreadCount = maxOf(0, old.unreadCount - 1)) }
        try { withContext(worker) { api.markRead(listOf(item.id)) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (gen == generation && state.value.items.firstOrNull { it.id == item.id }?.isRead == true) {
                mutable.update { old -> old.copy(items = old.items.map { if (it.id == item.id) item else it }, unreadCount = old.unreadCount + 1) }
            }
        }
    }

    /**
     * Read on sight, batched: a row that was displayed counts as read. The row on screen keeps its
     * «new» look (the highlight would die under the reader's eyes); the count drops and the server
     * hears at once, and the next opening reloads rows the server now calls read.
     */
    fun noteSeen(item: NotificationItem) {
        if (item.isRead || item.id in reportedSeen || state.value.items.none { it.id == item.id && !it.isRead }) return
        pendingSeen += item.id
        seenFlush?.cancel()
        seenFlush = scope.launch { delay(SEEN_DEBOUNCE_MILLIS); flushSeen() }
    }

    suspend fun flushSeen() {
        val gen = generation
        val ids = pendingSeen
        pendingSeen = mutableSetOf()
        if (ids.isEmpty()) return
        val flipped = state.value.items.count { it.id in ids && !it.isRead && it.id !in reportedSeen }
        reportedSeen += ids
        if (flipped == 0) return
        mutable.update { it.copy(unreadCount = maxOf(0, it.unreadCount - flipped)) }
        // Left optimistic on failure: the next count refresh corrects the badge, and showing read
        // rows as new again would be worse.
        ids.chunked(MARK_READ_BATCH).forEach { batch ->
            if (gen != generation || !signedIn()) return
            try { withContext(worker) { api.markRead(batch) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {}
        }
    }

    /** Server-side this marks ALL unread rows, not only the loaded page. */
    suspend fun markAllRead() {
        if (state.value.unreadCount <= 0) return
        mutable.update { old -> old.copy(items = old.items.map { it.copy(isRead = true) }, unreadCount = 0) }
        try { withContext(worker) { api.markRead(null) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {}
    }

    fun close() { clear(); scope.cancel() }

    fun flushOnDismiss() { scope.launch { flushSeen() } }

    fun clear() {
        generation++
        activeRefresh?.cancel()
        activeRefresh = null
        seenCursors.clear()
        reportedSeen.clear()
        nextCursor = null
        pendingSeen = mutableSetOf()
        seenFlush?.cancel()
        mutable.value = InboxState()
    }

    companion object {
        const val MAX_BUFFERED = 200
        const val MARK_READ_BATCH = 200
        const val SEEN_DEBOUNCE_MILLIS = 800L
    }
}

/** iOS NotificationItem.typedKind: an unknown raw kind stays a generic row. */
val NotificationItem.typedKind: NotificationKind? get() = NotificationKind.fromRawValue(kind)

/** iOS NotificationInviteRowModel.PreviewState. */
sealed interface InvitePreviewState {
    data object Loading : InvitePreviewState
    data class Loaded(val preview: CompanionInvitePreview) : InvitePreviewState
    data object Failed : InvitePreviewState
    /** Answered on another device: no longer a live invitation. */
    data object Unavailable : InvitePreviewState
}

/** iOS NotificationInviteRowModel: a pending, unanswered, still-live invite is a decision card. */
object NotificationInviteRows {
    fun isDecision(item: NotificationItem, answered: CompanionStatus?, preview: InvitePreviewState): Boolean =
        item.kind == NotificationKind.companionInvite.rawValue && answered == null && preview != InvitePreviewState.Unavailable

    /** A cancelled fetch is not a failure and must not be cached as one. */
    fun stateAfterFailure(wasCancelled: Boolean, isInviteGone: Boolean): InvitePreviewState? = when {
        wasCancelled -> null
        isInviteGone -> InvitePreviewState.Unavailable
        else -> InvitePreviewState.Failed
    }

    fun shouldContinuePaging(found: Boolean, failed: Boolean, hasMore: Boolean, pagesLoaded: Int, maxPages: Int): Boolean =
        !found && !failed && hasMore && pagesLoaded < maxPages
}

/**
 * iOS: CompanionsStore's invitation half. How this phone answered an invitation, kept across
 * launches under the iOS keys: by trip, AND by the notification that asked, so a new invitation to
 * the same trip is answerable again.
 */
class CompanionInvites(private val api: SocialApi, private val preferences: SharedPreferences, private val worker: CoroutineDispatcher) {
    private var generation = 0
    private val responded = MutableStateFlow(load(TRIPS_KEY).mapNotNull { (k, v) -> uuid(k)?.let { id -> v.toIntOrNull()?.let(CompanionStatus::fromRawValue)?.let { id to it } } }.toMap())
    private val invitations = MutableStateFlow(load(INVITATIONS_KEY).mapNotNull { (k, v) -> uuid(k)?.let { id -> uuid(v)?.let { id to it } } }.toMap())
    val answers: StateFlow<Map<UUID, CompanionStatus>> = responded.asStateFlow()

    fun respondedStatus(tripId: UUID, notificationId: UUID): CompanionStatus? =
        if (invitations.value[tripId] == notificationId) responded.value[tripId] else null

    /** Optimistic and persisted; rolled back (and rethrown) when the server refuses. */
    suspend fun respond(tripId: UUID, notificationId: UUID, accept: Boolean) {
        val gen = generation
        val previousStatus = responded.value[tripId]
        val previousInvitation = invitations.value[tripId]
        set(tripId, if (accept) CompanionStatus.accepted else CompanionStatus.declined, notificationId)
        try { withContext(worker) { api.respondToInvite(tripId, accept) } }
        catch (error: Exception) {
            if (gen == generation) set(tripId, previousStatus, previousInvitation)
            throw error
        }
    }

    suspend fun preview(tripId: UUID): CompanionInvitePreview = withContext(worker) { api.invitePreview(tripId) }

    /**
     * An accepted invitation's trip: there is no single-trip read for a trip one does not own, so it
     * is looked up in `/companions/my-trips`, at most [MAX_PAGES] pages.
     */
    suspend fun acceptedTrip(tripId: UUID): SocialFeedTrip? {
        var cursor: String? = null
        var pages = 0
        while (true) {
            val page = try { withContext(worker) { api.companionTrips(cursor) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { return null }
            pages++
            page.items.firstOrNull { it.id == tripId }?.let { return it }
            cursor = page.nextCursor
            if (!NotificationInviteRows.shouldContinuePaging(false, false, cursor != null, pages, MAX_PAGES)) return null
        }
    }

    fun clear() {
        generation++
        responded.value = emptyMap(); invitations.value = emptyMap()
        preferences.edit { remove(TRIPS_KEY); remove(INVITATIONS_KEY) }
    }

    private fun set(tripId: UUID, status: CompanionStatus?, invitation: UUID?) {
        responded.update { if (status == null) it - tripId else it + (tripId to status) }
        invitations.update { if (invitation == null) it - tripId else it + (tripId to invitation) }
        preferences.edit {
            putString(TRIPS_KEY, NetworkJson.wire.encodeToString(map, responded.value.entries.associate { it.key.toString().uppercase() to it.value.rawValue.toString() }))
            putString(INVITATIONS_KEY, NetworkJson.wire.encodeToString(map, invitations.value.entries.associate { it.key.toString().uppercase() to it.value.toString().uppercase() }))
        }
    }

    private fun load(key: String): Map<String, String> =
        preferences.getString(key, null)?.let { runCatching { NetworkJson.wire.decodeFromString(map, it) }.getOrNull() }.orEmpty()

    private fun uuid(raw: String) = runCatching { UUID.fromString(raw) }.getOrNull()

    private companion object {
        val map = MapSerializer(String.serializer(), String.serializer())
        const val TRIPS_KEY = "com.triptrack.companions.respondedTripIds"
        const val INVITATIONS_KEY = "com.triptrack.companions.respondedInvitationIds"
        const val MAX_PAGES = 5
    }
}
