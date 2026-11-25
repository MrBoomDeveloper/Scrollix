package com.mrboomdev.scrollix.features.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectionBannerTest {
    @Test
    fun `hidden while every account is connected`() {
        assertNull(offlineBannerMessage(emptyList(), queuedCount = 0))
        assertNull(offlineBannerMessage(emptyList(), queuedCount = 3))
    }

    @Test
    fun `names one and two accounts`() {
        assertEquals(
            "Offline — Work",
            offlineBannerMessage(listOf("Work"), queuedCount = 0)
        )

        assertEquals(
            "Offline — Work and Home",
            offlineBannerMessage(listOf("Work", "Home"), queuedCount = 0)
        )
    }

    @Test
    fun `truncates a long account list`() {
        assertEquals(
            "Offline — Work, Home and 2 more",
            offlineBannerMessage(
                listOf("Work", "Home", "Test", "Personal"),
                queuedCount = 0
            )
        )
    }

    @Test
    fun `appends the queued count with correct plural`() {
        assertEquals(
            "Offline — Work · 1 message waiting",
            offlineBannerMessage(listOf("Work"), queuedCount = 1)
        )

        assertEquals(
            "Offline — Work and Home · 4 messages waiting",
            offlineBannerMessage(listOf("Work", "Home"), queuedCount = 4)
        )
    }
}
