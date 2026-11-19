package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.ChatMessage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageHistoryControllerTest {
    @Test
    fun `loads the newest page and exposes it oldest first`() = runBlocking {
        val controller = controller { _, limit ->
            Result.success((100L downTo 100L - limit + 1).map(::message))
        }

        val result = controller.loadLatest(limit = 50)

        assertTrue(result.isSuccess)
        assertEquals((51L..100L).toList(), ids(controller))
        assertTrue(controller.state.value.hasMoreOlder)
        assertFalse(controller.state.value.isLoadingInitial)
    }

    @Test
    fun `older pages are prepended until the chat start is reached`() = runBlocking {
        val requests = mutableListOf<Long>()
        val controller = controller { fromMessageId, limit ->
            requests += fromMessageId

            Result.success(
                when {
                    fromMessageId == 0L -> (100L downTo 51L).toList()
                    fromMessageId > 1L -> ((fromMessageId - 1) downTo 1L).take(limit).toList()
                    else -> listOf(1L)
                }.map(::message)
            )
        }

        controller.loadLatest(limit = 50)

        val nextPageExists = controller.loadOlder(limit = 50)
        assertTrue(nextPageExists.getOrThrow())
        assertEquals((1L..100L).toList(), ids(controller))

        val nextPageExistsAgain = controller.loadOlder(limit = 50)
        assertFalse(nextPageExistsAgain.getOrThrow())
        assertFalse(controller.state.value.hasMoreOlder)

        // The newest page, then the page before 51 and finally the anchor-only
        // page that proves the beginning of the chat.
        assertEquals(listOf(0L, 51L, 1L), requests)
    }

    @Test
    fun `jumps load history until the requested message is available`() = runBlocking {
        val controller = controller { fromMessageId, limit ->
            Result.success(
                when {
                    fromMessageId == 0L -> (100L downTo 51L).toList()
                    fromMessageId > 1L -> ((fromMessageId - 1) downTo 1L).take(limit).toList()
                    else -> listOf(1L)
                }.map(::message)
            )
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
            Result.success(listOf(message(2), message(1)))
        }

        controller.loadLatest(limit = 50)
        controller.merge(listOf(message(1, text = "edited"), message(3)))

        assertEquals(listOf(1L, 2L, 3L), ids(controller))
        assertEquals("edited", controller.state.value.messages.first().text)
        // A short page is not treated as proof of the chat start; any older
        // history is discovered by the first older-page request.
        assertTrue(controller.state.value.hasMoreOlder)
    }

    @Test
    fun `a short newest page does not hide older history`() = runBlocking {
        val controller = controller { fromMessageId, _ ->
            Result.success(
                when (fromMessageId) {
                    0L -> listOf(message(10))
                    10L -> listOf(message(9), message(8), message(7))
                    else -> listOf(message(fromMessageId))
                }
            )
        }

        controller.loadLatest(limit = 50)

        assertTrue(controller.state.value.hasMoreOlder)
        assertEquals(listOf(10L), ids(controller))

        controller.loadOlder(limit = 50)

        assertEquals(listOf(7L, 8L, 9L, 10L), ids(controller))
        assertFalse(controller.state.value.hasMoreOlder)
    }

    @Test
    fun `jump to a missing message does not page through the whole history`() = runBlocking {
        var requests = 0

        val controller = controller { _, _ ->
            requests++
            Result.success((100L downTo 51L).map(::message))
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

            val id = if (fromMessageId == 0L) 100L else fromMessageId - 1
            Result.success(listOf(message(id)))
        }

        controller.loadLatest(limit = 50)
        val hasMore = controller.loadOlder(limit = 50)

        assertTrue(hasMore.getOrThrow())
        assertEquals((92L..100L).toList(), ids(controller))
        assertEquals(9, requests)
    }

    @Test
    fun `stops when a page cannot move the cursor`() = runBlocking {
        val controller = controller { fromMessageId, _ ->
            Result.success(listOf(message(if (fromMessageId == 0L) 10L else fromMessageId)))
        }

        controller.loadLatest(limit = 50)

        assertFalse(controller.loadOlder(limit = 50).getOrThrow())
        assertFalse(controller.state.value.hasMoreOlder)
    }

    private fun controller(
        maxJumpPages: Int = 100,
        fetch: suspend (fromMessageId: Long, limit: Int) -> Result<List<ChatMessage>>
    ): MessageHistoryController {
        return MessageHistoryController(
            pageLoader = object : MessagePageLoader {
                override suspend fun load(
                    fromMessageId: Long,
                    limit: Int
                ): Result<List<ChatMessage>> {
                    return fetch(fromMessageId, limit)
                }
            },
            maxJumpPages = maxJumpPages
        )
    }

    private fun message(id: Long, text: String = "message $id") = ChatMessage(
        providerId = "test",
        accountId = "test",
        providerDialogId = "1",
        messageId = id,
        senderId = 1,
        text = text,
        timestamp = id,
        isOutgoing = false
    )

    private fun ids(controller: MessageHistoryController): List<Long> {
        return controller.state.value.messages.map { message -> message.messageId }
    }
}
