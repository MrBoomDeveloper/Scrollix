package com.mrboomdev.scrollix.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random
import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.max.network.MaxApiException
import com.mrboomdev.scrollix.data.max.network.MaxConnectionClosedException
import com.mrboomdev.scrollix.data.max.network.maxFailure
import com.mrboomdev.scrollix.data.max.network.maxInvalidChatId
import com.mrboomdev.scrollix.data.max.network.maxNotConnected
import com.mrboomdev.scrollix.data.max.network.maxNotSignedIn
import com.mrboomdev.scrollix.data.max.network.maxUnsupported
import com.mrboomdev.scrollix.data.max.network.MaxUploadUserAgent
import com.mrboomdev.scrollix.data.max.network.MaxPush
import com.mrboomdev.scrollix.data.max.network.MaxRpc
import com.mrboomdev.scrollix.data.max.network.array
import com.mrboomdev.scrollix.data.max.network.asLongOrNull
import com.mrboomdev.scrollix.data.max.network.bool
import com.mrboomdev.scrollix.data.max.network.createMaxSocket
import com.mrboomdev.scrollix.data.max.network.int
import com.mrboomdev.scrollix.data.max.network.long
import com.mrboomdev.scrollix.data.max.network.double
import com.mrboomdev.scrollix.data.max.network.normalizeMaxPhone
import com.mrboomdev.scrollix.data.max.network.obj
import com.mrboomdev.scrollix.data.max.network.objects
import com.mrboomdev.scrollix.data.max.network.string
import com.mrboomdev.scrollix.data.model.AccountProfile
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.SessionException
import com.mrboomdev.scrollix.data.model.asSessionException
import com.mrboomdev.scrollix.data.model.AuthAction
import com.mrboomdev.scrollix.data.model.AuthCaptchaValueId
import com.mrboomdev.scrollix.data.model.AuthChallenge
import com.mrboomdev.scrollix.data.model.AuthField
import com.mrboomdev.scrollix.data.model.AuthFieldType
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMemberInfo
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.CustomEmojiAsset
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.model.DialogIdentity
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.DownloadedFile
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageLocationInfo
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessagePollInfo
import com.mrboomdev.scrollix.data.model.MessagePollOptionInfo
import com.mrboomdev.scrollix.data.model.MessageReactionInfo
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.model.SharedMediaKind
import com.mrboomdev.scrollix.data.model.StickerPack
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.model.StickerSetInfo
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.provider.MessageHistoryController
import com.mrboomdev.scrollix.data.provider.MessagePage
import com.mrboomdev.scrollix.data.provider.MessagePageLoader
import com.mrboomdev.scrollix.data.storage.formatPhoneNumber
import com.mrboomdev.scrollix.data.max.network.uploadMaxFile
import com.mrboomdev.scrollix.data.max.network.uploadMaxRaw
import com.mrboomdev.scrollix.data.storage.CacheStore
import com.mrboomdev.scrollix.data.storage.PlatformStorage
import com.mrboomdev.scrollix.fetchUrlBytes
import com.mrboomdev.scrollix.getPlatform
import com.mrboomdev.scrollix.media.renderStickerAnimationGif

// The current web client (web.max.ru bundle) connects to this host; the old
// ws-api.oneme.ru accepts TCP but never answers the WebSocket upgrade.
internal const val SavedDialogWelcomeKey = "welcome.saved.dialog.message"

internal const val MaxWebSocketUrl = "wss://api.oneme.ru/websocket"
internal const val MaxOrigin = "https://web.max.ru"
internal const val MaxConnectTimeoutMillis = 60_000L
// The WebSocket endpoint only accepts a WEB session ("session init forbidden"
// for deviceType ANDROID), so this mirrors the current real web client.
internal const val MaxAppVersion = "26.11.9"
internal const val MaxBuildNumber = 22367
internal const val MaxDeviceArch = "x86_64"
internal const val MaxDeviceName = "Chrome"
internal const val MaxOsVersion = "Windows 10"
internal const val MaxScreen = "1920x1080 1.0x"
internal const val MaxTimezone = "Europe/Moscow"
private const val SyntheticStickerPackId = Long.MIN_VALUE + 4096
internal const val SyntheticStickerPackTitle = "MAX stickers"

internal const val PhoneChallengeId = "phone"
internal const val CodeChallengeId = "code"
internal const val CaptchaChallengeId = "max-captcha"
internal const val PasswordChallengeId = "max-password"

// CAPTCHA_SESSION (web client opcode 224): creates the VK not-a-robot check and
// answers with the widget link that has to be solved before AUTH_REQUEST.
internal const val CaptchaSessionOpcode = 224


internal data class MaxUser(
    val id: Long,
    val name: String,
    val pictureUrl: String?,
    val username: String?,
    val phone: String?,
    val description: String?,
    val isVerified: Boolean = false,
    val isServiceAccount: Boolean = false
)


/**
 * MAX (ru.oneme.app) session over the reverse-engineered OneMe JSON protocol.
 *
 * The transport is the web WebSocket endpoint (`ver=11`): every request is an
 * opcode frame matched by `seq`, while `cmd=0` frames are live updates.
 * Sign-in uses MAX's SMS flow (VERIFICATION_REQUEST/CODE_ENTER) followed by
 * LOGIN, and the resulting token is stored per account.
 */
class MaxSession(
    account: AccountRecord,
    internal val storage: PlatformStorage,
    parentScope: CoroutineScope
) : AccountSession {
    override val accountId: String = account.id
    override val providerId: String = MaxProvider.id

    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    internal val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job])
    )

    internal val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    internal val _isConnected = MutableStateFlow(true)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    internal val _profile = MutableStateFlow<AccountProfile?>(null)
    override val profile: StateFlow<AccountProfile?> = _profile.asStateFlow()

    internal val _dialogs = MutableStateFlow<List<Dialog>>(emptyList())
    override val dialogs: StateFlow<List<Dialog>> = _dialogs.asStateFlow()

    private val _folders = MutableStateFlow<List<DialogFolder>>(emptyList())
    override val folders: StateFlow<List<DialogFolder>> = _folders.asStateFlow()

    internal val _isLoadingDialogs = MutableStateFlow(false)
    override val isLoadingDialogs: StateFlow<Boolean> = _isLoadingDialogs.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadedFile>>(emptyList())
    override val downloads: StateFlow<List<DownloadedFile>> = _downloads.asStateFlow()

    internal val histories = MutableStateFlow<Map<String, MessageHistoryController>>(emptyMap())
    private val emptyHistory = MutableStateFlow(
        MessageHistory(hasMoreOlder = false, isLoadingInitial = false)
    ).asStateFlow()

    internal val chats = linkedMapOf<Long, MaxChat>()
    internal val users = linkedMapOf<Long, MaxUser>()
    internal val pinnedChats = mutableSetOf<Long>()
    internal val typing = MutableStateFlow<Map<Long, String>>(emptyMap())
    internal val typingJobs = mutableMapOf<Long, Job>()
    internal val messageTimes = mutableMapOf<Long, Long>()

    /** Chats whose first message has been reached; older pages stop asking. */
    internal val historyStarts = mutableSetOf<Long>()

    internal val avatarBytes = mutableMapOf<String, ByteArray>()
    internal val maxStickerPacks = mutableMapOf<Long, StickerSetInfo>()
    internal val mediaIdsByUrl = mutableMapOf<String, Int>()
    internal val mediaUrlsById = mutableMapOf<Int, String>()
    private val mediaMemory = mutableMapOf<Int, ByteArray>()

    /**
     * Videos and GIFs are delivered without a URL; their id/token pair has to
     * be exchanged for a temporary MP4 link. The link is resolved lazily, when
     * the player actually needs the bytes.
     */
    internal val videoRefsById = mutableMapOf<Int, MaxVideoRef>()

    internal var rpc: MaxRpc? = null
    internal val connectMutex = Mutex()
    internal var pushJob: Job? = null
    internal var monitorJob: Job? = null
    internal var dialogsSaveJob: Job? = null
    internal var historySaveJob: Job? = null

    internal var started = false
    /** True after the first chat-list sync, so later refreshes stay silent. */
    internal var dialogsLoaded = false
    internal var closed = false
    internal var reconnectAttempts = 0
    internal var token: String? = null
    internal var deviceId: String = ""

    /**
     * LOGIN returns the newest page of chats plus `chatMarker`, the timestamp
     * from which GET_CHATS pages through the rest. Without it the chat list
     * only contains the few chats LOGIN happened to include.
     */
    internal var chatMarker: Long = 0L
    internal var meId: Long = 0
    internal var verifyToken: String? = null
    internal var passwordTrackId: String? = null
    internal var passwordHint: String? = null
    internal var phoneNumber: String? = null
    internal var pendingCodeType: String = "START_AUTH"
    internal var codeLength = 6

    internal val sessionPath: String
        get() = "max/$accountId/session.json"

    override fun messageHistory(providerDialogId: String): StateFlow<MessageHistory> {
        return historyFor(providerDialogId)?.state ?: emptyHistory
    }

    // ---------------------------------------------------------------- auth --


    override suspend fun submitAuth(values: Map<String, String>) {
        val challenge = (authState.value as? AuthState.Challenge)?.challenge ?: return

        when (challenge.id) {
            PhoneChallengeId -> requestCode(values["phone"].orEmpty(), "START_AUTH")
            CodeChallengeId -> submitCode(values["code"].orEmpty())
            PasswordChallengeId -> submitPassword(values["password"].orEmpty())
            CaptchaChallengeId -> submitCaptcha(values[AuthCaptchaValueId].orEmpty())
        }
    }

    override suspend fun performAuthAction(action: AuthAction) {
        when (action) {
            AuthAction.ResendCode -> phoneNumber?.let { phone -> requestCode(phone, "RESEND") }
            AuthAction.RefreshCaptcha -> phoneNumber?.let { phone -> requestCode(phone, pendingCodeType) }
            else -> Unit
        }
    }

    override suspend fun confirmQrCodeAuthentication(link: String): Result<Unit> {
        return Result.failure(
            maxFailure(
                "QR confirmation is not supported by the MAX client yet",
                SessionErrorCode.Unsupported
            )
        )
    }


    override suspend fun loadMessages(providerDialogId: String, limit: Int): Result<Unit> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(maxInvalidChatId())

        return history.loadLatest(limit)
    }

    override suspend fun loadOlderMessages(providerDialogId: String, limit: Int): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(maxInvalidChatId())

        return history.loadOlder(limit)
    }

    override suspend fun loadMessagesUntil(
        providerDialogId: String,
        messageId: Long
    ): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(maxInvalidChatId())

        return history.loadUntilLoaded(messageId)
    }

    override suspend fun loadMessagesAround(
        providerDialogId: String,
        messageId: Long,
        radius: Int
    ): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(maxInvalidChatId())

        return history.loadAround(messageId, radius)
    }


    override suspend fun loadFile(fileId: Int): Result<ByteArray> {
        mediaMemory[fileId]?.let { return Result.success(it) }

        val url = mediaUrlsById[fileId]
            ?: videoRefsById[fileId]?.let { ref ->
                resolveVideoUrl(fileId, ref)?.also { resolved ->
                    mediaUrlsById[fileId] = resolved
                    mediaIdsByUrl[resolved] = fileId
                }
            }
            ?: return Result.failure(maxFailure("Unknown MAX media id", SessionErrorCode.InvalidRequest))

        val cachePath = mediaCachePath(url)
        storage.readBytes(cachePath)?.takeIf { it.isNotEmpty() }?.let { cached ->
            mediaMemory[fileId] = cached
            return Result.success(cached)
        }

        val bytes = fetchUrlBytes(url)
            ?: return Result.failure(maxFailure("Could not download the MAX media", SessionErrorCode.Network))

        if (bytes.isEmpty()) {
            return Result.failure(maxFailure("MAX returned an empty media file"))
        }

        mediaMemory[fileId] = bytes
        runCatching { storage.writeBytes(cachePath, bytes) }

        return Result.success(bytes)
    }

    override suspend fun loadCachedMedia(remoteUniqueId: String): ByteArray? {
        if (!remoteUniqueId.startsWith("http")) return null

        return runCatching { storage.readBytes(mediaCachePath(remoteUniqueId)) }.getOrNull()
    }


    override suspend fun editMessage(
        providerDialogId: String,
        messageId: Long,
        text: String,
        customEmojis: List<CustomEmojiSpan>
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(maxInvalidChatId())
        val payload = buildJsonObject {
            put("chatId", chatId)
            put("messageId", messageId.toString())
            put("text", text)
            put("elements", JsonArray(emptyList()))
            put("attachments", JsonArray(emptyList()))
        }

        return try {
            val response = requestOnLiveConnection(opcode = 67, payload = payload)

            val message = response?.obj("message")?.let { parseMessage(chatId, it) }
            if (message != null && message.id != 0L) {
                applySentMessage(chatId, message)
            } else {
                applyEditedMessage(chatId, messageId, text)
            }

            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception.asSessionException())
        }
    }


    /**
     * Removes a chat from the account. MAX deletes the whole chat with CHAT_DELETE
     * (54); the local list is updated even when the server already dropped it.
     */
    override suspend fun deleteDialog(
        providerDialogId: String,
        revoke: Boolean
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(maxInvalidChatId())

        return try {
            requestOnLiveConnection(
                opcode = 54,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put("forMe", !revoke)
                }
            )

            chats.remove(chatId)
            histories.update { entries -> entries - providerDialogId }
            emitDialogs()
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // The chat may already be gone on the server; dropping it locally
            // still makes the delete button do what the user asked.
            chats.remove(chatId)
            histories.update { entries -> entries - providerDialogId }
            emitDialogs()
            Result.failure(exception.asSessionException())
        }
    }


    override suspend fun markMessagesRead(providerDialogId: String, messageIds: LongArray) {
        val chatId = providerDialogId.toLongOrNull() ?: return

        val newest = histories.value[providerDialogId]
            ?.state
            ?.value
            ?.messages
            ?.lastOrNull()

        val mark = newest?.timestamp ?: System.currentTimeMillis()

        runCatching {
            rpc?.request(
                opcode = 50,
                payload = buildJsonObject {
                    put("type", "READ_MESSAGE")
                    put("chatId", chatId)
                    newest?.let { put("messageId", it.messageId.toString()) }
                    put("mark", mark)
                }
            )
        }

        chats[chatId]?.let { chat ->
            chats[chatId] = chat.copy(unread = 0)
        }

        emitDialogs()
    }


    override suspend fun searchMessages(
        providerDialogId: String,
        query: String,
        limit: Int
    ): Result<List<ChatMessage>> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(maxInvalidChatId())
        val active = rpc ?: return Result.failure(maxNotConnected())

        return try {
            val response = active.request(
                opcode = 73,
                payload = buildJsonObject {
                    put("query", query)
                    put("count", limit)
                    put("chatId", chatId)
                }
            )

            val messages = response?.array("result").objects().mapNotNull { item ->
                val message = item.obj("message") ?: return@mapNotNull null
                toChatMessage(chatId, parseMessage(chatId, message))
            }

            Result.success(messages)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception.asSessionException())
        }
    }

    override suspend fun searchGlobalMessages(
        query: String,
        limit: Int
    ): Result<List<ChatMessage>> {
        val active = rpc ?: return Result.failure(maxNotConnected())

        return try {
            val response = active.request(
                opcode = 60,
                payload = buildJsonObject {
                    put("query", query)
                    put("count", limit)
                }
            )

            val messages = response?.array("result").objects().mapNotNull { item ->
                val chat = item.obj("chat")
                val chatId = chat?.long("id") ?: chat?.long("cid") ?: item.long("chatId")
                    ?: return@mapNotNull null
                val message = item.obj("message") ?: chat?.obj("lastMessage")
                    ?: return@mapNotNull null

                toChatMessage(chatId, parseMessage(chatId, message))
            }

            Result.success(messages)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception.asSessionException())
        }
    }

    override suspend fun searchChats(query: String, limit: Int): Result<List<Dialog>> {
        val normalized = query.trim()

        if (normalized.isEmpty()) return Result.success(emptyList())

        return Result.success(
            _dialogs.value
                .filter { dialog -> dialog.title.contains(normalized, ignoreCase = true) }
                .take(limit)
        )
    }

    override suspend fun loadMessage(
        providerDialogId: String,
        messageId: Long
    ): Result<ChatMessage?> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(maxInvalidChatId())
        val active = rpc ?: return Result.failure(maxNotConnected())

        return try {
            val response = active.request(
                opcode = 71,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put("messageIds", buildJsonArray { add(JsonPrimitive(messageId)) })
                }
            )

            val message = response?.array("messages").objects().firstOrNull()
                ?: return Result.success(null)

            Result.success(toChatMessage(chatId, parseMessage(chatId, message)))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception.asSessionException())
        }
    }

    override suspend fun loadMessageReadDate(
        providerDialogId: String,
        messageId: Long
    ): Result<Long?> = Result.success(null)

    override suspend fun loadPinnedMessageId(providerDialogId: String): Result<Long?> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(maxInvalidChatId())

        return Result.success(chats[chatId]?.pinnedMessageId)
    }

    // ------------------------------------------------------------- profile --


    override suspend fun loadOwnProfile(): Result<UserProfileDetails> {
        val profile = _profile.value
            ?: return Result.failure(maxNotSignedIn())

        val me = users[meId]

        return Result.success(
            UserProfileDetails(
                userId = meId.takeIf { it != 0L },
                displayName = profile.displayName,
                avatar = profile.avatar,
                username = me?.username,
                phoneNumber = me?.phone,
                bio = me?.description,
                kind = DialogKind.Direct
            )
        )
    }

    override suspend fun loadMentionAvatars(text: String): Map<String, Avatar> {
        return MentionUsernameRegex.findAll(text)
            .mapNotNull { match -> match.groupValues.getOrNull(1) }
            .distinct()
            .mapNotNull { username ->
                val user = users.values.firstOrNull { it.username.equals(username, true) }
                    ?: return@mapNotNull null

                username to avatarFor(user.name, user.pictureUrl)
            }
            .toMap()
    }

    override suspend fun resolveUsername(username: String): Result<Long> {
        val normalized = username.removePrefix("@")

        val user = users.values.firstOrNull { it.username.equals(normalized, true) }
            ?: return Result.failure(maxFailure("MAX user @$normalized was not found", SessionErrorCode.NotFound))

        return Result.success(user.id)
    }

    override suspend fun loadCustomEmoji(emojiId: Long): CustomEmojiAsset? = null

    // -------------------------------------------------------------- shared --


    override suspend fun setDialogPinned(
        providerDialogId: String,
        isPinned: Boolean,
        folderId: Int?
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(maxInvalidChatId())

        val result = requestChatOperation(
            chatId = chatId,
            operation = if (isPinned) "pin" else "unpin"
        )

        if (result.isSuccess) {
            if (isPinned) pinnedChats.add(chatId) else pinnedChats.remove(chatId)
            emitDialogs()
        }

        return result
    }

    override suspend fun setDialogMuted(
        providerDialogId: String,
        muted: Boolean
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(maxInvalidChatId())

        return requestChatOperation(
            chatId = chatId,
            operation = if (muted) "mute" else "unmute"
        )
    }

    override suspend fun setChatSubscribed(
        providerDialogId: String,
        subscribed: Boolean
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(maxInvalidChatId())
        val active = rpc ?: return Result.failure(maxNotConnected())

        return try {
            active.request(
                opcode = 75,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put("subscribe", subscribed)
                }
            )

            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception.asSessionException())
        }
    }


    override suspend fun sendFile(
        providerDialogId: String,
        path: String,
        asPhoto: Boolean
    ): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException("MAX file uploads are not implemented yet")
        )
    }

    override suspend fun sendFiles(
        providerDialogId: String,
        attachments: List<com.mrboomdev.scrollix.data.model.OutgoingAttachment>,
        caption: String,
        replyToMessageId: Long?,
        customEmojis: List<CustomEmojiSpan>
    ): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException("MAX file uploads are not implemented yet")
        )
    }


    override suspend fun sendLocation(
        providerDialogId: String,
        latitude: Double,
        longitude: Double,
        livePeriodSeconds: Int
    ): Result<Long> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(maxInvalidChatId())

        val attach = buildJsonObject {
            put("_type", "LOCATION")
            put("latitude", latitude)
            put("longitude", longitude)

            if (livePeriodSeconds > 0) {
                // MAX expects the live period in milliseconds.
                put("livePeriod", livePeriodSeconds * 1000L)
            }
        }

        return sendAttachments(
            chatId = chatId,
            text = "",
            attaches = buildJsonArray { add(attach) }
        ).map { message -> message?.id ?: 0L }
    }


    /**
     * MAX has no usable forward opcode (`MSG_FWD` 68 is rejected and
     * `FORWARD_MESSAGE` 70 only accepts text), so a forward is reproduced as a
     * copy: the text and every attachment are loaded and sent again. Stickers
     * that came from a MAX pack keep their server ids, so they still arrive as
     * native stickers rather than re-uploaded images.
     */


    override suspend fun loadStickerSet(setId: Long): Result<StickerSetInfo> {
        if (setId == SyntheticStickerPackId) {
            return Result.success(
                StickerSetInfo(
                    id = SyntheticStickerPackId,
                    title = SyntheticStickerPackTitle,
                    name = "max",
                    isInstalled = true,
                    stickers = collectedStickerMedia()
                )
            )
        }

        loadMaxStickerSets()[setId]?.let { return Result.success(it) }

        return Result.failure(maxFailure("Unknown MAX sticker pack", SessionErrorCode.NotFound))
    }

    override suspend fun setStickerSetInstalled(setId: Long, installed: Boolean): Result<Unit> {
        if (setId == SyntheticStickerPackId) return Result.success(Unit)

        return try {
            // ASSETS_ADD (29) toggles a pack in the account's favourites.
            requestOnLiveConnection(
                opcode = 29,
                payload = buildJsonObject {
                    put(
                        "type",
                        if (installed) "FAVORITE_STICKER_SET" else "UNFAVORITE_STICKER_SET"
                    )
                    put("id", setId)
                }
            )

            maxStickerPacks[setId]?.let { pack ->
                maxStickerPacks[setId] = pack.copy(isInstalled = installed)
            }

            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception.asSessionException())
        }
    }

    /**
     * MAX publishes its sticker catalogue through ASSETS_UPDATE (27) and
     * ASSETS_GET (26). Packs that arrive without sticker objects are skipped,
     * and the stickers this account has already received stay available as a
     * synthetic pack so the cross-provider picker keeps working offline.
     */
    override suspend fun loadStickerPacks(kind: StickerPackKind): Result<List<StickerPack>> {
        if (kind != StickerPackKind.Sticker) return Result.success(emptyList())

        val packs = loadMaxStickerSets().values
            .filter { pack -> pack.stickers.isNotEmpty() }
            .map { pack ->
                StickerPack(
                    id = pack.id,
                    title = pack.title,
                    isInstalled = pack.isInstalled,
                    thumbnailFileId = pack.stickers.firstOrNull()?.previewFileId
                )
            }

        if (packs.isNotEmpty()) return Result.success(packs)

        val stickers = collectedStickerMedia()
        if (stickers.isEmpty()) return Result.success(emptyList())

        return Result.success(
            listOf(
                StickerPack(
                    id = SyntheticStickerPackId,
                    title = SyntheticStickerPackTitle,
                    isInstalled = true,
                    thumbnailFileId = stickers.firstOrNull()?.previewFileId
                )
            )
        )
    }


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


    /**
     * A sticker imported from another provider has no MAX sticker id, so it
     * becomes a MAX photo. Any static image
     * Telegram hands us (WebP, PNG, JPEG, GIF) is uploaded as it is; animated
     * TGS/WebM stickers are prerendered to a transparent animated GIF first,
     * which MAX renders as an animated photo (its SDK models those as
     * `photo.isGif`).
     */

    /**
     * Sends an animation that came from another provider. A GIF or WebP
     * animation becomes an animated MAX photo; a video animation (Telegram
     * stores saved GIFs as MP4) goes through MAX's video upload.
     */

    override suspend fun sendAnimation(
        providerDialogId: String,
        animation: MessageMedia
    ): Result<Unit> {
        val bytes = loadMediaBytes(
            fileId = animation.fileId,
            uniqueId = animation.fileUniqueId
        ) ?: return Result.failure(maxFailure("Could not load the GIF", SessionErrorCode.Network))

        return sendAnimationBytes(
            providerDialogId = providerDialogId,
            bytes = bytes,
            mimeType = animation.mimeType,
            width = animation.width,
            height = animation.height,
            durationSeconds = animation.durationSeconds
        )
    }


    override suspend fun logout() {
        clearStoredSession()
        token = null
        close()
    }

    override suspend fun close() {
        historySaveJob?.cancel()
        historySaveJob = null
        if (closed) return

        closed = true
        _isLoadingDialogs.value = false
        closeRpc()
        scope.cancel()
        _authState.value = AuthState.Closed
    }
    internal fun MaxChat.kind(): DialogKind = when (type) {
    "CHAT" -> DialogKind.Group
    "CHANNEL" -> DialogKind.Channel
    else -> DialogKind.Direct
    }

    internal fun MaxChat.peerUserId(): Long? {
    return participantIds.firstOrNull { it != meId }
    }

    internal fun MaxChat.peerUser(): MaxUser? = peerUserId()?.let(users::get)

    override suspend fun sendMessage(providerDialogId: String, text: String, replyToMessageId: Long?, customEmojis: List<CustomEmojiSpan>) : Result<Unit> = sendMessageImpl(providerDialogId, text, replyToMessageId, customEmojis)
    override suspend fun deleteMessages(providerDialogId: String, messageIds: LongArray) : Result<Unit> = deleteMessagesImpl(providerDialogId, messageIds)

    override suspend fun forwardMessages(providerDialogId: String, messageIds: LongArray, targetDialogId: String) : Result<Unit> = forwardMessagesImpl(providerDialogId, messageIds, targetDialogId)
    override suspend fun sendPoll(providerDialogId: String, question: String, options: List<String>, isAnonymous: Boolean, allowsMultipleAnswers: Boolean, allowsRevoting: Boolean, questionCustomEmojis: List<CustomEmojiSpan>, optionCustomEmojis: List<List<CustomEmojiSpan>>, optionMediaPaths: List<String?>, isQuiz: Boolean, correctOptionIndex: Int?, explanation: String) : Result<Unit> = sendPollImpl(providerDialogId, question, options, isAnonymous, allowsMultipleAnswers, allowsRevoting, questionCustomEmojis, optionCustomEmojis, optionMediaPaths, isQuiz, correctOptionIndex, explanation)
    override suspend fun votePoll(providerDialogId: String, messageId: Long, optionIds: List<String>) : Result<Unit> = votePollImpl(providerDialogId, messageId, optionIds)
    override suspend fun sendSticker(providerDialogId: String, sticker: MessageMedia) : Result<Unit> = sendStickerImpl(providerDialogId, sticker)
    override suspend fun sendStickerBytes(providerDialogId: String, bytes: ByteArray, format: MessageStickerFormat, width: Int, height: Int) : Result<Unit> = sendStickerBytesImpl(providerDialogId, bytes, format, width, height)
    override suspend fun sendAnimationBytes(providerDialogId: String, bytes: ByteArray, mimeType: String?, width: Int, height: Int, durationSeconds: Int) : Result<Unit> = sendAnimationBytesImpl(providerDialogId, bytes, mimeType, width, height, durationSeconds)
    override suspend fun stopLiveLocation(providerDialogId: String, messageId: Long) : Result<Unit> = stopLiveLocationImpl(providerDialogId, messageId)
    override suspend fun setMessagePinned(providerDialogId: String, messageId: Long, isPinned: Boolean) : Result<Unit> = setMessagePinnedImpl(providerDialogId, messageId, isPinned)

    override suspend fun addMessageReaction(providerDialogId: String, messageId: Long, emoji: String, customEmojiId: Long?) : Result<Unit> = addMessageReactionImpl(providerDialogId, messageId, emoji, customEmojiId)
    override suspend fun removeMessageReaction(providerDialogId: String, messageId: Long, emoji: String, customEmojiId: Long?) : Result<Unit> = removeMessageReactionImpl(providerDialogId, messageId, emoji, customEmojiId)
    override suspend fun loadAvailableReactions(providerDialogId: String, messageId: Long) : Result<List<MessageReactionOption>> = loadAvailableReactionsImpl(providerDialogId, messageId)

    override suspend fun loadUserProfile(providerDialogId: String, userId: Long?) : Result<UserProfileDetails> = loadUserProfileImpl(providerDialogId, userId)
    override suspend fun loadSharedMedia(providerDialogId: String, kind: SharedMediaKind, fromMessageId: Long, limit: Int) : Result<List<ChatMessage>> = loadSharedMediaImpl(providerDialogId, kind, fromMessageId, limit)

    override suspend fun start() = startImpl()

}
