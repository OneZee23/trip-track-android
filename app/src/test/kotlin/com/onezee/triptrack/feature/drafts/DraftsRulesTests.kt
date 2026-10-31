@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.onezee.triptrack.feature.drafts

import com.onezee.triptrack.feature.trip.*
import com.onezee.triptrack.model.Trip
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.test.*
import org.junit.Test

// iOS: DraftsLayoutTests.swift
class DraftsLayoutTests {
    private val se = DraftsLayout(667.0, 20.0, 0.0)
    private val mini = DraftsLayout(812.0, 50.0, 34.0)
    private val thirteen = DraftsLayout(844.0, 47.0, 34.0)
    private val sixteen = DraftsLayout(852.0, 59.0, 34.0)
    private val proMax = DraftsLayout(932.0, 59.0, 34.0)
    private val layouts = listOf(se, mini, thirteen, sixteen, proMax)
    @Test fun testHeaderAndButtonMatchTheSpecTable() {
        for ((layout, values) in listOf(thirteen to listOf(93.0, 690.0, 302.0), mini to listOf(96.0, 658.0, 286.0),
            sixteen to listOf(105.0, 698.0, 306.0), proMax to listOf(105.0, 778.0, 346.0))) {
            assertEquals(values, listOf(layout.headerBottom, layout.buttonTop, layout.dialogTop))
        }
    }
    @Test fun testSEMatchesAboveAndIsTwelveBelowUnderneath() { assertEquals(66.0, se.headerBottom); assertEquals(213.5, se.dialogTop, 0.51); assertEquals(525.0 - 12, se.buttonTop); assertEquals(154.0 + 12, se.listBottomInset) }
    @Test fun testListInsetIsTheSameEverywhere() { for (layout in layouts) assertEquals(166.0, layout.listBottomInset) }
    @Test fun testSelectionButtonsCoverTheSameRowAsTheSingleOne() {
        for (layout in listOf(thirteen, DraftsLayout(667.0, 20.0, 0.0, 375.0))) {
            val row = layout.width - 32
            assertEquals(row * 0.4, layout.deleteButtonWidth, 0.01); assertTrue(layout.deleteButtonWidth + 8 < row)
        }
    }
    @Test fun testEmptyStateSitsAtTheSameOpticalHeightEverywhere() {
        assertEquals(253.2, thirteen.emptyTop, 0.1)
        for (layout in layouts) { assertTrue(layout.emptyTop > layout.headerBottom + 40); assertTrue(layout.emptyTop < layout.height / 2) }
    }
    @Test fun testTheButtonAlwaysSitsBetweenTheHeaderAndTheTabBar() {
        for (layout in layouts) { assertTrue(layout.buttonTop + 52 < layout.tabBarTop); assertTrue(layout.buttonTop > layout.headerBottom + 100) }
    }
}

// iOS: DraftsGroupingTests.swift (including DraftsBadge)
class DraftsGroupingTests {
    private val zone = ZoneId.of("Europe/Moscow")
    private val now = Instant.parse("2026-09-27T09:00:00Z")
    private fun trip(date: Instant) = TripSummary.from(Trip(startDate = date, endDate = date.plusSeconds(600), distance = 1000.0, title = "t"))
    @Test fun testGroupsComeInOrderAndFreshestFirst() {
        val groups = DraftsGrouping.build(listOf(trip(now.minusSeconds(6 * 86400)), trip(now.minusSeconds(3600)), trip(now), trip(now.minusSeconds(86400))), now, zone)
        assertEquals(listOf(DraftBucket.today, DraftBucket.yesterday, DraftBucket.earlier), groups.map { it.bucket })
        assertEquals(2, groups[0].trips.size); assertEquals(now, groups[0].trips[0].startDate)
    }
    @Test fun testEmptyGroupsAreDropped() { assertEquals(listOf(DraftBucket.today), DraftsGrouping.build(listOf(trip(now)), now, zone).map { it.bucket }) }
    @Test fun testDayBoundaryIsCalendarNotTwentyFourHours() {
        val day = java.time.LocalDate.of(2026, 9, 27).atStartOfDay(zone).toInstant()
        val groups = DraftsGrouping.build(listOf(trip(day.plusSeconds(30 * 60)), trip(day.minusSeconds(2 * 3600))), day.plusSeconds(23 * 3600), zone)
        assertEquals(listOf(DraftBucket.today, DraftBucket.yesterday), groups.map { it.bucket })
    }
    @Test fun testTheDotBurnsOnlyForDraftsRecordedAfterTheListWasOpened() {
        assertFalse(DraftsBadge.hasUnseen(emptyList(), now))
        assertFalse(DraftsBadge.hasUnseen(listOf(trip(now.minusSeconds(60))), now))
        assertFalse(DraftsBadge.hasUnseen(listOf(trip(now)), now))
        assertTrue(DraftsBadge.hasUnseen(listOf(trip(now.minusSeconds(60)), trip(now.plusSeconds(1))), now))
        assertTrue(DraftsBadge.hasUnseen(listOf(trip(now)), null))
    }
}

// iOS: DraftsListView.swift (confirm/discard via DraftDecisionQueue)
class DraftsDecisionTests : PresentationTestCase() {
    private val decisions = mutableListOf<Pair<UUID, DraftDecision>>()
    private val queue = object : DraftDecisionQueue {
        override suspend fun enqueue(id: UUID, decision: DraftDecision) { decisions.add(id to decision) }
        override suspend fun decisionsQueued() = Unit
    }
    private val seen = object : DraftsSeenStore {
        var date: Instant? = null
        override suspend fun seenAt() = date
        override suspend fun markSeen(at: Instant) { date = at }
    }
    private fun vm() = own(DraftsViewModel(repository, queue, seen, dispatcher, { t0 }, ZoneId.of("UTC")))
    @Test fun confirmsThroughTheDurableQueueAndDoesNotConfirmTheRepositoryDirectly() = runTest(dispatcher) {
        val draft = insert(draft = true)
        val vm = vm(); vm.refresh().join()
        assertTrue(vm.state.value.hasUnseen)
        vm.opened().join(); advanceUntilIdle(); assertFalse(vm.state.value.hasUnseen)
        vm.selectAll(); vm.confirm()!!.join()
        assertEquals(listOf(draft.id to DraftDecision.confirm), decisions)
        assertTrue(vm.state.value.drafts.isEmpty()); assertFalse(vm.state.value.selecting)
        assertEquals("draft", db.tripDao().trip(draft.id)!!.confirmation)
        vm.refresh().join(); assertTrue(vm.state.value.drafts.isEmpty())
    }
    @Test fun deletionRequiresTheExactHouseConfirmationAndQueuesDiscard() = runTest(dispatcher) {
        val draft = insert(draft = true)
        val vm = vm(); vm.refresh().join(); vm.askDelete(listOf(draft.id))
        assertTrue(decisions.isEmpty()); assertNotNull(vm.state.value.confirmation)
        assertNull(vm.acceptDelete(UUID.randomUUID()))
        vm.acceptDelete(vm.state.value.confirmation!!.id)!!.join()
        assertEquals(listOf(draft.id to DraftDecision.discard), decisions)
        assertNotNull(db.tripDao().trip(draft.id))
    }
    /** AppConfirmDialog dismisses itself, then runs the answer: the answer must still find its question. */
    @Test fun anAnswerAfterTheDialogsOwnDismissalStillDiscardsOnce() = runTest(dispatcher) {
        val draft = insert(draft = true)
        val vm = vm(); vm.refresh().join(); vm.askDelete(listOf(draft.id))
        val confirmation = vm.state.value.confirmation!!
        vm.cancelDelete()
        assertNull(vm.state.value.confirmation)
        vm.acceptDelete(confirmation.id)!!.join()
        assertEquals(listOf(draft.id to DraftDecision.discard), decisions)
        assertNull(vm.acceptDelete(confirmation.id))
    }
}
