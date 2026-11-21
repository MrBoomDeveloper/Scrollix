package com.mrboomdev.scrollix.data.storage

import com.mrboomdev.scrollix.data.cache.CacheDatabaseProvider
import com.mrboomdev.scrollix.data.cache.CachedDialogsEntity
import com.mrboomdev.scrollix.data.cache.CachedMessagesEntity
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Cache of the data that should be on screen right after a restart, before
 * TDLib has replayed its updates: the dialog list, the last messages of
 * recently opened chats, and downloaded media (stickers, custom emoji and
 * avatars).
 *
 * Dialogs and messages live in the Room database, media bytes stay on disk
 * below the app storage root.
 */
object CacheStore {
    enum class Category(val title: String) {
        Dialogs("Dialog list"),
        Messages("Messages"),
        Media("Stickers, emoji and avatars")
    }

    /** Only the newest messages are kept, older pages are fetched again. */
    private const val MaxCachedMessages = 100
    private const val MediaDirectory = "cache/media"

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val storage by lazy { createPlatformStorage() }
    private val dao get() = CacheDatabaseProvider.database.cacheDao()

    suspend fun loadDialogs(accountId: String): List<Dialog> {
        return try {
            val payload = dao.dialogs(accountId) ?: return emptyList()

            json.decodeFromString(ListSerializer(Dialog.serializer()), payload)
                .map { dialog -> dialog.copy(avatar = dialog.avatar?.copy(image = null)) }
        } catch (exception: Exception) {
            // A row written by an older build can be unreadable. Drop it so the
            // next save replaces it instead of failing on every startup.
            println("Dropping unreadable dialog cache: ${exception.message}")
            runCatching { dao.deleteDialogs(accountId) }
            emptyList()
        }
    }

    suspend fun saveDialogs(accountId: String, dialogs: List<Dialog>) {
        if (dialogs.isEmpty()) return

        runCatching {
            // A typing hint is transient, and avatar bytes are deliberately
            // not cached: TDLib file ids are not stable enough across restarts
            // to trust stored image bytes, so avatars are reloaded live.
            val cached = dialogs.map { dialog ->
                dialog.copy(
                    typingText = null,
                    avatar = dialog.avatar?.copy(image = null)
                )
            }

            dao.putDialogs(
                CachedDialogsEntity(
                    accountId = accountId,
                    payload = json.encodeToString(
                        ListSerializer(Dialog.serializer()),
                        cached
                    ),
                    updatedAt = currentTimeMillis()
                )
            )
        }.onFailure { exception ->
            println("Failed to cache dialogs: ${exception.message}")
        }
    }

    suspend fun loadMessages(
        accountId: String,
        providerDialogId: String,
        limit: Int = MaxCachedMessages
    ): List<ChatMessage> {
        return try {
            val payload = dao.messages(accountId, providerDialogId) ?: return emptyList()

            json.decodeFromString(ListSerializer(ChatMessage.serializer()), payload)
                .takeLast(limit)
                .map { message ->
                    message.copy(senderAvatar = message.senderAvatar?.copy(image = null))
                }
        } catch (exception: Exception) {
            println("Dropping unreadable message cache: ${exception.message}")
            runCatching { dao.deleteMessages(accountId, providerDialogId) }
            emptyList()
        }
    }

    suspend fun saveMessages(
        accountId: String,
        providerDialogId: String,
        messages: List<ChatMessage>
    ) {
        if (messages.isEmpty()) return

        runCatching {
            val cached = messages.takeLast(MaxCachedMessages).map { message ->
                message.copy(senderAvatar = message.senderAvatar?.copy(image = null))
            }

            dao.putMessages(
                CachedMessagesEntity(
                    accountId = accountId,
                    providerDialogId = providerDialogId,
                    payload = json.encodeToString(
                        ListSerializer(ChatMessage.serializer()),
                        cached
                    ),
                    updatedAt = currentTimeMillis()
                )
            )
        }
    }

    /**
     * Media is keyed by TDLib's remote unique id, a stable content identifier,
     * never by the runtime file id which can point at a different file after a
     * restart. Every account has its own TDLib instance, so it is scoped by
     * account as well.
     */
    fun readMedia(accountId: String, remoteUniqueId: String): ByteArray? {
        if (remoteUniqueId.isBlank()) return null

        return runCatching {
            storage.readBytes(mediaPath(accountId, remoteUniqueId))
        }.getOrNull()
    }

    fun saveMedia(accountId: String, remoteUniqueId: String, bytes: ByteArray) {
        if (remoteUniqueId.isBlank() || bytes.isEmpty()) return
        runCatching { storage.writeBytes(mediaPath(accountId, remoteUniqueId), bytes) }
    }

    suspend fun size(category: Category): Long {
        return runCatching {
            when (category) {
                Category.Dialogs -> dao.dialogsSize()
                Category.Messages -> dao.messagesSize()
                Category.Media -> storage.size(MediaDirectory)
            }
        }.getOrDefault(0L)
    }

    suspend fun clear(category: Category) {
        runCatching {
            when (category) {
                Category.Dialogs -> dao.clearDialogs()
                Category.Messages -> dao.clearMessages()
                Category.Media -> storage.delete(MediaDirectory)
            }
        }
    }

    suspend fun clearAll() {
        runCatching { dao.clearDialogs() }
        runCatching { dao.clearMessages() }
        runCatching { storage.delete(MediaDirectory) }
    }

    /**
     * Older builds persisted downloaded file bytes keyed by the numeric TDLib
     * file id. Those ids are not durable, so those bytes can belong to another
     * file after a restart. Only those numeric entries are dropped; the current
     * cache, keyed by remote unique ids, is left untouched.
     */
    fun pruneLegacyMedia() {
        runCatching {
            storage.listFiles(MediaDirectory).forEach { relativePath ->
                val fileName = relativePath.substringAfterLast('/')

                if (fileName.all(Char::isDigit)) {
                    storage.delete("$MediaDirectory/$relativePath")
                }
            }
        }
    }

    /** Drops everything cached for an account that is being removed. */
    suspend fun clearAccount(accountId: String) {
        runCatching { dao.deleteDialogs(accountId) }
        runCatching { dao.deleteMessages(accountId) }
        runCatching { storage.delete("$MediaDirectory/${safeName(accountId)}") }
    }

    private fun mediaPath(accountId: String, remoteUniqueId: String): String {
        return "$MediaDirectory/${safeName(accountId)}/${safeName(remoteUniqueId)}"
    }

    private fun safeName(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
