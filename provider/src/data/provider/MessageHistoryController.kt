package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Fetches one page of history; [fromMessageId] of 0 means the newest page. */
fun interface MessagePageLoader {
    suspend fun load(fromMessageId: Long, limit: Int): Result<List<ChatMessage>>
}

/**
 * Owns the message window of a single dialog.
 *
 * The controller is the only place that mutates the window, which keeps
 * pagination, live updates and jumps from racing each other. Every page
 * request is serialized, so a scroll trigger and a jump can never fetch
 * the same page twice or move the cursor backwards.
 */
class MessageHistoryController(
    private val pageLoader: MessagePageLoader,
    private val pageSize: Int = 50,
    private val maxRequestsPerPage: Int = 8,
    private val maxJumpPages: Int = 100,
    /** Messages restored from the local cache, shown before the first page arrives. */
    initialMessages: List<ChatMessage> = emptyList()
) {
    private val _state = MutableStateFlow(
        MessageHistory(
            messages = initialMessages,
            hasMoreOlder = initialMessages.isNotEmpty(),
            isLoadingInitial = initialMessages.isEmpty()
        )
    )
    val state: StateFlow<MessageHistory> = _state.asStateFlow()

    private val pageMutex = Mutex()

    /** Loads (or refreshes) the newest page and merges it into the window. */
    suspend fun loadLatest(limit: Int = pageSize): Result<Unit> = pageMutex.withLock {
        loadLatestLocked(limit)
    }

    private suspend fun loadLatestLocked(limit: Int): Result<Unit> {
        val hadMessages = _state.value.messages.isNotEmpty()

        if (!hadMessages) {
            _state.update { it.copy(isLoadingInitial = true) }
        }

        return try {
            val page = pageLoader.load(fromMessageId = 0, limit = limit).getOrThrow()
            merge(page)

            _state.update { state ->
                state.copy(
                    isLoadingInitial = false,
                    // The newest page doubles as the probe of the total chat
                    // size, so only it may decide whether history goes deeper.
                    // A short newest page is not proof that the chat has no
                    // older history: TDLib may return sparse pages. The first
                    // older request discovers where the history really ends.
                    hasMoreOlder = if (hadMessages) {
                        state.hasMoreOlder
                    } else {
                        page.isNotEmpty()
                    }
                )
            }

            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            _state.update { it.copy(isLoadingInitial = false) }
            Result.failure(exception)
        }
    }

    /**
     * Prepends the page that precedes the oldest loaded message.
     *
     * @return whether there is still older history left after this page.
     */
    suspend fun loadOlder(limit: Int = pageSize): Result<Boolean> = pageMutex.withLock {
        val current = _state.value

        if (current.messages.isEmpty()) {
            return@withLock loadLatestLocked(limit)
                .map { _state.value.hasMoreOlder }
        }

        if (!current.hasMoreOlder) {
            return@withLock Result.success(false)
        }

        val anchor = current.messages.first().messageId
        _state.update { it.copy(isLoadingOlder = true) }

        try {
            val collected = LinkedHashMap<Long, ChatMessage>()
            var cursor = anchor
            var hasMore = true
            var requests = 0

            // TDLib is allowed to return fewer messages than requested even
            // when older messages exist. Keep advancing the cursor until the
            // page is full, the chat start is reached, or the request budget
            // is spent — otherwise a sparse page looks like the end of chat
            // and the dialog stays stuck on a couple of messages.
            while (collected.size < limit && requests < maxRequestsPerPage) {
                val remaining = (limit - collected.size).coerceAtLeast(1)
                val page = pageLoader.load(fromMessageId = cursor, limit = remaining)
                    .getOrThrow()
                requests++

                if (page.isEmpty()) {
                    hasMore = false
                    break
                }

                val older = page.filter { message -> message.messageId < cursor }
                older.forEach { message -> collected[message.messageId] = message }

                val nextCursor = page.minOf { message -> message.messageId }

                // Nothing older than the cursor means the beginning of the
                // chat; a cursor that does not move would repeat the same page.
                if (older.isEmpty() || nextCursor >= cursor) {
                    hasMore = false
                    break
                }

                cursor = nextCursor
            }

            merge(collected.values.toList())

            _state.update { it.copy(hasMoreOlder = hasMore, isLoadingOlder = false) }
            Result.success(hasMore)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            _state.update { it.copy(isLoadingOlder = false) }
            Result.failure(exception)
        }
    }

    /**
     * Pages history backwards until [messageId] is part of the window.
     *
     * This is what makes it possible to jump to a pinned (or otherwise far
     * away) message that was never loaded, instead of silently doing nothing.
     */
    suspend fun loadUntilLoaded(messageId: Long, limit: Int = pageSize): Result<Boolean> {
        val initial = _state.value

        if (initial.contains(messageId)) return Result.success(true)

        // The window is contiguous, so a target above its oldest message that
        // is not in it does not exist at all (deleted, or newer than the
        // newest page). There is no point in paging history for it.
        val initialOldest = initial.oldestMessageId
        if (initialOldest != null && messageId > initialOldest) return Result.success(false)

        var pages = 0

        while (pages < maxJumpPages) {
            val result = loadOlder(limit)
            result.exceptionOrNull()?.let { return Result.failure(it) }

            val state = _state.value

            if (state.contains(messageId)) return Result.success(true)

            // Loaded pages are contiguous, so a target that sits above the
            // oldest loaded message but is not in the window does not exist
            // (it was deleted, or it is newer than the newest page). Stop
            // instead of paging the entire history for nothing.
            val oldest = state.oldestMessageId
            if (oldest != null && messageId > oldest) return Result.success(false)

            if (!state.hasMoreOlder || state.messages.isEmpty()) break

            pages++
        }

        return Result.success(_state.value.contains(messageId))
    }

    /**
     * Seeds the window with messages restored from the local cache, so a chat
     * can render its history before the first network page arrives.
     */
    fun seed(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return

        _state.update { state ->
            if (state.messages.isNotEmpty()) {
                state
            } else {
                MessageHistory(
                    messages = messages.sortedBy { message -> message.messageId },
                    hasMoreOlder = true,
                    isLoadingInitial = false
                )
            }
        }
    }

    /** Merges messages coming from a page load or a live update. */
    fun merge(incoming: List<ChatMessage>) {
        if (incoming.isEmpty()) return

        _state.update { state ->
            val byId = LinkedHashMap<Long, ChatMessage>(state.messages.size + incoming.size)

            state.messages.forEach { message -> byId[message.messageId] = message }
            incoming.forEach { message -> byId[message.messageId] = message }

            state.copy(messages = byId.values.sortedBy { it.messageId })
        }
    }

    /** Applies a live update to every message of the window. */
    fun mutate(transform: (ChatMessage) -> ChatMessage) {
        _state.update { state ->
            state.copy(messages = state.messages.map(transform))
        }
    }

    fun remove(messageIds: Set<Long>) {
        if (messageIds.isEmpty()) return

        _state.update { state ->
            if (state.messages.none { it.messageId in messageIds }) {
                state
            } else {
                state.copy(messages = state.messages.filterNot { it.messageId in messageIds })
            }
        }
    }

    /** Replaces a temporary/live message id with its final message. */
    fun replace(oldMessageId: Long, message: ChatMessage) {
        _state.update { state ->
            val affected = state.messages.any {
                it.messageId == oldMessageId || it.messageId == message.messageId
            }

            if (!affected) {
                state
            } else {
                state.copy(
                    messages = (
                        state.messages.filterNot {
                            it.messageId == oldMessageId || it.messageId == message.messageId
                        } + message
                    ).sortedBy { it.messageId }
                )
            }
        }
    }

}
