// iOS: -seed-garage-demo (GarageProfileShotTests). Fixed ids and dates keep every render deterministic.
package com.onezee.triptrack.feature.garage

import android.content.Context
import android.graphics.Bitmap
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID

internal object GarageFixtures {
    private fun id(seed: String) = UUID.nameUUIDFromBytes(seed.toByteArray())

    /** «Полторашка»: the Polo of the iPhone tour (garage-03), 56 trips on 37 days over 4 regions. */
    val car = Vehicle(
        id = id("garage_polo"), name = "Полторашка", avatarEmoji = "pixel_car_blue", avatarStyle = "car", make = "Volkswagen", model = "Polo", year = 2018, bodyType = "car",
        odometerKm = 1655.0, manualOdometerKm = 96400.0, level = 6, dashboardUnits = DashboardUnits.metric, createdAt = Instant.parse("2025-03-12T12:00:00Z"),
        cityConsumption = 8.5, highwayConsumption = 5.9, fuelPrice = 56.0, fuelCurrency = "₽", about = "Купил с пробегом, езжу на море каждое лето.",
        drive2URL = "https://www.drive2.ru/r/volkswagen/polo/123456789/", catalogModelId = "vw-polo",
    )

    val variant = com.onezee.triptrack.model.CarCatalog.Variant(id = "polo-16-at", displacementCc = 1598.0, powerHp = 110.0, transmission = "automatic", gears = 6, zeroToHundredS = 11.7, topSpeedKmh = 190.0)

    private val route = listOf(GeoPoint(45.0, 39.0), GeoPoint(45.01, 39.015), GeoPoint(45.017, 39.01), GeoPoint(45.03, 39.04))

    private fun region(code: String, ru: String, en: String) = MapRegionStat(code, "RU", ru, en, 100.0, emptyList(), emptyList(), 0, 0, null, 50.0, GeoPoint(45.0, 39.0), GeoBounds(44.0, 46.0, 38.0, 40.0))
    val regions = listOf(region("RU-KDA", "Краснодарский край", "Krasnodar Krai"), region("RU-ROS", "Ростовская область", "Rostov Oblast"), region("RU-AD", "Адыгея", "Adygea"), region("RU-STA", "Ставропольский край", "Stavropol Krai"))

    private fun trip(seed: String, title: String?, start: String, minutes: Long, km: Double, private: Boolean = false, elevation: Double = 0.0) = Trip(
        id = id(seed), startDate = Instant.parse(start), endDate = Instant.parse(start).plusSeconds(minutes * 60), title = title, distance = km * 1000, isPrivate = private,
        previewPolyline = Trip.encodePolyline(route), elevation = elevation, vehicleId = car.id, region = "RU-KDA")
    val longest = trip("garage_longest", "Краснодар — Горячий Ключ", "2026-11-02T15:01:00Z", 39, 47.0, private = true)
    val recent = listOf(longest, trip("garage_city", "Поездка по городу", "2026-11-01T11:14:00Z", 12, 5.0, private = true), trip("garage_sea", "К морю", "2026-10-28T08:30:00Z", 164, 214.0, elevation = 640.0))

    val photos = listOf("Море", "Перевал", "Заправка").mapIndexed { index, name -> TripPhoto(id("garage_photo_$index"), "garage_$index.jpg", name, Instant.parse("2026-10-2${index}T10:00:00Z")) }

    val passport = VehiclePassport(
        tripCount = 56, regionCount = 4, daysOnRoad = 37, distanceMetres = 2_155_000.0, durationSeconds = 360_000.0, recent = recent, routes = listOf(route, route.reversed()),
        cityCount = 7, regions = regions, years = listOf(VehicleYearDistance(2026, 1655.0)), photos = photos, highestTrip = recent.last(), longestTrip = recent.last(),
        maxDayDistanceMetresThisYear = 214_000.0, maxDayDurationSeconds = 9_840.0,
    )

    val passportState = VehiclePassportState(car, passport, catalogVariant = variant, dashboardUnit = DistanceUnit.km, loaded = true)

    val vehiclePhotos = (0..3).map { com.onezee.triptrack.data.VehiclePhoto(id("garage_car_photo_$it"), car.id, "car_$it.jpg", it == 0, Instant.parse("2026-10-2${it}T10:00:00Z"), null) }
}
