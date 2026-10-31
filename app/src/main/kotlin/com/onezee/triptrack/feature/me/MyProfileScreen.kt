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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.model.ProfileBackground
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
                             onEdit: (ProfileTextField) -> Unit, onCover: () -> Unit, onLevel: () -> Unit, onCountry: () -> Unit) {
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
        MeNavBar(strings.myProfileTitle(), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
            val colors = identity.effectiveBackground.colors()
            Box(Modifier.fillMaxWidth().height(112.dp).then(if (colors.isNotEmpty()) Modifier.background(Brush.linearGradient(colors)) else Modifier.background(AppTheme.accentBg)))
            Column(Modifier.fillMaxWidth().offset(y = (-48).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).background(AppTheme.colors.card, CircleShape).clickable(role = Role.Button) { avatars = !avatars }, contentAlignment = Alignment.Center) {
                    Text(identity.avatarEmoji, style = inter(52f))
                }
                MeRow(strings.myProfileChangeAvatar(), tag = "profile_avatar_picker", onClick = { avatars = !avatars })
                if (avatars) Column(Modifier.padding(horizontal = 16.dp)) {
                    ProfileIdentityChoices.avatars.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { emoji -> Box(Modifier.size(64.dp).background(if (identity.avatarEmoji == emoji) AppTheme.accentBg else AppTheme.colors.cardAlt, CircleShape)
                            .clickable(enabled = !busy, role = Role.Button) { save { store.setAvatar(emoji) } }, contentAlignment = Alignment.Center) { Text(emoji, style = inter(32f)) } }
                    }; Spacer(Modifier.height(12.dp)) }
                    Text(strings.myProfileAvatarHint(), style = AppType.caption, color = AppTheme.colors.textSecondary)
                }
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                }
                MeSection(strings.profileProgressGroup()) { MeRow(strings.myProfileRowLevel(), strings.tr(com.onezee.triptrack.designsystem.R.string.android_level_label, identity.level), tag = "profile_levels", onClick = onLevel) }
                if (failed) MeRow(strings.publicDataLoadFailed())
            }
        }
    }
    if (backgrounds) SettingsOptionPicker(strings.settingsProfileBackground(), MePlusGate.backgrounds, identity.effectiveBackground, "",
        label = { if (it == ProfileBackground.none) strings.cosmeticDefaultOption() else it.rawValue.replaceFirstChar(Char::uppercase) },
        onSelect = { save { store.setBackground(it) } }, onDismissRequest = { backgrounds = false }, testTagPrefix = "profile_background")
    if (username) UsernameEditor(identity.username, { store.setUsername(it) }, { username = false })
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
