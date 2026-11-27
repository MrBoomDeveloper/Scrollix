package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.MessageScheduling

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class SchedulingTest {
    private val zone = TimeZone.UTC

    @Test
    fun `presets compute the offsets`() {
        val now = 1_700_000_000_000L
        val presets = schedulePresets(now, zone)

        assertEquals(MessageScheduling.At(now + 30 * 60_000L), presets[0].scheduling)
        assertEquals(MessageScheduling.At(now + 60 * 60_000L), presets[1].scheduling)
        assertEquals(MessageScheduling.At(now + 180 * 60_000L), presets[2].scheduling)
        assertEquals(MessageScheduling.WhenOnline, presets.last().scheduling)
    }

    @Test
    fun `tomorrow at nine follows the time zone`() {
        val now = Instant.parse("2026-11-25T18:30:00Z").toEpochMilliseconds()
        val tomorrow = schedulePresets(now, zone)
            .first { preset -> preset.label == "Tomorrow at 9:00" }
            .scheduling as MessageScheduling.At

        assertEquals(
            "2026-11-26T09:00:00Z",
            Instant.fromEpochMilliseconds(tomorrow.sendDateMillis).toString()
        )
    }

    @Test
    fun `custom date and time are parsed`() {
        assertEquals(
            Instant.parse("2026-12-01T08:15:00Z").toEpochMilliseconds(),
            parseScheduleDateTime("2026-12-01", "08:15", zone)
        )

        assertNull(parseScheduleDateTime("2026-13-01", "08:15", zone))
        assertNull(parseScheduleDateTime("2026-12-01", "24:00", zone))
        assertNull(parseScheduleDateTime("tomorrow", "08:15", zone))
        assertNull(parseScheduleDateTime("2026-12-01", "8.15", zone))
    }

    @Test
    fun `labels describe the scheduling state`() {
        assertEquals("When online", MessageScheduling.WhenOnline.scheduleLabel())
    }
}
