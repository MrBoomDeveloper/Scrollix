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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
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
                    .padding(16.dp)
                    .alpha(rememberFadeIn(bitmap)),
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
    val poster = thumbnail
    val isReady = video != null && video.frames.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        // The preview stays underneath, blurred until the frames are ready,
        // then the animation fades in over it.
        if (poster != null) {
            Image(
                bitmap = poster,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .then(if (isReady) Modifier else Modifier.blur(16.dp)),
                contentScale = ContentScale.Fit
            )
        }

        if (isReady) {
            WebmFrames(
                animation = video,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            )
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else if (!isReady && poster == null) {
            Text(
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
