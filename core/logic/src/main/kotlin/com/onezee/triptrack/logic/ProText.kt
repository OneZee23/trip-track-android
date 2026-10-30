package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant

// iOS: AppStrings.swift, PRO strings. The app binds these keys to generated resources.
interface ProWords {
    fun text(key: ProText, lang: String): String
}

// iOS: AppStrings.swift
// Names match the iOS resource keys; date and price tokens are part of those templates.
enum class ProText {
    meProNone, meProNoneSub, meProActiveSub, meProTrialSub, meProExpired, meProExpiredSub,
    meProGraceSub, meProPendingSub, proManageTrialRenewsYear, proManageTrialRenewsMonth,
    proManageRenewsYear, proManageRenewsMonth, proManageEndsOn, proManageUntil,
}

// iOS: ProStatus.swift. Date formatting belongs to the caller's language/time-zone adapter.
fun ProStatus.rowTitle(lang: String, words: ProWords, formatDate: (Instant) -> String): String = when (this) {
    is ProStatus.Expired -> words.text(ProText.meProExpired, lang).replace("{date}", on?.let(formatDate) ?: "")
        .split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    ProStatus.HiddenStorefront -> ""
    else -> words.text(ProText.meProNone, lang)
}
fun ProStatus.rowSubtitle(lang: String, words: ProWords, formatDate: (Instant) -> String): String {
    fun dated(key: ProText, date: Instant?): String = if (date == null) words.text(ProText.meProNoneSub, lang)
        else words.text(key, lang).replace("{date}", formatDate(date))
    return when (this) {
        ProStatus.None -> words.text(ProText.meProNoneSub, lang)
        is ProStatus.Trial -> dated(ProText.meProTrialSub, until)
        is ProStatus.Active -> dated(ProText.meProActiveSub, until)
        is ProStatus.ProWithoutStore -> dated(ProText.meProActiveSub, until)
        is ProStatus.Expired -> words.text(ProText.meProExpiredSub, lang)
        ProStatus.Grace -> words.text(ProText.meProGraceSub, lang)
        ProStatus.Pending -> words.text(ProText.meProPendingSub, lang)
        ProStatus.HiddenStorefront -> ""
    }
}

// iOS: ProManageModel.swift. Prices remain verbatim storefront strings.
fun ProManageLine.text(lang: String, words: ProWords, formatDate: (Instant) -> String): String? {
    fun filled(key: ProText, date: Instant, price: String? = null): String {
        val dated = words.text(key, lang).replace("{date}", formatDate(date))
        return if (price != null) dated.replace("{price}", price) else dated
    }
    return when (this) {
        is ProManageLine.TrialRenews -> filled(if (period == PlusProductInfo.Period.yearly) ProText.proManageTrialRenewsYear
            else ProText.proManageTrialRenewsMonth, until, price)
        is ProManageLine.Renews -> filled(if (period == PlusProductInfo.Period.yearly) ProText.proManageRenewsYear
            else ProText.proManageRenewsMonth, on, price)
        is ProManageLine.EndsOn -> filled(ProText.proManageEndsOn, value)
        is ProManageLine.Until -> filled(ProText.proManageUntil, value)
        ProManageLine.BillingProblem -> words.text(ProText.meProGraceSub, lang)
        ProManageLine.Unknown -> null
    }
}
