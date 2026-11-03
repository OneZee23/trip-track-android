// iOS: PublicProfileView.toggleFollow/toggleBlock, ReportSheet.submit, TripCommentsStore,
// TripTrackApp.handleDeepLink. Actual ApiClient against MockWebServer envelopes.
package com.onezee.triptrack.social

import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.feed.ApiTripSocial
import com.onezee.triptrack.feature.social.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import mockwebserver3.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SocialWireBehaviourTests : SocialScreenTest() {
    private lateinit var server:MockWebServer
    private lateinit var client:ApiClient
    private lateinit var wire:ApiSocial
    private var failAction=false
    private var commentCreateCalls=0
    private val commentId=UUID.fromString("77777777-7777-4777-8777-777777777777")
    private fun ok(payload:String="{}")=MockResponse.Builder().body("""{"status":"ok","payload":$payload}""").build()
    private fun denied(code:String)=MockResponse.Builder().body("""{"status":"error","code":"$code","message":"test"}""").build()
    private inline fun <reified T> encoded(value:T)=NetworkJson.wire.encodeToString(value)
    @Before fun connect() {
        server=MockWebServer()
        server.dispatcher=object:Dispatcher(){ override fun dispatch(request:RecordedRequest):MockResponse = when {
            request.url.encodedPath.endsWith("/profile") -> ok(encoded(SocialFixtures.profile()))
            request.url.encodedPath.endsWith("/trips") -> ok(encoded(PublicTripsResponse(emptyList())))
            request.url.encodedPath.endsWith("/garage") -> ok(encoded(PublicGarageResponse(emptyList())))
            request.url.encodedPath=="/social/follow" || request.url.encodedPath=="/social/unfollow" -> if(failAction)denied("USER_NOT_FOUND") else ok("""{"following":true}""")
            request.url.encodedPath=="/social/block" || request.url.encodedPath=="/social/unblock" -> if(failAction)denied("USER_NOT_FOUND") else ok("""{"blocked":true}""")
            request.url.encodedPath=="/social/report" -> ok("""{"reported":true}""")
            request.url.encodedPath==ApiEndpoint.socialSearch -> ok(encoded(SocialUsersResponse(listOf(SocialFixtures.author))))
            request.url.encodedPath==ApiEndpoint.socialSuggested -> ok(encoded(SocialSuggestedResponse(listOf(SocialSuggestedUser(SocialFixtures.author.id, SocialFixtures.author.displayName, SocialFixtures.author.avatarEmoji, SocialFixtures.author.profileLevel, "sharedRegion", 425.2)))))
            request.url.encodedPath==ApiEndpoint.companionsList -> ok(encoded(CompanionsListResponse(listOf(CompanionItem(owner, "Driver", "🚗", CompanionStatus.accepted)), true)))
            request.url.encodedPath==ApiEndpoint.companionsCandidates -> ok(encoded(CompanionsCandidatesResponse(listOf(CompanionCandidate(SocialFixtures.account, "Rider", "🛻", 8)), null)))
            request.url.encodedPath==ApiEndpoint.companionsInvite -> ok("""{"companion":{"accountId":"${SocialFixtures.account}","status":0}}""")
            request.url.encodedPath==ApiEndpoint.companionsRemove -> ok("""{"ok":true}""")
            request.url.encodedPath==ApiEndpoint.companionsMyTrips -> ok(encoded(CompanionsMyTripsResponse(listOf(SocialFixtures.trip(0)), "next-page")))
            request.url.encodedPath=="/social/comments" -> ok(encoded(TripCommentsResponse(listOf(TripComment(commentId,SocialFixtures.author,"Тест",SocialFixtures.now,true)))))
            request.url.encodedPath=="/social/comment" -> { commentCreateCalls++; if(failAction)MockResponse.Builder().code(503).body("down").build() else ok("""{"id":"$commentId","createdAt":"2026-10-25T12:00:00Z"}""") }
            request.url.encodedPath=="/social/comment/delete" -> if(failAction)denied("VALIDATION_FAILED") else ok("""{"deleted":true}""")
            request.url.encodedPath=="/social/trip" -> ok(encoded(SocialTripResponse(SocialFixtures.trip(0))))
            request.url.encodedPath.startsWith("/s/") -> MockResponse.Builder().body("""<a href="triptrack://trip/${SocialFixtures.trip(0).id}">Open</a>""").build()
            request.url.encodedPath.startsWith("/j/") -> MockResponse.Builder().body("""<a href="triptrack://journey/${SocialFixtures.account}">Open</a>""").build()
            else -> ok("{}")
        } }
        server.start()
        val vault=object:SecureVault {
            var values=mapOf(TokenStore.accessTokenKey to "test-access",TokenStore.refreshTokenKey to "test-refresh",TokenStore.accountIdKey to owner.toString(),TokenStore.signedInKey to "true")
            override fun read():SecureRead<Map<String,String>> = if(values.isEmpty())SecureRead.Missing else SecureRead.Value(values)
            override fun write(values:Map<String,String>) { this.values=values }
        }
        val store=TokenStore(vault)
        client=ApiClient(store,server.url("/").toString(),pause={})
        wire=ApiSocial(client){true}
        services.close()
        services=makeSocial(wire) { path -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().url(server.url(path)).build()).execute().use { it.body.string() }
        } }
        feature.socialApi=ApiTripSocial(client){true}
    }
    @After fun disconnect() { client.close();server.close() }
    private fun request(path:String):RecordedRequest {
        repeat(server.requestCount) { val r=server.takeRequest(1,TimeUnit.SECONDS)!!;if(r.url.encodedPath==path)return r }
        error("No $path request")
    }
    private fun body(request:RecordedRequest)=NetworkJson.wire.parseToJsonElement(request.body!!.utf8()).jsonObject
    @Test fun followAndUnfollowUpdateCountsAndWire() {
        val vm=services.profile(SocialFixtures.account,null)
        networkMain { vm.refresh().join();vm.toggleFollow()!!.join() }
        assertTrue(vm.state.value.profile!!.isFollowing!!);assertEquals(13,vm.state.value.profile!!.followerCount)
        assertEquals(SocialFixtures.account.toString(),body(request("/social/follow"))["targetAccountId"]!!.jsonPrimitive.content.lowercase())
        networkMain { vm.toggleFollow()!!.join() }
        assertFalse(vm.state.value.profile!!.isFollowing!!);assertEquals(12,vm.state.value.profile!!.followerCount)
        request("/social/unfollow")
    }
    @Test fun failedFollowReturnsServerTruth() {
        val vm=services.profile(SocialFixtures.account,null);networkMain { vm.refresh().join() };failAction=true
        networkMain { vm.toggleFollow()!!.join() };assertEquals(12,vm.state.value.profile!!.followerCount);assertFalse(vm.state.value.isTogglingFollow)
    }
    @Test fun blockUnblockAndFailureRollback() {
        val vm=services.profile(SocialFixtures.account,null);networkMain { vm.refresh().join();vm.toggleBlock()!!.join() }
        assertTrue(vm.state.value.isBlocked);request("/social/block")
        failAction=true;networkMain { vm.toggleBlock()!!.join() };assertTrue(vm.state.value.isBlocked)
        failAction=false;networkMain { vm.toggleBlock()!!.join() };assertFalse(vm.state.value.isBlocked);request("/social/unblock")
    }
    @Test fun reportTypesReasonsAndNotesPreserveWire() {
        listOf(ReportTarget.User(SocialFixtures.account),ReportTarget.Trip(SocialFixtures.account),ReportTarget.Comment(commentId),ReportTarget.Vehicle(SocialFixtures.car.id),ReportTarget.VehiclePhoto(commentId)).forEach { target ->
            networkMain { wire.report(target,ReportReason.harassment,"Details") }
            val b=body(request("/social/report"));assertEquals(target.type,b["targetType"]!!.jsonPrimitive.content);assertEquals("harassment",b["reason"]!!.jsonPrimitive.content);assertEquals("Details",b["notes"]!!.jsonPrimitive.content)
        }
    }
    @Test fun commentsLoadPostDeleteAndCountChanges() {
        val vm=services.comments(SocialFixtures.trip(0).id);networkMain { vm.load()!!.join() }
        assertEquals("Тест",vm.state.value.comments.single().text)
        networkMain { vm.setText("  Новая строка\nещё строка  ");vm.post()!!.join() }
        assertEquals("",vm.state.value.text);assertEquals(1,commentCreateCalls)
        val b=body(request("/social/comment"));assertEquals("Новая строка\nещё строка",b["text"]!!.jsonPrimitive.content)
        networkMain { vm.delete(commentId)!!.join() };assertTrue(vm.state.value.comments.isEmpty());request("/social/comment/delete")
    }
    @Test fun lostCommentResponseIsOneAttemptAndKeepsDraft() {
        val vm=services.comments(SocialFixtures.trip(0).id);failAction=true
        networkMain { vm.setText("Не потерять");vm.post()!!.join() }
        assertEquals(1,commentCreateCalls);assertEquals("Не потерять",vm.state.value.text);assertTrue(vm.state.value.comments.isEmpty());assertNotNull(vm.state.value.message)
    }
    @Test fun failedDeleteRestoresOriginalPosition() {
        val vm=services.comments(SocialFixtures.trip(0).id);networkMain { vm.load()!!.join() };val before=vm.state.value.comments;failAction=true
        networkMain { vm.delete(commentId)!!.join() };assertEquals(before,vm.state.value.comments)
    }
    @Test fun sharedTripLinkFetchesPageThenPublicTripAndReplacesStack() {
        networkMain { services.open(DeepLink.SharedTrip("abc123")) }
        // Wait on the network and the main looper, just as the navigation host does.
        waitForDestination { it is TripsDestination.TripDetail }
        val dest=feature.feedNavigator.top as TripsDestination.TripDetail
        assertEquals(SocialFixtures.trip(0).id,dest.id);assertNotNull(dest.social)
        request("/s/abc123");request("/social/trip")
    }
    @Test fun sharedJourneyResolvesToJourneyWithoutLocalWrite() {
        networkMain { services.open(DeepLink.SharedJourney("abc123")) };waitForDestination { it is TripsDestination.SocialJourney }
        assertEquals(SocialFixtures.account,(feature.feedNavigator.top as TripsDestination.SocialJourney).id);request("/j/abc123")
        assertNull(networkMain { feature.journey(SocialFixtures.account) })
    }
    @Test fun publicTripsSendMaximumLimitAndCursorWithoutDoubleEncoding() {
        val cursor = "2026-08-01T10:00:00.000Z|11111111-1111-1111-1111-111111111111"
        networkMain { wire.publicTrips(SocialFixtures.account, cursor, 500, SocialFixtures.car.id) }
        val r = request(ApiEndpoint.userTrips(SocialFixtures.account).substringBefore('?'))
        assertEquals(cursor, r.url.queryParameter("cursor"))
        assertEquals("500", r.url.queryParameter("limit"))
        assertEquals(SocialFixtures.car.id.toString(), r.url.queryParameter("vehicleId")!!.lowercase())
        assertFalse(r.url.toString().contains("%3F")); assertFalse(r.url.toString().contains("%25"))
    }
    @Test fun emptyReadBatchCannotMarkEverythingAndAllReadOmitsIds() {
        val before = server.requestCount
        networkMain { assertFailsWith<IllegalArgumentException> { wire.markRead(emptyList()) } }
        assertEquals(before, server.requestCount)
        networkMain { wire.markRead(null) }
        assertFalse("ids" in body(request("/notifications/mark-read")))
    }
    @Test fun discoverSearchAndSuggestionsKeepTheIosPostContract() {
        networkMain { assertEquals(SocialFixtures.author.id, wire.searchPeople("Иван", 25).users.single().id) }
        val search = request(ApiEndpoint.socialSearch)
        assertEquals("test-access", search.headers["x-access-token"])
        assertEquals(JsonObject(mapOf("query" to JsonPrimitive("Иван"), "limit" to JsonPrimitive(25))), body(search))
        networkMain { assertEquals("sharedRegion", wire.suggestedPeople(10).users.single().matchReason) }
        val suggested = request(ApiEndpoint.socialSuggested)
        assertEquals(JsonObject(mapOf("limit" to JsonPrimitive(10))), body(suggested))
    }
    @Test fun guestDiscoverCallsDoNotSendAnAccessToken() {
        val guest = ApiSocial(client) { false }
        networkMain { guest.searchPeople("Driver") }
        assertNull(request(ApiEndpoint.socialSearch).headers["x-access-token"])
        networkMain { guest.suggestedPeople() }
        assertNull(request(ApiEndpoint.socialSuggested).headers["x-access-token"])
    }
    @Test fun companionRosterCandidatesInviteAndRemoveUseTheIosContract() {
        val tripId = SocialFixtures.trip(0).id
        networkMain { assertEquals(CompanionStatus.accepted, wire.companions(tripId).items.single().status) }
        assertEquals(JsonObject(mapOf("tripId" to JsonPrimitive(tripId.toString()))), body(request(ApiEndpoint.companionsList)))
        networkMain { assertEquals(SocialFixtures.account, wire.companionCandidates(tripId, "Rider", null).items.single().accountId) }
        assertEquals(JsonObject(mapOf("tripId" to JsonPrimitive(tripId.toString()), "query" to JsonPrimitive("Rider"))), body(request(ApiEndpoint.companionsCandidates)))
        networkMain { assertEquals(CompanionStatus.pending, wire.inviteCompanion(tripId, SocialFixtures.account).status) }
        assertEquals(setOf("tripId", "accountId"), body(request(ApiEndpoint.companionsInvite)).keys)
        networkMain { wire.removeCompanion(tripId, SocialFixtures.account) }
        assertEquals(setOf("tripId", "accountId"), body(request(ApiEndpoint.companionsRemove)).keys)
    }
    @Test fun companionTripsKeepTheCursorAndDecodeTheIosResponse() {
        val cursor = "2026-10-20T10:00:00Z|${SocialFixtures.trip(0).id}"
        networkMain {
            val response = wire.companionTrips(cursor)
            assertEquals(SocialFixtures.trip(0).id, response.items.single().id)
            assertEquals("next-page", response.nextCursor)
        }
        val sent = body(request(ApiEndpoint.companionsMyTrips))
        assertEquals(setOf("cursor"), sent.keys)
        assertEquals(cursor, sent["cursor"]!!.jsonPrimitive.content)
    }
    private fun <T> networkMain(block: suspend () -> T): T {
        var result: Result<T>? = null
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { result = runCatching { block() } }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (result == null && System.nanoTime() < deadline) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(2)
        }
        return checkNotNull(result) { "Network did not finish" }.getOrThrow()
    }
    private fun waitForDestination(matches:(TripsDestination?)->Boolean) {
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(!matches(feature.feedNavigator.top) && System.nanoTime()<deadline) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5) }
        assertTrue(matches(feature.feedNavigator.top))
    }
}
