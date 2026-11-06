// iOS: ManualTripSuggestions.swift cachedGeocode naming rule.
package com.onezee.triptrack.location

import com.onezee.triptrack.location.world.Placemark
import com.onezee.triptrack.location.world.TripNamer
import com.onezee.triptrack.logic.GeohashEncoder
import com.onezee.triptrack.model.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManualTripNamerTests : RecorderTestCase() {
    @Test fun pointNamesUseCachedLocalityThenRegionAndLeaveUnknownBlank() = test {
        var calls = 0
        val namer = TripNamer(
            repo, store,
            geocoder = { point ->
                calls++
                when {
                    point.latitude < 1 -> Placemark(locality = "Джубга", subAdministrativeArea = null, administrativeArea = "Краснодарский край")
                    point.latitude < 2 -> Placemark(locality = null, subAdministrativeArea = null, administrativeArea = "Краснодарский край")
                    else -> null
                }
            },
            sync = sink,
            isOffline = { false },
            autoTitle = { "must not be used" },
        )
        val localityPoint = GeoPoint(0.1, 10.0)
        val regionPoint = GeoPoint(1.1, 20.0)
        val unknownPoint = GeoPoint(2.1, 30.0)
        assertEquals("Джубга", namer.nameForPoint(localityPoint))
        assertEquals("Джубга", namer.nameForPoint(localityPoint))
        assertEquals("Краснодарский край", namer.nameForPoint(regionPoint))
        assertNull(namer.nameForPoint(unknownPoint))
        assertNull(namer.nameForPoint(unknownPoint))
        assertEquals(3, calls)
        assertEquals("Джубга", repo.cachedGeocode(GeohashEncoder.encode(localityPoint.latitude, localityPoint.longitude, 5))?.locality)
    }
}
