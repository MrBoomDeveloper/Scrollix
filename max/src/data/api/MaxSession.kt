package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.max.network.MaxApiException
import com.mrboomdev.scrollix.data.max.network.MaxConnectionClosedException
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

// The current web client (web.max.ru bundle) connects to this host; the old
// ws-api.oneme.ru accepts TCP but never answers the WebSocket upgrade.
private const val SavedDialogWelcomeKey = "welcome.saved.dialog.message"

private const val MaxWebSocketUrl = "wss://api.oneme.ru/websocket"
private const val MaxOrigin = "https://web.max.ru"
private const val MaxConnectTimeoutMillis = 60_000L
// The WebSocket endpoint only accepts a WEB session ("session init forbidden"
// for deviceType ANDROID), so this mirrors the current real web client.
private const val MaxAppVersion = "26.11.9"
private const val MaxBuildNumber = 22367
private const val MaxDeviceArch = "x86_64"
private const val MaxDeviceName = "Chrome"
private const val MaxOsVersion = "Windows 10"
private const val MaxScreen = "1920x1080 1.0x"
private const val MaxTimezone = "Europe/Moscow"
private const val SyntheticStickerPackId = Long.MIN_VALUE + 4096
private const val SyntheticStickerPackTitle = "MAX stickers"

private const val PhoneChallengeId = "phone"
private const val CodeChallengeId = "code"
private const val CaptchaChallengeId = "max-captcha"
private const val PasswordChallengeId = "max-password"

// CAPTCHA_SESSION (web client opcode 224): creates the VK not-a-robot check and
// answers with the widget link that has to be solved before AUTH_REQUEST.
private const val CaptchaSessionOpcode = 224

private val MentionUsernameRegex = Regex("""@([A-Za-z0-9_]{2,64})""")

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

private data class MaxVideoRef(
    val chatId: Long,
    val messageId: Long,
    val videoId: Long,
    val token: String?
)

internal data class MaxPollOption(
    val id: String,
    val text: String,
    val votes: Int,
    val isChosen: Boolean
)

internal data class MaxPoll(
    val id: Long,
    val question: String,
    val options: List<MaxPollOption>,
    val totalVoterCount: Int,
    val isAnonymous: Boolean,
    val allowsMultipleAnswers: Boolean,
    val isClosed: Boolean
) {
    fun toPollInfo(): MessagePollInfo {
        val total = totalVoterCount.coerceAtLeast(options.sumOf { it.votes })

        return MessagePollInfo(
            id = id,
            question = question,
            options = options.map { option ->
                MessagePollOptionInfo(
                    id = option.id,
                    text = option.text,
                    voterCount = option.votes,
                    votePercentage = if (total > 0) option.votes * 100 / total else 0,
                    isChosen = option.isChosen
                )
            },
            totalVoterCount = total,
            isClosed = isClosed,
            isAnonymous = isAnonymous,
            allowsMultipleAnswers = allowsMultipleAnswers,
            canSeeResults = true
        )
    }
}

internal data class MaxAttach(
    val type: String,
    val url: String?,
    val remoteId: String?,
    val fileName: String?,
    val mimeType: String?,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationSeconds: Int,
    val isGif: Boolean = false,
    /** Static thumbnail URL of a video/GIF attachment. */
    val thumbnailUrl: String? = null,
    /** Server id and token of a video/GIF whose bytes need a URL request. */
    val videoId: Long? = null,
    val videoToken: String? = null,
    /** Decoded `previewData` payload (a base64 data URI) for the poster. */
    val previewBytes: ByteArray? = null,
    val stickerId: Long? = null,
    val setId: Long? = null,
    val lottieUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val livePeriodSeconds: Int = 0,
    val poll: MaxPoll? = null
) {
    val previewLabel: String
        get() = when (type.uppercase()) {
            "PHOTO" -> "Photo"
            "VIDEO" -> "Video"
            "AUDIO", "UNSUPPORTED" -> "Voice message"
            "FILE" -> fileName ?: "File"
            "STICKER" -> "Sticker"
            "GIF" -> "GIF"
            "CALL" -> "Call"
            "CONTROL" -> ""
            "POLL" -> "Poll"
            "LOCATION" -> "Location"
            else -> type.lowercase().replaceFirstChar(Char::uppercase)
        }

    fun toLocationInfo(): MessageLocationInfo? {
        val latitude = latitude ?: return null
        val longitude = longitude ?: return null

        return MessageLocationInfo(
            latitude = latitude,
            longitude = longitude,
            isLive = livePeriodSeconds > 0,
            expiresInSeconds = livePeriodSeconds
        )
    }

    fun toPollInfo(): MessagePollInfo? = poll?.toPollInfo()
}

internal data class MaxMessage(
    val id: Long,
    val senderId: Long?,
    val text: String,
    val time: Long,
    val attaches: List<MaxAttach>,
    val reactions: List<MessageReactionInfo>,
    val views: Int,
    val forwardedFromName: String?,
    val replyTo: MessageReply?,
    val isService: Boolean
)

/** An image payload MAX accepts as a photo, with its upload metadata. */
private data class MaxImageUpload(
    val extension: String,
    val contentType: String
)

/** Where an upload request wants the bytes, with the ids the attach needs. */
private data class MaxUploadTarget(
    val url: String,
    val token: String?,
    val id: Long? = null,
    /** `FILE_UPLOAD` ids are UUID strings, not numbers. */
    val fileKey: String? = null
)

/** A photo prepared by `IMAGE_UPLOAD_URL` (80), ready to be attached. */
private data class MaxPhotoUpload(
    val token: String?,
    val photoId: Long?
)

internal data class MaxChat(
    val id: Long,
    val type: String,
    val title: String?,
    val description: String?,
    val status: String?,
    val ownerId: Long,
    val adminIds: Set<Long> = emptySet(),
    val participantIds: List<Long>,
    val participantsCount: Int?,
    val pictureUrl: String?,
    val lastMessage: MaxMessage?,
    val unread: Int,
    val lastEventTime: Long,
    val pinnedMessageId: Long?,
    val isVerified: Boolean = false,
    /** Channels may forbid copying their posts to other chats. */
    val canCopyMessages: Boolean = true,
    /** The chat lets every member pin (channel `ALL_CAN_PIN_MESSAGE`). */
    val allCanPinMessages: Boolean = false
)

private data class StoredMaxSession(
    val token: String,
    val deviceId: String
)

/** Result of AUTH_REQUEST (17), the first step of the SMS sign-in. */
internal data class MaxCodeRequest(
    val trackId: String,
    val codeLength: Int,
    val blockingDurationSeconds: Int
)

/**
 * AUTH_REQUEST (17) answers with `trackId`, `codeLength` and
 * `blockingDuration`; the track id becomes the `token` of the follow-up
 * AUTH (18) request. Alternate flows call the same value `token`, so both
 * keys are accepted.
 */
internal fun parseCaptchaLink(response: JsonElement?): String? {
    val root = response as? JsonObject ?: return null
    return root.string("link")?.takeIf { it.startsWith("http") }
}

/**
 * MAX contacts carry the avatar as `baseUrl`/`baseRawUrl` and the phone as a
 * bare international number (`{country}{subscriber}`); the older
 * `picture`/`phones` shapes are kept as a fallback.
 */
internal fun parseMaxUser(objectValue: JsonObject): MaxUser? {
    val id = objectValue.long("id") ?: return null

    val phone = objectValue.long("phone")
        ?.takeIf { it > 0L }
        ?.let { number -> formatPhoneNumber("+$number") }
        ?: objectValue.array("phones").objects().firstOrNull()?.string("number")

    return MaxUser(
        id = id,
        name = maxContactName(objectValue),
        pictureUrl = objectValue.string("baseRawUrl")
            ?: objectValue.string("baseUrl")
            ?: objectValue.obj("picture")?.string("url"),
        username = maxUsernameOf(objectValue),
        phone = phone,
        description = objectValue.string("about") ?: objectValue.string("description"),
        isVerified = maxVerifiedFlag(objectValue),
        isServiceAccount = objectValue.bool("isServiceAccount") == true
    )
}

/**
 * MAX exposes the handle as `username` on some payloads and only as a profile
 * `link` (`https://max.ru/u/<handle>`) on others. Showing the raw link where
 * the UI prints `@handle` reads wrong, so the handle is extracted.
 */
internal fun maxUsernameOf(objectValue: JsonObject): String? {
    val raw = objectValue.string("username") ?: objectValue.string("link") ?: return null

    return normalizeMaxUsername(raw)
}

internal fun normalizeMaxUsername(raw: String): String? {
    val trimmed = raw.trim().removePrefix("@").trim()
    if (trimmed.isEmpty()) return null

    if (!trimmed.startsWith("http", ignoreCase = true)) return trimmed

    return trimmed.substringAfterLast('/')
        .substringBefore('?')
        .takeIf { it.isNotBlank() }
}

/** MAX flags official channels and accounts with `isVerified`/`verified`. */
internal fun maxVerifiedFlag(objectValue: JsonObject): Boolean {
    return objectValue.bool("isVerified") == true ||
        objectValue.bool("verified") == true ||
        objectValue.bool("is_verified") == true
}

/**
 * Names come as `{type: ONEME|CUSTOM, name?, firstName?, lastName?}` entries.
 * The display name joins the first and last name with a space, preferring the
 * ONEME entry; an explicit `name` wins when the server filled it in.
 */
internal fun maxContactName(objectValue: JsonObject): String {
    val names = objectValue.array("names").objects()
    val preferred = names.firstOrNull { it.string("type") == "ONEME" }
        ?: names.firstOrNull()

    // `name` is often only the first name, so the first and last name are
    // joined first and `name` is just a fallback.
    fun fullNameOf(entry: JsonObject?): String? {
        entry ?: return null

        return listOfNotNull(
            entry.string("firstName"),
            entry.string("lastName")
        ).joinToString(" ").ifBlank { entry.string("name") }
    }

    return fullNameOf(preferred)
        ?: objectValue.string("name")
        ?: objectValue.string("title")
        ?: fullNameOf(objectValue)
        ?: names.firstNotNullOfOrNull(::fullNameOf)
        ?: "MAX user"
}

/** What CODE_ENTER (18) replied: a session, an extra password step, or nothing usable. */
internal sealed interface MaxCodeEntryResult {
    data class LoggedIn(val token: String) : MaxCodeEntryResult
    data class PasswordRequired(val trackId: String, val hint: String?) : MaxCodeEntryResult
    data class RegistrationRequired(val token: String) : MaxCodeEntryResult
    data class Rejected(val message: String?) : MaxCodeEntryResult
}

/**
 * CODE_ENTER answers with `tokenAttrs.LOGIN.token` for a finished sign-in. A
 * protected account instead gets a `passwordChallenge` that has to be solved
 * with `PASSWORD_CHECK` (115), and an unknown number gets a `REGISTER` token
 * for the profile-creation step. Reading only the login token made protected
 * accounts look like the code was rejected.
 */
internal fun parseCodeEntry(response: JsonElement?): MaxCodeEntryResult {
    val root = response as? JsonObject ?: return MaxCodeEntryResult.Rejected(null)
    val tokenAttrs = root.obj("tokenAttrs")

    tokenAttrs?.obj("LOGIN")?.string("token")?.let { token ->
        return MaxCodeEntryResult.LoggedIn(token)
    }

    val challenge = root.obj("passwordChallenge")
    val trackId = challenge?.string("trackId")

    if (trackId != null) {
        return MaxCodeEntryResult.PasswordRequired(trackId, challenge.string("hint"))
    }

    tokenAttrs?.obj("REGISTER")?.string("token")?.let { token ->
        return MaxCodeEntryResult.RegistrationRequired(token)
    }

    return MaxCodeEntryResult.Rejected(root.string("localizedMessage"))
}

internal fun parseMaxCodeRequest(response: JsonElement?): MaxCodeRequest? {
    val root = response as? JsonObject ?: return null
    val trackId = root.string("trackId") ?: root.string("token") ?: return null

    return MaxCodeRequest(
        trackId = trackId,
        codeLength = (root.int("codeLength") ?: 6).coerceAtLeast(1),
        blockingDurationSeconds = (root.int("blockingDuration") ?: 0).coerceAtLeast(0)
    )
}

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
    private val storage: PlatformStorage,
    parentScope: CoroutineScope
) : AccountSession {
    override val accountId: String = account.id
    override val providerId: String = MaxProvider.id

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

    private val chats = linkedMapOf<Long, MaxChat>()
    private val users = linkedMapOf<Long, MaxUser>()
    private val pinnedChats = mutableSetOf<Long>()
    private val typing = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val typingJobs = mutableMapOf<Long, Job>()
    private val messageTimes = mutableMapOf<Long, Long>()

    /** Chats whose first message has been reached; older pages stop asking. */
    private val historyStarts = mutableSetOf<Long>()

    private val avatarBytes = mutableMapOf<String, ByteArray>()
    private val maxStickerPacks = mutableMapOf<Long, StickerSetInfo>()
    private val mediaIdsByUrl = mutableMapOf<String, Int>()
    private val mediaUrlsById = mutableMapOf<Int, String>()
    private val mediaMemory = mutableMapOf<Int, ByteArray>()

    /**
     * Videos and GIFs are delivered without a URL; their id/token pair has to
     * be exchanged for a temporary MP4 link. The link is resolved lazily, when
     * the player actually needs the bytes.
     */
    private val videoRefsById = mutableMapOf<Int, MaxVideoRef>()

    private var rpc: MaxRpc? = null
    private val connectMutex = Mutex()
    private var pushJob: Job? = null
    private var monitorJob: Job? = null
    private var dialogsSaveJob: Job? = null
    private var historySaveJob: Job? = null

    private var started = false
    /** True after the first chat-list sync, so later refreshes stay silent. */
    private var dialogsLoaded = false
    private var closed = false
    private var reconnectAttempts = 0
    private var token: String? = null
    private var deviceId: String = ""

    /**
     * LOGIN returns the newest page of chats plus `chatMarker`, the timestamp
     * from which GET_CHATS pages through the rest. Without it the chat list
     * only contains the few chats LOGIN happened to include.
     */
    private var chatMarker: Long = 0L
    private var meId: Long = 0
    private var verifyToken: String? = null
    private var passwordTrackId: String? = null
    private var passwordHint: String? = null
    private var phoneNumber: String? = null
    private var pendingCodeType: String = "START_AUTH"
    private var codeLength = 6

    private val sessionPath: String
        get() = "max/$accountId/session.json"

    override fun messageHistory(providerDialogId: String): StateFlow<MessageHistory> {
        return historyFor(providerDialogId)?.state ?: emptyHistory
    }

    // ---------------------------------------------------------------- auth --

    override suspend fun start() {
        if (started) return
        started = true
        startHistorySaver()
        _authState.value = AuthState.Initializing

        try {
            val stored = readStoredSession()
            deviceId = stored?.deviceId ?: newDeviceId()

            if (stored == null) {
                _authState.value = AuthState.Challenge(phoneChallenge())
                return
            }

            // Restore the chat list from the previous run before the network
            // answers, so the home screen is populated immediately.
            runCatching { CacheStore.loadDialogs(accountId) }
                .getOrNull()
                ?.takeIf { cached -> cached.isNotEmpty() && _dialogs.value.isEmpty() }
                ?.let { cached -> _dialogs.value = cached }

            // A watchdog keeps the login screen from sitting on "Connecting"
            // forever when the transport stops answering.
            withTimeout(MaxConnectTimeoutMillis) {
                connect(stored.token)
            }
        } catch (exception: TimeoutCancellationException) {
            _authState.value = AuthState.Error(
                "MAX did not answer the sign-in request. Check the connection and try again."
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: MaxApiException) {
            if (exception.isAuthError) {
                clearStoredSession()
                _authState.value = AuthState.Challenge(phoneChallenge())
            } else {
                _authState.value = AuthState.Error(exception.message ?: "MAX sign-in failed")
            }
        } catch (exception: Exception) {
            _authState.value = AuthState.Error(
                "Could not connect to MAX: ${exception.message ?: "unknown error"}"
            )
        }
    }

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
            UnsupportedOperationException("QR confirmation is not supported by the MAX client yet")
        )
    }

    private suspend fun requestCode(rawPhone: String, type: String) {
        val phone = normalizeMaxPhone(rawPhone)

        if (phone.length < 5) {
            _authState.value = AuthState.Challenge(phoneChallenge("Enter a valid phone number"))
            return
        }

        phoneNumber = phone
        pendingCodeType = type
        updateChallenge { it.copy(isSubmitting = true, error = null) }

        try {
            val active = ensureAuthRpc()
            val captchaLink = loadCaptchaLink(active, phone)

            if (captchaLink != null) {
                _authState.value = AuthState.Challenge(captchaChallenge(captchaLink))
                return
            }

            sendCodeRequest(active, phone, type, captchaToken = null)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            _authState.value = AuthState.Challenge(
                phoneChallenge(describeAuthFailure(exception, "Could not request a confirmation code"))
            )
        }
    }

    /**
     * MAX asks for a VK not-a-robot check on some networks. The web client
     * creates the check with opcode 224 and renders the returned link in a
     * widget; the check reports its result with a `vk-sak-sdk` message that
     * carries the token. A missing check is not an error: the server then lets
     * AUTH_REQUEST through without a token.
     */
    private suspend fun loadCaptchaLink(active: MaxRpc, phone: String): String? {
        val response = try {
            active.request(
                opcode = CaptchaSessionOpcode,
                payload = buildJsonObject {
                    put("source", "auth")
                    put("identifier", phone)
                }
            )
        } catch (exception: MaxApiException) {
            if (exception.errorCode == "captcha.create-session-failed") return null
            throw exception
        }

        return parseCaptchaLink(response)
    }

    private suspend fun sendCodeRequest(
        active: MaxRpc,
        phone: String,
        type: String,
        captchaToken: String?
    ) {
        val response = active.request(
            opcode = 17,
            payload = buildJsonObject {
                put("phone", phone)
                put("type", type)
                put("language", "ru")

                if (captchaToken != null) {
                    put("captchaToken", captchaToken)
                }
            }
        )

        val parsed = parseMaxCodeRequest(response)
            ?: error("MAX did not return a verification token")

        verifyToken = parsed.trackId
        codeLength = parsed.codeLength

        _authState.value = AuthState.Challenge(
            codeChallenge(
                if (parsed.blockingDurationSeconds > 0) {
                    "MAX is limiting code requests. Try again in " +
                        "${parsed.blockingDurationSeconds}s."
                } else {
                    null
                }
            )
        )
    }

    private suspend fun submitCaptcha(token: String) {
        val phone = phoneNumber ?: return

        // An empty token means "reload": the previous check expired or could
        // not be displayed, so a fresh link is requested.
        if (token.isBlank()) {
            requestCode(phone, pendingCodeType)
            return
        }

        updateChallenge { it.copy(isSubmitting = true, error = null) }

        try {
            val active = ensureAuthRpc()
            sendCodeRequest(active, phone, pendingCodeType, captchaToken = token)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            _authState.value = AuthState.Challenge(
                phoneChallenge(describeCaptchaFailure(exception))
            )
        }
    }

    private fun describeCaptchaFailure(exception: Throwable): String {
        if (exception is MaxApiException &&
            exception.errorCode.orEmpty().contains("captcha", ignoreCase = true)
        ) {
            return "MAX did not accept the captcha. Please try again."
        }

        return describeAuthFailure(exception, "Could not complete the captcha")
    }

    private fun captchaChallenge(url: String): AuthChallenge {
        return AuthChallenge(
            id = CaptchaChallengeId,
            title = "Confirm you are not a robot",
            description = "MAX asks for a quick check before it sends the confirmation code.",
            actions = listOf(AuthAction.RefreshCaptcha),
            captchaUrl = url
        )
    }

    private fun describeAuthFailure(exception: Throwable, fallback: String): String {
        if (exception is MaxApiException) {
            val code = exception.errorCode.orEmpty()

            if (code.contains("captcha", ignoreCase = true)) {
                return "MAX requires a captcha on this network, and the check could not be loaded. " +
                    "Try another network (for example mobile data instead of Wi-Fi)."
            }

            if (code == "auth.blocked" || code.contains("flood", ignoreCase = true)) {
                return exception.description ?: "MAX is limiting sign-in attempts. Try again later."
            }
        }

        return exception.message ?: fallback
    }

    private suspend fun submitCode(code: String) {
        val verificationToken = verifyToken

        if (verificationToken == null) {
            _authState.value = AuthState.Challenge(phoneChallenge())
            return
        }

        if (code.isBlank()) {
            updateChallenge { it.copy(error = "Enter the code from the SMS") }
            return
        }

        updateChallenge { it.copy(isSubmitting = true, error = null) }

        try {
            val active = rpc ?: ensureAuthRpc()
            val response = active.request(
                opcode = 18,
                payload = buildJsonObject {
                    put("token", verificationToken)
                    put("verifyCode", code.trim())
                    put("authTokenType", "CHECK_CODE")
                }
            )

            val root = response?.jsonObject ?: error("MAX did not answer the code check")

            when (val result = parseCodeEntry(root)) {
                is MaxCodeEntryResult.LoggedIn -> {
                    verifyToken = null
                    phoneNumber = null
                    connect(result.token)
                }

                is MaxCodeEntryResult.PasswordRequired -> {
                    passwordTrackId = result.trackId
                    passwordHint = result.hint
                    _authState.value = AuthState.Challenge(passwordChallenge())
                }

                is MaxCodeEntryResult.RegistrationRequired -> {
                    _authState.value = AuthState.Challenge(
                        codeChallenge(
                            root.string("localizedMessage")
                                ?: "MAX treats this phone number as a new account, and creating an " +
                                "account from Scrollix is not supported yet"
                        )
                    )
                }

                is MaxCodeEntryResult.Rejected -> {
                    _authState.value = AuthState.Challenge(
                        codeChallenge(
                            result.message ?: "MAX did not accept the confirmation code"
                        )
                    )
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            _authState.value = AuthState.Challenge(
                codeChallenge(describeAuthFailure(exception, "Could not verify the code"))
            )
        }
    }

    /**
     * PASSWORD_CHECK (115) finishes the sign-in of an account protected with a
     * password. The web client calls it with the `trackId` from the
     * `passwordChallenge` and answers with the same `tokenAttrs.LOGIN.token`
     * shape as CODE_ENTER.
     */
    private suspend fun submitPassword(password: String) {
        val trackId = passwordTrackId

        if (trackId == null) {
            _authState.value = AuthState.Challenge(phoneChallenge())
            return
        }

        if (password.isBlank()) {
            updateChallenge { it.copy(error = "Enter the MAX account password") }
            return
        }

        updateChallenge { it.copy(isSubmitting = true, error = null) }

        try {
            val active = rpc ?: ensureAuthRpc()
            val response = active.request(
                opcode = 115,
                payload = buildJsonObject {
                    put("trackId", trackId)
                    put("password", password)
                }
            )

            val root = response?.jsonObject ?: error("MAX did not answer the password check")
            val accessToken = root.obj("tokenAttrs")?.obj("LOGIN")?.string("token")

            if (accessToken.isNullOrBlank()) {
                updateChallenge {
                    it.copy(
                        isSubmitting = false,
                        error = root.string("localizedMessage")
                            ?: "MAX rejected the password"
                    )
                }
                return
            }

            passwordTrackId = null
            passwordHint = null
            verifyToken = null
            phoneNumber = null
            connect(accessToken)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            updateChallenge {
                it.copy(
                    isSubmitting = false,
                    error = describeAuthFailure(exception, "Could not verify the password")
                )
            }
        }
    }

    private suspend fun ensureAuthRpc(): MaxRpc {
        rpc?.let { return it }

        val created = createRpc()
        created.connect(scope)

        try {
            created.request(6, initPayload())
            rpc = created
            return created
        } catch (exception: Exception) {
            if (rpc === created) rpc = null
            runCatching { created.close() }
            throw exception
        }
    }

    private suspend fun connect(token: String) {
        closeRpc()

        this.token = token
        this.closed = false

        val created = createRpc()
        created.connect(scope)
        rpc = created

        try {
            created.request(6, initPayload())
            val loginResponse = created.request(19, loginPayload(token))
            handleLogin(loginResponse, token)

            startPushLoop(created)
            startMonitor(created)

            _authState.value = AuthState.Ready
            runCatching { refreshDialogs() }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (rpc === created) rpc = null
            runCatching { created.close() }
            throw exception
        }
    }

    private fun createRpc(): MaxRpc {
        return MaxRpc(
            createMaxSocket(
                url = MaxWebSocketUrl,
                headers = mapOf(
                    "Origin" to MaxOrigin,
                    "User-Agent" to MaxUploadUserAgent
                )
            )
        )
    }

    private fun initPayload(): JsonObject = buildJsonObject {
        put("deviceId", deviceId)
        put(
            "userAgent",
            buildJsonObject {
                // Mirrors the web client's SESSION_INIT userAgent; the web
                // transport rejects an ANDROID deviceType outright.
                put("deviceType", "WEB")
                put("pushDeviceType", "WEBPUSH")
                put("appVersion", MaxAppVersion)
                put("arch", MaxDeviceArch)
                put("buildNumber", MaxBuildNumber)
                put("osVersion", MaxOsVersion)
                put("locale", "ru")
                put("deviceLocale", "ru")
                put("deviceName", MaxDeviceName)
                put("screen", MaxScreen)
                put("timezone", MaxTimezone)
            }
        )
        put("clientSessionId", System.currentTimeMillis())
    }

    private fun loginPayload(token: String): JsonObject = buildJsonObject {
        put("token", token)
        put("interactive", true)
        put("chatsCount", 100)
        // These are sync watermarks of a local cache; a fresh client has none,
        // so the server sends everything instead of skipping the chat list.
        put("chatsSync", 0)
        put("contactsSync", 0)
        put("presenceSync", 0)
        put("draftsSync", 0)
    }

    private fun handleLogin(payload: JsonElement?, token: String) {
        val root = payload?.jsonObject ?: throw MaxApiException(
            errorCode = null,
            description = "MAX did not return a session"
        )

        val contact = root.obj("profile")?.obj("contact")
        meId = contact?.long("id") ?: 0

        if (contact != null) {
            val parsed = parseUser(contact)

            if (parsed != null) {
                users[parsed.id] = parsed
            }

            _profile.value = AccountProfile(
                displayName = parsed?.name ?: "MAX",
                avatar = parsed?.let { avatarFor(it.name, it.pictureUrl) }
            )
        }

        // LOGIN carries user profiles under `contacts` (the web client syncs
        // them straight into its contacts store); `users` is accepted too for
        // older responses. Both use the same contact shape.
        listOf("contacts", "users").forEach { key ->
            root.array(key)?.objects()?.forEach { user ->
                parseUser(user)?.let { parsedUser -> users[parsedUser.id] = parsedUser }
            }
        }

        root.array("chats")?.objects()?.forEach(::mergeChat)
        // `time` is the same kind of server timestamp and is a valid start
        // marker when the response did not carry one.
        chatMarker = root.long("chatMarker") ?: root.long("time") ?: 0L

        reconnectAttempts = 0
        persistSession(token)
        emitDialogs()
    }

    private fun phoneChallenge(error: String? = null): AuthChallenge {
        return AuthChallenge(
            id = PhoneChallengeId,
            title = "Sign in to MAX",
            description = "Enter your phone number. MAX will send a confirmation code by SMS.",
            fields = listOf(
                AuthField(
                    id = "phone",
                    label = "Phone number",
                    type = AuthFieldType.Phone,
                    placeholder = "+7 999 123-45-67"
                )
            ),
            error = error
        )
    }

    private fun codeChallenge(error: String? = null): AuthChallenge {
        return AuthChallenge(
            id = CodeChallengeId,
            title = "Enter the code",
            description = "We sent a $codeLength-digit code to ${phoneNumber ?: "your phone"}.",
            fields = listOf(
                AuthField(
                    id = "code",
                    label = "Confirmation code",
                    type = AuthFieldType.Code
                )
            ),
            actions = listOf(AuthAction.ResendCode),
            error = error
        )
    }

    private fun passwordChallenge(error: String? = null): AuthChallenge {
        val hint = passwordHint?.takeIf { it.isNotBlank() }

        return AuthChallenge(
            id = PasswordChallengeId,
            title = "Enter your password",
            description = buildString {
                append("MAX protects this account with a password. Enter it to finish signing in.")

                if (hint != null) {
                    append(" Hint: $hint")
                }
            },
            fields = listOf(
                AuthField(
                    id = "password",
                    label = "Password",
                    type = AuthFieldType.Password
                )
            ),
            error = error
        )
    }

    private fun updateChallenge(transform: (AuthChallenge) -> AuthChallenge) {
        val current = (authState.value as? AuthState.Challenge)?.challenge ?: return
        _authState.value = AuthState.Challenge(transform(current))
    }

    // ------------------------------------------------------------- history --

    override suspend fun loadMessages(providerDialogId: String, limit: Int): Result<Unit> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))

        return history.loadLatest(limit)
    }

    override suspend fun loadOlderMessages(providerDialogId: String, limit: Int): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))

        return history.loadOlder(limit)
    }

    override suspend fun loadMessagesUntil(
        providerDialogId: String,
        messageId: Long
    ): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))

        return history.loadUntilLoaded(messageId)
    }

    override suspend fun loadMessagesAround(
        providerDialogId: String,
        messageId: Long,
        radius: Int
    ): Result<Boolean> {
        val history = historyFor(providerDialogId)
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))

        return history.loadAround(messageId, radius)
    }

    private fun historyFor(providerDialogId: String): MessageHistoryController? {
        val chatId = providerDialogId.toLongOrNull() ?: return null

        histories.value[providerDialogId]?.let { return it }

        val created = MessageHistoryController(
            pageLoader = object : MessagePageLoader {
                override suspend fun load(
                    fromMessageId: Long,
                    limit: Int
                ): Result<MessagePage> {
                    return try {
                        Result.success(MessagePage(fetchHistory(chatId, fromMessageId, limit)))
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Result.failure(exception)
                    }
                }
            }
        )

        histories.update { existing ->
            if (existing.containsKey(providerDialogId)) {
                existing
            } else {
                existing + (providerDialogId to created)
            }
        }

        val winner = histories.value[providerDialogId] ?: return null
        if (winner !== created) return winner

        // The cached window renders immediately while GET_HISTORY is in
        // flight, so opening a dialog does not wait for the network.
        scope.launch {
            val cached = CacheStore.loadMessages(accountId, providerDialogId)

            if (cached.isNotEmpty() && !closed) {
                created.seed(cached)
            }
        }

        return winner
    }

    /** Persists loaded windows for the next launch. */
    private fun startHistorySaver() {
        if (historySaveJob != null) return

        historySaveJob = scope.launch {
            val saved = mutableMapOf<String, List<ChatMessage>>()

            while (!closed) {
                delay(2_000)

                histories.value.forEach { (dialogId, controller) ->
                    val state = controller.state.value
                    val messages = state.messages

                    // A jump block that is missing newer history must not
                    // replace the newest-page cache.
                    if (
                        messages.isNotEmpty() &&
                        !state.hasMoreNewer &&
                        !state.isLoadingInitial &&
                        saved[dialogId] !== messages
                    ) {
                        CacheStore.saveMessages(accountId, dialogId, messages)
                        saved[dialogId] = messages
                    }
                }
            }
        }
    }

    /**
     * Chat requests can arrive while the socket is being re-established (the
     * chat list stays usable). Reconnect on demand instead of failing with
     * "MAX is not connected".
     */
    private suspend fun requireRpc(): MaxRpc {
        rpc?.takeIf(MaxRpc::isAlive)?.let { return it }

        val currentToken = token
            ?: error("MAX is not connected")

        return connectMutex.withLock {
            rpc?.takeIf(MaxRpc::isAlive) ?: run {
                rpc?.let { stale ->
                    rpc = null
                    runCatching { stale.close() }
                }

                connect(currentToken)
                rpc ?: error("MAX is not connected")
            }
        }
    }

    /**
     * Drops a transport that already failed, so the next attempt dials a fresh
     * socket instead of writing into the dead one again.
     */
    private suspend fun discardRpc(dead: MaxRpc) {
        connectMutex.withLock {
            if (rpc === dead) rpc = null
        }

        runCatching { dead.close() }
    }

    /**
     * Runs a request on a live socket and resends it after reconnecting when
     * MAX closed the connection underneath us. MAX drops idle sockets without
     * a close frame, so the loss only shows up on the next write ("Broken
     * pipe"); resending turns that into a working send. Repeating a write is
     * safe because MSG_SEND carries a `cid` MAX deduplicates.
     */
    private suspend fun <T> onLiveConnection(block: suspend (MaxRpc) -> T): T {
        if (closed) throw MaxConnectionClosedException("session was closed")

        var lastFailure: MaxConnectionClosedException? = null

        repeat(3) { attempt ->
            if (attempt > 0) delay(300L * attempt)

            val active = try {
                requireRpc()
            } catch (exception: MaxConnectionClosedException) {
                lastFailure = exception
                return@repeat
            }

            try {
                return block(active)
            } catch (exception: TimeoutCancellationException) {
                // The socket never answered. Drop it so the next request starts
                // on a fresh connection instead of timing out again.
                discardRpc(active)
                throw exception
            } catch (exception: MaxConnectionClosedException) {
                lastFailure = exception
                discardRpc(active)
            }
        }

        throw lastFailure ?: MaxConnectionClosedException("connection is not available")
    }

    /** Requests that must survive a socket MAX closed while nobody wrote. */
    private suspend fun requestOnLiveConnection(
        opcode: Int,
        payload: JsonObject
    ): JsonElement? = onLiveConnection { active -> active.request(opcode, payload) }

    private suspend fun fetchHistory(
        chatId: Long,
        fromMessageId: Long,
        limit: Int
    ): List<ChatMessage> {
        // Once the beginning of a chat has been seen, a later scroll to the
        // top must not probe the server again: MAX answers those requests
        // with the same oldest page, which keeps the list "loading" forever.
        if (fromMessageId != 0L && chatId in historyStarts) return emptyList()

        val from = if (fromMessageId == 0L) {
            System.currentTimeMillis()
        } else {
            // A failed time lookup must not fall back to "now": that returns
            // the newest page for an older anchor, every message gets filtered
            // out, and the chat looks finished. Returning no page is honest.
            messageTimes[fromMessageId] ?: loadMessageTime(chatId, fromMessageId)
                ?: return emptyList()
        }

        val response = requestOnLiveConnection(
            opcode = 49,
            payload = buildJsonObject {
                put("chatId", chatId)
                put("from", from)
                put("backward", limit.coerceIn(1, 100))
                put("forward", 0)
                put("backwardTime", 0)
                put("forwardTime", 0)
                put("getChat", true)
                put("getMessages", true)
                put("interactive", false)
            }
        )

        response?.obj("chat")?.let(::mergeChat)

        val messages = response?.array("messages").objects()
            .map { parseMessage(chatId, it) }
            // An id of 0 is a parse failure, not a message. It would sort
            // first and become the paging anchor, and fromMessageId = 0 is
            // the "newest page" sentinel, so paging would loop forever.
            .filter { message -> message.id != 0L }

        // MAX returns the anchor itself in an older page. When the page is
        // short and holds nothing before the anchor, this is the chat start.
        if (
            fromMessageId != 0L &&
            messages.size < limit &&
            messages.none { message -> message.id < fromMessageId }
        ) {
            historyStarts += chatId
            return emptyList()
        }

        messages.forEach { message -> messageTimes[message.id] = message.time }

        // GET_HISTORY may omit reactionInfo. The aggregate counters are then
        // fetched for the whole page in one GET_REACTIONS_BULK request.
        val reactions = fetchBulkReactions(chatId, messages)

        return messages
            .map { message ->
                toChatMessage(
                    chatId = chatId,
                    message = reactions[message.id]?.let { loaded ->
                        message.copy(reactions = loaded)
                    } ?: message
                )
            }
            .sortedBy { it.messageId }
    }

    /** Aggregate reactions for a page of messages, keyed by message id. */
    private suspend fun fetchBulkReactions(
        chatId: Long,
        messages: List<MaxMessage>
    ): Map<Long, List<MessageReactionInfo>> {
        if (messages.isEmpty()) return emptyMap()

        val response = runCatching {
            requestOnLiveConnection(
                opcode = 180,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put(
                        "messageIds",
                        buildJsonArray {
                            messages.forEach { message ->
                                add(JsonPrimitive(message.id.toString()))
                            }
                        }
                    )
                }
            )
        }.getOrNull() ?: return emptyMap()

        val payload = response.obj("messagesReactions")
            ?: response.obj("reactions")
            ?: return emptyMap()
        val result = mutableMapOf<Long, List<MessageReactionInfo>>()

        payload.forEach { (key, value) ->
            val messageId = key.toLongOrNull() ?: return@forEach
            val reactions = mapReactions(value as? JsonObject)

            if (reactions.isNotEmpty()) {
                result[messageId] = reactions
            }
        }

        return result
    }

    private suspend fun loadMessageTime(chatId: Long, messageId: Long): Long? {
        val active = rpc ?: return null

        val response = active.request(
            opcode = 71,
            payload = buildJsonObject {
                put("chatId", chatId)
                put(
                    "messageIds",
                    buildJsonArray { add(JsonPrimitive(messageId)) }
                )
            }
        )

        return response?.array("messages").objects().firstOrNull()?.long("time")
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
            ?: return Result.failure(IllegalArgumentException("Unknown MAX media id"))

        val cachePath = mediaCachePath(url)
        storage.readBytes(cachePath)?.takeIf { it.isNotEmpty() }?.let { cached ->
            mediaMemory[fileId] = cached
            return Result.success(cached)
        }

        val bytes = fetchUrlBytes(url)
            ?: return Result.failure(Exception("Could not download the MAX media"))

        if (bytes.isEmpty()) {
            return Result.failure(Exception("MAX returned an empty media file"))
        }

        mediaMemory[fileId] = bytes
        runCatching { storage.writeBytes(cachePath, bytes) }

        return Result.success(bytes)
    }

    override suspend fun loadCachedMedia(remoteUniqueId: String): ByteArray? {
        if (!remoteUniqueId.startsWith("http")) return null

        return runCatching { storage.readBytes(mediaCachePath(remoteUniqueId)) }.getOrNull()
    }

    /**
     * Exchanges a video/GIF id and token for a temporary CDN URL. MAX only
     * returns MP4 links here, which is also what the WebM frame decoder falls
     * back to on Android for the autoplaying GIF previews.
     */
    private suspend fun resolveVideoUrl(fileId: Int, ref: MaxVideoRef): String? {
        return try {
            val response = requestOnLiveConnection(
                opcode = 83,
                payload = buildJsonObject {
                    put("videoId", ref.videoId)
                    put("chatId", ref.chatId)
                    put("messageId", ref.messageId.toString())
                    ref.token?.takeIf { it.isNotBlank() }?.let { token ->
                        put("token", token)
                    }
                }
            )

            listOf("MP4_720", "MP4_480", "MP4_360", "MP4_240", "MP4_144", "EXTERNAL")
                .firstNotNullOfOrNull { key ->
                    response.string(key)?.takeIf { it.startsWith("http") }
                }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            null
        }
    }

    // ------------------------------------------------------------ messages --

    override suspend fun sendMessage(
        providerDialogId: String,
        text: String,
        replyToMessageId: Long?,
        customEmojis: List<CustomEmojiSpan>
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))

        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message is empty"))
        }

        val payload = buildJsonObject {
            put("chatId", chatId)
            put("notify", true)
            put(
                "message",
                buildJsonObject {
                    put("text", trimmed)
                    put("cid", System.currentTimeMillis())
                    put("elements", JsonArray(emptyList()))
                    put("attaches", JsonArray(emptyList()))
                    replyToMessageId?.let { id ->
                        // MAX replies are a `link` of type REPLY carrying the
                        // original message id.
                        put(
                            "link",
                            buildJsonObject {
                                put("type", "REPLY")
                                put("mid", id.toString())
                            }
                        )
                    }
                }
            )
        }

        return try {
            val response = requestOnLiveConnection(opcode = 64, payload = payload)

            val message = response?.obj("message")?.let { parseMessage(chatId, it) }
            if (message != null && message.id != 0L) {
                applySentMessage(chatId, message)
            }

            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun editMessage(
        providerDialogId: String,
        messageId: Long,
        text: String,
        customEmojis: List<CustomEmojiSpan>
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))
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
            Result.failure(exception)
        }
    }

    override suspend fun deleteMessages(
        providerDialogId: String,
        messageIds: LongArray
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))

        if (messageIds.isEmpty()) return Result.success(Unit)

        // Message ids travel as strings in some MAX clients and as numbers in
        // others, and the server answers cmd=3 for the shape it does not
        // expect. Both are tried so a delete is never lost to the encoding.
        val idShapes = listOf(
            messageIds.map { id -> JsonPrimitive(id.toString()) },
            messageIds.map { id -> JsonPrimitive(id) }
        )

        var lastFailure: Throwable? = null

        // Deleting is always meant for every participant, so forMe=false is
        // tried first. MAX only cascades older messages when a CHAT_ACTIVITY
        // (92) watermark was set right before the delete; this client never
        // sends 92 (reads go through CHAT_READ, 50), so the delete is safe.
        for (forMe in listOf(false, true)) {
            for (ids in idShapes) {
                val payload = buildJsonObject {
                    put("chatId", chatId)
                    put("messageIds", JsonArray(ids))
                    put("forMe", forMe)
                }

                val failure = try {
                    requestOnLiveConnection(opcode = 66, payload = payload)
                    null
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    exception
                }

                if (failure == null) {
                    removeDeletedLocally(providerDialogId, chatId, messageIds)
                    return Result.success(Unit)
                }

                lastFailure = failure
            }
        }

        return Result.failure(
            lastFailure ?: Exception("MAX did not delete the message")
        )
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
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))

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
            Result.failure(exception)
        }
    }

    /** Drops deleted ids from the window, the dialog preview and the badges. */
    private fun removeDeletedLocally(
        providerDialogId: String,
        chatId: Long,
        messageIds: LongArray
    ) {
        histories.value[providerDialogId]?.remove(messageIds.toSet())

        val chat = chats[chatId]
        val lastMessageId = chat?.lastMessage?.id

        if (chat != null && lastMessageId != null && lastMessageId in messageIds) {
            chats[chatId] = chat.copy(lastMessage = null)
        }

        emitDialogs()
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

    override suspend fun addMessageReaction(
        providerDialogId: String,
        messageId: Long,
        emoji: String,
        customEmojiId: Long?
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))
        val active = rpc ?: return Result.failure(IllegalStateException("MAX is not connected"))

        // MAX accepted more than one reaction payload shape across its
        // clients (`reactionType`/`id`, `type`/`emoji`, or a bare emoji).
        // Trying them in turn keeps reactions working across deployments.
        val shapes = listOf(
            buildJsonObject {
                put("chatId", chatId)
                put("messageId", messageId.toString())
                put(
                    "reaction",
                    buildJsonObject {
                        put("reactionType", "EMOJI")
                        put("id", emoji)
                    }
                )
            },
            buildJsonObject {
                put("chatId", chatId)
                put("messageId", messageId.toString())
                put(
                    "reaction",
                    buildJsonObject {
                        put("type", "EMOJI")
                        put("emoji", emoji)
                    }
                )
            },
            buildJsonObject {
                put("chatId", chatId)
                put("messageId", messageId.toString())
                put("reaction", emoji)
            }
        )

        var lastFailure: Throwable? = null

        shapes.forEach { payload ->
            try {
                val response = active.request(opcode = 178, payload = payload)

                response?.obj("reactionInfo")?.let { info ->
                    updateReactions(chatId, messageId, mapReactions(info))
                }

                return Result.success(Unit)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                lastFailure = exception
            }
        }

        return Result.failure(lastFailure ?: Exception("MAX rejected the reaction"))
    }

    override suspend fun removeMessageReaction(
        providerDialogId: String,
        messageId: Long,
        emoji: String,
        customEmojiId: Long?
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))
        val active = rpc ?: return Result.failure(IllegalStateException("MAX is not connected"))

        return try {
            active.request(
                opcode = 179,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put("messageId", messageId.toString())
                }
            )

            histories.value[providerDialogId]?.mutate { message ->
                if (message.messageId != messageId) {
                    message
                } else {
                    message.copy(reactions = message.reactions.filterNot { it.emoji == emoji })
                }
            }

            Result.success(Unit)
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
        val reactions = listOf("👍", "❤️", "🔥", "😂", "😮", "😢", "👏", "💯")
            .map { emoji -> MessageReactionOption(emoji) }

        return Result.success(reactions)
    }

    // -------------------------------------------------------------- search --

    override suspend fun searchMessages(
        providerDialogId: String,
        query: String,
        limit: Int
    ): Result<List<ChatMessage>> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))
        val active = rpc ?: return Result.failure(IllegalStateException("MAX is not connected"))

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
            Result.failure(exception)
        }
    }

    override suspend fun searchGlobalMessages(
        query: String,
        limit: Int
    ): Result<List<ChatMessage>> {
        val active = rpc ?: return Result.failure(IllegalStateException("MAX is not connected"))

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
            Result.failure(exception)
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
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))
        val active = rpc ?: return Result.failure(IllegalStateException("MAX is not connected"))

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
            Result.failure(exception)
        }
    }

    override suspend fun loadMessageReadDate(
        providerDialogId: String,
        messageId: Long
    ): Result<Long?> = Result.success(null)

    override suspend fun loadPinnedMessageId(providerDialogId: String): Result<Long?> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))

        return Result.success(chats[chatId]?.pinnedMessageId)
    }

    // ------------------------------------------------------------- profile --

    override suspend fun loadUserProfile(
        providerDialogId: String,
        userId: Long?
    ): Result<UserProfileDetails> {
        val chat = providerDialogId.toLongOrNull()?.let(chats::get)
        val resolvedUserId = userId ?: chat?.peerUserId()

        if (resolvedUserId != null) {
            val user = users[resolvedUserId]
            val name = user?.name ?: chat?.title ?: "User $resolvedUserId"

            return Result.success(
                UserProfileDetails(
                    userId = resolvedUserId,
                    displayName = name,
                    avatar = user?.let { avatarFor(it.name, it.pictureUrl) }
                        ?: chat?.let { avatarFor(name, it.pictureUrl) }
                        ?: Avatar.fromName(name),
                    username = user?.username,
                    phoneNumber = user?.phone,
                    bio = user?.description,
                    kind = DialogKind.Direct,
                    // MAX service accounts are the app's own notices and cannot
                    // be blocked from a third-party client.
                    canBlock = resolvedUserId != meId && user?.isServiceAccount != true
                )
            )
        }

        if (chat == null) {
            return Result.failure(IllegalArgumentException("Invalid MAX chat id"))
        }

        val title = chat.title ?: "MAX chat"
        val members = chat.participantIds.take(50).map { participantId ->
            val member = users[participantId]
            val name = member?.name ?: "User $participantId"

            ChatMemberInfo(
                userId = participantId,
                name = name,
                username = member?.username,
                avatar = member?.let { avatarFor(it.name, it.pictureUrl) }
            )
        }

        return Result.success(
            UserProfileDetails(
                displayName = title,
                avatar = avatarFor(title, chat.pictureUrl),
                kind = chat.kind(),
                memberCount = chat.participantsCount,
                description = chat.description,
                members = members
            )
        )
    }

    override suspend fun loadOwnProfile(): Result<UserProfileDetails> {
        val profile = _profile.value
            ?: return Result.failure(IllegalStateException("MAX is not signed in"))

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
            ?: return Result.failure(IllegalArgumentException("MAX user @$normalized was not found"))

        return Result.success(user.id)
    }

    override suspend fun loadCustomEmoji(emojiId: Long): CustomEmojiAsset? = null

    // -------------------------------------------------------------- shared --

    override suspend fun loadSharedMedia(
        providerDialogId: String,
        kind: SharedMediaKind,
        fromMessageId: Long,
        limit: Int
    ): Result<List<ChatMessage>> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))
        val active = rpc ?: return Result.failure(IllegalStateException("MAX is not connected"))

        val type = when (kind) {
            SharedMediaKind.Photo -> "IMAGE"
            SharedMediaKind.Video -> "VIDEO"
            SharedMediaKind.Animation -> "VIDEO"
            SharedMediaKind.Voice -> "AUDIO"
            SharedMediaKind.Link -> "SHARE"
            SharedMediaKind.File -> "FILE"
        }

        return try {
            val response = active.request(
                opcode = 51,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put("type", type)
                    put("count", limit)
                }
            )

            val messages = response?.array("messages").objects()
                .map { parseMessage(chatId, it) }
                .filter { it.id != 0L }
                .sortedByDescending { it.time }
                .map { toChatMessage(chatId, it) }

            Result.success(messages)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    // --------------------------------------------------------- dialog state --

    override suspend fun setDialogPinned(
        providerDialogId: String,
        isPinned: Boolean,
        folderId: Int?
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))

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
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))

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
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))
        val active = rpc ?: return Result.failure(IllegalStateException("MAX is not connected"))

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
            Result.failure(exception)
        }
    }

    private suspend fun requestChatOperation(chatId: Long, operation: String): Result<Unit> {
        val active = rpc ?: return Result.failure(IllegalStateException("MAX is not connected"))
        val userId = meId.takeIf { it != 0L }
            ?: return Result.failure(IllegalStateException("MAX user is unknown"))

        return try {
            active.request(
                opcode = 77,
                payload = buildJsonObject {
                    put("operation", operation)
                    put("chatId", chatId)
                    put("userIds", buildJsonArray { add(JsonPrimitive(userId)) })
                }
            )

            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    // ------------------------------------------------------- unsupported --

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

    override suspend fun sendPoll(
        providerDialogId: String,
        question: String,
        options: List<String>,
        isAnonymous: Boolean,
        allowsMultipleAnswers: Boolean,
        allowsRevoting: Boolean,
        questionCustomEmojis: List<CustomEmojiSpan>,
        optionCustomEmojis: List<List<CustomEmojiSpan>>,
        optionMediaPaths: List<String?>,
        isQuiz: Boolean,
        correctOptionIndex: Int?,
        explanation: String
    ): Result<Unit> {
        if (isQuiz) {
            return Result.failure(
                UnsupportedOperationException("Quizzes are not supported for MAX")
            )
        }

        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))
        val trimmedQuestion = question.trim()
        val answers = options.map(String::trim).filter(String::isNotEmpty)

        if (trimmedQuestion.isEmpty() || answers.size < 2) {
            return Result.failure(IllegalArgumentException("A poll needs a question and two answers"))
        }

        // FLAG_SETTINGS_ANONYMOUS=1, MULTISELECT=2, REVOTE=4. Custom emojis
        // and option media are Telegram-only for now, so they are ignored.
        val settings = (if (isAnonymous) 1 else 0) or
            (if (allowsMultipleAnswers) 2 else 0) or
            (if (allowsRevoting) 4 else 0)

        val attach = buildJsonObject {
            put("_type", "POLL")
            put("title", trimmedQuestion)
            put(
                "answers",
                buildJsonArray {
                    answers.forEach { answer ->
                        add(buildJsonObject { put("text", answer) })
                    }
                }
            )
            put("settings", settings)
        }

        return sendAttachments(
            chatId = chatId,
            text = "",
            attaches = buildJsonArray { add(attach) }
        ).map { }
    }

    override suspend fun votePoll(
        providerDialogId: String,
        messageId: Long,
        optionIds: List<String>
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))
        val active = rpc ?: return Result.failure(IllegalStateException("MAX is not connected"))
        val answers = optionIds.mapNotNull(String::toLongOrNull)

        if (answers.isEmpty()) {
            return Result.failure(IllegalArgumentException("No poll answers selected"))
        }

        val pollId = histories.value[providerDialogId]
            ?.state
            ?.value
            ?.messages
            ?.firstOrNull { message -> message.messageId == messageId }
            ?.poll
            ?.id
            ?.takeIf { it != 0L }
            ?: fetchPoll(chatId, messageId)?.id
            ?: return Result.failure(IllegalStateException("Poll is not loaded"))

        return try {
            active.request(
                opcode = 304,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put("messageId", messageId.toString())
                    put("pollId", pollId)
                    put("answersIds", buildJsonArray { answers.forEach { add(JsonPrimitive(it)) } })
                }
            )

            refreshPoll(chatId, messageId)
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun sendLocation(
        providerDialogId: String,
        latitude: Double,
        longitude: Double,
        livePeriodSeconds: Int
    ): Result<Long> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))

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

    override suspend fun stopLiveLocation(
        providerDialogId: String,
        messageId: Long
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))
        val active = rpc ?: return Result.failure(IllegalStateException("MAX is not connected"))

        return try {
            active.request(
                opcode = 124,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put("messageId", messageId.toString())
                }
            )

            histories.value[providerDialogId]?.mutate { message ->
                val location = message.location

                if (message.messageId == messageId && location != null) {
                    message.copy(
                        location = location.copy(isLive = false, expiresInSeconds = 0)
                    )
                } else {
                    message
                }
            }

            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private suspend fun sendAttachments(
        chatId: Long,
        text: String,
        attaches: JsonArray
    ): Result<MaxMessage?> {
        val payload = buildJsonObject {
            put("chatId", chatId)
            put("notify", true)
            put(
                "message",
                buildJsonObject {
                    put("text", text)
                    put("cid", System.currentTimeMillis())
                    put("elements", JsonArray(emptyList()))
                    put("attaches", attaches)
                }
            )
        }

        return try {
            val response = requestOnLiveConnection(opcode = 64, payload = payload)

            val message = response?.obj("message")?.let { parseMessage(chatId, it) }
            if (message != null && message.id != 0L) {
                applySentMessage(chatId, message)
            }

            Result.success(message)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private suspend fun fetchPoll(chatId: Long, messageId: Long): MaxPoll? {
        val active = rpc ?: return null

        val response = runCatching {
            active.request(
                opcode = 71,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put("messageIds", buildJsonArray { add(JsonPrimitive(messageId)) })
                }
            )
        }.getOrNull() ?: return null

        val objectValue = response.array("messages").objects()
            .firstOrNull { it.long("id") == messageId }
            ?: return null

        return parseMessage(chatId, objectValue).attaches
            .firstNotNullOfOrNull { it.poll }
    }

    private suspend fun refreshPoll(chatId: Long, messageId: Long) {
        val poll = fetchPoll(chatId, messageId) ?: return

        histories.value[chatId.toString()]?.mutate { message ->
            if (message.messageId == messageId) {
                message.copy(poll = poll.toPollInfo())
            } else {
                message
            }
        }
    }

    /**
     * MAX has no usable forward opcode (`MSG_FWD` 68 is rejected and
     * `FORWARD_MESSAGE` 70 only accepts text), so a forward is reproduced as a
     * copy: the text and every attachment are loaded and sent again. Stickers
     * that came from a MAX pack keep their server ids, so they still arrive as
     * native stickers rather than re-uploaded images.
     */
    override suspend fun forwardMessages(
        providerDialogId: String,
        messageIds: LongArray,
        targetDialogId: String
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))
        val targetChatId = targetDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX target chat id"))

        if (messageIds.isEmpty()) return Result.success(Unit)

        return try {
            val response = requestOnLiveConnection(
                opcode = 71,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put(
                        "messageIds",
                        buildJsonArray {
                            messageIds.forEach { id -> add(JsonPrimitive(id.toString())) }
                        }
                    )
                }
            )

            val messages = response?.array("messages").objects()
                .map { parseMessage(chatId, it) }
                .filter { it.id != 0L }
                .sortedBy { it.time }

            if (messages.isEmpty()) {
                return Result.failure(Exception("The forwarded message could not be loaded"))
            }

            var failure: Throwable? = null

            messages.forEach { message ->
                forwardCopy(targetChatId, message).onFailure { error ->
                    if (failure == null) failure = error
                }
            }

            failure?.let { error -> Result.failure(error) } ?: Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private suspend fun forwardCopy(targetChatId: Long, message: MaxMessage): Result<Unit> {
        var failure: Throwable? = null

        if (message.text.isNotBlank()) {
            sendText(targetChatId, message.text).onFailure { error -> failure = error }
        }

        message.attaches.forEach { attach ->
            forwardAttach(targetChatId, attach).onFailure { error ->
                if (failure == null) failure = error
            }
        }

        return failure?.let { error -> Result.failure(error) } ?: Result.success(Unit)
    }

    private suspend fun sendText(chatId: Long, text: String): Result<Unit> {
        return sendAttachments(
            chatId = chatId,
            text = text,
            attaches = JsonArray(emptyList())
        ).map { }
    }

    private suspend fun forwardAttach(targetChatId: Long, attach: MaxAttach): Result<Unit> {
        val type = attach.type.uppercase()

        if (type == "STICKER") {
            val stickerId = attach.stickerId
            val setId = attach.setId

            if (stickerId != null && setId != null) {
                return sendAttachments(
                    chatId = targetChatId,
                    text = "",
                    attaches = buildJsonArray {
                        add(
                            buildJsonObject {
                                put("_type", "STICKER")
                                put("stickerType", "STATIC")
                                put("setId", setId)
                                put("stickerId", stickerId)
                            }
                        )
                    }
                ).map { }
            }
        }

        // A sticker without server ids is copied as its image.
        if (type == "STICKER") return forwardAttachFile(targetChatId, attach)

        return when (type) {
            "PHOTO", "GIF", "VIDEO", "FILE", "UNSUPPORTED" ->
                forwardAttachFile(targetChatId, attach)

            else -> Result.success(Unit)
        }
    }

    private suspend fun forwardAttachFile(
        targetChatId: Long,
        attach: MaxAttach
    ): Result<Unit> {
        val url = attach.url
            ?: return Result.failure(Exception("The attachment has no downloadable URL"))
        val bytes = fetchUrlBytes(url)
            ?: return Result.failure(Exception("Could not download the attachment"))

        if (bytes.isEmpty()) {
            return Result.failure(Exception("The attachment is empty"))
        }

        val type = attach.type.uppercase()
        val image = sniffImage(bytes)
        val fileName = attach.fileName ?: defaultFileName(url, attach.mimeType)
        val extension = fileName.substringAfterLast('.', "bin")

        if (image != null && type != "FILE" && type != "VIDEO") {
            val photo = sendPhotoBytes(
                chatId = targetChatId,
                bytes = bytes,
                fileName = fileName,
                image = image,
                isGif = type == "GIF" || attach.isGif
            )

            if (photo.isSuccess) return photo
        }

        if (type == "VIDEO" || type == "GIF" || looksLikeMp4(bytes)) {
            val video = sendVideoBytes(
                chatId = targetChatId,
                bytes = bytes,
                width = attach.width,
                height = attach.height,
                durationSeconds = attach.durationSeconds,
                asGif = type == "GIF" || attach.isGif
            )

            if (video.isSuccess) return video
        }

        return sendFileAttach(
            chatId = targetChatId,
            bytes = bytes,
            fileName = fileName,
            extension = extension,
            contentType = attach.mimeType ?: image?.contentType ?: "application/octet-stream"
        )
    }

    private fun defaultFileName(url: String, mimeType: String?): String {
        val extension = when {
            mimeType?.contains("jpeg") == true -> "jpg"
            mimeType?.contains("png") == true -> "png"
            mimeType?.contains("webp") == true -> "webp"
            mimeType?.contains("gif") == true -> "gif"
            mimeType?.contains("mp4") == true -> "mp4"
            mimeType?.contains("webm") == true -> "webm"
            else -> url.substringBefore('?').substringAfterLast('.', "bin")
                .takeIf { it.length in 1..5 } ?: "bin"
        }

        return "attachment.$extension"
    }

    override suspend fun setMessagePinned(
        providerDialogId: String,
        messageId: Long,
        isPinned: Boolean
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))

        return try {
            // MAX pins through CHAT_SETTINGS (55): an id pins, an empty string
            // clears the pin.
            requestOnLiveConnection(
                opcode = 55,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put("pinMessageId", if (isPinned) messageId.toString() else "")
                }
            )

            chats[chatId]?.let { chat ->
                chats[chatId] = chat.copy(
                    pinnedMessageId = messageId.takeIf { isPinned }
                )
            }

            emitDialogs()
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

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

        return Result.failure(IllegalArgumentException("Unknown MAX sticker pack"))
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
            Result.failure(exception)
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

    private suspend fun loadMaxStickerSets(force: Boolean = false): Map<Long, StickerSetInfo> {
        if (!force && maxStickerPacks.isNotEmpty()) return maxStickerPacks

        return try {
            val media = mutableMapOf<Long, MutableList<MessageMedia>>()
            val titles = mutableMapOf<Long, String>()

            // MAX uses both singular and plural asset type names depending on
            // the deployment; request both so a pack list is never empty just
            // because of the label.
            listOf("STICKER", "STICKERS").forEach { assetType ->
                requestOnLiveConnection(
                    opcode = 27,
                    payload = buildJsonObject {
                        put("type", assetType)
                        put("sync", 0)
                    }
                )?.let { root -> collectStickerSets(root, media, titles) }
            }

            // The catalogue is published across several sections; walk each
            // one until it stops returning new pages.
            listOf(
                "NEW_STICKER_SETS",
                "STICKER_SETS",
                "MY_STICKER_SETS",
                "FAVORITE_STICKER_SETS"
            ).forEach { section ->
                var from = 0

                repeat(4) {
                    val page = requestOnLiveConnection(
                        opcode = 26,
                        payload = buildJsonObject {
                            put("sectionId", section)
                            put("from", from)
                            put("count", 100)
                        }
                    )

                    page?.let { root -> collectStickerSets(root, media, titles) }

                    val marker = page?.long("marker") ?: 0
                    if (marker <= 0 || marker.toInt() == from) return@repeat
                    from = marker.toInt()
                }
            }

            maxStickerPacks.clear()

            media.forEach { (setId, rawStickers) ->
                val stickers = rawStickers
                    .filter { sticker -> sticker.stickerId != null }
                    .distinctBy { sticker -> sticker.stickerId }

                if (stickers.isNotEmpty()) {
                    maxStickerPacks[setId] = StickerSetInfo(
                        id = setId,
                        title = titles[setId] ?: "$SyntheticStickerPackTitle #$setId",
                        name = "max-$setId",
                        isInstalled = true,
                        stickers = stickers
                    )
                }
            }

            maxStickerPacks
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            maxStickerPacks
        }
    }

    /** Walks a sticker-catalogue response and keeps every set that has images. */
    private fun collectStickerSets(
        element: JsonElement?,
        media: MutableMap<Long, MutableList<MessageMedia>>,
        titles: MutableMap<Long, String>
    ) {
        when (element) {
            is JsonArray -> element.forEach { item ->
                collectStickerSets(item, media, titles)
            }

            is JsonObject -> {
                // Some MAX responses put the set metadata in `set` and the
                // sticker list in `stickers`; both are accepted here.
                val setObject = element.obj("set")
                val rawStickers = element.array("stickers")?.objects()
                    ?: setObject?.array("stickers")?.objects()

                if (rawStickers != null) {
                    val setId = rawStickers.firstNotNullOfOrNull { sticker -> sticker.long("setId") }
                        ?: setObject?.long("id")
                        ?: element.long("setId")
                        ?: element.long("id")

                    if (setId != null) {
                        titles.putIfAbsent(
                            setId,
                            element.string("title") ?: element.string("name")
                                ?: "$SyntheticStickerPackTitle #$setId"
                        )

                        val stickers = media.getOrPut(setId) { mutableListOf() }
                        rawStickers.forEach { sticker ->
                            parseStickerMedia(sticker, setId)?.let(stickers::add)
                        }
                    }
                }

                element.values.forEach { value ->
                    collectStickerSets(value, media, titles)
                }
            }

            else -> Unit
        }
    }

    private fun parseStickerMedia(objectValue: JsonObject, setId: Long): MessageMedia? {
        val stickerId = objectValue.long("id") ?: return null
        val url = listOf("url", "iconUrl", "imageUrl", "baseUrl")
            .firstNotNullOfOrNull { key ->
                objectValue.string(key)?.takeIf { it.startsWith("http") }
            }
            ?: objectValue.obj("photo")?.string("url")?.takeIf { it.startsWith("http") }
            ?: objectValue.obj("sticker")?.string("url")?.takeIf { it.startsWith("http") }
        val lottieUrl = objectValue.string("lottiePlayUrl")
            ?: objectValue.string("lottieUrl")
            ?: objectValue.obj("sticker")?.string("lottieUrl")

        if (url == null && lottieUrl == null) return null

        val fileId = url?.let(::mediaIdForUrl)
        val emoji = (objectValue.array("tags")?.firstOrNull() as? JsonPrimitive)?.content

        return MessageMedia(
            kind = MessageMediaKind.Sticker,
            previewFileId = fileId,
            previewUniqueId = url,
            fileId = fileId,
            fileUniqueId = url,
            stickerFileId = fileId,
            stickerUniqueId = url,
            stickerFormat = MessageStickerFormat.Webp,
            // Packs may nest the set id inside the sticker entry.
            stickerSetId = objectValue.long("setId") ?: setId,
            stickerId = stickerId,
            stickerEmoji = emoji,
            lottieUrl = lottieUrl,
            width = objectValue.int("width") ?: objectValue.obj("photo")?.int("width") ?: 0,
            height = objectValue.int("height") ?: objectValue.obj("photo")?.int("height") ?: 0
        )
    }

    private fun collectedStickerMedia(): List<MessageMedia> {
        return histories.value.values
            .flatMap { controller -> controller.state.value.messages }
            .asSequence()
            .flatMap { message -> message.allMedia.asSequence() }
            .filter { media -> media.kind == MessageMediaKind.Sticker }
            .filter { media -> media.fileId != null || media.fileUniqueId != null }
            .distinctBy { media -> media.stickerUniqueId ?: media.fileUniqueId ?: media.fileId }
            .toList()
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

    override suspend fun sendSticker(
        providerDialogId: String,
        sticker: MessageMedia
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))
        val stickerId = sticker.stickerId

        // A sticker from a MAX pack goes out natively: MAX only needs its own
        // sticker id, so the animation and attribution stay intact.
        if (stickerId != null) {
            val native = sendAttachments(
                chatId = chatId,
                text = "",
                attaches = buildJsonArray {
                    add(
                        buildJsonObject {
                            put("_type", "STICKER")
                            put("stickerId", stickerId)
                        }
                    )
                }
            )

            // A server id the pack no longer accepts falls back to uploading
            // the sticker image, so the sticker still reaches the chat.
            if (native.isSuccess) return native.map { }
        }

        val bytes = loadMediaBytes(
            fileId = sticker.stickerFileId ?: sticker.fileId,
            uniqueId = sticker.stickerUniqueId ?: sticker.fileUniqueId
        ) ?: return Result.failure(Exception("Could not load the sticker"))

        return sendStickerBytes(
            providerDialogId = providerDialogId,
            bytes = bytes,
            format = sticker.stickerFormat,
            width = sticker.width,
            height = sticker.height
        )
    }

    /**
     * A sticker imported from another provider has no MAX sticker id, so it
     * becomes a MAX photo. Any static image
     * Telegram hands us (WebP, PNG, JPEG, GIF) is uploaded as it is; animated
     * TGS/WebM stickers are prerendered to a transparent animated GIF first,
     * which MAX renders as an animated photo (its SDK models those as
     * `photo.isGif`).
     */
    override suspend fun sendStickerBytes(
        providerDialogId: String,
        bytes: ByteArray,
        format: MessageStickerFormat,
        width: Int,
        height: Int
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))

        if (bytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Sticker is empty"))
        }

        val image = sniffImage(bytes)

        val upload = if (image != null) {
            bytes to image
        } else {
            // TGS (gzip) and WebM are not images, so they have to be
            // rasterized before MAX can display them.
            val gif = renderStickerAnimationGif(
                bytes = bytes,
                format = format,
                width = width,
                height = height
            ) ?: return Result.failure(
                UnsupportedOperationException(
                    "This sticker could not be converted for MAX"
                )
            )

            gif to MaxImageUpload("gif", "image/gif")
        }

        val fileName = "sticker.${upload.second.extension}"
        val photo = sendPhotoBytes(
            chatId = chatId,
            bytes = upload.first,
            fileName = fileName,
            image = upload.second,
            isGif = upload.second.extension == "gif"
        )

        if (photo.isSuccess) return photo

        // MAX does not take every sticker image through the photo pipeline, so
        // fall back to the documented file attachment instead of dropping it.
        return sendFileAttach(
            chatId = chatId,
            bytes = upload.first,
            fileName = fileName,
            extension = upload.second.extension,
            contentType = upload.second.contentType
        )
    }

    /**
     * Sends an animation that came from another provider. A GIF or WebP
     * animation becomes an animated MAX photo; a video animation (Telegram
     * stores saved GIFs as MP4) goes through MAX's video upload.
     */
    override suspend fun sendAnimationBytes(
        providerDialogId: String,
        bytes: ByteArray,
        mimeType: String?,
        width: Int,
        height: Int,
        durationSeconds: Int
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))

        if (bytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Animation is empty"))
        }

        val image = sniffImage(bytes)
        val extension = image?.extension ?: "gif"
        val fileName = "animation.$extension"

        if (image != null) {
            // `isGif` is what makes MAX render the photo as an animation
            // instead of a still picture.
            val photo = sendPhotoBytes(chatId, bytes, fileName, image, isGif = true)

            if (photo.isSuccess) return photo
        }

        if (mimeType?.startsWith("video/") == true || looksLikeMp4(bytes)) {
            val video = sendVideoBytes(
                chatId = chatId,
                bytes = bytes,
                width = width,
                height = height,
                durationSeconds = durationSeconds,
                asGif = true
            )

            if (video.isSuccess) return video
        }

        // A GIF MAX refuses as a photo or video still goes out as a file.
        return sendFileAttach(
            chatId = chatId,
            bytes = bytes,
            fileName = fileName,
            extension = extension,
            contentType = image?.contentType ?: mimeType ?: "image/gif"
        )
    }

    override suspend fun sendAnimation(
        providerDialogId: String,
        animation: MessageMedia
    ): Result<Unit> {
        val bytes = loadMediaBytes(
            fileId = animation.fileId,
            uniqueId = animation.fileUniqueId
        ) ?: return Result.failure(Exception("Could not load the GIF"))

        return sendAnimationBytes(
            providerDialogId = providerDialogId,
            bytes = bytes,
            mimeType = animation.mimeType,
            width = animation.width,
            height = animation.height,
            durationSeconds = animation.durationSeconds
        )
    }

    private suspend fun sendPhotoBytes(
        chatId: Long,
        bytes: ByteArray,
        fileName: String,
        image: MaxImageUpload,
        isGif: Boolean = false
    ): Result<Unit> {
        val upload = uploadPhoto(bytes, fileName, image.contentType)
            ?: return Result.failure(Exception("Could not upload the image to MAX"))

        return sendPhotoAttach(chatId, upload, isGif)
    }

    /**
     * The photo attach shape differs between MAX builds. Every accepted shape
     * is tried in turn (an accepted send stops the loop), because a rejected
     * shape makes the whole message fail and nothing shows up in the chat.
     */
    private suspend fun sendPhotoAttach(
        chatId: Long,
        upload: MaxPhotoUpload,
        isGif: Boolean = false
    ): Result<Unit> {
        val attempts = buildList {
            upload.token?.let { token ->
                add(
                    buildJsonObject {
                        put("_type", "PHOTO")
                        put("photoToken", token)
                        if (isGif) put("isGif", true)
                    }
                )

                add(
                    buildJsonObject {
                        put("_type", "PHOTO")
                        if (isGif) put("isGif", true)
                        put(
                            "photo",
                            buildJsonObject {
                                if (isGif) put("isGif", true)
                                put(
                                    "photos",
                                    buildJsonObject {
                                        put(
                                            "0",
                                            buildJsonObject { put("token", token) }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }

            upload.photoId?.let { photoId ->
                add(
                    buildJsonObject {
                        put("_type", "PHOTO")
                        put("photoId", photoId)
                        if (isGif) put("isGif", true)
                    }
                )
            }
        }

        if (attempts.isEmpty()) {
            return Result.failure(Exception("MAX did not return a photo token"))
        }

        var failure: Throwable? = null

        for (attach in attempts) {
            val result = sendAttachments(
                chatId = chatId,
                text = "",
                attaches = buildJsonArray { add(attach) }
            ).map { }

            if (result.isSuccess) return result

            failure = result.exceptionOrNull()
        }

        return Result.failure(failure ?: Exception("MAX did not accept the photo"))
    }

    /**
     * The attachment flow the MAX clients use: `FILE_UPLOAD` (87) → HTTP
     * upload → `NOTIF_ATTACH` (136) → `MSG_SEND` with a `FILE` attach. It takes
     * any bytes, so a sticker or GIF the photo pipeline refuses still lands in
     * the chat instead of silently disappearing.
     */
    private suspend fun sendFileAttach(
        chatId: Long,
        bytes: ByteArray,
        fileName: String,
        extension: String,
        contentType: String
    ): Result<Unit> {
        val response = runCatching {
            requireRpc().request(
                opcode = 87,
                payload = buildJsonObject {
                    put("name", fileName)
                    put("size", bytes.size)
                    put("ext", extension)
                    put("count", 1)
                }
            )
        }.getOrNull() ?: return Result.failure(Exception("MAX did not accept the upload"))

        val target = mediaUploadTarget(response, idKeys = emptySet())
            ?: return Result.failure(Exception("MAX did not return an upload URL"))

        val body = uploadMaxFile(target.url, fileName, bytes, contentType)
            ?: return Result.failure(Exception("Could not upload the file to MAX"))

        val uploaded = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val info = uploaded?.obj("info")

        val fileKey = uploaded?.string("fileId")
            ?: info?.string("fileId")
            ?: uploaded?.array("info")?.objects()?.firstOrNull()?.string("fileId")
            ?: target.fileKey
            ?: return Result.failure(Exception("MAX did not return a file id"))

        val token = uploaded?.string("token")
            ?: info?.string("token")
            ?: uploaded?.array("info")?.objects()?.firstOrNull()?.string("token")
            ?: target.token

        awaitAttachReady()

        val attach = buildJsonObject {
            put("_type", "FILE")
            put("fileId", fileKey)
            token?.let { value -> put("token", value) }
            put("name", fileName)
            put("size", bytes.size)
        }

        return sendAttachments(
            chatId = chatId,
            text = "",
            attaches = buildJsonArray { add(attach) }
        ).map { }
    }

    /**
     * A file may only be attached after MAX confirms the upload with
     * `NOTIF_ATTACH` (136). The wait is best-effort: a send without the push is
     * still attempted after the timeout, so a missing notification cannot make
     * a sticker send hang forever.
     */
    private suspend fun awaitAttachReady(timeoutMillis: Long = 8_000) {
        val active = rpc ?: return

        runCatching {
            withTimeoutOrNull(timeoutMillis) {
                active.pushes.first { push -> push.opcode == 136 }
            }
        }
    }

    /**
     * `IMAGE_UPLOAD_URL` (80) answers with the upload URL; its query carries
     * the photo ids the server fills in after the POST, and the upload body
     * carries the token and ids MAX uses across its media endpoints.
     */
    private suspend fun uploadPhoto(
        bytes: ByteArray,
        fileName: String,
        contentType: String
    ): MaxPhotoUpload? {
        val response = runCatching {
            requireRpc().request(
                opcode = 80,
                payload = buildJsonObject { put("count", 1) }
            )
        }.getOrNull() ?: return null

        val target = mediaUploadTarget(
            response = response,
            idKeys = setOf("photoId", "id")
        ) ?: return null

        val body = uploadMaxFile(target.url, fileName, bytes, contentType)
            ?: return null

        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

        awaitAttachReady()

        val upload = MaxPhotoUpload(
            token = photoTokenOf(root) ?: photoTokenOf(response) ?: target.token,
            photoId = root?.long("photoId")
                ?: root?.obj("photo")?.long("photoId")
                ?: target.id
                ?: photoIdFromUrl(target.url)
        )

        if (upload.token == null && upload.photoId == null) return null

        return upload
    }

    /** `photoIds` in the upload URL point at the photos the POST fills in. */
    private fun photoIdFromUrl(url: String): Long? {
        val value = url.substringAfter("photoIds=", "").substringBefore('&')

        return value.split(',', '%', ';')
            .firstNotNullOfOrNull { part -> part.toLongOrNull() }
    }

    /** Uploads an MP4 as a MAX video attachment (opcode 82). */
    private suspend fun sendVideoBytes(
        chatId: Long,
        bytes: ByteArray,
        width: Int,
        height: Int,
        durationSeconds: Int,
        asGif: Boolean = false
    ): Result<Unit> {
        val response = runCatching {
            requireRpc().request(
                opcode = 82,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put("count", 1)
                }
            )
        }.getOrNull()
            ?: return Result.failure(Exception("MAX did not accept the video upload"))

        val target = mediaUploadTarget(
            response = response,
            idKeys = setOf("videoId", "id")
        ) ?: return Result.failure(Exception("MAX did not return a video upload URL"))

        val videoId = target.id
            ?: return Result.failure(Exception("MAX did not return a video id"))

        uploadMaxRaw(target.url, bytes, "video/mp4")
            ?: return Result.failure(Exception("Could not upload the GIF to MAX"))

        awaitAttachReady()

        fun videoAttach(type: String, gif: Boolean) = buildJsonObject {
            put("_type", type)
            put("videoId", videoId)
            target.token?.let { token -> put("token", token) }

            if (gif) put("isGif", true)
            if (width > 0) put("width", width)
            if (height > 0) put("height", height)

            // MAX expects video durations in milliseconds.
            if (durationSeconds > 0) put("duration", durationSeconds * 1000L)
        }

        // A GIF is offered as a GIF attach first, so MAX animates it in place
        // instead of rendering a video bubble. Older builds only know VIDEO;
        // the `isGif` flag keeps the animation in that shape as well.
        val attaches = if (asGif) {
            listOf(
                videoAttach("GIF", true),
                videoAttach("VIDEO", true),
                videoAttach("VIDEO", false)
            )
        } else {
            listOf(videoAttach("VIDEO", false))
        }

        var lastFailure: Throwable? = null

        for (attach in attaches) {
            val result = sendAttachments(
                chatId = chatId,
                text = "",
                attaches = buildJsonArray { add(attach) }
            ).map { }

            if (result.isSuccess) return result
            lastFailure = result.exceptionOrNull()
        }

        return Result.failure(
            lastFailure ?: Exception("MAX did not accept the video")
        )
    }

    private fun photoTokenOf(response: JsonElement?): String? {
        val root = response?.jsonObject ?: return null

        root.string("token")?.let { return it }
        root.string("photoToken")?.let { return it }

        root.obj("photo")?.let { photo ->
            (photo.string("token") ?: photo.string("photoToken"))?.let { return it }
        }

        val info = root.array("info")?.objects().orEmpty() +
            listOfNotNull(root.obj("info"))

        info.firstNotNullOfOrNull { entry ->
            entry.string("token") ?: entry.string("photoToken")
        }?.let { return it }

        val photos = root.array("photos")?.objects().orEmpty() +
            root.obj("photos")
                ?.values
                ?.mapNotNull { value -> value as? JsonObject }
                .orEmpty()

        return photos.firstNotNullOfOrNull { entry ->
            entry.string("token") ?: entry.string("photoToken")
        }
    }

    private fun mediaUploadTarget(
        response: JsonElement?,
        idKeys: Set<String>
    ): MaxUploadTarget? {
        val root = response?.jsonObject ?: return null

        val candidates = buildList {
            add(root)
            root.obj("photo")?.let(::add)
            root.obj("info")?.let(::add)
            root.array("info")?.objects()?.let(::addAll)
            root.array("photos")?.objects()?.let(::addAll)
            root.obj("photos")?.values?.forEach { value ->
                (value as? JsonObject)?.let(::add)
            }
        }

        return candidates.firstNotNullOfOrNull { candidate ->
            val url = candidate.string("url")?.takeIf { it.startsWith("http") }
                ?: return@firstNotNullOfOrNull null

            var id: Long? = null
            for (key in idKeys) {
                id = candidate.long(key)
                if (id != null) break
            }

            MaxUploadTarget(
                url = url,
                token = candidate.string("token") ?: candidate.string("photoToken"),
                id = id,
                fileKey = candidate.string("fileId")
            )
        }
    }

    /** Detects the static image formats MAX accepts as a photo. */
    private fun sniffImage(bytes: ByteArray): MaxImageUpload? {
        fun matches(vararg values: Int): Boolean {
            if (bytes.size < values.size) return false

            return values.indices.all { index ->
                (bytes[index].toInt() and 0xFF) == values[index]
            }
        }

        return when {
            matches(0x89, 0x50, 0x4E, 0x47) -> MaxImageUpload("png", "image/png")
            matches(0xFF, 0xD8, 0xFF) -> MaxImageUpload("jpg", "image/jpeg")
            matches(0x47, 0x49, 0x46, 0x38) -> MaxImageUpload("gif", "image/gif")

            bytes.size > 12 &&
                bytes.decodeToString(0, 4) == "RIFF" &&
                bytes.decodeToString(8, 12) == "WEBP" ->
                MaxImageUpload("webp", "image/webp")

            else -> null
        }
    }

    /** An MP4/MOV container, which MAX takes through the video upload. */
    private fun looksLikeMp4(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        if (bytes.decodeToString(4, 8) != "ftyp") return false

        val brand = bytes.decodeToString(8, 12)
        return brand.startsWith("isom") ||
            brand.startsWith("mp4") ||
            brand.startsWith("M4V") ||
            brand.startsWith("qt")
    }

    private fun unsupported(feature: String): Result<Unit> {
        return Result.failure(UnsupportedOperationException("$feature are not supported by the MAX client"))
    }

    // --------------------------------------------------------- live updates --

    private fun startPushLoop(active: MaxRpc) {
        pushJob?.cancel()
        pushJob = scope.launch {
            active.pushes.collect { push -> runCatching { handlePush(push) } }
        }
    }

    private fun handlePush(push: MaxPush) {
        val payload = push.payload ?: return

        when (push.opcode) {
            129 -> {
                val chatId = payload.long("chatId") ?: return
                showTyping(chatId, payload.long("userId"))
            }

            130 -> {
                val chatId = payload.long("chatId") ?: return
                val mark = payload.long("mark") ?: return

                histories.value[chatId.toString()]?.mutate { message ->
                    if (message.isOutgoing && message.timestamp <= mark) {
                        message.copy(isRead = true)
                    } else {
                        message
                    }
                }

                emitDialogs()
            }

            140, 142 -> {
                val chatId = payload.long("chatId") ?: return
                val ids = payload.array("messageIds")
                    ?.mapNotNull { it.asLongOrNull() }
                    ?: listOfNotNull(payload.long("messageId"))

                if (ids.isNotEmpty()) {
                    histories.value[chatId.toString()]?.remove(ids.toSet())
                }
            }

            156 -> {
                val chatId = payload.long("chatId") ?: return
                val messageId = payload.long("messageId") ?: return
                val info = payload.obj("reactionInfo") ?: return
                updateReactions(chatId, messageId, mapReactions(info))
            }
        }

        val chatId = payload.long("chatId")
        val messageObject = payload.obj("message")

        if (chatId != null && messageObject != null) {
            val message = parseMessage(chatId, messageObject)
            if (message.id == 0L) return

            messageTimes[message.id] = message.time

            // A partial push (for example a payload that only carries an id)
            // must not replace a complete message. Edits carry their new text
            // or attachments, so requiring one of those keeps media intact.
            val hasContent = messageObject.containsKey("text") ||
                messageObject.containsKey("attaches")

            if (hasContent) {
                histories.value[chatId.toString()]
                    ?.merge(listOf(toChatMessage(chatId, message)))
            }

            val chat = chats[chatId]
            if (chat != null) {
                val incoming = message.senderId != null && message.senderId != meId
                chats[chatId] = chat.copy(
                    lastMessage = message,
                    lastEventTime = maxOf(chat.lastEventTime, message.time),
                    unread = if (incoming) chat.unread + 1 else chat.unread
                )
            }

            emitDialogs()
        }
    }

    private fun showTyping(chatId: Long, userId: Long?) {
        val name = userId?.let { users[it]?.name } ?: "Someone"

        typing.update { it + (chatId to "$name is typing…") }
        emitDialogs()

        typingJobs[chatId]?.cancel()
        typingJobs[chatId] = scope.launch {
            delay(5_000)
            typing.update { it - chatId }
            typingJobs.remove(chatId)
            emitDialogs()
        }
    }

    private fun updateReactions(
        chatId: Long,
        messageId: Long,
        reactions: List<MessageReactionInfo>
    ) {
        histories.value[chatId.toString()]?.mutate { message ->
            if (message.messageId == messageId) {
                message.copy(reactions = reactions)
            } else {
                message
            }
        }
    }

    private fun applySentMessage(chatId: Long, message: MaxMessage) {
        messageTimes[message.id] = message.time
        histories.value[chatId.toString()]?.merge(listOf(toChatMessage(chatId, message)))

        chats[chatId]?.let { chat ->
            chats[chatId] = chat.copy(
                lastMessage = message,
                lastEventTime = maxOf(chat.lastEventTime, message.time),
                unread = 0
            )
        }

        emitDialogs()
    }

    private fun applyEditedMessage(chatId: Long, messageId: Long, text: String) {
        histories.value[chatId.toString()]?.mutate { message ->
            if (message.messageId == messageId) message.copy(text = text) else message
        }

        val chat = chats[chatId]
        val last = chat?.lastMessage

        if (chat != null && last?.id == messageId) {
            chats[chatId] = chat.copy(lastMessage = last.copy(text = text))
        }

        emitDialogs()
    }

    // --------------------------------------------------------------- dialogs --

    private suspend fun refreshDialogs() {
        val active = rpc ?: return

        // The indicator means "the first chat list is still syncing". Later
        // refreshes (reconnects, background syncs) used to flip it on again,
        // which left the list spinning, so only the first load reports.
        val reportLoading = !dialogsLoaded
        if (reportLoading) _isLoadingDialogs.value = true

        try {
            // The web client repeats GET_CHATS with the marker returned by the
            // previous page until it becomes 0. The marker is a server
            // timestamp from LOGIN, not a local clock value.
            var marker = chatMarker
            var pages = 0

            while (marker != 0L && pages < 100) {
                val response = active.request(
                    opcode = 53,
                    payload = buildJsonObject { put("marker", marker) }
                )

                response?.array("chats")?.objects()?.forEach(::mergeChat)

                val next = response?.long("marker") ?: 0L
                if (next == marker) break

                marker = next
                pages++
            }

            chatMarker = 0L
            fetchMissingChatUsers(active)
            prefetchAvatars()
            refreshOwnProfileAvatar()
            emitDialogs()
            dialogsLoaded = true
        } finally {
            if (reportLoading) _isLoadingDialogs.value = false
        }
    }

    /**
     * DIALOG chats carry no `title`; their display name is the peer contact.
     * LOGIN only returns a handful of contacts, so peers that are not in that
     * snapshot have to be requested explicitly or the row stays nameless.
     */
    private suspend fun fetchMissingChatUsers(active: MaxRpc) {
        val missing = chats.values
            .asSequence()
            .filter { chat -> chat.type == "DIALOG" && chat.id != 0L }
            .mapNotNull { chat -> chat.participantIds.firstOrNull { id -> id != meId } }
            .filter { id -> id !in users }
            .distinct()
            .take(300)
            .toList()

        if (missing.isEmpty()) return

        for (chunk in missing.chunked(100)) {
            val response = runCatching {
                active.request(
                    opcode = 32,
                    payload = buildJsonObject {
                        put(
                            "contactIds",
                            buildJsonArray { chunk.forEach { id -> add(JsonPrimitive(id)) } }
                        )
                    }
                )
            }.getOrNull() ?: continue

            response.array("contacts")?.objects()?.forEach { contact ->
                parseUser(contact)?.let { user -> users[user.id] = user }
            }
        }
    }

    private fun mergeChat(objectValue: JsonObject) {
        val id = objectValue.long("id") ?: return
        val parsed = parseChat(objectValue)
        val previous = chats[id]

        chats[id] = parsed.copy(
            title = parsed.title ?: previous?.title,
            description = parsed.description ?: previous?.description,
            pictureUrl = parsed.pictureUrl ?: previous?.pictureUrl,
            participantsCount = parsed.participantsCount ?: previous?.participantsCount,
            participantIds = parsed.participantIds.ifEmpty { previous?.participantIds.orEmpty() },
            adminIds = parsed.adminIds.ifEmpty { previous?.adminIds.orEmpty() },
            isVerified = parsed.isVerified || previous?.isVerified == true,
            canCopyMessages = parsed.canCopyMessages && previous?.canCopyMessages != false,
            allCanPinMessages = parsed.allCanPinMessages ||
                previous?.allCanPinMessages == true,
            lastMessage = parsed.lastMessage ?: previous?.lastMessage,
            unread = if (objectValue.long("newMessages") != null) parsed.unread else previous?.unread ?: 0,
            lastEventTime = maxOf(parsed.lastEventTime, previous?.lastEventTime ?: 0L)
        )
    }

    private fun parseChat(objectValue: JsonObject): MaxChat {
        val id = objectValue.long("id") ?: 0
        val type = objectValue.string("type") ?: "DIALOG"
        val participantsObject = objectValue.obj("participants")
        val participants = participantsObject?.keys
            ?.mapNotNull { key -> key.toLongOrNull() }
            .orEmpty()

        val lastMessageObject = objectValue.obj("lastMessage")
        val options = objectValue.obj("options")

        return MaxChat(
            id = id,
            type = type,
            title = objectValue.string("title"),
            description = objectValue.string("description"),
            status = objectValue.string("status"),
            ownerId = objectValue.long("owner") ?: 0,
            adminIds = objectValue.array("admins")
                ?.mapNotNull { it.asLongOrNull() }
                ?.toSet()
                .orEmpty(),
            participantIds = participants,
            participantsCount = objectValue.int("participantsCount"),
            pictureUrl = objectValue.obj("picture")?.string("url")
                ?: objectValue.string("baseIconUrl")
                ?: objectValue.string("baseRawIconUrl"),
            lastMessage = lastMessageObject?.let { parseMessage(id, it) },
            unread = objectValue.int("newMessages") ?: 0,
            lastEventTime = objectValue.long("lastEventTime")
                ?: objectValue.long("modified")
                ?: objectValue.long("createdTime")
                ?: 0,
            pinnedMessageId = objectValue.obj("pinnedMessage")?.long("id"),
            isVerified = maxVerifiedFlag(objectValue),
            canCopyMessages = options?.bool("MESSAGE_COPY_NOT_ALLOWED") != true,
            allCanPinMessages = options?.bool("ALL_CAN_PIN_MESSAGE") == true
        )
    }

    /**
     * The profile was built before its avatar bytes were prefetched, so the
     * Accounts list kept showing initials for the signed-in user.
     */
    private fun refreshOwnProfileAvatar() {
        val me = users[meId] ?: return
        val current = _profile.value ?: return

        _profile.value = current.copy(avatar = avatarFor(me.name, me.pictureUrl))
    }

    private fun emitDialogs() {
        val typingState = typing.value

        val sortedChats = chats.values
            .filter { chat -> chat.status != "REMOVED" }
            .sortedByDescending { chat -> chat.lastMessage?.time ?: chat.lastEventTime }

        _dialogs.value = sortedChats.map { chat -> toDialog(chat, typingState[chat.id]) }

        // The full list (avatar, title, last message, time, unread count) is
        // persisted after a short pause so bursts of updates write once.
        dialogsSaveJob?.cancel()
        dialogsSaveJob = scope.launch {
            delay(1_500)

            val dialogs = _dialogs.value
            if (dialogs.isNotEmpty()) {
                CacheStore.saveDialogs(accountId, dialogs)
            }
        }
    }

    private fun toDialog(chat: MaxChat, typingText: String?): Dialog {
        val title = if (chat.id == 0L) {
            "Saved Messages"
        } else {
            chat.title
                ?: chat.peerUser()?.name
                ?: "MAX chat ${chat.id}"
        }

        val lastMessage = chat.lastMessage
        val media = lastMessage?.attaches?.firstOrNull()?.let { attach ->
            toMedia(attach, chat.id, lastMessage.id)
        }
        val peer = chat.peerUser()

        return Dialog(
            providerId = MaxProvider.id,
            accountId = accountId,
            providerDialogId = chat.id.toString(),
            identity = DialogIdentity(MaxProvider.id, chat.id.toString()),
            kind = chat.kind(),
            title = title,
            statusText = chat.participantsCount?.takeIf { chat.type != "DIALOG" }
                ?.let { count -> "$count members" },
            // A direct chat can offer the call action when MAX exposes the
            // peer's phone number.
            phoneNumber = peer?.phone,
            typingText = typingText,
            preview = lastMessage?.let(::previewText).orEmpty(),
            lastMessageMedia = media,
            lastMessageIsOutgoing = lastMessage?.senderId != null && lastMessage.senderId == meId,
            lastMessageIsRead = true,
            timestamp = lastMessage?.time ?: chat.lastEventTime,
            unreadCount = chat.unread,
            memberCount = chat.participantsCount,
            pinnedMessageId = chat.pinnedMessageId,
            // MAX does not expose posting rights for channels; the composer
            // stays available for every chat the account still belongs to and
            // the server reports a failure if posting is not allowed.
            canSendMessages = chat.status != "REMOVED",
            canPinMessages = meId != 0L && (
                meId == chat.ownerId ||
                    meId in chat.adminIds ||
                    chat.allCanPinMessages
            ),
            canDeleteMessages = chat.type != "DIALOG" && meId != 0L && (
                meId == chat.ownerId || meId in chat.adminIds
            ),
            isPinned = chat.id in pinnedChats,
            isSavedMessages = chat.id == 0L,
            isVerified = chat.isVerified || peer?.isVerified == true,
            order = lastMessage?.time ?: chat.lastEventTime,
            avatar = avatarFor(title, chat.pictureUrl ?: peer?.pictureUrl)
        )
    }

    private fun previewText(message: MaxMessage): String {
        if (message.text.isNotBlank()) return message.text

        return message.attaches.firstOrNull()?.previewLabel.orEmpty()
    }

    private suspend fun prefetchAvatars() {
        val urls = buildSet {
            chats.values.forEach { chat -> chat.pictureUrl?.let(::add) }
            users.values.forEach { user -> user.pictureUrl?.let(::add) }
        }
            .filterNot { url -> avatarBytes.containsKey(url) }
            .take(64)

        urls.chunked(6).forEach { chunk ->
            coroutineScope {
                chunk.map { url ->
                    async {
                        url to runCatching { fetchUrlBytes(url) }.getOrNull()
                    }
                }.awaitAll()
            }.forEach { (url, bytes) ->
                if (bytes != null && bytes.isNotEmpty()) {
                    avatarBytes[url] = bytes
                }
            }
        }
    }

    private fun avatarFor(name: String, url: String?): Avatar {
        val base = Avatar.fromName(name)

        if (url == null) return base

        return Avatar(
            initials = base.initials,
            colorSeed = base.colorSeed,
            image = avatarBytes[url],
            url = url
        )
    }

    // -------------------------------------------------------------- mapping --

    private fun parseUser(objectValue: JsonObject): MaxUser? = parseMaxUser(objectValue)

    private fun parseMessage(chatId: Long, objectValue: JsonObject): MaxMessage {
        val rawLink = objectValue.obj("link")
        val link = rawLink?.takeIf { it.string("type") == "FORWARD" }
        val forwards = link?.string("chatName")
        // Replies are a link of type REPLY carrying the original message id.
        val reply = rawLink?.takeIf { it.string("type") == "REPLY" }?.let { value ->
            val replyId = value.long("mid")
                ?: value.obj("message")?.long("id")
                ?: return@let null

            MessageReply(
                messageId = replyId,
                text = value.obj("message")?.string("text").orEmpty()
            )
        }

        val rawText = objectValue.string("text").orEmpty().ifBlank {
            link?.obj("message")?.string("text").orEmpty()
        }
        val isWelcome = rawText.trim() == SavedDialogWelcomeKey
        val text = if (isWelcome) {
            "Welcome to Saved Messages. Messages you send here are visible only to you."
        } else {
            rawText
        }

        val attaches = objectValue.array("attaches").objects().map(::parseAttach)

        return MaxMessage(
            id = objectValue.long("id") ?: 0,
            senderId = objectValue.long("sender"),
            text = text,
            time = objectValue.long("time") ?: 0,
            attaches = attaches,
            reactions = mapReactions(objectValue.obj("reactionInfo")),
            views = objectValue.obj("stats")?.int("views") ?: 0,
            forwardedFromName = forwards,
            replyTo = reply,
            isService = isWelcome || attaches.any { it.type.equals("CONTROL", true) }
        )
    }

    private fun parseAttach(objectValue: JsonObject): MaxAttach {
        val type = objectValue.string("_type") ?: objectValue.string("type") ?: "UNKNOWN"
        val url = listOf("url", "fileUrl", "photoUrl", "baseUrl", "thumbnailUrl", "previewData")
            .firstNotNullOfOrNull { key ->
                objectValue.string(key)?.takeIf { it.startsWith("http") }
            }

        val latitude = objectValue.double("latitude")
            ?: objectValue.obj("location")?.double("latitude")
        val longitude = objectValue.double("longitude")
            ?: objectValue.obj("location")?.double("longitude")
        val livePeriod = objectValue.long("livePeriod")
            ?: objectValue.obj("location")?.long("livePeriod")
            ?: 0
        val photo = objectValue.obj("photo")
        val sticker = objectValue.obj("sticker")
        val video = objectValue.obj("video")
        // Video and GIF attachments in history only carry a server id and a
        // token. Their bytes are resolved lazily through GET_VIDEO_URL; the
        // static thumbnail is the only immediately downloadable URL.
        val thumbnailUrl = objectValue.string("thumbnail")
            ?.takeIf { it.startsWith("http") }
            ?: objectValue.obj("thumbnail")?.string("url")?.takeIf { it.startsWith("http") }
            ?: video?.string("thumbnail")?.takeIf { it.startsWith("http") }

        return MaxAttach(
            type = type,
            url = url,
            remoteId = objectValue.string("fileId")
                ?: objectValue.string("photoId")
                ?: objectValue.string("videoId")
                ?: objectValue.string("token"),
            fileName = objectValue.string("name") ?: objectValue.string("fileName"),
            mimeType = objectValue.string("mimeType") ?: objectValue.string("mime"),
            size = objectValue.long("size") ?: objectValue.long("fileSize") ?: 0,
            width = objectValue.int("width") ?: photo?.int("width")
                ?: video?.int("width") ?: 0,
            height = objectValue.int("height") ?: photo?.int("height")
                ?: video?.int("height") ?: 0,
            thumbnailUrl = thumbnailUrl,
            videoId = objectValue.long("videoId")
                ?: objectValue.long("video_id")
                ?: video?.long("videoId")
                ?: objectValue.long("gifId"),
            videoToken = objectValue.string("token") ?: video?.string("token"),
            previewBytes = objectValue.string("previewData")?.let(::decodeDataUri),
            durationSeconds = ((objectValue.long("duration") ?: 0) / 1000).toInt(),
            // MAX models an animated image as a photo with the `isGif` flag,
            // but some clients only send the file as `FILE`/`PHOTO` with a
            // GIF mime type or `.gif` name. Both shapes must stay animated.
            isGif = objectValue.bool("isGif") == true ||
                objectValue.bool("gif") == true ||
                photo?.bool("isGif") == true ||
                type.equals("GIF", ignoreCase = true) ||
                (objectValue.string("mimeType") ?: objectValue.string("mime"))
                    ?.startsWith("image/gif", ignoreCase = true) == true ||
                (objectValue.string("name") ?: objectValue.string("fileName"))
                    ?.endsWith(".gif", ignoreCase = true) == true ||
                url?.substringBefore('?')?.endsWith(".gif", ignoreCase = true) == true,
            stickerId = objectValue.long("stickerId") ?: sticker?.long("id"),
            setId = objectValue.long("setId") ?: sticker?.long("setId"),
            // MAX ships most stickers as Lottie animations; the static `url`
            // only carries the first frame.
            lottieUrl = objectValue.string("lottiePlayUrl")
                ?: objectValue.string("lottieUrl")
                ?: sticker?.string("lottieUrl"),
            latitude = latitude,
            longitude = longitude,
            // MAX sends the live period in milliseconds; small values are
            // treated as seconds for responses from older clients.
            livePeriodSeconds = when {
                livePeriod <= 0 -> 0
                livePeriod >= 1000 -> (livePeriod / 1000).toInt()
                else -> livePeriod.toInt()
            },
            poll = if (type.equals("POLL", true)) parsePoll(objectValue) else null
        )
    }

    private fun parsePoll(objectValue: JsonObject): MaxPoll? {
        val source = objectValue.obj("poll") ?: objectValue
        val title = source.string("title") ?: return null
        val settings = source.int("settings") ?: 0
        val state = source.obj("state")
        val results = state?.array("result").objects()
        val answers = source.array("answers").objects()

        val options = answers.mapIndexed { index, answer ->
            val answerId = answer.long("answerId")
                ?: answer.long("id")
                ?: (index + 1).toLong()
            val result = results.firstOrNull { it.long("answerId") == answerId }

            MaxPollOption(
                id = answerId.toString(),
                text = answer.string("text").orEmpty(),
                votes = result?.int("voteCount") ?: answer.int("voteCount") ?: 0,
                // MAX marks the option the current account voted for in the
                // first bit of PollResult.options.
                isChosen = ((result?.int("options") ?: 0) and 1) != 0
            )
        }

        return MaxPoll(
            id = source.long("pollId") ?: objectValue.long("pollId") ?: 0,
            question = title,
            options = options,
            totalVoterCount = state?.int("total") ?: options.sumOf { it.votes },
            isAnonymous = (settings and 1) != 0,
            allowsMultipleAnswers = (settings and 2) != 0,
            isClosed = (settings and 8) != 0
        )
    }

    private fun toChatMessage(chatId: Long, message: MaxMessage): ChatMessage {
        val user = message.senderId?.let(users::get)
        val chat = chats[chatId]
        val chatTitle = chat?.title ?: chat?.peerUser()?.name
        val isOwn = message.senderId != null && message.senderId == meId
        // MAX has no per-message rights: the author may delete a message and a
        // chat owner or admin moderates the rest. Channels expose no admin
        // list, so only authored posts are deletable there.
        val canModerate = chat != null && meId != 0L && (
            meId == chat.ownerId || meId in chat.adminIds
        )
        val canDelete = isOwn || canModerate

        return ChatMessage(
            providerId = MaxProvider.id,
            accountId = accountId,
            providerDialogId = chatId.toString(),
            messageId = message.id,
            senderName = user?.name ?: chatTitle,
            senderAvatar = user?.let { avatarFor(it.name, it.pictureUrl) }
                ?: chat?.let { avatarFor(chatTitle ?: "MAX chat", it.pictureUrl) },
            senderId = message.senderId ?: 0,
            forwardedFromName = message.forwardedFromName,
            replyTo = message.replyTo,
            text = message.text,
            timestamp = message.time,
            isOutgoing = isOwn,
            isService = message.isService,
            canBeDeleted = canDelete,
            canBeDeletedForEveryone = canDelete,
            canBeForwarded = chat?.canCopyMessages != false,
            media = message.attaches.firstOrNull()?.let { attach ->
                toMedia(attach, chatId, message.id)
            },
            // MAX delivers every attachment of one message in the same
            // `attaches` array. Mapping only the first one turned media
            // galleries into single images; the full list drives the album
            // grid and the fullscreen viewer.
            mediaItems = message.attaches.mapNotNull { attach ->
                toMedia(attach, chatId, message.id)
            },
            mediaAlbumId = message.id.takeIf { message.attaches.size > 1 },
            poll = message.attaches.firstNotNullOfOrNull { attach -> attach.toPollInfo() },
            location = message.attaches.firstNotNullOfOrNull { attach -> attach.toLocationInfo() },
            reactions = message.reactions,
            viewCount = message.views,
            isRead = true
        )
    }

    private fun toMedia(
        attach: MaxAttach,
        chatId: Long,
        messageId: Long
    ): MessageMedia? {
        val isGif = attach.isGif ||
            attach.mimeType?.startsWith("image/gif", ignoreCase = true) == true ||
            attach.fileName?.endsWith(".gif", ignoreCase = true) == true ||
            attach.url?.substringBefore('?')?.endsWith(".gif", ignoreCase = true) == true

        val kind = when (attach.type.uppercase()) {
            // A GIF that MAX stores as a photo still has to animate in chat.
            "PHOTO" -> if (isGif) MessageMediaKind.Animation else MessageMediaKind.Photo
            "VIDEO" -> if (isGif) {
                MessageMediaKind.Animation
            } else {
                MessageMediaKind.Video
            }
            "AUDIO", "UNSUPPORTED" -> MessageMediaKind.VoiceNote
            // MAX usually wraps GIFs in a FILE attachment (sometimes naming
            // it `sticker.gif`), which used to render as a downloadable
            // binary instead of an autoplaying animation.
            "FILE" -> if (isGif) MessageMediaKind.Animation else MessageMediaKind.File
            "STICKER" -> MessageMediaKind.Sticker
            "GIF" -> MessageMediaKind.Animation
            else -> return null
        }

        val url = attach.url
        var fileId = url?.let(::mediaIdForUrl)

        // A video/GIF attachment usually has no direct URL. Give it a stable
        // synthetic media id and remember how to resolve its real link later.
        if (fileId == null && attach.videoId != null &&
            (kind == MessageMediaKind.Video || kind == MessageMediaKind.Animation)
        ) {
            val key = "max-video:$chatId:$messageId:${attach.videoId}"
            fileId = mediaIdForUrl(key)
            videoRefsById[fileId] = MaxVideoRef(
                chatId = chatId,
                messageId = messageId,
                videoId = attach.videoId,
                token = attach.videoToken
            )
        }

        // The preview is the static thumbnail when MAX provided one; the
        // animated attachment itself is not a preview image.
        val previewUrl = attach.thumbnailUrl
            ?: url?.takeIf { kind == MessageMediaKind.Photo || kind == MessageMediaKind.Sticker }
            ?: attach.url?.takeIf { kind == MessageMediaKind.Animation }
        val previewFileId = previewUrl?.let(::mediaIdForUrl)

        val stickerFormat = when {
            attach.mimeType == "application/x-tgsticker" || url?.endsWith(".tgs") == true ->
                MessageStickerFormat.Tgs

            attach.mimeType == "video/webm" || url?.endsWith(".webm") == true ->
                MessageStickerFormat.Webm

            else -> MessageStickerFormat.Webp
        }

        return MessageMedia(
            kind = kind,
            previewFileId = previewFileId ?: fileId,
            previewUniqueId = previewUrl ?: url,
            fileId = fileId,
            fileUniqueId = url ?: attach.remoteId,
            stickerFileId = fileId.takeIf { kind == MessageMediaKind.Sticker },
            stickerUniqueId = url.takeIf { kind == MessageMediaKind.Sticker },
            stickerFormat = stickerFormat,
            lottieUrl = attach.lottieUrl,
            fileName = attach.fileName,
            mimeType = attach.mimeType,
            size = attach.size,
            width = attach.width,
            height = attach.height,
            durationSeconds = attach.durationSeconds,
            minithumbnail = attach.previewBytes
        )
    }

    private fun mapReactions(objectValue: JsonObject?): List<MessageReactionInfo> {
        val counters = objectValue?.array("counters") ?: objectValue?.array("reactions")
        val chosen = objectValue?.string("yourReaction")
            ?: objectValue?.string("your_reaction")

        return counters.objects().mapNotNull { counter ->
            // Depending on the deployment a counter carries the emoji as a
            // plain string or as a `{reactionType, id}` object.
            val reaction = counter.obj("reaction")
            val emoji = counter.string("reaction")
                ?: reaction?.string("emoji")
                ?: reaction?.string("id")
                ?: counter.string("emoji")
                ?: return@mapNotNull null

            MessageReactionInfo(
                emoji = emoji,
                count = counter.int("count") ?: reaction?.int("count") ?: 0,
                isChosen = emoji == chosen
            )
        }
    }

    private fun mediaIdForUrl(url: String): Int {
        mediaIdsByUrl[url]?.let { return it }

        var candidate = url.hashCode()

        while (true) {
            val existing = mediaUrlsById[candidate]

            if (existing == null || existing == url) break

            candidate++
        }

        mediaIdsByUrl[url] = candidate
        mediaUrlsById[candidate] = url

        return candidate
    }

    private fun mediaCachePath(url: String): String {
        return "max/$accountId/media/${url.hashCode()}-${url.length}.bin"
    }

    /**
     * MAX sends a small inline poster as a base64 data URI. Decoding it here
     * gives photos and GIFs something to show before the real bytes arrive.
     */
    private fun decodeDataUri(value: String): ByteArray? {
        val comma = value.indexOf(',')
        if (!value.startsWith("data:") || comma < 0) return null

        val header = value.substring(0, comma)
        if (!header.contains("base64", ignoreCase = true)) return null

        return runCatching {
            java.util.Base64.getDecoder().decode(value.substring(comma + 1))
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun MaxChat.kind(): DialogKind = when (type) {
        "CHAT" -> DialogKind.Group
        "CHANNEL" -> DialogKind.Channel
        else -> DialogKind.Direct
    }

    private fun MaxChat.peerUserId(): Long? {
        return participantIds.firstOrNull { it != meId }
    }

    private fun MaxChat.peerUser(): MaxUser? = peerUserId()?.let(users::get)

    // -------------------------------------------------------------- storage --

    private fun readStoredSession(): StoredMaxSession? {
        val text = storage.readText(sessionPath) ?: return null
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val token = root.string("token")?.takeIf(String::isNotBlank) ?: return null
        val deviceId = root.string("deviceId")?.takeIf(String::isNotBlank) ?: return null

        return StoredMaxSession(token, deviceId)
    }

    private fun persistSession(token: String) {
        runCatching {
            storage.writeText(
                sessionPath,
                buildJsonObject {
                    put("token", token)
                    put("deviceId", deviceId)
                    put("userId", meId)
                }.toString()
            )
        }
    }

    private fun clearStoredSession() {
        runCatching { storage.delete(sessionPath) }
    }

    private fun newDeviceId(): String {
        fun hex(length: Int) = buildString(length) {
            repeat(length) { append(Random.nextInt(16).toString(16)) }
        }

        return "${hex(8)}-${hex(4)}-${hex(4)}-${hex(4)}-${hex(12)}"
    }

    // ----------------------------------------------------------- connection --

    private fun startMonitor(active: MaxRpc) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            val reason = active.closed.await()

            if (closed || token == null || rpc !== active) return@launch

            rpc = null
            monitorJob = null

            if (reconnectAttempts >= 5) {
                _authState.value = AuthState.Error(
                    "MAX connection was lost: ${reason ?: "unknown reason"}"
                )
                return@launch
            }

            reconnectAttempts++
            delay(2_000L * reconnectAttempts)

            runCatching {
                connectMutex.withLock {
                    // A request that hit the dead socket reconnects on demand;
                    // do not replace that fresh connection after the backoff.
                    if (rpc == null) connect(token!!)
                }
            }.onFailure { exception ->
                    _authState.value = AuthState.Error(
                        "Could not reconnect to MAX: ${exception.message ?: "unknown error"}"
                    )
                }
        }
    }

    private suspend fun closeRpc() {
        pushJob?.cancel()
        pushJob = null

        monitorJob?.cancel()
        monitorJob = null

        val current = rpc
        rpc = null

        current?.close()
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
}
