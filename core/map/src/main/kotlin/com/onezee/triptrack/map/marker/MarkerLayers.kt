package com.onezee.triptrack.map.marker

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import com.onezee.triptrack.map.route.RouteLayers
import com.onezee.triptrack.map.route.SpeedColorScale
import kotlin.math.hypot
import kotlin.math.max
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

/**
 * The MapLibre side of markers: one GeoJSON source and five layers, bottom to top: discs, their
 * centre dots, images (photos and home), labels, callouts. Images are style images keyed by marker
 * id; they are added and removed with the markers.
 */
internal class MarkerLayers(private val density: Float) {
    private var markers: List<MapMarker> = emptyList()
    private val images = HashMap<String, Bitmap>()

    val layerIds: List<String> = listOf(DISCS, DOTS, PHOTOS, LABELS, CALLOUTS)

    fun addTo(style: Style, markers: List<MapMarker>) {
        style.addSource(GeoJsonSource(SOURCE))
        style.addLayer(
            CircleLayer(DISCS, SOURCE)
                .withFilter(Expression.neq(Expression.get(HAS_IMAGE), true))
                .withProperties(
                    PropertyFactory.circleRadius(Expression.get(RADIUS)),
                    PropertyFactory.circleColor(Expression.toColor(Expression.get(FILL))),
                    PropertyFactory.circleStrokeColor(Expression.toColor(Expression.get(STROKE))),
                    PropertyFactory.circleStrokeWidth(Expression.get(STROKE_WIDTH)),
                ),
        )
        style.addLayer(
            CircleLayer(DOTS, SOURCE)
                .withFilter(Expression.has(DOT_RADIUS))
                .withProperties(
                    PropertyFactory.circleRadius(Expression.get(DOT_RADIUS)),
                    PropertyFactory.circleColor(Expression.toColor(Expression.get(DOT_COLOR))),
                ),
        )
        style.addLayer(
            SymbolLayer(PHOTOS, SOURCE)
                .withFilter(Expression.eq(Expression.get(HAS_IMAGE), true))
                .withProperties(
                    PropertyFactory.iconImage(Expression.get(IMAGE)),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                ),
        )
        style.addLayer(
            SymbolLayer(LABELS, SOURCE)
                .withFilter(Expression.has(LABEL))
                .withProperties(
                    PropertyFactory.textField(Expression.get(LABEL)),
                    // A font of the map style itself: labels need its glyphs.
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(12f),
                    PropertyFactory.textOffset(arrayOf(0f, 1.6f)),
                    PropertyFactory.textAnchor("top"),
                    PropertyFactory.textColor(INK),
                    PropertyFactory.textHaloColor(0xFFFFFFFF.toInt()),
                    PropertyFactory.textHaloWidth(1.2f),
                    PropertyFactory.textOptional(true),
                ),
        )
        // The callout stands ABOVE the disc and never takes a tap: the target is the disc's centre.
        style.addLayer(
            SymbolLayer(CALLOUTS, SOURCE)
                .withFilter(Expression.has(CALLOUT))
                .withProperties(
                    PropertyFactory.iconImage(Expression.get(CALLOUT)),
                    PropertyFactory.iconAnchor("bottom"),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                ),
        )
        update(style, markers)
    }

    fun update(style: Style, markers: List<MapMarker>) {
        this.markers = markers
        val wanted = HashMap<String, Pair<MapMarker, Bitmap>>()
        for (marker in markers) {
            marker.image?.let { wanted[imageId(marker)] = marker to it }
            marker.callout?.let { wanted[calloutId(marker)] = marker to it }
        }
        for (stale in images.keys - wanted.keys) style.removeImage(stale)
        for ((id, entry) in wanted) {
            val (marker, bitmap) = entry
            if (images[id] === bitmap) continue
            val prepared = if (id.startsWith(CALLOUT_PREFIX)) {
                // The card's bottom is transparent room for the disc and the gap: anchored at the
                // marker's point, it then stands clear of the disc whatever size the disc has.
                padBottom(bitmap, ((discRadius(marker) + MapMarker.CALLOUT_GAP) * density).toInt())
            } else if (marker.kind == MapMarker.Kind.HIDDEN_LOCK) {
                Bitmap.createScaledBitmap(bitmap, (PHOTO_DIAMETER * density).toInt(), (PHOTO_DIAMETER * density).toInt(), true)
            } else {
                val diameter = if (marker.kind == MapMarker.Kind.HOME) HOME_DIAMETER else PHOTO_DIAMETER
                val ring = if (marker.kind == MapMarker.Kind.HOME) HOME_RING else PHOTO_RING
                roundPhoto(bitmap, (diameter * density).toInt(), ring * density)
            }
            style.addImage(id, prepared)
        }
        images.clear()
        for ((id, entry) in wanted) images[id] = entry.second
        val features = markers.map { marker ->
            Feature.fromGeometry(RouteLayers.point(marker.position)).also { f ->
                f.addStringProperty(ID, marker.id)
                marker.label?.let { f.addStringProperty(LABEL, it) }
                val look = look(marker.kind)
                val scale = if (marker.selected) MapMarker.SELECTED_SCALE else 1.0
                f.addNumberProperty(RADIUS, look.radius * scale)
                f.addStringProperty(FILL, RouteLayers.rgba(look.fill))
                f.addStringProperty(STROKE, RouteLayers.rgba(look.stroke))
                f.addNumberProperty(STROKE_WIDTH, look.strokeWidth * scale)
                if (look.dotRadius > 0 && marker.image == null) {
                    f.addNumberProperty(DOT_RADIUS, look.dotRadius * scale)
                    f.addStringProperty(DOT_COLOR, RouteLayers.rgba(look.dot))
                }
                if (marker.image != null) {
                    f.addBooleanProperty(HAS_IMAGE, true)
                    f.addStringProperty(IMAGE, imageId(marker))
                }
                if (marker.callout != null) f.addStringProperty(CALLOUT, calloutId(marker))
            }
        }
        style.getSourceAs<GeoJsonSource>(SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /**
     * The marker under a tap, by a 44 pt target around each marker's CENTRE: centred rather than a
     * stretched frame, because a label drawn above or below the disc would pull the target off it.
     * The nearest centre wins when targets overlap.
     */
    fun hit(map: MapLibreMap, screen: PointF): MapMarker? {
        val half = (MapMarker.TOUCH_TARGET_POINTS / 2 * density).toFloat()
        var best: MapMarker? = null
        var bestDistance = Float.MAX_VALUE
        val box = RectF(screen.x - half, screen.y - half, screen.x + half, screen.y + half)
        val ids = map.queryRenderedFeatures(box, DISCS, PHOTOS).mapNotNull { it.getStringProperty(ID) }.toSet()
        for (marker in markers) {
            if (marker.id !in ids) continue
            val at = map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(marker.position.latitude, marker.position.longitude))
            val dx = at.x - screen.x
            val dy = at.y - screen.y
            if (max(kotlin.math.abs(dx), kotlin.math.abs(dy)) > half) continue
            val distance = hypot(dx, dy)
            if (distance < bestDistance) {
                bestDistance = distance
                best = marker
            }
        }
        return best
    }

    private data class Look(
        val radius: Double,
        val fill: Int,
        val stroke: Int,
        val strokeWidth: Double,
        val dotRadius: Double = 0.0,
        val dot: Int = 0,
    )

    private fun look(kind: MapMarker.Kind): Look = when (kind) {
        // iOS PlacePinView: a 20 pt accent disc with a 2 pt white border and a 6.4 pt white dot.
        MapMarker.Kind.PLACE -> Look(10.0, ACCENT, WHITE, 2.0, 3.2, WHITE)
        // The same disc in the muted colour: outside the period, not gone.
        MapMarker.Kind.PLACE_MUTED -> Look(10.0, MUTED, WHITE, 2.0, 3.2, WHITE)
        // A 16 pt white disc with a secondary ring and a 3.6 pt dot. The iOS ring is dashed and inset
        // 3 pt; MapLibre has no dashed circle stroke, so here it is solid and on the edge.
        MapMarker.Kind.PLACE_SUGGESTION -> Look(8.0, WHITE, SECONDARY, 2.0, 1.8, SECONDARY)
        // iOS compact checkpoint marker: 32 pt, white ring 2.5 pt around the accent.
        MapMarker.Kind.CHECKPOINT -> Look(13.5, CHECKPOINT, WHITE, 2.5)
        MapMarker.Kind.PHOTO -> Look(13.5, SECONDARY, WHITE, 2.5)
        // Drawn from its image; the disc is the fallback when a screen gives none.
        MapMarker.Kind.HOME -> Look(HOME_DIAMETER / 2, ACCENT, WHITE, HOME_RING.toDouble())
        MapMarker.Kind.HIDDEN_LOCK -> Look(10.0, WHITE, SECONDARY, 1.5)
    }

    /** How far the marker reaches above its point, in screen points. */
    private fun discRadius(marker: MapMarker): Double = when {
        marker.image != null -> (if (marker.kind == MapMarker.Kind.HOME) HOME_DIAMETER else PHOTO_DIAMETER) / 2
        else -> look(marker.kind).radius * (if (marker.selected) MapMarker.SELECTED_SCALE else 1.0)
    }

    private fun imageId(marker: MapMarker) = "triptrack-photo-${marker.id}"

    private fun calloutId(marker: MapMarker) = "$CALLOUT_PREFIX${marker.id}"

    companion object {
        const val DISCS = "triptrack-marker-discs"
        const val DOTS = "triptrack-marker-dots"
        const val PHOTOS = "triptrack-marker-photos"
        const val LABELS = "triptrack-marker-labels"
        const val CALLOUTS = "triptrack-marker-callouts"
        private const val SOURCE = "triptrack-markers"
        private const val ID = "id"
        private const val LABEL = "label"
        private const val RADIUS = "radius"
        private const val FILL = "fill"
        private const val STROKE = "stroke"
        private const val STROKE_WIDTH = "strokeWidth"
        private const val DOT_RADIUS = "dotRadius"
        private const val DOT_COLOR = "dotColor"
        private const val HAS_IMAGE = "hasImage"
        private const val IMAGE = "image"
        private const val CALLOUT = "callout"
        private const val CALLOUT_PREFIX = "triptrack-callout-"

        // iOS AtlasTheme.accent and mutedPin, secondary and control (light: the Atlas map is a day
        // map in either theme), RouteMapView.markerAccent, AtlasTheme.ink.
        private val ACCENT = SpeedColorScale.argb(0xC8, 0x47, 0x2D, 1.0)
        private val MUTED = SpeedColorScale.argb(0xA0, 0x98, 0x8B, 1.0)
        private val CHECKPOINT = SpeedColorScale.argb(0xC2, 0x45, 0x2B, 1.0)
        private val SECONDARY = SpeedColorScale.argb(0x64, 0x64, 0x6E, 1.0)
        private val INK = SpeedColorScale.argb(0x1E, 0x1E, 0x23, 1.0)
        private val WHITE = SpeedColorScale.argb(0xFF, 0xFF, 0xFF, 1.0)
        private const val PHOTO_DIAMETER = 32.0
        private const val PHOTO_RING = 2.5f

        // iOS HomePinView: 28 pt, a 2 pt white border.
        private const val HOME_DIAMETER = 28.0
        private const val HOME_RING = 2f

        /** [source] with [bottomPx] of transparent room under it. */
        fun padBottom(source: Bitmap, bottomPx: Int): Bitmap {
            val out = Bitmap.createBitmap(source.width, source.height + bottomPx.coerceAtLeast(0), Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(source, 0f, 0f, null)
            return out
        }

        /**
         * A photo as a round thumbnail with a white ring: without the ring a dark frame merges with the
         * map and a light one with the road, and the photo stops reading as an object.
         */
        fun roundPhoto(source: Bitmap, sizePx: Int, ringPx: Float): Bitmap {
            val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = 0xFFFFFFFF.toInt()
            val r = sizePx / 2f
            canvas.drawCircle(r, r, r, paint)
            val inner = r - ringPx
            val scale = max(2 * inner / source.width, 2 * inner / source.height)
            val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate(r - source.width * scale / 2, r - source.height * scale / 2)
            })
            paint.shader = shader
            canvas.drawCircle(r, r, inner, paint)
            return out
        }
    }
}
