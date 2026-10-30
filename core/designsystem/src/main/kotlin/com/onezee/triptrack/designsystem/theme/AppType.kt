package com.onezee.triptrack.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit

// iOS: AppFont.swift, AppType. The one typographic system of the app: one face, one set of roles.
// The reference is the Я screen (ProfileSectionLabel, ProfileTripCardView): the owner, looking at
// it on 26 September, «он офигенен, я хочу чтобы также было по всему приложению». The roles are
// taken from it, not invented.
//
// Hierarchy is carried by SIZE and COLOUR; weight only helps. ExtraBold goes to exactly two roles,
// the summary-tile number and the screen title: handing it out freely is what made screens
// «жирнючие». Above that the ceiling is bold for headings, semibold for numbers, medium for names,
// regular for the rest. Roles are named by meaning, so a size changes in one place.
//
// Tracking stays a separate value, as in iOS: call sites set it where the iOS call site does.
// Every role is a fixed size (see `inter`); text that follows the font scale uses `interScaled`.
object AppType {
    /** The hero number. One per screen; the size is the call site's, «189 миль» and «1 697 км» differ in length. */
    @Composable
    @ReadOnlyComposable
    fun display(size: Float = 34f): TextStyle = inter(size, InterWeight.semibold)

    /** Screen title, 28/800, from Лента: the second of the two ExtraBold roles. */
    val title: TextStyle @Composable @ReadOnlyComposable get() = inter(28f, InterWeight.heavy)
    val titleTracking: TextUnit @Composable @ReadOnlyComposable get() = points(-0.56f)

    /** Title of a sheet or a modal screen. */
    val sheetTitle: TextStyle @Composable @ReadOnlyComposable get() = inter(22f, InterWeight.bold)

    /** Title of the chosen object in a header (a region, a road). */
    val headerTitle: TextStyle @Composable @ReadOnlyComposable get() = inter(20f, InterWeight.semibold)

    /**
     * The number in a summary tile, several in a row, so smaller than the hero. The one place of
     * ExtraBold besides the title: beside it the caption can afford to be small and quiet.
     */
    val statValue: TextStyle @Composable @ReadOnlyComposable get() = inter(19f, InterWeight.heavy)
    val statValueTracking: TextUnit @Composable @ReadOnlyComposable get() = points(-0.19f)

    /** The unit beside a tile number («мили» next to «4.2»): half the size, secondary colour. */
    val statUnit: TextStyle @Composable @ReadOnlyComposable get() = inter(11f, InterWeight.semibold)

    /**
     * The caption under a tile number («ДИСТАНЦИЯ»), capitals in tertiary colour. Capitals are
     * legitimate here: it labels a quantity, it is not a heading.
     */
    val statCaption: TextStyle @Composable @ReadOnlyComposable get() = inter(11f, InterWeight.bold)
    val statCaptionTracking: TextUnit @Composable @ReadOnlyComposable get() = points(0.55f)

    /**
     * Section heading: dark ink, bold, normal case. The small grey capitals Atlas and Places used
     * from the HTML mock were exactly what set those tabs apart from the rest of the app.
     */
    val section: TextStyle @Composable @ReadOnlyComposable get() = inter(16f, InterWeight.bold)
    val sectionTracking: TextUnit @Composable @ReadOnlyComposable get() = points(-0.16f)

    /** Name of a card or a list row. */
    val itemTitle: TextStyle @Composable @ReadOnlyComposable get() = inter(16f, InterWeight.medium)

    /** The number on the right of a list row. */
    val itemValue: TextStyle @Composable @ReadOnlyComposable get() = inter(16f, InterWeight.semibold)

    /** Words beside the hero number: they explain it, they do not compete with it. */
    val unit: TextStyle @Composable @ReadOnlyComposable get() = inter(17f)
    val body: TextStyle @Composable @ReadOnlyComposable get() = inter(15f)
    val meta: TextStyle @Composable @ReadOnlyComposable get() = inter(13f)
    val caption: TextStyle @Composable @ReadOnlyComposable get() = inter(12f)
    val button: TextStyle @Composable @ReadOnlyComposable get() = inter(16f, InterWeight.semibold)

    /** Screen title in a push navigation bar: it sits between two buttons, not above content. */
    val navTitle: TextStyle @Composable @ReadOnlyComposable get() = inter(17f, InterWeight.semibold)

    /** A word instead of a button: «Выбрать», «Готово», «Отмена» in a header. */
    val action: TextStyle @Composable @ReadOnlyComposable get() = inter(15f, InterWeight.semibold)
    val chip: TextStyle @Composable @ReadOnlyComposable get() = inter(13f, InterWeight.medium)

    /**
     * Active segment and active sort chip: same size, one step heavier. A step and not a colour:
     * the fill already shows it is active, a second signal would be noise.
     */
    val chipActive: TextStyle @Composable @ReadOnlyComposable get() = inter(13f, InterWeight.semibold)
}
