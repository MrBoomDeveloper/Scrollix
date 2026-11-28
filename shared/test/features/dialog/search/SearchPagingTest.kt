package com.mrboomdev.scrollix.features.dialog.search

import com.mrboomdev.scrollix.data.model.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchPagingTest {
    @Test
    fun `an empty page changes nothing`() {
        val existing = listOf(message(id = 30), message(id = 20))

        assertEquals(existing, appendSearchPage(existing, emptyList()))
    }

    @Test
    fun `older results are appended newest first`() {
        val existing = listOf(message(id = 50), message(id = 40))

        assertEquals(
            listOf(50L, 40L, 30L, 20L),
            appendSearchPage(existing, listOf(message(id = 20), message(id = 30)))
                .map { it.messageId }
        )
    }

    @Test
    fun `repeated ids are dropped`() {
        val existing = listOf(message(id = 40), message(id = 20))

        assertEquals(
            listOf(40L, 20L, 10L),
            appendSearchPage(existing, listOf(message(id = 40), message(id = 10)))
                .map { it.messageId }
        )
    }

    @Test
    fun `the cursor points just below the oldest loaded id`() {
        assertEquals(19L, searchPageCursor(listOf(message(id = 30), message(id = 20))))
        assertNull(searchPageCursor(emptyList()))
    }

    private fun message(id: Long): ChatMessage = ChatMessage(
        providerId = "telegram",
        accountId = "1",
        providerDialogId = "2",
        messageId = id,
        senderId = 3,
        text = "message $id",
        timestamp = id,
        isOutgoing = false
    )
}
