package com.onezee.triptrack.sync

import androidx.work.Configuration
import androidx.work.WorkManager
import com.onezee.triptrack.data.sync.*
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test

// iOS: TripTrackApp service wiring, SyncCoordinator recovery. Exercises the production disk path.
class SyncEngineTests : SyncTestCase() {
    @Test fun productionAssemblyRestoresItsAccountScopedQueue() = runBlocking<Unit> {
        lateinit var engine: SyncEngine
        WorkManager.initialize(context, Configuration.Builder().setWorkerFactory(SyncWorkerFactory { engine.coordinator }).build())
        val workManager = WorkManager.getInstance(context)
        val apiScope = UUID.randomUUID().toString()
        suspend fun create() = SyncEngine.create(context, db, client, apiScope, { localUser }, { false }, { null }, { false }, {}, {}, workManager = workManager)
        engine = create()
        val operation = SyncOperation(SyncEntityType.photo, UUID.randomUUID(), SyncAction.delete)
        engine.queue.enqueue(operation); engine.close()
        engine = create()
        try { assertEquals(listOf(operation), engine.queue.state.value.pending); engine.clearAccountWork() }
        finally { engine.close() }
    }
}
