package com.mrboomdev.scrollix.features.settings

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class DataUsageFormattingTest {
    @Test
    fun `data sizes use binary units with trimmed decimals`() {
        assertEquals("0 B", formatDataSize(0))
        assertEquals("512 B", formatDataSize(512))
        assertEquals("1 KB", formatDataSize(1024))
        assertEquals("1.5 KB", formatDataSize(1536))
        assertEquals("1 MB", formatDataSize(1024L * 1024))
        assertEquals("2.5 GB", formatDataSize((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `dates are locale independent`() {
        assertEquals("2023-11-14", formatDataDate(1_700_000_000_000L, TimeZone.UTC))
        assertEquals("Unknown", formatDataDate(0L, TimeZone.UTC))
    }

    @Test
    fun `session times are relative up to a week`() {
        val now = 1_700_000_000_000L

        assertEquals("Just now", sessionTimestampLabel(now, now - 10_000L, TimeZone.UTC))
        assertEquals("5 min ago", sessionTimestampLabel(now, now - 5 * 60_000L, TimeZone.UTC))
        assertEquals("2 h ago", sessionTimestampLabel(now, now - 2 * 3_600_000L, TimeZone.UTC))
        assertEquals("3 d ago", sessionTimestampLabel(now, now - 3 * 86_400_000L, TimeZone.UTC))
    }

    @Test
    fun `older session times fall back to the absolute date`() {
        val now = 1_700_000_000_000L
        val old = now - 30L * 86_400_000L

        assertEquals(
            formatDataDate(old, TimeZone.UTC),
            sessionTimestampLabel(now, old, TimeZone.UTC)
        )

        assertEquals("Unknown", sessionTimestampLabel(now, 0L, TimeZone.UTC))
    }
}
