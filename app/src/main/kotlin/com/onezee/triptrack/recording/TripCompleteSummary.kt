// iOS: TripCompleteSummaryView.swift. The finish card always uses the light palette.
package com.onezee.triptrack.recording

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.R as Words
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.me.*
import com.onezee.triptrack.feature.settings.AvgSpeedMode
import com.onezee.triptrack.feature.settings.MeSheetHeader
import com.onezee.triptrack.feature.settings.MeToggle
import com.onezee.triptrack.feature.trip.ui.DraftTripBanner
import com.onezee.triptrack.location.world.TripRewardsResult
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.map.*
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.map.fog.FogLook
import com.onezee.triptrack.map.fog.FogPalette
import com.onezee.triptrack.map.route.MapRoutes
import com.onezee.triptrack.map.route.RouteAppearance
import com.onezee.triptrack.map.route.RouteShape
import com.onezee.triptrack.model.*
import com.onezee.triptrack.ui.LocalLiveMaps
import com.onezee.triptrack.ui.LiveMaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// iOS: TripCompleteSummaryView.compactDuration. Seconds are useful only below one minute.
fun compactSummaryDuration(seconds: Double, strings: AppStrings): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return when {
        hours > 0 && minutes == 0 -> "$hours ${strings.hoursUnitShort()}"
        hours > 0 -> "$hours ${strings.hoursUnitShort()} $minutes ${strings.minutesUnitShort()}"
        minutes > 0 -> "$minutes ${strings.minutesUnitShort()}"
        else -> "$total ${strings.secondsUnitShort()}"
    }
}

@Composable
fun TripCompleteSummary(
    trip: Trip,
    rewards: TripRewardsResult?,
    discoveries: TripDiscoveries?,
    onDone: (notes: String, publish: Boolean) -> Unit,
    onSaveNotes: (String) -> Unit,
    onPhoto: () -> Unit,
    onConfirmDraft: () -> Unit,
    onDiscardDraft: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    photoCount: Int = 0,
    averageSpeed: AvgSpeedMode = AvgSpeedMode.overall,
    fog: RevealedLayer? = null,
    failed: Boolean = false,
    photos: @Composable () -> Unit = {},
) {
    val strings = appStrings
    val windowDark = LocalWindowIsDark.current ?: AppTheme.isDark
    TripTrackTheme(false, strings.language, windowIsDark = windowDark, reduceMotion = LocalReduceMotion.current) {
        val colors = AppTheme.colors
        val unit = LocalDistanceUnit.current
        var notes by remember(trip.id) { mutableStateOf(trip.tripDescription.orEmpty()) }
        var notesDraft by remember { mutableStateOf("") }
        var editing by remember { mutableStateOf(false) }
        var publish by remember(trip.id) { mutableStateOf(false) }
        var badge by remember { mutableStateOf<AchievementRow?>(null) }
        val distance = Measure.distanceParts(trip.distance, unit, strings.language.code, strings, Measure.Style.tenths)
        val average = Measure.speedParts(if (averageSpeed == AvgSpeedMode.moving) trip.movingAverageSpeedMS else trip.averageSpeed, unit, strings.language.code, strings)
        val maximum = Measure.speedParts(trip.maxSpeed, unit, strings.language.code, strings)
        Column(modifier.fillMaxSize().background(colors.bg).testTag("trip_complete_summary")) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SummaryConfetti()
                Text(strings.tripFinishedTitle(), Modifier.padding(top = 8.dp), style = inter(22f, InterWeight.heavy), color = colors.text)
                if (trip.isDraft) DraftTripBanner(onConfirmDraft, onDiscardDraft, Modifier.padding(horizontal = 20.dp).padding(top = 14.dp))
                if (trip.trackPoints.isNotEmpty()) SummaryHero(trip, fog, discoveries?.visible()?.isEmpty == false,
                    Modifier.padding(horizontal = 20.dp).padding(top = 14.dp).height(139.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)))
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryStat(distance.value, distance.unit, strings.distance(), colors.distance, Modifier.weight(1f))
                        SummaryStat(compactSummaryDuration(trip.duration, strings), "", strings.duration(), colors.text, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryStat(average.value, average.unit, strings.avgSpeed(), AppTheme.blue, Modifier.weight(1f))
                        SummaryStat(maximum.value, maximum.unit, strings.maxShort(), AppTheme.red, Modifier.weight(1f))
                    }
                }
                discoveries?.let { TripRevealedBlock(it, Modifier.padding(horizontal = 20.dp).padding(top = 14.dp)) }
                if (rewards != null) SummaryRewards(rewards, Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) { badge = it }
                PressableCard({ notesDraft = notes; editing = true }, Modifier.padding(horizontal = 20.dp).padding(top = 14.dp).testTag("summary_description")) {
                    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph(AppGlyph.pencilLine, null, tint = AppTheme.accent, size = 22.dp)
                        Text(notes.ifEmpty { strings.describeTripPlaceholder() }, Modifier.weight(1f), style = inter(14f), color = if (notes.isEmpty()) colors.textTertiary else colors.text, maxLines = 2)
                        Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = 12.dp)
                    }
                }
                if (!trip.isDraft) Column(Modifier.padding(horizontal = 20.dp).padding(top = 10.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(4.dp)) {
                    MeToggle(strings.publishToFeed(), publish, { publish = it }, strings.publishToggleHint(), tag = "publish_toggle")
                    Text(strings.publishFootnote(), Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp), style = inter(11f), color = colors.textTertiary)
                }
                photos()
                Spacer(Modifier.height(16.dp))
            }
            if (failed) Text(strings.manualFailedDbTitle(), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = inter(13f), color = AppTheme.red)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TrackingButton(if (photoCount == 0) strings.photoShort() else "${strings.photoShort()} ($photoCount)", AppGlyph.cameraFill,
                    onPhoto, "summary_photo", Modifier.weight(1f), fill = Color.White, ink = colors.text, height = 44.dp, enabled = !busy)
                TrackingButton(strings.done(), null, { onDone(notes.trim(), publish && !trip.isDraft) }, "summary_done", Modifier.weight(1f), height = 44.dp, enabled = !busy)
            }
        }
        if (editing) ContentSizedSheet({ editing = false }, background = colors.bg, detent = SheetDetent.large) {
            val closeEditor = this::dismiss
            Column(Modifier.fillMaxSize().imePadding()) {
                MeSheetHeader(strings.describeTripPlaceholder(), closeEditor)
                BasicTextField(notesDraft, { notesDraft = it }, Modifier.fillMaxWidth().weight(1f).padding(20.dp).testTag("summary_notes_field"),
                    textStyle = interScaled(16f).copy(color = colors.text), cursorBrush = SolidColor(AppTheme.accent))
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TrackingButton(strings.cancel(), null, { editing = false }, "summary_notes_cancel", Modifier.weight(1f), fill = colors.cardAlt, ink = colors.text)
                    TrackingButton(strings.done(), null, { notes = notesDraft; onSaveNotes(notesDraft); editing = false }, "summary_notes_save", Modifier.weight(1f))
                }
            }
        }
        badge?.let { selected -> ContentSizedSheet({ badge = null }, background = colors.bg, detent = SheetDetent.large) {
            BadgeDetailScreen(selected, { badge = null }, unit)
        } }
    }
}

@Composable
private fun SummaryStat(value: String, unit: String, caption: String, ink: Color, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, Modifier.weight(1f, false), style = inter(24f, InterWeight.heavy, true), color = ink, maxLines = 1,
                autoSize = TextAutoSize.StepBased(inter(14.4f).fontSize, inter(24f).fontSize))
            if (unit.isNotEmpty()) Text(unit, Modifier.padding(start = 3.dp, bottom = 3.dp), style = inter(14f, InterWeight.medium), color = AppTheme.colors.textSecondary)
        }
        Text(caption.uppercase(appStrings.language.locale), style = inter(10f, InterWeight.bold), color = AppTheme.colors.textTertiary)
    }
}

@Composable
private fun SummaryRewards(data: TripRewardsResult, modifier: Modifier, onBadge: (AchievementRow) -> Unit) {
    val strings = appStrings
    Column(modifier.fillMaxWidth().background(Color(0xFFFFF7F0), RoundedCornerShape(16.dp))
        .border(1.dp, AppTheme.accent.copy(alpha = .2f), RoundedCornerShape(16.dp)).padding(16.dp).testTag("summary_rewards"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.tr(Words.string.android_recording_xp, data.xpEarned.toString()), Modifier.weight(1f), style = inter(28f, InterWeight.heavy), color = AppTheme.accent)
            if (data.newLevel > data.previousLevel) Text(strings.tr(Words.string.android_recording_level_up),
                Modifier.background(Color(0xFF1C1C1C), CircleShape).border(1.5.dp, AppTheme.accent, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                style = inter(9f, InterWeight.bold).copy(fontFamily = PressStart2PFamily), color = Color.White)
        }
        if (data.currentStreak > 1) Text(strings.streakDaysInARow(data.currentStreak), style = inter(13f, InterWeight.semibold), color = AppTheme.colors.text)
        data.roadCard?.takeIf { !it.isNew && it.timesDriven > 1 }?.let { Text(strings.repeatRouteTimes(it.timesDriven), style = inter(13f, InterWeight.semibold), color = AppTheme.colors.text) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            data.newBadges.take(4).forEach { rule ->
                val row = AchievementRow(rule.id, rule.category, rule.rarity, true, data.repeatedBadgeCounts[rule.id] ?: 1)
                Column(Modifier.widthIn(max = 74.dp).weight(1f, false).clickable(role = androidx.compose.ui.semantics.Role.Button) { onBadge(row) }.testTag("summary_badge_${rule.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BadgeArt(row, 52.dp)
                    Text(MeArtwork.badgeTitles[rule.id]?.let(strings::tr).orEmpty(), style = inter(9f, InterWeight.bold), color = badgeColor(rule.id), textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun SummaryHero(trip: Trip, fog: RevealedLayer?, revealed: Boolean, modifier: Modifier) {
    var route by remember(trip.id) { mutableStateOf<RouteShape?>(null) }
    val coordinates = remember(trip.id) { trip.trackPoints.map { it.coordinate } }
    val map = remember { MapHostState() }
    LaunchedEffect(trip) { route = withContext(Dispatchers.Default) { recordingRoute(trip.trackPoints) } }
    LaunchedEffect(route) { route?.bounds?.let { map.execute(MapCameraCommand.FitRoute(it, MapInsets.all(30.0), false)) } }
    Box(modifier.testTag("summary_hero_map")) {
        if (!LocalLiveMaps.current || LocalInspectionMode.current) TrackingMapPlaceholder(coordinates, Modifier.fillMaxSize())
        else MapHost(map, Modifier.fillMaxSize(), fog = fog?.let { MapFog(it, FogLook(FogPalette.NIGHT)) },
            routes = route?.let { MapRoutes(listOf(it), RouteAppearance.overFog(FogPalette.NIGHT)) },
            gesturesEnabled = false, textureMode = true, styleUri = LiveMaps.style(true))
        if (revealed) SummaryRevealVeil(coordinates, Modifier.fillMaxSize())
    }
}

@Composable
private fun SummaryRevealVeil(coordinates: List<GeoPoint>, modifier: Modifier) {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { withFrameNanos { }; progress.animateTo(1f, tween(if (reduce) 300 else 700, easing = androidx.compose.animation.core.LinearEasing)) }
    Canvas(modifier) {
        val veil = Color(0xFF262B36)
        if (reduce) drawRect(veil.copy(alpha = 1 - progress.value))
        else {
            val (start, end) = RevealSweep.axis(coordinates)
            val (clear, head) = RevealSweep.stops(progress.value.toDouble())
            drawRect(Brush.linearGradient(0f to Color.Transparent, clear to Color.Transparent, head to veil, 1f to veil,
                start = Offset(start.x * size.width, start.y * size.height), end = Offset(end.x * size.width, end.y * size.height)))
        }
    }
}

@Composable
private fun SummaryConfetti() {
    val colors = listOf(AppTheme.accent, AppTheme.yellow, AppTheme.green, AppTheme.accent, AppTheme.blue, AppTheme.red, AppTheme.teal, AppTheme.accent)
    val columns = listOf(.06, .19, .31, .44, .56, .69, .81, .93)
    val speeds = listOf(.28, .36, .24, .40, .30, .34, .26, .38)
    val phases = listOf(.10, .55, .80, .25, .65, .05, .40, .90)
    val spins = listOf(1.2, -.9, 1.5, -1.3, 1.0, -1.6, 1.1, -.8)
    val sways = listOf(.5, .7, .4, .6, .55, .45, .65, .5)
    val time = remember { mutableDoubleStateOf(0.0) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(reduce) {
        if (!reduce) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { time.doubleValue = (it - start) / 1e9 }
        }
    }
    Canvas(Modifier.fillMaxWidth().height(46.dp)) {
        val t = time.doubleValue
        colors.forEachIndexed { index, color ->
            val cycle = (t * speeds[index] + phases[index]) % 1
            val x = size.width * columns[index] + kotlin.math.sin((t * sways[index] + phases[index]) * Math.PI * 2) * 5.dp.toPx()
            val y = cycle * (size.height + 12.dp.toPx()) - 6.dp.toPx()
            val alpha = minOf(1.0, minOf(cycle, 1 - cycle) * 6).toFloat()
            withTransform({ translate(x.toFloat(), y.toFloat()); rotate((t * spins[index] * 60 + phases[index] * 360).toFloat(), Offset.Zero) }) {
                drawRoundRect(color.copy(alpha = alpha), Offset(-3.dp.toPx(), -3.dp.toPx()), androidx.compose.ui.geometry.Size(6.dp.toPx(), 6.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()))
            }
        }
    }
}
