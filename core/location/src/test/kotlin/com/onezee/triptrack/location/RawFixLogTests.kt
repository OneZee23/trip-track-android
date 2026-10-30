package com.onezee.triptrack.location

import com.onezee.triptrack.location.recording.RawFixLog
import com.onezee.triptrack.logic.TrackTestKit
import com.onezee.triptrack.model.FixGate
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

// iOS: RawFixLogTests.swift
class RawFixLogTests {
    private lateinit var dir: File
    private var now: Instant = Instant.ofEpochSecond(1_780_000_000)

    @Before
    fun makeDirectory() {
        dir = File(Files.createTempDirectory("rawfix").toFile(), RawFixLog.DIRECTORY)
    }

    @After
    fun removeDirectory() {
        dir.parentFile.deleteRecursively()
    }

    private fun file(id: UUID) = File(dir, "${id.toString().uppercase()}.csv")

    @Test
    fun lineIsLocaleProofAndCarriesTheDecision() {
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            val fix = TrackTestKit.fix(speed = 12.5, after = 0.0, accuracy = 72.25)
            val line = RawFixLog.line(fix, FixGate.Decision.Reject(FixGate.Reason.accuracy))
            assertTrue(line.contains("72.25"))
            assertTrue(line.endsWith("reject:accuracy"))
            assertFalse(line.contains(";"))
            assertEquals("1780000000.000", line.substringBefore(','))
            assertTrue(RawFixLog.line(fix, FixGate.Decision.Accept).endsWith(",accept"))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun recordsOnlyWhileATripIsOpenAndMarksLifecycle() {
        val log = RawFixLog(dir) { now }
        log.record("before")
        val id = UUID.randomUUID()
        log.begin(id)
        log.record("during")
        log.mark("background")
        log.end()
        log.record("after")
        val text = file(id).readText()
        assertTrue(text.startsWith(RawFixLog.HEADER))
        assertTrue(text.contains("during"))
        assertTrue(text.contains("background"))
        assertFalse(text.contains("before"))
        assertFalse(text.contains("after"))
    }

    @Test
    fun purgeDropsFilesOlderThanRetention() {
        val log = RawFixLog(dir) { now }
        log.begin(UUID.randomUUID())
        log.end()
        now = Instant.now().plusSeconds((RawFixLog.RETENTION_DAYS + 1) * 86_400L)
        log.purgeOld()
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun resumeAppendsAfterARecovery() {
        val id = UUID.randomUUID()
        val beforeKill = RawFixLog(dir) { now }
        beforeKill.begin(id)
        beforeKill.record("before-kill")
        val afterRecovery = RawFixLog(dir) { now }
        afterRecovery.resume(id)
        afterRecovery.record("after-recovery")
        val text = file(id).readText()
        assertTrue(text.startsWith(RawFixLog.HEADER))
        assertTrue(text.contains("before-kill"))
        assertTrue(text.contains("after-recovery"))
        assertEquals(2, text.split(RawFixLog.HEADER).size, "one header: recovery appends")
    }

    @Test
    fun removeDeletesTheFileAndClosesIfItWasOpen() {
        val log = RawFixLog(dir) { now }
        val id = UUID.randomUUID()
        log.begin(id)
        log.record("before-delete")
        assertTrue(file(id).exists())
        log.remove(id)
        assertFalse(file(id).exists())
        log.record("after-delete")
        assertFalse(file(id).exists(), "a write after the delete must not bring the file back")
    }

    @Test
    fun removeOfAnotherTripLeavesTheOpenLogRecording() {
        val log = RawFixLog(dir) { now }
        val open = UUID.randomUUID()
        val other = UUID.randomUUID()
        log.begin(other)
        log.end()
        log.begin(open)
        log.remove(other)
        log.record("still-open")
        assertTrue(file(open).readText().contains("still-open"))
        assertFalse(file(other).exists())
    }
}
