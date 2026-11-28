package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.provider.MediaSession
import com.mrboomdev.scrollix.platform.media.VideoPlayback
import com.mrboomdev.scrollix.platform.media.VideoPlaybackState
import com.mrboomdev.scrollix.platform.media.createVideoPlayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Draws video output. Android attaches a TextureView; desktop draws frames. */
@Composable
expect fun VideoRenderSurface(player: VideoPlayback, modifier: Modifier)

/**
 * A complete in-app video player: poster while loading, play/pause, seek bar,
 * elapsed/total time, mute and an error state. The surrounding fullscreen
 * viewer owns closing and downloading.
 */
@Composable
fun VideoPlayer(
    media: MessageMedia,
    session: MediaSession?,
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    val player = remember(media.fileId, media.fileUniqueId) { createVideoPlayer() }
    val state by player.state.collectAsState()
    var loadError by remember(media.fileId, media.fileUniqueId) {
        mutableStateOf<String?>(null)
    }
    var isMuted by remember(media.fileId) { mutableStateOf(false) }
    var isSeeking by remember(media.fileId) { mutableStateOf(false) }
    var seekValue by remember(media.fileId) { mutableLongStateOf(0L) }
    var qualityIndex by remember(media.fileId) { mutableIntStateOf(-1) }

    val qualities = media.videoQualities
    val selectedQuality = qualities.getOrNull(qualityIndex)
    val servedFileId = selectedQuality?.fileId ?: media.fileId
    val servedUniqueId = selectedQuality?.fileUniqueId ?: media.fileUniqueId

    val durationMs = state.durationMs.takeIf { it > 0L }
        ?: (media.durationSeconds.toLong() * 1000L).coerceAtLeast(0L)
    val positionMs = if (isSeeking) seekValue else state.positionMs

    DisposableEffect(player) {
        onDispose { player.stop() }
    }

    LaunchedEffect(servedFileId, servedUniqueId, session) {
        val fileId = servedFileId
        if (fileId == null) {
            loadError = "This video isn't available"
            return@LaunchedEffect
        }

        player.stop()

        val bytes = session?.loadMediaBytes(fileId, servedUniqueId)
        if (bytes == null) {
            loadError = "Couldn't download this video"
            return@LaunchedEffect
        }

        loadError = null
        if (!withContext(Dispatchers.IO) {
                player.load(bytes, startPositionMs = 0L, autoPlay = isActive)
            }
        ) {
            loadError = "Can't play this video"
        }
    }

    LaunchedEffect(player, isActive) {
        if (isActive) {
            player.play()
        } else {
            player.pause()
        }
    }

    LaunchedEffect(player, isMuted) {
        player.setMuted(isMuted)
    }

    val error = loadError ?: state.error

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable {
                if (state.isPlaying) player.pause() else player.play()
            },
        contentAlignment = Alignment.Center
    ) {
        if (state.frame == null) {
            media.minithumbnail?.let { thumbnail ->
                BytesImage(
                    bytes = thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            MediaImage(
                session = session,
                fileId = media.previewFileId,
                uniqueId = media.previewUniqueId,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        VideoRenderSurface(
            player = player,
            modifier = Modifier.fillMaxSize()
        )

        if (error != null) {
            Text(
                modifier = Modifier.padding(24.dp),
                text = error,
                color = Color.White,
                fontSize = 14.sp
            )
        } else if (state.isBuffering || state.durationMs == 0L) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.White
            )
        }

        if (error == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (qualities.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = "Quality",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )

                        QualityLabel(
                            text = "Auto",
                            selected = qualityIndex == -1,
                            onClick = { qualityIndex = -1 }
                        )

                        qualities.forEachIndexed { index, quality ->
                            QualityLabel(
                                text = quality.label,
                                selected = qualityIndex == index,
                                onClick = { qualityIndex = index }
                            )
                        }
                    }
                }

                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    value = positionMs.toFloat()
                        .coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                    onValueChange = { value ->
                        isSeeking = true
                        seekValue = value.toLong()
                    },
                    onValueChangeFinished = {
                        player.seekTo(seekValue)
                        isSeeking = false
                    },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        modifier = Modifier
                            .clickable {
                                if (state.isPlaying) player.pause() else player.play()
                            }
                            .padding(6.dp),
                        text = if (state.isPlaying) "⏸" else "▶",
                        color = Color.White,
                        fontSize = 18.sp
                    )

                    Text(
                        modifier = Modifier.weight(1f),
                        text = "${formatVideoTime(positionMs)} / " +
                            formatVideoTime(durationMs),
                        color = Color.White,
                        fontSize = 12.sp
                    )

                    Text(
                        modifier = Modifier
                            .clickable { isMuted = !isMuted }
                            .padding(6.dp),
                        text = if (isMuted) "🔇" else "🔊",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityLabel(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) Color.White.copy(alpha = 0.28f)
                else Color.White.copy(alpha = 0.08f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        text = text,
        color = Color.White,
        fontSize = 12.sp
    )
}

private fun formatVideoTime(milliseconds: Long): String {
    val safeSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val hours = safeSeconds / 3600L
    val minutes = (safeSeconds % 3600L) / 60L
    val seconds = safeSeconds % 60L

    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:" +
            seconds.toString().padStart(2, '0')
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
