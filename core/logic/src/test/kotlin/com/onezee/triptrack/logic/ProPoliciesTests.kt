package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import kotlin.test.*

// iOS: PlusGateTests.swift, PlusStoreTests.swift (pure part), PlusAvailabilityTests.swift, PlusAccess.swift.
class PlusStoreTests {
    @Test fun testAllFiveFeaturesAndAllAccessCombinations() {
        assertEquals(setOf("profileBackgrounds", "avatarFrame", "vehicleCardStyle", "routeLineStyle", "manualTrip"), PlusFeature.entries.map { it.name }.toSet())
        for (feature in PlusFeature.entries) {
            assertEquals(PlusAccessLevel.locked, PlusGate.allows(feature, false, false))
            assertEquals(PlusAccessLevel.hidden, PlusGate.allows(feature, false, true))
            assertEquals(PlusAccessLevel.open, PlusGate.allows(feature, true, false))
            assertEquals(PlusAccessLevel.open, PlusGate.allows(feature, true, true))
        }
    }
    @Test fun testStorefrontSpellingsAndGlobalAvailability() {
        for (code in listOf("RUS", "RU", "ru")) assertTrue(PlusStore.hidesPlus(code, true))
        for (code in listOf("GEO", "USA", null)) assertFalse(PlusStore.hidesPlus(code, true))
        for (code in listOf("USA", "DEU", "GEO", "RUS", null)) assertTrue(PlusStore.hidesPlus(code, false))
        assertTrue(PlusAccess.initialStorefrontHidesPlus("RUS", "US", true))
        assertTrue(PlusAccess.initialStorefrontHidesPlus(null, "RU", true))
        assertFalse(PlusAccess.initialStorefrontHidesPlus("USA", "RU", true))
        assertEquals("RUS", PlusAccess.storefrontCountry("RUS", null))
        assertEquals("DEU", PlusAccess.storefrontCountry("RUS", "DEU"))
    }
    @Test fun testStateTable() {
        assertEquals(PlusStore.State.trial, PlusStore.resolve(true, true, PlusStore.RenewalSummary.subscribed))
        assertEquals(PlusStore.State.active, PlusStore.resolve(true, false, PlusStore.RenewalSummary.subscribed))
        assertEquals(PlusStore.State.expired, PlusStore.resolve(false, false, PlusStore.RenewalSummary.expired))
        assertEquals(PlusStore.State.expired, PlusStore.resolve(false, false, PlusStore.RenewalSummary.revoked))
        assertEquals(PlusStore.State.none, PlusStore.resolve(false, false, null))
        for (renewal in listOf(PlusStore.RenewalSummary.inGracePeriod, PlusStore.RenewalSummary.inBillingRetryPeriod))
            assertEquals(PlusStore.State.grace, PlusStore.resolve(false, false, renewal))
        for (state in PlusStore.State.entries) assertEquals(state in setOf(PlusStore.State.trial, PlusStore.State.active, PlusStore.State.grace), PlusStore.grants(state))
    }
    @Test fun testTheLivestStatusOfTheGroupWins() {
        val expired = PlusStore.RenewalSummary.expired
        for ((states, result) in listOf(listOf(expired, PlusStore.RenewalSummary.inGracePeriod) to PlusStore.RenewalSummary.inGracePeriod,
            listOf(expired, PlusStore.RenewalSummary.subscribed) to PlusStore.RenewalSummary.subscribed,
            listOf(expired, PlusStore.RenewalSummary.revoked) to PlusStore.RenewalSummary.revoked, listOf(expired) to expired))
            assertEquals(result, PlusStore.summarize(states))
        assertNull(PlusStore.summarize(emptyList()))
    }
    @Test fun testTheLatestExpiryWins() {
        val early = Instant.ofEpochSecond(1000); val late = Instant.ofEpochSecond(2000)
        assertEquals(late, PlusStore.later(early, late)); assertEquals(late, PlusStore.later(late, early))
        assertEquals(late, PlusStore.later(null, late)); assertEquals(early, PlusStore.later(early, null)); assertNull(PlusStore.later(null, null))
        val a = Instant.ofEpochSecond(100); val b = Instant.ofEpochSecond(200)
        assertTrue(PlusStore.endsLater(b, a)); assertFalse(PlusStore.endsLater(a, b)); assertFalse(PlusStore.endsLater(a, a))
        assertTrue(PlusStore.endsLater(a, null)); assertFalse(PlusStore.endsLater(null, a))
        assertEquals(true, PlusStore.autoRenews(listOf(false, true))); assertEquals(false, PlusStore.autoRenews(listOf(false)))
        assertNull(PlusStore.autoRenews(emptyList()))
    }
    @Test fun testEachPurchaseOutcomeGetsItsOwnLine() {
        assertEquals(PlusStore.PurchaseMessage.none, PlusStore.message(PlusStore.PurchaseOutcome.Success))
        assertEquals(PlusStore.PurchaseMessage.none, PlusStore.message(PlusStore.PurchaseOutcome.Cancelled))
        assertEquals(PlusStore.PurchaseMessage.pending, PlusStore.message(PlusStore.PurchaseOutcome.Pending))
        assertEquals(PlusStore.PurchaseMessage.failed, PlusStore.message(PlusStore.PurchaseOutcome.Failed(java.io.IOException())))
    }
}

// iOS: ProStatusTests.swift (state/destination part).
class ProStatusTests {
    private val until = Instant.ofEpochSecond(1_822_000_000)
    @Test fun testSubscriptionBeatsStorefront() {
        val s = ProStatus.resolve(PlusStore.State.active, until, false, true)
        assertEquals(ProStatus.ProWithoutStore(until), s); assertEquals(ProRowDestination.manageSubscription, s.destination)
    }
    @Test fun testHiddenOnlyWhenThereIsNoSubscription() {
        for (state in listOf(PlusStore.State.none, PlusStore.State.expired)) {
            val s = ProStatus.resolve(state, if (state == PlusStore.State.expired) until else null, false, true)
            assertEquals(ProStatus.HiddenStorefront, s); assertEquals(ProRowDestination.none, s.destination); assertFalse(s.showsRow)
        }
    }
    @Test fun testEveryStatusHasItsOwnDestination() {
        for ((status, expected) in listOf(ProStatus.None to ProRowDestination.paywall,
            ProStatus.Trial(until) to ProRowDestination.manageSubscription, ProStatus.Active(until) to ProRowDestination.manageSubscription,
            ProStatus.Expired(until) to ProRowDestination.paywall, ProStatus.Grace to ProRowDestination.manageSubscription,
            ProStatus.Pending to ProRowDestination.nothing)) {
            assertEquals(expected, status.destination); assertTrue(status.showsRow)
        }
        assertEquals(ProStatus.Pending, ProStatus.resolve(PlusStore.State.none, null, true, false))
        assertEquals(ProStatus.Trial(until), ProStatus.resolve(PlusStore.State.trial, until, false, false))
        assertEquals(ProStatus.Grace, ProStatus.resolve(PlusStore.State.grace, until, true, false))
        assertEquals(ProStatus.Expired(until), ProStatus.resolve(PlusStore.State.expired, until, false, false))
    }
}

// iOS: ProManageModelTests.swift (model part; generated translated copy belongs to the app).
class ProManageModelTests {
    private val date = Instant.ofEpochSecond(1_822_000_000)
    @Test fun testTheLineNeverPromisesAChargeThatWillNotHappen() {
        data class Case(val status: ProStatus, val period: PlusProductInfo.Period?, val price: String?, val renews: Boolean?, val expected: ProManageLine)
        val year = PlusProductInfo.Period.yearly; val month = PlusProductInfo.Period.monthly
        val cases = listOf(
            Case(ProStatus.Trial(date), year, "24,99 €", true, ProManageLine.TrialRenews(date, "24,99 €", year)),
            Case(ProStatus.Active(date), month, "5,99 €", true, ProManageLine.Renews(date, "5,99 €", month)),
            Case(ProStatus.Trial(date), year, "24,99 €", false, ProManageLine.EndsOn(date)),
            Case(ProStatus.Active(date), year, "24,99 €", false, ProManageLine.EndsOn(date)),
            Case(ProStatus.Active(date), year, "24,99 €", null, ProManageLine.Until(date)),
            Case(ProStatus.Trial(date), null, null, true, ProManageLine.Until(date)),
            Case(ProStatus.ProWithoutStore(date), year, "24,99 €", true, ProManageLine.Until(date)),
            Case(ProStatus.ProWithoutStore(date), year, "24,99 €", false, ProManageLine.EndsOn(date)),
            Case(ProStatus.Grace, year, "24,99 €", true, ProManageLine.BillingProblem),
            Case(ProStatus.Active(null), year, "24,99 €", true, ProManageLine.Unknown),
        )
        for (c in cases) assertEquals(c.expected, ProManageLine.resolve(c.status, c.period, c.price, c.renews))
    }
}
