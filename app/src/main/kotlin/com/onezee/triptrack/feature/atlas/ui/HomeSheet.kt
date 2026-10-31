// iOS: HomeSheet.swift, HomePickerMapView.swift
package com.onezee.triptrack.feature.atlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppConfirm
import com.onezee.triptrack.designsystem.components.AppDialogAction
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.SheetDetent
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.AtlasTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.ContinuousRoundedRectangle
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.settings.HomeState
import com.onezee.triptrack.logic.radius
import com.onezee.triptrack.map.MapHost
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.map.marker.MapMarker
import com.onezee.triptrack.map.route.MapRoutes
import com.onezee.triptrack.map.route.RouteAppearance
import com.onezee.triptrack.map.route.RouteGeometry
import com.onezee.triptrack.model.GeoBounds
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.HomeSettings
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.ui.LocalLiveMaps
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * iOS: HomeSheet. Everything about home on one screen: place the point, show the pin or not, the
 * radius, trim public tracks or not, remove home. The «Вид карты» sheet carries one summary row:
 * a switch there would toggle what may not exist yet.
 */
@Composable
fun HomeSheet(home: HomeState, fallback: GeoPoint?, onChange: (HomeState) -> Unit, onDismissRequest: () -> Unit) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    var confirmingRemove by rememberSaveable { mutableStateOf(false) }
    ContentSizedSheet(onDismissRequest = onDismissRequest, background = atlas.background, detent = SheetDetent.large) {
        val sheet = this
        Column(Modifier.fillMaxWidth().testTag("home_sheet")) {
            AtlasControlsHeader(strings.homeSheetTitle(), onClose = { sheet.dismiss() })
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeMapCard(home, fallback) { onChange(home.copy(coordinate = it)) }
                if (home.coordinate != null) {
                    HomeCard {
                        ToggleRow(strings.homeShowOnMap(), home.showsOnMap, "home_shows_toggle") { onChange(home.copy(showsOnMap = it)) }
                        Note(strings.homeVisibleOnlyToYou())
                    }
                    HomeCard {
                        AtlasSectionTitle(strings.homeRadiusTitle())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (step in HomeSettings.Radius.entries) RadiusChip(step, home.radius == step, Modifier.weight(1f)) { onChange(home.copy(radius = step)) }
                        }
                    }
                    HomeCard {
                        ToggleRow(strings.homeZoneToggle(), home.trimsPublicTracks, "home_zone_toggle") { onChange(home.copy(trimsPublicTracks = it)) }
                        // The price is named BEFORE the press: switching it on re-sends every public trip.
                        Note(strings.homeZoneNote())
                    }
                    val haptics = rememberHaptics()
                    PressableCard(onClick = { haptics.tap(); confirmingRemove = true }, modifier = Modifier.fillMaxWidth().testTag("home_remove")) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
                            Text(strings.homeRemove(), style = AppType.action, color = AppTheme.red)
                        }
                    }
                }
            }
        }
        // A re-send happens only with the zone on (activeZone): promising one without it would
        // frighten with work that will not be done.
        AppConfirm(
            isPresented = confirmingRemove,
            onDismissRequest = { confirmingRemove = false },
            title = strings.homeRemoveTitle(),
            message = if (home.activeZone != null) strings.homeRemoveBody() else strings.homeRemoveBodyNoZone(),
            actions = listOf(AppDialogAction(strings.homeRemove(), AppDialogAction.Kind.destructive) {
                onChange(HomeState(radius = home.radius))
            }),
        )
    }
}

@Composable
private fun HomeCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(AtlasTheme.colors.card, ContinuousRoundedRectangle(AtlasTheme.cardRadius)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun Note(text: String) {
    Text(text, Modifier.fillMaxWidth(), style = AppType.meta, color = AtlasTheme.colors.secondary)
}

@Composable
private fun RadiusChip(step: HomeSettings.Radius, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val atlas = AtlasTheme.colors
    val strings = appStrings
    val haptics = rememberHaptics()
    PressableCard(onClick = { haptics.tap(); onClick() }, modifier = modifier.selectedSemantics(on).testTag("home_radius_${step.rawValue}")) {
        Box(Modifier.fillMaxWidth().height(36.dp).background(if (on) atlas.accent else atlas.chip, ContinuousRoundedRectangle(10.dp)),
            contentAlignment = Alignment.Center) {
            Text(Measure.radius(step.rawValue.toDouble(), LocalDistanceUnit.current, strings.language.code, strings),
                style = if (on) AppType.chipActive else AppType.chip, color = if (on) androidx.compose.ui.graphics.Color.White else atlas.ink, maxLines = 1)
        }
    }
}

/**
 * iOS: HomePickerMapView. A tap puts home where the finger is; the ring shows the radius. The camera
 * follows a MOVE of home, never a change of radius: the radius is chosen looking at the ring.
 */
@Composable
private fun HomeMapCard(home: HomeState, fallback: GeoPoint?, onPlace: (GeoPoint) -> Unit) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    val density = LocalDensity.current.density
    val haptics = rememberHaptics()
    Box(Modifier.fillMaxWidth().height(240.dp).clip(ContinuousRoundedRectangle(AtlasTheme.cardRadius)).background(atlas.fog).testTag("home_map")) {
        if (LocalLiveMaps.current) {
            val mapState = remember { MapHostState() }
            val radius = home.radius.rawValue.toDouble()
            val start = home.coordinate ?: fallback
            LaunchedEffect(home.coordinate) {
                val centre = home.coordinate ?: start ?: return@LaunchedEffect
                mapState.execute(MapCameraCommand.FitRoute(around(centre, radius * 3), MapInsets.Zero, animated = home.coordinate != null))
            }
            val house = remember(density) { homeGlyphBitmap(density) }
            val markers = home.coordinate?.let { listOf(MapMarker("home_pin_picker", it, MapMarker.Kind.HOME, image = house)) }.orEmpty()
            val ring = remember(home.coordinate, radius) {
                home.coordinate?.let { centre ->
                    MapRoutes(listOf(RouteGeometry.build(circle(centre, radius), preview = true)),
                        RouteAppearance(2.0, null, lineColorOverride = worldArgb(0xC8, 0x47, 0x2D, 0.7), showsEndpoints = false))
                }
            }
            MapHost(mapState, Modifier.fillMaxSize(), routes = ring, markers = markers, onMapTap = { haptics.tap(); onPlace(it.coordinate) },
                showsUserLocation = true)
        }
        // The hint stands only while there is no home: it explains the screen's one action.
        if (home.coordinate == null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(12.dp).background(atlas.glass, CapsuleShape).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(strings.homePlaceHint(), style = AppType.meta, color = atlas.ink)
            }
        }
    }
}

private fun around(centre: GeoPoint, metres: Double): GeoBounds {
    val dLat = metres / 111_320.0
    val dLon = metres / (111_320.0 * maxOf(0.01, cos(centre.latitude * PI / 180)))
    return GeoBounds(centre.latitude - dLat, centre.latitude + dLat, centre.longitude - dLon, centre.longitude + dLon)
}

/** The radius as a ring of 64 points: MapLibre has no circle in metres. */
private fun circle(centre: GeoPoint, metres: Double): List<GeoPoint> = (0..64).map { i ->
    val angle = 2 * PI * i / 64
    val dLat = metres * cos(angle) / 111_320.0
    val dLon = metres * sin(angle) / (111_320.0 * maxOf(0.01, cos(centre.latitude * PI / 180)))
    GeoPoint(centre.latitude + dLat, centre.longitude + dLon)
}
