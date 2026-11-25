package com.mrboomdev.scrollix.data.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageIntegrityTest {
    @Test
    fun `an unreadable file is copied aside and the original is removed`() {
        val storage = MemoryStorage()
        storage.writeText("settings.json", "{broken")

        val copy = StorageIntegrity.quarantine(
            storage = storage,
            fileName = "settings.json",
            content = "{broken",
            reason = "unexpected token"
        )

        assertEquals("settings.json.corrupt", copy)
        assertNull(storage.readText("settings.json"))
        assertEquals("{broken", storage.readText("settings.json.corrupt"))
    }

    @Test
    fun `copies are listed with the original name and their size`() {
        val storage = MemoryStorage()
        storage.writeText("outbox.json.corrupt", "{\"entries\":[]}")

        val copies = StorageIntegrity.copies(storage)

        assertEquals(1, copies.size)
        assertEquals("outbox.json.corrupt", copies.single().fileName)
        assertEquals("outbox.json", copies.single().label)
        assertEquals("{\"entries\":[]}".length.toLong(), copies.single().sizeBytes)
    }

    @Test
    fun `nothing is listed when every file was readable`() {
        assertTrue(StorageIntegrity.copies(MemoryStorage()).isEmpty())
    }

    @Test
    fun `deleting the copies leaves the state files alone`() {
        val storage = MemoryStorage()
        storage.writeText("settings.json", "{}")
        storage.writeText("settings.json.corrupt", "{broken")
        storage.writeText("outbox.json.corrupt", "[broken")

        StorageIntegrity.deleteCopies(storage)

        assertTrue(StorageIntegrity.copies(storage).isEmpty())
        assertEquals("{}", storage.readText("settings.json"))
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
            text[relativePath]?.let { value -> return value.length.toLong() }

            return text
                .filterKeys { path -> path.startsWith("$relativePath/") }
                .values
                .sumOf { value -> value.length.toLong() }
        }
    }
}
