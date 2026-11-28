package com.mrboomdev.scrollix.data.outbox

import com.mrboomdev.scrollix.data.model.MessageScheduling
import com.mrboomdev.scrollix.data.storage.PlatformStorage

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutboxStoreTest {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `queued messages survive a restart`() = runBlocking {
        val storage = MemoryStorage()
        val store = OutboxStore(storage)
        val entry = entry("one")

        store.add(entry)

        val reloaded = OutboxStore(storage)
        assertEquals(listOf(entry), reloaded.state.value.entries)
        assertEquals(CurrentOutboxVersion, reloaded.state.value.version)
    }

    @Test
    fun `queued caption edits keep their kind across a restart`() = runBlocking {
        val storage = MemoryStorage()
        val store = OutboxStore(storage)

        store.add(
            entry("caption").copy(
                kind = OutboxEntryKind.Edit,
                targetMessageIds = listOf(42),
                text = "new caption",
                isCaptionEdit = true
            )
        )

        val reloaded = OutboxStore(storage).state.value.entries.single()
        assertEquals(OutboxEntryKind.Edit, reloaded.kind)
        assertEquals(listOf(42L), reloaded.targetMessageIds)
        assertTrue(reloaded.isCaptionEdit)
    }

    @Test
    fun `an interrupted send is queued again at startup`() = runBlocking {
        val storage = MemoryStorage()
        storage.writeText(
            "outbox.json",
            json.encodeToString(
                OutboxState(
                    entries = listOf(entry("sending").copy(state = OutboxEntryState.Sending))
                )
            )
        )

        val store = OutboxStore(storage)
        store.recoverInterrupted()

        assertEquals(OutboxEntryState.Queued, store.state.value.entries.single().state)
        assertEquals(
            OutboxEntryState.Queued,
            OutboxStore(storage).state.value.entries.single().state
        )
    }

    @Test
    fun `updates and removals persist`() = runBlocking {
        val storage = MemoryStorage()
        val store = OutboxStore(storage)
        store.add(entry("one"))

        store.update("one") { current ->
            current.copy(attempts = 3, state = OutboxEntryState.Failed, lastError = "offline")
        }

        val updated = OutboxStore(storage).state.value.entries.single()
        assertEquals(3, updated.attempts)
        assertEquals(OutboxEntryState.Failed, updated.state)
        assertEquals("offline", updated.lastError)

        store.remove("one")
        assertTrue(OutboxStore(storage).state.value.entries.isEmpty())
    }

    @Test
    fun `duplicate ids collapse and a newer file is not rewritten`() {
        val duplicated = migrateOutbox(
            OutboxState(
                entries = listOf(
                    entry("same").copy(text = "old"),
                    entry("same").copy(text = "new")
                )
            )
        )

        assertEquals(listOf("new"), duplicated.entries.map { it.text })

        val future = OutboxState(
            version = CurrentOutboxVersion + 1,
            entries = listOf(entry("future"))
        )
        assertEquals(future, migrateOutbox(future))
    }

    @Test
    fun `removing an account drops only its queue`() = runBlocking {
        val store = OutboxStore(MemoryStorage())
        store.add(entry("a").copy(accountId = "first"))
        store.add(entry("b").copy(accountId = "second"))

        store.removeAccount("first")

        assertEquals(listOf("b"), store.state.value.entries.map { it.id })
        assertNull(store.find("a"))
    }

    @Test
    fun `version 1 text entries upgrade to the text kind`() {
        val storage = MemoryStorage()
        storage.writeText(
            "outbox.json",
            """
            {
              "version": 1,
              "entries": [
                {
                  "id": "legacy",
                  "localMessageId": -5,
                  "providerId": "telegram",
                  "accountId": "account",
                  "providerDialogId": "dialog",
                  "text": "old message",
                  "createdAt": 1
                }
              ]
            }
            """.trimIndent()
        )

        val entry = OutboxStore(storage).state.value.entries.single()

        assertEquals(CurrentOutboxVersion, OutboxStore(storage).state.value.version)
        assertEquals(OutboxEntryKind.Text, entry.kind)
        assertEquals("old message", entry.text)
        assertTrue(entry.attachments.isEmpty())
        assertTrue(entry.targetMessageIds.isEmpty())
    }

    @Test
    fun `file edit and delete entries persist their payload`() = runBlocking {
        val storage = MemoryStorage()
        val store = OutboxStore(storage)

        store.add(
            entry("files").copy(
                kind = OutboxEntryKind.Files,
                text = "caption",
                attachments = listOf(
                    OutboxAttachment(path = "/tmp/one.jpg", asPhoto = true),
                    OutboxAttachment(path = "/tmp/two.pdf", asPhoto = false)
                )
            )
        )
        store.add(
            entry("edit").copy(
                kind = OutboxEntryKind.Edit,
                text = "edited",
                targetMessageIds = listOf(42)
            )
        )
        store.add(
            entry("delete").copy(
                kind = OutboxEntryKind.Delete,
                targetMessageIds = listOf(42, 43)
            )
        )

        val reloaded = OutboxStore(storage).state.value.entries.associateBy { it.id }

        assertEquals("caption", reloaded.getValue("files").text)
        assertEquals(
            listOf("/tmp/one.jpg", "/tmp/two.pdf"),
            reloaded.getValue("files").attachments.map { attachment -> attachment.path }
        )
        assertEquals(listOf(42L), reloaded.getValue("edit").targetMessageIds)
        assertEquals(listOf(42L, 43L), reloaded.getValue("delete").targetMessageIds)
    }

    @Test
    fun `pending rows describe file sends without a caption`() {
        assertEquals("Photo", entry("one").copy(
            kind = OutboxEntryKind.Files,
            text = "",
            attachments = listOf(OutboxAttachment("/tmp/one.jpg", asPhoto = true))
        ).pendingText)

        assertEquals("2 files", entry("two").copy(
            kind = OutboxEntryKind.Files,
            text = "",
            attachments = listOf(
                OutboxAttachment("/tmp/one.jpg", asPhoto = true),
                OutboxAttachment("/tmp/two.pdf", asPhoto = false)
            )
        ).pendingText)

        assertEquals("hello", entry("text").pendingText)
    }

    @Test
    fun `scheduled sends keep their scheduling across a restart`() = runBlocking {
        val storage = MemoryStorage()
        val store = OutboxStore(storage)

        store.add(
            entry("dated").copy(
                scheduling = MessageScheduling.At(1_700_000_000_000L)
            )
        )
        store.add(
            entry("online").copy(
                scheduling = MessageScheduling.WhenOnline
            )
        )

        val reloaded = OutboxStore(storage).state.value.entries.associateBy { it.id }

        assertEquals(
            MessageScheduling.At(1_700_000_000_000L),
            reloaded.getValue("dated").scheduling
        )
        assertEquals(
            MessageScheduling.WhenOnline,
            reloaded.getValue("online").scheduling
        )
    }

    @Test
    fun `retry delays back off and cap`() {
        assertEquals(5_000L, outboxRetryDelayMillis(1))
        assertEquals(15_000L, outboxRetryDelayMillis(2))
        assertEquals(60_000L, outboxRetryDelayMillis(3))
        assertEquals(300_000L, outboxRetryDelayMillis(4))
        assertEquals(300_000L, outboxRetryDelayMillis(50))
    }

    @Test
    fun `entries of accounts that are gone are pruned and stay pruned`() = runBlocking {
        val storage = MemoryStorage()
        val store = OutboxStore(storage)
        store.add(entry("kept"))
        store.add(entry("orphan").copy(accountId = "removed"))

        store.pruneUnknownAccounts(setOf("account"))

        assertEquals(listOf("kept"), store.state.value.entries.map { entry -> entry.id })
        assertEquals(
            listOf("kept"),
            OutboxStore(storage).state.value.entries.map { entry -> entry.id }
        )
    }

    @Test
    fun `pruning nothing leaves the queue as it is`() {
        val entries = listOf(entry("one"), entry("two").copy(accountId = "other"))

        assertEquals(entries, entriesForKnownAccounts(entries, setOf("account", "other")))
        assertEquals(
            listOf("one"),
            entriesForKnownAccounts(entries, setOf("account")).map { entry -> entry.id }
        )
    }

    @Test
    fun `a version 2 file decodes with the pause fields at their defaults`() {
        val storage = MemoryStorage()
        storage.writeText(
            "outbox.json",
            """
            {
              "version": 2,
              "entries": [
                {
                  "id": "legacy",
                  "localMessageId": -7,
                  "providerId": "telegram",
                  "accountId": "account",
                  "providerDialogId": "dialog",
                  "kind": "Files",
                  "text": "caption",
                  "attachments": [
                    { "path": "/tmp/one.jpg", "asPhoto": true }
                  ],
                  "createdAt": 1
                }
              ]
            }
            """.trimIndent()
        )

        val state = OutboxStore(storage).state.value
        val entry = state.entries.single()

        assertEquals(CurrentOutboxVersion, state.version)
        assertFalse(entry.paused)
        assertNull(entry.progress)
        assertEquals("/tmp/one.jpg", entry.attachments.single().path)
    }

    @Test
    fun `a paused entry keeps its progress across a restart`() = runBlocking {
        val storage = MemoryStorage()
        val store = OutboxStore(storage)

        store.add(
            entry("paused").copy(
                state = OutboxEntryState.Queued,
                paused = true,
                progress = 0.5f
            )
        )

        val reloaded = OutboxStore(storage).state.value.entries.single()

        assertTrue(reloaded.paused)
        assertEquals(0.5f, reloaded.progress)
    }

    @Test
    fun `an album reports the slowest attachment`() {
        val attachments = listOf(
            OutboxAttachment(path = "/tmp/one.jpg", asPhoto = true),
            OutboxAttachment(path = "/tmp/two.jpg", asPhoto = true)
        )

        assertEquals(
            0.4f,
            outboxAttachmentProgress(
                attachments,
                mapOf("/tmp/one.jpg" to 0.4f, "/tmp/two.jpg" to 0.8f)
            )
        )

        assertEquals(
            0.6f,
            outboxAttachmentProgress(attachments, mapOf("/tmp/two.jpg" to 0.6f))
        )

        assertNull(outboxAttachmentProgress(attachments, emptyMap()))
    }

    @Test
    fun `only active file sends count towards the overall upload`() {
        val sending = entry("sending").copy(
            kind = OutboxEntryKind.Files,
            attachments = listOf(OutboxAttachment(path = "/tmp/a.jpg", asPhoto = true))
        )
        val paused = entry("paused").copy(
            kind = OutboxEntryKind.Files,
            paused = true,
            attachments = listOf(OutboxAttachment(path = "/tmp/b.jpg", asPhoto = true))
        )
        val text = entry("text")

        assertTrue(sending.isUploadActive())
        assertFalse(paused.isUploadActive())
        assertFalse(text.isUploadActive())

        assertEquals(
            0.5f,
            outboxOverallUploadProgress(
                listOf(sending, paused, text),
                mapOf("/tmp/a.jpg" to 0.5f, "/tmp/b.jpg" to 1f)
            )
        )

        assertEquals(0f, outboxOverallUploadProgress(listOf(paused, text), emptyMap()))
    }

    private fun entry(id: String): OutboxEntry {
        return OutboxEntry(
            id = id,
            localMessageId = -id.hashCode().toLong(),
            providerId = "telegram",
            accountId = "account",
            providerDialogId = "dialog",
            text = "hello",
            createdAt = 1
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
