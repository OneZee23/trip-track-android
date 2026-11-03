// iOS: TripDetailTourTests, TripEditSheetTests (the owner menu), ForeignTripNavigationTests, and the 3 November 2026
// parity audit of the trip screen (TD-01 to TD-13): what the screen draws and what its controls do.
package com.onezee.triptrack.trips

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.feature.trip.ui.TripAchievementsSection
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.onezee.triptrack.debug.TripsDemoSeed
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.feature.TripPins
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.social.SocialApi
import com.onezee.triptrack.feature.social.SocialFeature
import com.onezee.triptrack.model.SocialAuthor
import com.onezee.triptrack.model.SocialFeedResponse
import com.onezee.triptrack.model.SocialFeedTrip
import com.onezee.triptrack.model.SocialFeedVehicle
import com.onezee.triptrack.model.SocialShareResponse
import com.onezee.triptrack.model.SocialTrackPoint
import com.onezee.triptrack.model.SocialTripResponse
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.social.FakeSocialApi
import com.onezee.triptrack.social.MemoryCommentArchive
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class TripScreenDesignTests : TripsScreenTest() {
    /** The links minted so far: a share that reached the server asks for exactly one. */
    private val minted = mutableListOf<UUID>()
    private var services: SocialFeature? = null

    private class SharingApi(private val inner: FakeSocialApi, private val minted: MutableList<UUID>) : SocialApi by inner {
        override suspend fun shareTrip(tripId: UUID): SocialShareResponse {
            minted += tripId
            return SocialShareResponse("https://trip-track.app/s/abc123", "abc123")
        }
    }

    override fun socialServices(): SocialFeature = services ?: SocialFeature(
        SharingApi(FakeSocialApi(), minted), feature, null, { feature.accountId() },
        RuntimeEnvironment.getApplication().getSharedPreferences("social-tests", Context.MODE_PRIVATE), { "" }, Dispatchers.Main, { NOW },
        commentArchive = MemoryCommentArchive(),
    ).also { services = it }

    @After fun closeSocialServices() { services?.close() }

    private val ru get() = AppStrings(RuntimeEnvironment.getApplication(), AppLanguage.ru)

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun Dp.near(expected: Float, tolerance: Float = 0.7f) = assertEquals(expected, value, tolerance)

    private fun publicTrip(): Trip = trips().first { it.title == TripsDemoSeed.ROSTOV_CITY && !it.isPrivate }

    private fun earn(trip: Trip, ids: List<String>) = onMain {
        val entity = db.tripDao().trip(trip.id)!!
        db.tripDao().upsertTrip(entity.copy(badgesJSON = ids.joinToString(",", "[", "]") { "\"$it\"" }))
    }

    /** A showcase that remembers, as the Я identity does: three places. */
    private class Showcase(initial: List<UUID> = emptyList()) : TripPins {
        val ids = MutableStateFlow(initial)
        override val pinnedIds get() = ids
        override suspend fun toggle(id: UUID): Boolean {
            if (id in ids.value) { ids.value = ids.value - id; return true }
            if (ids.value.size >= 3) return false
            ids.value = ids.value + id
            return true
        }
    }

    /** Someone else's public trip, opened from a card of the feed, with the server's track. */
    private fun openForeignTrip(index: Int = 0, title: String? = null) {
        signIn()
        val cards = socialCards()
        val road = detail(trips().first { it.title == "Краснодар → Геленджик" }.id)!!
        social.feedPage = { SocialFeedResponse(cards) }
        social.remoteTrip = { id -> SocialTripResponse(cards.first { it.id == id }, road.trackPoints.map { SocialTrackPoint(it.latitude, it.longitude, it.speed, it.timestamp) }) }
        showFeed()
        if (title != null) { compose.onNodeWithTag("feed_list_all").performScrollToIndex(index); compose.onNodeWithText(title).performClick() }
        else compose.onAllNodesWithTag("social_trip_card")[index].performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("trip_detail").assertExists()
    }

    // ---- TD-01, SH-02: the top bar

    @Test fun theOwnTripHasShareAndMoreFusedInOneCapsule() {
        showMe()
        openTrip(latest())
        val share = bounds("detail_share")
        val more = bounds("detail_actions")
        // iOS: two 44 × 36 cells and a hairline, 88.5 × 36 in all.
        share.width.near(44f); share.height.near(36f)
        more.width.near(44f); more.height.near(36f)
        (more.left - share.right).near(0.5f)
        assertEquals(share.top.value, more.top.value, 0.1f)
        // The capsule is not wrapped in the circle's 44 target: its edge stands 12 from the screen.
        (compose.onRoot().getUnclippedBoundsInRoot().right - more.right).near(12f)
    }

    @Test fun someoneElsesPublicTripHasShareAloneAsACircle() {
        openForeignTrip()
        val share = bounds("detail_share")
        // The plain map-chrome circle: 36 in its 44 target, the edge of the circle 16 from the screen.
        share.width.near(44f); share.height.near(44f)
        (compose.onRoot().getUnclippedBoundsInRoot().right - share.right).near(12f)
        compose.onNodeWithTag("detail_actions").assertDoesNotExist()
    }

    @Test fun someoneElsesPrivateTripOffersNoShare() {
        signIn()
        val trip = SocialFeedTrip(id = UUID.fromString("0b6f6a52-7777-4c8e-9a3e-5d2b7c9e0a01"),
            author = SocialAuthor(UUID.fromString("0b6f6a52-8888-4c8e-9a3e-5d2b7c9e0a01"), displayName = "Аня", avatarEmoji = "🚙", profileLevel = 3),
            title = "Закрытая", startDate = NOW.minusSeconds(3600), distance = 1000.0, duration = 60, isPrivate = true, photoCount = 0,
            reactionCount = 0, reactionBreakdown = emptyList(), badgeIds = emptyList())
        social.remoteTrip = { SocialTripResponse(trip, emptyList()) }
        showMe()
        push(TripsDestination.TripDetail(trip.id, social = trip))
        compose.onNodeWithTag("trip_detail").assertExists()
        // iOS canOfferShare: sharing it could only fail (the server refuses a private trip of someone else).
        compose.onNodeWithTag("detail_share").assertDoesNotExist()
        compose.onNodeWithTag("detail_actions").assertDoesNotExist()
    }

    @Test fun shareMintsTheLinkAndHandsItToTheSystemSheet() {
        signIn()
        showMe()
        val trip = publicTrip()
        openTrip(trip)
        compose.onNodeWithTag("detail_share").performClick()
        compose.waitForIdle()
        assertEquals(listOf(trip.id), minted)
        val chooser = Shadows.shadowOf(compose.activity).nextStartedActivity
        assertNotNull(chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("https://trip-track.app/s/abc123", send.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test fun aGuestWhoSharesMintsNothing() {
        showMe()
        openTrip(publicTrip())
        compose.onNodeWithTag("detail_share").performClick()
        compose.waitForIdle()
        // The sign-in sheet needs the account services (null here); no link is asked for, no chooser is opened.
        assertTrue(minted.isEmpty())
        assertEquals(null, Shadows.shadowOf(compose.activity).nextStartedActivity)
    }

    @Test fun sharingAPrivateTripOfOnesOwnAsksToPublishFirst() {
        signIn()
        showMe()
        openTrip(latest())
        assertTrue(latest().isPrivate)
        compose.onNodeWithTag("detail_share").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("publish_sheet").assertExists()
        assertTrue(minted.isEmpty())
    }

    // ---- TD-08: nothing between the reactions and the discussion

    @Test fun theDetailsColumnOfOneOwnTripHasNoShareCard() {
        signIn()
        showMe()
        openTrip(publicTrip())
        compose.onNodeWithTag("trip_share").assertDoesNotExist()
        compose.onNodeWithTag("trip_report").assertDoesNotExist()
    }

    /** iOS has no report on the trip screen; it stays on the cards and the profile. */
    @Test fun theDetailsColumnOfSomeoneElsesTripHasNoShareCardAndNoReportFlag() {
        openForeignTrip()
        compose.onNodeWithTag("trip_share").assertDoesNotExist()
        compose.onNodeWithTag("trip_report").assertDoesNotExist()
    }

    // ---- TD-07: «Достижения поездки»

    @Test fun achievementsStandThreeAcrossAndATapOpensTheBadge() {
        earn(latest(), listOf("first_trip", "road_regular", "road_warrior", "first_km", "century", "thousand"))
        showMe()
        openTrip(latest())
        val a = bounds("trip_achievement_first_trip")
        val b = bounds("trip_achievement_road_regular")
        val c = bounds("trip_achievement_road_warrior")
        val d = bounds("trip_achievement_first_km")
        // iOS: three flexible columns 10 apart, the rows 10 apart; the card is 14 + 56 + 8 + 28 + 14 tall.
        (b.left - a.right).near(10f); (c.left - b.right).near(10f)
        assertEquals(a.top.value, b.top.value, 0.1f); assertEquals(a.top.value, c.top.value, 0.1f)
        assertEquals(a.width.value, b.width.value, 0.1f); assertEquals(a.width.value, c.width.value, 0.1f)
        a.height.near(120f, 1f)
        (d.top - a.bottom).near(10f)
        compose.onNodeWithTag("trip_achievement_first_trip").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("public_badge_overlay").assertExists()
        compose.onNodeWithTag("badge_close").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("public_badge_overlay").assertDoesNotExist()
    }

    /** iOS: «two on accessibility sizes, where a third of the width split «Марафонец» mid-word». */
    @Test fun achievementsStandTwoAcrossAtTheAccessibilityTextSizes() {
        show {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                TripAchievementsSection(listOf("first_trip", "road_regular", "road_warrior", "marathon_42"), {}, Modifier.padding(16.dp))
            }
        }
        val a = bounds("trip_achievement_first_trip")
        val b = bounds("trip_achievement_road_regular")
        val c = bounds("trip_achievement_road_warrior")
        val d = bounds("trip_achievement_marathon_42")
        assertEquals(a.top.value, b.top.value, 0.1f)
        assertEquals(c.top.value, d.top.value, 0.1f)
        assertTrue(c.top > a.bottom, "the third card starts the second row")
        (b.left - a.right).near(10f)
        // Two flexible columns of the 16-in-from-each-side width, 10 apart.
        a.width.near((compose.onRoot().getUnclippedBoundsInRoot().width.value - 32f - 10f) / 2f, 1f)
    }

    @Test fun aTripWithNothingEarnedHasNoAchievementsSection() {
        showMe()
        openTrip(latest())
        compose.onNodeWithTag("trip_achievements").assertDoesNotExist()
    }

    /** An id this build does not know (a newer server) is skipped; with nothing known left there is no section. */
    @Test fun anUnknownBadgeIdIsSkipped() {
        earn(latest(), listOf("a_badge_from_the_future"))
        showMe()
        openTrip(latest())
        compose.onNodeWithTag("trip_achievements").assertDoesNotExist()
    }

    @Test fun theSectionsStandInTheIosOrder() {
        earn(latest(), listOf("first_trip"))
        showMe()
        openTrip(latest())
        val movement = bounds("moving_stops").bottom
        val prompt = bounds("detail_add_description")
        val photos = bounds("detail_photos")
        val achievements = bounds("trip_achievements")
        assertTrue(movement < prompt.top && prompt.bottom < photos.top && photos.bottom < achievements.top)
        // iOS: 22 between sections; the photos heading is as tall as its text (the «+» keeps its target out of the layout).
        (prompt.bottom + Dp(22f + 20.6f + 10f)).let { assertEquals(it.value, photos.top.value, 3f) }
    }

    // ---- TD-05: the description prompt

    @Test fun anOwnTripWithNothingWrittenInvitesTheOwnerToWriteIt() {
        showMe()
        openTrip(latest())
        compose.onNodeWithTag("detail_add_description").assertExists().assertHasClickAction()
        compose.onNodeWithTag("detail_description").assertDoesNotExist()
    }

    @Test fun anOwnTripWithADescriptionShowsItAndNoPrompt() {
        onMain { repository.updateNotes(latest().id, "Ехали к морю") }
        showMe()
        openTrip(latest())
        compose.onNodeWithTag("detail_add_description").assertDoesNotExist()
        compose.onNodeWithTag("detail_description").assertExists()
    }

    /** Someone else's trip without a description has no section at all, and no way to write one. */
    @Test fun someoneElsesTripWithNothingWrittenHasNoPrompt() {
        openForeignTrip()
        compose.onNodeWithTag("detail_add_description").assertDoesNotExist()
        compose.onNodeWithTag("detail_description").assertDoesNotExist()
    }

    @Test fun theDescriptionSheetSavesWhatWasWritten() {
        showMe()
        openTrip(latest())
        compose.onNodeWithTag("detail_add_description").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("description_sheet").assertExists()
        compose.onNodeWithTag("description_field").performTextInput("Ехали к морю")
        compose.onNodeWithTag("description_done").performClick()
        compose.waitForIdle()
        assertEquals("Ехали к морю", detail(latest().id)!!.tripDescription)
        compose.onNodeWithTag("description_sheet").assertDoesNotExist()
        compose.onNodeWithTag("detail_description").assertExists()
    }

    @Test fun aDescriptionTheContentFilterRefusesIsNotSaved() {
        showMe()
        openTrip(latest())
        compose.onNodeWithTag("detail_add_description").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("description_field").performTextInput("жид")
        compose.onNodeWithTag("description_refused").assertExists()
        compose.onNodeWithTag("description_done").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("description_sheet").assertExists()
        assertTrue(detail(latest().id)!!.tripDescription.isNullOrEmpty())
    }

    @Test fun theDescriptionIsCutAtFiveHundred() {
        showMe()
        openTrip(latest())
        compose.onNodeWithTag("detail_add_description").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("description_field").performTextInput("а".repeat(520))
        compose.onNodeWithText("500 / 500").assertExists()
    }

    // ---- TD-10: the owner menu

    @Test fun thePinRowFollowsTheIosRules() {
        // A private trip is not pinned: the showcase shows what others can see.
        feature.pins = Showcase()
        showMe()
        openTrip(latest())
        compose.onNodeWithTag("detail_actions").performClick()
        compose.onNodeWithTag("detail_action_profile_pin").assertDoesNotExist()
        compose.onNodeWithTag("detail_action_publish").assertExists()
    }

    @Test fun aPublicFinishedTripCanBePinnedAndUnpinned() {
        val showcase = Showcase()
        feature.pins = showcase
        showMe()
        val trip = publicTrip()
        openTrip(trip)
        compose.onNodeWithTag("detail_actions").performClick()
        compose.onNodeWithText(ru.profilePin()).assertExists()
        compose.onNodeWithTag("detail_action_profile_pin").performClick()
        compose.waitForIdle()
        assertEquals(listOf(trip.id), showcase.ids.value)
        compose.onNodeWithTag("detail_actions").performClick()
        compose.onNodeWithText(ru.profileUnpin()).assertExists()
        compose.onNodeWithTag("detail_action_profile_pin").performClick()
        compose.waitForIdle()
        assertTrue(showcase.ids.value.isEmpty())
    }

    @Test fun theFourthPinAsksToFreeAPlace() {
        val others = List(3) { UUID.randomUUID() }
        val showcase = Showcase(others)
        feature.pins = showcase
        showMe()
        openTrip(publicTrip())
        compose.onNodeWithTag("detail_actions").performClick()
        compose.onNodeWithTag("detail_action_profile_pin").performClick()
        compose.waitForIdle()
        assertEquals(others, showcase.ids.value)
        // The word of the toast is the one «Я» shows when its own pin runs out of places.
        compose.onNodeWithText(ru.profileShowcaseLimit()).assertExists()
    }

    @Test fun withoutAShowcaseThereIsNoPinRow() {
        showMe()
        openTrip(publicTrip())
        compose.onNodeWithTag("detail_actions").performClick()
        compose.onNodeWithTag("detail_action_profile_pin").assertDoesNotExist()
    }

    // ---- TD-11, TD-02: the journey row and the car chip

    @Test fun theJourneyRowOfSomeoneElsesLegOpensTheirJourney() {
        openForeignTrip(index = 2, title = "Батуми → Кобулети")
        compose.onNodeWithTag("detail_journey_row").assertExists().performClick()
        compose.waitForIdle()
        val top = feature.feedNavigator.top as TripsDestination.SocialJourney
        assertEquals(UUID.fromString("0b6f6a52-5555-4c8e-9a3e-5d2b7c9e0a01"), top.id)
    }

    @Test fun aTripWithoutAJourneyHasNoRow() {
        openForeignTrip(index = 0)
        compose.onNodeWithTag("detail_journey_row").assertDoesNotExist()
    }

    @Test fun theCarChipOfSomeoneElsesTripIsATargetOnlyWithAPageToOpen() {
        // The first card's car has no id on the server: a plain label (a chevron on it would be a lie).
        openForeignTrip(index = 0)
        compose.onNodeWithTag("detail_foreign_vehicle").assertHasNoClickAction()
    }

    @Test fun theCarChipOfSomeoneElsesTripWithAnIdOpensTheirCar() {
        openForeignTrip(index = 2, title = "Батуми → Кобулети")
        compose.onNodeWithTag("detail_foreign_vehicle").assertHasClickAction().performClick()
        compose.waitForIdle()
        val top = feature.feedNavigator.top as TripsDestination.PublicCar
        assertEquals(UUID.fromString("0b6f6a52-6666-4c8e-9a3e-5d2b7c9e0a01"), top.vehicleId)
    }

    @Test fun theOwnCarChipOpensThePassport() {
        val opened = mutableListOf<UUID>()
        feature.openVehicle = { opened += it }
        val trip = latest()
        val vehicleId = onMain { vehicles.addVehicle("Полторашка", "🚗") }
        onMain { repository.updateVehicle(trip.id, vehicleId) }
        showMe()
        openTrip(trip)
        compose.onNodeWithTag("detail_own_vehicle").assertHasClickAction().performClick()
        assertEquals(listOf(vehicleId), opened)
    }

    @Test fun aTripWithoutACarHasNothingToOpen() {
        feature.openVehicle = { throw AssertionError("a trip without a car opened a passport") }
        showMe()
        openTrip(latest())
        compose.onNodeWithTag("detail_own_vehicle").assertHasNoClickAction()
    }

    // ---- TD-03, TD-06: the movement card and the photos heading

    @Test fun theStoppedLabelEndsAtTheCardsInnerEdge() {
        showMe()
        openTrip(latest())
        // The tag sits inside the card's 16 padding, so its right edge is the padding's edge.
        val inner = bounds("moving_stops")
        val label = compose.onNodeWithText("Без движения · 0 сек", useUnmergedTree = true).getUnclippedBoundsInRoot()
        // iOS: a Spacer(minLength: 8) pushes it to the 16-point padding (17 from the card's edge as measured), not 64 in.
        (inner.right - label.right).near(0f, 1f)
    }

    @Test fun thePhotosHeadingRowIsAsTallAsItsText() {
        showMe()
        openTrip(latest())
        compose.onNodeWithTag("detail_photos").performScrollTo()
        val heading = compose.onNodeWithText(ru.photos() + " · ", substring = true).getUnclippedBoundsInRoot()
        val strip = bounds("detail_photos")
        val plus = bounds("detail_add_photo")
        // iOS: the «+» keeps its 44-point target through a −12 padding, so the row is as tall as the heading's line and the strip
        // starts 10 below it; a 44-high box in the row made the distance 54 and left air around «Фото · N».
        val rowAndGap = (strip.top - heading.top).value
        assertTrue(rowAndGap in 28f..36f, "heading top to strip top is $rowAndGap, expected the line plus 10")
        // The «+» is the 20-point button of iOS, centred on the heading's line.
        plus.width.near(20f); plus.height.near(20f)
        assertEquals((heading.top + heading.height / 2).value, (plus.top + plus.height / 2).value, 1.5f)
    }

    // ---- TD-13: the trip that failed to load

    @Test fun theLoadFailedStateHasItsHeaderItsRingAndTheRetry() {
        showMe()
        push(TripsDestination.TripDetail(UUID.fromString("0b6f6a52-9999-4c8e-9a3e-5d2b7c9e0a01")))
        compose.onNodeWithTag("trip_load_failed").assertExists()
        compose.onNodeWithText("Поездка").assertExists()
        compose.onNodeWithTag("trip_load_retry").assertExists().assertHasClickAction()
        val back = bounds("detail_back")
        back.width.near(34f); back.height.near(34f)
        val ring = bounds("trip_load_failed_ring")
        ring.width.near(108f); ring.height.near(108f)
        // One spacer above the block and two below: it stands above the centre of the screen.
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue((ring.top + ring.height / 2).value < screen.height.value / 2)
    }
}
