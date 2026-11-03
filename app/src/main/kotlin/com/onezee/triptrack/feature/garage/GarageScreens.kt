// iOS: GarageView.swift, VehicleDetailView.swift, GarageComponents.swift
package com.onezee.triptrack.feature.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.onezee.triptrack.feature.me.ProfileStat
import com.onezee.triptrack.feature.me.ProfileTripRow
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
                item { Text(strings.garageActive(), style = AppType.section, color = AppTheme.colors.textSecondary) }
                item(key = state.active.vehicle.id) { GarageVehicleCard(state.active, true, unit, onVehicle, onSelect, onDelete, onUnsell) }
                item { Text(strings.garageActiveHint(), style = AppType.caption, color = AppTheme.colors.textTertiary) }
            }
            if (state.others.isNotEmpty()) {
                item { Text(strings.garageOthers(), style = AppType.section, color = AppTheme.colors.textSecondary) }
                items(state.others, key = { it.vehicle.id }) { GarageVehicleCard(it, false, unit, onVehicle, onSelect, onDelete, onUnsell) }
                item { Text(if (state.active == null) strings.garageNoActiveHint() else strings.garageArchiveHint(), style = AppType.caption, color = AppTheme.colors.textTertiary) }
            }
            if (state.sold.isNotEmpty()) {
                item { Text(strings.garageSold(), style = AppType.section, color = AppTheme.colors.textSecondary) }
                items(state.sold, key = { it.vehicle.id }) { GarageVehicleCard(it, false, unit, onVehicle, onSelect, onDelete, onUnsell) }
                item { Text(strings.garageSoldHint(), style = AppType.caption, color = AppTheme.colors.textTertiary) }
            }
            if (state.vehicles.count { it.vehicle.soldAt == null } >= 5) item { Text(strings.maxVehiclesHint(), style = AppType.caption, color = AppTheme.colors.textTertiary) }
        }
    }
}

@Composable
private fun GarageVehicleCard(item: GarageVehicle, active: Boolean, unit: DistanceUnit, onVehicle: (UUID) -> Unit, onSelect: (UUID) -> Unit, onDelete: (UUID) -> Unit, onUnsell: (UUID) -> Unit) {
    val strings = appStrings
    val vehicle = item.vehicle
    var actions by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Box {
        Column(Modifier.fillMaxWidth().clip(shape).background(if (active) AppTheme.accentBg else AppTheme.colors.card)
            .border(if (active) 2.dp else 1.dp, if (active) AppTheme.accent else AppTheme.colors.borderBright, shape)
            .combinedClickable(role = Role.Button, onClick = { onVehicle(vehicle.id) }, onLongClickLabel = strings.moreActions(), onLongClick = { actions = true })
            .testTag("garage_vehicle_${vehicle.id}")) {
            item.mainPhoto?.let { VehicleLocalPhoto(it.filename, Modifier.fillMaxWidth().height(150.dp).alpha(if (vehicle.soldAt != null) .55f else 1f), remoteURL = it.remoteURL) }
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.alpha(if (vehicle.soldAt != null) .55f else 1f)) { VehicleSpritePlate(vehicle) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(vehicle.name.ifBlank { strings.unnamedVehicle() }, Modifier.weight(1f), style = interScaled(16f, InterWeight.bold), color = AppTheme.colors.text, maxLines = 1)
                        if (vehicle.soldAt != null) Text(strings.vehicleSoldBadge(), style = interScaled(10f, InterWeight.bold), color = AppTheme.colors.textTertiary)
                        if (!vehicle.visibleToOthers) Glyph(AppGlyph.infoCircleFill, strings.vehicleHiddenFromOthers(), size = 11.dp, tint = AppTheme.colors.textTertiary)
                        if (active) Glyph(AppGlyph.checkmark, strings.vehicleMainLabel(), size = 15.dp, tint = AppTheme.accent)
                    }
                    Text(listOf(vehicle.make, vehicle.model, vehicle.year.takeIf { it > 0 }?.toString().orEmpty()).filter(String::isNotBlank).joinToString(" · "), style = interScaled(12f), color = AppTheme.colors.textSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f).height(4.dp).background(AppTheme.colors.goldBg, CircleShape)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(vehicle.progressToNextLevel.toFloat().coerceIn(0f, 1f)).background(AppTheme.gold, CircleShape))
                        }
                        Text(strings.tr(com.onezee.triptrack.designsystem.R.string.android_level_label, vehicle.level), style = interScaled(9f, InterWeight.bold), color = AppTheme.gold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(Measure.odometer(vehicle.displayOdometerKm, vehicle.dashboardUnit(unit), strings.language.code, strings), style = interScaled(15f, InterWeight.bold), color = AppTheme.colors.text)
                        if (item.tripCount > 0) Text(strings.tripsCount(item.tripCount), style = interScaled(15f, InterWeight.bold), color = AppTheme.colors.text)
                    }
                }
            }
        }
        val choices = buildList {
            if (!active && vehicle.soldAt == null) add(ActionPopoverItem(strings.makeMainVehicle(), AppGlyph.checkmark) { onSelect(vehicle.id) })
            if (vehicle.soldAt != null) add(ActionPopoverItem(strings.vehicleUnsell(), AppGlyph.arrowUturnBackwardCircleFill) { onUnsell(vehicle.id) })
            add(ActionPopoverItem(strings.deleteVehicle(), AppGlyph.trash, true) { onDelete(vehicle.id) })
        }
        ActionPopover(actions, { actions = false }, choices)
    }
}

@Composable
internal fun VehiclePassportScreen(state: VehiclePassportState, appUnit: DistanceUnit, onBack: () -> Unit,
                                   onSheet: (VehicleSheet) -> Unit, onSelect: () -> Unit, onTrip: (Trip) -> Unit) {
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
        // iOS: the bar stays on the vehicle page, and the last row clears it.
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = tabBarContentClearance()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().surfaceCard(24.dp).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clickable(role = Role.Button, onClickLabel = strings.vehiclePhotos()) { onSheet(VehicleSheet.photos) }) {
                    VehicleStage(vehicle, photoFilename = state.mainPhoto?.filename, remoteURL = state.mainPhoto?.remoteURL)
                }
                Text(vehicle.name.ifBlank { strings.unnamedVehicle() }, style = interScaled(19f, InterWeight.bold), color = AppTheme.colors.text)
                Text(listOf(vehicle.make, vehicle.model, vehicle.year.takeIf { it > 0 }?.toString().orEmpty()).filter(String::isNotBlank).joinToString(" · "), Modifier.padding(top = 7.dp), style = AppType.meta, color = AppTheme.colors.textSecondary)
                state.catalogVariant?.let { variant ->
                    val specs = listOfNotNull(variant.powerHp?.let { strings.garagePower(UnitNumber.upToTenth(it, strings.language.code)) },
                        variant.zeroToHundredS?.let { strings.garageAcceleration(UnitNumber.upToTenth(it, strings.language.code)) },
                        variant.topSpeedKmh?.let { Measure.speed(it / 3.6, appUnit, strings.language.code, strings) })
                    Text(specs.joinToString(" · "), Modifier.padding(top = 8.dp), style = AppType.caption, color = AppTheme.colors.textSecondary)
                }
                if (vehicle.soldAt != null) Text(strings.vehicleSoldState(strings.longDate(vehicle.soldAt!!)), Modifier.padding(top = 6.dp), style = AppType.caption, color = AppTheme.accent)
                if (vehicle.plate.isNotBlank()) Text(vehicle.plate, Modifier.padding(top = 8.dp).background(AppTheme.colors.cardAlt, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 5.dp), style = AppType.chip, color = AppTheme.colors.text)
                Box(Modifier.padding(top = 12.dp).background(AppTheme.colors.goldBg, CircleShape).clickable(role = Role.Button, onClickLabel = strings.vehicleLevelTitle()) { onSheet(VehicleSheet.level) }.testTag("vehicle_level_row").padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(strings.tr(com.onezee.triptrack.designsystem.R.string.android_level_label, vehicle.level), style = interScaled(11f, InterWeight.semibold), color = AppTheme.gold)
                }
                Spacer(Modifier.height(18.dp)); MeDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    ProfileStat(state.passport.tripCount.toString(), strings.nounTrips(state.passport.tripCount), Modifier.weight(1f))
                    ProfileStat(state.passport.regionCount.toString(), strings.nounRegions(state.passport.regionCount), Modifier.weight(1f))
                    ProfileStat(state.passport.daysOnRoad.toString(), strings.nounDays(state.passport.daysOnRoad), Modifier.weight(1f))
                }
                MeDivider()
                Text(strings.inGarageSince(strings.monthYear(vehicle.createdAt)), Modifier.fillMaxWidth().padding(top = 12.dp), style = AppType.caption, color = AppTheme.colors.textTertiary)
            }
            Drive2LogbookRow(vehicle.drive2URL, onConnect = if (Drive2Audience.invitesOnPage(strings.language.code)) ({ onSheet(VehicleSheet.drive2) }) else null)
            GarageMileageCard(vehicle.displayOdometerKm, vehicle.odometerKm, state.passport.years, state.dashboardUnit) { onSheet(VehicleSheet.edit) }
            if (vehicle.about.isNotBlank()) MeSection(strings.vehicleAbout()) { MeRow(vehicle.about) }
            MeSection(strings.vehicleWhereWas()) {
                MeRow(strings.statsRegions(), state.passport.regionCount.toString(), onClick = { onSheet(VehicleSheet.map) })
                if (state.passport.routes.isNotEmpty()) com.onezee.triptrack.feature.me.ProfileMapPreview(state.passport.routes,
                    Modifier.fillMaxWidth().height(130.dp).padding(horizontal = 14.dp).clickable { onSheet(VehicleSheet.map) })
            }
            MeSection(strings.vehiclePhotos()) {
                MeRow(strings.vehiclePhotos(), tag = "vehicle_photos", onClick = { onSheet(VehicleSheet.photos) })
                MeRow(strings.garageTripPhotos(), state.passport.photos.size.toString(), tag = "garage_trip_photos", onClick = { onSheet(VehicleSheet.tripPhotos) })
            }
            MeSection(strings.vehicleTripsTitle()) {
                state.passport.recent.forEach { ProfileTripRow(it, appUnit, onClick = { onTrip(it) }) }
                MeRow(strings.all(), state.passport.tripCount.toString(), onClick = { onSheet(VehicleSheet.trips) })
            }
            MeSection(strings.statsRecords()) {
                state.passport.longestTrip?.let { trip -> MeRow(strings.recordLongest(), Measure.distance(trip.distance, appUnit, strings.language.code, strings), onClick = { onTrip(trip) }) }
                state.passport.highestTrip?.takeIf { it.elevation > 0 }?.let { trip -> MeRow(strings.recordHighest(), Measure.elevation(trip.elevation, appUnit, strings.language.code, strings), onClick = { onTrip(trip) }) }
            }
            if (vehicle.type != VehicleType.bicycle && vehicle.soldAt == null) MeSection(strings.vehicleRecordingSection()) {
                MeRow(strings.autoRecord(), onClick = { onSheet(VehicleSheet.autoRecord) })
            }
            MeSection(strings.vehicleWhoSees()) { MeRow(strings.vehicleWhoSees(), subtitle = strings.vehiclePrivacyRowHint(), onClick = { onSheet(VehicleSheet.privacy) }) }
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
        Column(Modifier.fillMaxWidth().clickable(enabled = editable, role = Role.Button, onClick = onEdit)) {
            val parts = Measure.distancePartsKm(odometerKm, unit, strings.language.code, strings)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(parts.value, style = interScaled(28f, InterWeight.heavy), color = AppTheme.colors.text)
                Text(parts.unit, Modifier.weight(1f).padding(start = 5.dp, bottom = 3.dp), style = interScaled(13f, InterWeight.semibold), color = AppTheme.colors.textSecondary)
                if (editable) Glyph(AppGlyph.chevronRight, null, tint = AppTheme.colors.textTertiary, size = 16.dp)
            }
            Text(strings.garageOnOdometer().uppercase(strings.language.locale), style = AppType.statCaption, color = AppTheme.colors.textTertiary)
        }
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
