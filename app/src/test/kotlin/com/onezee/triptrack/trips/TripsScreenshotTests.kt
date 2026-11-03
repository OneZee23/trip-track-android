// iOS: TripDetailOpeningTests, DraftsFlowShotTests, JourneyComposerShotTests (the kept frames).
package com.onezee.triptrack.trips

import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import android.content.Context
import com.onezee.triptrack.debug.TripsDemoSeed
import com.onezee.triptrack.feature.TripPins
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.social.SocialFeature
import com.onezee.triptrack.social.FakeSocialApi
import com.onezee.triptrack.social.MemoryCommentArchive
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.feature.trip.ui.TripEditSheet
import com.onezee.triptrack.feature.social.ui.TripPosterSheet
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.robolectric.RuntimeEnvironment
import com.onezee.triptrack.model.SocialFeedResponse
import com.onezee.triptrack.model.SocialTrackPoint
import com.onezee.triptrack.model.SocialTripResponse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Every trip screen light and dark on the seeded library. Screens are captured from the compose
 * root; sheets, dialogs and the actions popover live in windows of their own, so those frames are
 * whole-screen captures.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class TripsScreenshotTests(private val dark: Boolean) : TripsScreenTest() {
    private val theme = if (dark) "dark" else "light"

    // The account-side half of the screens (the share, the bell, the discussion) is part of every frame here, as in the app.
    private var services: SocialFeature? = null
    override fun socialServices(): SocialFeature = services ?: SocialFeature(
        FakeSocialApi(), feature, null, { feature.accountId() },
        RuntimeEnvironment.getApplication().getSharedPreferences("social-tests", Context.MODE_PRIVATE), { "" }, Dispatchers.Main, { NOW },
        commentArchive = MemoryCommentArchive(),
    ).also { services = it }

    @After fun closeSocialServices() { services?.close() }

    /** The showcase as a phone keeps it: three places, in memory. */
    private val pinned = MutableStateFlow(emptyList<UUID>())
    private fun withShowcase() {
        feature.pins = object : TripPins {
            override val pinnedIds = pinned
            override suspend fun toggle(id: UUID): Boolean {
                if (id in pinned.value) { pinned.value = pinned.value - id; return true }
                if (pinned.value.size >= 3) return false
                pinned.value = pinned.value + id
                return true
            }
        }
    }

    /** What the badge ledger wrote on a trip when it finished: twelve engravings, four rows of three. */
    private fun earnBadges(trip: com.onezee.triptrack.model.Trip) = onMain {
        val ids = listOf("first_trip", "road_regular", "road_warrior", "first_km", "century", "thousand", "endurance", "weekend_warrior",
            "midnight_rider", "vault_keeper", "early_tester", "marathon_42")
        val entity = db.tripDao().trip(trip.id)!!
        db.tripDao().upsertTrip(entity.copy(badgesJSON = ids.joinToString(",", "[", "]") { "\"$it\"" }))
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/trips_${name}_$theme.png")

    private fun screen(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/trips_${name}_$theme.png")
    }

    @Test fun feed() {
        signIn()
        val cards = socialCards()
        social.feedPage = { SocialFeedResponse(cards) }
        showFeed(dark)
        compose.onAllNodesWithTag("social_trip_card")[0].assertExists()
        shot("feed")
    }

    /** Someone else's trip opened from its card: the server's track, their name, no owner menu. */
    @Test fun foreignTrip() {
        signIn()
        val cards = socialCards()
        val road = detail(trips().first { it.title == "Краснодар → Геленджик" }.id)!!
        social.feedPage = { SocialFeedResponse(cards) }
        social.remoteTrip = { id ->
            SocialTripResponse(cards.first { it.id == id }, road.trackPoints.map { SocialTrackPoint(it.latitude, it.longitude, it.speed, it.timestamp) })
        }
        showFeed(dark)
        compose.onAllNodesWithTag("social_trip_card")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("trip_detail").assertExists()
        shot("trip_foreign")
    }

    @Test fun me() {
        showMe(dark)
        compose.onNodeWithTag("profile_drafts").assertExists()
        shot("me")
    }

    @Test fun trip() {
        showMe(dark)
        openTrip(latest())
        compose.onNodeWithTag("detail_stats").assertExists()
        shot("trip")
    }

    @Test fun tripPhotos() {
        showMe(dark)
        openTrip(latest())
        compose.onNodeWithTag("detail_photos").performScrollTo()
        shot("trip_photos")
    }

    @Test fun tripMoments() {
        showMe(dark)
        openTrip(rostovRoad())
        compose.onNodeWithTag("trip_moments").performScrollTo()
        shot("trip_moments")
    }

    @Test fun tripActions() {
        showMe(dark)
        openTrip(latest())
        compose.onNodeWithTag("detail_actions").performClick()
        screen("trip_actions")
    }

    @Test fun tripEditSheet() {
        val trip = latest()
        show(dark) { TripEditSheet(trip, onMain { feature.vehicles() }, { _, _, _, _, _ -> }, {}) }
        compose.onNodeWithTag("trip_edit_sheet").assertExists()
        screen("trip_edit")
        compose.onNodeWithTag("trip_edit_access_row").performClick()
        compose.onNodeWithTag("trip_edit_access_private").assertExists()
        screen("trip_edit_access")
        compose.onNodeWithTag("trip_edit_access_private").performClick()
        compose.onNodeWithTag("trip_edit_vehicle_row").performClick()
        compose.onNodeWithTag("trip_edit_vehicle_transfer").assertExists()
        screen("trip_edit_vehicle")
    }

    @Test fun tripPosterSheet() {
        val trip = latest()
        show(dark) { TripPosterSheet(TripSummary.from(trip), "Водитель", "https://trip-track.app/s/abc123") {} }
        compose.onNodeWithTag("trip_poster_sheet").assertExists()
        screen("trip_poster")
        val strings = com.onezee.triptrack.designsystem.strings.AppStrings(RuntimeEnvironment.getApplication(), com.onezee.triptrack.designsystem.strings.AppLanguage.ru)
        compose.onNodeWithText(strings.tabMap()).performClick()
        screen("trip_poster_map")
        compose.onNodeWithText(strings.sharePosterMinimal()).performClick()
        screen("trip_poster_minimal")
        compose.onNodeWithText(strings.sharePosterStory()).performClick()
        screen("trip_poster_story")
        compose.onNodeWithText(strings.sharePosterStickerPng()).performClick()
        screen("trip_poster_sticker")
    }

    @Test fun tripClosedState() {
        signIn()
        val cards = socialCards()
        val road = detail(trips().first { it.title == "Краснодар → Геленджик" }.id)!!
        social.feedPage = { SocialFeedResponse(cards) }
        social.remoteTrip = { throw com.onezee.triptrack.network.ApiError.TripNotFound }
        showFeed(dark)
        compose.onAllNodesWithTag("social_trip_card")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Поездка закрыта").assertExists()
        screen("trip_closed")
    }

    @Test fun tripDeleteConfirmation() {
        showMe(dark)
        openTrip(latest())
        compose.onNodeWithTag("detail_actions").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("detail_action_delete").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("app_dialog").assertExists()
        screen("trip_delete")
    }

    @Test fun photoViewer() {
        showMe(dark)
        openTrip(latest())
        // The shelf is a row inside the page: the page scrolls to the row, not to the photo.
        compose.onNodeWithTag("detail_photos").performScrollTo()
        compose.onAllNodesWithTag("detail_photo")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("photo_viewer").assertExists()
        screen("photo_viewer")
    }

    @Test fun checkpointEditor() {
        showMe(dark)
        openTrip(rostovRoad())
        compose.onAllNodesWithTag("moment_checkpoint")[0].performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("checkpoint_editor").assertExists()
        screen("checkpoint_editor")
    }

    @Test fun segmentEditor() {
        showMe(dark)
        openTrip(rostovRoad())
        compose.onNodeWithTag("moment_segment").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("segment_editor").assertExists()
        screen("segment_editor")
    }

    @Test fun journey() {
        showMe(dark)
        push(TripsDestination.JourneyDetail(seededJourney().id))
        compose.onNodeWithTag("journey_totals").assertExists()
        shot("journey")
    }

    /** The stay opened: the city trips folded under «Ростов-на-Дону · 1 поездка по городу». */
    @Test fun journeyStay() {
        showMe(dark)
        push(TripsDestination.JourneyDetail(seededJourney().id))
        compose.onNodeWithTag("journey_local_group").performScrollTo().performClick()
        compose.waitForIdle()
        shot("journey_stay")
    }

    @Test fun journeyComposer() {
        showMe(dark)
        compose.onNodeWithTag("screen_me").performScrollToNode(hasTestTag("profile_trip_${latest().id}"))
        compose.onNodeWithTag("profile_trip_${latest().id}").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("journey_composer").assertExists()
        screen("journey_composer")
    }

    @Test fun journeyEdit() {
        showMe(dark)
        push(TripsDestination.JourneyDetail(seededJourney().id))
        compose.onNodeWithTag("detail_actions").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("journey_action_edit").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("journey_edit").assertExists()
        screen("journey_edit")
    }

    @Test fun drafts() {
        showMe(dark)
        push(TripsDestination.Drafts)
        compose.onAllNodesWithTag("draft_row")[0].assertExists()
        shot("drafts")
    }

    @Test fun draftsSelecting() {
        showMe(dark)
        push(TripsDestination.Drafts)
        compose.onNodeWithTag("drafts_select").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("draft_row")[1].performClick()
        compose.waitForIdle()
        shot("drafts_selecting")
    }

    /** TD-07: «Достижения поездки», four rows of three, after the photos. */
    @Test fun tripAchievements() {
        earnBadges(latest())
        showMe(dark)
        openTrip(latest())
        compose.onNodeWithTag("trip_achievements").performScrollTo()
        compose.onNodeWithTag("trip_achievement_first_trip").assertExists()
        shot("trip_achievements")
    }

    /** TD-05: the dashed invitation on one's own trip with nothing written, and the sheet it opens. */
    @Test fun tripDescriptionPrompt() {
        showMe(dark)
        openTrip(latest())
        compose.onNodeWithTag("detail_add_description").performScrollTo()
        shot("trip_description")
        compose.onNodeWithTag("detail_add_description").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("trip_edit_sheet").assertExists()
        screen("trip_edit_from_description")
    }

    /** TD-10: «Закрепить в профиле» on a public, finished trip. */
    @Test fun tripActionsPublic() {
        withShowcase()
        val road = trips().first { it.title == TripsDemoSeed.ROSTOV_CITY && !it.isPrivate }
        showMe(dark)
        openTrip(road)
        compose.onNodeWithTag("detail_actions").performClick()
        compose.onNodeWithTag("detail_action_profile_pin").assertExists()
        screen("trip_actions_public")
    }

    /** TD-13: a trip whose data never arrived. */
    @Test fun tripLoadFailed() {
        showMe(dark)
        push(TripsDestination.TripDetail(UUID.fromString("0b6f6a52-9999-4c8e-9a3e-5d2b7c9e0a01")))
        compose.onNodeWithTag("trip_load_failed").assertExists()
        shot("trip_load_failed")
    }

    /** TD-11, TD-02, TD-01: someone else's leg of a journey, its car with a chevron, share alone on the right. */
    @Test fun foreignTripJourney() {
        signIn()
        val cards = socialCards()
        val road = detail(trips().first { it.title == "Батуми → Кобулети" }.id)!!
        social.feedPage = { SocialFeedResponse(cards) }
        social.remoteTrip = { id ->
            SocialTripResponse(cards.first { it.id == id }, road.trackPoints.map { SocialTrackPoint(it.latitude, it.longitude, it.speed, it.timestamp) })
        }
        showFeed(dark)
        // The third card is below the fold: the list scrolls to it, and its title opens the trip.
        compose.onNodeWithTag("feed_list_all").performScrollToIndex(2)
        compose.onNodeWithText("Батуми → Кобулети").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("trip_detail").assertExists()
        compose.onNodeWithTag("detail_journey_row").assertExists()
        compose.onNodeWithTag("detail_share").assertExists()
        shot("trip_foreign_journey")
    }

    /** FD-01, FD-02: the bell beside «Лента» and the «…» on every card. */
    @Test fun feedMenus() {
        signIn()
        val cards = socialCards()
        social.feedPage = { SocialFeedResponse(cards) }
        showFeed(dark)
        compose.onAllNodesWithTag("feed_card_menu")[0].performClick()
        compose.onNodeWithTag("feed_card_action_share").assertExists()
        compose.onNodeWithTag("feed_card_action_report").assertExists()
        screen("feed_menu")
    }

    @Test fun feedOwnMenu() {
        signIn()
        val cards = socialCards()
        social.feedPage = { SocialFeedResponse(cards) }
        showFeed(dark)
        compose.onAllNodesWithTag("feed_card_menu")[1].performClick()
        compose.onNodeWithTag("feed_card_action_edit").assertExists()
        compose.onNodeWithTag("feed_card_action_share").assertExists()
        screen("feed_menu_own")
    }

    /** FD-01, FD-05: a guest has «Войти» in the corner and the same button on the «Подписки» page. */
    @Test fun feedGuest() {
        val cards = socialCards()
        social.feedPage = { SocialFeedResponse(cards) }
        showFeed(dark, signInDoor = {})
        compose.onNodeWithTag("feed_sign_in").assertExists()
        shot("feed_guest")
        compose.onNodeWithTag("feed_segment_following").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("feed_following_sign_in").assertExists()
        shot("feed_following_guest")
    }

    /** FD-01: the search circle, ready for Discover; the feed draws it only once there is somewhere to go. */
    @Test fun feedHeaderWithSearch() {
        show(dark) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize()) {
                com.onezee.triptrack.feature.feed.ui.FeedHeader({}, onSignIn = null, onBell = {}, onSearch = {},
                    modifier = androidx.compose.ui.Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp))
                com.onezee.triptrack.feature.feed.ui.FeedHeader({}, onSignIn = {}, onBell = null, onSearch = {},
                    modifier = androidx.compose.ui.Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp))
            }
        }
        shot("feed_header_search")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
