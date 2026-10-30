// iOS: Services/Social/GroupsWaitlistStore.swift
@file:UseSerializers(UUIDSerializer::class, IsoInstantSerializer::class)

package com.onezee.triptrack.network

import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.serialization.IsoInstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: Services/Social/GroupsWaitlistStore.swift
@Serializable
data class GroupsWaitlistRequest(
    val deviceId: UUID,
    val clubKey: String? = null,
)

// iOS: Services/Social/GroupsWaitlistStore.swift
@Serializable
data class GroupsWaitlistStatusRequest(
    val deviceId: UUID,
)

// iOS: Services/Social/GroupsWaitlistStore.swift
@Serializable
data class GroupsWaitlistState(
    val total: Int,
    val clubs: Map<String,Int>,
    val joined: Boolean,
    val joinedClubs: List<String>,
)

