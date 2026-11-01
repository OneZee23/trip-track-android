package com.onezee.triptrack.map.attribution

import android.content.ActivityNotFoundException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.R
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.SheetCloseCircle
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter

// iOS: ContentSizedSheet.swift, SettingsOptionPicker. Legal links use the house sheet on every map.
@Composable
fun MapAttributionSheet(
    attributions: List<MapAttribution>,
    onDismissRequest: () -> Unit,
    onOpenLink: ((String) -> Unit)? = null,
) {
    val strings = appStrings
    val uriHandler = LocalUriHandler.current
    var linkUnavailable by remember { mutableStateOf(false) }
    ContentSizedSheet(onDismissRequest = onDismissRequest) {
        val sheet = this
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)
                .testTag("map_attribution_header"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(strings.tr(R.string.android_map_attribution), style = inter(20f, InterWeight.semibold),
                color = AppTheme.colors.text, modifier = Modifier.weight(1f).semantics { heading() })
            SheetCloseCircle(onClick = { sheet.dismiss() }, modifier = Modifier.testTag("map_attribution_close"))
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 20.dp)
            .testTag("map_attribution_links")) {
            attributions.forEachIndexed { index, attribution ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) {
                        linkUnavailable = false
                        try {
                            if (onOpenLink != null) onOpenLink(attribution.url) else uriHandler.openUri(attribution.url)
                        } catch (_: IllegalArgumentException) {
                            linkUnavailable = true
                        } catch (_: ActivityNotFoundException) {
                            linkUnavailable = true
                        }
                    }.heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 12.dp)
                        .testTag("map_attribution_link_$index"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(attribution.title, style = inter(15f, InterWeight.medium),
                        color = if (AppTheme.isDark) AppTheme.colors.text else AppTheme.accent,
                        modifier = Modifier.weight(1f))
                    Glyph(AppGlyph.chevronRight, contentDescription = null, size = 16.dp, tint = AppTheme.colors.textSecondary)
                }
            }
        }
        if (linkUnavailable) Text(strings.tr(R.string.android_map_link_unavailable), style = inter(13f),
            color = AppTheme.red, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("map_attribution_error"))
    }
}
