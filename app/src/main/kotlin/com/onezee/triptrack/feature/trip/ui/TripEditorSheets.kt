package com.onezee.triptrack.feature.trip.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.trip.CheckpointState
import com.onezee.triptrack.feature.trip.PhotoState
import com.onezee.triptrack.feature.trip.SegmentState
import com.onezee.triptrack.ui.CheckpointReading
import com.onezee.triptrack.ui.PhotoImage
import com.onezee.triptrack.ui.PhotoSource
import java.util.UUID

/** iOS: CheckpointEditorSheet.maxNameLength, shared by the segment's name: 40 characters as people count them. */
const val MAX_NAME_LENGTH = 40

private val grapheme = Regex("\\X")

/** The first [limit] characters as people count them: an emoji with its modifiers is one. */
fun graphemePrefix(text: String, limit: Int): String = grapheme.findAll(text).take(limit).joinToString("") { it.value }
fun graphemeCount(text: String): Int = grapheme.findAll(text).count()

/**
 * The name field of the editors (iOS CheckpointEditorSheet / SegmentEditorSheet): Inter 16 on the
 * card-alt ground, cut at 40, with the counter from 30 on, red at the limit.
 */
@Composable
fun NameField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, testTag: String = "name_field") {
    val colors = AppTheme.colors
    val count = graphemeCount(value)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(graphemePrefix(it, MAX_NAME_LENGTH)) },
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
        AnimatedVisibility(count >= 30) {
            Text("$count/$MAX_NAME_LENGTH", style = inter(11f, InterWeight.medium, monospacedDigits = true),
                color = if (count >= MAX_NAME_LENGTH) AppTheme.red else colors.textTertiary, modifier = Modifier.padding(end = 4.dp))
        }
    }
}

/** A wide 46 button of the editors: the segment entry in accent, deletion in red, each on its own wash. */
@Composable
private fun WideButton(text: String, ink: Color, fill: Color, glyph: AppGlyph? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PressableCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(46.dp).background(fill, RoundedCornerShape(12.dp)),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) Glyph(glyph, contentDescription = null, tint = ink, size = 16.dp)
            Text(text, style = inter(15f, InterWeight.semibold), color = ink)
        }
    }
}

@Composable
private fun NumberBadge(number: Int) {
    Box(Modifier.size(28.dp).background(AppTheme.accent, CircleShape), contentAlignment = Alignment.Center) {
        Text("$number", style = inter(13f, InterWeight.bold, monospacedDigits = true), color = Color.White)
    }
}

/**
 * iOS: CheckpointEditorSheet.swift. Everything about a checkpoint in one sheet: its name, the cover
 * and attached photos, «Отрезок до…» (which turns the SAME sheet into a list of the other
 * checkpoints, never a sheet over a sheet), and deletion. Closing saves.
 *
 * [onSave] receives the name only when the person changed it, so a name the geocoder filled in
 * meanwhile is not overwritten with the old one; it is not called when nothing changed.
 */
@Composable
fun CheckpointEditorSheet(
    checkpoint: CheckpointState,
    tripPhotos: List<PhotoState>,
    otherCheckpoints: List<CheckpointState>,
    canCreateSegment: Boolean,
    onSave: (name: String?, nameEdited: Boolean, coverId: UUID?, photoIds: List<UUID>) -> Unit,
    onDelete: () -> Unit,
    onCreateSegment: (UUID) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val initialName = checkpoint.name.orEmpty()
    var name by remember { mutableStateOf(initialName) }
    var cover by remember { mutableStateOf(checkpoint.coverPhotoId) }
    val attached = remember { mutableStateListOf<UUID>().apply { addAll(checkpoint.attachedPhotoIds) } }
    var picking by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val nearby = checkpoint.photos
    val all = (nearby + tripPhotos).distinctBy { it.id }
    val byId = all.associateBy { it.id }
    // Shelf one: cover, then the attached in attach order, then what time or place linked.
    val linked = (listOfNotNull(cover) + attached + nearby.map { it.id }).distinct().mapNotNull(byId::get)
    val addable = tripPhotos.filter { photo -> linked.none { it.id == photo.id } }

    fun commit() {
        if (finished) return
        finished = true
        val trimmed = name.trim()
        val edited = trimmed != initialName
        if (edited || cover != checkpoint.coverPhotoId || attached.toList() != checkpoint.attachedPhotoIds) {
            onSave(trimmed.ifEmpty { null }, edited, cover, attached.toList())
        }
    }

    ContentSizedSheet(onDismissRequest = { commit(); onDismissRequest() }, background = colors.bg) {
        val sheet = this
        Column(Modifier.padding(20.dp).animateContentSize().testTag("checkpoint_editor"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (picking) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NavCircleButton(AppGlyph.chevronLeft, strings.back(), onClick = { haptics.tap(); picking = false })
                    Text(strings.segmentPickTitle(), style = inter(19f, InterWeight.bold), color = colors.text, maxLines = 1, modifier = Modifier.weight(1f))
                }
                Column(
                    Modifier.heightIn(max = minOf(otherCheckpoints.size * 64, 380).dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    otherCheckpoints.forEach { other ->
                        PressableCard(onClick = {
                            haptics.selection()
                            commit()
                            onCreateSegment(other.id)
                            sheet.dismiss()
                        }, modifier = Modifier.testTag("segment_pick_row")) {
                            Row(
                                Modifier.fillMaxWidth().background(colors.cardAlt, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                NumberBadge(other.number)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(other.displayName(strings), style = inter(15f, InterWeight.semibold), color = colors.text, maxLines = 1)
                                    Text(CheckpointReading.text(other.elapsed, other.metres, LocalDistanceUnit.current, strings),
                                        style = inter(12f, InterWeight.semibold, monospacedDigits = true), color = colors.textSecondary, maxLines = 1)
                                }
                                Glyph(AppGlyph.chevronRight, contentDescription = null, tint = colors.textTertiary, size = 15.dp)
                            }
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberBadge(checkpoint.number)
                    Text(strings.checkpointWord(), style = inter(19f, InterWeight.bold), color = colors.text, modifier = Modifier.weight(1f))
                    NavCircleButton(AppGlyph.xmark, strings.close(), onClick = { haptics.tap(); sheet.dismiss() }, modifier = Modifier.testTag("checkpoint_close"))
                }
                NameField(name, { name = it }, strings.checkpointNamePlaceholder(), testTag = "checkpoint_name")
                if (linked.isNotEmpty()) PhotoShelf(strings.checkpointPhotosNearby()) {
                    linked.forEachIndexed { index, photo ->
                        val isCover = index == 0
                        val manual = photo.id in attached || photo.id == cover
                        LinkedChip(photo, isCover, manual,
                            onTap = { if (photo.id != cover && !isCover) { haptics.selection(); cover = photo.id } },
                            onRemove = { haptics.tap(); attached.remove(photo.id); if (cover == photo.id) cover = null })
                    }
                }
                if (addable.isNotEmpty()) PhotoShelf(strings.checkpointPhotosOther()) {
                    addable.forEach { photo -> AddableChip(photo) { haptics.selection(); attached.add(photo.id) } }
                }
                if (canCreateSegment && otherCheckpoints.isNotEmpty()) {
                    WideButton(strings.segmentTo(), AppTheme.accent, AppTheme.accentBg, AppGlyph.arrowTriangleSwap, Modifier.testTag("checkpoint_segment_to")) {
                        haptics.tap(); picking = true
                    }
                }
                WideButton(strings.checkpointDelete(), AppTheme.red, AppTheme.red.copy(alpha = 0.10f), modifier = Modifier.testTag("checkpoint_delete")) {
                    haptics.tap(); confirmingDelete = true
                }
            }
        }
        AppConfirm(
            isPresented = confirmingDelete,
            onDismissRequest = { confirmingDelete = false },
            title = strings.checkpointDelete(),
            actions = listOf(AppDialogAction(strings.delete(), AppDialogAction.Kind.destructive, testTag = "checkpoint_delete_confirm") {
                finished = true
                onDelete()
                sheet.dismiss()
            }),
        )
    }
}

@Composable
private fun PhotoShelf(title: String, content: @Composable () -> Unit) {
    val strings = appStrings
    Column(Modifier.animateContentSize(spring(dampingRatio = 0.85f, stiffness = 440f)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercased(strings.language), style = inter(12f, InterWeight.bold), color = AppTheme.colors.textTertiary)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 1.dp, end = 10.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun LinkedChip(photo: PhotoState, isCover: Boolean, manual: Boolean, onTap: () -> Unit, onRemove: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box {
        PressableCard(
            onClick = onTap,
            modifier = Modifier.semantics { contentDescription = if (isCover) "${strings.nounPhotos(1)}, ${strings.checkpointCover()}" else strings.nounPhotos(1); selected = isCover }
                .testTag("checkpoint_linked_photo"),
        ) {
            Box(Modifier.size(64.dp).clip(shape)) {
                PhotoImage(PhotoSource.Local(photo.filename), maxPixels = 200, modifier = Modifier.size(64.dp))
                if (isCover) {
                    Text(strings.checkpointCover(), style = inter(9f, InterWeight.bold), color = Color.White, maxLines = 1,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).background(AppTheme.accent, CapsuleShape).padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Box(Modifier.size(64.dp).border(if (isCover) 3.dp else 1.dp, if (isCover) AppTheme.accent else colors.border, shape))
            }
        }
        if (manual) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(6.dp, (-6).dp)
                    .size(20.dp)
                    .background(colors.text, CircleShape)
                    .border(2.dp, colors.bg, CircleShape)
                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onRemove)
                    .semantics { contentDescription = strings.delete() }
                    .testTag("checkpoint_unlink_photo"),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(AppGlyph.xmark, contentDescription = null, tint = colors.bg, size = 11.dp)
            }
        }
    }
}

@Composable
private fun AddableChip(photo: PhotoState, onAdd: () -> Unit) {
    val strings = appStrings
    val shape = RoundedCornerShape(10.dp)
    PressableCard(onClick = onAdd, modifier = Modifier.semantics { contentDescription = "${strings.checkpointAttachPhoto()}: ${strings.nounPhotos(1)}" }
        .testTag("checkpoint_addable_photo")) {
        Box(Modifier.size(64.dp).clip(shape)) {
            PhotoImage(PhotoSource.Local(photo.filename), maxPixels = 200, modifier = Modifier.size(64.dp).alpha(0.8f))
            Box(Modifier.size(64.dp).border(1.dp, AppTheme.colors.border, shape))
            Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp).background(AppTheme.accent, CircleShape), contentAlignment = Alignment.Center) {
                Glyph(AppGlyph.plus, contentDescription = null, tint = Color.White, size = 12.dp)
            }
        }
    }
}

/**
 * iOS: SegmentEditorSheet.swift. The name of a segment («A → B» when it has none) and its deletion.
 * Time and kilometres are not shown here: they are read off the timeline. Closing saves a changed name.
 */
@Composable
fun SegmentEditorSheet(
    segment: SegmentState,
    title: String,
    onSave: (String?) -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val initial = segment.name.orEmpty()
    var name by remember { mutableStateOf(initial) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    fun commit() {
        if (finished) return
        finished = true
        val trimmed = name.trim()
        if (trimmed != initial) onSave(trimmed.ifEmpty { null })
    }
    ContentSizedSheet(onDismissRequest = { commit(); onDismissRequest() }, background = colors.bg) {
        val sheet = this
        Column(Modifier.padding(20.dp).testTag("segment_editor"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(strings.checkpointLeg().uppercased(strings.language), style = inter(12f, InterWeight.bold), color = AppTheme.accent)
                    Text(title, style = inter(19f, InterWeight.bold), color = colors.text, maxLines = 2)
                }
                Spacer(Modifier.size(6.dp))
                NavCircleButton(AppGlyph.xmark, strings.close(), onClick = { haptics.tap(); sheet.dismiss() }, modifier = Modifier.testTag("segment_close"))
            }
            NameField(name, { name = it }, strings.segmentNameField(), testTag = "segment_name")
            WideButton(strings.segmentDelete(), AppTheme.red, AppTheme.red.copy(alpha = 0.10f), modifier = Modifier.testTag("segment_delete")) {
                haptics.tap(); confirmingDelete = true
            }
        }
        AppConfirm(
            isPresented = confirmingDelete,
            onDismissRequest = { confirmingDelete = false },
            title = strings.segmentDeleteConfirm(),
            actions = listOf(AppDialogAction(strings.delete(), AppDialogAction.Kind.destructive, testTag = "segment_delete_confirm") {
                finished = true
                onDelete()
                sheet.dismiss()
            }),
        )
    }
}
