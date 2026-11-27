package com.mrboomdev.scrollix.features.dialog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordingWaveformTest {
    @Test
    fun `levels are clamped into the drawable range`() {
        val levels = appendWaveformLevel(
            levels = listOf(0.5f),
            level = 2.5f
        )

        assertEquals(listOf(0.5f, 1f), levels)
        assertTrue(levels.all { level -> level in 0f..1f })
    }

    @Test
    fun `the window keeps only the newest bars`() {
        val levels = (1..50).map { it / 50f }

        val window = appendWaveformLevel(
            levels = levels,
            level = 1f,
            size = 10
        )

        assertEquals(10, window.size)
        assertEquals(1f, window.last())
        // The oldest samples were dropped, not the newest.
        assertEquals(levels.takeLast(9), window.dropLast(1))
    }

    @Test
    fun `a non-positive window yields no bars`() {
        assertEquals(
            emptyList(),
            appendWaveformLevel(levels = listOf(0.4f), level = 0.6f, size = 0)
        )
    }
}
