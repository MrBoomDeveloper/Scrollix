package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.data.media.WebmAnimation
import com.mrboomdev.scrollix.data.media.loadWebmAnimation
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.provider.MediaSession
import com.mrboomdev.scrollix.data.storage.saveFileToDevice
import com.mrboomdev.scrollix.features.home.WebmFrames
import com.mrboomdev.scrollix.features.home.rememberFadeIn

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboomdev.scrollix.clickableNoIndication
import com.mrboomdev.scrollix.ui.icons.close_icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FullscreenImageViewer(
    media: MessageMedia,
    session: MediaSession?,
    caption: String = "",
    onClose: () -> Unit
) {
    FullscreenMediaFrame(onClose = onClose, caption = caption) {
        FullscreenImageContent(
            media = media,
            session = session,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        )
    }
}

@Composable
fun FullscreenVideoViewer(
    media: MessageMedia,
    session: MediaSession?,
    caption: String = "",
    onClose: () -> Unit
) {
    FullscreenMediaFrame(onClose = onClose, caption = caption) {
        FullscreenVideoContent(
            media = media,
            session = session,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        )
    }
}

@Composable
private fun FullscreenMediaFrame(
    onClose: () -> Unit,
    caption: String = "",
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .clickableNoIndication(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        content()

        MediaCaption(
            caption = caption,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        )

        IconButton(
            modifier = Modifier.align(Alignment.TopEnd),
            onClick = onClose,
        ) {
            Icon(
                tint = Color.White,
                imageVector = close_icon,
                contentDescription = "Close"
            )
        }
    }
}

/**
 * Caption of the message the media belongs to, drawn on a scrim so it stays
 * readable over bright photos. Long captions are clamped instead of covering
 * the whole picture; the full text is in the chat bubble behind the viewer.
 */
@Composable
private fun MediaCaption(caption: String, modifier: Modifier = Modifier) {
    if (caption.isBlank()) return

    Text(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        text = caption,
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun FullscreenImageContent(
    media: MessageMedia,
    session: MediaSession?,
    modifier: Modifier,
    /** Whether this page is the one the viewer shows; pages reset when inactive. */
    active: Boolean = true,
    /** Reports whether the photo is zoomed in, so the pager can stop paging. */
    onZoomChange: (Boolean) -> Unit = {}
) {
    ZoomableImage(
        media = media,
        session = session,
        modifier = modifier,
        active = active,
        onZoomChange = onZoomChange
    )
}

/**
 * A photo the user can pinch to zoom, drag while zoomed and double-tap to toggle
 * between fitted and 2.5x. Gestures are clamped to the container, so the photo
 * cannot be dragged off screen, and panning is disabled at 1x so a horizontal
 * swipe still pages the gallery.
 *
 * Taps on the photo are swallowed (only the backdrop and the close button
 * dismiss the viewer), which is also why single taps are consumed here instead
 * of with a clickable modifier that would eat the first tap of a double tap.
 */
@Composable
private fun ZoomableImage(
    media: MessageMedia,
    session: MediaSession?,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onZoomChange: (Boolean) -> Unit = {}
) {
    var loaded by remember(media.fileId, media.previewFileId) { mutableStateOf(false) }
    var failed by remember(media.fileId, media.previewFileId) { mutableStateOf(false) }
    var scale by remember(media.fileId) { mutableFloatStateOf(1f) }
    var offset by remember(media.fileId) { mutableStateOf(Offset.Zero) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    val fade = rememberFadeIn(if (loaded) media.fileId ?: media.previewFileId else null)
    val minithumbnail = media.minithumbnail

    fun clamp(target: Offset, value: Float): Offset {
        return clampZoomOffset(
            target = target,
            scale = value,
            containerWidth = container.width,
            containerHeight = container.height
        )
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
        onZoomChange(false)
    }

    // Leaving the page resets the zoom, so coming back starts fitted again.
    LaunchedEffect(active) {
        if (!active) reset()
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (scale * zoomChange).coerceIn(MinZoom, MaxZoom)

        scale = next
        offset = if (next <= MinZoom) Offset.Zero else clamp(offset + panChange, next)
        onZoomChange(next > MinZoom)
    }

    Box(
        modifier = modifier
            .onSizeChanged { size -> container = size }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .transformable(
                state = transformState,
                canPan = { scale > MinZoom },
                lockRotationOnZoomPan = true
            )
            .pointerInput(media.fileId, media.previewFileId) {
                detectTapGestures(
                    onTap = { /* swallowed: only the backdrop and ✕ close */ },
                    onDoubleTap = { position ->
                        if (scale > MinZoom) {
                            reset()
                        } else {
                            // Zoom in around the tapped point instead of the
                            // center, the way the official viewers behave.
                            val focus = Offset(
                                x = (container.width / 2f - position.x) * (DoubleTapZoom - 1f),
                                y = (container.height / 2f - position.y) * (DoubleTapZoom - 1f)
                            )

                            scale = DoubleTapZoom
                            offset = clamp(focus, DoubleTapZoom)
                            onZoomChange(true)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // The tiny embedded preview stands in, blurred, until the full photo
        // arrives; on a permanent failure it is left readable instead.
        if (minithumbnail != null && !loaded) {
            BytesImage(
                bytes = minithumbnail,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (failed) Modifier else Modifier.blur(12.dp)),
                contentScale = ContentScale.Fit
            )
        }

        MediaImage(
            session = session,
            fileId = media.fileId ?: media.previewFileId,
            uniqueId = media.fileUniqueId ?: media.previewUniqueId,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(fade),
            contentScale = ContentScale.Fit,
            onSuccess = { loaded = true },
            onError = { failed = true }
        )

        if (!loaded && !failed) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
    }
}

/**
 * Keeps a zoomed photo inside its container. At [scale] the content extends
 * `(scale - 1) / 2` of the container size to each side, which is the largest
 * translation that still shows no gap. Pure so the bounds are covered by tests.
 */
internal fun clampZoomOffset(
    target: Offset,
    scale: Float,
    containerWidth: Int,
    containerHeight: Int
): Offset {
    if (scale <= 1f) return Offset.Zero

    val maxX = (containerWidth * (scale - 1f) / 2f).coerceAtLeast(0f)
    val maxY = (containerHeight * (scale - 1f) / 2f).coerceAtLeast(0f)

    return Offset(
        x = target.x.coerceIn(-maxX, maxX),
        y = target.y.coerceIn(-maxY, maxY)
    )
}

/** Fitted zoom; below this the photo is not considered zoomed in. */
private const val MinZoom = 1f
private const val MaxZoom = 5f
private const val DoubleTapZoom = 2.5f

@Composable
private fun LegacyFullscreenVideoContent(
    media: MessageMedia,
    session: MediaSession?,
    modifier: Modifier,
    active: Boolean = true
) {
    val fileId = media.fileId
    var animation by remember(fileId, session) { mutableStateOf<WebmAnimation?>(null) }
    var thumbnail by remember(media.minithumbnail, media.previewFileId, session) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var isLoading by remember(fileId, session, active) { mutableStateOf(active) }

    LaunchedEffect(media.minithumbnail, media.previewFileId, session) {
        thumbnail = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }

        if (thumbnail == null) {
            val bytes = session?.loadMediaBytes(
                fileId = media.previewFileId,
                uniqueId = media.previewUniqueId
            )

            if (bytes != null) {
                thumbnail = withContext(Dispatchers.Default) {
                    decodeImageBitmap(bytes)
                }
            }
        }
    }

    LaunchedEffect(fileId, session, active) {
        if (!active) {
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        val currentFileId = fileId ?: 0
        val bytes = if (currentFileId != 0) {
            session?.loadMediaBytes(currentFileId, media.fileUniqueId)
        } else {
            null
        }

        if (bytes != null) {
            val (widthPx, heightPx) = fullscreenAnimationSize(media)

            animation = loadWebmAnimation(
                customEmojiId = currentFileId.toLong(),
                bytes = bytes,
                widthPx = widthPx,
                heightPx = heightPx
            )
        }

        isLoading = false
    }

    val video = animation
    val poster = thumbnail
    val isReady = video != null && video.frames.isNotEmpty()

    Box(modifier.clickable {}, contentAlignment = Alignment.Center) {
        // The preview stays underneath, blurred until the frames are ready,
        // then the animation fades in over it.
        if (poster != null) {
            Image(
                bitmap = poster,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (active && !isReady) {
                            Modifier.blur(16.dp)
                        } else {
                            Modifier
                        }
                    ),
                contentScale = ContentScale.Fit
            )
        }

        if (active && isReady) {
            WebmFrames(
                animation = video,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        if (active && isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else if (active && !isReady && poster == null) {
            Text(
                text = "Can't play this video",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun FullscreenVideoContent(
    media: MessageMedia,
    session: MediaSession?,
    modifier: Modifier,
    active: Boolean = true
) {
    VideoPlayer(
        media = media,
        session = session,
        modifier = modifier,
        isActive = active
    )
}

/**
 * Fullscreen album viewer: swipe between attachments, use the side arrows or
 * the thumbnail strip, and download the current attachment.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullscreenGalleryViewer(
    media: List<MessageMedia>,
    initialIndex: Int,
    session: MediaSession?,
    caption: String = "",
    onClose: () -> Unit
) {
    if (media.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, media.lastIndex),
        pageCount = { media.size }
    )
    val scope = rememberCoroutineScope()
    val currentIndex = pagerState.currentPage.coerceIn(0, media.lastIndex)
    val current = media[currentIndex]

    var isDownloading by remember(current.fileId, current.previewFileId) {
        mutableStateOf(false)
    }
    var downloaded by remember(current.fileId, current.previewFileId) {
        mutableStateOf(false)
    }
    var downloadFraction by remember(current.fileId, current.previewFileId) {
        mutableStateOf<Float?>(null)
    }
    var downloadJob by remember(current.fileId, current.previewFileId) {
        mutableStateOf<Job?>(null)
    }

    val downloadFileId = current.fileId ?: current.previewFileId

    // Watched only while this screen is saving, so a download started in the
    // chat does not make the counter move on its own.
    LaunchedEffect(isDownloading, downloadFileId) {
        if (!isDownloading || downloadFileId == null) return@LaunchedEffect

        while (true) {
            downloadFraction = session?.mediaDownloadProgress(downloadFileId)
            delay(150)
        }
    }
    // While the photo is zoomed, a horizontal drag pans it instead of paging.
    // The pager is blocked so a pan at the edge cannot flip the page.
    var isZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickableNoIndication(onClick = onClose)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isZoomed
        ) { page ->
            val item = media[page]

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp, bottom = 104.dp),
                contentAlignment = Alignment.Center
            ) {
                if (item.isVideo) {
                    FullscreenVideoContent(
                        media = item,
                        session = session,
                        modifier = Modifier.fillMaxSize(),
                        active = page == currentIndex
                    )
                } else {
                    FullscreenImageContent(
                        media = item,
                        session = session,
                        modifier = Modifier.fillMaxSize(),
                        active = page == currentIndex,
                        onZoomChange = { zoomed ->
                            // Only the visible page may block paging; an
                            // off-screen page still holds its own zoom state.
                            if (page == currentIndex) isZoomed = zoomed
                        }
                    )
                }
            }
        }

        Text(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            text = "${currentIndex + 1} / ${media.size}",
            color = Color.White,
            fontSize = 14.sp
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                enabled = !isDownloading,
                onClick = {
                    val fileId = current.fileId ?: current.previewFileId
                    ?: return@TextButton

                    downloadJob = scope.launch {
                        isDownloading = true

                        val bytes = session?.loadMediaBytes(
                            fileId = fileId,
                            uniqueId = current.fileUniqueId ?: current.previewUniqueId
                        )

                        if(bytes != null) {
                            downloaded = withContext(Dispatchers.IO) {
                                saveFileToDevice(
                                    fileName = savedMediaFileName(current),
                                    bytes = bytes
                                ) != null
                            }
                        }

                        isDownloading = false
                        downloadFraction = null
                        downloadJob = null
                    }
                }
            ) {
                Text(
                    text = when {
                        isDownloading -> downloadFraction
                            ?.let { fraction -> "Saving ${(fraction * 100).toInt()}%" }
                            ?: "Saving..."

                        downloaded -> "Saved"
                        else -> "Download"
                    },
                    color = Color.White
                )
            }

            if (isDownloading) {
                TextButton(
                    onClick = {
                        // The partial file stays in the media cache, so asking
                        // again resumes instead of starting over.
                        downloadJob?.cancel()
                        downloadJob = null
                        isDownloading = false
                        downloadFraction = null

                        downloadFileId?.let { fileId ->
                            scope.launch { session?.cancelMediaDownload(fileId) }
                        }
                    }
                ) {
                    Text(text = "Cancel", color = Color.White)
                }
            }

            IconButton(
                onClick = onClose,
            ) {
                Icon(
                    tint = Color.White,
                    imageVector = close_icon,
                    contentDescription = "Close"
                )
            }
        }

        if (currentIndex > 0) {
            GalleryArrow(text = "‹", alignment = Alignment.CenterStart) {
                scope.launch { pagerState.animateScrollToPage(currentIndex - 1) }
            }
        }

        if (currentIndex < media.lastIndex) {
            GalleryArrow(text = "›", alignment = Alignment.CenterEnd) {
                scope.launch { pagerState.animateScrollToPage(currentIndex + 1) }
            }
        }

        // Sits above the thumbnail strip, which is reserved by the page padding.
        MediaCaption(
            caption = caption,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 108.dp)
        )

        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(vertical = 10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(media) { index, item ->
                GalleryThumbnail(
                    media = item,
                    session = session,
                    selected = index == currentIndex,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                )
            }
        }
    }
}

@Composable
private fun BoxScope.GalleryArrow(
    text: String,
    alignment: Alignment,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .align(alignment)
            .padding(12.dp)
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 28.sp
        )
    }
}

@Composable
private fun GalleryThumbnail(
    media: MessageMedia,
    session: MediaSession?,
    selected: Boolean,
    onClick: () -> Unit
) {
    var failed by remember(media.previewFileId, media.minithumbnail) { mutableStateOf(false) }
    val minithumbnail = media.minithumbnail

    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        MediaImage(
            session = session,
            fileId = media.previewFileId,
            uniqueId = media.previewUniqueId,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onError = { failed = true }
        )

        if (failed && minithumbnail != null) {
            BytesImage(
                bytes = minithumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (media.isVideo) {
            Text(
                text = "▶",
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

private val MessageMedia.isVideo: Boolean
    get() = kind == MessageMediaKind.Video ||
        kind == MessageMediaKind.Animation ||
        kind == MessageMediaKind.VideoNote

private fun fullscreenAnimationSize(media: MessageMedia): Pair<Int, Int> {
    val maxSize = 320
    val aspectRatio = if (media.width > 0 && media.height > 0) {
        media.width.toFloat() / media.height
    } else {
        1f
    }

    return if (aspectRatio >= 1f) {
        maxSize to (maxSize / aspectRatio).toInt().coerceAtLeast(1)
    } else {
        (maxSize * aspectRatio).toInt().coerceAtLeast(1) to maxSize
    }
}
