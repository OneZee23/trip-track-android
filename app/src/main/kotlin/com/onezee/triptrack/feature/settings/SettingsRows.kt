// iOS: ProfileComponents.swift (SettingsIconRow, SettingsRowChevron, SettingsRowValue),
// ProfileSettingsSheet.swift (SettingsToggleRow, SettingsLinkRow)
package com.onezee.triptrack.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.account.HouseSwitch
import com.onezee.triptrack.feature.account.SyncState
import com.onezee.triptrack.feature.account.SyncStatusMarker
import com.onezee.triptrack.feature.me.isAccessibilitySize

/**
 * iOS: the icon square of a settings row. 30 × 30, radius 8, the accent wash and the glyph at 16 pt in the
 * accent (a box of 19.2, the call-site convention); a brand mark from the asset catalogue is a 16 pt template.
 * Decoration: the title beside it says what the row is.
 */
@Composable
internal fun SettingsTile(glyph: AppGlyph?, @DrawableRes icon: Int? = null, modifier: Modifier = Modifier) {
    Box(modifier.size(30.dp).background(AppTheme.accentBg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        if (icon != null) Image(painterResource(icon), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(AppTheme.accent))
        else if (glyph != null) Glyph(glyph, null, tint = AppTheme.accent, size = SettingsGlyphBox)
    }
}

/** A 16 pt symbol in the 1.2 × box convention of the glyph set. */
private val SettingsGlyphBox = 19.2.dp

/** iOS SettingsRowChevron: a 12.96 pt semibold chevron in an 18 × 18 frame, the tertiary ink. */
@Composable
internal fun SettingsRowChevron() {
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        Glyph(AppGlyph.chevronRight, null, tint = AppTheme.colors.textTertiary, size = 15.5.dp)
    }
}

/** iOS SettingsRowValue: the quiet value at the end of a row, 14 medium in the secondary ink. */
@Composable
internal fun SettingsRowValue(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = interScaled(14f, InterWeight.medium), color = AppTheme.colors.textSecondary, maxLines = if (isAccessibilitySize()) Int.MAX_VALUE else 1)
}

/**
 * iOS SettingsIconRow (no subtitle: 13 above and below, 56 with the tile) and SettingsLinkRow (a subtitle: 11
 * above and below and a gap of 2, about 55). The title is 14.5 semibold, the subtitle 11 in the secondary ink.
 * At the accessibility text sizes the trailing value goes under the title; a chevron alone stays beside it.
 */
@Composable
internal fun SettingsIconRow(
    title: String,
    modifier: Modifier = Modifier,
    glyph: AppGlyph? = null,
    @DrawableRes icon: Int? = null,
    subtitle: String? = null,
    tag: String = "",
    stacksTrailing: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val stacked = isAccessibilitySize() && stacksTrailing && trailing != null
    Row(
        modifier.fillMaxWidth().testTag(tag)
            .then(if (onClick != null) Modifier.plainPressFeedback(interaction).clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = if (subtitle != null) 11.dp else 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTile(glyph, icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (stacked) 6.dp else 2.dp)) {
            Text(title, style = interScaled(14.5f, InterWeight.semibold), color = colors.text)
            if (subtitle != null) Text(subtitle, style = interScaled(11f), color = colors.textSecondary)
            if (stacked) trailing?.invoke()
        }
        if (!stacked && trailing != null) trailing()
    }
}

/**
 * iOS SettingsToggleRow: the icon square, the title and the system switch at 0.9 in its 51 × 44 frame, so the
 * row stays 56 tall. The whole row switches for TalkBack and a tap beside the knob; the switch itself is the
 * iPhone's drawing ([HouseSwitch]). Dimmed while the server value is still coming.
 */
@Composable
internal fun SettingsToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, glyph: AppGlyph, enabled: Boolean = true, tag: String = "") {
    val accessibility = isAccessibilitySize()
    Row(
        Modifier.fillMaxWidth().testTag(tag).alpha(if (enabled) 1f else 0.55f)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 14.dp, vertical = if (accessibility) 13.dp else 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTile(glyph)
        Text(title, Modifier.weight(1f), style = interScaled(14.5f, InterWeight.semibold), color = AppTheme.colors.text)
        SettingsSwitch(checked, onChange, enabled)
    }
}

/** The system `Toggle` at 0.9: 51 × 31 drawn at 46 × 28, in the 51 × 44 frame iOS gives it. */
@Composable
internal fun SettingsSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean, scale: Float = 0.9f) {
    Box(Modifier.size(51.dp, 44.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        HouseSwitch(checked, onChange, Modifier.scale(scale), enabled)
    }
}

/**
 * iOS: the «Аккаунт и синхронизация» row of the settings sheet. A SettingsIconRow with the cloud in its tile (slashed
 * while sync is off), the title, the live sync marker and a chevron, 56 tall. iOS keeps AccountSettingsRow, the
 * bare 22 pt icon of the account sheet, out of this card.
 */
@Composable
internal fun SettingsAccountRow(cloudSyncEnabled: Boolean, state: SyncState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SettingsIconRow(appStrings.accountSyncTitle(), modifier, glyph = if (cloudSyncEnabled) AppGlyph.icloudFill else AppGlyph.icloudSlash, tag = "settings_account_sync", onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { SyncStatusMarker(state); SettingsRowChevron() }
    }
}
