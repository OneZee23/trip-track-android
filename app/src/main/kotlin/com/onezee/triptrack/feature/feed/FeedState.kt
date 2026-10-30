package com.onezee.triptrack.feature.feed

import com.onezee.triptrack.feature.trip.TripMessage
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

// iOS: FeedView.swift (FeedMode)
enum class FeedMode { all, following, mine }

// iOS: TripFilters.swift. The mutable domain filter never escapes into screen state.
data class FeedFilters(val region: String? = null, val dateFrom: Instant? = null, val dateTo: Instant? = null)

// iOS: SocialDTOs.swift (SocialAuthor)
data class AuthorState(val id: UUID, val displayName: String?, val avatarEmoji: String?, val profileLevel: Int,
    val isPlus: Boolean?, val avatarFrame: String?) {
    companion object { fun from(author: SocialAuthor) = AuthorState(author.id, author.displayName, author.avatarEmoji, author.profileLevel, author.isPlus, author.avatarFrame) }
}

// iOS: SocialFeedCardView.swift (the journey row is part of each social card, never a fold).
data class SocialCardState(val trip: TripSummary, val author: AuthorState, val reactionCount: Int,
    val breakdown: List<ReactionTally>, val myReaction: String?, val commentCount: Int,
    val photoCount: Int, val thumbnail: String?, val journey: JourneyRowState?) {
    companion object {
        fun from(item: SocialFeedTrip) = SocialCardState(TripSummary.from(item.asTrip()), AuthorState.from(item.author),
            item.reactionCount, item.reactionBreakdown.toList(), item.myReaction, item.commentCountRaw ?: 0,
            item.photoCount, item.firstPhotoThumbnail, item.journey?.let { JourneyRowState(it.id, it.title, it.startDate, it.endDate) })
    }
}

// iOS: SocialFeedTripJourney.swift
data class JourneyRowState(val id: UUID, val title: String?, val startDate: Instant, val endDate: Instant?)

// iOS: HistoryFolding.swift
sealed interface MineRowState {
    data class TripRow(val trip: TripSummary) : MineRowState
    data class JourneyRow(val journey: JourneyRowState, val legs: List<TripSummary>) : MineRowState
}

// iOS: FeedViewModel.swift (TripSection)
data class TripSection(val month: YearMonth, val trips: List<TripSummary>)

// iOS: SocialFeedStore.swift
data class SocialFeedState(val cards: List<SocialCardState> = emptyList(), val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false, val nextCursor: String? = null, val hasMore: Boolean = true,
    val error: TripMessage? = null, val lastLoadedAt: Instant? = null)

// iOS: FeedViewModel.swift, FeedView.swift
data class FeedState(val mode: FeedMode = FeedMode.all, val filters: FeedFilters = FeedFilters(),
    val all: SocialFeedState = SocialFeedState(), val following: SocialFeedState = SocialFeedState(),
    val trips: List<TripSummary> = emptyList(), val sections: List<TripSection> = emptyList(),
    val mineRows: List<MineRowState> = emptyList(), val isLoadingMine: Boolean = false,
    val hasMoreMine: Boolean = true, val totalTripCount: Int = 0, val totalKm: Double = 0.0,
    val totalDuration: Double = 0.0, val kmByDay: Map<LocalDate, Double> = emptyMap(),
    val maxKmDay: Double = 1.0, val uniqueRegions: List<String> = emptyList(),
    val vehicleIdByTripId: Map<UUID, UUID> = emptyMap(), val hasAnyPrivateTrip: Boolean = false,
    val message: TripMessage? = null, val currentRecording: TripSummary? = null)
