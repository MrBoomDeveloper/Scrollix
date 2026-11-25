package com.mrboomdev.scrollix.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.mrboomdev.scrollix.data.max.network.MaxConnectionClosedException
import com.mrboomdev.scrollix.data.max.network.maxFailure
import com.mrboomdev.scrollix.data.max.network.maxNotConnected
import com.mrboomdev.scrollix.data.max.network.MaxRpc
import com.mrboomdev.scrollix.data.max.network.array
import com.mrboomdev.scrollix.data.max.network.long
import com.mrboomdev.scrollix.data.max.network.obj
import com.mrboomdev.scrollix.data.max.network.objects
import com.mrboomdev.scrollix.data.max.network.string
import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.SessionException
import com.mrboomdev.scrollix.data.model.asSessionException
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageReactionInfo
import com.mrboomdev.scrollix.data.provider.MessageHistoryController
import com.mrboomdev.scrollix.data.provider.MessagePage
import com.mrboomdev.scrollix.data.provider.MessagePageLoader
import com.mrboomdev.scrollix.data.storage.CacheStore

internal fun MaxSession.historyFor(providerDialogId: String): MessageHistoryController? {
    val chatId = providerDialogId.toLongOrNull() ?: return null

    histories.value[providerDialogId]?.let { return it }

    val created = MessageHistoryController(
        pageLoader = object : MessagePageLoader {
            override suspend fun load(
                fromMessageId: Long,
                limit: Int
            ): Result<MessagePage> {
                return try {
                    Result.success(MessagePage(fetchHistory(chatId, fromMessageId, limit)))
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Result.failure(exception.asSessionException())
                }
            }
        }
    )

    histories.update { existing ->
        if (existing.containsKey(providerDialogId)) {
            existing
        } else {
            existing + (providerDialogId to created)
        }
    }

    val winner = histories.value[providerDialogId] ?: return null
    if (winner !== created) return winner

    // The cached window renders immediately while GET_HISTORY is in
    // flight, so opening a dialog does not wait for the network.
    scope.launch {
        val cached = CacheStore.loadMessages(accountId, providerDialogId)

        if (cached.isNotEmpty() && !closed) {
            created.seed(cached)
        }
    }

    return winner
}

/** Persists loaded windows for the next launch. */
internal fun MaxSession.startHistorySaver() {
    if (historySaveJob != null) return

    historySaveJob = scope.launch {
        val saved = mutableMapOf<String, List<ChatMessage>>()

        while (!closed) {
            delay(2_000)

            histories.value.forEach { (dialogId, controller) ->
                val state = controller.state.value
                val messages = state.messages

                // A jump block that is missing newer history must not
                // replace the newest-page cache.
                if (
                    messages.isNotEmpty() &&
                    !state.hasMoreNewer &&
                    !state.isLoadingInitial &&
                    saved[dialogId] !== messages
                ) {
                    CacheStore.saveMessages(accountId, dialogId, messages)
                    saved[dialogId] = messages
                }
            }
        }
    }
}

/**
 * Chat requests can arrive while the socket is being re-established (the
 * chat list stays usable). Reconnect on demand instead of failing with
 * "MAX is not connected".
 */
internal suspend fun MaxSession.requireRpc(): MaxRpc {
    rpc?.takeIf(MaxRpc::isAlive)?.let { return it }

    val currentToken = token
        ?: throw SessionException(SessionErrorCode.Network, "MAX is not connected")

    return connectMutex.withLock {
        rpc?.takeIf(MaxRpc::isAlive) ?: run {
            rpc?.let { stale ->
                rpc = null
                runCatching { stale.close() }
            }

            connect(currentToken)
            rpc ?: throw SessionException(SessionErrorCode.Network, "MAX is not connected")
        }
    }
}

/**
 * Drops a transport that already failed, so the next attempt dials a fresh
 * socket instead of writing into the dead one again.
 */
internal suspend fun MaxSession.discardRpc(dead: MaxRpc) {
    connectMutex.withLock {
        if (rpc === dead) rpc = null
    }

    runCatching { dead.close() }
}

/**
 * Runs a request on a live socket and resends it after reconnecting when
 * MAX closed the connection underneath us. MAX drops idle sockets without
 * a close frame, so the loss only shows up on the next write ("Broken
 * pipe"); resending turns that into a working send. Repeating a write is
 * safe because MSG_SEND carries a `cid` MAX deduplicates.
 */
internal suspend fun <T> MaxSession.onLiveConnection(block: suspend (MaxRpc) -> T): T {
    if (closed) throw MaxConnectionClosedException("session was closed")

    var lastFailure: MaxConnectionClosedException? = null

    repeat(3) { attempt ->
        if (attempt > 0) delay(300L * attempt)

        val active = try {
            requireRpc()
        } catch (exception: MaxConnectionClosedException) {
            lastFailure = exception
            return@repeat
        }

        try {
            return block(active)
        } catch (exception: TimeoutCancellationException) {
            // The socket never answered. Drop it so the next request starts
            // on a fresh connection instead of timing out again.
            discardRpc(active)
            throw exception
        } catch (exception: MaxConnectionClosedException) {
            lastFailure = exception
            discardRpc(active)
        }
    }

    throw lastFailure ?: MaxConnectionClosedException("connection is not available")
}

/** Requests that must survive a socket MAX closed while nobody wrote. */
internal suspend fun MaxSession.requestOnLiveConnection(
    opcode: Int,
    payload: JsonObject
): JsonElement? = onLiveConnection { active -> active.request(opcode, payload) }

internal suspend fun MaxSession.fetchHistory(
    chatId: Long,
    fromMessageId: Long,
    limit: Int
): List<ChatMessage> {
    // Once the beginning of a chat has been seen, a later scroll to the
    // top must not probe the server again: MAX answers those requests
    // with the same oldest page, which keeps the list "loading" forever.
    if (fromMessageId != 0L && chatId in historyStarts) return emptyList()

    val from = if (fromMessageId == 0L) {
        System.currentTimeMillis()
    } else {
        // A failed time lookup must not fall back to "now": that returns
        // the newest page for an older anchor, every message gets filtered
        // out, and the chat looks finished. Returning no page is honest.
        messageTimes[fromMessageId] ?: loadMessageTime(chatId, fromMessageId)
            ?: return emptyList()
    }

    val response = requestOnLiveConnection(
        opcode = 49,
        payload = buildJsonObject {
            put("chatId", chatId)
            put("from", from)
            put("backward", limit.coerceIn(1, 100))
            put("forward", 0)
            put("backwardTime", 0)
            put("forwardTime", 0)
            put("getChat", true)
            put("getMessages", true)
            put("interactive", false)
        }
    )

    response?.obj("chat")?.let(::mergeChat)

    val messages = response?.array("messages").objects()
        .map { parseMessage(chatId, it) }
        // An id of 0 is a parse failure, not a message. It would sort
        // first and become the paging anchor, and fromMessageId = 0 is
        // the "newest page" sentinel, so paging would loop forever.
        .filter { message -> message.id != 0L }

    // MAX returns the anchor itself in an older page. When the page is
    // short and holds nothing before the anchor, this is the chat start.
    if (
        fromMessageId != 0L &&
        messages.size < limit &&
        messages.none { message -> message.id < fromMessageId }
    ) {
        historyStarts += chatId
        return emptyList()
    }

    messages.forEach { message -> messageTimes[message.id] = message.time }

    // GET_HISTORY may omit reactionInfo. The aggregate counters are then
    // fetched for the whole page in one GET_REACTIONS_BULK request.
    val reactions = fetchBulkReactions(chatId, messages)

    return messages
        .map { message ->
            toChatMessage(
                chatId = chatId,
                message = reactions[message.id]?.let { loaded ->
                    message.copy(reactions = loaded)
                } ?: message
            )
        }
        .sortedBy { it.messageId }
}

/** Aggregate reactions for a page of messages, keyed by message id. */
internal suspend fun MaxSession.fetchBulkReactions(
    chatId: Long,
    messages: List<MaxMessage>
): Map<Long, List<MessageReactionInfo>> {
    if (messages.isEmpty()) return emptyMap()

    val response = runCatching {
        requestOnLiveConnection(
            opcode = 180,
            payload = buildJsonObject {
                put("chatId", chatId)
                put(
                    "messageIds",
                    buildJsonArray {
                        messages.forEach { message ->
                            add(JsonPrimitive(message.id.toString()))
                        }
                    }
                )
            }
        )
    }.getOrNull() ?: return emptyMap()

    val payload = response.obj("messagesReactions")
        ?: response.obj("reactions")
        ?: return emptyMap()
    val result = mutableMapOf<Long, List<MessageReactionInfo>>()

    payload.forEach { (key, value) ->
        val messageId = key.toLongOrNull() ?: return@forEach
        val reactions = mapReactions(value as? JsonObject)

        if (reactions.isNotEmpty()) {
            result[messageId] = reactions
        }
    }

    return result
}

internal suspend fun MaxSession.loadMessageTime(chatId: Long, messageId: Long): Long? {
    val active = rpc ?: return null

    val response = active.request(
        opcode = 71,
        payload = buildJsonObject {
            put("chatId", chatId)
            put(
                "messageIds",
                buildJsonArray { add(JsonPrimitive(messageId)) }
            )
        }
    )

    return response?.array("messages").objects().firstOrNull()?.long("time")
}

/**
 * Exchanges a video/GIF id and token for a temporary CDN URL. MAX only
 * returns MP4 links here, which is also what the WebM frame decoder falls
 * back to on Android for the autoplaying GIF previews.
 */
internal suspend fun MaxSession.resolveVideoUrl(fileId: Int, ref: MaxVideoRef): String? {
    return try {
        val response = requestOnLiveConnection(
            opcode = 83,
            payload = buildJsonObject {
                put("videoId", ref.videoId)
                put("chatId", ref.chatId)
                put("messageId", ref.messageId.toString())
                ref.token?.takeIf { it.isNotBlank() }?.let { token ->
                    put("token", token)
                }
            }
        )

        listOf("MP4_720", "MP4_480", "MP4_360", "MP4_240", "MP4_144", "EXTERNAL")
            .firstNotNullOfOrNull { key ->
                response.string(key)?.takeIf { it.startsWith("http") }
            }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        null
    }
}

// ------------------------------------------------------------ messages --

internal suspend fun MaxSession.requestChatOperation(chatId: Long, operation: String): Result<Unit> {
    val active = rpc ?: return Result.failure(maxNotConnected())
    val userId = meId.takeIf { it != 0L }
        ?: return Result.failure(maxFailure("MAX user is unknown", SessionErrorCode.NotFound))

    return try {
        active.request(
            opcode = 77,
            payload = buildJsonObject {
                put("operation", operation)
                put("chatId", chatId)
                put("userIds", buildJsonArray { add(JsonPrimitive(userId)) })
            }
        )

        Result.success(Unit)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception.asSessionException())
    }
}

// ------------------------------------------------------- unsupported --

internal suspend fun MaxSession.fetchPoll(chatId: Long, messageId: Long): MaxPoll? {
    val active = rpc ?: return null

    val response = runCatching {
        active.request(
            opcode = 71,
            payload = buildJsonObject {
                put("chatId", chatId)
                put("messageIds", buildJsonArray { add(JsonPrimitive(messageId)) })
            }
        )
    }.getOrNull() ?: return null

    val objectValue = response.array("messages").objects()
        .firstOrNull { it.long("id") == messageId }
        ?: return null

    return parseMessage(chatId, objectValue).attaches
        .firstNotNullOfOrNull { it.poll }
}

internal suspend fun MaxSession.refreshPoll(chatId: Long, messageId: Long) {
    val poll = fetchPoll(chatId, messageId) ?: return

    histories.value[chatId.toString()]?.mutate { message ->
        if (message.messageId == messageId) {
            message.copy(poll = poll.toPollInfo())
        } else {
            message
        }
    }
}
