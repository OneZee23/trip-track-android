// iOS: ReactionIconView.swift, ReactionTallyPill.swift, ReactionPickerOverlay.swift,
// ReactionBurstSprite.swift, ReactionsListSheet.swift, TripDetailView.swift (reactionsArea),
// TripDetailSections.swift (ReactionCountChip).
package com.onezee.triptrack.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.feature.feed.AuthorState
import com.onezee.triptrack.feature.social.ui.TripReactionsSection
import com.onezee.triptrack.feature.trip.ReactorState
import com.onezee.triptrack.feature.trip.TripDetailState
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.model.ReactionEmoji
import com.onezee.triptrack.model.ReactionTally
import com.onezee.triptrack.model.SocialAuthor
import com.onezee.triptrack.model.SocialFeedResponse
import com.onezee.triptrack.model.SocialReactionEntry
import com.onezee.triptrack.ui.reactions.ReactionBurstSprite
import com.onezee.triptrack.ui.reactions.ReactionChipStyle
import com.onezee.triptrack.ui.reactions.ReactionCountChip
import com.onezee.triptrack.ui.reactions.ReactionIcon
import com.onezee.triptrack.ui.reactions.ReactionTallyPill
import java.time.Duration
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * The owner's reaction artwork where the iPhone app draws it, light and dark: the six icons in both
 * states, the pills and the chips, the picker over the feed, the list of who reacted, and the trip
 * screen's «Реакции» card in its four states.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class ReactionScreenshotTests(private val dark: Boolean) : TripsScreenTest() {
    private val theme = if (dark) "dark" else "light"

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onNodeWithTag("specimen").captureRoboImage("src/test/screenshots/reactions_${name}_$theme.png")
    }

    private fun screen(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/reactions_${name}_$theme.png")
    }

    private fun specimen(content: @Composable () -> Unit) = show(dark) {
        Box(Modifier.testTag("specimen").padding(16.dp)) { content() }
    }

    @Test fun icons() {
        specimen {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (filled in listOf(false, true)) Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    ReactionEmoji.all.forEach { ReactionIcon(it, size = 24.dp, filled = filled) }
                }
                // The legacy keys draw the canon art; a key no build knows stays an emoji.
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    (ReactionEmoji.legacyToCanonical.keys + "🦄").forEach { ReactionIcon(it, size = 24.dp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ReactionEmoji.all.forEach { ReactionIcon(it, size = 14.dp) }
                    ReactionEmoji.all.forEach { ReactionIcon(it, size = 14.dp, filled = true) }
                }
            }
        }
        shot("icons")
    }

    @Test fun pillsAndChips() {
        specimen {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReactionTallyPill("🔥", 4, isMine = true) {}
                    ReactionTallyPill("🌅", 2, isMine = false) {}
                    ReactionTallyPill("❤️", 12, isMine = false) {}
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReactionCountChip("🤯", 3, style = ReactionChipStyle.breakdown)
                    ReactionCountChip("🏁", 1, style = ReactionChipStyle.unselected)
                    ReactionCountChip("🛣️", 7, style = ReactionChipStyle.mine)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
                    for (phase in listOf(0f, 0.35f, 0.7f)) Box(Modifier.size(64.dp), contentAlignment = Alignment.BottomCenter) {
                        ReactionBurstSprite("🔥", initialPhase = phase)
                    }
                }
            }
        }
        shot("pills")
    }

    @Test fun pickerOverTheFeed() {
        signIn()
        social.feedPage = { SocialFeedResponse(socialCards()) }
        showFeed(dark)
        compose.onAllNodesWithTag("feed_card_add_reaction")[0].performClick()
        compose.onNodeWithTag("reaction_picker").assertExists()
        screen("picker")
    }

    @Test fun whoReacted() {
        signIn()
        val cards = socialCards()
        social.feedPage = { SocialFeedResponse(cards) }
        social.reactionEntries = { reactors() }
        showFeed(dark)
        compose.onAllNodesWithTag("reaction_pill")[0].performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("reactions_list_sheet").assertExists()
        screen("list")
    }

    @Test fun tripCard() {
        val trip = TripSummary.from(trips().first { it.title == "Краснодар → Геленджик" }).copy(isPrivate = false, isOnServer = true)
        val reactors = reactors().map { ReactorState(AuthorState.from(it.user), it.emoji, it.createdAt) }
        specimen {
            Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Label("Чужая поездка")
                TripReactionsSection(TripDetailState(trip = trip, isOwn = false, myReaction = "❤️",
                    reactionBreakdown = listOf(ReactionTally("🔥", 4), ReactionTally("❤️", 2), ReactionTally("👍", 1), ReactionTally("🌅", 2)), reactionCount = 9),
                    onReact = {}, onOpenPicker = {}, onPeek = {}, onOpenProfile = {})
                Label("Своя поездка")
                TripReactionsSection(TripDetailState(trip = trip, isOwn = true, reactions = reactors, reactionCount = reactors.size),
                    onReact = {}, onOpenPicker = {}, onPeek = {}, onOpenProfile = {})
            }
        }
        shot("trip_card")
    }

    @Test fun tripCardEmpty() {
        val trip = TripSummary.from(trips().first { it.title == "Краснодар → Геленджик" }).copy(isPrivate = false, isOnServer = true)
        specimen {
            Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                TripReactionsSection(TripDetailState(trip = trip, isOwn = false), onReact = {}, onOpenPicker = {}, onPeek = {}, onOpenProfile = {})
                TripReactionsSection(TripDetailState(trip = trip, isOwn = true), onReact = {}, onOpenPicker = {}, onPeek = {}, onOpenProfile = {})
            }
        }
        shot("trip_empty")
    }

    @Composable
    private fun Label(text: String) = Text(text, style = inter(12f), color = AppTheme.colors.textTertiary)

    /** Four people on Аня's trip; one of them left the legacy ❤️, which draws as 👍. */
    private fun reactors(): List<SocialReactionEntry> = listOf(
        SocialReactionEntry(SocialAuthor(UUID.fromString("0b6f6a52-6666-4c8e-9a3e-5d2b7c9e0a01"), "Михаил", "🚐", 21), "🔥", NOW.minus(Duration.ofMinutes(12))),
        SocialReactionEntry(SocialAuthor(UUID.fromString("0b6f6a52-7777-4c8e-9a3e-5d2b7c9e0a01"), "Лена", "🚗", 9), "❤️", NOW.minus(Duration.ofHours(2))),
        SocialReactionEntry(SocialAuthor(UUID.fromString("0b6f6a52-8888-4c8e-9a3e-5d2b7c9e0a01"), "Дима", "🏍️", 3), "🔥", NOW.minus(Duration.ofDays(1))),
        SocialReactionEntry(SocialAuthor(UUID.fromString("0b6f6a52-9999-4c8e-9a3e-5d2b7c9e0a01"), "Ира", "🚙", 14), "🌅", NOW.minus(Duration.ofDays(3))),
    )

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
