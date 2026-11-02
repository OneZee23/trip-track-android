// iOS: Drive2LogbookRow.swift, Drive2Link.swift (Drive2Audience)
package com.onezee.triptrack.feature.garage

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.R
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.logic.Drive2Link
import java.util.Locale

/**
 * iOS: Drive2LogbookRow.swift. The car's DRIVE2 logbook as a card: their favicon, bundled once and
 * shown only here next to the link it names (owner, 23 Oct 2026; nothing is fetched), «Бортжурнал на
 * DRIVE2», a chevron. A linked logbook opens in the browser or the DRIVE2 app; an unlinked one invites
 * the owner with the favicon dimmed, and only where [onConnect] is given. No link and no invitation:
 * no row at all.
 */
@Composable
fun Drive2LogbookRow(link: String?, modifier: Modifier = Modifier, onConnect: (() -> Unit)? = null) {
    val parsed = link?.let(Drive2Link::normalized)
    if (parsed == null && onConnect == null) return
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val uri = LocalUriHandler.current
    PressableCard(
        onClick = { haptics.tap(); if (parsed != null) uri.openUri(parsed) else onConnect?.invoke() },
        modifier = modifier.fillMaxWidth().testTag("garage_drive2_row"),
    ) {
        Row(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.drive2_icon), null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).alpha(if (parsed == null) 0.55f else 1f))
            Text(
                if (parsed == null) strings.garageDrive2Connect() else strings.garageDrive2Title(),
                Modifier.weight(1f),
                style = inter(15f, if (parsed == null) InterWeight.medium else InterWeight.bold),
                color = if (parsed == null) colors.textSecondary else colors.text,
            )
            Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = 13.dp)
        }
    }
}

/**
 * iOS: Drive2Link.swift, Drive2Audience.invitesOnPage. The empty row invites only those DRIVE2 is
 * for: the Russian, Ukrainian and Kazakh interface, or a phone set to Russia, Belarus or Kazakhstan.
 */
object Drive2Audience {
    val languages = setOf("ru", "uk", "kk")
    val regions = setOf("RU", "BY", "KZ")

    fun invitesOnPage(language: String, region: String? = Locale.getDefault().country): Boolean =
        language in languages || region?.uppercase() in regions
}
