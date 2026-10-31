// iOS: DebugGarageSeed.swift, DebugProfileCardSeed.swift, RoadSceneTime.current DEBUG flags
package com.onezee.triptrack.feature.me

import android.content.Intent
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.db.entity.VehicleEntity
import com.onezee.triptrack.designsystem.navigation.AppTab
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.launch

// Debug-only extras: adb shell am start ... --ez seed_garage_demo true --es debug_road_time sunset.
object MeLaunch {
    fun apply(graph: AppGraph, intent: Intent) {
        intent.getStringExtra("debug_road_time")?.let { raw -> graph.me.roadTimeOverride = RoadSceneTime.entries.firstOrNull { it.name == raw } }
        if (!intent.getBooleanExtra("seed_garage_demo", false)) return
        graph.auth.serviceScope.launch {
            val settings = graph.settings.loadOrCreate()
            val id = UUID.fromString("d37a9000-0000-4000-8000-000000000091")
            if (graph.vehicles.vehicle(id) == null) graph.database.vehicleDao().upsertVehicle(VehicleEntity(
                id = id, name = "Polo", avatarEmoji = "pixel_car_blue", avatarStyle = "car", vehicleType = "car", make = "Volkswagen", model = "Polo", year = 2019,
                manualOdometerKm = 96400.0, dashboardUnits = "metric", createdAt = Instant.parse("2024-05-01T12:00:00Z"),
                catalogModelId = "volkswagen-polo", catalogGenerationId = "volkswagen-polo-sedan-2015", catalogVariantId = "volkswagen-polo-sedan-2015-16-110-at6", syncStatus = SyncStatus.synced.rawValue))
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now(zone).withDayOfMonth(1).atTime(12, 0).atZone(zone).toInstant()
            val route = listOf(GeoPoint(45.0, 39.0), GeoPoint(45.01, 39.015), GeoPoint(45.017, 39.01), GeoPoint(45.03, 39.04))
            for (index in 1..10) {
                val tripId = UUID.nameUUIDFromBytes("garage_demo_$index".toByteArray())
                if (graph.trips.fetchEntity(tripId) != null) continue
                val date = start.plusSeconds((index - 1) * 86400L)
                graph.database.tripDao().upsertTrip(TripEntity(tripId, startDate = date, endDate = date.plusSeconds(600), distance = 5000.0,
                    previewPolyline = Trip.encodePolyline(route), vehicleId = id, userId = settings.id, title = graph.strings.trips(),
                    region = "RU-KDA", isPrivate = true, syncStatus = SyncStatus.synced.rawValue))
            }
            graph.settingsManager.selectVehicle(id)
            if (intent.getBooleanExtra("seed_profile_card", false)) graph.me.identity.setBio(graph.strings.profileYourKilometersStreaks())
            graph.onboarding.complete()
            graph.tabStore.select(AppTab.profile)
            graph.me.profile.reload(); graph.me.garage.reload()
        }
    }
}
