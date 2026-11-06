// iOS: PlusStoreTests.swift, PlusGateTests.swift
package com.onezee.triptrack.feature.me

import android.content.Context
import com.onezee.triptrack.model.MeResponse
import com.onezee.triptrack.model.PlusStatusResponse
import com.onezee.triptrack.network.ApiError
import java.net.UnknownHostException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlusEntitlementStoreTests {
    private val accountA = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val accountB = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val now = Instant.parse("2026-11-06T10:00:00Z")

    private fun store(test: TestScope, api: FakeApi, clock: () -> Instant = { now }): PlusEntitlementStore {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("plus-entitlement-tests", Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }
        return PlusEntitlementStore(prefs, api, test.backgroundScope, clock)
    }

    @Test fun accountSwitchClearsCachedProBeforeTheNextRefresh() = runTest {
        val api = FakeApi().apply { next = PlusStatusResponse(active = true, until = now.plusSeconds(3_600)) }
        val store = store(this, api)
        store.setAccount(accountA)
        runCurrent()
        assertTrue(store.isPlus.value)
        store.setAccount(accountB)
        assertFalse(store.isPlus.value)
        assertNull(store.entitlement.value)
        runCurrent()
        assertEquals(accountB, store.entitlement.value?.accountId)
    }

    @Test fun cachedUntilClosesAccessWithoutAnotherRequest() = runTest {
        var clock = now
        val api = FakeApi().apply { next = PlusStatusResponse(active = true, until = now.plusSeconds(5)) }
        val store = store(this, api) { clock }
        store.setAccount(accountA)
        runCurrent()
        assertTrue(store.isPlus.value)
        clock = now.plusSeconds(5)
        advanceTimeBy(5_000)
        runCurrent()
        assertFalse(store.isPlus.value)
        assertEquals(1, api.statusCalls)
    }

    @Test fun offlineRefreshRetainsLastKnownState() = runTest {
        val api = FakeApi().apply { next = PlusStatusResponse(active = true, until = now.plusSeconds(3_600)) }
        val store = store(this, api)
        store.setAccount(accountA)
        runCurrent()
        api.failure = offline()
        store.refresh()
        assertTrue(store.isPlus.value)
        assertEquals(accountA, store.entitlement.value?.accountId)
    }

    @Test fun refreshAfterCachedUntilExpiresDropsProBeforeOfflineRequest() = runTest {
        var clock = now
        val api = FakeApi().apply { next = PlusStatusResponse(active = true, until = now.plusSeconds(5)) }
        val store = store(this, api) { clock }
        store.setAccount(accountA)
        runCurrent()
        assertTrue(store.isPlus.value)

        clock = now.plusSeconds(6)
        api.failure = offline()
        store.refresh()

        assertFalse(store.isPlus.value)
        assertEquals(2, api.statusCalls)
    }

    @Test fun signInAndResumeAccountRefreshesHitTheStatusEndpoint() = runTest {
        val api = FakeApi()
        val store = store(this, api)
        store.setAccount(accountA)
        runCurrent()
        store.refresh()
        assertEquals(2, api.statusCalls)
    }

    @Test fun unavailableStatusFallsBackToAuthMePlusUntil() = runTest {
        val api = FakeApi().apply {
            failure = ApiError.UnknownServer("PLUS_UNAVAILABLE", "")
            meResponse = MeResponse(isPublic = true, plusUntil = now.plusSeconds(3_600).toString())
        }
        val store = store(this, api)
        store.setAccount(accountA)
        runCurrent()
        assertTrue(store.isPlus.value)
        assertEquals(1, api.meCalls)
    }

    private fun offline() = ApiError.Network(com.onezee.triptrack.network.NetworkFailure.offline, UnknownHostException())

    private class FakeApi : PlusEntitlementApi {
        var next = PlusStatusResponse()
        var failure: Exception? = null
        var meResponse = MeResponse(isPublic = true)
        var statusCalls = 0
        var meCalls = 0
        override suspend fun status(): PlusStatusResponse { statusCalls++; failure?.let { throw it }; return next }
        override suspend fun me(): MeResponse { meCalls++; return meResponse }
    }
}
