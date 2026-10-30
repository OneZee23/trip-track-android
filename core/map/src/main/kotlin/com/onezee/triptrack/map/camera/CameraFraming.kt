package com.onezee.triptrack.map.camera

import com.onezee.triptrack.logic.MapRect
import com.onezee.triptrack.map.MapPoints
import com.onezee.triptrack.model.GeoBounds
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The smallest frames the camera agrees to show. A trip that never moved, or one point that survived
 * filtering, gives a rect of zero size, and fitting it literally takes the camera to the deepest zoom
 * there is, past any tiles: an empty grey rectangle that reads as a broken map. A floor keeps the
 * surrounding streets in frame.
 */
object CameraFraming {
    /** iOS: GeoBounds.mapRect, the Atlas floor: "give it a block to look at". */
    const val ATLAS_MINIMUM_METRES = 300.0

    /** iOS: RouteMapView.minimumSpanMetres, about 400 m across. */
    const val ROUTE_MINIMUM_METRES = 400.0

    /** iOS: GeoBounds.mapRect. The floor is measured at the box's centre latitude. */
    fun atlasRect(bounds: GeoBounds): MapRect {
        val a = MapPoints.x(bounds.minLon) to MapPoints.y(bounds.maxLat)
        val b = MapPoints.x(bounds.maxLon) to MapPoints.y(bounds.minLat)
        val rect = MapRect(min(a.first, b.first), min(a.second, b.second), abs(a.first - b.first), abs(a.second - b.second))
        val floor = ATLAS_MINIMUM_METRES * MapPoints.mapPointsPerMetre((bounds.minLat + bounds.maxLat) / 2)
        return floored(rect, floor)
    }

    /** iOS: RouteMapView.floored. The floor is measured at the rect's middle. */
    fun routeRect(bounds: GeoBounds): MapRect {
        val rect = MapPoints.rect(bounds)
        val centreLatitude = MapPoints.latitude(rect.y + rect.height / 2)
        return floored(rect, ROUTE_MINIMUM_METRES * MapPoints.mapPointsPerMetre(centreLatitude))
    }

    /** Grows a rect that is narrower or lower than [minimum] map points, around its middle. */
    fun floored(rect: MapRect, minimum: Double): MapRect {
        if (!(rect.width < minimum || rect.height < minimum)) return rect
        val width = max(rect.width, minimum)
        val height = max(rect.height, minimum)
        val midX = rect.x + rect.width / 2
        val midY = rect.y + rect.height / 2
        return MapRect(midX - width / 2, midY - height / 2, width, height)
    }
}
