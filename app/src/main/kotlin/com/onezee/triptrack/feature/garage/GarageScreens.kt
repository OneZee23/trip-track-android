// iOS: GarageView.swift, VehicleDetailView.swift, GarageComponents.swift
package com.onezee.triptrack.feature.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.navigation.tabBarContentClearance
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.me.vehicleLevelColor
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.util.UUID

/**
 * iOS: GarageView.emptyState. A drawn empty carport rather than a car: it says «nothing parked yet»,
 * where a car would say «here is a car». The title and its line, then the screen's one action as a
 * full-width accent capsule. The block sits a notch above the centre and scrolls when a large font
 * no longer lets it fit, so «Добавить машину» never leaves the screen first.
 */
@Composable
private fun GarageEmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .padding(horizontal = 32.dp).padding(bottom = 48.dp).testTag("garage_empty"),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            com.onezee.triptrack.ui.EmptyStateIllustration(com.onezee.triptrack.R.drawable.empty_garage, size = 148.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.garageEmptyTitle(), style = inter(18f, InterWeight.bold), color = colors.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(strings.garageEmptyBody(), style = inter(14f), color = colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Text(strings.addVehicleTitle(), Modifier.padding(top = 6.dp).fillMaxWidth().background(AppTheme.accent, CapsuleShape)
                .clickable(role = Role.Button) { haptics.tap(); onAdd() }.padding(vertical = 15.dp).testTag("garage_empty_add"),
                style = inter(16f, InterWeight.bold), color = androidx.compose.ui.graphics.Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
internal fun GarageScreen(state: GarageState, unit: DistanceUnit, onBack: () -> Unit, onAdd: () -> Unit,
                          onVehicle: (UUID) -> Unit, onSelect: (UUID) -> Unit, onDelete: (UUID) -> Unit, onUnsell: (UUID) -> Unit = {}) {
    val strings = appStrings
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding().testTag("garage_screen")) {
        // iOS GarageView.navRow sets navBarInSheet whether the list is pushed or presented.
        CustomNavBar(strings.garage(), onBack, inSheet = true) {
            MeNavAddButton(strings.addVehicleTitle(), state.vehicles.count { it.vehicle.soldAt == null } < 5, onAdd)
        }
        if (state.loaded && state.vehicles.isEmpty()) {
            GarageEmptyState(onAdd, Modifier.weight(1f))
        } else LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = tabBarContentClearance()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.failed) item { MeRow(strings.garageLoadFailed()) }
            if (state.active != null) {
                item { GarageSectionLabel(strings.garageActive()) }
                item(key = state.active.vehicle.id) { GarageVehicleCard(state.active, true, unit, onVehicle, onSelect, onDelete, onUnsell) }
                item { Text(strings.garageActiveHint(), style = inter(11f), color = AppTheme.colors.textTertiary) }
            }
            if (state.others.isNotEmpty()) {
                item { GarageSectionLabel(strings.garageOthers(), Modifier.padding(top = 4.dp)) }
                items(state.others, key = { it.vehicle.id }) { GarageVehicleCard(it, false, unit, onVehicle, onSelect, onDelete, onUnsell) }
                item { Text(if (state.active == null) strings.garageNoActiveHint() else strings.garageArchiveHint(), style = inter(11f), color = AppTheme.colors.textTertiary) }
            }
            if (state.sold.isNotEmpty()) {
                item { GarageSectionLabel(strings.garageSold(), Modifier.padding(top = 4.dp)) }
                items(state.sold, key = { it.vehicle.id }) { GarageVehicleCard(it, false, unit, onVehicle, onSelect, onDelete, onUnsell) }
                item { Text(strings.garageSoldHint(), style = inter(11f), color = AppTheme.colors.textTertiary) }
            }
            if (state.vehicles.count { it.vehicle.soldAt == null } >= 5) item { Text(strings.maxVehiclesHint(), Modifier.fillMaxWidth().padding(top = 4.dp), style = inter(12f), color = AppTheme.colors.textTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

/**
 * iOS GarageView.vehicleCard: the silhouette on its plate beside the name (16 bold, a lock when others cannot see the car, a
 * «Продана» capsule, a check on the main one), the plate chip alone (no make and model line), the level bar in the colour
 * of its decade with «LVL 6» in the pixel face, and two facts with their icons: the odometer and the trips, 15 bold. The main
 * vehicle wears the accent wash and a 2 pt frame. A hold opens the actions.
 */
@Composable
private fun GarageVehicleCard(item: GarageVehicle, active: Boolean, unit: DistanceUnit, onVehicle: (UUID) -> Unit, onSelect: (UUID) -> Unit, onDelete: (UUID) -> Unit, onUnsell: (UUID) -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val vehicle = item.vehicle
    val sold = vehicle.soldAt != null
    var actions by remember { mutableStateOf(false) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    Box {
        PressableCard(onClick = { onVehicle(vehicle.id) }, modifier = Modifier.fillMaxWidth().testTag("garage_vehicle_${vehicle.id}"), response = CardPressResponse.hold,
            onLongClick = { actions = true }, onLongClickLabel = strings.moreActions()) {
            Column(Modifier.fillMaxWidth().surfaceCard(16.dp).then(if (active) Modifier.background(AppTheme.accentBg, shape) else Modifier).clip(shape)) {
                item.mainPhoto?.let { VehicleLocalPhoto(it.filename, Modifier.fillMaxWidth().height(150.dp).alpha(if (sold) .55f else 1f), remoteURL = it.remoteURL) }
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.alpha(if (sold) .55f else 1f)) { VehicleSpritePlate(vehicle) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(vehicle.name.ifBlank { strings.unnamedVehicle() }, Modifier.weight(1f, fill = false), style = inter(16f, InterWeight.bold), color = colors.text, maxLines = 1)
                            if (!vehicle.visibleToOthers) Glyph(AppGlyph.lockFill, strings.vehicleHiddenFromOthers(), size = 13.2.dp, tint = colors.textTertiary)
                            if (sold) Text(strings.vehicleSoldBadge(), Modifier.background(colors.cardAlt, CapsuleShape).padding(horizontal = 7.dp, vertical = 3.dp), style = inter(10f, InterWeight.bold), color = colors.textTertiary)
                            Spacer(Modifier.weight(1f))
                            if (active) Glyph(AppGlyph.checkmark, strings.vehicleMainLabel(), size = 18.dp, tint = AppTheme.accent)
                        }
                        // iOS identityLine: the plate and nothing else; the kilometres live in the facts below.
                        if (vehicle.plate.isNotBlank()) VehiclePlateChip(vehicle.plate)
                        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            VehicleXpBar(vehicle.progressToNextLevel, vehicleLevelColor(vehicle.level), Modifier.weight(1f))
                            VehicleLevelMark(vehicle.level, 9f)
                        }
                        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            GarageFact(AppGlyph.mappin, Measure.odometer(vehicle.displayOdometerKm, vehicle.dashboardUnit(unit), strings.language.code, strings))
                            if (item.tripCount > 0) GarageFact(AppGlyph.flagCheckered, "${item.tripCount} " + strings.nounTrips(item.tripCount))
                        }
                    }
                }
            }
        }
        // iOS strokeBorder: the whole 2 pt line stays inside the card.
        if (active) Box(Modifier.matchParentSize().border(2.dp, AppTheme.accent, shape))
        val choices = buildList {
            if (!active && !sold) add(ActionPopoverItem(strings.makeMainVehicle(), AppGlyph.checkmark) { onSelect(vehicle.id) })
            if (sold) add(ActionPopoverItem(strings.vehicleUnsell(), AppGlyph.arrowUturnBackwardCircleFill) { onUnsell(vehicle.id) })
            add(ActionPopoverItem(strings.deleteVehicle(), AppGlyph.trash, true) { onDelete(vehicle.id) })
        }
        ActionPopover(actions, { actions = false }, choices)
    }
}

/** iOS GarageView.fact: an 11 semibold icon in the tertiary ink and the text in 15 bold with tabular digits. */
@Composable
private fun GarageFact(glyph: AppGlyph, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(glyph, null, tint = AppTheme.colors.textTertiary, size = 13.2.dp)
        Text(text, style = inter(15f, InterWeight.bold, monospacedDigits = true), color = AppTheme.colors.text, maxLines = 1)
    }
}

@Composable
internal fun VehiclePassportScreen(state: VehiclePassportState, appUnit: DistanceUnit, onBack: () -> Unit,
                                   onSheet: (VehicleSheet) -> Unit, onSelect: () -> Unit, onTrip: (Trip) -> Unit,
                                   stereo: StereoStatus = StereoStatus.NotLinked, autoRecordOn: Boolean = false, onBluetoothSettings: () -> Unit = {}) {
    val strings = appStrings
    val vehicle = state.vehicle
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding().testTag("vehicle_passport")) {
        CustomNavBar(vehicle?.name ?: strings.myVehicle(), onBack) {
            var actions by remember { mutableStateOf(false) }
            Box {
                NavCircleButton(AppGlyph.ellipsis, strings.moreActions(), { actions = true }, Modifier.testTag("garage_vehicle_actions"))
                ActionPopover(actions, onDismissRequest = { actions = false }, items = listOf(
                    ActionPopoverItem(strings.edit(), AppGlyph.pencil) { onSheet(VehicleSheet.edit) },
                    ActionPopoverItem(strings.garageDrive2Title(), AppGlyph.squareAndArrowUp) { onSheet(VehicleSheet.drive2) },
                    *if (vehicle?.soldAt == null) arrayOf(ActionPopoverItem(strings.makeMainVehicle(), AppGlyph.checkmark) { onSelect() }) else emptyArray(),
                    ActionPopoverItem(strings.deleteVehicle(), AppGlyph.trash, true) { onSheet(VehicleSheet.delete) },
                ))
            }
        }
        if (vehicle == null) {
            Text(if (state.failed) strings.garageLoadFailed() else if (state.loaded) strings.vehicleDeletedNote() else strings.loadingGeneric(), Modifier.padding(16.dp), style = AppType.body, color = AppTheme.colors.textSecondary)
            return@Column
        }
        // iOS: the bar stays on the vehicle page, and the last row clears it. 14 from the sides, 4 under the bar.
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(top = 4.dp, bottom = tabBarContentClearance()).testTag("vehicle_passport_scroll"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val width = vehicleStageWidth()
            VehicleHeroCard(
                name = vehicle.name.ifBlank { strings.unnamedVehicle() }, make = vehicle.make, model = vehicle.model, year = vehicle.year, bodyType = vehicle.bodyType,
                variant = state.catalogVariant, tags = VehicleModTags.effective(vehicle.modTags, isPlus = false),
                soldLine = vehicle.soldAt?.let { strings.vehicleSoldState(monthYearPrepositional(it, strings)) }, plate = vehicle.plate,
                level = vehicle.level, onLevel = { onSheet(VehicleSheet.level) },
                stats = VehicleHeroStats(state.passport.tripCount, state.passport.regionCount, state.passport.daysOnRoad, ready = state.loaded),
                since = com.onezee.triptrack.feature.stats.StatsPeriodFormat.monthYearGenitive(vehicle.createdAt, strings.language, strings.zone), foreign = false,
            ) {
                Box(Modifier.clickable(role = Role.Button, onClickLabel = strings.vehiclePhotos()) { onSheet(VehicleSheet.photos) }) {
                    VehicleStage(vehicle, width = width, photoFilename = state.mainPhoto?.filename, remoteURL = state.mainPhoto?.remoteURL)
                }
            }
            Drive2LogbookRow(vehicle.drive2URL, onConnect = if (Drive2Audience.invitesOnPage(strings.language.code)) ({ onSheet(VehicleSheet.drive2) }) else null)
            GarageMileageCard(vehicle.displayOdometerKm, vehicle.odometerKm, state.passport.years, state.dashboardUnit) { onSheet(VehicleSheet.edit) }
            if (state.passport.routes.isNotEmpty()) VehicleWhereWasCard(state.passport.routes, state.passport.regions, state.passport.regionCount, state.passport.cityCount,
                onOpen = { onSheet(VehicleSheet.map) }, wholeCardOpens = false)
            GarageTripPhotos(state.passport.photos.map { GaragePhoto.Local(it.id.toString(), it.filename) }, onOpen = { onSheet(VehicleSheet.tripPhotos) })
            if (vehicle.about.isNotBlank()) VehicleAboutCard(vehicle.about, onEdit = { onSheet(VehicleSheet.edit) }, by = null)
            VehicleTripsCard(state.passport.recent, state.passport.tripCount, appUnit, onAll = { onSheet(VehicleSheet.trips) }, onTrip = onTrip)
            VehicleRecordsCard(vehicleRecords(state.passport, appUnit, strings), onTrip)
            // A bicycle and a moped pair with no car stereo, so auto-record has nothing to key off: the block is absent, not
            // disabled. And a sold vehicle takes no trips, so offering it a stereo would promise what the app cannot do.
            if (vehicle.type.supportsAutoRecord && vehicle.soldAt == null) VehicleRecordingSection(stereo, autoRecordOn, onAutoRecord = { onSheet(VehicleSheet.autoRecord) }, onBluetoothSettings = onBluetoothSettings)
            if (vehicle.type.burnsFuel) VehicleEnergySections(vehicle, appUnit, onEdit = { onSheet(VehicleSheet.edit) })
        }
    }
}

// iOS: GarageMileageCard.swift. A single year compares nothing and has no bar.
@Composable
internal fun GarageMileageCard(odometerKm: Double, trackedKm: Double, years: List<VehicleYearDistance>, unit: DistanceUnit, editable: Boolean = true, onEdit: () -> Unit) {
    val strings = appStrings
    Column(Modifier.fillMaxWidth().surfaceCard(20.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(strings.garageMileage(), Modifier.testTag("garage_mileage"), style = AppType.section, color = AppTheme.colors.text)
        // Someone else's car (PublicVehicleView) reads the odometer; only one's own edits it.
        val odometer = @Composable {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val parts = Measure.distancePartsKm(odometerKm, unit, strings.language.code, strings)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(parts.value, style = interScaled(28f, InterWeight.heavy), color = AppTheme.colors.text)
                    Text(parts.unit, Modifier.weight(1f).padding(start = 5.dp, bottom = 3.dp), style = interScaled(13f, InterWeight.semibold), color = AppTheme.colors.textSecondary)
                    if (editable) Glyph(AppGlyph.chevronRight, null, tint = AppTheme.colors.textTertiary, size = 13.2.dp)
                }
                Text(strings.garageOnOdometer().uppercase(strings.language.locale), style = AppType.statCaption, letterSpacing = AppType.statCaptionTracking, color = AppTheme.colors.textTertiary)
            }
        }
        if (editable) PressableCard(onClick = onEdit, modifier = Modifier.fillMaxWidth().testTag("garage_odometer")) { odometer() } else odometer()
        if (years.size >= 2) {
            val peak = maxOf(years.maxOf { it.km }, 1.0)
            Row(Modifier.horizontalScroll(rememberScrollState()).height(110.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                years.forEach { year -> Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Measure.distanceKm(year.km, unit, strings.language.code, strings), style = interScaled(11f, InterWeight.semibold), color = AppTheme.colors.text, maxLines = 1)
                    Box(Modifier.width(32.dp).height(maxOf(2.0, 70 * year.km / peak).dp).background(AppTheme.accent, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                    Text(year.year.toString(), style = interScaled(11f), color = AppTheme.colors.textSecondary)
                } }
            }
        }
        Text(strings.garageTracked(Measure.distanceKm(trackedKm, unit, strings.language.code, strings)), style = interScaled(12f), color = AppTheme.colors.textSecondary)
    }
}
