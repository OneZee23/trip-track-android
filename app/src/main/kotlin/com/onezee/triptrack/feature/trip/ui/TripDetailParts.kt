package com.onezee.triptrack.feature.trip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.designsystem.strings.uppercased
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.interScaled
import com.onezee.triptrack.designsystem.theme.points
import com.onezee.triptrack.designsystem.theme.pressStart2P
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.trip.DetailPhotoState
import com.onezee.triptrack.feature.trip.TripDetailState
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.logic.UnitNumber
import com.onezee.triptrack.logic.consumptionUnit
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.logic.distanceParts
import com.onezee.triptrack.logic.elevationParts
import com.onezee.triptrack.logic.speedParts
import com.onezee.triptrack.model.ConsumptionUnit
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.TripEnergyMode
import com.onezee.triptrack.model.TripOrigin
import com.onezee.triptrack.model.Vehicle
import com.onezee.triptrack.ui.DetailChip
import com.onezee.triptrack.ui.DetailSectionHeader
import com.onezee.triptrack.ui.DetailStatCard
import com.onezee.triptrack.ui.PhotoImage
import com.onezee.triptrack.ui.PhotoSource
import com.onezee.triptrack.ui.RegionDisplay
import com.onezee.triptrack.ui.StatSegment
import com.onezee.triptrack.ui.TripAutoTitle
import com.onezee.triptrack.ui.cards.hasDisplayableName
import com.onezee.triptrack.ui.durationSegments
import java.time.Instant
import kotlin.math.roundToLong
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.style.TextOverflow
import com.onezee.triptrack.R
import com.onezee.triptrack.model.SocialAuthor
import com.onezee.triptrack.model.SocialFeedVehicle

/** A section of the detail panel: its heading over its content, 10 apart (iOS infoPanel sections). */
@Composable
fun DetailSection(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DetailSectionHeader(title, Modifier.weight(1f).semantics { heading() })
            trailing()
        }
        content()
    }
}

/** iOS: TripDetailView.barTitle / heading: the name, else the region, else «5 авг., 17:41». */
fun TripSummary.detailTitle(strings: AppStrings): String {
    if (hasDisplayableName(strings)) TripAutoTitle.localized(title, startDate, strings)?.takeIf { it.isNotEmpty() }?.let { return it }
    RegionDisplay.localized(region, strings.language)?.takeIf { it.isNotEmpty() }?.let { return it }
    return LocalizedDateFormatter.patterns("d MMM, HH:mm", strings.zone).getValue(strings.language).format(startDate)
}

/**
 * iOS: TripDetailView.titleBlock. The eyebrow in the pixel face («5 АВГУСТА · КРАСНОДАР. КРАЙ» when
 * the trip has a name), then the title in 26 bold that follows the font scale.
 */
@Composable
fun TripTitleBlock(trip: TripSummary, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val date = LocalizedDateFormatter.templates("dMMMM", strings.zone).getValue(strings.language).format(trip.startDate).uppercased(strings.language)
    val region = RegionDisplay.localized(trip.region, strings.language)
    val eyebrow = date + if (trip.hasDisplayableName(strings) && !region.isNullOrEmpty()) " · " + region.uppercased(strings.language) else ""
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(eyebrow, style = pressStart2P(9f), color = colors.textSecondary, maxLines = 1, softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = points(9f * 0.6f), maxFontSize = points(9f), stepSize = points(0.25f)))
        Text(trip.detailTitle(strings), style = interScaled(26f, InterWeight.bold), letterSpacing = points(-0.52f), color = colors.text,
            maxLines = 2, modifier = Modifier.semantics { heading() }.testTag("detail_title"))
    }
}

/** iOS: TripDetailView.timeRange. «09:24 – 11:02», across midnight «23:10 – 6 августа, 01:20». */
fun AppStrings.timeRange(start: Instant, end: Instant?): String {
    val hm = LocalizedDateFormatter.patterns("HH:mm", zone).getValue(language)
    if (end == null) return hm.format(start)
    val sameDay = start.atZone(zone).toLocalDate() == end.atZone(zone).toLocalDate()
    val tail = if (sameDay) hm.format(end) else LocalizedDateFormatter.patterns("d MMMM", zone).getValue(language).format(end) + ", " + hm.format(end)
    return "${hm.format(start)} – $tail"
}

/** iOS: TripDetailView.chipsRow. Time, the car, «Вписана рукой», and the privacy of one's own trip. */
@Composable
fun TripChipsRow(
    trip: TripSummary,
    isOwn: Boolean,
    signedIn: Boolean,
    vehicle: Vehicle?,
    vehicleKnown: Boolean,
    modifier: Modifier = Modifier,
    foreignVehicle: SocialFeedVehicle? = null,
    onForeignVehicle: (() -> Unit)? = null,
) {
    val strings = appStrings
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailChip { Text(strings.timeRange(trip.startDate, trip.endDate), style = inter(12f, InterWeight.medium, monospacedDigits = true)) }
        if (isOwn) {
            DetailChip {
                when {
                    vehicle != null -> {
                        Text(vehicle.avatarEmoji, style = inter(12f))
                        Text(vehicle.name, maxLines = 1)
                    }
                    trip.vehicleId != null && vehicleKnown -> {
                        Glyph(AppGlyph.car, contentDescription = null, size = 14.dp)
                        Text(strings.vehicleDeletedNote(), maxLines = 1)
                    }
                    trip.vehicleId == null -> {
                        Glyph(AppGlyph.car, contentDescription = null, size = 14.dp)
                        Text(strings.noVehicle(), maxLines = 1)
                    }
                    else -> Text("…")
                }
            }
        }
        if (!isOwn && foreignVehicle != null) {
            DetailChip(Modifier.then(if (onForeignVehicle != null) Modifier.clickable(onClick = onForeignVehicle) else Modifier).testTag("detail_foreign_vehicle")) {
                if (foreignVehicle.avatarEmoji.startsWith(PIXEL_AVATAR_PREFIX)) {
                    Image(ImageBitmap.imageResource(R.drawable.pixel_car), contentDescription = null, modifier = Modifier.size(14.dp),
                        contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                } else {
                    Text(foreignVehicle.avatarEmoji, style = inter(12f))
                }
                Text(foreignVehicle.name, maxLines = 1)
            }
        }
        if (trip.source == TripOrigin.manual) {
            DetailChip(Modifier.testTag("trip_manual_chip")) {
                Glyph(AppGlyph.pencilLine, contentDescription = null, size = 14.dp)
                Text(strings.manualTripBadge(), maxLines = 1)
            }
        }
        if (signedIn && isOwn) {
            DetailChip {
                Glyph(if (trip.isPrivate) AppGlyph.lockFill else AppGlyph.globe, contentDescription = null, size = 13.dp)
                Text(if (trip.isPrivate) strings.privacyOnlyMe() else strings.privacyPublic(), maxLines = 1)
            }
        }
    }
}

/**
 * iOS: TripDetailView.statsGrid. Two columns: distance, time, moving and stopped (recorded trips
 * that know them), average and maximum speed, elevation, then the energy tiles of the trip's car.
 * Every number comes from Measure with the person's unit; the cost from the car's own currency.
 */
@Composable
fun TripStatsGrid(state: TripDetailState, vehicle: Vehicle?, modifier: Modifier = Modifier) {
    val trip = state.trip ?: return
    val strings = appStrings
    val colors = AppTheme.colors
    val unit = LocalDistanceUnit.current
    val lang = strings.language.code
    val cards = mutableListOf<Triple<List<StatSegment>, String, Color>>()
    val distance = Measure.distanceParts(trip.distance, unit, lang, strings, Measure.Style.tenths)
    cards += Triple(listOf(StatSegment(distance.value, distance.unit)), strings.distance(), colors.distance)
    cards += Triple(durationSegments(trip.duration, strings), strings.duration(), colors.text)
    val recorded = trip.source == TripOrigin.recorded
    if (recorded && state.drivingTime + state.stoppedTime > 0) {
        cards += Triple(durationSegments(state.drivingTime, strings), strings.statMoving(), colors.text)
        cards += Triple(durationSegments(state.stoppedTime, strings), strings.statStops(), colors.text)
    }
    val average = if (trip.averageSpeed > 0) trip.averageSpeed else if (trip.duration > 0) trip.distance / trip.duration else 0.0
    Measure.speedParts(average, unit, lang, strings).let { cards += Triple(listOf(StatSegment(it.value, it.unit)), strings.statAvg(), AppTheme.blue) }
    if (recorded) Measure.speedParts(trip.maxSpeed, unit, lang, strings).let { cards += Triple(listOf(StatSegment(it.value, it.unit)), strings.statMax(), AppTheme.red) }
    if (state.hasElevation) {
        Measure.elevationParts(state.elevationGain, unit, lang, strings).let { cards += Triple(listOf(StatSegment("+" + it.value, it.unit)), strings.elevationGain(), colors.text) }
        Measure.elevationParts(state.maxAltitude, unit, lang, strings).let { cards += Triple(listOf(StatSegment(it.value, it.unit)), strings.maxAltitude(), colors.text) }
    }
    val energy = state.energyEstimate
    if (energy != null && vehicle != null) {
        // A part's tile exists only when that part has kilometres AND the car has its consumption:
        // «not set» never prints as zero.
        if (energy.electricMetres > 0 && energy.kWh > 0) {
            cards += Triple(listOf(StatSegment(UnitNumber.tenths(energy.kWh, lang), strings.unitKWhShort())), strings.statElectricity(), AppTheme.teal)
        }
        if (energy.fuelMetres > 0 && energy.litres > 0) {
            val gallons = vehicle.consumptionUnit(unit) == ConsumptionUnit.mpg
            val volume = if (gallons) energy.litres / ConsumptionUnit.litresPerGallon else energy.litres
            cards += Triple(listOf(StatSegment(UnitNumber.tenths(volume, lang), if (gallons) strings.unitGallonsShort() else strings.unitLitresShort())),
                strings.statFuel(), AppTheme.yellow)
        }
        val costs = listOfNotNull(energy.electricCost, energy.fuelCost)
        if (costs.isNotEmpty()) {
            val label = when {
                energy.electricCost != null && energy.fuelCost != null -> strings.statCost()
                energy.electricCost != null -> strings.statCostElectricity()
                else -> strings.statCostFuel()
            }
            cards += Triple(listOf(StatSegment(money(costs.sum()), vehicle.fuelCurrency)), label, AppTheme.accent)
        }
    }
    Column(modifier.testTag("detail_stats"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (segments, label, color) -> DetailStatCard(segments, label, color, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** iOS: TripDetailFormat.money. Whole units grouped by a narrow no-break space: «1 310». */
fun money(amount: Double): String {
    val digits = amount.roundToLong().toString()
    return digits.reversed().chunked(3).joinToString(" ").reversed()
}

/** The tiles the first frame shows while the full trip is read: the two it already knows, the rest held. */
@Composable
fun TripStatsSkeleton(trip: TripSummary, modifier: Modifier = Modifier) {
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val distance = Measure.distanceParts(trip.distance, unit, strings.language.code, strings, Measure.Style.tenths)
    val cardAlt = AppTheme.colors.cardAlt
    Column(modifier.testTag("detail_stats"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailStatCard(listOf(StatSegment(distance.value, distance.unit)), strings.distance(), AppTheme.green, Modifier.weight(1f))
            // Ink, as the loaded grid prints it (Figma 147:1190): iOS draws it in accent here and the
            // colour flips when the trip lands.
            DetailStatCard(durationSegments(trip.duration, strings), strings.duration(), AppTheme.colors.text, Modifier.weight(1f))
        }
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) { Box(Modifier.weight(1f).height(66.dp).background(cardAlt, RoundedCornerShape(14.dp)).semantics { }) }
            }
        }
    }
}

/**
 * iOS: TripDetailView.energyModeRow. Only on one's own plug-in hybrid: how the trip ran, Auto /
 * Electric / Fuel, and the kilometres on the battery when there were any. The choice is the
 * screen's state, written through to the trip.
 */
@Composable
fun EnergyModeRow(mode: TripEnergyMode, electricMetres: Double, onSelect: (TripEnergyMode) -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val unit = LocalDistanceUnit.current
    val haptics = rememberHaptics()
    Column(modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (electricMetres > 0) {
            Text(strings.tripOnBattery(Measure.distance(electricMetres, unit, strings.language.code, strings)), style = inter(12f), color = colors.textTertiary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(strings.tripEnergyModeTitle(), style = inter(13f, InterWeight.medium), color = colors.textSecondary, maxLines = 1, modifier = Modifier.weight(1f))
            Row(Modifier.background(colors.cardAlt, CapsuleShape).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for ((value, label) in listOf(TripEnergyMode.auto to strings.energyModeAuto(), TripEnergyMode.electric to strings.energyModeElectric(),
                    TripEnergyMode.fuel to strings.energyModeFuel())) {
                    val selected = value == mode
                    Text(
                        label,
                        style = inter(12f, if (selected) InterWeight.semibold else InterWeight.medium),
                        color = if (selected) Color.White else colors.textSecondary,
                        maxLines = 1,
                        modifier = Modifier
                            .background(if (selected) AppTheme.accent else Color.Transparent, CapsuleShape)
                            .clickable(interactionSource = null, indication = null, role = Role.Tab) {
                                if (!selected) { haptics.tap(); onSelect(value) }
                            }
                            .semantics { this.selected = selected }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("trip_energy_mode_${value.name}"),
                    )
                }
            }
        }
    }
}

/**
 * iOS: DraftTripBanner.swift. «Это ваша поездка?»: auto-tracking recorded it, and until «Моя» it is
 * in neither the atlas nor the stats. «Удалить» asks the screen's own delete question.
 */
@Composable
fun DraftTripBanner(onConfirm: () -> Unit, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Column(modifier.fillMaxWidth().surfaceCard().padding(16.dp).testTag("draft_banner"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(strings.draftBannerTitle(), style = inter(17f, InterWeight.bold), color = colors.text)
        Text(strings.draftBannerBody(), style = inter(14f), color = colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BannerButton(strings.draftConfirm(), Color.White, AppTheme.accent, Modifier.weight(1f).testTag("draft_confirm")) { haptics.tap(); onConfirm() }
            BannerButton(strings.draftDiscard(), AppTheme.red, colors.cardAlt, Modifier.weight(1f).testTag("draft_discard")) { haptics.tap(); onDiscard() }
        }
    }
}

@Composable
private fun BannerButton(text: String, ink: Color, fill: Color, modifier: Modifier, onClick: () -> Unit) {
    PressableCard(onClick = onClick, modifier = modifier) {
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).background(fill, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(text, style = inter(15f, InterWeight.semibold), color = ink, maxLines = 1)
        }
    }
}

/** iOS: DetailDescriptionCard. The trip's words, read-only here. */
@Composable
fun DescriptionCard(text: String, modifier: Modifier = Modifier) {
    val dark = AppTheme.isDark
    Text(
        text,
        style = interScaled(13.5f),
        color = if (dark) Color.White.copy(alpha = 0.78f) else Color(0xFF45454D),
        lineHeight = points(13.5f * 1.21f + 4.5f),
        modifier = modifier.fillMaxWidth().surfaceCard(14.dp).padding(horizontal = 16.dp, vertical = 14.dp).testTag("detail_description"),
    )
}

/** The bytes of a detail photo: the local file when it is here, else the server's copy. */
fun DetailPhotoState.source(thumbnail: Boolean): PhotoSource? = when {
    filename != null -> PhotoSource.Local(filename)
    thumbnail && thumbnailUrl != null -> PhotoSource.Remote(thumbnailUrl)
    originalUrl != null -> PhotoSource.Remote(originalUrl)
    thumbnailUrl != null -> PhotoSource.Remote(thumbnailUrl)
    else -> null
}

/**
 * iOS: TripDetailView.photosSection. «Фото · 3» with the add button on one's own trip, a strip of
 * 74 tiles; a tap opens the viewer, a hold asks to delete. Empty and one's own: the add card.
 */
@Composable
fun TripPhotosSection(
    photos: List<DetailPhotoState>,
    isOwn: Boolean,
    onOpen: (Int) -> Unit,
    onDelete: (DetailPhotoState) -> Unit,
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (!isOwn && photos.isEmpty()) return
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val title = if (photos.isEmpty()) strings.photos() else "${strings.photos()} · ${photos.size}"
    DetailSection(title, modifier, trailing = {
        if (isOwn && onAdd != null) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(44.dp)
                    .padding(start = 12.dp)
                    .plainPressFeedback(interaction)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); onAdd() }
                    .semantics { contentDescription = strings.addPhotos() }
                    .testTag("detail_add_photo"),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Glyph(AppGlyph.plusCircleFill, contentDescription = null, tint = AppTheme.accent, size = 24.dp)
            }
        }
    }) {
        if (photos.isEmpty()) {
            if (onAdd == null) return@DetailSection
            PressableCard(onClick = { haptics.tap(); onAdd() }, modifier = Modifier.fillMaxWidth().testTag("detail_add_photo_card")) {
                Column(Modifier.fillMaxWidth().surfaceCard(12.dp).padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Glyph(AppGlyph.camera, contentDescription = null, tint = colors.textTertiary, size = 28.dp)
                    Text(strings.addPhotos(), style = inter(12f), color = colors.textTertiary)
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("detail_photos")) {
                items(photos.size, key = { photos[it].id }) { index ->
                    val photo = photos[index]
                    Box(
                        Modifier
                            .size(74.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                interactionSource = null, indication = null,
                                onLongClick = if (isOwn) ({ haptics.action(); onDelete(photo) }) else null,
                                onClick = { onOpen(index) },
                            )
                            .testTag("detail_photo"),
                    ) {
                        if (photo.isMissing) {
                            Box(Modifier.size(74.dp).background(colors.cardAlt), contentAlignment = Alignment.Center) {
                                Glyph(AppGlyph.photo, contentDescription = null, tint = colors.textTertiary, size = 24.dp)
                            }
                        } else {
                            PhotoImage(photo.source(thumbnail = true), maxPixels = 300, modifier = Modifier.size(74.dp))
                        }
                    }
                }
            }
        }
    }
}

/** iOS VehicleAvatar.assetPrefix: an avatar that names a sprite, not an emoji to print. */
private const val PIXEL_AVATAR_PREFIX = "pixel_"

/**
 * iOS: TripDetailView.authorPill. Whose trip this is, on someone else's only: the avatar, the name,
 * «LVL n». iOS opens their profile; that screen is not ported yet, so here it is a label.
 */
@Composable
fun AuthorPill(author: SocialAuthor, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val strings = appStrings
    Row(
        modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(Color.Black.copy(alpha = 0.42f), CircleShape)
            .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("detail_author_pill"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).background(Color(0xFFF4F2EE), CircleShape), contentAlignment = Alignment.Center) {
            Text(author.avatarEmoji ?: "🚗", style = inter(16f))
        }
        Text(author.displayName ?: strings.companionsNoName(), style = inter(13f, InterWeight.bold), color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text("LVL ${author.profileLevel}", style = pressStart2P(7f), color = Color(0xFFD9DBE5).copy(alpha = 0.7f), maxLines = 1, softWrap = false)
    }
}
