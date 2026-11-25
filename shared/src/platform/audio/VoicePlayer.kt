package com.mrboomdev.scrollix.platform.audio

interface VoicePlayback {
    fun play(
        bytes: ByteArray,
        startPositionMs: Long = 0L,
        onFinished: () -> Unit
    ): Boolean

    fun seekTo(positionMs: Long)
    fun positionMs(): Long
    fun setVolume(volume: Float) {}
    fun stop()
}

expect fun createVoicePlayer(): VoicePlayback
