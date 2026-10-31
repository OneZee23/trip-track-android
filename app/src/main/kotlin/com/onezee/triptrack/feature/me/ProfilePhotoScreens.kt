// iOS: ProfileCoverPager.swift, ProfilePhotosView.swift, ProfilePhotoFullScreenView.swift
package com.onezee.triptrack.feature.me

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.network.ApiClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*

@Composable
internal fun ProfileCoverImage(photo: ProfileCoverPhoto, api: ApiClient?, modifier: Modifier = Modifier, fit: Boolean = false) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, photo.filename, photo.originalURL, photo.thumbnailURL) {
        value = withContext(Dispatchers.IO) {
            try {
                val local = photo.filename?.let { File(context.noBackupFilesDir, "ProfilePhotos/$it") }?.takeIf(File::isFile)
                local?.let { BitmapFactory.decodeFile(it.path) } ?: (photo.originalURL ?: photo.thumbnailURL)?.let { url -> api?.let { MePhotoCache.remote(url, it) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }
    Box(modifier.background(AppTheme.colors.cardAlt), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = if (fit) ContentScale.Fit else ContentScale.Crop) }
    }
}

@Composable
internal fun ProfilePhotosSheet(store: ProfilePhotoStore, api: ApiClient, signedIn: Boolean, onClose: () -> Unit) {
    val strings = appStrings
    val photos by store.photos.collectAsState()
    val syncFailed by store.failed.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Pair<List<ProfileCoverPhoto>, Int>?>(null) }
    var deleting by remember { mutableStateOf<UUID?>(null) }
    var importSession by remember { mutableStateOf(store.session) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ProfilePhotoStore.LIMIT)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true; failed = false
            try {
                for (uri in uris.take((ProfilePhotoStore.LIMIT - photos.size).coerceAtLeast(0))) {
                    val data = withContext(Dispatchers.IO) {
                        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            val ratio = minOf(1.0, 1440.0 / maxOf(info.size.width, info.size.height))
                            decoder.setTargetSize((info.size.width * ratio).toInt().coerceAtLeast(1), (info.size.height * ratio).toInt().coerceAtLeast(1))
                        }
                        // The store performs the one lossy JPEG encode, at the iOS quality of 70.
                        try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
                        finally { bitmap.recycle() }
                    }
                    store.add(data, importSession)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    ContentSizedSheet(onClose, background = AppTheme.colors.bg, detent = SheetDetent.large) {
        MeSheetHeader(strings.myProfileRowCover()) { dismiss() }
        MeRow(strings.profilePhotoCount(photos.size, ProfilePhotoStore.LIMIT))
        if (!signedIn) MeRow(strings.profilePhotosSignIn())
        MeAction(strings.profileAddPhoto(), {
            importSession = store.session
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, Modifier.padding(16.dp), enabled = !busy && photos.size < ProfilePhotoStore.LIMIT)
        if (failed || syncFailed) MeRow(strings.publicDataLoadFailed(), strings.tryAgain(), onClick = store::requestSync)
        if (photos.isEmpty()) MeRow(strings.profilePhotosEmpty())
        LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
                Column(Modifier.clickable(role = Role.Button) { viewer = photos.toList() to index }) {
                    ProfileCoverImage(photo, api, Modifier.fillMaxWidth().aspectRatio(1f))
                    if (index == 0) Text(strings.profilePhotoFirst(), style = AppType.caption, color = AppTheme.accent)
                }
            }
        }
    }
    viewer?.let { (pages, start) ->
        MePhotoViewer(strings.myProfileRowCover(), start, pages.size, { viewer = null },
            photo = { index -> ProfileCoverImage(pages[index], api, Modifier.fillMaxSize(), fit = true) },
            actions = { index ->
                MeRow(strings.profileMakeCover(), onClick = { scope.launch {
                    try { store.makeFirst(pages[index].id); viewer = null }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                } })
                MeRow(strings.delete(), destructive = true, onClick = { deleting = pages[index].id })
            })
    }
    deleting?.let { id -> AppConfirmDialog(strings.deletePhoto(), null, listOf(AppDialogAction(strings.delete(), AppDialogAction.Kind.destructive) {
        scope.launch {
            try { store.delete(id); viewer = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
    }), onDismiss = { deleting = null }) }
}
