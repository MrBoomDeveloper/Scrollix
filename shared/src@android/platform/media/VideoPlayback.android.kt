package com.mrboomdev.scrollix.platform.media

import com.mrboomdev.scrollix.data.storage.AndroidAppContext

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.min

actual fun createVideoPlayer(): VideoPlayback = AndroidVideoPlayer()

/**
 * MediaPlayer-backed playback. Video output goes to the [TextureView] the
 * player view attaches, so no frame is decoded into memory here.
 */
internal class AndroidVideoPlayer : VideoPlayback {
    private val _state = MutableStateFlow(VideoPlaybackState())
    override val state: StateFlow<VideoPlaybackState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var file: File? = null
    private var surface: Surface? = null
    private var textureView: TextureView? = null
    private var pollThread: Thread? = null

    @Volatile
    private var muted = false

    private var videoWidth = 0
    private var videoHeight = 0

    internal fun attach(view: TextureView) {
        textureView = view
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                texture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                setSurface(texture)
            }

            override fun onSurfaceTextureSizeChanged(
                texture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                applyAspectRatio()
            }

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                releaseSurface()
                return true
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }

        view.surfaceTexture?.let(::setSurface)
    }

    internal fun detach() {
        releaseSurface()
        textureView = null
    }

    private fun setSurface(texture: SurfaceTexture) {
        releaseSurface()
        val newSurface = Surface(texture)
        surface = newSurface
        player?.setSurface(newSurface)
    }

    private fun releaseSurface() {
        player?.setSurface(null)
        surface?.release()
        surface = null
    }

    override fun load(bytes: ByteArray, startPositionMs: Long, autoPlay: Boolean): Boolean {
        stop()

        return runCatching {
            val temp = File.createTempFile(
                "video",
                ".mp4",
                AndroidAppContext.context.cacheDir
            )
            temp.writeBytes(bytes)
            file = temp

            _state.value = VideoPlaybackState(isBuffering = true)

            val mediaPlayer = MediaPlayer().apply {
                setDataSource(temp.absolutePath)
                surface?.let { currentSurface -> this.setSurface(currentSurface) }
                setVolume(
                    if (muted) 0f else 1f,
                    if (muted) 0f else 1f
                )

                setOnVideoSizeChangedListener { _, width, height ->
                    // Inside apply { } these names resolve to MediaPlayer's
                    // read-only videoWidth/videoHeight, so the player's own
                    // size fields have to be qualified explicitly.
                    this@AndroidVideoPlayer.videoWidth = width
                    this@AndroidVideoPlayer.videoHeight = height
                    applyAspectRatio()
                }

                setOnPreparedListener { prepared ->
                    this@AndroidVideoPlayer.videoWidth = prepared.videoWidth
                    this@AndroidVideoPlayer.videoHeight = prepared.videoHeight
                    applyAspectRatio()

                    _state.value = _state.value.copy(
                        durationMs = prepared.duration.toLong().coerceAtLeast(0L),
                        isBuffering = false
                    )

                    if (startPositionMs > 0L) {
                        prepared.seekTo(
                            startPositionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        )
                    }

                    if (autoPlay) {
                        prepared.start()
                        _state.value = _state.value.copy(isPlaying = true)
                    }

                    startPolling(prepared)
                }

                setOnCompletionListener { completed ->
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        positionMs = completed.duration.toLong().coerceAtLeast(0L)
                    )
                }

                setOnErrorListener { _, _, _ ->
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        error = "Can't play this video"
                    )
                    true
                }

                prepareAsync()
            }

            player = mediaPlayer
            true
        }.getOrDefault(false)
    }

    override fun play() {
        val current = player ?: return
        runCatching {
            current.start()
            _state.value = _state.value.copy(isPlaying = true)
            startPolling(current)
        }
    }

    override fun pause() {
        player?.let { current ->
            runCatching { current.pause() }
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    override fun seekTo(positionMs: Long) {
        player?.let { current ->
            runCatching {
                current.seekTo(
                    positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                )
                _state.value = _state.value.copy(positionMs = positionMs)
            }
        }
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        player?.setVolume(
            if (muted) 0f else 1f,
            if (muted) 0f else 1f
        )
    }

    override fun stop() {
        pollThread?.interrupt()
        pollThread = null

        player?.let { current ->
            runCatching { current.stop() }
            runCatching { current.release() }
        }
        player = null

        releaseSurface()
        file?.delete()
        file = null
        videoWidth = 0
        videoHeight = 0
        _state.value = VideoPlaybackState()
    }

    private fun startPolling(current: MediaPlayer) {
        if (pollThread?.isAlive == true) return

        pollThread = Thread {
            try {
                while (player === current) {
                    if (current.isPlaying) {
                        _state.value = _state.value.copy(
                            positionMs = current.currentPosition.toLong()
                        )
                    }
                    Thread.sleep(200L)
                }
            } catch (_: InterruptedException) {
            } catch (_: IllegalStateException) {
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun applyAspectRatio() {
        val view = textureView ?: return
        if (videoWidth <= 0 || videoHeight <= 0) return

        view.post {
            val viewWidth = view.width.toFloat()
            val viewHeight = view.height.toFloat()
            if (viewWidth <= 0f || viewHeight <= 0f) return@post

            val scale = min(viewWidth / videoWidth, viewHeight / videoHeight)
            val matrix = Matrix()
            matrix.setScale(scale, scale)
            matrix.postTranslate(
                (viewWidth - videoWidth * scale) / 2f,
                (viewHeight - videoHeight * scale) / 2f
            )
            view.setTransform(matrix)
        }
    }
}
