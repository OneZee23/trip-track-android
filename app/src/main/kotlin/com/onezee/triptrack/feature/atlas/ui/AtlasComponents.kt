// iOS: AtlasSheetTitleRow, AtlasSubstateHeader, AtlasStatTrio, AtlasTripRow, MapTripThumb, AtlasPlaceCard,
// AtlasMapControls, AtlasBetaChip, FlowRow chips, SkeletonPlaceholder(.atlasStats / .placeSuggestion).
package com.onezee.triptrack.feature.atlas.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.CardPressResponse
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.components.isAccessibilityFontScale
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.AtlasTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.ContinuousRoundedRectangle
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.model.MapTripPin
import com.onezee.triptrack.ui.LightRoutePreview
import com.onezee.triptrack.ui.PhotoImage
import com.onezee.triptrack.ui.PhotoSource

/** iOS `atlasSectionStyle()`: the section heading of Я, ink, bold, normal case. */
@Composable
fun AtlasSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = AppType.section, letterSpacing = AppType.sectionTracking,
        color = AtlasTheme.colors.ink, maxLines = 1)
}

/**
 * iOS: AtlasBetaChip. «Бета ›» beside the tab title: it opens the beta card. A 44 target around a
 * small pill; the same chip on Atlas and Places, told apart by [testTag].
 */
@Composable
fun AtlasBetaChip(onClick: () -> Unit, testTag: String = "atlas_beta_chip") {
    val atlas = AtlasTheme.colors
    val strings = appStrings
    val haptics = rememberHaptics()
    PressableCard(
        onClick = { haptics.tap(); onClick() },
        modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp).semantics { contentDescription = strings.atlasBetaTitle() }.testTag(testTag),
    ) {
        Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.background(atlas.accentSoft, CapsuleShape).padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(strings.atlasBetaChip(), style = inter(11f, InterWeight.bold), color = atlas.accentInk, maxLines = 1)
                Glyph(AppGlyph.chevronRight, contentDescription = null, tint = atlas.accentInk, size = 9.dp)
            }
        }
    }
}

/**
 * iOS: AtlasSheetTitleRow. The tab's title as the FIRST row of the sheet, pinned above the scroll and
 * visible in every position (errata 1): a separate layer at the top of the screen ran into the clock.
 */
@Composable
fun AtlasSheetTitleRow(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(TITLE_ROW_HEIGHT).padding(horizontal = AtlasTheme.sideInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, Modifier.semantics { heading() }, style = AppType.title, letterSpacing = AppType.titleTracking,
            color = AtlasTheme.colors.ink, maxLines = 1)
        trailing()
        Spacer(Modifier.weight(1f))
    }
}

/** iOS AtlasSheetTitleRow.height: every collapsed height counts it, so it is a number. */
val TITLE_ROW_HEIGHT: Dp = 44.dp

/**
 * iOS: AtlasSubstateHeader. Back, the title and its line, close. Inside the sheet, not over the map:
 * the object's title belongs to the sheet it is open in. Back returns to where the person came from,
 * × to the summary; without anywhere to go back to there is only ×.
 */
@Composable
fun AtlasSubstateHeader(title: String, subtitle: String?, onBack: (() -> Unit)?, onClose: () -> Unit) {
    val atlas = AtlasTheme.colors
    val strings = appStrings
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AtlasTheme.sideInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) CircleButton(AppGlyph.chevronLeft, strings.back(), "atlas_substate_back", diameter = 40.dp, onClick = onBack)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, Modifier.semantics { heading() }, style = AppType.headerTitle, color = atlas.ink, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = AppType.meta, color = atlas.secondary, maxLines = 1)
        }
        CircleButton(AppGlyph.xmark, strings.close(), "atlas_substate_close", diameter = 40.dp, onClick = onClose)
    }
}

/** A disc of [diameter] inside a 44 target: the drawing follows the mock, the finger gets 44 (spec §7). */
@Composable
fun CircleButton(
    glyph: AppGlyph,
    label: String,
    testTag: String,
    diameter: Dp = 36.dp,
    fill: Color = AtlasTheme.colors.chip,
    tint: Color = AtlasTheme.colors.ink,
    glyphSize: Dp = 18.dp,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    PressableCard(
        onClick = { haptics.tap(); onClick() },
        modifier = Modifier.size(44.dp).semantics { contentDescription = label }.testTag(testTag),
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(diameter).background(fill, CircleShape), contentAlignment = Alignment.Center) {
                Glyph(glyph, contentDescription = null, tint = tint, size = glyphSize)
            }
        }
    }
}

/** iOS: AtlasStatTrio.Column. A column that leads somewhere gets a chevron. */
data class StatColumn(val id: String, val value: String, val unit: String?, val caption: String, val action: (() -> Unit)? = null)

/**
 * iOS: AtlasStatTrio. Three numbers in ONE white card: one total read three ways. A large font puts
 * them in a column, one number per row; nothing is cut or shrunk.
 */
@Composable
fun AtlasStatTrio(columns: List<StatColumn>, modifier: Modifier = Modifier) {
    val atlas = AtlasTheme.colors
    val stacked = isAccessibilityFontScale()
    val card = modifier.fillMaxWidth().background(atlas.card, RoundedCornerShape(AtlasTheme.cardRadius)).padding(16.dp)
    if (stacked) {
        Column(card, verticalArrangement = Arrangement.spacedBy(14.dp)) { columns.forEach { StatTrioColumn(it, Modifier) } }
    } else {
        Row(card, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            columns.forEach { StatTrioColumn(it, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun StatTrioColumn(column: StatColumn, modifier: Modifier) {
    val atlas = AtlasTheme.colors
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(column.value, style = AppType.statValue.copy(fontFeatureSettings = "tnum"), letterSpacing = AppType.statValueTracking,
                    color = atlas.ink, maxLines = 1)
                column.unit?.let { Text(it, Modifier.padding(bottom = 2.dp), style = AppType.statUnit, color = atlas.secondary, maxLines = 1) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(column.caption.uppercase(appStrings.language.locale), style = AppType.statCaption, letterSpacing = AppType.statCaptionTracking,
                    color = atlas.secondary, maxLines = 2)
                if (column.action != null) Glyph(AppGlyph.chevronRight, contentDescription = null, tint = atlas.secondary.copy(alpha = 0.8f), size = 9.dp)
            }
        }
    }
    val action = column.action
    if (action != null) {
        PressableCard(onClick = action, modifier = modifier.testTag("atlas_stat_${column.id}")) { body() }
    } else {
        Box(modifier.testTag("atlas_stat_${column.id}")) { body() }
    }
}

/** A chip of a visited city (iOS: Inter 13 medium on the card, 34 tall). */
@Composable
fun CityChip(text: String, onClick: (() -> Unit)? = null) {
    val atlas = AtlasTheme.colors
    val haptics = rememberHaptics()
    val chip: @Composable () -> Unit = {
        Box(Modifier.height(34.dp).background(atlas.card, CapsuleShape).padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
            Text(text, style = AppType.chip, color = atlas.ink, maxLines = 1)
        }
    }
    if (onClick == null) chip() else PressableCard(onClick = { haptics.selection(); onClick() }) { chip() }
}

/** «ещё N в тумане»: a dashed capsule, quieter than the cities that are open. */
@Composable
fun FogChip(text: String) {
    val atlas = AtlasTheme.colors
    val dash = atlas.secondary.copy(alpha = 0.6f)
    Box(
        Modifier.height(34.dp).drawBehind {
            val stroke = 1.dp.toPx()
            drawRoundRect(dash, topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(size.height / 2),
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
        }.padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppType.chip, color = atlas.secondary, maxLines = 1)
    }
}

/** iOS FlowRow: chips that wrap by themselves, never positioned for one screen width. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/**
 * iOS: MapTripThumb. The trip's photo if it has one, otherwise its own route: the shape of the road
 * is what one recognises, never a generic icon.
 */
@Composable
fun MapTripThumb(trip: MapTripPin, size: Dp) {
    val shape = ContinuousRoundedRectangle(size * 0.28f)
    Box(Modifier.size(size).clip(shape).background(AppTheme.colors.cardAlt)) {
        val photo = trip.photoFilename
        if (photo != null) {
            PhotoImage(PhotoSource.Local(photo), maxPixels = (size.value * 3).toInt(), modifier = Modifier.fillMaxSize())
        } else if (trip.route.size > 1) {
            LightRoutePreview(listOf(trip.route), Modifier.fillMaxSize(), accent = AtlasTheme.colors.accent, padding = 8.dp)
        }
    }
}

/**
 * iOS: AtlasTripRow. One row for a trip, on the region and on the road: the mini map, the title,
 * «когда · сколько», a chevron. 56 tall, a number: lists count their height by rows.
 */
@Composable
fun AtlasTripRow(title: String, subtitle: String, trip: MapTripPin, onOpen: () -> Unit, testTag: String = "atlas_region_trip") {
    val atlas = AtlasTheme.colors
    val haptics = rememberHaptics()
    PressableCard(onClick = { haptics.tap(); onOpen() }, modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MapTripThumb(trip, 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = AppType.itemTitle, color = atlas.ink, maxLines = 1)
                Text(subtitle, style = AppType.meta, color = atlas.secondary, maxLines = 1)
            }
            Glyph(AppGlyph.chevronRight, contentDescription = null, tint = atlas.secondary.copy(alpha = 0.7f), size = 14.dp)
        }
    }
}

/** What the 44 tile of [AtlasPlaceCard] draws: a pin for a place, the house for home. */
enum class PlaceCardTile { place, home }

/**
 * iOS: AtlasPlaceCard. Three lines and a cross, nothing else: the card answers «what is it and how many
 * times»; the place's own screen is one tap away. 96 tall by number: the slot's top is counted from it.
 */
@Composable
fun AtlasPlaceCard(tile: PlaceCardTile, name: String, line: String, actionTitle: String, onOpen: () -> Unit, onClose: () -> Unit,
                   modifier: Modifier = Modifier, testTag: String = "atlas_place_card") {
    val atlas = AtlasTheme.colors
    val strings = appStrings
    val shape = ContinuousRoundedRectangle(AtlasTheme.cardRadius)
    val shadow = Color.Black.copy(alpha = if (AppTheme.isDark) 0.45f else 0.16f)
    Row(
        modifier.fillMaxWidth().height(PLACE_CARD_HEIGHT).iosShadow(shape, shadow, radius = 12.dp, y = 8.dp)
            .background(atlas.card, shape).padding(12.dp).testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(44.dp).background(atlas.accentSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            if (tile == PlaceCardTile.home) Glyph(AppGlyph.houseFill, contentDescription = null, tint = atlas.accentInk, size = 22.dp)
            else WorldGlyphIcon(WorldGlyph.mappin, contentDescription = null, tint = atlas.accentInk, size = 22.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = AppType.itemTitle, color = atlas.ink, maxLines = 2)
            Text(line, style = AppType.meta, color = atlas.secondary, maxLines = 1)
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier.height(24.dp).plainPressFeedback(interaction)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen)
                    .testTag("atlas_place_open"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(actionTitle, style = AppType.button, color = atlas.accent, maxLines = 1)
                Glyph(AppGlyph.chevronRight, contentDescription = null, tint = atlas.accent, size = 13.dp)
            }
        }
        CircleButton(AppGlyph.xmark, strings.close(), "atlas_place_close", tint = atlas.secondary, glyphSize = 15.dp, onClick = onClose)
    }
}

/** iOS AtlasPlaceCard.height. */
val PLACE_CARD_HEIGHT: Dp = 96.dp

/**
 * iOS: AtlasMapControls. ONE glass capsule, 44 wide: «Вид карты» over «Моё местоположение», a
 * hairline between. In an empty atlas there is nothing to style, and the capsule becomes a circle.
 */
@Composable
fun AtlasMapControls(showsLayers: Boolean, locationDenied: Boolean, onLayers: () -> Unit, onLocate: () -> Unit, modifier: Modifier = Modifier,
                     enabled: Boolean = true) {
    val atlas = AtlasTheme.colors
    val strings = appStrings
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.width(44.dp).iosShadow(shape, Color.Black.copy(alpha = 0.12f), radius = 5.dp, y = 2.dp)
            .background(atlas.glass, shape).border(1.dp, atlas.glassBorder, shape),
    ) {
        if (showsLayers) {
            ControlButton(WorldGlyph.layers, strings.atlasMapStyle(), "atlas_appearance", atlas.ink, enabled) { haptics.tap(); onLayers() }
            Box(Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(1.dp).background(atlas.glassBorder))
        }
        ControlButton(if (locationDenied) WorldGlyph.locationSlash else WorldGlyph.location, strings.atlasMyLocation(), "atlas_locate",
            if (locationDenied) atlas.secondary else atlas.ink, enabled) { haptics.tap(); onLocate() }
    }
}

@Composable
private fun ControlButton(glyph: WorldGlyph, label: String, testTag: String, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    // Faded chrome takes no taps (iOS `.allowsHitTesting(opacity > 0.5)`): they reach the map.
    Box(
        Modifier.size(44.dp).plainPressFeedback(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label }.testTag(testTag),
        contentAlignment = Alignment.Center,
    ) { WorldGlyphIcon(glyph, contentDescription = null, tint = tint, size = 21.dp) }
}

/** iOS SkeletonPlaceholder: breathes between 0.55 and 1 with [periodMillis]; still under «Remove animations». */
@Composable
fun skeletonAlpha(periodMillis: Int = 900): Float {
    if (LocalReduceMotion.current) return 1f
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(0.55f, 1f, infiniteRepeatable(tween(periodMillis), RepeatMode.Reverse), label = "skeletonPulse").value
}

/**
 * iOS: SkeletonPlaceholder(.atlasStats, period 1.2). Three pairs of bars where the three numbers will
 * be: a spinner says nothing about the shape of what is coming, this row does.
 */
@Composable
fun AtlasStatsSkeleton() {
    val alpha = skeletonAlpha(1200)
    val fill = AppTheme.colors.cardAlt
    val label = appStrings.loadingGeneric()
    Row(
        Modifier.fillMaxWidth().background(AtlasTheme.colors.card, RoundedCornerShape(AtlasTheme.cardRadius)).padding(16.dp)
            .alpha(alpha).semantics(mergeDescendants = true) { contentDescription = label }.testTag("atlas_stats_skeleton"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(3) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(64.dp).height(20.dp).background(fill, CapsuleShape))
                Box(Modifier.width(48.dp).height(10.dp).background(fill, CapsuleShape))
            }
        }
    }
}

/** iOS: SkeletonPlaceholder(.placeSuggestion): the circle, two bars and the button's place. */
@Composable
fun SuggestionSkeleton(label: String) {
    val alpha = skeletonAlpha()
    val fill = AppTheme.colors.cardAlt
    Column(
        Modifier.fillMaxWidth().background(AtlasTheme.colors.card, ContinuousRoundedRectangle(18.dp))
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)
            .alpha(alpha).semantics(mergeDescendants = true) { contentDescription = label }.testTag("places_suggestions_skeleton"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(fill, CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(132.dp).height(12.dp).background(fill, CapsuleShape))
                Box(Modifier.width(84.dp).height(10.dp).background(fill, CapsuleShape))
            }
        }
        Box(Modifier.padding(start = 48.dp).width(168.dp).height(44.dp).background(fill, ContinuousRoundedRectangle(14.dp)))
    }
}

/** A selected-state chip of the sort row and the style tiles: the semantics say «selected». */
fun Modifier.selectedSemantics(selected: Boolean): Modifier = semantics { this.selected = selected }

/** Text style with tabular digits, iOS `.monospacedDigit()`. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
