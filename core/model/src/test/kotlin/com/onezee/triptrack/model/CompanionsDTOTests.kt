// iOS: CompanionsDTOTests.swift, SocialFeedVehicleDecodingTests.swift, PublicJourneyDTOTests.swift
package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.ModelJson
import java.time.Instant
import java.util.UUID
import kotlin.test.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class CompanionsDTOTests {
    private val json = ModelJson.wire
    private val companion = UUID.fromString("1efcd7ab-cd57-414e-a3ae-d5ed996991f3")
    private val driver = UUID.fromString("a1db2427-2a15-4496-9dd5-efc29edea5f8")

    @Test fun listItemStatusAccepted() {
        val result = json.decodeFromString<CompanionsListResponse>(fixture("CompanionsDTOTests-testListItemStatusAccepted"))
        assertEquals(1, result.items.size)
        assertEquals(companion, result.items[0].accountId)
        assertEquals("Companion B", result.items[0].displayName)
        assertEquals("🚗", result.items[0].avatarEmoji)
        assertEquals(CompanionStatus.accepted, result.items[0].status)
        assertFalse(result.isOwnerView)
    }

    @Test fun listItemStatusPending() {
        val result = json.decodeFromString<CompanionsListResponse>(fixture("CompanionsDTOTests-testListItemStatusPending"))
        assertEquals(CompanionStatus.pending, result.items.single().status)
        assertTrue(result.isOwnerView)
    }

    @Test fun listItemNullDisplayNameDoesNotFailDecode() {
        val item = json.decodeFromString<CompanionsListResponse>(fixture("CompanionsDTOTests-testListItemNullDisplayNameDoesNotFailDecode")).items.single()
        assertNull(item.displayName)
        assertNull(item.avatarEmoji)
        assertEquals(CompanionStatus.accepted, item.status)
    }

    @Test fun candidatesNextCursorNull() {
        val result = json.decodeFromString<CompanionsCandidatesResponse>(fixture("CompanionsDTOTests-testCandidatesNextCursorNull"))
        assertEquals(companion, result.items.single().accountId)
        assertEquals("Companion B", result.items.single().displayName)
        assertEquals(1, result.items.single().profileLevel)
        assertNull(result.nextCursor)
        val encoded = jsonObject(json.encodeToString(result.items.single()))
        assertTrue(encoded.containsKey("id"))
        assertFalse(encoded.containsKey("accountId"))
    }

    @Test fun candidatesNextCursorPresent() {
        val cursor = "Q29tcGFuaW9uIEJ8MWVmY2Q3YWItY2Q1Ny00MTRlLWEzYWUtZDVlZDk5Njk5MWYz"
        val raw = fixture("CompanionsDTOTests-testCandidatesNextCursorNull").replace("\"nextCursor\":null", "\"nextCursor\":\"$cursor\"")
        assertEquals(cursor, json.decodeFromString<CompanionsCandidatesResponse>(raw).nextCursor)
    }

    @Test fun candidateNullDisplayNameDoesNotFailDecode() {
        assertNull(json.decodeFromString<CompanionsCandidatesResponse>(fixture("CompanionsDTOTests-testCandidateNullDisplayNameDoesNotFailDecode")).items.single().displayName)
    }

    @Test fun invitePreviewExactFiveFields() {
        val raw = fixture("CompanionsDTOTests-testInvitePreviewExactFiveFields")
        assertEquals(setOf("driver", "startDate", "distance", "duration", "region"), jsonObject(raw).keys)
        val preview = json.decodeFromString<CompanionInvitePreview>(raw)
        assertEquals(driver, preview.driver?.accountId)
        assertEquals("Driver A", preview.driver?.displayName)
        assertEquals("🏎️", preview.driver?.avatarEmoji)
        assertEquals(Instant.parse("2026-08-10T18:19:21.136Z"), preview.startDate)
        assertEquals(42.5, preview.distance)
        assertEquals(3600.0, preview.duration)
        assertEquals("Test Region", preview.region)
    }

    @Test fun invitePreviewNullDriverDoesNotFailDecode() {
        assertNull(json.decodeFromString<CompanionInvitePreview>(fixture("CompanionsDTOTests-testInvitePreviewNullDriverDoesNotFailDecode")).driver)
    }

    @Test fun invitePreviewNullDurationDoesNotFailDecode() {
        assertNull(json.decodeFromString<CompanionInvitePreview>(fixture("CompanionsDTOTests-testInvitePreviewNullDurationDoesNotFailDecode")).duration)
    }

    @Test fun myTripsItemDecodesAsSocialFeedTrip() {
        val result = json.decodeFromString<CompanionsMyTripsResponse>(fixture("CompanionsDTOTests-testMyTripsItemDecodesAsSocialFeedTrip"))
        val trip = result.items.single()
        assertEquals(UUID.fromString("9c29078a-3b90-4a48-8469-206f4d641912"), trip.id)
        assertEquals(driver, trip.author.id)
        assertEquals("Driver A", trip.author.displayName)
        assertEquals("Acceptance test private trip", trip.title)
        assertEquals(42.5, trip.distance)
        assertEquals(0, trip.duration)
        assertEquals(3600, trip.drivingTime)
        assertNull(result.nextCursor)
    }

    @Test fun mutationResponsesDecode() {
        val invite = json.decodeFromString<CompanionsInviteResponse>(fixture("CompanionsDTOTests-testInviteResponseDecodes"))
        assertEquals(companion, invite.companion.accountId)
        assertEquals(CompanionStatus.pending, invite.companion.status)
        assertEquals(CompanionStatus.accepted, json.decodeFromString<CompanionsRespondResponse>(fixture("CompanionsDTOTests-testRespondResponseDecodes")).status)
        assertTrue(json.decodeFromString<CompanionsRemoveResponse>(fixture("CompanionsDTOTests-testRemoveResponseDecodes")).ok)
    }

    @Test fun feedTripPrivacyRemainsOptional() {
        assertNull(json.decodeFromString<CompanionsMyTripsResponse>(fixture("CompanionsDTOTests-testFeedTripWithoutIsPrivateKeyDecodesAsPublic")).items.single().isPrivate)
        assertEquals(true, json.decodeFromString<CompanionsMyTripsResponse>(fixture("CompanionsDTOTests-testFeedTripWithIsPrivateTrueProducesAPrivateTrip")).items.single().isPrivate)
    }

    @Test fun olderFeedVehicleWithoutIdStillDecodes() {
        val vehicle = json.decodeFromString<SocialFeedVehicle>(fixture("SocialFeedVehicleDecodingTests-testOlderServerWithoutIdStillDecodes"))
        assertNull(vehicle.id)
        assertEquals("Peugeot", vehicle.name)
    }

    @Test fun feedVehicleIdAndSilhouetteArrive() {
        val vehicle = json.decodeFromString<SocialFeedVehicle>(fixture("SocialFeedVehicleDecodingTests-testIdAndSilhouetteArrive"))
        assertEquals(UUID.fromString("7A1D0E8E-1111-4222-8333-944455556666"), vehicle.id)
        assertEquals("hatch", vehicle.avatarStyle)
    }

    @Test fun lowercaseIdFromPostgresDecodes() {
        val vehicle = json.decodeFromString<SocialFeedVehicle>(fixture("SocialFeedVehicleDecodingTests-testLowercaseIdFromPostgresDecodes"))
        assertEquals(UUID.fromString("7A1D0E8E-1111-4222-8333-944455556666"), vehicle.id)
    }

    @Test fun feedTripDecodesJourneyAndSurvivesItsAbsence() {
        val raw = fixture("PublicJourneyDTOTests-testFeedTripDecodesJourneyAndSurvivesItsAbsence")
        val trip = json.decodeFromString<SocialFeedTrip>(raw)
        assertEquals("Грузия", trip.journey?.title)
        assertNull(trip.journey?.endDate)
        val without = JsonObject(jsonObject(raw).filterKeys { it != "journey" }).toString()
        assertNull(json.decodeFromString<SocialFeedTrip>(without).journey)
        assertEquals(0, trip.commentCountRaw)
        assertTrue(jsonObject(json.encodeToString(trip)).containsKey("commentCount"))
        assertFalse(jsonObject(json.encodeToString(trip)).containsKey("commentCountRaw"))
        assertEquals(trip.journey, trip.copy(reactionCount = 1, myReaction = "👍").journey)
        assertEquals(trip.journey, trip.copy(commentCountRaw = 3).journey)
    }

    @Test fun publicJourneysResponseDecodes() {
        val result = json.decodeFromString<PublicJourneysResponse>(fixture("PublicJourneyDTOTests-testPublicJourneysResponseDecodes"))
        assertEquals(480000.0, result.journeys.single().legs.single().distance)
        assertNull(result.nextCursor)
    }
    @Test fun journeyShareKeepsBothFields() {
        val id = UUID.fromString("33333333-3333-4333-8333-333333333333")
        val request = SocialShareJourneyRequest(journeyId = id, expiresInDays = 7)
        val encoded = jsonObject(json.encodeToString(request))
        assertEquals(setOf("journeyId", "expiresInDays"), encoded.keys)
        assertEquals(JsonPrimitive(7), encoded["expiresInDays"])
        assertEquals(request, json.decodeFromString<SocialShareJourneyRequest>(encoded.toString()))
    }

}
