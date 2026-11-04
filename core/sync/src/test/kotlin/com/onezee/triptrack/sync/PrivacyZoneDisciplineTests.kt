package com.onezee.triptrack.sync

import java.io.File
import kotlin.test.*
import org.junit.Test

// iOS: PrivacyZoneDisciplineTests.swift. Coordinates are trimmed only at the send boundary.
class PrivacyZoneDisciplineTests {
    private fun root(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
    private fun code(text: String): String = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("(?m)//.*$"), "")
    @Test fun testTrimmingLivesOnlyAtTheSendBoundary() {
        val files = listOf(File(root(), "core"), File(root(), "app")).flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" && "/src/main/" in f.path }.toList() }
        assertTrue(files.isNotEmpty())
        val mentions = files.filter { code(it.readText()).contains("PrivacyZone.") }
        assertEquals(listOf("TripSyncPayloadMapper.kt"), mentions.map { it.name })
    }
    @Test fun testTheSendBoundaryStillTrims() {
        val mapper = code(File(root(), "core/sync/src/main/kotlin/com/onezee/triptrack/sync/TripSyncPayloadMapper.kt").readText())
        assertTrue(mapper.contains("TripHiddenPartsResolver.resolve("))
        assertTrue(mapper.contains("resolution.hidden[index]"))
        assertTrue(mapper.contains("TripHiddenPartsResolver.photoIsHidden("))
    }
    @Test fun testTheGuardCatchesPlantedLinesAndSparesTheListener() {
        assertTrue(code("val shown = PrivacyZone.trim(points, zone)").contains("PrivacyZone."))
        assertFalse(code("// PrivacyZone.trim(points, zone)\nlisten(homePrivacyZoneChanged)").contains("PrivacyZone."))
    }
}
