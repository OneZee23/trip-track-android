// iOS: PersonIdentity.swift (PersonAvatar, PersonName), AvatarFrame.swift
// (AvatarFrameRing), ProfileBackground.swift (gradients and patterns), SkeletonProfileView.swift
package com.onezee.triptrack.feature.social.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.model.AvatarFrame
import com.onezee.triptrack.model.ProfileBackground
import com.onezee.triptrack.model.SocialAuthor
import kotlin.math.max
import kotlin.math.sin

/** iOS PersonAvatar: the emoji on a disc, with the PRO frame the server sends for a live subscription. */
@Composable
fun PersonAvatar(emoji: String?, frame: String?, modifier: Modifier = Modifier, size: Dp = 36.dp, fill: Color = AppTheme.colors.cardAlt) {
    Box(modifier.size(size).background(fill, CircleShape).avatarFrame(AvatarFrame.fromRawValue(frame.orEmpty()) ?: AvatarFrame.none, if (size >= 56.dp) 3.dp else 2.dp),
        contentAlignment = Alignment.Center) {
        Text(emoji ?: "🚗", style = inter(size.value * .53f))
    }
}

/**
 * iOS PersonName: the name with the PRO badge by the feed's rule (someone else's badge always
 * shows, one's own obeys the privacy switch). An unnamed account reads «Пользователь».
 */
@Composable
fun PersonName(author: SocialAuthor, style: androidx.compose.ui.text.TextStyle, isOwn: Boolean, showsOwnBadge: Boolean, modifier: Modifier = Modifier, badgeHeight: Dp = 15.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(author.displayName ?: appStrings.blockedListUser(), style = style, color = AppTheme.colors.text, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (PlusBadgeVisibility.shows(author.isPlus, isOwn, showsOwnBadge)) ProBadge(height = badgeHeight)
    }
}

/** iOS ProfileSectionLabel / AppType.section: 16 bold in ink, ordinary case. */
@Composable
fun SocialSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = AppType.section, letterSpacing = AppType.sectionTracking, color = AppTheme.colors.text)
}

/** A failed load with a retry, or an empty list: centred words under an optional glyph. */
@Composable
fun SocialMessageState(text: String, modifier: Modifier = Modifier, glyph: SocialGlyph? = null, detail: String? = null,
                       retry: (() -> Unit)? = null, tag: String = "social_state") {
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (glyph != null) SocialGlyphIcon(glyph, null, tint = colors.textTertiary, size = 26.dp)
        Text(text, style = inter(14f, InterWeight.semibold), color = colors.textSecondary, textAlign = TextAlign.Center)
        if (!detail.isNullOrEmpty()) Text(detail, style = inter(12f), color = colors.textTertiary, textAlign = TextAlign.Center)
        if (retry != null) {
            val interaction = remember { MutableInteractionSource() }
            Text(appStrings.retry(), style = inter(14f, InterWeight.semibold), color = AppTheme.accent,
                modifier = Modifier.plainPressFeedback(interaction).background(AppTheme.accent.copy(alpha = .1f), CapsuleShape)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = retry)
                    .padding(horizontal = 18.dp, vertical = 11.dp).testTag("${tag}_retry"))
        }
    }
}

/** iOS SkeletonPlaceholder(.row): grey rows pulsing while a list of people loads. */
@Composable
fun SkeletonRows(count: Int, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val pulse = if (LocalReduceMotion.current) 1f else rememberInfiniteTransition(label = "rows")
        .animateFloat(.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "rowsPulse").value
    val label = appStrings.loadingGeneric()
    Column(modifier.alpha(pulse).semantics(mergeDescendants = true) { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) {
            Row(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(11.dp), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(colors.cardAlt, CircleShape))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(130.dp).height(10.dp).background(colors.cardAlt, CapsuleShape))
                    Box(Modifier.width(60.dp).height(8.dp).background(colors.cardAlt, CapsuleShape))
                }
            }
        }
    }
}

// MARK: - Paid cosmetics, drawn for someone else's profile

/**
 * iOS ProfileBackground.gradient: every background, the eight PRO ones included. Android sells no
 * PRO, but an iPhone subscriber's profile still shows what they paid for (`showsPaidCosmetics`).
 */
fun ProfileBackground.coverColors(): List<Color> = when (this) {
    ProfileBackground.none -> emptyList()
    ProfileBackground.sunset -> listOf(Color(.98f, .55f, .24f), Color(.95f, .31f, .46f), Color(.56f, .22f, .54f))
    ProfileBackground.ocean -> listOf(Color(.25f, .55f, .85f), Color(.14f, .37f, .67f), Color(.08f, .22f, .46f))
    ProfileBackground.forest -> listOf(Color(.32f, .59f, .36f), Color(.19f, .42f, .28f), Color(.10f, .25f, .18f))
    ProfileBackground.mountain -> listOf(Color(.55f, .62f, .70f), Color(.32f, .42f, .52f), Color(.16f, .22f, .30f))
    ProfileBackground.midnight -> listOf(Color(.18f, .15f, .38f), Color(.09f, .08f, .22f), Color(.03f, .02f, .08f))
    ProfileBackground.dawn -> listOf(Color(.99f, .85f, .62f), Color(.98f, .65f, .48f), Color(.76f, .42f, .47f))
    ProfileBackground.copper -> listOf(Color(.72f, .44f, .28f), Color(.48f, .27f, .17f), Color(.26f, .13f, .08f))
    ProfileBackground.slate -> listOf(Color(.42f, .47f, .52f), Color(.27f, .31f, .36f), Color(.14f, .16f, .20f))
    ProfileBackground.aurora -> listOf(Color(.24f, .85f, .66f), Color(.32f, .44f, .82f), Color(.56f, .28f, .74f))
    ProfileBackground.sand -> listOf(Color(.95f, .86f, .70f), Color(.84f, .70f, .49f), Color(.55f, .40f, .24f))
    ProfileBackground.plusNebula -> listOf(Color(.11f, .07f, .24f), Color(.36f, .14f, .53f), Color(.12f, .32f, .62f))
    ProfileBackground.plusLava -> listOf(Color(.88f, .36f, .13f), Color(.55f, .13f, .10f), Color(.16f, .05f, .06f))
    ProfileBackground.plusGlacier -> listOf(Color(.76f, .91f, .95f), Color(.42f, .68f, .84f), Color(.16f, .34f, .54f))
    ProfileBackground.plusNeon -> listOf(Color(.08f, .06f, .16f), Color(.16f, .86f, .78f), Color(.55f, .24f, .92f))
    ProfileBackground.plusCarbon -> listOf(Color(.16f, .17f, .20f), Color(.09f, .10f, .12f), Color(.04f, .04f, .05f))
    ProfileBackground.plusGold -> listOf(Color(.99f, .87f, .53f), Color(.78f, .57f, .18f), Color(.34f, .22f, .06f))
    ProfileBackground.plusTropic -> listOf(Color(.16f, .72f, .62f), Color(.13f, .50f, .47f), Color(.96f, .76f, .35f))
    ProfileBackground.plusStorm -> listOf(Color(.15f, .18f, .24f), Color(.28f, .34f, .42f), Color(.08f, .10f, .13f))
}

/** iOS ProfileBackground.effective: an expired or absent subscription shows the ordinary card. */
fun ProfileBackground.effectiveFor(paid: Boolean): ProfileBackground = if (rawValue.startsWith("plus_") && !paid) ProfileBackground.none else this

/** iOS ProfileBackground.view(): the diagonal gradient and, on three PRO backgrounds, its pattern. */
fun DrawScope.drawCover(background: ProfileBackground) {
    val colors = background.coverColors()
    if (colors.isEmpty()) return
    drawRect(Brush.linearGradient(colors, start = Offset.Zero, end = Offset(size.width, size.height)))
    val white = Color.White
    when (background) {
        // Contours: eight sine lines with a growing phase, like a topographic map.
        ProfileBackground.plusStorm -> for (line in 0 until 8) {
            val base = size.height * (line + .6f) / 8.5f
            val amp = size.height * .055f
            val path = Path().apply {
                moveTo(0f, base)
                var x = 0f
                while (x <= size.width) { lineTo(x, base + sin(x / max(size.width, 1f) * 6.2f + line * .8f) * amp); x += 4f }
            }
            drawPath(path, white.copy(alpha = .16f), style = Stroke(1.dp.toPx()))
        }
        // A grid of meridians and parallels: the atlas.
        ProfileBackground.plusCarbon -> {
            val step = max(size.width, size.height) / 9
            var x = step
            while (x < size.width) { drawLine(white.copy(alpha = .13f), Offset(x, 0f), Offset(x, size.height), .8.dp.toPx()); x += step }
            var y = step
            while (y < size.height) { drawLine(white.copy(alpha = .13f), Offset(0f, y), Offset(size.width, y), .8.dp.toPx()); y += step }
        }
        // Stars on a fixed jittered grid: no randomness, the tile and the banner must match.
        ProfileBackground.plusNebula -> for (row in 0 until 6) for (column in 0 until 9) {
            val jitter = ((row * 7 + column * 3) % 5) / 5f
            val x = (column + .3f + jitter * .5f) * size.width / 9
            val y = (row + .3f + jitter * .4f) * size.height / 6
            drawCircle(white.copy(alpha = .35f - jitter * .15f), (.7f + jitter * 1.1f).dp.toPx(), Offset(x, y))
        }
        else -> {}
    }
}

/** iOS AvatarFrame.colors: the angular gradients of the six PRO rings. */
fun AvatarFrame.ringColors(): List<Color> = when (this) {
    AvatarFrame.none -> emptyList()
    AvatarFrame.gold -> listOf(Color(.98f, .84f, .45f), Color(.76f, .55f, .16f), Color(1f, .93f, .70f), Color(.76f, .55f, .16f))
    AvatarFrame.chrome -> listOf(Color(.92f, .94f, .96f), Color(.55f, .59f, .64f), Color(.98f, .99f, 1f), Color(.45f, .49f, .55f))
    AvatarFrame.carbon -> listOf(Color(.22f, .23f, .26f), Color(.08f, .08f, .10f), Color(.34f, .36f, .40f), Color(.08f, .08f, .10f))
    AvatarFrame.neon -> listOf(Color(.16f, .95f, .79f), Color(.24f, .55f, .98f), Color(.16f, .95f, .79f))
    AvatarFrame.laurel -> listOf(Color(.55f, .72f, .42f), Color(.24f, .44f, .26f), Color(.72f, .84f, .55f), Color(.24f, .44f, .26f))
    AvatarFrame.flame -> listOf(Color(.99f, .76f, .28f), Color(.96f, .36f, .36f), Color(.99f, .76f, .28f))
}

/**
 * iOS avatarFrame(_:lineWidth:): the ring over a round avatar, with a hairline inward so a chrome
 * ring does not dissolve in a light avatar. [AvatarFrame.none] draws nothing.
 */
fun Modifier.avatarFrame(frame: AvatarFrame, lineWidth: Dp): Modifier {
    val colors = frame.ringColors()
    if (colors.isEmpty()) return this
    return drawWithContent {
        drawContent()
        val width = lineWidth.toPx()
        val hairline = width + 1.dp.toPx()
        drawCircle(Color.Black.copy(alpha = .10f), radius = size.minDimension / 2 - hairline / 2, style = Stroke(hairline))
        drawCircle(Brush.sweepGradient(colors + colors.first()), radius = size.minDimension / 2 - width / 2, style = Stroke(width))
    }
}

// MARK: - Glyphs

/**
 * The SF Symbols of the social screens the house set has no drawing for, on the same 24-unit grid
 * and semibold weight as the design system's glyphs (see WorldGlyph for the pattern).
 */
enum class SocialGlyph { bell, eyeFill, link, personPlus, personTwo, rosette, starFill, paperplane, arrowUp, handRaised, handRaisedSlash, squareGrid, listBullet, wifiExclamation, replyArrow, chart }

@Composable
fun SocialGlyphIcon(glyph: SocialGlyph, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = 22.dp) {
    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.size(size).then(semantics)) {
        scale(this.size.minDimension / 24f, pivot = Offset.Zero) { drawSocialGlyph(glyph, tint) }
    }
}

private fun line(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun path(vararg points: Float, close: Boolean = false): Path = Path().apply {
    moveTo(points[0], points[1])
    var i = 2
    while (i < points.size) { lineTo(points[i], points[i + 1]); i += 2 }
    if (close) close()
}

private fun DrawScope.drawSocialGlyph(glyph: SocialGlyph, tint: Color) {
    val w = 1.9f
    when (glyph) {
        SocialGlyph.bell -> {
            val body = Path().apply {
                moveTo(5.4f, 16.6f); lineTo(6.8f, 14.6f); lineTo(6.8f, 10.4f)
                cubicTo(6.8f, 7.4f, 9.0f, 5.0f, 12f, 5.0f); cubicTo(15.0f, 5.0f, 17.2f, 7.4f, 17.2f, 10.4f)
                lineTo(17.2f, 14.6f); lineTo(18.6f, 16.6f); close()
            }
            drawPath(body, tint, style = line(w))
            drawLine(tint, Offset(10.2f, 19.4f), Offset(13.8f, 19.4f), 2f, StrokeCap.Round)
            drawLine(tint, Offset(12f, 3.2f), Offset(12f, 5f), 2f, StrokeCap.Round)
        }
        SocialGlyph.eyeFill -> {
            val eye = Path().apply { moveTo(2.6f, 12f); cubicTo(5.2f, 7f, 8.4f, 5.4f, 12f, 5.4f); cubicTo(15.6f, 5.4f, 18.8f, 7f, 21.4f, 12f)
                cubicTo(18.8f, 17f, 15.6f, 18.6f, 12f, 18.6f); cubicTo(8.4f, 18.6f, 5.2f, 17f, 2.6f, 12f); close() }
            drawPath(eye, tint, style = Fill)
            drawCircle(Color.Black, 3.6f, Offset(12f, 12f), blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
            drawCircle(tint, 2f, Offset(12f, 12f))
        }
        SocialGlyph.link -> {
            drawRoundRect(tint, Offset(3.2f, 9.2f), Size(10f, 5.6f), androidx.compose.ui.geometry.CornerRadius(2.8f), style = line(w))
            drawRoundRect(tint, Offset(10.8f, 9.2f), Size(10f, 5.6f), androidx.compose.ui.geometry.CornerRadius(2.8f), style = line(w))
        }
        SocialGlyph.personPlus -> {
            drawCircle(tint, 3.4f, Offset(9.6f, 8f), style = line(w))
            drawPath(path(3.4f, 19.6f, 4.2f, 15.8f, 9.6f, 13.8f, 15f, 15.8f, 15.8f, 19.6f), tint, style = line(w))
            drawLine(tint, Offset(19f, 7.2f), Offset(19f, 12.8f), 2f, StrokeCap.Round)
            drawLine(tint, Offset(16.2f, 10f), Offset(21.8f, 10f), 2f, StrokeCap.Round)
        }
        SocialGlyph.personTwo -> {
            drawCircle(tint, 3f, Offset(8.6f, 8.4f), style = line(w))
            drawPath(path(3f, 19f, 3.8f, 15.6f, 8.6f, 13.8f, 13.4f, 15.6f, 14.2f, 19f), tint, style = line(w))
            drawCircle(tint, 2.6f, Offset(16.2f, 7.6f), style = line(1.7f))
            drawPath(path(16.4f, 12.6f, 19.8f, 13.8f, 21f, 17.6f), tint, style = line(1.7f))
        }
        SocialGlyph.rosette -> {
            drawCircle(tint, 5.4f, Offset(12f, 9.6f), style = line(w))
            drawPath(path(8.6f, 14f, 7.4f, 20.6f, 12f, 18.4f, 16.6f, 20.6f, 15.4f, 14f), tint, style = line(w))
        }
        SocialGlyph.starFill -> {
            val star = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 9.4f else 4f
                val a = Math.toRadians(-90.0 + i * 36.0)
                val x = 12f + r * kotlin.math.cos(a).toFloat()
                val y = 12.6f + r * kotlin.math.sin(a).toFloat()
                if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            drawPath(star, tint, style = Fill)
        }
        SocialGlyph.paperplane -> {
            drawPath(path(3.2f, 11.4f, 20.6f, 3.6f, 14.4f, 20.6f, 11f, 13.2f, close = true), tint, style = Fill)
        }
        SocialGlyph.arrowUp -> {
            drawLine(tint, Offset(12f, 19.4f), Offset(12f, 5.2f), 2.4f, StrokeCap.Round)
            drawPath(path(6.2f, 10.6f, 12f, 4.8f, 17.8f, 10.6f), tint, style = line(2.4f))
        }
        SocialGlyph.handRaised, SocialGlyph.handRaisedSlash -> {
            val hand = Path().apply {
                moveTo(7.2f, 12.6f); lineTo(7.2f, 6.4f); moveTo(10f, 11.4f); lineTo(10f, 4.6f); moveTo(12.8f, 11.4f); lineTo(12.8f, 5.2f)
                moveTo(15.6f, 12.2f); lineTo(15.6f, 7.4f)
            }
            drawPath(hand, tint, style = line(w))
            drawPath(path(7.2f, 12.6f, 5.4f, 11.2f, 4.6f, 12.6f, 8.4f, 18.6f, 11.6f, 20.2f, 15.6f, 18.4f, 15.6f, 12.2f), tint, style = line(w))
            if (glyph == SocialGlyph.handRaisedSlash) drawLine(tint, Offset(3.6f, 3.6f), Offset(20.4f, 20.4f), 2f, StrokeCap.Round)
        }
        SocialGlyph.squareGrid -> for (dx in listOf(4f, 13.2f)) for (dy in listOf(4f, 13.2f)) {
            drawRoundRect(tint, Offset(dx, dy), Size(6.8f, 6.8f), androidx.compose.ui.geometry.CornerRadius(1.6f), style = line(w))
        }
        SocialGlyph.listBullet -> for (y in listOf(6f, 12f, 18f)) {
            drawCircle(tint, 1.4f, Offset(5f, y))
            drawLine(tint, Offset(9f, y), Offset(20f, y), 2f, StrokeCap.Round)
        }
        SocialGlyph.wifiExclamation -> {
            drawArc(tint, 205f, 130f, false, Offset(3.6f, 6f), Size(16.8f, 16.8f), style = line(w))
            drawArc(tint, 210f, 120f, false, Offset(7.2f, 9.6f), Size(9.6f, 9.6f), style = line(w))
            drawLine(tint, Offset(19.8f, 13.4f), Offset(19.8f, 17.6f), 2f, StrokeCap.Round)
            drawCircle(tint, 1.1f, Offset(19.8f, 20.4f))
        }
        SocialGlyph.replyArrow -> {
            drawPath(path(9.6f, 5.4f, 3.6f, 11f, 9.6f, 16.6f, close = true), tint, style = Fill)
            val tail = Path().apply { moveTo(9f, 11f); cubicTo(15f, 11f, 19.6f, 12.6f, 20.4f, 18.6f) }
            drawPath(tail, tint, style = line(2.4f))
        }
        SocialGlyph.chart -> {
            drawRoundRect(tint, Offset(4f, 12f), Size(3.6f, 8f), androidx.compose.ui.geometry.CornerRadius(1f), style = Fill)
            drawRoundRect(tint, Offset(10.2f, 7f), Size(3.6f, 13f), androidx.compose.ui.geometry.CornerRadius(1f), style = Fill)
            drawRoundRect(tint, Offset(16.4f, 3.8f), Size(3.6f, 16.2f), androidx.compose.ui.geometry.CornerRadius(1f), style = Fill)
        }
    }
}
