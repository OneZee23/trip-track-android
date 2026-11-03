// iOS: ProfileHeroCard.swift (name, caption), ProfileAchievementsSection.swift (AchievementsContent.build),
// ProfileClubsRow.subtitle, ProfileHistoryCalendar.swift (dayFill, dayNumberColor), GamificationModels.swift (VehicleLevelSystem.color)
package com.onezee.triptrack.feature.me

import androidx.compose.ui.graphics.Color
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.AppColors
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.model.BadgeCategory
import com.onezee.triptrack.model.BadgeRarity
import com.onezee.triptrack.model.Vehicle
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MeLogicTests {
    private fun strings(language: AppLanguage = AppLanguage.ru) = AppStrings(RuntimeEnvironment.getApplication(), language, ZoneId.of("UTC"))

    // ---- The hero ----

    @Test fun aNameIsShownWhateverTheSession() {
        assertEquals(HeroName("Нико", false), heroDisplayName("  Нико ", signedIn = true, strings()))
        assertEquals(HeroName("Нико", false), heroDisplayName("Нико", signedIn = false, strings()))
    }

    @Test fun aSignedInAccountWithoutANameGetsTheDimPromptAndAGuestIsYou() {
        assertEquals(HeroName("Добавьте имя", true), heroDisplayName("", signedIn = true, strings()))
        assertEquals(HeroName("Добавьте имя", true), heroDisplayName("   ", signedIn = true, strings()))
        assertEquals(HeroName("Вы", false), heroDisplayName("", signedIn = false, strings()))
    }

    @Test fun theCaptionJoinsMakeAndModelWithASpaceThenTheYearThenThePower() {
        val polo = Vehicle(make = "Volkswagen", model = "Polo", year = 2018)
        // ME-12: «Volkswagen Polo · 2018 · 110 л.с.», not every part dot-separated.
        assertEquals("Volkswagen Polo · 2018 · 110 л.с.", heroCaption(polo, 110.0, strings()))
        assertEquals("Volkswagen Polo · 2018", heroCaption(polo, null, strings()))
        assertEquals("Volkswagen Polo · 2018 · 79.6 л.с.", heroCaption(polo, 79.6, strings()))
        assertEquals("Polo", heroCaption(Vehicle(make = "", model = "Polo", year = 0), null, strings()))
        assertEquals("", heroCaption(Vehicle(), null, strings()))
    }

    // ---- The sections ----

    private fun badge(id: String, rarity: BadgeRarity, unlocked: Boolean = true) = AchievementRow(id, BadgeCategory.distance, rarity, unlocked, if (unlocked) 1 else 0)

    @Test fun theRarestBadgeIsFeaturedAndATieKeepsTheEarlierOne() {
        val summary = AchievementsSummary.build(listOf(badge("a", BadgeRarity.common), badge("b", BadgeRarity.epic), badge("c", BadgeRarity.epic), badge("d", BadgeRarity.rare),
            badge("e", BadgeRarity.legendary, unlocked = false)))
        assertEquals("b", summary.featured?.id)
        // The rest follow rarest first, and in catalogue order inside a tier.
        assertEquals(listOf("c", "d", "a"), summary.strip.map { it.id })
        assertEquals(0, summary.overflow)
        assertEquals(4, summary.unlocked); assertEquals(5, summary.total)
    }

    @Test fun theStripHoldsSixAndTheRestIsACount() {
        val rows = (1..10).map { badge("b$it", BadgeRarity.common) }
        val summary = AchievementsSummary.build(rows)
        assertEquals("b1", summary.featured?.id)
        assertEquals(6, summary.strip.size)
        assertEquals(3, summary.overflow)
    }

    @Test fun nothingUnlockedHasNoFeaturedBadge() {
        val summary = AchievementsSummary.build(listOf(badge("a", BadgeRarity.rare, unlocked = false)))
        assertNull(summary.featured); assertTrue(summary.strip.isEmpty()); assertEquals(0, summary.unlocked); assertEquals(1, summary.total)
    }

    @Test fun aPhoneOnTheWaitlistCountsItselfSoBeFirstIsNeverSaidToIt() {
        assertEquals(strings().groupsWaitlistFirst(), clubsRowSubtitle(0, false, strings()))
        assertEquals(strings().groupsWaitlistCount(1) + " · " + strings().clubsRowJoined(), clubsRowSubtitle(0, true, strings()))
        assertEquals(strings().groupsWaitlistCount(128), clubsRowSubtitle(128, false, strings()))
        assertEquals(strings().groupsWaitlistCount(128) + " · " + strings().clubsRowJoined(), clubsRowSubtitle(128, true, strings()))
    }

    @Test fun aVehicleLevelTakesTheColourOfItsDecadeAndTheLastDecadeIsTheInk() {
        assertEquals(Color(155, 155, 165), vehicleLevelColor(1)); assertEquals(Color(155, 155, 165), vehicleLevelColor(9))
        assertEquals(Color(160, 113, 61), vehicleLevelColor(10)); assertEquals(AppTheme.accent, vehicleLevelColor(28))
        assertEquals(Color(30, 30, 35), vehicleLevelColor(100)); assertEquals(Color(30, 30, 35), vehicleLevelColor(240))
    }

    // ---- The calendar ----

    private val light = AppColors.light
    private fun look(heat: Int? = null, future: Boolean = false, range: Boolean = false, endpoint: Boolean = false, today: Boolean = false, colors: AppColors = light, dark: Boolean = false) =
        calendarDayLook(heat, future, range, endpoint, today, colors, dark)

    @Test fun aDayToComeIsTheCardAltAtFortyPercentWithAFaintNumeral() {
        // ME-18
        val day = look(future = true)
        assertEquals(light.cardAlt.copy(alpha = light.cardAlt.alpha * .4f), day.fill)
        assertEquals(light.textTertiary.copy(alpha = light.textTertiary.alpha * .35f), day.numeral)
    }

    @Test fun theEndsOfARangeAreInvertedAndTheDaysBetweenAreTheInkAtSixteenPercent() {
        val end = look(heat = 2, range = true, endpoint = true)
        assertEquals(light.text, end.fill); assertEquals(light.card, end.numeral)
        val between = look(heat = 2, range = true)
        assertEquals(light.text.copy(alpha = light.text.alpha * .16f), between.fill); assertEquals(light.text, between.numeral)
    }

    @Test fun todayHasATwoPointRingInTheInkAndInTheCardColourInsideAnEnd() {
        assertEquals(light.text, look(today = true).ring)
        assertEquals(light.card, look(today = true, range = true, endpoint = true).ring)
        assertNull(look().ring)
    }

    @Test fun anEmptyDayHasATertiaryNumeralAndOnlyThePalestStepKeepsTheDarkOne() {
        assertEquals(light.textTertiary, look().numeral)
        assertEquals(light.text, look(heat = 0).numeral)
        for (step in 1..3) assertEquals(Color.White, look(heat = step).numeral)
    }

    @Test fun theDarkCardCarriesDarkerTwinsOfTheHeatSteps() {
        assertEquals(Color(0xFF2C2A28), look(dark = true, colors = AppColors.dark).fill)
        assertEquals(Color(0xFF423C38), look(heat = 0, dark = true, colors = AppColors.dark).fill)
        assertEquals(Color(0xFFFCDBB8), look(heat = 0).fill)
    }

    @Test fun theMonthIsTheStandaloneNameCapitalised() {
        assertEquals("Ноябрь", monthName(LocalDate.of(2026, 11, 3), Locale("ru")))
        assertEquals("November", monthName(LocalDate.of(2026, 11, 3), Locale.ENGLISH))
    }

    @Test fun theEndsOfARangeAreItsFirstAndLastDay() {
        val range = CalendarRange(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 12))
        assertTrue(range.isEndpoint(LocalDate.of(2026, 10, 9))); assertTrue(range.isEndpoint(LocalDate.of(2026, 10, 12)))
        assertFalse(range.isEndpoint(LocalDate.of(2026, 10, 10))); assertTrue(range.contains(LocalDate.of(2026, 10, 10)))
    }
}
