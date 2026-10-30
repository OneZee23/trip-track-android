package com.onezee.triptrack.map.marker

import android.graphics.Bitmap
import com.onezee.triptrack.model.GeoPoint

/**
 * A point on the map the person can tap: a place, a suggested place, a checkpoint or a photo.
 *
 * Deliberately simple for now: a disc per kind, a round thumbnail for a photo, an optional label in
 * the map style's own font. The house pins of the iPhone app (PlacePinView, the labelled checkpoint
 * card, the stacked photo pin) come with the screens that need them.
 */
data class MapMarker(
    val id: String,
    val position: GeoPoint,
    val kind: Kind,
    val label: String? = null,
    /** A photo's thumbnail; drawn round with a white ring. Without it the photo is a plain disc. */
    val image: Bitmap? = null,
) {
    enum class Kind {
        /** A place (iOS: PlacePinView, filled disc, wins collisions). */
        PLACE,

        /** A suggested place: a hollow muted disc (iOS: the suggestion pin of PlacesMapView). */
        PLACE_SUGGESTION,

        /** A checkpoint on a trip (iOS: the compact checkpoint marker, 32 pt). */
        CHECKPOINT,

        /** A photo on a trip (iOS: PhotoAnnotation). */
        PHOTO,
    }

    companion object {
        /**
         * The touch target around a marker's CENTRE, in screen points: 44 pt, Apple's minimum target.
         * A 28 pt disc with 6 pt of slack gave 40, and a pin could only be hit far zoomed in (iOS:
         * MyMapRepresentable.Coordinator.touchTarget).
         */
        const val TOUCH_TARGET_POINTS = 44.0
    }
}
