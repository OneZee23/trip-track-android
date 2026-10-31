// iOS: ProfileSettingsSheet.swift, AppPreferencesView.swift, PrivacySettingsView.swift, NotificationPreferencesView.swift
package com.onezee.triptrack.feature.settings

import androidx.activity.compose.BackHandler

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.account.AccountEntry
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.ui.LocalAppVersion

@Composable
internal fun SettingsRoute(graph: AppGraph, onDismiss: () -> Unit) {
    val manager = graph.settingsManager
    val state by manager.state.collectAsState()
    val auth by graph.auth.state.collectAsState()
    val strings = appStrings
    val language = graph.languageManager.current(LocalConfiguration.current)
    val notifications = if (auth.isSignedIn) rememberMeModel(auth.accountId ?: Unit) { graph.me.notificationPreferences() } else null
    val notificationState = notifications?.state?.collectAsState()?.value ?: NotificationPreferencesState()
    val privacy = if (auth.isSignedIn) rememberMeModel(auth.accountId ?: Unit) { graph.me.privacy() } else null
    val privacyState = privacy?.state?.collectAsState()?.value ?: PrivacySettingsState()
    val usageEnabled by graph.me.analytics.enabled.collectAsState()
    val profile by graph.me.profile.state.collectAsState()
    LaunchedEffect(Unit) { if (manager.state.value.sheets.isEmpty()) manager.openSheet(SettingsSheet.settings) }
    DisposableEffect(Unit) { onDispose { manager.closeSheets() } }
    val context = LocalContext.current
    fun open(uri: String) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
    val pickers = setOf(SettingsSheet.units, SettingsSheet.theme, SettingsSheet.language, SettingsSheet.averageSpeed)
    val screen = state.sheets.lastOrNull { it !in pickers } ?: SettingsSheet.settings
    ContentSizedSheet(onDismiss, background = AppTheme.colors.bg, detent = SheetDetent.large) {
        BackHandler(state.sheets.size > 1 && state.sheet !in pickers) { manager.dismissSheet() }
        when (screen) {
            SettingsSheet.settings -> SettingsContent(state, graph.themeManager.mode, language, auth.isSignedIn, notificationState,
                onClose = { dismiss() }, onOpen = manager::openSheet, onNotificationMaster = { notifications?.setMaster(it) },
                onWriteAuthor = { open("mailto:privacy@trip-track.app") }, onTelegram = { open("https://t.me/onezee_co") },
                account = { AccountEntry(graph) })
            SettingsSheet.appPreferences -> SettingsDetail(strings.settingsAppPrefs(), manager::dismissSheet) {
                MeRow(strings.settingsUnits(), strings.unitDistanceBadge(state.distanceUnit), tag = "settings_units", onClick = { manager.openSheet(SettingsSheet.units) })
                MeDivider()
                MeRow(strings.avgSpeedModeTitle(), if (state.averageSpeed == AvgSpeedMode.overall) strings.avgSpeedOverall() else strings.avgSpeedMoving(), tag = "settings_average_speed", onClick = { manager.openSheet(SettingsSheet.averageSpeed) })
            }
            SettingsSheet.privacy -> SettingsDetail(strings.privacyTitle(), manager::dismissSheet) {
                MeToggle(strings.usageTitle(), usageEnabled, { graph.me.analytics.setEnabled(it, profile.hero.tripCount > 0) }, strings.usageDetail(), tag = "settings_usage_analytics")
                if (auth.isSignedIn) {
                    MeToggle(strings.publicProfileTitle(), privacyState.isPublic, { privacy?.setPublicProfile(it) }, strings.privacyPublicProfileSub(), enabled = privacyState.loaded, tag = "privacy_public_profile")
                    MeToggle(strings.settingsPublicProfile(), state.showOnPublicMap, manager::setShowOnPublicMap)
                    if (privacyState.supportsBlocks) ProfileVisibilityBlock.entries.forEach { block ->
                        MeToggle(visibilityLabel(block, strings), privacyState.visibility[block] == true, { privacy?.setVisibility(block, it) }, enabled = block !in privacyState.pending)
                    }
                    MeRow(strings.settingsNotifications(), onClick = { manager.openSheet(SettingsSheet.notifications) })
                }
                MeRow(strings.homeRow(), if (state.home.coordinate == null) strings.homeNotSet() else strings.homeZoneShort(Measure.radius(state.home.radius.rawValue.toDouble(), state.distanceUnit, strings.language.code, strings)), tag = "settings_home", onClick = { manager.openSheet(SettingsSheet.home) })
                if (privacyState.failed) MeRow(strings.publicDataLoadFailed(), strings.tryAgain(), onClick = { privacy?.load() })
            }
            SettingsSheet.notifications -> SettingsDetail(strings.settingsNotifications(), manager::dismissSheet) {
                NotificationPreferencesContent(notificationState, notifications)
            }
            SettingsSheet.home -> HomeSettingsContent(state.home, state.distanceUnit, manager::dismissSheet, manager::setHome)
            SettingsSheet.autoRecord -> AutoRecordSettingsContent(graph, state, manager::dismissSheet)
            SettingsSheet.stereo -> StereoPicker(graph, onBack = manager::dismissSheet)
            SettingsSheet.logs -> DebugLogsContent(graph, manager::dismissSheet)
            SettingsSheet.about -> SettingsDetail(strings.about(), manager::dismissSheet) {
                MeRow(strings.tr(com.onezee.triptrack.designsystem.R.string.app_name), LocalAppVersion.current)
                MeRow(strings.privacyPolicy(), onClick = { open("https://trip-track.app/privacy") })
                MeRow(strings.termsOfService(), onClick = { open("https://trip-track.app/terms") })
                MeRow(strings.osmAttribution(), onClick = { open("https://www.openstreetmap.org/copyright") })
                MeRow(strings.naturalEarthAttribution())
            }
            else -> Unit
        }
    }
    when (state.sheet) {
        SettingsSheet.units -> SettingsOptionPicker(strings.settingsUnits(), DistanceUnit.entries, state.distanceUnit, strings.unitsPickerFootnote(),
            label = { it.labelFull(strings.language.code, strings) }, badge = { strings.unitDistanceBadge(it) }, onSelect = manager::setDistanceUnit, onDismissRequest = manager::dismissSheet, testTagPrefix = "settings_units")
        SettingsSheet.averageSpeed -> SettingsOptionPicker(strings.avgSpeedModeTitle(), AvgSpeedMode.entries, state.averageSpeed, strings.avgSpeedPickerFootnote(),
            label = { if (it == AvgSpeedMode.overall) strings.avgSpeedOverall() else strings.avgSpeedMoving() }, onSelect = manager::setAverageSpeed, onDismissRequest = manager::dismissSheet, testTagPrefix = "settings_avg_speed")
        SettingsSheet.theme -> SettingsOptionPicker(strings.theme(), listOf(ThemeMode.system, ThemeMode.light, ThemeMode.dark), graph.themeManager.mode, strings.themePickerFootnote(),
            label = { themeLabel(it, strings) }, badgeGlyph = ::themeGlyph, onSelect = { graph.themeManager.select(it); manager.setTheme(it.rawValue) }, onDismissRequest = manager::dismissSheet, testTagPrefix = "settings_theme")
        SettingsSheet.language -> SettingsOptionPicker(strings.lang(), AppLanguage.displayOrder, language, strings.languagePickerFootnote(),
            label = { it.endonym }, badge = { it.badge }, onSelect = {
                // The activity is recreated immediately; no open picker may survive that saved frame.
                manager.dismissSheet()
                manager.setLanguage(it.code)
                graph.languageManager.choose(it)
            }, onDismissRequest = { if (manager.state.value.sheet == SettingsSheet.language) manager.dismissSheet() }, testTagPrefix = "settings_language")
        else -> Unit
    }
}

@Composable
internal fun SettingsContent(state: SettingsState, theme: ThemeMode, language: AppLanguage, signedIn: Boolean,
                             notifications: NotificationPreferencesState, onClose: () -> Unit, onOpen: (SettingsSheet) -> Unit,
                             onNotificationMaster: (Boolean) -> Unit, onWriteAuthor: () -> Unit, onTelegram: () -> Unit,
                             account: @Composable () -> Unit = {}) {
    val strings = appStrings
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).testTag("settings_sheet")) {
        MeSheetHeader(strings.settingsTitle(), onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.surfaceCard(16.dp)) {
                MeRow(strings.privacyTitle(), subtitle = if (signedIn) strings.privacyRowSub() else strings.usageTitle(), glyph = AppGlyph.infoCircleFill, tag = "settings_privacy", onClick = { onOpen(SettingsSheet.privacy) })
                if (signedIn) {
                    MeDivider()
                    MeToggle(strings.settingsNotifications(), notifications.master, onNotificationMaster, enabled = notifications.loaded, tag = "settings_notifications")
                }
                if (signedIn) account()
            }
            Column(Modifier.surfaceCard(16.dp)) {
                MeRow(strings.lang(), language.endonym, glyph = AppGlyph.characterBubble, tag = "settings_language", onClick = { onOpen(SettingsSheet.language) })
                MeDivider()
                MeRow(strings.theme(), themeLabel(theme, strings), glyph = themeGlyph(theme), tag = "settings_theme", onClick = { onOpen(SettingsSheet.theme) })
                MeDivider()
                MeRow(strings.settingsAppPrefs(), strings.unitDistanceBadge(state.distanceUnit), glyph = AppGlyph.gearshapeFill, tag = "settings_app_prefs", onClick = { onOpen(SettingsSheet.appPreferences) })
                MeDivider()
                MeRow(strings.autoRecord(), autoRecordLabel(state.autoRecordMode, strings), tag = "settings_auto_record", onClick = { onOpen(SettingsSheet.autoRecord) })
            }
            Column(Modifier.surfaceCard(16.dp)) {
                MeRow(strings.writeAuthor(), subtitle = strings.settingsWriteToAuthorSub(), glyph = AppGlyph.pencil, onClick = onWriteAuthor)
                MeDivider()
                MeRow(strings.telegramChannel(), subtitle = strings.settingsTelegramSub(), glyph = AppGlyph.squareAndArrowUp, onClick = onTelegram)
                MeDivider()
                MeRow(strings.settingsSendLogs(), subtitle = strings.settingsSendLogsSub(), glyph = AppGlyph.flag, tag = "settings_logs", onClick = { onOpen(SettingsSheet.logs) })
            }
            MeRow(strings.about(), LocalAppVersion.current, tag = "settings_about", onClick = { onOpen(SettingsSheet.about) })
            if (state.failed) MeRow(strings.publicDataLoadFailed())
        }
    }
}

@Composable
internal fun SettingsDetail(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        MeNavBar(title, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.surfaceCard(16.dp), content = content)
        }
    }
}

@Composable
internal fun NotificationPreferencesContent(state: NotificationPreferencesState, model: NotificationPreferencesViewModel?) {
    val strings = appStrings
    MeToggle(strings.settingsNotifications(), state.master, { model?.setMaster(it) }, enabled = state.loaded)
    MeToggle(strings.notificationPreferencesWhenSomeoneReacts(), state.reactions, { model?.setPreferences(reactions = it) }, enabled = state.loaded)
    MeToggle(strings.notificationPreferencesWhenSomeoneFollows(), state.follows, { model?.setPreferences(follows = it) }, enabled = state.loaded)
    MeToggle(strings.comments(), state.comments, { model?.setPreferences(comments = it) }, enabled = state.loaded)
    MeToggle(strings.notificationPreferencesWeeklyRecap(), state.weeklyRecap, { model?.setPreferences(weeklyRecap = it) }, enabled = state.loaded)
    MeToggle(strings.notifyCompanionsTitle(), state.companions, { model?.setPreferences(companions = it) }, enabled = state.loaded)
    if (state.failed) MeRow(strings.publicDataLoadFailed(), strings.tryAgain(), onClick = { model?.load() })
}

private fun themeLabel(mode: ThemeMode, strings: AppStrings): String = when (mode) { ThemeMode.system -> strings.themeSystem(); ThemeMode.light -> strings.light(); ThemeMode.dark -> strings.dark() }
private fun themeGlyph(mode: ThemeMode): AppGlyph = when (mode) { ThemeMode.system -> AppGlyph.gearshapeFill; ThemeMode.light -> AppGlyph.sunMaxFill; ThemeMode.dark -> AppGlyph.moonFill }
internal fun autoRecordLabel(mode: AutoRecordMode, strings: AppStrings): String = when (mode) { AutoRecordMode.off -> strings.autoRecordOff(); AutoRecordMode.remind -> strings.autoRecordRemind(); AutoRecordMode.auto -> strings.autoRecordAuto() }
private fun visibilityLabel(block: ProfileVisibilityBlock, strings: AppStrings): String = when (block) {
    ProfileVisibilityBlock.counters -> strings.trips(); ProfileVisibilityBlock.stats -> strings.stats(); ProfileVisibilityBlock.map -> strings.tabMap(); ProfileVisibilityBlock.achievements -> strings.achievementsSection()
}
