package com.onezee.triptrack.location

import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.location.recording.StartRefusal
import com.onezee.triptrack.location.recording.StartResult
import com.onezee.triptrack.location.recording.DiscardReason
import com.onezee.triptrack.location.recording.StopOutcome
import com.onezee.triptrack.logic.PostTripTrackProcessor
import com.onezee.triptrack.logic.RecordingMath
import com.onezee.triptrack.logic.openGaps
import com.onezee.triptrack.logic.SyntheticDrives
import com.onezee.triptrack.logic.TrackTestKit
import com.onezee.triptrack.logic.LocationFix
import com.onezee.triptrack.location.provider.LocationSource
import com.onezee.triptrack.location.provider.TrackingMode
import com.onezee.triptrack.model.RoadFillState
import com.onezee.triptrack.model.TrackGapFinder
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripConfirmation
import com.onezee.triptrack.model.TripJunkClassifier
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

// iOS: TripManager.swift and MapViewModel.swift recording paths; CoarseFixRecordingTests.swift,
// RecordingBreaksPersistenceTests.swift and TrackReplayTests.swift for the numbers.
class RecordingLifecycleTests : RecorderTestCase() {
    private suspend fun started(confirmation: TripConfirmation = TripConfirmation.confirmed): UUID {
        val result = recorder.start(confirmation = confirmation)
        return assertIs<StartResult.Started>(result).tripId
    }

    @Test
    fun startWritesAPrivateUnfinishedRowStampedWithARecordableCar() = test {
        val car = vehicles.addVehicle("Polo", "🚗")
        settings.save { it.copy(selectedVehicleId = car) }
        val id = started()
        val row = assertNotNull(db.tripDao().trip(id))
        assertNull(row.endDate)
        assertTrue(row.isPrivate)
        assertEquals(car, row.vehicleId)
        assertEquals(localUserId, row.userId)
        assertEquals(TripConfirmation.confirmed.rawValue, row.confirmation)
        assertTrue(recorder.state.value.isRecording)
    }

    // iOS: SettingsManager.recordableVehicleId, the last check at the stamp.
    @Test
    fun aSoldCarTakesNoNewTripAndAPassengerTripNone() = test {
        val sold = vehicles.addVehicle("Old", "🚙")
        vehicles.setVehicleSold(sold, now)
        settings.save { it.copy(selectedVehicleId = sold) }
        assertNull(db.tripDao().trip(started())!!.vehicleId)
        recorder.stop()
        val car = vehicles.addVehicle("Polo", "🚗")
        val passenger = assertIs<StartResult.Started>(recorder.start(vehicleId = car, isTransfer = true)).tripId
        val row = db.tripDao().trip(passenger)!!
        assertNull(row.vehicleId)
        assertTrue(row.isTransfer)
    }

    @Test
    fun startIsRefusedWithAReasonNotSilently() = test {
        started()
        assertEquals(StartResult.Refused(StartRefusal.alreadyRecording), recorder.start())
        recorder.stop()
        locationAllowed = false
        assertEquals(StartResult.Refused(StartRefusal.locationDenied), recorder.start())
    }

    // iOS: TripManager.handleNewLocation, «every 10 points or every 15 seconds».
    @Test
    fun pointsReachTheDiskInBatchesOfTen() = test {
        val id = started()
        drive(straight(0..8))
        assertEquals(0, storedPointCount(id), "nine stored points wait in memory")
        drive(straight(9..9, startNorth = 90.0))
        assertEquals(10, storedPointCount(id))
        assertEquals(90.0, db.tripDao().trip(id)!!.distance, 0.5, "the running distance is saved with its points")
    }

    @Test
    fun fifteenSecondsSaveASmallerBatch() = test {
        val id = started()
        // A point every four seconds: the batch closes on time, not on count.
        drive(listOf(0, 4, 8, 12).map { TrackTestKit.fix(north = it * 10.0, speed = 10.0, after = it.toDouble()) })
        assertEquals(0, storedPointCount(id))
        drive(listOf(TrackTestKit.fix(north = 160.0, speed = 10.0, after = 16.0)))
        assertEquals(5, storedPointCount(id))
    }

    // iOS: the provider gate (`FixGate`) runs before the recording and its verdict is journaled.
    @Test
    fun theProviderGateAndTheRawJournalSeeEveryFix() = test {
        val id = started()
        val good = TrackTestKit.fix(north = 0.0, speed = 10.0, after = 0.0)
        val rough = TrackTestKit.fix(north = 10.0, speed = 10.0, after = 1.0, accuracy = 250.0)
        drive(listOf(good, rough))
        now = TrackTestKit.at(2.0)
        recorder.onProviderFix(TrackTestKit.fix(north = 20.0, speed = 10.0, after = 2.0), ageSeconds = 30.0)
        val lines = java.io.File(rawFixLog.directory, "${id.toString().uppercase()}.csv").readLines()
        assertEquals(listOf("accept", "reject:accuracy", "reject:stale"), lines.drop(1).map { it.substringAfterLast(',') })
    }

    @Test
    fun gpsWatchdogUsesEveryProviderFixAndDoesNotRunWhilePaused() = test {
        val id = started()
        var callback: ((LocationFix, Double) -> Unit)? = null
        val source = object : LocationSource {
            override fun start(mode: TrackingMode, onFix: (LocationFix, Double) -> Unit) { callback = onFix }
            override fun stop() { callback = null }
        }
        source.start(TrackingMode.recording) { fix, age ->
            blocking { recorder.onProviderFix(fix, age) }
        }
        callback!!(TrackTestKit.fix(north = 0.0, speed = 0.0, after = 0.0), 0.0)
        now = TrackTestKit.at(29.0)
        recorder.tick()
        assertFalse(recorder.state.value.gpsLost)
        now = TrackTestKit.at(30.0)
        recorder.tick()
        assertTrue(recorder.state.value.gpsLost)
        callback!!(TrackTestKit.fix(north = 10.0, speed = 0.0, after = 31.0, accuracy = 250.0), 0.0)
        now = TrackTestKit.at(31.0)
        recorder.tick()
        assertFalse(recorder.state.value.gpsLost, "a rejected provider fix still proves GPS is delivering")
        val log = java.io.File(rawFixLog.directory, "${id.toString().uppercase()}.csv").readText()
        assertTrue(log.contains(" gpsLost\n"))
        recorder.setPaused(true)
        now = TrackTestKit.at(90.0)
        recorder.tick()
        assertFalse(recorder.state.value.gpsLost)
        source.stop()
    }

    // iOS: TripManager.isPaused, RecordingBreaksPersistenceTests.
    @Test
    fun aPauseIsSavedAtOnceAndItsStretchCountsNothing() = test {
        val id = started()
        drive(straight(0..59))
        now = TrackTestKit.at(60.0)
        recorder.setPaused(true)
        val row = db.tripDao().trip(id)!!
        assertNotNull(row.recordingBreaksJSON, "the boundary is on disk before anything else happens")
        assertEquals(60, storedPointCount(id), "the pause flushes the batch with it")
        // Fixes during the pause move the speedometer, not the trip.
        drive(straight(61..120, startNorth = 600.0))
        assertEquals(60, storedPointCount(id))
        now = TrackTestKit.at(121.0)
        recorder.setPaused(false)
        // The car resumes three kilometres away: the jump across the pause is not driven.
        drive(straight(122..181, startNorth = 3_600.0))
        recorder.stop()
        val trip = repo.fetchTripDetail(id)!!
        assertEquals(1, trip.recordingBreaks.size)
        assertTrue(trip.distance in 1_150.0..1_220.0, "two minutes at 10 m/s, no jump: ${trip.distance}")
    }

    @Test
    fun elapsedTimeLeavesThePausesOut() = test {
        started()
        now = TrackTestKit.at(100.0)
        recorder.setPaused(true)
        now = TrackTestKit.at(160.0)
        assertEquals(100.0, recorder.state.value.elapsedSeconds(now), 0.001)
        recorder.setPaused(false)
        now = TrackTestKit.at(200.0)
        assertEquals(140.0, recorder.state.value.elapsedSeconds(now), 0.001)
    }

    @Test
    fun stopFinishesTheRowAndEntersTheWorld() = test {
        val id = started()
        drive(straight(0..180))
        now = TrackTestKit.at(181.0)
        val outcome = assertIs<StopOutcome.Saved>(recorder.stop())
        val row = db.tripDao().trip(id)!!
        assertNotNull(row.endDate)
        assertNotNull(row.previewPolyline)
        assertTrue(row.isTrackProcessed, "post-processing ran on the final track")
        assertEquals(RoadFillState.done.rawValue, row.roadFillState)
        assertTrue(row.distance in 1_790.0..1_810.0, "three minutes at 10 m/s: ${row.distance}")
        assertTrue(row.xpEarned > 0)
        assertEquals(row.xpEarned, outcome.rewards.xpEarned)
        assertTrue(settings.settings()!!.profileXP > 0)
        assertTrue(SyncRequestOf(id, SyncAction.upload) in sink.enqueued, "the upload is queued by the world door")
        assertFalse(recorder.state.value.isRecording)
        assertFalse(recorder.state.value.isFinishing)
    }

    // iOS: RoadCollectionManager.processTrip through TripWorldEntry.rewards.
    @Test
    fun theSameRoadTwiceLevelsTheRoadAndCountsBothTrips() = test {
        started()
        drive(straight(0..180))
        val first = assertIs<StopOutcome.Saved>(recorder.stop()).rewards.roadCard!!
        assertTrue(first.isNew)
        assertEquals(1, first.timesDriven)
        now = TrackTestKit.at(4_000.0)
        started()
        drive(straight(4_000..4_180))
        val second = assertIs<StopOutcome.Saved>(recorder.stop()).rewards.roadCard!!
        assertEquals(2, second.timesDriven, "the history count includes the earlier trip")
        assertFalse(second.isNew)
        val road = db.mapDao().roads().single()
        assertEquals(2, road.timesDriven)
    }

    @Test
    fun aShortHopIsDeletedAsJunkWithItsJournal() = test {
        val id = started()
        drive(straight(0..59, speed = 2.0))
        now = TrackTestKit.at(60.0)
        val outcome = assertIs<StopOutcome.Discarded>(recorder.stop())
        assertEquals(DiscardReason.tooShort, outcome.reason)
        assertNull(db.tripDao().trip(id))
        assertFalse(java.io.File(rawFixLog.directory, "${id.toString().uppercase()}.csv").exists())
        assertTrue(sink.enqueued.none { it.entityId == id && it.action == SyncAction.upload })
    }

    @Test
    fun anExtendedSlowRecordingIsKeptAsADraftWithItsJunkReason() = test {
        val id = started()
        drive((0..181).map { TrackTestKit.fix(north = it * 0.2, speed = 0.2, after = it.toDouble()) })
        now = TrackTestKit.at(300.0)
        val outcome = assertIs<StopOutcome.AwaitingConfirmation>(recorder.stop())
        assertEquals(TripJunkClassifier.Reason.noDrivingSpeed, outcome.preservedJunkReason)
        assertEquals(TripConfirmation.draft, outcome.trip.confirmation)
        assertEquals(TripConfirmation.draft.rawValue, db.tripDao().trip(id)?.confirmation)
        assertEquals(listOf(id), notices.draftPrompts)
    }

    @Test
    fun aGpsLostRecordingWithoutPointsIsDiscardedInsteadOfSavedAsADraft() = test {
        val id = started()
        now = TrackTestKit.at(30.0)
        recorder.tick()
        assertTrue(recorder.state.value.gpsLost)
        now = TrackTestKit.at(60.0)
        val outcome = assertIs<StopOutcome.Discarded>(recorder.stop())
        assertEquals(DiscardReason.noTrackPoints, outcome.reason)
        assertNull(db.tripDao().trip(id))
    }

    // iOS: TripWorldEntry.route, a draft gets its question and stays out of the world.
    @Test
    fun aDraftWaitsForItsAnswer() = test {
        val id = started(TripConfirmation.draft)
        drive(straight(0..180))
        now = TrackTestKit.at(181.0)
        assertIs<StopOutcome.AwaitingConfirmation>(recorder.stop())
        assertEquals(listOf(id), notices.draftPrompts)
        val row = db.tripDao().trip(id)!!
        assertEquals(TripConfirmation.draft.rawValue, row.confirmation)
        assertEquals(0, row.xpEarned, "no rewards before «Mine»")
        assertTrue(row.isTrackProcessed, "the track is processed for drafts too")
        assertTrue(sink.enqueued.none { it.entityId == id && it.entityType == SyncEntityType.trip })
    }

    // The batching and the storage must not change a single metre: the same synthetic drive through
    // RecordingMath and the pure post-processor gives the same points and the same odometer.
    @Test
    fun theCityEveningDriveKeepsItsNumbersThroughStorage() = test {
        val fixes = SyntheticDrives.cityEvening()
        now = fixes.first().timestamp
        val id = started()
        drive(fixes)
        recorder.stop()
        val stored = repo.fetchTripDetail(id)!!

        val math = RecordingMath(Trip(startDate = fixes.first().timestamp))
        fixes.forEach { math.handleNewLocation(it, it.timestamp) }
        val replay = PostTripTrackProcessor.process(math.trip.copy(endDate = fixes.last().timestamp))
        assertEquals(replay.trackPoints.count { !it.isInterpolated }, stored.trackPoints.count { !it.isInterpolated })
        assertEquals(replay.distance, stored.distance, 1e-6)
        assertEquals(0, TrackGapFinder.openGaps(stored.trackPoints.map {
            TrackGapFinder.Point(it.latitude, it.longitude, it.timestamp, it.isInterpolated, 0)
        }).size)
    }

    // iOS: TrackReplayTests, the tunnel: the gap is filled straight at the finish and waits for a road.
    @Test
    fun aTunnelIsFilledAtTheFinish() = test {
        val fixes = SyntheticDrives.tunnel()
        now = fixes.first().timestamp
        val id = started()
        drive(fixes)
        recorder.stop()
        val trip = repo.fetchTripDetail(id)!!
        assertTrue(trip.trackPoints.any { it.isInterpolated })
        assertEquals(RoadFillState.pending, trip.roadFillState)
        assertTrue(SyncRequestOf(id, SyncAction.update) in sink.enqueued, "a filled track is a trip edit")
    }

    @Test
    fun aSecondStopDoesNothing() = test {
        started()
        drive(straight(0..10))
        recorder.stop()
        assertEquals(StopOutcome.NotRecording, recorder.stop())
    }

    @Test
    fun theFinishPreviewAgreesWithTheStop() = test {
        val id = started()
        drive(straight(0..120))
        val preview = assertNotNull(recorder.finishPreview())
        assertEquals(id, preview.tripID)
        recorder.stop()
        assertEquals(preview.distance, db.tripDao().trip(id)!!.distance, 1e-6)
    }

    private fun SyncRequestOf(id: UUID, action: SyncAction) =
        com.onezee.triptrack.data.sync.SyncRequest(SyncEntityType.trip, id, action)
}
