package com.onezee.triptrack.sync

import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.sync.*
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.*
import org.junit.Test

// iOS: LastSyncedAtStoreTests.swift and SyncQueueTests.swift; disk snapshot is Android's restart guarantee.
class CursorAndRecoveryTests : SyncTestCase() {
    private fun cursor() = LastSyncedAtStore(context.getSharedPreferences(UUID.randomUUID().toString(), 0), "test")
    @Test fun testCursorSurvivesWhenTheStoreIsTheSameOne() { val store = cursor(); store.set(t0, account!!, "A"); assertEquals(t0, store.get(account!!, "A")) }
    @Test fun testCursorIsDroppedWhenTheStoreIdentityChanges() { val store = cursor(); store.set(t0, account!!, "A"); assertNull(store.get(account!!, "B")) }
    @Test fun testNoStoreOpenWritesNothing() { val store = cursor(); store.set(t0, account!!, null); assertNull(store.get(account!!, "A")) }
    @Test fun testNoStoreOpenReadsNothing() { val store = cursor(); store.set(t0, account!!, "A"); assertNull(store.get(account!!, null)) }
    @Test fun testResetClearsBothKeys() { val store = cursor(); store.set(t0, account!!, "A"); store.reset(account!!); assertNull(store.get(account!!, "A")) }
    @Test fun testReadingDoesNotAdoptTheCurrentIdentity() { val store = cursor(); store.set(t0, account!!, "A"); assertNull(store.get(account!!, "B")); assertNull(store.get(account!!, "B")); assertEquals(t0, store.get(account!!, "A")) }
    @Test fun testAccountsDoNotShareACursor() { val store = cursor(); store.set(t0, account!!, "A"); assertNull(store.get(UUID.randomUUID(), "A")); assertEquals(t0, store.get(account!!, "A")) }
    @Test fun apiHostsDoNotShareCursors() {
        val prefs = context.getSharedPreferences(UUID.randomUUID().toString(), 0)
        LastSyncedAtStore(prefs, "one").set(t0, account!!, "A"); assertNull(LastSyncedAtStore(prefs, "two").get(account!!, "A"))
    }
    @Test fun storeIdentityIsStableInsideDatabaseAndChangesOnReplacement() = runBlocking<Unit> {
        val identity = SyncStoreIdentity.get(db); assertEquals(identity, SyncStoreIdentity.get(db))
        val other = TripTrackDatabase.inMemory(context)
        try { assertNotEquals(identity, SyncStoreIdentity.get(other)) } finally { other.close() }
    }
    @Test fun identitySurvivesClosingAndReopeningRoom() = runBlocking<Unit> {
        val name = UUID.randomUUID().toString() + ".db"
        val first = androidx.room.Room.databaseBuilder(context, TripTrackDatabase::class.java, name).build()
        val identity = SyncStoreIdentity.get(first); first.close()
        val second = androidx.room.Room.databaseBuilder(context, TripTrackDatabase::class.java, name).build()
        try { assertEquals(identity, SyncStoreIdentity.get(second)) } finally { second.close(); context.deleteDatabase(name) }
    }
    @Test fun pendingAndFailedOperationsSurviveQueueRecreationWithAttemptsAndScope() = runBlocking<Unit> {
        val file = File(directory, "queue.json"); val persistence = FileQueuePersistence(file, "host/account/store")
        val queue = SyncQueue(persistence, { true }); queue.configure(object : SyncTransport {
            override suspend fun execute(operation: SyncOperation) { throw IllegalStateException("not persisted") }
        })
        val failed = SyncOperation(SyncEntityType.trip, UUID.randomUUID(), SyncAction.upload); queue.enqueue(failed); queue.processQueue()
        val pending = SyncOperation(SyncEntityType.photo, UUID.randomUUID(), SyncAction.delete); queue.enqueue(pending)
        val restarted = SyncQueue(FileQueuePersistence(file, "host/account/store"), { true })
        assertEquals(listOf(pending), restarted.state.value.pending); assertEquals(failed.id, restarted.state.value.failed.single().id)
        assertEquals(1, restarted.state.value.failed.single().retryCount); assertEquals("IllegalStateException", restarted.state.value.failed.single().lastError)
        assertEquals(0, SyncQueue(FileQueuePersistence(file, "another-account"), { true }).pendingCount)
        restarted.clearAll(); assertEquals(0, SyncQueue(FileQueuePersistence(file, "host/account/store"), { true }).pendingCount)
    }
    @Test fun inFlightSnapshotIsRecoverableAfterProcessDeath() = runBlocking<Unit> {
        val persistence = QueuePersistence.Memory(); val queue = SyncQueue(persistence, { true }); val op = SyncOperation(SyncEntityType.trip, UUID.randomUUID(), SyncAction.upload)
        queue.enqueue(op); queue.configure(object : SyncTransport {
            override suspend fun execute(operation: SyncOperation) {
                assertEquals(listOf(op), SyncQueue(persistence, { true }).state.value.pending)
            }
        }); queue.processQueue(); assertEquals(0, SyncQueue(persistence, { true }).pendingCount)
    }
    @Test fun cappedFailureIsNotRevivedByDatabaseRecovery() = runBlocking<Unit> {
        val trip = tripPayload(); repo.applyRemoteTrip(trip)
        db.tripDao().upsertTrip(repo.fetchEntity(trip.id)!!.copy(userId = localUser, syncStatus = 0))
        val persistence = QueuePersistence.Memory(); val op = SyncOperation(SyncEntityType.trip, trip.id, SyncAction.update, retryCount = 5)
        persistence.write(QueueSnapshot(failed = listOf(op)))
        val queue = SyncQueue(persistence, { true }); val gate = SyncEnqueuer(db, queue, { true }, { cloud }, scope, {})
        val c = SyncCoordinator(client, db, repo, queue, gate, applier(), cursor(), { "A" }, { account }, { true }, { false }, { cloud }, { localUser }, { false }, {}, {})
        c.recoverPendingEntities(); assertEquals(listOf(op), queue.state.value.failed); assertTrue(queue.state.value.pending.isEmpty()); gate.close()
    }
}
