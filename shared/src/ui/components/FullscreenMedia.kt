package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.provider.AccountSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FullscreenImageViewer(
    media: MessageMedia,
    session: AccountSession?,
    onClose: () -> Unit
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            )

            else -> CircularProgressIndicator()
        }

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
fun FullscreenVideoViewer(
    media: MessageMedia,
    session: AccountSession?,
    onClose: () -> Unit
) {
    val fileId = media.fileId
    var animation by remember(fileId, session) { mutableStateOf<WebmAnimation?>(null) }
    var thumbnail by remember(media.minithumbnail, session) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(fileId, session) { mutableStateOf(true) }

    LaunchedEffect(fileId, session) {
        thumbnail = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }

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
    var frameIndex by remember(video) { mutableIntStateOf(0) }

    LaunchedEffect(video) {
        if (video == null) return@LaunchedEffect

        val frameRate = video.frameRate.coerceAtLeast(1f)
        val frameDuration = (1_000_000_000.0 / frameRate)
            .toLong()
            .coerceAtLeast(1L)
        var startedAt = 0L

        while (true) {
            withFrameNanos { now ->
                if (startedAt == 0L) startedAt = now
                frameIndex = (((now - startedAt) / frameDuration).toInt()) % video.frames.size
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        when {
            video != null && video.frames.isNotEmpty() -> Image(
                bitmap = video.frames[frameIndex.coerceIn(0, video.frames.lastIndex)],
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            )

            isLoading -> CircularProgressIndicator()

            thumbnail != null -> Image(
                bitmap = thumbnail!!,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            )

            else -> Text(
                text = "Can't play this video",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }

        Text(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clickable(onClick = onClose),
            text = "✕",
            color = Color.White,
            fontSize = 22.sp
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
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
