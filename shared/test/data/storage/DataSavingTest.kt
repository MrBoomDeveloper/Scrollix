package com.mrboomdev.scrollix.data.storage

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataSavingTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `the default profile plays everything`() {
        val defaults = DataSavingSettings()

        assertTrue(defaults.animations)
        assertTrue(defaults.videoNotes)
    }

    @Test
    fun `the switches are independent`() {
        val animationsOnly = DataSavingSettings(animations = true, videoNotes = false)
        assertTrue(animationsOnly.animations)
        assertFalse(animationsOnly.videoNotes)

        val notesOnly = DataSavingSettings(animations = false, videoNotes = true)
        assertFalse(notesOnly.animations)
        assertTrue(notesOnly.videoNotes)
    }

    @Test
    fun `switching one value keeps the other`() {
        val changed = DataSavingSettings(animations = false).copy(videoNotes = false)

        assertEquals(DataSavingSettings(animations = false, videoNotes = false), changed)
    }

    @Test
    fun `a legacy file with a preset profile still decodes`() {
        val decoded = json.decodeFromString(
            DataSavingSettings.serializer(),
            """{"preset":"Low","animations":false,"videoNotes":true}"""
        )

        assertFalse(decoded.animations)
        assertTrue(decoded.videoNotes)
    }
}
