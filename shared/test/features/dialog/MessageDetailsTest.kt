package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageDetailsTest {
    @Test
    fun `an edited message lists both timestamps`() {
        val rows = messageDetailsRows(
            message(
                timestamp = 1_000L,
                editDate = 5_000L
            ),
            formatDate = { millis -> "at $millis" }
        )

        assertEquals("at 1000", rows.value("Sent"))
        assertEquals("at 5000", rows.value("Edited"))
    }

    @Test
    fun `a never edited message has no edited row`() {
        val rows = messageDetailsRows(message(timestamp = 1_000L), formatDate = { "date" })

        assertNull(rows.value("Edited"))
    }

    @Test
    fun `outgoing messages are attributed to you and forwards keep their source`() {
        val rows = messageDetailsRows(
            message().copy(forwardedFromName = "Ann"),
            formatDate = { "date" }
        )

        assertEquals("You", rows.value("From"))
        assertEquals("Ann", rows.value("Forwarded from"))
    }

    @Test
    fun `a single photo row shows its size`() {
        val rows = messageDetailsRows(
            message().copy(
                media = MessageMedia(
                    kind = MessageMediaKind.Photo,
                    width = 1920,
                    height = 1080,
                    size = 2048
                )
            ),
            formatDate = { "date" }
        )

        assertEquals("Photo · 1920×1080 · 2 KB", rows.value("Media"))
    }

    @Test
    fun `an album row counts its items`() {
        val rows = messageDetailsRows(
            message().copy(
                mediaItems = listOf(
                    MessageMedia(kind = MessageMediaKind.Photo),
                    MessageMedia(kind = MessageMediaKind.Video),
                    MessageMedia(kind = MessageMediaKind.Photo)
                )
            ),
            formatDate = { "date" }
        )

        assertEquals("3 items · Photo, Video", rows.value("Media"))
    }

    @Test
    fun `a permalink appears only when the provider produced one`() {
        val withLink = messageDetailsRows(
            message(),
            permalink = "https://t.me/chat/7",
            formatDate = { "date" }
        )

        assertEquals("https://t.me/chat/7", withLink.value("Link"))

        val noLink = messageDetailsRows(message(), permalink = "", formatDate = { "date" })
        assertNull(noLink.value("Link"))
    }

    @Test
    fun `queued rows hide their synthetic id and counters hide at zero`() {
        val queued = messageDetailsRows(
            message().copy(messageId = -4L, viewCount = 0, shareCount = 0, replyCount = 0),
            formatDate = { "date" }
        )

        assertNull(queued.value("Message id"))
        assertNull(queued.value("Views"))
        assertNull(queued.value("Shares"))
        assertNull(queued.value("Replies"))
    }

    @Test
    fun `message id and counters are listed when present`() {
        val rows = messageDetailsRows(
            message().copy(viewCount = 12, shareCount = 1, replyCount = 3),
            formatDate = { "date" }
        )

        assertEquals("7", rows.value("Message id"))
        assertEquals("12", rows.value("Views"))
        assertEquals("1", rows.value("Shares"))
        assertEquals("3", rows.value("Replies"))
        assertTrue(rows.any { row -> row.label == "Sent" })
    }

    private fun List<MessageDetailRow>.value(label: String): String? {
        return firstOrNull { row -> row.label == label }?.value
    }

    private fun message(timestamp: Long = 0, editDate: Long = 0): ChatMessage {
        return ChatMessage(
            providerId = "telegram",
            accountId = "account",
            providerDialogId = "dialog",
            messageId = 7,
            senderId = 1,
            text = "hello",
            timestamp = timestamp,
            editDate = editDate,
            isOutgoing = true
        )
    }
}
