package com.onezee.triptrack.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.CustomNavBar
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// iOS: CustomNavBar.swift, NavBackButton.swift.
/**
 * SH-01 and ST-01 of the 3 November audit. The numbers are the iOS bar's: the controls 20 from the
 * edge, 10 under the status bar (20 in a sheet), 12 above the content, the title centred with 72 of
 * clearance, 18 heavy or 28 heavy for the three collection screens.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class CustomNavBarTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterComponentActivity()).around(compose)

    private fun show(
        title: String = "Гараж",
        subtitle: String? = null,
        largeTitle: Boolean = false,
        inSheet: Boolean = false,
        onBack: (() -> Unit)? = {},
        withTrailing: Boolean = false,
    ) = compose.setContent {
        Themed(dark = false) {
            Box(Modifier.fillMaxWidth()) {
                CustomNavBar(title, onBack, Modifier.testTag("bar"), subtitle, largeTitle, inSheet) {
                    if (withTrailing) Box(Modifier.size(40.dp).testTag("trailing"))
                }
            }
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot()

    @Test fun theControlsSitTwentyFromTheEdgeAndTenBelowTheTop() {
        show(withTrailing = true)
        val back = bounds("nav_back")
        assertEquals(20.dp, back.left)
        assertEquals(10.dp, back.top)
        assertEquals(40.dp, back.width)
        assertEquals(360.dp - 20.dp, bounds("trailing").right)
    }

    @Test fun theBarIsSixtyTwoHighAndLeavesTwelveUnderTheControls() {
        show()
        assertEquals(10.dp + 40.dp + 12.dp, bounds("bar").height)
    }

    @Test fun aSheetPutsTheRowTenLowerClearOfTheGrabber() {
        show(inSheet = true)
        assertEquals(20.dp, bounds("nav_back").top)
        assertEquals(20.dp + 40.dp + 12.dp, bounds("bar").height)
    }

    @Test fun aBarInsideAHouseSheetIsMarkedAsOneByItself() {
        compose.setContent {
            Themed(dark = false) {
                ContentSizedSheet(onDismissRequest = {}) { CustomNavBar("Гараж", {}, Modifier.testTag("bar")) }
            }
        }
        compose.waitForIdle()
        assertEquals(72.dp, bounds("bar").height)
    }

    @Test fun theTitleIsCentredOverTheWholeWidthWhateverTheControlsAre() {
        show(withTrailing = true)
        val title = bounds("nav_title")
        assertEquals(180f, (title.left + title.right).value / 2f, 0.6f)
    }

    @Test fun aLargeTitleIsAboutAThirdTallerThanTheBarTitle() {
        compose.setContent {
            Themed(dark = false) {
                Column {
                    CustomNavBar("Статистика", {})
                    CustomNavBar("Статистика", {}, largeTitle = true)
                }
            }
        }
        val titles = compose.onAllNodesWithTag("nav_title")
        titles.assertCountEquals(2)
        val small = titles[0].getBoundsInRoot().height.value
        val large = titles[1].getBoundsInRoot().height.value
        // 28 against 18, on one line each.
        assertEquals(28f / 18f, large / small, 0.08f)
    }

    @Test fun aLongLargeTitleStaysOnOneLineInsideItsClearance() {
        show("Очень длинное название экрана статистики", largeTitle = true)
        val title = bounds("nav_title")
        assertTrue(title.left.value >= 20f + 72f - 0.5f, "left ${title.left}")
        assertTrue(title.right.value <= 360f - 20f - 72f + 0.5f, "right ${title.right}")
        // One line of at most 28 sp heavy, about 34 dp tall, not two.
        assertTrue(title.height.value < 40f, "height ${title.height}")
    }

    @Test fun aSubtitleSitsUnderTheTitleAndTheTitleIsAHeading() {
        show("Статистика", subtitle = "Александр", largeTitle = true)
        assertTrue(bounds("nav_subtitle").top >= bounds("nav_title").bottom)
        assertTrue(SemanticsProperties.Heading in compose.onNodeWithTag("nav_title").fetchSemanticsNode().config)
    }

    @Test fun theBackCircleGoesBack() {
        var back = 0
        show(onBack = { back++ })
        compose.onNodeWithTag("nav_back").performClick()
        assertEquals(1, back)
    }

    @Test fun noBackHidesTheCircle() {
        show(onBack = null)
        compose.onAllNodesWithTag("nav_back").assertCountEquals(0)
    }
}
