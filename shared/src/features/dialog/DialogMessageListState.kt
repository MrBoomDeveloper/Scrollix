package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.storage.DialogScrollPosition

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/**
 * Owns everything that concerns scrolling inside a dialog:
 * the initial landing on the newest message, keeping the viewport anchored
 * when older pages are prepended, the "new messages" indicator and jumps
 * to messages that may still be outside the loaded window.
 */
@Stable
class DialogMessageListState internal constructor(
    val listState: LazyListState,
    startReady: Boolean,
    startNewestId: Long?,
    /** Whether the restored position is the bottom of the history. */
    val startsAtBottom: Boolean,
    private val history: () -> MessageHistory,
    /** Messages as rendered, with media albums merged into single rows. */
    private val displayMessages: () -> List<ChatMessage>,
    private val requestOlderPage: suspend () -> Result<Boolean>,
    /** Loads the true beginning and leaves the previous window as a gap. */
    private val requestBeginning: suspend () -> Result<Boolean>,
    private val requestMessage: suspend (Long) -> Result<Boolean>,
    private val requestAround: suspend (Long) -> Result<Boolean>,
    private val refreshNewest: suspend () -> Result<Unit>,
    private val onError: (Throwable) -> Unit
) {
    var hasNewMessages by mutableStateOf(false)
        internal set

    var isAtBottom by mutableStateOf(startsAtBottom)
        internal set

    var isJumping by mutableStateOf(false)
        private set

    /** True once the list sits at its restored position; gates the first frame. */
    var isReady by mutableStateOf(startReady)
        internal set

    internal var newestSeenId: Long? = startNewestId
    internal var isPaging = false

    private var queuedJumpMessageId: Long? = null

    /**
     * Scrolls to [messageId]. A message outside the window is fetched with a
     * windowed load around the target, not by paging the whole chat.
     *
     * While a jump is running, a second request replaces the target instead
     * of being dropped: tapping a pinned message during a long jump used to
     * surface "Couldn't load the message".
     */
    suspend fun jumpToMessage(messageId: Long): Boolean {
        if (isJumping) {
            queuedJumpMessageId = messageId
            return true
        }

        isJumping = true
        hasNewMessages = false

        try {
            var target: Long? = messageId

            while (target != null) {
                if (!runJump(target)) return false
                target = queuedJumpMessageId.also { queuedJumpMessageId = null }
            }

            return true
        } finally {
            queuedJumpMessageId = null
            isJumping = false
        }
    }

    private suspend fun runJump(messageId: Long): Boolean {
        if (!history().contains(messageId)) {
            val around = requestAround(messageId)

            val loaded = if (around.isSuccess && around.getOrThrow()) {
                true
            } else {
                requestMessage(messageId).getOrElse { throwable ->
                    onError(throwable)
                    return false
                }
            }

            if (!loaded) return false
        }

        val index = displayMessages().indexOfFirst { message ->
            messageId in message.allMessageIds
        }

        if (index < 0) return false

        // Animating across a long distance composes every intermediate row
        // and starts their media loads. An instant scroll is both faster and
        // less jarring for a jump.
        if (abs(index - listState.firstVisibleItemIndex) > 10) {
            listState.scrollToItem(index)
        } else {
            listState.animateScrollToItem(index)
        }

        return true
    }

    /**
     * Lands on the newest message with one scroll. The newest page is
     * refreshed first only so messages that were never merged are included;
     * the scroll itself is never animated across the whole gap.
     */
    suspend fun jumpToLatest() {
        if (isJumping) return

        isJumping = true
        hasNewMessages = false

        try {
            // Reaching the end must not walk through messages that were never
            // loaded: refresh the newest page (which also closes an
            // undiscovered gap) and let it lay out before landing on the true
            // end without any animation.
            refreshNewest().exceptionOrNull()?.let(onError)

            withFrameNanos { }
            withFrameNanos { }

            val messages = displayMessages()
            if (messages.isNotEmpty()) {
                listState.scrollToItem(messages.lastIndex, Int.MAX_VALUE)
            }

            newestSeenId = history().newestMessageId
            isAtBottom = true
        } finally {
            isJumping = false
        }
    }

    /**
     * Jumps to the true first message. Only the first page is fetched; the
     * middle stays marked as undiscovered and is filled lazily when the
     * placeholder scrolls into view.
     */
    suspend fun jumpToFirstMessage() {
        if (isJumping) return

        isJumping = true

        try {
            val result = requestBeginning()
            val throwable = result.exceptionOrNull()

            if (throwable != null) {
                onError(throwable)
                return
            }

            if (result.getOrNull() == true) {
                listState.scrollToItem(0)
            }
        } finally {
            isJumping = false
        }
    }
}

@Composable
fun rememberDialogMessageListState(
    providerDialogId: String,
    history: MessageHistory,
    displayMessages: List<ChatMessage>,
    initialMessageId: Long? = null,
    savedPosition: DialogScrollPosition?,
    trackPosition: Boolean,
    loadOlder: suspend () -> Result<Boolean>,
    loadBeginning: suspend () -> Result<Boolean>,
    loadUntilLoaded: suspend (Long) -> Result<Boolean>,
    loadAround: suspend (Long) -> Result<Boolean> = loadUntilLoaded,
    loadLatest: suspend () -> Result<Unit>,
    onPositionChanged: (DialogScrollPosition) -> Unit,
    onError: (Throwable) -> Unit
): DialogMessageListState {
    // A dialog that was opened before already has its window in memory, so the
    // LazyColumn can start exactly at the restored anchor instead of rendering
    // the newest message for one frame and then jumping there.
    val initialPosition = remember(providerDialogId) {
        val messages = displayMessages
        val saved = savedPosition

        when {
            messages.isEmpty() || history.isLoadingInitial -> null

            // A post opened from global search wins over the saved anchor.
            initialMessageId != null -> {
                val index = messages.indexOfFirst { message ->
                    initialMessageId in message.allMessageIds
                }

                if (index >= 0) index to 0 else null
            }

            saved != null && !saved.atBottom -> {
                val index = messages.indexOfFirst { message ->
                    saved.anchorMessageId in message.allMessageIds
                }

                if (index >= 0) index to saved.offset else null
            }

            else -> messages.lastIndex to 0
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPosition?.first ?: 0,
        initialFirstVisibleItemScrollOffset = initialPosition?.second ?: 0
    )
    val latestHistory = rememberUpdatedState(history)
    val latestDisplayMessages = rememberUpdatedState(displayMessages)
    val latestInitialMessageId = rememberUpdatedState(initialMessageId)
    val latestSavedPosition = rememberUpdatedState(savedPosition)
    val latestLoadOlder = rememberUpdatedState(loadOlder)
    val latestLoadBeginning = rememberUpdatedState(loadBeginning)
    val latestLoadUntilLoaded = rememberUpdatedState(loadUntilLoaded)
    val latestLoadAround = rememberUpdatedState(loadAround)
    val latestLoadLatest = rememberUpdatedState(loadLatest)
    val latestOnPositionChanged = rememberUpdatedState(onPositionChanged)
    val latestOnError = rememberUpdatedState(onError)

    val startAtBottom = initialPosition != null &&
        initialMessageId == null &&
        (savedPosition?.atBottom != false)

    val state = remember(providerDialogId) {
        DialogMessageListState(
            listState = listState,
            startReady = initialPosition != null,
            startNewestId = if (initialPosition != null) {
                history.newestMessageId
            } else {
                null
            },
            startsAtBottom = startAtBottom,
            history = { latestHistory.value },
            displayMessages = { latestDisplayMessages.value },
            requestOlderPage = { latestLoadOlder.value() },
            requestBeginning = { latestLoadBeginning.value() },
            requestMessage = { messageId -> latestLoadUntilLoaded.value(messageId) },
            requestAround = { messageId -> latestLoadAround.value(messageId) },
            refreshNewest = { latestLoadLatest.value() },
            onError = { throwable -> latestOnError.value(throwable) }
        )
    }

    LaunchedEffect(providerDialogId) {
        state.newestSeenId = if (state.isReady) history.newestMessageId else null
        state.hasNewMessages = false
        state.isAtBottom = state.startsAtBottom
    }

    // Restore the saved anchor on the first paint, paging history back when
    // the anchor is older than the initially loaded page. Dialogs that were
    // left at the bottom keep landing on the newest message.
    LaunchedEffect(
        providerDialogId,
        history.isLoadingInitial,
        history.messages.isNotEmpty()
    ) {
        if (state.isReady) return@LaunchedEffect
        if (history.isLoadingInitial) return@LaunchedEffect

        // A failed initial load leaves the window empty forever unless the
        // gate is released; the screen shows its error state above the list.
        if (history.messages.isEmpty()) {
            state.isReady = true
            return@LaunchedEffect
        }

        val initialTarget = latestInitialMessageId.value

        if (initialTarget != null) {
            var index = latestDisplayMessages.value.indexOfFirst { message ->
                initialTarget in message.allMessageIds
            }

            if (index < 0) {
                latestLoadUntilLoaded.value(initialTarget)
                index = latestDisplayMessages.value.indexOfFirst { message ->
                    initialTarget in message.allMessageIds
                }
            }

            if (index < 0) {
                // The target was deleted: land on the nearest surviving
                // message instead of falling through to the newest one.
                index = latestDisplayMessages.value.indexOfFirst { message ->
                    message.messageId > initialTarget
                }

                if (index < 0) index = latestDisplayMessages.value.lastIndex
            }

            if (index >= 0) {
                // requestScrollToItem records the position without waiting for
                // layout. The list is still hidden behind the isReady gate at
                // this point, so scrollToItem here would suspend forever.
                listState.requestScrollToItem(index)
                state.newestSeenId = latestHistory.value.newestMessageId
                state.isReady = true
                return@LaunchedEffect
            }
        }

        val saved = latestSavedPosition.value

        if (saved != null && !saved.atBottom) {
            var index = latestDisplayMessages.value.indexOfFirst { message ->
                saved.anchorMessageId in message.allMessageIds
            }

            if (index < 0) {
                latestLoadUntilLoaded.value(saved.anchorMessageId)
                index = latestDisplayMessages.value.indexOfFirst { message ->
                    saved.anchorMessageId in message.allMessageIds
                }
            }

            if (index < 0) {
                index = latestDisplayMessages.value.indexOfFirst { message ->
                    message.messageId > saved.anchorMessageId
                }

                if (index < 0) index = latestDisplayMessages.value.lastIndex
            }

            if (index >= 0) {
                listState.requestScrollToItem(index, saved.offset)
                state.newestSeenId = latestHistory.value.newestMessageId
                state.isReady = true
                return@LaunchedEffect
            }
        }

        val messages = latestDisplayMessages.value
        if (messages.isEmpty()) return@LaunchedEffect

        listState.requestScrollToItem(messages.lastIndex)
        state.newestSeenId = latestHistory.value.newestMessageId
        state.isReady = true
    }

    // Follow live messages only while the user is already at the bottom.
    // Otherwise raise the indicator and leave the viewport alone.
    LaunchedEffect(providerDialogId, history.newestMessageId, state.isReady) {
        if (!state.isReady) return@LaunchedEffect

        val newest = history.newestMessageId ?: return@LaunchedEffect
        val previous = state.newestSeenId

        state.newestSeenId = newest

        if (previous == null || previous == newest) return@LaunchedEffect

        val index = latestDisplayMessages.value.lastIndex
        if (index < 0) return@LaunchedEffect

        // Never move the viewport while the user is touching the list: content
        // shifting under a finger is what makes the history look like it
        // jumped. A plain scroll also avoids an animation that the user could
        // fight with.
        val laidOut = listState.layoutInfo.visibleItemsInfo.isNotEmpty()

        if (state.isAtBottom && laidOut && !listState.isScrollInProgress) {
            listState.scrollToItem(index)
        } else {
            state.hasNewMessages = true
        }
    }

    // Sending a message appends a pending row, and the composer's measured
    // height changes when the keyboard opens. While the user is at the bottom,
    // re-anchor after every such change so the newest row stays flush with the
    // composer instead of drifting a few pixels up.
    LaunchedEffect(
        providerDialogId,
        state.isReady,
        latestDisplayMessages.value.size
    ) {
        if (!state.isReady) return@LaunchedEffect
        if (!state.isAtBottom) return@LaunchedEffect
        if (latestDisplayMessages.value.isEmpty()) return@LaunchedEffect

        withFrameNanos { }
        listState.scrollToItem(latestDisplayMessages.value.lastIndex, Int.MAX_VALUE)
    }

    LaunchedEffect(providerDialogId, listState) {
        snapshotFlow {
            val info = listState.layoutInfo

            // canScrollForward already accounts for the bottom content
            // padding, which is the measured composer height. Comparing the
            // last item to viewportEndOffset marked the list "at bottom" while
            // the newest message was still behind the floating composer.
            info.visibleItemsInfo.isNotEmpty() && !listState.canScrollForward
        }
            .distinctUntilChanged()
            .collect { atBottom ->
                state.isAtBottom = atBottom

                if (atBottom) {
                    state.hasNewMessages = false
                }
            }
    }

    // Persist the visible anchor once scrolling settles. Tracking is disabled
    // while search results are shown, so they cannot overwrite the real
    // position of the dialog.
    LaunchedEffect(providerDialogId, listState, trackPosition) {
        if (!trackPosition) return@LaunchedEffect

        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling || !state.isReady) return@collect

                val current = latestHistory.value
                if (current.messages.isEmpty()) return@collect

                val info = listState.layoutInfo
                val first = info.visibleItemsInfo.firstOrNull() ?: return@collect
                val anchor = latestDisplayMessages.value.getOrNull(first.index)
                    ?: return@collect

                latestOnPositionChanged.value(
                    DialogScrollPosition(
                        anchorMessageId = anchor.messageId,
                        offset = listState.firstVisibleItemScrollOffset,
                        atBottom = !listState.canScrollForward
                    )
                )
            }
    }

    // Reaching the very top asks for exactly one older page. Compose then
    // anchors on the previously visible message, so the window grows without
    // the content jumping under the user's finger.
    LaunchedEffect(providerDialogId, listState, state.isReady) {
        if (!state.isReady) return@LaunchedEffect

        snapshotFlow {
            val info = listState.layoutInfo
            val laidOut = info.totalItemsCount > 0 && info.visibleItemsInfo.isNotEmpty()

            laidOut to (
                listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                )
        }
            .distinctUntilChanged()
            .collect { (laidOut, atTop) ->
                // Waiting for a real layout keeps the restore scroll position
                // valid; paging before the list exists would shift its index.
                if (!laidOut || !atTop || !state.isReady) return@collect

                val current = latestHistory.value
                if (current.messages.isEmpty() || !current.hasMoreOlder) return@collect
                if (state.isJumping || state.isPaging) return@collect

                state.isPaging = true

                try {
                    // Keep requesting pages while the viewport is still not
                    // filled; this only happens for chats shorter than the
                    // screen, where the list cannot scroll away from the top.
                    while (true) {
                        val before = latestHistory.value

                        if (state.isJumping || before.messages.isEmpty() || !before.hasMoreOlder) {
                            break
                        }

                        val result = latestLoadOlder.value()
                        val throwable = result.exceptionOrNull()

                        if (throwable != null) {
                            latestOnError.value(throwable)
                            break
                        }

                        val after = latestHistory.value

                        if (after.messages.size == before.messages.size || !after.hasMoreOlder) {
                            break
                        }

                        withFrameNanos { }

                        if (listState.firstVisibleItemIndex != 0 ||
                            listState.firstVisibleItemScrollOffset != 0
                        ) {
                            break
                        }

                        val info = listState.layoutInfo
                        val viewportFilled = info.visibleItemsInfo.lastOrNull()?.let { item ->
                            item.offset + item.size >= info.viewportEndOffset
                        } ?: false

                        if (viewportFilled) break
                    }
                } finally {
                    state.isPaging = false
                }
            }
    }

    return state
}

private const val MaxFirstMessagePages = 200
