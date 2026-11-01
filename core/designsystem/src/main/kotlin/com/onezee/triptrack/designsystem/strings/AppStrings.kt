package com.onezee.triptrack.designsystem.strings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.onezee.triptrack.designsystem.R
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.floor

// iOS: AppStrings.swift, AppStringsCore.swift and AppStrings+*.swift.
class AppStrings(
    context: Context,
    val language: AppLanguage = AppLanguage.detect(context.resources.configuration.locales.toLanguageTags().split(',')),
    val zone: ZoneId = ZoneId.systemDefault(),
) : ResourceAppStrings(), MeasureWords {
    private val localizedContext = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        setLocales(LocaleList(language.locale))
    })
    private val resources = localizedContext.resources
    private val argumentPattern = Regex("%%|%([0-9]+)\\$[sd]")
    private val monthYearFormatter by lazy { LocalizedDateFormatter.templates("LLLL yyyy", zone).getValue(language) }
    private val longDateFormatter by lazy { LocalizedDateFormatter.templates("d MMMM yyyy", zone).getValue(language) }
    private val genitiveMonthFormatter by lazy { LocalizedDateFormatter.patterns("MMMM", zone).getValue(language) }

    fun withLanguage(language: AppLanguage): AppStrings =
        if (language == this.language) this else AppStrings(localizedContext, language, zone)

    // iOS: BluetoothScanSheet.swift (.carAudio label); platform wording lives in android.json.
    fun androidAuto(): String = tr(R.string.android_auto)

    override fun tr(id: Int, vararg args: Any): String =
        if (args.isEmpty()) resources.getString(id) else format(id, resources.getString(id), args)

    fun tr(key: String, ru: String, en: String): String {
        if (language == AppLanguage.ru) return ru
        if (language == AppLanguage.en) return en
        val id = resources.getIdentifier(key, "string", localizedContext.packageName)
        return if (id != 0) tokenTemplate(id, tr(id)) else en
    }

    private fun tokenTemplate(id: Int, template: String): String {
        val tokens = ResourceTokenRules.tokens(id) ?: return template
        return argumentPattern.replace(template) { match ->
            if (match.value == "%%") "%" else "{${tokens[match.groupValues[1].toInt() - 1]}}"
        }
    }

    private fun format(id: Int, template: String, args: Array<out Any>): String {
        val tokens = ResourceTokenRules.tokens(id) ?: return String.format(language.locale, template, *args)
        require(tokens.size == args.size) { "Wrong number of string substitutions" }
        val values = tokens.zip(args.toList()).toMap()
        // Foundation replaces all occurrences in call order, including tokens in an earlier value.
        return ResourceTokenRules.order(id)!!.fold(tokenTemplate(id, template)) { text, token ->
            text.replace("{$token}", values.getValue(token).toString())
        }
    }

    // iOS deliberately simplifies Filipino to abs(n) <= 1; its ICU category for 2 is one.
    // Select a representative quantity for the iOS form, while formatting the original count.
    override fun plural(id: Int, count: Int, vararg args: Any): String {
        val quantity = when (pluralForm(count)) {
            PluralForm.one -> 1
            PluralForm.few -> 2
            PluralForm.many, PluralForm.fraction -> if (language == AppLanguage.fil) 4 else 5
        }
        return if (args.isEmpty()) resources.getQuantityString(id, quantity)
        else format(id, resources.getQuantityString(id, quantity), args)
    }

    // iOS: AppStringsCore.swift, PluralForm.
    enum class PluralForm { one, few, many, fraction }

    fun pluralForm(n: Int): PluralForm = countForm(n.toLong())

    private fun countForm(n: Long): PluralForm {
        val value = abs(n)
        val mod10 = value % 10
        val mod100 = value % 100
        return when (language) {
            AppLanguage.ru, AppLanguage.uk -> when {
                mod10 == 1L && mod100 != 11L -> PluralForm.one
                mod10 in 2L..4L && mod100 !in 12L..14L -> PluralForm.few
                else -> PluralForm.many
            }
            AppLanguage.pl -> when {
                value == 1L -> PluralForm.one
                mod10 in 2L..4L && mod100 !in 12L..14L -> PluralForm.few
                else -> PluralForm.many
            }
            AppLanguage.fr, AppLanguage.fil, AppLanguage.pt -> if (value <= 1) PluralForm.one else PluralForm.many
            AppLanguage.id -> PluralForm.many
            else -> if (value == 1L) PluralForm.one else PluralForm.many
        }
    }

    fun pluralForm(shown: Double, fractionDigits: Int): PluralForm =
        if (fractionDigits > 0) PluralForm.fraction else countForm(rounded(shown).toLong())

    fun plural(n: Int, one: String, few: String? = null, many: String): String =
        plural(pluralForm(n), one, few, many)

    fun plural(form: PluralForm, one: String, few: String? = null, many: String, fraction: String? = null): String =
        when (form) {
            PluralForm.one -> one
            PluralForm.few -> few ?: many
            PluralForm.many -> many
            PluralForm.fraction -> fraction ?: few ?: many
        }

    fun adminProSplit(trial: Int, paid: Int): String = tr(R.string.adminProSplit, trial, paid)
    fun adminTripsLine(trips: Int, manual: Int): String = tr(R.string.adminTripsLine, trips, manual)
    fun cosmeticRetainedChoice(kind: ProShowcaseKind, name: String): String =
        tr(R.string.logic_cosmeticRetainedChoice, when (kind) {
            ProShowcaseKind.profileBackground -> showcaseBg()
            ProShowcaseKind.avatarFrame -> showcaseFrame()
            ProShowcaseKind.vehicleCard -> showcaseCar()
            ProShowcaseKind.routeLine -> showcaseLine()
        }, name)
    fun profileChapterSummary(distance: String, trips: Int, regions: Int): String =
        "$distance · $trips ${nounTrips(trips)} · $regions ${nounRegions(regions)}"
    fun recordingDiscardReason(reason: TripJunkClassifier.Reason): String = tr(when (reason) {
        TripJunkClassifier.Reason.tooShort -> R.string.logic_recordingDiscardReason_tooShort
        TripJunkClassifier.Reason.noDrivingSpeed -> R.string.logic_recordingDiscardReason_noDrivingSpeed
    })
    fun placeLastPass(date: String): String = tr(R.string.placeLastPass, date)
    fun placeUsually(time: String): String = tr(R.string.placeUsually, time)
    fun placeHereTimes(count: Int): String = tr(R.string.placeHereTimes, "${formattedCount(count)} ${nounTimes(count)}")
    fun placeChipUsually(time: String): String = tr(R.string.placeChipUsually, time)
    fun atlasPlacesInPeriod(places: String, total: Int): String = tr(R.string.atlasPlacesInPeriod, places, formattedCount(total))
    fun placesCount(count: Int): String = "${formattedCount(count)} ${nounPlaces(count)}"
    fun groupsWaitlistCount(count: Int): String = tr(R.string.logic_groupsWaitlistCount, formattedCount(count), nounPeople(count))
    fun formattedCount(count: Int): String = UnitNumber.grouped(count.toLong(), language.code)
    fun clubWaitingCount(count: Int): String = tr(R.string.logic_clubWaitingCount, formattedCount(count))

    fun unitDistanceShort(unit: DistanceUnit, value: Double, fractionDigits: Int): String =
        if (unit == DistanceUnit.km) km() else tr(when (pluralForm(value, fractionDigits)) {
            PluralForm.one -> R.string.logic_unitDistanceShort_one
            PluralForm.few -> R.string.logic_unitDistanceShort_few
            PluralForm.many -> R.string.logic_unitDistanceShort_many
            PluralForm.fraction -> R.string.logic_unitDistanceShort_fraction
        })
    fun unitSpeedShort(unit: DistanceUnit): String = if (unit == DistanceUnit.km) kmh() else tr(R.string.logic_unitSpeedShort)
    fun unitElevationShort(unit: DistanceUnit, value: Double, fractionDigits: Int): String =
        if (unit == DistanceUnit.km) unitMeters() else tr(when (pluralForm(value, fractionDigits)) {
            PluralForm.one -> R.string.logic_unitElevationShort_one
            PluralForm.few -> R.string.logic_unitElevationShort_few
            PluralForm.many -> R.string.logic_unitElevationShort_many
            PluralForm.fraction -> R.string.logic_unitElevationShort_fraction
        })
    fun unitDistanceBadge(unit: DistanceUnit): String = if (unit == DistanceUnit.km) km() else
        withLanguage(AppLanguage.en).tr(R.string.logic_unitDistanceShort_one)
    fun pricePerLiter(currency: String): String = tr(R.string.logic_pricePerLiter, currency)
    fun totalKm(unit: DistanceUnit): String = tr(R.string.logic_totalKm, unitDistanceShort(unit, 100.0, 0))
    fun onboardingConsentMarkdown(termsURL: String, privacyURL: String): String = tr(
        R.string.logic_onboardingConsentMarkdown,
        "[${tr(R.string.logic_onboardingConsentMarkdown_terms)}]($termsURL)",
        "[${tr(R.string.logic_onboardingConsentMarkdown_privacy)}]($privacyURL)",
    )
    fun earnedTimes(count: Int): String = tr(R.string.logic_earnedTimes, count, nounTimes(count))
    fun mapOpenedSummary(distance: String, regions: Int): String =
        "${tr(R.string.mapOpenedSummary, distance)} · ${groupedNumber(regions)} ${nounRegions(regions)}"
    fun mapOpenedLabel(): String = tr(R.string.mapOpenedSummary).substringBefore("%1\$s").trim(' ', ':', '\u00a0', '\u202f')
    fun mapSealsCount(count: Int): String = "${groupedNumber(count)} ${nounSeals(count)}"
    fun mapRiddlesNear(count: Int): String = tr(R.string.mapRiddlesNear, "${groupedNumber(count)} ${nounRiddles(count)}")
    fun sealKind(kind: DiscoveryKind): String = tr(when (kind) {
        DiscoveryKind.secret -> R.string.sealKindSecret
        DiscoveryKind.riddle -> R.string.sealKindRiddle
        DiscoveryKind.milestone -> R.string.sealKindMilestone
    })
    fun sealAccessibility(kind: DiscoveryKind): String = tr(R.string.sealAccessibility, sealKind(kind))
    fun journalFindsSection(count: Int): String = tr(R.string.journalFindsSectionHead).let {
        if (count > 0) "$it · ${groupedNumber(count)}" else it
    }
    fun cardFoundBy(count: Int, name: String): String = tr(R.string.cardFoundBy, "${groupedNumber(count)} ${nounPeople(count)}", name)
    fun posterCaption(distance: String, seals: Int): String =
        "${myMapTitle()} · ${tr(R.string.posterCaption, distance)}" + if (seals > 0) " · ${mapSealsCount(seals)}" else ""
    fun mapRegionSince(date: Instant): String = tr(R.string.mapRegionSince, "${genitiveMonthFormatter.format(date)} ${date.atZone(zone).year}")
    fun mapKmDriven(unit: DistanceUnit): String = tr(R.string.logic_mapKmDriven, unitDistanceShort(unit, 100.0, 0))
    fun mapCitiesOfTotal(opened: Int, total: Int): String = "$opened ${ofWord()} $total"
    fun decimalSeparator(): String = UnitNumber.decimalSeparator(language.code)
    fun mapTripsSection(count: Int): String = "${tr(R.string.mapTripsSectionHead)} · $count"
    fun mapRoadTrips(count: Int): String = tr(R.string.logic_mapRoadTrips, "$count ${nounTrips(count)}")
    fun monthYear(date: Instant): String = monthYearFormatter.format(date)
    fun groupedNumber(value: Int): String = UnitNumber.grouped(value.toLong(), language.code)
    fun regionsGenitive(count: Int): String = nounRegions(count)
    fun citiesGenitive(count: Int): String = nounCities(count)
    fun tripsGenitive(count: Int): String = nounTrips(count)
    fun publishedTripsSignOutMessage(count: Int): String = tr(
        R.string.logic_publishedTripsSignOutMessage_head, count,
        plural(R.plurals.logic_publishedTripsSignOutMessage_noun, count),
        plural(R.plurals.logic_publishedTripsSignOutMessage_pron, count),
    ) + "\n\n" + tr(R.string.logic_publishedTripsSignOutMessage_tail)
    fun exploredPercent(percent: String, place: String): String = tr(R.string.logic_exploredPercent, percent, place)
    fun gpsAccuracyBody(current: String): String {
        // iOS uses Latin m for every language except Russian in this diagnostic paragraph.
        val unit = withLanguage(if (language == AppLanguage.ru) language else AppLanguage.en).unitMeters()
        return "${tr(R.string.gpsAccuracyCurrent)}: $current\n\n🟢 ≤10$unit — ${tr(R.string.gpsAccuracyGood)}\n🟠 >10$unit — ${tr(R.string.gpsAccuracyFair)}\n\n${tr(R.string.gpsAccuracyNote)}"
    }
    fun movingDot(value: String): String = "${statMoving()} · $value"
    fun stopsDot(value: String): String = "${statStops()} · $value"
    fun reactionsTitleN(n: Int): String = "${chipReactions()} · $n"
    fun chartMaxElev(max: String, gain: String): String = "${shortMax()} $max · ↑ $gain"
    fun chartMaxAvg(max: String, avg: String): String = "${shortMax()} $max · ${shortAvg()} $avg"
    fun commentsTitleN(n: Int): String = "${comments()} · $n"
    fun discussionSeeAllPill(n: Int): String = "${tr(R.string.discussionSeeAllPillHead)} · $n"
    fun relTimeMinutes(n: Int): String = "$n ${minutesUnitShort()}"
    fun relTimeHours(n: Int): String = "$n ${hoursUnitShort()}"
    fun relTimeDays(n: Int): String = "$n ${tr(R.string.daysUnitShort)}"
    fun autoStopMinutes(minutes: Int): String = "$minutes ${minutesUnitShort()}"
    fun notifTripStartBody(deviceName: String): String = tr(R.string.logic_notifTripStartBody, deviceName)
    fun checkpointDefaultName(number: Int): String = "${checkpointWord()} $number"
    fun segmentHistory(count: Int, times: String): String = tr(R.string.segmentHistory, "$count ${nounTimes(count)}", times)
    fun journeyDay(n: Int): String = tr(R.string.journeyDay, n)
    fun journeyDays(from: Int, to: Int): String = tr(R.string.journeyDays, from, to)
    fun journeyAroundTownCount(count: Int): String = tr(R.string.journeyAroundTownCount, count)
    fun journeyPublishAlreadyPublic(count: Int): String = tr(R.string.journeyPublishAlreadyPublic, "$count ${nounTrips(count)}")
    fun journeyPublishButton(count: Int): String = tr(R.string.journeyPublishButton, "$count ${nounTrips(count)}")

    // iOS: AppStrings.swift, TripStopReason (copy-only reason, not a stored recording state).
    enum class TripStopReason { bluetooth, inactivity }
    fun notifTripStopBody(minutes: Int?, reason: TripStopReason): String {
        val cause = tr(if (reason == TripStopReason.bluetooth) R.string.notifStopCauseBluetooth else R.string.notifStopCauseInactivity)
        val tail = if (minutes == null) tr(R.string.logic_notifTripStopBody_ask)
        else tr(R.string.logic_notifTripStopBody_tail, "$minutes ${minutesUnitShort()}")
        return "$cause $tail"
    }
    fun badgeLastEarned(date: Instant): String = tr(R.string.logic_badgeLastEarned, longDate(date))
    fun longDate(date: Instant): String = longDateFormatter.format(date)
    fun badgeEarnedOn(date: Instant, tripTitle: String?): String =
        "${tr(R.string.badgeEarnedOnPrefix)} ${longDate(date)}" + if (!tripTitle.isNullOrEmpty()) " · $tripTitle" else ""
    fun chartDistanceMark(unit: DistanceUnit, km: Double): String =
        if (unit == DistanceUnit.km) chartKmMark(km) else chartMileMark(km * 1000 / DistanceUnit.metresPerMile)
    fun chartMileMark(miles: Double): String {
        val n = rounded(miles).toLong().coerceAtLeast(0)
        return tr(R.string.logic_chartMileMark, n, ordinalFeminine(n))
    }
    fun ukrainianOrdinalFeminine(n: Int): String = ordinalFeminine(n.toLong())

    private fun ordinalFeminine(n: Long): String {
        val value = abs(n)
        return tr(when {
            value % 100 in 11L..19L -> R.string.logic_ordinal_0
            value % 10 == 1L -> R.string.logic_ordinal_1
            value % 10 == 2L -> R.string.logic_ordinal_2
            value % 10 == 3L -> R.string.logic_ordinal_3
            value % 10 in 7L..8L -> R.string.logic_ordinal_4
            value % 10 == 0L && value % 100 == 40L -> R.string.logic_ordinal_5
            value != 0L && value % 1000 == 0L -> R.string.logic_ordinal_6
            value == 0L -> R.string.logic_ordinal_5
            else -> R.string.logic_ordinal_0
        })
    }
    fun chartKmMark(km: Double): String = tr(R.string.logic_chartKmMark, rounded(km).toLong().coerceAtLeast(0))
    fun notifAutoStopSummary(distance: String, time: String): String = "$distance · $time"
    fun draftsAllMine(count: Int): String = tr(R.string.draftsAllMine, formattedCount(count))
    fun draftsMineCount(count: Int): String = tr(R.string.draftsMineCount, formattedCount(count))
    fun draftsSelectedCount(count: Int): String = tr(R.string.draftsSelectedCount, formattedCount(count))
    fun draftsDeleteTitle(count: Int): String = if (count <= 1) tr(R.string.draftsDeleteOne)
        else tr(R.string.draftsDeleteMany, formattedCount(count), nounDrafts(count))
    fun deleteAccountTitle(): String = deleteAccount()
    fun syncAttemptsLabel(count: Int): String = "$count ${plural(R.plurals.logic_syncAttemptsLabel_word, count)}"
    fun signInLegalMarkdown(termsURL: String): String = tr(R.string.logic_signInLegalMarkdown, "[${tr(R.string.signInLegalTermsWord)}]($termsURL)")
    fun entityLabel(type: String): String = when (type) {
        "trip" -> tripTitle()
        "vehicle" -> entityVehicle()
        "photo" -> photoShort()
        "vehiclePhoto" -> "${entityVehicle()} · ${photoShort()}"
        "journey" -> journeyWord()
        "settings" -> settingsTitle()
        else -> titlecased(type)
    }
    fun actionLabel(action: String): String = when (action) {
        "upload" -> tr(R.string.actionUpload)
        "update" -> tr(R.string.actionUpdate)
        "delete" -> tr(R.string.actionDelete)
        "unpublish" -> tr(R.string.actionUnpublish)
        else -> titlecased(action)
    }
    fun vehicleSoldState(`when`: String): String = tr(R.string.logic_vehicleSoldState, `when`)
    fun publicAboutBy(name: String): String = tr(R.string.logic_publicAboutBy, name)
    fun outOf(have: Int, all: Int): String = tr(R.string.logic_outOf, have, all)
    fun avatarColorName(color: String): String = when (color) {
        "white" -> tr(R.string.avatarColor_white)
        "black" -> tr(R.string.avatarColor_black)
        "silver" -> tr(R.string.avatarColor_silver)
        "gray" -> tr(R.string.avatarColor_gray)
        "red" -> tr(R.string.avatarColor_red)
        "blue" -> tr(R.string.avatarColor_blue)
        "orange" -> tr(R.string.avatarColor_orange)
        "green" -> tr(R.string.avatarColor_green)
        else -> color
    }
    fun avatarStyleName(style: String): String = when (style) {
        "car" -> tr(R.string.avatarStyle_car)
        "hatchback" -> tr(R.string.avatarStyle_hatchback)
        "crossover" -> tr(R.string.avatarStyle_crossover)
        "pickup" -> tr(R.string.avatarStyle_pickup)
        "van" -> tr(R.string.avatarStyle_van)
        "convertible" -> tr(R.string.avatarStyle_convertible)
        "sports" -> tr(R.string.avatarStyle_sports)
        "motorcycle" -> tr(R.string.avatarStyle_motorcycle)
        "scooter" -> tr(R.string.avatarStyle_scooter)
        "bicycle" -> tr(R.string.avatarStyle_bicycle)
        else -> style
    }
    fun inGarageSince(`when`: String): String = tr(R.string.logic_inGarageSince, `when`)
    fun sinceYear(year: Int): String = tr(R.string.logic_sinceYear, year)
    fun fuelPricePerUnit(unit: String): String = tr(R.string.logic_fuelPricePerUnit, unit)
    fun vehicleLevelToNext(distance: String, level: Int): String = tr(R.string.logic_vehicleLevelToNext, distance, level)
    fun vehicleLevelAndAbove(level: Int): String = tr(R.string.logic_vehicleLevelAndAbove, level)
    fun activityReactedTo(trip: String): String = tr(R.string.logic_activityReactedTo, trip)
    fun activityCommented(text: String?, trip: String): String = if (!text.isNullOrEmpty()) tr(R.string.logic_activityCommented_text, text)
        else tr(R.string.logic_activityCommented_trip, trip)
    fun activityAchievementUnlocked(badge: String): String = tr(R.string.logic_activityAchievementUnlocked, badge)
    fun syncUpdatedAgo(relative: String): String = tr(R.string.logic_syncUpdatedAgo, relative)
    fun syncOffPublishing(count: Int): String = "${syncStateOff()} · ${tr(R.string.syncPublishingWord)}: $count"
    fun syncingProgress(done: Int, total: Int): String = "${syncingNow()} $done/$total"
    fun syncQueuedCount(count: Int): String = "${syncStatusPendingHeader()}: $count"
    fun syncLastAt(relative: String): String = "${tr(R.string.syncLastAtHead)}: $relative"
    fun syncFailedCount(count: Int): String = "${tr(R.string.syncFailedCountHead)}: $count"
    fun blockedSince(date: String): String = tr(R.string.logic_blockedSince, date)
    fun achievementsProgress(unlocked: Int, total: Int): String = "$unlocked ${ofWord()} $total"
    fun calendarFilterActive(count: Int): String = tripsCount(count)
    fun statsKmTotal(unit: DistanceUnit): String = tr(R.string.logic_statsKmTotal, unitDistanceShort(unit, 100.0, 0))
    fun statsKmByMonth(unit: DistanceUnit): String = tr(if (unit == DistanceUnit.miles) R.string.logic_statsKmByMonth_miles else R.string.logic_statsKmByMonth_km)
    fun daysCount(n: Int): String = "$n ${nounDays(n)}"
    fun tripsCount(n: Int): String = "$n ${nounTrips(n)}"
    fun nameHelper(max: Int): String = tr(R.string.logic_nameHelper, max)
    fun followersCount(n: Int): String = "$n ${followersCaption(n)}"
    fun followingCountLabel(n: Int): String = "$n ${followingCaption(n)}"
    fun streakDaysInARow(n: Int): String = tr(R.string.logic_streakDaysInARow, n, daysCount(n))
    fun repeatRouteTimes(n: Int): String = tr(R.string.logic_repeatRouteTimes, n)
    fun commentReplyingTo(name: String): String = tr(R.string.logic_commentReplyingTo, name)
    fun blockProfileAction(isBlocked: Boolean): String = if (isBlocked) blockProfileUnblock() else blockProfileBlock()
    fun blockProfileConfirmTitle(isBlocked: Boolean): String = tr(if (isBlocked) R.string.blockProfileConfirmTitleUnblock else R.string.blockProfileConfirmTitleBlock)
    fun blockProfileConfirmBody(isBlocked: Boolean): String = tr(if (isBlocked) R.string.blockProfileConfirmBodyUnblock else R.string.blockProfileConfirmBodyBlock)
    fun companionsPendingSuffix(count: Int): String = tr(if (count == 1) R.string.logic_companionsPendingSuffix_one else R.string.logic_companionsPendingSuffix_many, count)
    fun companionsAndMore(count: Int): String = tr(R.string.logic_companionsAndMore, count)
    fun photosAdded(count: Int): String = tr(R.string.logic_photosAdded, photosCount(count))
    fun companionPhotoUploadPartial(succeeded: Int, total: Int): String = tr(R.string.logic_companionPhotoUploadPartial, "$succeeded ${ofWord()} $total ${nounPhotos(total)}")
    fun achievementsTitle(): String = achievementsSection()
    fun achievementsOpenedOf(unlocked: Int, total: Int): String = "${achievementsProgress(unlocked, total)} ${unlocked()}"
    fun achievementsEarnedBy(percent: String): String = tr(R.string.logic_achievementsEarnedBy, percent)
    fun achievementsRarityCount(rarity: BadgeRarity, count: Int): String {
        val word = tr(if (language == AppLanguage.ru) when (rarity) {
            BadgeRarity.common -> R.string.logic_rarityCount_common
            BadgeRarity.uncommon -> R.string.logic_rarityCount_uncommon
            BadgeRarity.rare -> R.string.logic_rarityCount_rare
            BadgeRarity.epic -> R.string.logic_rarityCount_epic
            BadgeRarity.legendary -> R.string.logic_rarityCount_legendary
            BadgeRarity.exclusive -> R.string.logic_rarityCount_exclusive
        } else when (rarity) {
            BadgeRarity.common -> R.string.badgeRarity_common
            BadgeRarity.uncommon -> R.string.badgeRarity_uncommon
            BadgeRarity.rare -> R.string.badgeRarity_rare
            BadgeRarity.epic -> R.string.badgeRarity_epic
            BadgeRarity.legendary -> R.string.badgeRarity_legendary
            BadgeRarity.exclusive -> R.string.badgeRarity_exclusive
        })
        return "$word $count"
    }
    fun achievementLockedProgress(current: String, target: String, unit: String): String = "$current / $target $unit"
    fun myProfileRowName(): String = nameEditorTitle()
    fun myProfileRowUsername(): String = usernameTitle()
    fun myProfileRowAbout(): String = aboutTitle()
    fun myProfileAvatarRetired(emoji: String): String = tr(R.string.logic_myProfileAvatarRetired, emoji)
    fun myProfileRowStats(): String = stats()
    fun myProfileStatsSummary(trips: Int, distance: String): String = "${tripsCount(trips)} · $distance"
    fun levelsRankOf(rank: Int, total: Int, level: Int): String = "${tr(R.string.levelsRankWord)} $rank ${ofWord()} $total · ${tr(R.string.levelsLevelWord)} $level"
    fun levelsXpProgress(current: String, target: String): String = "$current / $target XP"
    fun levelsToNextLevel(level: Int): String = tr(R.string.logic_levelsToNextLevel, level)
    fun levelsNextRank(rank: String, level: Int): String = tr(R.string.logic_levelsNextRank, "$rank · ${levelsRankRangePrefix()} $level")
    fun levelsRankRange(from: Int, to: Int?): String = "${levelsRankRangePrefix()} $from" + if (to == null) "+" else "–$to"
    fun levelsXpTotal(xp: String): String = tr(R.string.logic_levelsXpTotal, xp)
    fun statsRangeYear(): String = year()
    fun statsRangeAll(): String = total()
    fun statsVsPrevious(more: Boolean, year: Boolean, previous: String): String = tr(when {
        more && year -> R.string.logic_statsVsPrevious_more_year
        more -> R.string.logic_statsVsPrevious_more_month
        year -> R.string.logic_statsVsPrevious_less_year
        else -> R.string.logic_statsVsPrevious_less_month
    }, previous)
    fun statsNewPlacesLine(period: String, roads: Int, firstPlace: String?): String {
        val noun = plural(R.plurals.logic_statsNewPlacesLine_noun, roads)
        val head = tr(R.string.logic_statsNewPlacesLine_head, period, roads, noun)
        return if (firstPlace == null) head else tr(R.string.logic_statsNewPlacesLine, head, firstPlace)
    }
    fun statsRecordLongest(): String = recordLongest()
    fun statsRecordBestDay(unit: DistanceUnit): String {
        val u = unitDistanceShort(unit, 100.0, 0)
        return tr(if (unit == DistanceUnit.miles) R.string.logic_statsRecordBestDay_miles else R.string.logic_statsRecordBestDay_km, u, capitalizedFirst(u))
    }
    fun capitalizedFirst(s: String): String {
        if (s.isEmpty()) return s
        val firstEnd = s.offsetByCodePoints(0, 1)
        return s.substring(0, firstEnd).uppercased(language) + s.substring(firstEnd)
    }
    fun photosCount(n: Int): String = "$n ${nounPhotos(n)}"
    fun timesCount(n: Int): String = "$n ${nounTimes(n)}"
    fun statsWithTripTrackSince(date: String): String = tr(R.string.logic_statsWithTripTrackSince, date)
    fun statsMemoriesLine(distance: String, trips: Int): String = tr(R.string.logic_statsMemoriesLine,
        distance, tripsCount(trips), trips, plural(R.plurals.logic_statsMemoriesLine_noun, trips))
    fun contentFilterTooLong(max: Int): String = tr(R.string.logic_contentFilterTooLong, max)
    fun relTimeMinutesAgo(n: Int): String = tr(R.string.logic_relTimeMinutesAgo, relTimeMinutes(n))
    fun relTimeTodayAt(time: String): String = tr(R.string.logic_relTimeTodayAt, time)
    fun relTimeYesterdayAt(time: String): String = tr(R.string.logic_relTimeYesterdayAt, time)
    fun publicProfileTripsTotal(total: Int): String = tr(R.string.logic_publicProfileTripsTotal, total)
    fun relTimeDaysAgo(n: Int): String = tr(R.string.logic_relTimeDaysAgo, daysCount(n))
    fun riddleLine(type: RiddleType): String = tr(when (type) {
        RiddleType.pass -> R.string.riddleLine_pass
        RiddleType.lighthouse -> R.string.riddleLine_lighthouse
        RiddleType.border -> R.string.riddleLine_border
        RiddleType.ferry -> R.string.riddleLine_ferry
        RiddleType.dam -> R.string.riddleLine_dam
        RiddleType.bridge -> R.string.riddleLine_bridge
        RiddleType.viewpoint -> R.string.riddleLine_viewpoint
        RiddleType.observatory -> R.string.riddleLine_observatory
        RiddleType.seaRoad -> R.string.riddleLine_seaRoad
        RiddleType.extreme -> R.string.riddleLine_extreme
        RiddleType.centre -> R.string.riddleLine_centre
        RiddleType.tripoint -> R.string.riddleLine_tripoint
    })
    fun milestoneTitle(milestone: Milestone): String = tr(when (milestone) {
        Milestone.firstRegion -> R.string.milestoneTitle_firstRegion
        Milestone.easternmost -> R.string.milestoneTitle_easternmost
        Milestone.westernmost -> R.string.milestoneTitle_westernmost
        Milestone.northernmost -> R.string.milestoneTitle_northernmost
        Milestone.southernmost -> R.string.milestoneTitle_southernmost
        Milestone.above2000 -> R.string.milestoneTitle_above2000
        Milestone.belowSea -> R.string.milestoneTitle_belowSea
        Milestone.threeRegionsDay -> R.string.milestoneTitle_threeRegionsDay
        Milestone.countryBorder -> R.string.milestoneTitle_countryBorder
        Milestone.nightPass -> R.string.milestoneTitle_nightPass
    })
    fun findRarityLabel(raw: String): String? = when (raw) {
        "few" -> tr(R.string.findRarityFew)
        "tens" -> tr(R.string.findRarityTens)
        "hundreds" -> tr(R.string.findRarityHundreds)
        "many" -> tr(R.string.findRarityMany)
        else -> null
    }
    fun proManageTrialRenews(date: String, price: String, yearly: Boolean): String = tr(if (yearly) R.string.proManageTrialRenewsYear else R.string.proManageTrialRenewsMonth, date, price)
    fun proManageRenews(date: String, price: String, yearly: Boolean): String = tr(if (yearly) R.string.proManageRenewsYear else R.string.proManageRenewsMonth, date, price)
    fun garageTransmission(kind: String): String = when (kind) {
        "manual" -> tr(R.string.garageTransmissionManual)
        "automatic" -> tr(R.string.garageTransmissionAutomatic)
        "cvt" -> tr(R.string.garageTransmissionCvt)
        "amt" -> tr(R.string.garageTransmissionAmt)
        "dct" -> tr(R.string.garageTransmissionDct)
        else -> ""
    }
    fun garageGearbox(kind: String, gears: Int?): String = if (gears == null) garageTransmission(kind)
        else tr(R.string.garageGearbox, garageTransmission(kind), gears)
    fun atlasCitiesOutOf(visited: Int, total: Int): String = tr(R.string.atlasCitiesOutOf, formattedCount(visited), formattedCount(total), nounCities(total))
    fun atlasMoreInFog(count: Int): String = tr(R.string.atlasMoreInFog, formattedCount(count))
    fun live_distanceShort(unit: DistanceUnit, value: Double, fractionDigits: Int): String = unitDistanceShort(unit, value, fractionDigits)
    fun live_speedShort(unit: DistanceUnit): String = unitSpeedShort(unit)

    override fun word(key: UnitWord, lang: String): String = withLanguage(AppLanguage.fromCode(lang)).tr(when (key) {
        UnitWord.kilometre -> R.string.km
        UnitWord.kilometresPerHour -> R.string.kmh
        UnitWord.metre -> R.string.unitMeters
        UnitWord.mileOne -> R.string.logic_unitDistanceShort_one
        UnitWord.mileFew -> R.string.logic_unitDistanceShort_few
        UnitWord.mileMany -> R.string.logic_unitDistanceShort_many
        UnitWord.footOne -> R.string.logic_unitElevationShort_one
        UnitWord.footFew -> R.string.logic_unitElevationShort_few
        UnitWord.footMany -> R.string.logic_unitElevationShort_many
        UnitWord.footFraction -> R.string.logic_unitElevationShort_fraction
        UnitWord.milesPerHour -> R.string.logic_unitSpeedShort
        UnitWord.litre -> R.string.unitLitresShort
        UnitWord.kWh -> R.string.unitKWhShort
        UnitWord.kilometresFull -> R.string.unitKilometersFull
        UnitWord.milesFull -> R.string.unitMilesFull
        UnitWord.dashboardApp -> R.string.dashboardUnitsApp
        UnitWord.dashboardMetricFuel -> R.string.dashboardUnitsMetricFuel
        UnitWord.dashboardImperialFuel -> R.string.dashboardUnitsImperialFuel
    })

    // Foundation's bare capitalized uses Unicode default casing for unknown diagnostic raw values.
    private fun titlecased(value: String): String = android.icu.lang.UCharacter.toTitleCase(java.util.Locale.ROOT, value, null)
    private fun rounded(value: Double): Double {
        val magnitude = abs(value)
        val integral = floor(magnitude)
        // Adding 0.5 before floor can advance an already integral Double above 2^52.
        return Math.copySign(if (magnitude - integral >= 0.5) integral + 1 else integral, value)
    }
}
