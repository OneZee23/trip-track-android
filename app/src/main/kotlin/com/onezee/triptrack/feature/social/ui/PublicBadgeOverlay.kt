// iOS: BadgeDetailOverlay.swift. Public badges have no local earning date or trip to invent.
package com.onezee.triptrack.feature.social.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.me.*
import com.onezee.triptrack.logic.BadgeDefinitions
import com.onezee.triptrack.logic.shown

@Composable
fun PublicBadgeOverlay(row: AchievementRow, onDismiss: () -> Unit) {
    val strings = appStrings
    val definition = BadgeDefinitions.all.first { it.id == row.id }
    val secret = definition.isHidden && !row.unlocked
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .4f)).clickable(onClick = onDismiss).clearAndSetSemantics {})
            Box(Modifier.widthIn(max = 300.dp).padding(horizontal = 12.dp).background(AppTheme.colors.bg, RoundedCornerShape(24.dp)).testTag("public_badge_overlay")) {
                Column(Modifier.padding(24.dp).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (secret) Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) { Glyph(AppGlyph.lockFill, null, tint = AppTheme.colors.textTertiary, size = 36.dp) }
                    else BadgeArt(row, 96.dp)
                    Text(if (secret) "???" else MeArtwork.badgeTitles[row.id]?.let { strings.tr(it) }.orEmpty(), style = inter(22f, InterWeight.bold), color = if (row.unlocked) publicBadgeColor(row.id) else AppTheme.colors.text, textAlign = TextAlign.Center)
                    val figures = definition.figures.map { it.shown(LocalDistanceUnit.current, strings.language.code, strings) }
                    Text(if (secret) strings.badgeDetailHiddenAchievement() else MeArtwork.badgeDescriptions[row.id]?.let { strings.tr(it, *figures.toTypedArray()) }.orEmpty(), style = inter(14f), color = AppTheme.colors.textSecondary, textAlign = TextAlign.Center)
                    if (!row.unlocked) Text(strings.badgeDetailLocked(), style = AppType.chip, color = AppTheme.colors.textTertiary)
                }
                NavCircleButton(AppGlyph.xmark, strings.closeSheet(), onDismiss, Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("badge_close"))
            }
        }
    }
}

// iOS: BadgeDefinitions.swift (badge.color). These colours belong to the badge, not its tier.
private fun publicBadgeColor(id: String): Color = when (id) {
    "first_trip", "trips_500", "ten_thousand", "km_100k", "explorer_5", "globetrotter", "weekend_warrior", "highway_wolf", "streak_3", "streak_7" -> AppTheme.accent
    "road_regular", "trips_200", "endurance", "ambassador", "riddle_first", "above_clouds", "sea_level" -> AppTheme.blue
    "road_warrior", "explorer_25", "riddle_10", "long_shift" -> AppTheme.purple
    "legion", "million", "explorer_100" -> Color(0.9f, 0.65f, 0.1f)
    "first_km", "century", "explorer_10", "milestone_border", "wrapped_year" -> AppTheme.green
    "thousand", "veteran", "early_bird", "sunday_driver" -> AppTheme.yellow
    "equator" -> Color(0.2f, 0.55f, 0.9f)
    "km_250k", "iron_butt", "speed_demon", "streak_14", "streak_30" -> AppTheme.red
    "half_million", "explorer_50" -> Color(0.6f, 0.35f, 0.8f)
    "marathon_42", "marathon_100", "hours_50", "milestone_2000", "mountain_goat" -> AppTheme.teal
    "secret_komsomolsky" -> AppTheme.orange
    "night_rider" -> Color(0.3137254902f, 0.3137254902f, 0.6274509804f)
    "night_wolf" -> Color(0.2352941176f, 0.2352941176f, 0.5490196078f)
    "polar", "snow_leopard" -> Color(0.7058823529f, 0.862745098f, 1.0f)
    "midnight_rider" -> Color(0.55f, 0.4f, 0.85f)
    "centurion" -> Color(1.0f, 0.85f, 0.3f)
    "carpool_karaoke" -> Color(0.95f, 0.4f, 0.55f)
    "vault_keeper" -> Color(0.65f, 0.7f, 0.8f)
    "expedition" -> Color(0.65f, 0.5f, 0.35f)
    "early_tester", "veteran_2026" -> Color(0.86f, 0.2f, 0.58f)
    else -> AppTheme.accent
}
