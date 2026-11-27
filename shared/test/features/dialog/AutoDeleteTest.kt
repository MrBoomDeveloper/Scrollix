package com.mrboomdev.scrollix.features.dialog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutoDeleteTest {
    @Test
    fun `the last second is rounded up instead of showing zero`() {
        assertEquals("1s", formatAutoDeleteRemaining(1))
        assertEquals("1s", formatAutoDeleteRemaining(1000))
    }

    @Test
    fun `minutes and hours use two digit fields`() {
        assertEquals("1:01", formatAutoDeleteRemaining(61_000))
        assertEquals("59:59", formatAutoDeleteRemaining(3_599_000))
        assertEquals("1:00:00", formatAutoDeleteRemaining(3_600_000))
        assertEquals("2:03:04", formatAutoDeleteRemaining(7_384_000))
    }

    @Test
    fun `long timers collapse to whole days`() {
        assertEquals("1d", formatAutoDeleteRemaining(86_400_000))
        assertEquals("1d", formatAutoDeleteRemaining(90_000_000))
        assertEquals("2d", formatAutoDeleteRemaining(172_800_001))
    }

    @Test
    fun `an expired or absent timer has no label`() {
        assertNull(formatAutoDeleteRemaining(0))
        assertNull(formatAutoDeleteRemaining(-1_000))
    }
}
