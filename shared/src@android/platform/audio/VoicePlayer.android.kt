package com.mrboomdev.scrollix.platform.audio

import com.mrboomdev.scrollix.data.storage.AndroidAppContext

import android.media.MediaPlayer
import java.io.File

actual fun createVoicePlayer(): VoicePlayback = AndroidVoicePlayer()

private class AndroidVoicePlayer : VoicePlayback {
    override val supportsSpeed: Boolean = true

    private var player: MediaPlayer? = null
    private var file: File? = null
    private var speed = 1f

    override fun play(
        bytes: ByteArray,
        startPositionMs: Long,
        onFinished: () -> Unit
    ): Boolean {
        stop()

        return runCatching {
            val context = AndroidAppContext.context
            val temp = File.createTempFile("voice", ".ogg", context.cacheDir)
            temp.writeBytes(bytes)
            file = temp

            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(temp.absolutePath)
            mediaPlayer.setOnCompletionListener { completed ->
                runCatching { completed.release() }
                if (player === completed) player = null
                temp.delete()
                onFinished()
            }
            mediaPlayer.prepare()

            if (startPositionMs > 0L) {
                mediaPlayer.seekTo(
                    startPositionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                )
            }

            mediaPlayer.start()
            applySpeed(mediaPlayer)
            player = mediaPlayer
            true
        }.getOrDefault(false)
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.5f, 2f)
        player?.let(::applySpeed)
    }

    private fun applySpeed(mediaPlayer: MediaPlayer) {
        runCatching {
            mediaPlayer.playbackParams = mediaPlayer.playbackParams
                .setSpeed(speed)
                .setPitch(1f)
        }
    }

    override fun seekTo(positionMs: Long) {
        player?.let { mediaPlayer ->
            runCatching {
                mediaPlayer.seekTo(
                    positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                )
            }
        }
    }

    override fun positionMs(): Long {
        return player?.let { mediaPlayer ->
            runCatching { mediaPlayer.currentPosition.toLong() }.getOrDefault(0L)
        } ?: 0L
    }

    override fun stop() {
        player?.let { mediaPlayer ->
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.release() }
        }

        player = null
        file?.delete()
        file = null
    }
}
