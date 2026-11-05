package com.onezee.triptrack.feature.trip

import com.onezee.triptrack.logic.coordinate
import com.onezee.triptrack.logic.decodePolyline
import com.onezee.triptrack.logic.encodePolyline
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripHiddenParts
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** History cards in filming mode: build 15 drew the whole route there, hidden parts included (POCO, 5 Nov 2026). */
class TripFilmingCardTests {
    private val t0 = Instant.parse("2026-11-02T17:05:00Z")
    private val points = (0 until 40).map { index ->
        TrackPoint(latitude = 45.0 + index * 0.001, longitude = 39.0, speed = 15.0, horizontalAccuracy = 5.0,
            timestamp = t0.plusSeconds(index * 10L))
    }
    private val full = Trip(startDate = t0, endDate = points.last().timestamp, distance = 4_300.0, trackPoints = points,
        previewPolyline = Trip.encodePolyline(points.map { it.coordinate }),
        hiddenParts = TripHiddenParts(startMetres = 500, endMetres = 500))
    // What the history holds: the same trip without its points.
    private val listed = full.copy(trackPoints = emptyList())

    @Test
    fun aListedTripIsReadInFullAndKeepsOnlyItsMaskedRoute() = runBlocking {
        var asked = 0
        val card = filmingCard(listed, { id -> asked++; full.takeIf { it.id == id } }, null, null)!!
        assertEquals(1, asked)
        assertTrue(card.trackPoints.isEmpty())
        assertTrue(!listed.previewPolyline!!.contentEquals(card.previewPolyline!!))
        val shown = Trip.decodePolyline(card.previewPolyline!!)
        assertTrue(shown.size < points.size)
        // Nothing from the first or last 500 m: the masked route starts and ends inside the trip.
        assertTrue(shown.first().latitude > points.first().latitude + 0.004)
        assertTrue(shown.last().latitude < points.last().latitude - 0.004)
    }

    @Test
    fun aTripThatCannotBeReadHasNoCardAndTheFallbackDrawsNothing() = runBlocking {
        assertNull(filmingCard(listed, { null }, null, null))
        assertNull(filmingCard(listed, null, null, null))
        val bare = listed.withoutGeometry()
        assertNull(bare.previewPolyline)
        assertTrue(bare.photos.isEmpty() && bare.checkpoints.isEmpty())
    }
}
