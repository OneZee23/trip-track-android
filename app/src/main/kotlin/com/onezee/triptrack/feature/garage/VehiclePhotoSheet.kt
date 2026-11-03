// iOS: VehiclePhotosView.swift, VehiclePhotoImage.swift, PhotoFullScreenView.swift
package com.onezee.triptrack.feature.garage

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.onezee.triptrack.designsystem.navigation.tabBarContentClearance
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.data.VehiclePhoto
import com.onezee.triptrack.data.VehiclePhotoStore
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.feature.me.MePhotoViewer
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.*

/**
 * iOS VehiclePhotosView (pushed from the car's photo, never a sheet): the bar with «Фото» over the car's name and an accent
 * «+» at the end, the main photo across the width (180 high, radius 16, «Главная» on it), the others in three columns of 104
 * with radius 12, 8 apart, and a hint. A tap opens the photo; making it the main one and deleting it are in the viewer.
 * Empty, it is a real empty state: the drawing, the words and the one action.
 */
@Composable
internal fun VehiclePhotosScreen(id: UUID, vehicleName: String, store: VehiclePhotoStore, onBack: () -> Unit, onChanged: () -> Unit, onShowPhotos: suspend () -> Unit = {}) {
    val strings = appStrings
    val colors = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photos by remember(id) { mutableStateOf<List<VehiclePhoto>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Pair<List<VehiclePhoto>, Int>?>(null) }
    var delete by remember { mutableStateOf<VehiclePhoto?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var askVisibilityOpen by remember { mutableStateOf(false) }
    var loaded by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id, revision) {
        try { photos = withContext(Dispatchers.IO) { store.photos(id) }; loaded = true; onChanged() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isNotEmpty() && !busy) scope.launch {
            busy = true
            val hadNone = photos.isEmpty()
            try {
                withContext(Dispatchers.IO) {
                    for (uri in uris) {
                        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            val scale = minOf(1.0, 1440.0 / maxOf(info.size.width, info.size.height))
                            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                        }
                        val output = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, com.onezee.triptrack.data.files.VehiclePhotoFiles.JPEG_QUALITY, output)
                        bitmap.recycle()
                        checkNotNull(store.add(output.toByteArray(), id))
                    }
                }
                revision++
                val prefs = context.getSharedPreferences("${context.packageName}_preferences", android.content.Context.MODE_PRIVATE)
                val askKey = "vehiclePhotoVisibilityAsked.$id"
                if (hadNone && !prefs.getBoolean(askKey, false)) {
                    prefs.edit().putBoolean(askKey, true).apply()
                    askVisibilityOpen = true
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    VehiclePhotosPage(vehicleName, photos, loaded, failed, busy, onBack, onAdd = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onOpen = { index -> selected = photos.toList() to index })
    if (askVisibilityOpen) AppConfirmDialog(strings.vehiclePhotoAskTitle(), strings.vehiclePhotoAskBody(),
        actions = listOf(AppDialogAction(strings.vehiclePhotoAskShow()) {
            scope.launch {
                onShowPhotos()
                askVisibilityOpen = false
            }
        }), onDismiss = { askVisibilityOpen = false }, cancelTitle = strings.vehiclePhotoAskKeep())
    selected?.let { (pages, start) ->
        MePhotoViewer(strings.vehiclePhotos(), start, pages.size, { selected = null },
            photo = { index -> VehicleLocalPhoto(pages[index].filename, Modifier.fillMaxSize(), ContentScale.Fit, remoteURL = pages[index].remoteURL) },
            actions = { index ->
                val photo = pages[index]
                MeRow(strings.vehiclePhotoMakeMain(), tag = "vehicle_photo_make_main", onClick = { scope.launch {
                    try { store.makeMain(photo.id, id); selected = null; revision++ }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                } })
                MeRow(strings.delete(), destructive = true, onClick = { delete = photo })
            })
    }
    delete?.let { photo -> AppConfirmDialog(strings.deletePhoto(), null,
        actions = listOf(AppDialogAction(strings.delete(), AppDialogAction.Kind.destructive) {
            scope.launch {
                try { store.delete(photo.id, id); selected = null; revision++ }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
            }
        }), onDismiss = { delete = null }) }
}

/**
 * iOS: VehiclePhotosView.hint. A real empty state: the empty carport, the words and the screen's one
 * action, instead of a grey line under nothing and a «+» in the corner where nobody looks.
 */
@Composable
internal fun VehiclePhotosEmpty(busy: Boolean, onAdd: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 40.dp).testTag("vehicle_photos_empty"),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        com.onezee.triptrack.ui.EmptyStateIllustration(com.onezee.triptrack.R.drawable.empty_garage, size = 120.dp)
        Text(strings.vehiclePhotosEmpty(), style = inter(14f), color = colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(strings.addPhotos(), Modifier.fillMaxWidth()
            .background(AppTheme.accent.copy(alpha = if (busy) 0.35f else 1f), CapsuleShape)
            .clickable(enabled = !busy, role = androidx.compose.ui.semantics.Role.Button, onClick = onAdd)
            .padding(vertical = 15.dp).testTag("vehicle_photos_empty_add"),
            style = inter(16f, InterWeight.bold), color = androidx.compose.ui.graphics.Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** The page of [VehiclePhotosScreen] without the store and the viewer: what the person sees. */
@Composable
internal fun VehiclePhotosPage(vehicleName: String, photos: List<VehiclePhoto>, loaded: Boolean, failed: Boolean, busy: Boolean, onBack: () -> Unit, onAdd: () -> Unit, onOpen: (Int) -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().testTag("vehicle_photos_screen")) {
        GaragePageBar(strings.vehiclePhotos(), vehicleName, onBack) {
            Box(Modifier.size(44.dp).clickable(enabled = !busy, role = Role.Button, onClickLabel = strings.addPhotos()) { haptics.tap(); onAdd() }.testTag("vehicle_photos_add"), contentAlignment = Alignment.Center) {
                Glyph(AppGlyph.plus, null, tint = AppTheme.accent.copy(alpha = if (busy) 0.35f else 1f), size = 20.4.dp)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = tabBarContentClearance()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (failed) Text(strings.garageLoadFailed(), style = inter(12f), color = colors.textTertiary)
            val main = photos.firstOrNull { it.isMain } ?: photos.firstOrNull()
            if (main != null) {
                Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)).background(colors.cardAlt).clickable(role = Role.Button, onClickLabel = strings.vehiclePhotos()) { haptics.tap(); onOpen(photos.indexOf(main)) }.testTag("vehicle_photo_main")) {
                    VehicleLocalPhoto(main.filename, Modifier.fillMaxSize(), remoteURL = main.remoteURL)
                    Text(strings.vehiclePhotoMain(), Modifier.padding(10.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(horizontal = 9.dp, vertical = 5.dp), style = inter(10f, InterWeight.bold), color = Color.White, maxLines = 1)
                }
                photos.filter { it.id != main.id }.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { photo ->
                            PressableCard(onClick = { haptics.tap(); onOpen(photos.indexOfFirst { it.id == photo.id }) }, modifier = Modifier.weight(1f).testTag("vehicle_photo_${photo.id}")) {
                                VehicleLocalPhoto(photo.filename, Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(12.dp)).background(colors.cardAlt), remoteURL = photo.remoteURL)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text(strings.vehiclePhotosHint(), Modifier.fillMaxWidth().padding(top = 2.dp), style = inter(11f), color = colors.textTertiary)
            } else if (loaded) VehiclePhotosEmpty(busy, onAdd)
        }
    }
}
