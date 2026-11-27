package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptionEditingTest {
    @Test
    fun `photo and file messages edit their caption`() {
        assertTrue(message(media = media(MessageMediaKind.Photo)).editsCaption)
        assertTrue(message(media = media(MessageMediaKind.File)).editsCaption)
        assertTrue(message(media = media(MessageMediaKind.Video)).editsCaption)
    }

    @Test
    fun `stickers and notes are never caption edits`() {
        assertFalse(message(media = media(MessageMediaKind.Sticker)).editsCaption)
        assertFalse(message(media = media(MessageMediaKind.VoiceNote)).editsCaption)
        assertFalse(message(media = media(MessageMediaKind.VideoNote)).editsCaption)
        assertFalse(message(media = null, text = "plain").editsCaption)
    }

    @Test
    fun `an album counts as a caption only when every item does`() {
        val album = message(media = media(MessageMediaKind.Photo)).copy(
            mediaItems = listOf(
                media(MessageMediaKind.Photo),
                media(MessageMediaKind.Video)
            )
        )

        assertTrue(album.editsCaption)

        val mixed = album.copy(
            mediaItems = album.mediaItems + media(MessageMediaKind.Sticker)
        )

        assertFalse(mixed.editsCaption)
    }

    @Test
    fun `caption editing needs a provider that supports it`() {
        val photo = message(media = media(MessageMediaKind.Photo))

        assertFalse(photo.canBeEdited(supportsCaptionEditing = false))
        assertTrue(photo.canBeEdited(supportsCaptionEditing = true))
    }

    @Test
    fun `plain messages keep their own rules`() {
        val textOnly = message(media = null, text = "hello")

        assertTrue(textOnly.canBeEdited(supportsCaptionEditing = false))
        assertFalse(message(media = null, text = "  ").canBeEdited(supportsCaptionEditing = true))
        assertFalse(textOnly.copy(isOutgoing = false).canBeEdited(supportsCaptionEditing = true))
        assertFalse(textOnly.copy(isService = true).canBeEdited(supportsCaptionEditing = true))
    }

    private fun message(media: MessageMedia?, text: String = "caption"): ChatMessage {
        return ChatMessage(
            providerId = "telegram",
            accountId = "account",
            providerDialogId = "dialog",
            messageId = 1,
            senderId = 1,
            text = text,
            timestamp = 0,
            isOutgoing = true,
            media = media
        )
    }

    private fun media(kind: MessageMediaKind): MessageMedia = MessageMedia(kind = kind)
}
