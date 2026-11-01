// iOS: ProfileCoverPager.swift, PhotoFullScreenView.swift (frozen public pages).
package com.onezee.triptrack.feature.social.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.feature.me.MePhotoViewer
import com.onezee.triptrack.feature.settings.MeRow
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.ui.PhotoImage
import com.onezee.triptrack.ui.PhotoSource
import java.util.UUID

data class PublicPhotoPage(val id: UUID, val url: String)
data class PublicPhotoSelection(val pages: List<PublicPhotoPage>, val index: Int, val reportable: Boolean = true)

@Composable
fun PublicPhotoViewer(selection: PublicPhotoSelection, onDismiss: () -> Unit, onReport: ((UUID) -> Unit)? = null) {
    MePhotoViewer(appStrings.vehiclePhotos(), selection.index, selection.pages.size, onDismiss,
        photo = { index -> PublicPhoto(selection.pages[index]) },
        actions = { index -> onReport?.takeIf { selection.reportable }?.let { report -> MeRow(appStrings.reportProfileAction(), destructive = true,
            onClick = { onDismiss(); report(selection.pages[index].id) }) } })
}

@Composable
private fun PublicPhoto(page: PublicPhotoPage) {
    val images = com.onezee.triptrack.ui.LocalTripPhotoImages.current
    val source = PhotoSource.Remote(page.url)
    var attempt by remember(page.id) { mutableIntStateOf(0) }
    var settled by remember(page.id) { mutableStateOf(false) }
    val bitmap by produceState(images?.cached(source, 2400), page, attempt) {
        settled = false
        value = images?.load(source, 2400)
        settled = true
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        val image = bitmap
        if (image != null) {
            val fit = minOf(constraints.maxWidth.toFloat() / image.width, constraints.maxHeight.toFloat() / image.height)
            val fill = maxOf(constraints.maxWidth.toFloat() / image.width, constraints.maxHeight.toFloat() / image.height)
            Image(image, null, Modifier.fillMaxSize(), contentScale = if (fill <= fit * 1.2f) androidx.compose.ui.layout.ContentScale.Crop else androidx.compose.ui.layout.ContentScale.Fit)
        } else if (settled) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                com.onezee.triptrack.designsystem.components.Text(appStrings.publicDataLoadFailed(), style = com.onezee.triptrack.designsystem.theme.AppType.meta, color = androidx.compose.ui.graphics.Color.White)
                com.onezee.triptrack.designsystem.components.PressableCard(onClick = { attempt++ }) {
                    com.onezee.triptrack.designsystem.components.Text(appStrings.retry(), Modifier.padding(12.dp), style = com.onezee.triptrack.designsystem.theme.AppType.chipActive, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        } else com.onezee.triptrack.feature.account.Spinner(androidx.compose.ui.graphics.Color.White)
    }
}
