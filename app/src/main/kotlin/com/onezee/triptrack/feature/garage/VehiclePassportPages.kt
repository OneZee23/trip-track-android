// iOS: VehicleLevelInfoView.swift, VehicleTripsView.swift, VehicleMapView.swift, GaragePhotoGrid.swift
package com.onezee.triptrack.feature.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.navigation.tabBarContentClearance
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.me.RoutePreview
import com.onezee.triptrack.feature.me.vehicleLevelColor
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.map.*
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.route.*
import com.onezee.triptrack.model.*

/**
 * iOS GarageSectionLabel's page bar of the garage's pushed pages (the level, the trips, the photos): a bare chevron in a
 * 44 frame, the title in 17 bold over the car's name in 11, an optional bare control at the other end, 48 high, 12 in.
 * The vehicle page and the garage list wear `CustomNavBar`; these pages hang off the vehicle and draw this one.
 */
@Composable
internal fun GaragePageBar(title: String, subtitle: String?, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val colors = AppTheme.colors
    Box(modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp)) {
        Column(Modifier.align(Alignment.Center).padding(horizontal = 56.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, Modifier.semantics { heading() }.testTag("garage_page_title"), style = inter(17f, InterWeight.bold), color = colors.text, maxLines = 1)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = inter(11f), color = colors.textTertiary, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = appStrings.back(), onClick = onBack).testTag("nav_back"), contentAlignment = Alignment.Center) {
                Glyph(AppGlyph.chevronLeft, null, tint = colors.text, size = 19.2.dp)
            }
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** iOS VehicleXPBar: a capsule track in the tint at 16 %, the fill in the tint, never narrower than its own height. */
@Composable
internal fun VehicleXpBar(progress: Double, tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 5.dp) {
    BoxWithConstraints(modifier.fillMaxWidth().height(height).background(tint.copy(alpha = 0.16f), CircleShape)) {
        Box(Modifier.fillMaxHeight().width(maxOf(height, maxWidth * progress.coerceIn(0.0, 1.0).toFloat())).background(tint, CircleShape))
    }
}

/** iOS VehicleLevelPill: «LVL 28» in the pixel face, in the colour of its decade. */
@Composable
internal fun VehicleLevelMark(level: Int, size: Float) {
    Text("LVL $level", style = pressStart2P(size), color = vehicleLevelColor(level), maxLines = 1, softWrap = false)
}

/**
 * iOS VehicleLevelInfoView (pushed from the level pill of the vehicle page): the one place that explains what «LVL 6»
 * means. A card with the number at 40, the bar and the distance to the next level; how it grows and what it changes, in
 * prose; and the eleven colours, one row per decade.
 */
@Composable
internal fun VehicleLevelScreen(level: Int, levelKm: Double, unit: DistanceUnit, onBack: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val code = strings.language.code
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().testTag("vehicle_level_info_screen")) {
        CustomNavBar(strings.vehicleLevelTitle(), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(top = 4.dp, bottom = tabBarContentClearance()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(horizontal = 20.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                VehicleLevelMark(level, 40f)
                VehicleXpBar(VehicleLevelSystem.progressToNext(levelKm, level), vehicleLevelColor(level))
                Text(strings.vehicleLevelToNext(Measure.distanceKm(VehicleLevelSystem.kmToNextLevel(levelKm, level), unit, code, strings), level + 1), style = inter(12f, InterWeight.medium), color = colors.textTertiary, textAlign = TextAlign.Center)
            }
            LevelProse(strings.vehicleLevelHowGrows(), strings.vehicleLevelHowGrowsBody())
            LevelProse(strings.vehicleLevelAffects(), strings.vehicleLevelAffectsBody())
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GarageSectionLabel(strings.vehicleLevelColors())
                Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
                    (0..10).forEach { decade ->
                        if (decade > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.cardAlt))
                        // The first decade starts at 1, there is no level zero; the last has no upper bound to print.
                        val sample = if (decade == 0) 1 else decade * 10
                        val range = if (decade < 10) "$sample–${decade * 10 + 9}" else strings.vehicleLevelAndAbove(sample)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { VehicleLevelMark(sample, 10f) }
                            Text(range, style = inter(12f, InterWeight.medium, monospacedDigits = true), color = colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelProse(title: String, body: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GarageSectionLabel(title)
        Text(body, Modifier.fillMaxWidth(), style = inter(13f), lineHeight = points(13f * InterLineHeight + 3f), color = AppTheme.colors.textSecondary)
    }
}

/** iOS VehicleTripsView.Sort: by date, by length, by climb. */
internal enum class VehicleTripSort { date, distance, elevation }

internal fun List<Trip>.sortedFor(sort: VehicleTripSort): List<Trip> = when (sort) {
    VehicleTripSort.date -> sortedByDescending { it.startDate }
    VehicleTripSort.distance -> sortedByDescending { it.distance }
    VehicleTripSort.elevation -> sortedByDescending { it.elevation }
}

/**
 * iOS VehicleTripsView: «Поездки» over the car's name, a sort control (Дата, Расстояние, Высота), one card of rows with a
 * 38 route thumbnail, the title, the when, the distance with the climb under it and a chevron, and the note that private
 * trips are counted for the owner alone. Every row opens its trip.
 */
@Composable
internal fun VehicleTripsScreen(vehicleName: String, trips: List<Trip>, loaded: Boolean, unit: DistanceUnit, onBack: () -> Unit, onTrip: (Trip) -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    var sort by remember { mutableStateOf(VehicleTripSort.date) }
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().testTag("vehicle_trips_screen")) {
        GaragePageBar(strings.vehicleTripsShort(), vehicleName, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = tabBarContentClearance()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // iOS segments: the track in card-alt, the chosen segment on the card colour, 32 high, fully round.
            Row(Modifier.fillMaxWidth().background(colors.cardAlt, CircleShape).padding(3.dp).testTag("vehicle_trips_sort")) {
                VehicleTripSort.entries.forEach { value ->
                    val label = when (value) { VehicleTripSort.date -> strings.sortByDate(); VehicleTripSort.distance -> strings.sortByDistance(); VehicleTripSort.elevation -> strings.sortByElevation() }
                    val chosen = sort == value
                    Box(Modifier.weight(1f).height(32.dp).then(if (chosen) Modifier.background(colors.card, CircleShape) else Modifier)
                        .clip(CircleShape).clickable(role = Role.Tab) { haptics.tap(); sort = value }.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                        Text(label, style = inter(12f, if (chosen) InterWeight.semibold else InterWeight.medium), color = if (chosen) colors.text else colors.textSecondary, maxLines = 1)
                    }
                }
            }
            val rows = trips.sortedFor(sort)
            if (rows.isEmpty()) Text(if (loaded) strings.vehicleBiographyEmpty() else "", Modifier.fillMaxWidth().padding(top = 24.dp), style = inter(12f), color = colors.textTertiary)
            else Column(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(14.dp)) {
                rows.forEachIndexed { index, trip ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                    CompactTripRow(trip, unit, Modifier.clickable(role = Role.Button) { haptics.tap(); onTrip(trip) }.testTag("vehicle_trip_${trip.id}"), withElevation = true, minHeight = 46.dp) {
                        RoutePreview(trip.previewCoordinates, Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)))
                    }
                }
            }
            Text(strings.vehicleTripsPrivateNote(), Modifier.fillMaxWidth(), style = inter(11f), color = colors.textTertiary)
        }
    }
}

/**
 * iOS VehicleMapView: the car's own map, full screen, with the round back button and a capsule «Где была» over the car's name
 * across the top and, at the bottom, the capsule «4 региона · 7 городов». The map here is the house route map; iOS draws
 * the Atlas map with the fog.
 */
@Composable
internal fun VehicleMapScreen(vehicleName: String, routes: List<List<GeoPoint>>, regionCount: Int, cityCount: Int, onBack: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    Box(Modifier.fillMaxSize().background(colors.bg).testTag("vehicle_map_screen")) {
        val map = remember { MapHostState() }
        val points = remember(routes) { routes.flatten() }
        LaunchedEffect(points) {
            if (points.isNotEmpty()) map.execute(MapCameraCommand.FitRoute(GeoBounds(points.minOf { it.latitude }, points.maxOf { it.latitude }, points.minOf { it.longitude }, points.maxOf { it.longitude }), animated = false))
        }
        MapHost(map, Modifier.fillMaxSize(), routes = MapRoutes(routes.map { RouteGeometry.build(it, preview = true) }, RouteAppearance.plain.withSelectedRouteStyle()), systemInsets = WindowInsets(0))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(colors.card, CircleShape).clickable(role = Role.Button, onClickLabel = strings.back(), onClick = onBack).testTag("nav_back"), contentAlignment = Alignment.Center) {
                Glyph(AppGlyph.chevronLeft, null, tint = colors.text, size = 19.2.dp)
            }
            Column(Modifier.background(colors.card, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(strings.vehicleWhereWas(), style = inter(15f, InterWeight.bold), color = colors.text, maxLines = 1)
                Text(vehicleName, style = inter(11f), color = colors.textTertiary, maxLines = 1)
            }
        }
        Text("$regionCount ${strings.nounRegions(regionCount)} · $cityCount ${strings.nounCities(cityCount)}",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp + tabBarContentClearance()).background(colors.card, CircleShape).padding(horizontal = 14.dp, vertical = 9.dp),
            style = inter(12f, InterWeight.medium), color = colors.text)
    }
}

/**
 * iOS GaragePhotoGrid, a full-screen cover: the bar with the title centred, three columns of cells 112 high with radius 9, 6
 * apart and 14 from the edges. A tap opens the photo viewer on that photo; the caller owns the viewer.
 */
@Composable
internal fun GaragePhotoGridCover(title: String, photos: List<GaragePhoto>, onDismiss: () -> Unit, onOpen: (Int) -> Unit) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        PlainModalWindow()
        Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().navigationBarsPadding().testTag("garage_photo_grid")) {
            CustomNavBar(title, onDismiss, inSheet = false)
            LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
                    PressableCard(onClick = { haptics.tap(); onOpen(index) }, modifier = Modifier.testTag("garage_photo_$index")) {
                        GaragePhotoThumbnail(photo, Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(9.dp)))
                    }
                }
            }
        }
    }
}
