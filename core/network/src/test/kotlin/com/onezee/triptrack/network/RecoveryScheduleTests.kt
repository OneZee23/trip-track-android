// iOS: APIClientTokenRefreshTests.swift, APIClientKeychainUnavailableTests.swift
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.onezee.triptrack.network

import kotlin.test.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.Test

class RecoveryScheduleTests {
    @Test fun unavailableStorageWaitsThroughTheSequentialFiveThirty120SecondSchedule() = runTest {
        val vault = FakeVault().apply { unavailable = true }
        val waits = mutableListOf<Long>()
        val client = ApiClient(TokenStore(vault), scope = this, pause = { waits.add(it); delay(it) })
        assertFailsWith<ApiError.Transport> { client.refreshIfNeeded() }
        runCurrent()
        assertEquals(listOf(5_000L), waits)
        advanceTimeBy(5_000); runCurrent(); assertEquals(listOf(5_000L, 30_000L), waits)
        advanceTimeBy(30_000); runCurrent(); assertEquals(listOf(5_000L, 30_000L, 120_000L), waits)
        advanceTimeBy(120_000); runCurrent()
        assertIs<SecureRead.Unavailable>(TokenStore(vault).refreshTokenRead)
        client.close()
    }

    @Test fun missingRefreshTokenEndsTheRecoveryLoop() = runTest {
        val vault = FakeVault().apply { unavailable = true }
        val waits = mutableListOf<Long>()
        val client = ApiClient(TokenStore(vault), scope = this, pause = { waits.add(it); delay(it) })
        assertFailsWith<ApiError.Transport> { client.refreshIfNeeded() }
        runCurrent(); vault.unavailable = false
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf(5_000L), waits)
        client.close()
    }
}
