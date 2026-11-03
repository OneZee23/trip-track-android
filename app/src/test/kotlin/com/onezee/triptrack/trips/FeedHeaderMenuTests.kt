// iOS: FeedView (feedHeader, the card menu, the guest's sign-in prompt), SocialFeedCardView.menuItems, and the 3 November
// 2026 parity audit of the feed (FD-01 to FD-06).
package com.onezee.triptrack.trips

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.feed.SocialCardState
import com.onezee.triptrack.feature.feed.ui.PendingSocialAction
import com.onezee.triptrack.feature.feed.ui.PendingSocialActions
import com.onezee.triptrack.feature.social.SocialApi
import com.onezee.triptrack.feature.social.SocialFeature
import com.onezee.triptrack.model.SocialFeedResponse
import com.onezee.triptrack.model.SocialShareResponse
import com.onezee.triptrack.social.FakeSocialApi
import com.onezee.triptrack.social.MemoryCommentArchive
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FeedHeaderMenuTests : TripsScreenTest() {
    private val minted = mutableListOf<UUID>()
    private var services: SocialFeature? = null

    private class SharingApi(private val inner: FakeSocialApi, private val minted: MutableList<UUID>) : SocialApi by inner {
        override suspend fun shareTrip(tripId: UUID): SocialShareResponse {
            minted += tripId
            return SocialShareResponse("https://trip-track.app/s/abc123", "abc123")
        }
    }

    override fun socialServices(): SocialFeature = services ?: SocialFeature(
        SharingApi(FakeSocialApi(), minted), feature, null, { feature.accountId() },
        RuntimeEnvironment.getApplication().getSharedPreferences("social-tests", Context.MODE_PRIVATE), { "" }, Dispatchers.Main, { NOW },
        commentArchive = MemoryCommentArchive(),
    ).also { services = it }

    @After fun closeSocialServices() { services?.close() }

    private fun feedOf(signedIn: Boolean, door: (() -> Unit)? = null) {
        if (signedIn) signIn()
        val cards = socialCards()
        social.feedPage = { SocialFeedResponse(cards) }
        showFeed(signInDoor = door)
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top.value

    // ---- FD-01, FD-05: the corner of the header and the guest's page

    @Test fun aSignedInPersonHasTheBellAndItOpensTheInbox() {
        feedOf(signedIn = true)
        compose.onNodeWithTag("feed_bell").assertHasClickAction()
        compose.onNodeWithTag("feed_sign_in").assertDoesNotExist()
        val bell = compose.onNodeWithTag("feed_bell").getUnclippedBoundsInRoot()
        // iOS headerCircleButton: 34 × 34 on the card fill.
        assertEquals(34f, bell.width.value, 0.6f); assertEquals(34f, bell.height.value, 0.6f)
        compose.onNodeWithTag("feed_bell").performClick()
        compose.waitForIdle()
        assertEquals(TripsDestination.Inbox, feature.feedNavigator.top)
    }

    @Test fun aGuestHasSignInInTheCornerAndNoBell() {
        var opened = 0
        feedOf(signedIn = false, door = { opened++ })
        compose.onNodeWithTag("feed_bell").assertDoesNotExist()
        val button = compose.onNodeWithTag("feed_sign_in").getUnclippedBoundsInRoot()
        // iOS: Inter 14 bold, padding 16 / 9: 35 tall.
        assertEquals(35f, button.height.value, 1f)
        compose.onNodeWithTag("feed_sign_in").performClick()
        assertEquals(1, opened)
    }

    /** The account services of a test have no sign-in sheet behind them (`auth` is null) and the host brought no door: a press would do nothing. */
    @Test fun withNoWayToSignInTheGuestHasNoButton() {
        feedOf(signedIn = false)
        compose.onNodeWithTag("feed_sign_in").assertDoesNotExist()
    }

    @Test fun theFollowingPageOfAGuestHasTheSameButton() {
        var opened = 0
        feedOf(signedIn = false, door = { opened++ })
        compose.onNodeWithTag("feed_segment_following").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("feed_following_guest").assertExists()
        compose.onNodeWithTag("feed_following_sign_in").assertHasClickAction().performClick()
        assertEquals(1, opened)
    }

    // ---- FD-02: the «…» of every card

    @Test fun someoneElsesCardOffersShareAndReport() {
        feedOf(signedIn = true)
        compose.onAllNodesWithTag("feed_card_menu")[0].performClick()
        compose.onNodeWithTag("feed_card_action_share").assertExists()
        compose.onNodeWithTag("feed_card_action_report").assertExists()
        compose.onNodeWithTag("feed_card_action_edit").assertDoesNotExist()
        compose.onNodeWithTag("feed_card_action_delete").assertDoesNotExist()
        assertTrue(top("feed_card_action_share") < top("feed_card_action_report"))
    }

    @Test fun anOwnCardOffersEditShareMakePrivateAndDeleteInThatOrder() {
        feedOf(signedIn = true)
        compose.onAllNodesWithTag("feed_card_menu")[1].performClick()
        val order = listOf("feed_card_action_edit", "feed_card_action_share", "feed_card_action_private", "feed_card_action_delete")
        order.forEach { compose.onNodeWithTag(it).assertExists() }
        compose.onNodeWithTag("feed_card_action_report").assertDoesNotExist()
        order.zipWithNext().forEach { (a, b) -> assertTrue(top(a) < top(b), "$a stands above $b") }
    }

    @Test fun sharingACardOpensThePosterStudio() {
        feedOf(signedIn = true)
        compose.onAllNodesWithTag("feed_card_menu")[0].performClick()
        compose.onNodeWithTag("feed_card_action_share").performClick()
        compose.waitForIdle()
        assertEquals(1, minted.size)
        compose.onNodeWithTag("trip_poster_sheet").assertExists()
    }

    @Test fun reportingACardOpensTheReportSheet() {
        feedOf(signedIn = true)
        compose.onAllNodesWithTag("feed_card_menu")[0].performClick()
        compose.onNodeWithTag("feed_card_action_report").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("report_sheet").assertExists()
    }

    /** A guest needs an account to report; poster export stays available without one. */
    @Test fun guestCanExportPosterButReportNeedsSignIn() {
        var opened = 0
        feedOf(signedIn = false, door = { opened++ })
        compose.onAllNodesWithTag("feed_card_menu")[0].performClick()
        compose.onNodeWithTag("feed_card_action_report").performClick()
        compose.waitForIdle()
        assertEquals(1, opened)
        compose.onNodeWithTag("report_sheet").assertDoesNotExist()
        compose.onAllNodesWithTag("feed_card_menu")[0].performClick()
        compose.onNodeWithTag("feed_card_action_share").performClick()
        compose.waitForIdle()
        assertEquals(1, opened)
        assertTrue(minted.isEmpty())
        compose.onNodeWithTag("trip_poster_sheet").assertExists()
    }

    // ---- FD-06: a guest who reacts

    @Test fun aGuestWhoReactsIsAskedToSignInNotToasted() {
        var opened = 0
        feedOf(signedIn = false, door = { opened++ })
        compose.onAllNodesWithTag("feed_card_add_reaction")[0].performClick()
        compose.waitForIdle()
        assertEquals(1, opened)
    }

    // ---- the pending action a guest left behind (iOS pendingSocialAction / resumeAfterAuth)

    private fun card() = SocialCardState.from(socialCards().first())

    @Test fun whatAGuestTappedIsDoneOnceAfterARealSignIn() {
        val pending = PendingSocialActions()
        val picker = PendingSocialAction.Picker(card())
        pending.hold(picker)
        pending.authenticated()
        assertSame(picker, pending.release(signedIn = true))
        assertNull(pending.release(signedIn = true), "one-shot")
    }

    @Test fun aSheetClosedWithoutSigningInDropsIt() {
        val pending = PendingSocialActions()
        pending.hold(PendingSocialAction.Picker(card()))
        assertNull(pending.release(signedIn = false))
        // And it does not wait for the next sign-in either.
        pending.authenticated()
        assertNull(pending.release(signedIn = true))
    }

    @Test fun aSessionThatIsGoneAgainIsNeverReplayedFor() {
        val pending = PendingSocialActions()
        pending.hold(PendingSocialAction.Picker(card()))
        pending.authenticated()
        assertNull(pending.release(signedIn = false))
    }

    @Test fun aNewPromptForgetsTheSuccessOfAnEarlierOne() {
        val pending = PendingSocialActions()
        pending.hold(PendingSocialAction.Picker(card()))
        pending.authenticated()
        // The login outlived its sheet and succeeded late; the next prompt must not inherit it.
        pending.hold(PendingSocialAction.Picker(card()))
        assertNull(pending.release(signedIn = true))
    }
    // ---- FD-03: the PRO frame around the author's avatar

    /** What the screen shows a pixel in from the left edge of the avatar, in the middle row: the ring runs there. */
    private fun ring(index: Int): Int {
        val image = compose.onAllNodesWithTag("feed_card_avatar")[index].captureToImage().asAndroidBitmap()
        return image.getPixel(2, image.height / 2)
    }

    private fun warmth(pixel: Int) = android.graphics.Color.red(pixel) - android.graphics.Color.blue(pixel)

    @Test fun aFramedAuthorsAvatarCarriesTheRingAndAnUnknownFrameNone() {
        signIn()
        val cards = socialCards().toMutableList()
        cards[0] = cards[0].copy(author = cards[0].author.copy(avatarFrame = "frame_gold"))
        // The audit's fixture wrote the short «gold»: no build knows it, and iOS draws nothing for it.
        cards[1] = cards[1].copy(author = cards[1].author.copy(avatarFrame = "gold"))
        social.feedPage = { SocialFeedResponse(cards) }
        showFeed()
        assertTrue(warmth(ring(0)) > 60, "gold ring expected, got #%08x".format(ring(0)))
        assertTrue(warmth(ring(1)) < 25, "no ring expected, got #%08x".format(ring(1)))
    }

}
