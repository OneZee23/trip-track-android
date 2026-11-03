package com.onezee.triptrack.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.theme.AppRadius
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// iOS: ContentSizedSheet.swift, the system sheet presentation; AtlasControlsSheet.swift sets 28.
/**
 * SH-04 and SH-05 of the 3 November audit. The iPhone's system sheet dims the page by black at 0.12
 * in light and 0.29 in dark (the DRIVE2 sheet takes 248 to 218) and rounds its top corners to about
 * 10 on a continuous curve; every house sheet used the Atlas scrim (248 to 158) and a 28 circle.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class SheetLookTests(private val dark: Boolean) {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterComponentActivity()).around(compose)

    private fun show(radius: Dp = AppRadius.sheet, grabber: Boolean = false) {
        compose.setContent {
            Themed(dark) {
                ContentSizedSheet(onDismissRequest = {}, cornerRadius = radius, grabber = grabber) {
                    Box(Modifier.fillMaxWidth().height(120.dp))
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun thePageUnderASheetDimsLikeASystemSheet() {
        show()
        val scrim = compose.onNodeWithTag("sheet_scrim").captureToImage().asAndroidBitmap()
        val alpha = android.graphics.Color.alpha(scrim.getPixel(10, 10)) / 255f
        assertEquals(if (dark) 0.29f else 0.12f, alpha, 0.01f)
    }

    @Test fun theTopCornersAreTheSystemSheetsAndTheBottomOnesSquare() {
        show()
        val sheet = compose.onNodeWithTag("sheet").captureToImage().asAndroidBitmap()
        val density = sheet.width / 360f
        // A cut corner shows the scrim behind the sheet, which is far from opaque.
        assertTrue(android.graphics.Color.alpha(sheet.getPixel(1, 1)) < 128, "the corner is cut")
        assertEquals(255, android.graphics.Color.alpha(sheet.getPixel(1, sheet.height - 2)), "the bottom corner is square")
        // The continuous curve of a 10 dp corner meets the top edge 15 dp in; one pixel below the
        // edge it is already a few dp in from the side. A 28 circle would still be 22 dp in.
        val first = (0 until sheet.width).first { android.graphics.Color.alpha(sheet.getPixel(it, 1)) > 128 } / density
        assertTrue(first in 3f..12f, "the sheet starts $first dp in on its top row")
        val down = (0 until sheet.height).first { android.graphics.Color.alpha(sheet.getPixel(1, it)) > 128 } / density
        assertTrue(down in 4f..12f, "the left edge starts $down dp down")
    }

    /** SE-02: a sheet that iOS gives `presentationDragIndicator(.visible)` shows the 34 × 5 handle, 8 from the edge, at 18 % ink. */
    @Test fun aSheetCanShowTheSystemGrabber() {
        show(grabber = true)
        val sheet = compose.onNodeWithTag("sheet").captureToImage().asAndroidBitmap()
        val density = sheet.width / 360f
        val middle = sheet.width / 2
        fun red(x: Int, y: Int) = android.graphics.Color.red(sheet.getPixel(x, y))
        val ground = red(middle, (25 * density).toInt())
        val rows = (0 until (22 * density).toInt()).filter { kotlin.math.abs(red(middle, it) - ground) > 6 }
        assertTrue(rows.isNotEmpty(), "the grabber is drawn")
        assertEquals(8f, rows.first() / density, 0.6f, "it starts 8 dp below the edge")
        assertEquals(5f, (rows.last() - rows.first() + 1) / density, 0.6f, "it is 5 dp thick")
        val y = rows[rows.size / 2]
        val cols = (0 until sheet.width).filter { kotlin.math.abs(red(it, y) - ground) > 6 }
        assertEquals(34f, (cols.last() - cols.first() + 1) / density, 1f, "it is 34 dp wide")
        assertEquals(sheet.width / 2f, (cols.first() + cols.last()) / 2f, 2f, "it is centred")
        // 18 % of the ink over the ground: (209, 209, 209) over white in light, the ground lifted by 18 % of white in dark.
        val want = if (dark) ground + ((255 - ground) * 0.18f).toInt() else (ground * 0.82f).toInt()
        assertTrue(kotlin.math.abs(want - red(middle, y)) <= 4, "the ink is the iPhone's: wanted $want, got ${red(middle, y)}")
    }

    @Test fun aSheetWithoutTheGrabberDrawsNone() {
        show()
        val sheet = compose.onNodeWithTag("sheet").captureToImage().asAndroidBitmap()
        val density = sheet.width / 360f
        val ground = android.graphics.Color.red(sheet.getPixel(sheet.width / 2, (25 * density).toInt()))
        assertTrue((0 until (22 * density).toInt()).all { android.graphics.Color.red(sheet.getPixel(sheet.width / 2, it)) == ground || it < 2 * density }, "no handle")
    }

    @Test fun aScreenCanAskForTheAtlasRadius() {
        show(28.dp)
        val sheet = compose.onNodeWithTag("sheet").captureToImage().asAndroidBitmap()
        val density = sheet.width / 360f
        val first = (0 until sheet.width).first { android.graphics.Color.alpha(sheet.getPixel(it, 1)) > 128 } / density
        assertTrue(first > 14f, "the Atlas sheet starts $first dp in")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}
