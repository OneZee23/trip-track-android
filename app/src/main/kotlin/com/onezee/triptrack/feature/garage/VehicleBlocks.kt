// iOS: VehicleDetailView.swift (whereWasCard, vehicleTrips, vehicleRecords, aboutCard, recordingSection, fuelSection),
// GaragePhotoGrid.swift (GarageTripPhotos), GarageRegionChips.swift, PublicVehicleView.swift (records, photos, about)
package com.onezee.triptrack.feature.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.me.ProfileMapPreview
import com.onezee.triptrack.feature.me.ProfileSectionLabel
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.ui.LocalClock

/** iOS GarageSectionLabel: the heading of «Я», 16 bold in the ink, no capitals. The other blocks carry their title inside their card. */
@Composable
internal fun GarageSectionLabel(text: String, modifier: Modifier = Modifier) = ProfileSectionLabel(text, modifier)

/** iOS: a 12 pt chevron at the end of a row that opens something. */
@Composable
private fun RowChevron(size: Float = 11f) {
    Glyph(AppGlyph.chevronRight, null, tint = AppTheme.colors.textTertiary, size = (size * 1.2f).dp)
}

/** iOS GarageRegionChips: the first three regions as card-alt chips, radius 7, 10 medium, 8 and 5 of padding, then «+N». */
@Composable
internal fun GarageRegionChips(regions: List<MapRegionStat>, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    Row(modifier.horizontalScroll(rememberScrollState()).testTag("vehicle_region_chips"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        regions.take(3).forEach { region ->
            Text(region.localizedName(strings.language.code), Modifier.background(colors.cardAlt, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                style = inter(10f, InterWeight.medium), color = colors.textSecondary, maxLines = 1)
        }
        if (regions.size > 3) Text("+${regions.size - 3}", Modifier.padding(5.dp), style = inter(10f, InterWeight.medium), color = colors.textSecondary)
    }
}

/**
 * iOS whereWasCard / PublicVehicleView.mapCard: one card, 14 inside, radius 16. «Где была» 15 bold over «4 региона · 7
 * городов» 12 tertiary and a chevron, a mini map of 130 clipped to radius 12, and the regions as chips. The card
 * opens the whole map; on one's own page the header is what is tapped, as on iOS, on someone else's the whole card.
 */
@Composable
internal fun VehicleWhereWasCard(routes: List<List<GeoPoint>>, regions: List<MapRegionStat>, regionCount: Int, cityCount: Int, onOpen: () -> Unit, wholeCardOpens: Boolean, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val open = { haptics.tap(); onOpen() }
    Column(modifier.fillMaxWidth().surfaceCard(16.dp).then(if (wholeCardOpens) Modifier.clickable(role = Role.Button, onClickLabel = strings.vehicleWhereWas(), onClick = open) else Modifier)
        .padding(14.dp).testTag("vehicle_where_was"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().then(if (wholeCardOpens) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = strings.vehicleWhereWas(), onClick = open)),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(strings.vehicleWhereWas(), style = inter(15f, InterWeight.bold), color = colors.text)
                Text("$regionCount ${strings.nounRegions(regionCount)} · $cityCount ${strings.nounCities(cityCount)}", style = inter(12f), color = colors.textTertiary)
            }
            RowChevron(12f)
        }
        ProfileMapPreview(routes, Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(12.dp)))
        GarageRegionChips(regions)
    }
}

/** A photo of a trip as the garage shows it: a file of this phone or a picture of someone else's trip. */
internal sealed interface GaragePhoto {
    val id: String
    data class Local(override val id: String, val filename: String) : GaragePhoto
    data class Remote(override val id: String, val url: String) : GaragePhoto
}

/** One thumbnail of the strip or the grid: the photo fills the cell the caller gives it. */
@Composable
internal fun GaragePhotoThumbnail(photo: GaragePhoto, modifier: Modifier, maxSize: Int = 300) {
    // The ground stays while the picture is read, so a cell never collapses into the card behind it.
    val ground = modifier.background(AppTheme.colors.cardAlt)
    when (photo) {
        is GaragePhoto.Local -> VehicleLocalPhoto(photo.filename, ground, directory = "TripPhotos")
        is GaragePhoto.Remote -> com.onezee.triptrack.ui.PhotoImage(com.onezee.triptrack.ui.PhotoSource.Remote(photo.url), maxSize, ground)
    }
}

/**
 * iOS GarageTripPhotos: a card of 16 inside, radius 20. «Фото из поездок» 16 bold, the count in 12 and a chevron, over up
 * to four thumbnails 64 high, radius 9, 7 apart. Absent without photos; a tap opens the grid of all of them.
 */
@Composable
internal fun GarageTripPhotos(photos: List<GaragePhoto>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (photos.isEmpty()) return
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    PressableCard(onClick = { haptics.tap(); onOpen() }, modifier = modifier.fillMaxWidth().testTag("garage_trip_photos"), onClickLabel = strings.garageTripPhotos()) {
        Column(Modifier.fillMaxWidth().surfaceCard(20.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.garageTripPhotos(), Modifier.weight(1f), style = inter(16f, InterWeight.bold), color = colors.text)
                Text(photos.size.toString(), style = inter(12f), color = colors.textSecondary)
                RowChevron()
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                photos.take(4).forEach { photo -> GaragePhotoThumbnail(photo, Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(9.dp))) }
            }
        }
    }
}

/**
 * iOS aboutCard: the owner's line in 13 regular, under it, in 11 tertiary, «Это пишет владелец…» and an accent «Редактировать» link. On
 * someone else's car the footer is who wrote it. Absent when the line is empty.
 */
@Composable
internal fun VehicleAboutCard(about: String, onEdit: (() -> Unit)?, by: String?, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Column(modifier.fillMaxWidth().surfaceCard(16.dp).padding(14.dp).testTag("vehicle_about"), verticalArrangement = Arrangement.spacedBy(if (onEdit != null) 8.dp else 6.dp)) {
        Text(about, Modifier.fillMaxWidth(), style = inter(13f), color = colors.text)
        if (onEdit != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.vehicleAboutFooter(), Modifier.weight(1f), style = inter(11f), color = colors.textTertiary)
            Text(strings.edit(), Modifier.clickable(role = Role.Button) { haptics.tap(); onEdit() }.testTag("vehicle_about_edit"), style = inter(11f, InterWeight.semibold), color = AppTheme.accent)
        } else if (by != null) Text(by, style = inter(11f), color = colors.textTertiary)
    }
}

/**
 * iOS vehicleTrips: «Поездки этой машины» 14 semibold, the count in 12 and a chevron, then three compact rows under hairlines,
 * 10 apart: a lock for a private trip, the title 13 medium over «2 нояб., 18:01 · 39 мин» in 11 tertiary, the distance in 13 bold
 * and a chevron, 38 at least. The header opens the whole list, a row its trip.
 */
@Composable
internal fun VehicleTripsCard(trips: List<Trip>, count: Int, unit: DistanceUnit, onAll: () -> Unit, onTrip: (Trip) -> Unit, modifier: Modifier = Modifier) {
    if (trips.isEmpty()) return
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Column(modifier.fillMaxWidth().surfaceCard(16.dp).padding(14.dp).testTag("vehicle_trips"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = strings.vehicleTripsTitle()) { haptics.tap(); onAll() }.testTag("garage_trips_all"),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.vehicleTripsTitle(), Modifier.weight(1f), style = inter(14f, InterWeight.semibold), color = colors.text)
            Text(count.toString(), style = inter(12f), color = colors.textTertiary)
            RowChevron()
        }
        trips.forEachIndexed { index, trip ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            CompactTripRow(trip, unit, Modifier.clickable(role = Role.Button) { haptics.tap(); onTrip(trip) }.testTag("vehicle_trip_${trip.id}"), withElevation = false)
        }
    }
}

/**
 * The row of the garage's trip lists (iOS TripRowText): [thumbnail] if there is one (the full list has the route, 38),
 * title, when and how long, the distance and a chevron. [withElevation] adds the climb under the distance, as the full list
 * does; the compact rows of the vehicle page are 38 high, the full list's 46.
 */
@Composable
internal fun CompactTripRow(trip: Trip, unit: DistanceUnit, modifier: Modifier = Modifier, withElevation: Boolean, minHeight: Dp = 38.dp, thumbnail: (@Composable () -> Unit)? = null) {
    val strings = appStrings
    val colors = AppTheme.colors
    Row(modifier.fillMaxWidth().heightIn(min = minHeight), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        thumbnail?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                // The lock on this card too: whoever checks what their garage shows others looks at exactly this one.
                if (trip.isPrivate) Glyph(AppGlyph.lockFill, null, tint = colors.textTertiary, size = 10.8.dp)
                Text(GarageTripText.title(trip, strings), style = inter(13f, InterWeight.medium), color = if (trip.isPrivate && thumbnail != null) colors.textSecondary else colors.text, maxLines = 1)
            }
            Text(GarageTripText.whenText(trip, strings, LocalClock.current.instant()), style = inter(11f), color = colors.textTertiary, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Measure.distance(trip.distance, unit, strings.language.code, strings, Measure.Style.adaptive), style = inter(13f, InterWeight.bold), color = if (trip.isPrivate && thumbnail != null) colors.textSecondary else colors.text)
            if (withElevation && trip.elevation > 0) Text("↑ " + Measure.elevation(trip.elevation, unit, strings.language.code, strings), style = inter(11f), color = colors.textTertiary)
        }
        RowChevron()
    }
}

/** One of the records of a vehicle: its name over the trip or day it is about, the value, and a chevron only where it opens a trip. */
internal data class VehicleRecord(val label: String, val subtitle: String, val value: String, val trip: Trip? = null, val tag: String = "")

/**
 * iOS vehicleRecords: the heading «Рекорды» and one card of up to four rows (the longest trip, the highest, the best day, the
 * longest day): the name in 11 tertiary over the trip in 14 semibold, the value in 15 bold, 46 high, a hairline between.
 * The chevron is on the one row that opens something, the longest trip; the others are days, not trips.
 */
@Composable
internal fun VehicleRecordsCard(records: List<VehicleRecord>, onTrip: (Trip) -> Unit, modifier: Modifier = Modifier) {
    if (records.isEmpty()) return
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Column(modifier.fillMaxWidth().testTag("vehicle_records"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GarageSectionLabel(appStrings.statsRecords())
        Column(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(horizontal = 14.dp, vertical = 2.dp)) {
            records.forEachIndexed { index, record ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                val row = @Composable { RecordRow(record, Modifier.testTag(record.tag)) }
                if (record.trip != null) PressableCard(onClick = { haptics.tap(); onTrip(record.trip) }, modifier = Modifier.fillMaxWidth()) { row() } else row()
            }
        }
    }
}

@Composable
private fun RecordRow(record: VehicleRecord, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(modifier.fillMaxWidth().heightIn(min = 46.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(record.label, style = inter(11f), color = colors.textTertiary)
            Text(record.subtitle, style = inter(14f, InterWeight.semibold), color = colors.text, maxLines = 1)
        }
        if (record.value.isNotEmpty()) Text(record.value, style = inter(15f, InterWeight.bold), color = colors.text)
        if (record.trip != null) RowChevron()
    }
}

/**
 * iOS PublicVehicleView.records: the same heading, then one card per record with a dot (green for the longest, the accent for the
 * highest), the name in 11 tertiary over the trip in 14 semibold and the value in 15 bold.
 */
@Composable
internal fun ForeignRecordCards(records: List<Pair<VehicleRecord, Color>>, modifier: Modifier = Modifier) {
    if (records.isEmpty()) return
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth().testTag("vehicle_records"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GarageSectionLabel(appStrings.statsRecords())
        records.forEach { (record, dot) ->
            Row(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(dot, CircleShape))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(record.label, style = inter(11f), color = colors.textTertiary)
                    Text(record.subtitle, style = inter(14f, InterWeight.semibold), color = colors.text, maxLines = 1)
                }
                Text(record.value, style = inter(15f, InterWeight.bold), color = colors.text)
            }
        }
    }
}

/** What the vehicle's stereo is doing, in the three faces iOS draws: Bluetooth off, linked (connected or not), not linked. */
internal sealed interface StereoStatus {
    data object BluetoothOff : StereoStatus
    data class Linked(val name: String, val connected: Boolean) : StereoStatus
    data object NotLinked : StereoStatus
}

/**
 * iOS recordingSection: the heading «Запись» and one card. First the stereo's status (a 34 disc tinted at 14 %, the title in 14
 * bold over a 12 line, and on the right an accent capsule «Привязать магнитолу» or «Настройки»; a stereo that is connected has
 * nothing to press), a hairline, and «Автозапись · Вкл ›». One shape for the three states: the colour of the icon changes.
 */
@Composable
internal fun VehicleRecordingSection(status: StereoStatus, autoRecordOn: Boolean, onAutoRecord: () -> Unit, onBluetoothSettings: () -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Column(modifier.fillMaxWidth().testTag("vehicle_recording"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GarageSectionLabel(strings.vehicleRecordingSection())
        Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
            val (tint, title, subtitle) = when (status) {
                StereoStatus.BluetoothOff -> Triple(AppTheme.red, strings.btOffTitle(), strings.btOffChipBody())
                is StereoStatus.Linked -> Triple(if (status.connected) AppTheme.green else colors.textTertiary,
                    if (status.connected) strings.stereoConnectedTitle() else strings.stereoLinkedTitle(),
                    status.name + " · " + if (status.connected) strings.stereoStartsItself() else strings.stereoStartsOnConnect())
                StereoStatus.NotLinked -> Triple(colors.textTertiary, strings.stereoNotLinkedTitle(), strings.stereoNotLinkedBody())
            }
            Row(Modifier.fillMaxWidth().padding(14.dp).testTag("vehicle_stereo_status"), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                    Glyph(if (status == StereoStatus.BluetoothOff) AppGlyph.antennaRadiowavesSlash else AppGlyph.antennaRadiowaves, null, tint = tint, size = 18.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = inter(14f, InterWeight.bold), color = colors.text)
                    Text(subtitle, style = inter(12f), color = colors.textSecondary)
                }
                val action = when (status) {
                    StereoStatus.BluetoothOff -> strings.settingsButton() to onBluetoothSettings
                    StereoStatus.NotLinked -> strings.linkStereo() to onAutoRecord
                    is StereoStatus.Linked -> null
                }
                if (action != null) Text(action.first, Modifier.background(AppTheme.accentBg, CapsuleShape).clickable(role = Role.Button) { haptics.tap(); action.second() }.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = inter(13f, InterWeight.bold), color = AppTheme.accent, maxLines = 1)
            }
            Box(Modifier.padding(start = 14.dp).fillMaxWidth().height(1.dp).background(colors.border))
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { haptics.tap(); onAutoRecord() }.padding(14.dp).testTag("vehicle_autorecord_row"),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.autoRecord(), Modifier.weight(1f), style = inter(15f, InterWeight.medium), color = colors.text)
                Text(if (autoRecordOn) strings.autoRecordOn() else strings.autoRecordOff(), style = inter(13f, InterWeight.semibold), color = colors.textSecondary)
                RowChevron(12f)
            }
        }
    }
}

/**
 * iOS fuelSection: what the car has is what is shown. A fuel car gets «Топливо» and a card of three rows (the city, the
 * highway, the price of a litre or a gallon in the car's own dialect); an electric or plug-in car «Электричество» with the
 * consumption, the price of a kilowatt-hour and, for a hybrid, the range. An unset number is a dash, never a zero. Every row
 * opens the edit form.
 */
@Composable
internal fun VehicleEnergySections(vehicle: Vehicle, appUnit: DistanceUnit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val code = strings.language.code
    val dialect = vehicle.consumptionUnit(appUnit)
    val consumptionUnit = dialect.valueUnit(code, strings)
    val priceUnit = "${vehicle.fuelCurrency}/" + if (dialect.volumeUnit == VolumeUnit.gallons) strings.unitGallonsShort() else strings.unitLitresShort()
    val electric = vehicle.electricUnit(appUnit)
    val electricUnit = electric.valueUnit(code, strings)
    Column(modifier.fillMaxWidth().testTag("vehicle_energy"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vehicle.powertrain != Powertrain.electric) {
            GarageSectionLabel(strings.fuelSectionLabel())
            Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
                EnergyRow(strings.fuelCityRow(), UnitNumber.upToTenth(dialect.display(vehicle.cityConsumption), code) + " " + consumptionUnit, onEdit, "vehicle_fuel_city")
                EnergyRule()
                EnergyRow(strings.fuelHighwayRow(), UnitNumber.upToTenth(dialect.display(vehicle.highwayConsumption), code) + " " + consumptionUnit, onEdit, "vehicle_fuel_highway")
                EnergyRule()
                EnergyRow(strings.fuelPriceRow(), UnitNumber.upToTenth(dialect.displayPrice(vehicle.fuelPrice), code) + " " + priceUnit, onEdit, "vehicle_fuel_price")
            }
        }
        if (vehicle.powertrain != Powertrain.fuel) {
            GarageSectionLabel(strings.electricSectionLabel())
            Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
                EnergyRow(strings.electricConsumptionRow(), if (vehicle.electricConsumption > 0) UnitNumber.upToTenth(electric.display(vehicle.electricConsumption), code) + " " + electricUnit else "—", onEdit, "vehicle_electric_consumption")
                EnergyRule()
                EnergyRow(strings.electricPriceSection(), if (vehicle.electricityPrice > 0) UnitNumber.upToTenth(vehicle.electricityPrice, code) + " ${vehicle.fuelCurrency}/${strings.unitKWhShort()}" else "—", onEdit, "vehicle_electricity_price")
                if (vehicle.powertrain == Powertrain.pluginHybrid) {
                    EnergyRule()
                    EnergyRow(strings.electricRangeLabel(), if (vehicle.electricRangeKm > 0) Measure.distance(vehicle.electricRangeKm * 1000, vehicle.dashboardUnit(appUnit), code, strings) else "—", onEdit, "vehicle_electric_range")
                }
            }
        }
    }
}

/** iOS fuelRow: the title in 13 medium, the value in 13 semibold secondary and a chevron, 13 above and below. */
@Composable
private fun EnergyRow(title: String, value: String, onClick: () -> Unit, tag: String) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { haptics.tap(); onClick() }.padding(horizontal = 14.dp, vertical = 13.dp).testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = inter(13f, InterWeight.medium), color = colors.text)
        Text(value, style = inter(13f, InterWeight.semibold), color = colors.textSecondary)
        RowChevron()
    }
}

/** iOS fuelDivider: the card-alt rule, edge to edge, as the canon draws it. */
@Composable
private fun EnergyRule() = Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.cardAlt))

/** iOS PublicVehicleView.photosCard: the car's other photos as a bare strip, 110 by 84, radius 12, 8 apart, no heading and no card. */
@Composable
internal fun ForeignPhotoStrip(photos: List<GaragePhoto>, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (photos.isEmpty()) return
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("public_vehicle_photos"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.forEachIndexed { index, photo ->
            GaragePhotoThumbnail(photo, Modifier.size(110.dp, 84.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { haptics.tap(); onOpen(index) }.testTag("public_vehicle_photo"), 300)
        }
    }
}

/** The number of dp a section of the vehicle page keeps from its neighbours. */
internal val VehiclePageGap: Dp = 12.dp
