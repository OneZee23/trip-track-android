// iOS: ProShowcase.swift, ProShowcaseTileView.swift
package com.onezee.triptrack.model

enum class ProShowcaseKind(val rawValue: String, val feature: PlusFeature) {
    profileBackground("profileBackground", PlusFeature.profileBackgrounds),
    avatarFrame("avatarFrame", PlusFeature.avatarFrame),
    vehicleCard("vehicleCard", PlusFeature.vehicleCardStyle),
    routeLine("routeLine", PlusFeature.routeLineStyle);

    companion object {
        fun fromRawValue(value: String): ProShowcaseKind? = entries.find { it.rawValue == value }
    }

    fun tiles(): List<Tile> = when (this) {
        profileBackground -> ProfileBackground.entries.map { Tile(it.rawValue, it.rawValue.startsWith("plus_"), profileBackgroundName(it)) }
        avatarFrame -> AvatarFrame.entries.map { Tile(it.rawValue, it.isPlus, it.displayName) }
        vehicleCard -> VehicleCardStyle.entries.map { Tile(it.rawValue, it.isPlus, it.displayName) }
        routeLine -> RouteLineStyle.entries.map { Tile(it.rawValue, it.isPlus, it.displayName) }
    }.let { all -> all.filterNot(Tile::isPremium) + all.filter(Tile::isPremium) }

    fun preview(current: String, preview: String, isPlus: Boolean): String {
        val id = preview.ifEmpty { current }
        val premium = tiles().firstOrNull { it.id == id }?.isPremium == true
        val effectivePlus = isPlus || premium
        return when (this) {
            profileBackground -> effectiveBackground(id, effectivePlus)
            avatarFrame -> AvatarFrame.effective(id, effectivePlus).rawValue
            vehicleCard -> VehicleCardStyle.effective(id, effectivePlus).rawValue
            routeLine -> RouteLineStyle.effective(id, effectivePlus).rawValue
        }
    }

    fun effective(id: String, isPlus: Boolean): String = when (this) {
        profileBackground -> effectiveBackground(id, isPlus)
        avatarFrame -> AvatarFrame.effective(id, isPlus).rawValue
        vehicleCard -> VehicleCardStyle.effective(id, isPlus).rawValue
        routeLine -> RouteLineStyle.effective(id, isPlus).rawValue
    }
}

data class Tile(val id: String, val isPremium: Boolean, val name: String)

private fun effectiveBackground(id: String, isPlus: Boolean): String =
    (ProfileBackground.fromRawValue(id) ?: ProfileBackground.none).let {
        if (it.rawValue.startsWith("plus_") && !isPlus) ProfileBackground.none.rawValue else it.rawValue
    }

private fun profileBackgroundName(background: ProfileBackground): String = when (background) {
    ProfileBackground.none -> ""; ProfileBackground.sunset -> "Sunset"; ProfileBackground.ocean -> "Ocean"
    ProfileBackground.forest -> "Forest"; ProfileBackground.mountain -> "Mountain"; ProfileBackground.midnight -> "Midnight"
    ProfileBackground.dawn -> "Dawn"; ProfileBackground.copper -> "Copper"; ProfileBackground.slate -> "Slate"
    ProfileBackground.aurora -> "Aurora"; ProfileBackground.sand -> "Sand"; ProfileBackground.plusNebula -> "Nebula"
    ProfileBackground.plusLava -> "Lava"; ProfileBackground.plusGlacier -> "Glacier"; ProfileBackground.plusNeon -> "Neon"
    ProfileBackground.plusCarbon -> "Carbon"; ProfileBackground.plusGold -> "Gold"; ProfileBackground.plusTropic -> "Tropic"
    ProfileBackground.plusStorm -> "Storm"
}
