// iOS: ProfileTripsPagerTests, PublicCosmeticsTests, ProfileUnavailableTests,
// PublicAchievementsCatalogueTests. Inputs and assertions retained.
package com.onezee.triptrack.social

import com.onezee.triptrack.feature.social.*
import com.onezee.triptrack.logic.BadgeDefinitions
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import kotlin.test.*
import org.junit.Test

class ProfileRulesTests {
    private fun trip(n: Int, iso: String = "2026-10-01T10:00:00Z") = SocialFixtures.trip(n, iso)
    @Test fun continuesFromLastPreviewWithServerCursor() {
        val preview = (0 until 10).map { trip(it, "2026-10-0${it % 9 + 1}T10:00:00Z") }
        val pager = ProfileTripsPager.reset(preview, 45)
        assertEquals("2026-10-01T10:00:00.000Z|${preview.last().id}", pager.cursor)
        assertTrue(pager.shouldLoad(7, 10)); assertFalse(pager.shouldLoad(3, 10))
    }
    @Test fun completePreviewNeverAsks() {
        val pager = ProfileTripsPager.reset((0 until 7).map { trip(it) }, 7)
        assertNull(pager.cursor); assertFalse(pager.shouldLoad(6, 7))
    }
    @Test fun appendsDeduplicatesAndStops() {
        val cards = (0 until 10).map { trip(it) }
        val pager = ProfileTripsPager.reset(cards, 13).begin()
        assertFalse(pager.shouldLoad(9, 10))
        val (next, all) = pager.apply(SocialFeedResponse(listOf(cards[9], trip(10), trip(11), trip(12))), cards)
        assertEquals(13, all.size); assertFalse(next.shouldLoad(12, 13))
    }
    @Test fun repeatedCursorStops() {
        val cards = SocialFixtures.cards
        val (first, all) = ProfileTripsPager.reset(cards, 100).apply(SocialFeedResponse(listOf(trip(20)), "c1"), cards)
        assertEquals("c1", first.cursor)
        assertNull(first.apply(SocialFeedResponse(listOf(trip(21)), "c1"), all).first.cursor)
    }
    @Test fun failedPageKeepsCardsAndStops() {
        val pager = ProfileTripsPager.reset(SocialFixtures.cards, 45).begin().fail()
        assertFalse(pager.isLoading); assertFalse(pager.shouldLoad(9, 10))
    }
    @Test fun hiddenBadgeKeepsPaidCosmetics() { assertTrue(SocialFixtures.profile().copy(isPlus = false, hasPlus = true).showsPaidCosmetics) }
    @Test fun noSubscriptionShowsNone() { assertFalse(SocialFixtures.profile().copy(isPlus = false, hasPlus = false).showsPaidCosmetics) }
    @Test fun oldServerFallsBackToBadge() {
        assertTrue(SocialFixtures.profile().copy(isPlus = true, hasPlus = null).showsPaidCosmetics)
        assertFalse(SocialFixtures.profile().copy(isPlus = null, hasPlus = null).showsPaidCosmetics)
    }
    private val notFound = ApiError.UnknownServer("USER_NOT_FOUND", "User not found")
    @Test fun closedProfileKeepsPublicIdentity() {
        val author = SocialAuthor(SocialFixtures.account, "Марина К.", "😎", 7)
        assertEquals(ProfileUnavailableState.Closed("Марина К.", "😎"), ProfileUnavailableState.from(notFound, author))
    }
    @Test fun coldLinkNeverConfirmsIdentity() { assertEquals(ProfileUnavailableState.Unavailable, ProfileUnavailableState.from(notFound, null)) }
    @Test fun blankAuthorStaysAnonymous() { assertEquals(ProfileUnavailableState.Unavailable, ProfileUnavailableState.from(notFound, SocialFixtures.author.copy(displayName = "  ", avatarEmoji = null))) }
    @Test fun networkIsTransient() { assertEquals(ProfileUnavailableState.Transient, ProfileUnavailableState.from(java.io.IOException("offline"), SocialFixtures.author)) }
    @Test fun unknownServerIsTransient() { assertEquals(ProfileUnavailableState.Transient, ProfileUnavailableState.from(ApiError.UnknownServer("SOMETHING_NEW", "boom"), null)) }
    @Test fun banIsTransient() { assertEquals(ProfileUnavailableState.Transient, ProfileUnavailableState.from(ApiError.UserBanned, SocialFixtures.author)) }
    @Test fun countsKnownIdsWithSameDenominator() {
        val secret = BadgeDefinitions.all.first { it.isHidden }
        val plain = BadgeDefinitions.all.first { !it.isHidden }
        val catalogue = PublicBadgeCatalogue.build(listOf(plain.id, secret.id, "badge_from_a_newer_build"))
        assertEquals(setOf(plain.id, secret.id), catalogue.unlockedIds)
        assertEquals(BadgeDefinitions.all.count { !it.isHidden } + 1, catalogue.totalCount)
    }
    @Test fun rarestLeadGrid() {
        val catalogue = PublicBadgeCatalogue.build(BadgeDefinitions.all.map { it.id })
        val rarities = catalogue.orderedUnlocked.map { id -> BadgeDefinitions.all.first { it.id == id }.rarity.rawValue }
        assertEquals(BadgeDefinitions.all.size, rarities.size); assertEquals(rarities.sortedDescending(), rarities)
    }
    @Test fun emptyCollectionCountsCatalogue() {
        val c = PublicBadgeCatalogue.build(emptyList())
        assertEquals(0, c.unlockedIds.size); assertEquals(BadgeDefinitions.all.count { !it.isHidden }, c.totalCount); assertTrue(c.orderedUnlocked.isEmpty())
    }
    @Test fun badgesEndpointAndResponse() {
        assertTrue(ApiEndpoint.userBadges(SocialFixtures.account).endsWith("/badges"))
        assertEquals(listOf("first_trip", "iron_butt"), NetworkJson.wire.decodeFromString<SocialBadgesResponse>("""{"badges":["first_trip","iron_butt"]}""").badges)
    }
}
