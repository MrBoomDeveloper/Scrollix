package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchDateFilterTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `any time has no lower bound`() {
        assertEquals(0L, SearchDateFilter.AnyTime.minTimestamp(now))
    }

    @Test
    fun `windows start the expected number of days back`() {
        assertEquals(now - 7 * day, SearchDateFilter.LastWeek.minTimestamp(now))
        assertEquals(now - 30 * day, SearchDateFilter.LastMonth.minTimestamp(now))
        assertEquals(now - 365 * day, SearchDateFilter.LastYear.minTimestamp(now))
    }

    @Test
    fun `older messages are dropped and order is kept`() {
        val messages = listOf(
            message(id = 5, timestamp = now - day),
            message(id = 4, timestamp = now - 3 * day),
            message(id = 3, timestamp = now - 60 * day)
        )

        assertEquals(
            listOf(5L, 4L),
            filterMessagesByDate(messages, SearchDateFilter.LastWeek, now)
                .map { it.messageId }
        )
    }

    @Test
    fun `the boundary itself is included`() {
        val messages = listOf(message(id = 1, timestamp = now - 7 * day))

        assertEquals(
            listOf(1L),
            filterMessagesByDate(messages, SearchDateFilter.LastWeek, now)
                .map { it.messageId }
        )
    }

    @Test
    fun `any time keeps every message`() {
        val messages = listOf(
            message(id = 2, timestamp = now),
            message(id = 1, timestamp = 0)
        )

        assertEquals(messages, filterMessagesByDate(messages, SearchDateFilter.AnyTime, now))
        assertTrue(SearchDateFilter.AnyTime.minTimestamp(now) == 0L)
    }

    private fun message(id: Long, timestamp: Long): ChatMessage = ChatMessage(
        providerId = "telegram",
        accountId = "1",
        providerDialogId = "2",
        messageId = id,
        senderId = 3,
        text = "message $id",
        timestamp = timestamp,
        isOutgoing = false
    )
}
