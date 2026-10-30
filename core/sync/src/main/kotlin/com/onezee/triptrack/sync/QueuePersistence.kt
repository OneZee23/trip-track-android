package com.onezee.triptrack.sync

import android.util.AtomicFile
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.*

// iOS: SyncCoordinator.recoverPendingEntities; Android also retains failed attempts across death.
data class QueueSnapshot(val pending: List<SyncOperation> = emptyList(), val failed: List<SyncOperation> = emptyList())

// iOS: SyncQueue.swift (queue/failedQueue). The file belongs in noBackupFilesDir.
interface QueuePersistence {
    fun read(): QueueSnapshot
    fun write(snapshot: QueueSnapshot)

    class Memory : QueuePersistence {
        private var snapshot = QueueSnapshot()
        override fun read(): QueueSnapshot = snapshot
        override fun write(snapshot: QueueSnapshot) { this.snapshot = snapshot }
    }
}

// iOS: pending-entity recovery. Scope includes API host, account and database identity.
class FileQueuePersistence(file: File, private val scope: String) : QueuePersistence {
    private val file = AtomicFile(file)
    override fun read(): QueueSnapshot {
        val bytes = try { file.readFully() } catch (_: FileNotFoundException) { return QueueSnapshot() }
        val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        if (root.getValue("scope").jsonPrimitive.content != scope) return QueueSnapshot()
        fun ops(key: String): List<SyncOperation> = root.getValue(key).jsonArray.map { value ->
            val row = value.jsonObject
            SyncOperation(
                SyncEntityType.valueOf(row.getValue("type").jsonPrimitive.content),
                UUID.fromString(row.getValue("entityId").jsonPrimitive.content),
                SyncAction.valueOf(row.getValue("action").jsonPrimitive.content),
                UUID.fromString(row.getValue("id").jsonPrimitive.content),
                Instant.parse(row.getValue("createdAt").jsonPrimitive.content),
                row.getValue("retryCount").jsonPrimitive.int,
                row["lastError"]?.jsonPrimitive?.contentOrNull,
            )
        }
        return QueueSnapshot(ops("pending"), ops("failed"))
    }

    override fun write(snapshot: QueueSnapshot) {
        fun ops(rows: List<SyncOperation>) = JsonArray(rows.map { op -> buildJsonObject {
            put("type", op.entityType.name); put("entityId", op.entityId.toString()); put("action", op.action.name)
            put("id", op.id.toString()); put("createdAt", op.createdAt.toString()); put("retryCount", op.retryCount)
            op.lastError?.let { put("lastError", it) }
        } })
        val bytes = buildJsonObject {
            put("scope", scope); put("pending", ops(snapshot.pending)); put("failed", ops(snapshot.failed))
        }.toString().toByteArray()
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
}
