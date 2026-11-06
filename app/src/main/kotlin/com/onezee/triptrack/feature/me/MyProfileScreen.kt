// iOS: MyProfileView.swift, CountryPickerView.swift, UsernameEditorSheet.swift, ProfileBackgroundPicker.swift
package com.onezee.triptrack.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.navigation.tabBarContentClearance
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.model.ProfileBackground
import com.onezee.triptrack.model.AvatarFrame
import com.onezee.triptrack.feature.social.ui.avatarFrame
import com.onezee.triptrack.feature.social.ui.drawCover
import java.util.Locale
import kotlinx.coroutines.*

// iOS: MyProfileView.avatars, CountryChoice.all, UsernameEditorSheet.normalize.
internal enum class UsernameError { tooShort, invalidChars }

internal object ProfileIdentityChoices {
    val avatars = listOf("😎", "🙂", "😄", "🤠", "🥸", "🧔", "👨‍🦳", "👩", "👩‍🦰", "👱‍♀️", "👵", "🐶")
    val countries = "RU DE US GB FR IT ES PL KZ BY UA TR GE AM CN JP KR FI SE CZ AZ UZ KG TJ TM MD LT LV EE NO DK NL BE AT CH PT GR HU RO BG SK SI HR RS IE IS MN IL AE IN TH VN ID CA MX BR AR AU NZ ZA EG MA".split(' ')
    fun username(raw: String) = Regex("\\X").findAll(raw.lowercase(Locale.ROOT).trimStart('@')).take(20).joinToString("") { it.value }
    fun validUsername(value: String) = value.matches(Regex("[a-z0-9._]{3,20}"))
    /** iOS UsernameEditorSheet.revalidate: nothing to say while empty or unchanged, the length first, then the letters. */
    fun usernameError(value: String, initial: String): UsernameError? = when {
        value.isEmpty() || value == initial -> null
        Regex("\\X").findAll(value).count() < 3 -> UsernameError.tooShort
        !value.matches(Regex("[a-z0-9._]+")) -> UsernameError.invalidChars
        else -> null
    }
    fun flag(value: String): String? = when (value) {
        "WORLD" -> "🌍"; "NEUTRAL" -> "🏳️"
        else -> value.uppercase(Locale.ROOT).takeIf { it.length == 2 && it.all { char -> char in 'A'..'Z' } }
            ?.map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }?.joinToString("")
    }
}

@Composable
internal fun MyProfileScreen(identity: ProfileIdentity, store: ProfileIdentityStore, onBack: () -> Unit,
                             onEdit: (ProfileTextField) -> Unit, onCover: () -> Unit, onLevel: () -> Unit, onCountry: () -> Unit,
                             social: MyProfileSocial? = null, openBackgroundPicker: Boolean = false, onBackgroundPickerOpened: () -> Unit = {},
                             onFrameShowcase: () -> Unit = {}) {
    val strings = appStrings
    val scope = rememberCoroutineScope()
    var avatars by rememberSaveable { mutableStateOf(false) }
    var backgrounds by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(openBackgroundPicker) { if (openBackgroundPicker) { backgrounds = true; onBackgroundPickerOpened() } }
    fun save(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action(); failed = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding()) {
        CustomNavBar(strings.myProfileTitle(), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = tabBarContentClearance())) {
            val colors = identity.effectiveBackground.colors()
            Box(Modifier.fillMaxWidth().height(112.dp).drawBehind {
                if (colors.isNotEmpty()) drawCover(identity.effectiveBackground) else drawRect(AppTheme.accentBg)
            })
            Column(Modifier.fillMaxWidth().offset(y = (-48).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).background(AppTheme.colors.card, CircleShape).avatarFrame(identity.avatarFrame, 3.dp).clickable(role = Role.Button) { avatars = !avatars }, contentAlignment = Alignment.Center) {
                    Text(identity.avatarEmoji, style = inter(52f))
                    Box(Modifier.align(Alignment.BottomEnd).size(28.dp).background(AppTheme.accent, CircleShape), contentAlignment = Alignment.Center) {
                        Glyph(AppGlyph.pencil, null, tint = Color.White, size = 13.dp)
                    }
                }
                Text(identity.name.ifBlank { strings.meAddYourName() }, Modifier.padding(top = 8.dp), style = inter(17f, InterWeight.bold), color = AppTheme.colors.text, maxLines = 1)
                Text(identity.username.takeIf(String::isNotEmpty)?.let { "@$it" }.orEmpty(), style = AppType.caption, color = AppTheme.colors.textSecondary, maxLines = 1)
                Text(strings.myProfileChangeAvatar(), Modifier.padding(top = 8.dp).clickable(role = Role.Button) { avatars = !avatars }.testTag("profile_avatar_picker"),
                    style = inter(13f, InterWeight.semibold), color = AppTheme.accent)
                if (avatars) Column(Modifier.padding(horizontal = 16.dp)) {
                    ProfileIdentityChoices.avatars.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { emoji -> Box(Modifier.size(64.dp).background(if (identity.avatarEmoji == emoji) AppTheme.accentBg else AppTheme.colors.cardAlt, CircleShape)
                            .clickable(enabled = !busy, role = Role.Button) { save { store.setAvatar(emoji) } }, contentAlignment = Alignment.Center) { Text(emoji, style = inter(32f)) } }
                    }; Spacer(Modifier.height(12.dp)) }
                    Text(strings.myProfileAvatarHint(), style = AppType.caption, color = AppTheme.colors.textSecondary)
                }
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (social != null) FollowCard(social)
                MeSection(strings.profileDetailsGroup()) {
                    MeRow(strings.myProfileRowName(), identity.name.ifBlank { strings.meAddYourName() }, tag = "profile_edit_name", onClick = { onEdit(ProfileTextField.displayName) })
                    MeDivider()
                    MeRow(strings.myProfileRowUsername(), identity.username.takeIf(String::isNotEmpty)?.let { "@$it" } ?: strings.myProfileUsernameUnset(), onClick = { username = true })
                    MeDivider()
                    MeRow(strings.myProfileRowAbout(), identity.bio.ifBlank { strings.myProfileAboutUnset() }, tag = "profile_edit_bio", onClick = { onEdit(ProfileTextField.bio) })
                    MeDivider()
                    MeRow(strings.myProfileRowCountry(), ProfileIdentityChoices.flag(identity.country) ?: strings.countryNone(), tag = "profile_country", onClick = onCountry)
                }
                MeSection(strings.profileAppearanceGroup()) {
                    MeRow(strings.myProfileRowCover(), if (identity.coverPhotoIds.isEmpty()) strings.myProfileCoverUnset() else strings.photosCount(identity.coverPhotoIds.size), onClick = onCover)
                    MeDivider()
                    MeRow(strings.showcaseFrame(), identity.avatarFrame.displayName.ifBlank { strings.cosmeticDefaultOption() }, tag = "profile_avatar_frame", onClick = onFrameShowcase)
                    MeDivider()
                    MeRow(strings.settingsProfileBackground(), identity.effectiveBackground.displayNameOrDefault(strings), onClick = { backgrounds = true })
                    // «Как видят другие» sits with the rows that decide how that identity looks; only
                    // with a public profile: signed out the screen behind it has nothing to draw.
                    if (social != null) { MeDivider(); MeRow(strings.myProfileRowPreview(), tag = "my_profile_row_preview", onClick = social.onPreview) }
                }
                MeSection(strings.profileProgressGroup()) {
                    MeRow(strings.myProfileRowLevel(), strings.tr(com.onezee.triptrack.designsystem.R.string.android_level_label, identity.level), tag = "profile_levels", onClick = onLevel)
                    social?.onStats?.let { stats -> MeDivider(); MeRow(strings.myProfileRowStats(), tag = "my_profile_row_stats", onClick = stats) }
                }
                if (failed) MeRow(strings.publicDataLoadFailed())
            }
        }
    }
    if (backgrounds) SettingsOptionPicker(strings.settingsProfileBackground(), MePlusGate.backgrounds(identity.isPlus), identity.effectiveBackground, "",
        label = { it.displayNameOrDefault(strings) },
        badgeTint = { it.colors().firstOrNull() },
        onSelect = { save { store.setBackground(it) } }, onDismissRequest = { backgrounds = false }, testTagPrefix = "profile_background")
    if (username) UsernameEditor(identity.username, { store.setUsername(it) }, { username = false })
}

/** What «Мой профиль» needs of the social side: the account, its counts and the doors to the lists. */
internal class MyProfileSocial(
    val accountId: java.util.UUID,
    val api: com.onezee.triptrack.feature.social.SocialApi,
    val onFollowList: (com.onezee.triptrack.feature.social.FollowListMode) -> Unit,
    val onPreview: () -> Unit,
    val onStats: (() -> Unit)?,
)

/**
 * iOS MyProfileView.followCard: the account's follower and following totals as one card with two
 * doors. One profile GET; a failure leaves the dashes (a decoration, not a reason for a banner).
 */
@Composable
private fun FollowCard(social: MyProfileSocial) {
    val strings = appStrings
    val counts by produceState<Pair<Int, Int>?>(null, social.accountId) {
        value = try { social.api.profile(social.accountId).let { it.followerCount to it.followingCount } }
            catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
    }
    Row(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(com.onezee.triptrack.feature.social.FollowListMode.followers, com.onezee.triptrack.feature.social.FollowListMode.following).forEachIndexed { index, mode ->
            if (index > 0) Box(Modifier.width(1.dp).height(36.dp).background(AppTheme.colors.borderBright))
            val count = if (mode == com.onezee.triptrack.feature.social.FollowListMode.followers) counts?.first else counts?.second
            Column(Modifier.weight(1f).heightIn(min = 44.dp).clickable(role = Role.Button) { social.onFollowList(mode) }
                .testTag(if (index == 0) "my_profile_followers" else "my_profile_following"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(count?.toString() ?: "—", style = interScaled(20f, InterWeight.heavy, monospacedDigits = true), color = AppTheme.colors.text, maxLines = 1)
                Text(if (index == 0) strings.followersCaption(count ?: 0) else strings.followingCaption(count ?: 0), style = interScaled(11f, InterWeight.semibold), color = AppTheme.colors.textSecondary)
            }
        }
    }
}

/**
 * iOS UsernameEditorSheet: the name editor's chrome over a bordered field with the «@» glued to the
 * handle, the rules and a counter under it, and a line that says what is wrong while it is wrong.
 * The field takes the keyboard at once, as the name's does: build 13 opened a bare «@» with no caret
 * and a button at the bottom, a different editor one row below the name's.
 *
 * There is no server to ask whether a handle is free (iOS asks nobody either, `canCheckAvailability:
 * false`), so a valid new handle can be saved after the same 400 ms the iPhone waits for its answer.
 */
@Composable
internal fun UsernameEditor(initial: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val start = remember(initial) { ProfileIdentityChoices.username(initial) }
    var text by rememberSaveable { mutableStateOf(start) }
    var ready by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val error = ProfileIdentityChoices.usernameError(text, start)?.let { if (it == UsernameError.tooShort) strings.usernameTooShort() else strings.usernameInvalidChars() }
    LaunchedEffect(text) { ready = false; if (ProfileIdentityChoices.validUsername(text) && text != start) { delay(400); ready = true } }
    ContentSizedSheet(onClose, background = colors.bg) {
        val sheet = this
        fun save() { if (ready) { onSave(text); sheet.dismiss() } }
        ProfileEditorHeader(strings.usernameTitle(), ready, "username_editor_done", onCancel = { sheet.dismiss() }, onDone = ::save)
        val border = if (error != null) AppTheme.red else if (focused) AppTheme.accent else colors.borderBright
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(colors.card, RoundedCornerShape(14.dp))
            .border(1.5.dp, border, RoundedCornerShape(14.dp)).padding(start = 16.dp, end = 8.dp).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically) {
            // Glued to the handle, so the two read as one word; never part of what is stored.
            Text("@", style = inter(17f, InterWeight.semibold), color = colors.textTertiary)
            BasicTextField(text, { text = ProfileIdentityChoices.username(it) },
                Modifier.weight(1f).padding(vertical = 14.dp).focusRequester(focus).onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = strings.usernameTitle() }.testTag("username_editor_field"),
                singleLine = true, textStyle = inter(17f, InterWeight.semibold).copy(color = colors.text), cursorBrush = SolidColor(AppTheme.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }))
            if (text.isNotEmpty()) Box(Modifier.size(44.dp).clickable(role = Role.Button) { text = "" }.testTag("username_editor_clear"), contentAlignment = Alignment.Center) {
                Glyph(AppGlyph.xmarkCircleFill, strings.calendarClearFilter(), tint = colors.textTertiary, size = 18.dp)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Two lines kept, as on iOS: the sheet is as tall as its content and must not twitch.
                Text(strings.usernameHint(), Modifier.weight(1f), style = inter(11.5f), color = colors.textSecondary, minLines = 2)
                val count = ProfileTextRules.count(text)
                Text("$count/$USERNAME_MAX", style = inter(11f, InterWeight.medium, monospacedDigits = true),
                    color = if (count >= USERNAME_MAX) AppTheme.red else colors.textTertiary)
            }
            // Fixed height, so the line coming and going never resizes the sheet.
            Row(Modifier.height(16.dp).testTag("username_editor_verdict"), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (error != null) {
                    Glyph(AppGlyph.xmark, null, tint = AppTheme.red, size = 10.dp)
                    Text(error, style = inter(12f, InterWeight.semibold), color = AppTheme.red, maxLines = 1)
                }
            }
        }
        LaunchedEffect(Unit) { delay(100); focus.requestFocus() }
    }
}

private const val USERNAME_MAX = 20
