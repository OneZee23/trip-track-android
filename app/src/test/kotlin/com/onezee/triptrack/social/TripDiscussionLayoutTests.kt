// iOS: TripCommentsSection (isPreview, commentRow, relativeAge) and the 3 November 2026 parity audit (TD-09).
package com.onezee.triptrack.social

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.social.ui.relativeAge
import com.onezee.triptrack.model.SocialTripResponse
import com.onezee.triptrack.model.TripComment
import com.onezee.triptrack.model.TripCommentsResponse
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TripDiscussionLayoutTests : SocialScreenTest() {
    private val ru get() = AppStrings(RuntimeEnvironment.getApplication(), AppLanguage.ru)
    private val now = SocialFixtures.now

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun Dp.near(expected: Float, tolerance: Float = 0.8f) = assertEquals(expected, value, tolerance)

    private fun comment(n: Int, text: String, age: Long) =
        TripComment(UUID.fromString("77777777-7777-4777-8777-77777777777$n"), SocialFixtures.author, text, now.minusSeconds(age), false)

    private fun discussion(comments: List<TripComment>, count: Int = comments.size, onServer: Boolean = true) {
        val trip = SocialFixtures.trip(0).copy(commentCountRaw = count)
        social.remoteTrip = { SocialTripResponse(trip) }
        social.commentsPage = { TripCommentsResponse(comments) }
        showDestination(TripsDestination.TripDetail(trip.id, social = trip))
        if (onServer) compose.onNodeWithTag("trip_discussion").performScrollTo()
    }

    // ---- the age, as a compact word

    @Test fun theAgeIsACompactWordAndDaysCapTheScale() {
        val s = ru
        assertEquals(s.relTimeNow(), relativeAge(now.minusSeconds(30), now, s))
        assertEquals(s.relTimeMinutes(5), relativeAge(now.minusSeconds(5 * 60 + 20), now, s))
        assertEquals(s.relTimeHours(2), relativeAge(now.minusSeconds(2 * 3600 + 100), now, s))
        assertEquals(s.relTimeDays(3), relativeAge(now.minusSeconds(3 * 86_400L + 5), now, s))
        assertEquals(s.relTimeDays(40), relativeAge(now.minusSeconds(40 * 86_400L), now, s), "no weeks, no months")
        // A comment from a phone whose clock runs ahead is «сейчас», not a negative age.
        assertEquals(s.relTimeNow(), relativeAge(now.plusSeconds(90), now, s))
    }

    // ---- the teaser

    @Test fun theTeaserShowsTheTwoFreshestOldestFirstInOneCard() {
        discussion(listOf(comment(1, "Самый новый", 60), comment(2, "Постарше", 7200), comment(3, "Самый старый", 90_000)))
        compose.onAllNodesWithTag("comment_row").assertCountEquals(2)
        val rows = compose.onAllNodesWithTag("comment_row")
        // Read in the order it happened: the older of the two above the newer.
        assertTrue(rows[0].getUnclippedBoundsInRoot().top < rows[1].getUnclippedBoundsInRoot().top)
        // iOS commentRow: a 34 avatar 14 in from the card, the text column 10 after it.
        val card = bounds("trip_discussion")
        val avatar = compose.onAllNodesWithTag("comment_author")[0].getUnclippedBoundsInRoot()
        avatar.width.near(34f); avatar.height.near(34f)
        (avatar.left - card.left).near(14f)
        // The rows carry their own 14 / 12 padding: the card has none.
        (avatar.top - rows[0].getUnclippedBoundsInRoot().top).near(12f)
    }

    @Test fun theCardsRowsAreSeparatedByHairlinesAndTheWriteRowCloseIt() {
        discussion(listOf(comment(1, "Первый", 60), comment(2, "Второй", 7200)))
        val rows = compose.onAllNodesWithTag("comment_row")
        val first = rows[0].getUnclippedBoundsInRoot()
        val second = rows[1].getUnclippedBoundsInRoot()
        // A 1-point rule between rows: the next row starts 1 below the last.
        (second.top - first.bottom).near(1f, 0.6f)
        val cta = bounds("discussion_cta_row")
        // And one after the last row, before the write row (12 of its own margin around the pill).
        (cta.top - second.bottom).near(1f + 12f, 1f)
    }

    @Test fun theWriteRowIsAPillWithTheViewersDiscAndOpensTheThread() {
        discussion(listOf(comment(1, "Первый", 60)))
        compose.onNodeWithTag("comments_sheet").assertDoesNotExist()
        compose.onNodeWithTag("discussion_cta_row").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("comments_sheet").assertExists()
    }

    @Test fun theSeeAllPillIsThirtyTwoTallAndOpensTheThread() {
        discussion(listOf(comment(1, "Первый", 60)), count = 5)
        bounds("discussion_see_all").height.near(32f)
        compose.onNodeWithTag("discussion_see_all").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("comments_sheet").assertExists()
    }

    @Test fun anEmptyDiscussionHasTheWriteRowAndNoPill() {
        discussion(emptyList(), count = 0)
        compose.onNodeWithTag("discussion_cta_row").assertExists()
        compose.onNodeWithTag("discussion_see_all").assertDoesNotExist()
        compose.onAllNodesWithTag("comment_row").assertCountEquals(0)
    }

    @Test fun theHeadingIsTheSeventeenBoldOneTenAboveTheCard() {
        discussion(listOf(comment(1, "Первый", 60)))
        // iOS: DetailSectionHeader (17 bold) and 10 to the card: the section is the heading plus the card plus the pill.
        val section = bounds("trip_discussion")
        val first = compose.onAllNodesWithTag("comment_row")[0].getUnclippedBoundsInRoot()
        // The heading line of Inter 17 is about 21 tall.
        (first.top - section.top).near(21f + 10f, 2f)
    }
}
