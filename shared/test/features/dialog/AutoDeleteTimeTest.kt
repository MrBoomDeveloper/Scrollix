package com.mrboomdev.scrollix.features.dialog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoDeleteTimeTest {
    @Test
    fun presetsStartWithOffAndKeepServerOrder() {
        val presets = autoDeletePresets(0)

        assertEquals("Off", presets.first().label)
        assertEquals(0, presets.first().seconds)
        assertEquals(
            listOf("Off", "1 hour", "1 day", "1 week", "1 month"),
            presets.map { it.label }
        )
    }

    @Test
    fun anUnknownTimerGetsItsOwnRow() {
        val presets = autoDeletePresets(5_400)

        assertEquals(5_400, presets.last().seconds)
        assertEquals("90 minutes", presets.last().label)
        assertEquals(6, presets.size)
    }

    @Test
    fun aKnownTimerIsNotDuplicated() {
        val presets = autoDeletePresets(604_800)

        assertEquals(5, presets.size)
        assertNull(presets.firstOrNull { it.seconds == 604_800 && it.label != "1 week" })
    }

    @Test
    fun labelsCoverTheCommonUnits() {
        assertEquals("Off", autoDeleteLabel(0))
        assertEquals("1 day", autoDeleteLabel(86_400))
        assertEquals("2 days", autoDeleteLabel(172_800))
        assertEquals("6 hours", autoDeleteLabel(21_600))
        assertEquals("1 week", autoDeleteLabel(604_800))
        assertEquals("1 month", autoDeleteLabel(2_592_000))
        assertEquals("90s", autoDeleteLabel(90))
    }

    @Test
    fun everyPresetHasAUniqueValue() {
        val seconds = autoDeletePresets(0).map { it.seconds }

        assertTrue(seconds == seconds.distinct())
    }
}
