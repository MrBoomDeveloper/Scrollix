package com.mrboomdev.scrollix.platform.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoTrimTest {

    @Test
    fun `a range must be ordered and long enough`() {
        assertTrue(trimRangeValid(0, 1_000, 30_000))
        assertFalse(trimRangeValid(5_000, 5_000, 30_000))
        assertFalse(trimRangeValid(5_000, 4_000, 30_000))
        assertFalse(trimRangeValid(-1, 4_000, 30_000))
        assertFalse(trimRangeValid(0, 999, 30_000))
    }

    @Test
    fun `a range cannot pass the end of the file`() {
        assertFalse(trimRangeValid(29_000, 31_000, 30_000))
        assertTrue(trimRangeValid(29_000, 30_000, 30_000))
    }

    @Test
    fun `unknown duration skips the upper bound check`() {
        assertTrue(trimRangeValid(10_000, 12_000, 0))
    }

    @Test
    fun `progress maps the kept range onto zero to one`() {
        assertEquals(0f, trimProgress(4_000, 8_000, 2_000))
        assertEquals(0f, trimProgress(4_000, 8_000, 4_000))
        assertEquals(0.5f, trimProgress(4_000, 8_000, 6_000))
        assertEquals(1f, trimProgress(4_000, 8_000, 9_000))
    }

    @Test
    fun `an empty range reports completion instead of dividing by zero`() {
        assertEquals(1f, trimProgress(5_000, 5_000, 5_000))
    }

    @Test
    fun `the estimate keeps the share of the file that was cut`() {
        assertEquals(2_000_000L, estimatedTrimBytes(10_000_000L, 10_000L, 2_000L, 4_000L))
        assertEquals(0L, estimatedTrimBytes(10_000_000L, 10_000L, 4_000L, 2_000L))
        assertEquals(0L, estimatedTrimBytes(0L, 10_000L, 0L, 1_000L))
    }

    @Test
    fun `a trim needs twice its estimate free`() {
        assertFalse(trimFitsOnDisk(freeBytes = 1_999L, estimatedBytes = 1_000L))
        assertTrue(trimFitsOnDisk(freeBytes = 2_000L, estimatedBytes = 1_000L))
        assertFalse(trimFitsOnDisk(freeBytes = Long.MAX_VALUE, estimatedBytes = 0L))
    }
}
