package com.onezee.triptrack.sync

import com.onezee.triptrack.data.sync.*
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test

// iOS: SyncQueueTests.swift, plus failed-cap/restart/cancel races.
@OptIn(ExperimentalCoroutinesApi::class)
class SyncQueueTests {
    private class Transport : SyncTransport {
        val executed = mutableListOf<SyncOperation>()
        var fail = false
        var onExecute: suspend (SyncOperation) -> Unit = {}
        var batch = emptyList<SyncOperation>()
        var handled = emptySet<UUID>()
        override suspend fun execute(operation: SyncOperation) {
            executed.add(operation)
            onExecute(operation)
            if (fail) throw IllegalStateException("test")
        }
        override suspend fun uploadTripsBatch(operations: List<SyncOperation>, onChunkSynced: (Int) -> Unit): Set<UUID> {
            batch = operations
            onChunkSynced(handled.size)
            return handled
        }
    }
    private fun op(type: SyncEntityType = SyncEntityType.trip, action: SyncAction = SyncAction.upload, id: UUID = UUID.randomUUID()) = SyncOperation(type, id, action)
    private fun queue(transport: Transport? = null) = SyncQueue(isAuthorized = { true }).also { if (transport != null) it.configure(transport) }

    @Test fun testEnqueueIncrementsCount() { val q = queue(); q.enqueue(op()); q.enqueue(op(SyncEntityType.vehicle)); assertEquals(2, q.pendingCount) }
    @Test fun testDeduplication() { val q = queue(); val op = op(); q.enqueue(op); q.enqueue(op.copy(id = UUID.randomUUID())); assertEquals(1, q.pendingCount) }
    @Test fun testDifferentActionsNotDeduplicated() { val q = queue(); val op = op(); q.enqueue(op); q.enqueue(op.copy(action = SyncAction.delete)); assertEquals(2, q.pendingCount) }
    @Test fun testClearAll() { val q = queue(); q.enqueue(op()); q.enqueue(op()); q.clearAll(); assertEquals(0, q.pendingCount) }
    @Test fun testProcessQueueWithoutTransportDoesNothing() = runTest { val q = queue(); q.enqueue(op()); q.processQueue(); assertEquals(1, q.pendingCount) }
    @Test fun testBatchRemovesOnlyHandledTripOps() = runTest {
        val t = Transport(); val q = queue(t); val ops = List(3) { op() }; ops.forEach(q::enqueue)
        t.handled = ops.take(2).map { it.entityId }.toSet(); q.processQueue()
        assertEquals(ops, t.batch); assertEquals(listOf(ops.last()), t.executed); assertEquals(0, q.pendingCount)
    }
    @Test fun testBatchSkippedForSingleTripOp() = runTest { val t = Transport(); val q = queue(t); val op = op(); q.enqueue(op); q.processQueue(); assertTrue(t.batch.isEmpty()); assertEquals(listOf(op), t.executed) }
    @Test fun testBatchLeavesNonTripOpsToPerOp() = runTest {
        val t = Transport(); val q = queue(t); val trips = List(2) { op() }; val car = op(SyncEntityType.vehicle); val photo = op(SyncEntityType.photo)
        (trips + car + photo).forEach(q::enqueue); t.handled = trips.map { it.entityId }.toSet(); q.processQueue()
        assertEquals(trips, t.batch); assertEquals(listOf(car, photo), t.executed)
    }
    @Test fun testProcessQueueSkipsWhenNotAuthorized() = runTest {
        val t = Transport(); val q = SyncQueue(isAuthorized = { false }); q.configure(t); q.enqueue(op()); q.processQueue(); assertTrue(t.executed.isEmpty()); assertEquals(1, q.pendingCount)
    }
    @Test fun testProcessQueueStopsMidDrainWhenAuthorizationDrops() = runTest {
        var authorized = true; val q = SyncQueue(isAuthorized = { authorized }); val t = Transport(); q.configure(t)
        t.onExecute = { authorized = false }; repeat(3) { q.enqueue(op(SyncEntityType.vehicle)) }; q.processQueue()
        assertEquals(1, t.executed.size); assertEquals(2, q.pendingCount)
    }
    @Test fun testRetryFailedDoesNotResurrectOpsClearedDuringBackoff() = runTest {
        val t = Transport().apply { fail = true }; val q = queue(t); q.enqueue(op(SyncEntityType.settings)); q.processQueue()
        t.fail = false; t.executed.clear(); val retry = launch { q.retryFailed() }; runCurrent(); advanceTimeBy(300); q.clearAll(); retry.join()
        assertTrue(t.executed.isEmpty()); assertEquals(0, q.pendingCount)
    }
    @Test fun priorityAndFifoAreStable() = runTest {
        val t = Transport(); val q = queue(t)
        val ops = listOf(op(SyncEntityType.photo), op(SyncEntityType.vehiclePhoto), op(SyncEntityType.trip, SyncAction.update),
            op(SyncEntityType.vehicle), op(SyncEntityType.journey), op(SyncEntityType.settings), op(SyncEntityType.vehicle), op(SyncEntityType.discovery))
        ops.forEach(q::enqueue); q.processQueue()
        assertEquals(listOf(ops[5], ops[3], ops[6], ops[4], ops[2], ops[0], ops[7], ops[1]), t.executed)
    }
    @Test fun failedRowsReachCapButStayVisibleAndManualRetryResets() = runTest {
        val t = Transport().apply { fail = true }; val q = queue(t); q.enqueue(op()); q.processQueue()
        repeat(4) { q.retryFailed() }; assertEquals(5, t.executed.size); assertEquals(5, q.state.value.failed.single().retryCount)
        q.retryFailed(); assertEquals(5, t.executed.size); t.fail = false; q.retryFailedNow()
        assertEquals(0, t.executed.last().retryCount); assertEquals(0, q.pendingCount)
        assertEquals(30_000, testScheduler.currentTime)
    }
    @Test fun cancelRemovesPendingAndFailed() = runTest {
        val t = Transport().apply { fail = true }; val q = queue(t); val op = op(); q.enqueue(op); q.processQueue(); q.enqueue(op.copy(action = SyncAction.update))
        q.cancelOperations(op.entityId, op.entityType); assertEquals(0, q.pendingCount); assertTrue(q.state.value.failed.isEmpty())
    }
    @Test fun cancelDuringBackoffDoesNotReappend() = runTest {
        val t = Transport().apply { fail = true }; val q = queue(t); val op = op(); q.enqueue(op); q.processQueue()
        val retry = launch { q.retryFailed() }; runCurrent(); q.cancelOperations(op.entityId, op.entityType); retry.join(); assertEquals(0, q.pendingCount)
    }
    @Test fun clearDuringAnInFlightFailureDoesNotRestoreFailedRow() = runTest {
        val t = Transport().apply { fail = true }; val q = queue(t); t.onExecute = { q.clearAll() }; q.enqueue(op()); q.processQueue(); assertEquals(0, q.pendingCount)
    }
    @Test fun cancellationKeepsTheInFlightOperation() = runTest {
        val t = Transport(); val q = queue(t); val op = op(); q.enqueue(op); t.onExecute = { awaitCancellation() }
        val job = launch { q.processQueue() }; runCurrent(); job.cancelAndJoin(); assertEquals(listOf(op), q.state.value.pending); assertFalse(q.state.value.isSyncing)
    }
    @Test fun newEditDuringBatchIsNotRemovedWithAnOldAcknowledgement() = runTest {
        val q = queue(); val ops = List(2) { op() }; ops.forEach(q::enqueue)
        val executed = mutableListOf<SyncOperation>()
        q.configure(object : SyncTransport {
            override suspend fun uploadTripsBatch(operations: List<SyncOperation>, onChunkSynced: (Int) -> Unit): Set<UUID> {
                q.cancelOperations(ops[0].entityId, SyncEntityType.trip)
                q.enqueue(ops[0].copy(id = UUID.randomUUID()))
                return ops.map { it.entityId }.toSet()
            }
            override suspend fun execute(operation: SyncOperation) { executed.add(operation) }
        })
        q.processQueue(); assertEquals(1, executed.size); assertEquals(ops[0].entityId, executed.single().entityId)
    }
    @Test fun operationProperties() { val op = op(SyncEntityType.photo, SyncAction.delete); assertEquals(0, op.retryCount); assertNull(op.lastError) }
}
