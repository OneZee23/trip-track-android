package com.onezee.triptrack.logic

import com.onezee.triptrack.model.RecordingFinishPreview
import java.util.UUID
import kotlin.test.*

// iOS: RecordingFinishPreviewTests.swift (confirmation outcome identity).
class RecordingFinishPreviewTests {
    private val start = java.time.Instant.ofEpochSecond(1_760_000_000)
    private fun preview(id: UUID, distance: Double, duration: Double, speed: Double) =
        RecordingFinishPreview(id, start.addingSeconds(duration), distance, duration, speed)

    @Test fun testCrossingATimeBoundaryRequiresANewConfirmation() {
        val id = UUID.randomUUID()
        val short = preview(id, 100.0, 119.0, 0.0)
        val save = preview(id, 100.0, 120.0, 0.0)
        val slow = preview(id, 100.0, 181.0, 0.0)
        assertFalse(short.confirmsSameOutcome(save))
        assertFalse(save.confirmsSameOutcome(slow))
        assertFalse(short.confirmsSameOutcome(slow))
    }
    @Test fun testOrdinaryProgressDoesNotRequireRepeatedConfirmation() {
        val id = UUID.randomUUID()
        assertTrue(preview(id, 1000.0, 200.0, 10.0).confirmsSameOutcome(preview(id, 1100.0, 205.0, 12.0)))
        assertTrue(preview(id, 0.0, 2.0, 0.0).confirmsSameOutcome(preview(id, 0.0, 5.0, 0.0)))
        assertFalse(preview(UUID.randomUUID(), 0.0, 2.0, 0.0).confirmsSameOutcome(preview(UUID.randomUUID(), 0.0, 2.0, 0.0)))
    }
}
