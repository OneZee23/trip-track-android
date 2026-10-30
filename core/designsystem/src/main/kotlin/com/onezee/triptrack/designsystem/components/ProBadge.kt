package com.onezee.triptrack.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.pressStart2P

/**
 * iOS: ProBadge.swift. «PRO» beside a name: a pill set in the app's pixel face. A WORD, because
 * «PRO» is not translated into any of the thirteen languages, so the pill has one length
 * everywhere. Whether to show it is [PlusBadgeVisibility]'s call, the same in all four places.
 *
 * The glyph size is FIXED and does not follow [height]: seven points are the seven pixel rows of
 * the glyph as drawn in the mock, and a pixel face under six points turns to mush. The badge never
 * shrinks in favour of the name; the name is cut first.
 */
@Composable
fun ProBadge(modifier: Modifier = Modifier, height: Dp = 18.dp) {
    val label = appStrings.plusBadgeLabel()
    Box(
        modifier
            .height(height)
            .wrapContentWidth(unbounded = true)
            .background(AppTheme.accent, CapsuleShape)
            .padding(horizontal = 6.dp)
            .clearAndSetSemantics { contentDescription = label }
            .testTag("pro_badge"),
        contentAlignment = Alignment.Center,
    ) {
        // Verbatim, like iOS `Text(verbatim:)`: the mark is not copy.
        Text("PRO", style = pressStart2P(ProGlyphSize), color = Color.White, maxLines = 1, softWrap = false)
    }
}

private const val ProGlyphSize = 7f

// iOS: ProBadge.swift, PlusBadgeVisibility. One rule for the four places the badge appears (feed,
// comments, own profile, someone else's profile), so they cannot drift apart.
object PlusBadgeVisibility {
    /**
     * [isPlus] is what the server said about THIS account; `null` (an old server sent no field) is
     * «not said», so nothing is drawn, but no «no subscription» is concluded either. [showsOwnBadge]
     * is the privacy toggle, and it governs the OWN badge only: a setting of mine may not hide
     * somebody else's badge.
     */
    fun shows(isPlus: Boolean?, isOwn: Boolean, showsOwnBadge: Boolean): Boolean {
        if (!saysPlus(isPlus)) return false
        return if (isOwn) showsOwnBadge else true
    }

    /**
     * Did the server say this account has a live subscription? `null` is answered ONCE for all of
     * someone else's cosmetics: no. While the rule stood three times, background and frame read
     * «not said» as yes and the badge as no.
     */
    fun saysPlus(isPlus: Boolean?): Boolean = isPlus == true
}
