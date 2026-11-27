package com.mrboomdev.scrollix.data.vk

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlin.random.Random
import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.model.AccountProfile
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.AuthAction
import com.mrboomdev.scrollix.data.model.AuthChallenge
import com.mrboomdev.scrollix.data.model.AuthField
import com.mrboomdev.scrollix.data.model.AuthFieldType
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.CustomEmojiAsset
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.MessageScheduling
import com.mrboomdev.scrollix.data.model.MessageTextFormat
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.model.DownloadedFile
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.OutgoingAttachment
import com.mrboomdev.scrollix.data.model.StickerPack
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.model.StickerSetInfo
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.provider.MessageHistoryController
import com.mrboomdev.scrollix.data.provider.MessagePage
import com.mrboomdev.scrollix.data.provider.MessagePageLoader
import com.mrboomdev.scrollix.data.storage.CacheStore
import com.mrboomdev.scrollix.data.storage.PlatformStorage
import com.mrboomdev.scrollix.data.vk.network.VkApi
import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.SessionException
import com.mrboomdev.scrollix.data.model.asSessionException
import com.mrboomdev.scrollix.data.vk.network.VkApiException
import com.mrboomdev.scrollix.data.vk.network.vkUnsupported
import com.mrboomdev.scrollix.data.vk.network.array
import com.mrboomdev.scrollix.data.vk.network.int
import com.mrboomdev.scrollix.data.vk.network.long
import com.mrboomdev.scrollix.data.vk.network.obj
import com.mrboomdev.scrollix.data.vk.network.objects
import com.mrboomdev.scrollix.data.vk.network.parsePeerId
import com.mrboomdev.scrollix.data.vk.network.peerGroupId
import com.mrboomdev.scrollix.data.vk.network.peerUserId
import com.mrboomdev.scrollix.data.vk.network.string
import com.mrboomdev.scrollix.fetchUrlBytes

private const val TokenChallengeId = "vk-token"
private const val DialogsPageSize = 200
internal const val LongPollWaitSeconds = 25

/**
 * VK provider session.
 *
 * Authentication is token based: VK only issues user access tokens to a
 * registered application through its OAuth flow, and the provider does not
 * embed anyone's application credentials. [start] therefore restores a stored
 * token or asks for one, and the token is validated with `users.get` before it
 * is persisted.
 */
class VkSession(
    private val account: AccountRecord,
    internal val storage: PlatformStorage,
    parentScope: CoroutineScope
) : AccountSession {
    override val accountId: String = account.id
    override val providerId: String = VkProviderId

    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    internal val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job])
    )

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

    private val _downloads = MutableStateFlow<List<DownloadedFile>>(emptyList())
    override val downloads: StateFlow<List<DownloadedFile>> = _downloads.asStateFlow()

    internal val histories = MutableStateFlow<Map<String, MessageHistoryController>>(emptyMap())
    private val emptyHistory = MutableStateFlow(
        MessageHistory(hasMoreOlder = false, isLoadingInitial = false)
    ).asStateFlow()

    internal val users = linkedMapOf<Long, VkUser>()
    internal val groups = linkedMapOf<Long, VkGroup>()

    /** Synthetic file id -> remote URL for the media the session handed out. */
    internal val mediaUrls = mutableMapOf<Int, String>()

    internal var api: VkApi? = null
    private var token: String? = null
    private var meId: Long = 0
    private var meName: String = account.displayName

    private var started = false
    internal var closed = false
    internal var longPollJob: Job? = null
    private var refreshJob: Job? = null
    private val refreshMutex = Mutex()
    private var dialogsLoaded = false

    internal val sessionPath: String
        get() = "vk/$accountId/session.json"

    override fun messageHistory(providerDialogId: String): StateFlow<MessageHistory> {
        return historyFor(providerDialogId)?.state ?: emptyHistory
    }

    // ---------------------------------------------------------------- auth --

    override suspend fun start() {
        if (started) return
        started = true
        _authState.value = AuthState.Initializing

        val stored = readStoredSession()

        runCatching { CacheStore.loadDialogs(accountId) }
            .getOrNull()
            ?.takeIf { cached -> cached.isNotEmpty() && _dialogs.value.isEmpty() }
            ?.let { cached -> _dialogs.value = cached }

        if (stored == null) {
            _authState.value = AuthState.Challenge(tokenChallenge())
            return
        }

        try {
            val client = VkApi(stored.token, json)
            val user = loadUser(client, stored.userId)
                ?: throw VkApiException(5, "VK rejected the stored access token")

            token = stored.token
            api = client
            meId = user.id
            meName = user.name
            users[user.id] = user
            _profile.value = AccountProfile(displayName = user.name, avatar = user.avatar())

            _authState.value = AuthState.Ready
            refreshDialogs()
            startLongPoll()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: VkApiException) {
            if (exception.isAuthError) {
                clearStoredSession()
                _authState.value = AuthState.Challenge(tokenChallenge())
            } else {
                AppLog.warning(
                    "vk",
                    "Sign-in failed: ${exception.reason}",
                    exception
                )
                _authState.value = AuthState.Error(
                    "VK sign-in failed: ${exception.reason}"
                )
            }
        } catch (exception: Exception) {
            AppLog.error("vk", "Could not sign in to VK", exception)
            _authState.value = AuthState.Error(
                exception.message ?: "VK sign-in failed"
            )
        }
    }

    override suspend fun submitAuth(values: Map<String, String>) {
        val challenge = (authState.value as? AuthState.Challenge)?.challenge ?: return
        if (challenge.id != TokenChallengeId) return

        val candidate = values["token"]?.trim().orEmpty()
        if (candidate.isEmpty()) {
            _authState.value = AuthState.Challenge(
                tokenChallenge(error = "Paste an access token to sign in")
            )
            return
        }

        try {
            val client = VkApi(candidate, json)
            val user = loadUser(client, 0)
                ?: throw VkApiException(5, "The token does not belong to a VK user")

            token = candidate
            api = client
            meId = user.id
            meName = user.name
            users[user.id] = user
            _profile.value = AccountProfile(displayName = user.name, avatar = user.avatar())
            persistSession(candidate, user.id)

            _authState.value = AuthState.Ready
            refreshDialogs()
            startLongPoll()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: VkApiException) {
            AppLog.warning(
                "vk",
                "Token sign-in failed: ${exception.reason}",
                exception
            )
            _authState.value = AuthState.Challenge(
                tokenChallenge(
                    error = when {
                        exception.isFlood -> "VK is limiting requests. Wait and try again."
                        exception.isAuthError -> "VK rejected this token. Check it and retry."
                        else -> "VK: ${exception.reason}"
                    }
                )
            )
        } catch (exception: Exception) {
            AppLog.error("vk", "Could not sign in with the token", exception)
            _authState.value = AuthState.Error(exception.message ?: "VK sign-in failed")
        }
    }

    override suspend fun performAuthAction(action: AuthAction) {
        when (action) {
            is AuthAction.ResendCode, is AuthAction.RefreshCaptcha ->
                _authState.value = AuthState.Challenge(tokenChallenge())

            else -> Unit
        }
    }

    override suspend fun confirmQrCodeAuthentication(link: String): Result<Unit> =
        Result.failure(SessionException(SessionErrorCode.Unsupported, "VK has no QR sign-in here"))

    private fun tokenChallenge(error: String? = null): AuthChallenge = AuthChallenge(
        id = TokenChallengeId,
        title = "Sign in to VK",
        description = "VK requires a user access token for this app. Create an " +
            "application at dev.vk.com, authorize it with the messages scope, " +
            "and paste the access_token value from the redirect URL.",
        fields = listOf(
            AuthField(
                id = "token",
                label = "Access token",
                type = AuthFieldType.Password,
                placeholder = "vk1.a.…"
            )
        ),
        actions = listOf(AuthAction.Submit),
        error = error
    )

    // ------------------------------------------------------------- dialogs --

    internal suspend fun refreshDialogs() {
        if (closed) return

        refreshJob?.takeIf { job -> job.isActive }?.cancel()

        refreshJob = scope.launch {
            refreshMutex.withLock {
                if (!dialogsLoaded) _isLoadingDialogs.value = true

                try {
                    val client = api ?: return@withLock
                    val response = client.callObject(
                        "messages.getConversations",
                        mapOf(
                            "count" to "$DialogsPageSize",
                            "extended" to "1",
                            "fields" to "photo_100,photo_200,online,last_seen,status",
                            "filter" to "all"
                        )
                    )

                    indexExtended(response)

                    val mapped = response.array("items").objects().mapNotNull { item ->
                        val conversation = item.obj("conversation") ?: item
                        mapDialog(conversation)
                    }.sortedWith(
                        compareByDescending<Dialog> { dialog -> dialog.isPinned }
                            .thenByDescending { dialog -> dialog.timestamp }
                    )

                    _dialogs.value = mapped
                    dialogsLoaded = true

                    runCatching { CacheStore.saveDialogs(accountId, mapped) }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    if (!dialogsLoaded) {
                        _authState.value = AuthState.Error(
                            "Could not load VK chats: ${exception.message ?: "unknown error"}"
                        )
                    }
                } finally {
                    _isLoadingDialogs.value = false
                }
            }
        }
    }

    override suspend fun loadMessages(providerDialogId: String, limit: Int): Result<Unit> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(SessionException(SessionErrorCode.InvalidRequest, "Invalid VK peer id"))

        return history.loadLatest(limit)
    }

    override suspend fun loadOlderMessages(providerDialogId: String, limit: Int): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(SessionException(SessionErrorCode.InvalidRequest, "Invalid VK peer id"))

        return history.loadOlder(limit)
    }

    override suspend fun loadMessagesUntil(
        providerDialogId: String,
        messageId: Long
    ): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(SessionException(SessionErrorCode.InvalidRequest, "Invalid VK peer id"))

        return history.loadUntilLoaded(messageId)
    }

    override suspend fun loadMessagesAround(
        providerDialogId: String,
        messageId: Long,
        radius: Int
    ): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(SessionException(SessionErrorCode.InvalidRequest, "Invalid VK peer id"))

        return history.loadAround(messageId, radius)
    }

    private fun historyFor(providerDialogId: String): MessageHistoryController? {
        val peerId = parsePeerId(providerDialogId) ?: return null

        histories.value[providerDialogId]?.let { return it }

        val created = MessageHistoryController(
            pageLoader = object : MessagePageLoader {
                override suspend fun load(
                    fromMessageId: Long,
                    limit: Int
                ): Result<MessagePage> = try {
                    Result.success(fetchHistory(peerId, fromMessageId, limit))
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Result.failure(exception)
                }
            }
        )

        histories.update { existing ->
            existing + (providerDialogId to (existing[providerDialogId] ?: created))
        }

        return histories.value[providerDialogId]
    }

    private suspend fun fetchHistory(
        peerId: Long,
        fromMessageId: Long,
        limit: Int
    ): MessagePage {
        val client = api ?: throw SessionException(SessionErrorCode.Network, "VK is not connected")

        val params = buildMap {
            put("peer_id", peerId.toString())
            put("count", limit.coerceIn(1, 200).toString())
            put("rev", "1")
            put("extended", "1")
            if (fromMessageId > 0) put("start_message_id", fromMessageId.toString())
        }

        val response = client.callObject("messages.getHistory", params)
        indexExtended(response)

        val items = response.array("items").objects()
        val messages = items
            .mapNotNull { item -> mapMessage(peerId, item) }
            .filter { message -> fromMessageId == 0L || message.messageId <= fromMessageId }
            .sortedBy { message -> message.messageId }

        return MessagePage(
            messages = messages,
            totalCount = response.int("count")
        )
    }

    override suspend fun sendMessage(
        providerDialogId: String,
        text: String,
        replyToMessageId: Long?,
        customEmojis: List<CustomEmojiSpan>,
        formatting: List<MessageTextFormat>,
        scheduling: MessageScheduling?,
        sendSilently: Boolean,
        replyQuote: com.mrboomdev.scrollix.data.model.MessageReplyQuote?
    ): Result<Unit> = runCatchingApi {
        val peerId = parsePeerId(providerDialogId)
            ?: throw SessionException(SessionErrorCode.InvalidRequest, "Invalid VK peer id")
        val client = api ?: throw SessionException(SessionErrorCode.Network, "VK is not connected")

        client.call(
            "messages.send",
            mapOf(
                "peer_id" to peerId.toString(),
                "random_id" to Random.nextInt(1, Int.MAX_VALUE).toString(),
                "message" to text
            )
        )

        refreshDialogs()
    }

    override suspend fun sendFile(
        providerDialogId: String,
        path: String,
        asPhoto: Boolean
    ): Result<Unit> = sendFiles(
        providerDialogId = providerDialogId,
        attachments = listOf(OutgoingAttachment(path = path, asPhoto = asPhoto))
    )

    override suspend fun sendFiles(
        providerDialogId: String,
        attachments: List<OutgoingAttachment>,
        caption: String,
        replyToMessageId: Long?,
        customEmojis: List<CustomEmojiSpan>,
        formatting: List<MessageTextFormat>,
        scheduling: MessageScheduling?,
        sendSilently: Boolean
    ): Result<Unit> = runCatchingApi {
        if (attachments.isEmpty()) return@runCatchingApi

        val peerId = parsePeerId(providerDialogId)
            ?: throw SessionException(SessionErrorCode.InvalidRequest, "Invalid VK peer id")
        val client = api ?: throw SessionException(SessionErrorCode.Network, "VK is not connected")

        val uploaded = attachments.mapNotNull { attachment ->
            uploadAttachment(client, attachment)
        }

        if (uploaded.isEmpty()) {
            throw SessionException(SessionErrorCode.InvalidRequest, "VK did not accept the attachment")
        }

        client.call(
            "messages.send",
            mapOf(
                "peer_id" to peerId.toString(),
                "random_id" to Random.nextInt(1, Int.MAX_VALUE).toString(),
                "message" to caption,
                "attachment" to uploaded.joinToString(",")
            )
        )

        refreshDialogs()
    }

    override suspend fun markMessagesRead(providerDialogId: String, messageIds: LongArray) {
        val peerId = parsePeerId(providerDialogId) ?: return
        val lastId = messageIds.maxOrNull() ?: return

        runCatching {
            api?.call(
                "messages.markAsRead",
                mapOf(
                    "peer_id" to peerId.toString(),
                    "start_message_id" to lastId.toString()
                )
            )
        }
    }

    /**
     * VK stores a media caption in the message text, so `messages.edit` is the
     * caption call as well. An empty caption clears it.
     */
    override val supportsCaptionEditing: Boolean get() = true

    override suspend fun editMessageCaption(
        providerDialogId: String,
        messageId: Long,
        caption: String,
        customEmojis: List<CustomEmojiSpan>,
        formatting: List<MessageTextFormat>
    ): Result<Unit> = editMessage(
        providerDialogId = providerDialogId,
        messageId = messageId,
        text = caption,
        customEmojis = customEmojis,
        formatting = formatting
    )

    override suspend fun editMessage(
        providerDialogId: String,
        messageId: Long,
        text: String,
        customEmojis: List<CustomEmojiSpan>,
        formatting: List<MessageTextFormat>
    ): Result<Unit> = runCatchingApi {
        val peerId = parsePeerId(providerDialogId)
            ?: throw SessionException(SessionErrorCode.InvalidRequest, "Invalid VK peer id")

        api?.call(
            "messages.edit",
            mapOf(
                "peer_id" to peerId.toString(),
                "message_id" to messageId.toString(),
                "message" to text
            )
        )

        Unit
    }

    override suspend fun forwardMessages(
        providerDialogId: String,
        messageIds: LongArray,
        targetDialogId: String,
        hideSender: Boolean,
        removeCaption: Boolean,
        targetTopicId: Long?
    ): Result<Unit> = runCatchingApi {
        val target = parsePeerId(targetDialogId)
            ?: throw SessionException(SessionErrorCode.InvalidRequest, "Invalid VK peer id")

        val ids = buildJsonArray {
            messageIds.forEach { id -> add(kotlinx.serialization.json.JsonPrimitive(id)) }
        }.toString()

        api?.call(
            "messages.forward",
            mapOf(
                "peer_id" to target.toString(),
                "message_ids" to ids
            )
        )

        refreshDialogs()
    }

    override suspend fun deleteMessages(
        providerDialogId: String,
        messageIds: LongArray,
        forEveryone: Boolean
    ): Result<Unit> = runCatchingApi {
        val ids = buildJsonArray {
            messageIds.forEach { id -> add(kotlinx.serialization.json.JsonPrimitive(id)) }
        }.toString()

        api?.call(
            "messages.delete",
            mapOf(
                "message_ids" to ids,
                "delete_for_all" to if (forEveryone) "1" else "0",
                "spam" to "0"
            )
        )

        Unit
    }

    override suspend fun setMessagePinned(
        providerDialogId: String,
        messageId: Long,
        isPinned: Boolean
    ): Result<Unit> = runCatchingApi {
        val peerId = parsePeerId(providerDialogId)
            ?: throw SessionException(SessionErrorCode.InvalidRequest, "Invalid VK peer id")

        api?.call(
            if (isPinned) "messages.pin" else "messages.unpin",
            mapOf(
                "peer_id" to peerId.toString(),
                "message_id" to messageId.toString()
            )
        )

        Unit
    }

    override suspend fun addMessageReaction(
        providerDialogId: String,
        messageId: Long,
        emoji: String,
        customEmojiId: Long?
    ): Result<Unit> = unsupported("Message reactions")

    override suspend fun removeMessageReaction(
        providerDialogId: String,
        messageId: Long,
        emoji: String,
        customEmojiId: Long?
    ): Result<Unit> = unsupported("Message reactions")

    override suspend fun loadAvailableReactions(
        providerDialogId: String,
        messageId: Long
    ): Result<List<MessageReactionOption>> = Result.success(emptyList())

    override suspend fun loadPinnedMessageId(providerDialogId: String): Result<Long?> {
        val peerId = parsePeerId(providerDialogId)
            ?: return Result.failure(SessionException(SessionErrorCode.InvalidRequest, "Invalid VK peer id"))

        return try {
            val response = api?.callObject(
                "messages.getConversationsById",
                mapOf("peer_ids" to peerId.toString(), "extended" to "0")
            )

            Result.success(
                response?.array("items").objects().firstOrNull()
                    ?.obj("chat_settings")
                    ?.obj("pinned_message")
                    ?.long("id")
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun loadMessage(
        providerDialogId: String,
        messageId: Long
    ): Result<ChatMessage?> {
        val peerId = parsePeerId(providerDialogId)
            ?: return Result.failure(SessionException(SessionErrorCode.InvalidRequest, "Invalid VK peer id"))

        return try {
            val response = api?.callObject(
                "messages.getById",
                mapOf(
                    "message_ids" to messageId.toString(),
                    "extended" to "1",
                    "fields" to "photo_100,photo_200,status"
                )
            )

            indexExtended(response ?: JsonObject(emptyMap()))

            Result.success(
                response?.array("items").objects().firstOrNull()
                    ?.let { item -> mapMessage(peerId, item) }
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun loadMessageReadDate(
        providerDialogId: String,
        messageId: Long
    ): Result<Long?> = Result.success(null)

    override suspend fun loadFile(fileId: Int): Result<ByteArray> {
        val url = mediaUrls[fileId]
            ?: return Result.failure(SessionException(SessionErrorCode.InvalidRequest, "Unknown VK file id"))

        return runCatchingApi {
            fetchUrlBytes(url) ?: throw SessionException(SessionErrorCode.Network, "VK media download failed")
        }
    }

    // -------------------------------------------------------------- profiles --

    override suspend fun loadUserProfile(
        providerDialogId: String,
        userId: Long?
    ): Result<UserProfileDetails> {
        val peerId = parsePeerId(providerDialogId)
            ?: return Result.failure(SessionException(SessionErrorCode.InvalidRequest, "Invalid VK peer id"))

        return runCatchingApi {
            peerUserId(userId ?: peerId)?.let { resolveUserProfile(it) }
                ?: resolveGroupProfile(peerGroupId(peerId) ?: 0)
        }
    }

    override suspend fun loadOwnProfile(): Result<UserProfileDetails> = runCatchingApi {
        resolveUserProfile(meId)
    }

    override suspend fun setChatSubscribed(
        providerDialogId: String,
        subscribed: Boolean
    ): Result<Unit> = runCatchingApi {
        val peerId = parsePeerId(providerDialogId)
            ?: throw SessionException(SessionErrorCode.InvalidRequest, "Invalid VK peer id")
        val groupId = peerGroupId(peerId)
            ?: throw SessionException(SessionErrorCode.Unsupported, "Only communities can be joined")

        api?.call(
            if (subscribed) "groups.join" else "groups.leave",
            mapOf("group_id" to groupId.toString())
        )

        refreshDialogs()
    }

    override suspend fun votePoll(
        providerDialogId: String,
        messageId: Long,
        optionIds: List<String>
    ): Result<Unit> = unsupported("Polls")

    override suspend fun searchMessages(
        providerDialogId: String,
        query: String,
        limit: Int,
        fromMessageId: Long?
    ): Result<List<ChatMessage>> {
        val peerId = parsePeerId(providerDialogId)
            ?: return Result.failure(SessionException(SessionErrorCode.InvalidRequest, "Invalid VK peer id"))

        return runCatchingApi {
            val response = api?.callObject(
                "messages.search",
                mapOf(
                    "peer_id" to peerId.toString(),
                    "q" to query,
                    "count" to limit.coerceIn(1, 100).toString(),
                    "extended" to "1",
                    "fields" to "photo_100"
                )
            )

            indexExtended(response ?: JsonObject(emptyMap()))

            response?.array("items").objects()
                .mapNotNull { item -> mapMessage(peerId, item) }
                .sortedBy { message -> message.messageId }
                .orEmpty()
        }
    }

    override suspend fun searchGlobalMessages(
        query: String,
        limit: Int,
        minTimestamp: Long,
        maxTimestamp: Long
    ): Result<List<ChatMessage>> = runCatchingApi {
        val response = api?.callObject(
            "messages.search",
            mapOf(
                "q" to query,
                "count" to limit.coerceIn(1, 100).toString(),
                "extended" to "1",
                "fields" to "photo_100"
            )
        )

        indexExtended(response ?: JsonObject(emptyMap()))

        response?.array("items").objects()
            .mapNotNull { item ->
                val peerId = item.long("peer_id") ?: meId
                mapMessage(peerId, item)
            }
            .sortedByDescending { message -> message.timestamp }
            .orEmpty()
    }

    override suspend fun searchChats(
        query: String,
        limit: Int
    ): Result<List<Dialog>> = runCatchingApi {
        val response = api?.callObject(
            "messages.searchConversations",
            mapOf(
                "q" to query,
                "count" to limit.coerceIn(1, 100).toString(),
                "extended" to "1",
                "fields" to "photo_100,photo_200"
            )
        )

        indexExtended(response ?: JsonObject(emptyMap()))

        response?.array("items").objects()
            .mapNotNull { item ->
                val conversation = item.obj("conversation") ?: item
                mapDialog(conversation)
            }
            .orEmpty()
    }

    override suspend fun resolveUsername(username: String): Result<Long> = runCatchingApi {
        val response = api?.callObject(
            "utils.resolveScreenName",
            mapOf("screen_name" to username.removePrefix("@"))
        )

        val type = response?.string("type")
        val objectId = response?.long("object_id")
            ?: throw SessionException(SessionErrorCode.NotFound, "VK could not resolve @$username")

        when (type) {
            "user" -> objectId
            "group", "page" -> -objectId
            else -> objectId
        }
    }

    override suspend fun loadCustomEmoji(emojiId: Long): CustomEmojiAsset? = null

    // ------------------------------------------------------------- stickers --

    override suspend fun loadStickerSet(setId: Long): Result<StickerSetInfo> =
        unsupported("Sticker sets")

    override suspend fun setStickerSetInstalled(
        setId: Long,
        installed: Boolean
    ): Result<Unit> = unsupported("Sticker sets")

    override suspend fun loadStickerPacks(kind: StickerPackKind): Result<List<StickerPack>> =
        Result.success(emptyList())

    override suspend fun loadRecentStickers(kind: StickerPackKind): Result<List<MessageMedia>> =
        Result.success(emptyList())

    override suspend fun removeRecentSticker(sticker: MessageMedia): Result<Unit> =
        Result.success(Unit)

    override suspend fun clearRecentStickers(): Result<Unit> = Result.success(Unit)

    override suspend fun loadSavedAnimations(): Result<List<MessageMedia>> =
        Result.success(emptyList())

    override suspend fun removeSavedAnimation(animation: MessageMedia): Result<Unit> =
        Result.success(Unit)

    override suspend fun clearSavedAnimations(): Result<Unit> = Result.success(Unit)

    override suspend fun sendSticker(
        providerDialogId: String,
        sticker: MessageMedia
    ): Result<Unit> = unsupported("Stickers")

    override suspend fun sendAnimation(
        providerDialogId: String,
        animation: MessageMedia
    ): Result<Unit> = unsupported("Animations")

    override suspend fun setDialogPinned(
        providerDialogId: String,
        isPinned: Boolean,
        folderId: Int?
    ): Result<Unit> = unsupported("Pinning chats")

    // ------------------------------------------------------------- long poll --

    override suspend fun logout() {
        clearStoredSession()
        api = null
        token = null
        close()
    }

    override suspend fun close() {
        if (closed) return
        closed = true
        longPollJob?.cancel()
        longPollJob = null
        refreshJob?.cancel()
        refreshJob = null
        scope.cancel()
        _authState.value = AuthState.Closed
    }

    // -------------------------------------------------------------- helpers --

    private inline fun <T> runCatchingApi(block: () -> T?): Result<T> {
        return try {
            val value = block()
                ?: return Result.failure(
                    SessionException(SessionErrorCode.Unknown, "VK did not return a result")
                )
            Result.success(value)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception.asSessionException())
        }
    }

    private fun <T> unsupported(feature: String): Result<T> =
        Result.failure(vkUnsupported(feature))
}

internal data class StoredVkSession(
    val token: String,
    val userId: Long
)

internal data class VkUser(
    val id: Long,
    val name: String,
    val photoUrl: String?,
    val status: String? = null
) {
    fun avatar(): Avatar = Avatar(
        initials = name.split(' ')
            .filter(String::isNotBlank)
            .take(2)
            .joinToString("") { part -> part.take(1) }
            .ifBlank { "?" },
        colorSeed = name.hashCode(),
        url = photoUrl
    )
}

internal data class VkGroup(
    val id: Long,
    val name: String,
    val photoUrl: String?,
    val membersCount: Int? = null
) {
    fun avatar(): Avatar = Avatar(
        initials = name.take(1),
        colorSeed = name.hashCode(),
        url = photoUrl
    )
}
