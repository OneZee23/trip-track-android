// iOS: TripDetailOpeningTests, DraftsFlowShotTests, JourneyComposerShotTests (the kept frames).
package com.onezee.triptrack.trips

import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.onezee.triptrack.feature.TripsDestination
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

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
