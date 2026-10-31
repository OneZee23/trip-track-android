// iOS: ProfileHeroCard.swift, ProfileGarageHeader.swift, ProfileRoadScene.swift
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.garage.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.DistanceUnit
import java.util.UUID

@Composable
internal fun ProfileHeroCard(state: ProfileHeroState, unit: DistanceUnit, onSettings: () -> Unit,
                             onVehicle: (UUID) -> Unit, onAdd: () -> Unit, onProfile: () -> Unit = {}, onLevel: () -> Unit = {},
                             coverPhotos: List<ProfileCoverPhoto> = emptyList(), api: com.onezee.triptrack.network.ApiClient? = null, onPhotos: () -> Unit = {},
                             onStats: () -> Unit = {}) {
    if (coverPhotos.isNotEmpty()) {
        ProfilePhotoHero(state, unit, coverPhotos, api, onSettings, onVehicle, onProfile, onLevel, onPhotos, onStats)
        return
    }
    val strings = appStrings
    val gradient = state.identity.effectiveBackground.colors()
    val colors = if (gradient.isEmpty()) AppTheme.colors else AppTheme.colors.copy(text = Color.White, textSecondary = Color.White.copy(alpha = .85f))
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.roadHeroCard)
        .drawBehind { if (gradient.isNotEmpty()) { drawRect(Brush.linearGradient(gradient)); drawRect(Color.Black.copy(alpha = .64f)) } }
        .border(1.dp, if (gradient.isEmpty()) AppTheme.accent.copy(alpha = .22f) else Color.Transparent, shape).testTag("profile_hero")) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(60.dp).background(colors.cardAlt, CircleShape).clickable(role = Role.Button, onClickLabel = strings.myProfileTitle(), onClick = onProfile), contentAlignment = Alignment.Center) {
                    Text(state.identity.avatarEmoji, style = inter(32f))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(state.identity.name.ifBlank { strings.meGuestName() }, Modifier.testTag("profile_identity").clickable(role = Role.Button, onClick = onProfile), style = interScaled(20f, InterWeight.bold), color = colors.text)
                    Text(strings.tr(com.onezee.triptrack.designsystem.R.string.android_level_label, state.identity.level), Modifier.background(colors.goldBg, CircleShape).clickable(role = Role.Button, onClick = onLevel).padding(horizontal = 9.dp, vertical = 6.dp),
                        style = interScaled(11f, InterWeight.semibold), color = AppTheme.gold)
                }
                Box(Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = strings.settingsTitle(), onClick = onSettings).testTag("profile_gear"), contentAlignment = Alignment.Center) {
                    Glyph(AppGlyph.gearshapeFill, strings.settingsTitle(), tint = colors.textSecondary, size = 23.dp)
                }
            }
            if (state.identity.bio.isNotBlank()) Text(state.identity.bio, style = interScaled(14f), color = colors.text)
        }
        BoxWithConstraints {
            val stageWidth = minOf(maxWidth - 40.dp, 300.dp)
            val roadHeight = (state.cars.maxOfOrNull { VehicleAvatar.stageHeight(it.vehicle.avatarStyle, stageWidth).value } ?: 186f).dp + 20.dp
            if (state.cars.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (state.showsRoad) Image(painterResource(MeArtwork.drawables.getValue("road_${state.roadTime.name}")), null,
                        Modifier.fillMaxWidth().height(211.dp), contentScale = ContentScale.FillBounds)
                    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(130.dp).border(1.dp, colors.textSecondary.copy(alpha = .4f), RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button, onClick = onAdd).testTag("garage_header_add"), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("+", style = inter(24f), color = colors.textSecondary)
                            Text(strings.addVehicleTitle(), style = interScaled(14f, InterWeight.semibold), color = colors.textSecondary)
                        }
                    }
                }
            } else {
                val pager = rememberPagerState { state.cars.size }
                Column {
                    HorizontalPager(pager, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) { index ->
                        val car = state.cars[index]
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onVehicle(car.vehicle.id) }.testTag("garage_header_vehicle"), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().height(roadHeight), contentAlignment = Alignment.BottomCenter) {
                                if (car.photoFilename != null && state.showsRoad) VehicleLocalPhoto(car.photoFilename, Modifier.fillMaxSize(), remoteURL = car.remoteURL)
                                else {
                                    if (state.showsRoad) Image(painterResource(MeArtwork.drawables.getValue("road_${state.roadTime.name}")), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                                    VehicleStage(car.vehicle, Modifier.padding(vertical = 10.dp), stageWidth, car.photoFilename, state.identity.isPlus, car.remoteURL)
                                }
                            }
                            Text(car.vehicle.name.ifBlank { strings.defaultVehicleName() }, Modifier.padding(top = 8.dp), style = interScaled(17f, InterWeight.semibold), color = colors.text)
                            Text(listOf(car.vehicle.make, car.vehicle.model, car.vehicle.year.takeIf { it > 0 }?.toString().orEmpty()).filter(String::isNotEmpty).joinToString(" · "),
                                Modifier.padding(top = 8.dp, bottom = 14.dp), style = interScaled(12f), color = colors.textSecondary)
                        }
                    }
                    if (state.cars.size > 1) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                        state.cars.indices.forEach { index -> Box(Modifier.size(6.dp).background(if (index == pager.currentPage) AppTheme.accent else colors.textSecondary.copy(alpha = .3f), CircleShape)) }
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.accent.copy(alpha = .18f)))
            // iOS onTapStats: the three numbers open the full statistics.
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onStats).testTag("profile_stats_strip")) {
                ProfileStat(state.tripCount.toString(), strings.trips(), Modifier.weight(1f), colors.text, colors.textSecondary)
                ProfileStat(com.onezee.triptrack.model.Measure.distanceValue(state.distanceMetres, unit, strings.language.code, com.onezee.triptrack.model.Measure.Style.grouped), strings.statsKmTotal(unit), Modifier.weight(1f), colors.text, colors.textSecondary)
                ProfileStat(state.regionCount.toString(), strings.statsRegions(), Modifier.weight(1f), colors.text, colors.textSecondary)
            }
        }
    }
}

@Composable
private fun ProfilePhotoHero(state: ProfileHeroState, unit: DistanceUnit, photos: List<ProfileCoverPhoto>, api: com.onezee.triptrack.network.ApiClient?,
                             onSettings: () -> Unit, onVehicle: (UUID) -> Unit, onProfile: () -> Unit, onLevel: () -> Unit, onPhotos: () -> Unit, onStats: () -> Unit) {
    val strings = appStrings
    val pager = rememberPagerState { photos.size }
    Column(Modifier.fillMaxWidth().testTag("profile_hero")) {
        Box {
            HorizontalPager(pager, Modifier.fillMaxWidth().height(232.dp)) { index ->
                ProfileCoverImage(photos[index], api, Modifier.fillMaxSize().clickable(role = Role.Button, onClick = onPhotos))
            }
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(44.dp).background(Color.Black.copy(alpha = .3f), CircleShape).clickable(role = Role.Button, onClickLabel = strings.settingsTitle(), onClick = onSettings), contentAlignment = Alignment.Center) {
                Glyph(AppGlyph.gearshapeFill, strings.settingsTitle(), tint = Color.White, size = 23.dp)
            }
        }
        if (state.cars.isNotEmpty()) {
            val cars = rememberPagerState { state.cars.size }
            Box(Modifier.fillMaxWidth().height(106.dp)) {
            HorizontalPager(cars, Modifier.fillMaxWidth().height(188.dp).offset(y = (-82).dp)) { index ->
                val car = state.cars[index]
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onVehicle(car.vehicle.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    VehicleStage(car.vehicle, Modifier.height(155.dp), 250.dp, car.photoFilename, state.identity.isPlus, car.remoteURL)
                    Text(listOf(car.vehicle.name, car.vehicle.make, car.vehicle.model).filter(String::isNotEmpty).joinToString(" · "), style = interScaled(15f, InterWeight.semibold), color = AppTheme.colors.text)
                }
            }
        }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.identity.avatarEmoji, Modifier.clickable(role = Role.Button, onClick = onProfile), style = inter(36f))
            Text(state.identity.name.ifBlank { strings.meGuestName() }, Modifier.weight(1f).testTag("profile_identity").clickable(role = Role.Button, onClick = onProfile), style = interScaled(20f, InterWeight.bold), color = AppTheme.colors.text)
            Text(strings.tr(com.onezee.triptrack.designsystem.R.string.android_level_label, state.identity.level), Modifier.clickable(role = Role.Button, onClick = onLevel), style = AppType.meta, color = AppTheme.gold)
        }
        if (state.identity.bio.isNotEmpty()) Text(state.identity.bio, Modifier.padding(top = 14.dp), style = AppType.body, color = AppTheme.colors.text)
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onStats).padding(vertical = 18.dp).testTag("profile_stats_strip")) {
            ProfileStat(state.tripCount.toString(), strings.trips(), Modifier.weight(1f))
            ProfileStat(com.onezee.triptrack.model.Measure.distanceValue(state.distanceMetres, unit, strings.language.code, com.onezee.triptrack.model.Measure.Style.grouped), strings.statsKmTotal(unit), Modifier.weight(1f))
            ProfileStat(state.regionCount.toString(), strings.statsRegions(), Modifier.weight(1f))
        }
    }
}

@Composable
internal fun ProfileStat(value: String, title: String, modifier: Modifier = Modifier, ink: Color = AppTheme.colors.text, secondary: Color = AppTheme.colors.textSecondary) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, style = interScaled(23f, InterWeight.semibold), color = ink, maxLines = 1)
        Text(title, style = interScaled(11f), color = secondary, maxLines = 2)
    }
}
