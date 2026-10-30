package com.onezee.triptrack.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.theme.AppRadius
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.interScaled
import com.onezee.triptrack.designsystem.theme.points
import com.onezee.triptrack.designsystem.theme.rememberHaptics

/**
 * iOS `DynamicTypeSize.isAccessibilitySize`, the five largest iOS sizes. The first of them is
 * 1.65 × the default body; Android's font scale steps are 1.3, 1.5, 1.8 and 2.0, and 1.5 is the
 * first one past iOS's largest standard size (1.35 ×).
 */
@Composable
fun isAccessibilityFontScale(): Boolean = LocalDensity.current.fontScale >= 1.5f

/**
 * iOS: SettingsOptionPicker.swift. The settings picker sheet, worn by «Единицы», «Язык» and «Тема»
 * (Figma 1685:119 / 176 / 233): the same component with different rows. It reports a pick and
 * closes; which store the pick lands in is the caller's business.
 *
 * The leading badge is a 2–3 character token («км», «Ру»), or [badgeGlyph] where iOS names an SF
 * Symbol, or a colour disc from [badgeTint] where the answer IS a colour (route line); a `null`
 * tint means «no single colour» and falls back to the token.
 *
 * Long lists (more than eight) and accessibility font sizes get a full-height sheet that scrolls,
 * with the close control kept in view; short pickers stay sized to their content.
 */
@Composable
fun <T> SettingsOptionPicker(
    title: String,
    options: List<T>,
    selection: T,
    footnote: String,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
    badge: (T) -> String = { "" },
    badgeGlyph: ((T) -> AppGlyph)? = null,
    badgeTint: ((T) -> Color?)? = null,
    subtitle: ((T) -> String?)? = null,
    testTagPrefix: String = "settings_option",
) {
    val colors = AppTheme.colors
    val accessibility = isAccessibilityFontScale()
    val needsScroll = accessibility || options.size > 8
    ContentSizedSheet(
        onDismissRequest = onDismissRequest,
        background = colors.card,
        detent = if (needsScroll) SheetDetent.large else SheetDetent.content,
        contentClearsNavigationBar = !needsScroll,
    ) {
        val sheet = this
        Grabber()
        PickerHeader(title, testTagPrefix, onClose = { sheet.dismiss() })
        val choices: @Composable ColumnScope.() -> Unit = {
            Column(
                Modifier
                    .padding(horizontal = 8.dp)
                    .padding(top = if (accessibility) 6.dp else 0.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(if (accessibility) 12.dp else 4.dp),
            ) {
                options.forEachIndexed { index, option ->
                    // Canon draws a hairline only where two plain rows meet: the selected row's
                    // warm fill already parts it from its neighbours.
                    val showsDivider = index > 0 && option != selection && options[index - 1] != selection
                    OptionRow(
                        label = label(option),
                        subtitle = subtitle?.invoke(option),
                        isSelected = option == selection,
                        showsDivider = showsDivider,
                        accessibility = accessibility,
                        testTag = "${testTagPrefix}_row_$index",
                        badge = { selected -> OptionBadge(badge(option), badgeGlyph?.invoke(option), badgeTint?.invoke(option), selected) },
                        onClick = {
                            onSelect(option)
                            sheet.dismiss()
                        },
                    )
                }
            }
            Text(
                footnote,
                style = interScaled(11.5f),
                color = colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp),
            )
        }
        if (needsScroll) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = choices)
        } else {
            Column(content = choices)
        }
    }
}

@Composable
private fun Grabber() {
    val ink = if (AppTheme.isDark) Color.White else Color.Black
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 34.dp, height = 5.dp).background(ink.copy(alpha = 0.18f), CapsuleShape))
    }
}

@Composable
private fun PickerHeader(title: String, testTagPrefix: String, onClose: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = interScaled(16f, InterWeight.bold),
            color = colors.text,
            letterSpacing = points(-0.16f),
            modifier = Modifier.weight(1f),
        )
        SheetCloseCircle(onClick = onClose, modifier = Modifier.testTag("${testTagPrefix}_close"))
    }
}

/**
 * iOS: GarageComponents.swift, SheetCloseCircle. The «×» in a sheet header: a small grey disc, not
 * the nav bar's white control, which is far too loud for a header whose job is to name the sheet.
 */
@Composable
fun SheetCloseCircle(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(30.dp)
            .plainPressFeedback(interaction)
            .background(colors.cardAlt, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = null) {
                haptics.tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        // iOS: a 13 pt semibold xmark.
        Glyph(AppGlyph.xmark, contentDescription = appStrings.close(), tint = colors.textSecondary, size = 20.dp)
    }
}

@Composable
private fun OptionRow(
    label: String,
    subtitle: String?,
    isSelected: Boolean,
    showsDivider: Boolean,
    accessibility: Boolean,
    testTag: String,
    badge: @Composable (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val fill = if (isSelected) AppTheme.accentBg else Color.Transparent
    val rule = colors.borderBright
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                // The fill reaches 6 dp past the content, so the pill reads taller than its text.
                val bleed = 6.dp.toPx()
                drawRoundRect(fill, Offset(0f, -bleed), Size(size.width, size.height + 2 * bleed), CornerRadius(AppRadius.pickerRow.toPx()))
                if (showsDivider) {
                    // Past the badge column, centred in the 4 dp gap above the row.
                    val y = -2.dp.toPx()
                    drawRect(rule, Offset(56.dp.toPx(), y - 0.5.dp.toPx()), Size(size.width - 64.dp.toPx(), 1.dp.toPx()))
                }
            }
            .selectable(
                selected = isSelected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
            ) {
                haptics.tap()
                onClick()
            }
            .plainPressFeedback(interaction)
            .heightIn(min = 44.dp)
            .padding(vertical = if (accessibility) 6.dp else 0.dp)
            .padding(start = 8.dp, end = 16.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        badge(isSelected)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = interScaled(14.5f, if (isSelected) InterWeight.semibold else InterWeight.medium), color = colors.text)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = interScaled(11.5f), color = colors.textSecondary)
            }
        }
        // Kept in the layout at zero opacity, so moving the pick never shifts a label.
        Glyph(
            AppGlyph.checkmark,
            contentDescription = null,
            tint = AppTheme.accent.copy(alpha = if (isSelected) 1f else 0f),
            size = 20.dp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun OptionBadge(token: String, glyph: AppGlyph?, tint: Color?, isSelected: Boolean) {
    val colors = AppTheme.colors
    val ink = if (isSelected) AppTheme.accent else colors.text
    Box(
        Modifier
            .size(32.dp)
            .background(if (isSelected) AppTheme.accentDim else colors.cardAlt, RoundedCornerShape(AppRadius.pickerBadge)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            tint != null -> Box(
                Modifier
                    .size(18.dp)
                    .background(tint, CircleShape)
                    .border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape),
            )
            // iOS: a 13 pt semibold symbol.
            glyph != null -> Glyph(glyph, contentDescription = null, tint = ink, size = 16.dp)
            else -> Text(
                token,
                style = inter(12f, InterWeight.semibold),
                color = ink,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = points(12f * 0.7f), maxFontSize = points(12f)),
                textAlign = TextAlign.Center,
            )
        }
    }
}
