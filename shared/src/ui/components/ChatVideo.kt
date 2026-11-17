package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.provider.AccountSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val RoundVideoNoteSize = 250.dp

@Composable
fun ChatVideoPreview(
    media: MessageMedia,
    session: AccountSession?,
    isRound: Boolean = false,
    onClick: () -> Unit
) {
    var thumbnail by remember(media.minithumbnail, media.previewFileId, session) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var animation by remember(media.fileId, session, isRound) {
        mutableStateOf<WebmAnimation?>(null)
    }
    val roundSizePx = with(LocalDensity.current) {
        RoundVideoNoteSize.roundToPx()
    }

    LaunchedEffect(
        media.minithumbnail,
        media.previewFileId,
        media.fileId,
        session,
        isRound,
        roundSizePx
    ) {
        thumbnail = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }

        if (thumbnail == null) {
            val bytes = media.previewFileId?.let { fileId ->
                session?.loadFile(fileId)?.getOrNull()
            }

            if (bytes != null) {
                thumbnail = withContext(Dispatchers.Default) {
                    decodeImageBitmap(bytes)
                }
            }
        }

        if (isRound) {
            val fileId = media.fileId

            if (fileId != null) {
                val bytes = session?.loadFile(fileId)?.getOrNull()

                if (bytes != null) {
                    animation = loadWebmAnimation(
                        customEmojiId = fileId.toLong(),
                        bytes = bytes,
                        widthPx = roundSizePx,
                        heightPx = roundSizePx
                    )
                }
            }
        }
    }

    val aspectRatio = if (media.width > 0 && media.height > 0) {
        media.width.toFloat() / media.height
    } else {
        16f / 9f
    }
    val bitmap = thumbnail
    val webm = animation

    var frameIndex by remember(webm) { mutableIntStateOf(0) }
    var isPlaying by remember(webm) { mutableStateOf(true) }
    var progress by remember(webm) { mutableFloatStateOf(0f) }
    var playedNanos by remember(webm) { mutableLongStateOf(0L) }

    LaunchedEffect(webm, isPlaying) {
        val current = webm ?: return@LaunchedEffect
        if (!isPlaying || current.frames.isEmpty()) return@LaunchedEffect

        val frameRate = current.frameRate.coerceAtLeast(1f)
        val frameDuration = (1_000_000_000.0 / frameRate)
            .toLong()
            .coerceAtLeast(1L)
        val totalNanos = frameDuration * current.frames.size
        val startPosition = playedNanos.coerceIn(
            minimumValue = 0L,
            maximumValue = (totalNanos - 1).coerceAtLeast(0L)
        )
        var startedAt = 0L

        while (isPlaying) {
            withFrameNanos { now ->
                if (startedAt == 0L) startedAt = now

                val position = (startPosition + (now - startedAt))
                    .coerceIn(0L, totalNanos)
                playedNanos = position

                frameIndex = ((position / frameDuration).toInt())
                    .coerceIn(0, current.frames.lastIndex)
                progress = if (totalNanos <= 0L) {
                    1f
                } else {
                    (position.toFloat() / totalNanos).coerceIn(0f, 1f)
                }

                if (position >= totalNanos) {
                    progress = 1f
                    frameIndex = current.frames.lastIndex
                    isPlaying = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .then(
                if (isRound) {
                    Modifier.size(RoundVideoNoteSize)
                } else {
                    Modifier
                        .width(260.dp)
                        .aspectRatio(aspectRatio.coerceIn(0.75f, 1.8f))
                }
            )
            .clip(if (isRound) CircleShape else RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable {
                if (!isRound) {
                    onClick()
                    return@clickable
                }

                if (webm == null) return@clickable

                if (progress >= 1f) {
                    playedNanos = 0L
                    frameIndex = 0
                    progress = 0f
                    isPlaying = true
                } else {
                    isPlaying = !isPlaying
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            isRound && webm != null && webm.frames.isNotEmpty() -> {
                Image(
                    bitmap = webm.frames[frameIndex.coerceIn(0, webm.frames.lastIndex)],
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                RoundPlaybackRing(progress = progress)

                if (!isPlaying && progress >= 1f) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "▶",
                            color = Color.White,
                            fontSize = 22.sp
                        )
                    }
                }
            }

            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            else -> Text(
                text = "Video",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (!isRound) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶",
                    color = Color.White,
                    fontSize = 22.sp
                )
            }
        }
    }
}

@Composable
private fun RoundPlaybackRing(progress: Float) {
    val progressColor = MaterialTheme.colorScheme.primary

    Canvas(Modifier.fillMaxSize()) {
        val strokeWidth = 3.dp.toPx()
        val inset = strokeWidth / 2f
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = Color.Black.copy(alpha = 0.35f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
        )

        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
        )
    }
}
