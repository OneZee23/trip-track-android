package com.onezee.triptrack.sync

import android.util.AtomicFile
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

// iOS: DiscussionArchive.swift and APISyncTransport.archiveDiscussion. Root is in noBackupFilesDir.
class DiscussionArchive(private val client: ApiClient, private val directory: File) {
    suspend fun archive(tripId: UUID) {
        val comments = mutableListOf<TripComment>()
        var cursor: String? = null
        for (page in 0 until 10) {
            try {
                val response: TripCommentsResponse = client.post(ApiEndpoint.socialComments, TripCommentsRequest(tripId, 50, cursor))
                comments.addAll(response.comments)
                cursor = response.nextCursor ?: break
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: ApiError) { break }
        }
        if (comments.isEmpty()) return
        save(tripId, comments)
    }

    // iOS: DiscussionArchive.save, also called after a successful comments first-page read.
    suspend fun save(tripId: UUID, comments: List<TripComment>) {
        withContext(Dispatchers.IO) {
            val file = AtomicFile(File(directory, "$tripId.json"))
            val bytes = NetworkJson.wire.encodeToString(ListSerializer(TripComment.serializer()), comments).toByteArray()
            val output = file.startWrite()
            try { output.write(bytes); file.finishWrite(output) }
            catch (error: IOException) { file.failWrite(output); throw error }
        }
    }
    suspend fun load(tripId: UUID): List<TripComment> = withContext(Dispatchers.IO) {
        try {
            val bytes = AtomicFile(File(directory, "$tripId.json")).readFully()
            NetworkJson.wire.decodeFromString(ListSerializer(TripComment.serializer()), bytes.decodeToString())
        } catch (_: IOException) { emptyList() }
    }
    fun discard(tripId: UUID) { AtomicFile(File(directory, "$tripId.json")).delete() }
}
