// iOS: the SF Symbols of TrackingView, IdleHUDView, VehicleChip, GPSIndicatorView, TripCompleteSummaryView.
package com.onezee.triptrack.recording

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every drawing of [RecordingGlyph] is placed by its ink, so its ink must be what the iPhone's symbol
 * measures at the size TripTrack sets it in (macOS, 8× renders, [RecordingInk]). Rendered here on white
 * and measured: within half a dp on each side, the width of a hairline at these sizes.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class RecordingGlyphTests(private val glyph: RecordingGlyph, private val ink: RecordingInk.Ink) : RecordingScreenTest() {
    @Test fun theGlyphIsAsLargeAsItsSymbol() {
        show { Box(Modifier.testTag("cell").size(60.dp).background(Color.White), contentAlignment = Alignment.Center) { RecordingGlyphIcon(glyph, ink, Color.Black) } }
        val bitmap = compose.onNodeWithTag("cell").captureToImage().asAndroidBitmap()
        var minX = bitmap.width; var minY = bitmap.height; var maxX = -1; var maxY = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (android.graphics.Color.red(bitmap.getPixel(x, y)) < 128) {
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        assertTrue(maxX >= 0, "$glyph drew nothing")
        val density = bitmap.width / 60f
        val width = (maxX - minX + 1) / density
        val height = (maxY - minY + 1) / density
        println("INK $glyph want ${ink.width}x${ink.height} got ${"%.2f".format(width)}x${"%.2f".format(height)}")
        assertEquals(ink.width, width, 0.7f, "$glyph is ${width}dp wide, its symbol ${ink.width}dp")
        assertEquals(ink.height, height, 0.7f, "$glyph is ${height}dp tall, its symbol ${ink.height}dp")
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun glyphs(): List<Array<Any>> = listOf(
            RecordingGlyph.location to RecordingInk.locationMedium18,
            RecordingGlyph.locationFill to RecordingInk.locationFillMedium18,
            RecordingGlyph.locationNorthLineFill to RecordingInk.locationNorthLineFillMedium18,
            RecordingGlyph.personFill to RecordingInk.personFillSemibold12,
            RecordingGlyph.chevronUpChevronDown to RecordingInk.chevronUpChevronDownBold10,
            RecordingGlyph.route to RecordingInk.routeRegular13,
            RecordingGlyph.mountainTwo to RecordingInk.mountainTwoRegular13,
            RecordingGlyph.antenna to RecordingInk.antennaSemibold14,
            RecordingGlyph.antennaSlash to RecordingInk.antennaSlashSemibold15,
            RecordingGlyph.exclamationCircleFill to RecordingInk.exclamationCircleFillSemibold14,
            RecordingGlyph.textAlignLeft to RecordingInk.textAlignLeftRegular15,
            RecordingGlyph.flameFill to RecordingInk.flameFillRegular14,
            RecordingGlyph.circlePath to RecordingInk.circlePathRegular14,
            RecordingGlyph.sealFill to RecordingInk.sealFillSemibold14,
        ).map { (glyph, ink) -> arrayOf(glyph, ink) }
    }
}

/** A specimen of every drawing at 4× the iPhone's size, for the eye: the baseline of the shapes. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingGlyphSpecimenTests : RecordingScreenTest() {
    @Test fun specimen() {
        val all = RecordingGlyphTests.glyphs().map { it[0] as RecordingGlyph to it[1] as RecordingInk.Ink }
        show {
            androidx.compose.foundation.layout.Column(Modifier.testTag("specimen").background(Color.White).padding(12.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                for (line in all.chunked(4)) Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    for ((glyph, ink) in line) Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                        // 4× of the iPhone's size, so the shapes are checked against the symbols at one scale.
                        RecordingGlyphIcon(glyph, RecordingInk.Ink(ink.width * 4, ink.height * 4), Color.Black)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("specimen").captureRoboImage("src/test/screenshots/recording_glyphs_light.png")
    }
}
