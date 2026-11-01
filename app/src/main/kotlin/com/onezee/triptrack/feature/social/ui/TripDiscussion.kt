// iOS: TripCommentsSection.swift, TripCommentsScreen.swift. One row and composer for both depths.
package com.onezee.triptrack.feature.social.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.account.SignInAction
import com.onezee.triptrack.feature.account.SignInPromptSheet
import com.onezee.triptrack.feature.feed.*
import com.onezee.triptrack.feature.social.*
import com.onezee.triptrack.model.SocialAuthor
import com.onezee.triptrack.ui.RelativeTripDate
import com.onezee.triptrack.ui.navigation.DestinationNavigator
import com.onezee.triptrack.ui.navigation.entryViewModel
import com.onezee.triptrack.ui.toast
import java.util.UUID

@Composable
fun TripDiscussion(tripId: UUID, isOwner: Boolean, isOnServer: Boolean, initialCount: Int,
                   social: SocialFeature, navigator: DestinationNavigator<TripsDestination>, focus: com.onezee.triptrack.model.TripFocus = com.onezee.triptrack.model.TripFocus.Top) {
    val vm = entryViewModel("discussion:$tripId") { social.comments(tripId) }
    val state by vm.state.collectAsState()
    val strings = appStrings
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(focus == com.onezee.triptrack.model.TripFocus.Comments || focus is com.onezee.triptrack.model.TripFocus.Comment) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(vm, isOnServer) { vm.load(isOnServer) }
    if (state.unavailable) return
    val count = if (!state.hasLoadedOnce || state.loadFailed || state.nextCursor != null) maxOf(initialCount, state.comments.size) else state.comments.size
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).testTag("trip_discussion"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SocialSectionLabel(strings.commentsTitleN(count))
        Column(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.comments.take(2).reversed().forEach { comment ->
                DiscussionRow(comment, social, onAuthor = { navigator.push(TripsDestination.PublicProfile(it)) },
                    onReply = null, onDelete = null, onReport = null, onTap = { focused = false; open = true })
            }
            if (state.loadFailed) SocialMessageState(strings.discussionUnavailable(), retry = { vm.load(isOnServer) }, tag = "comments_preview_error")
            if (!isOnServer || state.isArchived) Text(if (state.isArchived) strings.discussionArchived() else strings.discussionNeedsCloud(), style = AppType.meta, color = AppTheme.colors.textTertiary)
            else PressableCard(onClick = { focused = true; open = true }, modifier = Modifier.fillMaxWidth().testTag("discussion_cta_row")) {
                Row(Modifier.fillMaxWidth().background(AppTheme.colors.cardAlt, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(social.ownAvatar(), null, size = 30.dp)
                    Text(if (state.comments.isEmpty()) strings.writeFirstMessage() else strings.commentPlaceholder(), Modifier.weight(1f).padding(horizontal = 10.dp), style = inter(14f), color = AppTheme.colors.textTertiary)
                    SocialGlyphIcon(SocialGlyph.paperplane, null, tint = AppTheme.accent, size = 15.dp)
                }
            }
        }
        if (count > 0) PressableCard(onClick = { focused = false; open = true }, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("discussion_see_all")) {
            Text(strings.discussionSeeAllPill(count), Modifier.background(AppTheme.accentBg, CapsuleShape).padding(horizontal = 14.dp, vertical = 9.dp), style = AppType.chipActive, color = AppTheme.accent)
        }
    }
    if (open) TripCommentsSheet(tripId, isOwner, isOnServer, count, focused, social, navigator, onDismiss = { open = false; vm.load(isOnServer) }, highlightId = (focus as? com.onezee.triptrack.model.TripFocus.Comment)?.id, model = vm)
}

@Composable
fun TripCommentsSheet(tripId: UUID, isOwner: Boolean, isOnServer: Boolean, initialCount: Int, startFocused: Boolean,
                      social: SocialFeature, navigator: DestinationNavigator<TripsDestination>, highlightId: UUID? = null, model: TripCommentsViewModel? = null, onDismiss: () -> Unit) {
    val vm = model ?: com.onezee.triptrack.ui.navigation.rememberLocalViewModel("thread:$tripId") { social.comments(tripId) }
    val state by vm.state.collectAsState()
    val strings = appStrings
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
    ContentSizedSheet(onDismissRequest = onDismiss, background = AppTheme.colors.bg, detent = if (startFocused) SheetDetent.large else SheetDetent.content) {
        val sheet = this
        Column(Modifier.fillMaxWidth().imePadding().testTag("comments_sheet")) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                val count = if (!state.hasLoadedOnce || state.loadFailed || state.nextCursor != null) maxOf(initialCount, state.comments.size) else state.comments.size
                Text(strings.commentsTitleN(count), Modifier.weight(1f), style = inter(19f, InterWeight.bold), color = AppTheme.colors.text)
                SheetCloseCircle(onClick = { sheet.dismiss() })
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    state.isLoading && state.comments.isEmpty() -> SkeletonRows(3)
                    state.loadFailed -> SocialMessageState(strings.discussionUnavailable(), retry = { vm.load(isOnServer) }, tag = "comments_error")
                    state.comments.isEmpty() -> Text(strings.noCommentsYet(), Modifier.padding(16.dp), style = AppType.body, color = AppTheme.colors.textTertiary)
                }
                if (state.nextCursor != null) PressableCard(onClick = { vm.loadMore() }, modifier = Modifier.testTag("comments_more")) {
                    Text(strings.showMoreComments(), Modifier.padding(12.dp), style = AppType.chipActive, color = AppTheme.accent)
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
                    Box(Modifier.bringIntoViewRequester(reveal).background(if (highlighted) AppTheme.accentBg else androidx.compose.ui.graphics.Color.Transparent)) {
                    DiscussionRow(comment, social, onAuthor = { navigator.push(TripsDestination.PublicProfile(it)) },
                        onReply = if (!isOnServer || state.isArchived) null else ({ if (social.signedIn) { vm.replyTo(comment); focus.requestFocus() } else signIn = true }),
                        onDelete = if (!state.isArchived && isOnServer && (comment.isMine || isOwner)) ({ deleting = comment }) else null,
                        onReport = if (!state.isArchived && !comment.isMine) ({ if (social.signedIn) report = ReportTarget.Comment(comment.id) else signIn = true }) else null)
                    }
                }
                if (!isOnServer || state.isArchived) Text(if (state.isArchived) strings.discussionArchived() else strings.discussionNeedsCloud(), style = AppType.meta, color = AppTheme.colors.textTertiary)
                else {
                    state.replyTo?.let { reply -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.commentReplyingTo(reply.author.displayName ?: strings.blockedListUser()), Modifier.weight(1f), style = AppType.caption, color = AppTheme.accent)
                        NavCircleButton(AppGlyph.xmark, strings.cancel(), { vm.replyTo(null) })
                    } }
                    if (!social.signedIn) PressableCard(onClick = { signIn = true }, modifier = Modifier.testTag("comments_guest")) {
                        Text(strings.commentPlaceholder(), Modifier.fillMaxWidth().surfaceCard(12.dp).padding(14.dp), style = AppType.body, color = AppTheme.colors.textTertiary)
                    } else Row(Modifier.fillMaxWidth().surfaceCard(12.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(state.text, vm::setText, Modifier.weight(1f).heightIn(min = 44.dp, max = 160.dp).focusRequester(focus).testTag("comment_input")
                            .semantics { contentDescription = strings.commentPlaceholder() }, textStyle = inter(14f).copy(color = AppTheme.colors.text), cursorBrush = SolidColor(AppTheme.accent),
                            decorationBox = { inner -> if (state.text.isEmpty()) Text(strings.commentPlaceholder(), style = inter(14f), color = AppTheme.colors.textTertiary); inner() })
                        if (state.isPosting) com.onezee.triptrack.feature.account.Spinner(AppTheme.accent)
                        else {
                            val interaction = remember { MutableInteractionSource() }
                            Box(Modifier.size(44.dp).plainPressFeedback(interaction).background(if (state.text.isBlank()) AppTheme.colors.cardAlt else AppTheme.accent, CapsuleShape)
                                .clickable(interactionSource = interaction, indication = null, enabled = state.text.isNotBlank(), role = Role.Button) { vm.post() }
                                .semantics { contentDescription = strings.send() }.testTag("comment_send"), contentAlignment = Alignment.Center) {
                                SocialGlyphIcon(SocialGlyph.arrowUp, null, tint = if (state.text.isBlank()) AppTheme.colors.textTertiary else androidx.compose.ui.graphics.Color.White, size = 20.dp)
                            }
                        }
                    }
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

@Composable
private fun DiscussionRow(comment: CommentState, social: SocialFeature, onAuthor: (UUID) -> Unit, onReply: (() -> Unit)?, onDelete: (() -> Unit)?, onReport: (() -> Unit)?, onTap: (() -> Unit)? = null) {
    val strings = appStrings
    var actions by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("comment_row"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PressableCard(onClick = { onAuthor(comment.author.id) }, modifier = Modifier.testTag("comment_author")) { PersonAvatar(comment.author.avatarEmoji, comment.author.avatarFrame, size = 30.dp) }
            PersonName(SocialAuthor(comment.author.id, comment.author.displayName, comment.author.avatarEmoji, comment.author.profileLevel, comment.author.isPlus, comment.author.avatarFrame),
                inter(12f, InterWeight.bold), comment.isMine, social.showsOwnBadge(), Modifier.weight(1f).clickable(role = Role.Button) { onAuthor(comment.author.id) })
            Text(RelativeTripDate.string(comment.createdAt, social.clock(), strings), style = AppType.caption, color = AppTheme.colors.textTertiary)
            if (onDelete != null || onReport != null) Box {
                NavCircleButton(AppGlyph.ellipsis, strings.moreActions(), { actions = true }, Modifier.testTag("comment_actions"))
                ActionPopover(actions, { actions = false }, buildList {
                    onDelete?.let { add(ActionPopoverItem(strings.delete(), AppGlyph.trash, isDestructive = true, testTag = "comment_delete", action = it)) }
                    onReport?.let { add(ActionPopoverItem(strings.reportProfileAction(), AppGlyph.flag, isDestructive = true, testTag = "comment_report", action = it)) }
                })
            }
        }
        comment.replyToName?.let { Text(strings.commentReplyingTo(it), style = AppType.caption, color = AppTheme.accent) }
        Text(comment.text, Modifier.then(if (onTap != null) Modifier.clickable(role = Role.Button, onClick = onTap) else Modifier), style = inter(14f), color = AppTheme.colors.text)
        onReply?.let { PressableCard(onClick = it, modifier = Modifier.testTag("comment_reply")) { Text(strings.commentReply(), Modifier.padding(vertical = 8.dp), style = AppType.caption, color = AppTheme.accent) } }
    }
}
