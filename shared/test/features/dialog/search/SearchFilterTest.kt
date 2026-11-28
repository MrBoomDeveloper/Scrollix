package com.mrboomdev.scrollix.features.dialog.search

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageLinkPreview
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchFilterTest {
    private fun message(
        id: Long,
        text: String = "",
        outgoing: Boolean = false,
        kind: MessageMediaKind? = null,
        preview: String? = null
    ) = ChatMessage(
        providerId = "telegram",
        accountId = "1",
        providerDialogId = "10",
        messageId = id,
        senderId = if (outgoing) 1 else 2,
        text = text,
        timestamp = id,
        isOutgoing = outgoing,
        media = kind?.let { MessageMedia(kind = it) },
        linkPreview = preview?.let { MessageLinkPreview(url = it) }
    )

    private val messages = listOf(
        message(1, text = "plain incoming"),
        message(2, text = "see https://example.com", outgoing = true),
        message(3, kind = MessageMediaKind.Photo),
        message(4, kind = MessageMediaKind.Video, outgoing = true),
        message(5, kind = MessageMediaKind.File),
        message(6, text = "caption only", preview = "https://example.com/a"),
        message(7, kind = MessageMediaKind.VideoNote),
        message(8, kind = MessageMediaKind.Animation),
        message(9, kind = MessageMediaKind.VoiceNote)
    )

    private fun ids(filter: DialogSearchFilter) =
        filterSearchMessages(messages, filter).map { it.messageId }

    @Test
    fun `all keeps every match`() {
        assertEquals((1L..9L).toList(), ids(DialogSearchFilter.All))
    }

    @Test
    fun `from me keeps only outgoing messages`() {
        assertEquals(listOf(2L, 4L), ids(DialogSearchFilter.FromMe))
    }

    @Test
    fun `photos and files match their own media kind`() {
        assertEquals(listOf(3L), ids(DialogSearchFilter.Photos))
        assertEquals(listOf(5L), ids(DialogSearchFilter.Files))
    }

    @Test
    fun `videos include video notes and animations`() {
        assertEquals(listOf(4L, 7L, 8L), ids(DialogSearchFilter.Videos))
    }

    @Test
    fun `links match a preview or a text url`() {
        assertEquals(listOf(2L, 6L), ids(DialogSearchFilter.Links))
    }
}
