package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionCopyTest {
    @Test
    fun `messages are joined oldest first with a blank line`() {
        val newest = message(id = 2, text = "second", timestamp = 2_000)
        val oldest = message(id = 1, text = "first", timestamp = 1_000)

        assertEquals(
            "first\n\nsecond",
            selectedMessageText(listOf(newest, oldest))
        )
    }

    @Test
    fun `service and blank messages are skipped`() {
        val text = message(id = 1, text = "hello", timestamp = 1_000)
        val service = message(id = 2, text = "Wallpaper changed", timestamp = 1_500, service = true)
        val empty = message(id = 3, text = "   ", timestamp = 2_000)

        assertEquals("hello", selectedMessageText(listOf(text, service, empty)))
    }

    @Test
    fun `nothing to copy gives an empty string`() {
        assertEquals(
            "",
            selectedMessageText(listOf(message(id = 1, text = "", timestamp = 1_000)))
        )
        assertEquals("", selectedMessageText(emptyList()))
    }

    @Test
    fun `an album row is copied once`() {
        val album = message(id = 5, text = "captioned album", timestamp = 1_000)

        assertEquals("captioned album", selectedMessageText(listOf(album, album)))
    }

    private fun message(
        id: Long,
        text: String,
        timestamp: Long,
        service: Boolean = false
    ) = ChatMessage(
        providerId = "telegram",
        accountId = "account",
        providerDialogId = "dialog",
        messageId = id,
        senderId = 1,
        text = text,
        timestamp = timestamp,
        isOutgoing = false,
        isService = service
    )
}
