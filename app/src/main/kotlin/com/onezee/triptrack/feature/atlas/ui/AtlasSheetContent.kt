// iOS: AtlasSummaryContent.swift, AtlasRegionContent.swift, MyMapSheet.swift (trip and road cards)
package com.onezee.triptrack.feature.atlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.navigation.tabBarContentClearance
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.AtlasTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.atlas.AtlasSheetDetent
import com.onezee.triptrack.feature.atlas.AtlasState
import com.onezee.triptrack.logic.RegionAtlas
import com.onezee.triptrack.logic.distanceKm
import com.onezee.triptrack.logic.distancePartsKm
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.logic.speed
import com.onezee.triptrack.logic.localizedName
import com.onezee.triptrack.logic.regionCount
import com.onezee.triptrack.logic.tripCount
import com.onezee.triptrack.logic.visitedCityCount
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.MapRegionStat
import com.onezee.triptrack.model.MapTripPin
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.ui.RelativeTripDate
import com.onezee.triptrack.ui.TripAutoTitle
import java.time.Instant
import java.util.UUID

/**
 * iOS: AtlasSummaryContent. ONE content for all three positions: the summary shows its top, the list
 * all of it with a scroll inside. Two copies of «Исследовано» would part like the two tab bars did.
 */
@Composable
fun ColumnScope.AtlasSummaryContent(
    state: AtlasState,
    detent: AtlasSheetDetent,
    isOffline: Boolean,
    onShare: () -> Unit,
    onShareUnavailable: () -> Unit,
    onExplain: () -> Unit,
    onOpenRegion: (String) -> Unit,
) {
    when (detent) {
        // In the peek only the pinned title row shows; the sheet draws it. The «189 miles of new
        // roads ▴» row was removed by errata 1: closing the place card brings the summary back.
        AtlasSheetDetent.peek -> Unit
        AtlasSheetDetent.collapsed -> Box(Modifier.padding(horizontal = 16.dp)) {
            ExploredBlock(state, isOffline, onShare, onShareUnavailable, onExplain)
        }
        AtlasSheetDetent.expanded -> {
            val strings = appStrings
            val unit = LocalDistanceUnit.current
            val cities = remember(state.overview, strings.language) { state.overview.sortedCities(strings.language.code).filter { it.wasVisited } }
            val totalCities = state.exploration.regions.sumOf { it.totalCities }
            val fogged = (totalCities - state.overview.cities.size).coerceAtLeast(0)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag("atlas_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = tabBarContentClearance()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "explored") { ExploredBlock(state, isOffline, onShare, onShareUnavailable, onExplain) }
                // No search field and no «Trips in the atlas» row yet, as on iOS: their screens are not
                // built, and a field that searches nothing is a press that does nothing.
                item(key = "regions-header") {
                    SectionHeader(strings.atlasRegions(), strings.formattedCount(state.exploration.regionCount), Modifier.padding(top = 4.dp))
                }
                item(key = "regions") {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(AtlasTheme.cardRadius)).background(AtlasTheme.colors.card)) {
                        state.exploration.regions.forEachIndexed { index, region ->
                            if (index > 0) Box(Modifier.padding(start = 68.dp).fillMaxWidth().height(1.dp).background(AtlasTheme.colors.separator))
                            RegionRow(region, unit, strings) { onOpenRegion(region.id) }
                        }
                    }
                }
                item(key = "cities-header") {
                    SectionHeader(strings.atlasCities(), strings.atlasCitiesOutOf(state.overview.cities.size, totalCities), Modifier.padding(top = 4.dp))
                }
                item(key = "cities") {
                    ChipFlow(Modifier.fillMaxWidth()) {
                        cities.forEach { CityChip(it.stat.localizedName(strings.language.code)) }
                        if (fogged > 0) FogChip(strings.atlasMoreInFog(fogged))
                    }
                }
            }
        }
    }
}

/** «Исследовано», share, the three numbers; the offline line above them and the skeleton while counting. */
@Composable
private fun ExploredBlock(state: AtlasState, isOffline: Boolean, onShare: () -> Unit, onShareUnavailable: () -> Unit, onExplain: () -> Unit) {
    val atlas = AtlasTheme.colors
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The numbers under it stay real: the atlas is counted from the phone's own data.
        if (isOffline) Text(strings.atlasOffline(), Modifier.testTag("atlas_offline_row"), style = AppType.meta, color = atlas.secondary)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // The title opens «how we count»: it has no ⓘ, the spec forbids second captions.
            val interaction = remember { MutableInteractionSource() }
            Box(Modifier.plainPressFeedback(interaction)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); onExplain() }
                .testTag("atlas_explored_title")) {
                AtlasSectionTitle(strings.atlasExplored())
            }
            Spacer(Modifier.weight(1f))
            ShareButton(state.isLoading, isOffline, onShare, onShareUnavailable)
        }
        if (state.isLoading) {
            AtlasStatsSkeleton()
        } else {
            val words = strings.language.code
            val roads = Measure.distancePartsKm(state.revealed.openedKm, unit, words, strings)
            val total = Measure.distancePartsKm(state.exploration.totalKm, unit, words, strings)
            AtlasStatTrio(listOf(
                StatColumn("roads", roads.value, roads.unit, strings.atlasNewRoads(), onExplain),
                StatColumn("total", total.value, total.unit, strings.atlasTotalShort(), onExplain),
                // No action: the trips list is a screen of its own, and a chevron would promise it.
                StatColumn("trips", "${state.exploration.tripCount}", null, strings.tripsGenitive(state.exploration.tripCount)),
            ))
        }
    }
}

/** While counting a circle stands where the button will be; offline it is secondary and explains itself. */
@Composable
private fun ShareButton(loading: Boolean, offline: Boolean, onShare: () -> Unit, onUnavailable: () -> Unit) {
    val atlas = AtlasTheme.colors
    val strings = appStrings
    val haptics = rememberHaptics()
    if (loading) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(36.dp).background(atlas.chip, CircleShape)) }
        return
    }
    PressableCard(
        onClick = { haptics.tap(); if (offline) onUnavailable() else onShare() },
        modifier = Modifier.size(44.dp).semantics { contentDescription = strings.share() }.testTag("atlas_share"),
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp).background(if (offline) atlas.chip else atlas.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                Glyph(AppGlyph.squareAndArrowUp, contentDescription = null, tint = if (offline) atlas.secondary else atlas.accentInk, size = 18.dp)
            }
        }
    }
}

/** A section heading and its count on the right, WITHOUT a chevron: «all regions» is not a screen yet. */
@Composable
fun SectionHeader(title: String, count: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AtlasSectionTitle(title)
        Text(count, style = AppType.meta.tabular(), color = AtlasTheme.colors.secondary, maxLines = 1)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun RegionRow(region: MapRegionStat, unit: DistanceUnit, strings: AppStrings, onOpen: () -> Unit) {
    val atlas = AtlasTheme.colors
    val distance = Measure.distanceKm(region.km, unit, strings.language.code, strings)
    val subtitle = "$distance · ${strings.atlasCitiesOutOf(region.cities.size, region.totalCities)}"
    PressableCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("atlas_region_${region.id}")) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(44.dp).background(atlas.accentSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Glyph(AppGlyph.map, contentDescription = null, tint = atlas.accentInk, size = 21.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(region.localizedName(strings.language.code), style = AppType.itemTitle, color = atlas.ink, maxLines = 1)
                Text(subtitle, style = AppType.meta, color = atlas.secondary, maxLines = 1)
            }
            Glyph(AppGlyph.chevronRight, contentDescription = null, tint = atlas.secondary.copy(alpha = 0.7f), size = 14.dp)
        }
    }
}

/** iOS AtlasRegionContent.subtitle: «Россия · с апреля 2026», where it is and since when it is one's own. */
fun regionSubtitle(region: MapRegionStat, strings: AppStrings): String {
    val country = RegionAtlas.shared.countryName(region.countryCode, strings.language.code) ?: region.countryCode
    val since = region.firstVisited ?: return country
    return "$country · ${strings.mapRegionSince(since)}"
}

/** «вчера · 12.4 км»: when and how far, in the person's unit. */
fun tripLine(trip: MapTripPin, strings: AppStrings, unit: DistanceUnit, now: Instant): String =
    "${RelativeTripDate.string(trip.startDate, now, strings)} · ${Measure.distance(trip.distance, unit, strings.language.code, strings, Measure.Style.tenths)}"

/**
 * iOS: AtlasRegionContent. The region lives in the SAME slot, not in a second sheet: the three
 * numbers, the cities and the trips. The header stands above the scroll, drawn by the sheet.
 */
@Composable
fun ColumnScope.AtlasRegionContent(
    state: AtlasState,
    region: MapRegionStat,
    now: Instant,
    onOpenTrip: (MapTripPin) -> Unit,
    onFocusCity: (com.onezee.triptrack.model.GeoPoint) -> Unit,
) {
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val atlas = AtlasTheme.colors
    var showsAll by rememberSaveable(region.id) { mutableStateOf(false) }
    val trips = state.selectedRegionTrips
    val shown = if (showsAll) trips else trips.take(4)
    val fogged = (region.totalCities - region.visitedCityCount).coerceAtLeast(0)
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth().testTag("atlas_region_page"),
        contentPadding = PaddingValues(start = AtlasTheme.sideInset, end = AtlasTheme.sideInset, top = 12.dp, bottom = tabBarContentClearance()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "stats") {
            val words = strings.language.code
            val opened = Measure.distancePartsKm(region.openedKm, unit, words, strings)
            val total = Measure.distancePartsKm(region.km, unit, words, strings)
            AtlasStatTrio(listOf(
                StatColumn("opened", opened.value, opened.unit, strings.atlasNewRoads()),
                StatColumn("total", total.value, total.unit, strings.atlasTotalShort()),
                StatColumn("trips", "${region.tripCount}", null, strings.tripsGenitive(region.tripCount)),
            ))
        }
        if (region.cities.isNotEmpty() || fogged > 0) {
            item(key = "cities-header") { SectionHeader(strings.atlasCities(), strings.atlasCitiesOutOf(region.visitedCityCount, region.totalCities)) }
            item(key = "cities") {
                // A city chip takes the camera closer; the region stays selected.
                ChipFlow(Modifier.fillMaxWidth()) {
                    region.cities.forEach { city -> CityChip(city.localizedName(strings.language.code)) { onFocusCity(city.coordinate) } }
                    if (fogged > 0) FogChip(strings.atlasMoreInFog(fogged))
                }
            }
        }
        if (trips.isNotEmpty()) {
            item(key = "trips-header") {
                SectionHeader(strings.atlasTripsHere(), "${trips.size}") {
                    if (trips.size > 4 && !showsAll) {
                        val haptics = rememberHaptics()
                        val interaction = remember { MutableInteractionSource() }
                        Row(
                            Modifier.height(28.dp).plainPressFeedback(interaction)
                                .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.selection(); showsAll = true }
                                .testTag("atlas_region_all_trips"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            // «Все», not «Все 51»: the count already stands in the heading beside it.
                            Text(strings.all(), style = AppType.meta, color = atlas.accent)
                            Glyph(AppGlyph.chevronDown, contentDescription = null, tint = atlas.accent, size = 12.dp)
                        }
                    }
                }
            }
            item(key = "trips") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(AtlasTheme.cardRadius)).background(atlas.card)) {
                    shown.forEachIndexed { index, trip ->
                        if (index > 0) Box(Modifier.padding(start = 64.dp).fillMaxWidth().height(1.dp).background(atlas.separator))
                        AtlasTripRow(TripAutoTitle.display(trip.title, trip.startDate, strings), tripLine(trip, strings, unit, now), trip, onOpen = { onOpenTrip(trip) })
                    }
                }
            }
        }
    }
}

/**
 * iOS: MyMapSheet. The card of an object chosen on the map: a trip or a road. The summary and the
 * region live in the slot; this keeps its own panel, as on iOS, because the spec gives these two no
 * state of their own. Heights by number: the trip 176, the road 260, the list up to 86 % of the screen.
 */
@Composable
fun AtlasSelectionPanel(
    trip: MapTripPin?,
    road: List<MapTripPin>?,
    expanded: Boolean,
    maxHeight: Float,
    now: Instant,
    onExpand: (Boolean) -> Unit,
    onSelectTrip: (UUID) -> Unit,
    onOpenTrip: (MapTripPin) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val atlas = AtlasTheme.colors
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val haptics = rememberHaptics()
    val canExpand = road != null && road.size > 2
    val base = if (trip != null) TRIP_PANEL else ROAD_PANEL
    val listHeight = road?.let { 64f + it.size * 52f } ?: base
    val height = (if (expanded && canExpand) minOf(maxOf(listHeight, base), maxHeight * 0.86f) else base) + tabBarContentClearance().value
    val shape = RoundedCornerShape(topStart = AtlasTheme.sheetRadius, topEnd = AtlasTheme.sheetRadius)
    Column(
        modifier.fillMaxWidth().height(height.dp)
            .iosShadow(shape, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.16f), radius = 20.dp, y = (-6).dp)
            .background(atlas.background, shape)
            .verticalFlick(onUp = { if (canExpand && !expanded) { haptics.selection(); onExpand(true) } },
                onDown = { haptics.tap(); if (expanded) onExpand(false) else onClose() })
            .testTag(if (trip != null) "mymap_trip_card" else "mymap_road_card"),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 5.dp).background(atlas.handle, RoundedCornerShape(3.dp)).testTag("mymap_grabber"))
                }
                if (trip != null) {
                    TripCard(trip, strings, unit, now) { haptics.action(); onOpenTrip(trip) }
                } else if (road != null) {
                    Row(Modifier.padding(start = 16.dp, end = 60.dp, top = 4.dp, bottom = 6.dp)) {
                        Text(strings.mapRoadTrips(road.size), style = inter(17f, InterWeight.semibold), color = atlas.ink, maxLines = 1)
                    }
                    val shown = if (expanded) road else road.take(2)
                    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        itemsIndexed(shown, key = { _, it -> it.id }) { _, item ->
                            AtlasTripRow(TripAutoTitle.display(item.title, item.startDate, strings), tripLine(item, strings, unit, now), item,
                                onOpen = { onSelectTrip(item.id) }, testTag = "mymap_road_trip")
                        }
                    }
                    if (!expanded && road.size > 2) {
                        Text(strings.mapRoadPullHint(), Modifier.fillMaxWidth().padding(top = 10.dp), style = inter(12f), color = atlas.secondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp)) {
                CircleButton(AppGlyph.xmark, strings.closeSheet(), "mymap_close", diameter = 36.dp, onClick = onClose)
            }
        }
    }
}

/** iOS MyMapSheet.dragGesture: up past 50 pt expands, down past 60 folds or closes. */
@Composable
private fun Modifier.verticalFlick(onUp: () -> Unit, onDown: () -> Unit): Modifier {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var total by remember { mutableStateOf(0f) }
    return draggable(
        state = rememberDraggableState { delta -> total += with(density) { delta.toDp().value } },
        orientation = Orientation.Vertical,
        onDragStarted = { total = 0f },
        onDragStopped = {
            when {
                total < -50 -> onUp()
                total > 60 -> onDown()
            }
        },
    )
}

private const val TRIP_PANEL = 176f
private const val ROAD_PANEL = 260f

@Composable
private fun TripCard(trip: MapTripPin, strings: AppStrings, unit: DistanceUnit, now: Instant, onOpen: () -> Unit) {
    val atlas = AtlasTheme.colors
    val words = strings.language.code
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 60.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        MapTripThumb(trip, 56.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(TripAutoTitle.display(trip.title, trip.startDate, strings), style = inter(16f, InterWeight.bold), color = atlas.ink, maxLines = 1)
            val headline = listOf(RelativeTripDate.string(trip.startDate, now, strings),
                Measure.distance(trip.distance, unit, words, strings, Measure.Style.grouped), humanTime(trip.duration, strings)).joinToString(" · ")
            Text(headline, style = inter(12f), color = atlas.secondary, maxLines = 1)
            val speeds = "${strings.myMapAvg()} ${Measure.speed(trip.avgSpeedMS, unit, words, strings)} · ${strings.myMapMax()} ${Measure.speed(trip.maxSpeedMS, unit, words, strings)}"
            Text(speeds, style = inter(12f), color = atlas.secondary, maxLines = 1)
        }
    }
    PressableCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp).testTag("mymap_open_trip")) {
        Box(Modifier.fillMaxWidth().background(atlas.accent, RoundedCornerShape(50)).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(strings.mapOpenTrip(), style = inter(15f, InterWeight.bold), color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
        }
    }
}

/** iOS Trip.formattedTimeHuman: «2 ч 10 мин», «18 мин 5 сек», «40 сек». */
fun humanTime(seconds: Double, strings: AppStrings): String {
    val total = maxOf(0L, seconds.toLong())
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "$h ${strings.hoursUnitShort()} $m ${strings.minutesUnitShort()}"
        m > 0 -> if (s > 0) "$m ${strings.minutesUnitShort()} $s ${strings.secondsUnitShort()}" else "$m ${strings.minutesUnitShort()}"
        else -> "$s ${strings.secondsUnitShort()}"
    }
}
