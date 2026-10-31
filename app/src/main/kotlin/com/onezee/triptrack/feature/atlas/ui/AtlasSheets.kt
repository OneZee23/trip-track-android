// iOS: AtlasControlsSheet.swift (AtlasAppearanceSheet, AtlasExplanationSheet, AtlasControlsHeader,
// AtlasStylePreview), AtlasBetaSheet.swift, AtlasBetaChip's sheet in MyMapView and PlacesView.
package com.onezee.triptrack.feature.atlas.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.SheetDetent
import com.onezee.triptrack.designsystem.components.SheetScope
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.AtlasTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.ContinuousRoundedRectangle
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.atlas.AtlasAppearance
import com.onezee.triptrack.feature.settings.HomeState
import com.onezee.triptrack.logic.radius
import com.onezee.triptrack.model.AtlasMapAppearance
import com.onezee.triptrack.model.Measure

/**
 * iOS: AtlasControlsHeader. The handle, the title in the sheet-title role and the grey ×, shared by
 * every Atlas sheet: a second copy of the header would part from the first on its first edit.
 */
@Composable
fun AtlasControlsHeader(title: String, onClose: () -> Unit, closeTag: String = "atlas_controls_close") {
    val atlas = AtlasTheme.colors
    val strings = appStrings
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(width = 36.dp, height = 5.dp).background(atlas.handle, CapsuleShape))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.weight(1f).semantics { heading() }, style = AppType.sheetTitle, color = atlas.ink, maxLines = 1)
            CircleButton(AppGlyph.xmark, strings.close(), closeTag, fill = atlas.searchBackground, tint = atlas.secondary, glyphSize = 15.dp, onClick = onClose)
        }
    }
}

/**
 * iOS: AtlasAppearanceSheet. The choices change the existing map at once; no second map is made.
 * Three styles in a row, the photos toggle, and the home row.
 *
 * Its height is a NUMBER (iOS `.height(396)`): measuring sheets with growing children twice ended in
 * a broken screen on the owner's phone. Change the content, change the number.
 */
@Composable
fun AtlasAppearanceSheet(
    appearance: AtlasAppearance,
    home: HomeState,
    onChange: (AtlasAppearance) -> Unit,
    onOpenHome: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    ContentSizedSheet(onDismissRequest = onDismissRequest, background = atlas.background) {
        val sheet = this
        Column(Modifier.fillMaxWidth().height(APPEARANCE_SHEET_HEIGHT).testTag("atlas_appearance_sheet")) {
            AtlasControlsHeader(strings.atlasMapStyle(), onClose = { sheet.dismiss() })
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Three equal columns, as the A7 mock draws: the captions differ in length.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((style, title) in listOf(AtlasMapAppearance.Style.fog to strings.atlasFogStyle(),
                        AtlasMapAppearance.Style.night to strings.atlasNightStyle(), AtlasMapAppearance.Style.cells to strings.atlasCellsStyle())) {
                        StyleCard(style, title, appearance.style == style, Modifier.weight(1f)) { onChange(appearance.copy(style = style)) }
                    }
                }
                Column(Modifier.fillMaxWidth().background(atlas.card, RoundedCornerShape(AtlasTheme.cardRadius)).padding(horizontal = 16.dp)) {
                    // City labels are gone (owner, 26 September): the map names its towns itself, and
                    // the switch went with them.
                    ToggleRow(strings.atlasShowPhotos(), appearance.showsPhotos, "atlas_photos_toggle") { onChange(appearance.copy(showsPhotos = it)) }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(atlas.separator))
                    HomeRow(home, strings, onOpenHome)
                }
            }
        }
    }
}

/** iOS: 340 before the home row plus that row's 56. */
val APPEARANCE_SHEET_HEIGHT: Dp = 396.dp

@Composable
private fun StyleCard(style: AtlasMapAppearance.Style, title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val atlas = AtlasTheme.colors
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(17.dp)
    PressableCard(onClick = { haptics.tap(); onClick() }, modifier = modifier.selectedSemantics(selected).testTag("atlas_style_${style.rawValue}")) {
        Column(
            Modifier.fillMaxWidth().background(atlas.card, shape).border(2.dp, if (selected) atlas.accent else Color.Transparent, shape)
                .padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AtlasStylePreview(style, Modifier.fillMaxWidth().height(82.dp).clip(RoundedCornerShape(12.dp)))
            Row(Modifier.heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val ink = if (selected) atlas.accent else atlas.ink
                Text(title, style = inter(14f, InterWeight.bold), color = ink, maxLines = 1)
                if (selected) Glyph(AppGlyph.checkmarkCircleFill, contentDescription = null, tint = ink, size = 14.dp)
            }
        }
    }
}

/**
 * iOS: AtlasStylePreview, a small drawing of each real appearance. «Клетки» draws CELLS: a tile
 * captioned «Клетки» drawn with a soft corridor would promise something other than what one sees.
 */
@Composable
fun AtlasStylePreview(style: AtlasMapAppearance.Style, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val night = style == AtlasMapAppearance.Style.night
        drawRect(if (night) Color(0.11f, 0.14f, 0.19f) else Color(0.90f, 0.90f, 0.88f))
        val route = previewRoute(size)
        val opened = Color(0.85f, 0.82f, 0.69f)
        if (style == AtlasMapAppearance.Style.cells) {
            drawOpenedCells(size, opened)
        } else {
            drawPath(route, if (night) Color(0.20f, 0.25f, 0.30f) else opened, style = Stroke(28.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val streets = Path()
        var x = -20.dp.toPx()
        while (x <= size.width + 40.dp.toPx()) {
            streets.moveTo(x, 0f)
            streets.lineTo(x + 40.dp.toPx(), size.height)
            x += 26.dp.toPx()
        }
        drawPath(streets, Color.White.copy(alpha = if (night) 0.07f else 0.55f), style = Stroke(1.dp.toPx()))
        drawPath(route, Color.White.copy(alpha = if (night) 0.25f else 0.95f), style = Stroke(4.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(route, if (night) Color(1f, 0.58f, 0.34f) else Color(0.78f, 0.28f, 0.18f),
            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        for (point in listOf(Offset(size.width * 0.16f, size.height * 0.75f), Offset(size.width * 0.78f, size.height * 0.23f))) {
            drawCircle(Color.White, 3.dp.toPx(), point)
            drawCircle(Color.Black.copy(alpha = 0.65f), 3.dp.toPx(), point, style = Stroke(1.dp.toPx()))
        }
    }
}

private fun DrawScope.previewRoute(size: Size): Path = Path().apply {
    moveTo(size.width * 0.16f, size.height * 0.75f)
    cubicTo(size.width * 0.59f, size.height * 0.95f, size.width * 0.42f, size.height * 0.10f, size.width * 0.78f, size.height * 0.23f)
}

/**
 * The cells the corridor touches. The side is «by the eye of the frame», not FogCellGrid's: the
 * preview is an 80 point drawing, and the real side in map points means nothing here.
 */
private fun DrawScope.drawOpenedCells(size: Size, colour: Color) {
    val side = 13.dp.toPx()
    val halo = 14.dp.toPx()
    // Sample the same cubic the route draws and keep the cells whose centre lies within the halo.
    val samples = (0..64).map { i ->
        val t = i / 64f
        val p0 = Offset(size.width * 0.16f, size.height * 0.75f)
        val p1 = Offset(size.width * 0.59f, size.height * 0.95f)
        val p2 = Offset(size.width * 0.42f, size.height * 0.10f)
        val p3 = Offset(size.width * 0.78f, size.height * 0.23f)
        val u = 1 - t
        p0 * (u * u * u) + p1 * (3 * u * u * t) + p2 * (3 * u * t * t) + p3 * (t * t * t)
    }
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            val centre = Offset(x + side / 2, y + side / 2)
            if (samples.any { (it - centre).getDistance() <= halo }) drawRect(colour, Offset(x, y), Size(side, side))
            x += side
        }
        y += side
    }
}

/**
 * iOS Toggle with the accent tint, drawn for the house: a 51 × 31 capsule and a 27 point thumb. The
 * whole row is the target; TalkBack hears a switch with its state.
 */
@Composable
fun ToggleRow(title: String, checked: Boolean, testTag: String, onChange: (Boolean) -> Unit) {
    val atlas = AtlasTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(interactionSource = interaction, indication = null) { haptics.selection(); onChange(!checked) }
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            }
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = inter(16f, InterWeight.semibold), color = atlas.ink)
        HouseSwitch(checked)
    }
}

@Composable
fun HouseSwitch(checked: Boolean) {
    val track by animateColorAsState(if (checked) AtlasTheme.colors.accent else AppTheme.colors.cardAlt, label = "switchTrack")
    val thumb by animateDpAsState(if (checked) 22.dp else 2.dp, label = "switchThumb")
    Box(Modifier.size(width = 51.dp, height = 31.dp).background(track, CapsuleShape)) {
        Box(Modifier.offset(x = thumb, y = 2.dp).size(27.dp).background(Color.White, CircleShape)
            .border(0.5.dp, Color.Black.copy(alpha = 0.06f), CircleShape))
    }
}

/**
 * iOS homeRow: a ROW, not a switch (a departure from board A7 with its reason). Home is set only by
 * hand, and a switch at a home that does not exist yet is a press that does nothing.
 */
@Composable
private fun HomeRow(home: HomeState, strings: AppStrings, onOpenHome: () -> Unit) {
    val atlas = AtlasTheme.colors
    val haptics = rememberHaptics()
    PressableCard(onClick = { haptics.tap(); onOpenHome() }, modifier = Modifier.fillMaxWidth().testTag("atlas_home_row")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.homeRow(), style = inter(16f, InterWeight.semibold), color = atlas.ink)
            Spacer(Modifier.weight(1f))
            Text(homeSummary(home, strings, LocalDistanceUnit.current), style = inter(14f), color = atlas.secondary, maxLines = 1)
            Glyph(AppGlyph.chevronRight, contentDescription = null, tint = atlas.secondary.copy(alpha = 0.7f), size = 14.dp)
        }
    }
}

/** iOS homeSummary: «Не задан» / «Виден только тебе» / «Скрыт на карте · зона 500 м». */
fun homeSummary(home: HomeState, strings: AppStrings, unit: com.onezee.triptrack.model.DistanceUnit): String {
    if (home.coordinate == null) return strings.homeNotSet()
    val parts = mutableListOf(if (home.showsOnMap) strings.homeVisibleOnlyToYou() else strings.homeHiddenOnMap())
    if (home.trimsPublicTracks) {
        parts += strings.homeZoneShort(Measure.radius(home.radius.rawValue.toDouble(), unit, strings.language.code, strings))
    }
    return parts.joinToString(" · ")
}

/** iOS: AtlasExplanationSheet. The numbers the app really counts, nothing it does not. */
@Composable
fun AtlasExplanationSheet(onDismissRequest: () -> Unit) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    ContentSizedSheet(onDismissRequest = onDismissRequest, background = atlas.background) {
        val sheet = this
        Column(Modifier.fillMaxWidth().testTag("atlas_explanation_sheet")) {
            AtlasControlsHeader(strings.atlasHowWeCount(), onClose = { sheet.dismiss() })
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Explanation(WorldGlyph.curvePath, strings.atlasNewRoads(), strings.atlasRoadsExplanation())
                Explanation(WorldGlyph.circlePath, strings.atlasTotalTravelled(), strings.atlasTripsExplanation())
                Explanation(null, strings.atlasPeriod(), strings.atlasPeriodRoadsExplanation())
            }
        }
    }
}

@Composable
private fun Explanation(symbol: WorldGlyph?, title: String, body: String) {
    val atlas = AtlasTheme.colors
    Column(
        Modifier.fillMaxWidth().background(atlas.card, RoundedCornerShape(AtlasTheme.cardRadius)).padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (symbol != null) WorldGlyphIcon(symbol, contentDescription = null, tint = atlas.accent, size = 20.dp)
            else Glyph(AppGlyph.calendar, contentDescription = null, tint = atlas.accent, size = 20.dp)
            Text(title.uppercase(appStrings.language.locale), style = AppType.statCaption, letterSpacing = AppType.statCaptionTracking, color = atlas.ink)
        }
        Text(body, style = inter(15f), color = atlas.secondary)
    }
}

/** iOS: AtlasBetaSheetModel.Subject. One card for both tabs: the question is the same. */
enum class BetaSubject { atlas, places }

/**
 * iOS: AtlasBetaSheet. «Атлас в бете» and its words, «Написать автору» by mail and «Понятно». A house
 * sheet sized to its content, never a system alert.
 */
@Composable
fun AtlasBetaSheet(subject: BetaSubject, onDismissRequest: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val context = LocalContext.current
    val haptics = rememberHaptics()
    ContentSizedSheet(onDismissRequest = onDismissRequest, background = colors.bg) {
        val sheet = this
        Column(Modifier.fillMaxWidth().padding(20.dp).testTag("atlas_beta_sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (subject == BetaSubject.atlas) strings.atlasBetaTitle() else strings.placesBetaTitle(), style = inter(19f, InterWeight.semibold), color = colors.text)
            Text(if (subject == BetaSubject.atlas) strings.atlasBetaBody() else strings.placesBetaBody(), style = inter(14f), color = colors.textSecondary)
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetButton(strings.writeAuthor(), AppTheme.accent, Color.White, "atlas_beta_feedback") {
                    haptics.tap()
                    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$AUTHOR_EMAIL")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                SheetButton(strings.ok(), colors.cardAlt, colors.textSecondary, "atlas_beta_dismiss") { haptics.tap(); sheet.dismiss() }
            }
        }
    }
}

/**
 * iOS ProfileSettingsSheet.authorEmail: the same mailbox the profile's «Написать автору» opens. One
 * address, one place that knows it, once the settings screen is ported.
 */
const val AUTHOR_EMAIL = "privacy@trip-track.app"

@Composable
fun SheetButton(title: String, fill: Color, ink: Color, testTag: String, onClick: () -> Unit) {
    PressableCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Box(Modifier.fillMaxWidth().background(fill, ContinuousRoundedRectangle(14.dp)).padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
            Text(title, style = inter(15f, InterWeight.semibold), color = ink, maxLines = 1)
        }
    }
}
