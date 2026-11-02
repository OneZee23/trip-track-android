// iOS: ReactionsListSheet.swift
package com.onezee.triptrack.feature.social.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.NavCircleButton
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.points
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.model.ReactionEmoji
import com.onezee.triptrack.model.ReactionTally
import com.onezee.triptrack.model.SocialAuthor
import com.onezee.triptrack.model.SocialReactionEntry
import com.onezee.triptrack.ui.LocalClock
import com.onezee.triptrack.ui.PullToRefresh
import com.onezee.triptrack.ui.RelativeTripDate
import com.onezee.triptrack.ui.reactions.ReactionIcon
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * iOS: ReactionsListSheet.swift. «Кто отреагировал», Telegram's reaction list, opened by holding a
 * reaction pill. Chips across the top switch between all reactions and one emoji; below, the people,
 * newest first, each a way into their profile. It reads `/social/reactions` itself ([load]): the card
 * only knows the tallies, and the trip screen's copy can be stale by the time of the press. Legacy
 * keys fold into their canonical chips like the tallies do, while each row still names the reaction
 * that person left, drawn as the canon art they see everywhere else.
 *
 * A row asks the host to open that profile once the sheet has closed ([onSelectUser]): the list never
 * owns navigation. Half the screen at least and taller with a long list, as iOS `.medium/.large`.
 */
@Composable
fun ReactionsListSheet(
    tripId: UUID,
    initialEmoji: String?,
    load: suspend (UUID) -> List<SocialReactionEntry>,
    ownId: UUID?,
    showsOwnBadge: Boolean,
    onSelectUser: (SocialAuthor) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val colors = AppTheme.colors
    val half = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } / 2
    var picked by remember { mutableStateOf<SocialAuthor?>(null) }
    ContentSizedSheet(
        onDismissRequest = { onDismissRequest(); picked?.let(onSelectUser) },
        background = colors.bg,
        minimumHeight = half,
    ) {
        val sheet = this
        ReactionsListContent(tripId, initialEmoji, load, ownId, showsOwnBadge,
            onClose = { sheet.dismiss() },
            onSelectUser = { author -> picked = author; sheet.dismiss() })
    }
}

@Composable
fun ReactionsListContent(
    tripId: UUID,
    initialEmoji: String?,
    load: suspend (UUID) -> List<SocialReactionEntry>,
    ownId: UUID?,
    showsOwnBadge: Boolean,
    onClose: () -> Unit,
    onSelectUser: (SocialAuthor) -> Unit,
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<SocialReactionEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(initialEmoji?.let(ReactionEmoji::canonical)) }
    suspend fun reload() {
        loadFailed = false
        try { entries = load(tripId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadFailed = true }
        finally { isLoading = false }
    }
    LaunchedEffect(tripId) { reload() }
    // Canonical emoji + count, most-used first: the order the card's pills use.
    val tallies = ReactionEmoji.mergedTallies(entries.map { ReactionTally(it.emoji, 1) })
    val visible = entries.sortedByDescending { it.createdAt }.filter { selected == null || ReactionEmoji.canonical(it.emoji) == selected }
    Column(Modifier.fillMaxWidth().testTag("reactions_list_sheet")) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.chipReactions(), style = inter(22f, InterWeight.heavy), letterSpacing = points(-0.22f), color = colors.text, maxLines = 1)
            if (entries.isNotEmpty()) Text("${entries.size}", style = inter(13f, InterWeight.bold, monospacedDigits = true), color = colors.textTertiary)
            Spacer(Modifier.weight(1f).width(8.dp))
            NavCircleButton(AppGlyph.xmark, strings.closeSheet(), { haptics.tap(); onClose() }, Modifier.testTag("reactions_list_close"))
        }
        if (tallies.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 3.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ListChip(entries.size, selected == null, "reactions_chip_all", onClick = { haptics.tap(); selected = null }) {
                    Text(strings.all(), style = inter(13f, InterWeight.bold), color = colors.text, maxLines = 1)
                }
                tallies.forEach { tally ->
                    val chosen = selected == tally.emoji
                    ListChip(tally.count, chosen, "reactions_chip", onClick = { haptics.tap(); selected = tally.emoji }) {
                        ReactionIcon(tally.emoji, size = 15.dp, filled = chosen, tint = if (chosen) AppTheme.accent else colors.text)
                    }
                }
            }
        }
        when {
            isLoading -> SkeletonRows(5, Modifier.padding(horizontal = 20.dp).padding(top = 30.dp))
            loadFailed -> ListMessage(strings.reactionsListCouldnTLoad(), strings.reactionsListCheckYourConnection()) {
                SocialGlyphIcon(SocialGlyph.wifiExclamation, null, tint = colors.textTertiary, size = 30.dp)
            }
            visible.isEmpty() -> ListMessage(strings.noReactionsYet(), strings.reactionsListBeTheFirst()) {
                // iOS draws the SF thumbs-up here; the owner's own «Класс» drawing says the same.
                ReactionIcon("👍", size = 30.dp, tint = colors.textTertiary)
            }
            else -> PullToRefresh(refreshing = refreshing, onRefresh = { scope.launch { refreshing = true; try { reload() } finally { refreshing = false } } }) {
                LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(visible, key = { _, entry -> entry.user.id.toString() + entry.createdAt }) { index, entry ->
                        Column(Modifier.animateItem()) {
                            ReactorRow(entry, isOwn = entry.user.id == ownId, showsOwnBadge = showsOwnBadge) { haptics.tap(); onSelectUser(entry.user) }
                            if (index < visible.lastIndex) {
                                val hairline = with(LocalDensity.current) { 1.toDp() }
                                Box(Modifier.padding(start = 67.dp).fillMaxWidth().height(hairline).background(colors.border))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** iOS chip(label:count:isSelected:): a label and its count in a capsule, the ring on the chosen one. */
@Composable
private fun ListChip(count: Int, isSelected: Boolean, tag: String, onClick: () -> Unit, label: @Composable () -> Unit) {
    val colors = AppTheme.colors
    val ring by animateColorAsState(if (isSelected) AppTheme.accent else Color.Transparent, tween(180, easing = EaseOut), label = "listChipRing")
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .plainPressFeedback(interaction)
            .background(colors.cardAlt, CapsuleShape)
            .border(1.5.dp, ring, CapsuleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        label()
        Text("$count", style = inter(12f, InterWeight.bold, monospacedDigits = true), color = if (isSelected) AppTheme.accent else colors.textSecondary)
    }
}

/** iOS row(_:): avatar, name with its badge, when, the reaction this person left, a chevron. */
@Composable
private fun ReactorRow(entry: SocialReactionEntry, isOwn: Boolean, showsOwnBadge: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val strings = appStrings
    val now = LocalClock.current.instant()
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .plainPressFeedback(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp)
            .testTag("reactions_list_row"),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(entry.user.avatarEmoji, entry.user.avatarFrame)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PersonName(entry.user, inter(14f, InterWeight.bold), isOwn = isOwn, showsOwnBadge = showsOwnBadge)
            Text(RelativeTripDate.string(entry.createdAt, now, strings), style = inter(11f), color = colors.textTertiary, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        ReactionIcon(ReactionEmoji.canonical(entry.emoji), size = 20.dp, filled = true)
        Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = 12.dp)
    }
}

/** iOS emptyState(icon:title:subtitle:): an icon, a line, a quieter line, from the top. */
@Composable
private fun ListMessage(title: String, subtitle: String, icon: @Composable () -> Unit) {
    val colors = AppTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp).padding(horizontal = 30.dp).testTag("reactions_list_message"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Text(title, style = inter(14f, InterWeight.semibold), color = colors.textSecondary, textAlign = TextAlign.Center)
        Text(subtitle, style = inter(12f), color = colors.textTertiary, textAlign = TextAlign.Center)
    }
}
