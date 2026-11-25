package com.mrboomdev.scrollix.data.max.network

import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.SessionException
import com.mrboomdev.scrollix.data.model.sessionExceptionFrom


/**
 * MAX uses dotted opcodes (`auth.invalid`, `flood.wait`, …) and prose
 * descriptions, so both are checked. The result feeds [SessionException] so the
 * UI can branch on codes instead of letters.
 */
internal fun maxSessionErrorCode(
    errorCode: String?,
    description: String?
): SessionErrorCode {
    val code = errorCode.orEmpty().lowercase()
    val text = description.orEmpty().lowercase()

    return when {
        "flood" in code ||
            "too many" in text ||
            "rate limit" in text -> SessionErrorCode.FloodWait

        code.startsWith("auth.") -> SessionErrorCode.Unauthorized

        "timeout" in code || "timed out" in text || "timeout" in text ->
            SessionErrorCode.Timeout

        "captcha" in code -> SessionErrorCode.InvalidRequest

        "not_found" in code || "not.found" in code || "not found" in text ->
            SessionErrorCode.NotFound

        "connection" in code ||
            "socket" in code ||
            "network" in code ||
            "broken pipe" in text -> SessionErrorCode.Network

        else -> sessionExceptionFrom(description ?: errorCode ?: "MAX request failed").code
    }
}

/** Session failure with an explicit code, used by the result-returning methods. */
internal fun maxFailure(
    message: String,
    code: SessionErrorCode = SessionErrorCode.Unknown
): SessionException = SessionException(code = code, message = message)

internal fun maxInvalidChatId(): SessionException =
    maxFailure("Invalid MAX chat id", SessionErrorCode.InvalidRequest)

internal fun maxNotConnected(): SessionException =
    maxFailure("MAX is not connected", SessionErrorCode.Network)

internal fun maxNotSignedIn(): SessionException =
    maxFailure("MAX is not signed in", SessionErrorCode.Unauthorized)

internal fun maxUnsupported(feature: String): SessionException =
    maxFailure("$feature are not supported by the MAX client", SessionErrorCode.Unsupported)
