package com.onezee.triptrack.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.onezee.triptrack.feature.trip.TripExport
import com.onezee.triptrack.feature.trip.TripExportFormat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * iOS: TripDetailView.performExport and ShareLinkPresenter. The file goes into its format's own
 * folder, which is emptied before every export (otherwise exports would pile up forever), and the
 * system share sheet, the one system sheet the app allows, takes it from there.
 */
object TripExportShare {
    suspend fun write(context: Context, export: TripExport): File = withContext(Dispatchers.IO) {
        val folder = File(File(context.cacheDir, "exports"), "${export.format.extension}-export")
        folder.deleteRecursively()
        folder.mkdirs()
        File(folder, export.filename).apply { writeText(export.content, Charsets.UTF_8) }
    }

    fun share(context: Context, file: File, format: TripExportFormat, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = when (format) {
                TripExportFormat.gpx -> "application/gpx+xml"
                TripExportFormat.csv -> "text/csv"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
