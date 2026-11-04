// iOS: NameEditorSheet.swift, AboutEditorSheet.swift, ContentFilter.swift.
package com.onezee.triptrack.feature.me

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.account.DisplayNames
import com.onezee.triptrack.feature.account.RandomDisplayName
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.logic.CommentContentRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// iOS: NameEditorSheet.maxLength / AboutEditorSheet.maxLength.
internal enum class ProfileTextField(val limit: Int) { displayName(30), bio(140) }

// iOS: ContentFilter.validate(.displayName / .tripNote). Reuse the existing sanitizer and denylist.
internal object ProfileTextRules {
    fun count(value: String) = Regex("\\X").findAll(value).count()
    fun cap(value: String, field: ProfileTextField) = Regex("\\X").findAll(value).take(field.limit).joinToString("") { it.value }
    fun error(raw: String, field: ProfileTextField, strings: AppStrings): String? {
        val clean = DisplayNames.sanitize(raw)
        if (clean.isEmpty()) return null
        if (count(clean) > field.limit) return strings.contentFilterTooLong(field.limit)
        if (field == ProfileTextField.displayName && count(clean) < 2) return strings.usernameTooShort()
        if (CommentContentRules.containsObjectionable(clean)) return strings.contentFilterContainsInappropriateLanguage()
        if (field == ProfileTextField.bio) return null
        if (clean.codePoints().noneMatch(Character::isLetter)) return strings.contentFilterMustContainAt()
        val graphemes = Regex("\\X").findAll(clean).map { it.value }.toList()
        if (graphemes.windowed(5).any { run -> run.all { it == run.first() } }) return strings.contentFilterTooManyRepeated()
        var punctuation = 0
        for (part in graphemes) {
            punctuation = if (part == " " || part.codePoints().anyMatch { Character.isLetterOrDigit(it) }) 0 else punctuation + 1
            if (punctuation >= 4) return strings.contentFilterTooManySymbols()
        }
        for (token in clean.split(Regex("[ \\-. ,’'/_]+"))) {
            val scalars = token.codePoints().toArray()
            val latin = scalars.any { it in 0x41..0x5A || it in 0x61..0x7A || it in 0xC0..0x24F }
            val cyrillic = scalars.any { it in 0x400..0x52F || it in 0x2DE0..0x2DFF || it in 0xA640..0xA69F }
            if (latin && cyrillic) return strings.contentFilterMixingLatinAnd()
        }
        return null
    }
    fun canSave(raw: String, initial: String, field: ProfileTextField, error: String?) = error == null &&
        (field == ProfileTextField.bio || DisplayNames.sanitize(raw).isNotEmpty()) &&
        (if (field == ProfileTextField.displayName) raw.trim() != initial.trim() else DisplayNames.sanitize(raw) != DisplayNames.sanitize(initial))
}

@Composable
internal fun ProfileTextEditor(field: ProfileTextField, initial: String, onSave: suspend (String) -> Unit, onClose: () -> Unit) {
    val strings = appStrings
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(field) { mutableStateOf(if (field == ProfileTextField.displayName && RandomDisplayName.isPlaceholder(initial)) "" else initial) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val error = ProfileTextRules.error(text, field, strings)
    val title = if (field == ProfileTextField.displayName) strings.nameEditorTitle() else strings.aboutTitle()
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val canSave = !saving && ProfileTextRules.canSave(text, initial, field, error)
    fun save() {
        if (!canSave) return
        saving = true; failed = false
        scope.launch {
            try { onSave(DisplayNames.sanitize(text)); onClose() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { saving = false }
        }
    }
    ContentSizedSheet(onClose, background = AppTheme.colors.bg) {
        val sheet = this
        ProfileEditorHeader(title, canSave, "profile_${field.name}_done", onCancel = { sheet.dismiss() }, onDone = ::save)
        val border = if (error != null) AppTheme.red else if (field == ProfileTextField.displayName || focused) AppTheme.accent else AppTheme.colors.borderBright
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(AppTheme.colors.card, RoundedCornerShape(14.dp))
            .border(1.5.dp, border, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            BasicTextField(text, { text = ProfileTextRules.cap(it, field) },
                Modifier.weight(1f).then(if (field == ProfileTextField.bio) Modifier.height(100.dp) else Modifier.heightIn(min = 24.dp))
                    .focusRequester(focus).onFocusChanged { focused = it.isFocused }.testTag("profile_${field.name}_field"),
                singleLine = field == ProfileTextField.displayName,
                textStyle = inter(17f, InterWeight.semibold).copy(color = AppTheme.colors.text), cursorBrush = SolidColor(AppTheme.accent),
                keyboardOptions = KeyboardOptions(imeAction = if (field == ProfileTextField.displayName) ImeAction.Done else ImeAction.Default),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { save() }))
            if (text.isNotEmpty()) Box(Modifier.size(24.dp).clickable(role = Role.Button) { text = "" }, contentAlignment = Alignment.Center) {
                Glyph(AppGlyph.xmarkCircleFill, strings.calendarClearFilter(), tint = AppTheme.colors.textTertiary, size = 18.dp)
            }
        }
        Row(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(error ?: if (field == ProfileTextField.displayName) strings.nameHelper(field.limit) else strings.aboutHint(),
                Modifier.weight(1f), style = inter(11.5f), color = if (error == null) AppTheme.colors.textSecondary else AppTheme.red)
            Text("${ProfileTextRules.count(if (field == ProfileTextField.bio) text else text.trim())}/${field.limit}", style = inter(11f, InterWeight.medium), color = AppTheme.colors.textTertiary)
        }
        if (failed) MeRow(strings.publicDataLoadFailed())
        LaunchedEffect(Unit) { delay(100); focus.requestFocus() }
    }
}

/**
 * iOS NameEditorSheet's chrome: Отмена · the title · Готово, the last one dim until there is something
 * to save. The name, the username and «О себе» share it, as on the iPhone: they sit one tap apart on
 * «Мой профиль», and a second editor style there would read as a second app.
 */
@Composable
internal fun ProfileEditorHeader(title: String, canSave: Boolean, doneTag: String, onCancel: () -> Unit, onDone: () -> Unit) {
    val strings = appStrings
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 14.dp), contentAlignment = Alignment.Center) {
        Text(title, style = inter(16f, InterWeight.bold), color = AppTheme.colors.text)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(strings.cancel(), Modifier.clickable(role = Role.Button, onClick = onCancel), style = inter(15f, InterWeight.semibold), color = AppTheme.colors.textSecondary)
            Text(strings.done(), Modifier.clickable(enabled = canSave, role = Role.Button, onClick = onDone).testTag(doneTag),
                style = inter(15f, InterWeight.bold), color = AppTheme.accent.copy(alpha = if (canSave) 1f else .4f))
        }
    }
}
