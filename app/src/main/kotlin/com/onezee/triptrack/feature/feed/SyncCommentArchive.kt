package com.onezee.triptrack.feature.feed

import com.onezee.triptrack.model.TripComment
import com.onezee.triptrack.sync.DiscussionArchive
import java.util.UUID

// iOS: DiscussionArchive.swift (read/save from TripCommentsStore)
class SyncCommentArchive(private val archive: DiscussionArchive) : CommentArchive {
    override suspend fun load(tripId: UUID): List<TripComment> = archive.load(tripId)
    override suspend fun save(tripId: UUID, comments: List<TripComment>) = archive.save(tripId, comments)
}
