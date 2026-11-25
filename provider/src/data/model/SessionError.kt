package com.mrboomdev.scrollix.data.model

/**
 * Everything that can go wrong in a provider, independent of the protocol.
 * The default retryability is what the UI should assume unless a provider says
 * otherwise.
 */
enum class SessionErrorCode(val retryable: Boolean = false) {
    Network(true),
    Timeout(true),
    FloodWait(true),
    Unauthorized(),
    NotFound(),
    InvalidRequest(),
    Storage(),
    Unsupported(),
    Unknown()
}

/**
 * The single failure type providers should return from session methods. The
 * message stays provider-specific and human-readable; [code] and [retryable]
 * let the UI decide whether to offer a retry instead of parsing text.
 */
open class SessionException(
    val code: SessionErrorCode,
    override val message: String,
    val retryable: Boolean = code.retryable,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Maps a provider's raw error text to a code. TDLib reports errors as stable
 * SCREAMING_SNAKE strings, MAX and VK mix those with prose, so the mapping is
 * intentionally generous and never throws.
 */
fun sessionExceptionFrom(message: String, cause: Throwable? = null): SessionException {
    val text = message.trim().ifEmpty { "Unknown error" }
    val lower = text.lowercase()

    val code = when {
        "flood_wait" in lower ||
            "too many requests" in lower ||
            "flood" in lower -> SessionErrorCode.FloodWait

        "timeout" in lower ||
            "timed out" in lower -> SessionErrorCode.Timeout

        "unauthorized" in lower ||
            "auth_key" in lower ||
            "token_invalid" in lower ||
            "invalid token" in lower ||
            "session expired" in lower -> SessionErrorCode.Unauthorized

        "not found" in lower ||
            "_not_found" in lower -> SessionErrorCode.NotFound

        "connection" in lower ||
            "network" in lower ||
            "socket" in lower ||
            "unreachable" in lower ||
            "disconnected" in lower ||
            "broken pipe" in lower -> SessionErrorCode.Network

        "unsupported" in lower ||
            "not supported" in lower -> SessionErrorCode.Unsupported

        "invalid" in lower ||
            "bad request" in lower -> SessionErrorCode.InvalidRequest

        else -> when {
            Regex("\\b(401|403)\\b").containsMatchIn(text) -> SessionErrorCode.Unauthorized
            Regex("\\b404\\b").containsMatchIn(text) -> SessionErrorCode.NotFound
            Regex("\\b400\\b").containsMatchIn(text) -> SessionErrorCode.InvalidRequest
            else -> SessionErrorCode.Unknown
        }
    }

    return SessionException(code = code, message = text, cause = cause)
}

/** Wraps a raw exception without losing its cause. */
fun Throwable.asSessionException(): SessionException {
    if (this is SessionException) return this

    return sessionExceptionFrom(
        message = message ?: this::class.simpleName ?: "Unknown error",
        cause = this
    )
}
