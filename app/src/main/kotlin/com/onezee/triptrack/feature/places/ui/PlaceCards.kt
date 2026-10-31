// iOS: PlaceCardView.swift (PlaceCardView, PlaceChipLabel), PlaceSuggestionCardView.swift
// (PlaceSuggestionCardView), PassCourseGlyph.swift. PlacesHowItWorksCard is not ported: since the
// panel redesign of 0.8.2 nothing on iOS shows it.
package com.onezee.triptrack.feature.places.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.AtlasTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.ContinuousRoundedRectangle
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.atlas.ui.WorldGlyph
import com.onezee.triptrack.feature.atlas.ui.WorldGlyphIcon
import com.onezee.triptrack.logic.isFirstTime
import com.onezee.triptrack.logic.isFrequentGuest
import com.onezee.triptrack.logic.lastAt
import com.onezee.triptrack.model.PlaceListItem
import com.onezee.triptrack.model.PlaceSuggestion
import com.onezee.triptrack.ui.CheckpointReading
import java.time.Instant

/** iOS `LocalizedDateFormatter.templates("dMMM")`: «25 сент.». */
fun AppStrings.dayMonth(date: Instant): String = LocalizedDateFormatter.templates("dMMM", zone).getValue(language).format(date)

/** «118 проездов · последний 25 сент.»; without passes just the number. */
fun passesLine(count: Int, lastAt: Instant?, strings: AppStrings): String {
    val passes = "${strings.formattedCount(count)} ${strings.nounPasses(count)}"
    val last = lastAt ?: return passes
    return "$passes · ${strings.placeLastPass(strings.dayMonth(last))}"
}

/**
 * iOS: PlaceCardView. The place's name 16/500 and two grey lines under it. The «частый гость» chip is
 * NEUTRAL and «обычно…» grey too: terracotta on this screen belongs to the action and to one big
 * number on the place screen. [compact] drops «обычно…» for a long list.
 */
@Composable
fun PlaceCard(item: PlaceListItem, compact: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val atlas = AtlasTheme.colors
    val strings = appStrings
    val haptics = rememberHaptics()
    val chip = when {
        item.isFrequentGuest -> strings.placeChipFrequent()
        item.isFirstTime -> strings.placeChipFirst()
        else -> null
    }
    PressableCard(onClick = { haptics.tap(); onTap() }, modifier = modifier.fillMaxWidth().testTag("place_card_${item.place.id.toString().uppercase()}")) {
        Row(
            Modifier.fillMaxWidth().background(atlas.card, ContinuousRoundedRectangle(18.dp)).heightIn(min = 44.dp)
                .padding(start = 13.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.place.name?.takeIf { it.isNotEmpty() } ?: strings.placeUnnamed(), Modifier.weight(1f), style = AppType.itemTitle,
                        color = atlas.ink, maxLines = 2)
                    if (chip != null) PlaceChipLabel(chip)
                }
                Text(passesLine(item.stats.passCount, item.lastAt, strings), style = AppType.meta, color = atlas.secondary, maxLines = 1)
                val usual = item.usual
                if (!compact && usual != null) {
                    Text(strings.placeUsually(CheckpointReading.clock(usual, strings)), style = AppType.meta, color = atlas.secondary, maxLines = 1)
                }
            }
            Glyph(AppGlyph.chevronRight, contentDescription = null, tint = atlas.secondary.copy(alpha = 0.7f), size = 14.dp)
        }
    }
}

/** iOS: PlaceChipLabel. Lowercase and grey: a CAPTION, not an award (places give no XP). */
@Composable
fun PlaceChipLabel(text: String) {
    val atlas = AtlasTheme.colors
    Box(Modifier.height(24.dp).background(atlas.chip, CapsuleShape).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
        Text(text, style = AppType.caption, color = atlas.secondary, maxLines = 1)
    }
}

/**
 * iOS: PlaceSuggestionCardView. The name, how many trips began or ended here, and ONE separate
 * button «Сохранить как место». The card itself does not press: a suggestion has no screen, and a row
 * pressed whole for its only action would promise a passage.
 */
@Composable
fun PlaceSuggestionCard(suggestion: PlaceSuggestion, onSave: () -> Unit, modifier: Modifier = Modifier) {
    val atlas = AtlasTheme.colors
    val strings = appStrings
    val haptics = rememberHaptics()
    Column(
        modifier.fillMaxWidth().background(atlas.card, ContinuousRoundedRectangle(18.dp))
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp).testTag("place_suggestion_${suggestion.cell}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).background(atlas.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                WorldGlyphIcon(WorldGlyph.mappin, contentDescription = null, tint = atlas.accent, size = 19.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(suggestion.name?.takeIf { it.isNotEmpty() } ?: strings.placeSuggestUnnamed(), style = AppType.itemTitle, color = atlas.ink, maxLines = 2)
                Text(strings.placeSuggestTrips("${strings.formattedCount(suggestion.trips)} ${strings.nounTrips(suggestion.trips)}"),
                    style = AppType.meta, color = atlas.secondary, maxLines = 2)
            }
        }
        // Under the text, indented past the circle: the card's action, not an icon in its corner.
        PressableCard(onClick = { haptics.tap(); onSave() }, modifier = Modifier.padding(start = 48.dp).testTag("place_suggestion_save_${suggestion.cell}")) {
            Row(
                Modifier.height(44.dp).background(atlas.accentSoft, ContinuousRoundedRectangle(14.dp)).padding(start = 10.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Glyph(AppGlyph.plus, contentDescription = null, tint = atlas.accentInk, size = 17.dp)
                Text(strings.placeSuggestSave(), style = AppType.button, color = atlas.accentInk, maxLines = 1)
            }
        }
    }
}

/**
 * iOS: PassCourseGlyph. The circle with the course arrow, ONE for both cards of the place screen; a dot
 * where the course is unknown (`PlacePass.unknownCourse`): an arrow would lie about a direction.
 */
@Composable
fun PassCourseGlyph(course: Double) {
    Box(Modifier.size(PASS_GLYPH).background(AppTheme.accentBg, CircleShape).semantics { }, contentAlignment = Alignment.Center) {
        if (course >= 0) WorldGlyphIcon(WorldGlyph.arrowUp, contentDescription = null, tint = AppTheme.accent, size = 18.dp, rotationDegrees = course.toFloat())
        else Box(Modifier.size(7.dp).background(AppTheme.colors.textTertiary, CircleShape))
    }
}

/** iOS PassCourseGlyph.diameter: both cards start their text column from it. */
val PASS_GLYPH: Dp = 40.dp
