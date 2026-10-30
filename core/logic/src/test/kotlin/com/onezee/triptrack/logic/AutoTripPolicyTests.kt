package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.AutoTripPolicy.BluetoothDisconnectDecision.*
import kotlin.test.*

// iOS: AutoStopOnPauseTests.swift, AutoTripDraftPolicyTests.swift
class AutoTripPolicyTests {
    private fun decide(mode: AutoRecordMode, isRecording: Boolean = true, isPaused: Boolean = false,
                       idle: Boolean = false, distance: Double = 20_000.0, duration: Double = 3600.0, timeout: Int = 3) =
        AutoTripPolicy.onBluetoothDisconnect(mode, isRecording, isPaused, idle, distance, duration, timeout)

    @Test fun testAPausedTripIsNeverStoppedByBluetooth() {
        for (mode in AutoRecordMode.entries) for (idle in listOf(false, true)) {
            for ((distance, duration) in listOf(20_000.0 to 3600.0, 50.0 to 30.0)) {
                assertEquals(Ignore, decide(mode, isPaused = true, idle = idle, distance = distance, duration = duration))
            }
        }
    }
    @Test fun testTheShopStopThatBrokeTheDrive() {
        assertEquals(Ignore, decide(AutoRecordMode.remind, isPaused = true, distance = 60_000.0, duration = 4350.0))
    }
    @Test fun testRemindModeAsksAndDoesNotAct() {
        assertEquals(PromptOnly, decide(AutoRecordMode.remind))
        assertEquals(PromptOnly, decide(AutoRecordMode.remind, idle = true))
    }
    @Test fun testOffModeStaysSilent() {
        assertEquals(Ignore, decide(AutoRecordMode.off))
        assertEquals(Ignore, decide(AutoRecordMode.off, idle = true))
    }
    @Test fun testAutoModeStillEndsAnObviouslyFinishedTrip() { assertEquals(StopNow, decide(AutoRecordMode.auto, idle = true)) }
    @Test fun testAutoModeStillEndsAfterARealDrive() { assertEquals(StopNow, decide(AutoRecordMode.auto, distance = 20_000.0, duration = 3600.0)) }
    @Test fun testAutoModeStillGivesTheGraceToAShortTrip() {
        assertEquals(PromptThenStop(3), decide(AutoRecordMode.auto, distance = 100.0, duration = 60.0, timeout = 3))
    }
    @Test fun testNothingHappensWhenNothingIsRecording() { assertEquals(Ignore, decide(AutoRecordMode.auto, isRecording = false, idle = true)) }
    @Test fun testStopModeTable() {
        assertEquals(AutoRecordMode.auto, AutoTripPolicy.stopMode(AutoRecordMode.remind, true))
        assertEquals(AutoRecordMode.remind, AutoTripPolicy.stopMode(AutoRecordMode.remind, false))
        assertEquals(AutoRecordMode.auto, AutoTripPolicy.stopMode(AutoRecordMode.off, true))
        assertEquals(AutoRecordMode.auto, AutoTripPolicy.stopMode(AutoRecordMode.auto, false))
    }
    @Test fun testDraftStopsOnDisconnectButPauseStillWins() {
        val mode = AutoTripPolicy.stopMode(AutoRecordMode.remind, true)
        assertEquals(StopNow, decide(mode, distance = 5000.0, duration = 900.0))
        assertEquals(Ignore, decide(mode, isPaused = true, idle = true, distance = 5000.0, duration = 900.0))
    }
    @Test fun testManualTripInRemindStillOnlyAsks() {
        val mode = AutoTripPolicy.stopMode(AutoRecordMode.remind, false)
        assertEquals(PromptOnly, decide(mode, idle = true, distance = 5000.0, duration = 900.0))
    }
    @Test fun inactivityAndRecoveryRespectModePauseAndStrictTimeouts() {
        for (mode in AutoRecordMode.entries) for (draft in listOf(false, true)) for (paused in listOf(false, true)) {
            val allowed = !paused && (draft || mode == AutoRecordMode.auto)
            assertFalse(AutoTripPolicy.shouldPromptForInactivity(mode, draft, true, paused, 600.0))
            assertEquals(allowed, AutoTripPolicy.shouldPromptForInactivity(mode, draft, true, paused, 600.001))
            assertFalse(AutoTripPolicy.shouldRecoverStaleTrip(mode, draft, true, paused, 900.0))
            assertEquals(allowed, AutoTripPolicy.shouldRecoverStaleTrip(mode, draft, true, paused, 900.001))
            assertFalse(AutoTripPolicy.shouldPromptForInactivity(mode, draft, false, paused, 1000.0))
            assertFalse(AutoTripPolicy.shouldRecoverStaleTrip(mode, draft, false, paused, 1000.0))
        }
        assertFalse(AutoTripPolicy.canStop(true, true))
        assertTrue(AutoTripPolicy.canStop(true, true, userAction = true))
        assertFalse(AutoTripPolicy.canStop(false, false, userAction = true))
    }
    @Test fun bluetoothBoundariesAndConfiguredTimeoutArePreserved() {
        assertEquals(StopNow, decide(AutoRecordMode.auto, distance = 500.0, duration = 180.0))
        assertEquals(PromptThenStop(7), decide(AutoRecordMode.auto, distance = 499.999, duration = 180.0, timeout = 7))
        assertEquals(PromptThenStop(7), decide(AutoRecordMode.auto, distance = 500.0, duration = 179.999, timeout = 7))
        assertEquals(420.0, AutoTripPolicy.autoStopDelaySeconds(7))
        assertEquals(5.0, AutoTripPolicy.eventDeduplicationWindow)
        assertEquals(30.0, AutoTripPolicy.triggerDeduplicationWindow)
        assertEquals(600.0, AutoTripPolicy.newDrivingSessionGap)
        assertEquals(300.0, AutoTripPolicy.bluetoothDisconnectFastStopIdleThreshold)
        assertEquals(25.0, AutoTripPolicy.backgroundLaunchTaskTimeout)
        assertEquals(1.0, AutoTripPolicy.audioRouteSettleDelay)
    }
}
