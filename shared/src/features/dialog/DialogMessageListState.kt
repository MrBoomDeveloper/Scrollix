package com.mrboomdev.scrollix.features.dialog

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
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.storage.DialogScrollPosition
import kotlinx.coroutines.flow.distinctUntilChanged

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
    private val history: () -> MessageHistory,
    private val requestOlderPage: suspend () -> Result<Boolean>,
    private val requestMessage: suspend (Long) -> Result<Boolean>,
    private val refreshNewest: suspend () -> Result<Unit>,
    private val onError: (Throwable) -> Unit
) {
    var hasNewMessages by mutableStateOf(false)
        internal set

    var isAtBottom by mutableStateOf(true)
        internal set

    var isJumping by mutableStateOf(false)
        private set

    /** True once the list sits at its restored position; gates the first frame. */
    var isReady by mutableStateOf(startReady)
        internal set

    internal var newestSeenId: Long? = startNewestId
    internal var isPaging = false

    /**
     * Scrolls to [messageId], paging history backwards first when the
     * message has not been loaded yet.
     */
    suspend fun jumpToMessage(messageId: Long): Boolean {
        if (isJumping) return false

        isJumping = true
        hasNewMessages = false

        try {
            if (!history().contains(messageId)) {
                val loaded = requestMessage(messageId).getOrElse { throwable ->
                    onError(throwable)
                    return false
                }

                if (!loaded) return false
            }

            val index = history().indexOf(messageId)
            if (index < 0) return false

            listState.animateScrollToItem(index)
            return true
        } finally {
            isJumping = false
        }
    }

    /**
     * Lands on the newest message that is already loaded first, so the jump
     * never depends on the network refresh. The newest page is then refreshed
     * so messages that were never merged into the window are included too.
     */
    suspend fun jumpToLatest() {
        if (isJumping) return

        isJumping = true
        hasNewMessages = false

        try {
            val loaded = history().messages
            if (loaded.isNotEmpty()) {
                listState.scrollToItem(loaded.lastIndex)
            }

            refreshNewest().exceptionOrNull()?.let(onError)

            val messages = history().messages
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.lastIndex)
            }

            newestSeenId = history().newestMessageId
        } finally {
            isJumping = false
        }
    }

    /** Pages the whole history in and lands on its first message. */
    suspend fun jumpToFirstMessage() {
        if (isJumping) return

        isJumping = true

        try {
            listState.scrollToItem(0)

            while (history().hasMoreOlder && history().messages.isNotEmpty()) {
                val sizeBefore = history().messages.size
                val result = requestOlderPage()

                result.exceptionOrNull()?.let { throwable ->
                    onError(throwable)
                    return
                }

                if (history().messages.size == sizeBefore) break
            }

            listState.scrollToItem(0)
        } finally {
            isJumping = false
        }
    }
}

@Composable
fun rememberDialogMessageListState(
    providerDialogId: String,
    history: MessageHistory,
    initialMessageId: Long? = null,
    savedPosition: DialogScrollPosition?,
    trackPosition: Boolean,
    loadOlder: suspend () -> Result<Boolean>,
    loadUntilLoaded: suspend (Long) -> Result<Boolean>,
    loadLatest: suspend () -> Result<Unit>,
    onPositionChanged: (DialogScrollPosition) -> Unit,
    onError: (Throwable) -> Unit
): DialogMessageListState {
    // A dialog that was opened before already has its window in memory, so the
    // LazyColumn can start exactly at the restored anchor instead of rendering
    // the newest message for one frame and then jumping there.
    val initialPosition = remember(providerDialogId) {
        val messages = history.messages
        val saved = savedPosition

        when {
            messages.isEmpty() || history.isLoadingInitial -> null

            // A post opened from global search wins over the saved anchor.
            initialMessageId != null -> {
                val index = messages.indexOfFirst { message ->
                    message.messageId == initialMessageId
                }

                if (index >= 0) index to 0 else null
            }

            saved != null && !saved.atBottom -> {
                val index = messages.indexOfFirst { message ->
                    message.messageId == saved.anchorMessageId
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
    val latestInitialMessageId = rememberUpdatedState(initialMessageId)
    val latestSavedPosition = rememberUpdatedState(savedPosition)
    val latestLoadOlder = rememberUpdatedState(loadOlder)
    val latestLoadUntilLoaded = rememberUpdatedState(loadUntilLoaded)
    val latestLoadLatest = rememberUpdatedState(loadLatest)
    val latestOnPositionChanged = rememberUpdatedState(onPositionChanged)
    val latestOnError = rememberUpdatedState(onError)

    val state = remember(providerDialogId) {
        DialogMessageListState(
            listState = listState,
            startReady = initialPosition != null,
            startNewestId = if (initialPosition != null) {
                history.newestMessageId
            } else {
                null
            },
            history = { latestHistory.value },
            requestOlderPage = { latestLoadOlder.value() },
            requestMessage = { messageId -> latestLoadUntilLoaded.value(messageId) },
            refreshNewest = { latestLoadLatest.value() },
            onError = { throwable -> latestOnError.value(throwable) }
        )
    }

    LaunchedEffect(providerDialogId) {
        state.newestSeenId = if (state.isReady) history.newestMessageId else null
        state.hasNewMessages = false
        state.isAtBottom = true
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
        if (history.isLoadingInitial || history.messages.isEmpty()) return@LaunchedEffect

        val initialTarget = latestInitialMessageId.value

        if (initialTarget != null) {
            var index = latestHistory.value.indexOf(initialTarget)

            if (index < 0) {
                latestLoadUntilLoaded.value(initialTarget)
                index = latestHistory.value.indexOf(initialTarget)
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
            var index = latestHistory.value.indexOf(saved.anchorMessageId)

            if (index < 0) {
                latestLoadUntilLoaded.value(saved.anchorMessageId)
                index = latestHistory.value.indexOf(saved.anchorMessageId)
            }

            if (index >= 0) {
                listState.requestScrollToItem(index, saved.offset)
                state.newestSeenId = latestHistory.value.newestMessageId
                state.isReady = true
                return@LaunchedEffect
            }
        }

        val messages = latestHistory.value.messages
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

        if (state.isAtBottom) {
            val index = latestHistory.value.messages.lastIndex
            if (index >= 0) listState.animateScrollToItem(index)
        } else {
            state.hasNewMessages = true
        }
    }

    LaunchedEffect(providerDialogId, listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()

            lastVisible != null && lastVisible.index >= info.totalItemsCount - 1
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
                val anchor = current.messages.getOrNull(first.index) ?: return@collect
                val last = info.visibleItemsInfo.lastOrNull()

                latestOnPositionChanged.value(
                    DialogScrollPosition(
                        anchorMessageId = anchor.messageId,
                        offset = listState.firstVisibleItemScrollOffset,
                        atBottom = last != null && last.index >= info.totalItemsCount - 1
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
