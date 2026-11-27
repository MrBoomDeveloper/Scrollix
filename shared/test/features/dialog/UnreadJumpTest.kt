package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnreadJumpTest {
    @Test
    fun `a loaded message resolves to its row`() {
        val messages = listOf(message(1), message(2))

        assertEquals(messages[1].rowId, unreadJumpRowId(messages, 2))
    }

    @Test
    fun `an album member resolves to the album row`() {
        val album = message(1, albumId = 9, messageIds = listOf(1, 2))

        assertEquals(album.rowId, unreadJumpRowId(listOf(album), 2))
    }

    @Test
    fun `an unknown or missing anchor has no target`() {
        assertNull(unreadJumpRowId(listOf(message(1)), 5))
        assertNull(unreadJumpRowId(listOf(message(1)), null))
    }

    private fun message(
        messageId: Long,
        albumId: Long? = null,
        messageIds: List<Long> = emptyList()
    ) = ChatMessage(
        providerId = "telegram",
        accountId = "account",
        providerDialogId = "chat",
        messageId = messageId,
        senderId = 1,
        text = "",
        timestamp = 0,
        isOutgoing = false,
        mediaAlbumId = albumId,
        messageIds = messageIds
    )
}
