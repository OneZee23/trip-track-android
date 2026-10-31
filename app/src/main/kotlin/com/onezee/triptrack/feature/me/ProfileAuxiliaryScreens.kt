// iOS: LevelsView.swift, CountryPickerView.swift. Both are pushes in ProfileView's MeDest path.
package com.onezee.triptrack.feature.me

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.R
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.logic.LevelSystem
import com.onezee.triptrack.logic.from
import com.onezee.triptrack.model.DriverRank
import java.util.Locale

// iOS: LevelsView.level / GamificationModels.DriverRank. Ranges derive from the shared level rule.
internal object ProfileLevels {
    fun level(stored: Int) = stored.coerceIn(1, LevelSystem.maxLevel)
    fun range(rank: DriverRank) = (1..LevelSystem.maxLevel).filter { DriverRank.from(it) == rank }.let { it.first()..it.last() }
    fun progress(stored: Int, xp: Int) = if (level(stored) == LevelSystem.maxLevel) 1f
        else (xp.toFloat() / LevelSystem.xpForNextLevel(level(stored)).coerceAtLeast(1)).coerceIn(0f, 1f)
}

@Composable
internal fun LevelsScreen(identity: ProfileIdentity, onBack: () -> Unit) {
    val strings = appStrings
    val level = ProfileLevels.level(identity.level)
    val rank = DriverRank.from(level)
    val colors = listOf(0xFF7C828E, 0xFFB0793C, 0xFFCE9A2E, 0xFFE1A017, 0xFFEE8121, 0xFFC2452B, 0xFFD83E15,
        0xFFC82E1B, 0xFFB42121, 0xFF9C1B28, 0xFF841A32, 0xFF6F162F, 0xFF581328, 0xFF421021)
    val dark = AppTheme.isDark
    fun ink(value: DriverRank): Color {
        val raw = colors[value.ordinal].toInt()
        if (!dark) return Color(raw)
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(raw, hsv)
        hsv[1] = minOf(hsv[1], .68f); hsv[2] = maxOf(hsv[2], .70f)
        return Color(AndroidColor.HSVToColor(hsv))
    }
    val titles = listOf(R.string.driver_rank_novice, R.string.driver_rank_driver, R.string.driver_rank_traveler, R.string.driver_rank_explorer,
        R.string.driver_rank_navigator, R.string.driver_rank_trucker, R.string.driver_rank_legend, R.string.driver_rank_pioneer,
        R.string.driver_rank_nomad, R.string.driver_rank_conqueror, R.string.driver_rank_cartographer, R.string.driver_rank_odysseus,
        R.string.driver_rank_roadSpirit, R.string.driver_rank_eternal)
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding().testTag("levels_screen")) {
        MeNavBar(strings.levelsTitle(), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.surfaceCard(20.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(64.dp).background(ink(rank).copy(alpha = if (dark) .18f else .14f), CircleShape).border(3.5.dp, ink(rank), CircleShape), contentAlignment = Alignment.Center) {
                        Text(level.toString(), style = inter(26f, InterWeight.heavy), color = ink(rank))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(strings.tr(titles[rank.ordinal]), style = inter(21f, InterWeight.bold), color = ink(rank))
                        Text(strings.levelsRankOf(rank.ordinal + 1, DriverRank.entries.size, level), style = inter(13f, InterWeight.medium), color = AppTheme.colors.textSecondary)
                    }
                }
                Box(Modifier.fillMaxWidth().height(6.dp).background(AppTheme.colors.cardAlt, CircleShape)) {
                    Box(Modifier.fillMaxWidth(ProfileLevels.progress(level, identity.xp)).fillMaxHeight().background(ink(rank), CircleShape))
                }
                Text(if (level == LevelSystem.maxLevel) strings.levelsXpTotal(strings.groupedNumber(identity.xp))
                    else strings.levelsXpProgress(strings.groupedNumber(identity.xp), strings.groupedNumber(LevelSystem.xpForNextLevel(level))),
                    style = AppType.body, color = AppTheme.colors.text)
                Text(if (level == LevelSystem.maxLevel) strings.levelsMaxLevel() else strings.levelsToNextLevel(level + 1), style = AppType.meta, color = AppTheme.colors.textSecondary)
                val next = DriverRank.entries.getOrNull(rank.ordinal + 1)
                Text(next?.let { strings.levelsNextRank(strings.tr(titles[it.ordinal]), ProfileLevels.range(it).first) } ?: strings.levelsFinalRank(), style = AppType.meta, color = ink(next ?: rank))
            }
            MeSection(strings.levelsHowToEarn()) {
                MeRow(strings.levelsRuleKm(), strings.levelsRuleKmBadge())
                MeRow(strings.levelsRuleFirst(), strings.levelsRuleFirstBadge())
                MeRow(strings.levelsRuleRegion(), strings.levelsRuleRegionBadge())
                MeRow(strings.levelsRuleLong(), strings.levelsRuleLongBadge())
            }
            MeSection(strings.levelsAllRanks()) {
                DriverRank.entries.forEach { item ->
                    val range = ProfileLevels.range(item)
                    Row(Modifier.fillMaxWidth().background(if (item == rank) ink(item).copy(alpha = if (AppTheme.isDark) .18f else .14f) else Color.Transparent)
                        .padding(12.dp).testTag("levels_rank_row").semantics { selected = item == rank }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.tr(titles[item.ordinal]), Modifier.weight(1f), style = AppType.meta, color = ink(item))
                        if (item == rank) Text(strings.levelsCurrentMarker(), style = AppType.caption, color = ink(item))
                        Text(strings.levelsRankRange(range.first, range.last), style = AppType.caption, color = AppTheme.colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CountryScreen(selection: String, onSelect: (String) -> Unit, onBack: () -> Unit) {
    val strings = appStrings
    fun choose(value: String) { if (value != selection) onSelect(value); onBack() }
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding().testTag("country_picker")) {
        MeNavBar(strings.countryTitle(), onBack)
        LazyVerticalGrid(GridCells.Fixed(5), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text(strings.countryHint(), style = AppType.meta, color = AppTheme.colors.textSecondary) }
            item(span = { GridItemSpan(maxLineSpan) }) { Column(Modifier.surfaceCard(16.dp).clip(RoundedCornerShape(16.dp))) {
                listOf("", "WORLD", "NEUTRAL").forEach { code ->
                    Row(Modifier.fillMaxWidth().background(if (selection == code) AppTheme.accentBg else Color.Transparent)
                        .clickable(role = Role.Button) { choose(code) }.padding(14.dp).testTag("country_$code").semantics { selected = selection == code },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                            val flag = ProfileIdentityChoices.flag(code)
                            if (flag == null) Glyph(AppGlyph.xmark, null, tint = AppTheme.colors.textSecondary, size = 16.dp)
                            else Text(flag, style = inter(18f))
                        }
                        Text(when (code) { "WORLD" -> strings.countryWorld(); "NEUTRAL" -> strings.countryNeutral(); else -> strings.countryNone() },
                            Modifier.weight(1f), style = inter(15f, InterWeight.semibold), color = AppTheme.colors.text)
                        if (selection == code) Glyph(AppGlyph.checkmark, null, tint = AppTheme.accent, size = 16.dp)
                    }
                }
            } }
            item(span = { GridItemSpan(maxLineSpan) }) { Text(strings.countrySectionAll(), style = AppType.caption, color = AppTheme.colors.textTertiary) }
            val entries = ProfileIdentityChoices.countries + listOf(selection.uppercase(Locale.ROOT)).filter { it.length == 2 && it !in ProfileIdentityChoices.countries }
            items(entries) { code ->
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(if (selection.uppercase(Locale.ROOT) == code) AppTheme.accentBg else AppTheme.colors.card, RoundedCornerShape(14.dp))
                    .then(if (selection.uppercase(Locale.ROOT) == code) Modifier.border(1.5.dp, AppTheme.accent, RoundedCornerShape(14.dp)) else Modifier)
                    .clickable(role = Role.Button, onClickLabel = Locale.Builder().setRegion(code).build().getDisplayCountry(strings.language.locale)) { choose(code) }
                    .testTag("country_$code").semantics { selected = selection == code }, contentAlignment = Alignment.Center) {
                    Text(ProfileIdentityChoices.flag(code).orEmpty(), style = inter(22f))
                    if (selection.uppercase(Locale.ROOT) == code) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.5.dp, end = 5.5.dp).size(18.dp).background(AppTheme.accent, CircleShape), contentAlignment = Alignment.Center) {
                        Glyph(AppGlyph.checkmark, null, tint = Color.White, size = 10.dp)
                    }
                }
            }
        }
    }
}
