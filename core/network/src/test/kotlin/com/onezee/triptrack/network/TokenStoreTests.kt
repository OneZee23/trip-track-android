// iOS: KeychainHelperTests.swift, KeychainWriteTests.swift, AuthSessionExpiryTests.swift
package com.onezee.triptrack.network

import kotlin.test.*
import org.junit.Test

class TokenStoreTests {
    @Test fun testReadDistinguishesMissingFromUnavailable() {
        val vault = FakeVault(); val store = TokenStore(vault)
        assertEquals(SecureRead.Missing, store.refreshTokenRead)
        store.set("access", "refresh")
        assertEquals(SecureRead.Value("refresh"), store.refreshTokenRead)
        vault.unavailable = true
        assertIs<SecureRead.Unavailable>(store.refreshTokenRead)
    }

    @Test fun testFailedUpdateThrowsAndKeepsPreviousValue() {
        val vault = FakeVault(); val store = signedInStore(vault)
        val before = vault.values
        vault.failWrites = true
        assertFailsWith<SecureStorageException> { store.set("new", "newRefresh") }
        assertEquals(before, vault.values)
        assertEquals(SecureRead.Value("expired"), store.accessTokenRead)
        assertEquals(SecureRead.Value("valid"), store.refreshTokenRead)
        assertTrue(store.state.value.isSignedIn)
    }

    @Test fun testUnavailableKeychainNeitherUpdatesNorAdds() {
        val vault = FakeVault(); val store = signedInStore(vault)
        val before = vault.values
        vault.unavailable = true
        assertFailsWith<SecureStorageException> { store.set("new", "newRefresh") }
        assertEquals(before, vault.values)
    }

    @Test fun testSessionExpiredDropsTokensButKeepsEverythingElse() {
        val vault = FakeVault(); val store = signedInStore(vault)
        store.sessionExpired(); store.hydrate()
        assertFalse(store.state.value.isSignedIn); assertTrue(store.state.value.needsReauth)
        assertEquals(SecureRead.Missing, store.accessTokenRead)
        assertEquals(SecureRead.Value("valid"), store.refreshTokenRead)
        assertEquals(accountId, store.state.value.accountId)
        assertEquals("Test driver", store.state.value.displayName)
        assertEquals("true", vault.values?.get(TokenStore.expiredKey))
    }

    @Test fun testUserNameSurvivesSoftExpiryAndLeavesWithTheSession() {
        val vault = FakeVault(); val store = signedInStore(vault)
        store.setUserName("  Road Runner ")
        assertEquals("Road Runner", store.state.value.displayName)
        store.sessionExpired()
        assertEquals("Road Runner", TokenStore(vault).apply { hydrate() }.state.value.displayName)
        store.setUserName(" ")
        assertNull(store.state.value.displayName)
        store.setUserName("Again"); store.clear(HardSignOutReason.userSignOut)
        assertNull(TokenStore(vault).apply { hydrate() }.state.value.displayName)
    }

    @Test fun testSessionExpiredIsIdempotent() {
        val store = signedInStore()
        store.sessionExpired(); store.sessionExpired()
        assertTrue(store.state.value.needsReauth)
        assertEquals(SecureRead.Value("valid"), store.refreshTokenRead)
    }

    @Test fun testRefreshAfterRelaunchRevivesSoftExpiredSession() {
        val vault = FakeVault(); val first = signedInStore(vault)
        first.sessionExpired()
        val store = TokenStore(vault).apply { hydrate() }
        assertTrue(store.state.value.needsReauth)
        store.set("back", "back-refresh"); store.sessionRecovered()
        assertTrue(store.state.value.isSignedIn); assertFalse(store.state.value.needsReauth)
        assertFalse(vault.values!!.containsKey(TokenStore.expiredKey))
    }

    @Test fun testKeychainUnavailableAtLaunchKeepsSessionAndReReadsLater() {
        val vault = FakeVault(); val store = signedInStore(vault)
        vault.unavailable = true; store.hydrate()
        assertTrue(store.state.value.isSignedIn); assertTrue(store.state.value.hydrationDeferred)
        assertFalse(store.state.value.needsReauth)
        vault.unavailable = false; store.hydrate()
        assertFalse(store.state.value.hydrationDeferred); assertTrue(store.state.value.isSignedIn)
    }

    @Test fun markerWithMissingTokensBecomesSoftExpiry() {
        val vault = FakeVault().apply { values = mapOf(TokenStore.signedInKey to "true", TokenStore.accountIdKey to accountId.toString()) }
        val store = TokenStore(vault).apply { hydrate() }
        assertFalse(store.state.value.isSignedIn); assertTrue(store.state.value.needsReauth)
        assertEquals(accountId, store.state.value.accountId)
    }

    @Test fun hardSignOutHasExactlyThreeReasonsAndClearsCredentials() {
        assertEquals(3, HardSignOutReason.entries.size)
        for (reason in HardSignOutReason.entries) {
            val store = signedInStore()
            store.clear(reason)
            assertEquals(SecureRead.Missing, store.refreshTokenRead)
            assertEquals(SecureRead.Missing, store.accountIdRead)
            assertEquals(SessionState(), store.state.value)
        }
    }

    @Test fun testServiceNameBindsBuildAndApiHost() {
        assertEquals("com.onezee.TripTrack.auth.api.trip-track.app", TokenStore.composeService("com.onezee.TripTrack", "API.TRIP-TRACK.APP"))
        assertNotEquals(TokenStore.composeService("com.onezee.TripTrack.dev", "api.example.invalid"),
            TokenStore.composeService("com.onezee.TripTrack", "api.trip-track.app"))
        assertEquals("com.triptrack.auth.unknown", TokenStore.composeService(null, null))
    }

    @Test fun clientCannotPresentHostScopedCredentialsToAnotherHost() {
        val vault = object : SecureVault {
            override val apiHost = "api.trip-track.app"
            override fun read(): SecureRead<Map<String, String>> = error("Must not read credentials")
            override fun write(values: Map<String, String>) = error("Must not write credentials")
        }
        assertFailsWith<IllegalArgumentException> { ApiClient(TokenStore(vault), "https://other.example.invalid") }
    }
}
