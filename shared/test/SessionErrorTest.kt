package com.mrboomdev.scrollix.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionErrorTest {
    @Test
    fun `flood waits are retryable`() {
        val error = sessionExceptionFrom("FLOOD_WAIT_30: too many requests")

        assertEquals(SessionErrorCode.FloodWait, error.code)
        assertTrue(error.retryable)
    }

    @Test
    fun `timeouts and connection losses are retryable`() {
        assertEquals(
            SessionErrorCode.Timeout,
            sessionExceptionFrom("Request timed out").code
        )
        assertEquals(
            SessionErrorCode.Network,
            sessionExceptionFrom("Connection closed by the server").code
        )
        assertTrue(sessionExceptionFrom("Socket is unreachable").retryable)
    }

    @Test
    fun `authorization and validation failures are not retryable`() {
        val unauthorized = sessionExceptionFrom("HTTP 401 Unauthorized")
        val notFound = sessionExceptionFrom("CHAT_NOT_FOUND")
        val invalid = sessionExceptionFrom("PHONE_CODE_INVALID")

        assertEquals(SessionErrorCode.Unauthorized, unauthorized.code)
        assertEquals(SessionErrorCode.NotFound, notFound.code)
        assertEquals(SessionErrorCode.InvalidRequest, invalid.code)
        assertFalse(unauthorized.retryable)
        assertFalse(notFound.retryable)
        assertFalse(invalid.retryable)
    }

    @Test
    fun `unsupported and unknown failures keep their original text`() {
        val unsupported = sessionExceptionFrom("Mini apps are not supported")
        val unknown = sessionExceptionFrom("Something odd happened")

        assertEquals(SessionErrorCode.Unsupported, unsupported.code)
        assertEquals(SessionErrorCode.Unknown, unknown.code)
        assertEquals("Something odd happened", unknown.message)
    }

    @Test
    fun `wrapping keeps the cause and does not wrap twice`() {
        val cause = IllegalStateException("Socket timeout")
        val mapped = cause.asSessionException()

        assertEquals(SessionErrorCode.Timeout, mapped.code)
        assertSame(cause, mapped.cause)

        val second = mapped.asSessionException()
        assertSame(mapped, second)
    }

    @Test
    fun `a provider may override retryability`() {
        val error = SessionException(
            code = SessionErrorCode.Unknown,
            message = "Retry later",
            retryable = true
        )

        assertTrue(error.retryable)
    }
}
