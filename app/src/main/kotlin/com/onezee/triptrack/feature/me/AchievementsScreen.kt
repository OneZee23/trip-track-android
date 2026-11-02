// iOS: BadgeArt.swift, BadgesView.swift, BadgeDetailView.swift, ProfileAchievementsSection.swift
package com.onezee.triptrack.feature.me

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.model.BadgeRarity
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.logic.BadgeDefinitions
import com.onezee.triptrack.logic.shown

@Composable
internal fun BadgeArt(badge: AchievementRow, side: Dp = 80.dp) {
    val title = MeArtwork.badgeTitles[badge.id]?.let { appStrings.tr(it) }.orEmpty()
    val resource = MeArtwork.drawables["badge_${badge.id}"] ?: return
    Image(painterResource(resource), title, Modifier.size(side).graphicsLayer { alpha = if (badge.unlocked) 1f else .35f },
        colorFilter = if (badge.unlocked) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }))
}

@Composable
internal fun BadgeTile(badge: AchievementRow, side: Dp = 40.dp) {
    val light = listOf(0xFFF1F2F3, 0xFFE6F5EB, 0xFFE7F1FC, 0xFFF3EBF9, 0xFFFCF4E4, 0xFFFBE7F2)
    val dark = listOf(0xFF35393D, 0xFF213F2F, 0xFF22384E, 0xFF382D48, 0xFF493D21, 0xFF47243C)
    Box(Modifier.size(side).background(Color((if (AppTheme.isDark) dark else light)[badge.rarity.rawValue]), RoundedCornerShape(side * (11f / 40)))
        .graphicsLayer { alpha = if (badge.unlocked) 1f else .55f }, contentAlignment = Alignment.Center) {
        BadgeArt(badge, side * .8f)
    }
}

@Composable
internal fun AchievementsScreen(badges: List<AchievementRow>, onBack: () -> Unit, onBadge: (String) -> Unit) =
    AchievementsScreen(badges, onBack, onBadge, null)

@Composable
internal fun AchievementsScreen(badges: List<AchievementRow>, onBack: () -> Unit, onBadge: (String) -> Unit, ownerName: String?) {
    val strings = appStrings
    var filter by remember { mutableIntStateOf(0) }
    val hidden = remember { BadgeDefinitions.all.filter { it.isHidden }.mapTo(HashSet()) { it.id } }
    val displayed = when (filter) { 1 -> badges.filter { it.unlocked }; 2 -> badges.filter { it.id in hidden }; else -> badges }
    val total = badges.count { it.id !in hidden || it.unlocked }
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding().testTag("achievements_screen")) {
        MeNavBar(strings.achievementsSection(), onBack)
        if (!ownerName.isNullOrBlank()) Text(ownerName, Modifier.padding(horizontal = 16.dp), style = AppType.caption, color = AppTheme.colors.textSecondary)
        Text(strings.achievementsOpenedOf(badges.count { it.unlocked }, total), Modifier.padding(16.dp), style = AppType.meta, color = AppTheme.colors.textSecondary)
        if (ownerName != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(strings.achievementsFilterAll(), strings.achievementsFilterUnlocked(), strings.achievementsFilterSecret()).forEachIndexed { index, title ->
                PressableCard(onClick = { filter = index }, modifier = Modifier.testTag("achievements_filter_$index")) {
                    Text(title, Modifier.background(if (filter == index) AppTheme.accentBg else AppTheme.colors.cardAlt, CapsuleShape).padding(horizontal = 12.dp, vertical = 10.dp), style = AppType.chip, color = if (filter == index) AppTheme.accent else AppTheme.colors.textSecondary)
                }
            }
        }
        // iOS emptyState: «nothing unlocked yet» under the shelf drawing; second person for your own
        // collection, third for somebody else's.
        if (displayed.isEmpty()) Column(Modifier.fillMaxWidth().padding(top = 24.dp).padding(horizontal = 16.dp).testTag("achievements_empty"),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.onezee.triptrack.ui.EmptyStateIllustration(com.onezee.triptrack.R.drawable.empty_achievements, size = 148.dp)
            Text(strings.achievementsEmpty(), style = inter(13f, InterWeight.bold), color = AppTheme.colors.text)
            Text(if (ownerName == null) strings.achievementsEmptyHint() else strings.achievementsEmptyOtherHint(), style = inter(11f, InterWeight.semibold),
                color = AppTheme.colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        else LazyVerticalGrid(GridCells.Adaptive(100.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(displayed.sortedWith(compareByDescending<AchievementRow> { it.unlocked }.thenBy { it.id in hidden && !it.unlocked }.thenByDescending { it.rarity.rawValue }), key = { it.id }) { badge ->
                Column(Modifier.fillMaxWidth().testTag("achievement_${badge.id}").surfaceCard(16.dp).clickable(role = Role.Button) { onBadge(badge.id) }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (badge.id in hidden && !badge.unlocked) Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) { Glyph(AppGlyph.lockFill, null, tint = AppTheme.colors.textTertiary, size = 28.dp) } else BadgeArt(badge)
                    Text(if (badge.id in hidden && !badge.unlocked) "???" else MeArtwork.badgeTitles[badge.id]?.let { strings.tr(it) }.orEmpty(), style = interScaled(12f, InterWeight.medium), color = AppTheme.colors.text)
                }
            }
        }
    }
}

@Composable
internal fun BadgeDetailScreen(badge: AchievementRow?, onBack: () -> Unit, unit: DistanceUnit = DistanceUnit.km) {
    val strings = appStrings
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        MeNavBar(badge?.id?.let { MeArtwork.badgeTitles[it] }?.let { strings.tr(it) } ?: strings.badges(), onBack)
        if (badge != null) {
            Spacer(Modifier.height(50.dp))
            BadgeArt(badge, 180.dp)
            Text(if (badge.unlocked) strings.unlocked() else strings.badgeDetailLocked(), Modifier.padding(20.dp), style = AppType.headerTitle, color = AppTheme.colors.text)
            MeArtwork.badgeDescriptions[badge.id]?.let { resource ->
                val figures = BadgeDefinitions.all.firstOrNull { it.id == badge.id }?.figures.orEmpty().map { it.shown(unit, strings.language.code, strings) }
                Text(strings.tr(resource, *figures.toTypedArray()), Modifier.padding(horizontal = 20.dp), style = AppType.body, color = AppTheme.colors.textSecondary)
            }
            if (badge.earnCount > 1) Text(strings.earnedTimes(badge.earnCount), style = AppType.body, color = AppTheme.colors.textSecondary)
        }
    }
}
