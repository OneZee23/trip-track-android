// iOS: MyMapRepresentable.swift (Coordinator.handleTap, trips(near:), placesVisible, syncPlaces,
// syncHome, syncSelectedRoute), MapZoomLevel, PlacePinView's label box, HomePinView.
package com.onezee.triptrack.feature.atlas.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.core.content.res.ResourcesCompat
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.designsystem.theme.AtlasTheme
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.logic.RegionAtlas
import com.onezee.triptrack.logic.RegionLabelModel
import com.onezee.triptrack.feature.atlas.AtlasSelection
import com.onezee.triptrack.feature.atlas.AtlasState
import com.onezee.triptrack.map.MapFog
import com.onezee.triptrack.map.MapHost
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.MapPoints
import com.onezee.triptrack.map.MapStyles
import com.onezee.triptrack.map.MapTap
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.map.fog.FogPalette
import com.onezee.triptrack.map.marker.MapMarker
import com.onezee.triptrack.map.route.MapRoutes
import com.onezee.triptrack.map.route.RouteAppearance
import com.onezee.triptrack.map.route.RouteGeometry
import com.onezee.triptrack.map.vein.MapVein
import com.onezee.triptrack.model.AtlasPlacePin
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.MapTripPin
import com.onezee.triptrack.ui.LocalLiveMaps
import com.onezee.triptrack.ui.LocalTripPhotoImages
import com.onezee.triptrack.ui.PhotoSource
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** iOS: MapZoomLevel. Counted in degrees of latitude the view shows. */
enum class MapZoomLevel { far, region, close;

    companion object {
        fun of(latitudeSpan: Double): MapZoomLevel = when {
            latitudeSpan > 3.0 -> far
            latitudeSpan > 0.4 -> region
            else -> close
        }

        /**
         * iOS Coordinator.placesVisible. From afar there are no place pins at all: a pin does not
         * scale with the map, and on a krai a dozen equal discs fought the opened world for
         * attention. A place appears as one comes closer, with the roads that lead to it.
         */
        fun placesVisible(latitudeSpan: Double): Boolean = of(latitudeSpan) != far
    }
}

/** A trip's route in map points with its box, built once per library for the road hit test. */
class RouteIndex(val id: UUID, val points: List<com.onezee.triptrack.logic.MapPoint>, val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    companion object {
        fun build(trips: List<MapTripPin>): List<RouteIndex> = trips.mapNotNull { trip ->
            if (trip.route.size < 2) return@mapNotNull null
            val points = trip.route.map(MapPoints::point)
            RouteIndex(trip.id, points, points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
        }
    }
}

/**
 * iOS: Coordinator.trips(near:on:). Every trip whose route passes within finger reach (26 pt) of the
 * tap, closest first. Only the nearest made the map feel like it held four trips: the roads one
 * drives most belong to a dozen, and a tap must answer with all of them. Everything is in MAP
 * points, so one conversion of the reach is all the camera is asked for.
 */
object RoadHits {
    const val REACH_POINTS = 26.0

    fun trips(tap: MapTap, routes: List<RouteIndex>): List<UUID> {
        val perScreenPoint = MapPoints.metresPerPoint(tap.coordinate.latitude, tap.zoom) * MapPoints.mapPointsPerMetre(tap.coordinate.latitude)
        val reach = REACH_POINTS * perScreenPoint
        val target = MapPoints.point(tap.coordinate)
        val hits = ArrayList<Pair<UUID, Double>>()
        for (route in routes) {
            // Whole-route reject first: at street zoom this discards the other side of the country.
            if (route.maxX + reach < target.x || route.minX - reach > target.x || route.maxY + reach < target.y || route.minY - reach > target.y) continue
            var best = Double.MAX_VALUE
            for (i in 1 until route.points.size) {
                val a = route.points[i - 1]
                val b = route.points[i]
                if (min(a.x, b.x) - reach > target.x || target.x > max(a.x, b.x) + reach ||
                    min(a.y, b.y) - reach > target.y || target.y > max(a.y, b.y) + reach) continue
                best = min(best, distance(target.x, target.y, a.x, a.y, b.x, b.y))
            }
            if (best <= reach) hits += route.id to best
        }
        return hits.sortedBy { it.second }.map { it.first }
    }

    private fun distance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 0) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}

/** What a tap on the Atlas map means once no marker took it (iOS Coordinator.handleTap after the pins). */
sealed interface AtlasMapTap {
    data class Road(val tripIds: List<UUID>) : AtlasMapTap
    data class Ground(val point: GeoPoint, val zoomToRegion: Boolean) : AtlasMapTap

    companion object {
        /**
         * Up close the road under the finger is the only thing on screen, and it belongs to its trips;
         * from the country view a tap is a region, and only there does the camera fly to it.
         */
        fun resolve(tap: MapTap, latitudeSpan: Double, routes: List<RouteIndex>): AtlasMapTap {
            val level = MapZoomLevel.of(latitudeSpan)
            if (level != MapZoomLevel.far) {
                val trips = RoadHits.trips(tap, routes)
                if (trips.isNotEmpty()) return Road(trips)
            }
            return Ground(tap.coordinate, zoomToRegion = level == MapZoomLevel.far)
        }
    }
}

/** iOS RouteVeinRenderer.resolvedColors().selected: the selected trip over the fog. */
fun selectedRouteColour(palette: FogPalette): Int =
    if (palette.isDark) worldArgb(0xF6, 0xB9, 0x8A, 1.0) else worldArgb(0xC8, 0x47, 0x2D, 1.0)

/**
 * The Atlas map: MapLibre with the fog in the chosen style, the vein of every opened road, the
 * selected trip over them, the places and home. Always the day map (iOS: the Atlas' MKMapView is
 * forced light): on the night map the open world came out darker than the fog.
 *
 * Without MapLibre (screenshot tests, an unsupported ABI) the ground is the fog's own colour: the
 * sheet and the chrome above it are what those frames check.
 */
@Composable
fun AtlasMapLayer(
    state: AtlasState,
    mapState: MapHostState,
    chrome: MapInsets,
    latitudeSpan: Double,
    strings: AppStrings,
    onMarker: (MapMarker) -> Unit,
    onTap: (MapTap) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!LocalLiveMaps.current) {
        Box(modifier.fillMaxSize().background(AtlasTheme.colors.fog).testTag("atlas_map"))
        return
    }
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val palette = state.fog.look.palette
    val unit = LocalDistanceUnit.current
    val labels = remember(state.revealed, unit, strings) {
        val visited = state.revealed.regionKm.filterValues { it > 0 }.keys
        // The first loading frame must not parse the atlas on the UI thread. A nonempty layer is
        // published only after MyMapViewModel has loaded the same atlas on its worker.
        if (visited.isEmpty()) emptyList() else RegionLabelModel.regionLabels(
            RegionAtlas.shared.regions, state.revealed, visited, unit, strings.language.code, strings)
    }
    val showsPlaces = MapZoomLevel.placesVisible(latitudeSpan)
    val calloutPin = state.selectedPlace
    val callout = remember(calloutPin, strings) { calloutPin?.let { placeCallout(context, density, it, strings) } }
    val house = remember(density) { homeGlyphBitmap(density) }
    val openTrip = (state.selection as? AtlasSelection.Trip)?.id
    val photoTrips = remember(state.exploration.trips, state.appearance.showsPhotos, openTrip) {
        tripPhotoPins(state.exploration.trips, state.appearance.showsPhotos, openTrip)
    }
    val images = LocalTripPhotoImages.current
    val thumbs by produceState(emptyMap<UUID, Bitmap>(), photoTrips, images) {
        if (images == null) return@produceState
        val loaded = HashMap<UUID, Bitmap>()
        for (trip in photoTrips) {
            val filename = trip.photoFilename ?: continue
            images.load(PhotoSource.Local(filename), (TRIP_THUMB_POINTS * density).toInt())?.let { loaded[trip.id] = it.asAndroidBitmap() }
        }
        value = loaded
    }
    val markers = remember(state.placePins, state.selectedPlaceId, showsPlaces, callout, state.home.mapPin, house, photoTrips, thumbs, openTrip) {
        buildList {
            for (trip in photoTrips) {
                // A disc until the thumbnail arrives, as iOS shows its glyph until then.
                add(MapMarker("$TRIP_MARKER${trip.id}", trip.coordinate, MapMarker.Kind.PHOTO, image = thumbs[trip.id], selected = trip.id == openTrip))
            }
            if (showsPlaces) for (pin in state.placePins) {
                val selected = pin.id == state.selectedPlaceId
                add(MapMarker("place:${pin.id}", pin.coordinate, if (pin.inPeriod) MapMarker.Kind.PLACE else MapMarker.Kind.PLACE_MUTED,
                    selected = selected, callout = if (selected) callout else null))
            }
            state.home.mapPin?.let { add(MapMarker(HOME_MARKER, it, MapMarker.Kind.HOME, image = house)) }
        }
    }
    val routes = remember(state.selectedRoute, palette) {
        state.selectedRoute.takeIf { it.size > 1 }?.let {
            MapRoutes(listOf(RouteGeometry.build(it, preview = true)), RouteAppearance.overFog(palette).copy(lineColorOverride = selectedRouteColour(palette)))
        }
    }
    val vein = remember(state.revealed, palette) { MapVein(state.revealed, palette) }
    val fog: MapFog = state.fog
    MapHost(
        state = mapState,
        modifier = modifier.fillMaxSize().testTag("atlas_map"),
        fog = fog,
        vein = vein,
        routes = routes,
        markers = markers,
        onMarkerTap = onMarker,
        chromeInsets = chrome,
        systemInsets = WindowInsets.safeDrawing,
        styleUri = MapStyles.OPEN_FREE_MAP_LIBERTY,
        onMapTap = onTap,
        showsUserLocation = state.locationPermission == com.onezee.triptrack.feature.atlas.AtlasLocationPermission.allowed,
        regionLabels = labels,
    )
}

const val HOME_MARKER = "home"

/** The id prefix of a trip's photo pin. */
private const val TRIP_MARKER = "trip:"

/** iOS TripPinView loads its thumbnail at 64 (`PhotoStorageService.loadThumbnail(maxSize: 64)`). */
private const val TRIP_THUMB_POINTS = 64

/**
 * iOS syncTripPins: with «Фото из поездок» on, a pin for every trip that has a photo, and only the open
 * trip's own while one is open. Trips without a photo get none: their roads take the tap.
 */
fun tripPhotoPins(trips: List<MapTripPin>, showsPhotos: Boolean, openTrip: UUID?): List<MapTripPin> =
    if (!showsPhotos) emptyList() else trips.filter { it.photoFilename != null && (openTrip == null || it.id == openTrip) }

/** The marker id of a trip's photo pin back to the trip. */
fun tripIdOf(marker: MapMarker): UUID? = marker.id.removePrefix(TRIP_MARKER).takeIf { marker.id.startsWith(TRIP_MARKER) }
    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

/** ARGB from 0…255 channels and a 0…1 alpha, the form the map module takes colours in. */
fun worldArgb(red: Int, green: Int, blue: Int, alpha: Double): Int =
    ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or (red shl 16) or (green shl 8) or blue

/** The marker id of a place pin back to the place. */
fun placeIdOf(marker: MapMarker): UUID? = marker.id.removePrefix("place:").takeIf { marker.id.startsWith("place:") }
    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

/**
 * iOS PlacePinView's label box: the chosen place's name and its last date over the disc. Inter 12
 * medium ink and Inter 12 secondary on the control's white, radius 10, a soft shadow, at most 160
 * wide plus the padding. Drawn here because the map's own symbols cannot carry our type or the card.
 */
fun placeCallout(context: Context, density: Float, pin: AtlasPlacePin, strings: AppStrings): Bitmap {
    val name = pin.name?.takeIf { it.isNotEmpty() } ?: strings.placeUnnamed()
    val detail = pin.lastAt?.let { LocalizedDateFormatter.templates("dMMM", strings.zone).getValue(strings.language).format(it) }
    fun font(id: Int) = runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.DEFAULT
    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font(com.onezee.triptrack.designsystem.R.font.inter_medium)
        textSize = 12 * density
        color = AtlasTheme.colors(false).ink.toArgb()
    }
    val detailPaint = Paint(namePaint).apply {
        typeface = font(com.onezee.triptrack.designsystem.R.font.inter_regular)
        color = AtlasTheme.colors(false).secondary.toArgb()
    }
    val maxText = 160 * density
    val shownName = ellipsize(name, namePaint, maxText)
    val textWidth = max(namePaint.measureText(shownName), detail?.let { detailPaint.measureText(it) } ?: 0f).coerceAtMost(maxText)
    val line = namePaint.fontSpacing
    val shadow = 4 * density
    val boxWidth = textWidth + 16 * density
    val boxHeight = line + (if (detail != null) detailPaint.fontSpacing else 0f) + 8 * density
    val bitmap = Bitmap.createBitmap((boxWidth + shadow * 2).toInt() + 1, (boxHeight + shadow * 2).toInt() + 1, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val box = RectF(shadow, shadow, shadow + boxWidth, shadow + boxHeight)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AtlasTheme.colors(false).control.toArgb()
        setShadowLayer(4 * density, 0f, 2 * density, Color.Black.copy(alpha = 0.18f).toArgb())
    }
    canvas.drawRoundRect(box, 10 * density, 10 * density, fill)
    val top = box.top + 4 * density
    canvas.drawText(shownName, box.left + 8 * density, top - namePaint.ascent(), namePaint)
    if (detail != null) canvas.drawText(detail, box.left + 8 * density, top + line - detailPaint.ascent(), detailPaint)
    return bitmap
}

private fun ellipsize(text: String, paint: Paint, width: Float): String {
    if (paint.measureText(text) <= width) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.substring(0, end) + "…") > width) end--
    return text.substring(0, end) + "…"
}

/** iOS HomePinView: the white house on the accent disc. The marker layer adds the white ring. */
fun homeGlyphBitmap(density: Float): Bitmap {
    val size = (28 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(AtlasTheme.colors(false).accent.toArgb())
    val scale = size / 24f * 0.62f
    val shift = (size - 24f * scale) / 2
    canvas.translate(shift, shift)
    canvas.scale(scale, scale)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val path = android.graphics.Path().apply {
        moveTo(12f, 3.6f); lineTo(21f, 11.4f); lineTo(19.2f, 11.4f); lineTo(19.2f, 20.4f); lineTo(14.2f, 20.4f); lineTo(14.2f, 14.8f)
        lineTo(9.8f, 14.8f); lineTo(9.8f, 20.4f); lineTo(4.8f, 20.4f); lineTo(4.8f, 11.4f); lineTo(3f, 11.4f); close()
    }
    canvas.drawPath(path, paint)
    return bitmap
}
