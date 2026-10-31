// iOS: DebugLogExporter.recentEntries(hoursBack:48, limit:800).
package com.onezee.triptrack.feature

import com.onezee.triptrack.feature.settings.MeDiagnostics
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MeDiagnosticsTests {
    @Test fun logKeepsEightHundredLinesAndOnlyTheLastFortyEightHours() = runTest {
        val directory = Files.createTempDirectory("me-logs").toFile()
        try {
            var now = Instant.parse("2026-10-22T09:00:00Z")
            val log = MeDiagnostics(directory.resolve("network.log"), { now }, backgroundScope)
            repeat(805) { log.append("HTTP $it"); runCurrent() }
            assertEquals(800, log.read().size); assertTrue(log.read().first().endsWith("HTTP 5"))
            now = now.plusSeconds(48 * 3600); assertEquals(800, log.read().size)
            now = now.plusSeconds(1); assertTrue(log.read().isEmpty())
        } finally { directory.deleteRecursively() }
    }
    @Test fun accountWipeRejectsAlreadyQueuedLines() = runTest {
        val directory = Files.createTempDirectory("me-logs").toFile()
        try {
            val log = MeDiagnostics(directory.resolve("network.log"), scope = backgroundScope)
            log.append("old"); log.clear(); runCurrent(); assertTrue(log.read().isEmpty())
            log.append("new"); runCurrent(); assertTrue(log.read().single().endsWith("new"))
        } finally { directory.deleteRecursively() }
    }
}
