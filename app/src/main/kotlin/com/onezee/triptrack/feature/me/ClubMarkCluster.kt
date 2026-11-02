// iOS: ClubMarkCluster.swift, ClubCatalog.swift, GroupsComingSoonView.swift
package com.onezee.triptrack.feature.me

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.R
import com.onezee.triptrack.designsystem.theme.AppTheme

/**
 * iOS: ClubCatalog.swift (Club.asset, Club.tint). The drawn marks of the clubs the teaser promises, in
 * the catalogue's order. The marks are deliberately generic, a roadster, a gear, a truck, a
 * mountain: a recognisable Miata or Impreza silhouette is somebody else's trade dress. The tints are
 * fixed, one value in both themes, as on iOS. Only the first four have a place on Android: the
 * catalogue and the club page behind «Посмотреть что будет» are not ported.
 */
internal enum class ClubMark(val id: String, @param:DrawableRes val asset: Int, val tint: Color) {
    miata("miata", R.drawable.club_miata, Color(0.98f, 0.90f, 0.90f)),
    vag("vag", R.drawable.club_vag, Color(0.89f, 0.93f, 0.99f)),
    trucking("trucking", R.drawable.club_trucking, Color(0.88f, 0.95f, 0.93f)),
    offroad("offroad", R.drawable.club_offroad, Color(0.90f, 0.96f, 0.88f)),
}

/**
 * iOS: ClubMarkCluster.swift. The club marks arranged as a group, the hero of the clubs screen. It
 * replaced a pixel car in a ring, an image that said «vehicle» on a screen about communities:
 * overlapping marks read as a group of somebodies, which is what a club is. One image for TalkBack,
 * not four.
 */
@Composable
internal fun ClubMarkCluster(modifier: Modifier = Modifier, size: Dp = 66.dp, overlap: Float = 0.30f) {
    val card = AppTheme.colors.card
    Row(modifier.semantics { hideFromAccessibility() }.testTag("club_mark_cluster"), horizontalArrangement = Arrangement.spacedBy(-(size * overlap))) {
        ClubMark.entries.forEach { club ->
            Box(Modifier.size(size).background(club.tint, CircleShape).border(3.dp, card, CircleShape), contentAlignment = Alignment.Center) {
                Image(ImageBitmap.imageResource(club.asset), contentDescription = null, modifier = Modifier.fillMaxSize().padding(size * 0.18f),
                    contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
            }
        }
    }
}
