package com.onezee.triptrack.sync

import com.onezee.triptrack.network.*
import java.time.Instant
import java.util.UUID

// iOS: R2PhotoStorage.swift (PhotoType)
enum class PhotoType { thumbnail, original }

// iOS: R2PhotoStorage.swift. Input is pixel-only JPEG prepared by PhotoEncoder.
class R2PhotoStorage(private val client: ApiClient, private val downloadBytes: suspend (String) -> ByteArray = client::getBytes) {
    suspend fun uploadPhotoPart(tripId: UUID, photoId: UUID, type: PhotoType, data: ByteArray, caption: String?, timestamp: Instant): PhotoUploadResponse =
        client.uploadMultipart(ApiEndpoint.photoUpload, TripPhotoUploadRequest(tripId, photoId, type.name, timestamp, caption).fields(), file(data))

    suspend fun uploadVehiclePhotoPart(vehicleId: UUID, photoId: UUID, type: PhotoType, data: ByteArray, isMain: Boolean, takenAt: Instant): PhotoUploadResponse =
        client.uploadMultipart(ApiEndpoint.vehiclePhotoUpload, VehiclePhotoUploadRequest(vehicleId, photoId, type.name, isMain, takenAt).fields(), file(data))

    suspend fun download(photoId: UUID, type: PhotoType): ByteArray {
        val response: PhotoURLResponse = client.post(ApiEndpoint.photoURL, PhotoURLRequest(photoId, type.name))
        return downloadBytes(response.url)
    }

    private fun file(data: ByteArray) = MultipartFile("file", "photo.jpg", "image/jpeg", data)
}
