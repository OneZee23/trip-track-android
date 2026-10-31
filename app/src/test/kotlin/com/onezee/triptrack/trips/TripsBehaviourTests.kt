// iOS: TripJourneyNavigationTests, DraftsFlowShotTests, TripDetailOpeningTests (the asserted steps).
package com.onezee.triptrack.trips

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.drafts.DraftDecision
import com.onezee.triptrack.model.SocialFeedResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TripsBehaviourTests : TripsScreenTest() {
    private fun drafts() = onMain { repository.fetchDraftTrips() }.sortedByDescending { it.startDate }

    @Test fun aHistoryCardOpensItsTripAndBackComesHome() {
        showMe()
        compose.onAllNodesWithTag("profile_trip_card")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("trip_detail").assertExists()
        assertEquals(latest().id, (feature.meNavigator.top as TripsDestination.TripDetail).id)
        // The screen leaves the tab bar to the screens under it.
        assertTrue(visibility.isHiddenByScreen)

        compose.onNodeWithTag("detail_back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("trip_detail").assertDoesNotExist()
        compose.onNodeWithTag("profile_history_header").assertExists()
        assertTrue(feature.meNavigator.stack.isEmpty())
        assertFalse(visibility.isHiddenByScreen)
    }

    /** A double tap on one card must not stack the same trip twice (iOS `push` is idempotent). */
    @Test fun theSameTripIsPushedOnce() {
        showMe()
        val destination = TripsDestination.TripDetail(latest().id)
        push(destination)
        push(destination)
        assertEquals(1, feature.meNavigator.stack.size)
    }

    @Test fun deletingFromTheActionsAsksFirstAndCancelKeepsTheTrip() {
        showMe()
        val trip = latest()
        openTrip(trip)
        compose.onNodeWithTag("detail_actions").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("detail_action_delete").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("app_dialog").assertExists()

        compose.onNodeWithTag("app_dialog_cancel").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("app_dialog").assertDoesNotExist()
        compose.onNodeWithTag("trip_detail").assertExists()
        assertNotNull(detail(trip.id))
    }

    @Test fun confirmingTheDeleteRemovesTheTripAndLeavesItsScreen() {
        showMe()
        val trip = latest()
        openTrip(trip)
        compose.onNodeWithTag("detail_actions").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("detail_action_delete").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("detail_confirm").performClick()
        compose.waitForIdle()

        assertTrue(trips().none { it.id == trip.id })
        assertTrue(feature.meNavigator.stack.isEmpty())
        compose.onNodeWithTag("trip_detail").assertDoesNotExist()
    }

    @Test fun deletingAJourneyKeepsItsTripsAndLeavesItsScreen() {
        showMe()
        val journey = seededJourney()
        val before = trips().size
        push(TripsDestination.JourneyDetail(journey.id))
        compose.onNodeWithTag("detail_actions").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("journey_action_delete").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("journey_confirm").performClick()
        compose.waitForIdle()

        assertTrue(onMain { repository.fetchJourneys() }.isEmpty())
        assertEquals(before, trips().size)
        assertTrue(feature.meNavigator.stack.isEmpty())
    }

    @Test fun publishingAPrivateTripGoesThroughItsSheet() {
        signIn()
        showMe()
        val trip = latest()
        assertTrue(trip.isPrivate)
        openTrip(trip)
        compose.onNodeWithTag("detail_actions").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("detail_action_publish").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("publish_sheet").assertExists()
        assertTrue(detail(trip.id)!!.isPrivate)

        compose.onNodeWithTag("publish_confirm").performClick()
        compose.waitForIdle()
        assertFalse(detail(trip.id)!!.isPrivate)
    }

    @Test fun renamingACheckpointSavesItsNameWhenTheSheetCloses() {
        showMe()
        val road = rostovRoad()
        openTrip(road)
        compose.onAllNodesWithTag("moment_checkpoint")[0].performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("checkpoint_name").performTextReplacement("Тихорецк")
        compose.onNodeWithTag("checkpoint_close").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("checkpoint_editor").assertDoesNotExist()
        val names = detail(road.id)!!.checkpoints.sortedBy { it.timestamp }.map { it.name }
        assertEquals(listOf("Тихорецк", "Батайск"), names)
    }

    @Test fun aHeldHistoryCardOpensTheJourneyComposer() {
        showMe()
        compose.onAllNodesWithTag("profile_trip_card")[0].performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("journey_composer").assertExists()
    }

    @Test fun openingTheDraftsClearsTheDot() {
        showMe()
        assertTrue(feature.drafts.state.value.hasUnseen)
        compose.onNodeWithTag("profile_drafts_row").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("drafts_list").assertExists()
        assertFalse(feature.drafts.state.value.hasUnseen)
    }

    @Test fun aSwipeRightConfirmsADraft() {
        showMe()
        val first = drafts().first()
        push(TripsDestination.Drafts)
        compose.onAllNodesWithTag("draft_row").assertCountEquals(4)

        compose.onAllNodesWithTag("draft_row")[0].performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(listOf(first.id to DraftDecision.confirm), decisions)
        compose.onAllNodesWithTag("draft_row").assertCountEquals(3)
    }

    @Test fun aSwipeLeftAsksBeforeDiscardingADraft() {
        showMe()
        val first = drafts().first()
        push(TripsDestination.Drafts)

        compose.onAllNodesWithTag("draft_row")[0].performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("app_dialog").assertExists()
        assertTrue(decisions.isEmpty())

        compose.onNodeWithTag("drafts_delete_confirm").performClick()
        compose.waitForIdle()
        assertEquals(listOf(first.id to DraftDecision.discard), decisions)
        compose.onAllNodesWithTag("draft_row").assertCountEquals(3)
    }

    @Test fun selectionModeConfirmsOnlyTheChosenDrafts() {
        showMe()
        val all = drafts()
        push(TripsDestination.Drafts)
        compose.onNodeWithTag("drafts_select").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("draft_row")[0].performClick()
        compose.onAllNodesWithTag("draft_row")[2].performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("drafts_mine_selected").performClick()
        compose.waitForIdle()

        assertEquals(setOf(all[0].id, all[2].id), decisions.map { it.first }.toSet())
        assertTrue(decisions.all { it.second == DraftDecision.confirm })
        compose.onAllNodesWithTag("draft_row").assertCountEquals(2)
    }

    @Test fun aFeedCardOpensTheTripWithWhatTheCardKnows() {
        signIn()
        val cards = socialCards()
        social.feedPage = { SocialFeedResponse(cards) }
        showFeed()
        compose.onAllNodesWithTag("social_trip_card")[0].performClick()
        compose.waitForIdle()

        val top = feature.feedNavigator.top as TripsDestination.TripDetail
        assertEquals(cards[0].id, top.id)
        assertEquals(cards[0].id, top.social?.id)
        compose.onNodeWithTag("trip_detail").assertExists()
        // Someone else's trip has no owner menu.
        compose.onNodeWithTag("detail_actions").assertDoesNotExist()
    }

    @Test fun aDialogOnTheFeedHidesTheTabBar() {
        signIn()
        val cards = socialCards()
        social.feedPage = { SocialFeedResponse(cards) }
        showFeed()
        compose.onAllNodesWithTag("feed_card_menu")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("feed_card_action_delete").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("app_dialog").assertExists()
        assertTrue(visibility.isHiddenByScreen)

        compose.onNodeWithTag("app_dialog_cancel").performClick()
        compose.waitForIdle()
        assertFalse(visibility.isHiddenByScreen)
    }
}
