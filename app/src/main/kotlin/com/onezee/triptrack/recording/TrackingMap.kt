// iOS: MapViewRepresentable.swift, SmoothTrackManager.swift, MapViewModel.swift (live fog).
package com.onezee.triptrack.recording

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.garage.VehicleAvatar
import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.map.*
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.map.fog.FogLook
import com.onezee.triptrack.map.fog.FogPalette
import com.onezee.triptrack.map.replay.CarHeadingPolicy
import com.onezee.triptrack.map.replay.MapCarMood
import com.onezee.triptrack.map.replay.MapPlayback
import com.onezee.triptrack.map.route.MapRoutes
import com.onezee.triptrack.map.route.RouteAppearance
import com.onezee.triptrack.map.route.withSelectedRouteStyle
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.RouteLineStyle
import com.onezee.triptrack.model.Vehicle
import com.onezee.triptrack.ui.LightRoutePreview
import com.onezee.triptrack.ui.LiveMaps
import com.onezee.triptrack.ui.LocalLiveMaps

// iOS: MKUserTrackingMode. A gesture ends following; the button cycles none → follow → heading → none.
enum class TrackingCameraMode { none, follow, heading;
    fun next(): TrackingCameraMode = when (this) { none -> follow; follow -> heading; heading -> none }
}

@Composable
internal fun TrackingMap(
    state: TrackingState,
    vehicle: Vehicle?,
    fog: RevealedLayer?,
    map: MapHostState,
    mode: TrackingCameraMode,
    onGesture: () -> Unit,
    bottomInset: Double,
    modifier: Modifier = Modifier,
) {
    val recorder = state.recorder
    val fix = recorder.location
    val reduceMotion = LocalReduceMotion.current
    val reveal = remember { Animatable(0f) }
    val hole = recorder.revealCoordinate.takeIf { recorder.isRecording && !recorder.isDraft }
    LaunchedEffect(hole, reduceMotion) {
        reveal.snapTo(if (reduceMotion) 1f else 0f)
        if (hole != null && !reduceMotion) reveal.animateTo(1f, tween(700, easing = Easing { 1 - (1 - it) * (1 - it) * (1 - it) }))
    }
    var course by remember { mutableDoubleStateOf(0.0) }
    var hasCourse by remember { mutableStateOf(false) }
    var lastFixTime by remember { mutableStateOf<java.time.Instant?>(null) }
    LaunchedEffect(fix) {
        fix ?: return@LaunchedEffect
        val target = CarHeadingPolicy.liveCourse(fix.course, fix.courseAccuracy, fix.speed)
        val dt = lastFixTime?.let { java.time.Duration.between(it, fix.timestamp).toMillis() / 1000.0 }?.coerceIn(0.0, 2.0) ?: 1.0
        course = if (!hasCourse && target != null) target else CarHeadingPolicy.smoothed(course, target, dt, reduceMotion)
        if (target != null) hasCourse = true
        lastFixTime = fix.timestamp
    }
    // Only the head's short tail animates; the whole drive never goes through a frame clock.
    val target = state.head.lastOrNull()
    var animatedHead by remember { mutableStateOf(target) }
    val headProgress = remember { Animatable(1f) }
    LaunchedEffect(target, recorder.isPaused) {
        if (target == null || recorder.isPaused) return@LaunchedEffect
        val from = animatedHead ?: target
        if (reduceMotion) { animatedHead = target; return@LaunchedEffect }
        headProgress.snapTo(0f)
        headProgress.animateTo(1f, tween(150, easing = Easing { 1 - (1 - it) * (1 - it) })) {
            animatedHead = GeoPoint(from.latitude + (target.latitude - from.latitude) * value,
                from.longitude + (target.longitude - from.longitude) * value)
        }
    }
    LaunchedEffect(map.gestureRevision) { if (map.gestureRevision > 0) onGesture() }
    LaunchedEffect(Unit) { map.execute(MapCameraCommand.Move(GeoPoint(55.75, 37.62), 5.0, false, bearing = 0.0)) }
    var centredOnce by remember { mutableStateOf(false) }
    LaunchedEffect(fix?.timestamp, mode, recorder.isRecording, bottomInset) {
        if (fix != null && mode != TrackingCameraMode.none) {
            val zoom = if (centredOnce) map.camera?.zoom ?: 16.0 else 16.0
            map.execute(MapCameraCommand.Move(GeoPoint(fix.latitude, fix.longitude), zoom, !centredOnce && !reduceMotion,
                bearing = if (mode == TrackingCameraMode.heading) course else 0.0,
                viewportInsets = MapInsets(bottom = if (recorder.isRecording) 0.0 else bottomInset)))
            centredOnce = true
        }
    }
    if (!LocalLiveMaps.current || LocalInspectionMode.current) {
        TrackingMapPlaceholder(state.coordinates, modifier)
    } else {
        val mood = when { recorder.isPaused -> MapCarMood.paused; recorder.isRecording && recorder.gpsStale -> MapCarMood.lost; else -> MapCarMood.normal }
        var collapsed by remember { mutableStateOf(false) }
        LaunchedEffect(map.camera?.metresPerPoint) { map.camera?.let { collapsed = CarHeadingPolicy.collapsed(it.metresPerPoint, collapsed) } }
        val pulses = recorder.isRecording && mood == MapCarMood.normal && !reduceMotion
        val pulse = if (pulses) rememberInfiniteTransition(label = "car_pulse").animateFloat(0f, 1f,
            infiniteRepeatable(tween(1900, easing = CubicBezierEasing(0f, 0f, .58f, 1f))), label = "pulse").value.toDouble() else null
        val playback = fix?.let { MapPlayback(GeoPoint(it.latitude, it.longitude), course, carColor(vehicle),
            collapsed = collapsed, mood = mood,
            accuracyMetres = it.horizontalAccuracy.takeIf { accuracy -> accuracy > 0 },
            coneHalfAngle = if (mood == MapCarMood.normal) CarHeadingPolicy.coneHalfAngle(it.courseAccuracy, it.speed) else null,
            pulse = pulse) }
        val head = if (recorder.isRecording && !recorder.isPaused) state.head.dropLast(1) + listOfNotNull(animatedHead) else emptyList()
        MapHost(map, modifier.testTag("tracking_live_map"),
            fog = fog?.let { MapFog(it, FogLook(FogPalette.NIGHT), hole?.let { point -> LiveReveal(point, reveal.value.toDouble()) }) },
            routes = state.route.let { MapRoutes(listOf(it), RouteAppearance.plain.withSelectedRouteStyle().copy(showsEndpoints = false)) },
            playback = playback,
            liveHead = head,
            gesturesEnabled = !recorder.isRecording,
            chromeInsets = MapInsets(bottom = bottomInset, top = 52.0),
            cameraChromeInsets = MapInsets.Zero,
            // The bottom of the map's safe area is the HUD's dock (bottomInset), not the navigation bar's own inset.
            systemInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            styleUri = LiveMaps.style(true),
        )
    }
}

@Composable
internal fun TrackingMapPlaceholder(coordinates: List<GeoPoint>, modifier: Modifier = Modifier, background: Color = Color(0xFF262B36)) {
    Box(modifier.background(background).testTag("tracking_map_placeholder"), contentAlignment = Alignment.Center) {
        if (coordinates.size > 1) LightRoutePreview(listOf(coordinates), Modifier.fillMaxSize().padding(70.dp), accent = AppTheme.accent, background = Color.Transparent)
        else Glyph(AppGlyph.map, null, tint = Color.White.copy(alpha = .14f), size = 44.dp)
    }
}

internal fun carColor(vehicle: Vehicle?): Int = when (vehicle?.avatarEmoji?.let(VehicleAvatar::drawnColor)) {
    "red" -> 0xFFE03B2C; "blue" -> 0xFF3B7DD8; "green" -> 0xFF4CAF50
    "yellow" -> 0xFFF2C41B; "black" -> 0xFF2A2C33; "gray" -> 0xFF707580; "silver" -> 0xFFA2A8B4
    "white" -> 0xFFD4D9E3
    else -> 0xFFEB571E
}.toInt()
