// iOS: Sync/APIClientTokenRefreshTests.swift, Sync/APIClientKeychainUnavailableTests.swift
package com.onezee.triptrack.network

import com.onezee.triptrack.model.*
import java.io.EOFException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlinx.coroutines.*
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

class ApiClientTokenRefreshTests {
    private lateinit var server: MockWebServer
    private lateinit var client: ApiClient
    private lateinit var store: TokenStore
    private lateinit var vault: FakeVault
    private val deaths = AtomicInteger()
    private val recoveries = AtomicInteger()
    private val refreshes = AtomicInteger()

    @Before fun setUp() {
        vault = FakeVault(); store = signedInStore(vault)
        server = MockWebServer().apply { start() }
        client = makeClient()
    }
    private fun makeClient(http: OkHttpClient = OkHttpClient(), pause: suspend (Long) -> Unit = {}): ApiClient =
        ApiClient(store, server.url("/").toString(), http, pause = pause,
            onSessionExpired = { deaths.incrementAndGet() }, onSessionRecovered = { recoveries.incrementAndGet() })
            .apply { refreshRecoveryDelays = emptyList() }
    @After fun tearDown() { client.close(); server.close() }
    private fun backend(reply: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = reply(request) }
    }
    private suspend fun call(): Payload = client.post("/foo", Req(1))

    @Test fun testSingleFlightRefreshOnConcurrent401() = runBlocking {
        val bothExpired = CountDownLatch(2)
        backend { request ->
            if (request.url.encodedPath == ApiEndpoint.refresh) {
                refreshes.incrementAndGet(); Thread.sleep(80); refreshed()
            } else if (request.headers["x-access-token"] == "expired") {
                bothExpired.countDown(); assertTrue(bothExpired.await(2, TimeUnit.SECONDS)); rejected()
            } else ok()
        }
        val a = async { client.post<Req, Payload>("/foo", Req(1)) }
        val b = async { client.post<Req, Payload>("/bar", Req(2)) }
        assertEquals(listOf(Payload(1), Payload(1)), awaitAll(a, b))
        assertEquals(1, refreshes.get())
        assertEquals(SecureRead.Value("newRefresh"), store.refreshTokenRead)
    }

    @Test fun testInvalidRefreshTokenFiresSessionDeathHandler() = runBlocking {
        backend { if (it.url.encodedPath == ApiEndpoint.refresh) rejected("INVALID_REFRESH_TOKEN") else rejected() }
        assertEquals(ApiError.InvalidRefreshToken, assertFailsWith<ApiError> { call() })
        assertEquals(1, deaths.get())
        assertEquals(SecureRead.Missing, store.accessTokenRead)
        assertEquals(SecureRead.Value("valid"), store.refreshTokenRead)
        assertEquals(accountId, store.state.value.accountId)
        assertTrue(store.state.value.needsReauth)
    }

    @Test fun testRejectedRetryWithLiveRefreshTokenKeepsSession() = runBlocking {
        backend { if (it.url.encodedPath == ApiEndpoint.refresh) { refreshes.incrementAndGet(); refreshed() } else rejected() }
        assertEquals(ApiError.UserNotAuth, assertFailsWith<ApiError> { call() })
        assertEquals(0, deaths.get()); assertEquals(2, refreshes.get())
        assertEquals(SecureRead.Value("newRefresh"), store.refreshTokenRead)
        assertTrue(store.state.value.isSignedIn)
        assertEquals(4, server.requestCount)
    }

    @Test fun testRejectedRetryWithDeadRefreshTokenExpiresSessionOnce() = runBlocking {
        backend { if (it.url.encodedPath == ApiEndpoint.refresh) {
            if (refreshes.incrementAndGet() == 1) refreshed() else rejected("INVALID_REFRESH_TOKEN")
        } else rejected() }
        assertEquals(ApiError.UserNotAuth, assertFailsWith<ApiError> { call() })
        assertEquals(1, deaths.get()); assertEquals(2, refreshes.get())
        assertEquals(SecureRead.Value("newRefresh"), store.refreshTokenRead)
    }

    @Test fun testReviveSoftExpiredSessionRefreshesAndFiresRecovery() = runBlocking {
        store.sessionExpired(); store.hydrate()
        backend { assertEquals(ApiEndpoint.refresh, it.url.encodedPath); refreshed("back", "backRefresh") }
        client.reviveSoftExpiredSession()
        assertEquals(1, recoveries.get()); assertTrue(store.state.value.isSignedIn)
        assertFalse(store.state.value.needsReauth)
        assertEquals(SecureRead.Value("back"), store.accessTokenRead)
    }

    @Test fun testReviveWithoutRefreshTokenSendsNothing() = runBlocking {
        store.clear(HardSignOutReason.userSignOut)
        client.reviveSoftExpiredSession()
        assertEquals(0, server.requestCount)
    }

    @Test fun testTransientRefreshFailureKeepsSession() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor {
            if (it.request().url.encodedPath == ApiEndpoint.refresh) { refreshes.incrementAndGet(); throw SocketTimeoutException() }
            it.proceed(it.request())
        }.build()
        client.close(); client = makeClient(http)
        backend { rejected() }
        assertEquals(NetworkFailure.timedOut, assertFailsWith<ApiError.Network> { call() }.failure)
        assertEquals(1, refreshes.get()); assertEquals(0, deaths.get())
        assertEquals(SecureRead.Value("valid"), store.refreshTokenRead)
        assertTrue(store.state.value.isSignedIn)
    }

    @Test fun lostRotationResponseReplaysTheSameOldTokenOnce() = runBlocking {
        val attempts = AtomicInteger()
        val bodies = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor {
            if (it.request().url.encodedPath == ApiEndpoint.refresh) {
                val body = okio.Buffer(); it.request().body!!.writeTo(body); bodies.add(body.readUtf8())
                if (attempts.incrementAndGet() == 1) throw EOFException()
            }
            it.proceed(it.request())
        }.build()
        client.close(); client = makeClient(http)
        backend { if (it.url.encodedPath == ApiEndpoint.refresh) refreshed() else if (it.headers["x-access-token"] == "expired") rejected() else ok() }
        assertEquals(Payload(1), call())
        assertEquals(2, attempts.get()); assertEquals(2, bodies.size)
        assertEquals("{\"refreshToken\":\"valid\"}", bodies[0]); assertEquals(bodies[0], bodies[1])
        assertEquals(0, deaths.get())
    }

    @Test fun refreshErrorsKeepOrExpireExactlyAsIos() = runBlocking {
        for ((response, transient) in listOf(
            rejected("UNKNOWN_SERVER_ERROR") to true, rejected("FUTURE_ERROR") to true,
            rejected("TOO_MANY_REQUESTS") to true, jsonResponse("broken") to true,
            jsonResponse("down", 503) to true, jsonResponse("denied", 401) to false,
        )) {
            client.close(); store = signedInStore(); client = makeClient()
            server.enqueue(response)
            assertFailsWith<ApiError> { client.refreshIfNeeded() }
            assertEquals(transient, store.state.value.isSignedIn)
            assertEquals(!transient, store.state.value.needsReauth)
            assertEquals(SecureRead.Value("valid"), store.refreshTokenRead)
        }
    }

    @Test fun testUnavailableKeychainDuringRefreshNeverKillsTheSession() = runBlocking {
        backend { rejected() }; vault.unavailable = true
        assertFailsWith<ApiError.Transport> { call() }
        assertEquals(0, deaths.get()); assertEquals(1, server.requestCount)
        vault.unavailable = false
        assertEquals(SecureRead.Value("valid"), store.refreshTokenRead)
    }

    @Test fun failedExpiryWriteKeepsPreviousCredentialsAndReturnsTransportError() = runBlocking {
        server.enqueue(rejected("INVALID_REFRESH_TOKEN"))
        vault.failWrites = true
        assertFailsWith<ApiError.Transport> { client.refreshIfNeeded() }
        assertEquals(0, deaths.get())
        assertTrue(store.state.value.isSignedIn)
        assertEquals(SecureRead.Value("expired"), store.accessTokenRead)
        assertEquals(SecureRead.Value("valid"), store.refreshTokenRead)
    }

    @Test fun testRecoveryLoopWaitsWhileKeychainIsLockedAndRecoversAfterUnlock() = runBlocking {
        val firstTick = CompletableDeferred<Unit>()
        val unlock = CompletableDeferred<Unit>()
        client.close(); client = makeClient(pause = {
            firstTick.complete(Unit); unlock.await()
        }).apply { refreshRecoveryDelays = listOf(80, 80, 80) }
        backend { if (it.url.encodedPath == ApiEndpoint.refresh) { refreshes.incrementAndGet(); refreshed("recovered", "recoveredRefresh") } else rejected() }
        vault.unavailable = true
        assertFailsWith<ApiError.Transport> { call() }
        withTimeout(2_000) { firstTick.await() }
        assertEquals(0, refreshes.get()); assertEquals(0, deaths.get())
        vault.unavailable = false; unlock.complete(Unit)
        withTimeout(2_000) { while (recoveries.get() == 0) delay(5) }
        assertEquals(1, recoveries.get()); assertEquals(SecureRead.Value("recovered"), store.accessTokenRead)
    }

    @Test fun testTransientRefreshFailureArmsBackgroundRecovery() = runBlocking {
        val recoveryReached = CompletableDeferred<Unit>()
        val allowRecovery = CompletableDeferred<Unit>()
        val http = OkHttpClient.Builder().addInterceptor {
            if (it.request().url.encodedPath == ApiEndpoint.refresh && refreshes.incrementAndGet() == 1) throw SocketTimeoutException()
            it.proceed(it.request())
        }.build()
        client.close(); client = makeClient(http, pause = { recoveryReached.complete(Unit); allowRecovery.await() })
            .apply { refreshRecoveryDelays = listOf(50, 50) }
        backend { if (it.url.encodedPath == ApiEndpoint.refresh) refreshed("recovered", "recoveredRefresh") else rejected() }
        assertFailsWith<ApiError.Network> { call() }
        withTimeout(2_000) { recoveryReached.await() }; allowRecovery.complete(Unit)
        withTimeout(2_000) { while (recoveries.get() == 0) delay(5) }
        assertEquals(2, refreshes.get()); assertEquals(0, deaths.get())
    }

    @Test fun testSessionBoundaryCancelsArmedRecovery() = runBlocking {
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        client.close(); client = makeClient(pause = { reached.complete(Unit); release.await() })
            .apply { refreshRecoveryDelays = listOf(200) }
        server.enqueue(jsonResponse("offline", 503))
        assertFailsWith<ApiError> { client.refreshIfNeeded() }
        withTimeout(2_000) { reached.await() }
        client.sessionBoundaryCrossed(); release.complete(Unit); delay(50)
        assertEquals(1, server.requestCount)
    }

    @Test fun lateRefreshCannotOverwriteNewLogin() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        backend {
            if (it.url.encodedPath == ApiEndpoint.refresh) {
                started.countDown(); release.await(3, TimeUnit.SECONDS); refreshed("stale", "staleRefresh")
            } else ok("{\"accessToken\":\"google\",\"refreshToken\":\"googleRefresh\",\"account\":{\"id\":\"$accountId\",\"avatarEmoji\":\"🚗\"},\"isNewAccount\":false}")
        }
        val refresh = async { runCatching { client.refreshIfNeeded() } }
        withContext(Dispatchers.IO) { assertTrue(started.await(2, TimeUnit.SECONDS)) }
        client.loginGoogle(GoogleLoginRequest("test-proof", "local", "raw", appVersion = "1"))
        release.countDown(); refresh.await()
        assertEquals(SecureRead.Value("google"), store.accessTokenRead)
        assertEquals(SecureRead.Value("googleRefresh"), store.refreshTokenRead)
        assertEquals(0, deaths.get())
    }
}
