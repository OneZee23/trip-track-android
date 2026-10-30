@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.onezee.triptrack.feature.journey

import com.onezee.triptrack.feature.trip.*
import com.onezee.triptrack.data.sync.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.sync.*
import java.time.ZoneId
import kotlin.test.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import org.junit.Test

// iOS: JourneyPublishTests.swift
class JourneyPublishTests : PresentationTestCase() {
    private fun manager() = JourneyManager(repository, sink, { authorized }, { cloud })
    @Test fun testPublishFlipsEachPrivateLegThroughItsOwnUpsert() = runTest(dispatcher) {
        val a = insert(); val b = insert(day = 1); val c = insert(day = 2, private = false)
        val manager = manager()
        val j = manager.create(listOf(a, b, c), "Грузия")
        assertEquals(setOf(a.id, b.id), manager.privateLegs(j).map { it.id }.toSet())
        sink.operations.clear()
        manager.publish(j.id)
        for (id in listOf(a.id, b.id, c.id)) assertFalse(repository.fetchTripDetail(id)!!.isPrivate)
        val queue = SyncQueue(isAuthorized = { true }, isOffline = { true })
        val gate = SyncEnqueuer(db, queue, { true }, { false }, scope, {})
        for (request in sink.operations) gate.enqueueIfAllowed(request)
        gate.close()
        assertEquals(setOf(a.id, b.id), queue.state.value.pending.filter { it.entityType == SyncEntityType.trip }.map { it.entityId }.toSet())
        assertFalse(repository.fetchJourney(j.id)!!.isPrivate)
        assertEquals(SyncEntityType.journey, sink.operations.last().entityType)
        assertTrue(queue.state.value.pending.any { it.entityType == SyncEntityType.journey && it.entityId == j.id })
    }
    @Test fun testHideLeavesLegsAlone() = runTest(dispatcher) {
        val a = insert()
        val manager = manager()
        val j = manager.create(listOf(a), null)
        manager.publish(j.id); sink.operations.clear()
        manager.hide(j.id)
        assertTrue(repository.fetchJourney(j.id)!!.isPrivate)
        assertFalse(repository.fetchTripDetail(a.id)!!.isPrivate)
        assertEquals(listOf(SyncAction.unpublish), sink.operations.map { it.action })
    }
    @Test fun creationPersistsUncheckedTripsInsideTheWindowAsExclusions() = runTest(dispatcher) {
        val a = insert(); val unchecked = insert(day = 1); val b = insert(day = 2)
        val j = manager().create(listOf(a, b), "  coast  ")
        assertEquals(listOf(unchecked.id), j.excludedTripIds)
        assertEquals("coast", j.title)
        assertEquals(setOf(a.id, b.id), repository.trips(j).map { it.id }.toSet())
    }
    @Test fun foreignJourneyUsesItsPayloadAndDoesNotMaterializeLocalRows() = runTest(dispatcher) {
        val card = api.card(day = t0)
        val response = SocialJourneyResponse(SocialJourneyHead(java.util.UUID.randomUUID(), startDate = t0,
            isPrivate = false, legCount = 1, author = card.author), listOf(card))
        val vm = own(JourneyDetailViewModel(response.journey.id, repository, manager(), dispatcher, { authorized }, social = response))
        vm.reload().join()
        assertEquals(listOf(card.id), vm.state.value.trips.map { it.id })
        assertNull(vm.state.value.cover); assertTrue(vm.state.value.isSocial)
        assertEquals(0, repository.fetchTripCount())
        assertNull(vm.requestPublish())
    }
    @Test fun signedOutPublishDoesNotFlipAnyLeg() = runTest(dispatcher) {
        val a = insert(); val manager = manager(); val j = manager.create(listOf(a), null)
        authorized = false
        assertFailsWith<JourneyError.SignInRequired> { manager.publish(j.id) }
        assertTrue(repository.fetchTripDetail(a.id)!!.isPrivate)
    }
}

// iOS: JourneyEditWindowTests.swift (screen-state portion), JourneyComposerSheet.swift
class JourneyEditStateTests : PresentationTestCase() {
    private val zone = ZoneId.of("UTC")
    private fun manager() = JourneyManager(repository, sink, { authorized }, { cloud })
    @Test fun emptyFutureWindowCanStillBeRenamedAndClampsBothPickers() = runTest(dispatcher) {
        val j = repository.saveJourney(Journey(startDate = t0.plusSeconds(5 * 86400), endDate = t0.plusSeconds(20 * 86400)))
        val vm = own(JourneyEditViewModel(j, repository, manager(), dispatcher, t0, zone))
        vm.state.first { !it.isLoading }
        assertEquals(t0, vm.state.value.start); assertEquals(t0, vm.state.value.end)
        assertTrue(vm.state.value.canSave)
        vm.setTitle("renamed"); vm.save()!!.join()
        val saved = repository.fetchJourney(j.id)!!
        assertEquals("renamed", saved.title)
        assertEquals(t0.atZone(zone).toLocalDate(), saved.startDate.atZone(zone).toLocalDate())
    }
    @Test fun renamingKeepsStoredHoursAndDateChangesUseCalendarEndOfDay() = runTest(dispatcher) {
        val a = insert(day = -6); val b = insert(day = -2)
        val manager = manager(); val j = manager.create(listOf(a, b), "title")
        val vm = own(JourneyEditViewModel(j, repository, manager, dispatcher, t0, zone))
        vm.state.first { !it.isLoading }; vm.setTitle("new"); vm.save()!!.join()
        assertEquals(j.startDate, repository.fetchJourney(j.id)!!.startDate)
        assertEquals(j.endDate, repository.fetchJourney(j.id)!!.endDate)
        vm.setEnd(t0.minusSeconds(86400)); vm.state.first { !it.isLoading }
        assertEquals(JourneyWindow.endOfDay(t0.minusSeconds(86400), zone), vm.plannedWindow().endDate)
    }
    @Test fun returningAnExcludedLegChangesCountButWaitsForSave() = runTest(dispatcher) {
        val a = insert(day = -2); val b = insert(day = -1)
        val j = repository.saveJourney(Journey(startDate = a.startDate, endDate = b.endDate, excludedTripIds = listOf(b.id)))
        val vm = own(JourneyEditViewModel(j, repository, manager(), dispatcher, t0, zone))
        vm.state.first { !it.isLoading }; assertEquals(1, vm.state.value.windowTripCount)
        vm.toggleReturning(b.id); vm.state.first { !it.isLoading }
        assertEquals(2, vm.state.value.windowTripCount)
        assertEquals(listOf(b.id), repository.fetchJourney(j.id)!!.excludedTripIds)
        assertEquals(listOf(b.id), vm.state.value.removedLegs.map { it.id })
        vm.save()!!.join(); assertTrue(repository.fetchJourney(j.id)!!.excludedTripIds.isEmpty())
    }
    @Test fun composerPreselectsOnlyTheChainAndShowsNeighboursUnchecked() = runTest(dispatcher) {
        fun route(a: Double, b: Double) = Trip.encodePolyline(listOf(GeoPoint(45.0, a), GeoPoint(45.0, b)))
        val a = insert(day = -2); val b = insert(day = -1); val unrelated = insert()
        db.tripDao().upsertTrip(db.tripDao().trip(a.id)!!.copy(previewPolyline = route(39.0, 40.0)))
        db.tripDao().upsertTrip(db.tripDao().trip(b.id)!!.copy(previewPolyline = route(40.0, 41.0)))
        db.tripDao().upsertTrip(db.tripDao().trip(unrelated.id)!!.copy(previewPolyline = route(50.0, 51.0)))
        val anchor = repository.fetchTripDetail(a.id)!!
        val vm = own(JourneyComposerViewModel(anchor, manager(), dispatcher, zone = zone))
        vm.state.first { !it.isLoading }
        assertEquals(setOf(a.id, b.id), vm.state.value.selected)
        assertEquals(3, vm.state.value.candidates.size)
        vm.toggle(a.id); assertFalse(a.id in vm.state.value.selected)
    }
    @Test fun suppliedPreselectionIsVisibleOnFirstStateAndDoesNotAddNeighbours() = runTest(dispatcher) {
        val a = insert(); insert(day = -1)
        val vm = own(JourneyComposerViewModel(a, manager(), dispatcher, listOf(a)))
        assertEquals(listOf(a.id), vm.state.value.candidates.map { it.id })
        assertEquals(setOf(a.id), vm.state.value.selected)
        vm.setTitle("a".repeat(61)); assertEquals(60, vm.state.value.title.length)
    }
}
