// iOS: VehicleStage.swift, VehicleAvatar.swift, VehicleSpriteMetrics.swift
package com.onezee.triptrack.feature.garage

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import com.onezee.triptrack.designsystem.theme.AppTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.feature.me.MeArtwork
import com.onezee.triptrack.feature.me.LocalMePhotoClient
import com.onezee.triptrack.feature.me.MePhotoCache
import com.onezee.triptrack.model.Vehicle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// iOS: VehicleAvatar.swift. Colour stays in the legacy-safe wire field.
internal object VehicleAvatar {
    val styles = listOf("car", "hatchback", "crossover", "pickup", "van", "convertible", "sports", "motorcycle", "scooter", "bicycle")
    val colors = listOf("white", "black", "silver", "gray", "red", "blue", "orange", "green")
    private val legacy = mapOf("🏎" to "red", "🚗" to "red", "🏍" to "silver", "🚙" to "blue", "🛻" to "green", "🏁" to "black", "🗺" to "blue", "⛽" to "red")
    fun drawnColor(avatar: String): String? = if (avatar.startsWith("pixel_")) avatar.removePrefix("pixel_").substringAfter('_', "").takeIf { it.isNotEmpty() }
        else legacy[avatar.replace("\uFE0F", "")]
    fun styles(type: com.onezee.triptrack.model.VehicleType): List<String> = when (type) {
        com.onezee.triptrack.model.VehicleType.moto -> listOf("motorcycle")
        com.onezee.triptrack.model.VehicleType.moped -> listOf("scooter")
        com.onezee.triptrack.model.VehicleType.bicycle -> listOf("bicycle")
        else -> styles.take(7)
    }
    fun asset(style: String?, avatar: String): String? = drawnColor(avatar)?.let { "pixel_${style?.takeIf { it in styles } ?: "car"}_$it" }
    fun asset(vehicle: Vehicle): String? = asset(vehicle.avatarStyle.takeIf { it in styles(vehicle.type) } ?: styles(vehicle.type).first(), vehicle.avatarEmoji)
    fun bounds(style: String): Pair<Double, Double> = when (style) {
        "bicycle" -> .0065 to .9925
        "car" -> .2785 to .9925
        "convertible" -> .3183 to .9935
        "crossover" -> .0140 to .9925
        "hatchback" -> .2914 to .9925
        "motorcycle" -> .0946 to .9925
        "pickup" -> .2581 to .9925
        "scooter" -> .0430 to .9935
        "sports" -> .3097 to .9925
        "van" -> .0258 to .9935
        else -> .0 to 1.0
    }
    fun stageHeight(style: String, width: Dp): Dp {
        val (top, bottom) = bounds(style)
        return maxOf(width * .62f, width * (.76 * .7976 * (bottom - top) + .22).toFloat())
    }
}

@Composable
internal fun PixelAsset(name: String, modifier: Modifier) {
    val id = MeArtwork.drawables[name] ?: return
    Image(ImageBitmap.imageResource(id), null, modifier, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
}

// iOS: VehicleSpritePlate.swift. Uniform list plates use the tallest silhouette's ink, not its canvas.
@Composable
internal fun VehicleSpritePlate(vehicle: Vehicle, width: Dp = 52.dp) {
    val style = vehicle.avatarStyle.takeIf { it in VehicleAvatar.styles(vehicle.type) } ?: VehicleAvatar.styles(vehicle.type).first()
    val spriteWidth = width * .86f
    val spriteHeight = spriteWidth * .7976f
    val height = spriteHeight * .986f + width * .20f
    val (top, bottom) = VehicleAvatar.bounds(style)
    Box(Modifier.size(width, height).clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(AppTheme.spritePlateTop, AppTheme.spritePlateBottom)))) {
        val asset = VehicleAvatar.asset(vehicle)
        if (asset != null) PixelAsset(asset, Modifier.size(spriteWidth, spriteHeight).offset(x = width * .07f, y = height * .5f - spriteHeight * ((top + bottom) / 2).toFloat()))
        else Text(vehicle.avatarEmoji, Modifier.align(Alignment.Center), style = inter(width.value * .42f))
    }
}

@Composable
internal fun VehicleStage(vehicle: Vehicle, modifier: Modifier = Modifier, width: Dp = 300.dp, photoFilename: String? = null, isPlus: Boolean = false, remoteURL: String? = null) {
    val state = VehicleStageState.build(vehicle, photoFilename, isPlus)
    val style = vehicle.avatarStyle.takeIf { it in VehicleAvatar.styles(vehicle.type) } ?: VehicleAvatar.styles(vehicle.type).first()
    val height = VehicleAvatar.stageHeight(style, width)
    if (photoFilename != null) {
        VehicleLocalPhoto(photoFilename, modifier.size(width, width * .60f), remoteURL = remoteURL)
        return
    }
    val wheelY = height - width * (92f / 170) + width * (85f / 170 * .80f)
    val spriteWidth = width * .76f
    val spriteHeight = spriteWidth * .7976f
    val bottom = VehicleAvatar.bounds(style).second.toFloat()
    Box(modifier.size(width, height)) {
        PixelAsset("platform_" + if (state.platform.rawValue.isEmpty()) "asphalt" else state.platform.rawValue.removePrefix("card_"),
            Modifier.size(width, width * (92f / 170)).offset(y = height - width * (92f / 170)))
        PixelAsset("platform_shadow", Modifier.size(width * .60f, width * .30f).offset(x = width * .20f, y = wheelY - width * .24f))
        val asset = VehicleAvatar.asset(vehicle)
        if (asset != null) PixelAsset(asset + if (state.finish == com.onezee.triptrack.model.VehicleFinish.matte) "_matte" else "",
            Modifier.size(spriteWidth, spriteHeight).offset(x = width * .12f, y = wheelY - spriteHeight * bottom))
        else Text(vehicle.avatarEmoji, Modifier.align(Alignment.Center), style = inter(width.value * .34f))
    }
}

@Composable
internal fun VehicleLocalPhoto(filename: String, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop, directory: String = "VehiclePhotos", remoteURL: String? = null) {
    val context = LocalContext.current
    val apiClient = LocalMePhotoClient.current
    val bitmap by produceState<ImageBitmap?>(null, filename, directory, remoteURL) {
        value = withContext(Dispatchers.IO) {
            try {
            // Only filenames written by our store may resolve in the private directory.
            val root = File(context.noBackupFilesDir, directory).canonicalFile
            val file = File(root, filename).canonicalFile
            val local = if (file.path.startsWith(root.path + File.separator) && file.isFile) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                val options = BitmapFactory.Options().apply {
                    while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1440) inSampleSize *= 2
                }
                BitmapFactory.decodeFile(file.path, options)
            } else null
            (local ?: remoteURL?.let { url -> apiClient?.let { MePhotoCache.remote(url, it) } })?.asImageBitmap()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }
    if (bitmap != null) Image(bitmap!!, null, modifier, contentScale = contentScale)
    else Box(modifier)
}
