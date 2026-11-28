package com.mrboomdev.scrollix.data.outbox

import com.mrboomdev.scrollix.data.storage.AppSettings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoCompressionTest {

    @Test
    fun `a photo only travels as a photo while compression is on`() {
        assertTrue(photoKeepsPhotoForm(asPhoto = true, compressPhotos = true))
        assertFalse(photoKeepsPhotoForm(asPhoto = true, compressPhotos = false))
    }

    @Test
    fun `a document is never turned into a photo`() {
        assertFalse(photoKeepsPhotoForm(asPhoto = false, compressPhotos = true))
        assertFalse(photoKeepsPhotoForm(asPhoto = false, compressPhotos = false))
    }

    @Test
    fun `settings written before the switch still compress photos`() {
        val decoded = Json { ignoreUnknownKeys = true }
            .decodeFromString(AppSettings.serializer(), """{"settingsVersion":1}""")

        assertTrue(decoded.compressPhotos)
    }
}
