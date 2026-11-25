package com.mrboomdev.scrollix.data.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataSavingTest {
    @Test
    fun `presets turn the matching switches on and off`() {
        val low = DataSavingSettings.of(DataSavingPreset.Low)
        assertEquals(false, low.animations)
        assertEquals(false, low.videoNotes)
        assertEquals(DataSavingPreset.Low, low.matchingPreset)

        val high = DataSavingSettings.of(DataSavingPreset.High)
        assertEquals(true, high.animations)
        assertEquals(true, high.videoNotes)
        assertEquals(DataSavingPreset.High, high.matchingPreset)
    }

    @Test
    fun `a changed switch stops matching its preset`() {
        // Animations off with round video notes on matches no preset.
        val custom = DataSavingSettings.of(DataSavingPreset.Low).copy(videoNotes = true)
        assertNull(custom.matchingPreset)
    }

    @Test
    fun `the default profile plays everything`() {
        val defaults = DataSavingSettings()
        assertTrue(defaults.animations)
        assertTrue(defaults.videoNotes)
    }
}
