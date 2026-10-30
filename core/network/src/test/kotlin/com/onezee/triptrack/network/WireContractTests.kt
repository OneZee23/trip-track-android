// iOS: CompanionsDTOTests.swift, ProfileJournalWireTests.swift, PublicJourneyDTOTests.swift,
// SocialFeedVehicleDecodingTests.swift, VehicleSyncPayloadTests.swift, SettingsSyncPayloadTests.swift,
// JourneySyncPayloadTests.swift, PlusAttachTests.swift, ISODateTests.swift
package com.onezee.triptrack.network

import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.serialization.IsoInstantSerializer
import java.time.Instant
import java.util.UUID
import kotlin.test.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.junit.Test

class WireContractTests {
    private val json = NetworkJson.wire
    private val id = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val date = Instant.ofEpochSecond(1_760_000_000)

    private inline fun <reified T> roundTrip(value: T): T {
        val encoded = json.encodeToString(value)
        return json.decodeFromString<T>(encoded).also { assertEquals(value, it, T::class.simpleName) }
    }
    private inline fun <reified T> readFixture(name: String): T = json.decodeFromString<T>(fixture(name)).also { roundTrip(it) }

    @Test fun companionsFixtures() {
        val accepted = readFixture<CompanionsListResponse>("CompanionsDTOTests-testListItemStatusAccepted")
        assertEquals(CompanionStatus.accepted, accepted.items.single().status)
        assertEquals("Companion B", accepted.items.single().displayName)
        assertEquals(CompanionStatus.pending, readFixture<CompanionsListResponse>("CompanionsDTOTests-testListItemStatusPending").items.single().status)
        assertNull(readFixture<CompanionsListResponse>("CompanionsDTOTests-testListItemNullDisplayNameDoesNotFailDecode").items.single().displayName)
        val candidate = readFixture<CompanionsCandidatesResponse>("CompanionsDTOTests-testCandidateNullDisplayNameDoesNotFailDecode")
        assertNull(candidate.items.single().displayName)
        val candidateJson = json.encodeToJsonElement(candidate.items.single()).jsonObject
        assertTrue("id" in candidateJson); assertFalse("accountId" in candidateJson)
        assertNull(readFixture<CompanionsCandidatesResponse>("CompanionsDTOTests-testCandidatesNextCursorNull").nextCursor)
        val preview = readFixture<CompanionInvitePreview>("CompanionsDTOTests-testInvitePreviewExactFiveFields")
        assertEquals(42.5, preview.distance)
        assertNull(readFixture<CompanionInvitePreview>("CompanionsDTOTests-testInvitePreviewNullDriverDoesNotFailDecode").driver)
        assertNull(readFixture<CompanionInvitePreview>("CompanionsDTOTests-testInvitePreviewNullDurationDoesNotFailDecode").duration)
        readFixture<CompanionsInviteResponse>("CompanionsDTOTests-testInviteResponseDecodes")
        readFixture<CompanionsRespondResponse>("CompanionsDTOTests-testRespondResponseDecodes")
        readFixture<CompanionsRemoveResponse>("CompanionsDTOTests-testRemoveResponseDecodes")
        readFixture<CompanionsMyTripsResponse>("CompanionsDTOTests-testMyTripsItemDecodesAsSocialFeedTrip")
        assertEquals(true, readFixture<CompanionsMyTripsResponse>("CompanionsDTOTests-testFeedTripWithIsPrivateTrueProducesAPrivateTrip").items.single().isPrivate)
        assertNull(readFixture<CompanionsMyTripsResponse>("CompanionsDTOTests-testFeedTripWithoutIsPrivateKeyDecodesAsPublic").items.single().isPrivate)
    }

    @Test fun profileNullAbsentAndMalformedJournalFields() {
        val absent = readFixture<MeResponse>("ProfileJournalWireTests-testMeWithoutJournalKeysMeansNotSaid")
        assertFalse(absent.bioProvided); assertNull(absent.photos); assertNull(absent.pinnedTripIds)
        val clear = readFixture<MeResponse>("ProfileJournalWireTests-testMeJournalAndNullBio")
        assertTrue(clear.bioProvided); assertNull(clear.bio); assertEquals(emptyList(), clear.photos)
        assertFalse(json.encodeToJsonElement(clear).jsonObject.containsKey("bioProvided"))
        val malformed = readFixture<MeResponse>("ProfileJournalWireTests-testMalformedOptionalMeFieldsDoNotBreakAccount")
        assertFalse(malformed.bioProvided); assertNull(malformed.photos); assertNull(malformed.pinnedTripIds)
        assertEquals(JsonObject(emptyMap()), json.encodeToJsonElement(ProfileUpdateRequest()))
        assertEquals(json.parseToJsonElement("{\"bio\":\"\",\"pinnedTripIds\":[]}"),
            json.encodeToJsonElement(ProfileUpdateRequest(bio = "", pinnedTripIds = emptyList())))
    }

    @Test fun malformedOptionalPhotoMetadataDoesNotBreakTheAccount() {
        val me = json.decodeFromString<MeResponse>("""{"isPublic":true,"photos":[{"id":"$id","thumbnailUrl":4,"originalUrl":false,"takenAt":{},"sortOrder":"1"}]}""")
        val photo = assertNotNull(me.photos).single()
        assertEquals(id, photo.id); assertNull(photo.thumbnailUrl); assertNull(photo.originalUrl)
        assertNull(photo.takenAt); assertNull(photo.sortOrder)
    }

    @Test fun settingsUseMetricWireValuesAndExcludeAccountCosmetics() {
        val settings = SettingsSyncPayload(id, "🚗", "system", "en", "miles", "gallons", 7.5, 56.0, "RUB",
            profileLevel = 5, profileXp = 1000, currentStreak = 2, bestStreak = 3, conflictVersion = 1, lastModifiedAt = date)
        val fields = json.encodeToJsonElement(roundTrip(settings)).jsonObject
        assertEquals(JsonPrimitive(7.5), fields["fuelConsumption"])
        assertEquals(JsonPrimitive("miles"), fields["distanceUnit"])
        for (key in listOf("avatarFrame", "showPlusBadge", "bio", "pinnedTripIds", "selectedVehicleId", "lastTripDate")) assertFalse(key in fields)
        val checkpoint = roundTrip(TripCheckpointPayload(id, date, distanceFromStart = 42.5, elapsedFromStart = 123.0, sortOrder = 0))
        val point = json.encodeToJsonElement(checkpoint).jsonObject
        assertFalse("latitude" in point); assertFalse("longitude" in point)
    }

    @Test fun socialAndJourneyFixtures() {
        readFixture<PublicJourneysResponse>("PublicJourneyDTOTests-testPublicJourneysResponseDecodes")
        readFixture<SocialFeedTrip>("PublicJourneyDTOTests-testFeedTripDecodesJourneyAndSurvivesItsAbsence")
        readFixture<SocialFeedVehicle>("SocialFeedVehicleDecodingTests-testIdAndSilhouetteArrive")
        readFixture<SocialFeedVehicle>("SocialFeedVehicleDecodingTests-testLowercaseIdFromPostgresDecodes")
        assertNull(readFixture<SocialFeedVehicle>("SocialFeedVehicleDecodingTests-testOlderServerWithoutIdStillDecodes").id)
        readFixture<List<SocialFind>>("SocialProfileFindsTests-testMissingOptionalFieldsDecodeAsUnknown")
        val status = readFixture<PlusStatusResponse>("PlusAttachTests-testAShortAnswerStillDecodes")
        assertTrue(status.active); assertFalse(status.isTrial)
        assertNull(readFixture<SyncPullResponse>("JourneySyncPayloadTests-testPullWithoutJourneysSectionDecodes").journeys)
        val journey = roundTrip(JourneySyncPayload(id, startDate = date, excludedTripIds = listOf(id), isPrivate = true, conflictVersion = 1, lastModifiedAt = date))
        assertNull(journey.endDate)
        assertFalse(json.encodeToJsonElement(journey).jsonObject.containsKey("endDate"))
    }

    @Test fun vehicleExplicitNullAndOldServerPresence() {
        val raw = """{"id":"$id","name":"Test car","avatarEmoji":"🚗","odometerKm":0,"level":1,"cityConsumption":0,"highwayConsumption":0,"fuelPrice":0,"conflictVersion":1,"lastModifiedAt":"2026-09-09T10:00:00Z"}"""
        val old = json.decodeFromString<VehicleSyncPayload>(raw)
        assertFalse(old.manualOdometerKnown); assertFalse(old.soldAtKnown)
        val encoded = json.encodeToJsonElement(old).jsonObject
        assertEquals(JsonNull, encoded["manualOdometerKm"]); assertEquals(JsonNull, encoded["soldAt"])
        assertFalse("manualOdometerKnown" in encoded); assertFalse("soldAtKnown" in encoded)
        val clear = json.decodeFromJsonElement<VehicleSyncPayload>(encoded)
        assertTrue(clear.manualOdometerKnown); assertTrue(clear.soldAtKnown)
        val zero = json.decodeFromJsonElement<VehicleSyncPayload>(JsonObject(encoded + ("manualOdometerKm" to JsonPrimitive(0))))
        assertEquals(0.0, zero.manualOdometerKm)
        val future = json.decodeFromJsonElement<VehicleSyncPayload>(JsonObject(encoded + mapOf("powertrain" to JsonPrimitive("future"), "dashboardUnits" to JsonPrimitive(4))))
        assertNull(future.powertrain); assertNull(future.dashboardUnits)
    }

    @Test fun tripArraysKeepOmissionAndExplicitEmptyDifferent() {
        val p = TripSyncPayload(id, startDate = date, distance = 1.0, maxSpeed = 1.0, averageSpeed = 1.0,
            fuelUsed = 0.0, elevation = 0.0, isPrivate = true, conflictVersion = 1, lastModifiedAt = date)
        val absent = json.encodeToJsonElement(roundTrip(p)).jsonObject
        for (key in listOf("trackPoints", "photos", "checkpoints", "segments", "recordingBreaks")) assertFalse(key in absent)
        val empty = json.encodeToJsonElement(roundTrip(p.copy(trackPoints = emptyList(), photos = emptyList(), checkpoints = emptyList(),
            segments = emptyList(), recordingBreaks = emptyList(), energyMode = "auto"))).jsonObject
        for (key in listOf("trackPoints", "photos", "checkpoints", "segments", "recordingBreaks")) assertEquals(JsonArray(emptyList()), empty[key])
        assertEquals(JsonPrimitive("auto"), empty["energyMode"])
        val future = json.decodeFromJsonElement<TripSyncPayload>(JsonObject(absent + ("source" to JsonPrimitive("future"))))
        assertEquals(TripOrigin.recorded, future.source)
    }

    @Test fun notificationPatchAndMarkReadNeverTurnEmptyIntoAll() {
        assertEquals("{}", json.encodeToString(NotificationPrefsUpdateRequest()))
        assertEquals("{\"notifyComments\":false}", json.encodeToString(NotificationPrefsUpdateRequest(notifyComments = false)))
        assertEquals("{}", json.encodeToString(NotificationsMarkReadRequest()))
        assertEquals("{\"ids\":[]}", json.encodeToString(NotificationsMarkReadRequest(emptyList())))
        val item = roundTrip(NotificationItem(id, "future_kind", isRead = false, createdAt = date))
        assertEquals("future_kind", item.kind); assertNull(item.actor)
    }

    @Test fun addedBoundaryDtosRoundTrip() {
        roundTrip(TripUpsertResponse(id, 1, date)); roundTrip(TripDetailRequest(id, true)); roundTrip(TripDeleteRequest(id, 1))
        roundTrip(VehicleUpsertResponse(id, 1)); roundTrip(VehicleDeleteRequest(id)); roundTrip(VehicleListResponse(emptyList()))
        roundTrip(JourneyUpsertResponse(id, 1)); roundTrip(JourneyDeleteRequest(id)); roundTrip(JourneyListResponse(emptyList()))
        roundTrip(SettingsUpsertResponse(2)); roundTrip(PhotoUploadResponse(id, "object-key", 1)); roundTrip(PhotoURLRequest(id, "original"))
        roundTrip(PhotoURLResponse("https://photos.trip-track.app/test.jpg", "2026-09-09T10:00:00Z")); roundTrip(PhotoDeleteRequest(id))
        roundTrip(VehiclePhotoMainRequest(id, id)); roundTrip(VehiclePhotoListResponse(listOf(OwnedVehiclePhoto(id, id, isMain = true, takenAt = date))))
        roundTrip(ProfilePhotoListResponse(listOf(ProfilePhotoDTO(id)))); roundTrip(ProfilePhotoOrderRequest(emptyList())); roundTrip(ProfilePhotoDeleteRequest(id.toString()))
        roundTrip(GroupsWaitlistRequest(id)); roundTrip(GroupsWaitlistStatusRequest(id)); roundTrip(GroupsWaitlistState(1, mapOf("miata" to 1), true, listOf("miata")))
        roundTrip(DeviceTokenRequest("test-device-token", id.toString(), "sandbox"))
        roundTrip(NotificationPrefsResponse(true, false, notifyWeeklyRecap = true)); roundTrip(PushData("REACTION", actorAccountId = id, tripId = id, emoji = "🔥"))
        roundTrip(PublicTripsResponse(listOf(PublicTripDTO(id, date, distance = 1000.0)), null))
        roundTrip(AdminSummary(1, 1, 1, 0, 0, 0, 1, 0)); roundTrip(AdminItems(listOf(AdminFlag("fogMetal", null, true))))
        roundTrip(AdminFlagRequest("fogMetal", false)); roundTrip(AdminEvent(type = "test", at = date))
        roundTrip(AdminSubscription("test", "test", "unattached", false, false, createdAt = date, attached = false, isAdmin = false, trips = 0))
        roundTrip(PlusAttachRequest("test-jws")); roundTrip(UsageEraseRequest("test-consent"))
        roundTrip(ProductAnalyticsSnapshot(1, id.toString(), 1, "2026-09-09", "2026-09-09", false, 0, 0, 0, 0, 0, 0, 0))
    }

    @Test fun isoDateFixturesAndUtcMilliseconds() {
        for (raw in listOf("2026-04-29T08:33:35.049Z", "2026-04-29T11:33:35.049+03:00", "2026-04-29T03:33:35.049-05:00")) {
            assertEquals(Instant.ofEpochMilli(1_777_451_615_049), json.decodeFromJsonElement(IsoInstantSerializer, JsonPrimitive(raw)))
        }
        assertEquals(Instant.ofEpochSecond(1_777_451_615), json.decodeFromJsonElement(IsoInstantSerializer, JsonPrimitive("2026-04-29T08:33:35Z")))
        assertEquals("\"2026-04-29T08:33:35.049Z\"", json.encodeToString(IsoInstantSerializer, Instant.ofEpochMilli(1_777_451_615_049)))
        for (raw in listOf("", "not a date", "2026-04-29", "2026-04-29T08:33:35.049")) {
            assertFailsWith<SerializationException> { json.decodeFromJsonElement(IsoInstantSerializer, JsonPrimitive(raw)) }
        }
    }
}
