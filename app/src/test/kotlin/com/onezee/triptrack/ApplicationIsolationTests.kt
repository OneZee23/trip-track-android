package com.onezee.triptrack

import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// iOS: StoreHealthTests.swift (services are started explicitly, independently of screen fixtures).
@RunWith(RobolectricTestRunner::class)
class ApplicationIsolationTests {
    @Test fun screenDependenciesDoNotStartRecordingRecovery() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as TestTripTrackApplication
        assertNull(withTimeoutOrNull(100) { app.appGraph.location.awaitStarted() })
    }

    @Test fun terminatingTheApplicationCancelsItsAuthWork() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as TestTripTrackApplication
        val started = CompletableDeferred<Unit>()
        val authWork = app.appGraph.auth.serviceScope.launch(Dispatchers.Default) {
            started.complete(Unit)
            awaitCancellation()
        }
        started.await()
        app.onTerminate()
        withTimeout(2_000) { authWork.join() }
        assertFalse(authWork.isActive)
    }
}
