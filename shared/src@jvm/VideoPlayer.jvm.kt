package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

actual fun createVideoPlayer(): VideoPlayback = JvmVideoPlayer()

@Composable
actual fun VideoRenderSurface(player: VideoPlayback, modifier: Modifier) {
    val state by player.state.collectAsState()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        state.frame?.let { frame ->
            Image(
                bitmap = frame,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

private class JvmVideoPlayer : VideoPlayback {
    private val _state = MutableStateFlow(VideoPlaybackState())
    override val state: StateFlow<VideoPlaybackState> = _state.asStateFlow()

    private val audio = createVoicePlayer()

    @Volatile
    private var bytes: ByteArray? = null

    private var grabber: FFmpegFrameGrabber? = null
    private var thread: Thread? = null
    private var session: Any? = null

    @Volatile
    private var paused = true

    @Volatile
    private var muted = false

    private var durationMs = 0L

    override fun load(bytes: ByteArray, startPositionMs: Long, autoPlay: Boolean): Boolean {
        stop()
        this.bytes = bytes

        return try {
            openGrabber(bytes, startPositionMs)

            _state.value = VideoPlaybackState(
                positionMs = startPositionMs,
                durationMs = durationMs,
                isBuffering = false
            )

            decodeCurrentFrame()

            if (autoPlay) {
                play()
            } else {
                paused = true
                _state.value = _state.value.copy(isPlaying = false)
            }

            true
        } catch (_: Exception) {
            _state.value = VideoPlaybackState(error = "Can't play this video")
            false
        } catch (_: LinkageError) {
            _state.value = VideoPlaybackState(error = "Video decoding is unavailable")
            false
        }
    }

    override fun play() {
        if (bytes == null) return

        if (durationMs > 0L && _state.value.positionMs >= durationMs) {
            seekTo(0L)
        }

        paused = false
        ensureThread()
        startAudio()
        _state.value = _state.value.copy(isPlaying = true, isBuffering = false)
    }

    override fun pause() {
        paused = true
        audio.stop()
        _state.value = _state.value.copy(isPlaying = false)
    }

    override fun seekTo(positionMs: Long) {
        val data = bytes ?: return
        val wasPlaying = !paused
        val target = positionMs.coerceIn(0L, durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)

        paused = true
        audio.stop()
        stopThread()

        try {
            openGrabber(data, target)
            _state.value = _state.value.copy(
                positionMs = target,
                frame = null,
                isPlaying = false,
                isBuffering = false
            )
            decodeCurrentFrame()

            if (wasPlaying) {
                play()
            }
        } catch (_: Exception) {
            _state.value = _state.value.copy(error = "Couldn't seek in this video")
        }
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        audio.setVolume(if (muted) 0f else 1f)
    }

    override fun stop() {
        paused = true
        session = null
        stopThread()
        audio.stop()
        runCatching { grabber?.close() }
        grabber = null
        bytes = null
        durationMs = 0L
        _state.value = VideoPlaybackState()
    }

    private fun openGrabber(data: ByteArray, startPositionMs: Long) {
        runCatching { grabber?.close() }
        silenceFfmpegLogs()

        val current = FFmpegFrameGrabber(ByteArrayInputStream(data))
        current.pixelFormat = AV_PIX_FMT_RGBA
        current.start()

        durationMs = (current.lengthInTime / 1000L).coerceAtLeast(0L)

        if (startPositionMs > 0L) {
            runCatching { current.timestamp = startPositionMs * 1000L }
        }

        grabber = current
    }

    private fun decodeCurrentFrame() {
        val current = grabber ?: return
        val frame = current.grabImage() ?: return
        val bitmap = frame.toImageBitmap() ?: return
        val position = (current.timestamp / 1000L).coerceAtLeast(0L)

        _state.value = _state.value.copy(
            frame = bitmap,
            positionMs = position,
            durationMs = durationMs
        )
    }

    private fun ensureThread() {
        if (thread != null) return

        val current = grabber ?: return
        val token = Any()
        session = token
        val frameDelayMs = (1000.0 / current.frameRate.coerceAtLeast(1.0))
            .toLong()
            .coerceIn(16L, 100L)

        thread = Thread {
            try {
                while (session === token) {
                    if (paused) {
                        Thread.sleep(20L)
                        continue
                    }

                    val frame = current.grabImage() ?: break
                    val bitmap = frame.toImageBitmap()
                    val position = ((frame.timestamp.takeIf { it > 0L }
                        ?: current.timestamp) / 1000L).coerceAtLeast(0L)

                    _state.value = _state.value.copy(
                        frame = bitmap,
                        positionMs = position,
                        durationMs = durationMs,
                        isPlaying = true,
                        isBuffering = false
                    )

                    Thread.sleep(frameDelayMs)
                }

                if (session === token) {
                    paused = true
                    audio.stop()
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        positionMs = durationMs
                    )
                }
            } catch (_: InterruptedException) {
            } catch (exception: Exception) {
                _state.value = _state.value.copy(
                    isPlaying = false,
                    error = exception.message ?: "Playback failed"
                )
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun startAudio() {
        val data = bytes ?: return
        audio.setVolume(if (muted) 0f else 1f)
        audio.play(data, _state.value.positionMs) {
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    private fun stopThread() {
        session = null
        thread?.interrupt()
        thread = null
    }
}

private fun Frame.toImageBitmap(): ImageBitmap? {
    val width = imageWidth
    val height = imageHeight
    if (width <= 0 || height <= 0) return null

    val buffer = image?.getOrNull(0) as? ByteBuffer ?: return null
    val rowBytes = width * 4
    val stride = imageStride.takeIf { it >= rowBytes } ?: rowBytes
    val pixels = ByteArray(width * height * 4)

    for (y in 0 until height) {
        val source = buffer.duplicate()
        source.position(y * stride)
        source.get(pixels, y * rowBytes, rowBytes)
    }

    val info = ImageInfo(
        width = width,
        height = height,
        colorType = ColorType.RGBA_8888,
        alphaType = ColorAlphaType.PREMUL
    )

    return Image.makeRaster(info, pixels, rowBytes).toComposeImageBitmap()
}
