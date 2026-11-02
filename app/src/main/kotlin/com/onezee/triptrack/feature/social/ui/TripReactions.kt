// iOS: TripDetailView.swift (reactionsArea, reactionsCard, reactionRow, emptyReactionsLine,
// firstReactionButton, reactionTallies, tallies, totalReactions).
package com.onezee.triptrack.feature.social.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.trip.ReactorState
import com.onezee.triptrack.feature.trip.TripDetailState
import com.onezee.triptrack.model.ReactionEmoji
import com.onezee.triptrack.model.ReactionTally
import com.onezee.triptrack.ui.DetailSectionHeader
import com.onezee.triptrack.ui.reactions.ReactionChipStyle
import com.onezee.triptrack.ui.reactions.ReactionCountChip
import com.onezee.triptrack.ui.reactions.ReactionIcon

/**
 * iOS: TripDetailView.reactionTallies. The rows to draw: the server's list once it lands, until then
 * what the feed already knew (a stranger's trip with 23 reactions must not open claiming nobody
 * reacted). Legacy keys fold into the drawn icon that replaced them. Most first; equal counts order
 * by the emoji itself, so a refresh never shuffles a level row under the finger that pulled.
 */
fun reactionTallies(reactions: List<ReactorState>, breakdown: List<ReactionTally>): List<ReactionTally> {
    val counts: Map<String, Int> = if (reactions.isNotEmpty()) {
        reactions.groupingBy { ReactionEmoji.canonical(it.emoji) }.eachCount()
    } else {
        breakdown.groupBy { ReactionEmoji.canonical(it.emoji) }.mapValues { (_, tallies) -> tallies.sumOf { it.count } }
    }
    return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { ReactionTally(it.key, it.value) }
}

/**
 * iOS: reactionsArea. The «Реакции · N» card when there are reactions; on someone else's private trip
 * nothing (none of our business); otherwise the section keeps its heading and its card, and the card
 * says the quiet part — with the way to be first on someone else's trip. The chip row is a control on
 * someone else's trip (tap leaves, moves or takes back our reaction) and a read-out on ours; on ours
 * the people who reacted follow, each a way into their profile. A hold on any chip peeks at who
 * reacted ([onPeek]).
 */
@Composable
fun TripReactionsSection(
    state: TripDetailState,
    onReact: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onPeek: (String) -> Unit,
    onOpenProfile: (ReactorState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trip = state.trip ?: return
    val strings = appStrings
    val tallies = reactionTallies(state.reactions, state.reactionBreakdown)
    when {
        tallies.isNotEmpty() -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val total = if (state.reactions.isEmpty()) state.reactionCount else state.reactions.size
            DetailSectionHeader(strings.reactionsTitleN(total))
            ReactionsCard(state, tallies, onReact, onOpenPicker, onPeek, onOpenProfile)
        }
        trip.isPrivate && !state.isOwn -> Unit
        else -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailSectionHeader(strings.chipReactions())
            // The line and the button side by side when both fit whole (iOS ViewThatFits); otherwise
            // the button moves under the line instead of being cut to «Поставить…».
            FitsSideBySide(
                Modifier.fillMaxWidth().surfaceCard(14.dp).padding(horizontal = 14.dp, vertical = 12.dp).testTag("reactions_empty_card"),
                line = { EmptyReactionsLine() },
                action = { if (!state.isOwn) FirstReactionButton(onOpenPicker) },
            )
        }
    }
}

@Composable
private fun ReactionsCard(
    state: TripDetailState,
    tallies: List<ReactionTally>,
    onReact: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onPeek: (String) -> Unit,
    onOpenProfile: (ReactorState) -> Unit,
) {
    val colors = AppTheme.colors
    val strings = appStrings
    val haptics = rememberHaptics()
    val mine = state.myReaction?.let(ReactionEmoji::canonical)
    Column(Modifier.fillMaxWidth().surfaceCard(16.dp).testTag("reactions_card")) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (tally in tallies) {
                val peek = { haptics.action(); onPeek(tally.emoji) }
                if (state.isOwn) {
                    ReactionCountChip(tally.emoji, tally.count, style = ReactionChipStyle.breakdown, onLongPress = peek)
                } else {
                    ReactionCountChip(
                        tally.emoji, tally.count,
                        style = if (mine == tally.emoji) ReactionChipStyle.mine else ReactionChipStyle.unselected,
                        onTap = { onReact(if (mine == tally.emoji) state.myReaction ?: tally.emoji else tally.emoji) },
                        onLongPress = peek,
                    )
                }
            }
            // «+»: the way in for a reaction that is not on the card yet (canon 467:259).
            if (!state.isOwn) {
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .plainPressFeedback(interaction)
                        .size(width = 34.dp, height = 28.dp)
                        .background(colors.cardAlt, CapsuleShape)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); onOpenPicker() }
                        .semantics { contentDescription = strings.addReaction() }
                        .testTag("reaction_add"),
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(AppGlyph.plus, null, tint = colors.textSecondary, size = 15.dp)
                }
            }
        }
        // Who reacted is the owner's view of their own trip; on someone else's the card is the chips.
        if (state.isOwn) {
            state.reactions.forEachIndexed { index, entry ->
                Box(Modifier.padding(start = if (index == 0) 0.dp else 14.dp).fillMaxWidth().height(1.dp).background(colors.border))
                ReactorRow(entry) { haptics.tap(); onOpenProfile(entry) }
            }
        }
    }
}

/** iOS reactionRow: avatar, name, «LVL N», the icon this person left, a chevron. */
@Composable
private fun ReactorRow(entry: ReactorState, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val strings = appStrings
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .plainPressFeedback(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .testTag("reaction_row"),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(colors.cardAlt, CircleShape), contentAlignment = Alignment.Center) {
            Text(entry.author.avatarEmoji ?: "🚗", style = inter(18f))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.author.displayName ?: strings.blockedListUser(), style = inter(14f, InterWeight.semibold), color = colors.text, maxLines = 1)
            Text("LVL ${entry.author.profileLevel}", style = inter(11f, InterWeight.medium, monospacedDigits = true), color = colors.textTertiary)
        }
        ReactionIcon(entry.emoji, size = 18.dp)
        Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = 12.dp)
    }
}

/** iOS emptyReactionsLine: the heart on its disc and «Пока никто не отреагировал». */
@Composable
private fun EmptyReactionsLine() {
    val colors = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(colors.cardAlt, CircleShape), contentAlignment = Alignment.Center) {
            Glyph(AppGlyph.heart, null, tint = colors.textTertiary, size = 17.dp)
        }
        Text(appStrings.noReactionsYet(), style = inter(14.5f), color = colors.textSecondary, maxLines = 2)
    }
}

/** iOS firstReactionButton: somebody has to be first, and on someone else's trip that is the point. */
@Composable
private fun FirstReactionButton(onOpenPicker: () -> Unit) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .plainPressFeedback(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); onOpenPicker() }
            .testTag("reaction_add_first"),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.background(AppTheme.accent.copy(alpha = 0.12f), CapsuleShape).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(AppGlyph.plus, null, tint = AppTheme.accent, size = 14.dp)
            Text(appStrings.addReaction(), style = inter(13f, InterWeight.semibold), color = AppTheme.accent, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * iOS `ViewThatFits(in: .horizontal)` over `HStack(spacing: 12) { line; Spacer(minLength: 8); action }`
 * and `VStack(alignment: .leading, spacing: 10) { line; action }`: side by side when the line's one-line
 * width, the 32 pt between them and the action all fit; stacked otherwise.
 */
@Composable
private fun FitsSideBySide(modifier: Modifier, line: @Composable () -> Unit, action: @Composable () -> Unit) {
    Layout(contents = listOf(line, action), modifier = modifier) { (lines, actions), constraints ->
        val gap = 32.dp.roundToPx()
        val stackGap = 10.dp.roundToPx()
        val width = constraints.maxWidth
        val button = actions.firstOrNull()?.measure(Constraints())
        val ideal = lines.first().maxIntrinsicWidth(Constraints.Infinity)
        if (button == null || ideal + gap + button.width <= width) {
            val room = if (button == null) width else (width - gap - button.width).coerceAtLeast(0)
            val text = lines.first().measure(Constraints(maxWidth = room))
            val height = maxOf(text.height, button?.height ?: 0)
            layout(width, height) {
                text.place(0, (height - text.height) / 2)
                if (button != null) button.place(width - button.width, (height - button.height) / 2)
            }
        } else {
            val text = lines.first().measure(Constraints(maxWidth = width))
            layout(width, text.height + stackGap + button.height) {
                text.place(0, 0)
                button.place(0, text.height + stackGap)
            }
        }
    }
}

/** For the hosts that open a profile from a reactor row. */
fun ReactorState.socialAuthor(): com.onezee.triptrack.model.SocialAuthor =
    com.onezee.triptrack.model.SocialAuthor(author.id, author.displayName, author.avatarEmoji, author.profileLevel, author.isPlus, author.avatarFrame)
