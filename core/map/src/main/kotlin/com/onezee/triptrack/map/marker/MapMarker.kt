package com.onezee.triptrack.map.marker

import android.graphics.Bitmap
import com.onezee.triptrack.model.GeoPoint

/**
 * A point on the map the person can tap: a place, a suggested place, a checkpoint or a photo.
 *
 * A disc per kind, a round thumbnail for a photo, an optional label in the map style's own font.
 * The pins of Atlas and Places follow iOS PlacePinView: a disc with a centre dot, three states
 * (place, suggestion, outside the period), and the chosen one drawn larger with its [callout] above.
 */
data class MapMarker(
    val id: String,
    val position: GeoPoint,
    val kind: Kind,
    val label: String? = null,
    /** A photo's thumbnail, or the home glyph; drawn round with a white ring. Without it the marker is a plain disc. */
    val image: Bitmap? = null,
    /** The chosen pin: 1.2× its size (iOS PlacePinView.setSelectedAppearance). */
    val selected: Boolean = false,
    /**
     * A card drawn by the screen in its own type, standing above the disc (iOS: the label box of the
     * selected pin, name and date). Only the selected pin carries one: ten names on a city map cover
     * one another and the road, and the map already names its towns itself.
     */
    val callout: Bitmap? = null,
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

        /**
         * A place outside the chosen period on the Atlas: muted, not gone. A place does not stop
         * existing because the window missed it (iOS: PlacePinView.Kind.outOfPeriod).
         */
        PLACE_MUTED,

        /** Home (iOS: HomePinView): a 28 pt accent disc with the house glyph, given as [image]. */
        HOME,

        /** Boundary of a hidden route segment, drawn with the app's own lock image. */
        HIDDEN_LOCK,
    }

    companion object {
        /**
         * The touch target around a marker's CENTRE, in screen points: 44 pt, Apple's minimum target.
         * A 28 pt disc with 6 pt of slack gave 40, and a pin could only be hit far zoomed in (iOS:
         * MyMapRepresentable.Coordinator.touchTarget).
         */
        const val TOUCH_TARGET_POINTS = 44.0

        /** iOS PlacePinView.setSelectedAppearance: the chosen pin is scaled by 1.2. */
        const val SELECTED_SCALE = 1.2

        /** Gap between the top of the disc and the bottom of its callout (iOS: `-(boxHeight + 6)`). */
        const val CALLOUT_GAP = 6.0
    }
}
