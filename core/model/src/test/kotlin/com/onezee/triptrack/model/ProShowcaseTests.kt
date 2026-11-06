// iOS: ProShowcaseTests.swift, ProShowcaseTileLabelTests.swift, AvatarFrame.swift, VehicleCardStyle.swift, VehicleFinish.swift, RouteLineStyle.swift
package com.onezee.triptrack.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProShowcaseTests {
    @Test fun eachKindKeepsPreviewAndSelectionOnOneIdentifier() {
        for (kind in ProShowcaseKind.entries) {
            val ids = kind.tiles().map(Tile::id)
            assertEquals(ids.distinct(), ids)
            for (id in ids) assertEquals(id, kind.preview(id, id, true))
        }
    }

    @Test fun expiredPaidChoicesResolveToFreeWithoutChangingStoredIds() {
        assertEquals(ProfileBackground.none.rawValue, ProShowcaseKind.profileBackground.effective(ProfileBackground.plusStorm.rawValue, false))
        assertEquals(AvatarFrame.none.rawValue, ProShowcaseKind.avatarFrame.effective(AvatarFrame.flame.rawValue, false))
        assertEquals(VehicleCardStyle.none.rawValue, ProShowcaseKind.vehicleCard.effective(VehicleCardStyle.sunset.rawValue, false))
        assertEquals(RouteLineStyle.speed.rawValue, ProShowcaseKind.routeLine.effective(RouteLineStyle.ice.rawValue, false))
        assertEquals("plus_storm", ProfileBackground.plusStorm.rawValue)
        assertEquals("frame_flame", AvatarFrame.flame.rawValue)
        assertEquals("card_sunset", VehicleCardStyle.sunset.rawValue)
        assertEquals("ice", RouteLineStyle.ice.rawValue)
    }

    @Test fun freeVehicleFinishRemainsAvailableAndPaidFinishesFallBack() {
        assertEquals(listOf(VehicleFinish.gloss, VehicleFinish.matte), VehicleFinish.availableFinishes)
        assertEquals(VehicleFinish.gloss, VehicleFinish.effective(VehicleFinish.matte.rawValue, false))
        assertEquals(VehicleFinish.matte, VehicleFinish.effective(VehicleFinish.matte.rawValue, true))
    }

    @Test fun onlyPremiumVariantsAreMarkedAsPaid() {
        assertFalse(ProShowcaseKind.profileBackground.tiles().first { it.id == "sunset" }.isPremium)
        assertTrue(ProShowcaseKind.profileBackground.tiles().first { it.id == "plus_nebula" }.isPremium)
        assertFalse(ProShowcaseKind.avatarFrame.tiles().first { it.id.isEmpty() }.isPremium)
        assertTrue(ProShowcaseKind.vehicleCard.tiles().first { it.id == "card_carbon" }.isPremium)
        assertTrue(ProShowcaseKind.routeLine.tiles().first { it.id == "ice" }.isPremium)
    }
}
