package com.onezee.triptrack.logic

import com.onezee.triptrack.model.TipMoment
import java.time.Instant
import kotlin.test.*

// iOS: TipMomentTests.swift
class TipMomentTests {
    private val now = Instant.ofEpochSecond(1_800_000_000)
    private fun ago(days: Int) = now.minusSeconds(days * 86_400L)
    private fun ready(tweak: TipMoment.Input.() -> Unit = {}) = TipMoment.Input(now, false, 120_000.0, false, 40,
        firstLaunchAt = ago(90), declines = 0, afterFailure = false).apply(tweak)
    @Test fun testAsksWhenEverythingLinedUp() { assertTrue(TipMoment.shouldAsk(ready())) }
    @Test fun testSilentWhereTheStorefrontSellsNothing() { assertFalse(TipMoment.shouldAsk(ready { storefrontHidesPlus = true })) }
    @Test fun testSilentOnADraft() { assertFalse(TipMoment.shouldAsk(ready { isDraft = true })) }
    @Test fun testSilentRightAfterAFailure() { assertFalse(TipMoment.shouldAsk(ready { afterFailure = true })) }
    @Test fun testSilentBeforeEnoughTrips() {
        assertFalse(TipMoment.shouldAsk(ready { tripCount = TipMoment.minTrips - 1 }))
        assertTrue(TipMoment.shouldAsk(ready { tripCount = TipMoment.minTrips }))
    }
    @Test fun testSilentBeforeEnoughDays() { assertFalse(TipMoment.shouldAsk(ready { firstLaunchAt = ago(TipMoment.minDays - 1) })) }
    @Test fun testSilentWhenTheInstallDateIsUnknown() { assertFalse(TipMoment.shouldAsk(ready { firstLaunchAt = null })) }
    @Test fun testSilentOnAnOrdinaryDrive() {
        assertFalse(TipMoment.shouldAsk(ready { tripDistanceMetres = 12_000.0 }))
        assertTrue(TipMoment.shouldAsk(ready { tripDistanceMetres = TipMoment.notableMetres }))
    }
    @Test fun testDoesNotAskTwiceWithinHalfAYear() {
        assertFalse(TipMoment.shouldAsk(ready { lastAskedAt = ago(TipMoment.cooldownDays - 1) }))
        assertTrue(TipMoment.shouldAsk(ready { lastAskedAt = ago(TipMoment.cooldownDays) }))
    }
    @Test fun testOneDeclineStretchesThePauseToAYear() {
        assertFalse(TipMoment.shouldAsk(ready { declines = 1; lastAskedAt = ago(TipMoment.cooldownAfterDeclineDays - 1) }))
        assertTrue(TipMoment.shouldAsk(ready { declines = 1; lastAskedAt = ago(TipMoment.cooldownAfterDeclineDays) }))
    }
    @Test fun testTwoDeclinesCloseTheQuestionForever() {
        assertFalse(TipMoment.shouldAsk(ready { declines = TipMoment.maxDeclines; lastAskedAt = ago(10 * 365) }))
    }
    @Test fun testQuietForAYearAfterATip() {
        assertFalse(TipMoment.shouldAsk(ready { tippedAt = ago(TipMoment.quietAfterTipDays - 1) }))
        assertTrue(TipMoment.shouldAsk(ready { tippedAt = ago(TipMoment.quietAfterTipDays) }))
    }
    @Test fun testClockGoingBackwardsDoesNotReopenTheQuestion() { assertFalse(TipMoment.shouldAsk(ready { lastAskedAt = ago(-30) })) }
    @Test fun testHiddenStorefrontBeatsEveryOtherCondition() {
        assertFalse(TipMoment.shouldAsk(ready { storefrontHidesPlus = true; tripDistanceMetres = 1_000_000.0; tripCount = 500 }))
    }
}
