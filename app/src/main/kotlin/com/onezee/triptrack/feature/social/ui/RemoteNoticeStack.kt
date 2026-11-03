// iOS: Views/RemoteConfig/RemoteNoticeStack.swift
package com.onezee.triptrack.feature.social.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.social.SocialFeature
import com.onezee.triptrack.network.RemoteConfig
import kotlinx.coroutines.CancellationException

@Composable
fun RemoteNoticeStack(social: SocialFeature, placement: RemoteConfig.Announcement.Placement, showsUpdate: Boolean, modifier: Modifier = Modifier) {
    val strings = appStrings
    val uriHandler = LocalUriHandler.current
    val config by produceState<RemoteConfig?>(null, social) {
        try { value = social.loadRemoteConfig() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = null }
    }
    val now = social.clock()
    val version = com.onezee.triptrack.BuildConfig.VERSION_NAME
    val need = if (showsUpdate) config?.updateNeed(version) else null
    val updateTarget = config?.update?.required ?: config?.update?.recommended
    val updateKey = "remoteConfig.dismissed"
    val dismissed = remember(social) { social.noticePreferences.getStringSet(updateKey, emptySet())?.toMutableSet() ?: mutableSetOf() }
    val notices = config?.announcements(placement, version, now).orEmpty().filterNot { "announcement:${it.id}" in dismissed }
    val showUpdate = need != null && (need == RemoteConfig.UpdateNeed.required || "update:$updateTarget" !in dismissed)
    if (!showUpdate && notices.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showUpdate) NoticeCard(
            strings.remoteUpdateTitle(), config?.update?.message?.text(strings.language.code) ?: strings.remoteUpdateBody(),
            strings.remoteUpdateAction(), if (need == RemoteConfig.UpdateNeed.required) null else ({
                dismissed += "update:$updateTarget"
                social.noticePreferences.edit().putStringSet(updateKey, dismissed.toSet()).apply()
            }), "remote_update_card",
            onAction = { uriHandler.openUri("https://play.google.com/store/apps/details?id=com.onezee.triptrack") },
        )
        notices.forEach { notice ->
            NoticeCard(notice.title.text(strings.language.code), notice.body.text(strings.language.code), notice.action?.label?.text(strings.language.code),
                onClose = {
                    val key = "announcement:${notice.id}"
                    dismissed += key
                    social.noticePreferences.edit().putStringSet(updateKey, dismissed.toSet()).apply()
                }, identifier = "remote_announcement_${notice.id}", onAction = notice.action?.let { action -> { uriHandler.openUri(action.url) } })
        }
    }
}

@Composable
private fun NoticeCard(title: String, body: String, actionLabel: String?, onClose: (() -> Unit)?, identifier: String, onAction: (() -> Unit)?) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp).testTag(identifier), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = inter(16f, InterWeight.bold), color = colors.text)
            if (onClose != null) Text("×", Modifier.size(36.dp).clickable(role = Role.Button, onClick = onClose), style = inter(18f, InterWeight.semibold), color = colors.textSecondary)
        }
        Text(body, style = inter(14f), color = colors.textSecondary)
        if (actionLabel != null && onAction != null) Text(actionLabel, Modifier.clickable(role = Role.Button, onClick = onAction).padding(vertical = 8.dp), style = inter(14f, InterWeight.semibold), color = AppTheme.accent)
    }
}
