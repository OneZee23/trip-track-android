// iOS: TrackingView.swift, IdleHUDView.swift, GPSIndicatorView.swift, VehicleChip.swift, SlideToStartView.swift,
// RecoveryPromptSheet.swift, TripCompleteSummaryView.swift, TripRevealedBlock.swift.
package com.onezee.triptrack.recording

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.location.recording.RecorderState
import com.onezee.triptrack.location.recording.StartRefusal
import com.onezee.triptrack.model.TripConfirmation
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The 3 November 2026 audit, «Recording and summary» (RC-01 to RC-34): the numbers of the iPhone's record
 * screen and finish card, each of them one the old screen missed. The iPhone's sizes are in points, the same
 * numbers as dp here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingDesignTests : RecordingScreenTest() {
    private val fixture = RecordingFixtures
    // Not «density»: inside an extension on Bitmap that name is the bitmap's own dpi.
    private val pxPerDp get() = compose.activity.resources.displayMetrics.density
    private val lightMap = Color(0xFFE9E7DF)

    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun bounds(tag: String): Rect = node(tag).fetchSemanticsNode().boundsInRoot
    private fun Float.inDp() = this / pxPerDp
    private fun width(tag: String) = bounds(tag).width.inDp()
    private fun height(tag: String) = bounds(tag).height.inDp()
    /** A node's size in dp: layout rounds to whole pixels, so a size is right to within 1 px (0.4 dp). */
    private fun assertSize(tag: String, w: Float, h: Float, tolerance: Float = .45f) {
        assertEquals(w, width(tag), tolerance, "$tag width")
        assertEquals(h, height(tag), tolerance, "$tag height")
    }
    private fun assertNear(expected: Int, actual: Int, tolerance: Int, message: String) = assertTrue(abs(expected - actual) <= tolerance, "$message: $actual is not within $tolerance of $expected")
    private fun screenshot(): Bitmap { compose.waitForIdle(); return compose.onRoot().captureToImage().asAndroidBitmap() }
    private fun Bitmap.at(x: Dp, y: Dp): Int = getPixel((x.value * pxPerDp).toInt().coerceIn(0, width - 1), (y.value * pxPerDp).toInt().coerceIn(0, height - 1))
    private fun Bitmap.red(x: Dp, y: Dp): Int = android.graphics.Color.red(at(x, y))
    private fun Bitmap.green(x: Dp, y: Dp): Int = android.graphics.Color.green(at(x, y))
    private fun Bitmap.blue(x: Dp, y: Dp): Int = android.graphics.Color.blue(at(x, y))

    /** One composition per test: a test that shows a second state swaps the scene, it cannot call setContent twice. */
    private var scene by mutableStateOf<@Composable () -> Unit>({})
    private var shown = false
    private fun display(dark: Boolean = true, content: @Composable () -> Unit) {
        if (!shown) {
            shown = true
            scene = content
            show(dark) { scene() }
        } else compose.runOnUiThread { scene = content }
        compose.waitForIdle()
    }

    private fun idle(state: TrackingState = TrackingState(recorder = RecorderState(gpsAccuracy = 8.0), totalMetres = 142_500_000.0, tripCount = 42), denied: Boolean = false,
                     discarded: String? = null, dock: Dp = 0.dp, canZoomIn: Boolean = true, canZoomOut: Boolean = true, mode: TrackingCameraMode = TrackingCameraMode.follow, vehicle: com.onezee.triptrack.model.Vehicle? = fixture.car) {
        display {
            TrackingScreen(state, vehicle, denied, TrackingActions(), fixture.now, discarded = discarded, dockInset = dock, canZoomIn = canZoomIn, canZoomOut = canZoomOut, cameraMode = mode,
                map = { TrackingMapPlaceholder(emptyList(), Modifier.fillMaxSize(), lightMap) })
        }
    }

    private fun recording(state: TrackingState = fixture.recording) {
        display { TrackingScreen(state, fixture.car, false, TrackingActions(), fixture.now, dockInset = 0.dp,
            map = { TrackingMapPlaceholder(emptyList(), Modifier.fillMaxSize(), lightMap) }) }
    }

    // RC-01 and RC-10: the card's shadow, and no haze along the bottom.
    @Test fun idleCardCastsTheIphonesShadowAndTheBottomHazeIsGone() {
        idle()
        val card = bounds("idle_hud")
        val shot = screenshot()
        val x = 30.dp
        val map = shot.red(x, (card.top.inDp() - 90).dp)
        val above = shot.red(x, (card.top.inDp() - 3).dp)
        assertTrue(map - above >= 12, "the map darkens above the card edge: $map then $above")
        val bottom = shot.red(x, (compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom.inDp() - 2).dp)
        assertTrue(bottom <= map + 1, "the strip under the card is not lightened by a haze: $bottom against $map")
    }

    // RC-02: 44, outlined by a centred hairline of white at .3, dim at the zoom limits.
    @Test fun mapButtonsAreOutlinedAndDimAtTheZoomLimits() {
        idle()
        for (tag in listOf("tracking_locate", "tracking_zoom_in", "tracking_zoom_out")) assertSize(tag, 44f, 44f)
        val shot = screenshot()
        val button = bounds("tracking_zoom_in")
        val midY = button.center.y.inDp().dp
        val fill = shot.red((button.left.inDp() + 5).dp, midY)
        val rim = shot.red((button.left.inDp() + .25f).dp, midY)
        assertTrue(rim >= fill + 25, "the hairline reads brighter than the fill: $rim over $fill")
        assertNear(78, fill, 6, "white at .2 under .85 over this map")
        fun glyphBrightness(): Int {
            val image = screenshot()
            val b = bounds("tracking_zoom_in")
            var best = 0
            for (px in 0 until 20) for (py in 0 until 20) best = maxOf(best, image.red((b.left.inDp() + 12 + px * 1f).dp, (b.top.inDp() + 12 + py * 1f).dp))
            return best
        }
        val enabled = glyphBrightness()
        idle(canZoomIn = false)
        val limit = glyphBrightness()
        assertTrue(enabled >= 235, "the plus is white: $enabled")
        assertTrue(limit <= 175, "the plus dims to .3 at the limit: $limit")
    }

    // RC-03: the arrow of the camera mode.
    @Test fun locationButtonDrawsTheArrowOfItsMode() {
        fun ink(mode: TrackingCameraMode): Pair<Float, Float> {
            idle(mode = mode)
            val shot = screenshot()
            val b = bounds("tracking_locate")
            val fill = shot.red((b.left.inDp() + 5).dp, (b.top.inDp() + 5).dp)
            var minX = 99f; var maxX = -1f; var minY = 99f; var maxY = -1f
            var x = 4f
            while (x < 40f) {
                var y = 4f
                while (y < 40f) {
                    val gap = abs(shot.red((b.left.inDp() + x).dp, (b.top.inDp() + y).dp) - fill) + abs(shot.green((b.left.inDp() + x).dp, (b.top.inDp() + y).dp) - fill)
                    if (gap > 90) { minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y) }
                    y += .25f
                }
                x += .25f
            }
            return (maxX - minX) to (maxY - minY)
        }
        val (outlineW, outlineH) = ink(TrackingCameraMode.none)
        assertEquals(17.1f, outlineW, 1.2f, "location is 17.1 wide at 18 medium"); assertEquals(17.1f, outlineH, 1.2f)
        val (filledW, filledH) = ink(TrackingCameraMode.follow)
        assertEquals(17.1f, filledW, 1.2f); assertEquals(16.3f, filledH, 1.2f)
        val (headingW, headingH) = ink(TrackingCameraMode.heading)
        assertEquals(13.1f, headingW, 1.2f, "location.north.line.fill is 13.1 wide"); assertEquals(21.0f, headingH, 1.2f, "and 21 tall")
    }

    // RC-04 and RC-05: the avatar, the up-and-down chevron, the person.
    @Test fun vehicleChipHasTheAvatarAndTheChevronAndThePassengerTheePerson() {
        idle()
        assertSize("vehicle_chip_avatar", 16f, 16f)
        assertSize("vehicle_chip_chevron", 7.38f, 10.5f)
        assertSize("idle_passenger_person", 10.0f, 10.62f)
        assertEquals(29.7f, height("vehicle_chip"), .7f, "chip 7 + 15.7 + 7")
        assertEquals(29.7f, height("idle_passenger_toggle"), .7f, "the toggle is as tall as the chip: padding 7, not 8")
    }

    @Test fun chipWithoutAGarageHasNoChevronAndRidingAsAPassengerShowsThePerson() {
        idle(vehicle = null)
        node("vehicle_chip_chevron").assertDoesNotExist()
        recording(fixture.recording.copy(recorder = fixture.recording.recorder.copy(vehicleId = null, isTransfer = true)))
        node("vehicle_chip_person").assertExists()
        node("vehicle_chip_avatar").assertDoesNotExist()
    }

    // RC-06: 6 of padding.
    @Test fun gpsPillIs27DpTall() {
        idle()
        assertEquals(27.3f, height("gps_indicator"), .7f)
    }

    // RC-07: the live thumb glows in accent, the held one does not.
    @Test fun liveThumbGlowsAndTheHeldOneDoesNot() {
        fun halo(blocked: Boolean): Int {
            display { Box(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 40.dp), contentAlignment = Alignment.Center) { SlideToStart(blocked, {}, {}) } }
            val track = bounds("slide_to_start")
            val shot = screenshot()
            // Above the track, over the middle of the thumb that stands 4 inside it: 6 from the thumb's edge.
            val x = (track.left.inDp() + 32).dp
            val y = (track.top.inDp() - 2).dp
            return shot.red(x, y) - shot.blue(x, y)
        }
        val live = halo(false)
        val held = halo(true)
        assertTrue(live >= 6, "the glow is accent over black, reaching past the track: $live")
        assertTrue(held <= 1, "the held thumb casts nothing: $held")
    }

    // RC-08: the outer ring starts at half strength.
    @Test fun outerRingStartsAtHalfStrength() {
        idle()
        val card = bounds("idle_hud")
        val shot = screenshot()
        val cy = (card.top.inDp() + 22 + 50).dp
        val cx = card.center.x.inDp().dp
        val ring = shot.red((cx.value + 50).dp, cy)
        val inside = shot.red((cx.value + 44).dp, cy)
        // Accent at .2 of .5 over the card: about 15 levels over what lies beside it.
        assertNear(15, ring - shot.red((cx.value + 47).dp, (cy.value + 40).dp), 7, "the ring is $ring where its inside is $inside")
    }

    // RC-09: the blocks stand 24 above the physical bottom, above a tappable bar only when there is one.
    @Test fun controlBlocksDockAboveTheBottomOrAboveATappableBar() {
        fun gap(dock: Dp, state: TrackingState, tag: String): Float {
            display { TrackingScreen(state, fixture.car, false, TrackingActions(), fixture.now, dockInset = dock, contentInsets = WindowInsets(top = 28.dp, bottom = 34.dp)) }
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            return (root.bottom - bounds(tag).bottom).inDp()
        }
        assertEquals(24f, gap(0.dp, fixture.recording, "tracking_stop"), .6f, "gesture navigation: 24 above the edge, as the iPhone")
        assertEquals(24f, gap(0.dp, TrackingState(), "idle_hud"), .6f)
        assertEquals(72f, gap(48.dp, fixture.recording, "tracking_stop"), .6f, "three-button navigation: 24 above its 48")
        assertEquals(72f, gap(48.dp, TrackingState(), "idle_hud"), .6f)
        val back = bounds("tracking_back")
        assertEquals(32f, back.top.inDp(), .6f, "the top row is 4 under the status bar")
    }

    // RC-11: the symbols of the stat tiles.
    @Test fun statTilesUseTheIphonesSymbols() {
        recording()
        assertSize("recording_stat_distance_icon", 11.5f, 14.0f)
        assertSize("recording_stat_altitude_icon", 22.38f, 11.25f)
        assertEquals(68.3f, height("recording_stats_panel"), 1f, "14 + 40 + 14")
    }

    // RC-12: sizes of the control row's marks, a shadow under the stop button, a hairline on the pause button.
    @Test fun controlRowHasItsMarksStrokesAndShadows() {
        recording()
        assertSize("tracking_stop_glyph", 15.7f, 15.7f)
        assertSize("tracking_pause_glyph", 12.88f, 16.75f)
        assertSize("tracking_stop", 52f, 52f)
        assertSize("tracking_pause", width("tracking_pause"), 52f)
        val shot = screenshot()
        val stop = bounds("tracking_stop")
        val below = shot.red(stop.center.x.inDp().dp, (stop.bottom.inDp() + 3).dp)
        val far = shot.red(stop.center.x.inDp().dp, (stop.bottom.inDp() + 18).dp)
        assertTrue(far - below >= 6, "the stop button casts black at .25: $below under it, $far further down")
        val pause = bounds("tracking_pause")
        val rim = shot.red((pause.left.inDp() + .25f).dp, pause.center.y.inDp().dp)
        val fill = shot.red((pause.left.inDp() + 8).dp, pause.center.y.inDp().dp)
        assertTrue(rim >= fill + 8, "the pause button has its hairline: $rim over $fill")
    }

    // RC-13: .72 over the map, not .78.
    @Test fun statsPanelGlassIsAt72() {
        recording()
        val panel = bounds("recording_stats_panel")
        val shot = screenshot()
        val value = shot.red((panel.left.inDp() + 10).dp, panel.center.y.inDp().dp)
        assertNear(94, value, 4, ".72 of (40, 40, 42) over (233, 231, 223)")
    }

    // RC-14: Inter 14 bold, 16 / 9, a shadow.
    @Test fun pausedPillIs35DpTallWithItsShadow() {
        recording(fixture.recording.copy(recorder = fixture.recording.recorder.copy(isPaused = true)))
        assertEquals(35f, height("recording_paused_pill"), .8f)
        assertSize("recording_paused_glyph", 8.12f, 10.62f)
        val pill = bounds("recording_paused_pill")
        val shot = screenshot()
        val x = pill.center.x.inDp().dp
        val near = shot.red(x, (pill.bottom.inDp() + 4).dp)
        val far = shot.red(x, (pill.bottom.inDp() + 40).dp)
        assertTrue(far - near >= 6, "the pill casts black at .28: $near under it, $far further down")
    }

    // RC-15: the label is in the top row, after the chip.
    @Test fun draftLabelSitsBesideTheChip() {
        recording(fixture.recording.copy(recorder = fixture.recording.recorder.copy(confirmation = TripConfirmation.draft)))
        val chip = bounds("vehicle_chip")
        val label = bounds("recording_draft_label")
        assertTrue(label.left >= chip.right, "after the chip")
        assertEquals(chip.center.y, label.center.y, 2f, "on the chip's line")
        assertEquals(38.5f, label.height.inDp(), .8f, "Inter 12 with 12 above and 12 below, as the code reads")
    }

    // RC-16: the symbols of the signal banners.
    @Test fun signalBannersWearTheIphonesSymbols() {
        recording(fixture.recording.copy(recorder = fixture.recording.recorder.copy(gpsStale = true)))
        assertSize("signal_lost_icon", 16.5f, 15.75f)
        assertEquals(12f, (bounds("signal_lost_icon").left - bounds("signal_lost_banner").left).inDp(), .6f)
        recording(fixture.recording.copy(recorder = fixture.recording.recorder.copy(gpsAccuracy = 60.0)))
        assertSize("signal_weak_disc", 22f, 22f)
        assertEquals(12f, (bounds("signal_weak_disc").left - bounds("signal_weak_toast").left).inDp(), .6f)
    }

    // RC-17: dark, 280 wide, the antenna in the signal's colour.
    @Test fun gpsLegendIsDarkWhateverTheTheme() {
        show(false) { Box(Modifier.padding(30.dp)) { GPSLegendCard(GPSSignal.medium, {}) } }
        assertEquals(280f, width("gps_legend"), .6f)
        assertSize("gps_legend_icon", 13.75f, 12.75f)
        val card = bounds("gps_legend")
        val shot = screenshot()
        assertNear(30, shot.red((card.left.inDp() + 8).dp, (card.top.inDp() + 30).dp), 2, "the card is (30, 30, 32), not the light theme's white")
    }

    // RC-18 and RC-19: the pill, the symbols, the return row, the shadow.
    @Test fun stopConfirmationHasItsPillItsSymbolsAndItsReturnRow() {
        show(false) { Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            // The too-short variant: the one the iPhone's 342 was measured on.
            StopConfirmationContent(com.onezee.triptrack.model.RecordingFinishPreview(fixture.id, fixture.start.plusSeconds(2), 0.0, 2.0, 0.0), false, false, {}, {}, {})
        } }
        assertEquals(32.9f, height("stop_confirm_pill"), .9f, "8 + 16.9 + 8")
        assertEquals(62f, height("stop_confirm_cancel"), .6f, "50 with 6 above and below")
        assertSize("stop_confirm_pause_glyph", 10.88f, 14f)
        assertEquals(52f, height("stop_confirm_finish"), .6f)
        assertEquals(342.5f, height("stop_confirmation"), 4f, "the card measures 342 on the iPhone")
    }

    // RC-20: the symbol before the reason, the way past it a capsule.
    @Test fun refusalToastHasItsSymbolAndACapsuleButton() {
        idle(TrackingState(refusal = StartRefusal.noFix))
        assertSize("start_refused_icon", 15.98f, 12.95f)
        assertEquals(31.7f, height("start_anyway"), .8f, "13 bold with 8 of padding, no longer a 40 dp button")
        assertEquals(width("start_refused_toast") - 28f, width("start_anyway"), .8f, "full width")
        idle(TrackingState(refusal = StartRefusal.locationDenied))
        assertSize("start_refused_icon", 14.25f, 14.25f)
    }

    // RC-21: the discard toast fills the width.
    @Test fun discardToastIsAFullWidthCapsule() {
        idle(discarded = "Поездка не сохранена")
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.width.inDp() - 32f, width("junk_discarded_toast"), .8f)
    }

    // RC-22: the subtitle at .42.
    @Test fun deniedSubtitleIsAtOfWhiteAt42() {
        idle(TrackingState(), denied = true)
        val subtitle = bounds("idle_denied_subtitle")
        val shot = screenshot()
        var best = 0
        var x = subtitle.left.inDp()
        while (x < subtitle.right.inDp()) {
            var y = subtitle.top.inDp()
            while (y < subtitle.bottom.inDp()) { best = maxOf(best, shot.red(x.dp, y.dp)); y += .4f }
            x += .4f
        }
        assertNear(162, best, 7, "white at .42 over the card (94), not .52 (178)")
    }

    // RC-23: no «saving» bar.
    @Test fun finishingShowsTheIdleHudAtOnce() {
        idle(TrackingState(recorder = RecorderState(isFinishing = true)))
        node("idle_hud").assertExists()
        node("recording_finishing").assertDoesNotExist()
    }

    // RC-24: the check before the recovered line, the second button's height.
    @Test fun recoveryChipCarriesItsCheck() {
        show(false) { RecoveryPromptContent(fixture.recovery, false, {}, {}) }
        assertSize("recovery_chip_check", 15.4f, 15.4f, .5f)
        assertEquals(48f, height("recovery_finish"), .6f)
        assertEquals(52f, height("recovery_continue"), .6f)
    }

    // RC-25: the badge opens the card in place.
    @Test fun badgeOpensTheCardInPlaceWithItsDate() {
        show(false) { TripCompleteSummary(fixture.trip, fixture.rewards, null, { _, _ -> }, {}, {}, {}, {}) }
        compose.onNodeWithTag("summary_badge_first_trip").performScrollTo().performClick()
        node("public_badge_overlay").assertExists()
        assertTrue(width("public_badge_overlay") <= 300f + 1, "a card up to 300 wide")
        node("public_badge_earned").assertExists()
    }

    // RC-26 and RC-27: the tiles of the description and the publish rows.
    @Test fun descriptionAndPublishRowsAreTilesBesideTheirText() {
        show(false) { TripCompleteSummary(fixture.trip, null, null, { _, _ -> }, {}, {}, {}, {}) }
        assertEquals(70f, height("summary_description"), .8f, "16 + 38 + 16")
        assertSize("summary_description_tile", 38f, 38f)
        assertEquals(16f, (bounds("summary_description_tile").top - bounds("summary_description").top).inDp(), .6f, "the row is top-aligned")
        assertSize("summary_publish_tile", 38f, 38f)
        assertSize("publish_toggle", 51f, 31f, .6f)
        assertEquals(16f, (bounds("publish_toggle").top - bounds("summary_publish").top).inDp(), .6f, "the switch stands at the top right")
        assertEquals(16f, (bounds("summary_publish").right - bounds("publish_toggle").right).inDp(), .6f)
    }

    // RC-28: 14 bold, the camera becomes a check.
    @Test fun bottomButtonsAre14Bold() {
        show(false) { TripCompleteSummary(fixture.trip, null, null, { _, _ -> }, {}, {}, {}, {}, photoCount = 2) }
        assertEquals(46f, width("summary_done_label"), 1.6f, "«Готово» in 14 bold is 46 wide, not 52.6")
        assertEquals(44f, height("summary_done"), .6f)
        compose.onNodeWithText("Фото (2)").assertExists()
    }

    // RC-29 and RC-30: the symbols of the Открыто header and of the rewards rows.
    @Test fun summaryRowsHaveTheirSymbols() {
        show(false) { TripCompleteSummary(fixture.trip, fixture.rewards, fixture.found, { _, _ -> }, {}, {}, {}, {}) }
        assertSize("summary_revealed_seal", 15.75f, 15.75f)
        assertSize("summary_streak_icon", 11.38f, 14.88f)
    }

    // RC-31: the pixel face in its own single weight.
    @Test fun levelUpPillIsSetInThePixelFaceRegular() {
        show(false) { TripCompleteSummary(fixture.trip, fixture.rewards, null, { _, _ -> }, {}, {}, {}, {}) }
        node("summary_level_up").assertExists()
        val glow = bounds("summary_rewards")
        val shot = screenshot()
        val under = shot.red(glow.center.x.inDp().dp, (glow.bottom.inDp() + 6).dp)
        val clear = shot.red(glow.center.x.inDp().dp, (glow.bottom.inDp() + 40).dp)
        assertTrue(abs(under - clear) <= 12, "the glow is a faint accent: $under, $clear")
    }

    // RC-32: Отмена, Описание, Сохранить in one row and the counter.
    @Test fun notesEditorIsOneRowOfCancelTitleAndSave() {
        show(false) { TripCompleteSummary(fixture.trip, null, null, { _, _ -> }, {}, {}, {}, {}) }
        compose.onNodeWithTag("summary_description").performScrollTo().performClick()
        node("summary_notes_save").assertExists()
        val cancel = bounds("summary_notes_cancel")
        val save = bounds("summary_notes_save")
        assertEquals(cancel.center.y, save.center.y, 1f, "one row")
        assertTrue(cancel.left < save.left)
        compose.onNodeWithText("Сохранить").assertExists()
        compose.onNodeWithText("0 / 500").assertExists()
    }

}
