// iOS: TripJourneyNavigationTests, DraftsFlowShotTests, TripDetailOpeningTests (the asserted steps).
package com.onezee.triptrack.trips

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.espresso.Espresso
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
        compose.onNodeWithTag("screen_me").performScrollToNode(hasTestTag("profile_trip_${latest().id}"))
        compose.onNodeWithTag("profile_trip_${latest().id}").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("trip_detail").assertExists()
        assertEquals(latest().id, (meNavigator.current as com.onezee.triptrack.feature.me.MeRoute.Trip).id)
        // The screen leaves the tab bar to the screens under it.
        assertTrue(visibility.isHiddenByScreen)

        compose.onNodeWithTag("detail_back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("trip_detail").assertDoesNotExist()
        compose.onNodeWithTag("profile_trip_${latest().id}").assertIsDisplayed()
        assertTrue(meNavigator.path.isEmpty())
        assertFalse(visibility.isHiddenByScreen)
    }

    @Test fun historyBackRestoresTheSameScrollOffset() {
        showMe()
        val card = "profile_trip_${latest().id}"
        compose.onNodeWithTag("screen_me").performScrollToNode(hasTestTag(card))
        fun offset() = compose.onNodeWithTag("screen_me").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val before = offset()
        assertTrue(before > 0f)
        compose.onNodeWithTag(card).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("trip_detail").assertExists()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag(card).assertIsDisplayed()
        assertEquals(before, offset())
        assertTrue(feature.feedNavigator.stack.isEmpty())
    }

    @Test fun draftsRowOpensTheSharedListThenItsTripAndBackReturnsToDrafts() {
        showMe()
        compose.onNodeWithTag("screen_me").performScrollToNode(hasTestTag("profile_drafts"))
        compose.onNodeWithTag("profile_drafts").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("drafts_list").assertExists()
        assertEquals(com.onezee.triptrack.feature.me.MeRoute.Drafts, meNavigator.current)
        // SH-09: iOS keeps the floating bar on the list, with «Все мои» standing over it.
        assertFalse(visibility.isHiddenByScreen)
        compose.onNodeWithTag("tab_bar").assertExists()
        compose.onAllNodesWithTag("draft_row")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("trip_detail").assertExists()
        assertTrue(visibility.isHiddenByScreen)
        compose.onNodeWithTag("detail_back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("drafts_list").assertExists()
        assertFalse(visibility.isHiddenByScreen)
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("profile_drafts").assertIsDisplayed()
        assertTrue(meNavigator.path.isEmpty())
    }

    @Test fun aJournalJourneyOpensItsSharedDetailInTheMeStack() {
        showMe()
        val journey = seededJourney()
        val tag = "profile_journey_${journey.id}"
        compose.onNodeWithTag("screen_me").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("journey_detail").assertExists()
        assertEquals(com.onezee.triptrack.feature.me.MeRoute.Journey(journey.id), meNavigator.current)
        assertTrue(feature.feedNavigator.stack.isEmpty())
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    @Test fun aPinnedTripOpensTheSameDetail() {
        val trip = latest()
        showMe { state -> state.copy(history = state.history.copy(pinned = listOf(trip), chapters = emptyList())) }
        val card = "profile_trip_${trip.id}"
        compose.onNodeWithTag("screen_me").performScrollToNode(hasTestTag(card))
        compose.onNodeWithTag(card).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("trip_detail").assertExists()
        assertEquals(trip.id, (meNavigator.current as com.onezee.triptrack.feature.me.MeRoute.Trip).id)
    }

    @Test fun anExpandedRoutineOpensItsTripAndReturnsWithTheRoutineExpanded() {
        val trips = trips().sortedByDescending { it.startDate }.take(3)
        val group = com.onezee.triptrack.model.ProfileRoutineTrips(trips, trips.sumOf { it.distance }, trips)
        showMe { state -> state.copy(history = state.history.copy(pinned = emptyList(), chapters = listOf(
            state.history.chapters.first().copy(rows = listOf(com.onezee.triptrack.model.HistoryRow.Routine(group)))
        ))) }
        compose.onNodeWithTag("screen_me").performScrollToNode(hasTestTag("profile_routine_toggle"))
        compose.onNodeWithTag("profile_routine_toggle").performClick()
        val row = "profile_routine_trip_${trips.first().id}"
        compose.onNodeWithTag("screen_me").performScrollToNode(hasTestTag(row))
        // Like ProfileCardShotTests.align: the floating bar overlaps the lazy list's viewport.
        compose.onNodeWithTag("screen_me").performTouchInput { swipeUp() }
        compose.onNodeWithTag(row).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("trip_detail").assertExists()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag(row).assertIsDisplayed()
    }

    // iOS: TripJourneyNavigationTests.testOwnTripCanOpenItsJourneyAndReturnToItsMap.
    @Test fun aJourneyLegCanOpenItsJourneyAndBackKeepsTheSameTripEntry() {
        showMe()
        val journey = seededJourney()
        push(TripsDestination.JourneyDetail(journey.id))
        compose.onAllNodesWithTag("journey_leg_row")[0].performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("trip_detail").assertExists()
        val trip = meNavigator.current
        val entry = meNavigator.stack.entryIds.last()
        compose.onNodeWithTag("detail_actions").performClick()
        compose.onNodeWithTag("detail_action_journey").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("journey_detail").assertExists()
        assertEquals(3, meNavigator.path.size)
        compose.onNodeWithTag("detail_back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("trip_detail").assertExists()
        assertEquals(trip, meNavigator.current)
        assertEquals(entry, meNavigator.stack.entryIds.last())
        assertTrue(feature.feedNavigator.stack.isEmpty())
    }

    /** A double tap on one card must not stack the same trip twice (iOS `push` is idempotent). */
    @Test fun theSameTripIsPushedOnce() {
        showMe()
        val destination = TripsDestination.TripDetail(latest().id)
        push(destination)
        push(destination)
        assertEquals(1, meNavigator.path.size)
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
        assertTrue(meNavigator.path.isEmpty())
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
        assertTrue(meNavigator.path.isEmpty())
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
        compose.onNodeWithTag("screen_me").performScrollToNode(hasTestTag("profile_trip_${latest().id}"))
        compose.onNodeWithTag("profile_trip_${latest().id}").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("journey_composer").assertExists()
    }

    @Test fun openingTheDraftsClearsTheDot() {
        showMe()
        assertTrue(feature.drafts.state.value.hasUnseen)
        compose.onNodeWithTag("screen_me").performScrollToNode(hasTestTag("profile_drafts"))
        compose.onNodeWithTag("profile_drafts").performClick()
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
        // The question stands over the bar, which would otherwise stay tappable under the scrim.
        assertTrue(visibility.isHiddenByScreen)

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
