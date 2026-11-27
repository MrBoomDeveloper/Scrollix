package com.mrboomdev.scrollix.features.dialog

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceWaveformTest {
    @Test
    fun `an empty waveform decodes to no bars`() {
        assertEquals(emptyList(), decodeVoiceWaveform(ByteArray(0)))
    }

    @Test
    fun `each byte is one normalized sample`() {
        val levels = decodeVoiceWaveform(byteArrayOf(0xE1.toByte()))

        assertEquals(1, levels.size)
        assertTrue(abs(levels[0] - 1f / 31f) < 0.0001f)
    }

    @Test
    fun `samples fill the whole normalized range`() {
        val levels = decodeVoiceWaveform(
            byteArrayOf(0x00, 0xFF.toByte())
        )

        assertEquals(listOf(0f, 1f), levels)
    }

    @Test
    fun `seek fractions stay inside the track`() {
        assertEquals(0.5f, waveformSeekFraction(x = 50f, width = 100))
        assertEquals(0f, waveformSeekFraction(x = -20f, width = 100))
        assertEquals(1f, waveformSeekFraction(x = 180f, width = 100))
        assertEquals(0f, waveformSeekFraction(x = 10f, width = 0))
    }
}
