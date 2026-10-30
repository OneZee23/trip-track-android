// iOS: Services/ProductAnalytics.swift
@file:UseSerializers(UUIDSerializer::class, IsoInstantSerializer::class)

package com.onezee.triptrack.network

import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.serialization.IsoInstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: Services/ProductAnalytics.swift
@Serializable
data class ProductAnalyticsSnapshot(
    val schema: Int,
    val token: String,
    val revision: Int,
    val cohortDay: String,
    val observedDay: String,
    val hadTrips: Boolean,
    val starts: Int,
    val saves: Int,
    val saveFailures: Int,
    val atlasOpens: Int,
    val fastSaves: Int,
    val mediumSaves: Int,
    val slowSaves: Int,
    val firstSaveDay: String? = null,
    val secondSaveDay: String? = null,
    val proOffers: Int? = null,
    val proBuyTaps: Int? = null,
    val proPurchases: Int? = null,
)

// iOS: Services/ProductAnalytics.swift
@Serializable
data class UsageEraseRequest(
    val token: String,
)

