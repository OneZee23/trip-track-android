// iOS: ProfileHistoryCalendar.swift, VehicleAvatarCatalogTests.swift, ProfilePreviewNavTests.swift
package com.onezee.triptrack.feature.me

import com.onezee.triptrack.feature.garage.VehicleAvatar
import com.onezee.triptrack.logic.PlusGate
import com.onezee.triptrack.model.*
import java.time.LocalDate
import java.util.UUID
import org.junit.Test
import kotlin.test.*

class MePresentationTests {
    private val first = LocalDate.of(2026, 10, 10)
    @Test fun sameDayTwiceClearsAndEarlierDaySwapsTheRange() {
        assertEquals(CalendarRange(first), CalendarRange().tap(first))
        assertEquals(CalendarRange(), CalendarRange(first).tap(first))
        assertEquals(CalendarRange(first.minusDays(2), first), CalendarRange(first).tap(first.minusDays(2)))
        assertEquals(CalendarRange(first, first.plusDays(2)), CalendarRange(first).tap(first.plusDays(2)))
        assertEquals(CalendarRange(first.plusDays(1)), CalendarRange(first, first.plusDays(2)).tap(first.plusDays(1)))
    }
    @Test fun rangeIncludesBothEndsAndEveryEmptyDayFilters() {
        val range = CalendarRange(first, first.plusDays(2))
        assertTrue(range.contains(first)); assertTrue(range.contains(first.plusDays(1))); assertTrue(range.contains(first.plusDays(2)))
        assertFalse(range.contains(first.minusDays(1))); assertFalse(range.contains(first.plusDays(3)))
    }
    @Test fun heatUsesQuartilesAndALoneDayIsThePeak() {
        assertNull(calendarHeatStep(0.0, 100.0))
        assertEquals(0, calendarHeatStep(25.0, 100.0))
        assertEquals(1, calendarHeatStep(50.0, 100.0))
        assertEquals(2, calendarHeatStep(75.0, 100.0))
        assertEquals(3, calendarHeatStep(100.0, 100.0))
        assertEquals(3, calendarHeatStep(5.0, 0.0))
    }
    @Test fun twoFastPushesHaveOneDestinationAndBackKeepsTheParent() {
        val navigation = MeNavigator()
        val passport = MeRoute.Passport(UUID.fromString("10000000-0000-4000-8000-000000000001"))
        navigation.push(MeRoute.Garage); navigation.push(passport); navigation.push(passport)
        assertEquals(listOf(MeRoute.Garage, passport), navigation.path)
        navigation.pop(); assertEquals(MeRoute.Garage, navigation.current)
        navigation.pop(); assertNull(navigation.current)
    }
    @Test fun legacyEmojiKeepsItsColourAndTakesTheStyle() {
        assertEquals("pixel_car_red", VehicleAvatar.asset("car", "🚗"))
        assertEquals("pixel_crossover_blue", VehicleAvatar.asset("crossover", "🚙"))
        assertEquals("red", VehicleAvatar.drawnColor("🏎️"))
        assertEquals("green", VehicleAvatar.drawnColor("🛻"))
        assertEquals("pixel_motorcycle_silver", VehicleAvatar.asset(Vehicle(avatarEmoji = "🏍️", type = VehicleType.moto)))
    }
    @Test fun everyLegacyEmojiIsDrawnWithOrWithoutTheVariationSelector() {
        listOf("🏎", "🚗", "🏍", "🚙", "🛻", "🏁", "🗺", "⛽").forEach { emoji ->
            listOf(emoji, emoji + "\uFE0F").forEach { assertNotNull(VehicleAvatar.asset("car", it)) }
        }
        assertNull(VehicleAvatar.asset("car", "🦄"))
        assertEquals("pixel_car_red", VehicleAvatar.asset("hovercraft", "pixel_car_red"))
        VehicleAvatar.styles.forEach { style -> VehicleAvatar.colors.forEach { color -> assertTrue("pixel_${style}_$color" in MeArtwork.drawables) } }
    }
    @Test fun everyProEntryUsesTheHiddenStorefrontGate() {
        PlusFeature.entries.forEach { assertEquals(PlusAccessLevel.hidden, MePlusGate.access(it)) }
        assertEquals(11, MePlusGate.backgrounds.size)
        assertTrue(MePlusGate.backgrounds.none { it.rawValue.startsWith("plus_") })
    }
    @Test fun profileIdentityPresetsAndLocalUsernameKeepTheIphoneContract() {
        assertEquals(12, ProfileIdentityChoices.avatars.size)
        assertEquals(listOf("😎", "🙂", "😄", "🤠"), ProfileIdentityChoices.avatars.take(4))
        assertEquals("alex_01", ProfileIdentityChoices.username("@@Alex_01"))
        assertEquals(20, ProfileIdentityChoices.username("a".repeat(25)).length)
        assertFalse(ProfileIdentityChoices.validUsername("ab")); assertFalse(ProfileIdentityChoices.validUsername("ник"))
        assertTrue(ProfileIdentityChoices.validUsername("abc._123"))
        assertNull(ProfileIdentityChoices.flag("")); assertEquals("🌍", ProfileIdentityChoices.flag("WORLD"))
        assertEquals("🏳️", ProfileIdentityChoices.flag("NEUTRAL")); assertEquals("🇷🇺", ProfileIdentityChoices.flag("RU"))
    }
}
