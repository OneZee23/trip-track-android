package com.onezee.triptrack.logic

import kotlin.math.floor

// iOS: CoreGraphics CGPoint (RegionLabelLOD.swift, FogCellGrid.swift).
data class MapPoint(val x: Double, val y: Double)

// iOS: CoreGraphics CGRect / MapKit MKMapRect (pure coordinate rectangle).
data class MapRect(val x: Double, val y: Double, val width: Double, val height: Double) {
    val minX: Double
        get() = minOf(x, x + width)

    val minY: Double
        get() = minOf(y, y + height)

    val maxX: Double
        get() = maxOf(x, x + width)

    val maxY: Double
        get() = maxOf(y, y + height)

    fun intersects(other: MapRect): Boolean =
        minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY
}

// iOS: FogVeilOverlay.swift (FogVeilRenderer pure geometry).
object FogVeilRenderer {
    const val streetHalfWidthMetres = 50.0
    const val haloHalfWidthPoints = 18.0

    fun haloHalfWidth(metresPerPoint: Double): Double =
        maxOf(streetHalfWidthMetres, haloHalfWidthPoints * metresPerPoint)

    fun corridorWidth(zoomScale: Double, metre: Double): Double {
        if (!(zoomScale > 0 && metre > 0)) return 0.0
        return haloHalfWidth(1 / (zoomScale * metre)) * 2 * metre
    }

    fun lod(zoomScale: Double): RevealedLayer.LOD =
        when {
            zoomScale > 1.5e-3 -> RevealedLayer.LOD.fine
            zoomScale > 1.5e-4 -> RevealedLayer.LOD.mid
            else -> RevealedLayer.LOD.far
        }
}

// iOS: RegionLabelLOD.swift
object RegionLabelLOD {
    const val regionMinSidePt = 140.0
    const val userDotClearancePt = 22.0

    fun level(bboxMinSidePt: Double): Boolean = bboxMinSidePt >= regionMinSidePt

    fun yieldsToUserDot(labelFrame: MapRect, userDotCentre: MapPoint?): Boolean {
        if (userDotCentre == null || labelFrame.width <= 0 || labelFrame.height <= 0) return false
        return labelFrame.intersects(
            MapRect(
                userDotCentre.x - userDotClearancePt,
                userDotCentre.y - userDotClearancePt,
                userDotClearancePt * 2,
                userDotClearancePt * 2,
            )
        )
    }

    fun yieldsToStatusBar(labelFrame: MapRect, safeTop: Double): Boolean =
        safeTop > 0 && labelFrame.width > 0 && labelFrame.height > 0 && labelFrame.minY < safeTop
}

// iOS: FogCellGrid.swift. Adapters convert their projection to MapKit's 2^28 world units.
data class FogCellGrid(
    val sideMapPoints: Double,
    val originOffset: MapPoint,
    val sideScreenPoints: Double,
) {
    companion object {
        const val baseMapPoints = 512.0
        const val minScreenPoints = 14.0
        const val maxDoublings = 12

        fun make(visible: MapRect, viewportWidth: Double): FogCellGrid? {
            if (!(viewportWidth > 0 && visible.width > 0 && visible.width.isFinite())) return null
            val scale = viewportWidth / visible.width
            if (!(scale.isFinite() && scale > 0)) return null
            var side = baseMapPoints
            var screen = side * scale
            var doublings = 0
            while (screen < minScreenPoints && doublings < maxDoublings) {
                side *= 2
                screen *= 2
                doublings++
            }
            if (!(screen >= minScreenPoints && screen.isFinite())) return null
            return FogCellGrid(
                side,
                MapPoint(
                    floor(visible.minX / side) * side - visible.minX,
                    floor(visible.minY / side) * side - visible.minY,
                ),
                screen,
            )
        }
    }
}
