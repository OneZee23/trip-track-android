@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.onezee.triptrack.feature.feed

import com.onezee.triptrack.feature.trip.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.ApiError
import java.time.ZoneId
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import org.junit.Test

// iOS: FeedViewModel.swift / SocialFeedStore.swift loading rules, FeedPreviewCachePerfTests.swift
class FeedLoadingTests : PresentationTestCase() {
    private var now = t0
    private var offline = false
    private fun vm() = own(FeedViewModel(repository, api, dispatcher, { authorized }, { offline }, { now }, ZoneId.of("UTC")))
    @Test fun firstShowDoesNotCancelTheInFlightRequest() = runTest(dispatcher) {
        val response = CompletableDeferred<SocialFeedResponse>()
        api.feedPage = { response.await() }
        val vm = vm()
        val first = vm.loadIfNeeded()!!
        runCurrent()
        assertTrue(vm.state.value.all.isLoading)
        assertNull(vm.loadIfNeeded())
        response.complete(SocialFeedResponse(listOf(api.card())))
        first.join()
        assertEquals(1, api.requests.size)
        assertNull(vm.loadIfNeeded())
        now = now.plusSeconds(900)
        vm.loadIfNeeded()!!.join()
        assertEquals(2, api.requests.size)
    }
    @Test fun explicitRefreshCancelsFirstAndKeepsLoadingUntilItsSuccessorCompletes() = runTest(dispatcher) {
        val response = CompletableDeferred<SocialFeedResponse>()
        api.feedPage = { response.await() }
        val vm = vm()
        val first = vm.refresh()!!
        runCurrent()
        val second = vm.refresh()!!
        runCurrent()
        assertTrue(first.isCancelled)
        assertTrue(vm.state.value.all.isLoading)
        response.complete(SocialFeedResponse(listOf(api.card())))
        second.join()
        assertFalse(vm.state.value.all.isLoading)
    }
    @Test fun stalePaginationCannotAppendOrRewindTheFreshCursor() = runTest(dispatcher) {
        val old = api.card()
        val fresh = api.card()
        val gate = CompletableDeferred<SocialFeedResponse>()
        api.feedPage = { request -> if (request.cursor == "old") gate.await() else SocialFeedResponse(listOf(old), "old") }
        val vm = vm(); vm.refresh()!!.join()
        val more = vm.loadMore(FeedMode.all, old.id)!!
        runCurrent()
        api.feedPage = { SocialFeedResponse(listOf(fresh), "fresh") }
        vm.refresh()!!.join()
        gate.complete(SocialFeedResponse(listOf(old), "rewound")); more.join()
        assertEquals(listOf(fresh.id), vm.state.value.all.cards.map { it.trip.id })
        assertEquals("fresh", vm.state.value.all.nextCursor)
    }
    @Test fun socialCardsKeepTheJourneyRowAndDeduplicateAppend() = runTest(dispatcher) {
        val journey = SocialFeedTripJourney(UUID.randomUUID(), startDate = t0)
        val card = api.card(journey = journey)
        val next = api.card(journey = journey)
        api.feedPage = { if (it.cursor == null) SocialFeedResponse(listOf(card), "next") else SocialFeedResponse(listOf(card, next)) }
        val vm = vm(); vm.refresh()!!.join(); vm.loadMore(FeedMode.all, card.id)!!.join()
        assertEquals(2, vm.state.value.all.cards.size)
        assertEquals(listOf(journey.id, journey.id), vm.state.value.all.cards.map { it.journey!!.id })
    }
    @Test fun privateLocalIntentWinsOverAStaleServerPage() = runTest(dispatcher) {
        val local = insert(private = true)
        api.feedPage = { SocialFeedResponse(listOf(api.card(id = local.id))) }
        val vm = vm(); vm.refresh()!!.join()
        assertTrue(vm.state.value.all.cards.isEmpty())
    }
    @Test fun ownPaginationUsesTwentyAndLastFiveAndStatsUseAllFilteredTrips() = runTest(dispatcher) {
        val trips = (0..44).map { insert(day = -it, km = 1.0) }
        val vm = vm(); vm.loadTrips().join()
        assertEquals(20, vm.state.value.trips.size); assertEquals(45, vm.state.value.totalTripCount); assertEquals(45.0, vm.state.value.totalKm)
        vm.loadMoreMine(trips[14].id); assertEquals(20, vm.state.value.trips.size)
        vm.loadMoreMine(trips[15].id); assertEquals(40, vm.state.value.trips.size)
        vm.loadMoreMine(trips[39].id); assertEquals(45, vm.state.value.trips.size); assertFalse(vm.state.value.hasMoreMine)
    }
    @Test fun onlyMineFoldsJourneyLegsAndDateFilterKeepsTheWholeSelectedDay() = runTest(dispatcher) {
        val a = insert(day = -1); val b = insert()
        repository.saveJourney(Journey(startDate = a.startDate, endDate = b.endDate))
        val vm = vm(); vm.loadTrips().join()
        assertEquals(1, vm.state.value.mineRows.size)
        assertIs<MineRowState.JourneyRow>(vm.state.value.mineRows.single())
        vm.setFilters(FeedFilters(dateFrom = b.startDate))
        assertEquals(listOf(b.id), vm.state.value.trips.map { it.id })
        vm.resetSecondaryFilters(); assertEquals(b.startDate, vm.state.value.filters.dateFrom)
    }
    @Test fun foldingUsesTheFullFilteredHistorySoAPageCannotChangeAJourneyTotal() = runTest(dispatcher) {
        val trips = (0..24).map { insert(day = -it, km = 1.0) }
        repository.saveJourney(Journey(startDate = trips.last().startDate, endDate = trips.first().endDate))
        val vm = vm(); vm.loadTrips().join()
        assertEquals(20, vm.state.value.trips.size)
        val row = assertIs<MineRowState.JourneyRow>(vm.state.value.mineRows.single())
        assertEquals(25, row.legs.size); assertEquals(25_000.0, row.legs.sumOf { it.distance })
    }
    @Test fun emptyTransientColdStartRetriesExactlyTwiceAndOfflineDoesNotRetry() = runTest(dispatcher) {
        api.feedPage = { throw ApiError.InvalidHttpStatus(503) }
        val vm = vm(); vm.refresh()!!.join(); advanceUntilIdle()
        assertEquals(3, api.requests.size)
        offline = true
        vm.refresh(); advanceUntilIdle()
        assertEquals(3, api.requests.size); assertFalse(vm.state.value.all.isLoading)
    }
    @Test fun reactionToggleStillSendsAfterTheCardWasEvicted() = runTest(dispatcher) {
        val card = api.card(reaction = "❤️")
        api.feedPage = { SocialFeedResponse(listOf(card)) }
        val vm = vm(); vm.refresh()!!.join()
        api.feedPage = { SocialFeedResponse(listOf(api.card())) }; vm.refresh()!!.join()
        var sent: String? = null
        api.reaction = { id, previous, _ -> assertEquals(card.id, id); sent = previous }
        vm.toggleReaction(card.id, "❤️")!!.join()
        assertEquals("❤️", sent)
    }
    @Test fun anUnsupersededColdStartCancellationRetriesWithoutUserRefresh() = runTest(dispatcher) {
        api.feedPage = { if (api.requests.size == 1) throw CancellationException("token refresh") else SocialFeedResponse(listOf(api.card())) }
        val vm = vm(); vm.refresh()!!.join(); advanceUntilIdle()
        vm.state.first { it.all.cards.isNotEmpty() }
        assertEquals(2, api.requests.size); assertEquals(1, vm.state.value.all.cards.size)
    }
    @Test fun failedReactionFromThePreviousSessionCannotRestoreRememberedState() = runTest(dispatcher) {
        val card = api.card(reaction = "❤️")
        api.feedPage = { SocialFeedResponse(listOf(card)) }
        val vm = vm(); vm.refresh()!!.join()
        val gate = CompletableDeferred<Unit>()
        api.reaction = { _, _, _ -> gate.await(); throw ApiError.InvalidHttpStatus(500) }
        val reacting = vm.toggleReaction(card.id, "❤️")!!; runCurrent()
        vm.clearSocial(); gate.complete(Unit); reacting.join()
        var previous: String? = "stale"
        api.reaction = { _, remembered, _ -> previous = remembered }
        vm.toggleReaction(card.id, "❤️")!!.join()
        assertNull(previous); assertTrue(vm.state.value.all.cards.isEmpty())
    }
    @Test fun followingReactionUsesItsOwnSnapshotWhenAllHasAnOlderCopy() = runTest(dispatcher) {
        val card = api.card(reaction = "❤️")
        api.feedPage = { SocialFeedResponse(listOf(if (it.type == "following") card else card.copy(myReaction = null))) }
        val vm = vm(); vm.refresh(FeedMode.all)!!.join(); vm.refresh(FeedMode.following)!!.join()
        var previous: String? = null
        api.reaction = { _, remembered, _ -> previous = remembered }
        vm.toggleReaction(card.id, "❤️", FeedMode.following)!!.join()
        assertEquals("❤️", previous); assertNull(vm.state.value.following.cards.single().myReaction)
    }
}
