package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaSaveNameTest {
    @Test
    fun `a provider supplied name is kept`() {
        assertEquals(
            "report.pdf",
            savedMediaFileName(
                MessageMedia(kind = MessageMediaKind.File, fileName = "report.pdf")
            )
        )
    }

    @Test
    fun `a name without an extension gets one from the kind`() {
        assertEquals(
            "holiday.jpg",
            savedMediaFileName(
                MessageMedia(kind = MessageMediaKind.Photo, fileName = "holiday")
            )
        )
    }

    @Test
    fun `nameless media fall back to a kind default`() {
        assertEquals(
            "photo.jpg",
            savedMediaFileName(MessageMedia(kind = MessageMediaKind.Photo))
        )
        assertEquals(
            "sticker.webp",
            savedMediaFileName(MessageMedia(kind = MessageMediaKind.Sticker))
        )
        assertEquals(
            "voice.ogg",
            savedMediaFileName(MessageMedia(kind = MessageMediaKind.VoiceNote))
        )
    }

    @Test
    fun `a nameless document keeps its generic name`() {
        assertEquals("file", savedMediaFileName(MessageMedia(kind = MessageMediaKind.File)))
    }
}
