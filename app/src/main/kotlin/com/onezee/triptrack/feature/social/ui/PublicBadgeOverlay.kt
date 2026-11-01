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
                    Text(if (secret) "???" else MeArtwork.badgeTitles[row.id]?.let { strings.tr(it) }.orEmpty(), style = inter(22f, InterWeight.bold), color = if (row.unlocked) badgeColor(row.id) else AppTheme.colors.text, textAlign = TextAlign.Center)
                    val figures = definition.figures.map { it.shown(LocalDistanceUnit.current, strings.language.code, strings) }
                    Text(if (secret) strings.badgeDetailHiddenAchievement() else MeArtwork.badgeDescriptions[row.id]?.let { strings.tr(it, *figures.toTypedArray()) }.orEmpty(), style = inter(14f), color = AppTheme.colors.textSecondary, textAlign = TextAlign.Center)
                    if (!row.unlocked) Text(strings.badgeDetailLocked(), style = AppType.chip, color = AppTheme.colors.textTertiary)
                }
                NavCircleButton(AppGlyph.xmark, strings.closeSheet(), onDismiss, Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("badge_close"))
            }
        }
    }
}
