// iOS: ProfileJournalWireTests.swift, VisibilityWireTests.swift, SocialProfileFindsTests.swift, PlusAttachTests.swift
package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.ModelJson
import java.util.UUID
import kotlin.test.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class ProfileWireTests {
    private val json = ModelJson.wire
    private val id = UUID.fromString("11111111-1111-4111-8111-111111111111")

    private fun profile(fields: String = ""): SocialProfile = json.decodeFromString("""
        {"id":"$id","displayName":"A","profileLevel":1,"currentStreak":0,"bestStreak":0,
         "stats":{"totalKm":100,"tripCount":5,"regionsCount":1,"publicTripCount":5},
         "recentBadges":[],"recentTrips":[],"followerCount":0,"followingCount":0$fields}
    """.trimIndent())

    @Test fun meWithoutJournalKeysMeansNotSaid() {
        val value = json.decodeFromString<MeResponse>(fixture("ProfileJournalWireTests-testMeWithoutJournalKeysMeansNotSaid"))
        assertNull(value.bio)
        assertFalse(value.bioProvided)
        assertNull(value.photos)
        assertNull(value.pinnedTripIds)
    }

    @Test fun meJournalAndNullBio() {
        val value = json.decodeFromString<MeResponse>("""{"isPublic":true,"bio":"Дороги","photos":[{"id":"$id","thumbnailUrl":"https://example.invalid/a.jpg"}],"pinnedTripIds":["$id"]}""")
        assertEquals("Дороги", value.bio)
        assertTrue(value.bioProvided)
        assertEquals(id, value.photos?.single()?.id)
        assertNull(value.photos?.single()?.sortOrder)
        assertEquals(listOf(id), value.pinnedTripIds)
        val clear = json.decodeFromString<MeResponse>(fixture("ProfileJournalWireTests-testMeJournalAndNullBio"))
        assertTrue(clear.bioProvided)
        assertNull(clear.bio)
        assertEquals(emptyList(), clear.photos)
        assertEquals(emptyList(), clear.pinnedTripIds)
    }

    @Test fun malformedOptionalMeFieldsDoNotBreakAccount() {
        val value = json.decodeFromString<MeResponse>(fixture("ProfileJournalWireTests-testMalformedOptionalMeFieldsDoNotBreakAccount"))
        assertTrue(value.isPublic)
        assertFalse(value.bioProvided)
        assertNull(value.bio)
        assertNull(value.photos)
        assertNull(value.pinnedTripIds)
    }

    @Test fun publicProfileWithoutJournalKeys() {
        val value = profile()
        assertNull(value.photos)
        assertNull(value.pinnedTrips)
    }

    @Test fun publicProfileWithPhotosAndPinnedTrip() {
        val value = profile(""","photos":[{"id":"$id","thumbnailUrl":"https://example.invalid/a.jpg","originalUrl":null,"takenAt":null}],"pinnedTrips":[{"id":"$id","startDate":"2026-10-20T09:00:00Z","distance":1000}]""")
        assertEquals(id, value.photos?.single()?.id)
        assertEquals(id, value.pinnedTrips?.single()?.id)
        assertEquals(1000.0, value.pinnedTrips?.single()?.distance)
        val malformed = profile(""","photos":false,"pinnedTrips":"future"""")
        assertNull(malformed.photos)
        assertNull(malformed.pinnedTrips)
    }

    @Test fun profileUpdateOmitsNilAndEncodesExplicitClears() {
        val request = ProfileUpdateRequest()
        assertFalse(jsonObject(json.encodeToString(request)).containsKey("bio"))
        assertFalse(jsonObject(json.encodeToString(request)).containsKey("pinnedTripIds"))
        request.bio = ""
        request.pinnedTripIds = emptyList()
        val clear = jsonObject(json.encodeToString(request))
        assertEquals(JsonPrimitive(""), clear["bio"])
        assertEquals(JsonArray(emptyList()), clear["pinnedTripIds"])
        request.bio = "Roads"
        request.pinnedTripIds = listOf(id.toString())
        val filled = jsonObject(json.encodeToString(request))
        assertEquals(JsonPrimitive("Roads"), filled["bio"])
        assertEquals(JsonArray(listOf(JsonPrimitive(id.toString()))), filled["pinnedTripIds"])
    }

    @Test fun eachVisibilityFlagReachesTheWire() {
        for (value in listOf(true, false)) {
            val requests = mapOf(
                "countersPublic" to ProfileUpdateRequest(countersPublic = value),
                "statsPublic" to ProfileUpdateRequest(statsPublic = value),
                "mapPublic" to ProfileUpdateRequest(mapPublic = value),
                "achievementsPublic" to ProfileUpdateRequest(achievementsPublic = value),
            )
            for ((key, request) in requests) {
                val encoded = jsonObject(json.encodeToString(request))
                assertEquals(setOf(key), encoded.keys)
                assertEquals(JsonPrimitive(value), encoded[key])
            }
        }
    }

    @Test fun plusUntilIsOptionalAndBadgePreferenceUsesProfileUpdate() {
        assertNull(json.decodeFromString<MeResponse>("""{"isPublic":true}""").plusUntil)
        assertEquals("2026-12-06T10:00:00Z", json.decodeFromString<MeResponse>("""{"isPublic":true,"plusUntil":"2026-12-06T10:00:00Z"}""").plusUntil)
        val update = jsonObject(json.encodeToString(ProfileUpdateRequest(showPlusBadge = false)))
        assertEquals(JsonPrimitive(false), update["showPlusBadge"])
    }

    @Test fun avatarFrameUsesProfileUpdateAndMeResponse() {
        val update = jsonObject(json.encodeToString(ProfileUpdateRequest(avatarFrame = "frame_gold")))
        assertEquals(setOf("avatarFrame"), update.keys)
        assertEquals(JsonPrimitive("frame_gold"), update["avatarFrame"])
        val me = json.decodeFromString<MeResponse>("""{"isPublic":true,"avatarFrame":"frame_gold"}""")
        assertEquals("frame_gold", me.avatarFrame)
    }

    @Test fun visibilityResponseDoesNotInventServerSupport() {
        val absent = json.decodeFromString<MeResponse>("""{"id":"$id","email":null,"displayName":"A","isPublic":true}""")
        assertNull(absent.countersPublic)
        assertNull(absent.statsPublic)
        assertNull(absent.mapPublic)
        assertNull(absent.achievementsPublic)
        val partial = json.decodeFromString<MeResponse>("""{"id":"$id","email":null,"displayName":"A","isPublic":true,"mapPublic":false}""")
        assertEquals(false, partial.mapPublic)
        assertNull(partial.statsPublic)
    }

    @Test fun missingFindsAndEmptyFindsStayDistinct() {
        assertNull(profile().finds)
        assertEquals(emptyList(), profile(",\"finds\":[]").finds)
    }

    @Test fun profileDecodesFindsList() {
        val finds = """[{"secretId":"s1","kind":"secret","symbol":"mountain.2","rarity":"few","foundAt":"2026-09-12T00:00:00.000Z","first":true},{"secretId":"s2","kind":"riddle","symbol":"light.beacon.max","rarity":"many","foundAt":"2026-09-13T10:00:00.000Z","first":false}]"""
        val value = profile(",\"finds\":$finds")
        assertEquals(2, value.finds?.size)
        assertEquals("s1", value.finds?.first()?.secretId)
        assertEquals("secret", value.finds?.first()?.kind)
        assertEquals("few", value.finds?.first()?.rarity)
        assertEquals(true, value.finds?.first()?.first)
        assertEquals(false, value.finds?.last()?.first)
    }

    @Test fun rowWithoutFoundAtIsSkippedAndProfileSurvives() {
        val finds = """[{"secretId":"s1","kind":"secret","symbol":"mountain.2","rarity":"few","foundAt":"2026-09-12T00:00:00.000Z","first":true},{"secretId":"broken","kind":"secret","symbol":"mountain.2","rarity":"few","first":false}]"""
        assertEquals("s1", profile(",\"finds\":$finds").finds?.single()?.secretId)
    }

    @Test fun missingOptionalFindFieldsDecodeAsUnknown() {
        val finds = fixture("SocialProfileFindsTests-testMissingOptionalFieldsDecodeAsUnknown")
        val value = profile(",\"finds\":$finds").finds!!.single()
        assertNull(value.rarity)
        assertNull(value.symbol)
        assertFalse(value.first)
    }

    @Test fun wrongTypeInOptionalFindFieldDoesNotFailRow() {
        val finds = """[{"secretId":"s1","kind":"secret","symbol":"mountain.2","rarity":7,"foundAt":"2026-09-12T00:00:00.000Z","first":"yes"}]"""
        val value = profile(",\"finds\":$finds").finds!!.single()
        assertNull(value.rarity)
        assertFalse(value.first)
        assertEquals("mountain.2", value.symbol)
    }

    @Test fun allRowsBrokenLeaveAnEmptyList() {
        val finds = """[{"secretId":"a","kind":"secret"},{"kind":"secret","foundAt":"2026-09-12T00:00:00.000Z"}]"""
        assertEquals(emptyList(), profile(",\"finds\":$finds").finds)
    }

    @Test fun handWrittenProfileDecodeKeepsOtherFields() {
        val value = profile(",\"finds\":[]")
        assertEquals(id, value.id)
        assertEquals("A", value.displayName)
        assertEquals(1, value.profileLevel)
        assertEquals(100.0, value.stats.totalKm)
        assertEquals(5, value.stats.tripCount)
        assertEquals(emptyList(), value.recentBadges)
        assertEquals(0, value.recentTrips.size)
        assertEquals(0, value.followerCount)
        assertNull(value.isFollowing)
        assertNull(value.bio)
        assertNull(value.visibility)
    }

    @Test fun oldVisibilityDefaultsOpen() {
        val flags = json.decodeFromString<SocialProfileVisibility>("{}")
        assertTrue(flags.counters)
        assertTrue(flags.stats)
        assertTrue(flags.map)
        assertTrue(flags.achievements)
        val partial = json.decodeFromString<SocialProfileVisibility>("""{"map":false}""")
        assertFalse(partial.map)
        assertTrue(partial.stats)
    }

    @Test fun shortPlusAnswerStillDecodes() {
        val status = json.decodeFromString<PlusStatusResponse>(fixture("PlusAttachTests-testAShortAnswerStillDecodes"))
        assertTrue(status.active)
        assertFalse(status.isTrial)
        assertNull(status.until)
        assertNull(status.productId)
        assertFalse(json.decodeFromString<PlusStatusResponse>("{}").active)
    }
    @Test fun quotedBooleanIsRejectedLikeSwiftTryDecode() {
        val finds = """[{"secretId":"s1","kind":"secret","foundAt":"2026-09-12T00:00:00.000Z","first":"true"}]"""
        assertFalse(profile(",\"finds\":$finds").finds!!.single().first)
    }

}
