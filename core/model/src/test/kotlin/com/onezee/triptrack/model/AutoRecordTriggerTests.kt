package com.onezee.triptrack.model

import kotlin.test.*
import kotlinx.serialization.json.Json

// iOS: AutoRecordTriggerTests.swift.
class AutoRecordTriggerTests {
    @Test fun testRawValuesStayCompatibleWithStorage() {
        assertEquals("motionOrStereo", AutoRecordTrigger.motionOrStereo.rawValue)
        assertEquals("stereoOnly", AutoRecordTrigger.stereoOnly.rawValue)
        for (trigger in AutoRecordTrigger.entries) {
            assertEquals("\"${trigger.rawValue}\"", Json.encodeToString(AutoRecordTrigger.serializer(), trigger))
            assertEquals(trigger, Json.decodeFromString(AutoRecordTrigger.serializer(), "\"${trigger.rawValue}\""))
        }
    }

    @Test fun testParsesStoredValues() {
        assertEquals(AutoRecordTrigger.motionOrStereo, AutoRecordTrigger.parse("motionOrStereo"))
        assertEquals(AutoRecordTrigger.stereoOnly, AutoRecordTrigger.parse("stereoOnly"))
    }

    @Test fun testMissingOrUnknownValueKeepsMotionEnabled() {
        for (raw in listOf(null, "unknown", "")) assertEquals(AutoRecordTrigger.motionOrStereo, AutoRecordTrigger.parse(raw))
    }
}
