package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.MessageScheduling

import dev.g000sha256.tdl.dto.MessageSchedulingStateSendAtDate
import dev.g000sha256.tdl.dto.MessageSchedulingStateSendWhenOnline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageSchedulingMappingTest {
    @Test
    fun `tdlib scheduling state maps into the shared model`() {
        assertEquals(
            MessageScheduling.At(1_700_000_000_000L),
            mapMessageScheduling(
                MessageSchedulingStateSendAtDate(
                    sendDate = 1_700_000_000,
                    repeatPeriod = 0
                )
            )
        )

        assertEquals(
            MessageScheduling.WhenOnline,
            mapMessageScheduling(MessageSchedulingStateSendWhenOnline())
        )

        assertNull(mapMessageScheduling(null))
    }

    @Test
    fun `scheduling becomes send options`() {
        assertNull((null as MessageScheduling?).sendOptions())

        val options = (MessageScheduling.At(1_700_000_000_000L) as MessageScheduling?)
            .sendOptions()

        val state = options?.schedulingState as? MessageSchedulingStateSendAtDate
        assertEquals(1_700_000_000, state?.sendDate)

        val online = (MessageScheduling.WhenOnline as MessageScheduling?)
            .sendOptions()
            ?.schedulingState

        assertTrue(online is MessageSchedulingStateSendWhenOnline)
    }
}
