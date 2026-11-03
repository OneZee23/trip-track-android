// iOS: ProfileComponents.swift, GarageComponents.swift, SettingsOptionPicker.swift
package com.onezee.triptrack.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*

// iOS: GarageView.navRow / NavCircleIcon. Keep the house circle while drawing the missing plus glyph locally.
@Composable
internal fun MeNavAddButton(description: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val ink = AppTheme.colors.text.copy(alpha = if (enabled) 1f else .35f)
    Box(Modifier.size(NavCircleDiameter).iosShadow(CircleShape, Color.Black.copy(alpha = if (AppTheme.isDark) .4f else .07f), radius = 5.dp, y = 2.dp)
        .background(AppTheme.colors.card, CircleShape).plainPressFeedback(interaction)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }.testTag("garage_add"), contentAlignment = Alignment.Center) {
        Glyph(AppGlyph.plus, contentDescription = null, tint = ink, size = 22.dp)
    }
}

@Composable
internal fun MeSheetHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = interScaled(22f, InterWeight.bold), color = AppTheme.colors.text)
        SheetCloseCircle(onClick = onClose)
    }
}

@Composable
internal fun MeRow(title: String, value: String = "", subtitle: String? = null, tag: String = "",
                   glyph: AppGlyph? = null, destructive: Boolean = false, enabled: Boolean = true,
                   @androidx.annotation.DrawableRes icon: Int? = null,
                   onClick: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().testTag(tag)
        .then(if (onClick != null) Modifier.plainPressFeedback(interaction).clickable(
            interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
        .padding(horizontal = 14.dp, vertical = 13.dp).heightIn(min = 30.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (glyph != null || icon != null) Box(Modifier.size(30.dp).background(AppTheme.accentBg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            // iOS SettingsLinkRow.assetIcon: a brand mark from the asset catalog, drawn as a 16 pt template.
            if (icon != null) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(icon), null, Modifier.size(16.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(AppTheme.accent))
            else if (glyph != null) Glyph(glyph, null, tint = AppTheme.accent, size = 19.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = interScaled(14.5f, InterWeight.semibold),
                color = if (destructive) AppTheme.red else AppTheme.colors.text.copy(alpha = if (enabled) 1f else 0.5f))
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = interScaled(11.5f), color = AppTheme.colors.textSecondary)
        }
        if (value.isNotEmpty()) Text(value, Modifier.widthIn(max = 150.dp), style = interScaled(14f), color = AppTheme.colors.textSecondary, maxLines = 2)
        if (onClick != null) Glyph(AppGlyph.chevronRight, null, tint = AppTheme.colors.textTertiary, size = 14.dp)
    }
}

@Composable
internal fun MeToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null,
                      enabled: Boolean = true, tag: String = "") {
    Row(Modifier.fillMaxWidth().testTag(tag).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = 14.dp, vertical = 10.dp).heightIn(min = 38.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = interScaled(14.5f, InterWeight.semibold), color = AppTheme.colors.text.copy(alpha = if (enabled) 1f else .5f))
            if (subtitle != null) Text(subtitle, style = interScaled(11.5f), color = AppTheme.colors.textSecondary)
        }
        Box(Modifier.size(46.dp, 28.dp).background(if (checked) AppTheme.accent else AppTheme.colors.cardAlt, CircleShape)
            .border(1.dp, AppTheme.colors.borderBright, CircleShape), contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.padding(3.dp).size(22.dp).background(Color.White, CircleShape))
        }
    }
}

@Composable
internal fun MeField(title: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
                     keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, tag: String = "") {
    Column(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = interScaled(11.5f, InterWeight.medium), color = AppTheme.colors.textSecondary)
        BasicTextField(value, onChange, Modifier.fillMaxWidth().heightIn(min = 30.dp).testTag(tag),
            textStyle = interScaled(16f).copy(color = AppTheme.colors.text), singleLine = singleLine,
            cursorBrush = SolidColor(AppTheme.accent), keyboardOptions = KeyboardOptions(keyboardType = keyboard))
    }
}

@Composable
internal fun MeAction(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false) {
    Box(modifier.fillMaxWidth().background(
        (if (destructive) AppTheme.red else AppTheme.accent).copy(alpha = if (enabled) 1f else .35f), RoundedCornerShape(14.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp), contentAlignment = Alignment.Center) {
        Text(title, style = interScaled(16f, InterWeight.semibold), color = Color.White)
    }
}

@Composable
internal fun MeSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 2.dp, top = 8.dp), style = interScaled(13f, InterWeight.semibold), color = AppTheme.colors.textSecondary)
        Column(Modifier.fillMaxWidth().surfaceCard(16.dp), content = content)
    }
}

@Composable
internal fun MeDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.borderBright))

// iOS: @StateObject. A pushed screen owns its tasks; popping it cancels them.
@Composable
internal inline fun <reified T : ViewModel> rememberMeModel(key: Any = Unit, crossinline create: () -> T): T {
    val store = remember(key) { ViewModelStore() }
    DisposableEffect(store) { onDispose { store.clear() } }
    return remember(store) { ViewModelProvider(store, object : ViewModelProvider.Factory {
        override fun <V : ViewModel> create(modelClass: Class<V>): V = modelClass.cast(create())!!
    })[T::class.java] }
}
