package com.onezee.triptrack.location

import com.onezee.triptrack.location.auto.AutoRecordSettings
import com.onezee.triptrack.location.auto.AutoTripController
import com.onezee.triptrack.location.auto.AutoTripMemory
import com.onezee.triptrack.location.auto.AutoTripNotices
import com.onezee.triptrack.location.auto.StopPromptReason
import com.onezee.triptrack.location.recording.StartRefusal
import com.onezee.triptrack.location.recording.StartResult
import com.onezee.triptrack.logic.TrackTestKit
import com.onezee.triptrack.model.AutoRecordMode
import com.onezee.triptrack.model.AutoRecordTrigger
import com.onezee.triptrack.model.SavedBluetoothDevice
import com.onezee.triptrack.model.TripConfirmation
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Test

// iOS: AutoTripService.swift; AutoStopOnPauseTests.swift and AutoTripDraftPolicyTests.swift for the
// rules, here exercised through the controller and a real recording.
class AutoTripControllerTests : RecorderTestCase() {
    private val stereo = SavedBluetoothDevice(uuid = "00:11:22:33:44:55", name = "Polo Audio")
    private val autoSettings = AutoRecordSettings.InMemory(mode = AutoRecordMode.auto, savedDevices = listOf(stereo))
    private val memory = AutoTripMemory.InMemory()
    private val prompts = Prompts()
    private var serviceStarts = 0
    private var serviceAllowed = true
    private var useOutputSnapshot = false
    private var currentOutput: SavedBluetoothDevice? = null
    private var gate = CompletableDeferred<Unit>()
    private val waits = mutableListOf<Long>()
    // The injected wait already controls the clock; enter it before asserting the armed delay.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var controller: AutoTripController

    @Before
    fun buildController() {
        controller = AutoTripController(
            recorder, autoSettings, memory, prompts,
            selectVehicle = { id -> settings.save { it.copy(selectedVehicleId = id) } },
            ensureRecordingService = { serviceStarts++; serviceAllowed },
            scope = scope,
            clock = { now },
            wait = { millis -> waits += millis; gate.await() },
            linkedStereo = {
                if (useOutputSnapshot) currentOutput
                else memory.connectedStereo?.let { autoSettings.savedDevice(it, null) }
            },
        )
        recorder.listener = controller
    }

    @After
    fun stopScope() {
        scope.cancel()
    }

    /** Fires the armed auto-stop timer and waits for what it does. */
    private suspend fun fireTimer() {
        val timer = controller.stopTimer ?: error("no timer armed")
        gate.complete(Unit)
        timer.join()
        gate = CompletableDeferred()
    }

    @Test
    fun aSavedStereoStartsAConfirmedTripInAutoMode() = test {
        controller.onStereoConnected(stereo.uuid, stereo.name)
        val state = recorder.state.value
        assertTrue(state.isRecording)
        assertEquals(TripConfirmation.confirmed, state.confirmation)
        assertEquals(1, serviceStarts)
        assertEquals(stereo.uuid, memory.connectedStereo)
    }

    @Test
    fun theStereosCarBecomesTheTripsCar() = test {
        val car = vehicles.addVehicle("Polo", "🚗")
        autoSettings.savedDevices = listOf(stereo.copy(vehicleId = car))
        controller.onStereoConnected(stereo.uuid, null)
        assertEquals(car, db.tripDao().trip(recorder.state.value.tripId!!)!!.vehicleId)
    }

    // iOS 0.8.1: «Remind» records at once, a draft until «Mine».
    @Test
    fun remindModeRecordsADraft() = test {
        autoSettings.mode = AutoRecordMode.remind
        controller.onStereoConnected(stereo.uuid, stereo.name)
        assertTrue(recorder.state.value.isRecording)
        assertEquals(TripConfirmation.draft, recorder.state.value.confirmation)
        assertTrue(memory.hasRemindedForCurrentTrip)
    }

    @Test
    fun offModeAndUnknownDevicesStaySilent() = test {
        controller.onStereoConnected("AA:BB:CC:DD:EE:FF", "Headphones")
        assertFalse(recorder.state.value.isRecording)
        autoSettings.mode = AutoRecordMode.off
        controller.onStereoConnected(stereo.uuid, stereo.name)
        controller.onAutomotiveDetected(null)
        assertFalse(recorder.state.value.isRecording)
        assertEquals(0, serviceStarts)
    }

    @Test
    fun aRefusedStartIsSaidOutLoud() = test {
        locationAllowed = false
        controller.onStereoConnected(stereo.uuid, stereo.name)
        assertEquals(listOf<Any>(StartRefusal.locationDenied), prompts.failures)
    }

    @Test
    fun aServiceTheSystemRefusedEndsTheRecordingAndSaysSo() = test {
        serviceAllowed = false
        controller.onStereoConnected(stereo.uuid, stereo.name)
        assertFalse(recorder.state.value.isRecording)
        assertEquals(listOf<Any>(StartRefusal.unknown), prompts.failures)
    }

    // iOS: AutoStopOnPauseTests.testAPausedTripIsNeverStoppedByBluetooth.
    @Test
    fun aPausedTripIsNeverStoppedByTheStereo() = test {
        controller.onStereoConnected(stereo.uuid, stereo.name)
        drive(straight(0..300))
        now = TrackTestKit.at(301.0)
        recorder.setPaused(true)
        now = TrackTestKit.at(320.0)
        controller.onStereoDisconnected(stereo.uuid, stereo.name)
        assertTrue(recorder.state.value.isRecording)
        assertTrue(prompts.stopPrompts.isEmpty())
        assertNull(controller.stopTimer)
    }

    // iOS: testTheShopStopThatBrokeTheDrive. A timer armed before the pause dies with the pause.
    @Test
    fun aPauseCancelsTheTimerArmedBeforeIt() = test {
        controller.onStereoConnected(stereo.uuid, stereo.name)
        drive(straight(0..60))
        now = TrackTestKit.at(70.0)
        controller.onStereoDisconnected(stereo.uuid, stereo.name)
        val timer = controller.stopTimer!!
        recorder.setPaused(true)
        assertTrue(timer.isCancelled)
        assertTrue(recorder.state.value.isRecording)
    }

    @Test
    fun aShortTripGetsTheQuestionThenStopsAfterTheTimeout() = test {
        controller.onStereoConnected(stereo.uuid, stereo.name)
        drive(straight(0..60))
        now = TrackTestKit.at(70.0)
        controller.onStereoDisconnected(stereo.uuid, stereo.name)
        assertEquals(listOf<Pair<Int?, StopPromptReason>>(3 to StopPromptReason.bluetooth), prompts.stopPrompts)
        assertEquals(listOf(180_000L), waits)
        assertTrue(recorder.state.value.isRecording)
        fireTimer()
        assertFalse(recorder.state.value.isRecording)
    }

    // iOS: testAutoModeStillEndsAfterARealDrive.
    @Test
    fun aRealDriveEndsAtOnceWhenTheStereoGoes() = test {
        controller.onStereoConnected(stereo.uuid, stereo.name)
        drive(straight(0..240))
        now = TrackTestKit.at(250.0)
        controller.onStereoDisconnected(stereo.uuid, stereo.name)
        assertFalse(recorder.state.value.isRecording)
        assertEquals(1, prompts.autoStops.size, "the summary notice of a confirmed trip")
    }

    // iOS: testRemindModeAsksAndDoesNotAct, for a trip the person started.
    @Test
    fun remindModeAsksAboutAPersonsTripAndDoesNothing() = test {
        autoSettings.mode = AutoRecordMode.remind
        assertIs<StartResult.Started>(recorder.start())
        drive(straight(0..240))
        now = TrackTestKit.at(250.0)
        controller.onStereoDisconnected(stereo.uuid, stereo.name)
        assertEquals(listOf<Pair<Int?, StopPromptReason>>(null to StopPromptReason.bluetooth), prompts.stopPrompts)
        assertTrue(recorder.state.value.isRecording)
        assertNull(controller.stopTimer)
    }

    // iOS 0.8.1: «who started it stops it». A draft the app started ends by the automatic rules.
    @Test
    fun aDraftTheAppStartedEndsByTheAutomaticRules() = test {
        autoSettings.mode = AutoRecordMode.remind
        controller.onStereoConnected(stereo.uuid, stereo.name)
        drive(straight(0..240))
        now = TrackTestKit.at(250.0)
        controller.onStereoDisconnected(stereo.uuid, stereo.name)
        assertFalse(recorder.state.value.isRecording)
        assertTrue(prompts.autoStops.isEmpty(), "a draft gets its question at the finish instead")
        assertFalse(memory.hasRemindedForCurrentTrip)
    }

    @Test
    fun stopNowFromTheNotificationEndsEvenAPausedTrip() = test {
        controller.onStereoConnected(stereo.uuid, stereo.name)
        drive(straight(0..240))
        recorder.setPaused(true)
        controller.stopRequestedByUser()
        assertFalse(recorder.state.value.isRecording)
    }

    @Test
    fun inactivityAsksAfterTenMinutesAndContinueQuietsIt() = test {
        controller.onStereoConnected(stereo.uuid, stereo.name)
        drive(straight(0..120))
        // Parked: fixes keep coming, the odometer stands still.
        drive((121..721 step 30).map { TrackTestKit.fix(north = 1_200.0, speed = 0.0, after = it.toDouble()) })
        drive(listOf(TrackTestKit.fix(north = 1_200.0, speed = 0.0, after = 731.0)))
        assertEquals(listOf<Pair<Int?, StopPromptReason>>(3 to StopPromptReason.inactivity), prompts.stopPrompts)
        controller.continueRequestedByUser()
        assertNull(controller.stopTimer)
        drive(listOf(TrackTestKit.fix(north = 1_200.0, speed = 0.0, after = 761.0)))
        assertEquals(1, prompts.stopPrompts.size, "not asked again on the next fix")
    }

    @Test
    fun motionStartsOnceInThirtySecondsAndBackdatesTheTrip() = test {
        controller.onAutomotiveDetected(TrackTestKit.at(-300.0))
        val first = recorder.state.value.tripId!!
        assertEquals(TrackTestKit.at(-300.0), db.tripDao().trip(first)!!.startDate)
        drive(straight(0..10))
        recorder.stop()
        now = TrackTestKit.at(20.0)
        controller.onAutomotiveDetected(null)
        assertFalse(recorder.state.value.isRecording, "inside the 30-second window of the last trigger")
        now = TrackTestKit.at(45.0)
        controller.onAutomotiveDetected(null)
        assertTrue(recorder.state.value.isRecording)
    }

    @Test
    fun remindModeDoesNotRemindTwiceInOneSession() = test {
        autoSettings.mode = AutoRecordMode.remind
        controller.onAutomotiveDetected(null)
        drive(straight(0..10))
        recorder.stop()
        now = TrackTestKit.at(100.0)
        controller.onAutomotiveDetected(null)
        assertFalse(recorder.state.value.isRecording)
        // A long gap after the car stopped: a new session.
        controller.onAutomotiveEnded()
        now = TrackTestKit.at(100.0 + 11 * 60)
        controller.onAutomotiveDetected(null)
        assertTrue(recorder.state.value.isRecording)
    }

    @Test
    fun aStaleTripIsSplitWhenTheStereoReconnects() = test {
        controller.onStereoConnected(stereo.uuid, stereo.name)
        val first = recorder.state.value.tripId!!
        drive(straight(0..240))
        // Twenty minutes without a fix (the phone slept in the car park): the reconnect is a new
        // session. A parked fix would have raised the inactivity question and moved the window.
        now = TrackTestKit.at(1_450.0)
        controller.onStereoConnected(stereo.uuid, stereo.name)
        assertFalse(recorder.state.value.isRecording)
        gate.complete(Unit)
        controller.pendingRestart!!.join()
        val second = recorder.state.value.tripId
        assertTrue(recorder.state.value.isRecording)
        assertTrue(second != null && second != first)
    }

    @Test
    fun androidAutoIsACarToo() = test {
        autoSettings.savedDevices = listOf(SavedBluetoothDevice(AutoTripController.ANDROID_AUTO, "Android Auto"))
        controller.onCarConnection(true)
        assertTrue(recorder.state.value.isRecording)
        drive(straight(0..240))
        now = TrackTestKit.at(250.0)
        controller.onCarConnection(false)
        assertFalse(recorder.state.value.isRecording)
    }

    // iOS: AutoTripTriggerPolicyTests.swift, AutoTripService.swift (all motion doors).
    @Test fun stereoOnlyMotionNeitherStartsNorSplitsAStaleTrip() = test {
        autoSettings.trigger = AutoRecordTrigger.stereoOnly
        controller.onAutomotiveDetected(null)
        controller.onWake(isAutomotive = true)
        assertFalse(recorder.state.value.isRecording)
        assertNull(memory.lastTripTriggerTime)
        recorder.start()
        val first = recorder.state.value.tripId
        drive(straight(0..240))
        now = TrackTestKit.at(1_450.0)
        controller.onAutomotiveDetected(null)
        controller.onWake(isAutomotive = true)
        assertEquals(first, recorder.state.value.tripId)
        assertTrue(recorder.state.value.isRecording)
        assertEquals(0, serviceStarts)
    }

    @Test fun rejectedMotionDoesNotCancelTheStereoStopTimer() = test {
        autoSettings.trigger = AutoRecordTrigger.stereoOnly
        controller.onStereoConnected(stereo.uuid, stereo.name)
        drive(straight(0..60))
        now = TrackTestKit.at(70.0)
        controller.onStereoDisconnected(stereo.uuid, stereo.name)
        val timer = controller.stopTimer
        controller.onAutomotiveDetected(null)
        assertEquals(timer, controller.stopTimer)
        assertFalse(timer!!.isCancelled)
    }

    @Test fun stereoOnlyWakeReadsTheLinkedOutputWithoutAnAutomotiveSignal() = test {
        autoSettings.trigger = AutoRecordTrigger.stereoOnly
        val car = vehicles.addVehicle("Polo", "🚗")
        currentOutput = stereo.copy(vehicleId = car)
        autoSettings.savedDevices = listOf(currentOutput!!)
        useOutputSnapshot = true
        assertNull(memory.connectedStereo, "the process missed the connect broadcast")
        controller.onWake(isAutomotive = false)
        assertTrue(recorder.state.value.isRecording)
        assertEquals(car, db.tripDao().trip(recorder.state.value.tripId!!)!!.vehicleId)
        assertEquals(stereo.uuid, memory.connectedStereo)
        drive(straight(0..240))
        now = TrackTestKit.at(250.0)
        controller.onStereoDisconnected(stereo.uuid, stereo.name)
        assertFalse(recorder.state.value.isRecording)
    }

    @Test fun stalePersistedStereoDoesNotCountAsAConnectedOutput() = test {
        autoSettings.trigger = AutoRecordTrigger.stereoOnly
        memory.connectedStereo = stereo.uuid
        useOutputSnapshot = true
        controller.onWake(isAutomotive = true)
        controller.onAutomotiveDetected(null)
        assertFalse(recorder.state.value.isRecording)
    }

    @Test fun oldTriggerStillNeedsAutomotiveForAWake() = test {
        useOutputSnapshot = true
        currentOutput = stereo
        controller.onWake(isAutomotive = false)
        assertFalse(recorder.state.value.isRecording)
        controller.onWake(isAutomotive = true)
        assertTrue(recorder.state.value.isRecording)
    }

    @Test fun unlinkedAndroidAutoDoesNotStartOrStopATrip() = test {
        controller.onCarConnection(true)
        assertFalse(recorder.state.value.isRecording)
        recorder.start()
        drive(straight(0..240))
        now = TrackTestKit.at(250.0)
        controller.onCarConnection(false)
        assertTrue(recorder.state.value.isRecording)
        assertTrue(prompts.stopPrompts.isEmpty())
    }

    @Test fun linkedAndroidAutoStartsAndStopsInStereoOnlyMode() = test {
        autoSettings.trigger = AutoRecordTrigger.stereoOnly
        val car = vehicles.addVehicle("Polo", "🚗")
        autoSettings.savedDevices = listOf(SavedBluetoothDevice(AutoTripController.ANDROID_AUTO, "Android Auto", car))
        controller.onCarConnection(true)
        assertTrue(recorder.state.value.isRecording)
        assertEquals(car, db.tripDao().trip(recorder.state.value.tripId!!)!!.vehicleId)
        drive(straight(0..240))
        now = TrackTestKit.at(250.0)
        controller.onCarConnection(false)
        assertFalse(recorder.state.value.isRecording)
    }

    private class Prompts : AutoTripNotices {
        val stopPrompts = mutableListOf<Pair<Int?, StopPromptReason>>()
        val failures = mutableListOf<Any>()
        val autoStops = mutableListOf<Double>()
        override fun tripStopPrompt(minutes: Int?, reason: StopPromptReason) {
            stopPrompts += minutes to reason
        }

        override fun cancelTripStopPrompt() = Unit
        override fun autoStartFailed(reason: StartRefusal) {
            failures += reason
        }

        override fun autoStopped(metres: Double, durationSeconds: Double) {
            autoStops += metres
        }
    }
}
