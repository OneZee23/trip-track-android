package com.onezee.triptrack.feature.social.ui

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.logic.distanceParts
import com.onezee.triptrack.logic.speedParts
import com.onezee.triptrack.ui.LocalTripPhotoImages
import com.onezee.triptrack.ui.PhotoSource
import com.onezee.triptrack.ui.TripAutoTitle
import java.io.File
import java.time.Duration

// iOS: StoryShareSheet.swift.
@Composable
fun TripPosterSheet(
    trip: TripSummary,
    authorName: String,
    shareUrl: String?,
    onDismiss: () -> Unit,
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var format by remember { mutableStateOf("poster") }
    val images = LocalTripPhotoImages.current
    val firstPhoto = trip.photos.firstOrNull()
    val photo by produceState<android.graphics.Bitmap?>(null, trip.id, firstPhoto?.id, images) {
        value = firstPhoto?.let { images?.load(PhotoSource.Local(it.filename), 2_073_600)?.asAndroidBitmap() }
    }
    var backgroundMode by remember { mutableStateOf(if (firstPhoto == null) "map" else "photo") }
    var copied by remember { mutableStateOf(false) }
    val unit = LocalDistanceUnit.current
    val duration = Duration.ofSeconds(trip.duration.toLong().coerceAtLeast(0))
    val shortDuration = buildString {
        if (duration.toHours() > 0) append("${duration.toHours()}ч ")
        append("${duration.toMinutes() % 60}м")
    }
    val title = trip.title?.trim()?.takeIf(String::isNotEmpty) ?: TripAutoTitle.localized(null, trip.startDate, strings) ?: strings.tripTitle()
    val distance = Measure.distanceParts(trip.distance, unit, strings.language.code, strings, Measure.Style.tenths)
    val speed = Measure.speedParts(trip.averageSpeed, unit, strings.language.code, strings)
    val spec = TripPosterSpec(title, "${distance.value} ${distance.unit}", shortDuration, "${speed.value} ${speed.unit}", trip.coordinates, trip.previewBreaks)
    var posterView by remember { mutableStateOf<TripPosterView?>(null) }
    ContentSizedSheet(onDismissRequest = onDismiss, background = colors.bg) {
        val sheet = this
        Column(Modifier.fillMaxWidth().testTag("trip_poster_sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.shareTripTitle(), Modifier.weight(1f), style = inter(19f, InterWeight.bold), color = colors.text)
                Box(Modifier.clip(RoundedCornerShape(50)).background(colors.card).clickable { sheet.dismiss() }.padding(10.dp)) {
                    Glyph(AppGlyph.xmark, strings.closeSheet(), tint = colors.textSecondary, size = 16.dp)
                }
            }
            ChipRow(
                values = buildList { add(strings.tabMap()); if (firstPhoto != null) add(strings.sharePosterPhoto()); add(strings.sharePosterMinimal()) },
                selected = buildList { add("map"); if (firstPhoto != null) add("photo"); add("minimal") }.indexOf(backgroundMode),
                onSelect = { index -> backgroundMode = buildList { add("map"); if (firstPhoto != null) add("photo"); add("minimal") }[index]; haptics.selection() }, testTag = "trip_poster_backgrounds",
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (format == "sticker") TransparencyChecker()
                AndroidView(
                    factory = { ctx -> TripPosterView(ctx).also { posterView = it } },
                    update = { it.spec = spec; it.backgroundMode = backgroundMode; it.photoBitmap = photo; it.transparent = format == "sticker"; posterView = it },
                    modifier = Modifier.width(when (format) { "story" -> 163.dp; "sticker" -> 290.dp; else -> 232.dp }).height(290.dp)
                        .clip(RoundedCornerShape(18.dp)).testTag("trip_poster_preview"),
                )
            }
            ChipRow(values = listOf(strings.sharePosterPoster(), strings.sharePosterStory(), strings.sharePosterStickerPng()),
                selected = listOf("poster", "story", "sticker").indexOf(format),
                onSelect = { format = listOf("poster", "story", "sticker")[it]; haptics.selection() }, testTag = "trip_poster_formats")
            Text(strings.shareCardCaption(), Modifier.fillMaxWidth().padding(horizontal = 24.dp), style = inter(12f), color = colors.textTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PosterAction(strings.share(), AppTheme.accent, Color.White, AppGlyph.squareAndArrowUp, Modifier.weight(1f), "trip_poster_share") {
                    val view = posterView ?: return@PosterAction
                    runCatching {
                        val bitmap = view.renderBitmap(1080, when (format) { "poster" -> 1350; "story" -> 1920; else -> 1080 })
                        val folder = File(File(context.cacheDir, "exports"), "trip-poster").apply { mkdirs() }
                        folder.listFiles()?.forEach(File::delete)
                        val file = File(folder, title.replace('/', '-').ifBlank { "TripTrack" } + ".png")
                        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TITLE, title)
                            if (shareUrl != null) putExtra(Intent.EXTRA_TEXT, shareUrl)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, title))
                    }.onFailure { Toast.makeText(context, strings.posterFailed(), Toast.LENGTH_SHORT).show() }
                }
                PosterAction(strings.save(), colors.card, colors.text, AppGlyph.squareAndArrowDown, Modifier.weight(1f), "trip_poster_save") {
                    val bitmap = posterView?.renderBitmap(1080, when (format) { "poster" -> 1350; "story" -> 1920; else -> 1080 }) ?: return@PosterAction
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "${title.replace('/', '-')}.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TripTrack")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }
                    runCatching {
                        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Image storage unavailable")
                        context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } ?: error("Image storage unavailable")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                        haptics.success()
                    }.onFailure { Toast.makeText(context, strings.posterFailed(), Toast.LENGTH_SHORT).show() }
                }
            }
            shareUrl?.let { url ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(colors.card).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(AppGlyph.squareAndArrowUp, null, tint = colors.textTertiary, size = 14.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(url, Modifier.weight(1f), style = inter(12f), color = colors.textSecondary, maxLines = 1)
                    Text(if (copied) strings.sharedTripLinkCopied() else strings.shareCopyLink(), Modifier.clickable {
                        com.onezee.triptrack.feature.social.ShareLinks.copy(context, url, title); copied = true
                    }, style = inter(12f, InterWeight.bold), color = if (copied) AppTheme.green else AppTheme.accent)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun TransparencyChecker() {
    Canvas(Modifier.width(290.dp).height(290.dp).clip(RoundedCornerShape(18.dp))) {
        val cell = 10.dp.toPx()
        drawRect(Color.White.copy(alpha = .9f))
        var row = 0
        var y = 0f
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(Color.Black.copy(alpha = .07f), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(cell, cell))
                x += cell * 2
            }
            y += cell
            row++
        }
    }
}

@Composable
private fun ChipRow(values: List<String>, selected: Int, onSelect: (Int) -> Unit, testTag: String) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth().testTag(testTag), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        values.forEachIndexed { index, label ->
            Box(Modifier.clip(RoundedCornerShape(50)).background(colors.card).clickable { onSelect(index) }
                .then(if (selected == index) Modifier.background(AppTheme.accent.copy(alpha = .08f), RoundedCornerShape(50)) else Modifier)
                .padding(horizontal = 13.dp, vertical = 8.dp)) {
                Text(label, style = inter(12.5f, if (selected == index) InterWeight.bold else InterWeight.semibold), color = if (selected == index) AppTheme.accent else colors.textSecondary)
            }
        }
    }
}

@Composable
private fun PosterAction(title: String, background: Color, ink: Color, glyph: AppGlyph, modifier: Modifier, tag: String, onClick: () -> Unit) {
    Row(modifier.height(46.dp).clip(RoundedCornerShape(14.dp)).background(background).clickable(onClick = onClick)
        .testTag(tag), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Glyph(glyph, null, tint = ink, size = 15.dp)
        Text(title, style = inter(15f, InterWeight.bold), color = ink)
    }
}
