// iOS: DebugLogsView.swift, DebugLogExporter.swift. Only the network logger's scrubbed lines enter here.
package com.onezee.triptrack.feature.settings

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.FileProvider
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.theme.*
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// iOS: DebugLogExporter.recentEntries(hoursBack:48, limit:800).
class MeDiagnostics(private val file: File, private val now: () -> Instant = Instant::now,
                    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
    private val generation = java.util.concurrent.atomic.AtomicLong()
    private val pending = Channel<Pair<Long, String>>(800, BufferOverflow.DROP_OLDEST)
    init { scope.launch { for ((epoch, line) in pending) write(epoch, line) } }
    fun append(scrubbed: String) { pending.trySend(generation.get() to "${now()} ${scrubbed.replace('\n', ' ')}") }
    @Synchronized private fun write(epoch: Long, line: String) {
        if (epoch != generation.get()) return
        try {
            file.parentFile?.mkdirs()
            file.writeText((read().takeLast(799) + line).joinToString("\n"))
        } catch (_: java.io.IOException) { /* Diagnostics must not break the request being logged. */ }
    }
    @Synchronized fun read(): List<String> = try {
        val earliest = now().minusSeconds(48 * 3600)
        if (file.exists()) file.readLines().filter { row -> runCatching { Instant.parse(row.substringBefore(' ')) >= earliest }.getOrDefault(false) }.takeLast(800) else emptyList()
    } catch (_: java.io.IOException) { emptyList() }
    @Synchronized fun clear() { generation.incrementAndGet(); file.delete() }
}

@Composable
internal fun DebugLogsContent(graph: AppGraph, onBack: () -> Unit) {
    val strings = appStrings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val rows by produceState<List<String>?>(null) { value = withContext(Dispatchers.IO) { graph.meDiagnostics.read().asReversed() } }
    SettingsDetail(strings.logsJournalTitle(), onBack) {
        Text(strings.logsPrivacyCaption(), style = AppType.caption, color = AppTheme.colors.textSecondary)
        MeAction(strings.logsShareFile(), {
            if (!sharing) {
                sharing = true
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.IO) {
                            File(context.cacheDir, "support/triptrack.log").also { it.parentFile?.mkdirs(); it.writeText(graph.meDiagnostics.read().joinToString("\n")) }
                        }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.support", file)
                        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(intent, strings.logsShareFile()))
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { sharing = false }
                }
            }
        }, Modifier.testTag("logs_share"), enabled = !sharing && !rows.isNullOrEmpty())
        if (failed) MeRow(strings.logsLoadFailed())
        if (rows == null) MeRow(strings.loadingGeneric())
        else if (rows!!.isEmpty()) MeRow(strings.logsEmpty())
        else rows!!.forEach { line -> Text(line, style = interScaled(11f), color = AppTheme.colors.textSecondary) }
    }
}
