package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.data.model.UndiscoveredMessagesMessageId
import com.mrboomdev.scrollix.data.model.groupMessageAlbums
import com.mrboomdev.scrollix.data.outbox.OutboxEntry
import com.mrboomdev.scrollix.data.outbox.OutboxEntryKind
import com.mrboomdev.scrollix.data.outbox.OutboxEntryState
import com.mrboomdev.scrollix.data.outbox.OutboxManager
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Data plumbing for the chat screen: collects the dialog, its history, the
 * outbox and settings, then keeps the rows the list renders in sync. Everything
 * here runs once per ViewModel, not per recomposition.
 */
internal fun DialogScreenViewModel.observe() {
    viewModelScope.launch {
        AppSettingsStore.state.collect { current ->
            settings = current
            refreshDoubleTapReaction()
            refreshWallpaper()
        }
    }

    viewModelScope.launch {
        ApiManager.folders.collect { loaded -> folders = loaded }
    }

    viewModelScope.launch {
        ApiManager.sessions.collect { loaded -> allSessions = loaded }
    }

    viewModelScope.launch {
        val currentSession = session ?: return@launch

        currentSession.dialogs.collect { loaded ->
            dialogs = loaded
            captureUnreadBoundary()
            resolvePinnedMessage()
            refreshHeaderStatus()
            refreshWallpaper()
        }
    }

    viewModelScope.launch {
        val currentSession = session ?: return@launch

        currentSession.isConnected.collect { connected ->
            isConnected = connected
            refreshHeaderStatus()
        }
    }

    viewModelScope.launch {
        session?.messageHistory(providerDialogId)?.collect { loaded ->
            applyHistory(loaded)
        }
    }

    viewModelScope.launch {
        OutboxManager.entries.collect { entries ->
            pendingMessages = entries
                .filter { entry ->
                    entry.accountId == accountId &&
                        entry.providerDialogId == providerDialogId &&
                        (
                            entry.kind == OutboxEntryKind.Text ||
                                entry.kind == OutboxEntryKind.Files
                            )
                }
                .map { entry -> entry.toPendingMessage() }

            recomputeDerived()
        }
    }

    viewModelScope.launch {
        snapshotFlow { searchQuery.text.toString() }
            .distinctUntilChanged()
            .collect { query -> runSearch(query) }
    }
}

/** Loads the chat once the screen is on stage. */
internal fun DialogScreenViewModel.begin() {
    val currentSession = session

    if (currentSession == null) {
        error = "Account session not found"
        history = history.copy(isLoadingInitial = false)
        draftRestored = true
        return
    }

    error = null

    viewModelScope.launch {
        currentSession.loadMessages(providerDialogId)
            .onFailure { error = it.message ?: "Failed to load messages" }
    }

    viewModelScope.launch {
        currentSession.loadPinnedMessageId(providerDialogId)
    }

    restoreDraft()
    resolvePinnedMessage()
}

internal fun DialogScreenViewModel.applyHistory(loaded: MessageHistory) {
    history = loaded
    messages = loaded.messages

    recomputeDerived()
    captureUnreadBoundary()
    resolvePinnedMessage()
    markNewestRead()
}

/**
 * Rebuilds every list the UI reads from the raw history, the outbox and the
 * search results. Called whenever one of those inputs changes.
 */
internal fun DialogScreenViewModel.recomputeDerived() {
    val currentHistory = history

    val gap = if (!currentHistory.hasUnloadedGap || currentHistory.newerMessages.isEmpty()) {
        null
    } else {
        val headNewest = currentHistory.messages.lastOrNull()
        val tailOldest = currentHistory.newerMessages.firstOrNull()

        ChatMessage(
            providerId = currentHistory.messages.firstOrNull()?.providerId ?: "",
            accountId = accountId,
            providerDialogId = providerDialogId,
            messageId = UndiscoveredMessagesMessageId,
            senderId = 0,
            text = "",
            timestamp = if (headNewest != null && tailOldest != null) {
                (headNewest.timestamp + tailOldest.timestamp) / 2
            } else 0L,
            isOutgoing = false,
            isService = true
        )
    }

    val head = currentHistory.messages.groupMessageAlbums()

    // While the middle is undiscovered the far end is not rendered at all, so a
    // scroll can never jump over the unloaded range into loaded messages.
    // Filling the gap lazily merges the tail back in order.
    displayMessages = if (gap != null) {
        head + gap
    } else {
        head + (currentHistory.newerMessages + pendingMessages).groupMessageAlbums()
    }

    threadRailItems = (currentHistory.messages + currentHistory.newerMessages)
        .filter { message -> message.replyCount > 0 }

    messagesById = (currentHistory.messages + currentHistory.newerMessages)
        .associateBy { message -> message.messageId }

    gapRowId = gap?.rowId

    visibleMessages = if (searchQuery.text.isBlank()) {
        displayMessages
    } else {
        searchResults.groupMessageAlbums()
    }

    trackLocallySentMessages()
    ensureMessageEmojiLoaded()
    loadMissingReplies()
}

private fun OutboxEntry.toPendingMessage(): ChatMessage {
    return ChatMessage(
        providerId = providerId,
        accountId = accountId,
        providerDialogId = providerDialogId,
        messageId = localMessageId,
        senderId = 0,
        text = pendingText,
        timestamp = createdAt,
        isOutgoing = true,
        customEmojis = customEmojis,
        replyTo = replyToMessageId?.let { messageId -> MessageReply(messageId = messageId) },
        sendState = if (state == OutboxEntryState.Failed) {
            MessageSendState.Failed
        } else {
            MessageSendState.Sending
        }
    )
}

/** Applies the configured double-tap reaction, falling back to the quick list. */
internal fun DialogScreenViewModel.refreshDoubleTapReaction() {
    val preferred = settings.doubleTapReaction

    doubleTapReaction = if (preferred != null) {
        MessageReactionOption(
            emoji = preferred.emoji,
            customEmojiId = preferred.customEmojiId
        )
    } else {
        settings.quickReactions.firstOrNull()?.let { emoji -> MessageReactionOption(emoji) }
    }
}

/**
 * The unread boundary is captured once per opened dialog: marking the messages
 * read must not remove the header while the chat stays open.
 */
internal fun DialogScreenViewModel.captureUnreadBoundary() {
    val currentDialog = dialog ?: return

    if (unreadAtOpen == null) {
        unreadAtOpen = currentDialog.unreadCount
    }

    if (unreadAnchorResolved) return

    val unread = unreadAtOpen ?: return
    if (messages.isEmpty()) return

    unreadAnchorResolved = true
    if (unread <= 0) return

    // The unread tail starts this many messages before the newest one.
    val start = (messages.size - unread).coerceIn(0, messages.lastIndex)
    val anchorIndex = (start until messages.size).firstOrNull { index ->
        !messages[index].isOutgoing
    } ?: start

    // Keep the raw id: the album base can change when an earlier member loads,
    // but the raw id stays inside allMessageIds of the row.
    unreadAnchorMessageId = messages[anchorIndex].messageId
}

/** Loads the pinned message when it sits outside the loaded window. */
internal fun DialogScreenViewModel.resolvePinnedMessage() {
    val pinnedId = dialog?.pinnedMessageId

    if (pinnedId == null) {
        loadedPinnedMessage = null
        attemptedPinnedMessageId = null
        return
    }

    if (messages.any { message -> pinnedId in message.allMessageIds }) return
    if (loadedPinnedMessage?.messageId == pinnedId) return
    if (attemptedPinnedMessageId == pinnedId) return

    val currentSession = session ?: return

    attemptedPinnedMessageId = pinnedId

    viewModelScope.launch {
        loadedPinnedMessage = currentSession.loadMessage(providerDialogId, pinnedId)
            .getOrNull()
    }
}

internal fun DialogScreenViewModel.markNewestRead() {
    if (messages.isEmpty()) return

    val newest = messages.lastOrNull()?.messageId ?: return
    if (newest == lastMarkedReadId) return

    val currentSession = session ?: return
    lastMarkedReadId = newest

    viewModelScope.launch {
        currentSession.markMessagesRead(
            providerDialogId = providerDialogId,
            messageIds = messages
                .takeLast(50)
                .map { message -> message.messageId }
                .toLongArray()
        )
    }
}

/**
 * A pending outgoing message is by definition one this device sent; messages
 * from another device arrive already marked as sent.
 */
internal fun DialogScreenViewModel.trackLocallySentMessages() {
    val pending = messages
        .filter { message -> message.isOutgoing && message.sendState == MessageSendState.Sending }
        .flatMap { message -> message.allMessageIds }

    if (pending.isEmpty()) return

    locallySentMessageIds = (locallySentMessageIds + pending)
        .toList()
        .takeLast(200)
        .toSet()
}

/** Loads the custom emoji used by stickers, reactions and poll options. */
internal fun DialogScreenViewModel.ensureMessageEmojiLoaded() {
    val currentSession = session ?: return

    val missing = messages
        .flatMap { message ->
            message.customEmojis.map { span -> span.emojiId } +
                message.reactions.mapNotNull { reaction -> reaction.customEmojiId } +
                message.poll?.options.orEmpty().flatMap { option ->
                    option.customEmojis.map { span -> span.emojiId }
                }
        }
        .distinct()
        .filter { id -> id !in customEmojiData && inFlightEmojiIds.add(id) }

    if (missing.isEmpty()) return

    viewModelScope.launch {
        val loaded = withContext(Dispatchers.Default) {
            missing.mapNotNull { id ->
                currentSession.loadCustomEmoji(id)?.let { asset -> id to asset }
            }.toMap()
        }

        inFlightEmojiIds.removeAll(missing)

        if (loaded.isNotEmpty()) {
            customEmojiData = customEmojiData + loaded.mapValues { (_, asset) -> asset.data }
            customEmojiAdaptive = customEmojiAdaptive + loaded
                .filterValues { asset -> asset.needsRepainting }
                .keys
        }
    }
}

/** Fetches quoted messages that are outside the loaded window. */
internal fun DialogScreenViewModel.loadMissingReplies() {
    val currentSession = session ?: return

    val missing = visibleMessages
        .mapNotNull { message -> message.replyTo?.messageId }
        .filter { id ->
            messages.none { it.messageId == id } &&
                id !in loadedReplies &&
                id !in attemptedReplyIds
        }
        .distinct()

    if (missing.isEmpty()) return

    attemptedReplyIds = attemptedReplyIds + missing

    viewModelScope.launch {
        val loaded = missing.mapNotNull { id ->
            currentSession.loadMessage(providerDialogId, id).getOrNull()
                ?.let { message -> id to message }
        }.toMap()

        if (loaded.isNotEmpty()) {
            loadedReplies = loadedReplies + loaded
        }
    }
}

internal fun DialogScreenViewModel.runSearch(query: String) {
    val trimmed = query.trim()

    if (trimmed.isEmpty() || session == null) {
        rawSearchResults = emptyList()
        searchResults = emptyList()
        isSearchPending = false
        recomputeDerived()
        return
    }

    isSearchPending = true

    viewModelScope.launch {
        delay(300.milliseconds)

        rawSearchResults = session?.searchMessages(providerDialogId, trimmed)
            ?.getOrDefault(emptyList())
            ?: emptyList()

        searchResults = filterSearchMessages(rawSearchResults, searchFilter)

        isSearchPending = false
        recomputeDerived()
    }
}

/** Decodes the app wallpaper and the chat's own one outside composition. */
internal fun DialogScreenViewModel.refreshWallpaper() {
    val currentSettings = settings
    val currentDialog = dialog
    val currentSession = session

    wallpaperJob?.cancel()

    wallpaperJob = viewModelScope.launch {
        appWallpaper = if (currentSettings.hasCustomWallpaper) {
            val bytes = withContext(Dispatchers.IO) {
                AppSettingsStore.readWallpaper()
            }

            bytes?.let { data ->
                withContext(Dispatchers.Default) { decodeImageBitmap(data) }
            }
        } else {
            null
        }

        val wallpaper = currentDialog?.wallpaper

        if (wallpaper == null) {
            wallpaperImage = null
            return@launch
        }

        val cacheKey = buildString {
            append(currentDialog.providerDialogId)
            append(':')
            append(wallpaper.fileUniqueId ?: wallpaper.fileId)
            append(':')
            append(wallpaper.thumbnailUniqueId ?: wallpaper.thumbnailFileId)
        }

        WallpaperImageCache.exact(cacheKey)?.let { cached ->
            wallpaperImage = cached
            return@launch
        }

        var image: ImageBitmap? = null
        val thumbnailFileId = wallpaper.thumbnailFileId

        // Cached media is addressed by the durable remote unique id, so a custom
        // wallpaper is restored right away instead of waiting for TDLib to
        // replay the chat: file ids stored in a cached dialog are only valid
        // for the run that produced them.
        val thumbnailBytes = wallpaper.thumbnailUniqueId
            ?.let { uniqueId -> currentSession?.loadCachedMedia(uniqueId) }
            ?: thumbnailFileId?.let { fileId -> currentSession?.loadFile(fileId)?.getOrNull() }

        if (thumbnailBytes != null) {
            image = withContext(Dispatchers.Default) {
                decodeImageBitmap(thumbnailBytes)
            }
        }

        val fileId = wallpaper.fileId

        if (image == null && fileId != null && fileId != thumbnailFileId) {
            val bytes = wallpaper.fileUniqueId
                ?.let { uniqueId -> currentSession?.loadCachedMedia(uniqueId) }
                ?: currentSession?.loadFile(fileId)?.getOrNull()

            if (bytes != null) {
                // Animated wallpapers (video/TGS) can't be decoded as a bitmap;
                // the thumbnail stays as the wallpaper in that case.
                image = withContext(Dispatchers.Default) {
                    decodeImageBitmap(bytes)
                }
            }
        }

        image?.let { decoded ->
            WallpaperImageCache.put(cacheKey, decoded)
            wallpaperImage = decoded
        }
    }
}

/** Message-list paging entry points, bound to this screen's session. */
internal suspend fun DialogScreenViewModel.loadOlderMessages(): Result<Boolean> {
    return session?.loadOlderMessages(providerDialogId)
        ?: Result.failure(IllegalStateException("Account session not found"))
}

internal suspend fun DialogScreenViewModel.loadBeginning(): Result<Boolean> {
    return session?.jumpToBeginning(providerDialogId)
        ?: Result.failure(IllegalStateException("Account session not found"))
}

internal suspend fun DialogScreenViewModel.loadUntilLoaded(messageId: Long): Result<Boolean> {
    return session?.loadMessagesUntil(providerDialogId, messageId)
        ?: Result.failure(IllegalStateException("Account session not found"))
}

internal suspend fun DialogScreenViewModel.loadAround(messageId: Long): Result<Boolean> {
    return session?.loadMessagesAround(providerDialogId, messageId)
        ?: Result.failure(IllegalStateException("Account session not found"))
}

internal suspend fun DialogScreenViewModel.loadLatest(): Result<Unit> {
    return session?.loadMessages(providerDialogId)
        ?: Result.failure(IllegalStateException("Account session not found"))
}

internal suspend fun DialogScreenViewModel.loadNewerMessages(): Result<Boolean>? {
    return session?.loadNewerMessages(providerDialogId)
}

/** Hands the queued scroll intent to the screen, which owns the list. */
internal fun DialogScreenViewModel.takeScrollRequest(): ScrollRequest? {
    val request = scrollRequest ?: return null
    scrollRequest = null
    return request
}
