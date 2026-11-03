package com.onezee.triptrack.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// iOS: Image(systemName:) with .font(.system(size:weight:)); ActionPopoverList.swift is the reference
// call site (15 pt semibold, a glyph box of 18 on Android).
/**
 * SH-07 of the 3 November audit: a glyph must draw about as large as the SF Symbol it stands for.
 * The convention of the call sites is a box of 1.2 × the point size, so at a box of [BOX] the symbol
 * would measure `ink × BOX / 1.2`. Every glyph is rendered and its ink compared with that, to within
 * [TOLERANCE]; the symbol sizes were measured once, on macOS, at 100 pt semibold, for sizing only.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class GlyphSizingTests(private val glyph: AppGlyph, private val symbol: Ink) {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterComponentActivity()).around(compose)

    /** Width and height of an SF Symbol's ink, in em. */
    data class Ink(val width: Float, val height: Float)

    @Test fun theGlyphDrawsAsLargeAsItsSymbol() {
        compose.setContent {
            Themed(dark = false) {
                Box(Modifier.testTag("cell").size(CELL.dp).background(Color.White), contentAlignment = Alignment.Center) {
                    Glyph(glyph, contentDescription = null, tint = Color.Black, size = BOX.dp)
                }
            }
        }
        val bitmap = compose.onNodeWithTag("cell").captureToImage().asAndroidBitmap()
        var minX = bitmap.width; var minY = bitmap.height; var maxX = -1; var maxY = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (android.graphics.Color.red(bitmap.getPixel(x, y)) < 128) {
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        assertTrue(maxX >= 0, "$glyph drew nothing")
        val density = bitmap.width / CELL.toFloat()
        val width = (maxX - minX + 1) / density
        val height = (maxY - minY + 1) / density
        val wantWidth = symbol.width * BOX / CONVENTION
        val wantHeight = symbol.height * BOX / CONVENTION
        if (glyph in OTHER_PROPORTIONS) {
            // Drawn in other proportions than their symbols: the factor matches the area instead.
            val area = kotlin.math.sqrt(width * height) / kotlin.math.sqrt(wantWidth * wantHeight)
            assertEquals(1f, area, AREA_TOLERANCE, "$glyph covers ${width}x${height}dp at a box of ${BOX}dp, its symbol ${wantWidth}x${wantHeight}dp")
        } else {
            assertEquals(1f, width / wantWidth, TOLERANCE, "$glyph is ${width}dp wide at a box of ${BOX}dp, its symbol ${wantWidth}dp")
            assertEquals(1f, height / wantHeight, TOLERANCE, "$glyph is ${height}dp tall at a box of ${BOX}dp, its symbol ${wantHeight}dp")
        }
    }

    companion object {
        private const val BOX = 100
        private const val CELL = 160

        /** Box ÷ point size at the call sites: 18 dp for a 15 pt symbol. */
        private const val CONVENTION = 1.2f
        private const val TOLERANCE = 0.15f
        private const val AREA_TOLERANCE = 0.25f

        private val OTHER_PROPORTIONS = setOf(AppGlyph.mountain, AppGlyph.roadLanes, AppGlyph.pause)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun glyphs(): List<Array<Any>> = SYMBOLS.map { (glyph, ink) -> arrayOf(glyph, ink) }

        private val SYMBOLS = listOf(
            AppGlyph.airplane to Ink(1.139f, 1.001f),
            AppGlyph.arrowClockwise to Ink(0.871f, 1.062f),
            AppGlyph.arrowDownRightAndArrowUpLeft to Ink(0.978f, 0.973f),
            AppGlyph.arrowTriangleSwap to Ink(1.035f, 1.008f),
            AppGlyph.arrowUpLeftAndArrowDownRight to Ink(0.956f, 0.941f),
            AppGlyph.arrowUturnBackwardCircleFill to Ink(1.012f, 1.011f),
            AppGlyph.bolt to Ink(0.738f, 1.125f),
            AppGlyph.bubbleRight to Ink(1.117f, 1.042f),
            AppGlyph.calendar to Ink(1.018f, 0.922f),
            AppGlyph.camera to Ink(1.228f, 0.951f),
            AppGlyph.cameraFill to Ink(1.228f, 0.951f),
            AppGlyph.car to Ink(1.208f, 0.941f),
            AppGlyph.characterBubble to Ink(1.117f, 1.042f),
            AppGlyph.checkmark to Ink(0.886f, 0.853f),
            AppGlyph.checkmarkCircle to Ink(1.012f, 1.011f),
            AppGlyph.checkmarkCircleFill to Ink(1.012f, 1.011f),
            AppGlyph.chevronDown to Ink(0.887f, 0.511f),
            AppGlyph.chevronLeft to Ink(0.520f, 0.871f),
            AppGlyph.chevronRight to Ink(0.520f, 0.871f),
            AppGlyph.chevronUp to Ink(0.887f, 0.511f),
            AppGlyph.circle to Ink(1.012f, 1.011f),
            AppGlyph.clock to Ink(1.012f, 1.011f),
            AppGlyph.ellipsis to Ink(0.932f, 0.204f),
            AppGlyph.exclamationmarkTriangleFill to Ink(1.040f, 0.941f),
            AppGlyph.flag to Ink(0.890f, 0.973f),
            AppGlyph.flagCheckered to Ink(0.890f, 0.973f),
            AppGlyph.flagFill to Ink(0.890f, 0.973f),
            AppGlyph.fuelpump to Ink(1.079f, 1.111f),
            AppGlyph.gearshapeFill to Ink(1.042f, 1.042f),
            AppGlyph.globe to Ink(1.012f, 1.011f),
            AppGlyph.heart to Ink(1.006f, 0.922f),
            AppGlyph.houseFill to Ink(1.228f, 1.061f),
            AppGlyph.infoCircleFill to Ink(1.012f, 1.011f),
            AppGlyph.locationFill to Ink(0.963f, 0.963f),
            AppGlyph.lock to Ink(0.693f, 0.983f),
            AppGlyph.lockFill to Ink(0.693f, 0.983f),
            AppGlyph.magnifyingglass to Ink(0.979f, 0.981f),
            AppGlyph.map to Ink(1.048f, 0.983f),
            AppGlyph.mappinAndEllipse to Ink(1.032f, 1.106f),
            AppGlyph.minus to Ink(0.838f, 0.124f),
            AppGlyph.minusCircle to Ink(1.012f, 1.011f),
            AppGlyph.moonFill to Ink(0.985f, 0.988f),
            AppGlyph.mountain to Ink(1.774f, 0.881f),
            AppGlyph.pause to Ink(0.477f, 0.853f),
            AppGlyph.pencil to Ink(0.838f, 0.829f),
            AppGlyph.pencilLine to Ink(1.008f, 0.835f),
            AppGlyph.photo to Ink(1.167f, 0.922f),
            AppGlyph.play to Ink(0.787f, 0.863f),
            AppGlyph.plus to Ink(0.838f, 0.821f),
            AppGlyph.plusCircleFill to Ink(1.012f, 1.011f),
            AppGlyph.roadLanes to Ink(1.488f, 0.761f),
            AppGlyph.speedometer to Ink(1.012f, 1.011f),
            AppGlyph.squareAndArrowDown to Ink(0.906f, 1.060f),
            AppGlyph.squareAndArrowUp to Ink(0.906f, 1.134f),
            AppGlyph.squareAndPencil to Ink(0.990f, 0.974f),
            AppGlyph.suitcase to Ink(1.188f, 0.983f),
            AppGlyph.suitcaseFill to Ink(1.188f, 0.983f),
            AppGlyph.sunMaxFill to Ink(1.103f, 1.101f),
            AppGlyph.tablecells to Ink(1.167f, 0.922f),
            AppGlyph.trash to Ink(0.988f, 1.111f),
            AppGlyph.tray to Ink(1.177f, 0.861f),
            AppGlyph.trayFull to Ink(1.177f, 0.861f),
            AppGlyph.xmark to Ink(0.796f, 0.789f),
            AppGlyph.xmarkCircleFill to Ink(1.012f, 1.011f),
        )
    }
}
