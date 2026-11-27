package com.mrboomdev.scrollix.data.media

/**
 * Resamples interleaved 16-bit PCM so it plays [speed] times faster. Faster
 * playback drops samples, slower playback interpolates between them, so the
 * pitch follows the rate like a tape would.
 *
 * Frames stay intact: every channel of a frame is taken from the same source
 * position, otherwise stereo would drift apart.
 */
internal fun resamplePcm16(
    pcm: ByteArray,
    channels: Int,
    speed: Float
): ByteArray {
    val safeSpeed = speed.coerceIn(0.5f, 2f)

    if (safeSpeed == 1f || channels <= 0) return pcm

    val frameSize = channels * 2
    val frames = pcm.size / frameSize
    if (frames == 0) return pcm

    val outputFrames = (frames / safeSpeed).toInt().coerceIn(1, frames)
    val output = ByteArray(outputFrames * frameSize)

    fun sampleAt(frame: Int, channel: Int): Int {
        val index = frame * frameSize + channel * 2
        return ((pcm[index].toInt() and 0xFF) or (pcm[index + 1].toInt() shl 8))
            .toShort()
            .toInt()
    }

    for (frame in 0 until outputFrames) {
        val source = frame * safeSpeed
        val low = source.toInt().coerceAtMost(frames - 1)
        val high = (low + 1).coerceAtMost(frames - 1)
        val fraction = source - low

        for (channel in 0 until channels) {
            val from = sampleAt(low, channel)
            val to = sampleAt(high, channel)
            val value = (from + (to - from) * fraction)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            val index = frame * frameSize + channel * 2
            output[index] = (value and 0xFF).toByte()
            output[index + 1] = ((value shr 8) and 0xFF).toByte()
        }
    }

    return output
}
