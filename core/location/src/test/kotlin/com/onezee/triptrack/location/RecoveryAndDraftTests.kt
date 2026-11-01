package com.onezee.triptrack.location

import android.content.Context
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.location.drafts.DraftDecision
import com.onezee.triptrack.location.drafts.PreferencesDraftDecisionQueue
import com.onezee.triptrack.location.recording.OrphanTriage
import com.onezee.triptrack.location.recording.StartRefusal
import com.onezee.triptrack.location.recording.StartResult
import com.onezee.triptrack.location.recording.StopOutcome
import com.onezee.triptrack.logic.TrackTestKit
import com.onezee.triptrack.logic.isDraft
import com.onezee.triptrack.model.TripConfirmation
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

// iOS: TripManager.cleanupOrphanedTrips / adoptRecoverableOrphan (RecoveryWindowTests.swift),
// DraftDecisionQueueTests.swift, ApplyDraftDecisionsTests.swift.
class RecoveryAndDraftTests : RecorderTestCase() {
    /** A recording that dies with the process after [seconds] of driving. */
    private suspend fun recordingThatDies(seconds: Int, from: Int = 0): UUID {
        now = TrackTestKit.at(from.toDouble())
        val id = assertIs<StartResult.Started>(recorder.start()).tripId
        // Every batch has reached the disk; what the process held in memory is gone with it.
        drive(straight(from until from + seconds))
        relaunch()
        return id
    }

    private fun journal(id: UUID) = File(rawFixLog.directory, "${id.toString().uppercase()}.csv")

    @Test
    fun aRecordingKilledMomentsAgoResumesWithoutAQuestion() = test {
        val id = recordingThatDies(120)
        now = TrackTestKit.at(125.0)
        val recovery = assertNotNull(recorder.recoverOrphans())
        assertTrue(recovery.isFresh)
        assertEquals(id, recovery.orphan.id)
        assertTrue(recorder.adoptRecoverable())
        val state = recorder.state.value
        assertTrue(state.isRecording)
        assertEquals(id, state.tripId)
        assertNull(state.recovery)
        // The journal is appended to, not truncated.
        val before = journal(id).readLines().size
        drive(straight(126..240, startNorth = 1_200.0))
        assertTrue(journal(id).readLines().size > before)
        recorder.stop()
        val trip = repo.fetchTripDetail(id)!!
        assertNotNull(trip.endDate)
        assertTrue(trip.distance > 2_000, "one trip across the death: ${trip.distance}")
    }

    @Test
    fun anOlderRecordingWaitsForTheQuestionAndBlocksAutoStarts() = test {
        recordingThatDies(120)
        now = TrackTestKit.at(119.0 + OrphanTriage.silentResumeWindowSeconds + 60)
        val recovery = assertNotNull(recorder.recoverOrphans())
        assertFalse(recovery.isFresh)
        assertFalse(recorder.state.value.isRecording)
        assertEquals(StartResult.Refused(StartRefusal.recoveryPending), recorder.start())
    }

    @Test
    fun finishAndSaveAdoptsThenStops() = test {
        val id = recordingThatDies(180)
        now = TrackTestKit.at(3_600.0)
        recorder.recoverOrphans()
        assertTrue(recorder.adoptRecoverable())
        assertIs<StopOutcome.Saved>(recorder.stop())
        assertNotNull(db.tripDao().trip(id)!!.endDate)
    }

    @Test
    fun beyondSixHoursAnOrphanIsClosedNotOffered() = test {
        val id = recordingThatDies(180)
        now = TrackTestKit.at(179.0 + OrphanTriage.maxRestorableAgeSeconds + 1)
        assertNull(recorder.recoverOrphans())
        val row = db.tripDao().trip(id)!!
        assertEquals(TrackTestKit.at(179.0), row.endDate, "closed at its last fix")
        assertNotNull(row.previewPolyline)
    }

    @Test
    fun emptyAndJunkOrphansDisappearAndOnlyTheNewestIsOffered() = test {
        val empty = assertIs<StartResult.Started>(recorder.start()).tripId
        relaunch()
        val junk = recordingThatDies(30, from = 10)
        now = TrackTestKit.at(1_000.0)
        val older = assertIs<StartResult.Started>(recorder.start()).tripId
        drive(straight(1_000 until 1_200))
        relaunch()
        now = TrackTestKit.at(2_000.0)
        val newest = assertIs<StartResult.Started>(recorder.start()).tripId
        drive(straight(2_000 until 2_200))
        relaunch()
        now = TrackTestKit.at(2_210.0)
        val recovery = assertNotNull(recorder.recoverOrphans())
        assertEquals(newest, recovery.orphan.id)
        assertNull(db.tripDao().trip(empty))
        assertNull(db.tripDao().trip(junk), "a short hop is junk even when it died")
        assertNotNull(db.tripDao().trip(older)!!.endDate, "the other orphan is closed, never a zombie")
        assertNull(db.tripDao().trip(newest)!!.endDate)
    }

    // Android: a recording that died while paused comes back paused (RecorderSession).
    @Test
    fun aPausedRecordingComesBackPaused() = test {
        val id = assertIs<StartResult.Started>(recorder.start()).tripId
        drive(straight(0 until 120))
        now = TrackTestKit.at(120.0)
        recorder.setPaused(true)
        relaunch()
        now = TrackTestKit.at(300.0)
        recorder.recoverOrphans()
        assertTrue(recorder.adoptRecoverable())
        assertEquals(id, recorder.state.value.tripId)
        assertTrue(recorder.state.value.isPaused)
        assertEquals(TrackTestKit.at(120.0), recorder.state.value.pauseStartedAt)
    }

    // iOS: DraftDecisionQueueTests.
    @Test
    fun theQueueSurvivesANewInstanceAndTheLastDecisionWins() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        PreferencesDraftDecisionQueue(context).apply {
            enqueue(first, DraftDecision.discard)
            enqueue(second, DraftDecision.confirm)
            enqueue(first, DraftDecision.confirm)
        }
        val reborn = PreferencesDraftDecisionQueue(context)
        assertEquals(first to DraftDecision.confirm, reborn.peek(), "the latest decision, in its first place")
        assertEquals(first to DraftDecision.confirm, reborn.peek(), "peek does not remove")
        reborn.remove(UUID.randomUUID())
        reborn.remove(first)
        assertEquals(listOf(second to DraftDecision.confirm), reborn.drain())
        assertNull(PreferencesDraftDecisionQueue(context).peek())
    }

    @Test
    fun theQueueIsStoredUnderTheIosKey() {
        val id = UUID.randomUUID()
        PreferencesDraftDecisionQueue(context).enqueue(id, DraftDecision.discard)
        val raw = context.getSharedPreferences(PreferencesDraftDecisionQueue.FILE_NAME, Context.MODE_PRIVATE)
            .getString("draftTrips.pendingDecisions", null)
        assertEquals("[[\"$id\",\"discard\"]]", raw)
    }

    /** A finished draft, its question posted, then the process dies before anyone answers. */
    private suspend fun finishedDraftThenDeath(): UUID {
        val id = assertIs<StartResult.Started>(recorder.start(confirmation = TripConfirmation.draft)).tripId
        drive(straight(0..240))
        assertIs<StopOutcome.AwaitingConfirmation>(recorder.stop())
        relaunch()
        return id
    }

    // «Mine» on the notification of a dead app: the receiver queues, a new process applies.
    @Test
    fun mineFromTheNotificationAfterDeathEntersTheWorld() = test {
        val id = finishedDraftThenDeath()
        sink.enqueued.clear()
        PreferencesDraftDecisionQueue(context).enqueue(id, DraftDecision.confirm)
        relaunch()
        assertEquals(listOf(id), drafts.apply())
        val row = db.tripDao().trip(id)!!
        assertEquals(TripConfirmation.confirmed.rawValue, row.confirmation)
        assertTrue(row.xpEarned > 0, "the rewards a finish would have given")
        assertEquals(1, sink.enqueued.count { it == SyncRequest(SyncEntityType.trip, id, SyncAction.upload) })
        assertTrue(id in notices.cleared)
        assertNull(PreferencesDraftDecisionQueue(context).peek())
        // Answered twice: nothing happens the second time.
        PreferencesDraftDecisionQueue(context).enqueue(id, DraftDecision.confirm)
        assertEquals(emptyList<UUID>(), drafts.apply())
    }

    @Test
    fun deleteFromTheNotificationAfterDeathLeavesNoTrace() = test {
        val id = finishedDraftThenDeath()
        assertTrue(journal(id).exists())
        PreferencesDraftDecisionQueue(context).enqueue(id, DraftDecision.discard)
        relaunch()
        assertEquals(listOf(id), drafts.apply())
        assertNull(db.tripDao().trip(id))
        assertFalse(journal(id).exists(), "the raw journal of maybe somebody else's drive goes too")
        assertTrue(id in notices.cleared)
    }

    @Test
    fun aDecisionNeverTouchesTheTripStillRecording() = test {
        val id = assertIs<StartResult.Started>(recorder.start(confirmation = TripConfirmation.draft)).tripId
        PreferencesDraftDecisionQueue(context).enqueue(id, DraftDecision.discard)
        assertEquals(emptyList<UUID>(), drafts.apply())
        assertNotNull(db.tripDao().trip(id))
        assertEquals(id to DraftDecision.discard, PreferencesDraftDecisionQueue(context).peek(), "it waits for the end")
    }

    @Test
    fun aConfirmedTripIsNotDiscardedByAStaleDecision() = test {
        val id = assertIs<StartResult.Started>(recorder.start()).tripId
        drive(straight(0..240))
        recorder.stop()
        PreferencesDraftDecisionQueue(context).enqueue(id, DraftDecision.discard)
        assertEquals(emptyList<UUID>(), drafts.apply())
        assertNotNull(db.tripDao().trip(id))
    }

    // iOS: MapViewModel.applyDraftDecisions. «Mine» replaces the open question with its rewards.
    @Test
    fun confirmingTheDisplayedDraftUpdatesItsSummaryWithoutAwardingTwice() = test {
        val id = assertIs<StartResult.Started>(recorder.start(confirmation = TripConfirmation.draft)).tripId
        drive(straight(0..240))
        assertIs<StopOutcome.AwaitingConfirmation>(recorder.stop())
        PreferencesDraftDecisionQueue(context).enqueue(id, DraftDecision.confirm)
        assertEquals(listOf(id), drafts.apply())
        val saved = assertIs<StopOutcome.Saved>(recorder.completion.value)
        assertEquals(id, saved.trip.id)
        assertFalse(saved.trip.isDraft)
        assertTrue(saved.rewards.xpEarned > 0)
        PreferencesDraftDecisionQueue(context).enqueue(id, DraftDecision.confirm)
        assertTrue(drafts.apply().isEmpty())
        assertEquals(saved, recorder.completion.value)
    }

    @Test
    fun discardingTheDisplayedDraftRemovesItsSummaryAndPreservesANewRecording() = test {
        val id = assertIs<StartResult.Started>(recorder.start(confirmation = TripConfirmation.draft)).tripId
        drive(straight(0..240))
        assertIs<StopOutcome.AwaitingConfirmation>(recorder.stop())
        val active = assertIs<StartResult.Started>(recorder.start()).tripId
        PreferencesDraftDecisionQueue(context).enqueue(id, DraftDecision.discard)
        assertEquals(listOf(id), drafts.apply())
        assertNull(recorder.completion.value)
        assertEquals(active, recorder.state.value.tripId)
        assertTrue(recorder.state.value.isRecording)
        assertNull(repo.fetchTripDetail(id))
    }
}
