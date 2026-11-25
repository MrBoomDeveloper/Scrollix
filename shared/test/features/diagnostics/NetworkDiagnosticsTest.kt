package com.mrboomdev.scrollix.features.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkDiagnosticsTest {
    @Test
    fun `connection label follows the session and its flag`() {
        assertEquals("Signing in", networkConnectionLabel(hasSession = false, isConnected = null))
        assertEquals("Offline", networkConnectionLabel(hasSession = true, isConnected = false))
        assertEquals("Connected", networkConnectionLabel(hasSession = true, isConnected = true))

        // A flow that has not emitted yet must not look like a disconnect.
        assertEquals("Connected", networkConnectionLabel(hasSession = true, isConnected = null))
    }

    @Test
    fun `outbox summary lists only non-zero states`() {
        assertEquals("Nothing queued", outboxSummary(waiting = 0, sending = 0, failed = 0))
        assertEquals("2 waiting", outboxSummary(waiting = 2, sending = 0, failed = 0))
        assertEquals(
            "2 waiting, 1 sending, 3 failed",
            outboxSummary(waiting = 2, sending = 1, failed = 3)
        )
        assertEquals("1 failed", outboxSummary(waiting = 0, sending = 0, failed = 1))
    }
}
