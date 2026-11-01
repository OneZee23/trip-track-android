package com.onezee.triptrack.map.camera

import com.onezee.triptrack.model.GeoBounds
import com.onezee.triptrack.model.GeoPoint
import kotlin.math.max

/** Edge insets in screen points (dp), in UIEdgeInsets order. */
data class MapInsets(val top: Double = 0.0, val left: Double = 0.0, val bottom: Double = 0.0, val right: Double = 0.0) {
    operator fun plus(other: MapInsets) =
        MapInsets(top + other.top, left + other.left, bottom + other.bottom, right + other.right)

    companion object {
        val Zero = MapInsets()

        fun all(value: Double) = MapInsets(value, value, value, value)
    }
}

/** A size in screen points (dp). */
data class MapSize(val width: Double, val height: Double)

/**
 * What the camera is asked to do (iOS: MapCameraCommand in MyMapViewModel.swift, plus the route fit
 * of RouteMapView and the plain moves the debug screen needs).
 */
sealed interface MapCameraCommand {
    val animated: Boolean

    /** Frame [bounds] under the chrome of [padding], with the Atlas' 300 m floor. */
    data class Fit(val bounds: GeoBounds, val padding: Padding, override val animated: Boolean = true) : MapCameraCommand

    /**
     * Frame a whole route in the map's current bounds with [insets] around it and the 400 m floor of
     * RouteMapView, so a trip that never moved shows its streets instead of a grey maximum zoom.
     */
    data class FitRoute(
        val bounds: GeoBounds,
        val insets: MapInsets = DEFAULT_ROUTE_INSETS,
        override val animated: Boolean = true,
    ) : MapCameraCommand

    /** Centre on [centre] at a MapLibre [zoom], keeping the heading. */
    data class Move(
        val centre: GeoPoint,
        val zoom: Double,
        override val animated: Boolean = true,
        /** iOS: RouteMapView.applyCamera, 0.45 s before following by non-animated centres. */
        val durationMillis: Int = 600,
        /** iOS: MKUserTrackingMode.followWithHeading. Null keeps the current map heading. */
        val bearing: Double? = null,
        /** iOS: MapViewRepresentable.layoutMargins. Keep a followed car above the idle HUD. */
        val viewportInsets: MapInsets? = null,
    ) : MapCameraCommand

    /** iOS: MapCameraCommand.Padding. How much chrome covers the map, counted from the screen's EDGES. */
    enum class Padding(val chrome: MapInsets) {
        /** The collapsed Atlas sheet plus navigation; also search targets framed without a card. */
        overview(MapInsets(top = 110.0, left = 40.0, bottom = 300.0, right = 40.0)),

        /** The region card and its navigation clearance take 460 points. */
        region(MapInsets(top = 110.0, left = 32.0, bottom = 480.0, right = 32.0)),

        /** The trip card includes the navigation clearance below its body. */
        trip(MapInsets(top = 110.0, left = 44.0, bottom = 310.0, right = 44.0));

        fun insets(safeArea: MapInsets, size: MapSize): MapInsets = insets(chrome, safeArea, size)

        companion object {
            /**
             * What must stay for the map after all insets. Below this asking makes no sense: on a
             * negative remainder MapKit answered with a frame the size of the world.
             */
            const val minimumViewport = 120.0

            /**
             * Insets to ADD to the map's safe area so the chrome is cleared (iOS: Padding.insets).
             *
             * MapKit frames by the SUM of the safe area and the edge padding. The Atlas gives its sheet
             * ~290 pt at the bottom through the safe area, and `.region` asked for another 480: −68 pt
             * remained on an 874 pt screen, and MapKit answered with a frame five times too wide ("I
             * tapped a region and got carried off into nowhere", owner, 26 September). So only what the
             * safe area does not already give is asked for, cut down whenever the map would keep less
             * than [minimumViewport]. The Android camera adds the safe area itself (MapCamera), so the
             * sum MapLibre receives is the same as MapKit's.
             */
            fun insets(chrome: MapInsets, safeArea: MapInsets, size: MapSize): MapInsets {
                fun extra(want: Double, have: Double) = max(0.0, want - have)
                var top = extra(chrome.top, safeArea.top)
                var bottom = extra(chrome.bottom, safeArea.bottom)
                var left = extra(chrome.left, safeArea.left)
                var right = extra(chrome.right, safeArea.right)

                // The remainder is counted from what MapKit counts it from: the size minus the safe
                // area minus our addition.
                fun fit(a: Double, b: Double, total: Double, safe: Double): Pair<Double, Double> {
                    val free = total - safe
                    if (!(free > 0)) return 0.0 to 0.0
                    val room = free - minimumViewport
                    if (!(room < a + b)) return a to b
                    if (!(room > 0)) return 0.0 to 0.0
                    val scale = room / (a + b)
                    return a * scale to b * scale
                }
                fit(top, bottom, size.height, safeArea.top + safeArea.bottom).let { (t, b) -> top = t; bottom = b }
                fit(left, right, size.width, safeArea.left + safeArea.right).let { (l, r) -> left = l; right = r }
                return MapInsets(top = top, left = left, bottom = bottom, right = right)
            }
        }
    }

    companion object {
        /** iOS: RouteMapView.defaultFitInsets, the insets when a screen names none. */
        val DEFAULT_ROUTE_INSETS = MapInsets.all(30.0)
    }
}
