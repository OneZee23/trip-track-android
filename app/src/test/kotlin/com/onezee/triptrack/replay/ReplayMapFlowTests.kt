package com.onezee.triptrack.replay

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.onezee.triptrack.trips.TripsScreenTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// iOS: CompanionsAndReplayTests.swift, test_replay_shows_map; ReplayBarStateTests.swift, entry/stop.
@RunWith(RobolectricTestRunner::class)
class ReplayMapFlowTests : TripsScreenTest() {
    @Test fun tripMapOpensIdleScrubsStopsAndReopensIdle() {
        showMe()
        openTrip(rostovRoad())
        compose.onNodeWithTag("detail_map_expand").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("fullscreen_replay_play").assertExists()
        compose.onNodeWithTag("fullscreen_replay_stop").assertDoesNotExist()
        compose.onNodeWithTag("fullscreen_replay_progress").performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        compose.onNodeWithTag("fullscreen_replay_stop").assertExists().performClick()
        compose.onNodeWithTag("fullscreen_replay_stop").assertDoesNotExist()
        compose.onNodeWithTag("fullscreen_map_close").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("fullscreen_map").assertDoesNotExist()
        compose.onNodeWithTag("detail_map_expand").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("fullscreen_replay_stop").assertDoesNotExist()
    }
}
