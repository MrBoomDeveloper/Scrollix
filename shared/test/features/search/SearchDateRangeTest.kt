package com.mrboomdev.scrollix.features.search

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.features.dialog.search.SearchDateFilter
import com.mrboomdev.scrollix.features.dialog.search.minTimestamp

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchDateRangeTest {
    private val zone = TimeZone.UTC
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    @Test
    fun `a preset keeps its window`() {
        val messages = listOf(
            message(id = 2, timestamp = now - day),
            message(id = 1, timestamp = now - 10 * day)
        )

        assertEquals(
            listOf(2L),
            filterMessagesByRange(
                messages = messages,
                range = MessageDateRange(preset = SearchDateFilter.LastWeek),
                nowMillis = now,
                zone = zone
            ).map { it.messageId }
        )
    }

    @Test
    fun `a custom range includes both boundary days`() {
        val from = parseDateBoundMillis("2024-03-01", endOfDay = false, zone)!!
        val to = parseDateBoundMillis("2024-03-02", endOfDay = true, zone)!!

        val messages = listOf(
            message(id = 4, timestamp = from - 1),
            message(id = 3, timestamp = from),
            message(id = 2, timestamp = to),
            message(id = 1, timestamp = to + 1)
        )

        assertEquals(
            listOf(3L, 2L),
            filterMessagesByRange(
                messages = messages,
                range = MessageDateRange(fromDate = "2024-03-01", toDate = "2024-03-02"),
                nowMillis = now,
                zone = zone
            ).map { it.messageId }
        )
    }

    @Test
    fun `a blank bound leaves that side open`() {
        val from = parseDateBoundMillis("2024-03-01", endOfDay = false, zone)!!

        val messages = listOf(
            message(id = 2, timestamp = now),
            message(id = 1, timestamp = from - 1)
        )

        assertEquals(
            listOf(2L),
            filterMessagesByRange(
                messages = messages,
                range = MessageDateRange(fromDate = "2024-03-01"),
                nowMillis = now,
                zone = zone
            ).map { it.messageId }
        )
    }

    @Test
    fun `a custom range wins over the preset`() {
        val from = parseDateBoundMillis("2024-03-01", endOfDay = false, zone)!!

        val messages = listOf(
            message(id = 2, timestamp = now),
            message(id = 1, timestamp = from)
        )

        assertEquals(
            listOf(2L, 1L),
            filterMessagesByRange(
                messages = messages,
                range = MessageDateRange(
                    preset = SearchDateFilter.LastWeek,
                    fromDate = "2024-03-01"
                ),
                nowMillis = now,
                zone = zone
            ).map { it.messageId }
        )
    }

    @Test
    fun `blank bounds keep every message`() {
        val messages = listOf(
            message(id = 2, timestamp = now),
            message(id = 1, timestamp = 0)
        )

        assertEquals(
            messages,
            filterMessagesByRange(
                messages = messages,
                range = MessageDateRange(),
                nowMillis = now,
                zone = zone
            )
        )
    }

    @Test
    fun `unparsable dates are reported invalid`() {
        assertNull(parseDateBoundMillis("2024-13-40", endOfDay = false, zone))
        assertNull(parseDateBoundMillis("tomorrow", endOfDay = false, zone))
        assertTrue(isInvalidDateBound("2024-02-30", endOfDay = false, zone))
        assertFalse(isInvalidDateBound("2024-02-29", endOfDay = false, zone))
        assertFalse(isInvalidDateBound("", endOfDay = false, zone))
    }

    @Test
    fun `bounds sent to the provider match the local filter`() {
        val from = parseDateBoundMillis("2024-03-01", endOfDay = false, zone)!!
        val to = parseDateBoundMillis("2024-03-02", endOfDay = true, zone)!!

        val (min, max) = MessageDateRange(
            fromDate = "2024-03-01",
            toDate = "2024-03-02"
        ).boundsMillis(now, zone)

        assertEquals(from, min)
        assertEquals(to, max)

        val (presetMin, presetMax) = MessageDateRange(
            preset = SearchDateFilter.LastWeek
        ).boundsMillis(now, zone)

        assertEquals(SearchDateFilter.LastWeek.minTimestamp(now), presetMin)
        assertEquals(0L, presetMax)
    }

    @Test
    fun `an open range sends no bounds`() {
        assertEquals(0L to 0L, MessageDateRange().boundsMillis(now, zone))
    }

    @Test
    fun `a range is active only when it narrows something`() {
        assertFalse(MessageDateRange().isActive)
        assertTrue(MessageDateRange(preset = SearchDateFilter.LastYear).isActive)
        assertTrue(MessageDateRange(fromDate = "2024-03-01").isActive)
        assertTrue(MessageDateRange(toDate = "2024-03-01").isActive)
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
