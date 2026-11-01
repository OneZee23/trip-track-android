package com.onezee.triptrack.logic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM tests run java.util.regex; phones run ICU. Flags the JVM accepts and ICU rejects compile
 * here and throw on the device, so the sources are read for them, like UnitsDisciplineTests on iOS.
 */
class RegexPortabilityTests {
    private val unsupported = listOf("(?U)", "UNICODE_CHARACTER_CLASS")

    @Test fun noSourceUsesAJvmOnlyRegexFlag() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val offenders = root.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", "tools") }
            .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.path }
            .flatMap { file -> file.readLines().mapIndexedNotNull { i, line -> if (unsupported.any(line::contains)) "${file.relativeTo(root)}:${i + 1}" else null } }
            .toList()
        assertEquals(emptyList(), offenders)
    }

    @Test fun whitespaceRunsMatchSwiftWhitespacesAndNewlines() {
        val all = "\t\n\u000B\u000C\r \u0085         　"
        assertTrue(UnicodeWhitespace.runs.matches(all))
        assertEquals(listOf("Краснодар", "Джубга"), "Краснодар 　Джубга".split(UnicodeWhitespace.runs))
        assertTrue(!UnicodeWhitespace.runs.containsMatchIn("a​b"))
    }
}
