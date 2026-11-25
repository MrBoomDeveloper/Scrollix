package com.mrboomdev.scrollix.data.diagnostics

import com.mrboomdev.scrollix.data.storage.PlatformStorage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticsFileLogTest {
    @Test
    fun `entries are appended in order`() {
        val storage = MemoryStorage()

        appendDiagnosticsEntry(storage, entry(timestamp = 1, message = "first"), maxChars = 1000)
        appendDiagnosticsEntry(storage, entry(timestamp = 2, message = "second"), maxChars = 1000)

        val text = storage.readText(DiagnosticsLogFileName).orEmpty()

        assertTrue(text.indexOf("first") < text.indexOf("second"))
        assertTrue(text.contains("INFO app | first"))
        assertNull(storage.readText(DiagnosticsLogBackupFileName))
    }

    @Test
    fun `the file rotates and keeps the newest half`() {
        val storage = MemoryStorage()
        val first = entry(timestamp = 1, message = "A".repeat(80))

        appendDiagnosticsEntry(storage, first, maxChars = 140)
        appendDiagnosticsEntry(storage, entry(timestamp = 2, message = "B".repeat(80)), maxChars = 140)

        val backup = assertNotNull(storage.readText(DiagnosticsLogBackupFileName))
        val current = storage.readText(DiagnosticsLogFileName).orEmpty()

        assertTrue(backup.contains("A".repeat(80)))
        assertTrue(current.contains("B".repeat(80)))
        assertTrue(current.length <= 140)
    }

    @Test
    fun `details are indented below the entry`() {
        val storage = MemoryStorage()

        appendDiagnosticsEntry(
            storage = storage,
            entry = entry(
                timestamp = 1,
                message = "failed",
                level = LogLevel.Error,
                details = "IllegalStateException: boom"
            )
        )

        val text = storage.readText(DiagnosticsLogFileName).orEmpty()

        assertTrue(text.contains("ERROR app | failed"))
        assertTrue(text.contains("    IllegalStateException: boom"))
    }

    @Test
    fun `appending to a missing file creates it`() {
        val storage = MemoryStorage()

        storage.appendText(DiagnosticsLogFileName, "line")

        assertEquals("line", storage.readText(DiagnosticsLogFileName))
    }

    private fun entry(
        timestamp: Long,
        message: String,
        level: LogLevel = LogLevel.Info,
        details: String? = null
    ): LogEntry {
        return LogEntry(
            timestampMillis = timestamp,
            level = level,
            tag = "app",
            message = message,
            details = details
        )
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
