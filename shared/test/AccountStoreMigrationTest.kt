package com.mrboomdev.scrollix.data.storage

import com.mrboomdev.scrollix.data.model.AccountRecord

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountStoreMigrationTest {
    @Test
    fun `legacy files upgrade and duplicates collapse to the newest record`() {
        val storage = MemoryStorage()
        storage.writeText(
            "accounts.json",
            """
            {
              "accounts": [
                {"id":"a","providerId":"telegram","displayName":"old","addedAt":1,"lastUsedAt":1},
                {"id":"a","providerId":"telegram","displayName":"new","addedAt":2,"lastUsedAt":2}
              ],
              "activeAccountId": "missing"
            }
            """.trimIndent()
        )

        val state = AccountStore(storage).state.value

        assertEquals(CurrentAccountStoreVersion, state.version)
        assertEquals(1, state.accounts.size)
        assertEquals("new", state.accounts.single().displayName)
        assertEquals("a", state.activeAccountId)
        assertTrue(storage.readText("accounts.json").orEmpty().contains("\"version\": 1"))
    }

    @Test
    fun `a missing file starts at the current version`() {
        val state = AccountStore(MemoryStorage()).state.value

        assertEquals(CurrentAccountStoreVersion, state.version)
        assertEquals(emptyList(), state.accounts)
    }

    @Test
    fun `a future file is not downgraded`() {
        val future = AccountStoreState(
            version = CurrentAccountStoreVersion + 10,
            accounts = listOf(account("a")),
            activeAccountId = null
        )

        assertEquals(future, migrateAccountStore(future))
    }

    private fun account(id: String): AccountRecord {
        return AccountRecord(
            id = id,
            providerId = "telegram",
            displayName = id,
            isPending = false,
            addedAt = 1,
            lastUsedAt = 1
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
