package com.onezee.triptrack.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// iOS: AppTheme.swift (PressableCardStyle), `.buttonStyle(.plain)`.
/**
 * SH-06 of the 3 November audit: a `clickable` that names no indication used to draw the foundation's
 * grey rectangle over the whole node. The theme now provides the house indication: the content dims
 * under the finger, like an iOS plain button, and nothing else is drawn.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class HouseIndicationTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterComponentActivity()).around(compose)

    private fun show() = compose.setContent {
        Themed(dark = false) {
            // The red is the content of the clickable, so it is what dims; the white page stays.
            Box(Modifier.background(Color.White).padding(10.dp)) {
                Box(Modifier.testTag("target").size(60.dp).clickable { }) {
                    Box(Modifier.fillMaxSize().background(Color.Red))
                }
            }
        }
    }

    /** What the screen shows at the centre of the control, the page behind it included. */
    private fun centre(): Int {
        val image = compose.onNodeWithTag("target").captureToImage().asAndroidBitmap()
        return image.getPixel(image.width / 2, image.height / 2)
    }

    @Test fun aPressDimsTheContentAndLeavesNoGreyRectangle() {
        show()
        val rest = centre()
        assertEquals(255, android.graphics.Color.red(rest))
        assertEquals(0, android.graphics.Color.green(rest))

        compose.onNodeWithTag("target").performTouchInput { down(center) }
        compose.waitForIdle()
        val pressed = centre()
        // Red at 0.55 over white: the red stays full and green and blue come up. The foundation's
        // default would have darkened the red channel to 178 and left green at 0.
        assertEquals(255, android.graphics.Color.red(pressed), "the content is not darkened")
        val green = android.graphics.Color.green(pressed)
        assertTrue(green in 100..130, "the content dims towards the page: green is $green")

        compose.onNodeWithTag("target").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(0, android.graphics.Color.green(centre()), "the release brings the content back")
    }
}
