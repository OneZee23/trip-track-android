// iOS: Trip.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class, Base64Serializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.Base64Serializer
import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.TripSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: Trip.swift
@KeepGeneratedSerializer
@Serializable(with = TripSerializer::class)
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class Trip(
    @Required
    val id: UUID = UUID.randomUUID(),
    @Required
    var startDate: Instant = Instant.now(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var endDate: Instant? = null,
    // Metres, independent of display units.
    @Required
    var distance: Double = 0.0,
    // Metres per second.
    @Required
    var maxSpeed: Double = 0.0,
    // Metres per second.
    @Required
    var averageSpeed: Double = 0.0,
    @Required
    var trackPoints: List<TrackPoint> = emptyList(),
    @Required
    var photos: List<TripPhoto> = emptyList(),
    @EncodeDefault
    var checkpoints: List<TripCheckpoint> = emptyList(),
    @EncodeDefault
    var segments: List<TripSegment> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var title: String? = null,
    @EncodeDefault
    var titleIsCustom: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var tripDescription: String? = null,
    @Required
    var fuelUsed: Double = 0.0,
    // Metres.
    @Required
    var elevation: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var region: String? = null,
    @Required
    var isPrivate: Boolean = true,
    @EncodeDefault
    var isTransfer: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var vehicleId: UUID? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var fuelCurrency: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var previewPolyline: ByteArray? = null,
    @Required
    var earnedBadgeIds: List<String> = emptyList(),
    @Required
    var xpEarned: Int = 0,
    @EncodeDefault
    var companions: List<TripCompanion> = emptyList(),
    @EncodeDefault
    var isOnServer: Boolean = false,
    @EncodeDefault
    var source: TripOrigin = TripOrigin.recorded,
    @EncodeDefault
    var confirmation: TripConfirmation = TripConfirmation.confirmed,
    @EncodeDefault
    var roadFillState: RoadFillState = RoadFillState.unchecked,
    @EncodeDefault
    var energyMode: TripEnergyMode = TripEnergyMode.auto,
    @EncodeDefault
    var recordingBreaks: List<Instant> = emptyList(),
) {
    companion object {}
}

// iOS: Trip.swift
object TripJunkClassifier {

    // iOS: Trip.swift
    enum class Reason {
        tooShort,
        noDrivingSpeed,
    }
}
