// iOS: ProfileCardShotTests, ProfileMonthlyJournalTests, DraftsFlowShotTests.testMeCard: what the Я page does and where it stands
package com.onezee.triptrack.feature.me

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.model.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * The Я page on the iPhone's numbers (3 November audit, ME-01…ME-37): the hero card is the first
 * thing on the page, the prompts and rows are the iOS ones, the history is a header, a search bar,
 * a calendar and real cards and tiles. Positions are measured in dp against the iPhone's points.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi")
class MeScreenTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    private val events = mutableListOf<String>()
    private val today = Instant.parse("2026-10-31T12:00:00Z")

    /** The real page; every callback writes what was asked for into [events]. */
    private fun show(state: MeState = MeFixtures.page, signedIn: Boolean = true, covers: List<ProfileCoverPhoto> = emptyList(), promptHidden: Boolean = false,
                     fontScale: Float = 1f, safeTop: androidx.compose.ui.unit.Dp = 0.dp, onState: (MeState) -> MeState = { it }) {
        compose.setContent {
            TripTrackTheme(false, AppLanguage.ru, reduceMotion = true) {
                // Inside the theme: it provides the density itself.
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides if (fontScale == 1f) density else Density(density.density, fontScale)) {
                    CompositionLocalProvider(LocalInspectionMode provides true, com.onezee.triptrack.ui.LocalClock provides Clock.fixed(today, ZoneOffset.UTC)) {
                        Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
                            MeContent(onState(state), DistanceUnit.km, { events += "settings" }, { events += "garage" }, { events += "vehicle" }, { events += "add_vehicle" },
                                { events += "achievements" }, { events += "clubs" }, { events += "drafts" }, { events += "profile" }, { events += "level" },
                                { events += "query:$it" }, { from, to -> events += "range:$from:$to" }, { events += "mode:${it.name}" }, { events += "clear" },
                                { events += "trip:${it.id}" }, { events += "journey" }, { events += "pin:$it" }, { events += "reload" },
                                account = { Box(Modifier.testTag("account_slot").size(1.dp)) },
                                coverPhotos = covers, onPhotos = { events += "photos" }, coverPromptHidden = promptHidden, onHideCoverPrompt = { events += "hide_cover" },
                                onUnpinElsewhere = { events += "unpin" }, onComposeJourney = { events += "compose:${it.id}" },
                                onStats = { events += "stats" }, signedIn = signedIn, onSignIn = { events += "sign_in" }, onBadge = { events += "badge:$it" },
                                onRecord = { events += "record" }, safeTop = safeTop)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot()
    /** A text's own box: the clickable row round it merges its children, so the merged tree would answer with the row's. */
    private fun textBounds(text: String, substring: Boolean = false) = compose.onNodeWithText(text, substring = substring, useUnmergedTree = true).getBoundsInRoot()
    private fun near(expected: Float, actual: androidx.compose.ui.unit.Dp, tolerance: Float = 0.75f, what: String = "") =
        assertTrue(abs(expected - actual.value) <= tolerance, "$what expected $expected dp, was ${actual.value} dp")

    // ---- Hero ----------------------------------------------------------------------------------

    @Test fun theHeroCardIsTheFirstThingOnThePageUnderTheTitleOfNobody() {
        show()
        // ME-01: iOS has no header on the tab; the card stands 4 pt under the safe area (the render has none).
        near(4f, bounds("profile_hero").top, what = "hero top")
        compose.onNodeWithText("Я").assertDoesNotExist()
    }

    @Test fun theLevelIsARowOfTextWithTheRankAndAChevron() {
        show()
        // ME-02: «LVL 8 Водитель ›», not a capsule with a number.
        compose.onNodeWithTag("profile_lvl_pill").assertTextEquals("LVL 8", "Водитель")
        compose.onNodeWithTag("profile_lvl_pill").performClick()
        assertEquals(listOf("level"), events)
    }

    @Test fun theNumbersStandAtTheEdgesOfTheCardNotInThreeCentredColumns() {
        show()
        // ME-05: the first column at the card's content edge (16 + 20), the last ending at its right edge.
        near(36f, textBounds("58").left, 1f, "first value")
        near(375f, textBounds("регионов").right, 1f, "last label")
        compose.onNodeWithTag("profile_stats_strip").performClick()
        assertEquals(listOf("stats"), events)
    }

    @Test fun theAvatarTheNameAndTheGearOpenTheirScreens() {
        show()
        compose.onNodeWithTag("profile_avatar").performClick()
        compose.onNodeWithTag("profile_identity").performClick()
        compose.onNodeWithTag("profile_gear").performClick()
        assertEquals(listOf("profile", "profile", "settings"), events)
        // ME-03: a 44 target round the gear.
        assertTrue(bounds("profile_gear").width.value >= 44f)
    }

    @Test fun theLargestTextStacksTheNamesUnderTheAvatarAndTheNumbersInAColumn() {
        show(fontScale = 2f)
        // ME-31: at the accessibility sizes the numbers form a column, and the name goes under the avatar.
        val trips = textBounds("58")
        val km = textBounds("155", substring = true)
        assertEquals(trips.left, km.left)
        assertTrue(km.top > trips.bottom, "the second number stands under the first: $trips $km")
        assertTrue(compose.onNodeWithTag("profile_identity").getBoundsInRoot().top >= bounds("profile_avatar").bottom, "the name is under the avatar row")
    }

    @Test fun theNumbersStandInOneRowAtTheNormalSize() {
        show()
        assertEquals(textBounds("58").top, textBounds("155", substring = true).top)
    }

    @Test fun aSignedInAccountWithNoNameShowsThePrompt() {
        show(onState = { it.copy(hero = it.hero.copy(signedIn = true, identity = it.hero.identity.copy(name = ""))) })
        // ME-37: «Добавьте имя», drawn dimmer; the guest name «Вы» is for someone signed out.
        compose.onNodeWithText("Добавьте имя").assertExists()
    }

    @Test fun theCoverRunsEdgeToEdgeFromTheTopWithACounterAndAWhiteGear() {
        show(covers = MeFixtures.cover, safeTop = 47.dp)
        // ME-26: the pager is 232 tall from the very top, not inset under a title.
        val pager = bounds("profile_photo_0")
        near(0f, pager.left, what = "pager left"); near(411f, pager.width, what = "pager width"); near(0f, pager.top, what = "pager top"); near(232f, pager.height, what = "pager height")
        // ME-27: «1 / 4» at 16 from the left, 8 under the safe area.
        compose.onNodeWithTag("profile_cover_counter").assertTextEquals("1 / 4")
        near(16f, bounds("profile_cover_counter").left, what = "counter left"); near(55f, bounds("profile_cover_counter").top, what = "counter top")
        // ME-28: a 40 circle, 16 from the edge, 4 under the safe area; the identity row has no gear of its own.
        compose.onAllNodesWithTag("profile_gear").assertCountEquals(1)
        near(40f, bounds("profile_gear").width, what = "gear"); near(411f - 16f, bounds("profile_gear").right, what = "gear right"); near(51f, bounds("profile_gear").top, what = "gear top")
    }

    @Test fun theCoverPanelPutsTheCarOverThePhotoAndTheAvatarAt46() {
        show(covers = MeFixtures.cover, safeTop = 47.dp)
        // ME-29: the avatar disc is 46 on a cover, 60 off it; the car's row stands above the identity.
        near(46f, bounds("profile_avatar").width, what = "avatar")
        assertTrue(bounds("garage_header_vehicle").top < bounds("profile_photo_0").bottom, "the car overlaps the photo")
    }

    @Test fun theCoverPromptIsOneAccentLineWithASmallCross() {
        show()
        // ME-06: a 48 high row in a card, a 48 target for the cross at its right.
        near(48f, bounds("profile_add_cover").height, what = "prompt")
        near(48f, bounds("profile_photos_add_hide").width, what = "cross")
        compose.onNodeWithTag("profile_photos_add_hide").performClick()
        compose.onNodeWithTag("profile_add_cover").performClick()
        assertEquals(listOf("hide_cover", "photos"), events)
    }

    @Test fun aHiddenCoverPromptLeavesNothingInItsPlace() {
        show(promptHidden = true)
        compose.onNodeWithTag("profile_add_cover").assertDoesNotExist()
    }

    // ---- The rows under the hero ---------------------------------------------------------------

    @Test fun aSignedInPersonHasOneTwelvePointGapBetweenThePromptAndTheSections() {
        show(signedIn = true)
        // ME-08: the empty account item used to double the list's spacing (24).
        near(12f, bounds("profile_sections_toggle").top - bounds("profile_add_cover").bottom, what = "gap")
    }

    @Test fun aSignedOutPersonWithTripsGetsTheCompactRowNotTheGuestCard() {
        show(signedIn = false)
        // ME-07: one line and a dark pill, 12 under the prompt and 12 above the sections; the big card is for zero trips.
        compose.onNodeWithTag("account_slot").assertDoesNotExist()
        compose.onNodeWithTag("profile_guest_signin").assertExists()
        near(12f, bounds("profile_guest_signin").top - bounds("profile_add_cover").bottom, what = "gap above")
        near(12f, bounds("profile_sections_toggle").top - bounds("profile_guest_signin").bottom, what = "gap below")
        compose.onNodeWithTag("profile_journal_signin_button").performClick()
        assertEquals(listOf("sign_in"), events)
    }

    @Test fun theSectionsToggleIsAPlainCardRowThatOpensTheThreeSections() {
        show()
        // ME-09: 52 high at least; ME-10: opened, the three sections are blocks of their own.
        assertTrue(bounds("profile_sections_toggle").height.value >= 52f)
        compose.onNodeWithTag("profile_achievements").assertDoesNotExist()
        compose.onNodeWithTag("profile_sections_toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("profile_achievements").assertExists()
        compose.onNodeWithTag("profile_garage_card").assertExists()
        compose.onNodeWithTag("profile_clubs_row").assertExists()
        compose.onNodeWithTag("profile_achievement_featured").performClick()
        compose.onNodeWithTag("profile_achievements_all").performClick()
        compose.onNodeWithTag("profile_garage_card").performClick()
        scrollTo("profile_clubs_row")
        compose.onNodeWithTag("profile_clubs_row").performClick()
        assertEquals(listOf("badge:${featuredId()}", "achievements", "garage", "clubs"), events)
        compose.onNodeWithTag("profile_sections_toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("profile_achievements").assertDoesNotExist()
    }

    private fun featuredId() = AchievementsSummary.build(MeFixtures.achievements).featured!!.id

    @Test fun theGarageSectionNamesTheMainCarAndTheEmptyGarageSaysWhatACarIsFor() {
        show(onState = { it.copy(vehicles = emptyList()) })
        compose.onNodeWithTag("profile_sections_toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("profile_garage_empty").assertExists()
        compose.onNodeWithTag("profile_garage_card").assertDoesNotExist()
    }

    @Test fun theFirstRunHasTheWelcomeCardTheGarageAndTheClubsAndNoHistory() {
        show(MeFixtures.firstRun, signedIn = true)
        // ME-32: no numbers, no sections row, no history, no calendar; the welcome card leads to recording.
        compose.onNodeWithTag("profile_welcome_record").assertExists()
        compose.onNodeWithTag("profile_stats_strip").assertDoesNotExist()
        compose.onNodeWithTag("profile_sections_toggle").assertDoesNotExist()
        compose.onNodeWithTag("profile_history_header").assertDoesNotExist()
        compose.onNodeWithTag("profile_calendar").assertDoesNotExist()
        compose.onNodeWithTag("profile_garage_card").assertExists()
        compose.onNodeWithTag("profile_clubs_row").assertExists()
        compose.onNodeWithTag("profile_welcome_record").performClick()
        assertEquals(listOf("record"), events)
    }

    @Test fun theFirstRunOfAGuestKeepsTheWholeSignInCardAndALibraryHasNoSuchCard() {
        show(MeFixtures.firstRun, signedIn = false)
        compose.onNodeWithTag("account_slot").assertExists()
    }

    @Test fun pinsFromAnotherPhoneAreAPlainLineWithAnAccentLink() {
        show(onState = { it.copy(showcaseElsewhere = listOf(UUID.randomUUID(), UUID.randomUUID())) })
        scrollTo("profile_showcase_unpin_elsewhere")
        // ME-36
        compose.onNodeWithTag("profile_showcase_unpin_elsewhere").performClick()
        assertEquals(listOf("unpin"), events)
    }

    @Test fun theDraftsAreOneCardUnderAHeading() {
        show()
        // DR-01: «Черновики» over one 68 high card with the count and when the newest was recorded.
        compose.onNodeWithTag("profile_drafts_header").assertTextEquals("Черновики")
        near(68f, bounds("profile_drafts_row").height, what = "card")
        compose.onNodeWithText("4 поездки").assertExists()
        compose.onNodeWithTag("profile_drafts").performClick()
        assertEquals(listOf("drafts"), events)
    }

    @Test fun noDraftsNoSectionNotEvenAnEmptyCard() {
        show(onState = { it.copy(drafts = emptyList()) })
        compose.onNodeWithTag("profile_drafts_header").assertDoesNotExist()
        compose.onNodeWithTag("profile_drafts_row").assertDoesNotExist()
    }

    // ---- History -------------------------------------------------------------------------------

    @Test fun theHistoryHeaderIsATitleAndTwoIconToggles() {
        show()
        scrollTo("profile_history_header")
        // ME-15: the two views are 44 icon buttons; the one in use is selected, the other asks for itself.
        compose.onNodeWithTag("profile_history_list").assertIsSelected()
        compose.onNodeWithTag("profile_history_grid").assertIsNotSelected()
        near(44f, bounds("profile_history_grid").width, what = "grid button")
        compose.onNodeWithText("Плиткой").assertDoesNotExist()
        compose.onNodeWithTag("profile_history_grid").performClick()
        compose.onNodeWithTag("profile_history_list").performClick()
        assertEquals(listOf("mode:grid"), events)
    }

    @Test fun theSearchIsOneBarWithAMagnifierAndItsPrompt() {
        show()
        scrollTo("profile_history_search")
        // ME-16: the prompt lives in the field, the bar is 48 high, 8 under the header.
        compose.onNodeWithText("Название или регион").assertExists()
        near(48f, bounds("profile_history_search_bar").height, what = "bar")
        compose.onNodeWithTag("profile_history_search").performTextInput("Сочи")
        assertEquals(listOf("query:Сочи"), events)
        compose.onNodeWithTag("profile_history_clear_search").assertDoesNotExist()
    }

    @Test fun aSearchInProgressCanBeClearedFromTheBar() {
        show(onState = { it.copy(query = "Сочи") })
        scrollTo("profile_history_clear_search")
        compose.onNodeWithTag("profile_history_clear_search").performClick()
        assertEquals(listOf("query:"), events)
    }

    @Test fun theCalendarOpensFromTheMonthAtItsRightAndFiltersTheRange() {
        show()
        scrollTo("profile_calendar")
        // ME-17: «Эта неделя» at the left, «Октябрь ⌄» at the right.
        compose.onNodeWithText("Эта неделя").assertExists()
        compose.onNodeWithTag("profile_calendar_month").assertTextEquals("Октябрь")
        // ME-18: a day to come cannot be chosen; every day before can.
        compose.onNodeWithTag("calendar_day_2026-11-01").assertIsNotEnabled()
        compose.onNodeWithTag("calendar_day_2026-10-30").performClick()
        assertTrue(events.single().startsWith("range:") && events.single().endsWith(":null"), "the first tap sets the start only: $events")
        compose.onNodeWithTag("profile_calendar_month").performClick()
        compose.onNodeWithTag("profile_calendar_expand").assertExists()
    }

    @Test fun theFilterLineSaysHowManyAndClearsWithAnAccentWord() {
        show(onState = { it.copy(from = Instant.parse("2026-10-09T00:00:00Z"), to = Instant.parse("2026-10-10T00:00:00Z")) })
        scrollTo("profile_calendar_clear")
        // ME-20
        compose.onNodeWithTag("profile_calendar_clear").assertTextEquals("Сбросить")
        compose.onNodeWithTag("profile_calendar_clear").performClick()
        assertEquals(listOf("range:null:null"), events)
    }

    @Test fun aHistoryCardHasTheCarAndNoMenuAndKeepsTheFourteenPointMargin() {
        show()
        val trip = MeFixtures.trips.first()
        scrollTo("profile_trip_${trip.id}")
        // ME-21: «· Полторашка» on the meta line, the privacy glyph in the right slot, no «…» circle.
        compose.onNodeWithTag("profile_trip_${trip.id}").assertExists()
        assertTrue(compose.onAllNodesWithText("Полторашка").fetchSemanticsNodes().isNotEmpty(), "the car's name is on the card")
        compose.onAllNodesWithContentDescription("Ещё").assertCountEquals(0)
        // ME-22: cards keep 14 at the sides, headings 16.
        near(14f, bounds("profile_trip_${trip.id}").left, what = "card left")
        near(411f - 14f, bounds("profile_trip_${trip.id}").right, what = "card right")
    }

    @Test fun aHeldCardOpensTheJourneySheetAndATapOpensTheTrip() {
        show()
        val trip = MeFixtures.trips.first()
        scrollTo("profile_trip_${trip.id}")
        compose.onNodeWithTag("profile_trip_${trip.id}").performTouchInput { longClick() }
        compose.onNodeWithTag("profile_trip_${trip.id}").performClick()
        assertEquals(listOf("compose:${trip.id}", "trip:${trip.id}"), events)
    }

    @Test fun pinningStaysReachableForTalkBackNowThatTheCardHasNoMenu() {
        show()
        val trip = MeFixtures.trips.first()
        scrollTo("profile_trip_${trip.id}")
        val card = compose.onNode(hasTestTag("profile_trip_card") and hasAnyAncestor(hasTestTag("profile_trip_${trip.id}")))
        val actions = card.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
        assertEquals(1, actions.orEmpty().size)
        actions!!.first().action()
        assertEquals(listOf("pin:${trip.id}"), events)
    }

    @Test fun theGridIsRealTilesTwoToARowWithEightPointGaps() {
        show(MeFixtures.page.copy(mode = HistoryMode.grid))
        scrollTo("profile_trip_tile")
        val first = compose.onAllNodesWithTag("profile_trip_tile")[0].getBoundsInRoot()
        val second = compose.onAllNodesWithTag("profile_trip_tile")[1].getBoundsInRoot()
        // ME-34: 14 at the edges, 8 between, the map 72 high flush with the top of the tile.
        near(14f, first.left, what = "first left"); near(8f, second.left - first.right, what = "gap"); near(411f - 14f, second.right, what = "second right")
        assertTrue(first.height.value in 100f..125f, "a tile is a 72 pt map and two lines of text, not a card")
        compose.onAllNodesWithTag("profile_trip_card").assertCountEquals(0)
        compose.onAllNodesWithTag("profile_trip_tile")[0].performClick()
        assertTrue(events.single().startsWith("trip:"))
    }

    @Test fun aMonthHeadingStandsTwelvePointsAboveItsFirstCard() {
        show()
        scrollTo("profile_month_header")
        // ME-23: from the summary line to the first card 12 pt (4 + 8), not 16.
        val summary = compose.onNodeWithTag("profile_month_summary").getBoundsInRoot()
        val card = bounds("profile_trip_${MeFixtures.trips.first().id}")
        near(12f, card.top - summary.bottom, what = "gap")
    }

    @Test fun anEmptyFilterResultIsAQuestionAndAnAccentTextButton() {
        show(onState = { it.copy(history = it.history.copy(visibleTrips = emptyList(), pinned = emptyList(), chapters = emptyList(), gridRuns = emptyList())) })
        scrollTo("profile_history_empty")
        // ME-35
        compose.onNodeWithTag("profile_history_empty").assertTextEquals("В эти дни поездок нет")
        compose.onNodeWithTag("profile_history_clear_filters").performClick()
        assertEquals(listOf("clear"), events)
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("screen_me").performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
    }
}
