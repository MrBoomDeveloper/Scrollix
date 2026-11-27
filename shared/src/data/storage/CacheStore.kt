package com.mrboomdev.scrollix.data.storage

import com.mrboomdev.scrollix.data.cache.CacheDatabaseProvider
import com.mrboomdev.scrollix.data.cache.CachedDialogsEntity
import com.mrboomdev.scrollix.data.cache.CachedMessagesEntity
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageRichBlock
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.features.home.key

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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

    // Scanning the media tree after every sticker would be wasteful, so the
    // budget is checked after a few megabytes or a minute of caching.
    private const val MediaLimitCheckBytes = 4L * 1024 * 1024
    private const val MediaLimitCheckIntervalMillis = 60_000L
    private var mediaBytesSinceLimitCheck = 0L
    private var lastLimitCheckMillis = 0L

    /**
     * Media referenced by a chat the user marked as "Keep media". These paths
     * still count toward the budget but are never selected for eviction.
     */
    private val protectedMedia = MutableStateFlow<Set<String>>(emptySet())

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val storage by lazy { createPlatformStorage() }
    private val dao get() = CacheDatabaseProvider.database.cacheDao()

    suspend fun loadDialogs(accountId: String): List<Dialog> {
        return try {
            val payload = dao.dialogs(accountId) ?: return emptyList()

            json.decodeFromString(ListSerializer(Dialog.serializer()), payload)
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
            // A typing hint is transient. The avatar bytes are stored with the
            // row so the whole list (avatar, title, preview, time, unread
            // count) can be drawn on the next launch before any provider has
            // connected.
            val cached = dialogs.map { dialog ->
                dialog.copy(typingText = null)
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
                .filter { message -> message.sendState == MessageSendState.Sent }
                .takeLast(limit)
                .map { message -> migrateCachedMessage(message) }
        } catch (exception: Exception) {
            println("Dropping unreadable message cache: ${exception.message}")
            runCatching { dao.deleteMessages(accountId, providerDialogId) }
            emptyList()
        }
    }

    /** Drops the cached window of a chat that was deleted or left. */
    suspend fun deleteMessages(accountId: String, providerDialogId: String) {
        runCatching { dao.deleteMessages(accountId, providerDialogId) }
    }

    suspend fun saveMessages(
        accountId: String,
        providerDialogId: String,
        messages: List<ChatMessage>
    ) {
        if (messages.isEmpty()) return
        if (messages.none { message -> message.sendState == MessageSendState.Sent }) return

        runCatching {
            // A pending or failed message has a temporary id that TDLib will
            // not resolve after a restart, so caching it would restore a
            // permanent "Sending" ghost.
            val cached = messages
                .filter { message -> message.sendState == MessageSendState.Sent }
                .takeLast(MaxCachedMessages)
                .map { message ->
                    message.copy(
                        senderAvatar = message.senderAvatar?.copy(image = null),
                        forwardedFromAvatar = message.forwardedFromAvatar?.copy(image = null)
                    )
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

            if (keepMediaKey(accountId, providerDialogId) in
                AppSettingsStore.state.value.keepMediaDialogIds
            ) {
                val paths = mediaPaths(accountId, cached)
                protectedMedia.update { current -> current + paths }
            }
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

        runCatching {
            storage.writeBytes(mediaPath(accountId, remoteUniqueId), bytes)
        }.onSuccess {
            // Every cached media file was downloaded by this device, so it is
            // the one place a received-traffic counter can see the bytes.
            DataUsageStore.record(accountId, receivedBytes = bytes.size.toLong())

            mediaBytesSinceLimitCheck += bytes.size
            val now = currentTimeMillis()

            if (
                mediaBytesSinceLimitCheck >= MediaLimitCheckBytes ||
                now - lastLimitCheckMillis >= MediaLimitCheckIntervalMillis
            ) {
                mediaBytesSinceLimitCheck = 0L
                lastLimitCheckMillis = now
                runCatching {
                    enforceMediaLimit(AppSettingsStore.state.value.mediaCacheLimitBytes)
                }
            }
        }
    }

    /** Media files currently cached, with the metadata eviction orders by. */
    fun mediaEntries(): List<MediaCacheEntry> {
        return runCatching {
            storage.listEntries(MediaDirectory).map { entry ->
                MediaCacheEntry(
                    relativePath = entry.relativePath,
                    sizeBytes = entry.sizeBytes,
                    lastModifiedAt = entry.lastModifiedAt
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Cached data grouped by chat: the stored message window plus the media
     * files those messages reference. Only chats with a cached window are
     * listed, so the storage center never invents a chat that was never opened.
     */
    suspend fun chatUsage(): List<ChatCacheUsage> {
        return runCatching {
            val sizes = dao.messageSizes()
            val mediaEntries = mediaEntries().associateBy { entry -> entry.relativePath }

            sizes.map { row ->
                val messages = loadMessages(row.accountId, row.providerDialogId)
                val account = safeName(row.accountId)
                val mediaBytes = mediaUniqueIds(messages).sumOf { uniqueId ->
                    mediaEntries["$account/${hexName(uniqueId)}"]?.sizeBytes ?: 0L
                }

                ChatCacheUsage(
                    accountId = row.accountId,
                    providerDialogId = row.providerDialogId,
                    messageBytes = row.sizeBytes,
                    mediaBytes = mediaBytes
                )
            }.sortedByDescending { usage -> usage.totalBytes }
        }.getOrDefault(emptyList())
    }

    /**
     * Removes the cached window of one chat and the media only that window
     * references. The chat itself, its server history and queued sends stay.
     */
    suspend fun clearChatCache(accountId: String, providerDialogId: String) {
        runCatching {
            val messages = loadMessages(accountId, providerDialogId)
            val account = safeName(accountId)
            val paths = mediaUniqueIds(messages)
                .map { uniqueId -> "$account/${hexName(uniqueId)}" }
                .toSet()

            paths.forEach { relativePath ->
                runCatching { storage.delete("$MediaDirectory/$relativePath") }
            }

            protectedMedia.update { current -> current - paths }
            dao.deleteMessages(accountId, providerDialogId)
        }
    }

    /**
     * Deletes the oldest cached media until the directory fits [limitBytes].
     * Returns the number of bytes freed. A non-positive limit means unlimited.
     */
    fun enforceMediaLimit(limitBytes: Long): Long {
        if (limitBytes <= 0L) return 0L

        val evicted = selectMediaForEviction(
            entries = mediaEntries(),
            limitBytes = limitBytes,
            protectedPaths = protectedMedia.value
        )
        var freedBytes = 0L

        evicted.forEach { entry ->
            val deleted = runCatching {
                storage.delete("$MediaDirectory/${entry.relativePath}")
            }.isSuccess

            if (deleted) freedBytes += entry.sizeBytes
        }

        return freedBytes
    }

    /**
     * Recomputes which cached media belongs to a kept chat from the last
     * cached window of every kept chat. Call it after startup and after the
     * kept set changes; new messages of a kept chat are protected as they are
     * cached, so this does not need to run on every save.
     */
    suspend fun refreshMediaProtection() {
        val paths = mutableSetOf<String>()

        AppSettingsStore.state.value.keepMediaDialogIds.forEach { key ->
            val separator = key.indexOf(':')
            if (separator <= 0 || separator == key.lastIndex) return@forEach

            val accountId = key.substring(0, separator)
            val dialogId = key.substring(separator + 1)
            paths += mediaPaths(accountId, loadMessages(accountId, dialogId))
        }

        protectedMedia.value = paths
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

        val accountPrefix = "${safeName(accountId)}/"
        protectedMedia.update { paths ->
            paths.filterNot { path -> path.startsWith(accountPrefix) }.toSet()
        }
    }

    private fun mediaPath(accountId: String, remoteUniqueId: String): String {
        return "$MediaDirectory/${safeName(accountId)}/${hexName(remoteUniqueId)}"
    }

    /** Relative paths (below the media directory) of a chat's cached media. */
    private fun mediaPaths(accountId: String, messages: List<ChatMessage>): Set<String> {
        val account = safeName(accountId)

        return mediaUniqueIds(messages).mapTo(mutableSetOf()) { uniqueId ->
            "$account/${hexName(uniqueId)}"
        }
    }

    /**
     * Remote unique ids are base64 and may contain '/', '+' and '='. Mapping
     * those characters to '_' makes distinct ids collide, so media names are
     * a lossless hex encoding of the whole id instead.
     */
    private fun hexName(value: String): String {
        return value.encodeToByteArray().joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    private fun safeName(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}

/** Cached bytes of one chat: its message window and referenced media. */
data class ChatCacheUsage(
    val accountId: String,
    val providerDialogId: String,
    val messageBytes: Long,
    val mediaBytes: Long
) {
    val totalBytes: Long get() = messageBytes + mediaBytes
}

/** One cached media file considered by the budget manager. */
data class MediaCacheEntry(
    val relativePath: String,
    val sizeBytes: Long,
    val lastModifiedAt: Long
)

/**
 * Oldest-first eviction until [limitBytes] fits. Pure so the order is covered
 * by tests; equal timestamps fall back to the path for a stable result.
 */
internal fun selectMediaForEviction(
    entries: List<MediaCacheEntry>,
    limitBytes: Long,
    protectedPaths: Set<String> = emptySet()
): List<MediaCacheEntry> {
    if (limitBytes <= 0L) return emptyList()

    var totalBytes = entries.sumOf { entry -> entry.sizeBytes }
    if (totalBytes <= limitBytes) return emptyList()

    val evicted = mutableListOf<MediaCacheEntry>()
    val oldestFirst = entries.sortedWith(
        compareBy({ entry -> entry.lastModifiedAt }, { entry -> entry.relativePath })
    )

    oldestFirst.forEach { entry ->
        if (totalBytes <= limitBytes) return@forEach
        if (entry.relativePath in protectedPaths) return@forEach

        evicted += entry
        totalBytes -= entry.sizeBytes
    }

    return evicted
}

/**
 * Normalizes a message read from the cache. Older builds stored unsupported
 * content as text and kept avatar bitmaps; neither may leak Copy/Edit actions
 * or stale bytes into the restored history.
 */
internal fun migrateCachedMessage(message: ChatMessage): ChatMessage {
    val isLegacyUnsupported = message.text.startsWith("This message is not supported")

    return message.copy(
        senderAvatar = message.senderAvatar?.copy(image = null),
        forwardedFromAvatar = message.forwardedFromAvatar?.copy(image = null),
        isService = message.isService || isLegacyUnsupported
    )
}

/**
 * Durable media ids referenced by messages, including albums, replies, poll
 * options and media embedded in rich text. Runtime file ids are deliberately
 * ignored: only these ids can be trusted after a restart.
 */
internal fun mediaUniqueIds(messages: List<ChatMessage>): Set<String> {
    val ids = mutableSetOf<String>()

    fun add(media: MessageMedia?) {
        media ?: return

        listOf(media.previewUniqueId, media.fileUniqueId, media.stickerUniqueId)
            .filterNotNull()
            .filter { uniqueId -> uniqueId.isNotBlank() }
            .forEach { uniqueId -> ids += uniqueId }

        media.videoQualities.forEach { quality ->
            quality.fileUniqueId
                ?.takeIf { uniqueId -> uniqueId.isNotBlank() }
                ?.let { uniqueId -> ids += uniqueId }
        }
    }

    messages.forEach { message ->
        message.allMedia.forEach { media -> add(media) }
        message.replyTo?.media?.let { media -> add(media) }
        message.poll?.options?.forEach { option -> add(option.media) }

        message.richBlocks.forEach { block ->
            if (block is MessageRichBlock.Media) {
                add(block.media)
            }
        }
    }

    return ids
}
