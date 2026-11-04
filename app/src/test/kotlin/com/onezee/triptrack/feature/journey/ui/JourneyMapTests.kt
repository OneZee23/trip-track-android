package com.onezee.triptrack.feature.journey.ui

import com.onezee.triptrack.feature.trip.CheckpointState
import com.onezee.triptrack.map.marker.MapMarker
import com.onezee.triptrack.model.GeoPoint
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Assert.assertTrue

class JourneyMapTests {
    @Test fun checkpointMarkersKeepTheirCoordinatesAndSkipUnlocatedCheckpoints() {
        val locatedId = UUID.randomUUID()
        val checkpoints = listOf(
            CheckpointState(locatedId, Instant.EPOCH, GeoPoint(45.0, 38.0), null, 1, 120.0, 60.0, emptyList(), null),
            CheckpointState(UUID.randomUUID(), Instant.EPOCH, null, null, 2, 200.0, 120.0, emptyList(), null),
        )

        val markers = journeyCheckpointMarkers(checkpoints)

        assertEquals(1, markers.size)
        assertEquals("journey_checkpoint:$locatedId", markers.single().id)
        assertEquals(GeoPoint(45.0, 38.0), markers.single().position)
        assertEquals(MapMarker.Kind.CHECKPOINT, markers.single().kind)
        assertTrue(markers.single().label == null)
    }
}
