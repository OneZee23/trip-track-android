// iOS: ProfileView.swift, ProfileHistoryMonthHeader.swift, ProfileRoutineTripsView.swift, ProfileTripCardView.swift
package com.onezee.triptrack.feature.me

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.navigation.tabBarContentClearance
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.drafts.ui.ProfileDraftsRow
import com.onezee.triptrack.feature.garage.VehicleAvatar
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.feature.social.ui.PublicJourneyCard
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.ui.LocalClock
import com.onezee.triptrack.ui.cards.ProfileCardVehicle
import com.onezee.triptrack.ui.cards.ProfileTripCard
import java.time.Instant

internal val LocalMeAverageSpeed = compositionLocalOf { AvgSpeedMode.overall }

/**
 * iOS: ProfileView.stageNavigation. The tab has no header of its own: the hero card is the first
 * thing on the page (4 under the status bar, or the cover from the very top of the screen), and
 * what follows it depends on the library. With no trips: the guest pitch, the first-trip card and
 * the garage and clubs already open. With trips: the sections toggle, the drafts, the history. The
 * gaps are the iOS paddings of each block (a list spacing would double them), so every block
 * carries its own.
 */
@Composable
internal fun MeContent(state: MeState, unit: DistanceUnit, onSettings: () -> Unit, onGarage: () -> Unit,
                        onVehicle: (java.util.UUID) -> Unit, onAddVehicle: () -> Unit, onAchievements: () -> Unit,
                        onClubs: () -> Unit, onDrafts: () -> Unit, onProfile: () -> Unit, onLevel: () -> Unit,
                        onQuery: (String) -> Unit, onRange: (Instant?, Instant?) -> Unit, onMode: (HistoryMode) -> Unit,
                        onClear: () -> Unit, onTrip: (Trip) -> Unit, onJourney: (Journey) -> Unit,
                        onPin: (java.util.UUID) -> Unit, onReload: () -> Unit,
                        account: @Composable () -> Unit = {}, coverPhotos: List<ProfileCoverPhoto> = emptyList(), api: com.onezee.triptrack.network.ApiClient? = null, onPhotos: () -> Unit = {},
                        coverPromptHidden: Boolean = false, onHideCoverPrompt: () -> Unit = {}, onUnpinElsewhere: () -> Unit = {}, onComposeJourney: (Trip) -> Unit = {},
                        onStats: () -> Unit = {}, signedIn: Boolean = true, needsReauth: Boolean = false, onSignIn: () -> Unit = {},
                        onBadge: (String) -> Unit = {}, onRecord: () -> Unit = {}, clubsSubtitle: String? = null,
                        safeTop: androidx.compose.ui.unit.Dp = statusBarTop()) {
    val strings = appStrings
    val colors = AppTheme.colors
    var sections by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val hasCover = coverPhotos.isNotEmpty()
    // iOS ProfileCoverScrollProbe: once the cover has left the top, the page colour covers the clock again.
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val now = LocalClock.current.instant()
    val hero = state.hero
    val reduceMotion = LocalReduceMotion.current
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        LazyColumn(Modifier.fillMaxSize().then(if (hasCover) Modifier else Modifier.statusBarsPadding()).testTag("screen_me"), state = listState,
            contentPadding = PaddingValues(bottom = tabBarContentClearance())) {
            item(key = "hero") {
                Column(Modifier.padding(bottom = 12.dp)) {
                    ProfileHeroCard(hero, unit, onSettings, onVehicle, onAddVehicle, onProfile, onLevel, coverPhotos, api, onPhotos, onStats, safeTop)
                    if (!hasCover && !coverPromptHidden) ProfileAddCoverPrompt(onPhotos, onHideCoverPrompt, Modifier.padding(horizontal = 16.dp).padding(top = 12.dp))
                }
            }
            if (state.failed) item(key = "retry") {
                Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth().surfaceCard(16.dp)) { MeRow(strings.tripLoadFailed(), strings.tryAgain(), tag = "me_retry", onClick = onReload) }
            }
            if (state.loaded && hero.tripCount == 0) {
                // Zero trips is not zero cars: the garage is the one section with something to do before the first kilometre.
                if (!signedIn) item(key = "account") { Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) { account() } }
                if (state.showcaseElsewhere.isNotEmpty()) item(key = "pins_elsewhere") { PinsElsewhereRow(state.showcaseElsewhere.size, onUnpinElsewhere, Modifier.padding(bottom = 12.dp)) }
                item(key = "welcome") { FirstTripWelcomeCard(onRecord, Modifier.padding(bottom = 12.dp)) }
                if (state.drafts.isNotEmpty()) item(key = "drafts") { DraftsSection(state, now, onDrafts) }
                item(key = "garage") { GarageSection(state.vehicles, mainVehicle(state), unit, onGarage, Modifier.padding(bottom = 12.dp)) }
                item(key = "clubs") { ClubsSection(clubsSubtitle ?: strings.groupsWaitlistFirst(), onClubs, Modifier.padding(bottom = 12.dp)) }
            } else if (state.loaded) {
                if (!signedIn) item(key = "journal_signin") { JournalSignInRow(needsReauth, onSignIn, Modifier.padding(bottom = 12.dp)) }
                item(key = "sections") {
                    Column(Modifier.padding(bottom = 12.dp)) {
                        ProfileSectionsToggle(sections, { sections = !sections })
                        AnimatedVisibility(sections, enter = if (reduceMotion) expandVertically(tween(0)) else expandVertically(tween(200)) + fadeIn(tween(200)),
                            exit = if (reduceMotion) shrinkVertically(tween(0)) else shrinkVertically(tween(200)) + fadeOut(tween(200))) {
                            Column {
                                Spacer(Modifier.height(12.dp))
                                AchievementsSection(state.achievements, onAchievements, onBadge, Modifier.padding(bottom = 12.dp))
                                Spacer(Modifier.height(12.dp))
                                GarageSection(state.vehicles, mainVehicle(state), unit, onGarage, Modifier.padding(bottom = 12.dp))
                                Spacer(Modifier.height(12.dp))
                                ClubsSection(clubsSubtitle ?: strings.groupsWaitlistFirst(), onClubs, Modifier.padding(bottom = 12.dp))
                            }
                        }
                    }
                }
                if (state.drafts.isNotEmpty()) item(key = "drafts") { DraftsSection(state, now, onDrafts) }
                item(key = "history_header") { ProfileHistoryHeader(state.query, onQuery, state.mode, onMode) }
                item(key = "calendar") {
                    // Canon's 16 pt to the first card is padding, so it survives a filter that matches nothing.
                    Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) { ProfileHistoryCalendar(state.calendar, state.from, state.to, state.history.visibleTrips.size, onRange) }
                }
                if (state.showcaseElsewhere.isNotEmpty()) item(key = "pins_elsewhere") { PinsElsewhereRow(state.showcaseElsewhere.size, onUnpinElsewhere, Modifier.padding(bottom = 12.dp)) }
                profileJournal(state, unit, onTrip, onJourney, onPin, onComposeJourney)
                if (state.history.visibleTrips.isEmpty()) item(key = "empty") { ProfileHistoryEmpty(state.query.isNotBlank(), onClear) }
            }
        }
        if (hasCover && scrolled) Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(safeTop).background(colors.bg))
    }
}

/** iOS: the main vehicle row's car: the selected one, else the first that can still be recorded on. */
private fun mainVehicle(state: MeState): Vehicle? =
    state.vehicles.firstOrNull { it.id == state.hero.identity.selectedVehicleId } ?: state.hero.cars.firstOrNull()?.vehicle

/** iOS: «Черновики», the heading over ONE card; no drafts, no section. The old tag stays on the section. */
@Composable
private fun DraftsSection(state: MeState, now: Instant, onDrafts: () -> Unit) {
    Box(Modifier.padding(bottom = 12.dp).testTag("profile_drafts")) {
        ProfileDraftsRow(state.drafts.size, state.drafts.maxOf { it.startDate }, now, onDrafts, Modifier.padding(horizontal = 16.dp))
    }
}

/**
 * iOS: pinnedHistory, then the list (a month heading and its cards) or the grid (runs of tiles, a
 * journey as a card of its own width between them). Cards and tiles keep 14 pt margins, headings 16.
 */
internal fun LazyListScope.profileJournal(state: MeState, unit: DistanceUnit, onTrip: (Trip) -> Unit, onJourney: (Journey) -> Unit, onPin: (java.util.UUID) -> Unit, onComposeJourney: (Trip) -> Unit = {}) {
    val levels = state.history.historicalLevels
    val level = { trip: Trip -> levels?.get(trip.id) ?: state.hero.identity.level }
    // The pinned cards show no car: the strip above them is the profile's showcase, not the journal.
    items(state.history.pinned, key = { "pin_${it.id}" }) { trip ->
        JournalTripCard(trip, level(trip), pinned = true, vehicles = emptyList(), onTrip = onTrip, onComposeJourney = null, onPin = onPin, modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp))
    }
    if (state.mode == HistoryMode.grid) {
        state.history.gridRuns.forEachIndexed { run, rows ->
            val journey = (rows.singleOrNull() as? HistoryRow.Journey)
            if (journey != null) {
                item(key = "grid_${run}_journey") { JournalJourney(journey, onJourney, Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp)) }
            } else {
                val trips = rows.filterIsInstance<HistoryRow.Trip>().map { it.value }
                trips.chunked(2).forEachIndexed { index, pair ->
                    item(key = "grid_${run}_$index") {
                        Row(Modifier.padding(horizontal = 14.dp).padding(bottom = if (index == (trips.size - 1) / 2) 12.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { trip -> JournalTile(trip, unit, onTrip, onComposeJourney, Modifier.weight(1f)) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    } else state.history.chapters.forEach { chapter ->
        // The heading's own 12 above and 4 below, and 8 more under it: 12 from the summary line to the first card.
        item(key = "chapter_${chapter.id}") { ProfileMonthHeader(chapter, unit, Modifier.padding(bottom = 8.dp)) }
        items(chapter.rows, key = { row -> when (row) { is HistoryRow.Trip -> "trip_${row.value.id}"; is HistoryRow.Journey -> "journey_${row.value1.id}"; is HistoryRow.Routine -> "routine_${row.value.trips.first().id}" } }) { row ->
            val modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp)
            when (row) {
                is HistoryRow.Trip -> JournalTripCard(row.value, level(row.value), pinned = false, vehicles = state.vehicles, onTrip = onTrip, onComposeJourney = onComposeJourney, onPin = onPin, modifier = modifier)
                is HistoryRow.Routine -> Box(modifier) { ProfileRoutineTripsView(row.value, unit, onTrip) }
                is HistoryRow.Journey -> JournalJourney(row, onJourney, modifier)
            }
        }
    }
}

/**
 * iOS ProfileTripCardView in the journal: the shared card, with the car the trip was driven in, the
 * average speed the person asked for and a real map where the device has one. A hold opens the
 * journey sheet. There is no menu on the card: pinning lives in the trip's own «…», and is offered
 * to TalkBack here as an action, so no one is left without it.
 */
@Composable
private fun JournalTripCard(trip: Trip, level: Int, pinned: Boolean, vehicles: List<Vehicle>, onTrip: (Trip) -> Unit, onComposeJourney: ((Trip) -> Unit)?,
                            onPin: (java.util.UUID) -> Unit, modifier: Modifier) {
    val strings = appStrings
    val haptics = rememberHaptics()
    val summary = remember(trip) { TripSummary.from(trip) }
    val vehicle = vehicles.firstOrNull { it.id == trip.vehicleId }?.let { car ->
        VehicleAvatar.asset(car)?.let { MeArtwork.drawables[it] }?.let { sprite ->
            ProfileCardVehicle(car.name.trim().ifEmpty { strings.avatarStyleName(car.avatarStyle) }, sprite)
        }
    }
    val speed = if (LocalMeAverageSpeed.current == AvgSpeedMode.moving) trip.movingAverageSpeedMS else trip.averageSpeed
    val pinnable = !trip.isPrivate && trip.endDate != null && trip.confirmation == TripConfirmation.confirmed
    val pinLabel = if (pinned) strings.profileUnpin() else strings.profilePin()
    Box(modifier.testTag("profile_trip_${trip.id}")) {
        ProfileTripCard(summary, onTap = { haptics.tap(); onTrip(trip) }, level = level, isPinned = pinned, mapHeight = 118.dp,
            pressResponse = if (onComposeJourney != null) CardPressResponse.hold else CardPressResponse.tap,
            onLongPress = onComposeJourney?.let { open -> { haptics.action(); open(trip) } }, onLongPressLabel = strings.journeyCombine(),
            vehicle = vehicle, averageSpeed = speed,
            modifier = if (pinnable) Modifier.semantics { customActions = listOf(CustomAccessibilityAction(pinLabel) { onPin(trip.id); true }) } else Modifier,
            map = { coordinates, height -> JournalMap(coordinates, height) })
    }
}

@Composable
private fun JournalMap(coordinates: List<GeoPoint>, height: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(12.dp))) { ProfileMapPreview(listOf(coordinates), Modifier.fillMaxSize()) }
}

@Composable
private fun JournalTile(trip: Trip, unit: DistanceUnit, onTrip: (Trip) -> Unit, onComposeJourney: (Trip) -> Unit, modifier: Modifier) {
    val strings = appStrings
    val haptics = rememberHaptics()
    Box(modifier.testTag("profile_trip_${trip.id}")) {
        ProfileTripTile(trip, unit, onTap = { haptics.tap(); onTrip(trip) }, onLongPress = { haptics.action(); onComposeJourney(trip) }, onLongPressLabel = strings.journeyCombine())
    }
}

/** iOS JourneyCardView in the history: the routes of every leg on one map, the pill, the legs' thumbnails, the title and one line. */
@Composable
private fun JournalJourney(row: HistoryRow.Journey, onJourney: (Journey) -> Unit, modifier: Modifier) {
    Box(modifier.testTag("profile_journey_${row.value1.id}")) { PublicJourneyCard(row.value1, row.legs, onTap = { onJourney(row.value1) }) }
}

@Composable
internal fun ProfileTripRow(trip: Trip, unit: DistanceUnit, pinned: Boolean = false, onClick: () -> Unit, onPin: (() -> Unit)? = null, historicalLevel: Int = 1, onLongPress: (() -> Unit)? = null) {
    val strings = appStrings
    var menu by remember { mutableStateOf(false) }
    val region = ProfileRegionDisplay.localized(trip.region, strings.language.code)
    Column(Modifier.fillMaxWidth().surfaceCard(16.dp).combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongPress).testTag("profile_trip_${trip.id}")) {
        Row(Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(strings.tr(com.onezee.triptrack.designsystem.R.string.android_level_label, historicalLevel),
                style = inter(13f).copy(fontFamily = FontFamily(Font(com.onezee.triptrack.R.font.handjet_black))), color = AppTheme.gold)
            Text(strings.longDate(trip.startDate) + region?.let { " · $it" }.orEmpty(), Modifier.weight(1f), style = inter(11f), color = AppTheme.colors.textTertiary, maxLines = 1)
            if (pinned) Text(strings.profilePinned(), style = inter(11f, InterWeight.semibold), color = AppTheme.accent)
            if (onPin != null && !trip.isPrivate && trip.endDate != null && trip.confirmation == TripConfirmation.confirmed) Box {
                NavCircleButton(AppGlyph.ellipsis, strings.moreActions(), { menu = true })
                ActionPopover(menu, { menu = false }, listOf(ActionPopoverItem(if (pinned) strings.profileUnpin() else strings.profilePin(), AppGlyph.flag, action = onPin)))
            }
        }
        Text(trip.title?.takeIf(String::isNotBlank) ?: region ?: strings.longDate(trip.startDate),
            Modifier.fillMaxWidth().padding(horizontal = 13.dp).padding(bottom = 10.dp), style = interScaled(17f, InterWeight.heavy), color = AppTheme.colors.text, maxLines = 2)
        Box(Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(12.dp))) {
            ProfileMapPreview(listOf(trip.previewCoordinates), Modifier.fillMaxSize())
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp).padding(top = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val distance = Measure.distanceParts(trip.distance, unit, strings.language.code, strings, Measure.Style.tenths)
            val speed = Measure.speedParts(if (LocalMeAverageSpeed.current == AvgSpeedMode.moving) trip.movingAverageSpeedMS else trip.averageSpeed, unit, strings.language.code, strings)
            val total = trip.duration.toLong().coerceAtLeast(0)
            val hours = total / 3600
            val minutes = total % 3600 / 60
            val time = if (hours > 0) "$hours ${strings.hoursUnitShort()} ${minutes.toString().padStart(2, '0')} ${strings.minutesUnitShort()}" else "$minutes ${strings.minutesUnitShort()}"
            JournalMetric(distance.value, distance.unit, strings.distance(), Modifier.weight(1f))
            JournalMetric(time, "", strings.duration(), Modifier.weight(1f))
            JournalMetric(speed.value, speed.unit, strings.avgSpeedShort(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun JournalMetric(value: String, unit: String, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val number = AppType.statValue.toSpanStyle().copy(color = AppTheme.colors.text)
        val word = AppType.statUnit.toSpanStyle().copy(color = AppTheme.colors.textSecondary)
        Text(buildAnnotatedString { withStyle(number) { append(value) }; if (unit.isNotEmpty()) withStyle(word) { append(" $unit") } }, maxLines = 1)
        Text(label.uppercase(appStrings.language.locale), style = AppType.statCaption, color = AppTheme.colors.textTertiary, maxLines = 1)
    }
}

@Composable
internal fun RoutePreview(points: List<GeoPoint>, modifier: Modifier = Modifier) {
    val ground = AppTheme.colors.cardAlt
    val route = AppTheme.accent
    Canvas(modifier.background(ground)) {
        if (points.size < 2) return@Canvas
        val minX = points.minOf { it.longitude }; val maxX = points.maxOf { it.longitude }
        val minY = points.minOf { it.latitude }; val maxY = points.maxOf { it.latitude }
        val spanX = (maxX - minX).coerceAtLeast(.001); val spanY = (maxY - minY).coerceAtLeast(.001)
        val scale = minOf(size.width * .80 / spanX, size.height * .78 / spanY)
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = (size.width / 2 + (point.longitude - (minX + maxX) / 2) * scale).toFloat()
            val y = (size.height / 2 - (point.latitude - (minY + maxY) / 2) * scale).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, route, style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}
