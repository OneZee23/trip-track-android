// iOS: ProfileSettingsSheet.swift, AppPreferencesView.swift, PrivacySettingsView.swift, NotificationPreferencesView.swift
package com.onezee.triptrack.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.account.AccountEntry
import com.onezee.triptrack.feature.me.isAccessibilitySize
import com.onezee.triptrack.feature.me.ProfileSectionLabel
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
    var adminOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (manager.state.value.sheets.isEmpty()) manager.openSheet(SettingsSheet.settings) }
    DisposableEffect(Unit) { onDispose { manager.closeSheets() } }
    val context = LocalContext.current
    fun open(uri: String) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
    val pickers = setOf(SettingsSheet.units, SettingsSheet.theme, SettingsSheet.language, SettingsSheet.averageSpeed)
    // iOS presents each sub-page as a sheet of its own over the one before it: Настройки, then Приватность or
    // «Единицы и формат» over it, a picker over that. The pickers are drawn below, over the last page.
    state.sheets.filter { it !in pickers }.forEachIndexed { index, page ->
        key(page) {
            val close: () -> Unit = if (index == 0) onDismiss else manager::dismissSheet
            // Приватность opens at iOS's medium detent: as tall as its rows, scrolling when they outgrow the screen.
            ContentSizedSheet(close, background = AppTheme.colors.bg, detent = if (page == SettingsSheet.privacy) SheetDetent.content else SheetDetent.large, grabber = page.hasGrabber) {
                when (page) {
                    SettingsSheet.settings -> SettingsContent(state, graph.themeManager.mode, language, auth.isSignedIn, notificationState,
                        isAdmin = AdminAccess.canShowAdminCard(notificationState.loaded, notificationState.isAdmin),
                        onAdmin = { adminOpen = true },
                        adminNewAccounts = notificationState.newAccounts,
                        onAdminNewAccounts = { notifications?.setPreferences(newAccounts = it) },
                        onClose = { dismiss() }, onOpen = manager::openSheet, onNotificationMaster = { notifications?.setMaster(it) },
                        onWriteAuthor = { open("mailto:privacy@trip-track.app") }, onTelegram = { open("https://t.me/onezee_co") },
                        onPolicy = { open("https://trip-track.app/privacy") }, onTerms = { open("https://trip-track.app/terms") },
                        onOpenStreetMap = { open("https://www.openstreetmap.org/copyright") },
                        account = { AccountEntry(graph) })
                    SettingsSheet.appPreferences -> SettingsSheetPage(strings.settingsAppPrefs()) {
                        SettingsPreferencesCard(state, onOpen = manager::openSheet)
                        Text(strings.appPrefsFootnote(), Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp), style = inter(11.5f), color = AppTheme.colors.textTertiary)
                    }
                    SettingsSheet.privacy -> SettingsSheetPage(strings.privacyTitle()) {
                        Column(Modifier.surfaceCard(16.dp)) {
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
                            MeDivider()
                            ProfileSectionLabel(strings.tr(com.onezee.triptrack.designsystem.R.string.privacyPlacesSection), Modifier.padding(horizontal = 14.dp).padding(top = 10.dp))
                            listOf(0 to com.onezee.triptrack.designsystem.R.string.edgeOptionNone, 200 to com.onezee.triptrack.designsystem.R.string.edgeOption200Note,
                                500 to com.onezee.triptrack.designsystem.R.string.edgeOption500Note, 1000 to com.onezee.triptrack.designsystem.R.string.edgeOption1000Note).forEach { (metres, label) ->
                                MeRow(strings.tr(if (metres == 0) com.onezee.triptrack.designsystem.R.string.privacyEdgesRow else label),
                                    value = if (state.privacyEdgeMetres == metres) "✓" else "",
                                    subtitle = if (metres == 0) null else Measure.distance(metres.toDouble(), state.distanceUnit, strings.language.code, strings),
                                    tag = "privacy_edge_$metres", onClick = { manager.setPrivacyEdgeMetres(metres) })
                            }
                            Text(strings.tr(com.onezee.triptrack.designsystem.R.string.privacyEdgesFootnote), Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), style = inter(11.5f), color = AppTheme.colors.textTertiary)
                            MeDivider()
                            ProfileSectionLabel(strings.tr(com.onezee.triptrack.designsystem.R.string.filmingSection), Modifier.padding(horizontal = 14.dp).padding(top = 10.dp))
                            MeToggle(strings.tr(com.onezee.triptrack.designsystem.R.string.filmingSection), state.filmingManual, manager::setFilmingManual,
                                strings.tr(com.onezee.triptrack.designsystem.R.string.filmingToggleSubtitle), tag = "filming_manual")
                            MeToggle(strings.tr(com.onezee.triptrack.designsystem.R.string.filmingAutoToggle), state.filmingAuto, manager::setFilmingAuto,
                                strings.tr(com.onezee.triptrack.designsystem.R.string.filmingAutoSubtitle), enabled = android.os.Build.VERSION.SDK_INT >= 35,
                                tag = "filming_auto", hint = if (android.os.Build.VERSION.SDK_INT >= 35) null else strings.tr(com.onezee.triptrack.designsystem.R.string.android_filming_auto_unavailable))
                            Text(strings.tr(com.onezee.triptrack.designsystem.R.string.filmingFootnote), Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), style = inter(11.5f), color = AppTheme.colors.textTertiary)
                            if (privacyState.failed) MeRow(strings.publicDataLoadFailed(), strings.tryAgain(), onClick = { privacy?.load() })
                        }
                    }
                    SettingsSheet.notifications -> SettingsSheetPage(strings.settingsNotifications()) {
                        Column(Modifier.surfaceCard(16.dp)) { NotificationPreferencesContent(notificationState, notifications) }
                    }
                    SettingsSheet.home -> HomeSettingsContent(state.home, state.distanceUnit, { dismiss() }, manager::setHome)
                    SettingsSheet.autoRecord -> AutoRecordSettingsContent(graph, state, { dismiss() }, onStereo = { manager.openSheet(SettingsSheet.stereo) })
                    SettingsSheet.stereo -> StereoPicker(graph, onBack = { dismiss() })
                    SettingsSheet.logs -> DebugLogsContent(graph) { dismiss() }
                    else -> Unit
                }
            }
        }
    }
    when (state.sheet) {
        SettingsSheet.units -> SettingsOptionPicker(strings.settingsUnits(), DistanceUnit.entries, state.distanceUnit, strings.unitsPickerFootnote(),
            label = { it.labelFull(strings.language.code, strings) }, badge = { strings.unitDistanceBadge(it) }, onSelect = manager::setDistanceUnit, onDismissRequest = manager::dismissSheet, testTagPrefix = "settings_units")
        SettingsSheet.averageSpeed -> SettingsOptionPicker(strings.avgSpeedModeTitle(), AvgSpeedMode.entries, state.averageSpeed, strings.avgSpeedPickerFootnote(),
            label = { if (it == AvgSpeedMode.overall) strings.avgSpeedOverall() else strings.avgSpeedMoving() },
            badgeGlyph = { if (it == AvgSpeedMode.overall) AppGlyph.clock else AppGlyph.car },
            onSelect = manager::setAverageSpeed, onDismissRequest = manager::dismissSheet, testTagPrefix = "settings_avg_speed")
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
    if (adminOpen) ContentSizedSheet({ adminOpen = false }, background = AppTheme.colors.bg, detent = SheetDetent.large) {
        com.onezee.triptrack.feature.settings.AdminPanelScreen(graph.apiClient) { adminOpen = false }
    }
}

/** The sheets iOS gives `presentationDragIndicator(.visible)`: Настройки, its sub-pages, auto-record and the stereo picker. */
internal val SettingsSheet.hasGrabber: Boolean
    get() = when (this) {
        SettingsSheet.settings, SettingsSheet.appPreferences, SettingsSheet.privacy, SettingsSheet.autoRecord, SettingsSheet.stereo -> true
        else -> false
    }

/**
 * iOS ProfileSettingsSheet: three cards, grouped by what a row is about. The account (privacy, notifications, sync;
 * a guest has the privacy row alone), the app (language, theme, units), the author (letter, channel, logs), and under
 * them the footer with the mark, the legal links and the map credits.
 */
@Composable
internal fun SettingsContent(state: SettingsState, theme: ThemeMode, language: AppLanguage, signedIn: Boolean,
                             notifications: NotificationPreferencesState, onClose: () -> Unit, onOpen: (SettingsSheet) -> Unit,
                             onNotificationMaster: (Boolean) -> Unit, onWriteAuthor: () -> Unit, onTelegram: () -> Unit,
                             onPolicy: () -> Unit = {}, onTerms: () -> Unit = {}, onOpenStreetMap: () -> Unit = {},
                             account: @Composable () -> Unit = {}, isAdmin: Boolean = false, onAdmin: () -> Unit = {},
                             adminNewAccounts: Boolean = true, onAdminNewAccounts: (Boolean) -> Unit = {}) {
    val strings = appStrings
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).testTag("settings_sheet")) {
        MeSheetHeader(strings.settingsTitle(), 12.dp, onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.surfaceCard(16.dp)) {
                SettingsIconRow(strings.privacyTitle(), glyph = AppGlyph.handRaisedFill, subtitle = if (signedIn) strings.privacyRowSub() else strings.usageTitle(), tag = "settings_privacy",
                    onClick = { onOpen(SettingsSheet.privacy) }, trailing = { SettingsRowChevron() }, stacksTrailing = false)
                if (signedIn) {
                    MeDivider()
                    SettingsToggleRow(strings.settingsNotifications(), notifications.master, onNotificationMaster, AppGlyph.bellFill, enabled = notifications.loaded, tag = "settings_notifications")
                    MeDivider()
                    account()
                }
            }
            if (isAdmin) Column(Modifier.surfaceCard(16.dp).testTag("settings_admin_group")) {
                ProfileSectionLabel(strings.adminSectionTitle(), Modifier.padding(horizontal = 14.dp).padding(top = 10.dp))
                SettingsIconRow(strings.adminPanelTitle(), glyph = AppGlyph.clipboard, subtitle = strings.adminPanelSubtitle(), tag = "settings_admin_panel", onClick = onAdmin) { SettingsRowChevron() }
                MeDivider()
                MeToggle(strings.adminNotifyNewUsers(), adminNewAccounts, onAdminNewAccounts, enabled = notifications.loaded,
                    tag = "settings_admin_new_accounts", hint = strings.adminHintNewUsers())
            }
            Column(Modifier.surfaceCard(16.dp)) {
                SettingsIconRow(strings.lang(), glyph = AppGlyph.characterBubble, tag = "settings_language", onClick = { onOpen(SettingsSheet.language) }) { SettingsRowValue(language.endonym) }
                MeDivider()
                SettingsIconRow(strings.theme(), glyph = themeGlyph(theme), tag = "settings_theme", onClick = { onOpen(SettingsSheet.theme) }) { SettingsRowValue(themeLabel(theme, strings)) }
                MeDivider()
                SettingsIconRow(strings.settingsAppPrefs(), glyph = AppGlyph.ruler, tag = "settings_app_prefs", onClick = { onOpen(SettingsSheet.appPreferences) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SettingsRowValue(strings.unitDistanceBadge(state.distanceUnit)); SettingsRowChevron()
                    }
                }
            }
            Column(Modifier.surfaceCard(16.dp)) {
                SettingsIconRow(strings.writeAuthor(), glyph = AppGlyph.envelopeFill, subtitle = strings.settingsWriteToAuthorSub(), tag = "settings_write_author",
                    onClick = onWriteAuthor, trailing = { SettingsRowChevron() }, stacksTrailing = false)
                MeDivider()
                SettingsIconRow(strings.telegramChannel(), icon = com.onezee.triptrack.R.drawable.telegram_icon, subtitle = strings.settingsTelegramSub(), tag = "settings_telegram",
                    onClick = onTelegram, trailing = { SettingsRowChevron() }, stacksTrailing = false)
                MeDivider()
                SettingsIconRow(strings.settingsSendLogs(), glyph = AppGlyph.ladybugFill, subtitle = strings.settingsSendLogsSub(), tag = "settings_logs",
                    onClick = { onOpen(SettingsSheet.logs) }, trailing = { SettingsRowChevron() }, stacksTrailing = false)
            }
            SettingsFooter(onPolicy, onTerms, onOpenStreetMap)
            if (state.failed) MeRow(strings.publicDataLoadFailed())
        }
    }
}

/** iOS AppPreferencesView.card: the units and the average speed; each shows its value and opens a picker. */
@Composable
internal fun SettingsPreferencesCard(state: SettingsState, onOpen: (SettingsSheet) -> Unit) {
    val strings = appStrings
    Column(Modifier.surfaceCard(16.dp)) {
        SettingsIconRow(strings.settingsUnits(), glyph = AppGlyph.ruler, tag = "settings_units", onClick = { onOpen(SettingsSheet.units) }) { SettingsRowValue(strings.unitDistanceBadge(state.distanceUnit)) }
        MeDivider()
        SettingsIconRow(strings.avgSpeedModeTitle(), glyph = AppGlyph.speedometer, tag = "settings_average_speed", onClick = { onOpen(SettingsSheet.averageSpeed) }) {
            SettingsRowValue(if (state.averageSpeed == AvgSpeedMode.overall) strings.avgSpeedOverall() else strings.avgSpeedMoving())
        }
    }
}

/**
 * iOS footer(): centred under the cards, 12 below them. The mark in the pixel face and the version, the two legal
 * links side by side (stacked at the accessibility sizes), the OpenStreetMap credit as a link and the Natural Earth
 * line as text: the licence names the first and asks nothing of the second.
 */
@Composable
private fun SettingsFooter(onPolicy: () -> Unit, onTerms: () -> Unit, onOpenStreetMap: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val accessibility = isAccessibilitySize()
    val haptics = rememberHaptics()
    val link = interScaled(11f, InterWeight.medium).copy(textDecoration = TextDecoration.Underline)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("settings_footer"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val mark = @Composable {
            Text(strings.tr(com.onezee.triptrack.designsystem.R.string.android_wordmark), style = pressStart2P(8f), color = AppTheme.accent)
            Text("· v${LocalAppVersion.current} · OneZee", style = interScaled(12f), color = colors.textSecondary)
        }
        if (accessibility) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) { mark() }
        else Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { mark() }
        val links = @Composable {
            Text(strings.privacyPolicy(), Modifier.clickable(role = Role.Button) { haptics.tap(); onPolicy() }.testTag("settings_privacy_policy"), style = link, color = colors.textSecondary, textAlign = TextAlign.Center)
            Text(strings.termsOfService(), Modifier.clickable(role = Role.Button) { haptics.tap(); onTerms() }.testTag("settings_terms"), style = link, color = colors.textSecondary, textAlign = TextAlign.Center)
        }
        if (accessibility) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) { links() }
        else Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) { links() }
        Text(strings.osmAttribution(), Modifier.clickable(role = Role.Button) { haptics.tap(); onOpenStreetMap() }.testTag("settings_osm_attribution"),
            style = interScaled(11f).copy(textDecoration = TextDecoration.Underline), color = colors.textSecondary, textAlign = TextAlign.Center)
        Text(strings.naturalEarthAttribution(), Modifier.testTag("settings_ne_attribution"), style = interScaled(11f), color = colors.textSecondary, textAlign = TextAlign.Center)
    }
}

/**
 * iOS: a sub-page of Настройки is a sheet of its own, headed like the settings sheet (22 bold, the grey 30 pt close
 * disc, 12 below it) with its cards 14 from the edges and 26 above the bottom.
 */
@Composable
internal fun SheetScope.SettingsSheetPage(title: String, maxContentHeight: androidx.compose.ui.unit.Dp? = null, content: @Composable ColumnScope.() -> Unit) {
    MeSheetHeader(title, 12.dp) { dismiss() }
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
        .padding(horizontal = 14.dp).padding(bottom = 26.dp)
        .then(maxContentHeight?.let { Modifier.heightIn(max = it).clipToBounds() } ?: Modifier), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

@Composable
internal fun SettingsDetail(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        CustomNavBar(title, onBack)
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
