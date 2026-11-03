package com.onezee.triptrack.map.area

import com.onezee.triptrack.model.GeoPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The ring MapLibre draws instead of an MKCircle: closed, round, and as far from the centre as the radius says. */
class MapAreaTests {
    private val centre = GeoPoint(45.035, 38.975)

    @Test fun theRingIsClosedAndHasSixtyFivePoints() {
        val ring = MapArea.disc(centre, 500.0)
        assertEquals(65, ring.size)
        assertEquals(ring.first().latitude, ring.last().latitude, 1e-9)
        assertEquals(ring.first().longitude, ring.last().longitude, 1e-9)
    }

    @Test fun everyPointLiesTheRadiusFromTheCentre() {
        for (metres in listOf(150.0, 500.0, 1_000.0)) {
            for (point in MapArea.disc(centre, metres)) {
                val north = (point.latitude - centre.latitude) * 111_320.0
                val east = (point.longitude - centre.longitude) * 111_320.0 * cos(Math.toRadians(centre.latitude))
                assertTrue(abs(hypot(north, east) - metres) < 0.01, "$metres m: a point at ${hypot(north, east)}")
            }
        }
    }

    @Test fun theRingDoesNotCollapseAtAPole() {
        val ring = MapArea.disc(GeoPoint(89.999, 10.0), 500.0)
        assertTrue(ring.all { it.longitude.isFinite() && it.latitude.isFinite() })
    }
}
