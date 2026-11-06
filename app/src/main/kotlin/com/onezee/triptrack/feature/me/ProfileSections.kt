// iOS: ProfileView.swift (profileSections, garageSection, clubsSection, firstTripWelcomeCard, journalSignInRow,
// pinsElsewhereRow), ProfileAchievementsSection.swift, ProfileClubsRow.swift, BadgeRarityStyle.swift
package com.onezee.triptrack.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.garage.VehicleSpritePlate
import com.onezee.triptrack.logic.dashboardUnit
import com.onezee.triptrack.logic.displayOdometerKm
import com.onezee.triptrack.logic.odometer
import com.onezee.triptrack.model.BadgeRarity
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.Vehicle

/** iOS ProfileSectionLabel / AppType.section: 16 bold in ink, ordinary case. */
@Composable
internal fun ProfileSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = AppType.section, letterSpacing = AppType.sectionTracking, color = AppTheme.colors.text)
}

/** The header of a section on «Я»: its label and, at the right, the way into the whole of it. */
@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        ProfileSectionLabel(title)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** «Все ›»-style link of a section header: 13 semibold in the secondary ink and a 13 pt chevron. */
@Composable
private fun SectionLink(text: String, tag: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(Modifier.clickable(role = Role.Button) { haptics.tap(); onClick() }.testTag(tag), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (text.isNotEmpty()) Text(text, style = inter(13f, InterWeight.semibold), color = AppTheme.colors.textSecondary, maxLines = 1)
        Glyph(AppGlyph.chevronRight, null, tint = AppTheme.colors.textSecondary, size = symbolBox(13f))
    }
}

// ---------------------------------------------------------------------------------------------
// The toggle

/**
 * iOS profileSections: «Гараж · Достижения · Клубы» in the secondary ink on a plain card, a down
 * chevron that turns over when it is open. Rare destinations stay one tap away, above the history.
 */
@Composable
internal fun ProfileSectionsToggle(open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, showPro: Boolean = false) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val title = buildList {
        add(strings.garage())
        if (showPro) add("PRO")
        add(strings.achievementsSection())
        add(strings.clubsTitle())
    }.joinToString(" · ")
    val state = if (open) strings.historySectionsExpanded() else strings.historySectionsCollapsed()
    PressableCard(onClick = { haptics.tap(); onToggle() }, modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth().semantics { stateDescription = state }.testTag("profile_sections_toggle")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(colors.card, ContinuousRoundedRectangle(16.dp)).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = interScaled(14f, InterWeight.semibold), color = colors.textSecondary)
            Glyph(AppGlyph.chevronDown, null, Modifier.rotate(if (open) 180f else 0f), tint = colors.textSecondary, size = symbolBox(13f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Achievements

/** What the achievements section draws, resolved once per set of badges (iOS AchievementsContent.build). */
internal data class AchievementsSummary(val featured: AchievementRow?, val strip: List<AchievementRow>, val overflow: Int, val unlocked: Int, val total: Int) {
    companion object {
        /** How many discs fit beside the «+N» capsule. */
        const val stripLimit = 6

        /**
         * The badge with the highest rarity is the featured one (a tie keeps the earlier, i.e. the
         * catalogue order); the rest follow rarest first, again in catalogue order inside a tier.
         * [rows] already leave out the hidden badges that are still locked, so the denominator is
         * what the achievements screen one tap away counts.
         */
        fun build(rows: List<AchievementRow>): AchievementsSummary {
            val unlocked = rows.filter { it.unlocked }
            if (unlocked.isEmpty()) return AchievementsSummary(null, emptyList(), 0, 0, rows.size)
            val featured = unlocked.drop(1).fold(unlocked.first()) { best, badge -> if (badge.rarity.rawValue > best.rarity.rawValue) badge else best }
            val rest = unlocked.withIndex().filter { it.value.id != featured.id }
                .sortedWith(compareByDescending<IndexedValue<AchievementRow>> { it.value.rarity.rawValue }.thenBy { it.index }).map { it.value }
            val strip = rest.take(stripLimit)
            return AchievementsSummary(featured, strip, rest.size - strip.size, unlocked.size, rows.size)
        }
    }
}

/** iOS BadgeRarityStyle: the ring of a tier, in sRGB; the row tint and the caption ink derive from it. */
private fun BadgeRarity.ring(): Triple<Float, Float, Float> = when (this) {
    BadgeRarity.common -> Triple(.55f, .57f, .60f)
    BadgeRarity.uncommon -> Triple(.18f, .68f, .35f)
    BadgeRarity.rare -> Triple(.20f, .55f, .90f)
    BadgeRarity.epic -> Triple(.60f, .35f, .80f)
    BadgeRarity.legendary -> Triple(.90f, .65f, .10f)
    BadgeRarity.exclusive -> Triple(.86f, .20f, .58f)
}

/** The featured row's wash: the ring over white at 14 %, or over the dark card at 10 % alpha. */
internal fun BadgeRarity.rowTint(dark: Boolean): Color {
    val (r, g, b) = ring()
    return if (dark) Color(r, g, b, .10f) else Color(1 - (1 - r) * .14f, 1 - (1 - g) * .14f, 1 - (1 - b) * .14f)
}

/** The rarity caption's ink: the ring shaded by 0.22 in light, lifted by 0.22 in dark. */
internal fun BadgeRarity.chipText(dark: Boolean): Color {
    val (r, g, b) = ring()
    return if (dark) Color(r + (1 - r) * .22f, g + (1 - g) * .22f, b + (1 - b) * .22f) else Color(r * .78f, g * .78f, b * .78f)
}

@Composable
private fun BadgeRarity.title(): String = appStrings.tr(when (this) {
    BadgeRarity.common -> com.onezee.triptrack.designsystem.R.string.badgeRarity_common
    BadgeRarity.uncommon -> com.onezee.triptrack.designsystem.R.string.badgeRarity_uncommon
    BadgeRarity.rare -> com.onezee.triptrack.designsystem.R.string.badgeRarity_rare
    BadgeRarity.epic -> com.onezee.triptrack.designsystem.R.string.badgeRarity_epic
    BadgeRarity.legendary -> com.onezee.triptrack.designsystem.R.string.badgeRarity_legendary
    BadgeRarity.exclusive -> com.onezee.triptrack.designsystem.R.string.badgeRarity_exclusive
})

/** iOS ProfileAchievementsSection: the progress counter in the header, one featured badge, a strip of the next rarest. */
@Composable
internal fun AchievementsSection(rows: List<AchievementRow>, onAll: () -> Unit, onBadge: (String) -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val summary = androidx.compose.runtime.remember(rows) { AchievementsSummary.build(rows) }
    Column(modifier.fillMaxWidth()) {
        SectionHeader(strings.achievementsSection()) { SectionLink(strings.achievementsProgress(summary.unlocked, summary.total), "profile_achievements_all", onAll) }
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().surfaceCard(16.dp).padding(14.dp).testTag("profile_achievements"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val featured = summary.featured
            if (featured == null) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.achievementsEmpty(), style = inter(13f, InterWeight.bold), color = colors.text)
                    Text(strings.achievementsEmptyHint(), style = inter(11f, InterWeight.semibold), color = colors.textSecondary, textAlign = TextAlign.Center)
                }
            } else {
                FeaturedBadge(featured, onBadge)
                if (summary.strip.isNotEmpty() || summary.overflow > 0) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    summary.strip.forEach { badge ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Box(Modifier.clickable(role = Role.Button) { onBadge(badge.id) }.testTag("profile_achievement_${badge.id}")) { BadgeTile(badge, 40.dp) }
                        }
                    }
                    if (summary.overflow > 0) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(34.dp, 30.dp).background(colors.cardAlt, CircleShape).clickable(role = Role.Button, onClick = onAll), contentAlignment = Alignment.Center) {
                            Text("+${summary.overflow}", style = inter(12f, InterWeight.semibold), color = colors.textSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedBadge(badge: AchievementRow, onBadge: (String) -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val dark = AppTheme.isDark
    val title = MeArtwork.badgeTitles[badge.id]?.let { strings.tr(it) }.orEmpty()
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().background(badge.rarity.rowTint(dark), shape).clickable(role = Role.Button) { onBadge(badge.id) }
        .padding(start = 10.dp, end = 12.dp).padding(vertical = 8.dp).testTag("profile_achievement_featured"),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        // A pinned badge stands on its own plate, so there is none under it here.
        BadgeArt(badge, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = inter(13f, InterWeight.bold), color = colors.text, maxLines = 1)
            Text(badge.rarity.title(), style = inter(11f, InterWeight.semibold), color = badge.rarity.chipText(dark), maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The garage

/** iOS VehicleLevelSystem.color(for:): «LVL 28» in the colour of its decade. */
internal fun vehicleLevelColor(level: Int): Color {
    val decade = (level / 10).coerceIn(0, 10)
    return listOf(
        Color(155, 155, 165), Color(160, 113, 61), AppTheme.accent, Color(217, 58, 30), Color(180, 35, 24), Color(155, 30, 60),
        Color(122, 31, 77), Color(91, 33, 96), Color(74, 30, 81), Color(51, 32, 63), Color(30, 30, 35),
    )[decade]
}

/**
 * iOS garageSection: the label with «Весь транспорт ›», then ONE row, the main vehicle (the rest are
 * one tap away behind the link), or, with none, a row that says what a vehicle is for here.
 */
@Composable
internal fun GarageSection(vehicles: List<Vehicle>, main: Vehicle?, unit: DistanceUnit, onGarage: () -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    Column(modifier.fillMaxWidth()) {
        SectionHeader(strings.garage()) { SectionLink(strings.garageAllVehicles(), "settings_garage", onGarage) }
        Box(Modifier.padding(horizontal = 16.dp)) {
            if (vehicles.isEmpty()) GarageEmptyRow(onGarage) else if (main != null) GarageVehicleRow(main, unit, onGarage)
        }
    }
}

@Composable
private fun GarageVehicleRow(vehicle: Vehicle, unit: DistanceUnit, onGarage: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Box(Modifier.fillMaxWidth().surfaceCard(16.dp).testTag("profile_garage_card")) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { haptics.tap(); onGarage() }.padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // The silhouette, not the photograph: a photo in a small tile has no size of its own and stretches it.
            VehicleSpritePlate(vehicle, 44.dp, cornerRadius = 10.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(vehicle.name.ifEmpty { strings.unnamedVehicle() }, Modifier.weight(1f, fill = false), style = inter(14.5f, InterWeight.bold), color = colors.text, maxLines = 1)
                    if (!vehicle.visibleToOthers) Glyph(AppGlyph.lockFill, strings.vehicleHiddenFromOthers(), tint = colors.textTertiary, size = symbolBox(10f))
                    Glyph(AppGlyph.checkmark, strings.vehicleMainLabel(), tint = AppTheme.accent, size = symbolBox(11f))
                }
                Text(Measure.odometer(vehicle.displayOdometerKm, vehicle.dashboardUnit(unit), strings.language.code, strings), style = inter(11.5f, InterWeight.medium),
                    color = colors.textTertiary, maxLines = 1)
            }
            Text("LVL ${vehicle.level}", style = pressStart2P(9f), color = vehicleLevelColor(vehicle.level), maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun GarageEmptyRow(onGarage: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth().surfaceCard(16.dp).clickable(role = Role.Button) { haptics.tap(); onGarage() }.padding(horizontal = 14.dp, vertical = 12.dp).testTag("profile_garage_empty"),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(AppTheme.accentBg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            MeGlyphIcon(MeGlyph.carTwoFill, null, tint = AppTheme.accent, size = 26.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(strings.garageEmptyTitle(), style = inter(14.5f, InterWeight.bold), color = colors.text, maxLines = 1)
            Text(strings.garageEmptyBody(), style = inter(11.5f), color = colors.textSecondary)
        }
        Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = symbolBox(13f))
    }
}

// ---------------------------------------------------------------------------------------------
// Clubs

/**
 * iOS clubsSection with ProfileClubsRow: the real number of people waiting or the invitation to be
 * first, and «you are in» for this phone. [subtitle] is built by [clubsRowSubtitle].
 */
@Composable
internal fun ClubsSection(subtitle: String, onClubs: () -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Column(modifier.fillMaxWidth()) {
        SectionHeader(strings.clubsTitle())
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().surfaceCard(16.dp).clickable(role = Role.Button) { haptics.tap(); onClubs() }
            .padding(horizontal = 14.dp, vertical = 12.dp).testTag("profile_clubs_row"),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(AppTheme.accentBg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                MeGlyphIcon(MeGlyph.personTwoFill, null, tint = AppTheme.accent, size = 24.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(strings.groupsComingTitle(), style = inter(14.5f, InterWeight.bold), color = colors.text, maxLines = 1)
                Text(subtitle, style = inter(11.5f), color = colors.textSecondary)
            }
            Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = symbolBox(13f))
        }
    }
}

/** iOS ProfileClubsRow.subtitle: a phone on the list counts itself, so «be the first» can never be said to it. */
internal fun clubsRowSubtitle(total: Int, joined: Boolean, strings: com.onezee.triptrack.designsystem.strings.AppStrings): String {
    val count = maxOf(total, if (joined) 1 else 0)
    val line = if (count > 0) strings.groupsWaitlistCount(count) else strings.groupsWaitlistFirst()
    return if (joined) "$line · ${strings.clubsRowJoined()}" else line
}

// ---------------------------------------------------------------------------------------------
// First run, signed out, pins on another phone

/** iOS recordFirstTripRow: with no trips yet, the one action that matters, in a card. */
@Composable
internal fun FirstTripWelcomeCard(onRecord: () -> Unit, onManualTrip: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    if (onManualTrip == null) {
        PressableCard(onClick = { haptics.tap(); onRecord() }, modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth().testTag("profile_welcome_record")) {
            Row(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(AppTheme.accentBg, CircleShape), contentAlignment = Alignment.Center) {
                    MeGlyphIcon(MeGlyph.carSideFill, null, tint = AppTheme.accent, size = 34.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.profileRecordYourFirst(), style = inter(15f, InterWeight.semibold), color = colors.text)
                    Text(strings.profileYourKilometersStreaks(), style = inter(12f), color = colors.textSecondary)
                }
                Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = symbolBox(13f))
            }
        }
        return
    }
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().surfaceCard(16.dp).testTag("profile_welcome_record")) {
        Row(Modifier.fillMaxWidth().clickable { haptics.tap(); onRecord() }.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).background(AppTheme.accentBg, CircleShape), contentAlignment = Alignment.Center) {
                MeGlyphIcon(MeGlyph.carSideFill, null, tint = AppTheme.accent, size = 34.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.profileRecordYourFirst(), style = inter(15f, InterWeight.semibold), color = colors.text)
                Text(strings.profileYourKilometersStreaks(), style = inter(12f), color = colors.textSecondary)
            }
            Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = symbolBox(13f))
        }
        Text(strings.tr(com.onezee.triptrack.designsystem.R.string.manualTripEntry),
            Modifier.fillMaxWidth().clickable { haptics.tap(); onManualTrip() }.padding(horizontal = 14.dp, vertical = 12.dp),
            style = inter(14f, InterWeight.semibold), color = AppTheme.accent)
    }
}

/** iOS journalSignInRow: with trips already on the phone, a guest gets one compact line and a dark pill, not the whole pitch. */
@Composable
internal fun JournalSignInRow(expired: Boolean, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Row(modifier.padding(horizontal = 16.dp).fillMaxWidth().surfaceCard().testTag("profile_guest_signin").padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (expired) strings.sessionExpiredTitle() else strings.profileJournalSignIn(), Modifier.weight(1f), style = interScaled(14f, InterWeight.medium), color = colors.text)
        PressableCard(onClick = { haptics.tap(); onSignIn() }, modifier = Modifier.testTag("profile_journal_signin_button")) {
            Text(strings.signInShort(), Modifier.background(colors.text, RoundedCornerShape(12.dp)).padding(horizontal = 13.dp, vertical = 9.dp),
                style = interScaled(13f, InterWeight.semibold), color = colors.card, maxLines = 1)
        }
    }
}

/** iOS pinsElsewhereRow: pins from another phone, a plain line and an accent link, no card. */
@Composable
internal fun PinsElsewhereRow(count: Int, onUnpin: () -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val haptics = rememberHaptics()
    Row(modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(strings.profileShowcaseElsewhere(count), Modifier.weight(1f), style = interScaled(13f), color = AppTheme.colors.textSecondary)
        PressableCard(onClick = { haptics.tap(); onUnpin() }, modifier = Modifier.testTag("profile_showcase_unpin_elsewhere")) {
            Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                Text(strings.profileUnpin(), style = interScaled(13f, InterWeight.semibold), color = AppTheme.accent, maxLines = 1)
            }
        }
    }
}
