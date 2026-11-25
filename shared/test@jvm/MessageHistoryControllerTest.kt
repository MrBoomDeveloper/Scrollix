package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageSendState

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDLib's `getChatHistory(fromMessageId, offset = 0, limit)` includes the
 * from-message in the returned page. Every mock here follows that contract;
 * the previous exclusive mock is what let the pager lose a message per page
 * and declare the chat finished after one older page.
 */
class MessageHistoryControllerTest {
    @Test
    fun `loads the newest page and exposes it oldest first`() = runBlocking {
        val controller = controller { _, limit ->
            Result.success(page((100L downTo 100L - limit + 1).map(::message)))
        }

        val result = controller.loadLatest(limit = 50)

        assertTrue(result.isSuccess)
        assertEquals((51L..100L).toList(), ids(controller))
        assertTrue(controller.state.value.hasMoreOlder)
        assertFalse(controller.state.value.isLoadingInitial)
    }

    @Test
    fun `one older page adds a full page instead of losing the anchor`() = runBlocking {
        val requests = mutableListOf<Pair<Long, Int>>()
        val controller = controller { fromMessageId, limit ->
            requests += fromMessageId to limit
            Result.success(inclusivePage(fromMessageId, limit, newest = 200))
        }

        controller.loadLatest(limit = 50)
        val hasMore = controller.loadOlder(limit = 50)

        assertTrue(hasMore.getOrThrow())
        assertEquals((101L..200L).toList(), ids(controller))
        // Newest page + exactly one older request. The old implementation
        // needed a second, one-message request that contained only the anchor
        // and then set hasMoreOlder = false.
        assertEquals(2, requests.size)
        assertEquals(151L, requests.last().first)
        assertEquals(51, requests.last().second)
    }

    @Test
    fun `older pages reach the chat start and stop there`() = runBlocking {
        val requests = mutableListOf<Long>()
        val controller = controller { fromMessageId, limit ->
            requests += fromMessageId
            Result.success(inclusivePage(fromMessageId, limit, newest = 120))
        }

        controller.loadLatest(limit = 50)
        assertTrue(controller.loadOlder(limit = 50).getOrThrow())
        assertEquals((21L..120L).toList(), ids(controller))

        assertFalse(controller.loadOlder(limit = 50).getOrThrow())
        assertEquals((1L..120L).toList(), ids(controller))
        assertFalse(controller.state.value.hasMoreOlder)

        // Newest page, then 71..21 and 21..1. The id 1 page proves the start
        // without an extra probe.
        assertEquals(listOf(0L, 71L, 21L), requests)
    }

    @Test
    fun `jumps load history until the requested message is available`() = runBlocking {
        val controller = controller { fromMessageId, limit ->
            Result.success(inclusivePage(fromMessageId, limit, newest = 120))
        }

        controller.loadLatest(limit = 50)
        val loaded = controller.loadUntilLoaded(messageId = 10)

        assertTrue(loaded.getOrThrow())
        assertTrue(controller.state.value.contains(10))
        assertEquals(1L, controller.state.value.messages.first().messageId)
    }

    @Test
    fun `live updates replace existing messages and stay sorted`() = runBlocking {
        val controller = controller { _, _ ->
            // A short page means this is the whole chat.
            Result.success(page(listOf(message(2), message(1))))
        }

        controller.loadLatest(limit = 50)
        controller.merge(listOf(message(1, text = "edited"), message(3)))

        assertEquals(listOf(1L, 2L, 3L), ids(controller))
        assertEquals("edited", controller.state.value.messages.first().text)
        // A short page is not treated as proof of the chat start.
        assertTrue(controller.state.value.hasMoreOlder)
    }

    @Test
    fun `a short newest page does not hide older history`() = runBlocking {
        val controller = controller { fromMessageId, _ ->
            Result.success(
                page(
                    when (fromMessageId) {
                        0L -> listOf(message(10))
                        10L -> listOf(message(10), message(9), message(8), message(7))
                        7L -> listOf(message(7), message(6), message(5))
                        5L -> listOf(message(5), message(4), message(3))
                        3L -> listOf(message(3), message(2), message(1))
                        else -> listOf(message(fromMessageId))
                    }
                )
            )
        }

        controller.loadLatest(limit = 50)

        assertTrue(controller.state.value.hasMoreOlder)
        assertEquals(listOf(10L), ids(controller))

        controller.loadOlder(limit = 50)

        assertEquals((1L..10L).toList(), ids(controller))
        assertFalse(controller.state.value.hasMoreOlder)
    }

    @Test
    fun `jump to a missing message does not page through the whole history`() = runBlocking {
        var requests = 0

        val controller = controller { _, limit ->
            requests++
            Result.success(inclusivePage(0, limit, newest = 100))
        }

        controller.loadLatest(limit = 50)
        val loaded = controller.loadUntilLoaded(messageId = 120)

        assertFalse(loaded.getOrThrow())
        assertEquals(1, requests)
    }

    @Test
    fun `sparse pages keep paging until the page budget is used`() = runBlocking {
        var requests = 0

        val controller = controller { fromMessageId, _ ->
            requests++

            val id = if (fromMessageId == 0L) 100L else fromMessageId
            Result.success(page(listOf(message(id), message(maxOf(1L, id - 1)))))
        }

        controller.loadLatest(limit = 50)
        val hasMore = controller.loadOlder(limit = 50)

        assertTrue(hasMore.getOrThrow())
        // The newest page itself already contains 99, so the eight older
        // requests reach 91.
        assertEquals((91L..100L).toList(), ids(controller))
        assertEquals(9, requests)
    }

    @Test
    fun `stops when a page cannot move the cursor`() = runBlocking {
        val controller = controller { fromMessageId, _ ->
            val id = if (fromMessageId == 0L) 10L else fromMessageId
            Result.success(page(listOf(message(id))))
        }

        controller.loadLatest(limit = 50)

        assertFalse(controller.loadOlder(limit = 50).getOrThrow())
        assertFalse(controller.state.value.hasMoreOlder)
    }

    @Test
    fun `a failed initial load can be retried`() = runBlocking {
        var attempts = 0

        val controller = controller { _, _ ->
            attempts++
            if (attempts == 1) {
                Result.failure(IllegalStateException("offline"))
            } else {
                Result.success(page(listOf(message(2), message(1))))
            }
        }

        assertTrue(controller.loadLatest(limit = 50).isFailure)
        assertFalse(controller.state.value.isLoadingInitial)
        assertFalse(controller.state.value.isLoadingOlder)
        assertTrue(controller.state.value.messages.isEmpty())

        assertTrue(controller.loadLatest(limit = 50).isSuccess)
        assertEquals(listOf(1L, 2L), ids(controller))
    }

    @Test
    fun `a disjoint newest page replaces the cache preview`() = runBlocking {
        val controller = controller(
            initialMessages = (501L..600L).map(::message)
        ) { _, limit ->
            Result.success(inclusivePage(0, limit, newest = 1_000))
        }

        assertTrue(controller.state.value.messages.first().messageId == 501L)

        controller.loadLatest(limit = 50)

        // Merging the preview would leave 601..950 missing with no way to
        // fetch them; the network page must win.
        assertEquals((951L..1_000L).toList(), ids(controller))
    }

    @Test
    fun `seeding ignores pending and failed messages`() = runBlocking {
        val controller = controller { _, _ -> Result.success(page(emptyList())) }

        controller.seed(
            listOf(
                message(1),
                message(2, sendState = MessageSendState.Sending),
                message(3, sendState = MessageSendState.Failed)
            )
        )

        assertEquals(listOf(1L), ids(controller))
    }

    @Test
    fun `loading around a message replaces the window and marks newer history`() = runBlocking {
        val controller = controller { fromMessageId, limit ->
            Result.success(inclusivePage(fromMessageId, limit, newest = 1_000))
        }

        controller.loadLatest(limit = 50)
        val loaded = controller.loadAround(500, radius = 50)

        assertTrue(loaded.getOrThrow())
        assertEquals((450L..500L).toList(), ids(controller))
        assertTrue(controller.state.value.hasMoreNewer)
        assertTrue(controller.state.value.hasMoreOlder)

        controller.loadLatest(limit = 50)

        assertEquals((951L..1_000L).toList(), ids(controller))
        assertFalse(controller.state.value.hasMoreNewer)
    }

    @Test
    fun `a page in flight cannot resurrect a deleted message`() = runBlocking {
        val latest = (1L..10L).map(::message)
        val controller = controller { _, _ -> Result.success(page(latest)) }

        controller.loadLatest(limit = 50)
        controller.remove(setOf(5L))
        controller.merge(latest)

        assertFalse(controller.state.value.contains(5))
        assertEquals((1L..10L).filterNot { it == 5L }, ids(controller))
    }

    @Test
    fun `a replaced temporary id is not duplicated by a page`() = runBlocking {
        val controller = controller { _, _ ->
            Result.success(page(listOf(message(2), message(1))))
        }

        controller.loadLatest(limit = 50)
        controller.merge(listOf(message(100)))
        controller.replace(100, message(101))
        controller.merge(listOf(message(101)))
        controller.merge(listOf(message(100)))

        assertEquals(listOf(1L, 2L, 101L), ids(controller))
    }

    private fun controller(
        maxJumpPages: Int = 100,
        maxRequestsPerPage: Int = 8,
        initialMessages: List<ChatMessage> = emptyList(),
        fetch: suspend (fromMessageId: Long, limit: Int) -> Result<MessagePage>
    ): MessageHistoryController {
        return MessageHistoryController(
            pageLoader = object : MessagePageLoader {
                override suspend fun load(
                    fromMessageId: Long,
                    limit: Int
                ): Result<MessagePage> {
                    return fetch(fromMessageId, limit)
                }
            },
            maxJumpPages = maxJumpPages,
            maxRequestsPerPage = maxRequestsPerPage,
            initialMessages = initialMessages
        )
    }

    /**
     * Mirrors `getChatHistory(fromMessageId, offset = 0, limit)`:
     * the from-message is included and the page walks towards id 1.
     */
    private fun inclusivePage(
        fromMessageId: Long,
        limit: Int,
        newest: Long
    ): MessagePage {
        val start = if (fromMessageId == 0L) newest else fromMessageId
        val end = maxOf(1L, start - limit + 1)
        return page((start downTo end).map(::message))
    }

    private fun page(messages: List<ChatMessage>) = MessagePage(messages)

    private fun message(
        id: Long,
        text: String = "message $id",
        sendState: MessageSendState = MessageSendState.Sent
    ) = ChatMessage(
        providerId = "test",
        accountId = "test",
        providerDialogId = "1",
        messageId = id,
        senderId = 1,
        text = text,
        timestamp = id,
        isOutgoing = false,
        sendState = sendState
    )

    private fun ids(controller: MessageHistoryController): List<Long> {
        return controller.state.value.messages.map { message -> message.messageId }
    }
}
