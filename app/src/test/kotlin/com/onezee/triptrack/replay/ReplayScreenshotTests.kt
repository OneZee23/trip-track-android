package com.onezee.triptrack.replay

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.trip.CheckpointState
import com.onezee.triptrack.feature.trip.TripDetailState
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.feature.trip.ui.*
import com.onezee.triptrack.map.replay.ReplayBarState
import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// iOS: CompanionsAndReplayTests.swift, replay frames; ReplayRelightShotTests.swift, stop restoration.
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi", application = Application::class)
class ReplayScreenshotTests(private val dark: Boolean) {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:org.junit.Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)
    private val t0 = Instant.ofEpochSecond(1_700_000_000)
    private val coords = listOf(GeoPoint(45.0,39.0), GeoPoint(45.002,39.003), GeoPoint(45.004,39.001), GeoPoint(45.006,39.005))
    private val input = TripReplayInput(coords, listOf(10.0,12.0,-1.0,8.0), (0..3).map { t0.plusSeconds(it * 10L) })
    private val checkpoint = CheckpointState(UUID.fromString("00000000-0000-0000-0000-000000000001"),t0.plusSeconds(10),coords[1],null,1,1250.0,10.0,emptyList(),null)
    private val theme get() = if (dark) "dark" else "light"

    private fun show(player: TripReplayPlayer, follows: Boolean = false, width: Int = 411) {
        val state = TripDetailState(trip = TripSummary.from(Trip(startDate = t0, distance = 4000.0)), isLoading = false, checkpoints = listOf(checkpoint), replay = input)
        compose.setContent {
            TripTrackTheme(isDark = dark, language = AppLanguage.ru) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Box(Modifier.width(width.dp).fillMaxHeight().background(AppTheme.colors.cardAlt)) {
                        // Native GL/tiles require a device; the route-only ground keeps chrome snapshots deterministic.
                        RoutePlaceholder(coords, Modifier.fillMaxSize())
                        FullscreenChrome(state, player.frame.holdingIndex?.let { "cp:${checkpoint.id}" }, player,
                            follows, {}, player::stop, true, {}, {}, { player.pause() }, {})
                    }
                }
            }
        }
    }
    private fun shot(name: String) { compose.waitForIdle(); compose.onRoot().captureRoboImage("src/test/screenshots/replay_${name}_$theme.png") }

    @Test fun idle() {
        val player = TripReplayPlayer(input,4000.0); player.change { setHolds(listOf(checkpoint.timestamp)) }; show(player)
        compose.onNodeWithTag("fullscreen_replay_play").assertExists()
        compose.onNodeWithTag("fullscreen_replay_stop").assertDoesNotExist()
        assertNull(player.frame.head)
        shot("idle")
    }
    @Test fun playingAndStopped() {
        val player = TripReplayPlayer(input,4000.0); player.change { setHolds(listOf(checkpoint.timestamp)); play(0.0); tick(.5) }; show(player)
        compose.onNodeWithTag("fullscreen_replay_stop").assertExists(); shot("playing")
        compose.onNodeWithTag("fullscreen_replay_rate").performClick(); assertEquals(1.5,player.frame.rate)
        compose.onNodeWithTag("fullscreen_replay_stop").performClick()
        assertNull(player.frame.head); assertNull(player.litFraction); assertEquals(ReplayBarState.idle,player.frame.phase)
        compose.onNodeWithTag("fullscreen_replay_stop").assertDoesNotExist(); shot("stopped")
    }
    @Test fun pausedAndScrubbed() {
        val player = TripReplayPlayer(input,4000.0); show(player)
        compose.onNodeWithTag("fullscreen_replay_progress").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(.65f)) }
        assertEquals(ReplayBarState.paused,player.frame.phase); assertEquals(.65,player.frame.progress,1e-6)
        shot("paused")
    }
    @Test fun checkpointHold() {
        val player = TripReplayPlayer(input,4000.0)
        player.change { setHolds(listOf(checkpoint.timestamp)); seek(.33); play(0.0); tick(.5) }
        assertEquals(0,player.frame.holdingIndex); show(player); shot("checkpoint")
    }
    @Test fun followingKnownAndUnknownSpeed() {
        val player = TripReplayPlayer(input,4000.0); player.change { seek(.1) }; show(player,true)
        compose.onNodeWithTag("replay_speed").assertExists(); shot("follow")
        compose.runOnUiThread { player.scrub(.55) }
        compose.onNodeWithTag("replay_speed").assertDoesNotExist(); shot("unknown_speed")
    }
    @Test fun narrowControls() {
        val player = TripReplayPlayer(input,4000.0); player.change { seek(.5); repeat(4) { cycleRate() } }; show(player,width = 320); shot("narrow")
    }
    @Test fun noTimestampsUsesCrawlWithoutSeekOrRate() {
        val player = TripReplayPlayer(input.copy(timestamps = emptyList()),4000.0); show(player)
        compose.onNodeWithTag("fullscreen_crawl_progress").assertExists()
        compose.onNodeWithTag("fullscreen_replay_progress").assertDoesNotExist()
        compose.onNodeWithTag("fullscreen_replay_rate").assertDoesNotExist()
        compose.onNodeWithTag("replay_timecode").assertDoesNotExist(); shot("crawl")
    }
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false),arrayOf(true))
    }
}
