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
import dev.g000sha256.tdl.dto.NetworkStatisticsEntryCall
import dev.g000sha256.tdl.dto.NetworkStatisticsEntryFile
import dev.g000sha256.tdl.dto.NetworkType
import dev.g000sha256.tdl.dto.NetworkTypeMobile
import dev.g000sha256.tdl.dto.NetworkTypeMobileRoaming
import dev.g000sha256.tdl.dto.NetworkTypeOther
import dev.g000sha256.tdl.dto.NetworkTypeWiFi
import dev.g000sha256.tdl.dto.PhoneNumberAuthenticationSettings
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
import kotlinx.coroutines.delay
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
import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.model.AccountProfile
import com.mrboomdev.scrollix.data.model.ActiveSessionInfo
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.AuthAction
import com.mrboomdev.scrollix.data.model.AuthChallenge
import com.mrboomdev.scrollix.data.model.AuthField
import com.mrboomdev.scrollix.data.model.AuthFieldType
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.CommonGroup
import com.mrboomdev.scrollix.data.model.CustomEmojiAsset
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.ChatMemberInfo
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.ChatWallpaper
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat
import com.mrboomdev.scrollix.data.model.DialogFolderPosition
import com.mrboomdev.scrollix.data.model.DialogIdentity
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageButton
import com.mrboomdev.scrollix.data.model.MessageDraft
import com.mrboomdev.scrollix.data.model.MessageLinkPreview
import com.mrboomdev.scrollix.data.model.MessageLocationInfo
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.ChatTopic
import com.mrboomdev.scrollix.data.model.MessageVideoQuality
import com.mrboomdev.scrollix.data.model.NotificationException
import com.mrboomdev.scrollix.data.model.WebAppLaunch
import com.mrboomdev.scrollix.data.model.NotificationScope
import com.mrboomdev.scrollix.data.model.NotificationSettings
import com.mrboomdev.scrollix.data.model.MessagePollInfo
import com.mrboomdev.scrollix.data.model.MessagePollOptionInfo
import com.mrboomdev.scrollix.data.model.MessageReactionInfo
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.DownloadedFile
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.OutgoingAttachment
import com.mrboomdev.scrollix.data.model.PostSearchPage
import com.mrboomdev.scrollix.data.model.PostSearchQuota
import com.mrboomdev.scrollix.data.model.PublicChatSearchKind
import com.mrboomdev.scrollix.data.model.MessageRichBlock
import com.mrboomdev.scrollix.data.model.MessageRichTextStyle
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.data.model.SharedMediaKind
import com.mrboomdev.scrollix.data.model.DataUsagePreset
import com.mrboomdev.scrollix.data.model.DataUsageSummary
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.model.PersonalChannel
import com.mrboomdev.scrollix.data.model.ProfileTrack
import com.mrboomdev.scrollix.data.model.StickerPack
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.model.StickerSetInfo
import com.mrboomdev.scrollix.data.model.RichTextSpan
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.model.parseFolderCustomEmojis
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.provider.MessageHistoryController
import com.mrboomdev.scrollix.data.provider.MessagePage
import com.mrboomdev.scrollix.data.provider.MessagePageLoader
import com.mrboomdev.scrollix.data.provider.TelegramLinkTarget
import com.mrboomdev.scrollix.data.storage.CacheStore
import com.mrboomdev.scrollix.data.storage.PlatformStorage
import com.mrboomdev.scrollix.data.storage.currentTimeMillis
import com.mrboomdev.scrollix.data.storage.formatPhoneNumber
import com.mrboomdev.scrollix.data.storage.formatTime
import com.mrboomdev.scrollix.data.storage.readFileBytes
import com.mrboomdev.scrollix.generated.BuildConfig
import com.mrboomdev.scrollix.tdlibDeviceModel
import com.mrboomdev.scrollix.tdlibSystemVersion
import com.mrboomdev.scrollix.toJsonStringByReflection

internal const val TypingStatusTimeoutMillis = 5_000L
internal const val MaxPendingLiveMessages = 100

/** Usernames that TDLib can resolve to a user profile. */
internal val MentionUsernameRegex = Regex("""(?<![\w@])@([A-Za-z][A-Za-z0-9_]{2,31})""")

class TelegramSession(
    account: AccountRecord,
    internal val storage: PlatformStorage,
    parentScope: CoroutineScope
) : AccountSession {
    override val accountId: String = account.id
    override val providerId: String = TelegramProvider.id

    internal val client = TdlClient.create()
    internal val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job])
    )
    /**
     * Per-dialog message windows. Every dialog owns its pagination cursor,
     * so paging one chat cannot disturb another chat's loaded range.
     */
    internal val histories = MutableStateFlow<Map<String, MessageHistoryController>>(emptyMap())

    /**
     * Live messages that arrived before a dialog had a controller. They are
     * replayed as soon as the controller is created instead of being dropped.
     */
    internal val pendingLiveMessages = mutableMapOf<String, MutableList<Message>>()

    private val emptyHistory = MutableStateFlow(
        MessageHistory(
            hasMoreOlder = false,
            isLoadingInitial = false
        )
    ).asStateFlow()

    internal val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    internal val _profile = MutableStateFlow<AccountProfile?>(null)
    override val profile: StateFlow<AccountProfile?> = _profile.asStateFlow()

    /** Snapshot restored from the database, kept around while the initial sync runs. */
    internal var restoredDialogs: List<Dialog> = emptyList()

    /** The first full chat sync is what makes the cached snapshot obsolete. */
    internal var dialogsSynced = false

    internal val _dialogs = MutableStateFlow<List<Dialog>>(emptyList())
    override val dialogs: StateFlow<List<Dialog>> = _dialogs.asStateFlow()

    internal val _folders = MutableStateFlow<List<DialogFolder>>(emptyList())
    override val folders: StateFlow<List<DialogFolder>> = _folders.asStateFlow()

    internal val _isLoadingDialogs = MutableStateFlow(false)
    override val isLoadingDialogs: StateFlow<Boolean> = _isLoadingDialogs.asStateFlow()

    internal val avatarCache = MutableStateFlow<Map<Int, ByteArray>>(emptyMap())
    internal val avatarRequests = MutableStateFlow<Set<Int>>(emptySet())
    internal val fileCache = MutableStateFlow<Map<Int, ByteArray>>(emptyMap())
    internal val mediaFileNames = MutableStateFlow<Map<Int, String>>(emptyMap())
    internal val _downloads = MutableStateFlow<List<DownloadedFile>>(emptyList())
    override val downloads: StateFlow<List<DownloadedFile>> = _downloads.asStateFlow()
    internal val customEmojiCache = mutableMapOf<Long, Sticker>()
    internal val customEmojiCacheMutex = Mutex()

    /**
     * Mention avatars are identical for every message that mentions the same
     * user, so the public chat lookup runs once per account and session.
     * A null value remembers a username that does not resolve.
     */
    internal val mentionAvatarCache = mutableMapOf<String, Avatar?>()
    internal val mentionAvatarMutex = Mutex()
    internal var folderEmojiJob: Job? = null
    internal val typingJobs = mutableMapOf<Long, Job>()

    internal val stateMutex = Mutex()
    internal val folderChatsMutex = Mutex()
    internal val loadedFolderChats = mutableSetOf<Int>()
    internal val loadingFolderChats = mutableSetOf<Int>()
    internal val _isConnected = MutableStateFlow(true)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    internal val _chats = MutableStateFlow<Map<Long, ChatEntry>>(emptyMap())
    internal val _users = MutableStateFlow<Map<Long, User>>(emptyMap())
    internal val _userStatuses = MutableStateFlow<Map<Long, UserStatus>>(emptyMap())
    internal val _requestedUsers = MutableStateFlow<Set<Long>>(emptySet())
    internal val _memberCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    internal val _requestedMemberCounts = MutableStateFlow<Set<String>>(emptySet())
    internal val _verifiedSupergroups = MutableStateFlow<Set<Long>>(emptySet())
    internal val _requestedVerifiedSupergroups = MutableStateFlow<Set<Long>>(emptySet())
    internal val _pinnedMessageIds = MutableStateFlow<Map<Long, Long>>(emptyMap())

    private var started = false
    internal var parametersSet = false
    internal var ready = false
    internal var closed = false
    internal var currentChallenge: AuthChallenge? = null
    internal var currentUserId: Long? = null

    override fun messageHistory(providerDialogId: String): StateFlow<MessageHistory> {
        return historyFor(providerDialogId)?.state ?: emptyHistory
    }

    override suspend fun loadMessages(providerDialogId: String, limit: Int): Result<Unit> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

        return history.loadLatest(limit)
    }

    override suspend fun loadOlderMessages(providerDialogId: String, limit: Int): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

        return history.loadOlder(limit)
    }

    override suspend fun jumpToBeginning(
        providerDialogId: String,
        limit: Int
    ): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

        return history.jumpToBeginning(limit)
    }

    override suspend fun loadNewerMessages(
        providerDialogId: String,
        limit: Int
    ): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

        return history.loadGap(limit)
    }

    override suspend fun loadMessagesUntil(
        providerDialogId: String,
        messageId: Long
    ): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

        return history.loadUntilLoaded(messageId)
    }

    override suspend fun loadMessagesAround(
        providerDialogId: String,
        messageId: Long,
        radius: Int
    ): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

        return history.loadAround(messageId, radius)
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

        // Show the cached dialog list before the first TDLib update arrives.
        scope.launch {
            val cached = CacheStore.loadDialogs(accountId)

            if (cached.isNotEmpty() && !closed) {
                restoredDialogs = cached
                _dialogs.value = mergeDialogs(cached, _dialogs.value)
            }
        }

        // Keep the dialog list and the loaded message windows in the database
        // so a restart can show them before TDLib replays its updates.
        scope.launch {
            var savedDialogs: List<Dialog>? = null
            val savedHistories = mutableMapOf<String, List<ChatMessage>>()

            while (!closed) {
                delay(2_000)

                val dialogs = _dialogs.value
                if (dialogs.isNotEmpty() && dialogs !== savedDialogs) {
                    CacheStore.saveDialogs(accountId, dialogs)
                    savedDialogs = dialogs
                }

                histories.value.forEach { (dialogId, controller) ->
                    val state = controller.state.value
                    val messages = state.messages

                    // Never overwrite a good newest-page cache with an older
                    // jump block: the next launch would restore a middle
                    // slice as if it were the end of the chat.
                    if (
                        messages.isNotEmpty() &&
                        !state.hasMoreNewer &&
                        !state.isLoadingInitial &&
                        savedHistories[dialogId] !== messages
                    ) {
                        CacheStore.saveMessages(accountId, dialogId, messages)
                        savedHistories[dialogId] = messages
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

        val result = try {
            when (challenge.id) {
                "phone" -> {
                    if (phoneNumber == null) {
                        setChallengeError(
                            id = challenge.id,
                            message = "Enter a phone number with the country code, " +
                                "for example +12345678900."
                        )
                        return
                    }

                    // A missed-call code is visible in the phone's call log
                    // even when this app runs on a desktop, so allowing it
                    // gives the server a channel the user can actually read.
                    // Flash calls need call-log access we don't have, so they
                    // stay disabled.
                    client.setAuthenticationPhoneNumber(
                        phoneNumber = phoneNumber,
                        settings = PhoneNumberAuthenticationSettings(
                            allowFlashCall = true,
                            allowMissedCall = true,
                            isCurrentPhoneNumber = true,
                            hasUnknownPhoneNumber = false,
                            allowSmsRetrieverApi = true,
                            firebaseAuthenticationSettings = FirebaseAuthenticationSettingsAndroid(),
                            authenticationTokens = emptyArray()
                        )
                    )
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
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // A thrown request must not leave the form stuck without telling
            // the user why the code was never requested.
            setChallengeError(
                id = challenge.id,
                message = authErrorMessage(
                    exception.message ?: "Telegram request failed"
                )
            )
            return
        }

        val failure = result as? TdlResult.Failure
        failure?.let { failed ->
            println("Telegram auth request failed: ${failed.message}")
        }

        stateMutex.withLock {
            val current = currentChallenge
            if (current?.id == challenge.id) {
                val updated = current.copy(
                    isSubmitting = false,
                    error = failure?.let { failed ->
                        authErrorMessage(failed.message)
                    },
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

        val result = try {
            when (action) {
                AuthAction.ResendCode -> client.resendAuthenticationCode(
                    reason = ResendCodeReasonUserRequest()
                )
                AuthAction.RecoverPassword -> client.requestAuthenticationPasswordRecovery()
                AuthAction.UseQrCode -> client.requestQrCodeAuthentication(
                    otherUserIds = longArrayOf()
                )
                AuthAction.Submit,
                AuthAction.RefreshCaptcha,
                is AuthAction.OpenUrl -> return
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            setChallengeError(
                id = challenge.id,
                message = authErrorMessage(
                    exception.message ?: "Telegram request failed"
                )
            )
            return
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
                println("Telegram auth action failed: ${result.message}")

                stateMutex.withLock {
                    val current = currentChallenge
                    if (current?.id == challenge.id) {
                        val updated = current.copy(
                            isSubmitting = false,
                            error = authErrorMessage(result.message)
                        )

                        currentChallenge = updated
                        _authState.value = AuthState.Challenge(updated)
                    }
                }
            }
        }
    }

    override suspend fun confirmQrCodeAuthentication(link: String): Result<Unit> {
        if (link.isBlank()) {
            return Result.failure(IllegalArgumentException("The QR code is empty"))
        }

        return try {
            when (val result = client.confirmQrCodeAuthentication(link)) {
                is TdlResult.Success -> Result.success(Unit)
                is TdlResult.Failure -> Result.failure(
                    Exception(authErrorMessage(result.message))
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun addMessageReaction(
        providerDialogId: String,
        messageId: Long,
        emoji: String,
        customEmojiId: Long?
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

        return try {
            when (val result = client.addMessageReaction(
                chatId = chatId,
                messageId = messageId,
                reactionType = reactionType(emoji, customEmojiId),
                isBig = false,
                updateRecentReactions = true
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

    override suspend fun removeMessageReaction(
        providerDialogId: String,
        messageId: Long,
        emoji: String,
        customEmojiId: Long?
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

        return try {
            when (val result = client.removeMessageReaction(
                chatId = chatId,
                messageId = messageId,
                reactionType = reactionType(emoji, customEmojiId)
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

    override suspend fun loadAvailableReactions(
        providerDialogId: String,
        messageId: Long
    ): Result<List<MessageReactionOption>> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

        return try {
            when (val result = client.getMessageAvailableReactions(
                chatId = chatId,
                messageId = messageId,
                rowSize = 4
            )) {
                is TdlResult.Success -> {
                    val available = result.result

                    val reactions = buildList {
                        addAll(available.topReactions.mapNotNull { it.toOption() })
                        addAll(available.recentReactions.mapNotNull { it.toOption() })
                        addAll(available.popularReactions.mapNotNull { it.toOption() })
                    }

                    Result.success(
                        reactions.distinctBy { it.key }.take(32)
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


    /**
     * Sends a GIF that came from another provider. The bytes are stored as a
     * local file, which TDLib uploads as an animation; a file that Telegram
     * rejects as an animation still goes out as a document.
     */


    /** Channels where this account may post: administrators and creators. */
    internal val _channelPostRights = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    internal val _channelPostRightRequests = MutableStateFlow<Set<Long>>(emptySet())


    override suspend fun loadFile(fileId: Int): Result<ByteArray> = loadFileImpl(fileId)
    override suspend fun loadCachedMedia(remoteUniqueId: String): ByteArray? = loadCachedMediaImpl(remoteUniqueId)
    override suspend fun loadFileIfUnique(fileId: Int, uniqueId: String): Result<ByteArray> = loadFileIfUniqueImpl(fileId, uniqueId)
    override suspend fun loadMessageThread(providerDialogId: String, messageId: Long, limit: Int): Result<com.mrboomdev.scrollix.data.model.ChatMessageThread> = loadMessageThreadImpl(providerDialogId, messageId, limit)
    override suspend fun sendMessageToThread(threadChatId: String, rootMessageId: Long, text: String, customEmojis: List<CustomEmojiSpan>): Result<Unit> = sendMessageToThreadImpl(threadChatId, rootMessageId, text, customEmojis)
    override suspend fun sendMessage(providerDialogId: String, text: String, replyToMessageId: Long?, customEmojis: List<CustomEmojiSpan>): Result<Unit> = sendMessageImpl(providerDialogId, text, replyToMessageId, customEmojis)
    override suspend fun sendFile(providerDialogId: String, path: String, asPhoto: Boolean): Result<Unit> = sendFileImpl(providerDialogId, path, asPhoto)
    override suspend fun sendVoiceNote(providerDialogId: String, path: String, durationSeconds: Int): Result<Unit> = sendVoiceNoteImpl(providerDialogId, path, durationSeconds)
    override suspend fun sendVideoNote(providerDialogId: String, path: String, durationSeconds: Int): Result<Unit> = sendVideoNoteImpl(providerDialogId, path, durationSeconds)
    override suspend fun sendPoll(providerDialogId: String, question: String, options: List<String>, isAnonymous: Boolean, allowsMultipleAnswers: Boolean, allowsRevoting: Boolean, questionCustomEmojis: List<CustomEmojiSpan>, optionCustomEmojis: List<List<CustomEmojiSpan>>, optionMediaPaths: List<String?>, isQuiz: Boolean, correctOptionIndex: Int?, explanation: String): Result<Unit> = sendPollImpl(providerDialogId, question, options, isAnonymous, allowsMultipleAnswers, allowsRevoting, questionCustomEmojis, optionCustomEmojis, optionMediaPaths, isQuiz, correctOptionIndex, explanation)
    override suspend fun sendLocation(providerDialogId: String, latitude: Double, longitude: Double, livePeriodSeconds: Int): Result<Long> = sendLocationImpl(providerDialogId, latitude, longitude, livePeriodSeconds)
    override suspend fun stopLiveLocation(providerDialogId: String, messageId: Long): Result<Unit> = stopLiveLocationImpl(providerDialogId, messageId)
    override suspend fun sendFiles(providerDialogId: String, attachments: List<OutgoingAttachment>, caption: String, replyToMessageId: Long?, customEmojis: List<CustomEmojiSpan>): Result<Unit> = sendFilesImpl(providerDialogId, attachments, caption, replyToMessageId, customEmojis)
    override suspend fun forwardMessages(providerDialogId: String, messageIds: LongArray, targetDialogId: String): Result<Unit> = forwardMessagesImpl(providerDialogId, messageIds, targetDialogId)
    override suspend fun deleteMessages(providerDialogId: String, messageIds: LongArray): Result<Unit> = deleteMessagesImpl(providerDialogId, messageIds)
    override suspend fun setMessagePinned(providerDialogId: String, messageId: Long, isPinned: Boolean): Result<Unit> = setMessagePinnedImpl(providerDialogId, messageId, isPinned)
    override suspend fun loadPinnedMessageId(providerDialogId: String): Result<Long?> = loadPinnedMessageIdImpl(providerDialogId)
    override suspend fun editMessage(providerDialogId: String, messageId: Long, text: String, customEmojis: List<CustomEmojiSpan>): Result<Unit> = editMessageImpl(providerDialogId, messageId, text, customEmojis)
    override suspend fun addContact(userId: Long, firstName: String, lastName: String): Result<Unit> = addContactImpl(userId, firstName, lastName)
    override suspend fun removeContact(userId: Long): Result<Unit> = removeContactImpl(userId)
    override suspend fun setUserBlocked(userId: Long, blocked: Boolean): Result<Unit> = setUserBlockedImpl(userId, blocked)
    override suspend fun votePoll(providerDialogId: String, messageId: Long, optionIds: List<String>): Result<Unit> = votePollImpl(providerDialogId, messageId, optionIds)
    override suspend fun loadMessage(providerDialogId: String, messageId: Long): Result<ChatMessage?> = loadMessageImpl(providerDialogId, messageId)
    override suspend fun markMessagesRead(providerDialogId: String, messageIds: LongArray) = markMessagesReadImpl(providerDialogId, messageIds)
    override suspend fun loadDraft(providerDialogId: String): Result<MessageDraft> = loadDraftImpl(providerDialogId)
    override suspend fun saveDraft(providerDialogId: String, text: String, customEmojis: List<CustomEmojiSpan>): Result<Unit> = saveDraftImpl(providerDialogId, text, customEmojis)

    override suspend fun searchMessages(providerDialogId: String, query: String, limit: Int): Result<List<ChatMessage>> = searchMessagesImpl(providerDialogId, query, limit)
    override suspend fun searchGlobalMessages(query: String, limit: Int): Result<List<ChatMessage>> = searchGlobalMessagesImpl(query, limit)
    override suspend fun searchChats(query: String, limit: Int): Result<List<Dialog>> = searchChatsImpl(query, limit)
    override suspend fun searchPublicChats(query: String, limit: Int, kind: PublicChatSearchKind): Result<List<Dialog>> = searchPublicChatsImpl(query, limit, kind)
    override suspend fun searchPublicPosts(query: String, offset: String, limit: Int, starCount: Long): Result<PostSearchPage> = searchPublicPostsImpl(query, offset, limit, starCount)
    override suspend fun loadPublicPostSearchQuota(query: String): Result<PostSearchQuota> = loadPublicPostSearchQuotaImpl(query)
    override suspend fun resolveUsername(username: String): Result<Long> = resolveUsernameImpl(username)
    override suspend fun resolveTelegramLink(url: String): Result<String> = resolveTelegramLinkImpl(url)
    override suspend fun resolveTelegramLinkTarget(url: String): Result<TelegramLinkTarget> = resolveTelegramLinkTargetImpl(url)
    override suspend fun loadMentionAvatars(text: String): Map<String, Avatar> = loadMentionAvatarsImpl(text)

    override suspend fun loadNotificationSettings(): Result<NotificationSettings> = loadNotificationSettingsImpl()
    override suspend fun setScopeMuted(scope: NotificationScope, muted: Boolean): Result<Unit> = setScopeMutedImpl(scope, muted)
    override suspend fun setDialogMuted(providerDialogId: String, muted: Boolean): Result<Unit> = setDialogMutedImpl(providerDialogId, muted)
    override suspend fun loadNotificationExceptions(scope: NotificationScope): Result<List<NotificationException>> = loadNotificationExceptionsImpl(scope)
    override suspend fun resetDialogNotificationSettings(providerDialogId: String): Result<Unit> = resetDialogNotificationSettingsImpl(providerDialogId)
    override suspend fun loadTopics(providerDialogId: String): Result<List<ChatTopic>> = loadTopicsImpl(providerDialogId)
    override suspend fun loadTopicMessages(providerDialogId: String, topicId: Long, limit: Int): Result<List<ChatMessage>> = loadTopicMessagesImpl(providerDialogId, topicId, limit)
    override suspend fun sendTopicMessage(providerDialogId: String, topicId: Long, text: String, customEmojis: List<CustomEmojiSpan>): Result<Unit> = sendTopicMessageImpl(providerDialogId, topicId, text, customEmojis)
    override suspend fun isDialogMuted(providerDialogId: String): Boolean? = isDialogMutedImpl(providerDialogId)
    override suspend fun loadDataUsage(): Result<DataUsageSummary> = loadDataUsageImpl()
    override suspend fun resetDataUsageStatistics(): Result<Unit> = resetDataUsageStatisticsImpl()
    override suspend fun applyDataUsagePreset(preset: DataUsagePreset): Result<Unit> = applyDataUsagePresetImpl(preset)
    override suspend fun loadActiveSessions(): Result<List<ActiveSessionInfo>> = loadActiveSessionsImpl()
    override suspend fun terminateSession(sessionId: Long): Result<Unit> = terminateSessionImpl(sessionId)
    override suspend fun terminateOtherSessions(): Result<Unit> = terminateOtherSessionsImpl()

    override suspend fun reorderPinnedDialogs(providerDialogIds: List<String>, folderId: Int?): Result<Unit> = reorderPinnedDialogsImpl(providerDialogIds, folderId)
    override suspend fun deleteDialog(providerDialogId: String, revoke: Boolean): Result<Unit> = deleteDialogImpl(providerDialogId, revoke)
    override suspend fun setDialogPinned(providerDialogId: String, isPinned: Boolean, folderId: Int?): Result<Unit> = setDialogPinnedImpl(providerDialogId, isPinned, folderId)
    override suspend fun setChatSubscribed(providerDialogId: String, subscribed: Boolean): Result<Unit> = setChatSubscribedImpl(providerDialogId, subscribed)
    override suspend fun loadOwnProfile(): Result<UserProfileDetails> = loadOwnProfileImpl()
    override suspend fun loadUserProfile(providerDialogId: String, userId: Long?): Result<UserProfileDetails> = loadUserProfileImpl(providerDialogId, userId)
    override suspend fun loadProfilePhotos(userId: Long): Result<List<MessageMedia>> = loadProfilePhotosImpl(userId)
    override suspend fun loadSharedMedia(providerDialogId: String, kind: SharedMediaKind, fromMessageId: Long, limit: Int): Result<List<ChatMessage>> = loadSharedMediaImpl(providerDialogId, kind, fromMessageId, limit)
    override suspend fun loadCustomEmoji(emojiId: Long): CustomEmojiAsset? = loadCustomEmojiImpl(emojiId)
    override suspend fun loadCustomEmojiSetIds(emojiIds: List<Long>): Map<Long, Long> = loadCustomEmojiSetIdsImpl(emojiIds)
    override suspend fun loadMessageReadDate(providerDialogId: String, messageId: Long): Result<Long?> = loadMessageReadDateImpl(providerDialogId, messageId)
    override suspend fun openWebApp(providerDialogId: String, button: MessageButton): Result<WebAppLaunch> = openWebAppImpl(providerDialogId, button)
    override suspend fun openMainWebApp(providerDialogId: String): Result<WebAppLaunch> = openMainWebAppImpl(providerDialogId)
    override suspend fun closeWebApp(launchId: Long) = closeWebAppImpl(launchId)

    override suspend fun loadStickerSet(setId: Long): Result<StickerSetInfo> = loadStickerSetImpl(setId)
    override suspend fun loadStickerSetByName(name: String, kind: StickerPackKind): Result<StickerSetInfo> = loadStickerSetByNameImpl(name, kind)
    override suspend fun setStickerSetInstalled(setId: Long, installed: Boolean): Result<Unit> = setStickerSetInstalledImpl(setId, installed)
    override suspend fun loadStickerPacks(kind: StickerPackKind): Result<List<StickerPack>> = loadStickerPacksImpl(kind)
    override suspend fun loadRecentStickers(kind: StickerPackKind): Result<List<MessageMedia>> = loadRecentStickersImpl(kind)
    override suspend fun removeRecentSticker(sticker: MessageMedia): Result<Unit> = removeRecentStickerImpl(sticker)
    override suspend fun clearRecentStickers(): Result<Unit> = clearRecentStickersImpl()
    override suspend fun loadSavedAnimations(): Result<List<MessageMedia>> = loadSavedAnimationsImpl()
    override suspend fun removeSavedAnimation(animation: MessageMedia): Result<Unit> = removeSavedAnimationImpl(animation)
    override suspend fun clearSavedAnimations(): Result<Unit> = clearSavedAnimationsImpl()
    override suspend fun sendSticker(providerDialogId: String, sticker: MessageMedia): Result<Unit> = sendStickerImpl(providerDialogId, sticker)
    override suspend fun sendStickerBytes(providerDialogId: String, bytes: ByteArray, format: MessageStickerFormat, width: Int, height: Int): Result<Unit> = sendStickerBytesImpl(providerDialogId, bytes, format, width, height)
    override suspend fun sendAnimation(providerDialogId: String, animation: MessageMedia): Result<Unit> = sendAnimationImpl(providerDialogId, animation)
    override suspend fun sendAnimationBytes(providerDialogId: String, bytes: ByteArray, mimeType: String?, width: Int, height: Int, durationSeconds: Int): Result<Unit> = sendAnimationBytesImpl(providerDialogId, bytes, mimeType, width, height, durationSeconds)

}
