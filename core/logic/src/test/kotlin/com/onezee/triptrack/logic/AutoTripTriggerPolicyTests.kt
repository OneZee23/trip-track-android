package com.onezee.triptrack.logic

import com.onezee.triptrack.model.AutoRecordTrigger
import com.onezee.triptrack.model.AutoTripPolicy
import kotlin.test.*

// iOS: AutoTripTriggerPolicyTests.swift. Keep every row of the iPhone table.
class AutoTripTriggerPolicyTests {
    @Test fun testMotionSignalStartsTripTable() {
        data class Case(val trigger: AutoRecordTrigger, val automotive: Boolean, val stereo: Boolean, val starts: Boolean)
        val cases = listOf(
            Case(AutoRecordTrigger.motionOrStereo, false, false, false),
            Case(AutoRecordTrigger.motionOrStereo, false, true, false),
            Case(AutoRecordTrigger.motionOrStereo, true, false, true),
            Case(AutoRecordTrigger.motionOrStereo, true, true, true),
            Case(AutoRecordTrigger.stereoOnly, false, false, false),
            Case(AutoRecordTrigger.stereoOnly, false, true, true),
            Case(AutoRecordTrigger.stereoOnly, true, false, false),
            Case(AutoRecordTrigger.stereoOnly, true, true, true),
        )
        for (row in cases) assertEquals(row.starts, AutoTripPolicy.motionSignalStartsTrip(row.trigger, row.automotive, row.stereo), row.toString())
    }
}
