@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.onezee.triptrack.feature.feed

import com.onezee.triptrack.feature.trip.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.ApiError
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test

// iOS: TripCommentsStore.swift (paging, optimistic post/delete and archive rules)
class TripCommentsStateTests : PresentationTestCase() {
    private val tripId = UUID.randomUUID()
    private val author = SocialAuthor(UUID.randomUUID(), "driver", profileLevel = 1)
    private var archived = emptyList<TripComment>()
    private val countChanges = mutableListOf<Int>()
    private val archive = object : CommentArchive {
        override suspend fun load(tripId: UUID) = archived
        override suspend fun save(tripId: UUID, comments: List<TripComment>) { archived = comments }
    }
    private fun comment(parent: UUID? = null) = TripComment(UUID.randomUUID(), author, "text", t0, false, parent)
    private fun vm() = own(TripCommentsViewModel(tripId, api, archive, CommentValidator { null }, { author }, dispatcher, { _, delta -> countChanges.add(delta) }, { t0 }))
    @Test fun neverUploadedTripReadsArchiveWithoutCallingTheServer() = runTest(dispatcher) {
        archived = listOf(comment())
        api.commentsPage = { error("Must not ask server") }
        val vm = vm(); vm.load(isOnServer = false)!!.join()
        assertTrue(vm.state.value.hasLoadedOnce); assertTrue(vm.state.value.isArchived)
        assertEquals(1, vm.state.value.comments.size); assertNull(vm.post())
    }
    @Test fun route404HidesComposerButArchiveFallbackRemainsReadable() = runTest(dispatcher) {
        api.commentsPage = { throw ApiError.InvalidHttpStatus(404) }
        val vm = vm(); vm.load()!!.join(); assertTrue(vm.state.value.unavailable)
        archived = listOf(comment()); vm.load()!!.join()
        assertFalse(vm.state.value.unavailable); assertFalse(vm.state.value.loadFailed); assertTrue(vm.state.value.isArchived)
    }
    @Test fun replyPostsOnceAgainstTheRootAndPreservesInternalNewlines() = runTest(dispatcher) {
        val root = UUID.randomUUID(); val parent = comment(root)
        val response = CompletableDeferred<SocialCommentCreateResponse>()
        var calls = 0
        api.create = { request -> calls++; assertEquals(root, request.parentId); assertEquals("a\n\nb", request.text); response.await() }
        val vm = vm(); vm.setText("  a\n\nb  "); vm.replyTo(CommentState.from(parent))
        val posting = vm.post()!!; runCurrent()
        assertEquals(root, vm.state.value.comments.first().parentId); assertNull(vm.post())
        val serverId = UUID.randomUUID(); response.complete(SocialCommentCreateResponse(serverId, t0.plusSeconds(1))); posting.join()
        assertEquals(1, calls); assertEquals(serverId, vm.state.value.comments.single().id)
        assertEquals(listOf(1), countChanges); assertEquals("", vm.state.value.text)
    }
    @Test fun failedPostRemovesTemporaryRowAndKeepsTheComposerForManualRetry() = runTest(dispatcher) {
        api.create = { throw ApiError.TooManyRequests }
        val vm = vm(); vm.setText("retry me"); vm.post()!!.join()
        assertTrue(vm.state.value.comments.isEmpty()); assertEquals("retry me", vm.state.value.text)
        assertEquals(TripMessage.commentRateLimited, vm.state.value.message); assertTrue(countChanges.isEmpty())
    }
    @Test fun failedDeleteRestoresOriginalSlotAndPaginationDeduplicates() = runTest(dispatcher) {
        val a = comment(); val b = comment()
        api.commentsPage = { if (it.cursor == null) TripCommentsResponse(listOf(a), "page2") else TripCommentsResponse(listOf(a, b)) }
        val vm = vm(); vm.load()!!.join(); vm.loadMore()!!.join()
        assertEquals(listOf(a.id, b.id), vm.state.value.comments.map { it.id })
        api.delete = { throw ApiError.InvalidHttpStatus(500) }
        vm.delete(a.id)!!.join()
        assertEquals(listOf(a.id, b.id), vm.state.value.comments.map { it.id })
        assertEquals(TripMessage.commentDeleteFailed, vm.state.value.message)
    }
}
