package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import kotlin.test.*

// iOS: ProContextOfferTests.swift
class ProContextOfferTests {
    private val now = Instant.ofEpochSecond(1_800_000_000)
    private fun ago(days: Int) = now.minusSeconds(days * 86_400L)
    private fun eligible(tweak: ProContextOffer.Input.() -> Unit = {}) = ProContextOffer.Input(now, false, false, 40,
        firstLaunchAt = ago(180), consecutiveDeclines = 0, totalDeclines = 0, shownMoments = emptySet(), isFirstSession = false,
        recordedTripThisSession = false, hasNetwork = true, onEligibleScreen = true, alreadyShownThisSession = false,
        entitlementsKnown = true, afterFailure = false, fromPush = false).apply(tweak)
    private val blocks: List<ProContextOffer.Input.() -> Unit> = listOf(
        { isPlus = true }, { entitlementsKnown = false }, { hasNetwork = false }, { isFirstSession = true },
        { recordedTripThisSession = true }, { afterFailure = true }, { fromPush = true },
        { onEligibleScreen = false }, { alreadyShownThisSession = true },
    )
    @Test fun testTheEligiblePersonGetsAnOffer() { assertNotNull(ProContextOffer.moment(eligible())) }
    @Test fun testEachHardBlockAloneIsEnough() {
        for (block in blocks) {
            val input = eligible(block)
            assertNull(ProContextOffer.moment(input)); assertTrue(ProContextOffer.isUnconditionallyBlocked(input))
        }
    }
    @Test fun testTheUnconditionalBlocksStopEvenTheNotice() {
        for (block in blocks) assertNull(ProContextOffer.moment(eligible { proExpiredAt = ago(3); block() }))
    }
    @Test fun testExpiryIgnoresEveryThresholdAndEveryRefusal() {
        assertEquals(ProOfferMoment.expired, ProContextOffer.moment(eligible {
            proExpiredAt = ago(2); tripCount = 1; firstLaunchAt = ago(1); lastOfferAt = ago(1); totalDeclines = 9; consecutiveDeclines = 9
        }))
    }
    @Test fun testTheSameViolationsStillCloseTheOffers() {
        val input = eligible { tripCount = 1; firstLaunchAt = ago(1); lastOfferAt = ago(1); totalDeclines = 9; consecutiveDeclines = 9 }
        assertNull(ProContextOffer.moment(input))
        assertTrue(ProContextOffer.isBlocked(input, ProOfferMoment.tenTrips)); assertTrue(ProContextOffer.isBlocked(input, ProOfferMoment.oneMonth))
        assertFalse(ProContextOffer.isBlocked(input, ProOfferMoment.expired))
    }
    @Test fun testAHiddenStorefrontSilencesTheOffersButNotTheNotice() {
        assertNull(ProContextOffer.moment(eligible { storefrontHidesPlus = true; tripCount = 10 }))
        assertNull(ProContextOffer.moment(eligible { storefrontHidesPlus = true; shownMoments = setOf(ProOfferMoment.tenTrips); firstLaunchAt = ago(40) }))
        assertEquals(ProOfferMoment.expired, ProContextOffer.moment(eligible { storefrontHidesPlus = true; proExpiredAt = ago(3) }))
        assertFalse(ProContextOffer.sells(ProOfferMoment.expired, true)); assertTrue(ProContextOffer.sells(ProOfferMoment.expired, false))
    }
    @Test fun testAnActivePlusBeatsEvenTheExpiredMoment() { assertNull(ProContextOffer.moment(eligible { isPlus = true; proExpiredAt = ago(3) })) }
    @Test fun testNothingBeforeTheFifthTripAndTheSeventhDay() {
        assertNull(ProContextOffer.moment(eligible { tripCount = 4 }))
        assertNull(ProContextOffer.moment(eligible { firstLaunchAt = ago(6) }))
        assertFalse(ProContextOffer.isBlocked(eligible { tripCount = ProContextOffer.minTrips; firstLaunchAt = ago(ProContextOffer.minDays);
            shownMoments = setOf(ProOfferMoment.tenTrips, ProOfferMoment.oneMonth) }, ProOfferMoment.tenTrips))
    }
    @Test fun testAnUnknownFirstLaunchMeansSilence() { assertNull(ProContextOffer.moment(eligible { firstLaunchAt = null })) }
    @Test fun testNoMoreThanOneSheetInThirtyDays() {
        assertNull(ProContextOffer.moment(eligible { lastOfferAt = ago(29) }))
        assertNotNull(ProContextOffer.moment(eligible { lastOfferAt = ago(30) }))
    }
    @Test fun testTwoDeclinesInARowBuyNinetyDaysOfSilence() {
        val paused = eligible { consecutiveDeclines = 2; totalDeclines = 2; lastOfferAt = ago(60) }
        assertNull(ProContextOffer.moment(paused)); assertNotNull(ProContextOffer.moment(paused.copy(lastOfferAt = ago(90))))
    }
    @Test fun testASingleDeclineDoesNotBuyThePause() {
        assertNotNull(ProContextOffer.moment(eligible { consecutiveDeclines = 1; totalDeclines = 1; lastOfferAt = ago(31) }))
    }
    @Test fun testThreeDeclinesTurnItOffForever() {
        assertNull(ProContextOffer.moment(eligible { totalDeclines = 3; consecutiveDeclines = 0; lastOfferAt = ago(3650) }))
    }
    @Test fun testMomentPriorityAndRepeatability() {
        assertEquals(ProOfferMoment.expired, ProContextOffer.moment(eligible { proExpiredAt = ago(5) }))
        assertEquals(ProOfferMoment.tenTrips, ProContextOffer.moment(eligible { tripCount = 10; firstLaunchAt = ago(40) }))
        assertEquals(ProOfferMoment.oneMonth, ProContextOffer.moment(eligible { shownMoments = setOf(ProOfferMoment.tenTrips); firstLaunchAt = ago(40) }))
        assertNull(ProContextOffer.moment(eligible { shownMoments = setOf(ProOfferMoment.tenTrips, ProOfferMoment.oneMonth) }))
        assertEquals(ProOfferMoment.expired, ProContextOffer.moment(eligible {
            shownMoments = ProOfferMoment.entries.toSet(); proExpiredAt = ago(120); lastOfferAt = ago(31)
        }))
    }
    @Test fun testNoThresholdReachedMeansNoOffer() {
        val input = eligible { tripCount = 9; firstLaunchAt = ago(20) }
        assertFalse(ProContextOffer.isBlocked(input, ProOfferMoment.tenTrips)); assertNull(ProContextOffer.moment(input))
    }
    @Test fun testEachMomentLeadsToWhatItPromised() {
        assertEquals(PlusFeature.profileBackgrounds, ProContextOffer.feature(ProOfferMoment.tenTrips))
        assertEquals(PlusFeature.routeLineStyle, ProContextOffer.feature(ProOfferMoment.oneMonth))
        assertEquals(PlusFeature.profileBackgrounds, ProContextOffer.feature(ProOfferMoment.expired))
    }
    @Test fun testDaysAreWholeElapsedDaysNotCalendarDays() {
        assertEquals(30L, ProContextOffer.days(ago(30), now))
        assertEquals(0L, ProContextOffer.days(now.minusSeconds(86_399), now))
        assertEquals(0L, ProContextOffer.days(now, now))
    }
}
