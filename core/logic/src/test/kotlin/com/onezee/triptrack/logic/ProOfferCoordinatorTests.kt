package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import kotlin.test.*

// iOS: ProOfferCoordinatorTests.swift. Session events are explicit in the pure Kotlin policy.
class ProOfferCoordinatorTests {
    private val now = Instant.ofEpochSecond(1_800_000_000)
    private fun input(tripCount: Int = 12) = ProContextOffer.Input(now, false, false, tripCount,
        firstLaunchAt = now.minusSeconds(40 * 86_400L), consecutiveDeclines = 0, totalDeclines = 0,
        shownMoments = emptySet(), isFirstSession = false, recordedTripThisSession = false, hasNetwork = true,
        onEligibleScreen = true, alreadyShownThisSession = false, entitlementsKnown = true, afterFailure = false, fromPush = false)

    @Test fun testATripRecordedInThisSessionBlocksOnlyThisSession() {
        val session = ProOfferSession(isFirstSession = false)
        session.recordingEnded()
        assertNull(session.moment(input()))
        session.sessionEnded()
        assertEquals(ProOfferMoment.tenTrips, session.moment(input()))
    }

    @Test fun testTheFirstSessionEndsWhenTheAppLeavesTheScreen() {
        val session = ProOfferSession(isFirstSession = true)
        assertNull(session.moment(input()))
        session.sessionEnded()
        assertEquals(ProOfferMoment.tenTrips, session.moment(input()))
    }

    @Test fun testWithoutATripTheOfferStillWaitsForItsThresholds() {
        val session = ProOfferSession(isFirstSession = false)
        assertNull(session.moment(input(tripCount = 4)))
        assertEquals(ProOfferMoment.tenTrips, session.moment(input(tripCount = 12)))
    }

    @Test fun backgroundDoesNotRepeatAnAlreadyShownExpiryNotice() {
        val session = ProOfferSession(isFirstSession = false)
        val expired = input().copy(proExpiredAt = now.minusSeconds(86_400))
        assertEquals(ProOfferMoment.expired, session.moment(expired))
        session.sessionEnded()
        assertNull(session.moment(expired))
    }

    @Test fun aTripEndingInThePocketQuietsTheNextVisit() {
        val session = ProOfferSession(isFirstSession = false)
        session.sessionEnded()
        session.recordingEnded()
        assertNull(session.moment(input()))
    }
}
