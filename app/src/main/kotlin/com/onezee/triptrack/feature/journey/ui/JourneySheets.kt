package com.onezee.triptrack.feature.journey.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppConfirm
import com.onezee.triptrack.designsystem.components.AppDialogAction
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.NavCircleButton
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.strings.uppercased
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.points
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.journey.JourneyComposerViewModel
import com.onezee.triptrack.feature.journey.JourneyEditViewModel
import com.onezee.triptrack.feature.journey.JourneyPublishState
import com.onezee.triptrack.feature.journey.JourneyState
import com.onezee.triptrack.feature.trip.PhotoState
import com.onezee.triptrack.feature.trip.TripMessage
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.ui.PhotoImage
import com.onezee.triptrack.ui.PhotoSource
import com.onezee.triptrack.ui.PrimaryButton
import com.onezee.triptrack.ui.cards.dayMonth
import com.onezee.triptrack.ui.confirmationAction
import com.onezee.triptrack.ui.confirmationMessage
import com.onezee.triptrack.ui.confirmationTitle
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.UUID

/** The text fields of the journey sheets: Inter 16 semibold on the card-alt ground. */
@Composable
private fun SheetTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, testTag: String) {
    val colors = AppTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = inter(16f, InterWeight.semibold).copy(color = colors.text),
        cursorBrush = SolidColor(AppTheme.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        decorationBox = { field ->
            Box(Modifier.fillMaxWidth().background(colors.cardAlt, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (value.isEmpty()) Text(placeholder, style = inter(16f, InterWeight.semibold), color = colors.textTertiary, maxLines = 1)
                field()
            }
        },
    )
}

@Composable
private fun SheetHeader(title: String, onClose: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    val strings = appStrings
    val haptics = rememberHaptics()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke()
        Text(title, style = inter(19f, InterWeight.bold), color = AppTheme.colors.text, maxLines = 2, modifier = Modifier.weight(1f))
        NavCircleButton(AppGlyph.xmark, strings.close(), onClick = { haptics.tap(); onClose() }, modifier = Modifier.testTag("journey_sheet_close"))
    }
}

/**
 * iOS: JourneyComposerSheet.swift. «Собрать путешествие»: the trips of a week around the anchor,
 * grouped by day; the chain around it ticked, the neighbours shown unticked (a ticked window is
 * someone else's work, a ticked chain is an answer to correct with one tap). One sheet for every
 * entry into it.
 */
@Composable
fun JourneyComposerSheet(vm: JourneyComposerViewModel, onCreated: (JourneyState) -> Unit, onDismissRequest: () -> Unit) {
    val state by vm.state.collectAsState()
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val unit = LocalDistanceUnit.current
    ContentSizedSheet(onDismissRequest = onDismissRequest, background = colors.bg) {
        val sheet = this
        LaunchedEffect(state.created) {
            state.created?.let { haptics.success(); onCreated(it); sheet.dismiss() }
        }
        Column(Modifier.padding(20.dp).testTag("journey_composer"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SheetHeader(strings.journeyComposeTitle(), onClose = { sheet.dismiss() }, leading = {
                Box(Modifier.size(28.dp).background(AppTheme.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Glyph(AppGlyph.suitcase, contentDescription = null, tint = Color.White, size = 16.dp)
                }
            })
            Text(strings.journeyComposeHint(), style = inter(12f, InterWeight.medium), color = colors.textTertiary)
            // The list gives way to the keyboard: while the name is typed, the field and «Создать» stay
            // in sight and the trips scroll in what is left.
            Column(Modifier.weight(1f, fill = false).heightIn(max = 280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val groups = state.candidates.groupBy { it.startDate.atZone(strings.zone).toLocalDate() }
                groups.entries.forEachIndexed { index, (_, trips) ->
                    Text(JourneyFormat.dayDate(trips.first().startDate, strings).uppercased(strings.language), style = inter(11f, InterWeight.bold),
                        letterSpacing = points(0.4f), color = colors.textTertiary, modifier = Modifier.padding(top = if (index == 0) 0.dp else 6.dp))
                    trips.forEach { trip ->
                        val ticked = trip.id in state.selected
                        val anchor = trip.id == vm.anchorId
                        PressableCard(onClick = { haptics.selection(); vm.toggle(trip.id) },
                            modifier = Modifier.semantics { selected = ticked }.testTag("journey_candidate")) {
                            val time = JourneyFormat.time(trip.startDate, strings) + (trip.endDate?.let { "–" + JourneyFormat.time(it, strings) } ?: "")
                            val km = Measure.distance(trip.distance, unit, strings.language.code, strings, Measure.Style.grouped)
                            JourneyLegRow(
                                trip, "$time · $km",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp)
                                    .background(colors.cardAlt, RoundedCornerShape(12.dp))
                                    .then(if (anchor) Modifier.border(1.dp, AppTheme.accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp)) else Modifier)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                caption = if (anchor) strings.journeyComposeAnchor() else null,
                            ) {
                                Glyph(if (ticked) AppGlyph.checkmarkCircleFill else AppGlyph.circle, contentDescription = null,
                                    tint = if (ticked) AppTheme.accent else colors.textTertiary, size = 24.dp)
                            }
                        }
                    }
                }
            }
            val ticked = state.candidates.filter { it.id in state.selected }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(AppGlyph.map, contentDescription = null, tint = if (ticked.isEmpty()) colors.textTertiary else AppTheme.accent, size = 15.dp)
                val summary = if (ticked.isEmpty()) strings.journeyEmptyTitle() else listOf(
                    "${ticked.size} ${strings.nounTrips(ticked.size)}",
                    Measure.distance(state.selectedMetres, unit, strings.language.code, strings, Measure.Style.grouped),
                    JourneyFormat.dateRange(ticked.minOf { it.startDate }, ticked.maxOf { it.endDate ?: it.startDate }, strings),
                ).joinToString(" · ")
                Text(summary, style = inter(13.5f, InterWeight.bold), color = if (ticked.isEmpty()) colors.textTertiary else colors.text, maxLines = 1,
                    modifier = Modifier.testTag("journey_composer_summary"))
            }
            SheetTextField(state.title, vm::setTitle, strings.journeyTitlePlaceholder(), "journey_composer_title")
            if (state.message == TripMessage.journeyOverlaps) {
                Text(strings.journeyOverlaps(), style = inter(12f, InterWeight.semibold), color = AppTheme.red)
            }
            PrimaryButton(strings.journeyCreate(), onClick = { vm.create() }, enabled = state.canCreate, modifier = Modifier.testTag("journey_composer_create"))
        }
    }
}

/**
 * iOS: JourneyEditSheet.swift. The name, the window's dates (past only, a live count of the trips
 * inside, Save off at zero once the dates moved), the removed legs to put back, the cover among the
 * legs' photos, and deletion. Nothing is written until Save.
 */
@Composable
fun JourneyEditSheet(vm: JourneyEditViewModel, photos: List<PhotoState>, onFinished: () -> Unit, onDismissRequest: () -> Unit) {
    val state by vm.state.collectAsState()
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val unit = LocalDistanceUnit.current
    var picking by remember { mutableStateOf<Boolean?>(null) } // true: start, false: end
    ContentSizedSheet(onDismissRequest = onDismissRequest, background = colors.bg) {
        val sheet = this
        LaunchedEffect(state.saved) { if (state.saved) { haptics.success(); onFinished(); sheet.dismiss() } }
        Column(Modifier.padding(20.dp).animateContentSize().testTag("journey_edit"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SheetHeader(strings.journeyEdit(), onClose = { sheet.dismiss() })
            SheetTextField(state.title, vm::setTitle, strings.journeyTitlePlaceholder(), "journey_edit_title")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caption(strings.journeyDates())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    DatePill(state.start, "journey_edit_start") { picking = true }
                    Text("–", style = inter(14f, InterWeight.bold), color = colors.textTertiary)
                    DatePill(state.end, "journey_edit_end") { picking = false }
                }
                Text(
                    if (state.windowTripCount == 0) strings.journeyDatesEmpty()
                    else "${state.windowTripCount} ${strings.nounTrips(state.windowTripCount)} ${strings.journeyInDates()}",
                    style = inter(12f, InterWeight.semibold),
                    color = if (state.windowTripCount == 0) AppTheme.red else colors.textSecondary,
                    modifier = Modifier.testTag("journey_edit_window_count"),
                )
                if (state.message == TripMessage.journeyOverlaps) {
                    Text(strings.journeyOverlaps(), style = inter(12f, InterWeight.semibold), color = AppTheme.red, modifier = Modifier.testTag("journey_edit_error"))
                }
            }
            if (state.removedLegs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Caption(strings.journeyRemovedLegs())
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.removedLegs.forEach { leg ->
                            val returning = leg.id in state.returningIds
                            PressableCard(onClick = { haptics.selection(); vm.toggleReturning(leg.id) },
                                modifier = Modifier.semantics { contentDescription = "${strings.journeyReturnLeg()}, ${JourneyFormat.tripTitle(leg, strings)}"; selected = returning }
                                    .testTag("journey_edit_return_leg")) {
                                Row(
                                    Modifier
                                        .background(colors.cardAlt, RoundedCornerShape(12.dp))
                                        .border(2.dp, if (returning) AppTheme.accent else Color.Transparent, RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LegThumbnail(leg)
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(JourneyFormat.dayDate(leg.startDate, strings), style = inter(12.5f, InterWeight.bold), color = colors.text, maxLines = 1)
                                        Text(Measure.distance(leg.distance, unit, strings.language.code, strings, Measure.Style.grouped),
                                            style = inter(11.5f, InterWeight.medium), color = colors.textTertiary, maxLines = 1)
                                    }
                                    Row(
                                        Modifier.background(if (returning) AppTheme.accent else AppTheme.accent.copy(alpha = 0.14f), CapsuleShape)
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Glyph(if (returning) AppGlyph.checkmark else AppGlyph.arrowClockwise, contentDescription = null,
                                            tint = if (returning) Color.White else AppTheme.accent, size = 11.dp)
                                        Text(strings.journeyReturnLeg(), style = inter(10.5f, InterWeight.bold), color = if (returning) Color.White else AppTheme.accent, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (photos.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Caption(strings.journeyCover())
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.forEach { photo ->
                            val chosen = photo.id == state.coverPhotoId
                            val shape = RoundedCornerShape(10.dp)
                            PressableCard(onClick = { haptics.selection(); vm.toggleCover(photo.id) },
                                modifier = Modifier.semantics { selected = chosen }.testTag("journey_edit_cover")) {
                                Box(Modifier.size(74.dp).clip(shape)) {
                                    PhotoImage(PhotoSource.Local(photo.filename), maxPixels = 240, modifier = Modifier.size(74.dp))
                                    Box(Modifier.size(74.dp).border(if (chosen) 3.dp else 1.dp, if (chosen) AppTheme.accent else colors.border, shape))
                                    if (chosen) Text(strings.journeyCover(), style = inter(9f, InterWeight.bold), color = Color.White, maxLines = 1,
                                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).background(AppTheme.accent, CapsuleShape)
                                            .padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
            PrimaryButton(strings.save(), onClick = { vm.save() }, enabled = state.canSave && !state.isSaving, modifier = Modifier.testTag("journey_edit_save"))
            PrimaryButton(strings.journeyDelete(), onClick = { haptics.tap(); vm.askDelete() }, fill = AppTheme.red.copy(alpha = 0.10f), ink = AppTheme.red,
                height = 46.dp, radius = 12.dp, weight = InterWeight.semibold, modifier = Modifier.testTag("journey_edit_delete"))
        }
        val confirmation = state.confirmation
        AppConfirm(
            item = confirmation,
            onDismissRequest = vm::cancelDelete,
            title = { strings.confirmationTitle(it) },
            message = { strings.confirmationMessage(it) },
            actions = { c -> listOf(AppDialogAction(strings.confirmationAction(c), AppDialogAction.Kind.destructive, testTag = "journey_edit_delete_confirm") { vm.acceptDelete(c.id) }) },
        )
        picking?.let { start ->
            HouseDatePickerSheet(
                selection = if (start) state.start else state.end,
                bounds = if (start) state.startBounds else state.endBounds,
                zone = strings.zone,
                onPick = { if (start) vm.setStart(it) else vm.setEnd(it) },
                onDismissRequest = { picking = null },
            )
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text.uppercased(appStrings.language), style = inter(12f, InterWeight.bold), color = AppTheme.colors.textTertiary)
}

/** iOS: the compact DatePicker: the date on a pill in accent that opens the calendar. */
@Composable
private fun DatePill(date: Instant, tag: String, onClick: () -> Unit) {
    val strings = appStrings
    Text(
        strings.dayMonth(date) + " " + date.atZone(strings.zone).year,
        style = inter(15f, InterWeight.medium),
        color = AppTheme.accent,
        maxLines = 1,
        modifier = Modifier
            .background(AppTheme.colors.cardAlt, RoundedCornerShape(8.dp))
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .testTag(tag),
    )
}

/**
 * The house calendar: a month at a time, the days outside [bounds] dimmed and inert. iOS shows its
 * system calendar popover here; a sheet of our own keeps the look (house rules: pickers are sheets).
 */
@Composable
internal fun HouseDatePickerSheet(selection: Instant, bounds: ClosedRange<Instant>, zone: java.time.ZoneId, onPick: (Instant) -> Unit, onDismissRequest: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val selected = selection.atZone(zone).toLocalDate()
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val first = bounds.start.atZone(zone).toLocalDate()
    val last = bounds.endInclusive.atZone(zone).toLocalDate()
    ContentSizedSheet(onDismissRequest = onDismissRequest, background = colors.card) {
        val sheet = this
        Column(Modifier.padding(20.dp).testTag("date_picker"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.monthYear(month.atDay(1).atStartOfDay(zone).toInstant()), style = inter(17f, InterWeight.bold), color = colors.text, modifier = Modifier.weight(1f))
                NavCircleButton(AppGlyph.chevronLeft, strings.back(), onClick = { if (month.atDay(1) > first.withDayOfMonth(1)) month = month.minusMonths(1) })
                Spacer(Modifier.width(8.dp))
                NavCircleButton(AppGlyph.chevronRight, strings.back(), onClick = { if (month.atEndOfMonth() < last) month = month.plusMonths(1) })
            }
            val firstDay = month.atDay(1)
            val weekStart = java.time.temporal.WeekFields.of(strings.language.locale).firstDayOfWeek
            val lead = (firstDay.dayOfWeek.value - weekStart.value + 7) % 7
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    Text(weekStart.plus(i.toLong()).getDisplayName(TextStyle.SHORT, strings.language.locale), style = inter(11f, InterWeight.semibold),
                        color = colors.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            val cells = List(lead) { null as LocalDate? } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                            if (day != null) {
                                val enabled = day >= first && day <= last
                                val isSelected = day == selected
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .background(if (isSelected) AppTheme.accent else Color.Transparent, CircleShape)
                                        .clickable(interactionSource = null, indication = null, enabled = enabled, role = Role.Button) {
                                            haptics.selection()
                                            // Keep the time of day the picker came with: only the date moves.
                                            val time = selection.atZone(zone).toLocalTime()
                                            onPick(day.atTime(time).atZone(zone).toInstant())
                                            sheet.dismiss()
                                        }
                                        .testTag("date_picker_day"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("${day.dayOfMonth}", style = inter(15f, if (isSelected) InterWeight.bold else InterWeight.medium),
                                        color = when { isSelected -> Color.White; enabled -> colors.text; else -> colors.textTertiary.copy(alpha = 0.5f) })
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * iOS: JourneyPublishSheet.swift (S5). What publishing will open: the journey becomes visible and
 * every leg that is private now opens with it, named here one by one; the legs already public are
 * only counted.
 */
@Composable
fun JourneyPublishSheet(title: String, publish: JourneyPublishState, onConfirm: () -> Unit, onDismissRequest: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val unit = LocalDistanceUnit.current
    ContentSizedSheet(onDismissRequest = onDismissRequest, background = colors.card) {
        val sheet = this
        Column(Modifier.padding(20.dp).testTag("journey_publish_sheet"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SheetHeader(strings.journeyPublishTitle(), onClose = { sheet.dismiss() })
            Text(strings.journeyPublishIntro(title), style = inter(14f, InterWeight.medium), color = colors.textSecondary)
            if (publish.privateLegs.isNotEmpty()) {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    publish.privateLegs.forEach { leg ->
                        val subtitle = "${strings.dayMonth(leg.startDate)} · ${Measure.distance(leg.distance, unit, strings.language.code, strings, Measure.Style.grouped)} · ${strings.journeyLegPrivateNow()}"
                        JourneyLegRow(leg, subtitle, Modifier.fillMaxWidth().background(colors.cardAlt, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp))
                    }
                }
            }
            if (publish.alreadyPublic > 0) {
                Text(strings.journeyPublishAlreadyPublic(publish.alreadyPublic), style = inter(13f, InterWeight.medium), color = colors.textTertiary)
            }
            PrimaryButton(
                if (publish.privateLegs.isEmpty()) strings.journeyPublishButtonNoLegs() else strings.journeyPublishButton(publish.privateLegs.size),
                onClick = { haptics.tap(); onConfirm(); sheet.dismiss() },
                modifier = Modifier.testTag("journey_publish_confirm"),
            )
            Text(
                strings.cancel(),
                style = inter(15f, InterWeight.semibold),
                color = AppTheme.accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .clickable(interactionSource = null, indication = null, role = Role.Button) { haptics.tap(); sheet.dismiss() }
                    .padding(vertical = 6.dp)
                    .testTag("journey_publish_cancel"),
            )
        }
    }
}
