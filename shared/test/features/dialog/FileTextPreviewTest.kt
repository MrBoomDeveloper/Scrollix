package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileTextPreviewTest {
    private fun file(
        name: String = "notes.txt",
        mime: String? = null
    ) = MessageMedia(
        kind = MessageMediaKind.File,
        fileName = name,
        mimeType = mime
    )

    @Test
    fun `text mime types are previewable`() {
        assertTrue(isTextPreviewable(file(name = "log", mime = "text/plain")))
        assertTrue(isTextPreviewable(file(name = "payload", mime = "application/json")))
    }

    @Test
    fun `known extensions are previewable without a mime type`() {
        assertTrue(isTextPreviewable(file(name = "README.md")))
        assertTrue(isTextPreviewable(file(name = "build.gradle.kts")))
    }

    @Test
    fun `binary files are not previewable`() {
        assertFalse(isTextPreviewable(file(name = "photo.jpg", mime = "image/jpeg")))
        assertFalse(isTextPreviewable(file(name = "archive.zip")))
        assertFalse(isTextPreviewable(file(name = "doc.pdf", mime = "application/pdf")))
    }

    @Test
    fun `preview decodes utf8 text`() {
        assertEquals("Привет\n", previewText("Привет\n".encodeToByteArray()))
        assertEquals("", previewText(ByteArray(0)))
    }

    @Test
    fun `long files are cut and say so`() {
        val bytes = ByteArray(64) { 'a'.code.toByte() }
        val preview = previewText(bytes, limitBytes = 16)

        assertTrue(preview.startsWith("a".repeat(16)))
        assertTrue(preview.endsWith("preview truncated at 0 KB"))
    }
}
