package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.AccountProfile
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.AuthAction
import com.mrboomdev.scrollix.data.model.AuthChallenge
import com.mrboomdev.scrollix.data.model.AuthField
import com.mrboomdev.scrollix.data.model.AuthFieldType
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.ChatWallpaper
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat
import com.mrboomdev.scrollix.data.model.DialogFolderPosition
import com.mrboomdev.scrollix.data.model.DialogIdentity
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageReactionInfo
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.model.parseFolderCustomEmojis
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.PlatformStorage
import com.mrboomdev.scrollix.data.storage.formatPhoneNumber
import com.mrboomdev.scrollix.data.storage.formatTime
import com.mrboomdev.scrollix.data.storage.readFileBytes
import com.mrboomdev.scrollix.generated.BuildConfig
import com.mrboomdev.scrollix.getPlatform
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TelegramAccountSession(
    account: AccountRecord,
    private val storage: PlatformStorage,
    parentScope: CoroutineScope
) : AccountSession {
    override val accountId: String = account.id
    override val providerId: String = TelegramProvider.id

    private val client = TdlClient.create()
    private val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job])
    )
    private val historyPageLoader = HistoryPageLoader<Message>(idOf = { it.id })
    private val historyMutex = Mutex()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _profile = MutableStateFlow<AccountProfile?>(null)
    override val profile: StateFlow<AccountProfile?> = _profile.asStateFlow()

    private val _dialogs = MutableStateFlow<List<Dialog>>(emptyList())
    override val dialogs: StateFlow<List<Dialog>> = _dialogs.asStateFlow()

    private val _folders = MutableStateFlow<List<DialogFolder>>(emptyList())
    override val folders: StateFlow<List<DialogFolder>> = _folders.asStateFlow()

    private val _isLoadingDialogs = MutableStateFlow(false)
    override val isLoadingDialogs: StateFlow<Boolean> = _isLoadingDialogs.asStateFlow()

    private val messagesByChat = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    private val avatarCache = MutableStateFlow<Map<Int, ByteArray>>(emptyMap())
    private val avatarRequests = MutableStateFlow<Set<Int>>(emptySet())
    private val fileCache = MutableStateFlow<Map<Int, ByteArray>>(emptyMap())
    private val customEmojiCache = mutableMapOf<Long, Sticker>()
    private val customEmojiCacheMutex = Mutex()
    private var folderEmojiJob: Job? = null

    private val stateMutex = Mutex()
    private val folderChatsMutex = Mutex()
    private val loadedFolderChats = mutableSetOf<Int>()
    private val loadingFolderChats = mutableSetOf<Int>()
    private val _chats = MutableStateFlow<Map<Long, ChatEntry>>(emptyMap())
    private val _users = MutableStateFlow<Map<Long, User>>(emptyMap())
    private val _userStatuses = MutableStateFlow<Map<Long, UserStatus>>(emptyMap())
    private val _requestedUsers = MutableStateFlow<Set<Long>>(emptySet())

    private var started = false
    private var parametersSet = false
    private var ready = false
    private var closed = false
    private var currentChallenge: AuthChallenge? = null
    private var currentUserId: Long? = null

    override fun messages(providerDialogId: String): StateFlow<List<ChatMessage>> {
        return messagesByChat
            .map { it[providerDialogId].orEmpty() }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = messagesByChat.value[providerDialogId].orEmpty()
            )
    }

    override suspend fun loadMessages(providerDialogId: String, limit: Int): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

        return historyMutex.withLock {
            loadLatestMessagesLocked(
                providerDialogId = providerDialogId,
                chatId = chatId,
                limit = limit
            )
        }
    }

    override suspend fun loadOlderMessages(providerDialogId: String, limit: Int): Result<Boolean> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

        return historyMutex.withLock {
            try {
                val existing = messagesByChat.value[providerDialogId].orEmpty()

                if (existing.isEmpty()) {
                    return@withLock loadLatestMessagesLocked(
                        providerDialogId = providerDialogId,
                        chatId = chatId,
                        limit = limit
                    ).map { true }
                }

                val oldestMessageId = existing.minOf { it.messageId }
                val result = historyPageLoader.load(
                    fromMessageId = oldestMessageId,
                    limit = limit,
                    accept = { message -> message.id < oldestMessageId }
                ) { fromMessageId, pageLimit ->
                    fetchChatHistory(
                        chatId = chatId,
                        fromMessageId = fromMessageId,
                        limit = pageLimit
                    )
                }

                val olderMessages = result.items.map(::mapMessage)

                if (olderMessages.isNotEmpty()) {
                    messagesByChat.update { messages ->
                        messages + (
                            providerDialogId to mergeMessages(
                                messages[providerDialogId].orEmpty() + olderMessages
                            )
                        )
                    }
                }

                Result.success(result.hasMore)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }
    }

    private suspend fun loadLatestMessagesLocked(
        providerDialogId: String,
        chatId: Long,
        limit: Int
    ): Result<Unit> {
        return try {
            val result = historyPageLoader.load(
                fromMessageId = 0,
                limit = limit
            ) { fromMessageId, pageLimit ->
                fetchChatHistory(
                    chatId = chatId,
                    fromMessageId = fromMessageId,
                    limit = pageLimit
                )
            }

            val messages = result.items.map(::mapMessage)

            messagesByChat.update { existing ->
                existing + (
                    providerDialogId to mergeMessages(
                        existing[providerDialogId].orEmpty() + messages
                    )
                )
            }

            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private suspend fun fetchChatHistory(
        chatId: Long,
        fromMessageId: Long,
        limit: Int
    ): List<Message> {
        return when (val result = client.getChatHistory(
            chatId = chatId,
            fromMessageId = fromMessageId,
            offset = 0,
            limit = limit.coerceIn(1, 100),
            onlyLocal = false
        )) {
            is TdlResult.Success -> result.result.messages.filterNotNull()
            is TdlResult.Failure -> throw Exception(result.message)
        }
    }

    override suspend fun loadFile(fileId: Int): Result<ByteArray> {
        if (fileId == 0) {
            return Result.failure(IllegalArgumentException("Invalid file id"))
        }

        fileCache.value[fileId]?.let { return Result.success(it) }

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
                        Result.success(bytes)
                    } else {
                        Result.failure(Exception("File is not available"))
                    }
                }

                is TdlResult.Failure -> Result.failure(Exception(result.message))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun sendMessage(providerDialogId: String, text: String): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message is empty"))
        }

        return try {
            when (val result = client.sendMessage(
                chatId = chatId,
                inputMessageContent = InputMessageText(
                    text = FormattedText(
                        text = trimmed,
                        entities = emptyArray<TextEntity>()
                    ),
                    linkPreviewOptions = null,
                    clearDraft = true
                )
            )) {
                is TdlResult.Success -> {
                    upsertMessage(result.result)
                    Result.success(Unit)
                }

                is TdlResult.Failure -> Result.failure(Exception(result.message))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun sendFile(
        providerDialogId: String,
        path: String,
        asPhoto: Boolean
    ): Result<Unit> {
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

                is TdlResult.Failure -> Result.failure(Exception(result.message))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun editMessage(
        providerDialogId: String,
        messageId: Long,
        text: String
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message is empty"))
        }

        return try {
            when (val result = client.editMessageText(
                chatId = chatId,
                messageId = messageId,
                inputMessageContent = InputMessageText(
                    text = FormattedText(
                        text = trimmed,
                        entities = emptyArray<TextEntity>()
                    ),
                    linkPreviewOptions = null,
                    clearDraft = false
                )
            )) {
                is TdlResult.Success -> {
                    upsertMessage(result.result)
                    Result.success(Unit)
                }

                is TdlResult.Failure -> Result.failure(Exception(result.message))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun setDialogPinned(
        providerDialogId: String,
        isPinned: Boolean,
        folderId: Int?
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

        val chatList = folderId?.let { folder -> ChatListFolder(folder) } ?: ChatListMain()

        return try {
            when (val result = client.toggleChatIsPinned(
                chatList = chatList,
                chatId = chatId,
                isPinned = isPinned
            )) {
                is TdlResult.Success -> {
                    // Re-read the chat so the pinned state comes back from the server.
                    when (val chat = client.getChat(chatId)) {
                        is TdlResult.Success -> upsertChat(chat.result)
                        is TdlResult.Failure -> Unit
                    }

                    Result.success(Unit)
                }

                is TdlResult.Failure -> Result.failure(Exception(result.message))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun loadUserProfile(
        providerDialogId: String,
        userId: Long?
    ): Result<UserProfileDetails> {
        return try {
            val resolvedUserId = userId ?: run {
                val chatId = providerDialogId.toLongOrNull()
                    ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

                val chat = when (val result = client.getChat(chatId)) {
                    is TdlResult.Success -> result.result
                    is TdlResult.Failure -> return Result.failure(Exception(result.message))
                }

                (chat.type as? ChatTypePrivate)?.userId
                    ?: return Result.success(UserProfileDetails())
            }

            val user = when (val result = client.getUser(resolvedUserId)) {
                is TdlResult.Success -> result.result
                is TdlResult.Failure -> return Result.failure(Exception(result.message))
            }

            val fullInfo = when (val result = client.getUserFullInfo(resolvedUserId)) {
                is TdlResult.Success -> result.result
                is TdlResult.Failure -> null
            }

            Result.success(
                UserProfileDetails(
                    displayName = userDisplayName(user),
                    avatar = userAvatar(user),
                    username = user.usernames?.activeUsernames?.firstOrNull(),
                    phoneNumber = user.phoneNumber.ifBlank { null },
                    bio = fullInfo?.bio?.text?.ifBlank { null },
                    birthdate = fullInfo?.birthdate?.let(::formatBirthdate),
                    status = userStatusText(user)
                )
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun formatBirthdate(birthdate: Birthdate): String {
        val months = listOf(
            "January",
            "February",
            "March",
            "April",
            "May",
            "June",
            "July",
            "August",
            "September",
            "October",
            "November",
            "December"
        )

        val day = birthdate.day.takeIf { it > 0 }
        val month = months.getOrNull(birthdate.month - 1)
        val year = birthdate.year.takeIf { it > 0 }

        return listOfNotNull(day?.toString(), month, year?.toString())
            .joinToString(" ")
    }

    override suspend fun searchMessages(
        providerDialogId: String,
        query: String,
        limit: Int
    ): Result<List<ChatMessage>> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())

        return try {
            when (val result = client.searchChatMessages(
                chatId = chatId,
                query = trimmed,
                fromMessageId = 0,
                offset = 0,
                limit = limit.coerceIn(1, 100),
                filter = SearchMessagesFilterEmpty()
            )) {
                is TdlResult.Success -> Result.success(
                    result.result.messages
                        .map(::mapMessage)
                        .sortedByDescending { it.messageId }
                )

                is TdlResult.Failure -> Result.failure(Exception(result.message))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun markMessagesRead(providerDialogId: String, messageIds: LongArray) {
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

    override suspend fun start() {
        if (started || closed) return
        started = true

        scope.launch {
            try {
                client.allUpdates.collect { update ->
                    if (!closed) {
                        handleUpdate(update)
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!closed) {
                    stateMutex.withLock {
                        setError(exception.message ?: "TDLib update stream failed")
                    }
                }
            }
        }

        when (val result = client.getAuthorizationState()) {
            is TdlResult.Success -> {
                if (!closed) {
                    handleAuthorizationState(result.result)
                }
            }

            is TdlResult.Failure -> {
                stateMutex.withLock {
                    setError("Failed to get Telegram authorization state: ${result.message}")
                }
            }
        }
    }

    override suspend fun submitAuth(values: Map<String, String>) {
        val challenge = stateMutex.withLock {
            val current = currentChallenge ?: return
            val submitting = current.copy(
                isSubmitting = true,
                error = null
            )

            currentChallenge = submitting
            _authState.value = AuthState.Challenge(submitting)
            submitting
        }

        val phoneNumber = normalizePhoneNumber(values["phone"].orEmpty())

        val result = when (challenge.id) {
            "phone" -> {
                if (phoneNumber == null) {
                    setChallengeError(
                        id = challenge.id,
                        message = "Enter the phone number in international format, starting with +. " +
                            "Example: +12345678900."
                    )
                    return
                }

                client.setAuthenticationPhoneNumber(phoneNumber = phoneNumber)
            }

            "code" -> client.checkAuthenticationCode(
                code = values["code"].orEmpty().trim()
            )

            "password" -> client.checkAuthenticationPassword(
                password = values["password"].orEmpty()
            )

            "password_recovery_code" -> client.checkAuthenticationPasswordRecoveryCode(
                recoveryCode = values["code"].orEmpty().trim()
            )

            "email" -> client.setAuthenticationEmailAddress(
                emailAddress = values["email"].orEmpty().trim()
            )

            "email_code" -> client.checkAuthenticationEmailCode(
                code = EmailAddressAuthenticationCode(
                    code = values["code"].orEmpty().trim()
                )
            )

            "registration" -> client.registerUser(
                firstName = values["first_name"].orEmpty().trim(),
                lastName = values["last_name"].orEmpty().trim(),
                disableNotification = false
            )

            else -> return
        }

        stateMutex.withLock {
            val current = currentChallenge
            if (current?.id == challenge.id) {
                val updated = current.copy(
                    isSubmitting = false,
                    error = (result as? TdlResult.Failure)?.message,
                    description = if (
                        challenge.id == "phone" &&
                        result is TdlResult.Success
                    ) {
                        "Code requested. Check Telegram on another device or your SMS messages. " +
                            "If it doesn't arrive, wait a moment and tap Resend code."
                    } else {
                        current.description
                    }
                )

                currentChallenge = updated
                _authState.value = AuthState.Challenge(updated)
            }
        }
    }

    override suspend fun performAuthAction(action: AuthAction) {
        val challenge = currentChallenge ?: return

        val result = when (action) {
            AuthAction.ResendCode -> client.resendAuthenticationCode(
                reason = ResendCodeReasonUserRequest()
            )
            AuthAction.RecoverPassword -> client.requestAuthenticationPasswordRecovery()
            AuthAction.UseQrCode -> client.requestQrCodeAuthentication(
                otherUserIds = longArrayOf()
            )
            AuthAction.Submit, is AuthAction.OpenUrl -> return
        }

        when (result) {
            is TdlResult.Success -> {
                if (action == AuthAction.RecoverPassword && challenge.id == "password") {
                    stateMutex.withLock {
                        if (currentChallenge?.id == "password") {
                            val recovery = AuthChallenge(
                                id = "password_recovery_code",
                                title = "Recovery code",
                                description = "Enter the recovery code sent to your email address.",
                                fields = listOf(
                                    AuthField(
                                        id = "code",
                                        label = "Recovery code",
                                        type = AuthFieldType.Code
                                    )
                                ),
                                actions = listOf(AuthAction.Submit)
                            )

                            currentChallenge = recovery
                            _authState.value = AuthState.Challenge(recovery)
                        }
                    }
                } else {
                    stateMutex.withLock {
                        val current = currentChallenge
                        if (current?.id == challenge.id) {
                            val updated = current.copy(
                                isSubmitting = false,
                                error = null
                            )

                            currentChallenge = updated
                            _authState.value = AuthState.Challenge(updated)
                        }
                    }
                }
            }

            is TdlResult.Failure -> {
                stateMutex.withLock {
                    val current = currentChallenge
                    if (current?.id == challenge.id) {
                        val updated = current.copy(
                            isSubmitting = false,
                            error = result.message
                        )

                        currentChallenge = updated
                        _authState.value = AuthState.Challenge(updated)
                    }
                }
            }
        }
    }

    override suspend fun logout() {
        if (closed) return

        if (ready) {
            runCatching { client.logOut() }
        }

        close()
    }

    override suspend fun close() {
        if (closed) return
        closed = true

        runCatching { client.close() }
        scope.cancel()
        _isLoadingDialogs.value = false
        _folders.value = emptyList()
        _authState.value = AuthState.Closed
    }

    private suspend fun handleUpdate(update: Update) {
        when (update) {
            is UpdateAuthorizationState -> handleAuthorizationState(update.authorizationState)

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

            is UpdateChatReadOutbox -> handleChatReadOutbox(
                chatId = update.chatId,
                lastReadOutboxMessageId = update.lastReadOutboxMessageId
            )

            is UpdateChatPermissions -> updateChat(update.chatId) {
                it.copy(permissions = update.permissions)
            }

            is UpdateChatBackground -> {
                updateChat(update.chatId) { entry ->
                    entry.copy(background = update.background)
                }
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

                emitDialogs()
            }

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

    private suspend fun handleAuthorizationState(state: AuthorizationState) {
        stateMutex.withLock {
            when (state) {
                is AuthorizationStateWaitTdlibParameters -> {
                    if (parametersSet) return@withLock
                    parametersSet = true

                    if (BuildConfig.TELEGRAM_API_ID <= 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
                        setError(
                            "Telegram API credentials are missing. " +
                                "Create local.properties from local.properties.example and rebuild."
                        )
                        return@withLock
                    }

                    val databaseDirectory = storage.ensureDirectory(
                        "accounts/$accountId/tdlib/db"
                    )
                    val filesDirectory = storage.ensureDirectory(
                        "accounts/$accountId/tdlib/files"
                    )

                    when (val result = client.setTdlibParameters(
                        useTestDc = false,
                        databaseDirectory = databaseDirectory,
                        filesDirectory = filesDirectory,
                        databaseEncryptionKey = ByteArray(0),
                        useFileDatabase = true,
                        useChatInfoDatabase = true,
                        useMessageDatabase = true,
                        useSecretChats = true,
                        apiId = BuildConfig.TELEGRAM_API_ID,
                        apiHash = BuildConfig.TELEGRAM_API_HASH,
                        systemLanguageCode = "en",
                        deviceModel = "Scrollix",
                        systemVersion = getPlatform().name,
                        applicationVersion = "1.0.0"
                    )) {
                        is TdlResult.Success -> Unit
                        is TdlResult.Failure -> setError(
                            "Failed to configure TDLib: ${result.message}"
                        )
                    }
                }

                is AuthorizationStateWaitPhoneNumber -> {
                    setChallenge(
                        AuthChallenge(
                            id = "phone",
                            title = "Your phone number",
                            description = "Enter your phone number in international format, starting with +. " +
                                "Example: +12345678900. Telegram may send the code to your Telegram app " +
                                "on another device instead of SMS.",
                            fields = listOf(
                                AuthField(
                                    id = "phone",
                                    label = "Phone number",
                                    type = AuthFieldType.Phone,
                                    placeholder = "+12345678900"
                                )
                            ),
                            actions = listOf(AuthAction.Submit, AuthAction.UseQrCode)
                        )
                    )
                }

                is AuthorizationStateWaitCode -> {
                    setChallenge(
                        AuthChallenge(
                            id = "code",
                            title = "Enter the code",
                            description = codeDeliveryDescription(state),
                            fields = listOf(
                                AuthField(
                                    id = "code",
                                    label = "Code",
                                    type = AuthFieldType.Code
                                )
                            ),
                            actions = listOf(
                                AuthAction.Submit,
                                AuthAction.ResendCode,
                                AuthAction.UseQrCode
                            )
                        )
                    )
                }

                is AuthorizationStateWaitPassword -> {
                    setChallenge(
                        AuthChallenge(
                            id = "password",
                            title = "Two-step verification",
                            description = state.passwordHint.ifBlank {
                                "Enter your two-step verification password."
                            },
                            fields = listOf(
                                AuthField(
                                    id = "password",
                                    label = "Password",
                                    type = AuthFieldType.Password
                                )
                            ),
                            actions = buildList {
                                add(AuthAction.Submit)
                                if (state.hasRecoveryEmailAddress) {
                                    add(AuthAction.RecoverPassword)
                                }
                            }
                        )
                    )
                }

                is AuthorizationStateWaitEmailAddress -> {
                    setChallenge(
                        AuthChallenge(
                            id = "email",
                            title = "Your email address",
                            description = "Enter the email address for this Telegram account.",
                            fields = listOf(
                                AuthField(
                                    id = "email",
                                    label = "Email address",
                                    type = AuthFieldType.Email
                                )
                            ),
                            actions = listOf(AuthAction.Submit)
                        )
                    )
                }

                is AuthorizationStateWaitEmailCode -> {
                    setChallenge(
                        AuthChallenge(
                            id = "email_code",
                            title = "Enter the email code",
                            description = "We sent a code to ${state.codeInfo.emailAddressPattern}.",
                            fields = listOf(
                                AuthField(
                                    id = "code",
                                    label = "Code",
                                    type = AuthFieldType.Code
                                )
                            ),
                            actions = listOf(AuthAction.Submit, AuthAction.ResendCode)
                        )
                    )
                }

                is AuthorizationStateWaitRegistration -> {
                    setChallenge(
                        AuthChallenge(
                            id = "registration",
                            title = "Create your account",
                            description = state.termsOfService.text.text,
                            fields = listOf(
                                AuthField(
                                    id = "first_name",
                                    label = "First name",
                                    type = AuthFieldType.FirstName
                                ),
                                AuthField(
                                    id = "last_name",
                                    label = "Last name (optional)",
                                    type = AuthFieldType.LastName
                                )
                            ),
                            actions = listOf(AuthAction.Submit)
                        )
                    )
                }

                is AuthorizationStateWaitOtherDeviceConfirmation -> {
                    setChallenge(
                        AuthChallenge(
                            id = "other_device",
                            title = "Confirm on another device",
                            description = "Scan this QR code with Telegram on a device where you are already logged in. " +
                                "The code refreshes automatically.",
                            actions = listOf(AuthAction.OpenUrl(state.link)),
                            qrLink = state.link
                        )
                    )
                }

                is AuthorizationStateWaitPremiumPurchase -> {
                    setChallenge(
                        AuthChallenge(
                            id = "premium",
                            title = "Telegram Premium required",
                            description = "This account needs Telegram Premium before it can be used. " +
                                "Contact ${state.supportEmailAddress} for help."
                        )
                    )
                }

                is AuthorizationStateReady -> {
                    val wasReady = ready
                    ready = true
                    currentChallenge = null
                    _authState.value = AuthState.Ready

                    if (!wasReady) {
                        loadProfileAndChats()
                    }
                }

                is AuthorizationStateClosed -> {
                    ready = false
                    currentChallenge = null
                    _authState.value = AuthState.Closed
                }

                is AuthorizationStateClosing, is AuthorizationStateLoggingOut -> {
                    ready = false
                    _authState.value = AuthState.Initializing
                }

            }
        }
    }

    private suspend fun setChallengeError(id: String, message: String) {
        stateMutex.withLock {
            val current = currentChallenge
            if (current?.id == id) {
                val updated = current.copy(
                    isSubmitting = false,
                    error = message
                )

                currentChallenge = updated
                _authState.value = AuthState.Challenge(updated)
            }
        }
    }

    private fun codeDeliveryDescription(state: AuthorizationStateWaitCode): String {
        val phoneNumber = state.codeInfo.phoneNumber

        return when (state.codeInfo.type) {
            is AuthenticationCodeTypeTelegramMessage -> {
                "Telegram sent the code to your Telegram app on another device. " +
                    "Open Telegram there to view it. If you can't access that device, use QR code login."
            }

            is AuthenticationCodeTypeSms -> {
                "Telegram sent the code by SMS to ${formatPhoneNumber(phoneNumber)}. " +
                    "Check your messages. If it doesn't arrive, tap Resend code."
            }

            is AuthenticationCodeTypeCall -> {
                "Telegram will call ${formatPhoneNumber(phoneNumber)} with the code. Answer the call."
            }

            is AuthenticationCodeTypeFlashCall -> {
                "Telegram will call ${formatPhoneNumber(phoneNumber)}. The code is in the caller's phone number."
            }

            is AuthenticationCodeTypeMissedCall -> {
                "Telegram will call ${formatPhoneNumber(phoneNumber)}. The last digits of the missed call are the code."
            }

            is AuthenticationCodeTypeFragment -> {
                "Telegram sent the code through Fragment. Check your Fragment account."
            }

            is AuthenticationCodeTypeFirebaseAndroid,
            is AuthenticationCodeTypeFirebaseIos -> {
                "Telegram sent the code through Firebase. Check the notification on this device."
            }

            is AuthenticationCodeTypeSmsWord -> {
                "Telegram sent an SMS with a code word to ${formatPhoneNumber(phoneNumber)}. Check your messages."
            }

            is AuthenticationCodeTypeSmsPhrase -> {
                "Telegram sent an SMS with a code phrase to ${formatPhoneNumber(phoneNumber)}. Check your messages."
            }

        }
    }

    private fun normalizePhoneNumber(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val digits = trimmed.filter(Char::isDigit)
        if (digits.length < 7) return null

        return when {
            trimmed.startsWith("+") -> "+$digits"
            trimmed.startsWith("00") -> "+${digits.drop(2)}"
            else -> null
        }
    }

    private fun setChallenge(challenge: AuthChallenge) {
        ready = false
        currentChallenge = challenge
        _authState.value = AuthState.Challenge(challenge)
    }

    private fun setError(message: String) {
        ready = false
        currentChallenge = null
        _authState.value = AuthState.Error(message)
    }

    private fun loadProfileAndChats() {
        scope.launch {
            when (val result = client.getMe()) {
                is TdlResult.Success -> {
                    currentUserId = result.result.id
                    _users.update { it + (result.result.id to result.result) }
                    updateProfile(result.result)
                }

                is TdlResult.Failure -> Unit
            }

            loadAllChats()
        }
    }

    private fun updateProfile(user: User) {
        val displayName = userDisplayName(user)
        val nickname = user.usernames?.activeUsernames?.firstOrNull()
        val photo = user.profilePhoto
        val image = photo?.small?.let(::avatarBytes)
            ?: photo?.minithumbnail?.data

        val avatar = image?.let { bytes ->
            Avatar(
                initials = Avatar.fromName(displayName).initials,
                colorSeed = displayName.hashCode(),
                image = bytes
            )
        } ?: Avatar.fromName(displayName)

        _profile.value = AccountProfile(
            displayName = displayName,
            nickname = nickname,
            avatar = avatar
        )
    }

    private fun userAvatar(user: User): Avatar {
        val displayName = userDisplayName(user)
        val photo = user.profilePhoto
        val image = photo?.small?.let(::avatarBytes)
            ?: photo?.minithumbnail?.data

        return image?.let { bytes ->
            Avatar(
                initials = Avatar.fromName(displayName).initials,
                colorSeed = displayName.hashCode(),
                image = bytes
            )
        } ?: Avatar.fromName(displayName)
    }

    private fun userStatusText(user: User): String? {
        return when (val status = _userStatuses.value[user.id] ?: user.status) {
            is UserStatusOnline -> "online"
            is UserStatusOffline -> "last seen ${formatTime(status.wasOnline.toLong() * 1000L)}"
            is UserStatusRecently -> "last seen recently"
            is UserStatusLastWeek -> "last seen within a week"
            is UserStatusLastMonth -> "last seen within a month"
            else -> null
        }
    }

    private fun userDisplayName(user: User): String {
        return listOf(user.firstName, user.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Telegram account" }
    }

    private fun mapMessage(message: Message): ChatMessage {
        val senderUser = if (message.isOutgoing) {
            null
        } else {
            (message.senderId as? MessageSenderUser)?.let { sender ->
                _users.value[sender.userId]
            }
        }

        val senderName = if (message.isOutgoing) {
            null
        } else {
            when (val sender = message.senderId) {
                is MessageSenderUser -> senderUser?.let(::userDisplayName)
                is MessageSenderChat -> _chats.value[sender.chatId]?.title
            }
        }

        return ChatMessage(
            providerId = providerId,
            accountId = accountId,
            providerDialogId = message.chatId.toString(),
            messageId = message.id,
            senderName = senderName,
            senderAvatar = senderUser?.let(::userAvatar),
            senderId = when(val sender = message.senderId) {
                is MessageSenderUser -> sender.userId
                is MessageSenderChat -> sender.chatId
            },
            text = messageText(message.content),
            timestamp = message.date.toLong() * 1000L,
            isOutgoing = message.isOutgoing,
            media = mapMedia(message.content),
            customEmojis = messageCustomEmojis(message.content),
            replyTo = (message.replyTo as? MessageReplyToMessage)?.let { reply ->
                MessageReply(
                    messageId = reply.messageId,
                    text = reply.quote?.text?.text.orEmpty()
                )
            },
            reactions = mapReactions(message.interactionInfo),
            sendState = when (message.sendingState) {
                is MessageSendingStatePending -> MessageSendState.Sending
                is MessageSendingStateFailed -> MessageSendState.Failed
                else -> MessageSendState.Sent
            },
            isRead = message.isOutgoing && message.id <= (
                _chats.value[message.chatId]?.lastReadOutboxMessageId ?: 0L
            )
        )
    }

    private fun messageText(content: MessageContent): String {
        return when (content) {
            is MessageText -> content.text.text
            is MessagePhoto -> content.caption.text
            is MessageDocument -> content.caption.text
            is MessageVideo -> content.caption.text
            is MessageSticker -> ""
            is MessageVideoNote -> ""
            is MessageVoiceNote -> ""
            is MessageAudio -> content.caption.text
            is MessageAnimation -> content.caption.text
            else -> messageBody(content)
        }
    }

    private fun mapReactions(interactionInfo: MessageInteractionInfo?): List<MessageReactionInfo> {
        return interactionInfo?.reactions?.reactions.orEmpty().mapNotNull { reaction ->
            val emoji = (reaction.type as? ReactionTypeEmoji)?.emoji
                ?: return@mapNotNull null

            MessageReactionInfo(
                emoji = emoji,
                count = reaction.totalCount,
                isChosen = reaction.isChosen
            )
        }
    }

    private fun updateMessageReactions(
        chatId: Long,
        messageId: Long,
        interactionInfo: MessageInteractionInfo?
    ) {
        val dialogId = chatId.toString()

        messagesByChat.update { existing ->
            if (dialogId !in existing) return@update existing

            existing + (
                dialogId to existing[dialogId].orEmpty().map { message ->
                    if (message.messageId == messageId) {
                        message.copy(reactions = mapReactions(interactionInfo))
                    } else {
                        message
                    }
                }
            )
        }
    }

    private fun mapMedia(content: MessageContent): MessageMedia? {
        return when (content) {
            is MessagePhoto -> {
                val size = content.photo.previewSize()

                MessageMedia(
                    kind = MessageMediaKind.Photo,
                    previewFileId = size?.photo?.id,
                    fileId = content.photo.sizes.maxByOrNull { it.photo.size }?.photo?.id,
                    fileName = "Photo",
                    mimeType = "image/jpeg",
                    size = size?.photo?.size ?: 0,
                    width = size?.width ?: 0,
                    height = size?.height ?: 0,
                    minithumbnail = content.photo.minithumbnail?.data
                )
            }

            is MessageDocument -> {
                val document = content.document
                val isImage = document.mimeType.startsWith("image/")
                val thumbnail = document.thumbnail

                MessageMedia(
                    kind = if (isImage) MessageMediaKind.Photo else MessageMediaKind.File,
                    previewFileId = if (isImage) {
                        thumbnail?.file?.id ?: document.document.id
                    } else {
                        null
                    },
                    fileId = document.document.id,
                    fileName = document.fileName,
                    mimeType = document.mimeType,
                    size = document.document.size,
                    width = thumbnail?.width ?: 0,
                    height = thumbnail?.height ?: 0,
                    minithumbnail = document.minithumbnail?.data
                )
            }

            is MessageAudio -> {
                val audio = content.audio

                MessageMedia(
                    kind = MessageMediaKind.Audio,
                    fileId = audio.audio.id,
                    fileName = audio.fileName.ifBlank {
                        listOf(audio.performer, audio.title)
                            .filter(String::isNotBlank)
                            .joinToString(" - ")
                            .ifBlank { "Audio" }
                    },
                    mimeType = audio.mimeType,
                    size = audio.audio.size,
                    durationSeconds = audio.duration,
                    minithumbnail = audio.albumCoverMinithumbnail?.data
                )
            }

            is MessageAnimation -> {
                val animation = content.animation

                MessageMedia(
                    kind = MessageMediaKind.Animation,
                    previewFileId = animation.thumbnail?.file?.id,
                    fileId = animation.animation.id,
                    fileName = animation.fileName.ifBlank { "GIF" },
                    mimeType = animation.mimeType,
                    size = animation.animation.size,
                    width = animation.width,
                    height = animation.height,
                    minithumbnail = animation.minithumbnail?.data
                )
            }

            is MessageVoiceNote -> {
                val note = content.voiceNote

                MessageMedia(
                    kind = MessageMediaKind.VoiceNote,
                    fileId = note.voice.id,
                    fileName = "Voice message",
                    mimeType = note.mimeType,
                    size = note.voice.size,
                    durationSeconds = note.duration,
                    waveform = note.waveform
                )
            }

            is MessageVideoNote -> {
                val note = content.videoNote

                MessageMedia(
                    kind = MessageMediaKind.VideoNote,
                    previewFileId = note.thumbnail?.file?.id,
                    fileId = note.video.id,
                    fileName = "Video message",
                    mimeType = "video/mp4",
                    size = note.video.size,
                    width = note.length,
                    height = note.length,
                    minithumbnail = note.minithumbnail?.data
                )
            }

            is MessageVideo -> {
                val video = content.video

                MessageMedia(
                    kind = MessageMediaKind.Video,
                    previewFileId = video.thumbnail?.file?.id,
                    fileId = video.video.id,
                    fileName = video.fileName,
                    mimeType = video.mimeType,
                    size = video.video.size,
                    width = video.width,
                    height = video.height,
                    minithumbnail = video.minithumbnail?.data
                )
            }

            is MessageSticker -> {
                val sticker = content.sticker

                MessageMedia(
                    kind = MessageMediaKind.Sticker,
                    size = sticker.sticker.size,
                    width = sticker.width,
                    height = sticker.height,
                    fileId = sticker.sticker.id,
                    stickerFileId = sticker.sticker.id,
                    stickerFormat = when (sticker.format) {
                        is StickerFormatTgs -> MessageStickerFormat.Tgs
                        is StickerFormatWebm -> MessageStickerFormat.Webm
                        else -> MessageStickerFormat.Webp
                    }
                )
            }

            else -> null
        }
    }

    private fun messageCustomEmojis(content: MessageContent): List<CustomEmojiSpan> {
        val text = when (content) {
            is MessageText -> content.text
            is MessagePhoto -> content.caption
            is MessageDocument -> content.caption
            is MessageVideo -> content.caption
            is MessageAnimation -> content.caption
            else -> return emptyList()
        }

        return text.entities.mapNotNull { entity ->
            val type = entity.type as? TextEntityTypeCustomEmoji
                ?: return@mapNotNull null

            CustomEmojiSpan(
                start = entity.offset,
                end = entity.offset + entity.length,
                emojiId = type.customEmojiId
            )
        }
    }

    override suspend fun loadCustomEmoji(emojiId: Long): ByteArray? {
        return try {
            val sticker = customEmojiCacheMutex.withLock {
                customEmojiCache[emojiId]
            } ?: run {
                when (val response = client.getCustomEmojiStickers(longArrayOf(emojiId))) {
                    is TdlResult.Success -> {
                        val found = response.result.stickers.firstOrNull() ?: return null

                        customEmojiCacheMutex.withLock {
                            customEmojiCache[emojiId] = found
                        }

                        found
                    }

                    is TdlResult.Failure -> return null
                }
            }

            loadFile(sticker.sticker.id).getOrNull()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            null
        }
    }

    private fun Photo.previewSize(): PhotoSize? {
        return sizes
            .filter { it.width in 1..800 }
            .maxByOrNull { it.width }
            ?: sizes.minByOrNull { it.width }
    }

    private fun upsertMessage(message: Message) {
        val dialogId = message.chatId.toString()
        val mapped = mapMessage(message)

        messagesByChat.update { existing ->
            if (dialogId !in existing) return@update existing

            existing + (
                dialogId to mergeMessages(existing[dialogId].orEmpty() + mapped)
            )
        }
    }

    private fun replaceMessage(chatId: Long, oldMessageId: Long, message: Message) {
        val dialogId = chatId.toString()
        val mapped = mapMessage(message)

        messagesByChat.update { existing ->
            if (dialogId !in existing) return@update existing

            existing + (
                dialogId to mergeMessages(
                    existing[dialogId].orEmpty().filterNot {
                        it.messageId == oldMessageId || it.messageId == mapped.messageId
                    } + mapped
                )
            )
        }
    }

    private fun updateMessageContent(chatId: Long, messageId: Long, content: MessageContent) {
        val dialogId = chatId.toString()

        messagesByChat.update { existing ->
            if (dialogId !in existing) return@update existing

            existing + (
                dialogId to existing[dialogId].orEmpty().map { message ->
                    if (message.messageId == messageId) {
                        message.copy(
                            text = messageText(content),
                            media = mapMedia(content)
                        )
                    } else {
                        message
                    }
                }
            )
        }
    }

    private fun removeMessages(chatId: Long, messageIds: LongArray) {
        val dialogId = chatId.toString()
        val ids = messageIds.toSet()

        messagesByChat.update { existing ->
            if (dialogId !in existing) return@update existing

            existing + (
                dialogId to existing[dialogId].orEmpty().filterNot { it.messageId in ids }
            )
        }
    }

    private fun mergeMessages(messages: List<ChatMessage>): List<ChatMessage> {
        return messages
            .associateBy { it.messageId }
            .values
            .sortedBy { it.messageId }
    }

    private fun handleChatReadOutbox(chatId: Long, lastReadOutboxMessageId: Long) {
        updateChat(chatId) { it.copy(lastReadOutboxMessageId = lastReadOutboxMessageId) }

        val dialogId = chatId.toString()

        messagesByChat.update { chats ->
            val messages = chats[dialogId] ?: return@update chats

            chats + (dialogId to messages.map { message ->
                if (
                    message.isOutgoing &&
                    !message.isRead &&
                    message.messageId <= lastReadOutboxMessageId
                ) {
                    message.copy(isRead = true)
                } else {
                    message
                }
            })
        }
    }

    private fun requestFolderChatsLoad(folderIds: Collection<Int>) {
        if (closed || folderIds.isEmpty()) return

        scope.launch {
            val toLoad = folderChatsMutex.withLock {
                folderIds.filter { folderId ->
                    folderId !in loadedFolderChats &&
                        loadingFolderChats.add(folderId)
                }
            }

            for (folderId in toLoad) {
                scope.launch {
                    var succeeded = false

                    try {
                        loadChatsForFolder(folderId)
                        succeeded = true
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        if (!closed) {
                            println(
                                "Failed to load Telegram folder chats: " +
                                    exception.message
                            )
                        }
                    } finally {
                        folderChatsMutex.withLock {
                            loadingFolderChats.remove(folderId)

                            if (succeeded) {
                                loadedFolderChats.add(folderId)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestFolderCustomEmojiLoad() {
        folderEmojiJob?.cancel()
        if (closed) return

        folderEmojiJob = scope.launch {
            val ids = _folders.value
                .flatMap { it.customEmojis }
                .map { it.customEmojiId }
                .distinct()

            if (ids.isEmpty()) return@launch

            try {
                val stickers = loadCustomEmojiStickers(ids)
                if (stickers.isEmpty()) return@launch

                currentCoroutineContext().ensureActive()
                applyCustomEmojiMetadata(stickers)
                loadCustomEmojiAssets(stickers)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!closed) {
                    println(
                        "Failed to load Telegram folder custom emoji: " +
                            exception.message
                    )
                }
            }
        }
    }

    private suspend fun loadCustomEmojiStickers(
        ids: List<Long>
    ): Map<Long, Sticker> {
        val result = mutableMapOf<Long, Sticker>()
        val missing = mutableListOf<Long>()

        customEmojiCacheMutex.withLock {
            for (id in ids) {
                val sticker = customEmojiCache[id]

                if (sticker != null) {
                    result[id] = sticker
                } else {
                    missing += id
                }
            }
        }

        for (chunk in missing.chunked(200)) {
            when (val response = client.getCustomEmojiStickers(chunk.toLongArray())) {
                is TdlResult.Success -> {
                    val stickers = response.result.stickers.mapNotNull { sticker ->
                        val type = sticker.fullType as? StickerFullTypeCustomEmoji
                            ?: return@mapNotNull null
                        type.customEmojiId to sticker
                    }

                    customEmojiCacheMutex.withLock {
                        for ((id, sticker) in stickers) {
                            customEmojiCache[id] = sticker
                            result[id] = sticker
                        }
                    }
                }

                is TdlResult.Failure -> {
                    if (!closed) {
                        println(
                            "Failed to load Telegram custom emoji stickers: " +
                                response.message
                        )
                    }
                }
            }
        }

        return result
    }

    private fun applyCustomEmojiMetadata(stickers: Map<Long, Sticker>) {
        _folders.update { folders ->
            folders.map { folder ->
                folder.copy(
                    customEmojis = folder.customEmojis.map { emoji ->
                        val sticker = stickers[emoji.customEmojiId]

                        if (sticker == null) {
                            emoji
                        } else {
                            val type = sticker.fullType as? StickerFullTypeCustomEmoji

                            emoji.copy(
                                needsRepainting = type?.needsRepainting == true,
                                format = sticker.format.toFolderEmojiFormat()
                            )
                        }
                    }
                )
            }
        }
    }

    private suspend fun loadCustomEmojiAssets(stickers: Map<Long, Sticker>) {
        val thumbnails = mutableMapOf<Long, ByteArray>()

        for ((customEmojiId, sticker) in stickers) {
            currentCoroutineContext().ensureActive()

            val fileId = sticker.thumbnail?.file?.id ?: continue
            loadFileBytes(fileId)?.let { thumbnails[customEmojiId] = it }
        }

        if (thumbnails.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            applyCustomEmojiAssets(thumbnails = thumbnails)
        }

        val data = mutableMapOf<Long, ByteArray>()

        for ((customEmojiId, sticker) in stickers) {
            currentCoroutineContext().ensureActive()

            val fileId = sticker.sticker.id
            loadFileBytes(fileId)?.let { data[customEmojiId] = it }
        }

        if (data.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            applyCustomEmojiAssets(data = data)
        }
    }

    private suspend fun loadFileBytes(fileId: Int): ByteArray? {
        if (fileId == 0) return null
        return loadFile(fileId).getOrNull()
    }

    private fun applyCustomEmojiAssets(
        thumbnails: Map<Long, ByteArray> = emptyMap(),
        data: Map<Long, ByteArray> = emptyMap()
    ) {
        if (thumbnails.isEmpty() && data.isEmpty()) return

        _folders.update { folders ->
            folders.map { folder ->
                folder.copy(
                    customEmojis = folder.customEmojis.map { emoji ->
                        emoji.copy(
                            thumbnail = thumbnails[emoji.customEmojiId]
                                ?: emoji.thumbnail,
                            data = data[emoji.customEmojiId] ?: emoji.data
                        )
                    }
                )
            }
        }
    }

    private fun StickerFormat.toFolderEmojiFormat(): DialogFolderEmojiFormat {
        return when (this) {
            is StickerFormatTgs -> DialogFolderEmojiFormat.Tgs
            is StickerFormatWebp -> DialogFolderEmojiFormat.Webp
            is StickerFormatWebm -> DialogFolderEmojiFormat.Webm
        }
    }

    private suspend fun loadChatsForFolder(folderId: Int) {
        var loadAttempts = 0

        while (!closed && loadAttempts < 1000) {
            loadAttempts++

            when (val result = client.loadChats(ChatListFolder(folderId), 100)) {
                is TdlResult.Success -> Unit
                is TdlResult.Failure -> {
                    if (result.code != 404) {
                        println(
                            "Failed to load Telegram folder $folderId: " +
                                result.message
                        )
                    }

                    break
                }
            }
        }

        when (val result = client.getChats(ChatListFolder(folderId), 10_000)) {
            is TdlResult.Success -> {
                for (chatId in result.result.chatIds) {
                    if (closed) return

                    when (val chat = client.getChat(chatId)) {
                        is TdlResult.Success -> upsertChat(chat.result)
                        is TdlResult.Failure -> Unit
                    }
                }
            }

            is TdlResult.Failure -> {
                if (result.code != 404) {
                    println(
                        "Failed to get Telegram folder $folderId chats: " +
                            result.message
                    )
                }
            }
        }

        emitDialogs()
    }

    private suspend fun loadAllChats() {
        _isLoadingDialogs.value = true

        try {
            var loadAttempts = 0

            while (!closed && loadAttempts < 1000) {
                loadAttempts++

                when (val result = client.loadChats(ChatListMain(), 100)) {
                    is TdlResult.Success -> Unit
                    is TdlResult.Failure -> {
                        if (result.code != 404) {
                            println("Failed to load Telegram chats: ${result.message}")
                        }
                        break
                    }
                }
            }

            when (val result = client.getChats(ChatListMain(), 10_000)) {
                is TdlResult.Success -> {
                    for (chatId in result.result.chatIds) {
                        if (closed) return
                        when (val chat = client.getChat(chatId)) {
                            is TdlResult.Success -> upsertChat(chat.result)
                            is TdlResult.Failure -> Unit
                        }
                    }
                }

                is TdlResult.Failure -> {
                    println("Failed to get Telegram chats: ${result.message}")
                }
            }

            loadPrivateUsers()
            emitDialogs()
            requestFolderChatsLoad(_folders.value.map { it.id })
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (!closed) {
                println("Failed to load Telegram dialogs: ${exception.message}")
            }
        } finally {
            _isLoadingDialogs.value = false
        }
    }

    private fun avatarBytes(file: File?): ByteArray? {
        if (file == null || file.id == 0) return null

        avatarCache.value[file.id]?.let { return it }
        requestAvatar(file)
        return null
    }

    private fun requestAvatar(file: File) {
        var shouldLoad = false

        avatarRequests.update { requested ->
            if (file.id in requested || avatarCache.value.containsKey(file.id)) {
                requested
            } else {
                shouldLoad = true
                requested + file.id
            }
        }

        if (!shouldLoad) return

        scope.launch {
            val result = try {
                client.downloadFile(
                    fileId = file.id,
                    priority = 16,
                    offset = 0,
                    limit = 0,
                    synchronous = true
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                null
            }

            if (result is TdlResult.Success && result.result.local.isDownloadingCompleted) {
                val bytes = readFileBytes(result.result.local.path)
                if (bytes != null && bytes.isNotEmpty()) {
                    avatarCache.update { it + (file.id to bytes) }
                    emitDialogs()
                    _users.value[currentUserId]?.let(::updateProfile)
                }
            }
        }
    }

    private fun loadPrivateUsers() {
        _chats.value.values
            .mapNotNull { (it.type as? ChatTypePrivate)?.userId }
            .distinct()
            .forEach(::requestUser)
    }

    private fun requestUser(userId: Long) {
        if (_users.value.containsKey(userId)) return

        var shouldLoad = false
        _requestedUsers.update { requested ->
            if (userId in requested) {
                requested
            } else {
                shouldLoad = true
                requested + userId
            }
        }

        if (!shouldLoad) return

        scope.launch {
            when (val result = client.getUser(userId)) {
                is TdlResult.Success -> {
                    _users.update { it + (userId to result.result) }
                    emitDialogs()
                }

                is TdlResult.Failure -> Unit
            }
        }
    }

    private fun upsertChat(chat: Chat) {
        _chats.update { it + (chat.id to ChatEntry.from(chat)) }

        val type = chat.type
        if (type is ChatTypePrivate) {
            requestUser(type.userId)
        }

        emitDialogs()
    }

    private fun updateChat(chatId: Long, transform: (ChatEntry) -> ChatEntry) {
        _chats.update { chats ->
            val entry = chats[chatId] ?: return@update chats
            chats + (chatId to transform(entry))
        }

        emitDialogs()
    }

    private fun toChatWallpaper(chatBackground: ChatBackground): ChatWallpaper {
        val background = chatBackground.background
        val type = background.type
        val fill = when (type) {
            is BackgroundTypeFill -> type.fill
            is BackgroundTypePattern -> type.fill
            else -> null
        }

        val colors = when (fill) {
            is BackgroundFillSolid -> listOf(fill.color)
            is BackgroundFillGradient -> listOf(fill.topColor, fill.bottomColor)
            is BackgroundFillFreeformGradient -> fill.colors.toList()
            else -> emptyList()
        }

        val document = background.document

        return ChatWallpaper(
            fileId = document?.document?.id,
            thumbnailFileId = document?.thumbnail?.file?.id
                ?: document?.document?.id,
            colors = colors,
            gradientAngle = (fill as? BackgroundFillGradient)?.rotationAngle ?: 0,
            isPattern = type is BackgroundTypePattern,
            intensity = (type as? BackgroundTypePattern)?.intensity ?: 100,
            darkThemeDimming = chatBackground.darkThemeDimming
        )
    }

    private fun emitDialogs() {
        val users = _users.value

        val dialogs = _chats.value.values.mapNotNull { entry ->
            val mainPosition = entry.positions.firstOrNull {
                it.list is ChatListMain && it.order != 0L
            } ?: return@mapNotNull null

            val user = (entry.type as? ChatTypePrivate)?.let { users[it.userId] }
            val kind = when (val type = entry.type) {
                is ChatTypePrivate -> {
                    if (user?.type is UserTypeBot) DialogKind.Bot else DialogKind.Direct
                }

                is ChatTypeBasicGroup -> DialogKind.Group
                is ChatTypeSupergroup -> {
                    if (type.isChannel) DialogKind.Channel else DialogKind.Group
                }

                is ChatTypeSecret -> DialogKind.Secret
            }

            val identity = when (val type = entry.type) {
                is ChatTypePrivate -> DialogIdentity(
                    providerId = providerId,
                    remoteId = "user:${type.userId}"
                )

                is ChatTypeBasicGroup -> DialogIdentity(
                    providerId = providerId,
                    remoteId = "basicGroup:${type.basicGroupId}"
                )

                is ChatTypeSupergroup -> DialogIdentity(
                    providerId = providerId,
                    remoteId = "supergroup:${type.supergroupId}"
                )

                is ChatTypeSecret -> DialogIdentity(
                    providerId = providerId,
                    remoteId = "secret:$accountId:${type.secretChatId}"
                )
            }

            val photoFile = entry.photo?.small ?: user?.profilePhoto?.small
            val imageBytes = photoFile?.let(::avatarBytes)
                ?: entry.photo?.minithumbnail?.data
                ?: user?.profilePhoto?.minithumbnail?.data

            Dialog(
                providerId = providerId,
                accountId = accountId,
                providerDialogId = entry.chatId.toString(),
                identity = identity,
                kind = kind,
                title = entry.title,
                statusText = user?.let(::userStatusText),
                preview = messagePreview(entry.lastMessage),
                timestamp = (entry.lastMessage?.date ?: 0).toLong() * 1000L,
                unreadCount = entry.unreadCount,
                canSendMessages = entry.permissions.canSendBasicMessages,
                isPinned = mainPosition.isPinned,
                isMarkedAsUnread = entry.isMarkedAsUnread,
                order = mainPosition.order,
                folderIds = entry.positions.mapNotNull { position ->
                    (position.list as? ChatListFolder)?.chatFolderId
                }.toSet(),
                folderPositions = entry.positions.mapNotNull { position ->
                    val folderId = (position.list as? ChatListFolder)?.chatFolderId
                        ?: return@mapNotNull null

                    DialogFolderPosition(
                        folderId = folderId,
                        order = position.order,
                        isPinned = position.isPinned
                    )
                },
                wallpaper = entry.background?.let(::toChatWallpaper),
                avatar = imageBytes?.let { image ->
                    Avatar(
                        initials = Avatar.fromName(entry.title).initials,
                        colorSeed = entry.title.hashCode(),
                        image = image
                    )
                } ?: Avatar.fromName(entry.title)
            )
        }.sortedWith(
            compareByDescending<Dialog> {
                if (it.isPinned) 1 else 0
            }
                .thenByDescending { it.order }
                .thenByDescending { it.timestamp }
        )

        _dialogs.value = dialogs
    }

    private fun messagePreview(message: Message?): String {
        if (message == null) return "No messages yet"

        val preview = messageBody(message.content)
            .replace('\n', ' ')
            .trim()
            .ifBlank { "Message" }

        return if (message.isOutgoing) "You: $preview" else preview
    }

    private fun messageBody(content: MessageContent): String {
        return when (content) {
            is MessageText -> content.text.text
            is MessagePhoto -> content.caption.text.ifBlank { "Photo" }
            is MessageVideo -> content.caption.text.ifBlank { "Video" }
            is MessageSticker -> "Sticker"
            is MessageDocument -> content.caption.text.ifBlank {
                content.document.fileName.ifBlank { "Document" }
            }
            is MessageAudio -> "Audio"
            is MessageVoiceNote -> "Voice message"
            is MessageVideoNote -> "Video message"
            is MessageAnimation -> "GIF"
            is MessagePoll -> "Poll"
            is MessageLocation -> "Location"
            is MessageContact -> "Contact"
            is MessageCall -> "Call"
            else -> content::class.simpleName?.removePrefix("Message") ?: "Message"
        }
    }

    private data class ChatEntry(
        val chatId: Long,
        val type: ChatType,
        val title: String,
        val photo: ChatPhotoInfo?,
        val background: ChatBackground? = null,
        val lastMessage: Message?,
        val positions: List<ChatPosition>,
        val permissions: ChatPermissions,
        val unreadCount: Int,
        val lastReadOutboxMessageId: Long,
        val isMarkedAsUnread: Boolean
    ) {
        companion object {
            fun from(chat: Chat): ChatEntry {
                return ChatEntry(
                    chatId = chat.id,
                    type = chat.type,
                    title = chat.title,
                    photo = chat.photo,
                    background = chat.background,
                    lastMessage = chat.lastMessage,
                    positions = chat.positions.filter { position ->
                        position.order != 0L
                    },
                    permissions = chat.permissions,
                    unreadCount = chat.unreadCount,
                    lastReadOutboxMessageId = chat.lastReadOutboxMessageId,
                    isMarkedAsUnread = chat.isMarkedAsUnread
                )
            }
        }
    }
}
