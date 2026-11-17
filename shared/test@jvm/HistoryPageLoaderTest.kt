package com.mrboomdev.scrollix.data.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryPageLoaderTest {
    @Test
    fun `loads more than one page until limit is reached`() = runBlocking {
        val loader = HistoryPageLoader<TestMessage>(idOf = { it.id })
        val requests = mutableListOf<Pair<Long, Int>>()

        val result = loader.load(
            fromMessageId = 0,
            limit = 5
        ) { fromMessageId, limit ->
            requests += fromMessageId to limit

            when (fromMessageId) {
                0L -> listOf(TestMessage(10))
                10L -> listOf(
                    TestMessage(9),
                    TestMessage(8),
                    TestMessage(7),
                    TestMessage(6)
                )
                else -> emptyList()
            }
        }

        assertEquals(
            listOf(10L, 9L, 8L, 7L, 6L),
            result.items.map { it.id }
        )
        assertEquals(listOf(0L to 5, 10L to 4), requests)
        assertTrue(result.hasMore)
    }

    @Test
    fun `stops when TDLib returns an empty page`() = runBlocking {
        val loader = HistoryPageLoader<TestMessage>(idOf = { it.id })

        val result = loader.load(
            fromMessageId = 0,
            limit = 50
        ) { fromMessageId, _ ->
            if (fromMessageId == 0L) listOf(TestMessage(10)) else emptyList()
        }

        assertEquals(listOf(10L), result.items.map { it.id })
        assertFalse(result.hasMore)
    }

    @Test
    fun `respects request budget when TDLib returns one message at a time`() = runBlocking {
        val loader = HistoryPageLoader<TestMessage>(
            maxRequests = 3,
            idOf = { it.id }
        )

        val result = loader.load(
            fromMessageId = 0,
            limit = 10
        ) { fromMessageId, _ ->
            val id = if (fromMessageId == 0L) 10 else fromMessageId - 1
            listOf(TestMessage(id))
        }

        assertEquals(listOf(10L, 9L, 8L), result.items.map { it.id })
        assertTrue(result.hasMore)
    }

    @Test
    fun `filters messages that are not older than the cursor`() = runBlocking {
        val loader = HistoryPageLoader<TestMessage>(idOf = { it.id })

        val result = loader.load(
            fromMessageId = 10,
            limit = 2,
            accept = { it.id < 10 }
        ) { _, _ ->
            listOf(
                TestMessage(11),
                TestMessage(10),
                TestMessage(9),
                TestMessage(8)
            )
        }

        assertEquals(listOf(9L, 8L), result.items.map { it.id })
        assertTrue(result.hasMore)
    }

    @Test
    fun `stops when the cursor does not advance`() = runBlocking {
        val loader = HistoryPageLoader<TestMessage>(idOf = { it.id })

        val result = loader.load(
            fromMessageId = 10,
            limit = 5
        ) { _, _ ->
            listOf(TestMessage(10))
        }

        // The anchor itself may come back in the page; the session filters and dedupes it.
        assertEquals(listOf(10L), result.items.map { it.id })
        assertFalse(result.hasMore)
    }

    private data class TestMessage(val id: Long)
}
