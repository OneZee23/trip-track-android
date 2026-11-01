// iOS: StatsScreenFlowTests, PublicAchievementsFlowTests, PublicLevelsFlowTests,
// PublicMapFlowTests, ProfileTapTargetsTests. Native map gestures are outside Robolectric.
package com.onezee.triptrack.social

import androidx.compose.ui.test.*
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.model.SocialTripResponse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SocialFlowTests : SocialScreenTest() {
    @Test fun barSelectsMonthAndChipsReturnToIt() {
        showDestination(TripsDestination.PublicStats(SocialFixtures.account,"Test Traveller"))
        compose.onNodeWithTag("stats_month_8").performClick()
        compose.onNodeWithText("Сентябрь 2026").assertExists()
        compose.onNodeWithTag("stats_month_11").assertIsNotEnabled()
        compose.onNodeWithTag("stats_range_year").performClick().assertIsSelected()
        compose.onNodeWithTag("stats_range_year").performClick().assertIsNotSelected()
        compose.onNodeWithText("Сентябрь 2026").assertExists()
        compose.onNodeWithTag("stats_range_all").performClick()
        compose.onNodeWithTag("stats_month_8").assertIsNotEnabled()
        compose.onNodeWithTag("stats_year_back").assertDoesNotExist()
    }
    @Test fun publicAchievementsUseOwnerAndSecretFilter() {
        showDestination(TripsDestination.PublicAchievements(SocialFixtures.account,"Test Traveller",listOf("first_trip","iron_butt")))
        compose.onNodeWithText("Test Traveller").assertExists()
        compose.onNodeWithTag("achievements_filter_1").performClick()
        compose.onNodeWithTag("achievement_first_trip").performClick()
        compose.onNodeWithTag("public_badge_overlay").assertExists()
        compose.onNodeWithTag("badge_close").performClick()
        compose.onNodeWithTag("achievements_filter_2").performClick()
        compose.onAllNodesWithText("???").onFirst().assertExists()
    }
    @Test fun levelAndBackStayOnTheirProfileStack() {
        showDestination(TripsDestination.PublicProfile(SocialFixtures.account,SocialFixtures.author))
        compose.onNodeWithTag("public_lvl_pill").performClick()
        compose.onNodeWithTag("levels_screen").assertExists()
        compose.onNodeWithText("Test Traveller").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("public_profile").assertExists()
    }
    @Test fun commentAffordanceOpensThreadAndAuthorReturnsToThread() {
        social.remoteTrip={SocialTripResponse(SocialFixtures.trip(0))}
        social.commentsPage={com.onezee.triptrack.model.TripCommentsResponse(listOf(com.onezee.triptrack.model.TripComment(java.util.UUID.randomUUID(),SocialFixtures.author,"Красивая дорога",SocialFixtures.now,false)))}
        showDestination(TripsDestination.PublicProfile(SocialFixtures.account,SocialFixtures.author))
        compose.onAllNodesWithTag("feed_card_comments").onFirst().performScrollTo().performClick()
        compose.onNodeWithTag("comments_sheet").assertExists()
        compose.onAllNodesWithTag("comment_author").onFirst().performClick()
        compose.onNodeWithTag("public_profile").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("comments_sheet").assertExists()
    }
}
