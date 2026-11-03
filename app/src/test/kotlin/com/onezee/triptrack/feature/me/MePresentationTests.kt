// iOS: ProfileHistoryCalendar.swift, VehicleAvatarCatalogTests.swift, ProfilePreviewNavTests.swift
package com.onezee.triptrack.feature.me

import com.onezee.triptrack.feature.garage.VehicleAvatar
import com.onezee.triptrack.logic.PlusGate
import com.onezee.triptrack.model.*
import java.time.LocalDate
import java.util.UUID
import org.junit.Test
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MePresentationTests {
    @org.junit.Before fun mainDispatcher() { kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher()) }
    @org.junit.After fun resetDispatcher() { kotlinx.coroutines.Dispatchers.resetMain() }
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
    // iOS: ProfileView.navigationDestination: the garage, the vehicle page, the drafts and the clubs keep
    // the floating bar (their content ends at CustomTabBar.clearanceAboveSafeArea); every other screen
    // pushed from Я calls `.hideAppTabBar()`.
    @Test fun theTabBarStaysOnlyOnTheFourScreensIosKeepsItOn() {
        val id = UUID.fromString("10000000-0000-4000-8000-000000000001")
        val keeps = listOf(MeRoute.Garage, MeRoute.Passport(id), MeRoute.Clubs, MeRoute.Drafts)
        keeps.forEach { assertTrue(it.keepsTabBar, "$it keeps the bar") }
        val hides = listOf(MeRoute.Profile, MeRoute.Achievements, MeRoute.Badge("first"), MeRoute.Levels, MeRoute.Country, MeRoute.Stats,
            MeRoute.Trip(id), MeRoute.Journey(id), MeRoute.SocialJourney(id),
            MeRoute.Social(com.onezee.triptrack.feature.TripsDestination.PublicProfile(id)))
        hides.forEach { assertFalse(it.keepsTabBar, "$it hides the bar") }
    }
    @Test fun twoFastPushesHaveOneDestinationAndBackKeepsTheParent() {
        val navigation = MeNavigator()
        val passport = MeRoute.Passport(UUID.fromString("10000000-0000-4000-8000-000000000001"))
        navigation.push(MeRoute.Garage); navigation.push(passport); navigation.push(passport)
        assertEquals(listOf(MeRoute.Garage, passport), navigation.path)
        navigation.pop(); assertEquals(MeRoute.Garage, navigation.current)
        navigation.pop(); assertNull(navigation.current)
    }
    // iOS: ProfilePreviewNavTests.testDoubleTapDoesNotStackTheSameScreenTwice.
    @Test fun tripPushFromASharedScreenUsesTheSameMeEntryDespiteADifferentPreview() {
        val id = UUID.fromString("10000000-0000-4000-8000-000000000001")
        val navigation = MeNavigator()
        navigation.push(MeRoute.Trip(id))
        navigation.push(com.onezee.triptrack.feature.TripsDestination.TripDetail(id, Trip(id = id)))
        assertEquals(1, navigation.path.size)
        assertEquals(MeRoute.Trip(id), navigation.current)
    }
    // iOS: ProfilePreviewNavTests.testSameScreenDeeperInThePathStillPushes.
    @Test fun aTripDeeperInThePathStillPushesAndReturnsToItsJourney() {
        val trip = MeRoute.Trip(UUID.fromString("10000000-0000-4000-8000-000000000001"))
        val journey = MeRoute.Journey(UUID.fromString("10000000-0000-4000-8000-000000000002"))
        val navigation = MeNavigator(listOf(trip, journey))
        navigation.push(com.onezee.triptrack.feature.TripsDestination.TripDetail(trip.id))
        assertEquals(listOf(trip, journey, trip), navigation.path)
        navigation.pop()
        assertEquals(journey, navigation.current)
    }
    // iOS: ProfilePreviewNavTests.testDifferentDestinationStillPushes.
    @Test fun differentJourneysAreDifferentMeScreens() {
        val first = UUID.fromString("10000000-0000-4000-8000-000000000001")
        val second = UUID.fromString("10000000-0000-4000-8000-000000000002")
        val navigation = MeNavigator()
        navigation.push(com.onezee.triptrack.feature.TripsDestination.JourneyDetail(first))
        navigation.push(com.onezee.triptrack.feature.TripsDestination.JourneyDetail(second))
        assertEquals(listOf(MeRoute.Journey(first), MeRoute.Journey(second)), navigation.path)
    }
    @Test fun restoringTheTabKeepsItsLiveTripEntryAndAFreshProcessRestoresSubjects() {
        val id = UUID.fromString("10000000-0000-4000-8000-000000000001")
        val owner = MeNavigator(listOf(MeRoute.Trip(id)))
        val entry = owner.stack.entryIds.single()
        assertSame(owner, MeNavigator.saver(owner).restore(listOf("trip:$id")))
        assertEquals(entry, owner.stack.entryIds.single())
        val fresh = MeNavigator()
        assertSame(fresh, MeNavigator.saver(fresh).restore(listOf("drafts", "trip:$id")))
        assertEquals(listOf(MeRoute.Drafts, MeRoute.Trip(id)), fresh.path)
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
