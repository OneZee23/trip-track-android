// iOS: MyProfileView.swift, CountryPickerView.swift, UsernameEditorSheet.swift, ProfileBackgroundPicker.swift
package com.onezee.triptrack.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.model.ProfileBackground
import com.onezee.triptrack.ui.AvatarGlyph
import java.util.Locale
import kotlinx.coroutines.*

// iOS: MyProfileView.avatars, CountryChoice.all, UsernameEditorSheet.normalize.
internal object ProfileIdentityChoices {
    val avatars = listOf("😎", "🙂", "😄", "🤠", "🥸", "🧔", "👨‍🦳", "👩", "👩‍🦰", "👱‍♀️", "👵", "🐶")
    val countries = "RU DE US GB FR IT ES PL KZ BY UA TR GE AM CN JP KR FI SE CZ AZ UZ KG TJ TM MD LT LV EE NO DK NL BE AT CH PT GR HU RO BG SK SI HR RS IE IS MN IL AE IN TH VN ID CA MX BR AR AU NZ ZA EG MA".split(' ')
    fun username(raw: String) = Regex("\\X").findAll(raw.lowercase(Locale.ROOT).trimStart('@')).take(20).joinToString("") { it.value }
    fun validUsername(value: String) = value.matches(Regex("[a-z0-9._]{3,20}"))
    fun flag(value: String): String? = when (value) {
        "WORLD" -> "🌍"; "NEUTRAL" -> "🏳️"
        else -> value.uppercase(Locale.ROOT).takeIf { it.length == 2 && it.all { char -> char in 'A'..'Z' } }
            ?.map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }?.joinToString("")
    }
}

@Composable
internal fun MyProfileScreen(identity: ProfileIdentity, store: ProfileIdentityStore, onBack: () -> Unit,
                             onEdit: (ProfileTextField) -> Unit, onCover: () -> Unit, onLevel: () -> Unit, onCountry: () -> Unit,
                             social: MyProfileSocial? = null) {
    val strings = appStrings
    val scope = rememberCoroutineScope()
    var avatars by rememberSaveable { mutableStateOf(false) }
    var backgrounds by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
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
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
            val colors = identity.effectiveBackground.colors()
            Box(Modifier.fillMaxWidth().height(112.dp).then(if (colors.isNotEmpty()) Modifier.background(Brush.linearGradient(colors)) else Modifier.background(AppTheme.accentBg)))
            Column(Modifier.fillMaxWidth().offset(y = (-48).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).background(AppTheme.colors.card, CircleShape).clickable(role = Role.Button) { avatars = !avatars }, contentAlignment = Alignment.Center) {
                    AvatarGlyph(identity.avatarEmoji, 52f)
                }
                MeRow(strings.myProfileChangeAvatar(), tag = "profile_avatar_picker", onClick = { avatars = !avatars })
                if (avatars) Column(Modifier.padding(horizontal = 16.dp)) {
                    ProfileIdentityChoices.avatars.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { emoji -> Box(Modifier.size(64.dp).background(if (identity.avatarEmoji == emoji) AppTheme.accentBg else AppTheme.colors.cardAlt, CircleShape)
                            .clickable(enabled = !busy, role = Role.Button) { save { store.setAvatar(emoji) } }, contentAlignment = Alignment.Center) { AvatarGlyph(emoji, 32f) } }
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
                    MeRow(strings.settingsProfileBackground(), if (identity.effectiveBackground == ProfileBackground.none) strings.cosmeticDefaultOption() else identity.effectiveBackground.rawValue.replaceFirstChar(Char::uppercase), onClick = { backgrounds = true })
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
    if (backgrounds) SettingsOptionPicker(strings.settingsProfileBackground(), MePlusGate.backgrounds, identity.effectiveBackground, "",
        label = { if (it == ProfileBackground.none) strings.cosmeticDefaultOption() else it.rawValue.replaceFirstChar(Char::uppercase) },
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

@Composable
private fun UsernameEditor(initial: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    val strings = appStrings
    var text by rememberSaveable { mutableStateOf(initial) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(text) { ready = false; if (ProfileIdentityChoices.validUsername(text) && text != initial) { delay(400); ready = true } }
    ContentSizedSheet(onClose, background = AppTheme.colors.bg) {
        MeSheetHeader(strings.usernameTitle()) { dismiss() }
        MeField("@", text, { text = ProfileIdentityChoices.username(it) }, tag = "username_editor_field")
        Text(strings.usernameHint(), Modifier.padding(16.dp), style = AppType.caption, color = AppTheme.colors.textSecondary)
        if (text.isNotEmpty() && text.length < 3) MeRow(strings.usernameTooShort())
        else if (text.isNotEmpty() && !ProfileIdentityChoices.validUsername(text)) MeRow(strings.usernameInvalidChars())
        // iOS also has no availability endpoint; a failed check allows a locally stored handle.
        MeAction(strings.done(), { onSave(text); onClose() }, Modifier.padding(16.dp), enabled = ready)
    }
}
