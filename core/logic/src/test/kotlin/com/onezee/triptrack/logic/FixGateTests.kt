package com.onezee.triptrack.logic

import com.onezee.triptrack.model.FixGate
import com.onezee.triptrack.model.FixGate.Decision.*
import com.onezee.triptrack.model.FixGate.Reason.*
import kotlin.test.*

// iOS: FixGateTests.swift
class FixGateTests {
    private fun decide(acc: Double, age: Double = 0.0, speed: Double = 10.0, recording: Boolean = true,
                       limit: Double = FixGate.recordingAccuracyLimit) = FixGate.decide(acc, age, speed, recording, limit)

    @Test fun testRecordingAcceptsCoarseFixesUpToTwoHundredMetres() {
        for (acc in listOf(64.0, 120.0, 200.0)) assertEquals(Accept, decide(acc))
    }
    @Test fun testRecordingRejectsBeyondTwoHundredMetres() { assertEquals(Reject(accuracy), decide(201.0)) }
    @Test fun testIdleUsesTheSameCeilingAsRecording() {
        for (acc in listOf(90.0, 150.0, 200.0)) assertEquals(Accept, decide(acc, recording = false))
        assertEquals(Reject(accuracy), decide(201.0, recording = false))
    }
    @Test fun testIdleIsNeverStricterThanRecording() { assertTrue(FixGate.idleAccuracyLimit >= FixGate.recordingAccuracyLimit) }
    @Test fun testIdleStillRejectsTheObviouslyFalse() {
        assertEquals(Reject(invalid), decide(-1.0, recording = false))
        assertEquals(Reject(stale), decide(10.0, age = 10.0, recording = false))
        assertEquals(Reject(speed), decide(10.0, speed = 90.0, recording = false))
    }
    @Test fun testInvalidStaleAndImpossibleSpeedAreRejected() {
        assertEquals(Reject(invalid), decide(-1.0))
        assertEquals(Reject(stale), decide(10.0, age = 10.0))
        assertEquals(Reject(speed), decide(10.0, speed = 90.0))
    }
    @Test fun testUnknownSpeedIsKept() { assertEquals(Accept, decide(10.0, speed = -1.0)) }
    @Test fun testTheOldPipelineCanBeReplayed() { assertEquals(Reject(accuracy), decide(70.0, limit = 65.0)) }
}
