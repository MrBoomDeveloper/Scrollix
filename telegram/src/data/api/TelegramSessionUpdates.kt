package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.model.parseFolderCustomEmojis

internal suspend fun TelegramSession.handleUpdate(update: Update) {
    AppLog.protocol("telegram", "in update", update.toString())

    when (update) {
        is UpdateAuthorizationState -> handleAuthorizationState(update.authorizationState)

        is UpdateConnectionState -> {
            // "Updating" means TDLib is still fetching chats, not that the
            // network is gone; only a state that cannot reach the server at
            // all counts as a connection failure.
            _isConnected.value = update.state !is ConnectionStateWaitingForNetwork
        }

        is UpdateNewChat -> {
            upsertChat(update.chat)
        }

        is UpdateChatTitle -> {
            updateChat(update.chatId) { it.copy(title = update.title) }
        }

        is UpdateChatPhoto -> {
            updateChat(update.chatId) { it.copy(photo = update.photo) }
        }

        is UpdateChatLastMessage -> {
            updateChat(update.chatId) {
                it.copy(
                    lastMessage = update.lastMessage,
                    positions = update.positions.filter { position ->
                        position.order != 0L
                    }
                )
            }
        }

        // A draft saved here or on another device arrives as an update, so the
        // draft center can stay current without a full dialog reload.
        is UpdateChatDraftMessage -> {
            updateChat(update.chatId) { entry ->
                entry.copy(
                    draftText = (update.draftMessage?.content as? DraftMessageContentText)
                        ?.text
                        ?.text
                        .orEmpty()
                )
            }
        }

        is UpdateChatPosition -> {
            updateChat(update.chatId) { entry ->
                val positions = entry.positions.filterNot {
                    it.list == update.position.list
                }

                entry.copy(
                    positions = if (update.position.order == 0L) {
                        positions
                    } else {
                        positions + update.position
                    }
                )
            }
        }

        is UpdateChatReadInbox -> {
            updateChat(update.chatId) { it.copy(unreadCount = update.unreadCount) }
        }

        is UpdateChatUnreadMentionCount -> {
            updateChat(update.chatId) {
                it.copy(unreadMentionCount = update.unreadMentionCount)
            }
        }

        is UpdateChatUnreadReactionCount -> {
            updateChat(update.chatId) {
                it.copy(unreadReactionCount = update.unreadReactionCount)
            }
        }

        is UpdateChatReadOutbox -> handleChatReadOutbox(
            chatId = update.chatId,
            lastReadOutboxMessageId = update.lastReadOutboxMessageId
        )

        is UpdateChatPermissions -> updateChat(update.chatId) {
            it.copy(permissions = update.permissions)
        }

        is UpdateSupergroupFullInfo -> setMemberCount(
            key = "supergroup:${update.supergroupId}",
            memberCount = update.supergroupFullInfo.memberCount
        )

        is UpdateBasicGroupFullInfo -> setMemberCount(
            key = "basicGroup:${update.basicGroupId}",
            memberCount = update.basicGroupFullInfo.members.size
        )

        is UpdateChatBackground -> {
            updateChat(update.chatId) { entry ->
                entry.copy(background = update.background)
            }
        }

        is UpdateChatEmojiStatus -> {
            updateChat(update.chatId) { entry ->
                entry.copy(emojiStatus = update.emojiStatus)
            }
        }

        is UpdateMessageReactions -> {
            updateMessageReactions(
                chatId = update.chatId,
                messageId = update.messageId,
                reactions = update.reactions
            )
        }

        is UpdateMessageInteractionInfo -> {
            updateMessageReactions(
                chatId = update.chatId,
                messageId = update.messageId,
                interactionInfo = update.interactionInfo
            )
        }


        is UpdateChatIsMarkedAsUnread -> {
            updateChat(update.chatId) {
                it.copy(isMarkedAsUnread = update.isMarkedAsUnread)
            }
        }

        is UpdateChatAddedToList -> {
            when (val result = client.getChat(update.chatId)) {
                is TdlResult.Success -> upsertChat(result.result)
                is TdlResult.Failure -> Unit
            }
        }

        is UpdateChatRemovedFromList -> {
            updateChat(update.chatId) { entry ->
                entry.copy(
                    positions = entry.positions.filterNot {
                        it.list == update.chatList
                    }
                )
            }
        }

        is UpdateChatFolders -> {
            _folders.value = update.chatFolders.mapIndexed { index, folder ->
                val text = folder.name.text.text
                val name = text.ifBlank { "Folder" }

                DialogFolder(
                    id = folder.id,
                    name = name,
                    iconName = folder.icon.name,
                    colorId = folder.colorId,
                    order = index,
                    isShareable = folder.isShareable,
                    animateCustomEmoji = folder.name.animateCustomEmoji,
                    customEmojis = if (text.isBlank()) {
                        emptyList()
                    } else {
                        parseFolderCustomEmojis(
                            text = text,
                            entities = folder.name.text.entities.toList()
                        )
                    }
                )
            }

            requestFolderChatsLoad(update.chatFolders.map { it.id })
            requestFolderCustomEmojiLoad()
        }

        is UpdateUserStatus -> {
            _userStatuses.update { it + (update.userId to update.status) }
            emitDialogs()
        }

        is UpdateUser -> {
            _users.update { it + (update.user.id to update.user) }
            _userStatuses.update { it + (update.user.id to update.user.status) }

            if (update.user.id == currentUserId) {
                updateProfile(update.user)
            }

            refreshMessageSenders()
            emitDialogs()
        }

        is UpdateChatAction -> handleChatAction(
            chatId = update.chatId,
            senderId = update.senderId,
            action = update.action
        )

        is UpdateNewMessage -> upsertMessage(update.message)

        is UpdateMessageContent -> updateMessageContent(
            chatId = update.chatId,
            messageId = update.messageId,
            content = update.newContent
        )

        is UpdateMessageSendSucceeded -> replaceMessage(
            chatId = update.message.chatId,
            oldMessageId = update.oldMessageId,
            message = update.message
        )

        is UpdateMessageSendFailed -> removeMessages(
            chatId = update.message.chatId,
            messageIds = longArrayOf(update.oldMessageId)
        )

        is UpdateDeleteMessages -> removeMessages(
            chatId = update.chatId,
            messageIds = update.messageIds
        )

        else -> Unit
    }
}

internal fun TelegramSession.refreshReactionAvatars() {
    val users = _users.value

    histories.value.values.forEach { history ->
        history.mutate { message ->
            if (message.reactions.none { it.reactorIds.isNotEmpty() }) {
                message
            } else {
                message.copy(
                    reactions = message.reactions.map { reaction ->
                        reaction.copy(
                            reactorAvatars = reaction.reactorIds.mapNotNull { userId ->
                                users[userId]?.let(::userAvatar)
                            },
                            // Names arrive with the user, so the reactor list
                            // fills in as TDLib answers the user requests.
                            reactors = reaction.reactors.map { reactor ->
                                val user = users[reactor.id] ?: return@map reactor

                                reactor.copy(
                                    name = userDisplayName(user),
                                    avatar = userAvatar(user)
                                )
                            }
                        )
                    }
                )
            }
        }
    }
}

internal fun TelegramSession.refreshMessageSenders() {
    val users = _users.value

    histories.value.values.forEach { history ->
        history.mutate { message ->
            val user = if (message.isOutgoing) null else users[message.senderId]

            if (user == null) {
                message
            } else {
                message.copy(
                    senderName = userDisplayName(user),
                    senderAvatar = userAvatar(user),
                    senderEmojiStatusId = emojiStatusId(user.emojiStatus)
                )
            }
        }
    }
}

internal fun TelegramSession.updateMessageReactions(
    chatId: Long,
    messageId: Long,
    interactionInfo: MessageInteractionInfo?
) {
    histories.value[chatId.toString()]?.mutate { message ->
        if (message.messageId == messageId) {
            message.copy(
                reactions = mapReactions(interactionInfo),
                // New replies change the thread badge and the thread rail
                // without a reload of the message itself.
                replyCount = interactionInfo?.replyInfo?.replyCount ?: message.replyCount,
                viewCount = interactionInfo?.viewCount ?: 0,
                shareCount = interactionInfo?.forwardCount ?: 0
            )
        } else {
            message
        }
    }
}

internal fun TelegramSession.updateMessageReactions(
    chatId: Long,
    messageId: Long,
    reactions: Array<out MessageReaction>
) {
    histories.value[chatId.toString()]?.mutate { message ->
        if (message.messageId == messageId) {
            message.copy(reactions = mapReactions(reactions))
        } else {
            message
        }
    }
}

internal fun TelegramSession.upsertMessage(message: Message) {
    // A scheduled message is not part of the history yet; it waits in the
    // chat's schedule queue and is opened from the scheduled-messages dialog.
    if (message.schedulingState != null) return

    val content = message.content

    // The related UpdateChatBackground normally arrives separately, but
    // applying the service message's background immediately keeps a live
    // "set wallpaper" message and the chat background in sync.
    if (content is MessageChatSetBackground) {
        updateChat(message.chatId) { entry ->
            entry.copy(background = content.background)
        }
    }

    val key = message.chatId.toString()
    val history = histories.value[key]

    if (history == null) {
        // A message can arrive before the dialog was ever opened. Buffer
        // it so creating the controller does not lose it.
        pendingLiveMessages.getOrPut(key) { mutableListOf() }.apply {
            add(message)
            if (size > MaxPendingLiveMessages) removeAt(0)
        }
        return
    }

    history.merge(listOf(mapMessage(message)))
}

internal fun TelegramSession.replaceMessage(chatId: Long, oldMessageId: Long, message: Message) {
    val key = chatId.toString()
    val history = histories.value[key]

    if (history == null) {
        pendingLiveMessages[key]?.let { pending ->
            val index = pending.indexOfLast { it.id == oldMessageId }
            if (index >= 0) pending[index] = message else pending += message
        }
        return
    }

    history.replace(oldMessageId, mapMessage(message))
}

internal fun TelegramSession.updateMessageContent(chatId: Long, messageId: Long, content: MessageContent) {
    histories.value[chatId.toString()]?.mutate { message ->
        if (message.messageId == messageId) {
            message.copy(
                text = messageText(content),
                media = mapMedia(content),
                location = mapLocation(content),
                // A checklist task toggle arrives as a content update, so the
                // card has to be re-read here or the new state never shows.
                checklist = mapChecklist(content),
                isService = isServiceContent(content)
            )
        } else {
            message
        }
    }
}

internal fun TelegramSession.removeMessages(chatId: Long, messageIds: LongArray) {
    val ids = messageIds.toSet()
    pendingLiveMessages[chatId.toString()]?.removeAll { message -> message.id in ids }
    histories.value[chatId.toString()]?.remove(ids)
}

internal fun TelegramSession.handleChatReadOutbox(chatId: Long, lastReadOutboxMessageId: Long) {
    updateChat(chatId) { it.copy(lastReadOutboxMessageId = lastReadOutboxMessageId) }

    histories.value[chatId.toString()]?.mutate { message ->
        if (
            message.isOutgoing &&
            !message.isRead &&
            message.messageId <= lastReadOutboxMessageId
        ) {
            message.copy(isRead = true)
        } else {
            message
        }
    }
}

internal fun TelegramSession.upsertChat(chat: Chat) {
    _chats.update { it + (chat.id to ChatEntry.from(chat)) }

    when (val type = chat.type) {
        is ChatTypePrivate -> requestUser(type.userId)
        is ChatTypeSupergroup -> {
            requestMemberCount(chat)
            requestSupergroupVerification(type.supergroupId)
        }

        is ChatTypeBasicGroup -> requestMemberCount(chat)
        else -> Unit
    }

    emitDialogs()
}

internal fun TelegramSession.setMemberCount(key: String, memberCount: Int) {
    if (memberCount <= 0) return

    _memberCounts.update { it + (key to memberCount) }
    emitDialogs()
}

internal fun TelegramSession.handleChatAction(
    chatId: Long,
    senderId: MessageSender,
    action: ChatAction
) {
    // Actions of this account itself are reported too; they must not show
    // up as if somebody else was typing in the open chat.
    if (senderId is MessageSenderUser && senderId.userId == currentUserId) {
        return
    }

    val activityText = chatActionLabel(action)?.let { label ->
        actionTextFor(chatId, senderId, label)
    }

    typingJobs.remove(chatId)?.cancel()
    updateChat(chatId) { entry -> entry.copy(typingText = activityText) }

    if (activityText == null) return

    // TDLib does not repeat the action, so the hint has to expire on its
    // own when the person stops typing or leaves the app.
    typingJobs[chatId] = scope.launch {
        delay(TypingStatusTimeoutMillis)

        updateChat(chatId) { entry ->
            if (entry.typingText == activityText) {
                entry.copy(typingText = null)
            } else {
                entry
            }
        }
    }
}

internal fun TelegramSession.chatActionLabel(action: ChatAction): String? = when (action) {
    is ChatActionTyping -> "typing…"
    is ChatActionRecordingVoiceNote -> "recording a voice message…"
    is ChatActionUploadingVoiceNote -> "sending a voice message…"
    is ChatActionRecordingVideoNote -> "recording a video message…"
    is ChatActionUploadingVideoNote -> "sending a video message…"
    is ChatActionRecordingVideo -> "recording a video…"
    is ChatActionUploadingVideo -> "sending a video…"
    is ChatActionUploadingPhoto -> "sending a photo…"
    is ChatActionUploadingDocument -> "sending a file…"
    is ChatActionChoosingContact -> "sharing a contact…"
    is ChatActionChoosingLocation -> "sharing a location…"
    is ChatActionChoosingSticker -> "choosing a sticker…"
    is ChatActionStartPlayingGame -> "playing a game…"
    else -> null
}

internal fun TelegramSession.actionTextFor(
    chatId: Long,
    senderId: MessageSender,
    label: String
): String? {
    val entry = _chats.value[chatId] ?: return null
    val privateUserId = (entry.type as? ChatTypePrivate)?.userId

    return when (senderId) {
        is MessageSenderUser -> {
            // In private chats there is only one other person to name.
            if (privateUserId == senderId.userId) {
                label
            } else {
                val user = _users.value[senderId.userId]
                    ?: run {
                        requestUser(senderId.userId)
                        null
                    }

                user?.let { "${userDisplayName(it)} is $label" }
            }
        }

        is MessageSenderChat -> _chats.value[senderId.chatId]?.title
            ?.let { title -> "$title is $label" }
    }
}

internal fun TelegramSession.updateChat(chatId: Long, transform: (ChatEntry) -> ChatEntry) {
    _chats.update { chats ->
        val entry = chats[chatId] ?: return@update chats
        chats + (chatId to transform(entry))
    }

    emitDialogs()
}
