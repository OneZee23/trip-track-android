package com.onezee.triptrack.feature.trip.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.feature.trip.CheckpointState
import com.onezee.triptrack.feature.trip.PhotoPinState
import com.onezee.triptrack.map.MapHost
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.map.marker.MapMarker
import com.onezee.triptrack.map.route.MapRoutes
import com.onezee.triptrack.map.route.RouteAppearance
import com.onezee.triptrack.map.route.RouteShape
import com.onezee.triptrack.map.replay.MapPlayback
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.ui.LightRoutePreview
import com.onezee.triptrack.ui.LiveMaps

/** What the trip's one map draws: the selected trip only, its checkpoints and its photos. */
@Immutable
data class TripMapInput(
    val route: RouteShape?,
    val checkpoints: List<CheckpointState>,
    val photoPins: List<PhotoPinState>,
    val photoBitmaps: Map<java.util.UUID, Bitmap>,
    val playback: MapPlayback? = null,
    val litFraction: Double? = null,
    val hiddenLockPoints: List<GeoPoint> = emptyList(),
)

/**
 * iOS: MapExpansionState.swift. The fullscreen map is the SAME map as the hero: it crossfades in at
 * its final size (200 ms, 150 with reduced motion) while the hero shows a still, and on the way back
 * it fades out over that still before the map returns to the hero.
 */
enum class MapExpansion { collapsed, expanding, expanded, collapsing;
    val presented: Boolean get() = this != collapsed
    val showsChrome: Boolean get() = this == expanded
}

/** The expansion state and the overlay's opacity, owned by the trip screen. */
@Stable
class TripMapExpansion {
    var state by mutableStateOf(MapExpansion.collapsed)
        private set
    internal val opacity = Animatable(0f)

    fun expand() { if (state == MapExpansion.collapsed) state = MapExpansion.expanding }
    fun collapse() { if (state == MapExpansion.expanded) state = MapExpansion.collapsing }

    internal suspend fun run(reduceMotion: Boolean, onExpanded: () -> Unit, onCollapsed: () -> Unit) {
        val spec = tween<Float>(if (reduceMotion) 150 else 200, easing = EaseOut)
        when (state) {
            MapExpansion.expanding -> { opacity.animateTo(1f, spec); state = MapExpansion.expanded; onExpanded() }
            MapExpansion.collapsing -> { opacity.animateTo(0f, spec); state = MapExpansion.collapsed; onCollapsed() }
            else -> Unit
        }
    }
}

/** Drives the expansion's fades; the callbacks re-fit the camera to the slot the map now sits in. */
@Composable
fun TripMapExpansionEffects(expansion: TripMapExpansion, onExpanded: () -> Unit, onCollapsed: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(expansion.state) { expansion.run(reduceMotion, onExpanded, onCollapsed) }
}

/**
 * The live map of a trip: plain route appearance (no fog on the trip screen, CLAUDE.md «Trip detail
 * map scope»), compact markers on the hero and named ones fullscreen. The style follows the app's
 * theme, which reaches it explicitly.
 */
@Composable
fun TripLiveMap(
    state: MapHostState,
    input: TripMapInput,
    fullscreen: Boolean,
    dark: Boolean,
    chromeInsets: MapInsets,
    onMarkerTap: (MapMarker) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = appStrings
    val markers = remember(input.checkpoints, input.photoPins, input.photoBitmaps, input.hiddenLockPoints, fullscreen, strings.language) {
        input.checkpoints.mapNotNull { checkpoint ->
            checkpoint.coordinate?.let { MapMarker("cp:${checkpoint.id}", it, MapMarker.Kind.CHECKPOINT, label = if (fullscreen) checkpoint.displayName(strings) else null) }
        } + input.photoPins.map { pin -> MapMarker("photo:${pin.id}", pin.coordinate, MapMarker.Kind.PHOTO, image = input.photoBitmaps[pin.id]) } +
            input.hiddenLockPoints.mapIndexed { index, point -> MapMarker("hidden_lock:$index", point, MapMarker.Kind.HIDDEN_LOCK, image = hiddenLockBitmap()) }
    }
    val style = remember { LiveMaps.style(dark) }
    val appearance = RouteAppearance.plain.copy(hiddenInkArgb = AppTheme.colors.text.copy(alpha = 0.55f).toArgb())
    MapHost(
        state = state,
        modifier = modifier.testTag("detail_live_map"),
        routes = input.route?.let { MapRoutes(listOf(it), appearance, input.litFraction) },
        playback = input.playback,
        markers = markers,
        onMarkerTap = onMarkerTap,
        chromeInsets = chromeInsets,
        cameraChromeInsets = MapInsets.Zero,
        // The hero stands under the status bar and ends far above the navigation bar.
        systemInsets = if (fullscreen) WindowInsets.safeDrawing else WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        styleUri = style,
        // The one map moves between the hero and the fullscreen layer and fades in both.
        textureMode = true,
        gesturesEnabled = fullscreen,
    )
}

/** The fit of the hero: the whole route with 30 of air (iOS RouteMapView.defaultFitInsets), no animation. */
fun heroFit(route: RouteShape?): MapCameraCommand? =
    route?.bounds?.let { MapCameraCommand.FitRoute(it, MapInsets(top = 52.0, left = 40.0, bottom = 40.0, right = 52.0), animated = false) }

/**
 * The fit of the fullscreen layer (iOS FullscreenMapSheet.fitPadding without replay): 56 under the
 * top controls, 28 at the sides, 56 above the bottom plus the card that may stand there. The map's
 * own safe area is added by the controller.
 */
fun fullscreenFit(route: RouteShape?, cardHeight: Double, replay: Boolean = false): MapCameraCommand? =
    route?.bounds?.let { MapCameraCommand.FitRoute(it, MapInsets(top = 56.0, left = 28.0, bottom = (if (replay) 190.0 else 56.0) + cardHeight, right = 28.0), animated = false) }

/**
 * iOS: TripDetailRoutePlaceholder.swift. The first frame of the hero, and its stand-in while the map
 * is elsewhere: the route alone on the card-alt ground (32 of padding around the drawing's own 16),
 * or the map symbol when there is nothing to draw.
 */
@Composable
fun RoutePlaceholder(coordinates: List<GeoPoint>, modifier: Modifier = Modifier) {
    Box(modifier.background(AppTheme.colors.cardAlt).testTag("detail_map_placeholder"), contentAlignment = Alignment.Center) {
        if (coordinates.size > 1) {
            LightRoutePreview(listOf(coordinates), Modifier.fillMaxSize().padding(32.dp))
        } else {
            Glyph(AppGlyph.map, contentDescription = null, tint = AppTheme.colors.textTertiary, size = 34.dp)
        }
    }
}

/**
 * The hero's live map fades in once its first fitted camera has settled (iOS: `hasRenderedRoute`,
 * 180 ms, immediate with reduced motion); the placeholder stays until then and never returns.
 */
@Composable
fun rememberRevealOpacity(rendered: Boolean): Float {
    val reduceMotion = LocalReduceMotion.current
    val alpha = remember { Animatable(if (rendered) 1f else 0f) }
    LaunchedEffect(rendered) {
        if (rendered) { if (reduceMotion) alpha.snapTo(1f) else alpha.animateTo(1f, tween(180, easing = EaseOut)) }
    }
    return alpha.value
}

/** [content] faded by [opacity]; the overlay's ground is the theme's, so the hero never shows through. */
@Composable
fun FullscreenLayer(opacity: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().alpha(opacity).background(AppTheme.colors.bg).testTag("fullscreen_map")) { content() }
}
