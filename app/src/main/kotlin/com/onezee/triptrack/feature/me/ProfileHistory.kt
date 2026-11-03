// iOS: ProfileView.swift (historyHeader, historyModeControls, historyEmptyState), ProfileTripTile.swift,
// ProfileHistoryMonthHeader.swift, ProfileRoutineTripsView.swift
package com.onezee.triptrack.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.logic.previewCoordinates
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.ProfileHistoryMonth
import com.onezee.triptrack.model.ProfileRoutineTrips
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.ui.RegionDisplay
import com.onezee.triptrack.ui.TripAutoTitle
import com.onezee.triptrack.ui.cards.dayMonth

// ---------------------------------------------------------------------------------------------
// Header: the title, the two views, the search

/**
 * iOS historyHeader: «История» at 22 bold with the grid and the list at its right, then one 48 pt
 * search bar. At the largest text sizes the two views go under the title.
 */
@Composable
internal fun ProfileHistoryHeader(query: String, onQuery: (String) -> Unit, mode: HistoryMode, onMode: (HistoryMode) -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val accessibility = isAccessibilitySize()
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.historySection(), Modifier.weight(1f).semantics { heading() }.testTag("profile_history_header"),
                style = interScaled(22f, InterWeight.bold), color = AppTheme.colors.text)
            if (!accessibility) HistoryModeControls(mode, onMode)
        }
        if (accessibility) HistoryModeControls(mode, onMode)
        HistorySearchBar(query, onQuery)
    }
}

/** iOS historyModeControls: two 44 pt icon buttons, no plate, the accent alone says which one is on. */
@Composable
private fun HistoryModeControls(mode: HistoryMode, onMode: (HistoryMode) -> Unit) {
    val strings = appStrings
    val haptics = rememberHaptics()
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            Triple(HistoryMode.grid, MeGlyph.squareGrid, strings.historyModeGrid() to "profile_history_grid"),
            Triple(HistoryMode.list, MeGlyph.lines3, strings.historyModeList() to "profile_history_list"),
        ).forEach { (choice, glyph, label) ->
            val active = mode == choice
            Box(Modifier.size(44.dp).selectable(selected = active, role = Role.RadioButton) { if (!active) { haptics.tap(); onMode(choice) } }
                .semantics { contentDescription = label.first }.testTag(label.second), contentAlignment = Alignment.Center) {
                MeGlyphIcon(glyph, null, tint = if (active) AppTheme.accent else AppTheme.colors.textSecondary, size = symbolBox(16f))
            }
        }
    }
}

/** iOS: a magnifier, a 15 pt field with its prompt, a clear button while typing; the whole bar focuses the field. */
@Composable
private fun HistorySearchBar(query: String, onQuery: (String) -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(colors.card, RoundedCornerShape(14.dp)).testTag("profile_history_search_bar")
        .clickable(interactionSource = null, indication = null) { focus.requestFocus(); keyboard?.show() }.padding(start = 14.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(AppGlyph.magnifyingglass, null, tint = colors.textSecondary, size = symbolBox(17f))
        BasicTextField(query, onQuery, Modifier.weight(1f).focusRequester(focus).testTag("profile_history_search"), singleLine = true,
            textStyle = interScaled(15f).copy(color = colors.text), cursorBrush = SolidColor(AppTheme.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false), keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(strings.historySearch(), style = interScaled(15f), color = colors.textSecondary, maxLines = 1)
                    field()
                }
            })
        if (query.isNotEmpty()) Box(Modifier.size(44.dp).clickable(role = Role.Button) { onQuery("") }.semantics { contentDescription = strings.historyClearSearch() }
            .testTag("profile_history_clear_search"), contentAlignment = Alignment.Center) {
            Glyph(AppGlyph.xmarkCircleFill, null, tint = colors.textSecondary, size = symbolBox(17f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Months and cards

/** iOS ProfileHistoryMonthHeader: the month at 24 heavy with −0.5 tracking, then its distance, trips and regions. */
@Composable
internal fun ProfileMonthHeader(chapter: ProfileHistoryMonth, unit: DistanceUnit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(chapter.heading, Modifier.semantics { heading() }.testTag("profile_month_header"), style = interScaled(24f, InterWeight.heavy), letterSpacing = points(-0.5f), color = colors.text)
        Text(strings.profileChapterSummary(Measure.distance(chapter.distance, unit, strings.language.code, strings), chapter.tripCount, chapter.regionCount),
            modifier = Modifier.testTag("profile_month_summary"), style = interScaled(13f), color = colors.textSecondary)
    }
}

/** iOS ProfileTripTile.titleText / TripAutoTitle ladder: a real name, else the region, else the date. */
@Composable
internal fun profileTripTitle(trip: Trip): String {
    val strings = appStrings
    val named = !trip.title.isNullOrBlank() && !TripAutoTitle.isAuto(trip.title, trip.startDate, strings.zone)
    val region = RegionDisplay.localized(trip.region, strings.language)
    return (if (named) TripAutoTitle.localized(trip.title, trip.startDate, strings)?.takeIf { it.isNotEmpty() } else null)
        ?: region?.takeIf { it.isNotEmpty() } ?: strings.dayMonth(trip.startDate)
}

/**
 * iOS ProfileTripTile: a cell of the grid, a 72 pt map flush with the top and two lines under it,
 * the title and «2 нояб · 46 км». The grid trades the metrics strip for density.
 */
@Composable
internal fun ProfileTripTile(trip: Trip, unit: DistanceUnit, onTap: () -> Unit, modifier: Modifier = Modifier,
                             onLongPress: (() -> Unit)? = null, onLongPressLabel: String? = null) {
    val strings = appStrings
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val shadow = if (AppTheme.isDark) Color.Transparent else Color.Black.copy(alpha = 0.03f)
    val meta = strings.dayMonth(trip.startDate) + " · " + Measure.distance(trip.distance, unit, strings.language.code, strings, Measure.Style.grouped)
    PressableCard(onClick = onTap, modifier = modifier.fillMaxWidth().testTag("profile_trip_tile"), response = CardPressResponse.hold,
        onLongClick = onLongPress, onLongClickLabel = onLongPressLabel) {
        Column(Modifier.fillMaxWidth().iosShadow(shape, shadow, radius = 2.dp, y = 1.dp).clip(shape).background(colors.card)) {
            // The thumbnail is flush with three edges, so the tile's own clip rounds the two corners that need it.
            Box(Modifier.fillMaxWidth().height(72.dp)) {
                if (trip.previewCoordinates.size > 1) com.onezee.triptrack.ui.MapSnapshotPreview(trip.previewCoordinates, trip.id, Modifier.fillMaxSize(), height = 72.dp, width = 175.dp, cornerRadius = 0.dp)
                else Box(Modifier.fillMaxSize().background(colors.cardAlt), contentAlignment = Alignment.Center) {
                    Glyph(AppGlyph.mapSlash, null, tint = colors.textTertiary, size = symbolBox(16f))
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(profileTripTitle(trip), style = inter(12f, InterWeight.bold), color = colors.text, maxLines = 1)
                Text(meta, style = inter(10f, InterWeight.semibold), color = colors.textTertiary, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Repeated trips

/**
 * iOS ProfileRoutineTripsView: «N повторяющихся поездок · 50 км» with the titles of the first ones;
 * it opens in place (a chevron that turns over, not one that points on) into compact rows, seven
 * of them and «Ещё N» for the rest.
 */
@Composable
internal fun ProfileRoutineTripsView(group: ProfileRoutineTrips, unit: DistanceUnit, onTrip: (Trip) -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val key = group.trips.first().id.toString()
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    var showsAll by rememberSaveable(key) { mutableStateOf(false) }
    val accessibility = isAccessibilitySize()
    fun title(trip: Trip) = TripAutoTitle.localized(trip.title, trip.startDate, strings)?.takeIf { it.isNotEmpty() }
        ?: RegionDisplay.localized(trip.region, strings.language)?.takeIf { it.isNotEmpty() } ?: strings.dayMonth(trip.startDate)
    val state = if (expanded) strings.historySectionsExpanded() else strings.historySectionsCollapsed()
    Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
        PressableCard(onClick = { haptics.selection(); expanded = !expanded }, modifier = Modifier.fillMaxWidth().semantics { stateDescription = state }.testTag("profile_routine_toggle")) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).background(colors.cardAlt, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Glyph(AppGlyph.arrowClockwise, null, tint = colors.text, size = symbolBox(16f))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.profileRoutineCount(group.trips.size) + " · " + Measure.distance(group.distance, unit, strings.language.code, strings),
                        style = interScaled(15f, InterWeight.semibold), color = colors.text, maxLines = if (accessibility) Int.MAX_VALUE else 1,
                        autoSize = if (accessibility) null else TextAutoSize.StepBased(minFontSize = (15f * .85f).sp, maxFontSize = 15.sp))
                    Text(group.titleTrips.joinToString(", ", transform = ::title), style = interScaled(13f), color = colors.textSecondary, maxLines = if (accessibility) Int.MAX_VALUE else 1)
                }
                Glyph(if (expanded) AppGlyph.chevronUp else AppGlyph.chevronDown, null, tint = colors.textTertiary, size = symbolBox(11f))
            }
        }
        if (expanded) {
            (if (showsAll) group.trips else group.trips.take(7)).forEach { trip ->
                PressableCard(onClick = { haptics.tap(); onTrip(trip) }, modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth().testTag("profile_routine_trip_${trip.id}")) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).drawHairline(colors.border), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title(trip), style = interScaled(14f), color = colors.text, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        Text(strings.dayMonth(trip.startDate), style = interScaled(12f), color = colors.textTertiary, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Text(Measure.distance(trip.distance, unit, strings.language.code, strings), style = interScaled(13f, InterWeight.semibold), color = colors.text, maxLines = 1)
                    }
                }
            }
            if (!showsAll && group.trips.size > 7) PressableCard(onClick = { haptics.selection(); showsAll = true }, modifier = Modifier.fillMaxWidth().testTag("profile_routine_more")) {
                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                    Text(strings.profileMoreTrips(group.trips.size - 7), style = interScaled(14f, InterWeight.medium), color = AppTheme.accent)
                }
            }
        }
    }
}

/** A 1 pt rule over the top of a row, the way iOS overlays it: inside the row's own horizontal padding. */
private fun Modifier.drawHairline(color: Color): Modifier = drawBehind {
    drawRect(color, topLeft = Offset.Zero, size = Size(size.width, 1.dp.toPx()))
}

// ---------------------------------------------------------------------------------------------
// Empty

/** iOS historyEmptyState: what the filter found, said at 17 semibold, and an accent text button to undo it. */
@Composable
internal fun ProfileHistoryEmpty(searching: Boolean, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().surfaceCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (searching) strings.historyNoMatches() else strings.historyNoTripsInPeriod(), Modifier.testTag("profile_history_empty"),
            style = interScaled(17f, InterWeight.semibold), color = colors.text)
        Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClear).testTag("profile_history_clear_filters"), contentAlignment = Alignment.CenterStart) {
            Text(strings.historyClearFilters(), style = interScaled(15f, InterWeight.medium), color = AppTheme.accent)
        }
    }
}
