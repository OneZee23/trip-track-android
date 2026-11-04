// iOS: ProfileHeroCard.swift, ProfileGarageHeader.swift, ProfileRoadScene.swift, ProfileCoverPager.swift
package com.onezee.triptrack.feature.me

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.garage.*
import com.onezee.triptrack.feature.social.ui.drawCover
import com.onezee.triptrack.feature.social.ui.rankTitle
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.DriverRank
import com.onezee.triptrack.model.RoadSceneTime
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.logic.UnitNumber
import com.onezee.triptrack.logic.distanceValue
import com.onezee.triptrack.logic.from
import com.onezee.triptrack.model.Vehicle
import com.onezee.triptrack.ui.AvatarGlyph
import java.util.UUID

/** The box a call site gives an SF Symbol of [pt] points: 1.2 × the point size, the convention of the glyph set. */
internal fun symbolBox(pt: Float): Dp = (pt * 1.2f).dp

/** iOS `dynamicTypeSize.isAccessibilitySize`: the five largest sizes start at 1.65 × the default body. */
@Composable
internal fun isAccessibilitySize(): Boolean = LocalDensity.current.fontScale >= 1.6f

@Composable
internal fun statusBarTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** iOS: the name in the header. A signed-in account with no name yet shows the prompt, drawn dimmer. */
internal data class HeroName(val text: String, val isPlaceholder: Boolean)

internal fun heroDisplayName(name: String, signedIn: Boolean, strings: AppStrings): HeroName {
    val trimmed = name.trim()
    if (trimmed.isNotEmpty()) return HeroName(trimmed, false)
    return if (signedIn) HeroName(strings.meAddYourName(), true) else HeroName(strings.meGuestName(), false)
}

/**
 * iOS: VehicleCatalogCaption.modelLine: «Volkswagen Polo · 2018 · 110 л.с.». Make and model are one
 * part, the year follows when there is one, and so does the catalogue's power of the variant.
 */
internal fun heroCaption(vehicle: Vehicle, powerHp: Double?, strings: AppStrings): String {
    val parts = mutableListOf(listOf(vehicle.make, vehicle.model).filter { it.isNotEmpty() }.joinToString(" "))
    if (vehicle.year > 0) parts += vehicle.year.toString()
    if (powerHp != null) parts += strings.garagePower(UnitNumber.upToTenth(powerHp, strings.language.code))
    return parts.filter { it.isNotEmpty() }.joinToString(" · ")
}

/** The colours the card wears: ink on the page, or white on a chosen background. */
private class HeroInk(val hasPhotos: Boolean, val hasCover: Boolean, colors: AppColors) {
    val showsRoad = !hasPhotos && !hasCover
    val ink: Color = if (hasCover) Color.White else colors.text
    val secondary: Color = if (hasCover) Color.White.copy(alpha = .85f) else colors.textSecondary
    val rule: Color = if (hasCover) Color.White.copy(alpha = .22f) else AppTheme.accent.copy(alpha = .18f)
    val avatarFill: Color = if (hasCover) Color.White.copy(alpha = .16f) else colors.cardAlt
    val level: Color = if (hasCover) Color.White else if (showsRoad) colors.roadHeroAccent else AppTheme.accent
}

/**
 * iOS ProfileHeroCard (own). Three shapes: photos make a cover pager with the panel under it; a
 * chosen background fills the card under a dark veil with white ink; otherwise the road scene sits
 * behind their car. The card draws its own outer margins (16 at the sides, 4 under the status bar),
 * as on iOS; the cover runs edge to edge.
 */
@Composable
internal fun ProfileHeroCard(state: ProfileHeroState, unit: DistanceUnit, onSettings: () -> Unit,
                             onVehicle: (UUID) -> Unit, onAdd: () -> Unit, onProfile: () -> Unit = {}, onLevel: () -> Unit = {},
                             coverPhotos: List<ProfileCoverPhoto> = emptyList(), api: com.onezee.triptrack.network.ApiClient? = null, onPhotos: () -> Unit = {},
                             onStats: () -> Unit = {}, safeTop: Dp = statusBarTop()) {
    val colors = AppTheme.colors
    val gradient = state.identity.effectiveBackground.colors()
    val ink = HeroInk(coverPhotos.isNotEmpty(), gradient.isNotEmpty(), colors)
    val actions = HeroActions(onSettings, onVehicle, onAdd, onProfile, onLevel, onStats)
    when {
        ink.hasPhotos -> PhotoHero(state, unit, ink, actions, coverPhotos, api, onPhotos, safeTop)
        ink.hasCover -> ChosenBackgroundCard(state, unit, ink, actions)
        else -> RoadCard(state, unit, ink, actions)
    }
}

private class HeroActions(val onSettings: () -> Unit, val onVehicle: (UUID) -> Unit, val onAdd: () -> Unit,
                          val onProfile: () -> Unit, val onLevel: () -> Unit, val onStats: () -> Unit)

private val CardShape = ContinuousRoundedRectangle(24.dp)

/** iOS roadCard: identity and bio, the car on its road, the three numbers under a rule. */
@Composable
private fun RoadCard(state: ProfileHeroState, unit: DistanceUnit, ink: HeroInk, actions: HeroActions) {
    val colors = AppTheme.colors
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 4.dp).fillMaxWidth().clip(CardShape).background(colors.roadHeroCard)
        .border(1.dp, AppTheme.accent.copy(alpha = .22f), CardShape).testTag("profile_hero")) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            HeroIdentityRow(state, ink, actions)
            if (state.identity.bio.isNotBlank()) Text(state.identity.bio, Modifier.testTag("profile_bio"), style = interScaled(14f), color = ink.ink)
        }
        OwnGarageHeader(state, ink, actions, roadTime = state.roadTime, onCoverEdge = false)
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (state.tripCount > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(ink.rule))
                HeroStats(state, unit, ink, actions.onStats)
            }
        }
    }
}

/** iOS originalCard: a chosen background under a dark veil, white ink, the car on its platform. */
@Composable
private fun ChosenBackgroundCard(state: ProfileHeroState, unit: DistanceUnit, ink: HeroInk, actions: HeroActions) {
    val background = state.identity.effectiveBackground
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 4.dp).fillMaxWidth().clip(CardShape)
        .drawBehind { drawCover(background); drawRect(Color.Black.copy(alpha = .64f)) }.testTag("profile_hero")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            HeroIdentityRow(state, ink, actions)
            if (state.identity.bio.isNotBlank()) Text(state.identity.bio, Modifier.testTag("profile_bio"), style = interScaled(14f), color = ink.ink, maxLines = 3)
            OwnGarageHeader(state, ink, actions, roadTime = null, onCoverEdge = false)
            if (state.tripCount > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(ink.rule))
                HeroStats(state, unit, ink, actions.onStats)
            }
        }
    }
}

/** iOS: the cover pager over the whole top of the screen and, under it, the panel with the car pulled up over the photo. */
@Composable
private fun PhotoHero(state: ProfileHeroState, unit: DistanceUnit, ink: HeroInk, actions: HeroActions, photos: List<ProfileCoverPhoto>,
                      api: com.onezee.triptrack.network.ApiClient?, onPhotos: () -> Unit, safeTop: Dp) {
    val colors = AppTheme.colors
    val hasVehicle = state.cars.isNotEmpty()
    val background = state.identity.effectiveBackground
    Column(Modifier.fillMaxWidth().testTag("profile_hero")) {
        ProfileCoverPager(photos, api, onPhotos, actions.onSettings, safeTop)
        Column(Modifier.fillMaxWidth().drawBehind {
            if (ink.hasCover) { drawCover(background); drawRect(Color.Black.copy(alpha = .64f)) } else drawRect(colors.bg)
        }.padding(horizontal = 16.dp).padding(top = if (hasVehicle) 0.dp else 18.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (hasVehicle) Box(Modifier.pullUp(82.dp)) { OwnGarageHeader(state, ink, actions, roadTime = null, onCoverEdge = true) }
            HeroIdentityRow(state, ink, actions)
            if (state.identity.bio.isNotBlank()) Text(state.identity.bio, Modifier.testTag("profile_bio"), style = interScaled(15f), color = ink.ink)
            if (state.tripCount > 0) Box(Modifier.padding(top = 4.dp)) { HeroStats(state, unit, ink, actions.onStats) }
        }
    }
}

/** iOS `.padding(.top, -n)`: the layout is [amount] shorter and the content stands that much higher. */
private fun Modifier.pullUp(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val pull = amount.roundToPx()
    layout(placeable.width, (placeable.height - pull).coerceAtLeast(0)) { placeable.place(0, -pull) }
}

// ---------------------------------------------------------------------------------------------
// Identity

/** iOS identityRow: the avatar, the name with the level under it, the gear; stacked at the largest text sizes. */
@Composable
private fun HeroIdentityRow(state: ProfileHeroState, ink: HeroInk, actions: HeroActions) {
    val strings = appStrings
    val accessibility = isAccessibilitySize()
    val name = heroDisplayName(state.identity.name, state.signedIn, strings)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            val side = if (ink.hasPhotos) 46.dp else 60.dp
            PressableCard(onClick = actions.onProfile, onClickLabel = strings.myProfileTitle(), modifier = Modifier.testTag("profile_avatar")) {
                Box(Modifier.size(side).background(ink.avatarFill, CircleShape), contentAlignment = Alignment.Center) {
                    AvatarGlyph(state.identity.avatarEmoji, if (ink.hasPhotos) 26f else 32f)
                }
            }
            if (!accessibility) IdentityText(state, name, ink, actions, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            if (!ink.hasPhotos) GearButton(strings.settingsTitle(), ink.secondary, actions.onSettings)
        }
        if (accessibility) IdentityText(state, name, ink, actions, Modifier.fillMaxWidth())
    }
}

/** iOS: `gearshape` 19 medium in the secondary ink, a 44 target. */
@Composable
private fun GearButton(label: String, tint: Color, onClick: () -> Unit) {
    PressableCard(onClick = onClick, onClickLabel = label, modifier = Modifier.testTag("profile_gear")) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { MeGlyphIcon(MeGlyph.gearshape, label, tint = tint, size = symbolBox(19f)) }
    }
}

@Composable
private fun IdentityText(state: ProfileHeroState, name: HeroName, ink: HeroInk, actions: HeroActions, modifier: Modifier) {
    val strings = appStrings
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PressableCard(onClick = actions.onProfile, onClickLabel = strings.myProfileTitle(), modifier = Modifier.testTag("profile_identity")) {
            Box(Modifier.heightIn(min = if (ink.hasPhotos) 26.dp else 32.dp), contentAlignment = Alignment.CenterStart) {
                Text(name.text, style = interScaled(21f, InterWeight.bold), letterSpacing = points(-0.3f), color = if (name.isPlaceholder) ink.secondary else ink.ink)
            }
        }
        LevelRow(state.identity.level, ink, actions.onLevel)
    }
}

/** iOS: «LVL 8» in Handjet Black 17, the rank in Inter 12 medium and a 9 pt chevron: a row of text, no capsule. */
@Composable
private fun LevelRow(level: Int, ink: HeroInk, onClick: () -> Unit) {
    val strings = appStrings
    Row(Modifier.heightIn(min = if (ink.hasPhotos) 18.dp else 26.dp).clickable(role = Role.Button, onClick = onClick).testTag("profile_lvl_pill"),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("LVL $level", style = handjet(17f), color = ink.level)
        Text(rankTitle(DriverRank.from(level)), style = interScaled(12f, InterWeight.medium), color = ink.secondary)
        Glyph(AppGlyph.chevronRight, null, tint = ink.secondary, size = symbolBox(9f))
    }
}

// ---------------------------------------------------------------------------------------------
// Numbers

/**
 * iOS statsRow: the three numbers across the whole card, the first at its left edge and the last at
 * its right, equal gaps between. At the largest text sizes they stand in a column.
 */
@Composable
private fun HeroStats(state: ProfileHeroState, unit: DistanceUnit, ink: HeroInk, onClick: () -> Unit) {
    val strings = appStrings
    val accessibility = isAccessibilitySize()
    val columns = listOf(
        state.tripCount.toString() to strings.trips(),
        Measure.distanceValue(state.distanceMetres, unit, strings.language.code, Measure.Style.grouped) to strings.statsKmTotal(unit),
        state.regionCount.toString() to strings.statsRegions(),
    )
    PressableCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("profile_stats_strip")) {
        if (accessibility) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            columns.forEach { (value, label) -> HeroStatColumn(value, label, ink, Modifier.fillMaxWidth(), shrinks = true) }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            columns.forEach { (value, label) -> HeroStatColumn(value, label, ink, Modifier) }
        }
    }
}

@Composable
private fun HeroStatColumn(value: String, label: String, ink: HeroInk, modifier: Modifier, shrinks: Boolean = false) {
    val size = if (ink.hasPhotos) 19f else 22f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = interScaled(size, if (ink.hasPhotos) InterWeight.heavy else InterWeight.bold, monospacedDigits = true), color = ink.ink, maxLines = 1,
            autoSize = if (shrinks) TextAutoSize.StepBased(minFontSize = (size * .7f).sp, maxFontSize = size.sp) else null)
        Text(label, style = interScaled(11f, InterWeight.medium), color = ink.secondary)
    }
}

/** The three-number row the garage passport still draws: centred columns. */
@Composable
internal fun ProfileStat(value: String, title: String, modifier: Modifier = Modifier, ink: Color = AppTheme.colors.text, secondary: Color = AppTheme.colors.textSecondary) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, style = interScaled(23f, InterWeight.semibold), color = ink, maxLines = 1)
        Text(title, style = interScaled(11f), color = secondary, maxLines = 2)
    }
}

// ---------------------------------------------------------------------------------------------
// The car

/** iOS ProfileRoadScene: the scene, with a 25 pt fade into the card colour over its foot. */
@Composable
internal fun ProfileRoadScene(time: RoadSceneTime, modifier: Modifier = Modifier) {
    val card = AppTheme.colors.roadHeroCard
    val resource = MeArtwork.drawables.getValue("road_${time.name}")
    Box(modifier) {
        Image(painterResource(resource), null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(25.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, card.copy(alpha = if (AppTheme.isDark) .2f else .42f)))))
    }
}

private fun styleOf(vehicle: Vehicle): String = vehicle.avatarStyle.takeIf { it in VehicleAvatar.styles(vehicle.type) } ?: VehicleAvatar.styles(vehicle.type).first()

/**
 * iOS ProfileGarageHeader (own cars). On the road the band is as tall as the tallest drawn car plus
 * 20 and a photo fills it; off the road the car stands on its platform, narrower; on a cover it is
 * 250 wide, bottom-aligned in 155, with its name and caption on one row. Several cars page, with dots.
 */
@Composable
private fun OwnGarageHeader(state: ProfileHeroState, ink: HeroInk, actions: HeroActions, roadTime: RoadSceneTime?, onCoverEdge: Boolean) {
    val strings = appStrings
    val cars = state.cars
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val width = when {
        onCoverEdge -> 250.dp
        roadTime != null -> minOf(screenWidth - 72.dp, 300.dp)
        else -> minOf(screenWidth * .67f, 300.dp)
    }
    if (cars.isEmpty()) {
        if (onCoverEdge) return
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (roadTime != null) ProfileRoadScene(roadTime, Modifier.fillMaxWidth().height(211.dp))
            AddVehicleButton(ink.secondary, actions.onAdd, Modifier.padding(horizontal = if (roadTime == null) 0.dp else 20.dp))
        }
        return
    }
    val bandHeight = (cars.maxOf { VehicleAvatar.stageHeight(styleOf(it.vehicle), width).value }).dp
    if (cars.size == 1) {
        GaragePage(cars.first(), ink, actions, roadTime, onCoverEdge, width, bandHeight, state.identity.isPlus, strings)
        return
    }
    val pager = rememberPagerState { cars.size }
    Column {
        HorizontalPager(pager, Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { index ->
            GaragePage(cars[index], ink, actions, roadTime, onCoverEdge, width, bandHeight, state.identity.isPlus, strings)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            cars.indices.forEach { index -> Box(Modifier.size(6.dp).background(if (index == pager.currentPage) AppTheme.accent else ink.secondary.copy(alpha = .3f), CircleShape)) }
        }
    }
}

@Composable
private fun GaragePage(car: HeroCarStage, ink: HeroInk, actions: HeroActions, roadTime: RoadSceneTime?, onCoverEdge: Boolean, width: Dp, roadStage: Dp,
                       isPlus: Boolean, strings: AppStrings) {
    val vehicle = car.vehicle
    val name = vehicle.name.ifBlank { strings.defaultVehicleName() }
    val caption = heroCaption(vehicle, car.powerHp, strings)
    val nameStyle = if (roadTime != null) interScaled(17f, InterWeight.semibold) else inter(if (onCoverEdge) 15f else 16f, InterWeight.semibold)
    val captionStyle = if (roadTime != null) interScaled(12f) else inter(12f)
    PressableCard(onClick = { actions.onVehicle(vehicle.id) }, modifier = Modifier.fillMaxWidth().testTag("garage_header_vehicle")) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                roadTime != null && car.photoFilename != null ->
                    // A photo fills the band edge to edge and the scene steps aside: a daytime photo on the drawn night road read as a collage.
                    VehicleLocalPhoto(car.photoFilename, Modifier.fillMaxWidth().height(roadStage + 20.dp), remoteURL = car.remoteURL)
                roadTime != null -> Box(Modifier.fillMaxWidth().height(roadStage + 20.dp)) {
                    ProfileRoadScene(roadTime, Modifier.matchParentSize())
                    Box(Modifier.fillMaxSize().padding(vertical = 10.dp), contentAlignment = Alignment.BottomCenter) {
                        VehicleStage(vehicle, Modifier, width, car.photoFilename, isPlus, car.remoteURL)
                    }
                }
                onCoverEdge -> Box(Modifier.fillMaxWidth().height(155.dp), contentAlignment = Alignment.BottomCenter) {
                    // The stage may be taller than 155 (a crossover): it grows upward over the photo.
                    VehicleStage(vehicle, Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true), width, car.photoFilename, isPlus, car.remoteURL)
                }
                else -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    VehicleStage(vehicle, Modifier, width, car.photoFilename, isPlus, car.remoteURL)
                }
            }
            val label = @Composable {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = nameStyle, color = ink.ink)
                    Glyph(AppGlyph.chevronRight, null, tint = ink.secondary, size = symbolBox(11f))
                }
            }
            if (onCoverEdge) Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                label()
                if (caption.isNotEmpty()) Text(caption, style = captionStyle, color = ink.secondary, maxLines = 1)
            } else {
                label()
                if (caption.isNotEmpty()) Text(caption, style = captionStyle, color = ink.secondary, textAlign = TextAlign.Center)
            }
        }
    }
}

/** iOS addVehicleButton: a dashed box, a thin plus and a word. */
@Composable
private fun AddVehicleButton(secondary: Color, onAdd: () -> Unit, modifier: Modifier) {
    val dash = secondary.copy(alpha = .4f)
    PressableCard(onClick = onAdd, modifier = modifier.fillMaxWidth().height(130.dp).testTag("garage_header_add")) {
        Box(Modifier.fillMaxSize().drawBehind {
            val stroke = 1.dp.toPx()
            drawRoundRect(dash, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), CornerRadius(16.dp.toPx()),
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
        }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MeGlyphIcon(MeGlyph.plus, null, tint = secondary, size = symbolBox(24f))
                Text(appStrings.addVehicleTitle(), style = inter(14f, InterWeight.semibold), color = secondary)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The cover

/** iOS ProfileCoverPager.cover: 232 from the very top of the screen, the page counter and, for one's own, the gear. */
@Composable
private fun ProfileCoverPager(photos: List<ProfileCoverPhoto>, api: com.onezee.triptrack.network.ApiClient?, onPhotos: () -> Unit, onSettings: () -> Unit, safeTop: Dp) {
    val strings = appStrings
    val pager = rememberPagerState { photos.size }
    Box(Modifier.fillMaxWidth().height(232.dp)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { index ->
            PressableCard(onClick = onPhotos, onClickLabel = strings.photos(), modifier = Modifier.fillMaxSize().testTag("profile_photo_$index")) {
                ProfileCoverImage(photos[index], api, Modifier.fillMaxSize())
            }
        }
        CoverPageCounter(pager.currentPage, photos.size, Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = safeTop + 8.dp))
        PressableCard(onClick = onSettings, onClickLabel = strings.settingsTitle(), modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = safeTop + 4.dp).testTag("profile_gear")) {
            Box(Modifier.size(40.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                MeGlyphIcon(MeGlyph.gearshape, strings.settingsTitle(), tint = Color(.12f, .12f, .12f), size = symbolBox(18f))
            }
        }
    }
}

/**
 * iOS ProfileCoverPager.pageCounter: «1 / 4», Inter 12 semibold, white on near-black, a capsule. The caller
 * places it: 8 under the status bar on one's own cover, 54 on someone else's, under the back button.
 */
@Composable
internal fun CoverPageCounter(page: Int, count: Int, modifier: Modifier = Modifier) {
    Text("${minOf(page + 1, count)} / $count", modifier.testTag("profile_cover_counter").background(Color(.08f, .08f, .08f), CapsuleShape).padding(horizontal = 9.dp, vertical = 6.dp),
        style = inter(12f, InterWeight.semibold, monospacedDigits = true), color = Color.White)
}

/**
 * iOS addCoverPrompt: one accent line in a card, the picture-with-plus and the words, and a plain
 * cross at the right that hides it for good. No chevron: the whole left side opens the picker.
 */
@Composable
internal fun ProfileAddCoverPrompt(onAdd: () -> Unit, onHide: () -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    // iOS `.secondary`: the system's secondary label, not the app's own secondary ink.
    val cross = if (AppTheme.isDark) Color(235, 235, 245).copy(alpha = .6f) else Color(60, 60, 67).copy(alpha = .6f)
    Row(modifier.fillMaxWidth().surfaceCard(), verticalAlignment = Alignment.CenterVertically) {
        PressableCard(onClick = onAdd, modifier = Modifier.weight(1f).testTag("profile_add_cover")) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MeGlyphIcon(MeGlyph.photoBadgePlus, null, tint = AppTheme.accent, size = 20.3.dp)
                Text(strings.profileAddCover(), style = interScaled(14f, InterWeight.medium), color = AppTheme.accent, maxLines = 1)
                Spacer(Modifier.weight(1f))
            }
        }
        PressableCard(onClick = onHide, modifier = Modifier.semantics { contentDescription = strings.profileCoverPromptHide() }.testTag("profile_photos_add_hide")) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Glyph(AppGlyph.xmark, null, tint = cross, size = symbolBox(12f)) }
        }
    }
}
