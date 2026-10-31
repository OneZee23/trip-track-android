// iOS: AuthSessionExpiryTests.swift, CompanionsCacheSignOutTests.swift, AuthService.handleAuthorization /
// prepareForIdentity / signOut / deleteAccount; Android adds the Google credential flows
package com.onezee.triptrack.feature.account

import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.model.SyncStatus
import com.onezee.triptrack.network.ApiError
import com.onezee.triptrack.network.GoogleNonce
import com.onezee.triptrack.network.NetworkJson
import com.onezee.triptrack.network.TokenStore
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class AuthServiceTests : AccountTestCase() {
    private val t0: Instant = Instant.parse("2026-10-01T09:00:00Z")

    /** A soft expiry as a previous launch left it: no access token, the refresh token kept. */
    private fun seedSoftExpired(account: UUID, name: String? = "Тестовый Водитель") {
        vault.values = buildMap {
            put(TokenStore.refreshTokenKey, "refresh-$account")
            put(TokenStore.accountIdKey, account.toString())
            put(TokenStore.signedInKey, "true")
            if (name != null) put(TokenStore.userNameKey, name)
        }
        tokenStore.hydrate()
    }

    private fun trip(isPrivate: Boolean, serverCreatedAt: Instant?, syncStatus: SyncStatus): UUID = runBlocking {
        val id = UUID.randomUUID()
        db.tripDao().upsertTrip(TripEntity(id = id, startDate = t0, endDate = t0.plusSeconds(3600), isPrivate = isPrivate,
            serverCreatedAt = serverCreatedAt, syncStatus = syncStatus.rawValue, userId = localUser, lastModifiedAt = t0, distance = 12_000.0))
        id
    }

    private fun cloudSyncOn() {
        prefs.set(AccountSettings.CLOUD_KEY, "true")
        prefs.set(AccountSettings.FIRST_TOGGLE_KEY, "true")
    }

    // MARK: - Sign in

    @Test fun aNewAccountSignsInWithTheRawNonceAndTakesItsGoogleName() {
        routeLogin(accountA, name = null, isNew = true); routeQuietAccount(accountA)
        credentials.answers += token()

        assertEquals(SignInOutcome.SignedIn(isNewAccount = true), runBlocking { auth.signIn(context) })

        val ask = credentials.asks.single()
        assertEquals(GoogleSignInFlow.button, ask.flow)
        val login = requestsTo("/auth/google/login").single()
        assertNull(login.headers["x-access-token"], "login is public")
        val body = NetworkJson.wire.parseToJsonElement(login.body!!.utf8()).jsonObject
        // Google got the SHA-256 hex of the raw nonce; the server gets the raw nonce itself.
        val raw = body.getValue("nonce").jsonPrimitive.content
        assertEquals(GoogleNonce.fromRaw(raw).sha256, ask.hashedNonce)
        assertNotEquals(raw, ask.hashedNonce)
        assertEquals("header.payload.signature", body.getValue("identityToken").jsonPrimitive.content)
        assertEquals(localUser.toString(), body.getValue("localUserId").jsonPrimitive.content)
        assertEquals("android", body.getValue("platform").jsonPrimitive.content)
        assertEquals("Pixel Test", body.getValue("deviceName").jsonPrimitive.content)
        assertEquals("1.0-test", body.getValue("appVersion").jsonPrimitive.content)

        val state = awaitState { it.isSignedIn }
        assertEquals(accountA, state.accountId)
        assertEquals("Alex Example", state.userName)
        assertEquals("alex@gmail.com", state.userEmail)
        assertEquals("access-$accountA", vault.values?.get(TokenStore.accessTokenKey))
        // The engine follows the account, then the first pass; the nameless account gets the name.
        awaitCalls { "firstSync" in it }
        assertEquals(listOf("switch:$accountA", "firstSync"), sync.calls.filter { it.startsWith("switch") || it == "firstSync" })
        awaitRequests("/auth/profile-update")
        assertTrue(requestsTo("/auth/profile-update").first().body!!.utf8().contains("\"displayName\":\"Alex Example\""))
        // The globe opt-in is read back from the server before any profile push.
        assertEquals("true", prefs.string(AccountSettings.PUBLIC_MAP_KEY))
    }

    @Test fun aReturningAccountKeepsItsServerNameOverGoogles() {
        routeLogin(accountA, name = "Road Runner"); routeQuietAccount(accountA)
        credentials.answers += token(name = "Alex Example")

        assertEquals(SignInOutcome.SignedIn(isNewAccount = false), runBlocking { auth.signIn(context) })

        assertEquals("Road Runner", awaitState { it.isSignedIn }.userName)
        // One confirmation push of the server's own name (no record yet means «not confirmed»).
        awaitRequests("/auth/profile-update")
        assertTrue(requestsTo("/auth/profile-update").first().body!!.utf8().contains("Road Runner"))
        runBlocking { kotlinx.coroutines.withTimeout(5_000) { while (prefs.string(ProfileSyncLatch.KEY) != "true") kotlinx.coroutines.delay(10) } }
    }

    @Test fun aNamelessAccountWithoutAGoogleNameGetsAGeneratedOne() {
        routeLogin(accountA, name = null, isNew = true); routeQuietAccount(accountA)
        credentials.answers += token(name = null)
        runBlocking { auth.signIn(context) }
        assertTrue(RandomDisplayName.isPlaceholder(awaitState { it.isSignedIn }.userName))
    }

    @Test fun aClosedGoogleSheetChangesNothing() {
        seedSoftExpired(accountA)
        credentials.answers += GoogleCredentialResult.Cancelled

        assertEquals(SignInOutcome.Cancelled, runBlocking { auth.signIn(context) })

        assertTrue(requests.isEmpty())
        val state = awaitState { it.needsReauth }
        assertEquals(accountA, state.accountId)
        assertEquals("refresh-$accountA", vault.values?.get(TokenStore.refreshTokenKey))
        assertTrue(sync.calls.isEmpty())
    }

    /** iOS: nothing about the previous identity is touched until the login answered with success. */
    @Test fun aNetworkErrorKeepsThePreviousSessionAsItWas() {
        seedSoftExpired(accountA); cloudSyncOn()
        server.close()
        credentials.answers += token()

        assertIs<SignInOutcome.Failed>(runBlocking { auth.signIn(context) })

        val state = awaitState { it.needsReauth }
        assertFalse(state.isSignedIn); assertFalse(state.isAuthenticating)
        assertEquals(accountA, state.accountId)
        assertEquals("Тестовый Водитель", state.userName)
        assertEquals("refresh-$accountA", vault.values?.get(TokenStore.refreshTokenKey))
        assertTrue(accountSettings.cloudSyncEnabled)
        assertTrue(sync.calls.isEmpty())
    }

    @Test fun aRejectedGoogleTokenIsAFailureAndNoSession() {
        routes["/auth/google/login"] = { envelopeError("INVALID_GOOGLE_TOKEN") }
        credentials.answers += token()
        assertIs<SignInOutcome.Failed>(runBlocking { auth.signIn(context) })
        assertFalse(auth.state.value.isSignedIn)
        assertNull(vault.values)
    }

    @Test fun anUnreachableServerStatusIsAFailureToo() {
        routes["/auth/google/login"] = { mockwebserver3.MockResponse.Builder().code(503).build() }
        credentials.answers += token()
        assertIs<SignInOutcome.Failed>(runBlocking { auth.signIn(context) })
        assertNull(vault.values)
    }

    @Test fun withoutAClientIdNothingIsAsked() {
        client.close(); clientId = ""; buildAuth()
        assertEquals(SignInOutcome.NotConfigured, runBlocking { auth.signIn(context) })
        assertTrue(credentials.asks.isEmpty())
        assertTrue(requests.isEmpty())
    }

    @Test fun theReturningFlowTriesAuthorizedAccountsThenEveryAccountThenTheButton() {
        routeLogin(accountA, name = "Road Runner"); routeQuietAccount(accountA)
        credentials.answers += GoogleCredentialResult.NoAccount
        credentials.answers += GoogleCredentialResult.NoAccount
        credentials.answers += token()

        assertIs<SignInOutcome.SignedIn>(runBlocking { auth.signIn(context, GoogleSignInFlow.returning) })

        assertEquals(
            listOf(GoogleSignInFlow.returning to true, GoogleSignInFlow.returning to false, GoogleSignInFlow.button to false),
            credentials.asks.map { it.flow to it.authorizedOnly },
        )
        assertEquals(1, credentials.asks.map { it.hashedNonce }.distinct().size, "one nonce per attempt")
    }

    @Test fun theReturningFlowStopsAtTheFirstAuthorizedAccount() {
        routeLogin(accountA, name = "Road Runner"); routeQuietAccount(accountA)
        credentials.answers += token()
        runBlocking { auth.signIn(context, GoogleSignInFlow.returning) }
        assertEquals(listOf(GoogleSignInFlow.returning to true), credentials.asks.map { it.flow to it.authorizedOnly })
    }

    @Test fun noAccountOnTheButtonIsAFailureWithoutARequest() {
        credentials.answers += GoogleCredentialResult.NoAccount
        assertEquals(SignInOutcome.Failed("noAccount"), runBlocking { auth.signIn(context) })
        assertTrue(requests.isEmpty())
    }

    // MARK: - prepareForIdentity

    /** iOS: testSignInWithDifferentAppleIdPurgesInheritedState. */
    @Test fun aDifferentAccountAfterASoftExpiryPurgesTheInheritedState() {
        seedSoftExpired(accountA); cloudSyncOn(); latch.markConfirmed()
        val pendingPublic = trip(isPrivate = false, serverCreatedAt = null, syncStatus = SyncStatus.pendingUpload)
        routeLogin(accountB, name = null); routeQuietAccount(accountB)
        credentials.answers += token(name = null)

        assertIs<SignInOutcome.SignedIn>(runBlocking { auth.signIn(context) })

        val state = awaitState { it.isSignedIn }
        assertEquals(accountB, state.accountId)
        assertNotEquals("Тестовый Водитель", state.userName, "the previous name must not land on the new account")
        assertFalse(accountSettings.cloudSyncEnabled, "the previous Cloud Sync consent must not carry over")
        assertEquals("false", prefs.string(AccountSettings.FIRST_TOGGLE_KEY))
        // The old queue is emptied BEFORE the new account's engine exists.
        val clear = sync.calls.indexOf("clear")
        assertTrue(clear >= 0 && clear < sync.calls.indexOf("switch:$accountB"))
        val row = runBlocking { db.tripDao().trip(pendingPublic)!! }
        assertTrue(row.isPrivate, "a public trip never uploaded must not upload under the new account")
    }

    /** iOS: testSignInWithSameAppleIdKeepsState. */
    @Test fun theSameAccountSigningInAgainKeepsItsState() {
        seedSoftExpired(accountA, name = "Local Rename"); cloudSyncOn()
        routeLogin(accountA, name = "Old Server Name"); routeQuietAccount(accountA)
        credentials.answers += token()

        assertIs<SignInOutcome.SignedIn>(runBlocking { auth.signIn(context) })

        val state = awaitState { it.isSignedIn }
        assertEquals("Local Rename", state.userName)
        assertTrue(accountSettings.cloudSyncEnabled)
        assertFalse("clear" in sync.calls)
        assertFalse(state.needsReauth)
    }

    /** A guest's library is the phone's: the first sign-in purges nothing. */
    @Test fun aGuestsFirstSignInPurgesNothing() {
        val publicDraftOfAGuest = trip(isPrivate = false, serverCreatedAt = null, syncStatus = SyncStatus.pendingUpload)
        routeLogin(accountA, name = "Road Runner"); routeQuietAccount(accountA)
        credentials.answers += token()
        runBlocking { auth.signIn(context) }
        awaitState { it.isSignedIn }
        assertFalse("clear" in sync.calls)
        assertFalse(runBlocking { db.tripDao().trip(publicDraftOfAGuest)!! }.isPrivate)
    }

    // MARK: - Soft expiry and recovery

    /** iOS: testSessionExpiredDropsTokensButKeepsEverythingElse / testInvalidRefreshTokenAsksForReauthButKeepsTheToken. */
    @Test fun aRejectedRefreshExpiresSoftlyAndKeepsEverything() {
        seedSession(accountA); cloudSyncOn()
        routes["/auth/me"] = { envelopeError("USER_NOT_AUTH") }
        routes["/auth/refresh"] = { envelopeError("INVALID_REFRESH_TOKEN") }

        runBlocking { auth.refreshMe() }

        val state = awaitState { it.needsReauth }
        assertFalse(state.isSignedIn)
        assertEquals(accountA, state.accountId)
        assertEquals("Тестовый Водитель", state.userName)
        assertNull(vault.values?.get(TokenStore.accessTokenKey))
        assertEquals("refresh-$accountA", vault.values?.get(TokenStore.refreshTokenKey), "the refresh token survives a soft death")
        assertTrue(accountSettings.cloudSyncEnabled)
        assertFalse("clear" in sync.calls); assertFalse("switch:null" in sync.calls)
        assertEquals(0, credentials.cleared)
    }

    /** iOS: testRefreshAfterRelaunchRevivesSoftExpiredSession / testSuccessfulRefreshAfterSoftDeathRestoresSessionSilently. */
    @Test fun aLaterRefreshRevivesTheSessionWithoutATap() {
        seedSoftExpired(accountA)
        routes["/auth/refresh"] = { ok("{\"accessToken\":\"back\",\"refreshToken\":\"back-refresh\"}") }

        auth.onForeground()

        val state = awaitState { it.isSignedIn }
        assertFalse(state.needsReauth)
        assertEquals("back", vault.values?.get(TokenStore.accessTokenKey))
        assertNull(vault.values?.get(TokenStore.expiredKey))
        // The queue paused by the expiry gets a pass.
        awaitCalls { "requestSync" in it }
    }

    /** iOS: testKeychainUnavailableAtLaunchKeepsSessionAndReReadsLater. */
    @Test fun aLockedStoreAtLaunchIsUnknownNotSignedOut() {
        vault.values = mapOf(TokenStore.accessTokenKey to "a", TokenStore.refreshTokenKey to "r",
            TokenStore.accountIdKey to accountA.toString(), TokenStore.signedInKey to "true")
        vault.unavailable = true

        auth.start()

        val deferred = awaitState { it.hydrationDeferred }
        assertFalse(deferred.needsReauth)
        vault.unavailable = false
        auth.onForeground()
        assertTrue(awaitState { it.isSignedIn && !it.hydrationDeferred }.isSignedIn)
        awaitCalls { "switch:$accountA" in it }
    }

    // MARK: - The three hard sign-outs

    /** iOS: testUserSignOutClearsEverythingIncludingRefreshToken; CompanionsCacheSignOutTests. */
    @Test fun signOutClearsEverythingIncludingTheRefreshToken() {
        seedSession(accountA); routeQuietAccount(accountA); cloudSyncOn(); latch.markConfirmed()
        val pendingPublic = trip(isPrivate = false, serverCreatedAt = null, syncStatus = SyncStatus.pendingUpload)
        val cached = trip(isPrivate = true, serverCreatedAt = null, syncStatus = SyncStatus.synced)
        runBlocking { db.tripDao().upsertTrip(db.tripDao().trip(cached)!!.copy(companionsJSON = "[]")) }

        runBlocking { auth.signOut() }

        val logout = requestsTo("/auth/logout").single()
        assertEquals("access-$accountA", logout.headers["x-access-token"])
        assertEquals(emptyMap(), vault.values)
        val state = auth.state.value
        assertFalse(state.isSignedIn); assertFalse(state.needsReauth); assertNull(state.accountId); assertNull(state.userName)
        assertFalse(accountSettings.cloudSyncEnabled)
        assertEquals("false", prefs.string(AccountSettings.FIRST_TOGGLE_KEY))
        assertNull(prefs.string(ProfileSyncLatch.KEY))
        assertTrue(sync.calls.indexOf("clear") < sync.calls.indexOf("switch:null"))
        assertEquals(1, credentials.cleared)
        runBlocking {
            assertTrue(db.tripDao().trip(pendingPublic)!!.isPrivate)
            assertEquals(SyncStatus.synced.rawValue, db.tripDao().trip(pendingPublic)!!.syncStatus)
            assertNull(db.tripDao().trip(cached)!!.companionsJSON)
        }
    }

    /** iOS: signOut skips /auth/logout without an access token: it could only answer USER_NOT_AUTH. */
    @Test fun signOutAfterASoftExpirySkipsTheLogoutRequest() {
        seedSoftExpired(accountA)
        runBlocking { auth.signOut() }
        assertTrue(requestsTo("/auth/logout").isEmpty())
        assertEquals(emptyMap(), vault.values)
        assertFalse(auth.state.value.needsReauth)
    }

    /** «Hide public and sign out»: a private upsert of every public trip, then the logout. */
    @Test fun hidePublicSendsAPrivateUpsertBeforeTheLogout() {
        seedSession(accountA); routeQuietAccount(accountA)
        val published = trip(isPrivate = false, serverCreatedAt = t0, syncStatus = SyncStatus.synced)
        routes["/trips/upsert"] = { ok("{\"id\":\"$published\",\"conflictVersion\":3,\"serverCreatedAt\":\"2026-10-01T09:00:00.000Z\"}") }

        assertEquals(1, runBlocking { auth.publishedTripCount() })
        runBlocking { auth.signOut(hidePublic = true) }

        val order = requests.map { it.url.encodedPath }.filter { it == "/trips/upsert" || it == "/auth/logout" }
        assertEquals(listOf("/trips/upsert", "/auth/logout"), order)
        val body = NetworkJson.wire.parseToJsonElement(requestsTo("/trips/upsert").single().body!!.utf8()).jsonObject
        assertEquals("true", body.getValue("isPrivate").jsonPrimitive.content)
        val row = runBlocking { db.tripDao().trip(published)!! }
        assertTrue(row.isPrivate)
        assertEquals(3, row.conflictVersion)
    }

    @Test fun deleteAccountGoesToTheServerFirstThenWipesThePhone() {
        seedSession(accountA); routes["/auth/delete-account"] = { ok() }
        runBlocking { auth.deleteAccount() }
        assertEquals(1, requestsTo("/auth/delete-account").size)
        assertEquals(1, wiped)
        assertEquals(emptyMap(), vault.values)
        assertTrue("switch:null" in sync.calls)
        assertEquals(1, credentials.cleared)
    }

    /** A refusal keeps the account alive with everything left to try again from. */
    @Test fun aRefusedDeletionKeepsTheAccountAndThePhone() {
        seedSession(accountA); routes["/auth/delete-account"] = { envelopeError("UNKNOWN_SERVER_ERROR") }
        assertFailsWith<ApiError> { runBlocking { auth.deleteAccount() } }
        assertEquals(0, wiped)
        assertTrue(auth.state.value.isSignedIn)
        assertEquals("access-$accountA", vault.values?.get(TokenStore.accessTokenKey))
        assertTrue(sync.calls.isEmpty())
    }

    @Test fun aBanSignsOutLikeTheButton() {
        seedSession(accountA); cloudSyncOn()
        routes["/auth/me"] = { envelopeError("USER_BANNED") }
        runBlocking { auth.refreshMe() }
        // The Google credential is cleared last: by then the whole cleanup has run.
        runBlocking { kotlinx.coroutines.withTimeout(5_000) { while (credentials.cleared == 0) kotlinx.coroutines.delay(10) } }
        assertTrue("switch:null" in sync.calls)
        assertEquals(emptyMap(), vault.values)
        assertFalse(accountSettings.cloudSyncEnabled)
    }

    // MARK: - Account snapshot

    @Test fun meSeedsThePublicSwitchAndBackfillsTheEmail() {
        seedSession(accountA)
        routes["/auth/me"] = { ok("{\"id\":\"$accountA\",\"isPublic\":false,\"email\":\"road@gmail.com\"}") }
        runBlocking { auth.refreshMe() }
        val state = awaitState { it.isPublicProfile != null }
        assertEquals(false, state.isPublicProfile)
        assertEquals("road@gmail.com", state.userEmail)
    }

    @Test fun anotherAccountsMeIsIgnored() {
        seedSession(accountA)
        routes["/auth/me"] = { ok("{\"id\":\"$accountB\",\"isPublic\":false}") }
        runBlocking { auth.refreshMe() }
        assertNull(auth.state.value.isPublicProfile)
    }

    @Test fun thePublicProfileSwitchSendsOnlyIsPublic() {
        seedSession(accountA); routes["/auth/profile-update"] = { ok() }
        assertTrue(runBlocking { auth.setPublicProfile(false) })
        assertEquals("{\"isPublic\":false}", requestsTo("/auth/profile-update").single().body!!.utf8())
        assertEquals(false, awaitState { it.isPublicProfile != null }.isPublicProfile)
    }

    @Test fun aRefusedSwitchReportsFailure() {
        seedSession(accountA); routes["/auth/profile-update"] = { envelopeError("VALIDATION_FAILED") }
        assertFalse(runBlocking { auth.setPublicProfile(false) })
        assertNull(auth.state.value.isPublicProfile)
    }

    @Test fun aNameTheServerNeverConfirmedIsPushedAtLaunch() {
        seedSession(accountA, name = "Клаус"); latch.markUnconfirmed(); routes["/auth/profile-update"] = { ok() }
        auth.start()
        awaitRequests("/auth/profile-update")
        assertEquals("{\"displayName\":\"Клаус\"}", requestsTo("/auth/profile-update").single().body!!.utf8())
    }
}
