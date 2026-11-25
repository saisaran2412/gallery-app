package com.example.core.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.Options
import coil.request.videoFrameMillis
import coil.size.Precision
import coil.size.Scale
import coil.size.pxOrElse
import com.example.core.storage.AppStorageDirs
import com.example.data.local.MediaItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * High-throughput hardware-accelerated ImageLoader with bounded memory + disk caches
 * and viewport lookahead prefetching for lag-free scrolling.
 */
object FolioImageLoader {

    @Volatile
    private var INSTANCE: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
        }
    }

    private fun build(context: Context): ImageLoader {
        val thumbDir = AppStorageDirs.thumbnailsCacheDir(context)
        return ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStoreThumbnailFetcher.Factory(context))
                }
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.28)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(thumbDir)
                    .maxSizeBytes(160L * 1024L * 1024L) // 160 MB bounded thumbnail cache
                    .build()
            }
            .allowHardware(true)
            .allowRgb565(true)
            .dispatcher(Dispatchers.IO)
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
    }

    fun prefetchThumbnails(
        context: Context,
        items: List<MediaItemEntity>
    ) {
        if (items.isEmpty()) return
        val loader = get(context)
        items.forEach { item ->
            val cacheKey = "thumb_${item.id}_${item.dateModifiedMillis}"
            val req = ImageRequest.Builder(context)
                .data(Uri.parse(item.uriString))
                .size(360, 420)
                .scale(Scale.FILL)
                .precision(Precision.INEXACT)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .apply {
                    if (item.isVideo) videoFrameMillis(1_000L)
                }
                .build()
            loader.enqueue(req)
        }
    }
}

private class MediaStoreThumbnailFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val targetW = options.size.width.pxOrElse { 360 }.coerceIn(64, 512)
        val targetH = options.size.height.pxOrElse { 360 }.coerceIn(64, 512)

        val bitmap: Bitmap = suspendCancellableCoroutine<Bitmap?> { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            val result = runCatching {
                context.contentResolver.loadThumbnail(uri, Size(targetW, targetH), signal)
            }.getOrNull()
            if (cont.isActive) {
                cont.resume(result)
            }
        } ?: return null

        return DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            if (data.scheme != ContentResolver.SCHEME_CONTENT) return null
            if (data.authority != "media") return null
            val w = options.size.width.pxOrElse { Int.MAX_VALUE }
            val h = options.size.height.pxOrElse { Int.MAX_VALUE }
            if (w > 512 || h > 512) return null
            return MediaStoreThumbnailFetcher(context, data, options)
        }
    }
}
