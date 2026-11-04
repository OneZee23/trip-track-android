// iOS: VehicleEditFormView.avatarCard, avatarHero, colorRow, swatchButton, avatarCell, powertrainCard
package com.onezee.triptrack.feature.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.model.Powertrain
import com.onezee.triptrack.model.Vehicle

/**
 * iOS avatarCard: a card of 14 inside, radius 16, headed «Аватар». The choice is two axes, what it is and what colour: the chosen
 * car is shown once, large, on the dark sprite plate (168 × 106 on a plate of 208 × 130, radius 18), and the rows under it are only
 * controls. «Тип» is a grid of four tiles of 68 on the same plate (the chosen one has the accent at 16 % and a 2 pt accent frame),
 * absent while the vehicle has a single silhouette: a row holding one tile reads as a choice taken away. «Цвет» is a row of swatches
 * of 34 in targets of 44, 10 apart, that scrolls; the chosen one has a 2 pt ring 4 outside the dot.
 */
@Composable
internal fun VehicleAvatarCard(vehicle: Vehicle, onAvatar: (color: String, style: String) -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val color = VehicleAvatar.drawnColor(vehicle.avatarEmoji) ?: "orange"
    val styles = VehicleAvatar.styles(vehicle.type)
    val style = vehicle.avatarStyle.takeIf { it in styles } ?: styles.first()
    val plate = Brush.verticalGradient(listOf(AppTheme.spritePlateTop, AppTheme.spritePlateBottom))
    Column(modifier.fillMaxWidth().surfaceCard(16.dp).padding(14.dp).testTag("vehicle_avatar_card"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GarageSectionLabel(strings.avatarSection())
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val asset = VehicleAvatar.asset(vehicle)
            if (asset != null) Box(Modifier.size(208.dp, 130.dp).background(plate, RoundedCornerShape(18.dp)).testTag("vehicle_avatar_hero"), contentAlignment = Alignment.Center) {
                // Fitted, as iOS draws it (`scaledToFit` in 168 × 106): stretched to the box, the
                // 256 × 204 sprite came out squashed (owner on his phone, 4 Nov 2026).
                PixelAsset(asset, Modifier.size(168.dp, 106.dp), ContentScale.Fit)
            } else Text(vehicle.avatarEmoji, Modifier.height(88.dp), style = inter(56f))
        }
        if (styles.size > 1) {
            GarageSectionLabel(strings.avatarStyleSection())
            styles.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { value ->
                        val chosen = value == style
                        val label = avatarStyleLabel(value, strings)
                        val tile = RoundedCornerShape(12.dp)
                        Box(Modifier.weight(1f).height(68.dp).background(plate, tile).then(if (chosen) Modifier.background(AppTheme.accent.copy(alpha = 0.16f), tile).border(2.dp, AppTheme.accent, tile) else Modifier)
                            .clip12().clickable(role = Role.RadioButton, onClickLabel = label) { onAvatar(color, value) }
                            .semantics { selected = chosen; contentDescription = label }.testTag("vehicle_avatar_style_$value"), contentAlignment = Alignment.Center) {
                            // Drawn in the colour that is chosen now, so the row shows shapes and not a paint chart.
                            PixelAsset("pixel_${value}_$color", Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 7.dp), ContentScale.Fit)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        GarageSectionLabel(strings.avatarColorSection())
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VehicleAvatar.colors.forEach { value ->
                val chosen = value == color
                val label = avatarColorLabel(value, strings)
                // 34 of paint inside a target of 44: the ring sits outside the dot with a gap, so the chosen colour is never squeezed by its own marker.
                Box(Modifier.size(44.dp).clickable(role = Role.RadioButton, onClickLabel = label) { onAvatar(value, style) }.semantics { selected = chosen; contentDescription = label }.testTag("vehicle_avatar_color_$value"), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(34.dp).background(avatarSwatch(value), CircleShape).then(if (swatchNeedsBorder(value)) Modifier.border(1.dp, colors.textTertiary.copy(alpha = 0.35f), CircleShape) else Modifier))
                    if (chosen) Box(Modifier.size(42.dp).border(2.dp, AppTheme.accent, CircleShape))
                }
            }
        }
    }
}

private fun Modifier.clip12(): Modifier = this.then(Modifier.clip(RoundedCornerShape(12.dp)))

/** iOS VehicleAvatar.swatch: the base of each colour's ramp, so the dot reads as the paint the sprite wears. */
internal fun avatarSwatch(color: String): Color = Color(when (color) {
    "white" -> 0xFFD4D9E3; "black" -> 0xFF2A2C33; "silver" -> 0xFFA2A8B4; "gray" -> 0xFF707580
    "red" -> 0xFFE03B2C; "blue" -> 0xFF3B7DD8; "green" -> 0xFF4CAF50; "yellow" -> 0xFFF2C41B; else -> 0xFFEB571E
})

/** iOS VehicleAvatar.swatchNeedsBorder: only a swatch this pale (luminance above 0.72) vanishes into a light card without a hairline. */
internal fun swatchNeedsBorder(color: String): Boolean {
    val c = avatarSwatch(color)
    return 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue > 0.72f
}

/**
 * iOS powertrainCard: «Двигатель» is a card of its own, no heading above it: the title in 15 medium over «Чем машина едет» in 12,
 * the value in 13 medium and a chevron, because the row opens a sheet. Above the fuel block, next to what it commands.
 */
@Composable
internal fun PowertrainCard(powertrain: Powertrain, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    PressableCard(onClick = onClick, modifier = modifier.fillMaxWidth().testTag("vehicle_powertrain_row")) {
        Row(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(strings.vehiclePowertrainTitle(), style = inter(15f, InterWeight.medium), color = colors.text)
                Text(strings.vehiclePowertrainSubtitle(), style = inter(12f), color = colors.textTertiary)
            }
            Text(powertrainLabel(powertrain, strings), style = inter(13f, InterWeight.medium), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = 14.4.dp)
        }
    }
}
