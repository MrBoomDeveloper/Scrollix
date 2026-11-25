package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaViewerTest {
    @AfterTest
    fun tearDown() = MediaViewer.close()

    @Test
    fun `a gallery keeps its caption and drops an open single viewer`() {
        MediaViewer.showImage(photo("first"), session = null)
        MediaViewer.showGallery(
            media = listOf(photo("a"), photo("b")),
            initialIndex = 1,
            session = null,
            caption = "Album caption"
        )

        assertNull(MediaViewer.request.value)
        assertEquals("Album caption", MediaViewer.gallery.value?.caption)
        assertEquals(1, MediaViewer.gallery.value?.initialIndex)
    }

    @Test
    fun `a single photo keeps its caption and defaults to empty`() {
        MediaViewer.showImage(photo("a"), session = null, caption = "Photo caption")
        assertEquals("Photo caption", MediaViewer.request.value?.caption)
        assertEquals(false, MediaViewer.request.value?.isVideo)

        MediaViewer.showVideo(photo("b"), session = null)
        assertEquals("", MediaViewer.request.value?.caption)
        assertEquals(true, MediaViewer.request.value?.isVideo)
    }

    @Test
    fun `an out of range gallery index is clamped`() {
        MediaViewer.showGallery(
            media = listOf(photo("a"), photo("b")),
            initialIndex = 42,
            session = null
        )

        assertEquals(1, MediaViewer.gallery.value?.initialIndex)
    }

    @Test
    fun `an empty gallery is ignored`() {
        MediaViewer.showGallery(media = emptyList(), initialIndex = 0, session = null)

        assertNull(MediaViewer.gallery.value)
        assertEquals(false, MediaViewer.isShowing)
    }

    @Test
    fun `closing clears every request`() {
        MediaViewer.showGallery(listOf(photo("a")), initialIndex = 0, session = null)
        MediaViewer.close()

        assertNull(MediaViewer.gallery.value)
        assertNull(MediaViewer.request.value)
        assertEquals(false, MediaViewer.isShowing)
    }

    private fun photo(id: String) = MessageMedia(
        kind = MessageMediaKind.Photo,
        fileUniqueId = id,
        fileName = "$id.jpg"
    )
}
