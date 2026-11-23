package com.mrboomdev.scrollix.data.vk

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
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.model.DialogIdentity
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.DownloadedFile
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
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
import com.mrboomdev.scrollix.data.vk.network.VkApiException
import com.mrboomdev.scrollix.data.vk.network.array
import com.mrboomdev.scrollix.data.vk.network.asLongOrNull
import com.mrboomdev.scrollix.data.vk.network.bool
import com.mrboomdev.scrollix.data.vk.network.int
import com.mrboomdev.scrollix.data.vk.network.long
import com.mrboomdev.scrollix.data.vk.network.longPollUrl
import com.mrboomdev.scrollix.data.vk.network.mediaIdForUrl
import com.mrboomdev.scrollix.data.vk.network.obj
import com.mrboomdev.scrollix.data.vk.network.objects
import com.mrboomdev.scrollix.data.vk.network.parseLongPollPage
import com.mrboomdev.scrollix.data.vk.network.parsePeerId
import com.mrboomdev.scrollix.data.vk.network.peerChatId
import com.mrboomdev.scrollix.data.vk.network.peerGroupId
import com.mrboomdev.scrollix.data.vk.network.peerUserId
import com.mrboomdev.scrollix.data.vk.network.string
import com.mrboomdev.scrollix.data.vk.network.vkHttpRequest
import com.mrboomdev.scrollix.data.vk.network.vkReadFileBytes
import com.mrboomdev.scrollix.fetchUrlBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

private const val TokenChallengeId = "vk-token"
private const val DialogsPageSize = 200
private const val LongPollWaitSeconds = 25

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
    private val storage: PlatformStorage,
    parentScope: CoroutineScope
) : AccountSession {
    override val accountId: String = account.id
    override val providerId: String = VkProviderId

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val scope = CoroutineScope(
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

    private val histories = MutableStateFlow<Map<String, MessageHistoryController>>(emptyMap())
    private val emptyHistory = MutableStateFlow(
        MessageHistory(hasMoreOlder = false, isLoadingInitial = false)
    ).asStateFlow()

    private val users = linkedMapOf<Long, VkUser>()
    private val groups = linkedMapOf<Long, VkGroup>()

    /** Synthetic file id -> remote URL for the media the session handed out. */
    private val mediaUrls = mutableMapOf<Int, String>()

    private var api: VkApi? = null
    private var token: String? = null
    private var meId: Long = 0
    private var meName: String = account.displayName

    private var started = false
    private var closed = false
    private var longPollJob: Job? = null
    private var refreshJob: Job? = null
    private val refreshMutex = Mutex()
    private var dialogsLoaded = false

    private val sessionPath: String
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
                _authState.value = AuthState.Error(
                    "VK sign-in failed: ${exception.reason}"
                )
            }
        } catch (exception: Exception) {
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
        Result.failure(UnsupportedOperationException("VK has no QR sign-in here"))

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

    private suspend fun refreshDialogs() {
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

    private fun mapDialog(conversation: JsonObject): Dialog? {
        val peer = conversation.obj("peer") ?: return null
        val peerId = peer.long("id") ?: return null
        val type = peer.string("type") ?: when {
            peerId > 2_000_000_000L -> "chat"
            peerId < 0 -> "group"
            else -> "user"
        }

        val settings = conversation.obj("chat_settings")
        val last = conversation.obj("last_message")
        val user = users[peerUserId(peerId) ?: 0]
        val group = groups[peerGroupId(peerId) ?: 0]

        val title = when (type) {
            "user" -> user?.name
            "group" -> group?.name
            else -> settings.string("title") ?: group?.name
        } ?: "VK chat"

        val avatar = when (type) {
            "user" -> user?.avatar()
            "group" -> group?.avatar()
            else -> settings.obj("photo")?.string("photo_100")
                ?.let { url -> Avatar(initials = title.take(1), colorSeed = title.hashCode(), url = url) }
        } ?: Avatar.fromName(title)

        val timestamp = (last?.long("date") ?: 0L) * 1000

        return Dialog(
            providerId = VkProviderId,
            accountId = accountId,
            providerDialogId = peerId.toString(),
            identity = DialogIdentity(VkProviderId, peerId.toString()),
            kind = when (type) {
                "group" -> DialogKind.Group
                "chat" -> DialogKind.Group
                else -> DialogKind.Direct
            },
            title = title,
            statusText = user?.status,
            preview = conversationPreview(last),
            lastMessageIsOutgoing = last?.bool("out") ?: false,
            timestamp = timestamp,
            unreadCount = conversation.int("unread_count") ?: 0,
            memberCount = settings.int("members_count") ?: group?.membersCount,
            pinnedMessageId = settings.obj("pinned_message").long("id"),
            canSendMessages = conversation.obj("can_write")?.bool("allowed") ?: true,
            isPinned = conversation.bool("is_pinned") ?: false,
            isMarkedAsUnread = conversation.bool("is_marked_unread") ?: false,
            order = timestamp,
            avatar = avatar
        )
    }

    private fun conversationPreview(last: JsonObject?): String {
        if (last == null) return ""

        val text = last.string("text").orEmpty()
        if (text.isNotBlank()) return text

        val attachment = last.array("attachments").objects().firstOrNull() ?: return ""
        return attachmentLabel(attachment.string("type"))
    }

    // ------------------------------------------------------------- history --

    override suspend fun loadMessages(providerDialogId: String, limit: Int): Result<Unit> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid VK peer id"))

        return history.loadLatest(limit)
    }

    override suspend fun loadOlderMessages(providerDialogId: String, limit: Int): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid VK peer id"))

        return history.loadOlder(limit)
    }

    override suspend fun loadMessagesUntil(
        providerDialogId: String,
        messageId: Long
    ): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid VK peer id"))

        return history.loadUntilLoaded(messageId)
    }

    override suspend fun loadMessagesAround(
        providerDialogId: String,
        messageId: Long,
        radius: Int
    ): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid VK peer id"))

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
        val client = api ?: throw IllegalStateException("VK is not connected")

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

    private fun indexExtended(root: JsonObject) {
        root.array("profiles").objects().forEach { profile ->
            val id = profile.long("id") ?: return@forEach
            users[id] = VkUser(
                id = id,
                name = listOfNotNull(
                    profile.string("first_name"),
                    profile.string("last_name")
                ).joinToString(" ").ifBlank { "VK user" },
                photoUrl = profile.string("photo_100") ?: profile.string("photo_200"),
                status = profile.string("status")
            )
        }

        root.array("groups").objects().forEach { group ->
            val id = group.long("id") ?: return@forEach
            groups[id] = VkGroup(
                id = id,
                name = group.string("name") ?: "VK community",
                photoUrl = group.string("photo_100") ?: group.string("photo_200"),
                membersCount = group.int("members_count")
            )
        }
    }

    private fun mapMessage(peerId: Long, raw: JsonObject): ChatMessage? {
        val messageId = raw.long("id") ?: return null
        val fromId = raw.long("from_id") ?: 0L
        val date = raw.long("date") ?: 0L
        val action = raw.obj("action")

        val senderUser = users[peerUserId(fromId) ?: 0]
        val senderGroup = groups[peerGroupId(fromId) ?: 0]
        val senderName = senderUser?.name ?: senderGroup?.name
        val senderAvatar = senderUser?.avatar() ?: senderGroup?.avatar()

        val attachments = raw.array("attachments").objects()
        val media = attachments.firstOrNull()?.let(::mapAttachment)

        val reply = raw.obj("reply_message")?.let { reply ->
            MessageReply(
                messageId = reply.long("id") ?: 0L,
                senderName = users[reply.long("from_id") ?: 0]?.name,
                text = reply.string("text").orEmpty(),
                media = reply.array("attachments").objects().firstOrNull()
                    ?.let(::mapAttachment)
            )
        }

        val forwarded = raw.array("fwd_messages").objects().firstOrNull()

        return ChatMessage(
            providerId = VkProviderId,
            accountId = accountId,
            providerDialogId = peerId.toString(),
            messageId = messageId,
            senderName = senderName,
            senderAvatar = senderAvatar,
            senderId = fromId,
            forwardedFromName = forwarded?.let { message ->
                users[message.long("from_id") ?: 0]?.name
            },
            text = action?.let(::actionText) ?: raw.string("text").orEmpty(),
            timestamp = date * 1000,
            isOutgoing = raw.bool("out") ?: false,
            isService = action != null,
            media = media,
            replyTo = reply,
            canBeDeleted = true,
            canBeDeletedForEveryone = raw.bool("out") ?: false,
            canBeForwarded = true,
            sendState = MessageSendState.Sent,
            isRead = raw.bool("read_state") ?: true
        )
    }

    private fun actionText(action: JsonObject): String {
        val type = action.string("type").orEmpty()
        val memberId = action.long("member_id")
        val member = memberId?.let { id -> users[id]?.name }

        return when (type) {
            "chat_photo_update" -> "Chat photo updated"
            "chat_photo_remove" -> "Chat photo removed"
            "chat_create" -> "Chat created"
            "chat_title_update" -> "Chat renamed to \"${action.string("text").orEmpty()}\""
            "chat_invite_user" -> "${member ?: "A user"} joined the chat"
            "chat_kick_user" -> "${member ?: "A user"} left the chat"
            "chat_pin_message" -> "Message pinned"
            "chat_unpin_message" -> "Message unpinned"
            "chat_invite_user_by_link" -> "Someone joined by link"
            else -> type.replace('_', ' ').replaceFirstChar(Char::uppercase)
        }
    }

    private fun mapAttachment(attachment: JsonObject): MessageMedia? {
        return when (attachment.string("type")) {
            "photo" -> {
                val sizes = attachment.obj("photo")?.array("sizes").objects().orEmpty()
                val best = sizes.maxByOrNull { size ->
                    (size.int("width") ?: 0) * (size.int("height") ?: 0)
                } ?: return null
                val url = best.string("url") ?: return null

                MessageMedia(
                    kind = MessageMediaKind.Photo,
                    previewFileId = registerMedia(url),
                    previewUniqueId = url,
                    fileId = registerMedia(url),
                    fileUniqueId = url,
                    width = best.int("width") ?: 0,
                    height = best.int("height") ?: 0
                )
            }

            "sticker" -> {
                val sticker = attachment.obj("sticker") ?: return null
                val images = sticker.array("images").objects()
                val image = images.maxByOrNull { it.int("width") ?: 0 } ?: return null
                val url = image.string("url") ?: return null

                MessageMedia(
                    kind = MessageMediaKind.Sticker,
                    previewFileId = registerMedia(url),
                    previewUniqueId = url,
                    fileId = registerMedia(url),
                    fileUniqueId = url,
                    width = image.int("width") ?: 0,
                    height = image.int("height") ?: 0,
                    stickerFormat = MessageStickerFormat.Webp,
                    stickerEmoji = sticker.string("emoji")
                )
            }

            "doc" -> {
                val doc = attachment.obj("doc") ?: return null
                val url = doc.string("url") ?: return null
                val extension = doc.string("ext").orEmpty()

                MessageMedia(
                    kind = if (extension.equals("gif", ignoreCase = true)) {
                        MessageMediaKind.Animation
                    } else {
                        MessageMediaKind.File
                    },
                    previewFileId = registerMedia(url),
                    previewUniqueId = url,
                    fileId = registerMedia(url),
                    fileUniqueId = url,
                    fileName = doc.string("title"),
                    mimeType = doc.string("ext")?.let { "application/$it" },
                    size = doc.long("size") ?: 0
                )
            }

            "audio_message" -> {
                val audio = attachment.obj("audio_message") ?: return null
                val url = audio.string("link_mp3") ?: audio.string("link_ogg") ?: return null

                MessageMedia(
                    kind = MessageMediaKind.VoiceNote,
                    fileId = registerMedia(url),
                    fileUniqueId = url,
                    durationSeconds = audio.int("duration") ?: 0
                )
            }

            "video" -> {
                val video = attachment.obj("video") ?: return null
                val url = video.string("photo_800")
                    ?: video.string("photo_320")
                    ?: video.array("image").objects().lastOrNull()?.string("url")

                if (url == null) return null

                MessageMedia(
                    kind = MessageMediaKind.Video,
                    previewFileId = registerMedia(url),
                    previewUniqueId = url,
                    fileId = registerMedia(url),
                    fileUniqueId = url,
                    durationSeconds = video.int("duration") ?: 0,
                    width = video.int("width") ?: 0,
                    height = video.int("height") ?: 0
                )
            }

            "audio" -> {
                val audio = attachment.obj("audio") ?: return null
                val url = audio.string("url") ?: return null

                MessageMedia(
                    kind = MessageMediaKind.Audio,
                    fileId = registerMedia(url),
                    fileUniqueId = url,
                    fileName = audio.string("title"),
                    durationSeconds = audio.int("duration") ?: 0
                )
            }

            else -> null
        }
    }

    private fun attachmentLabel(type: String?): String = when (type) {
        "photo" -> "[Photo]"
        "video" -> "[Video]"
        "doc" -> "[File]"
        "sticker" -> "[Sticker]"
        "audio_message" -> "[Voice message]"
        "audio" -> "[Audio]"
        else -> "[Attachment]"
    }

    private fun registerMedia(url: String): Int {
        val id = mediaIdForUrl(url)
        mediaUrls[id] = url
        return id
    }

    // -------------------------------------------------------------- sending --

    override suspend fun sendMessage(
        providerDialogId: String,
        text: String,
        replyToMessageId: Long?,
        customEmojis: List<CustomEmojiSpan>
    ): Result<Unit> = runCatchingApi {
        val peerId = parsePeerId(providerDialogId)
            ?: throw IllegalArgumentException("Invalid VK peer id")
        val client = api ?: throw IllegalStateException("VK is not connected")

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
        customEmojis: List<CustomEmojiSpan>
    ): Result<Unit> = runCatchingApi {
        if (attachments.isEmpty()) return@runCatchingApi

        val peerId = parsePeerId(providerDialogId)
            ?: throw IllegalArgumentException("Invalid VK peer id")
        val client = api ?: throw IllegalStateException("VK is not connected")

        val uploaded = attachments.mapNotNull { attachment ->
            uploadAttachment(client, attachment)
        }

        if (uploaded.isEmpty()) {
            throw IllegalStateException("VK did not accept the attachment")
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

    private suspend fun uploadAttachment(
        client: VkApi,
        attachment: OutgoingAttachment
    ): String? {
        val bytes = vkReadFileBytes(attachment.path) ?: return null
        val fileName = attachment.path.substringAfterLast('/').ifBlank { "file" }
        val isImage = attachment.path.substringAfterLast('.', "")
            .lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp")

        return if (attachment.asPhoto && isImage) {
            uploadPhoto(client, fileName, bytes)
        } else {
            uploadDocument(client, fileName, bytes)
        }
    }

    private suspend fun uploadPhoto(
        client: VkApi,
        fileName: String,
        bytes: ByteArray
    ): String? {
        val server = client.callObject("photos.getMessagesUploadServer")
        val uploadUrl = server.string("upload_url") ?: return null

        val uploaded = client.upload(
            uploadUrl = uploadUrl,
            fieldName = "photo",
            fileName = fileName,
            bytes = bytes,
            contentType = "image/jpeg"
        ) ?: return null

        val serverId = uploaded.string("server") ?: return null
        val photo = uploaded.string("photo") ?: return null
        val hash = uploaded.string("hash") ?: return null

        val saved = client.callArray(
            "photos.saveMessagesPhoto",
            mapOf("server" to serverId, "photo" to photo, "hash" to hash)
        ).firstOrNull() ?: return null

        val id = saved.long("id") ?: return null
        val ownerId = saved.long("owner_id") ?: return null
        val accessKey = saved.string("access_key")

        return buildString {
            append("photo").append(ownerId).append('_').append(id)
            if (accessKey != null) append('_').append(accessKey)
        }
    }

    private suspend fun uploadDocument(
        client: VkApi,
        fileName: String,
        bytes: ByteArray
    ): String? {
        val server = client.callObject(
            "docs.getMessagesUploadServer",
            mapOf("type" to "doc")
        )
        val uploadUrl = server.string("upload_url") ?: return null

        val uploaded = client.upload(
            uploadUrl = uploadUrl,
            fieldName = "file",
            fileName = fileName,
            bytes = bytes,
            contentType = "application/octet-stream"
        ) ?: return null

        val file = uploaded.string("file") ?: return null

        val saved = client.callObject("docs.save", mapOf("file" to file))
        val doc = saved.obj("doc")
            ?: saved.array("docs").objects().firstOrNull()
            ?: return null

        val id = doc.long("id") ?: return null
        val ownerId = doc.long("owner_id") ?: return null
        val accessKey = doc.string("access_key")

        return buildString {
            append("doc").append(ownerId).append('_').append(id)
            if (accessKey != null) append('_').append(accessKey)
        }
    }

    // ------------------------------------------------------------- messages --

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

    override suspend fun editMessage(
        providerDialogId: String,
        messageId: Long,
        text: String,
        customEmojis: List<CustomEmojiSpan>
    ): Result<Unit> = runCatchingApi {
        val peerId = parsePeerId(providerDialogId)
            ?: throw IllegalArgumentException("Invalid VK peer id")

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
        targetDialogId: String
    ): Result<Unit> = runCatchingApi {
        val target = parsePeerId(targetDialogId)
            ?: throw IllegalArgumentException("Invalid VK peer id")

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
        messageIds: LongArray
    ): Result<Unit> = runCatchingApi {
        val ids = buildJsonArray {
            messageIds.forEach { id -> add(kotlinx.serialization.json.JsonPrimitive(id)) }
        }.toString()

        api?.call(
            "messages.delete",
            mapOf(
                "message_ids" to ids,
                "delete_for_all" to "1",
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
            ?: throw IllegalArgumentException("Invalid VK peer id")

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
            ?: return Result.failure(IllegalArgumentException("Invalid VK peer id"))

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
            ?: return Result.failure(IllegalArgumentException("Invalid VK peer id"))

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
            ?: return Result.failure(IllegalArgumentException("Unknown VK file id"))

        return runCatchingApi {
            fetchUrlBytes(url) ?: throw IllegalStateException("VK media download failed")
        }
    }

    // -------------------------------------------------------------- profiles --

    override suspend fun loadUserProfile(
        providerDialogId: String,
        userId: Long?
    ): Result<UserProfileDetails> {
        val peerId = parsePeerId(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid VK peer id"))

        return runCatchingApi {
            peerUserId(userId ?: peerId)?.let { resolveUserProfile(it) }
                ?: resolveGroupProfile(peerGroupId(peerId) ?: 0)
        }
    }

    override suspend fun loadOwnProfile(): Result<UserProfileDetails> = runCatchingApi {
        resolveUserProfile(meId)
    }

    private suspend fun resolveUserProfile(userId: Long): UserProfileDetails {
        val response = api?.callObject(
            "users.get",
            mapOf(
                "user_ids" to userId.toString(),
                "fields" to "photo_200,status,online,last_seen,bdate,about,domain,contacts"
            )
        )

        indexExtended(response ?: JsonObject(emptyMap()))

        val user = response?.array("items")?.objects()?.firstOrNull()
            ?: throw IllegalStateException("VK user not found")

        val name = listOfNotNull(
            user.string("first_name"),
            user.string("last_name")
        ).joinToString(" ").ifBlank { "VK user" }

        users[userId] = VkUser(
            id = userId,
            name = name,
            photoUrl = user.string("photo_200") ?: user.string("photo_100"),
            status = user.string("status")
        )

        return UserProfileDetails(
            userId = userId,
            displayName = name,
            avatar = users[userId]?.avatar(),
            username = user.string("domain"),
            bio = user.string("about"),
            birthdate = user.string("bdate"),
            status = user.string("status"),
            isOnline = user.bool("online") ?: false,
            kind = DialogKind.Direct
        )
    }

    private suspend fun resolveGroupProfile(groupId: Long): UserProfileDetails {
        val response = api?.call(
            "groups.getById",
            mapOf(
                "group_ids" to groupId.toString(),
                "fields" to "description,members_count,photo_200,status"
            )
        )

        // v5.199 answers with `{"groups": [...]}`; older versions used an
        // `items` array or a bare array, so accept all three shapes.
        val g = when (response) {
            is JsonObject -> response.array("groups")?.objects()?.firstOrNull()
                ?: response.array("items")?.objects()?.firstOrNull()
                ?: response.takeIf { it["id"] != null }

            is JsonArray -> response.objects().firstOrNull()
            else -> null
        } ?: throw IllegalStateException("VK community not found")

        val name = g.string("name") ?: groups[groupId]?.name ?: "VK community"

        return UserProfileDetails(
            displayName = name,
            avatar = Avatar(
                initials = name.take(1),
                colorSeed = name.hashCode(),
                url = g.string("photo_200") ?: g.string("photo_100")
            ),
            status = g.string("status"),
            description = g.string("description"),
            memberCount = g.int("members_count"),
            kind = DialogKind.Group,
            canBlock = false
        )
    }

    override suspend fun setChatSubscribed(
        providerDialogId: String,
        subscribed: Boolean
    ): Result<Unit> = runCatchingApi {
        val peerId = parsePeerId(providerDialogId)
            ?: throw IllegalArgumentException("Invalid VK peer id")
        val groupId = peerGroupId(peerId)
            ?: throw UnsupportedOperationException("Only communities can be joined")

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
        limit: Int
    ): Result<List<ChatMessage>> {
        val peerId = parsePeerId(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid VK peer id"))

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
        limit: Int
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
            ?: throw IllegalStateException("VK could not resolve @$username")

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

    private fun startLongPoll() {
        if (longPollJob?.isActive == true) return

        longPollJob = scope.launch {
            while (!closed) {
                try {
                    pollOnce()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    delay(3_000)
                }
            }
        }
    }

    private suspend fun pollOnce() {
        val client = api ?: return
        val server = client.callObject(
            "messages.getLongPollServer",
            mapOf("need_pts" to "1", "lp_version" to "3")
        )

        var host = server.string("server") ?: return
        var key = server.string("key") ?: return
        var ts = server.string("ts") ?: return

        while (!closed) {
            val body = vkHttpRequest(
                url = longPollUrl(host, key, ts, LongPollWaitSeconds),
                timeoutSeconds = LongPollWaitSeconds + 15
            )

            if (body == null) {
                delay(2_000)
                return
            }

            val page = parseLongPollPage(body, json) ?: return

            if (page.failed != null) {
                val fresh = api?.callObject(
                    "messages.getLongPollServer",
                    mapOf("need_pts" to "1", "lp_version" to "3")
                ) ?: return

                host = fresh.string("server") ?: return
                key = fresh.string("key") ?: return
                ts = fresh.string("ts") ?: return
                continue
            }

            page.ts?.let { ts = it }
            handleUpdates(page.updates)
        }
    }

    private fun handleUpdates(updates: List<JsonArray>) {
        var refresh = false

        updates.forEach { update ->
            val code = update.firstOrNull().asLongOrNull()?.toInt()

            when (code) {
                // New or edited message: refresh the list and merge into an
                // already-open history window.
                4, 5 -> {
                    refresh = true
                    val messageId = update.getOrNull(1).asLongOrNull() ?: return@forEach
                    val peerId = update.getOrNull(3).asLongOrNull() ?: return@forEach
                    val history = histories.value[peerId.toString()] ?: return@forEach

                    scope.launch {
                        runCatching {
                            val current = api?.callObject(
                                "messages.getById",
                                mapOf(
                                    "message_ids" to messageId.toString(),
                                    "extended" to "1",
                                    "fields" to "photo_100"
                                )
                            ) ?: return@launch

                            indexExtended(current)
                            current.array("items").objects()
                                .mapNotNull { item -> mapMessage(peerId, item) }
                                .let(history::merge)
                        }
                    }
                }

                // Read receipts and unread counters only affect the chat list.
                6, 7, 80 -> refresh = true

                else -> Unit
            }
        }

        if (refresh) {
            scope.launch { refreshDialogs() }
        }
    }

    // ---------------------------------------------------------------- misc --

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

    private suspend fun loadUser(client: VkApi, expectedId: Long): VkUser? {
        val response = client.callObject(
            "users.get",
            buildMap {
                if (expectedId > 0) put("user_ids", expectedId.toString())
                put("fields", "photo_100,photo_200,status")
            }
        )

        indexExtended(response)

        return response.array("items").objects().firstOrNull()?.let { item ->
            val id = item.long("id") ?: return@let null
            VkUser(
                id = id,
                name = listOfNotNull(
                    item.string("first_name"),
                    item.string("last_name")
                ).joinToString(" ").ifBlank { "VK user" },
                photoUrl = item.string("photo_100") ?: item.string("photo_200"),
                status = item.string("status")
            )
        }
    }

    private fun readStoredSession(): StoredVkSession? {
        val text = storage.readText(sessionPath) ?: return null
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return null

        val token = root.string("token")?.takeIf(String::isNotBlank) ?: return null
        val userId = root.long("userId") ?: 0L
        return StoredVkSession(token, userId)
    }

    private fun persistSession(token: String, userId: Long) {
        runCatching {
            storage.writeText(
                sessionPath,
                buildJsonObject {
                    put("token", token)
                    put("userId", userId)
                }.toString()
            )
        }
    }

    private fun clearStoredSession() {
        runCatching { storage.delete(sessionPath) }
    }

    private inline fun <T> runCatchingApi(block: () -> T?): Result<T> {
        return try {
            val value = block()
                ?: return Result.failure(IllegalStateException("VK did not return a result"))
            Result.success(value)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun <T> unsupported(feature: String): Result<T> =
        Result.failure(UnsupportedOperationException("$feature are not supported by VK here"))
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
