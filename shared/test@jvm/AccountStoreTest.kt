package com.mrboomdev.scrollix.data.storage

import com.mrboomdev.scrollix.data.model.AccountRecord
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountStoreTest {
    @Test
    fun `accounts persist and active account falls back after removal`() = runBlocking {
        val storage = MemoryStorage()
        val store = AccountStore(storage)
        val first = account("first", addedAt = 1)
        val second = account("second", addedAt = 2)

        store.add(first)
        assertEquals(first.id, store.state.value.activeAccountId)

        store.add(second)
        store.setActive(second.id)
        assertEquals(second.id, store.state.value.activeAccountId)

        val reloaded = AccountStore(storage)
        assertEquals(
            listOf(first.id, second.id),
            reloaded.state.value.accounts.map { it.id }
        )
        assertEquals(second.id, reloaded.state.value.activeAccountId)

        reloaded.remove(second.id)
        assertEquals(first.id, reloaded.state.value.activeAccountId)
    }

    private fun account(id: String, addedAt: Long): AccountRecord {
        return AccountRecord(
            id = id,
            providerId = "telegram",
            displayName = id,
            isPending = false,
            addedAt = addedAt,
            lastUsedAt = addedAt
        )
    }

    private class MemoryStorage : PlatformStorage {
        private val files = mutableMapOf<String, String>()

        override val rootDirectory: String = "/memory"

        override fun readText(relativePath: String): String? {
            return files[relativePath]
        }

        override fun writeText(relativePath: String, text: String) {
            files[relativePath] = text
        }

        override fun delete(relativePath: String) {
            files.keys.removeAll { path ->
                path == relativePath || path.startsWith("$relativePath/")
            }
        }

        override fun ensureDirectory(relativePath: String): String {
            return "$rootDirectory/$relativePath"
        }

        override fun absolutePath(relativePath: String): String {
            return "$rootDirectory/$relativePath"
        }

        override fun exists(relativePath: String): Boolean {
            return files.containsKey(relativePath)
        }
    }
}
