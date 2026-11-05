package com.onezee.triptrack.map.route

import com.onezee.triptrack.model.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HiddenRouteLayersTests {
    @Test
    fun hiddenRunsReachTheMapSourceAndHiddenLayersHaveTheIosPaint() {
        val appearance = RouteAppearance.plain.copy(hiddenInkArgb = 0x8C123456.toInt())
        val hiddenRun = RouteLine(listOf(GeoPoint(45.0, 39.0), GeoPoint(45.001, 39.001)), null, hidden = true)
        val routes = MapRoutes(listOf(RouteShape(emptyList(), null, listOf(hiddenRun))), appearance)
        val sourceFeatures = RouteLayers().lineFeatures(routes, appearance)

        assertEquals(listOf(RouteLayers.HIDDEN_CASING, RouteLayers.HIDDEN_LINE), RouteLayers().layerIds.take(2))
        assertEquals(1, sourceFeatures.size)
        assertTrue(sourceFeatures.single().getBooleanProperty("hidden"))
        val paint = RouteLayers().hiddenLineProperties(appearance).associate { it.name to it.value }
        assertEquals(RouteLayers.rgba(appearance.hiddenInkArgb), paint["line-color"])
        assertEquals(4f, paint["line-width"])
        assertEquals("round", paint["line-cap"])
        assertEquals("round", paint["line-join"])
        assertEquals(listOf(.025f, 1.8f), (paint["line-dasharray"] as Array<*>).toList())
        assertFalse(sourceFeatures.single().getBooleanProperty("dashed"))
    }
}
