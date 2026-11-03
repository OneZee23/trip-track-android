// iOS: TrackingView.swift, IdleHUDView.swift, GPSIndicatorView.swift, VehicleChip.swift.
package com.onezee.triptrack.recording

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.onezee.triptrack.R
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.navigation.tabBarTappableInset
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.atlas.ui.WorldGlyph
import com.onezee.triptrack.feature.atlas.ui.WorldGlyphIcon
import com.onezee.triptrack.feature.garage.VehicleAvatar
import com.onezee.triptrack.feature.me.MeArtwork
import com.onezee.triptrack.location.recording.StartRefusal
import com.onezee.triptrack.location.service.formattedDuration
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.Vehicle
import kotlinx.coroutines.delay
import java.time.Instant

// iOS: TrackingView.swift. The screen renders the same states in production and the Compose tests.
data class TrackingActions(
    val back: () -> Unit = {},
    val start: () -> Unit = {},
    val blocked: () -> Unit = {},
    val startAnyway: () -> Unit = {},
    val pause: () -> Unit = {},
    val checkpoint: () -> Unit = {},
    val finish: () -> Unit = {},
    val vehicle: () -> Unit = {},
    val transfer: (Boolean) -> Unit = {},
    val health: () -> Unit = {},
    val locate: () -> Unit = {},
    val zoom: (Boolean) -> Unit = {},
)

/**
 * The glass of the record screen: Color(40, 40, 42) at the alpha the piece asks for. iOS uses .72 for
 * the idle card, the stats panel and the GPS pill, .78 for the vehicle chip, the pause button and
 * the checkpoint circle, .92 for the toasts.
 */
internal val RecordingTint = Color(40, 40, 42)
internal fun recordingGlass(alpha: Float): Color = RecordingTint.copy(alpha = alpha)

/** The record screen is dark whatever the app's theme is (iOS: ContentView sets .dark on it). */
private val Dark = AppColors.dark

/** iOS `.stroke(lineWidth:)`: centred on the edge of the shape, half of it outside, unlike `.strokeBorder`. */
private fun Modifier.centredStroke(color: Color, radius: Dp, width: Dp = 1.dp): Modifier = drawBehind {
    drawRoundRect(color, Offset.Zero, size, CornerRadius(radius.toPx()), style = Stroke(width.toPx()))
}

/**
 * iOS TapOnlyButtonStyle: the label dims to 0.72 under the finger, and nothing else answers it. The
 * movement rule of that style (a flick that starts on the control is not a tap) is the platform's
 * own touch slop here.
 */
private fun Modifier.tapOnlyFeedback(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) .72f else 1f, tween(120, easing = EaseOut), label = "tapOnly")
    graphicsLayer { this.alpha = alpha }
}

@Composable
fun TrackingScreen(
    state: TrackingState,
    vehicle: Vehicle?,
    locationDenied: Boolean,
    actions: TrackingActions,
    now: Instant,
    modifier: Modifier = Modifier,
    healthHint: String? = null,
    cameraMode: TrackingCameraMode = TrackingCameraMode.follow,
    discarded: String? = null,
    contentInsets: WindowInsets = WindowInsets.safeDrawing,
    /**
     * What the control blocks stand above: iOS docks them 24 above the PHYSICAL bottom edge, a
     * full inset left a band of map under the panel. A tappable navigation bar (three buttons)
     * takes touches, so there the blocks stand above it; with gesture navigation they sit where
     * iOS puts them, like the tab bar.
     */
    dockInset: Dp = tabBarTappableInset(),
    /** iOS VehicleChip.canSwitch: the garage has vehicles, so the chip offers a choice. */
    hasVehicles: Boolean = vehicle != null,
    canZoomIn: Boolean = true,
    canZoomOut: Boolean = true,
    onHudHeightChanged: (Double) -> Unit = {},
    map: @Composable () -> Unit = { TrackingMapPlaceholder(state.coordinates, Modifier.fillMaxSize()) },
) {
    val strings = appStrings
    val recorder = state.recorder
    val density = LocalDensity.current.density
    val measureHud = Modifier.onSizeChanged { onHudHeightChanged(it.height / density.toDouble()) }
    Box(modifier.fillMaxSize().testTag("screen_record")) {
        // The haze is on the TOP only: the controls at the bottom stand on the map itself.
        Box(Modifier.fillMaxSize().edgeScrims(top = true)) { map() }
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + dockInset),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BackButton(actions.back)
                    // The vehicle chip and the draft label share the slot between the arrow and the GPS pill; the chip is the
                    // one that gives way to a long name.
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (recorder.isRecording) {
                            VehicleChip(vehicle, recorder.isTransfer, hasVehicles, compact = true,
                                modifier = Modifier.weight(1f, fill = false), onClick = actions.vehicle)
                            if (recorder.isDraft) DraftPill(Modifier.testTag("recording_draft_label"))
                        }
                    }
                    if (!locationDenied || recorder.isRecording) GPSIndicator(recorder.gpsAccuracy, recorder.isRecording && recorder.gpsStale)
                }
                if (recorder.isRecording && recorder.isPaused) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PausedPill() }
                if (recorder.isRecording && !recorder.isPaused) {
                    if (recorder.gpsStale) SignalLostBanner(strings.signalLostTitle(), strings.signalLostHint())
                    else if (recorder.gpsAccuracy > 35) WeakSignalToast(strings.weakSignalTitle(), strings.weakSignalHint())
                }
            }
            Spacer(Modifier.weight(1f))
            if (recorder.isRecording) {
                RecordingHUD(state, now, actions, measureHud)
            } else {
                // After a finish confirmation the HUD is the idle one at once, and the summary sheet opens over it.
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.End) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LocateButton(cameraMode, strings.atlasMyLocation(), actions.locate)
                        MapButton(strings.fullscreenMapZoomIn(), "tracking_zoom_in", { actions.zoom(true) }, enabled = canZoomIn) { tint ->
                            Glyph(AppGlyph.plus, null, tint = tint, size = 20.dp)
                        }
                        MapButton(strings.fullscreenMapZoomOut(), "tracking_zoom_out", { actions.zoom(false) }, enabled = canZoomOut) { tint ->
                            Glyph(AppGlyph.minus, null, tint = tint, size = 20.dp)
                        }
                    }
                }
                state.refusal?.let { refusal ->
                    RefusalToast(refusal, strings, actions.startAnyway, Modifier.padding(bottom = 10.dp))
                }
                discarded?.let { DiscardToast(it, Modifier.padding(bottom = 10.dp)) }
                IdleHUD(state, vehicle, hasVehicles, locationDenied, healthHint, actions, measureHud)
            }
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    val strings = appStrings
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    // chevron.left, 18 semibold: 9.25 × 15.1 of ink in a 44 disc of black at .4.
    Box(Modifier.size(44.dp).plainPressFeedback(interaction).background(Color.Black.copy(alpha = .4f), CircleShape)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); onClick() }
        .semantics { contentDescription = strings.back() }.testTag("tracking_back"), contentAlignment = Alignment.Center) {
        Glyph(AppGlyph.chevronLeft, null, tint = Color.White, size = 21.dp)
    }
}

/**
 * iOS mapButton: 44, radius 12, white at .2 over .85 with a centred white .3 hairline, the symbol
 * 18 medium: white, or white at .3 where the zoom has reached its limit.
 */
@Composable
private fun MapButton(label: String, tag: String, onClick: () -> Unit, enabled: Boolean = true, content: @Composable (Color) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.size(44.dp).plainPressFeedback(interaction).background(Color(0.2f, 0.2f, 0.2f, .85f), shape).centredStroke(Color.White.copy(alpha = .3f), 12.dp)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) { haptics.tap(); onClick() }
        .semantics { contentDescription = label }.testTag(tag), contentAlignment = Alignment.Center) {
        content(Color.White.copy(alpha = if (enabled) 1f else .3f))
    }
}

/** iOS trackingIcon: `location` while the camera is free, `location.fill` following, `location.north.line.fill` with the heading. */
@Composable
private fun LocateButton(mode: TrackingCameraMode, label: String, onClick: () -> Unit) {
    MapButton(label, "tracking_locate", onClick) {
        when (mode) {
            TrackingCameraMode.none -> RecordingGlyphIcon(RecordingGlyph.location, RecordingInk.locationMedium18, Dark.text)
            TrackingCameraMode.follow -> RecordingGlyphIcon(RecordingGlyph.locationFill, RecordingInk.locationFillMedium18, AppTheme.accent)
            TrackingCameraMode.heading -> RecordingGlyphIcon(RecordingGlyph.locationNorthLineFill, RecordingInk.locationNorthLineFillMedium18, AppTheme.accent)
        }
    }
}

@Composable
private fun IdleHUD(state: TrackingState, vehicle: Vehicle?, hasVehicles: Boolean, denied: Boolean, healthHint: String?, actions: TrackingActions, modifier: Modifier) {
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val shape = RoundedCornerShape(22.dp)
    // iOS: .ultraThinMaterial under Color(40, 40, 42) at .72, and a shadow of black .35, radius 24, y 8. Compose cannot
    // blur what lies behind a layer on minSdk 29 (docs/divergences.md): the tint stands for the material, the shadow is
    // the iPhone's.
    Column(modifier.fillMaxWidth().iosShadow(shape, Color.Black.copy(alpha = .35f), 24.dp, y = 8.dp).background(recordingGlass(.72f), shape).testTag("idle_hud"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = 22.dp, bottom = 14.dp).size(100.dp), contentAlignment = Alignment.Center) {
            // iOS PulseRingModifier: the outer ring starts at half strength and fades out as it grows to 1.1.
            val ring = if (LocalReduceMotion.current) 0f else rememberInfiniteTransition(label = "idle_ring")
                .animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = EaseInOut)), label = "ring").value
            val accent = AppTheme.accent
            Box(Modifier.size(100.dp).graphicsLayer { scaleX = 1 + ring * .1f; scaleY = scaleX; alpha = .5f * (1 - ring) }
                .drawBehind { drawCircle(accent.copy(alpha = .2f), size.minDimension / 2, style = Stroke(2.dp.toPx())) })
            Box(Modifier.size(82.dp).background(accent.copy(alpha = .2f), CircleShape)
                .drawBehind { drawCircle(accent.copy(alpha = .7f), size.minDimension / 2, style = Stroke(2.5.dp.toPx())) })
            Image(ImageBitmap.imageResource(R.drawable.pixel_car), null, Modifier.size(52.dp, 32.dp), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
        }
        Text(if (denied) strings.noGeoTitle() else strings.readyToRide(), Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp),
            style = inter(if (denied) 19f else 22f, InterWeight.bold), color = Color.White, textAlign = TextAlign.Center)
        if (denied) Text(strings.noGeoSubtitle(), Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp).testTag("idle_denied_subtitle"),
            style = inter(13f), color = Color.White.copy(alpha = .42f), textAlign = TextAlign.Center)
        else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically) {
                VehicleChip(vehicle, false, hasVehicles, dimmed = state.pendingTransfer, modifier = Modifier.weight(1f, fill = false), onClick = actions.vehicle)
                PassengerToggle(state.pendingTransfer) { actions.transfer(!state.pendingTransfer) }
            }
            if (state.totalMetres > 0 || state.tripCount > 0) Text(
                "${Measure.distanceValue(state.totalMetres, unit, strings.language.code)} ${strings.totalKm(unit)} · ${state.tripCount} ${strings.tripsGenitive(state.tripCount)}",
                Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp), style = inter(13f, monospacedDigits = true), color = Color.White.copy(alpha = .42f), textAlign = TextAlign.Center)
            else Spacer(Modifier.height(14.dp))
        }
        if (healthHint != null) Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 12.dp)
            .clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = actions.health).padding(8.dp).testTag("recording_health_hint"),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(AppGlyph.infoCircleFill, null, tint = Color(0xFFFF9F0A), size = 15.dp)
            Text(healthHint, Modifier.weight(1f), style = inter(12f), color = Color.White.copy(alpha = .7f))
            Glyph(AppGlyph.chevronRight, null, tint = Color.White.copy(alpha = .55f), size = 12.dp)
        }
        SlideToStart(blocked = !denied && state.recorder.gpsAccuracy <= 0, onStart = actions.start, onBlockedAttempt = actions.blocked,
            labelOverride = if (denied) strings.openSettings() else null,
            modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 22.dp))
    }
}

/**
 * iOS passengerToggle: the person, «Я пассажир», in a capsule of white at .12 (accent when on), padding 12 / 7 and
 * a hairline of white at .08 while it is off. It stays one line: the chip beside it is what gives way.
 */
@Composable
private fun PassengerToggle(on: Boolean, onToggle: () -> Unit) {
    val strings = appStrings
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val ink = Color.White.copy(alpha = if (on) 1f else .7f)
    Row(Modifier.testTag("idle_passenger_toggle").plainPressFeedback(interaction)
        .background(if (on) AppTheme.accent else Color.White.copy(alpha = .12f), CircleShape)
        .border(1.dp, Color.White.copy(alpha = if (on) 0f else .08f), CircleShape)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.selection(); onToggle() }
        .semantics { selected = on }.padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        RecordingGlyphIcon(RecordingGlyph.personFill, RecordingInk.personFillSemibold12, ink, Modifier.testTag("idle_passenger_person"))
        Text(strings.passengerToggle(), style = inter(13f, InterWeight.semibold), color = ink, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun RecordingHUD(state: TrackingState, now: Instant, actions: TrackingActions, modifier: Modifier) {
    val recorder = state.recorder
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val lang = strings.language.code
    val speed = Measure.speedParts(recorder.speed, unit, lang, strings)
    val distance = Measure.distanceParts(recorder.distance, unit, lang, strings, Measure.Style.tenths)
    val altitude = Measure.elevationParts(recorder.location?.altitude ?: 0.0, unit, lang, strings)
    Column(modifier.fillMaxWidth().testTag("recording_hud"), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(when { recorder.isPaused -> "0"; recorder.gpsStale -> "–"; else -> speed.value }, Modifier.testTag("tracking_speed"),
                style = inter(92f, InterWeight.heavy), letterSpacing = points(-3.68f),
                color = if (recorder.isPaused || recorder.gpsStale) Color.White.copy(alpha = .28f) else AppTheme.accent)
            Text(speed.unit.uppercase(strings.language.locale), style = inter(13f, InterWeight.medium), letterSpacing = points(.78f), color = Color.White.copy(alpha = .55f))
        }
        val panel = RoundedCornerShape(22.dp)
        Row(Modifier.testTag("recording_stats_panel").fillMaxWidth().background(recordingGlass(.72f), panel).border(1.dp, Color.White.copy(alpha = .08f), panel).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            RecordingStat(distance.value, distance.unit, strings.live_distanceCaption(), Modifier.weight(1f)) {
                RecordingGlyphIcon(RecordingGlyph.route, RecordingInk.routeRegular13, Color.White.copy(alpha = .4f), Modifier.testTag("recording_stat_distance_icon"))
            }
            Box(Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = .08f)))
            RecordingStat(formattedDuration(recorder.elapsedSeconds(now)), null, strings.live_timeCaption(), Modifier.weight(1f)) {
                Glyph(AppGlyph.clock, null, tint = Color.White.copy(alpha = .4f), size = 15.4.dp, modifier = Modifier.testTag("recording_stat_time_icon"))
            }
            Box(Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = .08f)))
            RecordingStat(altitude.value, altitude.unit, strings.elevation(), Modifier.weight(1f)) {
                RecordingGlyphIcon(RecordingGlyph.mountainTwo, RecordingInk.mountainTwoRegular13, Color.White.copy(alpha = .4f), Modifier.testTag("recording_stat_altitude_icon"))
            }
        }
        ControlsRow(state, actions)
    }
}

/**
 * iOS controlsRow: stop small on the left, pause or resume wide in the middle, the checkpoint round on
 * the right. All three are tap-only (TapOnlyButtonStyle) and cast black at .25, radius 6, y 2.
 */
@Composable
private fun ControlsRow(state: TrackingState, actions: TrackingActions) {
    val recorder = state.recorder
    val strings = appStrings
    val haptics = rememberHaptics()
    val enabled = !state.busy
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val stopInteraction = remember { MutableInteractionSource() }
        Box(Modifier.size(52.dp).iosShadow(CircleShape, Color.Black.copy(alpha = .25f), 6.dp, y = 2.dp).tapOnlyFeedback(stopInteraction)
            .background(AppTheme.red, CircleShape)
            .clickable(interactionSource = stopInteraction, indication = null, enabled = enabled, role = Role.Button) { haptics.tap(); actions.finish() }
            .semantics { contentDescription = strings.stop() }.testTag("tracking_stop"), contentAlignment = Alignment.Center) {
            // stop.fill, 19 bold: a square of 15.7.
            Box(Modifier.size(15.7.dp).background(Color.White, RoundedCornerShape(3.4.dp)).testTag("tracking_stop_glyph"))
        }
        val pauseInteraction = remember { MutableInteractionSource() }
        val pauseShape = RoundedCornerShape(14.dp)
        Row(Modifier.weight(1f).height(52.dp).iosShadow(pauseShape, Color.Black.copy(alpha = .25f), 6.dp, y = 2.dp).tapOnlyFeedback(pauseInteraction)
            .background(if (recorder.isPaused) AppTheme.accent else recordingGlass(.78f), pauseShape).border(1.dp, Color.White.copy(alpha = .12f), pauseShape)
            .clickable(interactionSource = pauseInteraction, indication = null, enabled = enabled, role = Role.Button) { haptics.tap(); actions.pause() }
            .testTag("tracking_pause"), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            // pause.fill / play.fill, 19 bold.
            if (recorder.isPaused) RecordingShape(RecordingMark.play, 16.12f, 17.12f, Color.White, Modifier.testTag("tracking_pause_glyph"))
            else RecordingShape(RecordingMark.pause, 12.88f, 16.75f, Color.White, Modifier.testTag("tracking_pause_glyph"))
            Text(if (recorder.isPaused) strings.resumeAction() else strings.pauseAction(), style = interScaled(16f, InterWeight.bold), color = Color.White, maxLines = 1)
        }
        val flagInteraction = remember { MutableInteractionSource() }
        Box(Modifier.size(52.dp).iosShadow(CircleShape, Color.Black.copy(alpha = .25f), 6.dp, y = 2.dp).tapOnlyFeedback(flagInteraction)
            .background(recordingGlass(.78f), CircleShape).border(1.dp, Color.White.copy(alpha = .12f), CircleShape)
            .clickable(interactionSource = flagInteraction, indication = null, enabled = enabled, role = Role.Button) { haptics.tap(); actions.checkpoint() }
            .semantics { contentDescription = strings.checkpointAdd() }.testTag("tracking_checkpoint"), contentAlignment = Alignment.Center) {
            // The number takes the flag's place once there are checkpoints: the counter is the only answer the press gets.
            if (recorder.checkpointCount > 0) Text(recorder.checkpointCount.toString(), style = inter(19f, InterWeight.bold, true), color = Color.White)
            else Glyph(AppGlyph.flagFill, null, tint = Color.White, size = 20.4.dp, modifier = Modifier.testTag("tracking_checkpoint_glyph"))
        }
    }
}

@Composable
private fun RecordingStat(value: String, unit: String?, caption: String, modifier: Modifier, icon: @Composable () -> Unit) {
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$caption: $value ${unit.orEmpty()}" },
        verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(15.dp), contentAlignment = Alignment.Center) { icon() }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = inter(18f, InterWeight.heavy, true), color = Color.White, maxLines = 1)
            unit?.let { Text(it.uppercase(appStrings.language.locale), Modifier.padding(start = 2.dp, bottom = 2.dp), style = inter(11f), color = Color.White.copy(alpha = .5f)) }
        }
    }
}

/**
 * iOS VehicleChip: the vehicle's own pixel avatar at 16 × 16 (its emoji when it has no sprite), the
 * name, and the up-and-down chevron when the garage has anything to choose. Riding as a passenger
 * shows the person and the word, no vehicle is the minus circle. On the idle card it is white at
 * .12 with a .08 hairline; in the top bar over the map, dark glass with a .14 one.
 */
@Composable
internal fun VehicleChip(vehicle: Vehicle?, transfer: Boolean, hasVehicles: Boolean, modifier: Modifier = Modifier, compact: Boolean = false, dimmed: Boolean = false, onClick: () -> Unit) {
    val strings = appStrings
    val name = if (transfer) strings.tripTransferTitle() else vehicle?.name?.let { if (compact && it.length > 10) it.substringAfterLast(' ') else it } ?: strings.noVehicleOption()
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val ink = Color.White.copy(alpha = .9f)
    Row(modifier.testTag("vehicle_chip").alpha(if (dimmed) .4f else 1f).plainPressFeedback(interaction)
        .background(if (compact) recordingGlass(.78f) else Color.White.copy(alpha = .12f), CircleShape)
        .border(1.dp, Color.White.copy(alpha = if (compact) .14f else .08f), CircleShape)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); onClick() }
        .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            transfer -> RecordingGlyphIcon(RecordingGlyph.personFill, RecordingInk.personFillSemibold12, ink, Modifier.testTag("vehicle_chip_person"))
            vehicle != null -> VehicleAvatarMark(vehicle)
            else -> Glyph(AppGlyph.minusCircle, null, tint = Color.White.copy(alpha = .75f), size = 14.4.dp)
        }
        Text(name, Modifier.weight(1f, fill = false), style = inter(13f, InterWeight.semibold), color = ink, maxLines = 1)
        if (hasVehicles) RecordingGlyphIcon(RecordingGlyph.chevronUpChevronDown, RecordingInk.chevronUpChevronDownBold10, Color.White.copy(alpha = .75f), Modifier.testTag("vehicle_chip_chevron"))
    }
}

/** The pixel avatar in a 16 × 16 frame, nearest neighbour as everywhere the cars are drawn; the emoji of a vehicle without a sprite. */
@Composable
private fun VehicleAvatarMark(vehicle: Vehicle) {
    val sprite = VehicleAvatar.asset(vehicle)?.let { MeArtwork.drawables[it] }
    if (sprite != null) Image(ImageBitmap.imageResource(sprite), null, Modifier.size(16.dp).testTag("vehicle_chip_avatar"), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
    else Text(vehicle.avatarEmoji, Modifier.testTag("vehicle_chip_emoji"), style = inter(14f))
}

// iOS: GPSIndicatorView.Signal. A stale fix wins even when accuracy is zero.
enum class GPSSignal(val argb: Long) {
    searching(0xFF8E8E93), accurate(0xFF30D158), medium(0xFFFFD60A), weak(0xFFFF453A), lost(0xFFF5A623);
    fun label(s: AppStrings): String = when (this) { searching -> s.gpsSearching(); accurate -> s.gpsAccurate(); medium -> s.gpsMedium(); weak -> s.gpsWeak(); lost -> s.gpsLost() }
    companion object { fun of(accuracy: Double, stale: Boolean): GPSSignal = when { stale -> lost; accuracy <= 0 -> searching; accuracy <= 10 -> accurate; accuracy <= 35 -> medium; else -> weak } }
}

@Composable
internal fun GPSIndicator(accuracy: Double, stale: Boolean) {
    val strings = appStrings
    val signal = GPSSignal.of(accuracy, stale)
    var legend by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    LaunchedEffect(legend) { if (legend) { delay(5000); legend = false } }
    Box {
        Row(Modifier.testTag("gps_indicator").background(recordingGlass(.72f), CircleShape).border(1.dp, Color.White.copy(alpha = .08f), CircleShape)
            .clickable(interactionSource = null, indication = null, role = Role.Button) { haptics.tap(); legend = !legend }
            .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Color(signal.argb), CircleShape))
            Text(signal.label(strings), style = inter(12.5f, InterWeight.semibold), color = Color.White)
        }
        // The legend stands 44 under the pill, right edges together; the transparent margin is room for its shadow, which a
        // popup window would otherwise cut.
        if (legend) Popup(Alignment.TopEnd, with(density) { IntOffset(LEGEND_MARGIN.roundToPx(), (44.dp - LEGEND_MARGIN).roundToPx()) }, { legend = false }, PopupProperties(focusable = true)) {
            Box(Modifier.padding(LEGEND_MARGIN)) { GPSLegendCard(signal, { legend = false }) }
        }
    }
}

/** iOS GPSIndicatorView.tooltipCard: 280 wide, in the dark palette whatever the app's theme (the record screen is forced dark). */
@Composable
internal fun GPSLegendCard(signal: GPSSignal, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val card = RoundedCornerShape(16.dp)
    Column(modifier.testTag("gps_legend").width(280.dp).iosShadow(card, Color.Black.copy(alpha = .3f), 16.dp, y = 6.dp)
        .background(Dark.card, card).centredStroke(Dark.border, 16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecordingGlyphIcon(RecordingGlyph.antenna, RecordingInk.antennaRegular14, Color(signal.argb), Modifier.testTag("gps_legend_icon"))
            Text(strings.gpsAccuracyTitle(), Modifier.weight(1f), style = inter(14f, InterWeight.bold), color = Dark.text)
            val closeInteraction = remember { MutableInteractionSource() }
            Box(Modifier.size(24.dp).plainPressFeedback(closeInteraction).background(Dark.cardAlt, CircleShape)
                .clickable(interactionSource = closeInteraction, indication = null, role = Role.Button) { onClose() }
                .semantics { contentDescription = strings.close() }.testTag("gps_legend_close"), contentAlignment = Alignment.Center) {
                Glyph(AppGlyph.xmark, null, tint = Dark.textTertiary, size = 12.dp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (signal == GPSSignal.searching) LegendRow(GPSSignal.searching, strings.gpsLegendSearching(), strings)
            LegendRow(GPSSignal.accurate, strings.gpsLegendAccurate(), strings)
            LegendRow(GPSSignal.medium, strings.gpsLegendMedium(), strings)
            LegendRow(GPSSignal.weak, strings.gpsLegendWeak(), strings)
        }
    }
}

private val LEGEND_MARGIN = 24.dp

@Composable
private fun LegendRow(grade: GPSSignal, detail: String, strings: AppStrings) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Color(grade.argb), CircleShape))
        Text(grade.label(strings), style = inter(12.5f, InterWeight.semibold), color = Dark.text)
        Spacer(Modifier.weight(1f).widthIn(min = 6.dp))
        Text(detail, style = inter(12f, InterWeight.medium), color = Dark.textSecondary)
    }
}

/** iOS weakSignalToast: the red disc with the «!», the two lines, one hairline of #FF453A at .35. */
@Composable
private fun WeakSignalToast(title: String, hint: String) {
    val shape = RoundedCornerShape(14.dp)
    val red = Color(0xFFFF453A)
    Row(Modifier.fillMaxWidth().background(Color(41, 20, 18, 240), shape).testTag("signal_weak_toast").border(1.dp, red.copy(alpha = .35f), shape)
        .padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).background(red, CircleShape).testTag("signal_weak_disc"), contentAlignment = Alignment.Center) {
            Text("!", style = inter(14f, InterWeight.bold), color = Color.White)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = inter(13f, InterWeight.semibold), color = Color.White)
            Text(hint, style = inter(11.5f), color = Color(0xFFB8B8C2))
        }
    }
}

/** iOS signalLostBanner: the slashed antenna in amber, the two lines on #2A1F12. */
@Composable
private fun SignalLostBanner(title: String, hint: String) {
    val shape = RoundedCornerShape(14.dp)
    val amber = Color(0xFFF5A623)
    Row(Modifier.testTag("signal_lost_banner").fillMaxWidth().background(Color(0xFF2A1F12), shape).border(1.dp, amber.copy(alpha = .4f), shape).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        RecordingGlyphIcon(RecordingGlyph.antennaSlash, RecordingInk.antennaSlashSemibold15, amber, Modifier.testTag("signal_lost_icon"))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = inter(13f, InterWeight.semibold), color = Color.White)
            Text(hint, style = inter(11.5f), color = Color(0xFFC9A878))
        }
    }
}

/**
 * iOS pausedPill: solid accent, Inter 14 bold, padding 16 / 9 and a soft shadow. It is paused in one
 * colour, here and on the resume button.
 */
@Composable
private fun PausedPill() {
    val strings = appStrings
    Row(Modifier.testTag("recording_paused_pill").iosShadow(CircleShape, Color.Black.copy(alpha = .28f), 10.dp, y = 3.dp).background(AppTheme.accent, CircleShape).padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        // pause.fill, 12 bold.
        RecordingShape(RecordingMark.pause, 8.12f, 10.62f, Color.White, Modifier.testTag("recording_paused_glyph"))
        Text(strings.recordingPausedPill(), style = inter(14f, InterWeight.bold), color = Color.White)
    }
}

/**
 * iOS: Text(draftRecordingLabel) in `.glassPill()`: Inter 12 semibold in the secondary ink on the card colour. The text pads
 * 10 / 6 and the pill 14 / 6 more, so 24 on each side across and 12 on each side down: 38.5 tall, the audit's «about 26»
 * counts the 12 once.
 */
@Composable
private fun DraftPill(modifier: Modifier = Modifier) {
    Text(appStrings.draftRecordingLabel(), modifier.background(Dark.card, CircleShape).padding(horizontal = 24.dp, vertical = 12.dp),
        style = inter(12f, InterWeight.semibold), color = Dark.textSecondary, maxLines = 1)
}

/**
 * iOS refusalToast: the reason on the full width with its amber symbol, and the way past it under it as a
 * capsule: «Всё равно начать» in accent bold on accent at .16. The corner is 18 with the way past, 22 without.
 */
@Composable
private fun RefusalToast(refusal: StartRefusal, strings: AppStrings, onStartAnyway: () -> Unit, modifier: Modifier = Modifier) {
    val amber = Color(0xFFFF9F0A)
    val shape = RoundedCornerShape(if (refusal == StartRefusal.noFix) 18.dp else 22.dp)
    Column(modifier.testTag("start_refused_toast").fillMaxWidth().background(recordingGlass(.92f), shape).centredStroke(amber.copy(alpha = .35f), if (refusal == StartRefusal.noFix) 18.dp else 22.dp)
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (refusal == StartRefusal.noFix) RecordingGlyphIcon(RecordingGlyph.antenna, RecordingInk.antennaSemibold14, amber, Modifier.testTag("start_refused_icon"))
            else RecordingGlyphIcon(RecordingGlyph.exclamationCircleFill, RecordingInk.exclamationCircleFillSemibold14, amber, Modifier.testTag("start_refused_icon"))
            Text(refusal.text(strings), Modifier.weight(1f), style = inter(13f, InterWeight.semibold), color = Color.White)
        }
        if (refusal == StartRefusal.noFix) {
            val interaction = remember { MutableInteractionSource() }
            val haptics = rememberHaptics()
            Text(strings.startAnyway(), Modifier.testTag("start_anyway").fillMaxWidth().plainPressFeedback(interaction).background(AppTheme.accent.copy(alpha = .16f), CircleShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); onStartAnyway() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
                style = inter(13f, InterWeight.bold), color = AppTheme.accent, textAlign = TextAlign.Center)
        }
    }
}

/** iOS junkDiscardedToast: a capsule on the full width, the trash at .7, the line, a hairline of white at .14. */
@Composable
private fun DiscardToast(text: String, modifier: Modifier = Modifier) {
    Row(modifier.testTag("junk_discarded_toast").fillMaxWidth().background(recordingGlass(.92f), CircleShape).border(1.dp, Color.White.copy(alpha = .14f), CircleShape)
        .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(AppGlyph.trash, null, tint = Color.White.copy(alpha = .7f), size = 16.dp)
        Text(text, Modifier.weight(1f), style = inter(13f, InterWeight.semibold), color = Color.White, maxLines = 2)
    }
}

@Composable
internal fun TrackingButton(label: String, glyph: AppGlyph?, onClick: () -> Unit, tag: String, modifier: Modifier = Modifier,
    fill: Color = AppTheme.accent, ink: Color = Color.White, height: Dp = 52.dp, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth().heightIn(min = height).alpha(if (enabled) 1f else .45f).plainPressFeedback(interaction)
        .background(fill, RoundedCornerShape(14.dp)).clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) { haptics.tap(); onClick() }
        .padding(horizontal = 12.dp, vertical = 12.dp).testTag(tag),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        glyph?.let { Glyph(it, null, tint = ink, size = 19.dp); Spacer(Modifier.width(8.dp)) }
        Text(label, style = interScaled(16f, InterWeight.bold), color = ink, textAlign = TextAlign.Center)
    }
}

internal fun StartRefusal.text(s: AppStrings): String = when (this) {
    StartRefusal.noFix -> s.startRefusedNoFix()
    StartRefusal.locationDenied -> s.startRefusedNoGeo()
    StartRefusal.recoveryPending -> s.startRefusedRecovery()
    StartRefusal.unknown, StartRefusal.alreadyRecording -> s.startRefusedUnknown()
}
