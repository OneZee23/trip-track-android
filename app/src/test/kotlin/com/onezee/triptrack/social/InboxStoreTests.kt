@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
// iOS: NotificationsInboxStore.swift, NotificationInviteRowModel.swift (read-on-sight, paging).
package com.onezee.triptrack.social

import com.onezee.triptrack.feature.social.InboxStore
import com.onezee.triptrack.model.*
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.After
import org.junit.Test

class InboxStoreTests {
    private val dispatcher=StandardTestDispatcher()
    private lateinit var inbox:InboxStore
    @Before fun main() { Dispatchers.setMain(dispatcher) }
    @After fun close() { if(::inbox.isInitialized)inbox.close();Dispatchers.resetMain() }
    @Test fun readOnSightIsCountedOnceAndKeepsNewLook() = runTest(dispatcher) {
        val item=SocialFixtures.notice();val batches=mutableListOf<List<UUID>?>()
        val api=object:FakeSocialApi(){override suspend fun notifications(cursor:String?)=NotificationsFeedResponse(listOf(item));override suspend fun markRead(ids:List<UUID>?) {batches+=ids}}
        inbox=InboxStore(api,{true},dispatcher);inbox.refresh();inbox.noteSeen(item);inbox.flushSeen();inbox.noteSeen(item);inbox.flushSeen()
        assertEquals(0,inbox.state.value.unreadCount);assertFalse(inbox.state.value.items.single().isRead);assertEquals(1,batches.size);assertEquals(listOf(item.id),batches.single())
    }
    @Test fun failureIsNotAnEmptyInbox() = runTest(dispatcher) {
        val api=object:FakeSocialApi(){override suspend fun notifications(cursor:String?):NotificationsFeedResponse=throw java.io.IOException("offline")}
        inbox=InboxStore(api,{true},dispatcher);inbox.refresh();assertTrue(inbox.state.value.loadFailed);assertTrue(inbox.state.value.loadedOnce)
    }
    @Test fun clearCancelsRefreshAndOldAccountCannotReturn() = runTest(dispatcher) {
        val answer=CompletableDeferred<NotificationsFeedResponse>()
        val api=object:FakeSocialApi(){override suspend fun notifications(cursor:String?)=answer.await()}
        inbox=InboxStore(api,{true},dispatcher);val job=launch { runCatching { inbox.refresh() } };runCurrent()
        inbox.clear();answer.complete(NotificationsFeedResponse(listOf(SocialFixtures.notice())));job.join()
        assertTrue(inbox.state.value.items.isEmpty());assertEquals(0,inbox.state.value.unreadCount)
    }
    @Test fun pagesDeduplicateAndRepeatedCursorStops() = runTest(dispatcher) {
        val first=SocialFixtures.notice();val second=SocialFixtures.notice();var calls=0
        val api=object:FakeSocialApi(){override suspend fun notifications(cursor:String?):NotificationsFeedResponse { calls++;return NotificationsFeedResponse(if(cursor==null)listOf(first) else listOf(first,second),"same")}}
        inbox=InboxStore(api,{true},dispatcher);inbox.refresh();inbox.loadMoreIfNeeded(first);inbox.loadMoreIfNeeded(second)
        assertEquals(listOf(first.id,second.id),inbox.state.value.items.map{it.id});assertEquals(2,calls);assertFalse(inbox.state.value.hasMore)
    }
    @Test fun markReadKeepsAchievementIdAndRevertsOnFailure() = runTest(dispatcher) {
        val item=SocialFixtures.notice("achievement").copy(badgeId="first_trip")
        val api=object:FakeSocialApi(){override suspend fun notifications(cursor:String?)=NotificationsFeedResponse(listOf(item));override suspend fun markRead(ids:List<UUID>?) {throw java.io.IOException("offline")}}
        inbox=InboxStore(api,{true},dispatcher);inbox.refresh();inbox.markRead(item)
        assertEquals(item,inbox.state.value.items.single());assertEquals(1,inbox.state.value.unreadCount)
    }
    @Test fun seenRequestsSplitAtTwoHundred() = runTest(dispatcher) {
        val items=(0..200).map{SocialFixtures.notice()};val batches=mutableListOf<Int>()
        val api=object:FakeSocialApi(){override suspend fun notifications(cursor:String?)=NotificationsFeedResponse(items);override suspend fun unreadCount()=201;override suspend fun markRead(ids:List<UUID>?) {batches+=ids!!.size}}
        inbox=InboxStore(api,{true},dispatcher);inbox.refresh();items.forEach(inbox::noteSeen);inbox.flushSeen()
        assertEquals(listOf(200,1),batches);assertEquals(0,inbox.state.value.unreadCount)
    }
}
