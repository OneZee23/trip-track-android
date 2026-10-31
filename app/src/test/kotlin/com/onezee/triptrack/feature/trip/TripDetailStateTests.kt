@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.onezee.triptrack.feature.trip

import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.db.entity.VehicleEntity
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.util.UUID
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test

// iOS: TripDetailFirstPaintTests.swift (known values, without rendering)
class TripDetailFirstPaintTests : PresentationTestCase() {
    @Test fun testDistanceValueIsPresentOnTheFirstLightFrame() {
        val preview = Trip(startDate = t0, endDate = t0.plusSeconds(60), distance = 49_000.0)
        val vm = own(TripDetailViewModel(preview.id, repository, api, dispatcher, { owner }, preview))
        assertEquals(49_000.0, vm.state.value.trip!!.distance)
        assertEquals(49.0, DistanceUnit.km.distance(vm.state.value.trip!!.distance))
        assertTrue(vm.state.value.isLoading)
    }
    @Test fun testDurationSegmentsArePresentOnTheFirstDarkFrame() {
        val preview = Trip(startDate = t0, endDate = t0.plusSeconds(4 * 3600 + 58 * 60))
        val vm = own(TripDetailViewModel(preview.id, repository, api, dispatcher, { owner }, preview))
        assertEquals((4 * 3600 + 58 * 60).toDouble(), vm.state.value.trip!!.duration)
    }
}

// iOS: TripDetailView.swift (tripDetailAsync, replaceTrip), TripDetailSharePrivacyTests.swift
class TripDetailStateTests : PresentationTestCase() {
    @Test fun fullTripIsReadOnTheInjectedWorkerAndMissingTripEndsLoading() = runTest(dispatcher) {
        val worker = StandardTestDispatcher(scheduler, "detail-worker")
        val repository = object : TripRepository by this@TripDetailStateTests.repository {
            override suspend fun fetchTripDetail(id: UUID): Trip? {
                assertSame(worker, currentCoroutineContext()[ContinuationInterceptor])
                return null
            }
        }
        val vm = own(TripDetailViewModel(UUID.randomUUID(), repository, api, worker, { owner }))
        vm.load().join()
        assertFalse(vm.state.value.isLoading); assertTrue(vm.state.value.loadFailed)
    }
    @Test fun replacingTheSameIdInvalidatesPendingPhotoGeometry() = runTest(dispatcher) {
        val t = Trip(startDate = t0)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var calls = 0
        val preparer = TripPhotoPreparer { trip ->
            if (calls++ == 0) { firstStarted.complete(Unit); withContext(NonCancellable) { releaseFirst.await() } }
            TripPhotoPlacement.prepare(trip.photos, trip.checkpoints, trip.trackPoints, trip.startDate)
        }
        val vm = own(TripDetailViewModel(t.id, repository, api, dispatcher, { owner }, photoPreparer = preparer))
        val old = vm.replaceTrip(t)
        firstStarted.await()
        val cp = TripCheckpoint(timestamp = t0, latitude = 45.0, longitude = 39.0, distanceFromStart = 0.0, elapsedFromStart = 0.0)
        vm.replaceTrip(t.copy(checkpoints = listOf(cp))).join()
        releaseFirst.complete(Unit); old.join()
        assertEquals(2L, vm.state.value.geometryRevision)
        assertEquals(listOf(cp.id), vm.state.value.checkpoints.map { it.id })
        assertEquals(cp.id, vm.state.value.moments.single().checkpointId)
    }
    @Test fun everyOwnTripShowsMomentsEvenWhenTheTimelineIsEmpty() = runTest(dispatcher) {
        val trip = insert()
        val vm = own(TripDetailViewModel(trip.id, repository, api, dispatcher, { owner }))
        vm.load().join()
        assertTrue(vm.state.value.showsMoments); assertTrue(vm.state.value.moments.isEmpty())
    }
    @Test fun remoteOwnedTripExportsItsRemoteTrackWithoutCreatingLocalRows() = runTest(dispatcher) {
        val card = api.card(author = owner, day = t0)
        api.remoteTrip = { SocialTripResponse(card, listOf(SocialTrackPoint(45.0, 39.0, 12.5, t0), SocialTrackPoint(45.1, 39.1, 13.0, t0.plusSeconds(60)))) }
        val vm = own(TripDetailViewModel(card.id, repository, api, dispatcher, { owner }, social = card))
        vm.load().join(); vm.export(TripExportFormat.csv)!!.join()
        assertTrue(vm.state.value.isOwn); assertTrue(vm.state.value.isRemoteBacked)
        assertContains(vm.state.value.exported!!.content, "12.500000")
        assertEquals(3, vm.state.value.exported!!.content.lines().size)
        assertEquals(0, repository.fetchTripCount())
    }
    @Test fun deleteWaitsForHouseConfirmationAndKeepsTheSubjectAfterDismissal() = runTest(dispatcher) {
        val trip = insert()
        val vm = own(TripDetailViewModel(trip.id, repository, api, dispatcher, { owner }))
        vm.load().join(); vm.requestDelete()
        assertNotNull(repository.fetchTripDetail(trip.id))
        val confirmation = vm.state.value.confirmation!!
        assertEquals("deleteTrip", confirmation.titleKey)
        assertNull(vm.acceptConfirmation(UUID.randomUUID()))
        vm.acceptConfirmation(confirmation.id)!!.join()
        assertNull(vm.state.value.confirmation); assertTrue(vm.state.value.deleted)
        assertNull(repository.fetchTripDetail(trip.id))
    }
    /** AppConfirmDialog dismisses itself, then runs the answer; a dismissal alone deletes nothing. */
    @Test fun deleteStillRunsWhenTheDialogDismissesBeforeAnswering() = runTest(dispatcher) {
        val kept = insert()
        val keeper = own(TripDetailViewModel(kept.id, repository, api, dispatcher, { owner }))
        keeper.load().join(); keeper.requestDelete(); keeper.cancelConfirmation()
        assertNull(keeper.state.value.confirmation); assertNotNull(repository.fetchTripDetail(kept.id))

        val trip = insert(day = 1)
        val vm = own(TripDetailViewModel(trip.id, repository, api, dispatcher, { owner }))
        vm.load().join(); vm.requestDelete()
        val confirmation = vm.state.value.confirmation!!
        vm.cancelConfirmation()
        vm.acceptConfirmation(confirmation.id)!!.join()
        assertTrue(vm.state.value.deleted)
        assertNull(repository.fetchTripDetail(trip.id))
        assertNull(vm.acceptConfirmation(confirmation.id))
    }
    @Test fun privateForeignTripsCannotBeSharedAndEmptyTrackCannotBeExported() = runTest(dispatcher) {
        val trip = Trip(startDate = t0, isPrivate = true)
        val vm = own(TripDetailViewModel(trip.id, repository, api, dispatcher, { owner }))
        vm.replaceTrip(trip, own = false).join()
        assertFalse(vm.state.value.canShare); assertFalse(vm.state.value.showsMoments)
        assertNull(vm.export(TripExportFormat.gpx)); assertEquals(TripMessage.exportFailed, vm.state.value.message)
    }
    @Test fun localPublishRequiresAnAccountBeforeFlippingPrivacy() = runTest(dispatcher) {
        val t = insert()
        val vm = own(TripDetailViewModel(t.id, repository, api, dispatcher, { null }))
        vm.load().join(); vm.requestPublish()
        assertFalse(vm.state.value.publishRequested)
        assertEquals(TripMessage.signInToPublish, vm.state.value.message)
        assertTrue(repository.fetchTripDetail(t.id)!!.isPrivate)
    }
    @Test fun replayUsesOneShapePreservingSelectionForAllThreeArrays() {
        val points = (0..999).map { i -> TrackPoint(latitude = 45.0 + i * 0.00001,
            longitude = 39.0 + kotlin.math.sin(i.toDouble()) * 0.001, speed = i.toDouble(), timestamp = t0.plusSeconds(i.toLong())) }
        val replay = TripReplay.prepare(points)
        assertEquals(300, replay.coords.size)
        assertEquals(points.first().coordinate, replay.coords.first()); assertEquals(points.last().coordinate, replay.coords.last())
        for (i in replay.coords.indices) {
            val source = points[replay.speeds[i].toInt()]
            assertEquals(source.coordinate, replay.coords[i]); assertEquals(source.timestamp, replay.timestamps[i])
        }
    }
    @Test fun hybridModeChangesScreenStateImmediatelyAndReusesTheDayEnergyRule() = runTest(dispatcher) {
        val vehicleId = UUID.randomUUID()
        db.vehicleDao().upsertVehicle(VehicleEntity(vehicleId, powertrain = "pluginHybrid", electricRangeKm = 50.0, electricConsumption = 20.0))
        val t = insert(km = 100.0)
        repository.updateVehicle(t.id, vehicleId)
        val vm = own(TripDetailViewModel(t.id, repository, api, dispatcher, { owner }, vehicles = vehicleStore))
        vm.load().join()
        assertTrue(vm.state.value.canChooseEnergyMode)
        assertEquals(50_000.0, vm.state.value.energyEstimate!!.electricMetres)
        val change = vm.setEnergyMode(TripEnergyMode.fuel)!!
        assertEquals(TripEnergyMode.fuel, vm.state.value.energyMode)
        change.join()
        assertEquals(0.0, vm.state.value.energyEstimate!!.electricMetres)
        assertEquals(TripEnergyMode.fuel, repository.fetchTripDetail(t.id)!!.energyMode)
    }
    @Test fun remotePrivacyFlipKeepsTheMetadataInsteadOfReloadingTheOriginalFeedCopy() = runTest(dispatcher) {
        val card = api.card(author = owner, day = t0)
        val vm = own(TripDetailViewModel(card.id, repository, api, dispatcher, { owner }, social = card))
        vm.load().join(); vm.requestHide()!!.join()
        vm.acceptConfirmation(vm.state.value.confirmation!!.id)!!.join()
        assertTrue(vm.state.value.trip!!.isPrivate)
        assertEquals(card.id, vm.state.value.trip!!.id)
    }
}
