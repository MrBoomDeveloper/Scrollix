package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.max.network.MaxApiException
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
import com.mrboomdev.scrollix.data.max.network.uploadMaxFile
import com.mrboomdev.scrollix.data.storage.PlatformStorage
import com.mrboomdev.scrollix.fetchUrlBytes
import com.mrboomdev.scrollix.getPlatform
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

// The current web client (web.max.ru bundle) connects to this host; the old
// ws-api.oneme.ru accepts TCP but never answers the WebSocket upgrade.
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
// Matches the browser user agent of the real MAX web client; nothing here
// names Scrollix.
private const val MaxUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"

private const val PhoneChallengeId = "phone"
private const val CodeChallengeId = "code"
private const val CaptchaChallengeId = "max-captcha"

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
    val description: String?
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
    val isService: Boolean
)

internal data class MaxChat(
    val id: Long,
    val type: String,
    val title: String?,
    val description: String?,
    val status: String?,
    val ownerId: Long,
    val participantIds: List<Long>,
    val participantsCount: Int?,
    val pictureUrl: String?,
    val lastMessage: MaxMessage?,
    val unread: Int,
    val lastEventTime: Long,
    val pinnedMessageId: Long?
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

    private val avatarBytes = mutableMapOf<String, ByteArray>()
    private val mediaIdsByUrl = mutableMapOf<String, Int>()
    private val mediaUrlsById = mutableMapOf<Int, String>()
    private val mediaMemory = mutableMapOf<Int, ByteArray>()

    private var rpc: MaxRpc? = null
    private var pushJob: Job? = null
    private var monitorJob: Job? = null

    private var started = false
    private var closed = false
    private var reconnectAttempts = 0
    private var token: String? = null
    private var deviceId: String = ""
    private var meId: Long = 0
    private var verifyToken: String? = null
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
        _authState.value = AuthState.Initializing

        try {
            val stored = readStoredSession()
            deviceId = stored?.deviceId ?: newDeviceId()

            if (stored == null) {
                _authState.value = AuthState.Challenge(phoneChallenge())
                return
            }

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
            val accessToken = root.obj("tokenAttrs")?.obj("LOGIN")?.string("token")

            if (accessToken.isNullOrBlank()) {
                val needsPassword = root.obj("tokenAttrs")?.keys?.any { it != "LOGIN" } == true
                val message = if (needsPassword) {
                    "This MAX account asks for an additional password, which is not supported yet"
                } else {
                    root.string("localizedMessage") ?: "MAX rejected the confirmation code"
                }

                _authState.value = AuthState.Challenge(codeChallenge(message))
                return
            }

            verifyToken = null
            phoneNumber = null
            connect(accessToken)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            _authState.value = AuthState.Challenge(
                codeChallenge(describeAuthFailure(exception, "Could not verify the code"))
            )
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
                    "User-Agent" to MaxUserAgent
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
        put("chatsSync", 100)
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

        root.array("users")?.objects()?.forEach { user ->
            parseUser(user)?.let { users[it.id] = it }
        }

        root.array("chats")?.objects()?.forEach(::mergeChat)

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
            existing + (providerDialogId to (existing[providerDialogId] ?: created))
        }

        return histories.value[providerDialogId]
    }

    private suspend fun fetchHistory(
        chatId: Long,
        fromMessageId: Long,
        limit: Int
    ): List<ChatMessage> {
        val active = rpc ?: error("MAX is not connected")

        val from = if (fromMessageId == 0L) {
            System.currentTimeMillis()
        } else {
            // A failed time lookup must not fall back to "now": that returns
            // the newest page for an older anchor, every message gets filtered
            // out, and the chat looks finished. Returning no page is honest.
            messageTimes[fromMessageId] ?: loadMessageTime(chatId, fromMessageId)
                ?: return emptyList()
        }

        val response = active.request(
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

        messages.forEach { message -> messageTimes[message.id] = message.time }

        return messages.map { toChatMessage(chatId, it) }.sortedBy { it.messageId }
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

        val active = rpc ?: return Result.failure(IllegalStateException("MAX is not connected"))

        return try {
            val response = active.request(
                opcode = 64,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put("notify", true)
                    put(
                        "message",
                        buildJsonObject {
                            put("text", trimmed)
                            put("cid", System.currentTimeMillis())
                            put("elements", JsonArray(emptyList()))
                            put("attaches", JsonArray(emptyList()))
                        }
                    )
                }
            )

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
        val active = rpc ?: return Result.failure(IllegalStateException("MAX is not connected"))

        return try {
            val response = active.request(
                opcode = 67,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put("messageId", messageId.toString())
                    put("text", text)
                    put("elements", JsonArray(emptyList()))
                    put("attachments", JsonArray(emptyList()))
                }
            )

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

        val active = rpc ?: return Result.failure(IllegalStateException("MAX is not connected"))

        return try {
            active.request(
                opcode = 66,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put(
                        "messageIds",
                        buildJsonArray { messageIds.forEach { add(JsonPrimitive(it)) } }
                    )
                    // MAX has a server-side cascade bug for forMe=false after a
                    // CHAT_ACTIVITY watermark, so deletes stay local-only here.
                    put("forMe", true)
                }
            )

            histories.value[providerDialogId]?.remove(messageIds.toSet())

            val chat = chats[chatId]
            val lastMessageId = chat?.lastMessage?.id

            if (chat != null && lastMessageId != null && lastMessageId in messageIds) {
                chats[chatId] = chat.copy(lastMessage = null)
            }

            emitDialogs()
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
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

    override suspend fun addMessageReaction(
        providerDialogId: String,
        messageId: Long,
        emoji: String,
        customEmojiId: Long?
    ): Result<Unit> {
        val chatId = providerDialogId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid MAX chat id"))
        val active = rpc ?: return Result.failure(IllegalStateException("MAX is not connected"))

        return try {
            val response = active.request(
                opcode = 178,
                payload = buildJsonObject {
                    put("chatId", chatId)
                    put("messageId", messageId.toString())
                    put(
                        "reaction",
                        buildJsonObject {
                            put("reactionType", "EMOJI")
                            put("id", emoji)
                        }
                    )
                }
            )

            response?.obj("reactionInfo")?.let { info ->
                updateReactions(chatId, messageId, mapReactions(info))
            }

            Result.success(Unit)
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
                    kind = DialogKind.Direct
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
        val active = rpc ?: return Result.failure(IllegalStateException("MAX is not connected"))

        return try {
            val response = active.request(
                opcode = 64,
                payload = buildJsonObject {
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
            )

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

    override suspend fun forwardMessages(
        providerDialogId: String,
        messageIds: LongArray,
        targetDialogId: String
    ): Result<Unit> = unsupported("Forwarding")

    override suspend fun setMessagePinned(
        providerDialogId: String,
        messageId: Long,
        isPinned: Boolean
    ): Result<Unit> = unsupported("Pinned messages")

    override suspend fun loadStickerSet(setId: Long): Result<StickerSetInfo> {
        if (setId != SyntheticStickerPackId) {
            return Result.failure(IllegalArgumentException("Unknown MAX sticker pack"))
        }

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

    override suspend fun setStickerSetInstalled(setId: Long, installed: Boolean): Result<Unit> {
        return if (setId == SyntheticStickerPackId) {
            Result.success(Unit)
        } else {
            unsupported("Sticker packs")
        }
    }

    /**
     * MAX has no sticker catalog, so the stickers this account received are
     * exposed as a synthetic pack. That makes them available to other
     * providers through the cross-provider sticker picker.
     */
    override suspend fun loadStickerPacks(kind: StickerPackKind): Result<List<StickerPack>> {
        if (kind != StickerPackKind.Sticker) return Result.success(emptyList())

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
     * MAX has no pack system, so a sticker imported from another provider is
     * re-uploaded as a MAX photo. Only static WebP stickers can be converted
     * without a server-side image pipeline; animated TGS/WebM stickers are
     * rejected instead of being sent as broken attachments.
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

        if (format != MessageStickerFormat.Webp) {
            return Result.failure(
                UnsupportedOperationException(
                    "MAX can only import static WebP stickers; animated stickers need conversion"
                )
            )
        }

        val token = uploadPhotoToken(bytes, "webp", "image/webp")
            ?: return Result.failure(Exception("Could not upload the sticker to MAX"))

        val attach = buildJsonObject {
            put("_type", "PHOTO")
            put("photoToken", token)
        }

        return sendAttachments(
            chatId = chatId,
            text = "",
            attaches = buildJsonArray { add(attach) }
        ).map { }
    }

    private suspend fun uploadPhotoToken(
        bytes: ByteArray,
        extension: String,
        contentType: String
    ): String? {
        val active = rpc ?: return null

        val response = runCatching {
            active.request(
                opcode = 80,
                payload = buildJsonObject {
                    put("count", 1)
                    put("type", 0)
                    put("uploaderType", 0)
                    put("profile", false)
                }
            )
        }.getOrNull() ?: return null

        val url = response.string("url") ?: return null
        val body = uploadMaxFile(url, "sticker.$extension", bytes, contentType) ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return null

        return root.obj("photos")
            ?.values
            ?.firstOrNull()
            ?.jsonObject
            ?.string("token")
            ?: root.string("token")
    }

    override suspend fun sendAnimation(
        providerDialogId: String,
        animation: MessageMedia
    ): Result<Unit> = unsupported("Animations")

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
        _isLoadingDialogs.value = true

        try {
            val response = active.request(
                opcode = 53,
                payload = buildJsonObject {
                    put("count", 100)
                    put("marker", System.currentTimeMillis())
                }
            )

            response?.array("chats")?.objects()?.forEach(::mergeChat)
            prefetchAvatars()
            emitDialogs()
        } finally {
            _isLoadingDialogs.value = false
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
            lastMessage = parsed.lastMessage ?: previous?.lastMessage,
            unread = if (objectValue.long("newMessages") != null) parsed.unread else previous?.unread ?: 0,
            lastEventTime = maxOf(parsed.lastEventTime, previous?.lastEventTime ?: 0L)
        )
    }

    private fun parseChat(objectValue: JsonObject): MaxChat {
        val id = objectValue.long("id") ?: 0
        val type = objectValue.string("type") ?: "DIALOG"
        val participants = objectValue.obj("participants")?.keys
            ?.mapNotNull { key -> key.toLongOrNull() }
            .orEmpty()

        val lastMessageObject = objectValue.obj("lastMessage")

        return MaxChat(
            id = id,
            type = type,
            title = objectValue.string("title"),
            description = objectValue.string("description"),
            status = objectValue.string("status"),
            ownerId = objectValue.long("owner") ?: 0,
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
            pinnedMessageId = objectValue.obj("pinnedMessage")?.long("id")
        )
    }

    private fun emitDialogs() {
        val typingState = typing.value

        _dialogs.value = chats.values
            .filter { chat -> chat.status != "REMOVED" }
            .sortedByDescending { chat -> chat.lastMessage?.time ?: chat.lastEventTime }
            .map { chat -> toDialog(chat, typingState[chat.id]) }
    }

    private fun toDialog(chat: MaxChat, typingText: String?): Dialog {
        val title = chat.title
            ?: chat.peerUser()?.name
            ?: "MAX chat ${chat.id}"

        val lastMessage = chat.lastMessage
        val media = lastMessage?.attaches?.firstOrNull()?.let(::toMedia)
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
            typingText = typingText,
            preview = lastMessage?.let(::previewText).orEmpty(),
            lastMessageMedia = media,
            lastMessageIsOutgoing = lastMessage?.senderId != null && lastMessage.senderId == meId,
            lastMessageIsRead = true,
            timestamp = lastMessage?.time ?: chat.lastEventTime,
            unreadCount = chat.unread,
            memberCount = chat.participantsCount,
            pinnedMessageId = chat.pinnedMessageId,
            canSendMessages = chat.type != "CHANNEL",
            isPinned = chat.id in pinnedChats,
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

    private fun parseUser(objectValue: JsonObject): MaxUser? {
        val id = objectValue.long("id") ?: return null

        return MaxUser(
            id = id,
            name = contactName(objectValue),
            pictureUrl = objectValue.obj("picture")?.string("url"),
            username = objectValue.string("username") ?: objectValue.string("link"),
            phone = objectValue.array("phones").objects().firstOrNull()?.string("number"),
            description = objectValue.string("about") ?: objectValue.string("description")
        )
    }

    private fun contactName(objectValue: JsonObject): String {
        val names = objectValue.array("names").objects()
        val fullName = names.firstOrNull { it.string("type") == "FULL_NAME" }?.string("name")
        val anyName = names.firstOrNull()?.string("name")

        return objectValue.string("name")
            ?: objectValue.string("title")
            ?: objectValue.string("firstName")?.let { first ->
                listOfNotNull(first, objectValue.string("lastName")).joinToString(" ")
            }
            ?: fullName
            ?: anyName
            ?: "MAX user"
    }

    private fun parseMessage(chatId: Long, objectValue: JsonObject): MaxMessage {
        val link = objectValue.obj("link")?.takeIf { it.string("type") == "FORWARD" }
        val forwards = link?.string("chatName")

        val text = objectValue.string("text").orEmpty().ifBlank {
            link?.obj("message")?.string("text").orEmpty()
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
            isService = attaches.any { it.type.equals("CONTROL", true) }
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
            width = objectValue.int("width") ?: 0,
            height = objectValue.int("height") ?: 0,
            durationSeconds = ((objectValue.long("duration") ?: 0) / 1000).toInt(),
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
            text = message.text,
            timestamp = message.time,
            isOutgoing = message.senderId != null && message.senderId == meId,
            isService = message.isService,
            media = message.attaches.firstOrNull()?.let(::toMedia),
            poll = message.attaches.firstNotNullOfOrNull { attach -> attach.toPollInfo() },
            location = message.attaches.firstNotNullOfOrNull { attach -> attach.toLocationInfo() },
            reactions = message.reactions,
            viewCount = message.views,
            isRead = true
        )
    }

    private fun toMedia(attach: MaxAttach): MessageMedia? {
        val kind = when (attach.type.uppercase()) {
            "PHOTO" -> MessageMediaKind.Photo
            "VIDEO" -> MessageMediaKind.Video
            "AUDIO", "UNSUPPORTED" -> MessageMediaKind.VoiceNote
            "FILE" -> MessageMediaKind.File
            "STICKER" -> MessageMediaKind.Sticker
            "GIF" -> MessageMediaKind.Animation
            else -> return null
        }

        val url = attach.url
        val fileId = url?.let(::mediaIdForUrl)

        val stickerFormat = when {
            attach.mimeType == "application/x-tgsticker" || url?.endsWith(".tgs") == true ->
                MessageStickerFormat.Tgs

            attach.mimeType == "video/webm" || url?.endsWith(".webm") == true ->
                MessageStickerFormat.Webm

            else -> MessageStickerFormat.Webp
        }

        return MessageMedia(
            kind = kind,
            previewFileId = fileId,
            previewUniqueId = url,
            fileId = fileId,
            fileUniqueId = url ?: attach.remoteId,
            stickerFileId = fileId.takeIf { kind == MessageMediaKind.Sticker },
            stickerUniqueId = url.takeIf { kind == MessageMediaKind.Sticker },
            stickerFormat = stickerFormat,
            fileName = attach.fileName,
            mimeType = attach.mimeType,
            size = attach.size,
            width = attach.width,
            height = attach.height,
            durationSeconds = attach.durationSeconds
        )
    }

    private fun mapReactions(objectValue: JsonObject?): List<MessageReactionInfo> {
        val counters = objectValue?.array("counters").objects()
        val chosen = objectValue?.string("yourReaction")

        return counters.mapNotNull { counter ->
            val emoji = counter.string("reaction") ?: return@mapNotNull null

            MessageReactionInfo(
                emoji = emoji,
                count = counter.int("count") ?: 0,
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

            runCatching { connect(token!!) }
                .onFailure { exception ->
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
        if (closed) return

        closed = true
        closeRpc()
        scope.cancel()
        _authState.value = AuthState.Closed
    }
}
