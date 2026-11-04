package com.onezee.triptrack.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.R
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.logic.distanceParts
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.feature.onboarding.OnboardingHero
import com.onezee.triptrack.R as AppR

// iOS: PrivacyEdgeQuestionSheet.swift.
@Composable
fun PrivacyEdgeQuestionSheet(onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    var selected by remember { mutableStateOf<Int?>(null) }
    val options = listOf(0 to (R.string.edgeOptionNone to null), 200 to (0 to R.string.edgeOption200Note),
        500 to (0 to R.string.edgeOption500Note), 1000 to (0 to R.string.edgeOption1000Note))
    val unit = LocalDistanceUnit.current
    ContentSizedSheet(onDismissRequest = onDismiss, background = colors.card, detent = com.onezee.triptrack.designsystem.components.SheetDetent.large) {
        val sheet = this
        // The question scrolls and «Сохранить» stays under it: on the POCO F1 (393 × 780 dp with a
        // three-button bar) the button fell below the screen and the choice could not be saved.
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp).testTag("privacy_edge_question"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnboardingHero(AppR.drawable.hidden_edges_illustration, top = 8.dp)
            Text(strings.tr(R.string.edgeQuestionTitle), style = inter(20f, InterWeight.bold), color = colors.text)
            Text(strings.tr(R.string.edgeQuestionBody), style = inter(14f), color = colors.textSecondary)
            options.forEach { (metres, labels) ->
                val active = selected == metres
                Row(Modifier.fillMaxWidth().background(if (active) AppTheme.accent.copy(alpha = .12f) else colors.cardAlt, RoundedCornerShape(14.dp))
                    .clickable(role = Role.RadioButton) { selected = metres }.padding(14.dp).testTag("privacy_edge_choice_$metres"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val distance = Measure.distanceParts(metres.toDouble(), unit, strings.language.code, strings)
                        Text(if (metres == 0) strings.tr(requireNotNull(labels.first)) else "${distance.value} ${distance.unit}", style = inter(14f, if (active) InterWeight.bold else InterWeight.medium), color = colors.text)
                        labels.second?.let { Text(strings.tr(it), style = inter(12f), color = colors.textSecondary) }
                    }
                    Text(if (active) "●" else "○", style = inter(17f), color = if (active) AppTheme.accent else colors.textTertiary)
                }
            }
            Text(strings.tr(R.string.edgeQuestionFootnote), style = inter(12f), color = colors.textTertiary)
        }
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().background(if (selected == null) colors.cardAlt else AppTheme.accent, RoundedCornerShape(50))
                .clickable(enabled = selected != null, role = Role.Button) { selected?.let(onSave); sheet.dismiss() }
                .padding(vertical = 15.dp).testTag("privacy_edge_save"), horizontalArrangement = Arrangement.Center) {
                Text(strings.save(), style = inter(15f, InterWeight.bold), color = if (selected == null) colors.textTertiary else androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}
