// iOS: VehicleDetailView.heroCard, PublicVehicleView.header, VehicleCatalogCaption.swift, VehicleTagsRow.swift,
// GarageComponents.swift (VehiclePlateChip), ProfileComponents.swift (LvlPill)
package com.onezee.triptrack.feature.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.logic.UnitNumber
import com.onezee.triptrack.model.CarCatalog

/**
 * iOS `heroPlateWidth`: the stage is 0.77 of the screen's width, at most 340, so the car stays the biggest thing on
 * the card on a small phone and does not swallow the fold on a large one. A main photo takes the same width.
 */
@Composable
internal fun vehicleStageWidth(): Dp = minOf((LocalConfiguration.current.screenWidthDp * 0.77f).dp, 340.dp)

/** The three numbers of a vehicle's biography. [ready] is false while they are being counted: dashes, not zeros. */
internal data class VehicleHeroStats(val trips: Int, val regions: Int, val days: Int, val ready: Boolean = true)

/**
 * iOS VehicleDetailView.heroCard (own: 18 across, 22 above, 16 below, the stage 4 under the top, the name 16 under
 * the stage) and PublicVehicleView.header (someone else's: 14 across, 18 above and below, the name 12 under the
 * stage). Radius 20 either way. Name, the catalogue's line and capsule, the tags, the sold line, the plate and the
 * level pill, then, under a rule, the three numbers and, under another, «В гараже с…».
 */
@Composable
internal fun VehicleHeroCard(
    name: String,
    make: String,
    model: String,
    year: Int,
    bodyType: String,
    variant: CarCatalog.Variant?,
    tags: List<String>,
    soldLine: String?,
    plate: String?,
    level: Int,
    stats: VehicleHeroStats?,
    since: String?,
    foreign: Boolean,
    modifier: Modifier = Modifier,
    onLevel: (() -> Unit)? = null,
    stage: @Composable () -> Unit,
) {
    val strings = appStrings
    val colors = AppTheme.colors
    Column(
        modifier.fillMaxWidth().surfaceCard(20.dp)
            .padding(horizontal = if (foreign) 14.dp else 18.dp).padding(top = if (foreign) 18.dp else 22.dp, bottom = if (foreign) 18.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = if (foreign) 0.dp else 4.dp)) { stage() }
        Text(name, Modifier.padding(top = if (foreign) 12.dp else 16.dp).testTag("vehicle_hero_name"), style = inter(19f, InterWeight.bold), color = colors.text, maxLines = 1, overflow = TextOverflow.MiddleEllipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = points(19f * 0.6f), maxFontSize = points(19f), stepSize = points(0.25f)))
        VehicleCatalogCaption(make, model, year, bodyType, variant, Modifier.padding(top = 4.dp))
        if (tags.isNotEmpty()) VehicleTagsRow(tags, Modifier.padding(top = 8.dp))
        if (soldLine != null) Text(soldLine, Modifier.padding(top = 6.dp), style = inter(11f, InterWeight.semibold), color = AppTheme.accent, textAlign = TextAlign.Center)
        if (!plate.isNullOrBlank()) VehiclePlateChip(plate, Modifier.padding(top = 8.dp))
        VehicleLvlPill(level, Modifier.padding(top = 12.dp), onLevel)
        if (stats != null) {
            HeroRule(Modifier.padding(top = if (foreign) 16.dp else 18.dp))
            VehicleCircles(stats, Modifier.padding(top = 14.dp))
        }
        if (since != null) {
            // Someone else's card draws the second rule only when there is a line under it; one's own always has it.
            HeroRule(Modifier.padding(top = 14.dp))
            Text(strings.inGarageSince(since), Modifier.fillMaxWidth().padding(top = 12.dp), style = inter(11f), color = colors.textTertiary)
        }
    }
}

@Composable
private fun HeroRule(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.border))

/**
 * iOS passportCircles: three numbers, each in a third of the width, 20 bold over an 11 tertiary label, 3 apart. While
 * they are counted they read «—»: the loading state must not equal «0 поездок». A library with no trips says that
 * the biography is empty.
 */
@Composable
private fun VehicleCircles(stats: VehicleHeroStats, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().testTag("vehicle_passport_numbers")) {
            listOf(
                Triple(stats.trips, strings.nounTrips(stats.trips), "vehicle_trips_count"),
                Triple(stats.regions, strings.nounRegions(stats.regions), "vehicle_regions_count"),
                Triple(stats.days, strings.nounDays(stats.days), "vehicle_days_count"),
            ).forEach { (value, label, tag) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (stats.ready) value.toString() else "—", Modifier.testTag(tag), style = inter(20f, InterWeight.bold), color = colors.text)
                    Text(label, style = inter(11f), color = colors.textTertiary, maxLines = 2, textAlign = TextAlign.Center)
                }
            }
        }
        if (stats.ready && stats.trips == 0) Text(strings.vehicleBiographyEmpty(), Modifier.padding(horizontal = 14.dp), style = inter(11f), color = colors.textTertiary, textAlign = TextAlign.Center)
    }
}

/**
 * iOS VehicleCatalogCaption (showsCapsule): «Volkswagen Polo седан · 2018» in 13 secondary, the body type in lower
 * case after the model unless the model already names it, and under it, 10 down, the catalogue's engine line in a
 * card-alt capsule. Nothing at all when nothing is filled in: a lone dot is worse than no line.
 */
@Composable
internal fun VehicleCatalogCaption(make: String, model: String, year: Int, bodyType: String, variant: CarCatalog.Variant?, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val line = catalogModelLine(make, model, year, bodyType, strings.language.locale) { strings.avatarStyleName(it) }
    val spec = variant?.specLine(strings)
    if (line.isEmpty() && spec == null) return
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (line.isNotEmpty()) Text(line, Modifier.testTag("vehicle_model_line"), style = inter(13f), color = colors.textSecondary, textAlign = TextAlign.Center)
        if (spec != null) Text(spec, Modifier.testTag("vehicle_catalog_spec").background(colors.cardAlt, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            style = inter(12f, InterWeight.semibold), color = colors.text, textAlign = TextAlign.Center)
    }
}

/**
 * iOS VehicleTagsRow: the owner's short words as accent text, 11 semibold on the accent at 8 %, radius 8, with the PRO
 * pill after them; in one row when it fits and one under another when it does not.
 */
@Composable
internal fun VehicleTagsRow(tags: List<String>, modifier: Modifier = Modifier) {
    val gap = 6.dp
    Layout(
        content = {
            tags.forEach { tag ->
                Text(tag, Modifier.background(AppTheme.accent.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    style = inter(11f, InterWeight.semibold), color = AppTheme.accent)
            }
            ProBadge(height = 14.dp)
        },
        modifier = modifier.testTag("vehicle_tags"),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val gapPx = gap.roundToPx()
        val rowWidth = placeables.sumOf { it.width } + gapPx * (placeables.size - 1)
        if (rowWidth <= constraints.maxWidth) {
            val height = placeables.maxOf { it.height }
            layout(rowWidth, height) {
                var x = 0
                placeables.forEach { it.place(x, (height - it.height) / 2); x += it.width + gapPx }
            }
        } else {
            val width = placeables.maxOf { it.width }
            val height = placeables.sumOf { it.height } + gapPx * (placeables.size - 1)
            layout(width, height) {
                var y = 0
                placeables.forEach { it.place((width - it.width) / 2, y); y += it.height + gapPx }
            }
        }
    }
}

/**
 * iOS VehiclePlateChip: the plate drawn as a plate, 11 semibold with 0.3 of tracking in the ink, on the card colour with
 * a 1 pt frame, radius 4, 6 and 2 of padding. One neutral chip for every country.
 */
@Composable
internal fun VehiclePlateChip(plate: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Text(plate, modifier.testTag("vehicle_plate_chip").background(AppTheme.colors.card, shape).border(1.dp, AppTheme.colors.border, shape).padding(horizontal = 6.dp, vertical = 2.dp),
        style = inter(11f, InterWeight.semibold), color = AppTheme.colors.text, letterSpacing = points(0.3f), maxLines = 1)
}

/**
 * iOS LvlPill (Figma 580:129): a star and «LVL 6» in Handjet Black 15, gold on the gold wash, 12 before and 14 after,
 * 2 above and below. SwiftUI's stack still spaces the empty rank text after the number, so the trailing edge is 20.
 * A button when [onClick] is given: the level has a screen of its own.
 */
@Composable
internal fun VehicleLvlPill(level: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val strings = appStrings
    val label = strings.vehicleLevelTitle()
    Row(
        modifier.background(AppTheme.colors.goldBg, CapsuleShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick) else Modifier.semantics { contentDescription = "LVL $level" })
            .testTag("vehicle_level_row")
            .padding(start = 12.dp, end = 20.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(AppGlyph.starFill, null, tint = AppTheme.gold, size = 13.2.dp)
        Text("LVL $level", style = handjet(15f), color = AppTheme.gold, maxLines = 1, softWrap = false)
    }
}

/** iOS VehicleCatalogCaption.modelLine with its capsule: make and model joined by a space, the body type after them, only the year after « · ». */
internal fun catalogModelLine(make: String, model: String, year: Int, bodyType: String, locale: java.util.Locale, bodyName: (String) -> String): String {
    val body = if (bodyType.isEmpty()) "" else bodyName(bodyType).lowercase(locale)
    val modelWithBody = if (body.isNotEmpty() && !model.contains(body, ignoreCase = true)) listOf(model, body).joinToString(" ") else model
    val parts = mutableListOf<String>()
    listOf(make, modelWithBody).filter { it.isNotEmpty() }.joinToString(" ").takeIf { it.isNotEmpty() }?.let { parts += it }
    if (year > 0) parts += year.toString()
    return parts.joinToString(" · ")
}

/**
 * iOS CarCatalog.Variant.specLine: the engine (displacement in litres and power), the gearbox and the 0–100 time,
 * « · » between them: «1.6 110 л.с. · АКПП 6 · 0–100 за 11.7 с». The top speed is not part of it.
 */
internal fun CarCatalog.Variant.specLine(strings: com.onezee.triptrack.designsystem.strings.AppStrings): String? {
    val code = strings.language.code
    val engine = buildList {
        displacementCc?.takeIf { it > 0 }?.let { add(UnitNumber.tenths(it / 1000, code)) }
        powerHp?.takeIf { it > 0 }?.let { add(strings.garagePower(UnitNumber.upToTenth(it, code))) }
    }
    val parts = buildList {
        if (engine.isNotEmpty()) add(engine.joinToString(" "))
        transmission?.takeIf { it in setOf("manual", "automatic", "cvt", "amt", "dct") }?.let { add(strings.garageGearbox(it, gears)) }
        zeroToHundredS?.takeIf { it > 0 }?.let { add(strings.garageAcceleration(UnitNumber.upToTenth(it, code))) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
