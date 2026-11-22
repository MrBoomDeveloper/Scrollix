package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.saveFileToDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FullscreenImageViewer(
    media: MessageMedia,
    session: AccountSession?,
    onClose: () -> Unit
) {
    FullscreenMediaFrame(onClose = onClose) {
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
    session: AccountSession?,
    onClose: () -> Unit
) {
    FullscreenMediaFrame(onClose = onClose) {
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
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        content()

        Text(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clickable(onClick = onClose),
            text = "✕",
            color = Color.White,
            fontSize = 22.sp
        )
    }
}

@Composable
private fun FullscreenImageContent(
    media: MessageMedia,
    session: AccountSession?,
    modifier: Modifier
) {
    var image by remember(media.fileId, media.previewFileId, media.minithumbnail, session) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(media.fileId, media.previewFileId, media.minithumbnail, session) {
        image = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }

        val bytes = (media.fileId ?: media.previewFileId)?.let { fileId ->
            session?.loadFile(fileId)?.getOrNull()
        }

        if (bytes != null) {
            image = withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }
    }

    val bitmap = image

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.alpha(rememberFadeIn(bitmap)),
            contentScale = ContentScale.Fit
        )
    } else {
        CircularProgressIndicator()
    }
}

@Composable
private fun FullscreenVideoContent(
    media: MessageMedia,
    session: AccountSession?,
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
            val bytes = media.previewFileId?.let { previewId ->
                session?.loadFile(previewId)?.getOrNull()
            }

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
            session?.loadFile(currentFileId)?.getOrNull()
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

    Box(modifier, contentAlignment = Alignment.Center) {
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

/**
 * Fullscreen album viewer: swipe between attachments, use the side arrows or
 * the thumbnail strip, and download the current attachment.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullscreenGalleryViewer(
    media: List<MessageMedia>,
    initialIndex: Int,
    session: AccountSession?,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable(onClick = onClose)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
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
                        modifier = Modifier.fillMaxSize()
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(enabled = !isDownloading) {
                        val fileId = current.fileId ?: current.previewFileId
                            ?: return@clickable

                        scope.launch {
                            isDownloading = true

                            val bytes = session?.loadFile(fileId)?.getOrNull()

                            if (bytes != null) {
                                downloaded = withContext(Dispatchers.IO) {
                                    saveFileToDevice(
                                        fileName = current.downloadFileName(),
                                        bytes = bytes
                                    ) != null
                                }
                            }

                            isDownloading = false
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = when {
                        isDownloading -> "Saving..."
                        downloaded -> "Saved"
                        else -> "Download"
                    },
                    color = Color.White,
                    fontSize = 13.sp
                )
            }

            Text(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                text = "✕",
                color = Color.White,
                fontSize = 22.sp
            )
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
    session: AccountSession?,
    selected: Boolean,
    onClick: () -> Unit
) {
    var bitmap by remember(media.previewFileId, media.minithumbnail, session) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(media.previewFileId, media.minithumbnail, session) {
        bitmap = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }

        if (bitmap == null) {
            val bytes = media.previewFileId?.let { previewId ->
                session?.loadFile(previewId)?.getOrNull()
            }

            if (bytes != null) {
                bitmap = withContext(Dispatchers.Default) {
                    decodeImageBitmap(bytes)
                }
            }
        }
    }

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
        val image = bitmap

        if (image != null) {
            Image(
                bitmap = image,
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

private fun MessageMedia.downloadFileName(): String {
    val base = fileName?.takeIf { it.isNotBlank() } ?: when (kind) {
        MessageMediaKind.Photo -> "photo"
        MessageMediaKind.Video, MessageMediaKind.VideoNote -> "video"
        MessageMediaKind.Animation -> "animation"
        MessageMediaKind.Audio -> "audio"
        else -> "file"
    }

    if (base.contains('.')) return base

    return when (kind) {
        MessageMediaKind.Photo -> "$base.jpg"
        MessageMediaKind.Video, MessageMediaKind.VideoNote -> "$base.mp4"
        MessageMediaKind.Animation -> "$base.gif"
        MessageMediaKind.Audio -> "$base.mp3"
        else -> base
    }
}

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
