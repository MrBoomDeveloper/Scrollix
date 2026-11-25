package com.mrboomdev.scrollix.platform.media

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow

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
