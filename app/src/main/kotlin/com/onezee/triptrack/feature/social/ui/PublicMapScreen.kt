// iOS: PublicMapView.swift (body, header, sheet, regionPanel, tripsPanel, summary, summaryColumns)
package com.onezee.triptrack.feature.social.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.atlas.AtlasCameraAction
import com.onezee.triptrack.feature.atlas.AtlasSelection
import com.onezee.triptrack.feature.atlas.ui.*
import com.onezee.triptrack.feature.feed.asTrip
import com.onezee.triptrack.feature.social.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.model.*
import com.onezee.triptrack.ui.TripAutoTitle
import com.onezee.triptrack.ui.navigation.DestinationNavigator
import com.onezee.triptrack.ui.navigation.entryViewModel

/**
 * iOS: PublicMapView. Not a second map: the Atlas's own map and sheet with someone's public trips
 * as the input. A region opens in the one sheet with its numbers and trips, a road lists its
 * trips, and a trip is asked from the server and opened on top, as a record in their statistics.
 * Only «back» stands over the map: the name is the sheet's first row.
 */
@Composable
fun PublicMapScreen(dest: TripsDestination.PublicMap, social: SocialFeature, navigator: DestinationNavigator<TripsDestination>, model: PublicMapViewModel? = null) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    val haptics = rememberHaptics()
    val vm = model ?: entryViewModel("map:${dest.id}:${dest.vehicleId}") { PublicMapViewModel(dest.id, dest.vehicleId, social.api, social.worker) }
    val state by vm.state.collectAsState()
    val failed by vm.failed.collectAsState()
    val opening by vm.openingTripId.collectAsState()
    var toast by remember { mutableStateOf<ToastItem?>(null) }
    val mapState = remember { MapHostState() }
    var sheetHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val now = social.clock()
    HideAppTabBar()
    BackHandler(enabled = state.selection != null) { vm.closeSelection() }

    // Where the camera looks while everything loads: the profile's latest trips (0.9.1).
    LaunchedEffect(Unit) { dest.start?.let { mapState.execute(MapCameraCommand.Fit(it, MapCameraCommand.Padding.overview, animated = false)) } }
    LaunchedEffect(state.cameraRevision) {
        val action = state.camera ?: return@LaunchedEffect
        if (action is AtlasCameraAction.Command) mapState.execute(action.command)
        vm.acknowledgeCamera(state.cameraRevision)
    }
    fun open(trip: MapTripPin) = vm.openTrip(trip.id,
        onOpened = { item -> navigator.push(TripsDestination.TripDetail(item.id, preview = item.asTrip(), social = item)) },
        onFailed = { toast = ToastItem(ToastType.error, strings.tripLoadFailed()) })

    Box(Modifier.fillMaxSize().background(atlas.background).testTag("public_map")) {
        val routes = remember(state.exploration) { RouteIndex.build(state.exploration.trips) }
        AtlasMapLayer(state, mapState, MapInsets(bottom = with(density) { sheetHeight.toDp().value.toDouble() }), mapState.camera?.latitudeSpan ?: 0.0, strings,
            onMarker = { marker -> tripIdOf(marker)?.let { vm.selectRoad(listOf(it)) } }, onTap = { tap ->
                when (val meaning = AtlasMapTap.resolve(tap, mapState.camera?.latitudeSpan ?: 0.0, routes)) {
                    is AtlasMapTap.Road -> { haptics.tap(); vm.selectRoad(meaning.tripIds) }
                    is AtlasMapTap.Ground -> vm.tapMap(meaning.point, meaning.zoomToRegion)
                }
            })
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            NavCircleButton(AppGlyph.chevronLeft, strings.back(), { navigator.pop() }, Modifier.testTag("public_map_back"))
        }
        val region = state.selectedRegion
        val selectedTrips = state.selectedTrip?.let { listOf(it) } ?: state.selectedRoad
        val screen = LocalConfiguration.current.screenHeightDp
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .clip(RoundedCornerShape(topStart = AtlasTheme.sheetRadius, topEnd = AtlasTheme.sheetRadius)).background(atlas.background)
            .onGloballyPositioned { sheetHeight = it.size.height }.navigationBarsPadding()
            .testTag(when { region != null -> "public_map_region"; selectedTrips != null -> "public_map_trips"; else -> "public_map_summary" })) {
            when {
                region != null -> Column(Modifier.height(minOf(440f, screen * .55f).dp)) {
                    Spacer(Modifier.height(12.dp))
                    AtlasSubstateHeader(region.localizedName(strings.language.code), regionSubtitle(region, strings), onBack = null, onClose = vm::closeSelection)
                    AtlasRegionContent(state, region, now, onOpenTrip = ::open, onFocusCity = vm::focusCity)
                }
                selectedTrips != null -> Column(Modifier.height(PublicMapStart.tripsPanelHeight(selectedTrips.size).dp)) {
                    Spacer(Modifier.height(12.dp))
                    AtlasSubstateHeader(strings.atlasTripsHere(), "${selectedTrips.size} ${strings.nounTrips(selectedTrips.size)}", onBack = null, onClose = vm::closeSelection)
                    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = AtlasTheme.sideInset, vertical = 12.dp).clip(RoundedCornerShape(AtlasTheme.cardRadius)).background(atlas.card),
                        userScrollEnabled = selectedTrips.size > PublicMapStart.VISIBLE_TRIP_ROWS) {
                        itemsIndexed(selectedTrips, key = { _, trip -> trip.id }) { index, trip ->
                            if (index > 0) Box(Modifier.padding(start = 64.dp).fillMaxWidth().height(1.dp).background(atlas.separator))
                            AtlasTripRow(TripAutoTitle.display(trip.title, trip.startDate, strings), tripLine(trip, strings, LocalDistanceUnit.current, now), trip,
                                onOpen = { if (opening == null) open(trip) })
                        }
                    }
                }
                else -> Summary(dest.name, state, failed, onRetry = { vm.load() })
            }
        }
        ToastHost(toast, onDismiss = { toast = null })
    }
}

@Composable
private fun Summary(name: String?, state: com.onezee.triptrack.feature.atlas.AtlasState, failed: Boolean, onRetry: () -> Unit) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    val unit = LocalDistanceUnit.current
    Box(Modifier.padding(top = 4.dp).testTag("public_map_title")) { AtlasSheetTitleRow(name?.trim()?.takeIf { it.isNotEmpty() } ?: strings.profileMapEntryTitle()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = AtlasTheme.sideInset).padding(top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            // Otherwise the summary states «0 маршрутов · 0 км» about another person all through the load.
            state.isLoading -> {
                Box(Modifier.fillMaxWidth().background(atlas.card, RoundedCornerShape(AtlasTheme.cardRadius)).padding(16.dp).testTag("public_map_skeleton")) { AtlasStatsSkeleton() }
                Text(strings.publicMapOnlyPublic(), style = AppType.meta, color = atlas.secondary)
            }
            // A failed load is NOT «nothing published»: that would be a claim about someone we did not check.
            failed && state.exploration.trips.isEmpty() -> {
                Text(strings.publicDataLoadFailed(), style = inter(14f, InterWeight.semibold), color = atlas.ink)
                Text(strings.retry(), Modifier.clickable(role = Role.Button, onClick = onRetry).testTag("public_map_retry"), style = inter(13f, InterWeight.bold), color = AppTheme.accent)
            }
            state.isEmpty -> {
                Text(strings.publicMapEmptyTitle(), style = inter(14f, InterWeight.semibold), color = atlas.ink)
                Text(strings.publicMapEmptyBody(), style = inter(11.5f), color = atlas.secondary, textAlign = TextAlign.Center)
            }
            else -> {
                if (failed) Text(strings.publicDataPartial(), style = AppType.meta, color = atlas.secondary)
                val trips = state.exploration.trips.size
                val distance = Measure.distancePartsKm(state.exploration.totalKm, unit, strings.language.code, strings)
                val regions = state.exploration.regions.size
                AtlasStatTrio(buildList {
                    add(StatColumn("trips", "$trips", null, strings.tripsGenitive(trips)))
                    add(StatColumn("distance", distance.value, distance.unit, strings.atlasTotalShort()))
                    if (!PublicMapStart.hidesRegions(regions, trips)) add(StatColumn("regions", "$regions", null, strings.nounRegions(regions)))
                })
                Text(strings.publicMapOnlyPublic(), style = AppType.meta, color = atlas.secondary, modifier = Modifier.testTag("public_map_only_public"))
            }
        }
    }
}
