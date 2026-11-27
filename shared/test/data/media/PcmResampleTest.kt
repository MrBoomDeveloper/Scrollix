package com.mrboomdev.scrollix.data.media

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PcmResampleTest {
    private fun frames(count: Int, channels: Int = 2): ByteArray {
        val frameSize = channels * 2
        val bytes = ByteArray(count * frameSize)

        for (frame in 0 until count) {
            for (channel in 0 until channels) {
                val value = (frame * 100 + channel).toShort()
                val index = frame * frameSize + channel * 2
                bytes[index] = (value.toInt() and 0xFF).toByte()
                bytes[index + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
            }
        }

        return bytes
    }

    @Test
    fun `normal speed returns the same samples`() {
        val pcm = frames(10)
        assertContentEquals(pcm, resamplePcm16(pcm, channels = 2, speed = 1f))
    }

    @Test
    fun `twice the speed keeps every second frame`() {
        val pcm = frames(100)
        val result = resamplePcm16(pcm, channels = 2, speed = 2f)

        // 100 stereo frames in, 50 out; output frame f is source frame 2f.
        assertEquals(200, result.size)
        assertContentEquals(
            byteArrayOf(0, 0, 1, 0, -56, 0, -55, 0),
            result.copyOfRange(0, 8)
        )
    }

    @Test
    fun `channels stay in step at a fractional rate`() {
        val pcm = frames(12)
        val result = resamplePcm16(pcm, channels = 2, speed = 1.5f)

        // Frames are resampled as a unit, so the byte count stays a whole
        // number of stereo frames.
        assertEquals(0, result.size % 4)
        assertEquals(8 * 4, result.size)
    }

    @Test
    fun `rates outside the range are clamped`() {
        val pcm = frames(100)

        // 5x is clamped to 2x, which keeps half the frames.
        assertEquals(200, resamplePcm16(pcm, channels = 2, speed = 5f).size)
    }
}
