package com.example.worktrack

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

internal sealed interface PhotoThumbnailState {
    data object Loading : PhotoThumbnailState
    data object Unavailable : PhotoThumbnailState
    data class Ready(val bitmap: Bitmap) : PhotoThumbnailState
}

private const val THUMBNAIL_PIXELS = 256
private val thumbnailCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

@Composable
internal fun rememberPhotoThumbnail(uri: String): State<PhotoThumbnailState> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state = remember(uri) { mutableStateOf<PhotoThumbnailState>(PhotoThumbnailState.Loading) }
    LaunchedEffect(uri) {
        state.value = PhotoThumbnailState.Loading
        val cached = thumbnailCache.get(uri)
        state.value = if (cached != null) {
            PhotoThumbnailState.Ready(cached)
        } else {
            try {
                val bitmap = withContext(Dispatchers.IO) { decodeThumbnail(context, Uri.parse(uri), THUMBNAIL_PIXELS) }
                if (bitmap == null) {
                    PhotoThumbnailState.Unavailable
                } else {
                    thumbnailCache.put(uri, bitmap)
                    PhotoThumbnailState.Ready(bitmap)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                PhotoThumbnailState.Unavailable
            }
        }
    }
    return state
}

@Composable
internal fun PhotoThumbnail(state: PhotoThumbnailState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.size(88.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            PhotoThumbnailState.Loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            PhotoThumbnailState.Unavailable -> Icon(
                Icons.Outlined.BrokenImage,
                contentDescription = stringResource(R.string.photo_status_missing),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            is PhotoThumbnailState.Ready -> Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.photo_status_available),
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

private fun decodeThumbnail(context: android.content.Context, uri: Uri, targetPixels: Int): Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(thumbnailSampleSize(info.size.width, info.size.height, targetPixels))
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = thumbnailSampleSize(bounds.outWidth, bounds.outHeight, targetPixels)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}

internal fun thumbnailSampleSize(width: Int, height: Int, targetPixels: Int): Int {
    if (width <= 0 || height <= 0 || targetPixels <= 0) return 1
    var sample = 1
    val largest = max(width, height)
    while (largest / (sample * 2) >= targetPixels) sample *= 2
    return sample
}
