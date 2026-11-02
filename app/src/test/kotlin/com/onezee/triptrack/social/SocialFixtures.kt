// iOS: ProfileTripsPagerTests.swift, PublicMapFlowTests.swift (-debug-foreign-trip).
package com.onezee.triptrack.social

import com.onezee.triptrack.logic.encodePolyline

import com.onezee.triptrack.feature.social.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.PublicTripDTO
import com.onezee.triptrack.network.PublicTripsResponse
import java.time.Instant
import java.util.UUID

internal object SocialFixtures {
    val account = UUID.fromString("88888888-8888-4888-8888-888888888888")
    val author = SocialAuthor(account, "Test Traveller", "😎", 21)
    val now = Instant.parse("2026-10-25T12:00:00Z")
    val route = listOf(GeoPoint(43.4, 1.0), GeoPoint(47.0, 2.0), GeoPoint(50.65, 3.1))
    fun trip(n: Int, iso: String = "2026-10-01T10:00:00Z") = SocialFeedTrip(
        id = UUID.fromString("%08d-0000-4000-8000-000000000000".format(n)), author = author,
        startDate = Instant.parse(iso), distance = 1000.0, duration = 60, photoCount = 0, reactionCount = 0,
        reactionBreakdown = emptyList(), badgeIds = emptyList(), title = "Toulouse → Lille",
        previewPolyline = java.util.Base64.getEncoder().encodeToString(Trip.encodePolyline(route)))
    val cards = (0 until 10).map { trip(it) }
    fun profile() = SocialProfile(id = account, displayName = author.displayName, avatarEmoji = author.avatarEmoji,
        profileLevel = 21, currentStreak = 3, bestStreak = 8, profileXp = 26400, hasPlus = true, isPlus = false,
        profileBackground = "plus_nebula", avatarFrame = "gold", bio = "За красивыми дорогами",
        stats = SocialProfileStats(1842.0, 45, 7, 45), followerCount = 12, followingCount = 4,
        recentBadges = listOf("first_trip"), recentTrips = cards.map { t -> SocialProfileRecentTrip(t.id, t.title, t.startDate, t.distance,
            region = t.region, previewPolyline = t.previewPolyline, author = t.author, duration = t.duration) })
    val car = PublicVehicle(UUID.fromString("10000000-0000-4000-8000-000000000001"), "Polo", "pixel_car_blue", make = "Volkswagen", model = "Polo", year = 2019,
        level = 4, odometerKm = 96400.0, mapVisible = true, photosVisible = true, about = "Дороги к морю", createdAt = Instant.parse("2024-05-01T12:00:00Z"))
    fun publicTrip(n: Int) = PublicTripDTO(trip(n).id, Instant.ofEpochSecond(1780000000), Instant.ofEpochSecond(1780017880), 412000.0, 17880, "Нижегородская обл.",
        java.util.Base64.getEncoder().encodeToString(Trip.encodePolyline(listOf(GeoPoint(55.7558, 37.6173), GeoPoint(56.3269, 44.0059)))))
    // iOS: DebugForeignTripFixture.publicTrips, default france variant.
    fun mapTrips(): List<PublicTripDTO> = listOf(
        listOf(43.4, 1.0, 50.65, 3.1, 896.3),
        listOf(50.6, 3.0, 45.8, 1.3, 945.7),
    ).mapIndexed { index, values ->
        val (lat, lon, endLat, endLon, km) = values
        val points = (0 until 2400).map { i ->
            val fraction = i / 2399.0
            GeoPoint(lat + (endLat - lat) * fraction + kotlin.math.sin(fraction * 40 + index) * .03,
                lon + (endLon - lon) * fraction + kotlin.math.cos(fraction * 33 + index) * .03)
        }
        val start = Instant.ofEpochSecond(1790000000L - index * 86400)
        PublicTripDTO(UUID.fromString("D37A2001-0000-4000-8000-%012d".format(index)), start, start.plusSeconds((km * 40).toLong()), km * 1000,
            (km * 40).toInt(), if (index == 0) "Occitanie" else "Hauts-de-France", java.util.Base64.getEncoder().encodeToString(Trip.encodePolyline(points)),
            if (index == 0) "Toulouse → Lille" else "Lille → Limoges")
    }
    fun notice(kind: String = "follow", id: UUID = UUID.randomUUID()) = NotificationItem(id, kind, tripId = trip(0).id,
        tripTitle = "LEAKED-TITLE", isRead = false, createdAt = Instant.ofEpochSecond(1700000000), actor = author)
}

internal open class FakeSocialApi : SocialApi {
    var profileValue = SocialFixtures.profile()
    var journeysValue = emptyList<PublicJourneyDto>()
    var notificationsValue = listOf(SocialFixtures.notice())
    var profileError: Exception? = null
    var inboxError: Exception? = null
    var cars = listOf(SocialFixtures.car)
    var publicPage: suspend (String?) -> PublicTripsResponse = { PublicTripsResponse(SocialFixtures.mapTrips(), null) }
    override suspend fun profile(id: UUID): SocialProfile { profileError?.let { throw it }; return profileValue }
    override suspend fun profileTrips(id: UUID, cursor: String, limit: Int) = SocialFeedResponse(emptyList())
    override suspend fun badges(id: UUID) = listOf("first_trip", "iron_butt")
    override suspend fun garage(id: UUID) = cars
    override suspend fun vehicle(id: UUID, vehicleId: UUID) = cars.first { it.id == vehicleId }
    override suspend fun journeys(id: UUID, cursor: String?, limit: Int) = PublicJourneysResponse(journeysValue)
    override suspend fun publicTrips(id: UUID, cursor: String?, limit: Int?, vehicleId: UUID?) = publicPage(cursor)
    override suspend fun follow(id: UUID, follow: Boolean) {}
    var peopleValue: suspend (UUID, FollowListMode) -> SocialFollowersResponse = { _, _ -> SocialFollowersResponse(listOf(SocialFixtures.author), 1) }
    override suspend fun people(id: UUID, mode: FollowListMode) = peopleValue(id, mode)
    override suspend fun block(id: UUID, block: Boolean) {}
    override suspend fun report(target: ReportTarget, reason: ReportReason, notes: String?) {}
    override suspend fun shareTrip(tripId: UUID) = error("unused")
    override suspend fun shareJourney(journeyId: UUID) = error("unused")
    override suspend fun trip(id: UUID, includeTrack: Boolean) = SocialTripResponse(SocialFixtures.cards.firstOrNull { it.id == id } ?: SocialFixtures.trip(0).copy(id = id))
    override suspend fun notifications(cursor: String?): NotificationsFeedResponse { inboxError?.let { throw it };return NotificationsFeedResponse(notificationsValue) }
    override suspend fun unreadCount() = 1
    override suspend fun markRead(ids: List<UUID>?) {}
    override suspend fun invitePreview(tripId: UUID) = CompanionInvitePreview(CompanionDriverPreview(SocialFixtures.account,"Driver","🚗"),SocialFixtures.now,42000.0,3600.0,"Тверская область")
    override suspend fun respondToInvite(tripId: UUID, accept: Boolean) = if (accept) CompanionStatus.accepted else CompanionStatus.declined
    override suspend fun companionTrips(cursor: String?) = CompanionsMyTripsResponse(emptyList())
}
