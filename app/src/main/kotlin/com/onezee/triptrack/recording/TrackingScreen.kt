// iOS: TrackingView.swift, IdleHUDView.swift, GPSIndicatorView.swift, VehicleChip.swift.
package com.onezee.triptrack.recording

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.onezee.triptrack.R
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.*
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

internal val RecordingGlass = Color(0xFF28282A).copy(alpha = .78f)

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
    onHudHeightChanged: (Double) -> Unit = {},
    map: @Composable () -> Unit = { TrackingMapPlaceholder(state.coordinates, Modifier.fillMaxSize()) },
) {
    val strings = appStrings
    val recorder = state.recorder
    val density = LocalDensity.current.density
    val measureHud = Modifier.onSizeChanged { onHudHeightChanged(it.height / density.toDouble()) }
    Box(modifier.fillMaxSize().testTag("screen_record")) {
        Box(Modifier.fillMaxSize().edgeScrims(top = true, bottom = true)) { map() }
        Column(Modifier.fillMaxSize().windowInsetsPadding(contentInsets).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TrackingIcon(AppGlyph.chevronLeft, strings.back(), "tracking_back", actions.back,
                        Modifier.size(44.dp), Color.Black.copy(alpha = .4f), CircleShape)
                    if (recorder.isRecording) Box(Modifier.weight(1f)) {
                        VehicleChip(vehicle, recorder.isTransfer, compact = true,
                            modifier = Modifier.width(IntrinsicSize.Max), onClick = actions.vehicle)
                    }
                    else Spacer(Modifier.weight(1f))
                    if (!locationDenied || recorder.isRecording) GPSIndicator(recorder.gpsAccuracy, recorder.isRecording && recorder.gpsStale)
                }
                if (recorder.isRecording && recorder.isDraft) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    StatusPill(strings.draftRecordingLabel(), Modifier.testTag("recording_draft_label"))
                }
                if (recorder.isRecording && recorder.isPaused) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    StatusPill(strings.recordingPausedPill(), fill = AppTheme.accent, glyph = AppGlyph.pause)
                }
                if (recorder.isRecording && !recorder.isPaused) {
                    if (recorder.gpsStale) SignalBanner(strings.signalLostTitle(), strings.signalLostHint(), false)
                    else if (recorder.gpsAccuracy > 35) SignalBanner(strings.weakSignalTitle(), strings.weakSignalHint(), true)
                }
            }
            Spacer(Modifier.weight(1f))
            if (recorder.isRecording) {
                RecordingHUD(state, now, actions, measureHud)
            } else if (recorder.isFinishing) {
                Row(Modifier.fillMaxWidth().background(RecordingGlass, RoundedCornerShape(22.dp)).padding(24.dp)
                    .testTag("recording_finishing"), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    RecordingSpinner(Color.White, Modifier.size(24.dp))
                    Text(strings.stopConfirmAction(), Modifier.padding(start = 12.dp), style = inter(16f, InterWeight.bold), color = Color.White)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.End) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrackingIcon(if (cameraMode == TrackingCameraMode.heading) AppGlyph.chevronUp else AppGlyph.mappinAndEllipse,
                            strings.atlasMyLocation(), "tracking_locate", actions.locate, fill = RecordingGlass,
                            tint = if (cameraMode == TrackingCameraMode.none) Color.White else AppTheme.accent)
                        TrackingIcon(AppGlyph.plus, strings.fullscreenMapZoomIn(), "tracking_zoom_in", { actions.zoom(true) })
                        TrackingIcon(AppGlyph.minus, strings.fullscreenMapZoomOut(), "tracking_zoom_out", { actions.zoom(false) })
                    }
                }
                state.refusal?.let { refusal ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).background(RecordingGlass, RoundedCornerShape(18.dp))
                        .border(1.dp, Color(0xFFFF9F0A).copy(alpha = .35f), RoundedCornerShape(18.dp)).padding(14.dp).testTag("start_refused_toast"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(refusal.text(strings), style = inter(13f, InterWeight.semibold), color = Color.White)
                        if (refusal == StartRefusal.noFix) TrackingButton(strings.startAnyway(), AppGlyph.play,
                            actions.startAnyway, "start_anyway", fill = AppTheme.accent.copy(alpha = .16f), ink = AppTheme.accent, height = 40.dp)
                    }
                }
                discarded?.let { StatusPill(it, Modifier.padding(bottom = 10.dp).testTag("junk_discarded_toast"), glyph = AppGlyph.trash) }
                IdleHUD(state, vehicle, locationDenied, healthHint, actions, measureHud)
            }
        }
    }
}

@Composable
private fun IdleHUD(state: TrackingState, vehicle: Vehicle?, denied: Boolean, healthHint: String?, actions: TrackingActions, modifier: Modifier) {
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    Column(modifier.fillMaxWidth().background(RecordingGlass, RoundedCornerShape(22.dp)).testTag("idle_hud"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = 22.dp, bottom = 14.dp).size(100.dp), contentAlignment = Alignment.Center) {
            val ring = if (LocalReduceMotion.current) 0f else rememberInfiniteTransition(label = "idle_ring")
                .animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = EaseInOut)), label = "ring").value
            Box(Modifier.size(100.dp).graphicsLayer { scaleX = 1 + ring * .1f; scaleY = scaleX; alpha = 1 - ring }
                .border(2.dp, AppTheme.accent.copy(alpha = .2f), CircleShape))
            Box(Modifier.size(82.dp).background(AppTheme.accent.copy(alpha = .2f), CircleShape)
                .border(2.5.dp, AppTheme.accent.copy(alpha = .7f), CircleShape))
            Image(painterResource(R.drawable.pixel_car), null, Modifier.size(52.dp, 32.dp))
        }
        Text(if (denied) strings.noGeoTitle() else strings.readyToRide(), Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp),
            style = inter(if (denied) 19f else 22f, InterWeight.bold), color = Color.White, textAlign = TextAlign.Center)
        if (denied) Text(strings.noGeoSubtitle(), Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp),
            style = inter(13f), color = Color.White.copy(alpha = .52f), textAlign = TextAlign.Center)
        else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically) {
                VehicleChip(vehicle, false, dimmed = state.pendingTransfer, modifier = Modifier.weight(1f, fill = false).width(IntrinsicSize.Max), onClick = actions.vehicle)
                val interaction = remember { MutableInteractionSource() }
                Text(strings.passengerToggle(), Modifier.plainPressFeedback(interaction)
                    .background(if (state.pendingTransfer) AppTheme.accent else Color.White.copy(alpha = .12f), CircleShape)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { actions.transfer(!state.pendingTransfer) }
                    .semantics { selected = state.pendingTransfer }.padding(horizontal = 12.dp, vertical = 8.dp).testTag("idle_passenger_toggle"),
                    style = inter(13f, InterWeight.semibold), color = Color.White.copy(alpha = if (state.pendingTransfer) 1f else .7f))
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
                style = inter(92f, InterWeight.heavy).copy(letterSpacing = (-3.68).sp),
                color = if (recorder.isPaused || recorder.gpsStale) Color.White.copy(alpha = .28f) else AppTheme.accent)
            Text(speed.unit.uppercase(strings.language.locale), style = inter(13f, InterWeight.medium).copy(letterSpacing = .78.sp), color = Color.White.copy(alpha = .55f))
        }
        Row(Modifier.fillMaxWidth().background(RecordingGlass, RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(22.dp)).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            RecordingStat(distance.value, distance.unit, AppGlyph.roadLanes, strings.live_distanceCaption(), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = .08f)))
            RecordingStat(formattedDuration(recorder.elapsedSeconds(now)), null, AppGlyph.clock, strings.live_timeCaption(), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = .08f)))
            RecordingStat(altitude.value, altitude.unit, AppGlyph.mountain, strings.elevation(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(AppTheme.red)
                .clickable(enabled = !state.busy, role = Role.Button, onClick = actions.finish)
                .semantics { contentDescription = strings.stop() }.testTag("tracking_stop"), contentAlignment = Alignment.Center) {
                Box(Modifier.size(18.dp).background(Color.White, RoundedCornerShape(2.dp)))
            }
            TrackingButton(if (recorder.isPaused) strings.resumeAction() else strings.pauseAction(),
                if (recorder.isPaused) AppGlyph.play else AppGlyph.pause, actions.pause, "tracking_pause", Modifier.weight(1f),
                fill = if (recorder.isPaused) AppTheme.accent else RecordingGlass, enabled = !state.busy)
            if (recorder.checkpointCount == 0) TrackingIcon(AppGlyph.flagFill, strings.checkpointAdd(), "tracking_checkpoint", actions.checkpoint, Modifier.size(52.dp), shape = CircleShape, enabled = !state.busy)
            else Box(Modifier.size(52.dp).background(RecordingGlass, CircleShape).clickable(enabled = !state.busy, role = Role.Button, onClick = actions.checkpoint)
                .semantics { contentDescription = strings.checkpointAdd() }.testTag("tracking_checkpoint"), contentAlignment = Alignment.Center) {
                Text(recorder.checkpointCount.toString(), style = inter(19f, InterWeight.bold, true), color = Color.White)
            }
        }
    }
}

@Composable
private fun RecordingStat(value: String, unit: String?, glyph: AppGlyph, caption: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$caption: $value ${unit.orEmpty()}" },
        verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Glyph(glyph, null, tint = Color.White.copy(alpha = .4f), size = 13.dp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = inter(18f, InterWeight.heavy, true), color = Color.White, maxLines = 1)
            unit?.let { Text(it.uppercase(appStrings.language.locale), Modifier.padding(start = 2.dp, bottom = 2.dp), style = inter(11f), color = Color.White.copy(alpha = .5f)) }
        }
    }
}

@Composable
internal fun VehicleChip(vehicle: Vehicle?, transfer: Boolean, modifier: Modifier = Modifier, compact: Boolean = false, dimmed: Boolean = false, onClick: () -> Unit) {
    val strings = appStrings
    val name = if (transfer) strings.tripTransferTitle() else vehicle?.name?.let { if (compact && it.length > 10) it.substringAfterLast(' ') else it } ?: strings.noVehicleOption()
    val interaction = remember { MutableInteractionSource() }
    Row(modifier.alpha(if (dimmed) .4f else 1f).plainPressFeedback(interaction)
        .background(if (compact) RecordingGlass else Color.White.copy(alpha = .12f), CircleShape)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 7.dp).testTag("vehicle_chip"),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (vehicle != null && !transfer) Glyph(AppGlyph.car, null, tint = Color.White.copy(alpha = .9f), size = 16.dp)
        else Glyph(if (transfer) AppGlyph.car else AppGlyph.minusCircle, null, tint = Color.White.copy(alpha = .75f), size = 12.dp)
        Text(name, Modifier.weight(1f, fill = false), style = inter(13f, InterWeight.semibold), color = Color.White.copy(alpha = .9f), maxLines = 1)
        Glyph(AppGlyph.chevronDown, null, tint = Color.White.copy(alpha = .75f), size = 10.dp)
    }
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
    val y = with(LocalDensity.current) { 44.dp.roundToPx() }
    LaunchedEffect(legend) { if (legend) { delay(5000); legend = false } }
    Box {
        Row(Modifier.background(RecordingGlass, CircleShape).clickable(role = Role.Button) { legend = !legend }
            .padding(horizontal = 12.dp, vertical = 8.dp).testTag("gps_indicator"),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Color(signal.argb), CircleShape))
            Text(signal.label(strings), style = inter(12.5f, InterWeight.semibold), color = Color.White)
        }
        if (legend) Popup(Alignment.TopEnd, IntOffset(0, y), { legend = false }, PopupProperties(focusable = true)) {
            Column(Modifier.widthIn(max = 280.dp).background(AppTheme.colors.card, RoundedCornerShape(16.dp))
                .border(1.dp, AppTheme.colors.border, RoundedCornerShape(16.dp)).padding(14.dp).testTag("gps_legend"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.gpsAccuracyTitle(), Modifier.weight(1f), style = inter(14f, InterWeight.bold), color = AppTheme.colors.text)
                    TrackingIcon(AppGlyph.xmark, strings.close(), "gps_legend_close", { legend = false }, Modifier.size(24.dp), AppTheme.colors.cardAlt, tint = AppTheme.colors.text)
                }
                val rows = buildList {
                    if (signal == GPSSignal.searching) add(GPSSignal.searching to strings.gpsLegendSearching())
                    add(GPSSignal.accurate to strings.gpsLegendAccurate()); add(GPSSignal.medium to strings.gpsLegendMedium()); add(GPSSignal.weak to strings.gpsLegendWeak())
                }
                rows.forEach { (grade, detail) -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Color(grade.argb), CircleShape))
                    Text(grade.label(strings), Modifier.weight(1f), style = inter(12.5f, InterWeight.semibold), color = AppTheme.colors.text)
                    Text(detail, style = inter(12f), color = AppTheme.colors.textSecondary)
                } }
            }
        }
    }
}

@Composable
private fun SignalBanner(title: String, hint: String, weak: Boolean) {
    Row(Modifier.fillMaxWidth().background(if (weak) Color(0xF0291412) else Color(0xFF2A1F12), RoundedCornerShape(14.dp))
        .border(1.dp, (if (weak) AppTheme.red else Color(0xFFF5A623)).copy(alpha = .4f), RoundedCornerShape(14.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(AppGlyph.exclamationmarkTriangleFill, null, tint = if (weak) AppTheme.red else Color(0xFFF5A623), size = 20.dp)
        Column {
            Text(title, style = inter(13f, InterWeight.semibold), color = Color.White)
            Text(hint, style = inter(11.5f), color = if (weak) Color(0xFFB8B8C2) else Color(0xFFC9A878))
        }
    }
}

@Composable
internal fun StatusPill(label: String, modifier: Modifier = Modifier, fill: Color = RecordingGlass, glyph: AppGlyph? = null, ink: Color = Color.White) {
    Row(modifier.background(fill, CircleShape).padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        glyph?.let { Glyph(it, null, tint = ink, size = 12.dp) }
        Text(label, style = inter(13f, InterWeight.semibold), color = ink)
    }
}

@Composable
internal fun TrackingIcon(glyph: AppGlyph, label: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    fill: Color = RecordingGlass, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp), tint: Color = Color.White, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Box(modifier.size(44.dp).plainPressFeedback(interaction).background(fill, shape)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) { haptics.tap(); onClick() }
        .semantics { contentDescription = label }.testTag(tag), contentAlignment = Alignment.Center) {
        Glyph(glyph, null, tint = tint.copy(alpha = if (enabled) tint.alpha else .3f), size = 20.dp)
    }
}

@Composable
internal fun TrackingButton(label: String, glyph: AppGlyph?, onClick: () -> Unit, tag: String, modifier: Modifier = Modifier,
    fill: Color = AppTheme.accent, ink: Color = Color.White, height: androidx.compose.ui.unit.Dp = 52.dp, enabled: Boolean = true) {
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

@Composable
internal fun RecordingSpinner(color: Color, modifier: Modifier = Modifier) {
    val rotation = if (LocalReduceMotion.current) 0f else rememberInfiniteTransition(label = "recording_wait")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "rotation").value
    Canvas(modifier.progressSemantics()) { drawArc(color, rotation, 270f, false, style = Stroke(width = 2.dp.toPx())) }
}

internal fun StartRefusal.text(s: AppStrings): String = when (this) {
    StartRefusal.noFix -> s.startRefusedNoFix()
    StartRefusal.locationDenied -> s.startRefusedNoGeo()
    StartRefusal.recoveryPending -> s.startRefusedRecovery()
    StartRefusal.unknown, StartRefusal.alreadyRecording -> s.startRefusedUnknown()
}
