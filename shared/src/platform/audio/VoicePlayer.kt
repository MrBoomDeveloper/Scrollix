package com.mrboomdev.scrollix.platform.audio

interface VoicePlayback {
    /** Whether [setSpeed] changes the playback rate on this platform. */
    val supportsSpeed: Boolean
        get() = false

    /**
     * Plays [speed] times faster (1 is normal). Platforms without resampling
     * or a rate control ignore it and keep [supportsSpeed] false.
     */
    fun setSpeed(speed: Float) {}

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
