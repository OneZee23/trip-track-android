package com.onezee.triptrack.designsystem.strings

import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.*

// iOS: LocalizationTests.swift. The generated handoff remains the port's coverage inventory.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PortingCoverageTests {
    @Test fun everyManualFunctionExistsOrHasAnExplicitSkipReason() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "tools/strings/manual-porting.md").isFile }
        val rows = File(root, "tools/strings/manual-porting.md").readLines()
            .filter { it.startsWith("| `") }.map { row -> row.split('|').map { it.trim().trim('`') } }
        val methods = AppStrings::class.java.methods.map { it.name }.toSet() +
            LocalizedDateFormatter::class.java.methods.map { it.name } +
            Class.forName("com.onezee.triptrack.designsystem.strings.AppLanguageKt").methods.map { it.name }
        val missing = rows.filter { row -> row[2] !in methods && !row[3].startsWith("Intentionally skipped:") }
        assertTrue(missing.isEmpty(), missing.joinToString("\n"))
        val manifest = JSONObject(File(root, "tools/strings/manifest.json").readText())
        val functions = manifest.getJSONArray("functions")
        val incomplete = (0 until functions.length()).map { functions.getJSONObject(it) }.filter { !it.getBoolean("complete") }
        assertEquals(186, incomplete.size)
        for (function in incomplete) {
            val location = "${function.getString("file")}:${function.getInt("line")}"
            val name = function.getString("function")
            assertTrue(rows.any { it[1] == location && it[2] == name }, "Missing handoff for $location $name")
        }
        val skipped = JSONObject(File(root, "tools/strings/porting-status.json").readText())
        for (key in skipped.keys()) {
            assertTrue(incomplete.any { it.getString("function") == key }, "Stale skip $key")
            assertTrue(skipped.getString(key).length > 40, "Skip needs a reason: $key")
        }
    }

    @Test fun iosLanguageCodesAndDetectionStayStable() {
        assertEquals(listOf("en", "ru", "de", "es", "fr", "it", "pl", "id", "tr", "fil", "uk", "kk", "pt"), AppLanguage.entries.map { it.code })
        assertEquals(AppLanguage.entries.toSet(), AppLanguage.displayOrder.toSet())
        assertEquals(AppLanguage.pt, AppLanguage.detect(listOf("pt-BR")))
        assertEquals("BR", AppLanguage.pt.locale.country)
        assertEquals(AppLanguage.es, AppLanguage.detect(listOf("zh-Hans-CN", "es-MX")))
        assertEquals(AppLanguage.en, AppLanguage.detect(listOf("zh")))
        assertEquals(AppLanguage.en, AppLanguage.fromCode("unknown"))
    }
}
