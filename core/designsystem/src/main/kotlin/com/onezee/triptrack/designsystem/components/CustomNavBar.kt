// iOS: CustomNavBar.swift, NavBackButton.swift
package com.onezee.triptrack.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.points

/**
 * iOS `\.navBarInSheet`: the bar sits at the top of a sheet, where there is no status bar above it
 * and UIKit draws the grabber over the first 10 pt. [ContentSizedSheet] sets it for what it holds;
 * a screen that iOS marks the same way while it is pushed (the garage) passes `inSheet` itself.
 */
val LocalNavBarInSheet = compositionLocalOf { false }

/**
 * iOS: CustomNavBar.swift. The one top bar of every pushed screen: the title centred over the
 * whole width, the back circle at the leading edge and the screen's own actions trailing.
 *
 * The numbers are the iOS ones, from the bar's own comments. The controls are inset 20 from the
 * edge, not the artboard's 10: on a 440 pt phone the artboard value puts them on the curved glass.
 * The title is 18 heavy, with 72 of clearance from each side so a long one truncates before it
 * meets a control. The row clears the status bar by 10 (20 in a sheet, under the grabber) and
 * keeps 12 below. [largeTitle] is the display title of the two collection screens and the ranks
 * («Статистика», «Достижения», «Уровни»): 28 heavy, tracking −0.56, shrinking to 70 % before it
 * truncates. [subtitle] is the second line, the owner's name on someone else's screen.
 *
 * The status bar is the caller's: put the bar under `statusBarsPadding()`, as every screen does.
 * [onBack] null hides the back circle, for a sheet root that closes from the trailing side.
 */
@Composable
fun CustomNavBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    largeTitle: Boolean = false,
    inSheet: Boolean = LocalNavBarInSheet.current,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = AppTheme.colors
    val topInset = if (inSheet) NavBarSheetTopInset else NavBarTopInset
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = NavCircleDiameter + NavBarBottomInset + topInset)
            .padding(start = NavBarSideInset, end = NavBarSideInset, top = topInset, bottom = NavBarBottomInset),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = NavBarTitleClearance),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (largeTitle) {
                Text(
                    title,
                    Modifier.semantics { heading() }.testTag("nav_title"),
                    style = inter(LargeTitleSize, InterWeight.heavy),
                    color = colors.text,
                    letterSpacing = points(LargeTitleTracking),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = points(LargeTitleSize * LargeTitleMinimumScale),
                        maxFontSize = points(LargeTitleSize),
                        stepSize = points(0.5f),
                    ),
                )
            } else {
                Text(
                    title,
                    Modifier.semantics { heading() }.testTag("nav_title"),
                    style = inter(TitleSize, InterWeight.heavy),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    Modifier.testTag("nav_subtitle"),
                    style = inter(11f, InterWeight.medium),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) NavCircleButton(AppGlyph.chevronLeft, appStrings.back(), onBack, Modifier.testTag("nav_back"))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

/** Inset of the controls from the screen edge. */
val NavBarSideInset = 20.dp

/** Under the status bar on a pushed screen. */
val NavBarTopInset = 10.dp

/** Under the grabber, in a sheet. */
val NavBarSheetTopInset = 20.dp

val NavBarBottomInset = 12.dp

/** Both controls (40 + the 20 inset) and a gap, so a long title truncates before it touches one. */
private val NavBarTitleClearance = 72.dp

private const val TitleSize = 18f
private const val LargeTitleSize = 28f
private const val LargeTitleMinimumScale = 0.7f

/** iOS `.tracking(-0.56)` on the 28 pt title. */
private const val LargeTitleTracking = -0.56f
