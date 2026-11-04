// iOS: AtlasSlotTests.swift, AtlasChromeGeometryTests.swift
package com.onezee.triptrack.feature

import com.onezee.triptrack.feature.atlas.*
import org.junit.Test
import kotlin.test.*

class AtlasSlotTests {
    @Test fun pushedTripOwnsBackBeforeAtlasLayers() {
        assertFalse(atlasOwnsBack(true, true, false, AtlasSheetDetent.expanded))
        assertTrue(atlasOwnsBack(false, true, false, AtlasSheetDetent.expanded))
    }
    private val se = AtlasSlot(667.0, 20.0, 0.0)
    private val mini = AtlasSlot(812.0, 50.0, 34.0)
    private val thirteen = AtlasSlot(844.0, 47.0, 34.0)
    private val fifteen = AtlasSlot(852.0, 59.0, 34.0)
    private val proMax = AtlasSlot(932.0, 59.0, 34.0)
    @Test fun detentTopsMatchErrata() {
        assertEquals(534.0, thirteen.collapsedTop()); assertEquals(654.0, thirteen.peekTop)
        assertEquals(103.0, thirteen.expandedTop); assertEquals(456.0, thirteen.collapsedTop(AtlasSummaryVariant.region))
        assertEquals(420.0, thirteen.substateCeiling)
        assertEquals(502.0, mini.collapsedTop()); assertEquals(622.0, mini.peekTop); assertEquals(106.0, mini.expandedTop)
        assertEquals(622.0, proMax.collapsedTop()); assertEquals(742.0, proMax.peekTop); assertEquals(115.0, proMax.expandedTop)
    }
    @Test fun everySubstateStaysUnderCeiling() {
        for (slot in listOf(se, mini, thirteen, fifteen, proMax)) assertTrue(slot.collapsedTop(AtlasSummaryVariant.region) >= slot.substateCeiling)
    }
    @Test fun seFollowsOurTabBar() {
        assertEquals(76.0, se.expandedTop); assertEquals(357.0, se.collapsedTop())
        assertFalse(se.showsPeekRow); assertTrue(thirteen.showsPeekRow)
        assertEquals(16.0, se.modalBottomPadding); assertEquals(34.0, thirteen.modalBottomPadding)
    }
    @Test fun collapsedTopFollowsContent() {
        assertEquals(534.0, thirteen.collapsedTop(AtlasSummaryVariant.summary))
        assertEquals(542.0, thirteen.collapsedTop(AtlasSummaryVariant.shortCard))
        assertEquals(524.0, thirteen.collapsedTop(AtlasSummaryVariant.emptyAtlas))
        assertEquals(478.0, thirteen.collapsedTop(AtlasSummaryVariant.withNoticeRow))
    }
    @Test fun settleBoundaryIsFortyFivePercent() {
        val boundary = thirteen.settleBoundary()
        assertEquals(534 - (534 - thirteen.expandedTop) * .45, boundary, .001)
        assertEquals(AtlasSheetDetent.expanded, AtlasSlot.settle(boundary - 1, 0.0, thirteen))
        assertEquals(AtlasSheetDetent.collapsed, AtlasSlot.settle(boundary + 1, 0.0, thirteen))
    }
    @Test fun flickBeatsNearestDetent() {
        assertEquals(AtlasSheetDetent.expanded, AtlasSlot.settle(520.0, -400.0, thirteen))
        assertEquals(AtlasSheetDetent.collapsed, AtlasSlot.settle(130.0, 400.0, thirteen))
    }
    @Test fun clampingUsesRubberAndKeepsPeek() {
        assertEquals(thirteen.expandedTop - 30, thirteen.clamped(thirteen.expandedTop - 100), .001)
        assertEquals(564.0, thirteen.clamped(634.0), .001)
        assertEquals(654.0, thirteen.clamped(654.0, AtlasSheetDetent.peek))
    }
    @Test fun attributionNeverFollowsExpandedList() {
        assertEquals(318.0, thirteen.attributionInset(AtlasSheetDetent.collapsed))
        assertEquals(198.0, thirteen.attributionInset(AtlasSheetDetent.peek))
        assertEquals(thirteen.attributionInset(AtlasSheetDetent.collapsed), thirteen.attributionInset(AtlasSheetDetent.expanded))
        assertEquals(374.0, thirteen.attributionInset(AtlasSheetDetent.collapsed, AtlasSummaryVariant.withNoticeRow))
        for (slot in listOf(se, mini, thirteen, fifteen, proMax)) for (detent in AtlasSheetDetent.entries) for (variant in AtlasSummaryVariant.entries) {
            val inset = slot.attributionInset(detent, variant)
            assertTrue(inset > 0); assertTrue(slot.height - slot.safeTop - inset > 120)
        }
    }
    @Test fun substateReachesListAndDismissesOnlyBelowItself() {
        val rest = thirteen.collapsedTop(AtlasSummaryVariant.region)
        assertEquals(300.0, thirteen.substateTop(300.0))
        assertEquals(thirteen.expandedTop - 30, thirteen.substateTop(thirteen.expandedTop - 100), .001)
        assertEquals(700.0, thirteen.substateTop(700.0))
        assertFalse(AtlasSlot.substateDismisses(200.0, 800.0, thirteen, AtlasSummaryVariant.region))
        assertFalse(AtlasSlot.substateDismisses(rest + 40, 0.0, thirteen, AtlasSummaryVariant.region))
        assertTrue(AtlasSlot.substateDismisses(rest + 80, 0.0, thirteen, AtlasSummaryVariant.region))
        assertTrue(AtlasSlot.substateDismisses(rest + 10, 400.0, thirteen, AtlasSummaryVariant.region))
        assertFalse(AtlasSlot.substateDismisses(rest + 80, -400.0, thirteen, AtlasSummaryVariant.region))
    }
    @Test fun onlyMapControlsFade() {
        assertEquals(1.0, AtlasSlot.controlsOpacity(534.0)); assertEquals(1.0, AtlasSlot.controlsOpacity(300.0))
        assertEquals(.5, AtlasSlot.controlsOpacity(270.0)); assertEquals(0.0, AtlasSlot.controlsOpacity(240.0))
    }
    @Test fun containerCoordinatesPreserveScreenChrome() {
        for (slot in listOf(se, mini, thirteen, fifteen, proMax)) for (detent in AtlasSheetDetent.entries) {
            val screenTop = slot.top(detent)
            assertEquals(screenTop, AtlasSheet.containerTop(screenTop, slot.safeTop) + slot.safeTop)
            assertEquals(slot.height, screenTop + AtlasSheet.height(screenTop, slot.height))
        }
    }
}
