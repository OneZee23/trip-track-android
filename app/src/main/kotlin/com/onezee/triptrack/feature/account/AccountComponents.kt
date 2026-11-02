// iOS: AccountSyncComponents.swift (AccountSectionLabel, AccountSettingsRow), the SF Symbols of
// CloudSyncView / SyncStatusSheetView / ProfileView's guest card, SwiftUI Toggle and ProgressView
package com.onezee.triptrack.feature.account

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.rememberHaptics

/** The SF Symbols these screens use, redrawn on the 24-unit grid of `AppGlyph`. */
enum class AccountGlyph {
    globe, syncPath, lockFill, noSign, arrowDownToLine, warning, flag, flagFill, camera, person, car, carCircleFill,
    clock, arrowClockwise, cloudFill, personExclamation, photoFill, mapFill, sealFill, gearFill,
}

/** iOS `Image(systemName:)` for an [AccountGlyph]; decoration, so hidden from TalkBack. */
@Composable
fun AccountIcon(glyph: AccountGlyph, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val sharedGlyph = when (glyph) {
        AccountGlyph.globe -> AppGlyph.globe
        AccountGlyph.lockFill -> AppGlyph.lockFill
        AccountGlyph.flag -> AppGlyph.flag
        AccountGlyph.flagFill -> AppGlyph.flagFill
        AccountGlyph.camera -> AppGlyph.camera
        AccountGlyph.car -> AppGlyph.car
        AccountGlyph.clock -> AppGlyph.clock
        AccountGlyph.arrowClockwise -> AppGlyph.arrowClockwise
        AccountGlyph.gearFill -> AppGlyph.gearshapeFill
        else -> null
    }
    if (sharedGlyph != null) {
        Glyph(sharedGlyph, contentDescription = null, modifier = modifier, tint = tint, size = size)
    } else {
        Canvas(modifier.size(size)) {
            scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) { drawAccountGlyph(glyph, tint) }
        }
    }
}

private fun line(width: Float = 1.7f) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.drawAccountGlyph(glyph: AccountGlyph, tint: Color) {
    val c = Offset(12f, 12f)
    when (glyph) {
        AccountGlyph.globe -> {
            drawCircle(tint, 9f, c, style = line())
            drawOval(tint, Offset(8f, 3f), Size(8f, 18f), style = line())
            drawLine(tint, Offset(3f, 12f), Offset(21f, 12f), 1.7f)
            drawLine(tint, Offset(5f, 7.5f), Offset(19f, 7.5f), 1.4f)
            drawLine(tint, Offset(5f, 16.5f), Offset(19f, 16.5f), 1.4f)
        }
        AccountGlyph.syncPath -> {
            drawCircle(tint, 2.6f, Offset(5.5f, 5.5f), style = line())
            drawCircle(tint, 2.6f, Offset(18.5f, 18.5f), style = line())
            val path = Path().apply {
                moveTo(5.5f, 8.1f); cubicTo(5.5f, 16f, 18.5f, 8f, 18.5f, 15.9f)
            }
            drawPath(path, tint, style = line())
        }
        AccountGlyph.lockFill -> {
            drawRoundRect(tint, Offset(5f, 10.5f), Size(14f, 10.5f), CornerRadius(2.4f))
            drawArc(tint, 180f, 180f, false, Offset(8f, 4f), Size(8f, 9f), style = line(2f))
            drawLine(tint, Offset(8f, 8.5f), Offset(8f, 10.8f), 2f)
            drawLine(tint, Offset(16f, 8.5f), Offset(16f, 10.8f), 2f)
        }
        AccountGlyph.noSign -> {
            drawCircle(tint, 8.8f, c, style = line(1.9f))
            drawLine(tint, Offset(5.8f, 5.8f), Offset(18.2f, 18.2f), 1.9f)
        }
        AccountGlyph.arrowDownToLine -> {
            drawLine(tint, Offset(12f, 3.5f), Offset(12f, 15f), 1.8f, StrokeCap.Round)
            drawPath(Path().apply { moveTo(7.5f, 10.8f); lineTo(12f, 15.3f); lineTo(16.5f, 10.8f) }, tint, style = line(1.8f))
            drawLine(tint, Offset(5f, 20f), Offset(19f, 20f), 1.8f, StrokeCap.Round)
        }
        AccountGlyph.warning -> {
            drawPath(Path().apply { moveTo(12f, 3.5f); lineTo(21.5f, 20f); lineTo(2.5f, 20f); close() }, tint, style = line(1.7f))
            drawLine(tint, Offset(12f, 9.5f), Offset(12f, 14.3f), 1.8f, StrokeCap.Round)
            drawCircle(tint, 1.05f, Offset(12f, 17.1f))
        }
        AccountGlyph.flag, AccountGlyph.flagFill -> {
            drawLine(tint, Offset(5.5f, 3.5f), Offset(5.5f, 21f), 1.8f, StrokeCap.Round)
            val cloth = Path().apply {
                moveTo(5.5f, 4.5f); cubicTo(9f, 3f, 12f, 6f, 18.5f, 4.5f); lineTo(18.5f, 13f)
                cubicTo(12f, 14.5f, 9f, 11.5f, 5.5f, 13f); close()
            }
            if (glyph == AccountGlyph.flagFill) drawPath(cloth, tint) else drawPath(cloth, tint, style = line())
        }
        AccountGlyph.camera -> {
            drawRoundRect(tint, Offset(3f, 7f), Size(18f, 13f), CornerRadius(2.6f), style = line())
            drawPath(Path().apply { moveTo(8f, 7f); lineTo(9.6f, 4.5f); lineTo(14.4f, 4.5f); lineTo(16f, 7f) }, tint, style = line())
            drawCircle(tint, 3.6f, Offset(12f, 13.4f), style = line())
        }
        AccountGlyph.person -> {
            drawCircle(tint, 3.8f, Offset(12f, 7.8f), style = line())
            drawPath(Path().apply { moveTo(4.5f, 20.5f); cubicTo(5f, 15f, 19f, 15f, 19.5f, 20.5f) }, tint, style = line())
        }
        AccountGlyph.car, AccountGlyph.carCircleFill -> {
            val ink = if (glyph == AccountGlyph.carCircleFill) Color.White else tint
            if (glyph == AccountGlyph.carCircleFill) {
                drawCircle(tint, 11f, c)
                scale(0.62f, pivot = c) { drawCar(ink) }
            } else drawCar(ink)
        }
        AccountGlyph.clock -> {
            drawCircle(tint, 8.8f, c, style = line())
            drawPath(Path().apply { moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f) }, tint, style = line())
        }
        AccountGlyph.arrowClockwise -> {
            drawArc(tint, -60f, 300f, false, Offset(4.5f, 4.5f), Size(15f, 15f), style = line(2.1f))
            drawPath(Path().apply { moveTo(15.5f, 2.8f); lineTo(19.6f, 5.6f); lineTo(15.4f, 8.6f) }, tint, style = line(2.1f))
        }
        AccountGlyph.cloudFill -> drawPath(Path().apply {
            moveTo(7f, 19f)
            cubicTo(3.6f, 19f, 2.5f, 15.8f, 3.4f, 13.9f)
            cubicTo(4.2f, 12.2f, 5.9f, 11.6f, 7.1f, 11.8f)
            cubicTo(7.6f, 8.2f, 10.2f, 6f, 13.2f, 6f)
            cubicTo(16.6f, 6f, 18.8f, 8.7f, 18.9f, 11.7f)
            cubicTo(21f, 12f, 22f, 13.9f, 21.8f, 15.6f)
            cubicTo(21.6f, 17.6f, 20f, 19f, 17.8f, 19f)
            close()
        }, tint)
        AccountGlyph.personExclamation -> {
            drawCircle(tint, 3.5f, Offset(9.5f, 8f), style = line())
            drawPath(Path().apply { moveTo(2.5f, 20.5f); cubicTo(3f, 15.2f, 14f, 14.8f, 15.5f, 18.5f) }, tint, style = line())
            drawCircle(tint, 4.6f, Offset(18.2f, 16.6f))
            drawLine(Color.White, Offset(18.2f, 14.2f), Offset(18.2f, 17f), 1.5f, StrokeCap.Round)
            drawCircle(Color.White, 0.8f, Offset(18.2f, 18.9f))
        }
        AccountGlyph.photoFill -> {
            drawRoundRect(tint, Offset(3f, 5f), Size(18f, 14f), CornerRadius(2.6f))
            drawPath(Path().apply { moveTo(5f, 17f); lineTo(10f, 11f); lineTo(13.5f, 14.6f); lineTo(15.5f, 12.6f); lineTo(19f, 17f); close() }, Color.White)
            drawCircle(Color.White, 1.5f, Offset(15.5f, 8.8f))
        }
        AccountGlyph.mapFill -> drawPath(Path().apply {
            moveTo(3f, 6f); lineTo(9f, 4f); lineTo(15f, 6f); lineTo(21f, 4f); lineTo(21f, 18f); lineTo(15f, 20f)
            lineTo(9f, 18f); lineTo(3f, 20f); close()
        }, tint)
        AccountGlyph.sealFill -> {
            for (i in 0 until 12) rotate(i * 30f, pivot = c) { drawCircle(tint, 2.4f, Offset(12f, 3.6f)) }
            drawCircle(tint, 8.2f, c)
        }
        AccountGlyph.gearFill -> {
            drawCircle(tint, 6.6f, c)
            for (i in 0 until 8) rotate(i * 45f, pivot = c) { drawRoundRect(tint, Offset(10.4f, 2.6f), Size(3.2f, 4.6f), CornerRadius(0.8f)) }
            drawCircle(Color.White, 2.6f, c)
        }
    }
}

private fun DrawScope.drawCar(ink: Color) {
    val body = Path().apply {
        moveTo(3f, 15.5f); lineTo(3f, 12.6f); lineTo(5.4f, 7.4f); cubicTo(5.8f, 6.6f, 6.5f, 6.2f, 7.4f, 6.2f)
        lineTo(16.6f, 6.2f); cubicTo(17.5f, 6.2f, 18.2f, 6.6f, 18.6f, 7.4f); lineTo(21f, 12.6f); lineTo(21f, 15.5f); close()
    }
    drawPath(body, ink, style = line())
    drawLine(ink, Offset(3.5f, 12.4f), Offset(20.5f, 12.4f), 1.5f)
    drawCircle(ink, 1.9f, Offset(7.2f, 17.4f))
    drawCircle(ink, 1.9f, Offset(16.8f, 17.4f))
}

/** iOS: AccountSectionLabel = ProfileSectionLabel since 0.8.9: the house section heading. */
@Composable
fun AccountSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppType.section,
        letterSpacing = AppType.sectionTracking,
        color = AppTheme.colors.text,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * iOS: AccountSettingsRow. px14 py13, a 17 pt icon in a fixed 22 pt slot (titles stay aligned
 * across glyphs of different widths), a 15 medium title, a wrapping 11.5 subtitle, any trailing
 * content, and an optional full-bleed hairline under it.
 */
@Composable
fun AccountSettingsRow(
    icon: AccountGlyph,
    title: String,
    modifier: Modifier = Modifier,
    iconColor: Color? = null,
    titleColor: Color? = null,
    subtitle: String? = null,
    showsDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier
                            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                                haptics.tap()
                                onClick()
                            }
                            .plainPressFeedback(interaction)
                    } else Modifier,
                )
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                AccountIcon(icon, iconColor ?: colors.textSecondary, size = 22.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = inter(15f, InterWeight.medium), color = titleColor ?: colors.text)
                if (subtitle != null) Text(subtitle, style = inter(11.5f), color = colors.textTertiary)
            }
            trailing()
        }
        if (showsDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

/**
 * iOS: SwiftUI `Toggle` tinted with the accent, the switch every iOS settings row wears: 51 × 31,
 * a white knob on a spring. A switch to TalkBack, with its title as the label.
 */
@Composable
fun HouseSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = AppTheme.colors
    val track by animateColorAsState(
        if (checked) AppTheme.accent else if (AppTheme.isDark) Color.White.copy(alpha = 0.16f) else Color(0xFFE9E9EB),
        tween(200), label = "switchTrack",
    )
    val travel by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 700f), label = "switchKnob")
    Box(
        modifier
            .size(width = 51.dp, height = 31.dp)
            .background(track, CircleShape)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, interactionSource = remember { MutableInteractionSource() },
                indication = null, onValueChange = onCheckedChange),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { IntOffset((2.dp + 20.dp * travel).roundToPx(), 0) }
                .size(27.dp)
                .background(if (enabled) Color.White else colors.cardAlt, CircleShape),
        )
    }
}

/** iOS: ProgressView(), the small spinning arc. */
@Composable
fun Spinner(tint: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    Canvas(modifier.size(size)) {
        rotate(angle) {
            val inset = this.size.minDimension * 0.12f
            drawArc(tint, 0f, 270f, false, Offset(inset, inset), Size(this.size.width - 2 * inset, this.size.height - 2 * inset),
                style = Stroke(width = this.size.minDimension * 0.13f, cap = StrokeCap.Round))
        }
    }
}

/**
 * iOS `Text(.init(markdown))` for the copy that carries links: `[word](url)` becomes a tappable
 * span in [linkColor]; the rest is plain text.
 */
@Composable
fun linkedText(markdown: String, linkColor: Color): AnnotatedString =
    remember(markdown, linkColor) {
        buildAnnotatedString {
            val pattern = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
            var cursor = 0
            for (match in pattern.findAll(markdown)) {
                append(markdown.substring(cursor, match.range.first))
                val (word, url) = match.destructured
                withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor)))) { append(word) }
                cursor = match.range.last + 1
            }
            append(markdown.substring(cursor))
        }
    }

/** The grabber the house sheets draw (34 × 5 at 18 %). */
@Composable
fun SheetGrabber(modifier: Modifier = Modifier) {
    val ink = if (AppTheme.isDark) Color.White else Color.Black
    Box(modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 34.dp, height = 5.dp).background(ink.copy(alpha = 0.18f), CircleShape))
    }
}

/** A test tag that is also the iOS accessibility identifier it mirrors. */
fun Modifier.iosIdentifier(identifier: String): Modifier = testTag(identifier)
