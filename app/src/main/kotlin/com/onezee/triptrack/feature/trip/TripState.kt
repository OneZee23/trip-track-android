package com.onezee.triptrack.feature.trip

import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

// iOS: TripDetailView.swift (openingPreview), Trip.swift
data class TripSummary(
    val id: UUID, val startDate: Instant, val endDate: Instant?, val title: String?,
    val description: String?, val region: String?, val distance: Double, val duration: Double,
    val maxSpeed: Double, val averageSpeed: Double, val vehicleId: UUID?, val isPrivate: Boolean,
    val isOnServer: Boolean, val source: TripOrigin, val confirmation: TripConfirmation,
    val energyMode: TripEnergyMode, val coordinates: List<GeoPoint>, val photos: List<PhotoState>,
    val badgeIds: List<String>, val isTransfer: Boolean,
) {
    companion object {
        fun from(trip: Trip) = TripSummary(trip.id, trip.startDate, trip.endDate, trip.title,
            trip.tripDescription, trip.region, trip.distance, trip.duration, trip.maxSpeed, trip.averageSpeed,
            trip.vehicleId, trip.isPrivate, trip.isOnServer, trip.source, trip.confirmation, trip.energyMode,
            trip.previewCoordinates.toList(), trip.photos.map(PhotoState::from), trip.earnedBadgeIds.toList(), trip.isTransfer)
    }
}

// iOS: TripPhoto.swift
data class PhotoState(val id: UUID, val filename: String, val caption: String?, val timestamp: Instant) {
    companion object { fun from(photo: TripPhoto) = PhotoState(photo.id, photo.filename, photo.caption, photo.timestamp) }
}

// iOS: TripCheckpoint.swift, TripDetailView.swift (checkpointMarkers)
data class CheckpointState(
    val id: UUID, val timestamp: Instant, val coordinate: GeoPoint?, val name: String?,
    val number: Int, val metres: Double, val elapsed: Double, val photos: List<PhotoState>, val placeId: UUID?,
)

// iOS: TripMoment.swift
data class MomentState(val id: UUID, val checkpointId: UUID?, val number: Int?, val elapsed: Double,
    val metres: Double, val photos: List<PhotoState>)

// iOS: TripSegment.swift, SegmentHistory.swift
data class SegmentState(val id: UUID, val fromId: UUID, val toId: UUID, val name: String?,
    val elapsed: Double?, val metres: Double?, val history: List<Double>)

// iOS: AppDialog.swift (appConfirm). The UI resolves these AppStrings keys through generated resources.
data class HouseConfirmation(val id: UUID = UUID.randomUUID(), val titleKey: String, val messageKey: String?,
    val destructiveKey: String, val itemIds: List<UUID>, val journeyTitle: String? = null)

// iOS: SignInPromptSheet.swift, ToastItem.swift
enum class TripMessage { signInToPublish, loadFailed, actionFailed, exportFailed, commentPostFailed,
    commentDeleteFailed, commentRateLimited, commentTooLong, inappropriateLanguage, journeyOverlaps, journeyEmpty }

// iOS: TripManager.swift (currentTrip). The recording service implements this without a location dependency.
interface CurrentRecordingSource { val currentRecording: Flow<TripSummary?> }

// iOS: TripDetailView.swift (performExport)
enum class TripExportFormat(val extension: String) { gpx("gpx"), csv("csv") }

// iOS: TripDetailView.swift (activityItem). A platform adapter writes/shares this content.
data class TripExport(val filename: String, val format: TripExportFormat, val content: String)
