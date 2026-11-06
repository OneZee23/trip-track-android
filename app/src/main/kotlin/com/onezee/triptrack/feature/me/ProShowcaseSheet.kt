// iOS: ProShowcaseSheet.swift, ProShowcaseTileView.swift, ProShowcaseHost.swift
package com.onezee.triptrack.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.feature.social.ui.avatarFrame
import com.onezee.triptrack.feature.social.ui.drawCover
import com.onezee.triptrack.feature.settings.MeAction
import com.onezee.triptrack.feature.settings.MeSheetHeader
import com.onezee.triptrack.model.*

@Composable
internal fun ProShowcaseSheet(kind: ProShowcaseKind, current: String, isPlus: Boolean, signedIn: Boolean, cloudSyncEnabled: Boolean,
                              profileBackground: ProfileBackground = ProfileBackground.none, avatarFrame: AvatarFrame = AvatarFrame.none,
                              avatarEmoji: String = "😎", displayName: String = "",
                              vehicle: Vehicle? = null, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val strings = appStrings
    var preview by remember(kind, current) { mutableStateOf(kind.effective(current, isPlus)) }
    val tiles = kind.tiles()
    val free = tiles.filterNot(Tile::isPremium)
    val paid = tiles.filter(Tile::isPremium)
    ContentSizedSheet(onDismiss, background = AppTheme.colors.bg, detent = SheetDetent.large) {
        val sheet = this
        MeSheetHeader(kind.showcaseTitle(strings), { sheet.dismiss() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ShowcasePreview(kind, preview, profileBackground, avatarFrame, avatarEmoji, displayName, vehicle)
            if (kind == ProShowcaseKind.routeLine) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.showcaseFree(), style = AppType.section, color = AppTheme.colors.text)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppTheme.colors.cardAlt).clickable { preview = ""; onPick("") }
                        .padding(horizontal = 14.dp, vertical = 10.dp).testTag("pro_tile_speed"), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(32.dp).background(AppTheme.accentBg, CircleShape), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize().padding(6.dp)) { drawLine(AppTheme.green, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width * .35f, size.height * .4f), 2.dp.toPx()); drawLine(AppTheme.yellow, androidx.compose.ui.geometry.Offset(size.width * .35f, size.height * .4f), androidx.compose.ui.geometry.Offset(size.width, 0f), 2.dp.toPx()) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(strings.showcaseLineSpeed(), style = AppType.itemTitle, color = AppTheme.colors.text)
                            Text(strings.showcaseLineSpeedSub(), style = AppType.meta, color = AppTheme.colors.textSecondary)
                        }
                        if (preview.isEmpty()) Text("✓", style = AppType.itemTitle, color = AppTheme.accent)
                    }
                }
            } else ShowcaseGroup(strings.showcaseFree(), free, preview, kind) { id -> preview = id; onPick(id) }
            if (paid.isNotEmpty()) ShowcaseGroup(strings.showcasePro(), paid, preview, kind) { id -> preview = id; onPick(id) }
            val footnote = when (kind) {
                ProShowcaseKind.profileBackground, ProShowcaseKind.avatarFrame -> if (signedIn) null else strings.showcaseVisibleAfterSignIn()
                ProShowcaseKind.vehicleCard -> if (signedIn && cloudSyncEnabled) strings.vehicleCardStylePickerFootnote() else strings.showcaseVisibleAfterSync()
                ProShowcaseKind.routeLine -> strings.routeLinePickerFootnote()
            }
            if (footnote != null) Text(footnote, style = AppType.caption, color = AppTheme.colors.textTertiary)
            MeAction(strings.showcaseDone(), { sheet.dismiss() }, Modifier.fillMaxWidth().testTag("pro_showcase_done"))
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ShowcasePreview(kind: ProShowcaseKind, id: String, background: ProfileBackground, currentFrame: AvatarFrame, avatar: String, name: String, vehicle: Vehicle?) {
    when (kind) {
        ProShowcaseKind.profileBackground -> ProfileShowcasePreview(ProfileBackground.fromRawValue(id) ?: ProfileBackground.none, currentFrame, avatar, name)
        ProShowcaseKind.avatarFrame -> ProfileShowcasePreview(background, AvatarFrame.fromRawValue(id) ?: AvatarFrame.none, avatar, name)
        ProShowcaseKind.vehicleCard -> {
            val selected = (vehicle ?: Vehicle(avatarEmoji = "pixel_car_blue")).copy(cardStyle = id.takeIf(String::isNotEmpty))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppTheme.colors.cardAlt).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                com.onezee.triptrack.feature.garage.VehicleStage(selected, width = 210.dp, isPlus = true)
                Text(selected.name.ifBlank { appStrings.proDemoCarEmptyTitle() }, style = inter(13f, InterWeight.semibold), color = AppTheme.colors.text, maxLines = 1)
            }
        }
        ProShowcaseKind.routeLine -> Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(AppTheme.colors.cardAlt).testTag("pro_showcase_preview")) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 22.dp)) {
                val path = Path().apply { moveTo(0f, size.height * .72f); cubicTo(size.width * .18f, size.height * .76f, size.width * .18f, size.height * .32f, size.width * .38f, size.height * .43f); cubicTo(size.width * .55f, size.height * .52f, size.width * .63f, size.height * .16f, size.width, size.height * .24f) }
                if (id.isEmpty()) {
                    drawPath(path, AppTheme.green, style = Stroke(width = 5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(8.dp.toPx())))
                } else drawPath(path, routeColor(id), style = Stroke(width = 5.dp.toPx()))
            }
        }
    }
}

@Composable
private fun ProfileShowcasePreview(background: ProfileBackground, frame: AvatarFrame, avatar: String, name: String) {
    val displayName = name.ifBlank { appStrings.meGuestName() }
    Row(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp))
        .then(if (background == ProfileBackground.none) Modifier.background(AppTheme.colors.cardAlt) else Modifier.drawBehind { drawCover(background) })
        .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).background(androidx.compose.ui.graphics.Color.White.copy(alpha = .18f), CircleShape)
            .border(2.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = .9f), CircleShape)
            .avatarFrame(frame, 3.dp), contentAlignment = Alignment.Center) { Text(avatar, style = inter(20f)) }
        Text(displayName, style = inter(17f, InterWeight.semibold), color = if (background == ProfileBackground.none) AppTheme.colors.text else androidx.compose.ui.graphics.Color.White, maxLines = 1)
    }
}

@Composable
private fun ShowcaseGroup(title: String, tiles: List<Tile>, selected: String, kind: ProShowcaseKind, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = AppType.section, color = AppTheme.colors.text)
            Text(tiles.size.toString(), style = AppType.meta, color = AppTheme.colors.textTertiary)
        }
        LazyVerticalGrid(GridCells.Fixed(3), modifier = Modifier.heightIn(max = if (tiles.size > 6) 340.dp else 230.dp), userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tiles.size) { index ->
                val tile = tiles[index]
                Column(Modifier.testTag("pro_tile_${tile.id.ifEmpty { "none" }}").clickable { onPick(tile.id) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.fillMaxWidth().height(if (kind == ProShowcaseKind.avatarFrame) 84.dp else 76.dp)
                        .clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.cardAlt)
                        .then(if (selected == tile.id) Modifier.border(2.dp, AppTheme.accent, RoundedCornerShape(12.dp)) else Modifier), contentAlignment = Alignment.Center) {
                        ShowcaseSwatch(kind, tile.id, Modifier.size(44.dp))
                    }
                    val label = if (tile.id.isEmpty()) when (kind) {
                        ProShowcaseKind.profileBackground, ProShowcaseKind.avatarFrame -> appStrings.cosmeticDefaultOption()
                        else -> ""
                    } else tile.name
                    if (label.isNotEmpty()) Text(label, style = inter(11f, InterWeight.medium), color = if (selected == tile.id) AppTheme.accent else AppTheme.colors.textSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ShowcaseSwatch(kind: ProShowcaseKind, id: String, modifier: Modifier) {
    when (kind) {
        ProShowcaseKind.profileBackground -> {
            val background = ProfileBackground.fromRawValue(id) ?: ProfileBackground.none
            if (background == ProfileBackground.none) Text("∅", style = inter(24f), color = AppTheme.colors.textTertiary, modifier = modifier)
            else Box(modifier.drawBehind { drawCover(background) })
        }
        ProShowcaseKind.avatarFrame -> Box(modifier.background(AppTheme.colors.card, CircleShape).avatarFrame(AvatarFrame.fromRawValue(id) ?: AvatarFrame.none, 3.dp), contentAlignment = Alignment.Center) {
            Text("А", style = inter(20f, InterWeight.bold), color = AppTheme.colors.text)
        }
        ProShowcaseKind.vehicleCard -> {
            val style = VehicleCardStyle.fromRawValue(id) ?: VehicleCardStyle.none
            com.onezee.triptrack.feature.garage.PixelAsset("platform_" + if (style == VehicleCardStyle.none) "asphalt" else style.rawValue.removePrefix("card_"), modifier)
        }
        ProShowcaseKind.routeLine -> Box(modifier.background(routeColor(id), CircleShape))
    }
}

private fun routeColor(id: String) = when (RouteLineStyle.fromRawValue(id)) {
    RouteLineStyle.amber -> androidx.compose.ui.graphics.Color(0xFFF5A623)
    RouteLineStyle.crimson -> androidx.compose.ui.graphics.Color(0xFFE03344)
    RouteLineStyle.violet -> androidx.compose.ui.graphics.Color(0xFF8B5CF6)
    RouteLineStyle.teal -> androidx.compose.ui.graphics.Color(0xFF18B5A8)
    RouteLineStyle.lime -> androidx.compose.ui.graphics.Color(0xFF86CB2E)
    RouteLineStyle.ice -> androidx.compose.ui.graphics.Color(0xFF6FB6E8)
    else -> AppTheme.accent
}

private fun ProShowcaseKind.showcaseTitle(strings: AppStrings) = when (this) {
    ProShowcaseKind.profileBackground -> strings.showcaseBg()
    ProShowcaseKind.avatarFrame -> strings.showcaseFrame()
    ProShowcaseKind.vehicleCard -> strings.showcaseCar()
    ProShowcaseKind.routeLine -> strings.showcaseLine()
}
