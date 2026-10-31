// iOS: BadgeDefinitions.swift, BadgeDetailView.swift, LocalizationTests.swift
package com.onezee.triptrack.feature.me

import com.onezee.triptrack.designsystem.strings.*
import com.onezee.triptrack.logic.BadgeDefinitions
import com.onezee.triptrack.logic.shown
import com.onezee.triptrack.model.DistanceUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BadgeCopyTests {
    @Test fun everyBadgeHasArtAndFormatsItsFiguresInAllSupportedLanguages() {
        AppLanguage.entries.forEach { language ->
            val strings = AppStrings(RuntimeEnvironment.getApplication(), language)
            BadgeDefinitions.all.forEach { badge ->
                assertNotNull(MeArtwork.drawables["badge_${badge.id}"], badge.id)
                assertTrue(strings.tr(MeArtwork.badgeTitles.getValue(badge.id)).isNotBlank())
                DistanceUnit.entries.forEach { unit ->
                    val figures = badge.figures.map { it.shown(unit, language.code, strings) }
                    val description = strings.tr(MeArtwork.badgeDescriptions.getValue(badge.id), *figures.toTypedArray())
                    assertTrue(description.isNotBlank()); assertFalse(description.contains("%@"))
                }
            }
        }
    }
}
