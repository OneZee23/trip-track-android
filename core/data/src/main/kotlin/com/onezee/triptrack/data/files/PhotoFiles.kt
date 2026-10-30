// iOS: PhotoStorageService.swift (the file half), VehiclePhotoStore.swift (directory, add, delete)
package com.onezee.triptrack.data.files

import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * JPEG files of trip photos. The database row says a photo exists; only this says whether the
 * bytes are on THIS phone: a pull rebuilds rows without files, and a restored backup has the store
 * but not the photo directory.
 *
 * Layout as on iOS: `TripPhotos/<TRIP-ID>/<PHOTO-FILE-ID>.jpg`, and the row keeps the relative name
 * `<TRIP-ID>/<PHOTO-FILE-ID>.jpg`. The name also travels to the server as the photo's `filename`.
 *
 * The directory must be excluded from Android backup (the app's backup rules), for the reason iOS
 * marks it `isExcludedFromBackup`: data stays on the device unless Cloud Sync is on.
 */
interface TripPhotoFiles {
    /** Writes the encoded JPEG; returns the relative filename, or null when nothing was written. */
    fun save(jpeg: ByteArray, tripId: UUID): String?
    fun exists(filename: String): Boolean
    fun read(filename: String): ByteArray?
    fun delete(filename: String)
    /** Every file of one trip: a cascade removes the rows, never the bytes. */
    fun deleteAll(tripId: UUID)
    fun wipe()

    companion object {
        /** iOS: `image.jpegData(compressionQuality: 0.8)`. The encoder in the app uses it. */
        const val JPEG_QUALITY = 80
    }
}

/** JPEG files of car photos: `VehiclePhotos/<FILE-ID>.jpg`. They belong to the car, not a trip. */
interface VehiclePhotoFiles {
    fun save(jpeg: ByteArray): String?
    fun exists(filename: String): Boolean
    fun read(filename: String): ByteArray?
    fun delete(filename: String)
    fun wipe()

    companion object {
        /** iOS: `image.jpegData(compressionQuality: 0.85)`. */
        const val JPEG_QUALITY = 85
    }
}

/** iOS: PhotoStorageService on `Documents/TripPhotos`. */
class DirectoryTripPhotoFiles(private val root: File) : TripPhotoFiles {
    override fun save(jpeg: ByteArray, tripId: UUID): String? {
        val tripFolder = tripId.toString().uppercase()
        val dir = File(root, tripFolder)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val name = UUID.randomUUID().toString().uppercase() + ".jpg"
        return try {
            File(dir, name).writeBytes(jpeg)
            "$tripFolder/$name"
        } catch (_: IOException) {
            null
        }
    }

    override fun exists(filename: String): Boolean = resolve(filename)?.isFile == true

    override fun read(filename: String): ByteArray? = try {
        resolve(filename)?.takeIf(File::isFile)?.readBytes()
    } catch (_: IOException) {
        null
    }

    override fun delete(filename: String) {
        resolve(filename)?.delete()
    }

    override fun deleteAll(tripId: UUID) {
        File(root, tripId.toString().uppercase()).deleteRecursively()
    }

    override fun wipe() {
        root.deleteRecursively()
    }

    // A filename from the server or an old row must not reach outside the photo directory.
    private fun resolve(filename: String): File? = safeChild(root, filename)
}

/** iOS: VehiclePhotoStore on `Documents/VehiclePhotos`. */
class DirectoryVehiclePhotoFiles(private val root: File) : VehiclePhotoFiles {
    override fun save(jpeg: ByteArray): String? {
        if (!root.isDirectory && !root.mkdirs()) return null
        val name = UUID.randomUUID().toString().uppercase() + ".jpg"
        return try {
            File(root, name).writeBytes(jpeg)
            name
        } catch (_: IOException) {
            null
        }
    }

    override fun exists(filename: String): Boolean = safeChild(root, filename)?.isFile == true

    override fun read(filename: String): ByteArray? = try {
        safeChild(root, filename)?.takeIf(File::isFile)?.readBytes()
    } catch (_: IOException) {
        null
    }

    override fun delete(filename: String) {
        safeChild(root, filename)?.delete()
    }

    override fun wipe() {
        root.deleteRecursively()
    }
}

/** iOS: PhotoStorageService.safePhotoURL, the path traversal guard. */
internal fun safeChild(root: File, relative: String): File? {
    if (relative.isEmpty()) return null
    val base = root.canonicalFile
    val candidate = File(base, relative).canonicalFile
    return candidate.takeIf { it.path.startsWith(base.path + File.separator) }
}
