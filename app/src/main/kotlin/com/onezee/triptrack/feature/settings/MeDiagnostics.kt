// iOS: DebugLogsView.swift, DebugLogExporter.swift. Only the network logger's scrubbed lines enter here.
package com.onezee.triptrack.feature.settings

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.FileProvider
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.CustomNavBar
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.theme.*
import androidx.compose.ui.unit.dp
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
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
        CustomNavBar(strings.logsJournalTitle(), onBack)
        Text(strings.logsPrivacyCaption(), Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = AppType.caption, color = AppTheme.colors.textSecondary)
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
        }, Modifier.padding(horizontal = 16.dp).testTag("logs_share"), enabled = !sharing && !rows.isNullOrEmpty())
        if (failed) Box(Modifier.padding(horizontal = 16.dp)) { MeRow(strings.logsLoadFailed()) }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
            when {
                rows == null -> MeRow(strings.loadingGeneric())
                rows!!.isEmpty() -> MeRow(strings.logsEmpty())
                else -> Column(Modifier.fillMaxWidth().surfaceCard(14.dp).padding(horizontal = 12.dp)) {
                    rows!!.forEachIndexed { index, line ->
                        if (index > 0) Spacer(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.border))
                        Text(line, Modifier.fillMaxWidth().padding(vertical = 9.dp), style = interScaled(11f), color = AppTheme.colors.textSecondary)
                    }
                }
            }
        }
    }
}
