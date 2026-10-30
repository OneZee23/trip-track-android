package com.onezee.triptrack.location.recording

import com.onezee.triptrack.logic.LocationFix
import com.onezee.triptrack.model.FixGate
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * iOS: RawFixLog.swift. Every provider fix of a recording, accepted and rejected with its reason,
 * plus process lifecycle marks. It answers what the summary log cannot: were there no fixes, or
 * were they thrown away. Kept 14 days.
 *
 * The directory lives under `noBackupFilesDir`: the coordinates of every drive must not reach a
 * cloud backup or a new phone, as with the trip photos. Calls are serialized by the monitor; each
 * line carries its own time, so the order of arrival does not matter.
 */
class RawFixLog(val directory: File, private val now: () -> Instant = Instant::now) {
    private var current: File? = null
    private var bytesWritten = 0L

    @Synchronized
    fun begin(tripId: UUID) {
        prepareDirectory()
        val file = file(tripId)
        val head = HEADER + "\n"
        file.writeText(head)
        current = file
        bytesWritten = head.toByteArray().size.toLong()
    }

    /**
     * Recovery of an orphaned recording: the fixes from before the process died are on disk and must
     * not be truncated the way `begin` would. A fresh process does not know its byte count, so it
     * reads the size from the file; no file (reinstall, removed by hand) starts one.
     */
    @Synchronized
    fun resume(tripId: UUID) {
        prepareDirectory()
        val file = file(tripId)
        if (!file.exists()) {
            begin(tripId)
            return
        }
        current = file
        bytesWritten = file.length()
    }

    @Synchronized
    fun end() {
        current = null
    }

    /**
     * «Delete» leaves no trace: a draft may have been somebody else's drive. Closing first matters:
     * a write between the delete and the close would bring the file back.
     */
    @Synchronized
    fun remove(tripId: UUID) {
        val file = file(tripId)
        if (current == file) current = null
        file.delete()
    }

    @Synchronized
    fun record(line: String) {
        val file = current ?: return
        if (bytesWritten >= MAX_BYTES_PER_TRIP) return
        val data = (line + "\n").toByteArray()
        runCatching { FileOutputStream(file, true).use { it.write(data) } }
            .onSuccess { bytesWritten += data.size }
    }

    /** A process lifecycle mark: background, foreground, launch. */
    fun mark(event: String) {
        record("#${now()} $event")
    }

    @Synchronized
    fun purgeOld() {
        val cutoff = now().minusSeconds(RETENTION_DAYS * 86_400L).toEpochMilli()
        directory.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    /** Files touched since [date], newest first, for the log export. */
    @Synchronized
    fun files(modifiedSince: Instant): List<File> =
        (directory.listFiles() ?: emptyArray()).filter { it.lastModified() >= modifiedSince.toEpochMilli() }
            .sortedByDescending { it.lastModified() }

    /** The account wipe takes every file, like `LocalDataWipe` on iOS. */
    @Synchronized
    fun wipe() {
        current = null
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun file(tripId: UUID) = File(directory, "${tripId.toString().uppercase(Locale.ROOT)}.csv")

    private fun prepareDirectory() {
        directory.mkdirs()
    }

    companion object {
        const val DIRECTORY = "RawFixes"
        const val RETENTION_DAYS = 14
        const val MAX_BYTES_PER_TRIP = 20L * 1024 * 1024
        const val HEADER = "ts,lat,lon,acc,speed,course,alt,decision"

        /** A dot is the decimal separator in any locale. */
        fun line(fix: LocationFix, decision: FixGate.Decision): String {
            fun n(value: Double, digits: Int) = String.format(Locale.ROOT, "%.${digits}f", value)
            val verdict = when (decision) {
                FixGate.Decision.Accept -> "accept"
                is FixGate.Decision.Reject -> "reject:${decision.value.rawValue}"
            }
            val seconds = fix.timestamp.epochSecond + fix.timestamp.nano / 1e9
            return listOf(
                n(seconds, 3), n(fix.latitude, 7), n(fix.longitude, 7),
                n(fix.horizontalAccuracy, 2), n(fix.speed, 2), n(fix.course, 1), n(fix.altitude, 1),
                verdict,
            ).joinToString(",")
        }
    }
}
