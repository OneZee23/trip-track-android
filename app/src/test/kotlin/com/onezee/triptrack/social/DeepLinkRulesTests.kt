// iOS: TripTrackApp.handleDeepLink/handleWebLink, NotificationManager; no dedicated XCTest exists.
package com.onezee.triptrack.social

import com.onezee.triptrack.feature.social.*
import kotlin.test.*
import org.junit.Test

class DeepLinkRulesTests {
    private val id=SocialFixtures.account
    @Test fun profileWebLinks() { listOf("https://trip-track.app/u/$id","https://www.trip-track.app/u/${id.toString().uppercase()}").forEach { assertEquals(DeepLink.Profile(id),DeepLink.parse(it)) } }
    @Test fun customRoutes() {
        assertEquals(DeepLink.Profile(id),DeepLink.parse("triptrack://profile/$id"));assertEquals(DeepLink.Profile(id),DeepLink.parse("triptrack://u/$id"))
        assertEquals(DeepLink.Trip(id),DeepLink.parse("triptrack://trip/$id"));assertEquals(DeepLink.Journey(id),DeepLink.parse("triptrack://journey/$id"));assertEquals(DeepLink.Recording,DeepLink.parse("triptrack://recording"))
    }
    @Test fun shareLinksResolveSameKindOnly() {
        val t=DeepLink.parse("https://trip-track.app/s/abc_-123")!!;val j=DeepLink.parse("https://trip-track.app/j/abc_-123")!!
        assertEquals("/s/abc_-123",ShareCodePage.path(t));assertEquals("/j/abc_-123",ShareCodePage.path(j))
        val html="""<a href="triptrack://trip/$id">Open</a>"""
        assertEquals(DeepLink.Trip(id),ShareCodePage.target(html,t));assertNull(ShareCodePage.target(html,j))
        assertEquals(DeepLink.Journey(id),ShareCodePage.target("triptrack://journey/$id",j));assertNull(ShareCodePage.target("expired",t))
    }
    @Test fun untrustedAndMalformedLinksIgnored() {
        listOf("https://example.org/u/$id","https://trip-track.app.example.org/u/$id","https://trip-track.app/u/nope","https://trip-track.app/s/a%2Fb","https://trip-track.app/","triptrack://trip/1-1-1-1-1","file:///u/$id","broken %").forEach { assertNull(DeepLink.parse(it),it) }
    }
    @Test fun profileSharingKeepsCanonicalUrl() { assertEquals("https://trip-track.app/u/${id.toString().uppercase()}",profileShareUrl(id)) }
    @Test fun pushesDoNotOpenUnacceptedPrivateTrip() {
        listOf("REACTION","COMMENT","COMPANION_ACCEPTED").forEach { assertEquals(DeepLink.Trip(id),DeepLink.fromPush(it,mapOf("tripId" to "$id"))) }
        listOf("FOLLOW","COMPANION_INVITE").forEach { assertEquals(DeepLink.Feed,DeepLink.fromPush(it,mapOf("tripId" to "$id"))) }
        assertNull(DeepLink.fromPush("FUTURE",mapOf("tripId" to "$id")));assertNull(DeepLink.fromPush("COMMENT",mapOf("tripId" to "broken")))
        assertFalse(DeepLink.refreshesInbox("NEW_ACCOUNT"));assertTrue(DeepLink.refreshesInbox("COMPANION_INVITE"))
    }
}
