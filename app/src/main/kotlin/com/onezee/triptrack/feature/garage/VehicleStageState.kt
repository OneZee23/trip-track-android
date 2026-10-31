// iOS: VehicleStage.swift, ProfileGarageHeader.swift
package com.onezee.triptrack.feature.garage

import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*

// iOS: VehicleStage.swift. A photo has priority over the sprite and its platform.
data class VehicleStageState(
    val avatar: String,
    val bodyStyle: String,
    val photoFilename: String?,
    val platform: VehicleCardStyle,
    val finish: VehicleFinish,
    val modTags: List<String>,
) {
    companion object {
        fun build(vehicle: Vehicle, photoFilename: String?, isPlus: Boolean) = VehicleStageState(
            vehicle.avatarEmoji, vehicle.avatarStyle, photoFilename,
            VehicleCardStyle.effective(vehicle.cardStyle, isPlus), VehicleFinish.effective(vehicle.paintFinish, isPlus),
            VehicleModTags.effective(vehicle.modTags, isPlus),
        )
    }
}
