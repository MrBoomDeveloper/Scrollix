package com.mrboomdev.scrollix.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import com.mrboomdev.scrollix.data.max.network.MaxApiException
import com.mrboomdev.scrollix.data.max.network.MaxUploadUserAgent
import com.mrboomdev.scrollix.data.max.network.MaxRpc
import com.mrboomdev.scrollix.data.max.network.array
import com.mrboomdev.scrollix.data.max.network.createMaxSocket
import com.mrboomdev.scrollix.data.max.network.long
import com.mrboomdev.scrollix.data.max.network.normalizeMaxPhone
import com.mrboomdev.scrollix.data.max.network.obj
import com.mrboomdev.scrollix.data.max.network.objects
import com.mrboomdev.scrollix.data.max.network.string
import com.mrboomdev.scrollix.data.model.AccountProfile
import com.mrboomdev.scrollix.data.model.AuthAction
import com.mrboomdev.scrollix.data.model.AuthChallenge
import com.mrboomdev.scrollix.data.model.AuthField
import com.mrboomdev.scrollix.data.model.AuthFieldType
import com.mrboomdev.scrollix.data.model.AuthState

internal suspend fun MaxSession.requestCode(rawPhone: String, type: String) {
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
internal suspend fun MaxSession.loadCaptchaLink(active: MaxRpc, phone: String): String? {
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

internal suspend fun MaxSession.sendCodeRequest(
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

internal suspend fun MaxSession.submitCaptcha(token: String) {
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

internal fun MaxSession.describeCaptchaFailure(exception: Throwable): String {
    if (exception is MaxApiException &&
        exception.errorCode.orEmpty().contains("captcha", ignoreCase = true)
    ) {
        return "MAX did not accept the captcha. Please try again."
    }

    return describeAuthFailure(exception, "Could not complete the captcha")
}

internal fun MaxSession.captchaChallenge(url: String): AuthChallenge {
    return AuthChallenge(
        id = CaptchaChallengeId,
        title = "Confirm you are not a robot",
        description = "MAX asks for a quick check before it sends the confirmation code.",
        actions = listOf(AuthAction.RefreshCaptcha),
        captchaUrl = url
    )
}

internal fun MaxSession.describeAuthFailure(exception: Throwable, fallback: String): String {
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

internal suspend fun MaxSession.submitCode(code: String) {
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
internal suspend fun MaxSession.submitPassword(password: String) {
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

internal suspend fun MaxSession.ensureAuthRpc(): MaxRpc {
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

internal suspend fun MaxSession.connect(token: String) {
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

        _isConnected.value = true
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

internal fun MaxSession.createRpc(): MaxRpc {
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

internal fun MaxSession.initPayload(): JsonObject = buildJsonObject {
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

internal fun MaxSession.loginPayload(token: String): JsonObject = buildJsonObject {
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

internal fun MaxSession.handleLogin(payload: JsonElement?, token: String) {
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

internal fun MaxSession.phoneChallenge(error: String? = null): AuthChallenge {
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

internal fun MaxSession.codeChallenge(error: String? = null): AuthChallenge {
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

internal fun MaxSession.passwordChallenge(error: String? = null): AuthChallenge {
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

internal fun MaxSession.updateChallenge(transform: (AuthChallenge) -> AuthChallenge) {
    val current = (authState.value as? AuthState.Challenge)?.challenge ?: return
    _authState.value = AuthState.Challenge(transform(current))
}

// ------------------------------------------------------------- history --
