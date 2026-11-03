package com.onezee.triptrack.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.R

// iOS: SF Symbols and CustomTabBar.swift tab artwork.
enum class AppGlyph {
    checkmark,
    xmark,
    chevronLeft,
    chevronRight,
    ellipsis,
    checkmarkCircleFill,
    xmarkCircleFill,
    infoCircleFill,
    arrowUturnBackwardCircleFill,
    gearshapeFill,
    moonFill,
    sunMaxFill,
    pencil,
    trash,
    squareAndArrowUp,
    flag,
    characterBubble,
    plus,
    lock,
    lockFill,
    globe,
    map,
    mapSlash,
    locationFill,
    suitcase,
    suitcaseFill,
    squareAndPencil,
    pencilLine,
    camera,
    cameraFill,
    photo,
    flagFill,
    flagCheckered,
    mappinAndEllipse,
    arrowUpLeftAndArrowDownRight,
    arrowDownRightAndArrowUpLeft,
    arrowClockwise,
    exclamationmarkTriangleFill,
    car,
    tray,
    tablecells,
    squareAndArrowDown,
    magnifyingglass,
    minusCircle,
    plusCircleFill,
    checkmarkCircle,
    circle,
    bubbleRight,
    heart,
    calendar,
    clock,
    speedometer,
    mountain,
    bolt,
    fuelpump,
    roadLanes,
    arrowTriangleSwap,
    pause,
    play,
    airplane,
    minus,
    chevronUp,
    chevronDown,
    houseFill,
    trayFull,
    faceSmiling,
    faceDashed,
    starFill,
    bellFill,
    handRaisedFill,
    envelopeFill,
    ruler,
    ladybugFill,
    antennaRadiowaves,
    antennaRadiowavesSlash,
    musicNote,
    speakerWaveFill,
    icloudFill,
    icloudSlash,
    mappin,
    clipboard,
    tabHome,
    tabMaps,
    tabRecord,
    tabPlaces,
    tabProfile
}

private val AppGlyph.vectorResource: Int
    get() = when (this) {
        AppGlyph.checkmark -> R.drawable.glyph_checkmark
        AppGlyph.xmark -> R.drawable.glyph_xmark
        AppGlyph.chevronLeft -> R.drawable.glyph_chevron_left
        AppGlyph.chevronRight -> R.drawable.glyph_chevron_right
        AppGlyph.ellipsis -> R.drawable.glyph_ellipsis
        AppGlyph.checkmarkCircleFill -> R.drawable.glyph_checkmark_circle_fill
        AppGlyph.xmarkCircleFill -> R.drawable.glyph_xmark_circle_fill
        AppGlyph.infoCircleFill -> R.drawable.glyph_info_circle_fill
        AppGlyph.arrowUturnBackwardCircleFill -> R.drawable.glyph_arrow_uturn_backward_circle_fill
        AppGlyph.gearshapeFill -> R.drawable.glyph_gearshape_fill
        AppGlyph.moonFill -> R.drawable.glyph_moon_fill
        AppGlyph.sunMaxFill -> R.drawable.glyph_sun_max_fill
        AppGlyph.pencil -> R.drawable.glyph_pencil
        AppGlyph.trash -> R.drawable.glyph_trash
        AppGlyph.squareAndArrowUp -> R.drawable.glyph_square_and_arrow_up
        AppGlyph.flag -> R.drawable.glyph_flag
        AppGlyph.characterBubble -> R.drawable.glyph_character_bubble
        AppGlyph.plus -> R.drawable.glyph_plus
        AppGlyph.lock -> R.drawable.glyph_lock
        AppGlyph.lockFill -> R.drawable.glyph_lock_fill
        AppGlyph.globe -> R.drawable.glyph_globe
        AppGlyph.map -> R.drawable.glyph_map
        AppGlyph.mapSlash -> R.drawable.glyph_map_slash
        AppGlyph.locationFill -> R.drawable.glyph_location_fill
        AppGlyph.suitcase -> R.drawable.glyph_suitcase
        AppGlyph.suitcaseFill -> R.drawable.glyph_suitcase_fill
        AppGlyph.squareAndPencil -> R.drawable.glyph_square_and_pencil
        AppGlyph.pencilLine -> R.drawable.glyph_pencil_line
        AppGlyph.camera -> R.drawable.glyph_camera
        AppGlyph.cameraFill -> R.drawable.glyph_camera_fill
        AppGlyph.photo -> R.drawable.glyph_photo
        AppGlyph.flagFill -> R.drawable.glyph_flag_fill
        AppGlyph.flagCheckered -> R.drawable.glyph_flag_checkered
        AppGlyph.mappinAndEllipse -> R.drawable.glyph_mappin_and_ellipse
        AppGlyph.arrowUpLeftAndArrowDownRight -> R.drawable.glyph_arrow_up_left_and_arrow_down_right
        AppGlyph.arrowDownRightAndArrowUpLeft -> R.drawable.glyph_arrow_down_right_and_arrow_up_left
        AppGlyph.arrowClockwise -> R.drawable.glyph_arrow_clockwise
        AppGlyph.exclamationmarkTriangleFill -> R.drawable.glyph_exclamationmark_triangle_fill
        AppGlyph.car -> R.drawable.glyph_car
        AppGlyph.tray -> R.drawable.glyph_tray
        AppGlyph.tablecells -> R.drawable.glyph_tablecells
        AppGlyph.squareAndArrowDown -> R.drawable.glyph_square_and_arrow_down
        AppGlyph.magnifyingglass -> R.drawable.glyph_magnifyingglass
        AppGlyph.minusCircle -> R.drawable.glyph_minus_circle
        AppGlyph.plusCircleFill -> R.drawable.glyph_plus_circle_fill
        AppGlyph.checkmarkCircle -> R.drawable.glyph_checkmark_circle
        AppGlyph.circle -> R.drawable.glyph_circle
        AppGlyph.bubbleRight -> R.drawable.glyph_bubble_right
        AppGlyph.heart -> R.drawable.glyph_heart
        AppGlyph.calendar -> R.drawable.glyph_calendar
        AppGlyph.clock -> R.drawable.glyph_clock
        AppGlyph.speedometer -> R.drawable.glyph_speedometer
        AppGlyph.mountain -> R.drawable.glyph_mountain
        AppGlyph.bolt -> R.drawable.glyph_bolt
        AppGlyph.fuelpump -> R.drawable.glyph_fuelpump
        AppGlyph.roadLanes -> R.drawable.glyph_road_lanes
        AppGlyph.arrowTriangleSwap -> R.drawable.glyph_arrow_triangle_swap
        AppGlyph.pause -> R.drawable.glyph_pause
        AppGlyph.play -> R.drawable.glyph_play
        AppGlyph.airplane -> R.drawable.glyph_airplane
        AppGlyph.minus -> R.drawable.glyph_minus
        AppGlyph.chevronUp -> R.drawable.glyph_chevron_up
        AppGlyph.chevronDown -> R.drawable.glyph_chevron_down
        AppGlyph.houseFill -> R.drawable.glyph_house_fill
        AppGlyph.trayFull -> R.drawable.glyph_tray_full
        AppGlyph.faceSmiling -> R.drawable.glyph_face_smiling
        AppGlyph.faceDashed -> R.drawable.glyph_face_dashed
        AppGlyph.starFill -> R.drawable.glyph_star_fill
        AppGlyph.bellFill -> R.drawable.glyph_bell_fill
        AppGlyph.handRaisedFill -> R.drawable.glyph_hand_raised_fill
        AppGlyph.envelopeFill -> R.drawable.glyph_envelope_fill
        AppGlyph.ruler -> R.drawable.glyph_ruler
        AppGlyph.ladybugFill -> R.drawable.glyph_ladybug_fill
        AppGlyph.antennaRadiowaves -> R.drawable.glyph_antenna_radiowaves
        AppGlyph.antennaRadiowavesSlash -> R.drawable.glyph_antenna_radiowaves_slash
        AppGlyph.musicNote -> R.drawable.glyph_music_note
        AppGlyph.speakerWaveFill -> R.drawable.glyph_speaker_wave_fill
        AppGlyph.icloudFill -> R.drawable.glyph_icloud_fill
        AppGlyph.icloudSlash -> R.drawable.glyph_icloud_slash
        AppGlyph.mappin -> R.drawable.glyph_mappin
        AppGlyph.clipboard -> R.drawable.glyph_clipboard
        AppGlyph.tabHome -> R.drawable.glyph_tab_home
        AppGlyph.tabMaps -> R.drawable.glyph_tab_maps
        AppGlyph.tabRecord -> R.drawable.glyph_tab_record
        AppGlyph.tabPlaces -> R.drawable.glyph_tab_places
        AppGlyph.tabProfile -> R.drawable.glyph_tab_profile
    }

/**
 * How much larger than its box the artwork of a glyph is drawn: its optical size.
 *
 * A call site passes a box, in the unit the screens were laid out in: about 1.2 × the point size
 * the same place sets its SF Symbol in (15 pt in the popover rows, 18 dp here). A symbol of font
 * size f fills about f of that box, 0.8 to 1.2 f depending on the shape; the artwork here keeps a
 * margin inside its 24-unit viewport that differs from glyph to glyph (a chevron fills 75 % of it,
 * a house 80 %), so at the same box some came out 15 to 35 % smaller and thinner than the symbol
 * (the 3 November audit, SH-07). The factor is the one that brings each glyph's ink to that of its
 * symbol at box / 1.2, in 0.05 steps: `ink(symbol, semibold) / (1.2 × ink(artwork) / 24)`, on the
 * geometric mean of width and height. The symbols were measured for size only, nothing of them
 * is copied. Strokes grow with the artwork, which is what the «thinner» was.
 *
 * `GlyphSizingTests` renders every glyph and checks it against the symbol's ink box; redrawing an
 * artwork, or giving it a different margin, asks for a new factor there before anywhere else.
 * Glyphs without a symbol (the tab icons, the faces) keep their box.
 */
internal val AppGlyph.opticalScale: Float
    get() = when (this) {
        AppGlyph.clock, AppGlyph.globe, AppGlyph.minusCircle, AppGlyph.pause, AppGlyph.pencilLine,
        AppGlyph.plus -> 1.05f
        AppGlyph.arrowClockwise, AppGlyph.calendar, AppGlyph.exclamationmarkTriangleFill,
        AppGlyph.flag, AppGlyph.flagCheckered, AppGlyph.flagFill, AppGlyph.heart, AppGlyph.lockFill,
        AppGlyph.magnifyingglass, AppGlyph.map -> 1.1f
        AppGlyph.airplane, AppGlyph.arrowDownRightAndArrowUpLeft,
        AppGlyph.arrowUpLeftAndArrowDownRight, AppGlyph.bolt, AppGlyph.camera,
        AppGlyph.characterBubble, AppGlyph.locationFill, AppGlyph.minus, AppGlyph.moonFill,
        AppGlyph.photo, AppGlyph.squareAndPencil, AppGlyph.xmark -> 1.15f
        AppGlyph.bubbleRight, AppGlyph.car, AppGlyph.checkmark, AppGlyph.houseFill,
        AppGlyph.mappinAndEllipse, AppGlyph.pencil, AppGlyph.play, AppGlyph.tablecells, AppGlyph.tray,
        AppGlyph.trayFull, AppGlyph.envelopeFill, AppGlyph.ladybugFill, AppGlyph.speakerWaveFill -> 1.2f
        AppGlyph.bellFill, AppGlyph.ruler, AppGlyph.starFill -> 1.15f
        AppGlyph.icloudSlash -> 1.1f
        AppGlyph.icloudFill -> 1.25f
        AppGlyph.arrowTriangleSwap, AppGlyph.cameraFill, AppGlyph.fuelpump, AppGlyph.mountain,
        AppGlyph.squareAndArrowDown, AppGlyph.suitcase, AppGlyph.suitcaseFill, AppGlyph.trash -> 1.25f
        AppGlyph.roadLanes -> 1.35f
        AppGlyph.squareAndArrowUp, AppGlyph.clipboard -> 1.3f
        else -> 1f
    }

/**
 * iOS `Image(systemName:)` for an [AppGlyph]. `null` description: decoration, hidden from TalkBack.
 * [size] is the glyph's box and what layout sees; the artwork is drawn [opticalScale] times larger
 * inside it and may pass its edge by a hair, which nothing clips.
 */
@Composable
fun Glyph(
    glyph: AppGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    val described = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Box(modifier.size(size).then(described), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(glyph.vectorResource),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * glyph.opticalScale),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}
