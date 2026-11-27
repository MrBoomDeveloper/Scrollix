package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.InputMessagePoll
import dev.g000sha256.tdl.dto.InputMessageVideoNote
import dev.g000sha256.tdl.dto.InputMessageVoiceNote
import dev.g000sha256.tdl.dto.InputPollOption
import dev.g000sha256.tdl.dto.InputPollTypeRegular
import dev.g000sha256.tdl.dto.InputVideoNote
import dev.g000sha256.tdl.dto.InputVoiceNote
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageDraft
import com.mrboomdev.scrollix.data.model.MessageScheduling
import com.mrboomdev.scrollix.data.model.MessageTextFormat
import com.mrboomdev.scrollix.data.model.OutgoingAttachment
import com.mrboomdev.scrollix.data.storage.CacheStore
import com.mrboomdev.scrollix.data.storage.readFileBytes

internal suspend fun TelegramSession.loadFileImpl(fileId: Int): Result<ByteArray> {
    if (fileId == 0) {
        return Result.failure(IllegalArgumentException("Invalid file id"))
    }

    fileCache.value[fileId]?.let { return Result.success(it) }

    val cacheKey = cacheKeyFor(fileId)

    if (cacheKey != null) {
        val cached = withContext(Dispatchers.IO) {
            CacheStore.readMedia(accountId, cacheKey)
        }

        if (cached != null && cached.isNotEmpty()) {
            fileCache.update { it + (fileId to cached) }
            return Result.success(cached)
        }
    }

    return try {
        when (val result = client.downloadFile(
            fileId = fileId,
            priority = 16,
            offset = 0,
            limit = 0,
            synchronous = true
        )) {
            is TdlResult.Success -> {
                val bytes = withContext(Dispatchers.IO) {
                    readFileBytes(result.result.local.path)
                }

                if (bytes != null && bytes.isNotEmpty()) {
                    fileCache.update { it + (fileId to bytes) }

                    val key = cacheKey
                        ?: result.result.remote.uniqueId.takeIf { it.isNotBlank() }

                    if (key != null) {
                        withContext(Dispatchers.IO) {
                            CacheStore.saveMedia(accountId, key, bytes)
                        }
                    }

                    registerDownload(fileId, bytes.size.toLong())
                    Result.success(bytes)
                } else {
                    Result.failure(Exception("File is not available"))
                }
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.loadCachedMediaImpl(remoteUniqueId: String): ByteArray? {
    if (remoteUniqueId.isBlank()) return null

    return withContext(Dispatchers.IO) {
        CacheStore.readMedia(accountId, remoteUniqueId)
    }
}

internal suspend fun TelegramSession.loadFileIfUniqueImpl(fileId: Int, uniqueId: String): Result<ByteArray> {
    // A stale file id from the cache can now belong to another file. Only
    // download it when the runtime id still resolves to the same content.
    if (cacheKeyFor(fileId) != uniqueId) {
        return Result.failure(IllegalStateException("File id is no longer valid"))
    }

    return loadFile(fileId)
}

internal suspend fun TelegramSession.loadMessageThreadImpl(providerDialogId: String, messageId: Long, limit: Int): Result<com.mrboomdev.scrollix.data.model.ChatMessageThread> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.getMessageThread(chatId, messageId)) {
            is TdlResult.Success -> {
                val info = result.result

                Result.success(
                    com.mrboomdev.scrollix.data.model.ChatMessageThread(
                        chatId = info.chatId.toString(),
                        messageThreadId = info.messageThreadId,
                        messages = info.messages
                            .map(::mapMessage)
                            .sortedBy { message -> message.messageId },
                        replyCount = info.replyInfo?.replyCount ?: 0,
                        unreadCount = info.unreadMessageCount
                    )
                )
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.sendMessageToThreadImpl(threadChatId: String, rootMessageId: Long, text: String, customEmojis: List<CustomEmojiSpan>, formatting: List<MessageTextFormat>): Result<Unit> {
    // Channel comments live in the discussion chat, so the caller passes
    // that chat id; replying to the root is what creates/continues a thread.
    return sendMessage(
        providerDialogId = threadChatId,
        text = text,
        replyToMessageId = rootMessageId,
        customEmojis = customEmojis,
        formatting = formatting
    )
}

internal suspend fun TelegramSession.sendMessageImpl(providerDialogId: String, text: String, replyToMessageId: Long?, customEmojis: List<CustomEmojiSpan>, formatting: List<MessageTextFormat>, scheduling: MessageScheduling? = null): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        return Result.failure(IllegalArgumentException("Message is empty"))
    }

    AppLog.protocol(
        "telegram",
        "out sendMessage chat=$chatId",
        "text=$trimmed reply=$replyToMessageId"
    )

    return try {
        when (val result = client.sendMessage(
            chatId = chatId,
            replyTo = replyToMessageId?.let { messageId ->
                InputMessageReplyToMessage(
                    messageId = messageId,
                    quote = null,
                    checklistTaskId = 0,
                    pollOptionId = ""
                )
            },
            options = scheduling.sendOptions(),
            inputMessageContent = InputMessageText(
                text = FormattedText(
                    text = trimmed,
                    entities = customEmojiEntities(text, customEmojis) +
                        formattingEntities(text, formatting)
                ),
                linkPreviewOptions = null,
                clearDraft = true
            )
        )) {
            is TdlResult.Success -> {
                upsertMessage(result.result)
                Result.success(Unit)
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.sendFileImpl(providerDialogId: String, path: String, asPhoto: Boolean): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    if (path.isBlank()) {
        return Result.failure(IllegalArgumentException("File path is empty"))
    }

    val content: InputMessageContent = if (asPhoto) {
        InputMessagePhoto(
            photo = InputPhoto(
                photo = InputFileLocal(path),
                thumbnail = null,
                video = null,
                addedStickerFileIds = intArrayOf(),
                width = 0,
                height = 0
            ),
            caption = null,
            showCaptionAboveMedia = false,
            selfDestructType = null,
            hasSpoiler = false
        )
    } else {
        InputMessageDocument(
            document = InputDocument(
                document = InputFileLocal(path),
                thumbnail = null,
                disableContentTypeDetection = false
            ),
            caption = null
        )
    }

    return try {
        when (val result = client.sendMessage(
            chatId = chatId,
            inputMessageContent = content
        )) {
            is TdlResult.Success -> {
                upsertMessage(result.result)
                Result.success(Unit)
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.sendVoiceNoteImpl(providerDialogId: String, path: String, durationSeconds: Int): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    if (path.isBlank()) {
        return Result.failure(IllegalArgumentException("Voice file path is empty"))
    }

    return sendContent(
        chatId = chatId,
        content = InputMessageVoiceNote(
            voiceNote = InputVoiceNote(
                voiceNote = InputFileLocal(path),
                duration = durationSeconds.coerceAtLeast(1),
                waveform = byteArrayOf()
            ),
            caption = FormattedText("", emptyArray()),
            selfDestructType = null
        )
    )
}

internal suspend fun TelegramSession.sendVideoNoteImpl(providerDialogId: String, path: String, durationSeconds: Int): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    if (path.isBlank()) {
        return Result.failure(IllegalArgumentException("Video file path is empty"))
    }

    return sendContent(
        chatId = chatId,
        content = InputMessageVideoNote(
            videoNote = InputVideoNote(
                videoNote = InputFileLocal(path),
                thumbnail = null,
                duration = durationSeconds.coerceIn(1, 60),
                length = 240
            ),
            selfDestructType = null
        )
    )
}

internal suspend fun TelegramSession.sendPollImpl(providerDialogId: String, question: String, options: List<String>, isAnonymous: Boolean, allowsMultipleAnswers: Boolean, allowsRevoting: Boolean, questionCustomEmojis: List<CustomEmojiSpan>, optionCustomEmojis: List<List<CustomEmojiSpan>>, optionMediaPaths: List<String?>, isQuiz: Boolean, correctOptionIndex: Int?, explanation: String): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    val cleanQuestion = question.trim()
    val cleanedOptions = options.mapIndexedNotNull { index, text ->
        val trimmed = text.trim()
        if (trimmed.isEmpty()) null else index to trimmed
    }

    if (cleanQuestion.isEmpty()) {
        return Result.failure(IllegalArgumentException("Poll question is empty"))
    }
    if (cleanedOptions.size < 2) {
        return Result.failure(IllegalArgumentException("A poll needs two options"))
    }

    // Blank options were dropped above, so the correct answer has to be
    // re-mapped onto the list that is actually sent.
    val quizAnswerIndex = if (isQuiz) {
        cleanedOptions.indexOfFirst { (originalIndex, _) ->
            originalIndex == correctOptionIndex
        }
    } else {
        -1
    }

    if (isQuiz && quizAnswerIndex < 0) {
        return Result.failure(IllegalArgumentException("A quiz needs a correct answer"))
    }

    return sendContent(
        chatId = chatId,
        content = InputMessagePoll(
            question = FormattedText(
                text = cleanQuestion,
                entities = customEmojiEntities(question, questionCustomEmojis)
            ),
            options = cleanedOptions.map { (originalIndex, text) ->
                val mediaPath = optionMediaPaths
                    .getOrNull(originalIndex)
                    ?.takeIf(String::isNotBlank)

                InputPollOption(
                    text = FormattedText(
                        text = text,
                        entities = customEmojiEntities(
                            options[originalIndex],
                            optionCustomEmojis.getOrNull(originalIndex).orEmpty()
                        )
                    ),
                    media = mediaPath?.let { path ->
                        InputPollMediaPhoto(
                            photo = InputPhoto(
                                photo = InputFileLocal(path),
                                thumbnail = null,
                                video = null,
                                addedStickerFileIds = intArrayOf(),
                                width = 0,
                                height = 0
                            )
                        )
                    }
                )
            }.toTypedArray(),
            description = FormattedText("", emptyArray()),
            media = null,
            isAnonymous = isAnonymous,
            // Telegram quizzes are single-answer and cannot be re-voted.
            allowsMultipleAnswers = allowsMultipleAnswers && !isQuiz,
            allowsRevoting = if (isQuiz) false else allowsRevoting,
            membersOnly = false,
            countryCodes = emptyArray(),
            shuffleOptions = false,
            hideResultsUntilCloses = false,
            type = if (isQuiz) {
                InputPollTypeQuiz(
                    correctOptionIds = intArrayOf(quizAnswerIndex),
                    explanation = FormattedText(explanation.trim(), emptyArray()),
                    explanationMedia = null
                )
            } else {
                InputPollTypeRegular(allowAddingOptions = false)
            },
            openPeriod = 0,
            closeDate = 0,
            isClosed = false
        )
    )
}

internal suspend fun TelegramSession.sendLocationImpl(providerDialogId: String, latitude: Double, longitude: Double, livePeriodSeconds: Int): Result<Long> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
        return Result.failure(IllegalArgumentException("Invalid coordinates"))
    }

    val location = Location(
        latitude = latitude,
        longitude = longitude,
        horizontalAccuracy = 0.0
    )

    // TDLib 17 has no live period on static locations: live shares are a
    // different message content and are stopped by editing the message.
    val content: InputMessageContent = if (livePeriodSeconds > 0) {
        InputMessageLiveLocation(
            location = LiveLocation(
                location = location,
                livePeriod = livePeriodSeconds,
                heading = 0,
                proximityAlertRadius = 0
            )
        )
    } else {
        InputMessageLocation(location = location)
    }

    return try {
        when (val result = client.sendMessage(
            chatId = chatId,
            inputMessageContent = content
        )) {
            is TdlResult.Success -> {
                upsertMessage(result.result)
                Result.success(result.result.id)
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.stopLiveLocationImpl(providerDialogId: String, messageId: Long): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        // A null location is TDLib's way of ending a live share.
        when (val result = client.editMessageLiveLocation(
            chatId = chatId,
            messageId = messageId,
            replyMarkup = null,
            location = null
        )) {
            is TdlResult.Success -> {
                upsertMessage(result.result)
                Result.success(Unit)
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.sendFilesImpl(providerDialogId: String, attachments: List<OutgoingAttachment>, caption: String, replyToMessageId: Long?, customEmojis: List<CustomEmojiSpan>, formatting: List<MessageTextFormat>, scheduling: MessageScheduling? = null): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    if (attachments.isEmpty()) {
        return Result.failure(IllegalArgumentException("No attachments selected"))
    }

    if (attachments.size > 10) {
        return Result.failure(IllegalArgumentException("An album can contain at most 10 files"))
    }

    AppLog.protocol(
        "telegram",
        "out sendFiles chat=$chatId",
        "count=${attachments.size} caption=${caption.trim()}"
    )

    val trimmedCaption = caption.trim()
    val replyTo = replyToMessageId?.let { messageId ->
        InputMessageReplyToMessage(
            messageId = messageId,
            quote = null,
            checklistTaskId = 0,
            pollOptionId = ""
        )
    }

    val contents = attachments.mapIndexed { index, attachment ->
        val fileCaption = if (index == 0 && trimmedCaption.isNotEmpty()) {
            FormattedText(
                text = trimmedCaption,
                entities = customEmojiEntities(caption, customEmojis) +
                    formattingEntities(caption, formatting)
            )
        } else {
            null
        }

        if (attachment.asPhoto) {
            InputMessagePhoto(
                photo = InputPhoto(
                    photo = InputFileLocal(attachment.path),
                    thumbnail = null,
                    video = null,
                    addedStickerFileIds = intArrayOf(),
                    width = 0,
                    height = 0
                ),
                caption = fileCaption,
                showCaptionAboveMedia = false,
                selfDestructType = null,
                hasSpoiler = false
            )
        } else {
            InputMessageDocument(
                document = InputDocument(
                    document = InputFileLocal(attachment.path),
                    thumbnail = null,
                    disableContentTypeDetection = false
                ),
                caption = fileCaption
            )
        }
    }

    return try {
        if (contents.size == 1) {
            when (val result = client.sendMessage(
                chatId = chatId,
                replyTo = replyTo,
                options = scheduling.sendOptions(),
                inputMessageContent = contents.single()
            )) {
                is TdlResult.Success -> {
                    upsertMessage(result.result)
                    Result.success(Unit)
                }

                is TdlResult.Failure -> Result.failure(result.toSessionException())
            }
        } else {
            when (val result = client.sendMessageAlbum(
                chatId = chatId,
                replyTo = replyTo,
                options = scheduling.sendOptions(),
                inputMessageContents = contents.toTypedArray()
            )) {
                is TdlResult.Success -> {
                    result.result.messages.filterNotNull().forEach(::upsertMessage)
                    Result.success(Unit)
                }

                is TdlResult.Failure -> Result.failure(result.toSessionException())
            }
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.forwardMessagesImpl(
    providerDialogId: String,
    messageIds: LongArray,
    targetDialogId: String,
    hideSender: Boolean = false,
    removeCaption: Boolean = false
): Result<Unit> {
    val fromChatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))
    val targetChatId = targetDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid target dialog id"))

    if (messageIds.isEmpty()) return Result.success(Unit)

    return try {
        when (val result = client.forwardMessages(
            chatId = targetChatId,
            fromChatId = fromChatId,
            messageIds = messageIds.sortedArray(),
            // TDLib copies the content instead of referencing the original
            // message when sendCopy is set, which is what "hide sender" means.
            sendCopy = hideSender,
            removeCaption = removeCaption
        )) {
            is TdlResult.Success -> Result.success(Unit)
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.messageLinkImpl(
    providerDialogId: String,
    messageId: Long
): Result<String> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.getMessageLink(
            chatId = chatId,
            messageId = messageId,
            mediaTimestamp = 0,
            checklistTaskId = 0,
            pollOptionId = "",
            forAlbum = false,
            inMessageThread = false
        )) {
            is TdlResult.Success -> Result.success(result.result.link)
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.deleteMessagesImpl(
    providerDialogId: String,
    messageIds: LongArray,
    forEveryone: Boolean = true
): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    if (messageIds.isEmpty()) return Result.success(Unit)

    AppLog.protocol(
        "telegram",
        "out deleteMessages chat=$chatId",
        "ids=${messageIds.joinToString(",")}"
    )

    return try {
        when (val result = client.deleteMessages(
            chatId = chatId,
            messageIds = messageIds,
            revoke = forEveryone
        )) {
            is TdlResult.Success -> {
                removeMessages(chatId, messageIds)
                Result.success(Unit)
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.setMessagePinnedImpl(providerDialogId: String, messageId: Long, isPinned: Boolean): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        val result = if (isPinned) {
            client.pinChatMessage(
                chatId = chatId,
                messageId = messageId,
                disableNotification = false,
                onlyForSelf = false
            )
        } else {
            client.unpinChatMessage(
                chatId = chatId,
                messageId = messageId
            )
        }

        when (result) {
            is TdlResult.Success -> {
                _pinnedMessageIds.update { pinned ->
                    if (isPinned) pinned + (chatId to messageId) else pinned - chatId
                }
                emitDialogs()
                Result.success(Unit)
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.loadPinnedMessageIdImpl(providerDialogId: String): Result<Long?> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.getChatPinnedMessage(chatId)) {
            is TdlResult.Success -> {
                val messageId = result.result.id
                _pinnedMessageIds.update { it + (chatId to messageId) }
                emitDialogs()
                Result.success(messageId.takeIf { it > 0 })
            }

            is TdlResult.Failure -> {
                _pinnedMessageIds.update { it - chatId }
                emitDialogs()
                Result.success(null)
            }
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.addContactImpl(userId: Long, firstName: String, lastName: String): Result<Unit> {
    return try {
        // TDLib adds a user to the contact list through an imported
        // contact, which is keyed by the phone number when the profile
        // exposes one. Without it the name is still stored locally.
        val phoneNumber = _users.value[userId]?.phoneNumber.orEmpty()

        when (
            val result = client.addContact(
                userId = userId,
                contact = dev.g000sha256.tdl.dto.ImportedContact(
                    phoneNumber = phoneNumber,
                    firstName = firstName,
                    lastName = lastName,
                    note = dev.g000sha256.tdl.dto.FormattedText(
                        text = "",
                        entities = emptyArray()
                    )
                ),
                sharePhoneNumber = false
            )
        ) {
            is TdlResult.Success -> Result.success(Unit)
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.removeContactImpl(userId: Long): Result<Unit> {
    return try {
        when (val result = client.removeContacts(longArrayOf(userId))) {
            is TdlResult.Success -> Result.success(Unit)
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.setUserBlockedImpl(userId: Long, blocked: Boolean): Result<Unit> {
    return try {
        when (val result = client.setMessageSenderBlockList(
            senderId = MessageSenderUser(userId),
            blockList = if (blocked) BlockListMain() else null
        )) {
            is TdlResult.Success -> Result.success(Unit)
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.votePollImpl(providerDialogId: String, messageId: Long, optionIds: List<String>): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        val numericOptionIds = IntArray(optionIds.size) { index ->
            optionIds[index].toIntOrNull()
                ?: return Result.failure(
                    IllegalArgumentException("Unsupported poll option id")
                )
        }

        when (val result = client.setPollAnswer(chatId, messageId, numericOptionIds)) {
            is TdlResult.Success -> {
                refreshMessage(chatId, messageId)
                Result.success(Unit)
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.stopPollImpl(
    providerDialogId: String,
    messageId: Long
): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.stopPoll(
            chatId = chatId,
            messageId = messageId,
            replyMarkup = null
        )) {
            is TdlResult.Success -> {
                // The closed state arrives with the refreshed message, so the
                // card stops offering votes without waiting for an update.
                refreshMessage(chatId, messageId)
                Result.success(Unit)
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.loadMessageImpl(providerDialogId: String, messageId: Long): Result<ChatMessage?> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.getMessage(
            chatId = chatId,
            messageId = messageId
        )) {
            is TdlResult.Success -> Result.success(mapMessage(result.result))
            is TdlResult.Failure -> Result.success(null)
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.markMessagesReadImpl(providerDialogId: String, messageIds: LongArray) {
    val chatId = providerDialogId.toLongOrNull() ?: return
    if (messageIds.isEmpty()) return

    try {
        client.viewMessages(
            chatId = chatId,
            messageIds = messageIds,
            forceRead = true
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
    }
}

internal suspend fun TelegramSession.loadDraftImpl(providerDialogId: String): Result<MessageDraft> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        val chat = when (val result = client.getChat(chatId)) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> return Result.failure(result.toSessionException())
        }

        val content = chat.draftMessage?.content as? DraftMessageContentText
            ?: return Result.success(MessageDraft())

        Result.success(
            MessageDraft(
                text = content.text.text,
                customEmojis = customEmojiSpans(content.text),
                formatting = textFormats(content.text)
            )
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.saveDraftImpl(providerDialogId: String, text: String, customEmojis: List<CustomEmojiSpan>, formatting: List<MessageTextFormat>): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        // An empty draft content clears the server-side draft.
        val content = DraftMessageContentText(
            text = FormattedText(
                text = text,
                entities = customEmojiEntities(text, customEmojis, trim = false) +
                    formattingEntities(text, formatting, trim = false)
            ),
            linkPreviewOptions = null
        )

        when (val result = client.setChatDraftMessage(
            chatId = chatId,
            draftMessage = DraftMessage(
                date = 0,
                replyTo = null,
                content = content,
                effectId = 0,
                suggestedPostInfo = null
            )
        )) {
            is TdlResult.Success -> Result.success(Unit)
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

