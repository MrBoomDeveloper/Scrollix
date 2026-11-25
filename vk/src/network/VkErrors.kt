package com.mrboomdev.scrollix.data.vk.network

import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.SessionException
import com.mrboomdev.scrollix.data.model.sessionExceptionFrom

/**
 * VK's numeric error codes, mapped once for every call. Codes not listed fall
 * back to the text mapping so a new server error still lands on a useful code.
 */
internal fun vkSessionErrorCode(code: Int, reason: String): SessionErrorCode {
    return when (code) {
        // 5 = invalid token, 27 = group auth failed, 28 = app auth failed.
        5, 27, 28 -> SessionErrorCode.Unauthorized

        // 6 = legacy flood, 9 = too many requests per second.
        6, 9 -> SessionErrorCode.FloodWait

        // 10 = internal server error; retryable, but not a client mistake.
        10 -> SessionErrorCode.Unknown

        // 14 = captcha needed, 17 = validation required, 100 = invalid params.
        14, 17, 100 -> SessionErrorCode.InvalidRequest

        // 15 = access denied, 18 = user deleted, 113 = invalid user id.
        15, 18, 113 -> SessionErrorCode.NotFound

        // The HTTP layer failed or returned no parseable envelope.
        -1 -> SessionErrorCode.Network

        else -> sessionExceptionFrom(reason).code
    }
}

internal fun vkUnsupported(feature: String): SessionException =
    SessionException(
        code = SessionErrorCode.Unsupported,
        message = "$feature are not supported by VK here"
    )
