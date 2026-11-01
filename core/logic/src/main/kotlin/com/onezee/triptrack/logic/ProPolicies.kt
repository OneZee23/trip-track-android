package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

// iOS: PlusGate.swift. Existing entitlement wins even where purchases are hidden.
object PlusGate {
    fun allows(feature: PlusFeature, isPlus: Boolean, storefrontHidesPlus: Boolean): PlusAccessLevel = when {
        isPlus -> PlusAccessLevel.open
        storefrontHidesPlus -> PlusAccessLevel.hidden
        else -> PlusAccessLevel.locked
    }
}

// iOS: PlusAccess.swift. A missing storefront response preserves the last known country.
object PlusAccess {
    const val storefrontKey = "plus.storefront.country.v1"
    fun initialStorefrontHidesPlus(rememberedCountry: String?, deviceRegion: String?, available: Boolean): Boolean =
        PlusStore.hidesPlus(rememberedCountry ?: deviceRegion, available)
    fun storefrontCountry(knownCountry: String?, responseCountry: String?): String? = responseCountry?.uppercase(Locale.ROOT) ?: knownCountry
}

// iOS: PlusStore.swift. Android launch supplies available=false; the gate stays generic.
fun PlusStore.hidesPlus(countryCode: String?, available: Boolean): Boolean =
    !available || countryCode?.uppercase(Locale.ROOT) in setOf("RU", "RUS")
// iOS: PlusStore.swift, refreshUnknownStorefront. A known country is updated by storefront events.
fun PlusStore.refreshUnknownStorefront(knownCountry: String?, readCountry: () -> String?): String? =
    if (knownCountry == null) readCountry()?.uppercase(Locale.ROOT) else null
fun PlusStore.grants(state: PlusStore.State): Boolean = state in setOf(PlusStore.State.trial, PlusStore.State.active, PlusStore.State.grace)
fun PlusStore.resolve(entitled: Boolean, isTrial: Boolean, renewal: PlusStore.RenewalSummary?): PlusStore.State = when {
    renewal == PlusStore.RenewalSummary.inGracePeriod || renewal == PlusStore.RenewalSummary.inBillingRetryPeriod -> PlusStore.State.grace
    entitled -> if (isTrial) PlusStore.State.trial else PlusStore.State.active
    renewal == PlusStore.RenewalSummary.expired || renewal == PlusStore.RenewalSummary.revoked -> PlusStore.State.expired
    else -> PlusStore.State.none
}
fun PlusStore.summarize(states: List<PlusStore.RenewalSummary>): PlusStore.RenewalSummary? =
    listOf(PlusStore.RenewalSummary.inGracePeriod, PlusStore.RenewalSummary.inBillingRetryPeriod,
        PlusStore.RenewalSummary.subscribed, PlusStore.RenewalSummary.revoked, PlusStore.RenewalSummary.expired).firstOrNull { it in states }
fun PlusStore.endsLater(candidate: Instant?, than: Instant?): Boolean = candidate != null && (than == null || candidate > than)
fun PlusStore.later(a: Instant?, b: Instant?): Instant? = if (a == null) b else if (b == null) a else maxOf(a, b)
fun PlusStore.autoRenews(live: List<Boolean>): Boolean? = if (live.isEmpty()) null else live.any { it }
fun PlusStore.message(outcome: PlusStore.PurchaseOutcome): PlusStore.PurchaseMessage = when (outcome) {
    PlusStore.PurchaseOutcome.Success, PlusStore.PurchaseOutcome.Cancelled -> PlusStore.PurchaseMessage.none
    PlusStore.PurchaseOutcome.Pending -> PlusStore.PurchaseMessage.pending
    is PlusStore.PurchaseOutcome.Failed -> PlusStore.PurchaseMessage.failed
}

// iOS: ProStatus.swift
fun ProStatus.Companion.resolve(state: PlusStore.State, expires: Instant?, isPending: Boolean,
                                storefrontHidesPlus: Boolean): ProStatus = when {
    PlusStore.grants(state) -> when {
        storefrontHidesPlus -> ProStatus.ProWithoutStore(expires)
        state == PlusStore.State.trial -> ProStatus.Trial(expires)
        state == PlusStore.State.grace -> ProStatus.Grace
        else -> ProStatus.Active(expires)
    }
    storefrontHidesPlus -> ProStatus.HiddenStorefront
    isPending -> ProStatus.Pending
    state == PlusStore.State.expired -> ProStatus.Expired(expires)
    else -> ProStatus.None
}
val ProStatus.showsRow: Boolean get() = this != ProStatus.HiddenStorefront
val ProStatus.destination: ProRowDestination get() = when (this) {
    ProStatus.None, is ProStatus.Expired -> ProRowDestination.paywall
    is ProStatus.Trial, is ProStatus.Active, ProStatus.Grace, is ProStatus.ProWithoutStore -> ProRowDestination.manageSubscription
    ProStatus.Pending -> ProRowDestination.nothing
    ProStatus.HiddenStorefront -> ProRowDestination.none
}

// iOS: ProManageModel.swift. Unknown renewal state must never promise a charge.
fun ProManageLine.Companion.resolve(status: ProStatus, period: PlusProductInfo.Period?, productPrice: String?,
                                    willAutoRenew: Boolean?): ProManageLine {
    if (status == ProStatus.Grace) return ProManageLine.BillingProblem
    val date = when (status) {
        is ProStatus.Trial -> status.until
        is ProStatus.Active -> status.until
        is ProStatus.ProWithoutStore -> status.until
        else -> null
    } ?: return ProManageLine.Unknown
    if (willAutoRenew == false) return ProManageLine.EndsOn(date)
    val price = productPrice.takeUnless { status is ProStatus.ProWithoutStore }
    if (willAutoRenew == null || price == null || period == null) return ProManageLine.Until(date)
    return if (status is ProStatus.Trial) ProManageLine.TrialRenews(date, price, period)
    else ProManageLine.Renews(date, price, period)
}

// iOS: TipMoment.swift
val TipMoment.minTrips: Int get() = 20
val TipMoment.minDays: Int get() = 30
val TipMoment.notableMetres: Double get() = 100_000.0
val TipMoment.cooldownDays: Int get() = 180
val TipMoment.cooldownAfterDeclineDays: Int get() = 365
val TipMoment.maxDeclines: Int get() = 2
val TipMoment.quietAfterTipDays: Int get() = 365
fun TipMoment.shouldAsk(input: TipMoment.Input): Boolean {
    if (input.storefrontHidesPlus || input.isDraft || input.afterFailure || input.declines >= maxDeclines) return false
    fun days(from: Instant): Long = maxOf(0, ProContextOffer.days(from, input.now))
    if (input.tippedAt?.let { days(it) < quietAfterTipDays } == true) return false
    if (input.tripCount < minTrips) return false
    if (input.firstLaunchAt?.let { days(it) >= minDays } != true) return false
    if (input.tripDistanceMetres < notableMetres) return false
    val cooldown = if (input.declines > 0) cooldownAfterDeclineDays else cooldownDays
    if (input.lastAskedAt?.let { days(it) < cooldown } == true) return false
    return true
}

// iOS: ProContextOffer.swift
val ProContextOffer.minTrips: Int get() = 5
val ProContextOffer.minDays: Int get() = 7
val ProContextOffer.cooldownDays: Int get() = 30
val ProContextOffer.declinesForPause: Int get() = 2
val ProContextOffer.pauseDays: Int get() = 90
val ProContextOffer.maxDeclines: Int get() = 3
val ProContextOffer.tripsForFirstOffer: Int get() = 10
val ProContextOffer.daysForSecondOffer: Int get() = 30
fun ProContextOffer.days(from: Instant, to: Instant): Long = (to.secondsSince(from) / 86_400).toLong()
fun ProContextOffer.isUnconditionallyBlocked(input: ProContextOffer.Input): Boolean = with(input) {
    isPlus || !entitlementsKnown || !hasNetwork || isFirstSession || recordedTripThisSession || afterFailure ||
        fromPush || !onEligibleScreen || alreadyShownThisSession
}
fun ProContextOffer.isBlocked(input: ProContextOffer.Input, moment: ProOfferMoment): Boolean {
    if (isUnconditionallyBlocked(input)) return true
    // Expiry is a notice about the saved appearance, independent of sales thresholds and refusals.
    if (moment == ProOfferMoment.expired) return false
    if (input.totalDeclines >= maxDeclines || input.tripCount < minTrips) return true
    if (input.firstLaunchAt?.let { days(it, input.now) >= minDays } != true) return true
    input.lastOfferAt?.let {
        val since = days(it, input.now)
        if (since < cooldownDays || (input.consecutiveDeclines >= declinesForPause && since < pauseDays)) return true
    }
    return false
}
fun ProContextOffer.moment(input: ProContextOffer.Input): ProOfferMoment? {
    val moment = when {
        input.proExpiredAt != null -> ProOfferMoment.expired
        input.tripCount >= tripsForFirstOffer && ProOfferMoment.tenTrips !in input.shownMoments -> ProOfferMoment.tenTrips
        input.firstLaunchAt?.let { days(it, input.now) >= daysForSecondOffer } == true && ProOfferMoment.oneMonth !in input.shownMoments -> ProOfferMoment.oneMonth
        else -> return null
    }
    if (isBlocked(input, moment)) return null
    if (input.storefrontHidesPlus && moment != ProOfferMoment.expired) return null
    return moment
}
fun ProContextOffer.sells(moment: ProOfferMoment, storefrontHidesPlus: Boolean): Boolean = !storefrontHidesPlus
fun ProContextOffer.feature(moment: ProOfferMoment): PlusFeature =
    if (moment == ProOfferMoment.oneMonth) PlusFeature.routeLineStyle else PlusFeature.profileBackgrounds

// iOS: ProOfferCoordinator.swift (session policy only). No purchase UI is enabled on Android.
class ProOfferSession(private var isFirstSession: Boolean) {
    private var recordedTripThisSession = false
    private var shownThisSession = false

    fun recordingEnded() { recordedTripThisSession = true }

    fun sessionEnded() {
        isFirstSession = false
        recordedTripThisSession = false
        // M4 can repeat: keep the shown flag for the whole process, as on iOS.
    }

    fun moment(input: ProContextOffer.Input): ProOfferMoment? = ProContextOffer.moment(input.copy(
        isFirstSession = isFirstSession,
        recordedTripThisSession = recordedTripThisSession,
        alreadyShownThisSession = shownThisSession,
    ))?.also { shownThisSession = true }
}

// iOS: RoadSceneTime.swift. The caller supplies local time; the scene never requests location.
fun RoadSceneTime.Companion.slot(date: Instant, zone: ZoneId): RoadSceneTime = when (date.atZone(zone).hour) {
    in 5..7 -> RoadSceneTime.dawn
    in 8..16 -> RoadSceneTime.day
    in 17..19 -> RoadSceneTime.sunset
    else -> RoadSceneTime.night
}
