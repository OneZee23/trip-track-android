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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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

@Composable
internal fun VehiclePhotoSheet(id: UUID, store: VehiclePhotoStore, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val strings = appStrings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photos by remember(id) { mutableStateOf<List<VehiclePhoto>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Pair<List<VehiclePhoto>, Int>?>(null) }
    var delete by remember { mutableStateOf<VehiclePhoto?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var loaded by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id, revision) {
        try { photos = withContext(Dispatchers.IO) { store.photos(id) }; loaded = true; onChanged() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isNotEmpty() && !busy) scope.launch {
            busy = true
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
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    ContentSizedSheet(onDismiss, background = AppTheme.colors.bg, detent = SheetDetent.large) {
        MeSheetHeader(strings.vehiclePhotos()) { dismiss() }
        if (loaded && photos.isEmpty()) {
            VehiclePhotosEmpty(busy) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        } else {
            MeAction(strings.choosePhotosTitle(), { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.padding(16.dp), enabled = !busy)
            if (failed) MeRow(strings.garageLoadFailed())
            LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.id }) { photo ->
                    Column(Modifier.clickable { selected = photos.toList() to photos.indexOfFirst { it.id == photo.id } }.testTag("vehicle_photo_${photo.id}")) {
                        VehicleLocalPhoto(photo.filename, Modifier.fillMaxWidth().aspectRatio(1f), remoteURL = photo.remoteURL)
                        if (photo.isMain) Text(strings.vehiclePhotoMain(), style = AppType.caption, color = AppTheme.accent)
                    }
                }
            }
        }
    }
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
