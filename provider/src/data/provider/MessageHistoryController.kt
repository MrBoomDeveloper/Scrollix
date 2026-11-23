package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageSendState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One page of provider history.
 *
 * TDLib's `getChatHistory(fromMessageId, offset = 0, limit)` returns the
 * message identified by [fromMessageId] **inclusively**, followed by older
 * messages. A page therefore contains `limit` messages only when the anchor
 * itself is counted; callers that want `n` new messages must ask for `n + 1`.
 */
data class MessagePage(
    val messages: List<ChatMessage>,
    /** `Messages.totalCount` when the provider exposes it, otherwise null. */
    val totalCount: Int? = null
)

/** Fetches one page of history; [fromMessageId] of 0 means the newest page. */
fun interface MessagePageLoader {
    suspend fun load(fromMessageId: Long, limit: Int): Result<MessagePage>

    /**
     * Loads a page with an optional TDLib offset. A negative offset returns
     * messages newer than the anchor, which is what fills an undiscovered
     * gap. Loaders that cannot express it keep the default behavior.
     */
    suspend fun load(
        fromMessageId: Long,
        limit: Int,
        offset: Int
    ): Result<MessagePage> = load(fromMessageId, limit)
}

/**
 * Owns the message window of a single dialog.
 *
 * The window is a contiguous range `[oldest, newest]` per block. Pagination
 * never walks the whole chat: [loadAround] replaces the window with a block
 * around an arbitrary message, and [loadLatest] resets it to the newest page.
 * Every page request is serialized, so a scroll trigger and a jump can never
 * fetch the same page twice or move the cursor backwards.
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
            messages = initialMessages.sortedBy { message -> message.messageId },
            hasMoreOlder = initialMessages.isNotEmpty(),
            isLoadingInitial = initialMessages.isEmpty()
        )
    )
    val state: StateFlow<MessageHistory> = _state.asStateFlow()

    private val pageMutex = Mutex()

    /** True while the window still holds the cache preview rather than a network block. */
    private var isSeededWindow = initialMessages.isNotEmpty()

    /**
     * Ids deleted while the dialog was open. A page that was already in
     * flight must not resurrect them.
     */
    private val deletedIds = LinkedHashSet<Long>()

    /**
     * Temporary id -> final id. A page fetched between `sendMessage` and
     * `UpdateMessageSendSucceeded` can otherwise show the same message twice.
     */
    private val aliases = LinkedHashMap<Long, Long>()

    /** Loads (or refreshes) the newest page and merges it into the window. */
    suspend fun loadLatest(limit: Int = pageSize): Result<Unit> = pageMutex.withLock {
        loadLatestLocked(limit)
    }

    private suspend fun loadLatestLocked(limit: Int): Result<Unit> {
        val current = _state.value
        val hadMessages = current.messages.isNotEmpty()
        // An undiscovered gap means the window is a far-away block that the
        // newest page does not overlap: refreshing to the end has to replace
        // it, otherwise the missing middle would stay invisible.
        val replaceWindow = !hadMessages ||
            current.hasMoreNewer ||
            current.hasUnloadedGap

        if (!hadMessages) {
            _state.update { it.copy(isLoadingInitial = true) }
        }

        return try {
            val page = pageLoader.load(fromMessageId = 0, limit = limit).getOrThrow()
            val fetched = page.messages
                .filterNot { message -> message.messageId in deletedIds }
                .filter { message -> message.sendState != MessageSendState.Failed }

            // A refresh can return a page that is entirely newer than the
            // loaded window (messages arrived while the chat was closed).
            // Merging the two blocks would leave an invisible hole that older
            // paging can never fill, which looked like missing messages when
            // the dialog was reopened. Message ids are not contiguous, so
            // "adjacent" cannot be inferred from id arithmetic: only an
            // overlapping page may be merged.
            val currentNewest = current.newestMessageId
            val currentIds = current.messages.mapTo(HashSet()) { it.messageId }
            val pageIsDetached = hadMessages &&
                fetched.isNotEmpty() &&
                fetched.none { message -> message.messageId in currentIds } &&
                currentNewest != null &&
                fetched.maxOf { message -> message.messageId } > currentNewest

            mergeMessages(fetched, replace = replaceWindow || pageIsDetached)
            isSeededWindow = false

            _state.update { state ->
                state.copy(
                    isLoadingInitial = false,
                    isLoadingOlder = false,
                    hasMoreNewer = false,
                    totalCount = page.totalCount ?: state.totalCount,
                    newerMessages = emptyList(),
                    hasUnloadedGap = false,
                    // A refresh must not lower a flag that an older-page
                    // request already answered; the newest page is only a
                    // probe when the window was empty.
                    hasMoreOlder = when {
                        pageIsDetached || replaceWindow -> fetched.isNotEmpty()
                        else -> state.hasMoreOlder
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
            var requests = 0
            var reachedStart = false

            // TDLib may return fewer messages than requested even when older
            // messages exist. Keep advancing the cursor until the page is
            // full, the chat start is reached, or the request budget is spent.
            while (collected.size < limit && requests < maxRequestsPerPage) {
                val need = limit - collected.size
                // The anchor comes back in the page, so ask for one extra
                // message to fill `need` new entries. Asking for `need` alone
                // wastes the slot on the anchor and eventually requests a
                // one-message page that contains only the anchor, which used
                // to be mistaken for the beginning of the chat.
                val requestLimit = (need + 1).coerceAtMost(100)
                val page = pageLoader.load(fromMessageId = cursor, limit = requestLimit)
                    .getOrThrow()
                requests++

                if (page.messages.isEmpty()) {
                    reachedStart = true
                    break
                }

                val fresh = page.messages.filter { message ->
                    message.messageId < cursor && message.messageId !in deletedIds
                }

                if (fresh.isEmpty()) {
                    // The response cannot move the cursor. Treat it as the
                    // start only when the provider did not tell us that more
                    // messages exist; otherwise keep the flag so a later
                    // scroll can retry instead of ending the chat forever.
                    val knownMore = page.totalCount
                        ?.let { total -> total > _state.value.messages.size }
                        ?: false
                    reachedStart = !knownMore
                    break
                }

                fresh.forEach { message -> collected[message.messageId] = message }

                // The first message id marks the beginning of the chat. This
                // saves the extra one-message probe that would otherwise be
                // needed to discover it.
                if (fresh.any { message -> message.messageId <= 1 }) {
                    reachedStart = true
                    break
                }

                val nextCursor = fresh.minOf { message -> message.messageId }
                if (nextCursor >= cursor) {
                    reachedStart = true
                    break
                }

                cursor = nextCursor

                page.totalCount?.let { total ->
                    _state.update { state -> state.copy(totalCount = total) }
                }
            }

            mergeMessages(collected.values.toList(), replace = false)

            val hasMore = !reachedStart
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
     * Replaces the window with a block around [messageId], so a pinned or
     * linked message can be opened without paging the whole chat.
     *
     * The block contains the target and older messages. Newer history is not
     * fetched; [MessageHistory.hasMoreNewer] tells the UI that "jump to
     * latest" must refresh before it can land on the newest message.
     */
    suspend fun loadAround(messageId: Long, radius: Int = pageSize): Result<Boolean> =
        pageMutex.withLock {
            if (radius <= 0) {
                return@withLock Result.failure(
                    IllegalArgumentException("Radius must be positive")
                )
            }

            val requestLimit = (radius + 1).coerceAtMost(100)

            return@withLock try {
                val page = pageLoader.load(fromMessageId = messageId, limit = requestLimit)
                    .getOrThrow()
                val block = page.messages
                    .filterNot { message -> message.messageId in deletedIds }
                    .filter { message -> message.sendState != MessageSendState.Failed }
                    .sortedBy { message -> message.messageId }

                if (block.isEmpty()) {
                    Result.success(false)
                } else {
                    _state.value = MessageHistory(
                        messages = block,
                        hasMoreOlder = block.first().messageId > 1,
                        isLoadingInitial = false,
                        isLoadingOlder = false,
                        hasMoreNewer = true,
                        totalCount = page.totalCount
                    )
                    isSeededWindow = false
                    Result.success(block.any { message -> message.messageId == messageId })
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _state.update { it.copy(isLoadingOlder = false) }
                Result.failure(exception)
            }
        }

    /**
     * Jumps to the true beginning of the chat without paging the whole
     * history. The oldest message id is found with a binary search over
     * `getChatHistory` anchors, the first page is fetched directly, and the
     * previously loaded window is kept as the far-end preview with an
     * undiscovered gap in between.
     */
    suspend fun jumpToBeginning(limit: Int = pageSize): Result<Boolean> =
        pageMutex.withLock {
            val current = _state.value
            val upper = current.newestMessageId ?: return@withLock Result.success(false)

            return@withLock try {
                val firstId = findFirstMessageId(upper)
                val pageLimit = limit.coerceIn(2, 100)
                val page = pageLoader.load(
                    fromMessageId = firstId,
                    limit = pageLimit,
                    // TDLib has no "oldest page" call; a negative offset asks
                    // for the first message plus newer messages around it.
                    offset = -(pageLimit - 1)
                ).getOrThrow()

                val head = page.messages
                    .filterNot { message -> message.messageId in deletedIds }
                    .filter { message -> message.sendState != MessageSendState.Failed }
                    .sortedBy { message -> message.messageId }

                if (head.isEmpty()) {
                    return@withLock Result.success(false)
                }

                val headNewest = head.last().messageId
                val tail = current.messages
                    .filter { message -> message.messageId > headNewest }
                    .takeLast(pageSize)
                val hasGap = tail.isNotEmpty()

                _state.value = MessageHistory(
                    messages = head,
                    hasMoreOlder = false,
                    isLoadingInitial = false,
                    isLoadingOlder = false,
                    hasMoreNewer = true,
                    totalCount = page.totalCount ?: current.totalCount,
                    newerMessages = if (hasGap) tail else emptyList(),
                    hasUnloadedGap = hasGap
                )
                isSeededWindow = false
                Result.success(true)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _state.update { state -> state.copy(isLoadingOlder = false) }
                Result.failure(exception)
            }
        }

    /**
     * Loads one page of the undiscovered range. Scrolling to the gap calls
     * this repeatedly until the head block meets the retained tail.
     */
    suspend fun loadGap(limit: Int = pageSize): Result<Boolean> = pageMutex.withLock {
        val current = _state.value

        if (!current.hasUnloadedGap || current.newerMessages.isEmpty()) {
            return@withLock Result.success(false)
        }

        val headNewest = current.messages.lastOrNull()?.messageId
            ?: return@withLock Result.success(false)
        val tailOldest = current.newerMessages.first().messageId

        return@withLock try {
            val pageLimit = limit.coerceIn(2, 100)
            val page = pageLoader.load(
                fromMessageId = headNewest,
                limit = pageLimit,
                offset = -(pageLimit - 1)
            ).getOrThrow()

            val fetched = page.messages
                .filterNot { message -> message.messageId in deletedIds }
                .filter { message -> message.sendState != MessageSendState.Failed }
                .filter { message -> message.messageId > headNewest }
                .sortedBy { message -> message.messageId }

            val reachesTail = fetched.any { message -> message.messageId >= tailOldest }
            val inGap = fetched.filter { message -> message.messageId < tailOldest }
            val merged = (current.messages + inGap)
                .distinctBy { message -> message.messageId }
                .sortedBy { message -> message.messageId }

            // No newer message exists between the blocks (or the page reached
            // the retained tail): close the gap and join both blocks.
            val closesGap = reachesTail || inGap.isEmpty()
            val joined = if (closesGap) {
                (merged + current.newerMessages)
                    .distinctBy { message -> message.messageId }
                    .sortedBy { message -> message.messageId }
            } else {
                merged
            }

            _state.value = current.copy(
                messages = joined,
                newerMessages = if (closesGap) emptyList() else current.newerMessages,
                hasUnloadedGap = !closesGap
            )
            Result.success(true)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    /**
     * Binary-searches the first existing message id. `getChatHistory` returns
     * the anchor or the closest older message, so a midpoint without messages
     * proves the chat starts later. This costs a logarithmic number of tiny
     * requests and never walks the history.
     */
    private suspend fun findFirstMessageId(upper: Long): Long {
        var low = 1L
        var high = upper.coerceAtLeast(1L)
        var probes = 0

        while (low < high && probes < 64) {
            val mid = low + (high - low) / 2
            val page = pageLoader.load(fromMessageId = mid, limit = 1).getOrThrow()
            val closest = page.messages
                .mapNotNull { message -> message.messageId.takeIf { id -> id in 1..mid } }
                .maxOrNull()

            if (closest == null) {
                low = mid + 1
            } else {
                high = closest
            }

            probes++
        }

        return high
    }

    /**
     * Pages history backwards until [messageId] is part of the window.
     *
     * A target newer than a [loadAround] block is fetched by loading around
     * it instead; a target above a contiguous window that is not in it was
     * deleted (or is newer than the newest page), so paging would be wasted.
     */
    suspend fun loadUntilLoaded(messageId: Long, limit: Int = pageSize): Result<Boolean> {
        val initial = _state.value

        if (initial.contains(messageId)) return Result.success(true)

        val initialNewest = initial.newestMessageId
        if (initial.hasMoreNewer && initialNewest != null && messageId > initialNewest) {
            return loadAround(messageId, radius = limit)
        }

        val initialOldest = initial.oldestMessageId
        if (initialOldest != null && messageId > initialOldest) return Result.success(false)

        var pages = 0

        while (pages < maxJumpPages) {
            val result = loadOlder(limit)
            result.exceptionOrNull()?.let { return Result.failure(it) }

            val state = _state.value

            if (state.contains(messageId)) return Result.success(true)

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

        val restorable = messages
            .filter { message -> message.sendState == MessageSendState.Sent }
            .sortedBy { message -> message.messageId }

        if (restorable.isEmpty()) return

        var seededNow = false

        _state.update { state ->
            if (state.messages.isNotEmpty()) {
                state
            } else {
                seededNow = true
                MessageHistory(
                    messages = restorable,
                    hasMoreOlder = true,
                    isLoadingInitial = false
                )
            }
        }

        // Only mark the window as a preview when this call actually seeded it.
        if (seededNow) {
            isSeededWindow = true
        }
    }

    /** Merges messages coming from a live update. */
    fun merge(incoming: List<ChatMessage>) {
        if (incoming.isEmpty()) return

        val accepted = incoming.filter { message ->
            val finalId = aliases[message.messageId]
            finalId == null && message.messageId !in deletedIds
        }

        if (accepted.isEmpty()) return

        // While the window is an older block, messages newer than its newest
        // item are outside the window. Merging them would create a hole that
        // older paging cannot fill; "jump to latest" refreshes instead.
        val current = _state.value
        val newest = current.newestMessageId
        val inWindow = if (current.hasMoreNewer && newest != null) {
            accepted.filter { message -> message.messageId <= newest }
        } else {
            accepted
        }

        if (inWindow.isEmpty()) return

        mergeMessages(inWindow, replace = false)
    }

    /** Applies a live update to every message of the window. */
    fun mutate(transform: (ChatMessage) -> ChatMessage) {
        _state.update { state ->
            state.copy(messages = state.messages.map(transform))
        }
    }

    fun remove(messageIds: Set<Long>) {
        if (messageIds.isEmpty()) return

        messageIds.forEach { id ->
            deletedIds += id
            aliases.remove(id)
        }

        // A page that was fetched before the delete may still be merged
        // later; the tombstone set is what keeps it out. Keep the set small.
        while (deletedIds.size > MaxDeletedIds) {
            deletedIds.remove(deletedIds.first())
        }

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
        if (oldMessageId != message.messageId) {
            aliases[oldMessageId] = message.messageId
            while (aliases.size > MaxAliases) {
                aliases.remove(aliases.keys.first())
            }
        }

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

    private fun mergeMessages(incoming: List<ChatMessage>, replace: Boolean) {
        if (incoming.isEmpty() && !replace) return

        _state.update { state ->
            if (incoming.isEmpty() && !replace) {
                state
            } else {
                val current = if (replace) emptyList() else state.messages
                val byId = LinkedHashMap<Long, ChatMessage>(current.size + incoming.size)

                current.forEach { message -> byId[message.messageId] = message }
                incoming.forEach { message ->
                    if (message.messageId !in deletedIds) {
                        byId[message.messageId] = message
                    }
                }

                state.copy(messages = byId.values.sortedBy { it.messageId })
            }
        }
    }

    private companion object {
        const val MaxDeletedIds = 4_096
        const val MaxAliases = 1_024
    }
}
