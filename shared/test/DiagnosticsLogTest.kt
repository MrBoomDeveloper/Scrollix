package com.mrboomdev.scrollix.data.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticsLogTest {
    private fun log(
        capacity: Int = 10,
        clock: () -> Long = { 1_700_000_000_000L }
    ) = DiagnosticsLog(capacity = capacity, clock = clock)

    @Test
    fun `keeps entries in order and drops the oldest beyond capacity`() {
        var now = 1000L
        val log = log(capacity = 3, clock = { now++ })

        log.info("a", "first")
        log.warning("b", "second")
        log.error("c", "third")
        log.error("d", "fourth")

        val entries = log.snapshot()

        assertEquals(3, entries.size)
        assertEquals(listOf("second", "third", "fourth"), entries.map { it.message })
        assertEquals(listOf(1001L, 1002L, 1003L), entries.map { it.timestampMillis })
    }

    @Test
    fun `normalizes a blank tag and message`() {
        val log = log()

        log.record(LogLevel.Info, "   ", "  ")

        val entry = log.snapshot().single()
        assertEquals("app", entry.tag)
        assertEquals("(no message)", entry.message)
    }

    @Test
    fun `records the throwable type, message and cause chain without an entry for clean ones`() {
        val log = log()
        val failure = IllegalStateException(
            "Session is closed",
            IllegalArgumentException("socket reset")
        )

        log.error("telegram", "Could not load chats", failure)
        log.info("app", "Plain event")

        val detailed = log.snapshot().first()
        assertEquals("IllegalStateException: Session is closed\nCaused by: IllegalArgumentException: socket reset", detailed.details)

        assertNull(log.snapshot().last().details)
    }

    @Test
    fun `clear removes every entry`() {
        val log = log()
        log.info("a", "one")
        log.info("a", "two")

        log.clear()

        assertTrue(log.snapshot().isEmpty())
    }

    @Test
    fun `report lists header facts, counts and entries`() {
        val log = log(clock = { 1_724_000_000_000L })
        log.info("app", "Started")
        log.error("telegram", "TDLib failed")

        val report = log.report(
            DiagnosticsFacts(
                platform = "Desktop (Linux)",
                edition = "Test server",
                storageProfile = "scrollix-test",
                accounts = 2,
                generatedAtMillis = 1_724_000_000_000L
            )
        )

        assertTrue(report.startsWith("Scrollix diagnostics"))
        assertTrue(report.contains("Platform: Desktop (Linux)"))
        assertTrue(report.contains("Edition: Test server"))
        assertTrue(report.contains("Storage profile: scrollix-test"))
        assertTrue(report.contains("Accounts: 2"))
        assertTrue(report.contains("Log entries: 2 (1 error(s), 0 warning(s))"))
        assertTrue(report.contains("INFO app | Started"))
        assertTrue(report.contains("ERROR telegram | TDLib failed"))
        assertTrue(report.contains("2024-08-18T"))
    }

    @Test
    fun `report says so when nothing was recorded`() {
        val report = log().report()

        assertTrue(report.contains("Log entries: 0 (0 error(s), 0 warning(s))"))
        assertTrue(report.contains("No events were recorded."))
    }

    @Test
    fun `the app log forwards new entries to an attached sink`() {
        val received = mutableListOf<LogEntry>()

        try {
            AppLog.attachSink { entry -> received += entry }
            AppLog.info("sink", "forwarded")
        } finally {
            AppLog.attachSink(null)
        }

        assertEquals(listOf("forwarded"), received.map { entry -> entry.message })
    }

    @Test
    fun `protocol dumps redact credentials and truncate long payloads`() {
        val redacted = redactProtocolPayload(
            """{"access_token":"secret-token","api_hash":"secret-hash","text":"hello"}"""
        )

        assertTrue(redacted.contains("<redacted>"))
        assertTrue(!redacted.contains("secret-token"))
        assertTrue(!redacted.contains("secret-hash"))
        assertTrue(redacted.contains("hello"))

        val form = redactProtocolPayload("access_token=abc123&v=5.199&message=hi")
        assertTrue(!form.contains("abc123"))
        assertTrue(form.contains("v=5.199"))

        val truncated = redactProtocolPayload("x".repeat(2_000), maxChars = 100)
        assertTrue(truncated.endsWith("... (truncated)"))
        assertTrue(truncated.length < 130)
    }

    @Test
    fun `the app log emits protocol frames only when enabled`() {
        val received = mutableListOf<LogEntry>()
        val previous = AppLog.protocolDumpsEnabled

        try {
            AppLog.attachSink { entry -> received += entry }
            AppLog.protocolDumpsEnabled = false
            AppLog.protocol("max", "in opcode=2", "payload")
            assertTrue(received.isEmpty())

            AppLog.protocolDumpsEnabled = true
            AppLog.protocol("max", "in opcode=2", """{"access_token":"secret","text":"hi"}""")
        } finally {
            AppLog.protocolDumpsEnabled = previous
            AppLog.attachSink(null)
        }

        val entry = received.single()
        assertEquals("protocol/max", entry.tag)
        assertTrue(entry.message.contains("hi"))
        assertTrue(!entry.message.contains("secret"))
    }

    @Test
    fun `errors with a throwable reach the non-fatal handler`() {
        val received = mutableListOf<String>()
        val throwable = IllegalStateException("handled")

        try {
            AppLog.attachNonFatalHandler { tag, message, failure ->
                received += "$tag|$message|${failure.message}"
            }
            AppLog.error("telegram", "load failed", throwable)
            AppLog.error("telegram", "plain error")
        } finally {
            AppLog.attachNonFatalHandler(null)
        }

        assertEquals(listOf("telegram|load failed|handled"), received)
    }

    @Test
    fun `a zero capacity log records nothing instead of failing`() {
        val log = log(capacity = 0)
        log.error("a", "ignored")

        assertTrue(log.snapshot().isEmpty())
    }
}
