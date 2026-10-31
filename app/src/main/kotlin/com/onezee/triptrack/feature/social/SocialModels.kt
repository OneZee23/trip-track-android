// iOS: ProfileTripsPager.swift, ProfileUnavailableState.swift, SocialReactions.swift,
// PublicJourneyAdapter.swift, TripSource.swift (RemoteTripSource, PublicTripDTO.asTrip),
// SocialDTOs.swift (feedTrip, showsPaidCosmetics)
package com.onezee.triptrack.feature.social

import com.onezee.triptrack.designsystem.components.PlusBadgeVisibility
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.ApiEndpoint
import com.onezee.triptrack.network.ApiError
import com.onezee.triptrack.network.PublicTripDTO
import java.util.Base64
import java.util.UUID

/**
 * iOS: ProfileTripsPager. The header says «Поездки · 45» while the profile brings the ten newest;
 * the rest come in pages from `/users/:id/profile-trips` with the feed cursor, the first page
 * continuing from the last of the ten without an extra request. A value, so what to ask, when to
 * stop and how not to double a card are tested, not scrolled.
 */
data class ProfileTripsPager(
    val cursor: String? = null,
    /** The server promises more trips than there are: without it ten trips would ask for nothing. */
    val exhausted: Boolean = true,
    val isLoading: Boolean = false,
    private val seenCursors: Set<String> = emptySet(),
) {
    fun shouldLoad(appearedIndex: Int, loadedCount: Int): Boolean =
        !exhausted && !isLoading && cursor != null && appearedIndex >= loadedCount - PREFETCH_DISTANCE

    fun begin() = copy(isLoading = true)

    /** New cards are appended; repeats (page borders, a profile refresh mid-scroll) are dropped. */
    fun apply(page: SocialFeedResponse, cards: List<SocialFeedTrip>): Pair<ProfileTripsPager, List<SocialFeedTrip>> {
        val known = cards.mapTo(HashSet()) { it.id }
        val merged = cards + page.trips.filter { known.add(it.id) }
        val next = page.nextCursor?.takeIf { it.isNotEmpty() && it !in seenCursors }
        return (if (next != null) copy(isLoading = false, cursor = next, seenCursors = seenCursors + next)
        else copy(isLoading = false, cursor = null, exhausted = true)) to merged
    }

    /** The page did not come: what is shown stays, the list stops growing until the next refresh. */
    fun fail() = copy(isLoading = false, exhausted = true)

    companion object {
        const val PAGE_SIZE = 20
        /** The same threshold as PublicJourneysView: scrolling never stops to wait for the network. */
        const val PREFETCH_DISTANCE = 3

        /** After the profile loaded: its ten previews and how many public trips there are in all. */
        fun reset(preview: List<SocialFeedTrip>, publicCount: Int?): ProfileTripsPager {
            val last = preview.lastOrNull()
            if (last == null || (publicCount ?: 0) <= preview.size) return ProfileTripsPager()
            return ProfileTripsPager(cursor = ApiEndpoint.feedCursor(last.startDate, last.id), exhausted = false)
        }
    }
}

/**
 * iOS: ProfileUnavailableState. The server answers USER_NOT_FOUND alike for «no such account»,
 * «closed» and «blocked», on purpose: a link must not tell them apart. The one exception is an
 * identity that came with the tap (seen in the public feed a moment ago): naming it hides nothing.
 */
sealed interface ProfileUnavailableState {
    data class Closed(val name: String, val avatar: String?) : ProfileUnavailableState
    data object Unavailable : ProfileUnavailableState
    /** The network, a ban, an unknown code: a failed load, which offers a retry, not a follow. */
    data object Transient : ProfileUnavailableState

    companion object {
        fun from(error: Throwable, preloaded: SocialAuthor?): ProfileUnavailableState {
            if (error !is ApiError.UnknownServer || error.code != "USER_NOT_FOUND") return Transient
            // An empty name gives nothing worth showing: «Закрытый профиль» under a nameless disc
            // reads as a broken screen.
            val name = preloaded?.displayName?.trim().orEmpty()
            return if (name.isEmpty()) Unavailable else Closed(name, preloaded?.avatarEmoji)
        }
    }
}

/** iOS SocialProfile.showsPaidCosmetics: the live subscription, and the badge flag on an old server. */
val SocialProfile.showsPaidCosmetics: Boolean get() = PlusBadgeVisibility.saysPlus(hasPlus ?: isPlus)

/** What the owner lets others see; a server without the feature sends no block, which means open. */
val SocialProfile.openBlocks: SocialProfileVisibility get() = visibility ?: SocialProfileVisibility()

/** iOS SocialProfileRecentTrip.feedTrip: the feed's item; the fallback author only for a pre-feed server. */
fun SocialProfileRecentTrip.feedTrip(fallbackAuthor: SocialAuthor): SocialFeedTrip = SocialFeedTrip(
    id = id, author = author ?: fallbackAuthor, title = title, description = description, startDate = startDate,
    endDate = endDate, distance = distance, duration = duration ?: legacyDurationSeconds, maxSpeed = maxSpeed,
    elevation = elevation, maxAltitude = maxAltitude, drivingTime = drivingTime, stoppedTime = stoppedTime,
    region = region, isPrivate = isPrivate, previewPolyline = previewPolyline, photoCount = photoCount ?: 0,
    firstPhotoThumbnail = firstPhotoThumbnail, vehicle = vehicle, reactionCount = reactionCount ?: 0,
    reactionBreakdown = reactionBreakdown.orEmpty(), myReaction = myReaction, badgeIds = badgeIds.orEmpty(),
    commentCountRaw = commentCount,
    // The profile carries no journey: `/users/:id/profile` builds its items without the key.
    journey = null,
)

/** A pre-feed payload's duration: its moving time, else start to end; zero when neither came. */
private val SocialProfileRecentTrip.legacyDurationSeconds: Int get() {
    drivingTime?.takeIf { it > 0 }?.let { return it }
    val end = endDate ?: return 0
    return maxOf(0L, end.epochSecond - startDate.epochSecond).toInt()
}

val SocialFeedTrip.commentCount: Int get() = commentCountRaw ?: 0

fun SocialFeedTrip.withCommentCount(count: Int) = copy(commentCountRaw = count)

/** iOS SocialFeedTrip.with(reactionCount:myReaction:): the breakdown rebuilt for the optimistic toggle. */
fun SocialFeedTrip.withReaction(count: Int, mine: String?): SocialFeedTrip {
    val breakdown = reactionBreakdown.associate { it.emoji to it.count }.toMutableMap()
    myReaction?.let { old -> breakdown[old] = (breakdown[old] ?: 1) - 1; if ((breakdown[old] ?: 0) <= 0) breakdown.remove(old) }
    mine?.let { breakdown[it] = (breakdown[it] ?: 0) + 1 }
    return copy(reactionCount = count, myReaction = mine,
        reactionBreakdown = breakdown.map { ReactionTally(it.key, it.value) }.sortedByDescending { it.count })
}

/** iOS togglingReaction: same emoji takes it back, another moves it, none of mine adds one. */
fun SocialFeedTrip.togglingReaction(emoji: String): SocialFeedTrip {
    val same = myReaction == emoji
    val count = when {
        same -> maxOf(0, reactionCount - 1)
        myReaction != null -> reactionCount
        else -> reactionCount + 1
    }
    return withReaction(count, if (same) null else emoji)
}

/** iOS Trip(publicLeg:): a light leg of someone else's journey, coordinates from its preview. */
fun PublicJourneyLeg.asTrip(): Trip {
    val seconds = (duration ?: 0).toDouble()
    return Trip(id = id, startDate = startDate, endDate = endDate ?: startDate.plusMillis((seconds * 1000).toLong()),
        distance = distance, averageSpeed = if (seconds > 0) distance / seconds else 0.0, region = region, isPrivate = false,
        previewPolyline = previewPolyline?.let(::decodedPolyline), isOnServer = true)
}

/** iOS Journey(publicJourney:ownerId:): values in, never written locally. */
fun PublicJourneyDto.asJourney(ownerId: UUID) = Journey(id = id, userId = ownerId, title = title, startDate = startDate,
    endDate = endDate, coverPhotoId = coverPhotoId, isPrivate = false)

/**
 * iOS PublicTripDTO.asTrip. A broken base64 draws an empty map, so it is dropped explicitly; an open
 * trip ends at its driving time, or it would grow while one watches.
 */
fun PublicTripDTO.asTrip(): Trip {
    val seconds = (duration ?: 0).toDouble()
    val end = endDate ?: if (seconds > 0) startDate.plusMillis((seconds * 1000).toLong()) else startDate
    return Trip(id = id, startDate = startDate, endDate = end, distance = distance,
        averageSpeed = if (seconds > 0) distance / seconds else 0.0, title = title, region = region,
        // The endpoint sends only public trips; a private flag here would mean the filter leaked.
        isPrivate = false, previewPolyline = previewPolyline?.let(::decodedPolyline), isOnServer = true)
}

internal fun decodedPolyline(base64: String): ByteArray? = runCatching { Base64.getDecoder().decode(base64) }.getOrNull()

/** iOS TripSourceResult: the trips AND whether the load failed; an empty list alone would lie. */
data class TripSourceResult(val trips: List<Trip>, val failed: Boolean)

/**
 * iOS RemoteTripSource. Someone's public trips page by page: 500 per page (the server's ceiling),
 * at most 20 pages, deduplicated by id. A failed page, a repeated cursor or the page cap all return
 * what was collected marked failed: a partial map with confident numbers looks complete.
 */
object RemoteTrips {
    const val PAGE_SIZE = 500
    private const val MAX_PAGES = 20

    suspend fun load(api: SocialApi, accountId: UUID, vehicleId: UUID? = null): TripSourceResult {
        val collected = mutableListOf<Trip>()
        val seenIds = HashSet<UUID>()
        val seenCursors = HashSet<String>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val page = try { api.publicTrips(accountId, cursor, PAGE_SIZE, vehicleId) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { return TripSourceResult(collected, true) }
            page.trips.filter { seenIds.add(it.id) }.mapTo(collected) { it.asTrip() }
            val next = page.nextCursor?.takeIf { it.isNotEmpty() } ?: return TripSourceResult(collected, false)
            if (!seenCursors.add(next)) return TripSourceResult(collected, true)
            cursor = next
        }
        return TripSourceResult(collected, true)
    }
}
