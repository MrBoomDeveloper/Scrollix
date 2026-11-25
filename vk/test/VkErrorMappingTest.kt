package com.mrboomdev.scrollix.data.vk.network

import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.SessionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VkErrorMappingTest {
    @Test
    fun `auth codes map to unauthorized`() {
        assertEquals(
            SessionErrorCode.Unauthorized,
            VkApiException(5, "User authorization failed").code
        )
        assertEquals(
            SessionErrorCode.Unauthorized,
            VkApiException(28, "Application authorization failed").code
        )
    }

    @Test
    fun `flood codes are retryable`() {
        val exception = VkApiException(9, "Too many requests per second")

        assertEquals(SessionErrorCode.FloodWait, exception.code)
        assertTrue(exception.retryable)
        assertTrue(exception.isFlood)
    }

    @Test
    fun `not found codes cover deleted users and invalid ids`() {
        assertEquals(
            SessionErrorCode.NotFound,
            VkApiException(113, "Invalid user id").code
        )
        assertEquals(
            SessionErrorCode.NotFound,
            VkApiException(18, "User was deleted or banned").code
        )
    }

    @Test
    fun `transport failures are network errors`() {
        val exception = VkApiException(-1, "VK did not answer messages.send")

        assertTrue(exception is SessionException)
        assertEquals(SessionErrorCode.Network, exception.code)
        assertTrue(exception.retryable)
    }

    @Test
    fun `internal server errors stay retryable`() {
        val exception = VkApiException(10, "Internal server error")

        assertTrue(exception.retryable)
        assertTrue(exception.isRetryable)
    }

    @Test
    fun `unsupported features use the shared unsupported code`() {
        assertEquals(SessionErrorCode.Unsupported, vkUnsupported("Polls").code)
    }
}
