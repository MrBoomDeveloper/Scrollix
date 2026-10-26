package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.AccountProfile
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.AuthAction
import com.mrboomdev.scrollix.data.model.AuthChallenge
import com.mrboomdev.scrollix.data.model.AuthField
import com.mrboomdev.scrollix.data.model.AuthFieldType
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogIdentity
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.PlatformStorage
import com.mrboomdev.scrollix.generated.BuildConfig
import com.mrboomdev.scrollix.getPlatform
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _profile = MutableStateFlow<AccountProfile?>(null)
    override val profile: StateFlow<AccountProfile?> = _profile.asStateFlow()

    private val _dialogs = MutableStateFlow<List<Dialog>>(emptyList())
    override val dialogs: StateFlow<List<Dialog>> = _dialogs.asStateFlow()

    private val stateMutex = Mutex()
    private val _chats = MutableStateFlow<Map<Long, ChatEntry>>(emptyMap())
    private val _users = MutableStateFlow<Map<Long, User>>(emptyMap())
    private val _requestedUsers = MutableStateFlow<Set<Long>>(emptySet())

    private var started = false
    private var parametersSet = false
    private var ready = false
    private var closed = false
    private var currentChallenge: AuthChallenge? = null
    private var currentUserId: Long? = null

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

            is UpdateChatIsMarkedAsUnread -> {
                updateChat(update.chatId) {
                    it.copy(isMarkedAsUnread = update.isMarkedAsUnread)
                }
            }

            is UpdateChatAddedToList -> {
                if (update.chatList is ChatListMain) {
                    when (val result = client.getChat(update.chatId)) {
                        is TdlResult.Success -> upsertChat(result.result)
                        is TdlResult.Failure -> Unit
                    }
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

            is UpdateUser -> {
                _users.update { it + (update.user.id to update.user) }

                if (update.user.id == currentUserId) {
                    updateProfile(update.user)
                }

                emitDialogs()
            }

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
                "Telegram sent the code by SMS to $phoneNumber. " +
                    "Check your messages. If it doesn't arrive, tap Resend code."
            }

            is AuthenticationCodeTypeCall -> {
                "Telegram will call $phoneNumber with the code. Answer the call."
            }

            is AuthenticationCodeTypeFlashCall -> {
                "Telegram will call $phoneNumber. The code is in the caller's phone number."
            }

            is AuthenticationCodeTypeMissedCall -> {
                "Telegram will call $phoneNumber. The last digits of the missed call are the code."
            }

            is AuthenticationCodeTypeFragment -> {
                "Telegram sent the code through Fragment. Check your Fragment account."
            }

            is AuthenticationCodeTypeFirebaseAndroid,
            is AuthenticationCodeTypeFirebaseIos -> {
                "Telegram sent the code through Firebase. Check the notification on this device."
            }

            is AuthenticationCodeTypeSmsWord -> {
                "Telegram sent an SMS with a code word to $phoneNumber. Check your messages."
            }

            is AuthenticationCodeTypeSmsPhrase -> {
                "Telegram sent an SMS with a code phrase to $phoneNumber. Check your messages."
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
        val displayName = listOf(user.firstName, user.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Telegram account" }

        val nickname = user.usernames?.activeUsernames?.firstOrNull()
        val avatar = user.profilePhoto?.minithumbnail?.data?.let { image ->
            Avatar(
                initials = Avatar.fromName(displayName).initials,
                colorSeed = displayName.hashCode(),
                image = image
            )
        } ?: Avatar.fromName(displayName)

        _profile.value = AccountProfile(
            displayName = displayName,
            nickname = nickname,
            avatar = avatar
        )
    }

    private suspend fun loadAllChats() {
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
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (!closed) {
                println("Failed to load Telegram dialogs: ${exception.message}")
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

            val avatarBytes = entry.photo?.minithumbnail?.data
                ?: user?.profilePhoto?.minithumbnail?.data

            Dialog(
                providerId = providerId,
                accountId = accountId,
                providerDialogId = entry.chatId.toString(),
                identity = identity,
                kind = kind,
                title = entry.title,
                preview = messagePreview(entry.lastMessage),
                timestamp = (entry.lastMessage?.date ?: 0).toLong() * 1000L,
                unreadCount = entry.unreadCount,
                isPinned = mainPosition.isPinned,
                isMarkedAsUnread = entry.isMarkedAsUnread,
                order = mainPosition.order,
                avatar = avatarBytes?.let { image ->
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
                .thenByDescending { it.timestamp }
                .thenByDescending { it.order }
        )

        _dialogs.value = dialogs
    }

    private fun messagePreview(message: Message?): String {
        if (message == null) return "No messages yet"

        val preview = when (val content = message.content) {
            is MessageText -> content.text.text
            is MessagePhoto -> "Photo"
            is MessageVideo -> "Video"
            is MessageSticker -> "Sticker"
            is MessageDocument -> "Document"
            is MessageAudio -> "Audio"
            is MessageVoiceNote -> "Voice message"
            is MessageVideoNote -> "Video message"
            is MessageAnimation -> "GIF"
            is MessagePoll -> "Poll"
            is MessageLocation -> "Location"
            is MessageContact -> "Contact"
            is MessageCall -> "Call"
            else -> content::class.simpleName?.removePrefix("Message") ?: "Message"
        }.replace('\n', ' ').trim().ifBlank { "Message" }

        return if (message.isOutgoing) "You: $preview" else preview
    }

    private data class ChatEntry(
        val chatId: Long,
        val type: ChatType,
        val title: String,
        val photo: ChatPhotoInfo?,
        val lastMessage: Message?,
        val positions: List<ChatPosition>,
        val unreadCount: Int,
        val isMarkedAsUnread: Boolean
    ) {
        companion object {
            fun from(chat: Chat): ChatEntry {
                return ChatEntry(
                    chatId = chat.id,
                    type = chat.type,
                    title = chat.title,
                    photo = chat.photo,
                    lastMessage = chat.lastMessage,
                    positions = chat.positions.filter { position ->
                        position.order != 0L
                    },
                    unreadCount = chat.unreadCount,
                    isMarkedAsUnread = chat.isMarkedAsUnread
                )
            }
        }
    }
}
