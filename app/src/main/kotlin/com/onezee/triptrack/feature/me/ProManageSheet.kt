// iOS: ProManageSheet.swift, ProManageModel.swift
package com.onezee.triptrack.feature.me

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.R
import com.onezee.triptrack.feature.settings.MeRow
import com.onezee.triptrack.feature.settings.MeSheetHeader
import com.onezee.triptrack.model.ProfileBackground
import com.onezee.triptrack.model.PlusFeature
import java.time.Instant
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter

@Composable
internal fun ProManageSheet(identity: ProfileIdentity, until: Instant?, onDismiss: () -> Unit, onBackground: () -> Unit, onRedeem: () -> Unit,
                            onFutureFeature: (PlusFeature) -> Unit = {}) {
    val strings = appStrings
    val date = until?.let { LocalizedDateFormatter.templates("dMMMyyyy", strings.zone).getValue(strings.language).format(it) }
    ContentSizedSheet(onDismiss, background = AppTheme.colors.bg, detent = SheetDetent.large) {
        val sheet = this
        MeSheetHeader(strings.tr(R.string.pro_manage_title), { sheet.dismiss() })
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.tr(R.string.pro_works_until, date.orEmpty()), style = inter(14f), color = AppTheme.colors.textSecondary, modifier = Modifier.testTag("pro_manage_status"))
            Column(Modifier.surfaceCard(16.dp)) {
                MeRow(strings.tr(R.string.pro_feature_background), backgroundChoice(identity.effectiveBackground, strings), tag = "pro_manage_background", onClick = {
                    sheet.dismiss()
                    onBackground()
                })
                if (SHOW_FUTURE_PRO_FEATURES) {
                    MeRow("Avatar frame", tag = "pro_manage_avatar_frame", onClick = { sheet.dismiss(); onFutureFeature(PlusFeature.avatarFrame) })
                    MeRow("Vehicle card", tag = "pro_manage_vehicle_card", onClick = { sheet.dismiss(); onFutureFeature(PlusFeature.vehicleCardStyle) })
                    MeRow("Route line", tag = "pro_manage_route_line", onClick = { sheet.dismiss(); onFutureFeature(PlusFeature.routeLineStyle) })
                    MeRow("Manual trip", tag = "pro_manage_manual_trip", onClick = { sheet.dismiss(); onFutureFeature(PlusFeature.manualTrip) })
                }
            }
            Column(Modifier.surfaceCard(16.dp)) {
                MeRow(strings.tr(R.string.promo_another), tag = "pro_manage_redeem", onClick = {
                    sheet.dismiss()
                    onRedeem()
                })
            }
        }
    }
}

private fun backgroundChoice(value: ProfileBackground, strings: com.onezee.triptrack.designsystem.strings.AppStrings): String =
    value.displayNameOrDefault(strings)

private const val SHOW_FUTURE_PRO_FEATURES = false
