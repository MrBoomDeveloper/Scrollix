package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.data.provider.MediaSession

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.setSingletonImageLoaderFactory
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import okio.Buffer

/**
 * Durable identity of media shown through Coil. [uniqueId] is preferred for
 * the cache key because runtime file ids change between app launches.
 */
internal data class MediaImageRequest(
    val session: MediaSession?,
    val fileId: Int?,
    val uniqueId: String?
) {
    val cacheKey: String?
        get() = when {
            uniqueId != null -> "scrollix:${session?.accountId}:$uniqueId"
            fileId != null -> "scrollix:${session?.accountId}:file:$fileId"
            else -> null
        }
}

/**
 * Reads media through [AccountSession.loadMediaBytes], which already owns the
 * disk cache and the provider download progress. Coil only adds in-memory
 * caching, request de-duplication and decoding on top.
 */
private class MediaImageFetcher(
    private val request: MediaImageRequest,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val bytes = request.session?.loadMediaBytes(
            fileId = request.fileId,
            uniqueId = request.uniqueId
        ) ?: return null

        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().apply { write(bytes) },
                fileSystem = options.fileSystem
            ),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<MediaImageRequest> {
        override fun create(
            data: MediaImageRequest,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = MediaImageFetcher(data, options)
    }
}

private class MediaImageKeyer : Keyer<MediaImageRequest> {
    override fun key(data: MediaImageRequest, options: Options): String? = data.cacheKey
}

/** Installs the process-wide Coil loader before the first image is composed. */
@Composable
internal fun ConfigureCoilImageLoader() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(MediaImageFetcher.Factory())
                add(MediaImageKeyer())
            }
            // CacheStore owns the disk budget and the keep-media policy. Coil
            // must not create a second, uncontrolled media cache.
            .diskCache(null)
            .build()
    }
}

/**
 * An image backed by a provider file. The caller supplies the aspect ratio or
 * a fixed size, exactly like the previous decode-and-draw path.
 */
@Composable
internal fun MediaImage(
    session: MediaSession?,
    fileId: Int?,
    uniqueId: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    colorFilter: ColorFilter? = null,
    alpha: Float = 1f,
    onLoading: (() -> Unit)? = null,
    onSuccess: (() -> Unit)? = null,
    onError: (() -> Unit)? = null
) {
    AsyncImage(
        model = MediaImageRequest(session, fileId, uniqueId),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = colorFilter,
        alpha = alpha,
        onLoading = { onLoading?.invoke() },
        onSuccess = { onSuccess?.invoke() },
        onError = { onError?.invoke() }
    )
}

/** An image whose bytes are already in memory, such as an avatar or thumbnail. */
@Composable
internal fun BytesImage(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    colorFilter: ColorFilter? = null,
    alpha: Float = 1f,
    onLoading: (() -> Unit)? = null,
    onSuccess: (() -> Unit)? = null,
    onError: (() -> Unit)? = null
) {
    AsyncImage(
        model = bytes,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = colorFilter,
        alpha = alpha,
        onLoading = { onLoading?.invoke() },
        onSuccess = { onSuccess?.invoke() },
        onError = { onError?.invoke() }
    )
}
