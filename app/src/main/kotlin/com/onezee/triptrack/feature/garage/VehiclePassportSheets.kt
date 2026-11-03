// iOS: VehicleDetailView.swift (the sheets of the vehicle page), Drive2LinkSheet.swift, GaragePhotoGrid.swift
package com.onezee.triptrack.feature.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.me.MePhotoViewer
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.logic.Drive2Link
import com.onezee.triptrack.model.*
import kotlinx.coroutines.launch

@Composable
internal fun VehiclePassportSheets(graph: AppGraph, model: VehiclePassportViewModel, state: VehiclePassportState,
                                    settings: SettingsState, onEdit: () -> Unit, onDelete: () -> Unit, onTrip: (Trip) -> Unit) {
    val vehicle = state.vehicle ?: return
    val auth by graph.auth.state.collectAsState()
    when (state.sheet) {
        VehicleSheet.edit -> LaunchedEffect(Unit) { onEdit() }
        VehicleSheet.delete -> LaunchedEffect(Unit) { onDelete() }
        VehicleSheet.dashboardUnits -> DashboardUnitsPicker(vehicle.dashboardUnits, settings.distanceUnit, vehicle.type == VehicleType.car,
            onSelect = model::setDashboardUnits, onDismiss = model::dismissSheet)
        VehicleSheet.drive2 -> Drive2Sheet(vehicle.drive2URL.orEmpty(), auth.isSignedIn && settings.cloudSyncEnabled,
            onSave = model::setDrive2, onDismiss = model::dismissSheet)
        VehicleSheet.autoRecord -> AutoRecordSheets(graph, settings, vehicle.id, model::dismissSheet)
        VehicleSheet.tripPhotos -> TripPhotosCover(state.passport.photos, model::dismissSheet)
        // The level, the trips, the map and the photos are pages of the stack, not sheets: iOS pushes them.
        VehicleSheet.level, VehicleSheet.trips, VehicleSheet.map, VehicleSheet.photos, null -> Unit
    }
}

/**
 * iOS: the auto-record sheet of the vehicle page, and over it the stereo sheet (BluetoothScanSheet) once the person
 * links one. Two sheets, one over the other, each with its system grabber; the second closes into the first.
 */
@Composable
private fun AutoRecordSheets(graph: AppGraph, settings: SettingsState, vehicleId: java.util.UUID, onDismiss: () -> Unit) {
    var stereos by remember { mutableStateOf(false) }
    ContentSizedSheet(onDismiss, background = AppTheme.colors.bg, detent = SheetDetent.large, grabber = true) {
        AutoRecordSettingsContent(graph, settings, { dismiss() }, vehicleId, onStereo = { stereos = true })
    }
    if (stereos) ContentSizedSheet({ stereos = false }, background = AppTheme.colors.bg, detent = SheetDetent.large, grabber = true) {
        StereoPicker(graph, onBack = { dismiss() }, vehicleId = vehicleId)
    }
}

/** iOS GarageTripPhotos' full-screen cover: the grid of every trip photo of the car and, on a tap, the viewer over it. */
@Composable
private fun TripPhotosCover(photos: List<TripPhoto>, onDismiss: () -> Unit) {
    val strings = appStrings
    var open by remember { mutableStateOf<Int?>(null) }
    val pages = remember(photos) { photos.map { GaragePhoto.Local(it.id.toString(), it.filename) } }
    GaragePhotoGridCover(strings.garageTripPhotos(), pages, onDismiss, onOpen = { open = it })
    open?.let { start ->
        MePhotoViewer(strings.garageTripPhotos(), start, pages.size, { open = null },
            photo = { index -> VehicleLocalPhoto(photos[index].filename, Modifier.fillMaxSize(), ContentScale.Fit, "TripPhotos") })
    }
}

/**
 * iOS Drive2LinkSheet: a white sheet, 20 inside, nothing but the link. The title in 19 bold and a 44 grey close disc; the help
 * in 14; the link in a card-alt box (radius 12, 12 inside, two or three lines) with an accent paste button; under it a status
 * line (green «✓ Ссылка на машину: Volkswagen · Polo» or the hint) and the note about where the link stays; «Сохранить» 52 high;
 * «Отвязать» as centred accent text under it.
 */
@Composable
internal fun Drive2Sheet(initial: String, syncs: Boolean, onSave: (String) -> Boolean, onDismiss: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var input by rememberSaveable { mutableStateOf(initial) }
    val normalized = Drive2Link.normalized(input)
    val paste = androidx.compose.ui.res.stringResource(android.R.string.paste)
    ContentSizedSheet(onDismiss, background = colors.card) {
        // Inside the columns below the sheet's own receiver is hidden by theirs, so the way out is taken here.
        val close = { dismiss() }
        Column(Modifier.fillMaxWidth().padding(20.dp).testTag("drive2_sheet"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.garageDrive2Title(), Modifier.weight(1f), style = inter(19f, InterWeight.bold), color = colors.text)
                Box(Modifier.size(44.dp).background(colors.cardAlt, androidx.compose.foundation.shape.CircleShape).clickable(role = Role.Button) { close() }.semantics { contentDescription = strings.close() }.testTag("drive2_close"), contentAlignment = Alignment.Center) {
                    Glyph(AppGlyph.xmark, null, tint = colors.textSecondary, size = 14.4.dp)
                }
            }
            Text(strings.garageDrive2Help(), style = inter(14f), color = colors.textSecondary)
            Row(Modifier.fillMaxWidth().background(colors.cardAlt, RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (input.isEmpty()) Text(strings.garageDrive2Field(), style = inter(14f), color = colors.textTertiary)
                    BasicTextField(input, { input = it }, Modifier.fillMaxWidth().testTag("garage_drive2_field"), textStyle = inter(14f).copy(color = colors.text), minLines = 2, maxLines = 3,
                        cursorBrush = SolidColor(AppTheme.accent), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                }
                Box(Modifier.size(width = 44.dp, height = 34.dp).background(AppTheme.accent.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClickLabel = paste) {
                        haptics.tap()
                        scope.launch { clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.let { input = it } }
                    }.testTag("drive2_paste"), contentAlignment = Alignment.Center) {
                    Glyph(AppGlyph.clipboard, null, tint = AppTheme.accent, size = 20.dp)
                }
            }
            if (normalized != null) {
                val (make, model) = normalized.substringAfter("/r/").split('/').let { it[0].capitalizedWords() to it[1].capitalizedWords() }
                Text(strings.garageDrive2Valid(make, model), Modifier.testTag("drive2_status"), style = inter(12f), color = AppTheme.green)
            } else Text(strings.garageDrive2Hint(), Modifier.testTag("drive2_status"), style = inter(12f), color = colors.textSecondary)
            Text(if (syncs) strings.garageDrive2PublicNote() else strings.garageDrive2LocalNote(), style = inter(12f), color = colors.textTertiary)
            val valid = normalized != null
            PressableCard(onClick = { if (valid && onSave(input)) { haptics.success(); close() } }, enabled = valid, modifier = Modifier.fillMaxWidth().testTag("garage_drive2_save")) {
                Box(Modifier.fillMaxWidth().height(52.dp).background(AppTheme.accent.copy(alpha = if (valid) 1f else 0.4f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text(strings.save(), style = inter(16f, InterWeight.semibold), color = Color.White)
                }
            }
            if (initial.isNotBlank() && Drive2Link.normalized(initial) != null) PressableCard(onClick = { if (onSave("")) { haptics.tap(); close() } }, modifier = Modifier.fillMaxWidth().testTag("garage_drive2_unlink")) {
                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                    Text(strings.garageDrive2Unlink(), style = inter(14f, InterWeight.semibold), color = AppTheme.accent)
                }
            }
        }
    }
}

/** iOS `capitalized` for the make and the model of a DRIVE2 link: «land-rover» reads «Land-Rover». */
private fun String.capitalizedWords(): String = split('-', '_').joinToString("-") { part -> part.replaceFirstChar { it.titlecase() } }
