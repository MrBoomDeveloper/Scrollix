package com.mrboomdev.scrollix.data.storage

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Disk backed cache of the data that should be on screen right after a
 * restart, before TDLib has replayed its updates: the dialog list, the last
 * messages of recently opened chats, and downloaded media (stickers, custom
 * emoji and avatars). Everything lives under the app storage root, next to
 * the account databases.
 */
object CacheStore {
    enum class Category(val title: String, internal val directory: String) {
        Dialogs(title = "Dialog list", directory = "cache/dialogs"),
        Messages(title = "Messages", directory = "cache/messages"),
        Media(title = "Stickers, emoji and avatars", directory = "cache/media")
    }

    /** Only the newest messages are kept, older pages are fetched again. */
    private const val MaxCachedMessages = 100

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val storage by lazy { createPlatformStorage() }

    fun loadDialogs(accountId: String): List<Dialog> {
        return runCatching {
            val text = storage.readText(dialogsPath(accountId)) ?: return emptyList()
            json.decodeFromString(ListSerializer(Dialog.serializer()), text)
        }.getOrDefault(emptyList())
    }

    fun saveDialogs(accountId: String, dialogs: List<Dialog>) {
        runCatching {
            // A typing hint is transient, it must not come back after a restart.
            val cached = dialogs.map { dialog -> dialog.copy(typingText = null) }

            storage.writeText(
                relativePath = dialogsPath(accountId),
                text = json.encodeToString(ListSerializer(Dialog.serializer()), cached)
            )
        }
    }

    fun loadMessages(
        accountId: String,
        providerDialogId: String,
        limit: Int = MaxCachedMessages
    ): List<ChatMessage> {
        return runCatching {
            val text = storage.readText(messagesPath(accountId, providerDialogId))
                ?: return emptyList()

            json.decodeFromString(ListSerializer(ChatMessage.serializer()), text)
                .takeLast(limit)
        }.getOrDefault(emptyList())
    }

    fun saveMessages(
        accountId: String,
        providerDialogId: String,
        messages: List<ChatMessage>
    ) {
        if (messages.isEmpty()) return

        runCatching {
            storage.writeText(
                relativePath = messagesPath(accountId, providerDialogId),
                text = json.encodeToString(
                    ListSerializer(ChatMessage.serializer()),
                    messages.takeLast(MaxCachedMessages)
                )
            )
        }
    }

    /**
     * TDLib file ids are only unique per account, and every account has its
     * own TDLib instance, so cached media is always scoped by account.
     */
    fun readMedia(accountId: String, fileId: Int): ByteArray? {
        if (fileId == 0) return null
        return runCatching { storage.readBytes(mediaPath(accountId, fileId)) }.getOrNull()
    }

    fun saveMedia(accountId: String, fileId: Int, bytes: ByteArray) {
        if (fileId == 0 || bytes.isEmpty()) return
        runCatching { storage.writeBytes(mediaPath(accountId, fileId), bytes) }
    }

    fun size(category: Category): Long {
        return runCatching { storage.size(category.directory) }.getOrDefault(0L)
    }

    fun clear(category: Category) {
        runCatching { storage.delete(category.directory) }
    }

    fun clearAll() {
        Category.entries.forEach(::clear)
    }

    /** Drops everything cached for an account that is being removed. */
    fun clearAccount(accountId: String) {
        runCatching { storage.delete(dialogsPath(accountId)) }
        runCatching {
            storage.delete("${Category.Messages.directory}/${safeName(accountId)}")
        }
        runCatching {
            storage.delete("${Category.Media.directory}/${safeName(accountId)}")
        }
    }

    private fun dialogsPath(accountId: String): String {
        return "${Category.Dialogs.directory}/${safeName(accountId)}.json"
    }

    private fun messagesPath(accountId: String, providerDialogId: String): String {
        return "${Category.Messages.directory}/${safeName(accountId)}/" +
            "${safeName(providerDialogId)}.json"
    }

    private fun mediaPath(accountId: String, fileId: Int): String {
        return "${Category.Media.directory}/${safeName(accountId)}/$fileId"
    }

    private fun safeName(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
