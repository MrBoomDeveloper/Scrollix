package com.mrboomdev.scrollix.data.storage

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessagePollInfo
import com.mrboomdev.scrollix.data.model.MessagePollOptionInfo
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageRichBlock
import com.mrboomdev.scrollix.data.model.MessageVideoQuality
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaKeepTest {
    @Test
    fun `durable ids are collected from every media position`() {
        val photo = MessageMedia(
            kind = MessageMediaKind.Photo,
            previewUniqueId = "preview",
            fileUniqueId = "file",
            videoQualities = listOf(
                MessageVideoQuality(fileUniqueId = "quality"),
                MessageVideoQuality(fileUniqueId = " ")
            )
        )
        val sticker = MessageMedia(
            kind = MessageMediaKind.Sticker,
            stickerUniqueId = "sticker"
        )
        val replyMedia = MessageMedia(
            kind = MessageMediaKind.Video,
            fileUniqueId = "reply"
        )
        val pollMedia = MessageMedia(
            kind = MessageMediaKind.Animation,
            fileUniqueId = "poll"
        )
        val richMedia = MessageMedia(
            kind = MessageMediaKind.File,
            fileUniqueId = "rich"
        )

        val message = ChatMessage(
            providerId = "telegram",
            accountId = "account",
            providerDialogId = "dialog",
            messageId = 7,
            senderId = 1,
            text = "media",
            timestamp = 1,
            isOutgoing = true,
            media = photo,
            mediaItems = listOf(photo, sticker),
            replyTo = MessageReply(messageId = 1, media = replyMedia),
            poll = MessagePollInfo(
                id = 1,
                question = "Which?",
                options = listOf(
                    MessagePollOptionInfo(id = "a", text = "A", media = pollMedia)
                )
            ),
            richBlocks = listOf(MessageRichBlock.Media(media = richMedia))
        )

        assertEquals(
            setOf(
                "preview",
                "file",
                "quality",
                "sticker",
                "reply",
                "poll",
                "rich"
            ),
            mediaUniqueIds(listOf(message))
        )
    }

    @Test
    fun `messages without durable media contribute nothing`() {
        val message = ChatMessage(
            providerId = "telegram",
            accountId = "account",
            providerDialogId = "dialog",
            messageId = 1,
            senderId = 1,
            text = "text",
            timestamp = 1,
            isOutgoing = false,
            media = MessageMedia(kind = MessageMediaKind.File, fileId = 42)
        )

        assertEquals(emptySet(), mediaUniqueIds(listOf(message)))
    }
}
