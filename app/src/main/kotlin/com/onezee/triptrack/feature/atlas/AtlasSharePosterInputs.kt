// iOS: AtlasSharePoster.swift (frame and composition inputs; rendering belongs to the map/UI).
package com.onezee.triptrack.feature.atlas

import com.onezee.triptrack.logic.MapRect
import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.map.MapPoints
import com.onezee.triptrack.model.Discovery
import com.onezee.triptrack.model.GeoBounds

// iOS: AtlasSharePoster.swift. A frozen value prevents a reload changing the picture during rendering.
data class AtlasSharePosterInputs(
    val frame: GeoBounds,
    val layer: RevealedLayer,
    val seals: List<Discovery>,
    val appearance: AtlasAppearance,
    val caption: String,
    val width: Double = 540.0,
    val height: Double = 960.0,
    val scale: Double = 3.0,
    val captionHeight: Double = 148.0,
    val sealSize: Double = 34.0,
) {
    companion object {
        fun from(state: AtlasState, caption: String): AtlasSharePosterInputs? {
            val frame = frame(state.revealed) ?: return null
            return AtlasSharePosterInputs(MapPoints.bounds(frame), state.revealed, state.journal.finds.toList(), state.appearance, caption)
        }
        fun frame(layer: RevealedLayer, width: Double = 540.0, height: Double = 960.0): MapRect? {
            if (layer.isEmpty || width <= 0 || height <= 0) return null
            val points = layer.fine.flatten().map(MapPoints::point)
            if (points.isEmpty()) return null
            val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
            if (!listOf(minX, maxX, minY, maxY).all { it.isFinite() }) return null
            val centreX = (minX + maxX) / 2; val centreY = (minY + maxY) / 2
            val floor = 4_000 * MapPoints.mapPointsPerMetre(MapPoints.latitude(centreY))
            var spanX = ((maxX - minX) * 1.2).coerceAtLeast(floor)
            var spanY = ((maxY - minY) * 1.2).coerceAtLeast(floor)
            val wanted = width / height
            if (spanX / spanY < wanted) spanX = spanY * wanted else spanY = spanX / wanted
            return MapRect(centreX - spanX / 2, centreY - spanY / 2, spanX, spanY)
        }
    }
}
