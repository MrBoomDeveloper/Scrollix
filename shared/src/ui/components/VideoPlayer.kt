package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Playback state shared by the Android MediaPlayer and the desktop FFmpeg
 * decoder. [frame] is only populated on desktop, where frames are rendered
 * into Compose; Android renders through a TextureView.
 */
data class VideoPlaybackState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val frame: ImageBitmap? = null,
    val error: String? = null
)

interface VideoPlayback {
    val state: StateFlow<VideoPlaybackState>

    /** Loads a downloaded video and optionally starts playing it. */
    fun load(
        bytes: ByteArray,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true
    ): Boolean

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setMuted(muted: Boolean) {}
    fun stop()
}

expect fun createVideoPlayer(): VideoPlayback

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
    session: AccountSession?,
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    val player = remember(media.fileId, media.fileUniqueId) { createVideoPlayer() }
    val state by player.state.collectAsState()
    var poster by remember(media.minithumbnail, media.previewFileId) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var loadError by remember(media.fileId, media.fileUniqueId) {
        mutableStateOf<String?>(null)
    }
    var isMuted by remember(media.fileId) { mutableStateOf(false) }
    var isSeeking by remember(media.fileId) { mutableStateOf(false) }
    var seekValue by remember(media.fileId) { mutableLongStateOf(0L) }

    val durationMs = state.durationMs.takeIf { it > 0L }
        ?: (media.durationSeconds.toLong() * 1000L).coerceAtLeast(0L)
    val positionMs = if (isSeeking) seekValue else state.positionMs

    DisposableEffect(player) {
        onDispose { player.stop() }
    }

    LaunchedEffect(media.fileId, media.fileUniqueId, session) {
        poster = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) { decodeImageBitmap(bytes) }
        }

        if (poster == null) {
            val preview = session?.loadMediaBytes(
                fileId = media.previewFileId,
                uniqueId = media.previewUniqueId
            )

            if (preview != null) {
                poster = withContext(Dispatchers.Default) {
                    decodeImageBitmap(preview)
                }
            }
        }

        val fileId = media.fileId
        if (fileId == null) {
            loadError = "This video isn't available"
            return@LaunchedEffect
        }

        val bytes = session?.loadMediaBytes(fileId, media.fileUniqueId)
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
        if (state.frame == null && poster != null) {
            Image(
                bitmap = poster!!,
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
