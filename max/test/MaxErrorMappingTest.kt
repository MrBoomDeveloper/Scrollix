package com.mrboomdev.scrollix.data.max.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.SessionException

class MaxErrorMappingTest {
    @Test
    fun `auth errors map to unauthorized`() {
        assertEquals(
            SessionErrorCode.Unauthorized,
            maxSessionErrorCode("auth.invalid", "Invalid token")
        )
        assertEquals(
            SessionErrorCode.Unauthorized,
            MaxApiException("auth.invalid", "Invalid token").code
        )
    }

    @Test
    fun `flood errors stay retryable`() {
        val exception = MaxApiException("flood.wait", "Too many requests")

        assertEquals(SessionErrorCode.FloodWait, exception.code)
        assertTrue(exception.retryable)
    }

    @Test
    fun `a lost connection is a retryable network failure`() {
        val exception = MaxConnectionClosedException("Broken pipe")

        assertTrue(exception is SessionException)
        assertEquals(SessionErrorCode.Network, exception.code)
        assertTrue(exception.retryable)
    }

    @Test
    fun `unknown codes fall back without throwing`() {
        assertEquals(
            SessionErrorCode.Unknown,
            maxSessionErrorCode(null, "Something odd happened")
        )
    }

    @Test
    fun `result factories carry explicit codes`() {
        assertEquals(SessionErrorCode.InvalidRequest, maxInvalidChatId().code)
        assertEquals(SessionErrorCode.Network, maxNotConnected().code)
        assertEquals(SessionErrorCode.Unauthorized, maxNotSignedIn().code)
        assertEquals(SessionErrorCode.Unsupported, maxUnsupported("Polls").code)
    }
}
