package com.mrboomdev.scrollix.data.diagnostics

import com.mrboomdev.scrollix.data.storage.PlatformStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashReportTest {
    @Test
    fun `report contains environment, stack trace and recent log`() {
        val throwable = IllegalStateException("boom", IllegalArgumentException("bad state"))

        val report = buildCrashReport(
            threadName = "main",
            throwable = throwable,
            timestampMillis = 1_700_000_000_000L,
            platform = "Desktop",
            edition = "Production",
            storageProfile = "scrollix",
            logEntries = listOf(
                LogEntry(
                    timestampMillis = 1_700_000_000_000L,
                    level = LogLevel.Error,
                    tag = "telegram",
                    message = "TDLib failed",
                    details = "IllegalStateException: boom"
                )
            )
        )

        assertTrue(report.startsWith("Scrollix crash report"))
        assertTrue(report.contains("Kind: uncaught"))
        assertTrue(report.contains("Thread: main"))
        assertTrue(report.contains("IllegalStateException: boom"))
        assertTrue(report.contains("Caused by:"))
        assertTrue(report.contains("IllegalArgumentException: bad state"))
        assertTrue(report.contains("ERROR telegram | TDLib failed"))
        assertTrue(report.contains("    IllegalStateException: boom"))
    }

    @Test
    fun `only the newest log entries are kept`() {
        val entries = (1..100).map { index ->
            LogEntry(
                timestampMillis = index.toLong(),
                level = LogLevel.Info,
                tag = "app",
                message = "entry-$index"
            )
        }

        val report = buildCrashReport(
            threadName = "main",
            throwable = RuntimeException("failure"),
            timestampMillis = 1L,
            platform = "Desktop",
            edition = "Production",
            storageProfile = "scrollix",
            logEntries = entries
        )

        assertFalse(report.contains("| entry-1\n"))
        assertTrue(report.contains("| entry-100"))
    }

    @Test
    fun `handled errors produce a non-fatal report with context`() {
        val report = buildNonFatalReport(
            tag = "telegram",
            message = "Could not load chats",
            throwable = IllegalStateException("boom"),
            timestampMillis = 1_700_000_000_000L,
            platform = "Desktop",
            edition = "Production",
            storageProfile = "scrollix",
            logEntries = listOf(
                LogEntry(
                    timestampMillis = 1_700_000_000_000L,
                    level = LogLevel.Error,
                    tag = "telegram",
                    message = "Could not load chats"
                )
            )
        )

        assertTrue(report.startsWith("Scrollix non-fatal report"))
        assertTrue(report.contains("Kind: handled"))
        assertTrue(report.contains("Context: telegram | Could not load chats"))
        assertTrue(report.contains("IllegalStateException: boom"))
        assertTrue(report.contains("ERROR telegram | Could not load chats"))
    }

    @Test
    fun `store keeps the crash and non-fatal reports separate`() {
        val store = CrashReportStore(MemoryStorage())

        store.save("uncaught report")
        store.saveNonFatal("handled report")

        assertEquals("uncaught report", store.read())
        assertEquals("handled report", store.readNonFatal())

        store.clearNonFatal()
        assertNull(store.readNonFatal())
        assertEquals("uncaught report", store.read())

        store.clear()
        assertNull(store.read())
    }

    @Test
    fun `store saves reads truncates and clears the last report`() {
        val storage = MemoryStorage()
        val store = CrashReportStore(storage)

        assertNull(store.read())

        store.save("first report")
        assertEquals("first report", store.read())

        store.save("x".repeat(100_000))
        val stored = store.read().orEmpty()
        assertTrue(stored.length < 100_000)
        assertTrue(stored.contains("crash report truncated"))

        store.clear()
        assertNull(store.read())
    }

    private class MemoryStorage : PlatformStorage {
        private val text = mutableMapOf<String, String>()

        override val rootDirectory: String = "/memory"

        override fun readText(relativePath: String): String? = text[relativePath]

        override fun writeText(relativePath: String, text: String) {
            this.text[relativePath] = text
        }

        override fun readBytes(relativePath: String): ByteArray? = null

        override fun writeBytes(relativePath: String, bytes: ByteArray) = Unit

        override fun delete(relativePath: String) {
            text.keys.removeAll { path ->
                path == relativePath || path.startsWith("$relativePath/")
            }
        }

        override fun ensureDirectory(relativePath: String): String = "$rootDirectory/$relativePath"

        override fun absolutePath(relativePath: String): String = "$rootDirectory/$relativePath"

        override fun exists(relativePath: String): Boolean = text.containsKey(relativePath)

        override fun listFiles(relativePath: String): List<String> {
            return text.keys
                .filter { path -> path.startsWith("$relativePath/") }
                .map { path -> path.removePrefix("$relativePath/") }
        }

        override fun size(relativePath: String): Long {
            return text
                .filterKeys { path -> path.startsWith("$relativePath/") }
                .values
                .sumOf { value -> value.length.toLong() }
        }
    }
}
