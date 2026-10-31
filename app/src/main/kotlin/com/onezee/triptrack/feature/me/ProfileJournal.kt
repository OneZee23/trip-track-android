// iOS: ProfileView.swift, ProfileHistoryMonthHeader.swift, ProfileRoutineTripsView.swift, ProfileTripCardView.swift
package com.onezee.triptrack.feature.me

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.navigation.tabBarContentClearance
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.time.Instant

internal val LocalMeAverageSpeed = compositionLocalOf { AvgSpeedMode.overall }

@Composable
internal fun MeContent(state: MeState, unit: DistanceUnit, onSettings: () -> Unit, onGarage: () -> Unit,
                        onVehicle: (java.util.UUID) -> Unit, onAddVehicle: () -> Unit, onAchievements: () -> Unit,
                        onClubs: () -> Unit, onDrafts: () -> Unit, onProfile: () -> Unit, onLevel: () -> Unit,
                        onQuery: (String) -> Unit, onRange: (Instant?, Instant?) -> Unit, onMode: (HistoryMode) -> Unit,
                        onClear: () -> Unit, onTrip: (Trip) -> Unit, onJourney: (Journey) -> Unit,
                        onPin: (java.util.UUID) -> Unit, onReload: () -> Unit,
                        account: @Composable () -> Unit = {}, coverPhotos: List<ProfileCoverPhoto> = emptyList(), api: com.onezee.triptrack.network.ApiClient? = null, onPhotos: () -> Unit = {},
                        coverPromptHidden: Boolean = false, onHideCoverPrompt: () -> Unit = {}, onUnpinElsewhere: () -> Unit = {}, onComposeJourney: (Trip) -> Unit = {}) {
    val strings = appStrings
    var sections by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding().testTag("screen_me"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = tabBarContentClearance()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "title") { Text(strings.tabMe(), style = AppType.title, color = AppTheme.colors.text, modifier = Modifier.semantics { heading() }) }
        item(key = "hero") { ProfileHeroCard(state.hero, unit, onSettings, onVehicle, onAddVehicle, onProfile, onLevel, coverPhotos, api, onPhotos) }
        if (coverPhotos.isEmpty() && !coverPromptHidden) item(key = "cover_prompt") {
            Row(Modifier.surfaceCard(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { MeRow(strings.profileAddCover(), tag = "profile_add_cover", onClick = onPhotos) }
                NavCircleButton(AppGlyph.xmark, strings.profileCoverPromptHide(), onHideCoverPrompt, Modifier.padding(end = 8.dp))
            }
        }
        item(key = "account") { account() }
        item(key = "sections") {
            Column(Modifier.surfaceCard(16.dp)) {
                MeRow(listOf(strings.garage(), strings.achievementsSection(), strings.clubsTitle()).joinToString(" · "),
                    tag = "profile_sections_toggle", onClick = { sections = !sections })
                if (sections) {
                    MeRow(strings.garage(), state.hero.cars.size.toString(), onClick = onGarage)
                    MeRow(strings.achievementsSection(), strings.achievementsProgress(state.achievements.count { it.unlocked }, state.achievements.size), onClick = onAchievements)
                    MeRow(strings.groupsComingTitle(), subtitle = strings.groupsWaitlistFirst(), onClick = onClubs)
                }
            }
        }
        if (state.drafts.isNotEmpty()) item(key = "drafts") { Column(Modifier.surfaceCard(16.dp)) { MeRow(strings.draftsTitle(), state.drafts.size.toString(), tag = "profile_drafts", onClick = onDrafts) } }
        if (state.failed) item { MeRow(strings.tripLoadFailed(), strings.tryAgain(), tag = "me_retry", onClick = onReload) }
        item(key = "history_heading") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.historySection(), Modifier.weight(1f).testTag("profile_history_header"), style = AppType.section, color = AppTheme.colors.text)
                MeHistoryMode(state.mode, onMode)
            }
            Column(Modifier.fillMaxWidth().surfaceCard(14.dp)) { MeField(strings.historySearch(), state.query, onQuery, tag = "profile_history_search") }
        }
        item(key = "calendar") { ProfileHistoryCalendar(state.calendar, state.from, state.to, state.history.visibleTrips.size, onRange) }
        if (state.showcaseElsewhere.isNotEmpty()) item(key = "pins_elsewhere") { MeRow(strings.profileShowcaseElsewhere(state.showcaseElsewhere.size), strings.profileUnpin(), tag = "profile_showcase_unpin_elsewhere", onClick = onUnpinElsewhere) }
        profileJournal(state, unit, onTrip, onJourney, onPin, onComposeJourney)
        if (state.loaded && state.history.visibleTrips.isEmpty()) item(key = "empty") {
            Column(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (state.query.isNotBlank()) strings.historyNoMatches() else strings.historyNoTripsInPeriod(), style = AppType.body, color = AppTheme.colors.textSecondary)
                if (state.from != null || state.query.isNotBlank()) MeAction(strings.historyClearFilters(), onClear)
            }
        }
    }
}

internal fun LazyListScope.profileJournal(state: MeState, unit: DistanceUnit, onTrip: (Trip) -> Unit, onJourney: (Journey) -> Unit, onPin: (java.util.UUID) -> Unit, onComposeJourney: (Trip) -> Unit = {}) {
    items(state.history.pinned, key = { "pin_${it.id}" }) { trip -> ProfileTripRow(trip, unit, pinned = true, onClick = { onTrip(trip) }, onLongPress = { onComposeJourney(trip) }, onPin = { onPin(trip.id) }, historicalLevel = state.history.historicalLevels?.get(trip.id) ?: state.hero.identity.level) }
    if (state.mode == HistoryMode.grid) {
        state.history.gridRuns.forEachIndexed { run, rows ->
            rows.chunked(2).forEachIndexed { index, pair -> item(key = "grid_${run}_$index") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { row -> Box(Modifier.weight(1f)) { JournalRow(row, unit, onTrip, onJourney, onPin, state.history.historicalLevels, onComposeJourney) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            } }
        }
    } else state.history.chapters.forEach { chapter ->
        item(key = "chapter_${chapter.id}") {
            Column(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(chapter.heading, style = interScaled(24f, InterWeight.heavy), color = AppTheme.colors.text, modifier = Modifier.semantics { heading() }.testTag("profile_month_header"))
                Text(appStrings.profileChapterSummary(Measure.distance(chapter.distance, unit, appStrings.language.code, appStrings), chapter.tripCount, chapter.regionCount), style = interScaled(13f), color = AppTheme.colors.textSecondary)
            }
        }
        items(chapter.rows, key = { row -> when (row) { is HistoryRow.Trip -> "trip_${row.value.id}"; is HistoryRow.Journey -> "journey_${row.value1.id}"; is HistoryRow.Routine -> "routine_${row.value.trips.first().id}" } }) { row ->
            JournalRow(row, unit, onTrip, onJourney, onPin, state.history.historicalLevels, onComposeJourney)
        }
    }
}

@Composable
private fun JournalRow(row: HistoryRow, unit: DistanceUnit, onTrip: (Trip) -> Unit, onJourney: (Journey) -> Unit, onPin: (java.util.UUID) -> Unit, levels: Map<java.util.UUID, Int>?, onComposeJourney: (Trip) -> Unit) {
    when (row) {
        is HistoryRow.Trip -> ProfileTripRow(row.value, unit, onClick = { onTrip(row.value) }, onLongPress = { onComposeJourney(row.value) }, onPin = { onPin(row.value.id) }, historicalLevel = levels?.get(row.value.id) ?: 1)
        is HistoryRow.Routine -> ProfileRoutineTripsView(row.value, unit, onTrip)
        is HistoryRow.Journey -> Column(Modifier.surfaceCard(16.dp)) { MeRow(row.value1.title.orEmpty().ifBlank { appStrings.journeyWord() }, appStrings.tripsCount(row.legs.size), tag = "profile_journey_${row.value1.id}", onClick = { onJourney(row.value1) }) }
    }
}

@Composable
internal fun ProfileRoutineTripsView(group: ProfileRoutineTrips, unit: DistanceUnit, onTrip: (Trip) -> Unit) {
    val strings = appStrings
    var expanded by rememberSaveable(group.trips.first().id.toString()) { mutableStateOf(false) }
    var showsAll by rememberSaveable(group.trips.first().id.toString()) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
        MeRow(strings.profileRoutineCount(group.trips.size) + " · " + Measure.distance(group.distance, unit, strings.language.code, strings),
            subtitle = group.titleTrips.mapNotNull { it.title }.joinToString(", "), tag = "profile_routine_toggle", onClick = { expanded = !expanded })
        if (expanded) {
            (if (showsAll) group.trips else group.trips.take(7)).forEach { trip ->
                MeDivider()
                MeRow(trip.title ?: strings.longDate(trip.startDate), Measure.distance(trip.distance, unit, strings.language.code, strings), tag = "profile_routine_trip_${trip.id}", onClick = { onTrip(trip) })
            }
            if (!showsAll && group.trips.size > 7) MeRow(strings.profileMoreTrips(group.trips.size - 7), tag = "profile_routine_more", onClick = { showsAll = true })
        }
    }
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

@Composable
private fun MeHistoryMode(mode: HistoryMode, onMode: (HistoryMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        HistoryMode.entries.forEach { choice ->
            val label = if (choice == HistoryMode.list) appStrings.historyModeList() else appStrings.historyModeGrid()
            Text(label, Modifier.background(if (mode == choice) AppTheme.accentBg else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
                .clickable(role = Role.RadioButton) { onMode(choice) }.padding(horizontal = 10.dp, vertical = 12.dp),
                style = interScaled(12f, InterWeight.semibold), color = if (mode == choice) AppTheme.accent else AppTheme.colors.textSecondary)
        }
    }
}
