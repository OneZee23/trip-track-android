// iOS: Views/Admin/AdminPanelView.swift, AdminModel.swift
package com.onezee.triptrack.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.feature.me.ProfileSectionLabel
import com.onezee.triptrack.network.AdminApi
import com.onezee.triptrack.network.AdminEvent
import com.onezee.triptrack.network.AdminFlag
import com.onezee.triptrack.network.AdminSubscription
import com.onezee.triptrack.network.AdminSummary
import com.onezee.triptrack.network.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun AdminPanelScreen(client: ApiClient, onBack: () -> Unit) {
    val strings = appStrings
    val api = remember(client) { AdminApi(client) }
    var revision by remember { mutableIntStateOf(0) }
    var summary by remember { mutableStateOf<AdminSummary?>(null) }
    var subscriptions by remember { mutableStateOf<List<AdminSubscription>>(emptyList()) }
    var events by remember { mutableStateOf<List<AdminEvent>>(emptyList()) }
    var flags by remember { mutableStateOf<List<AdminFlag>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(api, revision) {
        loading = true
        failed = false
        try {
            val results = coroutineScope {
                val s = async { api.summary() }; val sub = async { api.subscriptions() }
                val e = async { api.events() }; val f = async { api.flags() }
                listOf(s.await(), sub.await(), e.await(), f.await())
            }
            summary = results[0] as AdminSummary
            subscriptions = (results[1] as com.onezee.triptrack.network.AdminItems<*>).items.filterIsInstance<AdminSubscription>()
            events = (results[2] as com.onezee.triptrack.network.AdminItems<*>).items.filterIsInstance<AdminEvent>()
            flags = (results[3] as com.onezee.triptrack.network.AdminItems<*>).items.filterIsInstance<AdminFlag>()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize()) {
        MeSheetHeader(strings.adminPanelTitle(), 12.dp, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (failed) MeRow(strings.adminLoadFailed(), strings.tryAgain(), onClick = { revision++ })
            if (loading && summary == null) MeRow(strings.loadingGeneric())
            summary?.let { value ->
                Column(Modifier.surfaceCard(16.dp)) {
                    ProfileSectionLabel(strings.adminAccounts())
                    MeRow(value.accounts.toString(), strings.adminNew7d() + ": ${value.accountsNew7d}")
                    MeRow(strings.adminActive7d() + ": ${value.accountsActive7d}")
                    MeRow(strings.adminProActive() + ": ${value.proActive}", "${value.proTrial} / ${value.proPaid}")
                MeRow(strings.adminTripsLine(value.trips, value.tripsManual))
                }
            }
            Column(Modifier.surfaceCard(16.dp)) {
                ProfileSectionLabel(strings.adminSubscriptions())
                if (subscriptions.isEmpty()) MeRow(strings.adminNothingYet())
                subscriptions.forEach { item -> MeRow("${item.txTail} · ${item.product}", "${item.status} · ${item.trips}") }
            }
            Column(Modifier.surfaceCard(16.dp)) {
                ProfileSectionLabel(strings.adminEvents())
                if (events.isEmpty()) MeRow(strings.adminNothingYet())
                events.forEach { item -> MeRow(item.type + (item.subtype?.let { " · $it" } ?: ""), item.outcome.orEmpty()) }
            }
            Column(Modifier.surfaceCard(16.dp)) {
                ProfileSectionLabel(strings.adminFlags())
                flags.forEach { flag ->
                    val current = flag.enabled ?: flag.builtIn
                    MeRow(flag.name, current?.let { if (it) "✓" else "—" } ?: strings.adminDefault(), onClick = {
                        if (current != null) scope.launch { try { api.setFlag(flag.name, !current); revision++ } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failed = true } }
                    })
                }
                Text(strings.adminFlagsFootnote(), Modifier.padding(14.dp), style = inter(11f), color = AppTheme.colors.textTertiary)
            }
        }
    }
}
