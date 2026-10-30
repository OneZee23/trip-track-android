// iOS: PlusPaywallModel.swift
package com.onezee.triptrack.model

// iOS: PlusPaywallModel.swift
data class PlusProductInfo(
    val id: String,
    val displayPrice: String,
    val period: Period,
    val trialDays: Int? = null,
    val price: java.math.BigDecimal? = null,
    val format: ((java.math.BigDecimal) -> String)? = null,
) {

    // iOS: PlusPaywallModel.swift
    enum class Period(val rawValue: String) {
        yearly("yearly"),
        monthly("monthly");

        companion object {
            fun fromRawValue(value: String): Period? = entries.find { it.rawValue == value }
        }
    }
}

// iOS: PlusPaywallModel.swift
data class PlusPlan(
    val id: String,
    val period: PlusProductInfo.Period,
    val title: String,
    val caption: String? = null,
    val captionIsAccent: Boolean,
    val savingPercent: Int? = null,
    val displayPrice: String,
    val isDefault: Boolean,
    val hasFreeWeek: Boolean,
)
