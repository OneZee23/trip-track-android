// iOS: DiscussionArchive.swift. The app graph supplies the durable archive.
package com.onezee.triptrack.social

import com.onezee.triptrack.feature.feed.CommentArchive
import com.onezee.triptrack.model.TripComment
import java.util.UUID

internal class MemoryCommentArchive : CommentArchive {
    private val threads = mutableMapOf<UUID, List<TripComment>>()
    override suspend fun load(tripId: UUID) = threads[tripId].orEmpty()
    override suspend fun save(tripId: UUID, comments: List<TripComment>) { threads[tripId] = comments }
}
