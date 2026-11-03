package com.onezee.triptrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.uppercased
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.interScaled
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.points
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.logic.JourneyWindow

/**
 * iOS: MapChromeButton.swift. A control floating over a map: a 36 circle in a 44 target whose
 * ground cross-fades from dark glass (over the hero) to the solid card-alt (once the bar has
 * filled in), the glyph from white to ink at the half-way point. [progress] is the bar's fill.
 */
@Composable
fun MapChromeButton(
    glyph: AppGlyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(44.dp)
            .plainPressFeedback(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        MapChromeSurface(progress, Modifier.size(36.dp), CircleShape)
        Glyph(glyph, contentDescription = null, tint = chromeInk(progress), size = 18.dp)
    }
}

/** The ink of a map control: white over the glass, the theme's ink once the bar is solid. */
@Composable
fun chromeInk(progress: Float): Color = if (progress <= 0.5f) Color.White else AppTheme.colors.text

/**
 * iOS: MapChromeSurface. Glass is `.ultraThinMaterial` forced dark there; here the dark tint
 * without the blur (docs/divergences.md, «Glass background»), with the same white hairline.
 */
@Composable
fun MapChromeSurface(progress: Float, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape) {
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .alpha(1f - progress)
                .background(ChromeGlass, shape)
                .border(0.5.dp, Color.White.copy(alpha = 0.14f), shape),
        )
        Box(Modifier.matchParentSize().alpha(progress).background(AppTheme.colors.cardAlt, shape))
    }
}

/**
 * The dark glass of map controls: a near-black at the opacity the material reads at over a map.
 * The dark ultra-thin material turns the light hero (244, 242, 238) into (170, 169, 167) on the
 * iPhone, which #1C1C1E gives at 0.34 (the 3 November audit, SH-03: 0.62 read charcoal, 109).
 */
val ChromeGlass = Color(0xFF1C1C1E).copy(alpha = 0.34f)

/**
 * iOS: TripDetailTopBar.swift. Back on the left, the actions on the right, both over the hero;
 * as the hero scrolls away the bar fills with the background and the title fades in past 60 %.
 * Laid out from the physical top: [topInset] is the status bar.
 */
@Composable
fun DetailTopBar(
    progress: Float,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
    backTag: String = "detail_back",
    /** iOS TripDetailTopBar `pill`: whose trip it is, beside «Назад», gone once the page scrolls. */
    pill: (@Composable () -> Unit)? = null,
) {
    val strings = appStrings
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier.fillMaxWidth()) {
        // The bar's ground never takes touches: only its controls do.
        Column(Modifier.matchParentSize().alpha(progress)) {
            Box(Modifier.fillMaxWidth().weight(1f).background(AppTheme.colors.bg))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(AppTheme.colors.border))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = topInset + 8.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title,
                style = inter(15f, InterWeight.semibold),
                color = AppTheme.colors.text,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 108.dp)
                    .alpha(((progress - 0.6f) / 0.4f).coerceIn(0f, 1f)),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Back and the pill share the start; the pill takes what it needs of the rest.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    MapChromeButton(AppGlyph.chevronLeft, strings.back(), onBack, Modifier.testTag(backTag), progress)
                    if (pill != null && progress < 0.4f) {
                        Box(Modifier.padding(start = 8.dp, end = 8.dp).weight(1f, fill = false).alpha((1f - progress * 2.5f).coerceIn(0f, 1f))) { pill() }
                    }
                }
                actions?.invoke()
            }
        }
    }
}

/** iOS: the top bar's progress, quantized to 1/20 so the body does not recompose on every pixel. */
fun heroProgress(scrolled: Float, span: Float): Float {
    val raw = (scrolled / maxOf(1f, span)).coerceIn(0f, 1f)
    return Math.round(raw * 20) / 20f
}

/** iOS: DetailStatCard.Segment. A number and its unit word, kept apart so each takes its own size. */
data class StatSegment(val value: String, val unit: String = "")

/**
 * iOS: DetailStatCard.swift. The tile of the details grid: the number large, the unit half its
 * size, the caption in small capitals. The value is one line that shrinks as a piece (0.6) so
 * «4 ч 58 мин» never wraps between the hours and the minutes.
 */
@Composable
fun DetailStatCard(segments: List<StatSegment>, label: String, color: Color, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val shadow = if (AppTheme.isDark) Color.Transparent else Color.Black.copy(alpha = 0.03f)
    val value = remember(segments, color, colors.textSecondary) {
        buildAnnotatedString {
            segments.forEachIndexed { index, segment ->
                // A small-font space between segments, as iOS joins them.
                if (index > 0) withStyle(SpanStyle(fontSize = UnitEm, fontWeight = FontWeight.W500)) { append(" ") }
                withStyle(SpanStyle(color = color, fontFeatureSettings = "tnum")) { append(segment.value) }
                if (segment.unit.isNotEmpty()) {
                    withStyle(SpanStyle(fontSize = UnitEm, fontWeight = FontWeight.W500, color = colors.textSecondary)) {
                        append(" ")
                        append(segment.unit)
                    }
                }
            }
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .iosShadow(shape, shadow, radius = 2.dp, y = 1.dp)
            .background(colors.card, shape)
            .padding(start = 14.dp, end = 12.dp, top = 13.dp, bottom = 13.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            value,
            style = inter(24f, InterWeight.heavy),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = points(24f * 0.6f), maxFontSize = points(24f), stepSize = points(0.5f)),
        )
        Text(
            label.uppercased(strings.language),
            style = inter(10f, InterWeight.bold),
            letterSpacing = points(0.2f),
            color = colors.textTertiary,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = points(8f), maxFontSize = points(10f), stepSize = points(0.25f)),
        )
    }
}

private val UnitEm = (13f / 24f).em

/** iOS: TripDetailFormat.durationSegments. Hours and minutes, minutes alone, seconds under a minute; never a clock. */
fun durationSegments(seconds: Double, strings: AppStrings): List<StatSegment> {
    val total = maxOf(0L, seconds.toLong())
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return when {
        hours > 0 -> listOfNotNull(StatSegment("$hours", strings.hoursUnitShort()), if (minutes > 0) StatSegment("$minutes", strings.minutesUnitShort()) else null)
        minutes > 0 -> listOf(StatSegment("$minutes", strings.minutesUnitShort()))
        else -> listOf(StatSegment("${total % 60}", strings.secondsUnitShort()))
    }
}

/** iOS: JourneyFormat.duration, the segments joined: «3 ч 5 мин». */
fun durationText(seconds: Double, strings: AppStrings): String =
    durationSegments(seconds, strings).joinToString(" ") { "${it.value} ${it.unit}" }

/** iOS: DetailSectionHeader. The section heading of a detail screen, following the font scale. */
@Composable
fun DetailSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = interScaled(17f, InterWeight.bold), color = AppTheme.colors.text, modifier = modifier)
}

/** iOS: DetailChipSurface. A capsule chip on the card ground with the light-only shadow. */
@Composable
fun DetailChip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shadow = if (AppTheme.isDark) Color.Transparent else Color.Black.copy(alpha = 0.03f)
    Row(
        modifier
            .iosShadow(CapsuleShape, shadow, radius = 2.dp, y = 1.dp)
            .background(AppTheme.colors.card, CapsuleShape)
            .padding(start = 11.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.onezee.triptrack.designsystem.components.LocalTextStyle provides inter(12f, InterWeight.medium),
            com.onezee.triptrack.designsystem.components.LocalContentColor provides AppTheme.colors.textSecondary,
        ) { content() }
    }
}

/**
 * iOS: JourneyLinkRow.swift. «Часть путешествия «X» · 6 дней ›» — the way from a trip to the
 * journey it belongs to, in accent on the accent wash.
 */
@Composable
fun JourneyLinkRow(title: String?, start: java.time.Instant, end: java.time.Instant?, onTap: () -> Unit, modifier: Modifier = Modifier, now: java.time.Instant = LocalClock.current.instant()) {
    val strings = appStrings
    val haptics = rememberHaptics()
    val days = JourneyWindow.days(start, end, now, strings.zone)
    val name = title?.trim()?.takeIf { it.isNotEmpty() } ?: strings.journeyWord()
    PressableCard(onClick = { haptics.tap(); onTap() }, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(AppTheme.accentBg, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(AppGlyph.suitcaseFill, contentDescription = null, tint = AppTheme.accent, size = 15.dp)
            Text("${strings.journeyPartOf(name)} · $days ${strings.nounDays(days)}", style = inter(12f, InterWeight.semibold),
                color = AppTheme.accent, maxLines = 1, modifier = Modifier.weight(1f))
            Glyph(AppGlyph.chevronRight, contentDescription = null, tint = AppTheme.accent, size = 12.dp)
        }
    }
}

/** A full-width primary button of the sheets: white on accent, 50 high, the iOS 14 radius. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Color = AppTheme.accent,
    ink: Color = Color.White,
    height: Dp = 50.dp,
    radius: Dp = 14.dp,
    weight: InterWeight = InterWeight.bold,
) {
    PressableCard(onClick = onClick, modifier = modifier.fillMaxWidth(), enabled = enabled) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer { alpha = if (enabled) 1f else 0.6f }
                .background(if (enabled) fill else AppTheme.colors.cardAlt, RoundedCornerShape(radius)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = inter(15f, weight), color = if (enabled) ink else AppTheme.colors.textTertiary, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

/** The 40 close circle of the house sheets (iOS NavCircleIcon("xmark")). */
@Composable
fun SheetCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    com.onezee.triptrack.designsystem.components.NavCircleButton(AppGlyph.xmark, appStrings.close(), onClick, modifier)
}
