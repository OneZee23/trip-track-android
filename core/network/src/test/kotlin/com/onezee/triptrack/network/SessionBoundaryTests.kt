// iOS: AuthSessionExpiryTests.swift, APIClientTokenRefreshTests.swift
package com.onezee.triptrack.network

import com.onezee.triptrack.model.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlinx.coroutines.*
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Test

class SessionBoundaryTests {
    @Test fun userSignOutClearsRefreshEvenWhenLogoutFails() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val store = signedInStore()
            val reasons = mutableListOf<HardSignOutReason>()
            ApiClient(store, server.url("/").toString(), onHardSignOut = { reasons.add(it) }).use { client ->
                server.enqueue(jsonResponse("offline", 503))
                client.signOut()
                assertEquals(SecureRead.Missing, store.refreshTokenRead)
                assertEquals(listOf(HardSignOutReason.userSignOut), reasons)
            }
        }
    }

    @Test fun signOutAfterSoftExpiryNeedsNoNetwork() = runBlocking {
        val store = signedInStore(); store.sessionExpired()
        ApiClient(store).use { client ->
            client.signOut()
            assertEquals(SecureRead.Missing, store.refreshTokenRead)
            assertEquals(SessionState(), store.state.value)
        }
    }

    @Test fun deleteOnlyClearsAfterServerSuccessAndBanIsTheThirdHardPath() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val store = signedInStore()
            val reasons = mutableListOf<HardSignOutReason>()
            ApiClient(store, server.url("/").toString(), onHardSignOut = { reasons.add(it) }).use { client ->
                server.enqueue(rejected("VALIDATION_FAILED"))
                assertFailsWith<ApiError.ValidationFailed> { client.deleteAccount() }
                assertEquals(SecureRead.Value("valid"), store.refreshTokenRead)
                server.enqueue(jsonResponse("{\"status\":\"ok\"}"))
                client.deleteAccount()
                assertEquals(listOf(HardSignOutReason.accountDeletion), reasons)
                assertEquals(SecureRead.Missing, store.refreshTokenRead)
                store.acceptLogin(LoginResponse("access", "refresh", AccountDTO(accountId, avatarEmoji = "🚗"), false))
                server.enqueue(rejected("USER_BANNED"))
                assertEquals(ApiError.UserBanned, assertFailsWith<ApiError> { client.get<Payload>("/test") })
                assertEquals(listOf(HardSignOutReason.accountDeletion, HardSignOutReason.banned), reasons)
                assertEquals(SecureRead.Missing, store.refreshTokenRead)
            }
        }
    }

    @Test fun cancellingOneWaiterDoesNotCancelSharedRefresh() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    started.countDown(); release.await(2, TimeUnit.SECONDS)
                    return refreshed()
                }
            }
            val store = signedInStore()
            ApiClient(store, server.url("/").toString()).use { client ->
                val first = async { client.refreshIfNeeded() }
                withContext(Dispatchers.IO) { assertTrue(started.await(2, TimeUnit.SECONDS)) }
                val second = async { client.refreshIfNeeded() }
                yield(); first.cancelAndJoin(); release.countDown(); second.await()
                assertEquals(1, server.requestCount)
                assertEquals(SecureRead.Value("newRefresh"), store.refreshTokenRead)
            }
        }
    }
}
