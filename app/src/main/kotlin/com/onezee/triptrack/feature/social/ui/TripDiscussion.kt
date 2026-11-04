// iOS: TripCommentsSection.swift, TripCommentsScreen.swift. One row and composer for both depths.
package com.onezee.triptrack.feature.social.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.account.SignInAction
import com.onezee.triptrack.feature.account.SignInPromptSheet
import com.onezee.triptrack.feature.feed.*
import com.onezee.triptrack.feature.social.*
import com.onezee.triptrack.model.AvatarFrame
import com.onezee.triptrack.model.SocialAuthor
import com.onezee.triptrack.ui.DetailSectionHeader
import com.onezee.triptrack.ui.navigation.DestinationNavigator
import com.onezee.triptrack.ui.navigation.entryViewModel
import com.onezee.triptrack.ui.toast
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * iOS: TripCommentsSection (isPreview). «Обсуждение · N» on the trip screen, a teaser of constant
 * height: the two freshest messages in one card, a row that looks like a composer but is a door (it
 * opens the thread with the keyboard up), and a «Всё обсуждение · N ›» pill. It is a section of the
 * details column: the screen's 22-point rhythm sits around it, the heading is the 17 bold one.
 */
@Composable
fun TripDiscussion(tripId: UUID, isOwner: Boolean, isOnServer: Boolean, initialCount: Int,
                   social: SocialFeature, navigator: DestinationNavigator<TripsDestination>, focus: com.onezee.triptrack.model.TripFocus = com.onezee.triptrack.model.TripFocus.Top) {
    val vm = entryViewModel("discussion:$tripId") { social.comments(tripId) }
    val state by vm.state.collectAsState()
    val strings = appStrings
    val colors = AppTheme.colors
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(focus == com.onezee.triptrack.model.TripFocus.Comments || focus is com.onezee.triptrack.model.TripFocus.Comment) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(vm, isOnServer) { vm.load(isOnServer) }
    if (state.unavailable) return
    val count = if (!state.hasLoadedOnce || state.loadFailed || state.nextCursor != null) maxOf(initialCount, state.comments.size) else state.comments.size
    Column(Modifier.fillMaxWidth().testTag("trip_discussion"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailSectionHeader(strings.commentsTitleN(count), Modifier.semantics { heading() })
        Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
            state.comments.take(2).reversed().forEach { comment ->
                DiscussionRow(comment, social, onAuthor = { navigator.push(TripsDestination.PublicProfile(it)) },
                    onReply = null, onDelete = null, onReport = null, onTap = { focused = false; open = true })
                Divider()
            }
            if (state.loadFailed) SocialMessageState(strings.discussionUnavailable(), retry = { vm.load(isOnServer) }, tag = "comments_preview_error")
            // Nothing to reply to: a saved copy only when the device really kept one, else the trip is not in the cloud.
            if (!isOnServer || state.isArchived) Text(if (state.isArchived) strings.discussionArchived() else strings.discussionNeedsCloud(), Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                style = inter(12f), color = colors.textTertiary)
            else {
                // Dressed as the text field it stands in for: filled, bordered, a send glyph at the end.
                val interaction = remember { MutableInteractionSource() }
                Row(
                    Modifier.fillMaxWidth().padding(12.dp)
                        .plainPressFeedback(interaction)
                        .background(colors.cardAlt, RoundedCornerShape(12.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button) { focused = true; open = true }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                        .testTag("discussion_cta_row"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    OwnAvatarDisc(social.ownAvatar())
                    Text(if (state.comments.isEmpty()) strings.writeFirstMessage() else strings.commentPlaceholder(), Modifier.weight(1f), style = inter(14f), color = colors.textTertiary, maxLines = 1)
                    SocialGlyphIcon(SocialGlyph.paperplane, null, tint = AppTheme.accent, size = 15.dp)
                }
            }
        }
        if (count > 0) {
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier.align(Alignment.CenterHorizontally).height(32.dp)
                    .plainPressFeedback(interaction)
                    .background(AppTheme.accent.copy(alpha = 0.12f), CapsuleShape)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { focused = false; open = true }
                    .padding(horizontal = 14.dp)
                    .testTag("discussion_see_all"),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.discussionSeeAllPill(count), style = inter(13f, InterWeight.semibold), color = AppTheme.accent, maxLines = 1)
                Glyph(AppGlyph.chevronRight, null, tint = AppTheme.accent, size = 12.dp)
            }
        }
    }
    if (open) TripCommentsSheet(tripId, isOwner, isOnServer, count, focused, social, navigator, onDismiss = { open = false; vm.load(isOnServer) }, highlightId = (focus as? com.onezee.triptrack.model.TripFocus.Comment)?.id, model = vm)
}

/**
 * iOS: TripCommentsScreen. The whole thread in a sheet: the same card as the teaser (rows split by
 * hairlines, the composer as its last row), older messages above, newest at the bottom. Replying
 * lives here only.
 */
@Composable
fun TripCommentsSheet(tripId: UUID, isOwner: Boolean, isOnServer: Boolean, initialCount: Int, startFocused: Boolean,
                      social: SocialFeature, navigator: DestinationNavigator<TripsDestination>, highlightId: UUID? = null, model: TripCommentsViewModel? = null, onDismiss: () -> Unit) {
    val vm = model ?: com.onezee.triptrack.ui.navigation.rememberLocalViewModel("thread:$tripId") { social.comments(tripId) }
    val state by vm.state.collectAsState()
    val strings = appStrings
    val colors = AppTheme.colors
    var deleting by remember { mutableStateOf<CommentState?>(null) }
    var report by remember { mutableStateOf<ReportTarget?>(null) }
    var signIn by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<ToastItem?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(vm, highlightId) {
        vm.load(isOnServer)?.join()
        var pages = 0
        while (highlightId != null && state.comments.none { it.id == highlightId } && state.nextCursor != null && pages < 5) {
            vm.loadMore()?.join() ?: break
            pages++
        }
    }
    LaunchedEffect(state.message) { state.message?.let { toast = strings.toast(it) } }
    ContentSizedSheet(onDismissRequest = onDismiss, background = colors.bg, detent = if (startFocused) SheetDetent.large else SheetDetent.content) {
        val sheet = this
        Column(Modifier.fillMaxWidth().testTag("comments_sheet")) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                val count = if (!state.hasLoadedOnce || state.loadFailed || state.nextCursor != null) maxOf(initialCount, state.comments.size) else state.comments.size
                Text(strings.commentsTitleN(count), Modifier.weight(1f), style = inter(19f, InterWeight.bold), color = colors.text)
                SheetCloseCircle(onClick = { sheet.dismiss() })
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
                    when {
                        state.isLoading && state.comments.isEmpty() -> SkeletonRows(3, Modifier.padding(12.dp))
                        state.loadFailed -> SocialMessageState(strings.discussionUnavailable(), retry = { vm.load(isOnServer) }, tag = "comments_error")
                        state.comments.isEmpty() -> {
                            Text(strings.noCommentsYet(), Modifier.fillMaxWidth().padding(vertical = 16.dp), style = inter(14f), color = colors.textTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Divider()
                        }
                    }
                    // Above the thread, not below it: what it loads is older, and older is up.
                    if (state.nextCursor != null) {
                        val interaction = remember { MutableInteractionSource() }
                        Text(strings.showMoreComments(), Modifier.fillMaxWidth().plainPressFeedback(interaction)
                            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { vm.loadMore() }
                            .padding(vertical = 8.dp).testTag("comments_more"),
                            style = inter(13f, InterWeight.semibold), color = AppTheme.accent, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Divider()
                    }
                    state.comments.reversed().forEach { comment ->
                        val reveal = remember(comment.id) { androidx.compose.foundation.relocation.BringIntoViewRequester() }
                        var highlighted by remember(comment.id) { mutableStateOf(false) }
                        LaunchedEffect(highlightId, comment.id) {
                            if (highlightId == comment.id) {
                                kotlinx.coroutines.delay(550)
                                reveal.bringIntoView()
                                highlighted = true
                                kotlinx.coroutines.delay(2400)
                                highlighted = false
                            }
                        }
                        Box(Modifier.bringIntoViewRequester(reveal)) {
                            DiscussionRow(comment, social, onAuthor = { navigator.push(TripsDestination.PublicProfile(it)) },
                                onReply = if (!isOnServer || state.isArchived) null else ({ if (social.signedIn) { vm.replyTo(comment); focus.requestFocus() } else signIn = true }),
                                onDelete = if (!state.isArchived && isOnServer && (comment.isMine || isOwner)) ({ deleting = comment }) else null,
                                onReport = if (!state.isArchived && !comment.isMine) ({ if (social.signedIn) report = ReportTarget.Comment(comment.id) else signIn = true }) else null,
                                highlighted = highlighted)
                        }
                        Divider()
                    }
                    if (!isOnServer || state.isArchived) Text(if (state.isArchived) strings.discussionArchived() else strings.discussionNeedsCloud(), Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        style = inter(12f), color = colors.textTertiary)
                    else Composer(state, vm, social, focus, onGuestTap = { signIn = true })
                }
            }
        }
        ToastHost(toast, onDismiss = { toast = null })
        LaunchedEffect(startFocused, social.signedIn) { if (startFocused && social.signedIn) focus.requestFocus() }
    }
    AppConfirm(item = deleting, onDismissRequest = { deleting = null }, title = { strings.deleteCommentConfirm() }, actions = { comment ->
        listOf(AppDialogAction(strings.delete(), AppDialogAction.Kind.destructive, testTag = "comment_delete_confirm") { vm.delete(comment.id) })
    })
    report?.let { ReportSheet(it, social) { report = null } }
    if (signIn) social.auth?.let { SignInPromptSheet(SignInAction.comment, it, onDismissRequest = { signIn = false }) }
}

/** iOS composerRow: the reply chip, then the viewer's disc, the field as a pill and a round «↑», grey until there is something to send. */
@Composable
private fun Composer(state: TripCommentsState, vm: TripCommentsViewModel, social: SocialFeature, focus: FocusRequester, onGuestTap: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
            // A guest reads but cannot write: a tap anywhere on the row opens the sign-in sheet, not the field.
            .then(if (!social.signedIn) Modifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onGuestTap).testTag("comments_guest") else Modifier),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.replyTo?.let { reply ->
            Row(
                Modifier.padding(start = 38.dp).background(AppTheme.accentBg, CapsuleShape).padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                SocialGlyphIcon(SocialGlyph.replyArrow, null, tint = AppTheme.accent, size = 12.dp)
                Text(strings.commentReplyingTo(reply.author.displayName ?: strings.blockedListUser()), Modifier.weight(1f, fill = false), style = inter(12f, InterWeight.semibold), color = colors.textSecondary, maxLines = 1)
                val interaction = remember { MutableInteractionSource() }
                Box(Modifier.size(22.dp).plainPressFeedback(interaction)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { vm.replyTo(null) }
                    .semantics { contentDescription = strings.cancel() }, contentAlignment = Alignment.Center) {
                    Glyph(AppGlyph.xmark, null, tint = colors.textTertiary, size = 12.dp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OwnAvatarDisc(social.ownAvatar())
            Box(Modifier.weight(1f).heightIn(min = 38.dp).background(colors.cardAlt, CapsuleShape).padding(horizontal = 14.dp, vertical = 9.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(state.text, vm::setText, Modifier.fillMaxWidth().heightIn(max = 120.dp).focusRequester(focus).testTag("comment_input")
                    .semantics { contentDescription = strings.commentPlaceholder() }, enabled = social.signedIn, textStyle = inter(14f).copy(color = colors.text), cursorBrush = SolidColor(AppTheme.accent),
                    decorationBox = { inner -> if (state.text.isEmpty()) Text(strings.commentPlaceholder(), style = inter(14f), color = colors.textTertiary, maxLines = 1); inner() })
            }
            if (state.isPosting) com.onezee.triptrack.feature.account.Spinner(AppTheme.accent)
            else {
                val interaction = remember { MutableInteractionSource() }
                val ready = state.text.isNotBlank()
                Box(Modifier.size(32.dp).plainPressFeedback(interaction).background(if (ready) AppTheme.accent else colors.textTertiary.copy(alpha = 0.35f), CircleShape)
                    .clickable(interactionSource = interaction, indication = null, enabled = ready && social.signedIn, role = Role.Button) { vm.post() }
                    .semantics { contentDescription = strings.send() }.testTag("comment_send"), contentAlignment = Alignment.Center) {
                    SocialGlyphIcon(SocialGlyph.arrowUp, null, tint = Color.White, size = 18.dp)
                }
            }
        }
    }
}

/** iOS: the viewer's 30 disc on a 12 % accent wash with their emoji at 15. */
@Composable
private fun OwnAvatarDisc(emoji: String) {
    Box(Modifier.size(30.dp).background(AppTheme.accent.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) { Text(emoji, style = inter(15f)) }
}

/** The hairline between the rows of the discussion card. */
@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.border))
}

/** iOS: TripCommentsSection.relativeAge. «сейчас», «5 мин», «2 ч», «3 д»; days cap the scale. */
internal fun relativeAge(date: Instant, now: Instant, strings: AppStrings): String {
    val delta = maxOf(0L, Duration.between(date, now).seconds)
    return when {
        delta < 60 -> strings.relTimeNow()
        delta < 3600 -> strings.relTimeMinutes((delta / 60).toInt())
        delta < 86_400 -> strings.relTimeHours((delta / 3600).toInt())
        else -> strings.relTimeDays((delta / 86_400).toInt())
    }
}

/**
 * iOS: TripCommentsSection.commentRow. A 34 disc (the person's PRO frame on it) beside the name,
 * the badge and «· 2 ч» on one baseline, then the text at 14; a reply is indented one step and says
 * whom it answers. The padding is the row's own (14 / 12): the card has none. «Ответить» lives in
 * the thread only. iOS opens delete from a long press; here a quiet «…» also carries the report
 * Android adds, and only where there is something to do.
 */
@Composable
private fun DiscussionRow(
    comment: CommentState, social: SocialFeature, onAuthor: (UUID) -> Unit, onReply: (() -> Unit)?, onDelete: (() -> Unit)?,
    onReport: (() -> Unit)?, onTap: (() -> Unit)? = null, highlighted: Boolean = false,
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val accent = AppTheme.accent
    var actions by remember { mutableStateOf(false) }
    val isReply = comment.parentId != null
    val author = SocialAuthor(comment.author.id, comment.author.displayName, comment.author.avatarEmoji, comment.author.profileLevel, comment.author.isPlus, comment.author.avatarFrame)
    Row(
        Modifier.fillMaxWidth().testTag("comment_row")
            // Arriving from «X прокомментировал»: the row is tinted and rides an accent rail down its leading edge.
            .drawBehind {
                if (highlighted) {
                    drawRoundRect(accent.copy(alpha = 0.10f), Offset(6.dp.toPx(), 0f), Size(size.width - 12.dp.toPx(), size.height), CornerRadius(12.dp.toPx()))
                    drawRoundRect(accent, Offset(6.dp.toPx(), 8.dp.toPx()), Size(3.dp.toPx(), size.height - 16.dp.toPx()), CornerRadius(1.5.dp.toPx()))
                }
            }
            .padding(start = if (isReply) 42.dp else 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val frame = AvatarFrame.fromRawValue(comment.author.avatarFrame.orEmpty()) ?: AvatarFrame.none
        Box(
            Modifier.size(34.dp).background(colors.cardAlt, CircleShape).avatarFrame(frame, 2.dp)
                .clickable(role = Role.Button) { onAuthor(comment.author.id) }.testTag("comment_author"),
            contentAlignment = Alignment.Center,
        ) { Text(comment.author.avatarEmoji ?: "🚗", style = inter(17f)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(comment.author.displayName ?: strings.companionsNoName(), Modifier.weight(1f, fill = false).alignBy(FirstBaseline)
                    .clickable(role = Role.Button) { onAuthor(comment.author.id) }, style = inter(13f, InterWeight.semibold), color = colors.text, maxLines = 1)
                if (PlusBadgeVisibility.shows(author.isPlus, comment.isMine, social.showsOwnBadge())) ProBadge(height = 15.dp, modifier = Modifier.alignBy { it.measuredHeight })
                Text("· " + relativeAge(comment.createdAt, social.clock(), strings), Modifier.alignBy(FirstBaseline), style = inter(11f), color = colors.textTertiary, maxLines = 1)
            }
            // Named, not just indented: a reply can be paged in without its parent.
            if (isReply) comment.replyToName?.let { Text(strings.commentReplyingTo(it), style = inter(11f, InterWeight.semibold), color = accent, maxLines = 1) }
            Text(comment.text, Modifier.then(if (onTap != null) Modifier.clickable(role = Role.Button, onClick = onTap) else Modifier),
                style = inter(14f), lineHeight = points(14f * 1.21f + 4.5f), color = colors.text)
            onReply?.let {
                val interaction = remember { MutableInteractionSource() }
                Text(strings.commentReply(), Modifier.padding(top = 2.dp).plainPressFeedback(interaction)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = it).testTag("comment_reply"),
                    style = inter(12f, InterWeight.semibold), color = colors.textTertiary)
            }
        }
        if (onDelete != null || onReport != null) Box {
            val interaction = remember { MutableInteractionSource() }
            Box(Modifier.size(28.dp).plainPressFeedback(interaction)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) { actions = true }
                .semantics { contentDescription = strings.moreActions() }.testTag("comment_actions"), contentAlignment = Alignment.Center) {
                Glyph(AppGlyph.ellipsis, null, tint = colors.textTertiary, size = 20.dp)
            }
            ActionPopover(actions, { actions = false }, buildList {
                onDelete?.let { add(ActionPopoverItem(strings.delete(), AppGlyph.trash, isDestructive = true, testTag = "comment_delete", action = it)) }
                onReport?.let { add(ActionPopoverItem(strings.reportProfileAction(), AppGlyph.flag, isDestructive = true, testTag = "comment_report", action = it)) }
            })
        }
    }
}
