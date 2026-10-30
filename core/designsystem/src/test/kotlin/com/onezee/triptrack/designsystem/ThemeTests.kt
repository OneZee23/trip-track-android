package com.onezee.triptrack.designsystem

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.onezee.triptrack.designsystem.components.HoldableCard
import com.onezee.triptrack.designsystem.components.PlusBadgeVisibility
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.InterFamily
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.ThemeManager
import com.onezee.triptrack.designsystem.theme.ThemeMode
import com.onezee.triptrack.designsystem.theme.continuousRoundedRect
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.interScaled
import com.onezee.triptrack.designsystem.theme.skiaShadowRadius
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// iOS: ThemeManager.swift, AppFont.swift (OneTypefaceTests.swift), AppTheme.swift, ProBadge.swift
// (AvatarFrameTests.swift), LanguageManager.swift.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterComponentActivity()).around(compose)

    private fun preferences() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("theme-tests", Context.MODE_PRIVATE).also { it.edit().clear().commit() }

    @Test fun themeRawValuesAreTheStoredValues() {
        assertEquals(listOf("dark", "light", "system"), ThemeMode.entries.map { it.rawValue })
        assertEquals("appThemeMode", ThemeManager.STORAGE_KEY)
    }

    @Test fun systemHandsTheChoiceToThePhone() {
        assertEquals(true, ThemeMode.dark.isDark(systemIsDark = false))
        assertEquals(false, ThemeMode.light.isDark(systemIsDark = true))
        assertEquals(true, ThemeMode.system.isDark(systemIsDark = true))
        assertEquals(false, ThemeMode.system.isDark(systemIsDark = false))
    }

    @Test fun theChoiceIsSavedAndReadBack() {
        val preferences = preferences()
        assertEquals(ThemeMode.system, ThemeManager(preferences).mode)
        ThemeManager(preferences).select(ThemeMode.dark)
        assertEquals("dark", preferences.getString("appThemeMode", null))
        assertEquals(ThemeMode.dark, ThemeManager(preferences).mode)
        preferences.edit().putString("appThemeMode", "sepia").commit()
        assertEquals(ThemeMode.system, ThemeManager(preferences).mode)
    }

    /**
     * Hierarchy by size and colour; ExtraBold belongs to exactly two roles, the screen title and
     * the tile number, and every role is set in Inter.
     */
    @Test fun rolesFollowTheReferenceScreen() {
        lateinit var roles: Map<String, TextStyle>
        compose.setContent {
            Themed(dark = false) {
                roles = mapOf(
                    "display" to AppType.display(), "title" to AppType.title, "sheetTitle" to AppType.sheetTitle,
                    "headerTitle" to AppType.headerTitle, "statValue" to AppType.statValue, "statUnit" to AppType.statUnit,
                    "statCaption" to AppType.statCaption, "section" to AppType.section, "itemTitle" to AppType.itemTitle,
                    "itemValue" to AppType.itemValue, "unit" to AppType.unit, "body" to AppType.body, "meta" to AppType.meta,
                    "caption" to AppType.caption, "button" to AppType.button, "navTitle" to AppType.navTitle,
                    "action" to AppType.action, "chip" to AppType.chip, "chipActive" to AppType.chipActive,
                )
            }
        }
        compose.waitForIdle()
        val expected = mapOf(
            "display" to (34f to 600), "title" to (28f to 800), "sheetTitle" to (22f to 700), "headerTitle" to (20f to 600),
            "statValue" to (19f to 800), "statUnit" to (11f to 600), "statCaption" to (11f to 700), "section" to (16f to 700),
            "itemTitle" to (16f to 500), "itemValue" to (16f to 600), "unit" to (17f to 400), "body" to (15f to 400),
            "meta" to (13f to 400), "caption" to (12f to 400), "button" to (16f to 600), "navTitle" to (17f to 600),
            "action" to (15f to 600), "chip" to (13f to 500), "chipActive" to (13f to 600),
        )
        assertEquals(expected.keys, roles.keys)
        for ((role, style) in roles) {
            val (size, weight) = expected.getValue(role)
            assertEquals(InterFamily, style.fontFamily, role)
            assertEquals(FontWeight(weight), style.fontWeight, role)
            // Robolectric's font scale is 1, so the fixed size in sp equals the iOS points.
            assertEquals(size, style.fontSize.value, 0.01f, role)
        }
        assertEquals(setOf("title", "statValue"), roles.filterValues { it.fontWeight == FontWeight.W800 }.keys)
    }

    /**
     * `inter` is iOS `fixedSize`: the system font scale does not move it. `interScaled` follows the
     * system's own curve, which from Android 14 is non-linear: 15 sp at 2× is 27 dp, not 30.
     */
    @Test fun fixedRolesIgnoreTheFontScaleAndScaledOnesFollowIt() {
        lateinit var fixed: TextStyle
        lateinit var scaled: TextStyle
        val density = Density(density = 2f, fontScale = 2f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                fixed = inter(15f)
                scaled = interScaled(15f)
            }
        }
        compose.waitForIdle()
        with(density) {
            assertEquals(30f, fixed.fontSize.toPx(), 0.01f)
            assertEquals(15.sp.toPx(), scaled.fontSize.toPx(), 0.01f)
            assertTrue(scaled.fontSize.toPx() > 1.5f * fixed.fontSize.toPx())
        }
    }

    @Test fun everyInterWeightHasItsBundledFace() {
        val resources = RuntimeEnvironment.getApplication().resources
        val faces = listOf(R.font.inter_regular, R.font.inter_medium, R.font.inter_semibold, R.font.inter_bold, R.font.inter_extrabold, R.font.press_start_2p_regular)
        faces.forEach { assertNotNull(resources.getFont(it)) }
        assertEquals(listOf(400, 500, 600, 700, 800), InterWeight.entries.map { it.fontWeight.weight })
    }

    @Test fun continuousCornersStayInsideTheirRectangle() {
        val size = Size(320f, 200f)
        val bounds = continuousRoundedRect(size, 22f).getBounds()
        assertEquals(0f, bounds.left, 0.01f)
        assertEquals(0f, bounds.top, 0.01f)
        assertEquals(320f, bounds.right, 0.01f)
        assertEquals(200f, bounds.bottom, 0.01f)
        // A radius too big for the side shrinks until the two corners of the side meet.
        val pill = continuousRoundedRect(Size(100f, 40f), 50f).getBounds()
        assertEquals(40f, pill.height, 0.01f)
        assertEquals(100f, pill.width, 0.01f)
    }

    /** Core Animation's shadow radius is sigma; Skia turns its radius into sigma as 0.57735 r + 0.5. */
    @Test fun shadowRadiusKeepsTheIosSoftness() {
        for (sigma in listOf(2f, 8f, 14f, 24f)) {
            assertEquals(sigma, 0.57735f * skiaShadowRadius(sigma) + 0.5f, 0.001f)
        }
    }

    @Test fun holdPressLastsAsLongAsTheHoldGesture() {
        assertEquals(500L, HoldableCard.holdMillis(systemLongPressMillis = 400))
        assertEquals(1_000L, HoldableCard.holdMillis(systemLongPressMillis = 1_000))
    }

    /** iOS AvatarFrameTests.testBadgeVisibilityTable, case for case. */
    @Test fun badgeVisibilityTable() {
        val table = listOf(
            Triple(true, false, true) to true,
            Triple(true, false, false) to true,
            Triple(true, true, true) to true,
            Triple(true, true, false) to false,
            Triple(false, false, true) to false,
            Triple(false, true, true) to false,
            Triple<Boolean?, Boolean, Boolean>(null, false, true) to false,
            Triple<Boolean?, Boolean, Boolean>(null, true, true) to false,
        )
        for ((input, expected) in table) {
            val (isPlus, isOwn, showsOwn) = input
            assertEquals(expected, PlusBadgeVisibility.shows(isPlus, isOwn, showsOwn), "isPlus=$isPlus own=$isOwn toggle=$showsOwn")
        }
        assertEquals(false, PlusBadgeVisibility.saysPlus(null))
        assertEquals(false, PlusBadgeVisibility.saysPlus(false))
        assertEquals(true, PlusBadgeVisibility.saysPlus(true))
    }

    @Test fun languagesNameThemselves() {
        val expected = mapOf(
            AppLanguage.en to ("English" to "En"), AppLanguage.ru to ("Русский" to "Ру"), AppLanguage.de to ("Deutsch" to "De"),
            AppLanguage.es to ("Español" to "Es"), AppLanguage.fr to ("Français" to "Fr"), AppLanguage.it to ("Italiano" to "It"),
            AppLanguage.pl to ("Polski" to "Pl"), AppLanguage.id to ("Bahasa Indonesia" to "Id"), AppLanguage.tr to ("Türkçe" to "Tr"),
            AppLanguage.fil to ("Filipino" to "Fi"), AppLanguage.uk to ("Українська" to "Ук"), AppLanguage.kk to ("Қазақша" to "Қа"),
            AppLanguage.pt to ("Português (Brasil)" to "Pt"),
        )
        assertEquals(AppLanguage.entries.toSet(), expected.keys)
        for ((language, names) in expected) {
            assertEquals(names.first, language.endonym)
            assertEquals(names.second, language.badge)
        }
    }
}
