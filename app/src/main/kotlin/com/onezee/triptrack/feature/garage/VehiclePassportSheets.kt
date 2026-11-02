// iOS: VehicleDetailView.swift, Drive2LinkSheet.swift, VehiclePrivacySheet.swift
package com.onezee.triptrack.feature.garage

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.me.ProfileTripRow
import com.onezee.triptrack.feature.me.MePhotoViewer
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.logic.Drive2Link
import com.onezee.triptrack.map.*
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.route.*
import com.onezee.triptrack.model.*
import kotlinx.coroutines.launch

@Composable
internal fun VehiclePassportSheets(graph: AppGraph, model: VehiclePassportViewModel, state: VehiclePassportState,
                                    settings: SettingsState, onEdit: () -> Unit, onDelete: () -> Unit, onTrip: (Trip) -> Unit) {
    val vehicle = state.vehicle ?: return
    val strings = appStrings
    val scope = rememberCoroutineScope()
    val auth by graph.auth.state.collectAsState()
    when (state.sheet) {
        VehicleSheet.edit -> LaunchedEffect(Unit) { onEdit() }
        VehicleSheet.delete -> LaunchedEffect(Unit) { onDelete() }
        VehicleSheet.dashboardUnits -> DashboardUnitsPicker(vehicle.dashboardUnits, settings.distanceUnit, vehicle.type == VehicleType.car,
            onSelect = model::setDashboardUnits, onDismiss = model::dismissSheet)
        VehicleSheet.photos -> VehiclePhotoSheet(vehicle.id, graph.me.vehiclePhotos, model::dismissSheet, model::load)
        VehicleSheet.drive2 -> Drive2Sheet(vehicle.drive2URL.orEmpty(), auth.isSignedIn && settings.cloudSyncEnabled,
            onSave = model::setDrive2, onDismiss = model::dismissSheet)
        null -> Unit
        else -> ContentSizedSheet(model::dismissSheet, background = AppTheme.colors.bg, detent = SheetDetent.large) {
            when (state.sheet) {
                VehicleSheet.level -> SettingsDetail(strings.vehicleLevelTitle(), model::dismissSheet) {
                    MeRow(strings.vehicleLevelHowGrows(), subtitle = strings.vehicleLevelHowGrowsBody())
                    MeRow(strings.vehicleLevelAffects(), subtitle = strings.vehicleLevelAffectsBody())
                }
                VehicleSheet.privacy -> SettingsDetail(strings.vehicleWhoSees(), model::dismissSheet) {
                    var busy by remember { mutableStateOf(false) }
                    var failed by remember { mutableStateOf(false) }
                    fun update(show: Boolean = vehicle.visibleToOthers, plate: Boolean = vehicle.plateVisible, map: Boolean = vehicle.mapVisible, photos: Boolean = vehicle.photosVisible) {
                        if (busy) return
                        busy = true
                        scope.launch {
                            try { graph.vehicles.updateVehicleVisibility(vehicle.id, show, plate, map, photos); model.load(); failed = false }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { failed = true }
                            finally { busy = false }
                        }
                    }
                    MeToggle(strings.showVehicleToggle(), vehicle.visibleToOthers, { update(show = it) }, strings.showVehicleHint(), enabled = !busy)
                    MeToggle(strings.plateShowToOthers(), vehicle.plateVisible, { update(plate = it) }, strings.vehiclePlateHint(), enabled = !busy)
                    MeToggle(strings.vehicleShowMap(), vehicle.mapVisible, { update(map = it) }, strings.vehicleShowMapHint(), enabled = !busy)
                    MeToggle(strings.vehicleShowPhotos(), vehicle.photosVisible, { update(photos = it) }, strings.vehicleShowPhotosHint(), enabled = !busy)
                    if (failed) MeRow(strings.garageLoadFailed())
                }
                VehicleSheet.autoRecord -> {
                    var stereos by remember { mutableStateOf(false) }
                    BackHandler(stereos) { stereos = false }
                    if (stereos) StereoPicker(graph, { stereos = false }, vehicle.id)
                    else AutoRecordSettingsContent(graph, settings, model::dismissSheet, vehicle.id, onStereo = { stereos = true })
                }
                VehicleSheet.map -> {
                    MeSheetHeader(strings.vehicleWhereWas()) { dismiss() }
                    val map = remember { MapHostState() }
                    val points = state.passport.routes.flatten()
                    LaunchedEffect(points) { if (points.isNotEmpty()) map.execute(MapCameraCommand.FitRoute(GeoBounds(points.minOf { it.latitude }, points.maxOf { it.latitude }, points.minOf { it.longitude }, points.maxOf { it.longitude }), animated = false)) }
                    MapHost(map, Modifier.weight(1f), routes = MapRoutes(state.passport.routes.map { RouteGeometry.build(it, preview = true) }, RouteAppearance.plain),
                        systemInsets = WindowInsets(0))
                }
                VehicleSheet.trips -> {
                    val trips by produceState<List<Trip>>(emptyList(), vehicle.id) { value = graph.trips.fetchVehicleGarageTrips(vehicle.id) }
                    SettingsDetail(strings.vehicleTripsTitle(), model::dismissSheet) {
                        trips.forEach { trip -> ProfileTripRow(trip, settings.distanceUnit, onClick = { model.dismissSheet(); onTrip(trip) }) }
                    }
                }
                VehicleSheet.tripPhotos -> TripPhotoGrid(state.passport.photos, model::dismissSheet)
                else -> Unit
            }
        }
    }
}

@Composable
internal fun Drive2Sheet(initial: String, syncs: Boolean, onSave: (String) -> Boolean, onDismiss: () -> Unit) {
    val strings = appStrings
    val uri = LocalUriHandler.current
    var input by rememberSaveable { mutableStateOf(initial) }
    val normalized = Drive2Link.normalized(input)
    ContentSizedSheet(onDismiss, background = AppTheme.colors.bg) {
        MeSheetHeader(strings.garageDrive2Title()) { dismiss() }
        Text(strings.garageDrive2Help(), Modifier.padding(horizontal = 16.dp), style = AppType.meta, color = AppTheme.colors.textSecondary)
        MeField(strings.garageDrive2Field(), input, { input = it }, keyboard = KeyboardType.Uri, singleLine = false, tag = "garage_drive2_field")
        Text(if (syncs) strings.garageDrive2PublicNote() else strings.garageDrive2LocalNote(), Modifier.padding(16.dp), style = AppType.caption, color = AppTheme.colors.textSecondary)
        if (normalized != null) MeRow(strings.garageDrive2Title(), onClick = { uri.openUri(normalized) })
        MeAction(strings.save(), { if (onSave(input)) dismiss() }, Modifier.padding(16.dp).testTag("garage_drive2_save"), enabled = input.isBlank() || normalized != null)
        if (initial.isNotBlank()) MeRow(strings.garageDrive2Unlink(), destructive = true, onClick = { if (onSave("")) dismiss() })
    }
}

@Composable
private fun TripPhotoGrid(photos: List<TripPhoto>, onBack: () -> Unit) {
    var selected by remember { mutableStateOf<Pair<List<TripPhoto>, Int>?>(null) }
    MeSheetHeader(appStrings.garageTripPhotos(), onBack)
    LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it.id }) { photo -> VehicleLocalPhoto(photo.filename, Modifier.fillMaxWidth().aspectRatio(1f).clickable { selected = photos.toList() to photos.indexOfFirst { it.id == photo.id } }, directory = "TripPhotos") }
    }
    selected?.let { (pages, start) -> MePhotoViewer(appStrings.garageTripPhotos(), start, pages.size, { selected = null },
        photo = { index -> VehicleLocalPhoto(pages[index].filename, Modifier.fillMaxSize(), ContentScale.Fit, "TripPhotos") }) }
}
