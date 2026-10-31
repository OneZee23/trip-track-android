// iOS: ProductAnalyticsTests.swift. Consent, UTC dates and the same latency boundaries.
package com.onezee.triptrack.feature

import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.network.*
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProductAnalyticsTests {
    private val preferences = MemoryPreferences()
    private val sent = mutableListOf<ProductAnalyticsSnapshot>()
    private val erased = mutableListOf<String>()
    private var fails = false
    private val service = object : UsageService {
        override suspend fun snapshot(value: ProductAnalyticsSnapshot) { sent += value; if (fails) throw IOException() }
        override suspend fun erase(token: String) { erased += token; if (fails) throw IOException() }
    }
    private fun TestScope.analytics() = ProductAnalytics(preferences, service, backgroundScope, true) { Instant.parse("2026-10-22T09:00:00Z") }
    @Test fun offByDefaultAndNoPreConsentHistory() = runTest {
        val analytics = analytics(); analytics.recordingStarted(UUID.randomUUID()); analytics.atlasOpened(); analytics.flush()
        assertFalse(analytics.enabled.value); assertTrue(sent.isEmpty())
        analytics.setEnabled(true, hadTrips = true); analytics.flush()
        assertEquals(0, sent.single().starts); assertEquals(0, sent.single().atlasOpens); assertTrue(sent.single().hadTrips)
    }
    @Test fun firstAndRepeatSavesAndAtlasWithoutAnAccount() = runTest {
        val analytics = analytics(); analytics.setEnabled(true)
        val first = UUID.randomUUID(); analytics.recordingStarted(first); analytics.recordingFinished(first, true, true, .09)
        val second = UUID.randomUUID(); analytics.recordingStarted(second); analytics.recordingFinished(second, true, true, .1)
        val third = UUID.randomUUID(); analytics.recordingStarted(third); analytics.recordingFinished(third, true, true, 1.0)
        analytics.atlasOpened(); analytics.flush()
        val value = sent.single()
        assertEquals(3, value.starts); assertEquals(3, value.saves); assertEquals(1, value.atlasOpens)
        assertEquals(1, value.fastSaves); assertEquals(1, value.mediumSaves); assertEquals(1, value.slowSaves)
        assertEquals("2026-10-22", value.firstSaveDay); assertEquals(value.firstSaveDay, value.secondSaveDay)
    }
    @Test fun interruptedRecordingSurvivesRelaunchAndDuplicateFinishDoesNotCount() = runTest {
        val analytics = analytics(); analytics.setEnabled(true)
        val id = UUID.randomUUID(); analytics.recordingStarted(id); analytics.recordingStarted(id)
        val restored = analytics(); restored.recordingFinished(id, true, true, .2); restored.recordingFinished(id, true, true, .2); restored.flush()
        assertEquals(1, sent.single().starts); assertEquals(1, sent.single().saves)
        assertFalse(NetworkJson.wire.encodeToString(sent.single()).contains(id.toString()))
    }
    @Test fun failedAndJunkSavesNeverBecomeActivation() = runTest {
        val analytics = analytics(); analytics.setEnabled(true)
        val first = UUID.randomUUID(); analytics.recordingStarted(first); analytics.recordingFinished(first, false, true, .1)
        val second = UUID.randomUUID(); analytics.recordingStarted(second); analytics.recordingFinished(second, true, false, .1)
        analytics.flush(); assertEquals(1, sent.single().saveFailures); assertEquals(0, sent.single().saves); assertNull(sent.single().firstSaveDay)
    }
    @Test fun revocationQueuesDeletionAcrossRelaunch() = runTest {
        val analytics = analytics(); analytics.setEnabled(true); analytics.flush()
        val token = sent.single().token; analytics.setEnabled(false)
        assertFalse(analytics.enabled.value); assertTrue(analytics.deletionPending.value)
        val restored = analytics(); restored.flush()
        assertEquals(listOf(token), erased); assertFalse(restored.deletionPending.value)
        restored.setEnabled(true); restored.flush(); assertNotEquals(token, sent.last().token)
    }
    @Test fun failedRequestRetriesTheSameRevisionAndRevocationDeletesInstead() = runTest {
        val analytics = analytics(); analytics.setEnabled(true); fails = true; analytics.flush()
        val first = sent.single(); fails = false; analytics.flush()
        assertEquals(first, sent.last())
        analytics.setEnabled(false); analytics.flush(); assertEquals(listOf(first.token), erased)
    }
    @Test fun debugNeverSendsUsageAndEraseQueueStaysBounded() = runTest {
        val analytics = ProductAnalytics(preferences, service, backgroundScope, false)
        repeat(17) { analytics.setEnabled(true); analytics.setEnabled(false) }
        analytics.setEnabled(true); analytics.flush()
        assertFalse(analytics.enabled.value); assertTrue(sent.isEmpty()); assertTrue(erased.isEmpty())
    }
}
