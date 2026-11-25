package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.model.AccountProfile
import com.mrboomdev.scrollix.data.model.AuthAction
import com.mrboomdev.scrollix.data.model.AuthChallenge
import com.mrboomdev.scrollix.data.model.AuthField
import com.mrboomdev.scrollix.data.model.AuthFieldType
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.storage.formatPhoneNumber
import com.mrboomdev.scrollix.data.storage.formatTime
import com.mrboomdev.scrollix.generated.BuildConfig
import com.mrboomdev.scrollix.tdlibDeviceModel
import com.mrboomdev.scrollix.tdlibSystemVersion
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

internal suspend fun TelegramSession.handleAuthorizationState(state: AuthorizationState) {
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

                val parametersResult = client.setTdlibParameters(
                    // Test-server builds talk to the test DC and keep their
                    // own database directory (see the storage profile).
                    useTestDc = BuildConfig.TEST_SERVER_MODE,
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
                    deviceModel = tdlibDeviceModel(),
                    systemVersion = tdlibSystemVersion(),
                    applicationVersion = "13.1.1"
                )

                // A second Scrollix process (or a stale hot-reload
                // session) can hold the TDLib database lock. This used to
                // retry in a per-run directory, which "fixed" the lock by
                // opening a second TDLib database and therefore a second
                // server session. Telegram then delivers login codes into
                // that orphaned session instead of sending an SMS, and the
                // user can never read them. Refuse to fork the database
                // and say what is actually wrong.
                if (parametersResult is TdlResult.Failure &&
                    parametersResult.message.contains("lock", ignoreCase = true)
                ) {
                    setError(
                        "Another Scrollix instance is already using this account's " +
                            "database. Close the other window or process and start again. " +
                            "Opening a second database would create a duplicate Telegram " +
                            "session that receives login codes you can never read."
                    )
                    return@withLock
                }

                when (parametersResult) {
                    is TdlResult.Success -> Unit
                    is TdlResult.Failure -> setError(
                        "Failed to configure TDLib: ${parametersResult.message}"
                    )
                }
            }

            is AuthorizationStateWaitPhoneNumber -> {
                setChallenge(
                    AuthChallenge(
                        id = "phone",
                        title = "Your phone number",
                        description =                        "Enter your phone number with the country code, for example +12345678900. " +
                        "Telegram may send the code to your Telegram app on another device " +
                        "instead of SMS.",
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
                        // Only a code with a known next delivery method can
                        // be resent. TDLib rejects the call otherwise with
                        // "Authentication code can't be resent".
                        resendAfterSeconds = state.codeInfo.nextType
                            ?.let { state.codeInfo.timeout.coerceAtLeast(0) },
                        fields = listOf(
                            AuthField(
                                id = "code",
                                label = "Code",
                                type = AuthFieldType.Code
                            )
                        ),
                        actions = buildList {
                            add(AuthAction.Submit)
                            if (state.codeInfo.nextType != null) {
                                add(AuthAction.ResendCode)
                            }
                            add(AuthAction.UseQrCode)
                        }
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
                        // Email code info carries no resend timeout, so the
                        // screen keeps its own cooldown for this challenge.
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

internal suspend fun TelegramSession.setChallengeError(id: String, message: String) {
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

internal fun TelegramSession.codeDeliveryDescription(state: AuthorizationStateWaitCode): String {
    val codeInfo = state.codeInfo
    val phoneNumber = codeInfo.phoneNumber

    val base = when (codeInfo.type) {
        is AuthenticationCodeTypeTelegramMessage -> {
            // TDLib reports this type even when nothing is delivered, and
            // the server may refuse to switch to SMS (nextType == null).
            // A stale Scrollix/desktop session is the usual reason it is
            // chosen, so point at the place that can clear it.
            "Telegram sent the code to a Telegram session that is already signed in " +
                "to this account. If no former Scrollix session is still in use, " +
                "terminate it in Telegram → Settings → Devices so the code is sent " +
                "by SMS instead. Otherwise log in with the QR code."
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
            // Firebase delivery is configured only in the official apps and
            // Scrollix never receives that push, so promising a notification
            // would leave the user waiting for something that cannot arrive.
            "Telegram tried to deliver the code through Firebase, which this app " +
                "can't receive. Tap Resend code to get it by SMS or a phone call instead."
        }

        is AuthenticationCodeTypeSmsWord -> {
            "Telegram sent an SMS with a code word to ${formatPhoneNumber(phoneNumber)}. Check your messages."
        }

        is AuthenticationCodeTypeSmsPhrase -> {
            "Telegram sent an SMS with a code phrase to ${formatPhoneNumber(phoneNumber)}. Check your messages."
        }

    }

    // TDLib alternates delivery channels on resend; telling the user what
    // comes next makes a missing SMS less confusing.
    val nextHint = when (val next = codeInfo.nextType) {
        null -> ""
        is AuthenticationCodeTypeCall ->
            " The next request will arrive as a phone call."

        is AuthenticationCodeTypeFlashCall,
        is AuthenticationCodeTypeMissedCall ->
            " The next request will arrive as a call."

        is AuthenticationCodeTypeSms,
        is AuthenticationCodeTypeSmsWord,
        is AuthenticationCodeTypeSmsPhrase ->
            " The next request will be delivered by SMS."

        else -> ""
    }

    return base + nextHint
}

internal fun TelegramSession.normalizePhoneNumber(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    val digits = trimmed.filter(Char::isDigit)
    if (digits.length < 7) return null

    return when {
        trimmed.startsWith("+") -> "+$digits"
        trimmed.startsWith("00") -> "+${digits.drop(2)}"
        // A common local format: 8 followed by the subscriber number.
        digits.length == 11 && digits.startsWith('8') -> "+7${digits.drop(1)}"
        // The country code is what matters for TDLib; accept it without a
        // leading plus so a formatting slip cannot block the code request.
        else -> "+$digits"
    }
}

/** Turns raw TDLib errors into messages that explain what to do next. */
internal fun TelegramSession.authErrorMessage(raw: String): String {
    val floodWaitSeconds = Regex("FLOOD_WAIT_(\\d+)")
        .find(raw)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()

    return when {
        floodWaitSeconds != null -> {
            val minutes = (floodWaitSeconds + 59) / 60
            "Telegram is limiting code requests for this account. " +
                "Wait about $minutes minute(s) before trying again."
        }

        raw.contains("PHONE_NUMBER_INVALID") ->
            "Telegram rejected this phone number. Check the country code and try again."

        raw.contains("PHONE_NUMBER_BANNED") ->
            "This phone number is banned from Telegram."

        raw.contains("PHONE_NUMBER_OCCUPIED") ->
            "This phone number is already used by another Telegram account."

        raw.contains("PHONE_CODE_INVALID") ->
            "The verification code is incorrect. Check the digits and try again."

        raw.contains("PHONE_CODE_EXPIRED") ->
            "The verification code expired. Request a new one."

        raw.contains("Authentication code can't be resent") ||
            raw.contains("PHONE_CODE_CANT_BE_RESENT") ->
            "Telegram has no other way to deliver this code. Log in with the QR " +
                "code from a phone that is already signed in to this account."

        raw.contains("API_ID_INVALID") || raw.contains("API_ID_PUBLISHED_FLOOD") ->
            "Telegram rejected this app's API credentials (${raw.take(120)}). " +
                "Put a valid application pair into telegram.apiId and telegram.apiHash " +
                "in local.properties and rebuild."

        raw.contains("PHONE_NUMBER_FLOOD") ->
            "Telegram is blocking code requests for this number for a while " +
                "(${raw.take(120)}). Wait before trying again."

        // Never hide an unknown failure: the raw TDLib reason is the only
        // way to tell why the code could not be requested.
        else -> "Telegram: $raw"
    }
}

internal fun TelegramSession.setChallenge(challenge: AuthChallenge) {
    ready = false

    // TDLib re-emits the same state after a failed attempt. Keeping the
    // previous error makes it visible instead of flashing and vanishing.
    val previous = currentChallenge
    val next = if (
        previous != null &&
        previous.id == challenge.id &&
        previous.description == challenge.description
    ) {
        challenge.copy(error = previous.error)
    } else {
        challenge
    }

    currentChallenge = next
    _authState.value = AuthState.Challenge(next)
}

internal fun TelegramSession.setError(message: String) {
    ready = false
    currentChallenge = null
    AppLog.error("telegram", message)
    _authState.value = AuthState.Error(message)
}

internal fun TelegramSession.loadProfileAndChats() {
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

internal fun TelegramSession.updateProfile(user: User) {
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

internal fun TelegramSession.userAvatar(user: User): Avatar {
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

internal fun TelegramSession.userStatusText(user: User): String? {
    return when (val status = _userStatuses.value[user.id] ?: user.status) {
        is UserStatusOnline -> "online"
        is UserStatusOffline -> "last seen ${formatTime(status.wasOnline.toLong() * 1000L)}"
        is UserStatusRecently -> "last seen recently"
        is UserStatusLastWeek -> "last seen within a week"
        is UserStatusLastMonth -> "last seen within a month"
        else -> null
    }
}

internal fun TelegramSession.emojiStatusId(status: EmojiStatus?): Long? {
    return when (val type = status?.type) {
        is EmojiStatusTypeCustomEmoji -> type.customEmojiId
        is EmojiStatusTypeUpgradedGift -> type.symbolCustomEmojiId.takeIf { it != 0L }
        else -> null
    }
}

internal fun TelegramSession.userDisplayName(user: User): String {
    return listOf(user.firstName, user.lastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Telegram account" }
}
