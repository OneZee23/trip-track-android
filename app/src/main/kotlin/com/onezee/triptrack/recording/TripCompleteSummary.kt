// iOS: TripCompleteSummaryView.swift. The finish card always uses the light palette.
package com.onezee.triptrack.recording

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.R as Words
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.me.*
import com.onezee.triptrack.feature.settings.AvgSpeedMode
import com.onezee.triptrack.feature.account.HouseSwitch
import com.onezee.triptrack.feature.social.ui.PublicBadgeOverlay
import com.onezee.triptrack.feature.trip.ui.MAX_NOTES_LENGTH
import com.onezee.triptrack.feature.trip.ui.graphemeCount
import com.onezee.triptrack.feature.trip.ui.graphemePrefix
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
                DescriptionCard(notes, { notesDraft = notes; editing = true }, Modifier.padding(horizontal = 20.dp).padding(top = 14.dp))
                if (!trip.isDraft) PublishCard(publish, { publish = it }, Modifier.padding(horizontal = 20.dp).padding(top = 10.dp))
                photos()
                Spacer(Modifier.height(16.dp))
            }
            if (failed) Text(strings.manualFailedDbTitle(), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = inter(13f), color = AppTheme.red)
            BottomBar(photoCount, busy, onPhoto) { onDone(notes.trim(), publish && !trip.isDraft) }
        }
        if (editing) NotesEditorSheet(notesDraft, { notes = it; onSaveNotes(it); editing = false }) { editing = false }
        // The badge card in place over the finish sheet, with the date the drive that just ended earned it.
        badge?.let { selected -> PublicBadgeOverlay(selected, trip.endDate ?: trip.startDate) { badge = null } }
    }
}

/**
 * iOS: the summary cards: white, and a shadow of black at .03, radius 2, y 1 under the shape (the
 * stat tiles, the description, the publish row and «Открыто»). The finish card is always light.
 */
internal fun Modifier.summaryCard(radius: Dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return iosShadow(shape, Color.Black.copy(alpha = .03f), 2.dp, y = 1.dp).background(Color.White, shape)
}

@Composable
private fun SummaryStat(value: String, unit: String, caption: String, ink: Color, modifier: Modifier) {
    Column(modifier.summaryCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, Modifier.weight(1f, false), style = inter(24f, InterWeight.heavy, true), color = ink, maxLines = 1,
                autoSize = TextAutoSize.StepBased(inter(14.4f).fontSize, inter(24f).fontSize))
            if (unit.isNotEmpty()) Text(unit, Modifier.padding(start = 3.dp, bottom = 3.dp), style = inter(14f, InterWeight.medium), color = AppTheme.colors.textSecondary)
        }
        Text(caption.uppercase(appStrings.language.locale), style = inter(10f, InterWeight.bold), letterSpacing = points(.4f), color = AppTheme.colors.textTertiary)
    }
}

/**
 * iOS gamificationSection: «+150 XP» with the pixel «LEVEL UP!», the streak and the repeated road each with its
 * accent symbol, the new badges. The card glows (accent at .12, radius 16, y 4) and comes in with a fade and a
 * scale from .97, half a second, unless «Remove animations» asks otherwise.
 */
@Composable
private fun SummaryRewards(data: TripRewardsResult, modifier: Modifier, onBadge: (AchievementRow) -> Unit) {
    val strings = appStrings
    val shape = RoundedCornerShape(16.dp)
    val reduce = LocalReduceMotion.current
    var shown by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { shown = true }
    val entrance by animateFloatAsState(if (shown) 1f else 0f, if (reduce) snap() else tween(500, easing = EaseOut), label = "rewardsEntrance")
    Column(modifier.fillMaxWidth().graphicsLayer { alpha = entrance; scaleX = .97f + .03f * entrance; scaleY = scaleX }
        .iosShadow(shape, AppTheme.accent.copy(alpha = .12f), 16.dp, y = 4.dp).background(Color(0xFFFFF7F0), shape)
        .border(1.dp, AppTheme.accent.copy(alpha = .2f), shape).padding(16.dp).testTag("summary_rewards"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.tr(Words.string.android_recording_xp, data.xpEarned.toString()), Modifier.weight(1f), style = inter(28f, InterWeight.heavy), color = AppTheme.accent)
            // PressStart2P ships one weight: its style is the face's own, not Inter's bold laid over it.
            if (data.newLevel > data.previousLevel) Text(strings.tr(Words.string.android_recording_level_up),
                Modifier.background(Color(0xFF1C1C1C), CircleShape).border(1.5.dp, AppTheme.accent, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp).testTag("summary_level_up"),
                style = pressStart2P(9f), color = Color.White)
        }
        if (data.currentStreak > 1) RewardLine(strings.streakDaysInARow(data.currentStreak)) {
            RecordingGlyphIcon(RecordingGlyph.flameFill, RecordingInk.flameFillRegular14, AppTheme.accent, Modifier.testTag("summary_streak_icon"))
        }
        data.roadCard?.takeIf { !it.isNew && it.timesDriven > 1 }?.let {
            RewardLine(strings.repeatRouteTimes(it.timesDriven)) { RecordingGlyphIcon(RecordingGlyph.circlePath, RecordingInk.circlePathRegular14, AppTheme.accent, Modifier.testTag("summary_repeat_icon")) }
        }
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

/** A line of the rewards card: the accent symbol, six apart from the words (13 semibold). */
@Composable
private fun RewardLine(text: String, icon: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        icon()
        Text(text, style = inter(13f, InterWeight.semibold), color = AppTheme.colors.text)
    }
}

/**
 * iOS descriptionCard: the text-lines symbol in a 38 tile of accent at .08, the line (placeholder in
 * Inter 14 medium, a written note regular) top-aligned beside it, two lines at most, and a chevron
 * level with the tile's middle. 16 of padding, the card 70 tall.
 */
@Composable
private fun DescriptionCard(notes: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Row(modifier.testTag("summary_description").fillMaxWidth().plainPressFeedback(interaction).summaryCard(16.dp)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); onClick() }
        .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(38.dp).background(AppTheme.accent.copy(alpha = .08f), RoundedCornerShape(10.dp)).testTag("summary_description_tile"), contentAlignment = Alignment.Center) {
            RecordingGlyphIcon(RecordingGlyph.textAlignLeft, RecordingInk.textAlignLeftRegular15, AppTheme.accent)
        }
        Text(notes.ifEmpty { appStrings.describeTripPlaceholder() }, Modifier.weight(1f),
            style = inter(14f, if (notes.isEmpty()) InterWeight.medium else InterWeight.regular), color = if (notes.isEmpty()) colors.textTertiary else colors.text, maxLines = 2)
        Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = 14.4.dp, modifier = Modifier.padding(top = 12.dp))
    }
}

/**
 * iOS publishRow: the globe in the same tile, one column of the title (14 bold), what each position of the switch
 * does and the rule that nothing leaves the phone unasked (both 11, tertiary), the switch at the top right.
 */
@Composable
private fun PublishCard(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    Row(modifier.testTag("summary_publish").fillMaxWidth().summaryCard(16.dp).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(38.dp).background(AppTheme.accent.copy(alpha = .08f), RoundedCornerShape(10.dp)).testTag("summary_publish_tile"), contentAlignment = Alignment.Center) {
            Glyph(AppGlyph.globe, null, tint = AppTheme.accent, size = 20.4.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(strings.publishToFeed(), style = inter(14f, InterWeight.bold), color = colors.text)
            Text(strings.publishToggleHint(), style = inter(11f), color = colors.textTertiary)
            Text(strings.publishFootnote(), style = inter(11f), color = colors.textTertiary)
        }
        Spacer(Modifier.width(8.dp))
        HouseSwitch(on, onChange, Modifier.semantics { contentDescription = strings.publishToFeed() }.testTag("publish_toggle"))
    }
}

/**
 * iOS bottomBar, pinned under the scroll: «Фото» (the camera becomes a check mark once photos are attached) in
 * white with a hairline, «Готово» in accent with a faint shadow, both Inter 14 bold, 44 tall, radius 14.
 */
@Composable
private fun BottomBar(photoCount: Int, busy: Boolean, onPhoto: () -> Unit, onDone: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val photoInteraction = remember { MutableInteractionSource() }
        Row(Modifier.weight(1f).height(44.dp).alpha(if (busy) .45f else 1f).plainPressFeedback(photoInteraction).background(Color.White, shape).border(1.dp, colors.border, shape)
            .clickable(interactionSource = photoInteraction, indication = null, enabled = !busy, role = Role.Button) { haptics.tap(); onPhoto() }.testTag("summary_photo"),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Glyph(if (photoCount == 0) AppGlyph.cameraFill else AppGlyph.checkmarkCircleFill, null, tint = colors.text, size = 17.7.dp)
            Text(if (photoCount == 0) strings.photoShort() else "${strings.photoShort()} ($photoCount)", Modifier.testTag("summary_photo_label"), style = inter(14f, InterWeight.bold), color = colors.text, maxLines = 1)
        }
        val doneInteraction = remember { MutableInteractionSource() }
        Box(Modifier.weight(1f).height(44.dp).alpha(if (busy) .45f else 1f).plainPressFeedback(doneInteraction)
            .iosShadow(shape, AppTheme.accent.copy(alpha = .3f), 1.5.dp, y = 1.dp).background(AppTheme.accent, shape)
            .clickable(interactionSource = doneInteraction, indication = null, enabled = !busy, role = Role.Button) { haptics.tap(); onDone() }.testTag("summary_done"),
            contentAlignment = Alignment.Center) {
            Text(strings.done(), Modifier.testTag("summary_done_label"), style = inter(14f, InterWeight.bold), color = Color.White, maxLines = 1)
        }
    }
}

/**
 * iOS NotesEditorView: «Отмена», «Описание» and «Сохранить» in one row (16: medium secondary, bold, bold accent),
 * the text on the card colour with 4 of extra line space, and the «N / 500» counter at the bottom right, red at
 * the limit. A note longer than 500 keeps its own length as the limit, so opening the editor never cuts it. It
 * opens at full height with the keyboard up: iOS starts at the medium detent, which the keyboard makes the same.
 */
@Composable
private fun NotesEditorSheet(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val limit = maxOf(MAX_NOTES_LENGTH, graphemeCount(initial))
    var text by remember { mutableStateOf(initial) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    ContentSizedSheet(onDismiss, background = colors.card, detent = SheetDetent.large) {
        val sheet = this
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 18.dp)) {
                Text(strings.descriptionSection(), Modifier.align(Alignment.Center), style = inter(16f, InterWeight.bold), color = colors.text)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.cancel(), Modifier.clickable(interactionSource = null, indication = null, role = Role.Button) { haptics.tap(); sheet.dismiss() }.testTag("summary_notes_cancel"),
                        style = inter(16f, InterWeight.medium), color = colors.textSecondary)
                    Spacer(Modifier.weight(1f))
                    Text(strings.save(), Modifier.clickable(interactionSource = null, indication = null, role = Role.Button) { haptics.action(); onSave(text); sheet.dismiss() }.testTag("summary_notes_save"),
                        style = inter(16f, InterWeight.bold), color = AppTheme.accent)
                }
            }
            BasicTextField(text, { text = graphemePrefix(it, limit) }, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp).padding(top = 8.dp).focusRequester(focus).testTag("summary_notes_field"),
                textStyle = inter(16f).copy(color = colors.text, lineHeight = points(16f * InterLineHeight + 4f)), cursorBrush = SolidColor(AppTheme.accent),
                decorationBox = { field -> Box(Modifier.padding(horizontal = 5.dp, vertical = 8.dp)) { field() } })
            val count = graphemeCount(text)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.End) {
                Text("$count / $limit", style = inter(12f, InterWeight.medium, monospacedDigits = true), color = if (count >= limit) AppTheme.red else colors.textTertiary)
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
            gesturesEnabled = false, textureMode = true, styleUri = LiveMaps.style(true), systemInsets = WindowInsets(0))
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
