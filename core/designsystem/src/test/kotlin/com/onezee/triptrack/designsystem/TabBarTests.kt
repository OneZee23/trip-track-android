package com.onezee.triptrack.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.navigation.AppTab
import com.onezee.triptrack.designsystem.navigation.CustomTabBar
import com.onezee.triptrack.designsystem.navigation.TabBarMetrics
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

// iOS: CustomTabBar.swift, CustomTabBarLiftTests.swift, AppTabTests.swift.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TabBarTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterComponentActivity()).around(compose)

    private val strings get() = AppStrings(RuntimeEnvironment.getApplication(), AppLanguage.ru)

    /** Gap + capsule + raised disk + 8 of air, from the physical bottom on gesture navigation. */
    @Test fun clearanceIsTheDesignGeometry() {
        assertEquals(110.dp, TabBarMetrics.clearance)
        assertEquals(TabBarMetrics.bottomGap + TabBarMetrics.pillHeight + TabBarMetrics.recordRise + 8.dp, TabBarMetrics.clearance)
    }

    @Test fun clearanceAboveTheSafeAreaSubtractsTheInset() {
        assertEquals(76.dp, TabBarMetrics.clearanceAboveSafeArea(bottomInset = 34.dp))
        assertEquals(110.dp, TabBarMetrics.clearanceAboveSafeArea(bottomInset = 0.dp))
    }

    @Test fun clearanceAboveTheSafeAreaNeverGoesNegative() {
        assertEquals(0.dp, TabBarMetrics.clearanceAboveSafeArea(bottomInset = 200.dp))
    }

    /** Three-button navigation: the capsule stands 22 above the buttons, so the room grows by their height. */
    @Test fun aTappableNavigationBarLiftsTheCapsule() {
        assertEquals(110.dp, TabBarMetrics.clearanceAboveSafeArea(bottomInset = 48.dp, tappableInset = 48.dp))
    }

    @Test fun rawValuesAreStable() {
        assertEquals(listOf("home", "maps", "record", "places", "profile"), AppTab.entries.map { it.rawValue })
        assertEquals("selectedTabV2", AppTab.storageKey)
    }

    /** 0.6.8 removed «Группы»: a stored «groups» opens Лента, and nothing else is guessed. */
    @Test fun storedGroupsFallsBackToHome() {
        assertNull(AppTab.fromRawValue("groups"))
        assertEquals(AppTab.places, AppTab.fromRawValue("places"))
        assertEquals(AppTab.home, AppTab.stored("groups"))
        assertEquals(AppTab.home, AppTab.stored(null))
        assertEquals(AppTab.profile, AppTab.stored("profile"))
    }

    @Test fun eachTabAndTheDiskSelectTheirTab() {
        val picked = mutableListOf<AppTab>()
        var selected by mutableStateOf(AppTab.home)
        compose.setContent {
            Themed(dark = false) {
                CustomTabBar(selectedTab = selected, onSelect = {
                    picked += it
                    selected = it
                })
            }
        }
        listOf(AppTab.maps, AppTab.places, AppTab.profile, AppTab.record, AppTab.home).forEach {
            compose.onNodeWithTag("tab_${it.rawValue}").performClick()
        }
        assertEquals(listOf(AppTab.maps, AppTab.places, AppTab.profile, AppTab.record, AppTab.home), picked)
    }

    @Test fun tappingTheOpenTabReportsAReselect() {
        val reselected = mutableListOf<AppTab>()
        compose.setContent {
            Themed(dark = false) {
                CustomTabBar(selectedTab = AppTab.home, onSelect = {}, onReselect = { reselected += it })
            }
        }
        compose.onNodeWithTag("tab_maps").performClick()
        compose.onNodeWithTag("tab_home").performClick()
        assertEquals(listOf(AppTab.home), reselected)
    }

    @Test fun tabsAreTabsAndOnlyTheOpenOneIsSelected() {
        compose.setContent { Themed(dark = false) { CustomTabBar(selectedTab = AppTab.places, onSelect = {}) } }
        compose.onNodeWithTag("tab_places").assertIsSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        listOf(AppTab.home, AppTab.maps, AppTab.profile).forEach { compose.onNodeWithTag("tab_${it.rawValue}").assertIsNotSelected() }
    }

    /** Recording is an action, not a tab: the disk has no label, only what TalkBack says. */
    @Test fun theRecordDiskHasNoLabel() {
        compose.setContent { Themed(dark = false) { CustomTabBar(selectedTab = AppTab.home, onSelect = {}) } }
        compose.onNodeWithTag("tab_record").assertContentDescriptionContains(strings.recordTripCta())
        compose.onNodeWithText(strings.recordTripCta()).assertDoesNotExist()
        compose.onNodeWithText(strings.feed()).assertExists()
        compose.onNodeWithText(strings.tabMap()).assertExists()
        compose.onNodeWithText(strings.tabPlaces()).assertExists()
        compose.onNodeWithText(strings.tabMe()).assertExists()
    }

    /** What the eye sees as a dot on Я is said aloud too. */
    @Test fun unseenDraftsAreSpokenOnMe() {
        var unseen by mutableStateOf(true)
        compose.setContent { Themed(dark = false) { CustomTabBar(selectedTab = AppTab.home, onSelect = {}, hasUnseenDrafts = unseen) } }
        compose.onNodeWithTag("tab_profile").assertContentDescriptionContains(strings.draftsTabHint())
        unseen = false
        compose.waitForIdle()
        compose.onNodeWithTag("tab_profile_unseen", useUnmergedTree = true).assertDoesNotExist()
    }
}
