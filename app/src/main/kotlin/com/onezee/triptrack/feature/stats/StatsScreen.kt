// iOS: StatsScreenView.swift (body, memoryCard, headline, yearStrip, factsRow, newPlacesSection,
// hallOfFame, archiveCard, emptyState, remote states, openForeignTrip)
package com.onezee.triptrack.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.me.RoutePreview
import com.onezee.triptrack.feature.social.ui.SocialGlyph
import com.onezee.triptrack.feature.social.ui.SocialGlyphIcon
import com.onezee.triptrack.feature.social.ui.SocialSectionLabel
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.ui.RegionDisplay
import java.time.YearMonth
import java.util.UUID

/** One record's finished content (iOS RecordRow); a row without a trip has no chevron and no tap. */
private data class RecordRow(val id: String, val label: String, val subject: String, val value: String, val tripId: UUID?)

/**
 * iOS: StatsScreenView. The answer first («393 км · 18 поездок» for the month), the year as twelve
 * columns that ARE the month picker, the time and regions of the period, new places, lifetime
 * records and, on one's own, the archive line. Someone else's screen drops the year-ago card, the
 * roads and the archive: those live only on the owner's phone. Every number is real or absent.
 */
@Composable
fun StatsScreen(vm: StatsViewModel, ownerName: String?, onBack: () -> Unit, onOpenTrip: (UUID, String) -> Unit,
                loadingRecordId: String? = null, onRecordTrip: (() -> Unit)? = null) {
    val strings = appStrings
    val colors = AppTheme.colors
    val state by vm.state.collectAsState()
    HideAppTabBar()
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().testTag("stats_screen")) {
        CustomNavBar(strings.stats(), onBack = onBack, subtitle = ownerName, largeTitle = true)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val aggregates = state.aggregates
            when {
                // Up to twenty pages in a row: an empty screen all that time looks broken.
                aggregates == null -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { com.onezee.triptrack.feature.atlas.ui.CarLoading() }
                vm.isRemote && state.failed && aggregates.tripCount == 0 -> Column(Modifier.fillMaxWidth().padding(top = 60.dp).testTag("stats_remote_failed"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialGlyphIcon(SocialGlyph.wifiExclamation, null, tint = colors.textTertiary, size = 26.dp)
                    Text(strings.publicDataLoadFailed(), style = inter(14f, InterWeight.bold), color = colors.text)
                    Text(strings.retry(), Modifier.clickable(role = Role.Button) { vm.load() }.padding(8.dp), style = inter(13f, InterWeight.bold), color = AppTheme.accent)
                }
                else -> {
                    if (vm.isRemote && state.failed) Row(Modifier.fillMaxWidth().background(AppTheme.accentBg, RoundedCornerShape(12.dp)).padding(10.dp).testTag("stats_partial_banner"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph(AppGlyph.exclamationmarkTriangleFill, null, tint = AppTheme.accent, size = 12.dp)
                        Text(strings.publicDataPartial(), Modifier.weight(1f), style = inter(11.5f), color = colors.textSecondary)
                        Text(strings.retry(), Modifier.clickable(role = Role.Button) { vm.load() }, style = inter(12f, InterWeight.bold), color = AppTheme.accent)
                    }
                    when {
                        aggregates.tripCount == 0 && vm.isRemote -> Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 60.dp).testTag("stats_remote_empty"),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SocialGlyphIcon(SocialGlyph.chart, null, tint = colors.textTertiary, size = 26.dp)
                            Text(strings.publicMapEmptyTitle(), style = inter(14f, InterWeight.bold), color = colors.text)
                            Text(strings.publicMapEmptyBody(), style = inter(12f), color = colors.textTertiary, textAlign = TextAlign.Center)
                        }
                        aggregates.tripCount == 0 -> EmptyStats(onRecordTrip)
                        else -> StatsContent(vm, state, aggregates, onOpenTrip, loadingRecordId)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsContent(vm: StatsViewModel, state: StatsState, aggregates: MeAggregates, onOpenTrip: (UUID, String) -> Unit, loadingRecordId: String?) {
    val strings = appStrings
    val colors = AppTheme.colors
    val unit = LocalDistanceUnit.current
    val zone = strings.zone
    val haptics = rememberHaptics()
    val current = vm.slice(state)
    val previous = vm.previousSlice(state)
    if (!vm.isRemote) aggregates.yearAgoTrip?.let { trip -> MemoryCard(trip) { onOpenTrip(trip.id, "memory") } }
    // The headline: the period small, then the answer as one phrase; the kilometres are the one red thing.
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(top = 4.dp, bottom = 2.dp).testTag("stats_summary"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(when (state.range) { StatsRange.month -> StatsPeriodFormat.monthYear(state.anchor, strings.language, zone); StatsRange.year -> "${state.anchor.year}"; StatsRange.all -> strings.statsRangeAll() },
            style = AppType.meta, color = colors.textSecondary)
        val distance = Measure.distanceKm(current.km, unit, strings.language.code, strings)
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = AppTheme.accent)) { append(distance) }
            withStyle(SpanStyle(color = colors.textTertiary)) { append(" · ") }
            withStyle(SpanStyle(color = colors.text)) { append(strings.tripsCount(current.trips)) }
        }, style = AppType.title, letterSpacing = AppType.titleTracking, maxLines = 2)
        if (state.range != StatsRange.all) Box(Modifier.heightIn(min = 18.dp)) {
            // No line when there is nothing to compare, or when the printed numbers coincide.
            val line = previous?.takeIf { it.km > 0 }?.let { prev ->
                val shown = Measure.distanceKm(prev.km, unit, strings.language.code, strings)
                if (shown == distance) null else strings.statsVsPrevious(current.km > prev.km, state.range == StatsRange.year, shown)
            }
            if (line != null) Text(line, style = AppType.meta, color = colors.textSecondary)
        }
    }
    YearStrip(vm, state, aggregates)
    // The facts the phrase lacks: time at the wheel and the period's regions; never a zero.
    val regions = vm.regionsInRange(state)
    if (current.trips > 0) Row(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(vertical = 14.dp).testTag("stats_facts")) {
        val hours = current.hours
        if (hours < 1) FactTile("${maxOf(1, Math.round(hours * 60).toInt())}", strings.minutesUnitShort(), strings.journeyDrivingLabel(), false)
        else FactTile(strings.groupedNumber(Math.round(hours).toInt()), strings.hoursUnitShort(), strings.journeyDrivingLabel(), false)
        if (regions > 0) FactTile(strings.groupedNumber(regions), null, strings.nounRegions(regions), true)
    }
    if (state.range != StatsRange.all) {
        val newRegions = vm.newRegions(state)
        val roads = if (vm.isRemote) 0 else vm.newRoads(state)
        if (newRegions.isNotEmpty() || roads > 0) {
            SocialSectionLabel(strings.statsNewPlaces(), Modifier.padding(start = 2.dp, top = 6.dp))
            Column(Modifier.fillMaxWidth().surfaceCard(16.dp).testTag("stats_new_places")) {
                newRegions.forEachIndexed { index, (name, date) ->
                    if (index > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp).testTag("stats_new_region"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(StatsPeriodFormat.dayMonthShort(date, strings.language, zone), Modifier.widthIn(min = 56.dp), style = inter(12f, monospacedDigits = true), color = colors.textSecondary, maxLines = 1)
                        Text(RegionDisplay.localized(name, strings.language) ?: name, Modifier.weight(1f), style = AppType.itemTitle, color = colors.text, maxLines = 1)
                    }
                }
                if (roads > 0) {
                    if (newRegions.isNotEmpty()) RowDivider()
                    val period = when (state.range) { StatsRange.month -> StatsPeriodFormat.monthLower(state.anchor, strings.language, zone); StatsRange.year -> "${state.anchor.year}"; StatsRange.all -> strings.statsRangeAll().lowercase(strings.language.locale) }
                    Text(strings.statsNewPlacesLine(period, roads, null), Modifier.padding(horizontal = 16.dp, vertical = 13.dp), style = AppType.meta, color = colors.textSecondary)
                }
            }
        }
    }
    val records = records(aggregates, strings, unit, zone)
    if (records.isNotEmpty()) {
        SocialSectionLabel(strings.statsRecords(), Modifier.padding(start = 2.dp, top = 6.dp))
        Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
            records.forEachIndexed { index, row ->
                if (index > 0) RowDivider()
                val tapped = row.tripId?.let { id -> { haptics.tap(); onOpenTrip(id, row.id) } }
                val line = @Composable { modifier: Modifier ->
                    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(row.label, style = AppType.caption, color = colors.textSecondary, maxLines = 1)
                            Text(row.subject, style = AppType.itemTitle, color = colors.text, maxLines = 1)
                        }
                        Text(row.value, style = AppType.itemValue.copy(fontFeatureSettings = "tnum"), color = colors.text, maxLines = 1)
                        // The chevron's slot stays on a row without one, or the numbers would not line up.
                        Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) {
                            if (loadingRecordId == row.id) com.onezee.triptrack.feature.account.Spinner(colors.textTertiary, size = 12.dp)
                            else if (row.tripId != null) Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = 12.dp)
                        }
                    }
                }
                // iOS: the row is a Button in PressableCardStyle; a row without a trip is plain text.
                if (tapped != null) PressableCard(onClick = tapped, modifier = Modifier.fillMaxWidth().testTag("stats_record_row")) { line(Modifier) }
                else line(Modifier.testTag("stats_record_row"))
            }
        }
    }
    // Remote trips carry neither photos nor notes: «0 поездок с фото» would be a claim about someone unchecked.
    if (!vm.isRemote) aggregates.firstTripDate?.let { first ->
        Column(Modifier.fillMaxWidth().padding(top = 10.dp).surfaceCard(16.dp).padding(16.dp).testTag("stats_archive"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.statsWithTripTrackSince(StatsPeriodFormat.monthYearGenitive(first, strings.language, zone)), style = AppType.itemTitle, color = colors.text)
            Text(strings.statsMemoriesLine(Measure.distanceKm(aggregates.totalKm, unit, strings.language.code, strings), aggregates.tripsWithMemories), style = AppType.meta, color = colors.textSecondary)
        }
    }
}

/** iOS memoryCard: «В этот день год назад» with the trip's route, name and distance. */
@Composable
private fun MemoryCard(trip: Trip, onOpen: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val unit = LocalDistanceUnit.current
    PressableCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("stats_memory_card")) {
        Column(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(strings.statsYearAgoToday().uppercase(strings.language.locale), style = AppType.statCaption, letterSpacing = AppType.statCaptionTracking, color = colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(80.dp, 56.dp).clip(RoundedCornerShape(10.dp))) { RoutePreview(trip.previewCoordinates, Modifier.matchParentSize()) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(trip.title?.takeIf { it.isNotEmpty() } ?: trip.region?.takeIf { it.isNotEmpty() } ?: StatsPeriodFormat.dayMonthShort(trip.startDate, strings.language, strings.zone),
                        style = AppType.itemTitle, color = colors.text, maxLines = 1)
                    val km = Measure.distance(trip.distance, unit, strings.language.code, strings, Measure.Style.grouped)
                    Text(if (trip.photos.isEmpty()) km else "$km · ${strings.photosCount(trip.photos.size)}", style = AppType.caption, color = colors.textSecondary, maxLines = 1)
                }
                Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = 13.dp)
            }
        }
    }
}

/** iOS yearStrip: twelve columns, a tap parks the month on one; «Год» and «Всё время» under them. */
@Composable
private fun YearStrip(vm: StatsViewModel, state: StatsState, aggregates: MeAggregates) {
    val strings = appStrings
    val colors = AppTheme.colors
    val unit = LocalDistanceUnit.current
    val haptics = rememberHaptics()
    val chart = vm.chart(state)
    val maxKm = chart.km.maxOrNull() ?: 0.0
    val labels = remember(strings.language) { StatsPeriodFormat.shortMonths(strings.language, strings.zone) }
    val dim = AppTheme.accent.copy(alpha = if (AppTheme.isDark) .22f else .12f)
    Column(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(horizontal = 14.dp).padding(top = 4.dp, bottom = 6.dp).testTag("stats_chart"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            val title = if (state.range != StatsRange.all) "${state.anchor.year}" else {
                val nowYear = YearMonth.now().year
                val first = aggregates.firstTripDate?.atZone(strings.zone)?.year ?: nowYear
                if (first >= nowYear) "$nowYear" else "$first–$nowYear"
            }
            Text(title, Modifier.weight(1f).padding(start = 2.dp), style = AppType.itemValue.copy(fontFeatureSettings = "tnum"), color = colors.text)
            if (state.range != StatsRange.all) listOf(-1 to AppGlyph.chevronLeft, 1 to AppGlyph.chevronRight).forEach { (delta, glyph) ->
                val target = vm.stepTarget(delta)
                Box(Modifier.size(44.dp).alpha(if (target == null) .3f else 1f).clickable(enabled = target != null, role = Role.Button) { haptics.tap(); vm.stepYear(delta) }
                    .semantics { contentDescription = target?.year?.toString().orEmpty() }.testTag(if (delta < 0) "stats_year_back" else "stats_year_forward"), contentAlignment = Alignment.Center) {
                    Glyph(glyph, null, tint = colors.textSecondary, size = 14.dp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(126.dp).semantics { contentDescription = strings.statsKmByMonth(unit) }, verticalAlignment = Alignment.Bottom) {
            (0 until 12).forEach { i ->
                val km = chart.km[i]
                val selected = state.range == StatsRange.month && chart.highlight == i
                val lit = if (state.range == StatsRange.month) selected else true
                val pickable = vm.pickable(i, state)
                val height = if (maxKm > 0 && km > 0) maxOf(4.0, km / maxKm * 104).dp else 4.dp
                Column(Modifier.weight(1f).clickable(enabled = pickable, role = Role.Button) { haptics.selection(); vm.selectMonth(i) }
                    .semantics { this.selected = selected; contentDescription = "${StatsPeriodFormat.monthName(i, strings.language, strings.zone)}, ${Measure.distanceKm(km, unit, strings.language.code, strings)}, ${strings.tripsCount(chart.trips[i])}" }
                    .padding(horizontal = 2.5.dp).testTag("stats_month_$i"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.height(104.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth().height(height).alpha(if (km > 0) 1f else .35f).background(if (lit) AppTheme.accent else dim, RoundedCornerShape(4.dp)))
                    }
                    Text(labels[i], style = inter(11f, if (selected) InterWeight.bold else InterWeight.medium), color = if (selected) colors.text else colors.textSecondary, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            listOf(StatsRange.year to strings.statsRangeYear(), StatsRange.all to strings.statsRangeAll()).forEach { (value, title) ->
                val on = state.range == value
                val interaction = remember { MutableInteractionSource() }
                Box(Modifier.height(44.dp).plainPressFeedback(interaction).clickable(interactionSource = interaction, indication = null, role = Role.Tab) { haptics.tap(); vm.toggleRange(value) }
                    .semantics { this.selected = on }.testTag(if (value == StatsRange.year) "stats_range_year" else "stats_range_all"), contentAlignment = Alignment.Center) {
                    Text(title, Modifier.background(if (on) colors.cardAlt else androidx.compose.ui.graphics.Color.Transparent, CapsuleShape).padding(horizontal = 14.dp, vertical = 7.dp),
                        style = if (on) AppType.chipActive else AppType.chip, color = if (on) colors.text else colors.textSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun RowScope.FactTile(value: String, unit: String?, caption: String, divider: Boolean) {
    val colors = AppTheme.colors
    Row(Modifier.weight(1f)) {
        if (divider) Box(Modifier.width(1.dp).height(44.dp).background(colors.border))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(buildAnnotatedString {
                withStyle(AppType.statValue.toSpanStyle().copy(color = colors.text, fontFeatureSettings = "tnum")) { append(value) }
                if (unit != null) withStyle(AppType.statUnit.toSpanStyle().copy(color = colors.textSecondary)) { append(" $unit") }
            }, maxLines = 1)
            Text(caption.uppercase(appStrings.language.locale), style = AppType.statCaption, letterSpacing = AppType.statCaptionTracking, color = colors.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun RowDivider() = Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(AppTheme.colors.border))

/** Every record that could be sourced; one without data is absent, never a zero. */
private fun records(a: MeAggregates, strings: AppStrings, unit: DistanceUnit, zone: java.time.ZoneId): List<RecordRow> = buildList {
    val code = strings.language.code
    if (a.longestTripKm > 0) add(RecordRow("longest", strings.statsRecordLongest(),
        a.longestTripTitle?.takeIf { it.isNotEmpty() } ?: a.longestTripRegion?.takeIf { it.isNotEmpty() } ?: a.longestTripDate?.let { StatsPeriodFormat.dayMonthShort(it, strings.language, zone) } ?: "—",
        Measure.distanceKm(a.longestTripKm, unit, code, strings), a.longestTripId))
    val bestDay = a.bestDayDate
    if (a.bestDayKm > 0 && bestDay != null) add(RecordRow("bestDay", strings.statsRecordBestDay(unit), StatsPeriodFormat.dayMonth(bestDay, strings.language, zone),
        Measure.distanceKm(a.bestDayKm, unit, code, strings), a.bestDayTripId))
    val place = a.photoPlace
    if (a.photoPlaceCount > 0 && place != null) add(RecordRow("photos", strings.statsRecordMostPhotos(), RegionDisplay.localized(place, strings.language) ?: place, strings.photosCount(a.photoPlaceCount), null))
    // A road driven once is not a champion: «47 раз» needs a repeat.
    val road = a.championRoadName
    if (a.championRoadDrives >= 2 && road != null) add(RecordRow("road", strings.statsRecordChampionRoad(), road, strings.timesCount(a.championRoadDrives), null))
    val farthest = a.farthestPlace
    if (a.farthestKm > 0 && farthest != null) add(RecordRow("farthest", strings.statsRecordFarthest(), farthest, Measure.distanceKm(a.farthestKm, unit, code, strings), a.farthestTripId))
}

/** iOS emptyState: the idle ring, the invitation, «Записать поездку» into the record tab. */
@Composable
private fun EmptyStats(onRecordTrip: (() -> Unit)?) {
    val strings = appStrings
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp).padding(top = 120.dp).testTag("stats_empty"), horizontalAlignment = Alignment.CenterHorizontally) {
        com.onezee.triptrack.ui.IdleRing()
        Spacer(Modifier.height(22.dp))
        Text(strings.statsEmptyTitle(), style = inter(19f, InterWeight.bold), color = colors.text)
        Spacer(Modifier.height(8.dp))
        Text(strings.statsEmptyBody(), style = inter(14f), color = colors.textSecondary, textAlign = TextAlign.Center)
        if (onRecordTrip != null) {
            Spacer(Modifier.height(18.dp))
            Text(strings.recordTripCta(), Modifier.background(AppTheme.accent, RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onRecordTrip)
                .padding(horizontal = 20.dp, vertical = 13.dp).testTag("stats_empty_cta"), style = inter(15f, InterWeight.bold), color = androidx.compose.ui.graphics.Color.White)
        }
    }
}
