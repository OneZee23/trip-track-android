// iOS: ProfileTripCardView.authorRow (the vehicle riding on the meta line)
package com.onezee.triptrack.ui.cards

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.inter

/**
 * The car a trip was driven in, for the meta line of [ProfileTripCard]: «Which car was this?» is a
 * caption-weight fact, so it rides on the line instead of growing the card a row of its own. [name]
 * is the garage name or, for a car never named, the silhouette's own word; [sprite] the drawn car.
 */
data class ProfileCardVehicle(val name: String, @DrawableRes val sprite: Int)

/** iOS: «·», the 22 × 14 pixel car (scaled to fit, never smoothed) and the name in 11 semibold. */
@Composable
internal fun ProfileCardVehicleTag(vehicle: ProfileCardVehicle) {
    val colors = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("·", style = inter(11f), color = colors.textTertiary, maxLines = 1)
        Image(ImageBitmap.imageResource(vehicle.sprite), null, Modifier.size(22.dp, 14.dp), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
        Text(vehicle.name, style = inter(11f, InterWeight.semibold), color = colors.textSecondary, maxLines = 1)
    }
}
