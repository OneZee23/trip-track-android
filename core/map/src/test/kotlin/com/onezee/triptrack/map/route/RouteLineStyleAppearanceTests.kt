// iOS: RouteLineStyle.swift, ProShowcaseTests.swift
package com.onezee.triptrack.map.route

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.RouteLineStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteLineStyleAppearanceTests {
    @Test fun selectedColourOverridesSpeedBasedAndSpeedlessLines() {
        val colour = RouteLineStyle.ice.colorArgb!!
        val look = RouteAppearance.plain.copy(lineColorOverride = colour)
        val coordinates = listOf(GeoPoint(55.0, 37.0), GeoPoint(55.1, 37.1))
        for (speed in listOf(48.0, null)) assertEquals(colour, look.argb(RouteLine(coordinates, speed)))
        assertEquals(RouteAppearance.UNKNOWN_SPEED, look.argb(RouteLine(coordinates, -1.0)))
    }
}
